package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import model.{AccountInfo, UpdateInfo}
import model.Info.Uuid
import service.AccountService
import service.validator.{InvalidUuidFormatException, NoSuchAccountException}

import scala.util.{Failure, Success}

class AccountManager()(implicit val accountService: AccountService)
  extends Actor
    with ActorLogging{

  import AccountManager._

  override def receive: Receive = {
    case CreateAccount =>
      sender ! accountService.createAccount

    case GetAccountInfo(id) =>
      accountService getAccountInfo id match {
        case Success(info) => sender ! info

        case Failure(ex: InvalidUuidFormatException) => sender ! InvalidUuidFormat(ex, id)

        case Failure(ex: NoSuchAccountException) => sender ! NoSuchAccount(ex, id)
      }

    case GetAllAccounts =>
      sender ! AllAccountsInfo(accountService findAll)

    case UpdateAccount(newInfo) =>
      accountService updateAccount newInfo match {
        case Success(info) => sender ! AccountUpdated(newInfo)

        case Failure(ex: InvalidUuidFormatException) => sender ! InvalidUuidFormat(ex, newInfo.id)

        case Failure(ex: NoSuchAccountException) => sender ! NoSuchAccount(ex, newInfo.id)
      }

    case op @ Deposit(_, _, _) =>
      forwardOperationToAccount(op, sender)

    case op @ Withdraw(_, _, _) =>
      forwardOperationToAccount(op, sender)
  }

  private def forwardOperationToAccount(op: Operation, sender: ActorRef): Unit = {
    accountService getAccountInfo op.recipient match {
      case Success(info) =>
        val account = context.child(info.id) match {
          case Some(actor) =>
            log.debug("Found actor of account {}", info.id)
            actor

          case None =>
            log.debug("Creating actor of account {}", info.id)
            val accountActorRef = context.actorOf(Props(new Account(info, self)), info.id)
            accountActorRef
        }

        account forward op

      case Failure(ex: InvalidUuidFormatException) => sender ! InvalidUuidFormat(ex, op.recipient, Some(op.transactionId))

      case Failure(ex: NoSuchAccountException) => sender ! NoSuchAccount(ex, op.recipient, Some(op.transactionId))
    }
  }

}


object AccountManager {
  case class GetAccountInfo(id: Uuid)
  case class GetAllAccounts()
  case class AllAccountsInfo(accounts: Iterable[AccountInfo])
  case class CreateAccount()
  case class UpdateAccount(newInfo: AccountInfo)
  case class AccountUpdated(info: AccountInfo)

  trait Operation {
    val recipient: Uuid
    val transactionId: Uuid
    val amount: BigDecimal
  }

  case class Deposit(override val recipient: Uuid,
                     override val transactionId: Uuid,
                     override val amount: BigDecimal) extends Operation
  case class Withdraw(override val recipient: Uuid,
                      override val transactionId: Uuid,
                      override val amount: BigDecimal) extends Operation
  case class DepositSuccess(transactionId: Uuid, info: AccountInfo) extends UpdateInfo[AccountInfo]
  case class WithdrawalSuccess(transactionId: Uuid, info: AccountInfo) extends UpdateInfo[AccountInfo]

  case class InsufficientFunds(transactionId: Uuid, info: AccountInfo)
  case class InvalidUuidFormat(ex: InvalidUuidFormatException, id: Uuid, transactionId: Option[Uuid] = None)
  case class NoSuchAccount(ex: NoSuchAccountException, id: Uuid, transactionId: Option[Uuid] = None)
}
