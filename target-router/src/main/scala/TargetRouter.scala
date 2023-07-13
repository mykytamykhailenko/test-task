import Util._
import io.circe.{Json, parser}
import io.circe.parser.parse
import model._
import model.UserError._
import router.model._
import zio._
import zio.http.Method.POST
import zio.http.Server.Config
import zio.http._

object TargetRouter extends ZIOAppDefault {

  import io.circe.generic.auto._
  import io.circe.syntax._

  private type Token = Unit

  private val token: Token = ()

  private val aMinute = 1.minute

  private val oneToken = 1

  private val recoveredToken = "Recovered another token"


  private def createRateLimiter(counter: Ref[Int],
                                queue: Queue[Token],
                                tokens: Int,
                                rate: Duration): UIO[RateLimitInfo] =
    for {
      passed <- queue.poll
      count <- if (passed.nonEmpty) counter.updateAndGet(_ - oneToken) else counter.get
      currentTime <- Clock.instant
    } yield RateLimitInfo(passed = passed.nonEmpty, limit = tokens, remaining = count,
      reset = currentTime.toEpochMilli + ((tokens - count) * rate.toMillis))


  /**
   * Creates a token bucket rate limiter.
   *
   * You can specify the number of available tokens.
   * The more tokens you allocate, the worse bursts it can handle.
   * However, you should keep it relatively low, because too many concurrent requests may worsen the overall performance.
   *
   * You can allocate a single token, in which case it will behave like a conventional rate limiter.
   *
   * @return The rate limiter effect
   */
  private def createTokenBucketRateLimiter(): URIO[Configuration, UIO[RateLimitInfo]] =
    for {
      config <- ZIO.service[Configuration]

      tokens = config.tokens.min(config.requests)

      counter <- Ref.make(tokens)
      queue <- Queue.bounded[Token](tokens)
      _ <- queue.offerAll(Array.fill(tokens)(token))

      rate = aMinute.dividedBy(config.requests)
      schedule = Schedule.fixed(rate)

      // It will immediately recover the first token.
      _ <- (queue.offer() *> counter.update(_ + oneToken) *> ZIO.logInfo(recoveredToken)).repeat(schedule).fork

    } yield createRateLimiter(counter, queue, tokens, rate)


  private def extractTarget(body: String, target: Int) =
    ZIO.attempt {
      parse(body).flatMap(_.hcursor.downField("args").get[String]("target"))
        .map(_.toInt)
        .getOrElse(target)
    }.catchAll(_ => ZIO.succeed(target))

  private def handleFlow(configuration: Configuration, request: Request): ZIO[Client, UserError, Option[Response]] = {

    val body = request.body.asString.orDie

    for {
      userRequest <- parseUserRequest(body, acceptEmptyTarget = true)

      requestUrl = userRequest.target.fold("https://httpbin.org/get")(target => s"https://httpbin.org/get?target=$target")

      url <- ZIO.fromEither(URL.decode(requestUrl)).orDie

      _ <- ZIO.logInfo(s"Querying ${url.encode}...")

      target <- Client.request(Request.get(url))
        .flatMap(_.body.asString)
        .flatMap(extractTarget(_, configuration.target))
        .tapErrorCause(cause => ZIO.logInfoCause(s"Encountered an error while accessing ${url.encode}", cause))
        .catchAllCause(_ => ZIO.succeed(configuration.target))

      _ <- ZIO.logInfo(s"The target is: $target")

      body = userRequest.copy(target = Some(target)).asJson.toString()

      response <- Client.request(
        s"${configuration.link}/target",
        POST,
        Headers(Header.ContentType(MediaType.application.`json`)),
        Body.fromString(body)).orDie

    } yield Some(response)

  }

  private def routes(checkRateLimitInfo: UIO[RateLimitInfo]): Http[Configuration with Client, Nothing, Request, Response] = Http.collectZIO[Request] {

    case req@POST -> Root / "find" =>

      val response = for {
        configuration <- ZIO.service[Configuration]

        rateLimitInfo <- checkRateLimitInfo
        rateLimitHeaders = rateLimitInfo.toHeaders

        response <- if (rateLimitInfo.passed && !configuration.debugRateLimits) handleFlow(configuration, req) else ZIO.succeed(None)

      } yield response.fold(Response.status(Status.TooManyRequests).copy(headers = rateLimitHeaders)) { response =>
        response.copy(headers = response.headers ++ rateLimitHeaders)
      }

      response.catchAll { error =>
        val json = error.asJson.toString()
        ZIO.succeed(Response.json(json).copy(status = Status.BadRequest))
      }

    case _ => ZIO.succeed(Response.status(Status.NotFound))

  }

  private val layers = (ZLayer.succeed(Config.default.port(4040)) >>> Server.live) ++ Configuration.live ++ Client.default

  override def run = {
    for {
      checkRateLimitInfo <- createTokenBucketRateLimiter()
      _ <- Server.serve(routes(checkRateLimitInfo) @@ RequestHandlerMiddlewares.debug)
    } yield ()
  }.provideLayer(layers)


}
