import actors.{AccountManager, TransactionManager}
import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import repository.{AccountInMemoryRepository, AccountRepository, TransactionInMemoryRepository, TransactionRepository}
import server.Server
import service.{AccountService, TransactionService}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.io.StdIn

object MoneyTransferApp extends App {
  implicit val system: ActorSystem = ActorSystem("money-transfer-api")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  implicit val accountRepository: AccountRepository = new AccountInMemoryRepository
  implicit val accountService: AccountService = new AccountService
  val accountManager = system.actorOf(Props(new AccountManager), "account-manager")
  implicit val transactionRepository: TransactionRepository = new TransactionInMemoryRepository
  implicit val transactionService: TransactionService = new TransactionService
  val transactionManager = system.actorOf(Props(new TransactionManager(accountManager)), "transaction-manager")

  val interface = "localhost"
  val port = 8080
  val server = new Server(accountManager, transactionManager)

  Future {
    println(s"\nServer online at http://$interface:$port/\nPress Enter to stop...\n")
    StdIn.readLine()
    server.stop.foreach(_ => system.terminate().foreach(println))
  }
}
