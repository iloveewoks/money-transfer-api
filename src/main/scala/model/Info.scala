package model

import model.Info.Uuid

object Info {
  type Uuid = String
}

case class AccountInfo(id: Uuid, balance: BigDecimal = 0)

trait UpdateInfo[T] {
  def info: T
}

trait TransactionInfo {
  val id: Uuid
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
}
