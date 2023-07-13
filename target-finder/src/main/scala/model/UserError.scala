package model

sealed trait UserError

object UserError {

  case class FailedToParse(error: String) extends UserError

  /** You need to supply at least 2 values. */
  case object NotEnoughData extends UserError

  /** Could not find the appropriate combination of 2 values, which can be summed up and would be equal to "target" */
  case object CouldNotFindAppropriateValues extends UserError

}