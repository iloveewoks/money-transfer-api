package service.validator

object Validator {
  val uuidRegEx = "^[A-Fa-f\\d]{8}-[A-Fa-f\\d]{4}-4[A-Fa-f\\d]{3}-[89ABab][A-Fa-f\\d]{3}-[A-Fa-f\\d]{12}$"
}

case class InvalidUuidFormatException(message: String) extends Exception(message)
case class NoSuchAccountException(message: String) extends Exception(message)
