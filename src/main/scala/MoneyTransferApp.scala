import actors.Account.Deposit
import actors.{Account, AccountManager}
import actors.AccountManager.{AllAccountsInfo, CreateAccount, GetAccountActorRef, GetAllAccounts}
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import model.AccountInfo
import repository.{AccountInMemoryRepository, AccountRepository}
import service.AccountService

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object MoneyTransferApp extends App {
  val system = ActorSystem("test")
  implicit val accountRepository: AccountRepository = new AccountInMemoryRepository
  implicit val accountService: AccountService = new AccountService()
  val accountManager = system.actorOf(Props(new AccountManager(system)), "account-manager")
  implicit val timeout: Timeout = 15.seconds
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val newAccount = accountManager ? CreateAccount
  newAccount.onComplete {
    case Success(info: AccountInfo) =>
      println(s"Created account $info")
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

  Thread.sleep(100)
  val allAccounts = accountManager ? GetAllAccounts

  allAccounts.onComplete {
    case Success(AllAccountsInfo(accounts)) =>
      println("All accounts:")
      accounts.foreach(println)
    case Failure(ex) => println(ex)
  }

  Thread.sleep(10000)
  system terminate
}
