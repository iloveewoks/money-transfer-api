package actors

import akka.actor.{Actor, ActorLogging, Props}
import model.AccountInfo
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

        case Failure(ex: InvalidUuidFormatException) => sender ! InvalidUuidFormat(ex)

        case Failure(ex: NoSuchAccountException) => sender ! NoSuchAccount(ex)
      }

    case GetAllAccounts =>
      sender ! AllAccountsInfo(accountService findAll)

    case UpdateAccount(newInfo) =>
      accountService updateAccount newInfo match {
        case Success(info) => sender ! AccountUpdated(newInfo)

        case Failure(ex: InvalidUuidFormatException) => sender ! InvalidUuidFormat(ex)

        case Failure(ex: NoSuchAccountException) => sender ! NoSuchAccount(ex)
      }

    case GetAccountActorRef(id) =>
      accountService getAccountInfo id match {
        case Success(info) =>
          context.child(info.id) match {
            case Some(actor) =>
              log.debug("Found actor of account {}", info.id)
              sender ! actor

            case None =>
              val accountActorRef = context.actorOf(Props(new Account(info, self)), info.id)
              sender ! accountActorRef
          }

        case Failure(ex: InvalidUuidFormatException) => sender ! InvalidUuidFormat(ex)

        case Failure(ex: NoSuchAccountException) => sender ! NoSuchAccount(ex)
      }
  }
}


object AccountManager {
  case class GetAccountInfo(id: Uuid)
  case class GetAccountActorRef(id: Uuid)
  case class GetAllAccounts()
  case class AllAccountsInfo(accounts: Iterable[AccountInfo])
  case class CreateAccount()
  case class UpdateAccount(newInfo: AccountInfo)
  case class AccountUpdated(info: AccountInfo)
  case class InvalidUuidFormat(ex: InvalidUuidFormatException)
  case class NoSuchAccount(ex: NoSuchAccountException)
}
