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
}
case class DepositTransactionInfo(override val id: Uuid,
                                  to: Uuid,
                                  amount: BigDecimal,
                                  override val status: TransactionStatus.Value)  extends TransactionInfo

case class WithdrawalTransactionInfo(override val id: Uuid,
                                     from: Uuid,
                                     amount: BigDecimal,
                                     override val status: TransactionStatus.Value)  extends TransactionInfo

case class TransferTransactionInfo(override val id: Uuid,
                                   from: Uuid,
                                   to: Uuid,
                                   amount: BigDecimal,
                                   override val status: TransactionStatus.Value)  extends TransactionInfo

object TransactionStatus extends Enumeration {
  val CREATED = Value("Transaction created")

  val ERROR = Value("There was an error during transaction")

  val COMPLETED = Value("Transaction completed")

  val WITHDRAWN = Value("Funds been withdrawn during transfer transaction")

}
