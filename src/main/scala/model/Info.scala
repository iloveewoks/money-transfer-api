package model

import java.util.UUID

import model.Info.Uuid

object Info {
  type Uuid = String

  def generateUuid: Uuid = UUID.randomUUID().toString
}

trait Info {
  val id: Uuid
}

case class AccountInfo(override val id: Uuid, balance: BigDecimal = 0) extends Info

trait UpdateInfo[T] {
  def info: T
}

trait TransactionInfo extends Info {
  val status: TransactionStatus.Value
  val transactionType: TransactionType.Value
}
case class DepositTransactionInfo(override val id: Uuid,
                                  to: Uuid,
                                  amount: BigDecimal,
                                  override val status: TransactionStatus.Value)  extends TransactionInfo {
  override val transactionType = TransactionType.DEPOSIT
}

case class WithdrawalTransactionInfo(override val id: Uuid,
                                     from: Uuid,
                                     amount: BigDecimal,
                                     override val status: TransactionStatus.Value)  extends TransactionInfo {
  override val transactionType = TransactionType.WITHDRAWAL
}

case class TransferTransactionInfo(override val id: Uuid,
                                   from: Uuid,
                                   to: Uuid,
                                   amount: BigDecimal,
                                   override val status: TransactionStatus.Value)  extends TransactionInfo {
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
