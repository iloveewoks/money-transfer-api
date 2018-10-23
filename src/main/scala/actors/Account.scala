package actors

import actors.AccountManager.UpdateAccount
import akka.actor.{ActorLogging, ActorRef}
import akka.persistence.PersistentActor
import model.{AccountInfo, UpdateAccountInfo}

class Account(var info: AccountInfo, accountManager: ActorRef)
  extends PersistentActor
    with ActorLogging {

  import Account._

  override def persistenceId: String = info.id

  override def receiveRecover: Receive = {
    case infoUpdate: UpdateAccountInfo => {
      log.debug(s"Updating account state from {} to {}", info, infoUpdate.info)
      info = infoUpdate.info
    }
  }

  override def receiveCommand: Receive = {
    case GetInfo =>
      log.debug("Getting account info: {}", info)
      sender() ! info

    case Deposit(amount) =>
      log.debug("Depositing {} to account {}", amount, info.id)
      info = info.copy(balance = info.balance + amount)
      accountManager ! UpdateAccount(info)
      sender() ! Success(info)

    case Withdrawal(amount) if amount > info.balance =>
      log.debug("Cannot withdraw {} from {}", amount, info)
      sender() ! InsufficientFunds(info)

    case Withdrawal(amount)  =>
      log.debug("Withdrawing {} from account {}", amount, info.id)
      info = info.copy(balance = info.balance - amount)
      accountManager ! UpdateAccount(info)
      sender() ! Success(info)
  }
}

object Account {
  case class GetInfo()
  case class Deposit(amount: BigDecimal)
  case class Withdrawal(amount: BigDecimal)
  case class Success(info: AccountInfo)
  case class InsufficientFunds(info: AccountInfo)
}
