import Util._
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import model._
import zio._
import zio.http.Method.POST
import zio.http._

object TargetFinder extends ZIOAppDefault {

  private val routes: UHttpApp = Http.collectZIO[Request] {

    case req @ POST -> Root / "target" =>

      // I do not check the content-type, though I should.
      val body = req.body.asString.orDie

      val response = for {
        UserRequest(data, Some(target)) <- parseUserRequest(body)
        userResponse <- createUserResponse(data, target)
        json = userResponse.asJson.toString()
      } yield Response.json(json)

      response.tapError(error => ZIO.logInfo(s"Encountered following error: $error")).catchAll { error =>
        val json = error.asJson.toString()
        ZIO.succeed(Response.json(json).copy(status = Status.BadRequest))
      }

    case _ => ZIO.succeed(Response.status(Status.NotFound))

  }

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = Server.serve(routes @@ RequestHandlerMiddlewares.debug).provideLayer(Server.default)


}
