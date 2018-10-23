package actors

import actors.AccountManager.{AccountInfoMsg, UpdateAccount}
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
      sender() ! AccountInfoMsg(info)

    case Deposit(amount) =>
      info = AccountInfo(info.id, info.balance + amount)
      accountManager ! UpdateAccount(info)
      sender() ! Success(info)

    case Withdrawal(amount) if amount > info.balance =>
      sender() ! InsufficientFunds(info)

    case Withdrawal(amount)  =>
      info = AccountInfo(info.id, info.balance - amount)
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
