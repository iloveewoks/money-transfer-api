package server

import actors.{AccountManager, TransactionManager}
import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import model._
import org.scalatest._
import repository.{AccountInMemoryRepository, AccountRepository, TransactionInMemoryRepository, TransactionRepository}
import service.validator.NoSuchAccountException
import service.{AccountService, TransactionService}

import scala.concurrent.ExecutionContextExecutor

class ServerSpec extends Matchers with FlatSpecLike
  with ScalatestRouteTest with RestService with BeforeAndAfterEach with BeforeAndAfterAll {

  override implicit val actorSystem: ActorSystem = system
  override implicit val executionContext: ExecutionContextExecutor = executor
  implicit val accountRepository: AccountRepository = new AccountInMemoryRepository
  implicit val accountService: AccountService = new AccountService
  val accountManager = system.actorOf(Props(new AccountManager), "test-account-manager")
  implicit val transactionRepository: TransactionRepository = new TransactionInMemoryRepository
  implicit val transactionService: TransactionService = new TransactionService
  val transactionManager = system.actorOf(Props(new TransactionManager(accountManager)), "test-transaction-manager")

  override def afterEach(): Unit = {
    accountRepository.entities.clear
    transactionRepository.entities.clear
  }

  override def afterAll(): Unit = cleanUp

  "Server" should s"create account POST /$accountsPrefix" in {

    val request = Post(s"/$accountsPrefix")

    request ~> route ~> check {
      status should === (StatusCodes.Created)

      contentType should ===(ContentTypes.`application/json`)

      entityAs[AccountInfo] should === (accountRepository.findAll.head)
    }
  }

  it should s"respond with collection of every account GET /$accountsPrefix" in {
    accountService.createAccount
    accountService.createAccount
    accountService.createAccount

    val request = Get(s"/$accountsPrefix")

    request ~> route ~> check {
      status should === (StatusCodes.OK)

      contentType should ===(ContentTypes.`application/json`)

      entityAs[List[AccountInfo]] should === (accountService.findAll.toList)
    }
  }

  it should s"respond with info about specified account GET /$accountsPrefix/uuid" in {
    val account = accountService createAccount

    val request = Get(s"/$accountsPrefix/${account.id}")

    request ~> route ~> check {
      status should === (StatusCodes.OK)

      contentType should ===(ContentTypes.`application/json`)

      entityAs[AccountInfo] should === (account)
    }
  }

  it should s"fail when requested account doesn't exist GET /$accountsPrefix/uuid" in {
    val randomUuid = Info.randomUuid
    val request = Get(s"/$accountsPrefix/$randomUuid")

    request ~> route ~> check {
      status should === (StatusCodes.NotFound)

      contentType should ===(ContentTypes.`text/plain(UTF-8)`)

      entityAs[String] should === (NoSuchAccountException(randomUuid).getMessage)
    }
  }

  it should s"respond with collection of every transaction GET /$transactionsPrefix" in {
    transactionService save DepositTransactionInfo(to = Info.randomUuid, amount = 100)
    transactionService save WithdrawalTransactionInfo(from = Info.randomUuid, amount = 100)
    transactionService save TransferTransactionInfo(from = Info.randomUuid, to = Info.randomUuid, amount = 100)

    val request = Get(s"/$transactionsPrefix")

    request ~> route ~> check {
      status should === (StatusCodes.OK)

      contentType should ===(ContentTypes.`application/json`)

      entityAs[List[TransactionInfo]] should === (transactionService.findAll.toList)
    }
  }

}
