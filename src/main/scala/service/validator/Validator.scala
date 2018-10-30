package service.validator

import cats.data.ValidatedNel
import service.validator.Validator.ValidationResult

object Validator {
  val uuidRegEx = "^[A-Fa-f\\d]{8}-[A-Fa-f\\d]{4}-4[A-Fa-f\\d]{3}-[89ABab][A-Fa-f\\d]{3}-[A-Fa-f\\d]{12}$"

  type ValidationResult[A] = ValidatedNel[ValidationFailure, A]
}

abstract class ValidationFailure(val message: String)

trait Validatable[A] {
  def validate: ValidationResult[A]
}

case object IdIsEmpty extends ValidationFailure("Id is empty")
case object IdFormatIsInvalid extends ValidationFailure("Id format is not valid UUID")
case object AmountIsNotPositive extends ValidationFailure("Amount should be positive")
case object SourceAndDestinationAreTheSame extends ValidationFailure("Source and destination accounts should not be the same")

case class InvalidUuidFormatException(message: String) extends Exception(message)
case class NoSuchAccountException(message: String) extends Exception(message)
case class NoSuchTransactionException(message: String) extends Exception(message)
