package service.validator

import cats.data.ValidatedNel
import model.AccountInfo
import model.Info.Uuid
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

case class InvalidUuidFormatException(id: Uuid) extends Exception(s"UUID $id is invalid")
case class NoSuchAccountException(id: Uuid) extends Exception(s"Account with UUID $id not found")
case class NoSuchTransactionException(id: Uuid) extends Exception(s"Transaction with UUID $id not found")
case class InsufficientFundsException(accountInfo: AccountInfo, transactionId: Uuid)
  extends Exception(s"Not enough funds on account $accountInfo during transaction $transactionId")
