import actors.AccountManager
import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import repository.{AccountInMemoryRepository, AccountRepository}
import service.AccountService

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

object MoneyTransferApp extends App {
  val system = ActorSystem("test")
  implicit val accountRepository: AccountRepository = new AccountInMemoryRepository
  implicit val accountService: AccountService = new AccountService()
  val accountManager = system.actorOf(Props(new AccountManager), "account-manager")
  implicit val timeout: Timeout = 150.seconds
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  Thread.sleep(10000)
  system terminate
}
