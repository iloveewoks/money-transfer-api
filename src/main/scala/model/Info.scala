package model

import java.time.Instant
import java.util.UUID

import model.Info.Uuid
import service.validator.Validator.uuidRegEx

object Info {
  type Uuid = String

  def randomUuid: Uuid = UUID.randomUUID().toString
}

trait Info {
  val id: Uuid

  require(id matches uuidRegEx, "Id should be valid UUID")
}

case class AccountInfo(override val id: Uuid = Info.randomUuid, balance: BigDecimal = 0) extends Info

trait UpdateInfo[T] {
  def info: T
}

trait TransactionInfo extends Info {
  val status: TransactionStatus.Value
  val transactionType: TransactionType.Value
  val dateTime: Instant
}
case class DepositTransactionInfo(override val id: Uuid = Info.randomUuid,
                                  to: Uuid,
                                  amount: BigDecimal,
                                  override val status: TransactionStatus.Value = TransactionStatus.CREATED,
                                  override val dateTime: Instant = Instant.now)  extends TransactionInfo {
  require(id matches uuidRegEx)
  require(to matches uuidRegEx)
  require(amount > 0)

  override val transactionType = TransactionType.DEPOSIT
}

case class WithdrawalTransactionInfo(override val id: Uuid = Info.randomUuid,
                                     from: Uuid,
                                     amount: BigDecimal,
                                     override val status: TransactionStatus.Value = TransactionStatus.CREATED,
                                     override val dateTime: Instant = Instant.now)  extends TransactionInfo {
  require(id matches uuidRegEx)
  require(from matches uuidRegEx)
  require(amount > 0)
  override val transactionType = TransactionType.WITHDRAWAL
}

case class TransferTransactionInfo(override val id: Uuid = Info.randomUuid,
                                   from: Uuid,
                                   to: Uuid,
                                   amount: BigDecimal,
                                   override val status: TransactionStatus.Value = TransactionStatus.CREATED,
                                   override val dateTime: Instant = Instant.now)  extends TransactionInfo {
  require(id matches uuidRegEx)
  require(from matches uuidRegEx)
  require(to matches uuidRegEx)
  require(!from.equals(to))
  require(amount > 0)
  override val transactionType = TransactionType.TRANSFER
}

object TransactionType extends Enumeration {
  val DEPOSIT = Value
  val WITHDRAWAL = Value
  val TRANSFER = Value
}

object TransactionStatus extends Enumeration {
  val CREATED = Value

  val ERROR = Value

  val COMPLETED = Value

  val WITHDRAWN = Value

}
