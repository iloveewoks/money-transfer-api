import actors.Account.{Deposit, GetInfo}
import actors.{Account, AccountManager}
import actors.AccountManager.{AllAccountsInfo, CreateAccount, GetAccountActorRef, GetAllAccounts}
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import model.AccountInfo
import model.Info.Uuid
import repository.{AccountInMemoryRepository, AccountRepository}
import service.AccountService

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object MoneyTransferApp extends App {
  val system = ActorSystem("test")
  implicit val accountRepository: AccountRepository = new AccountInMemoryRepository
  implicit val accountService: AccountService = new AccountService()
  val accountManager = system.actorOf(Props(new AccountManager), "account-manager")
  implicit val timeout: Timeout = 150.seconds
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  var uuid: Uuid = ""
  val newAccount = accountManager ? CreateAccount
  newAccount.onComplete {
    case Success(info: AccountInfo) =>
      println(s"Created account $info")
      uuid = info.id
      val accountFuture = accountManager ? GetAccountActorRef(info.id)
      accountFuture.onComplete {
        case Success(account: ActorRef) =>
          val depositRes = account ? Deposit(100)
          depositRes onComplete {
            case Success(Account.Success(info)) =>
              println(s"Deposited 100 to account $info")
          }
      }
    case Failure(ex) => println(ex)
  }

  Thread.sleep(2000)
  var allAccounts = accountManager ? GetAllAccounts

  allAccounts.onComplete {
    case Success(AllAccountsInfo(accounts)) =>
      println("All accounts:")
      accounts.foreach(println)
    case Failure(ex) => println(ex)
  }

  Thread.sleep(2000)

  val accountFuture = accountManager ? GetAccountActorRef(uuid)
  accountFuture.onComplete {
    case Success(account: ActorRef) =>
      val accountInfoFuture = account ? GetInfo
      accountInfoFuture.onComplete {
        case Success(info) => println(s"Received account info second time: $info")
      }
  }

  Thread.sleep(2000)

  allAccounts = accountManager ? GetAllAccounts

  allAccounts.onComplete {
    case Success(AllAccountsInfo(accounts)) =>
      println("All accounts:")
      accounts.foreach(println)
    case Failure(ex) => println(ex)
  }

  Thread.sleep(10000)
  system terminate
}
