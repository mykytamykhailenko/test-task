import io.circe._
import model._
import model.UserError._
import zio.{IO, UIO, ZIO}

import scala.annotation.tailrec

object Util {

  type CirceError = io.circe.Error

  def createUserResponse(data: Array[Int], target: Int): IO[UserError, UserResponse] = {

    def takeValue(tuple: (Int, Int)): Int = tuple._1

    def takeIndex(tuple: (Int, Int)): Int = tuple._2

    val array = data.zipWithIndex.sortBy(takeValue)

    @tailrec
    def findTarget(base: Int, other: Int): IO[UserError, UserResponse] =
      if (array.length > other) {

        val possibleTarget = takeValue(array(base)) + takeValue(array(other))

        if (possibleTarget == target) {
          val data = Array(array(base), array(other))

          ZIO.succeed(UserResponse(data.map(takeIndex), data.map(takeValue)))
        }
        else if (possibleTarget < target) findTarget(base, other + 1)
        else findTarget(base + 1, base + 2)
      } else if (array.length > base) findTarget(base + 1, base + 2) else ZIO.fail(CouldNotFindAppropriateValues)


    if (data.length > 1) findTarget(0, 1) else ZIO.fail(NotEnoughData)
  }


  import io.circe.generic.auto._

  def parseUserRequest(body: UIO[String], acceptEmptyTarget: Boolean = false): IO[UserError, UserRequest] = {

    val parse =
      body
        .map(parser.parse)
        .absolve[CirceError, Json] // The compiler needs this hint to compile.
        .map(_.as[UserRequest])
        .absolve
        .mapError(error => FailedToParse(error.getMessage))

    val failure = FailedToParse("'target' must not be empty")

    val response = if (acceptEmptyTarget) parse else parse.filterOrFail(_.target.nonEmpty)(failure)

    response.tapError(error => ZIO.logWarning(s"Failed to parse the user request: $error"))
  }

}
