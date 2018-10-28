package actors

import actors.AccountManager.UpdateAccount
import akka.actor.{ActorLogging, ActorRef}
import akka.persistence.PersistentActor
import model.Info.Uuid
import model.{AccountInfo, UpdateInfo}

class Account(var info: AccountInfo, accountManager: ActorRef)
  extends PersistentActor
    with ActorLogging {

  import Account._

  override def persistenceId: String = info.id

  override def receiveRecover: Receive = {
    case infoUpdate: UpdateInfo[AccountInfo] => {
      log.debug(s"Updating account state from {} to {}", info, infoUpdate.info)
      info = infoUpdate.info
    }
  }

  override def receiveCommand: Receive = {
    case GetInfo =>
      log.debug("Getting account info: {}", info)
      sender() ! info

    case Deposit(transactionId, amount) =>
      persist(DepositSuccess(transactionId, info.copy(balance = info.balance + amount))) { depositSuccess =>
        log.debug("Deposited {} to account {} during transaction {}", amount, info.id, transactionId)
        info = info.copy(balance = info.balance + amount)
        accountManager ! UpdateAccount(info)
        sender() ! depositSuccess
      }

    case Withdrawal(transactionId, amount) if amount > info.balance =>
      log.debug("Cannot withdraw {} from {} during transaction {}", amount, info, transactionId)
      sender() ! InsufficientFunds(transactionId, info)

    case Withdrawal(transactionId, amount)  =>
      persist(WithdrawalSuccess(transactionId, info.copy(balance = info.balance - amount))) { withdrawalSuccess =>
        log.debug("Withdrawed {} from account {} during transaction {}", amount, info.id, transactionId)
        info = info.copy(balance = info.balance - amount)
        accountManager ! UpdateAccount(info)
        sender() ! withdrawalSuccess
      }
  }
}

object Account {
  case class GetInfo()
  case class Deposit(transactionId: Uuid, amount: BigDecimal)
  case class Withdrawal(transactionId: Uuid, amount: BigDecimal)
  case class DepositSuccess(transactionId: Uuid, info: AccountInfo) extends UpdateInfo[AccountInfo]
  case class WithdrawalSuccess(transactionUuid, info: AccountInfo) extends UpdateInfo[AccountInfo]
  case class InsufficientFunds(transactionId: Uuid, info: AccountInfo)
}
