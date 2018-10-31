package server

import java.time.Instant

import actors.AccountManager.InsufficientFunds
import actors.{AccountManager, TransactionManager}
import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.ValidationRejection
import akka.http.scaladsl.testkit.ScalatestRouteTest
import model._
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import repository.{AccountInMemoryRepository, AccountRepository, TransactionInMemoryRepository, TransactionRepository}
import service.validator._
import service.{AccountService, TransactionService}

import scala.concurrent.ExecutionContextExecutor

class ServerSpec extends Matchers with FlatSpecLike with ScalaFutures
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

  it should s"respond with collection of every transaction sorted by date-time desc GET /$transactionsPrefix" in {
    transactionService save DepositTransactionInfo(to = Info.randomUuid, amount = 100)
    transactionService save WithdrawalTransactionInfo(from = Info.randomUuid, amount = 100)
    transactionService save TransferTransactionInfo(from = Info.randomUuid, to = Info.randomUuid, amount = 100)

    val request = Get(s"/$transactionsPrefix")

    request ~> route ~> check {
      status should === (StatusCodes.OK)

      contentType should ===(ContentTypes.`application/json`)

      entityAs[List[TransactionInfo]] should === (transactionService.findAll(_.dateTime)(Ordering[Instant].reverse).toList)
    }
  }

  it should s"respond with info about specified transaction GET /$transactionsPrefix/uuid" in {
    val transaction = transactionService save DepositTransactionInfo(to = Info.randomUuid, amount = 100)

    val request = Get(s"/$transactionsPrefix/${transaction.id}")

    request ~> route ~> check {
      status should === (StatusCodes.OK)

      contentType should ===(ContentTypes.`application/json`)

      entityAs[DepositTransactionInfo] should === (transactionService.getTransactionInfo(transaction.id).get)
    }
  }

  it should s"fail when requested transaction doesn't exist GET /$transactionsPrefix/uuid" in {
    val randomUuid = Info.randomUuid
    val request = Get(s"/$transactionsPrefix/$randomUuid")

    request ~> route ~> check {
      status should === (StatusCodes.NotFound)

      contentType should ===(ContentTypes.`text/plain(UTF-8)`)

      entityAs[String] should === (NoSuchTransactionException(randomUuid).getMessage)
    }
  }

  it should s"deposit specified amount of money to specified account POST /$transactionsPrefix/deposit" in {
    val account = accountService createAccount
    val amount = 100
    val deposit = TransactionManager.Deposit(account.id, amount)
    val depositEntity = Marshal(deposit).to[MessageEntity].futureValue

    val request = Post(s"/$transactionsPrefix/deposit").withEntity(depositEntity)

    request ~> route ~> check {
      status should === (StatusCodes.OK)

      contentType should ===(ContentTypes.`application/json`)

      val transaction = transactionService.findAll.head.asInstanceOf[DepositTransactionInfo]

      transaction.to should === (deposit.to)
      transaction.amount should === (amount)
      transaction.status should === (TransactionStatus.COMPLETED)

      accountService.getAccountInfo(transaction.to).map(_.balance).get should === (amount)

      entityAs[DepositTransactionInfo] should === (transaction)
    }
  }

  it should s"fail deposit if specified account doesn't exist POST /$transactionsPrefix/deposit" in {
    val randomUuid = Info.randomUuid
    val amount = 100
    val deposit = TransactionManager.Deposit(randomUuid, amount)
    val depositEntity = Marshal(deposit).to[MessageEntity].futureValue

    val request = Post(s"/$transactionsPrefix/deposit").withEntity(depositEntity)

    request ~> route ~> check {
      status should === (StatusCodes.NotFound)

      contentType should ===(ContentTypes.`text/plain(UTF-8)`)

      transactionService.findAll.head.status should === (TransactionStatus.ERROR)

      entityAs[String] should === (NoSuchAccountException(randomUuid).getMessage)
    }
  }

  it should s"withdraw specified amount of money from specified account if there is enough on accounts balance POST /$transactionsPrefix/withdraw" in {
    val account = accountService createAccount
    val amount = 100
    val withdraw = TransactionManager.Withdraw(account.id, amount)
    val withdrawEntity = Marshal(withdraw).to[MessageEntity].futureValue

    accountService updateAccount account.copy(balance = amount * 2)

    val request = Post(s"/$transactionsPrefix/withdraw").withEntity(withdrawEntity)

    request ~> route ~> check {
      status should === (StatusCodes.OK)

      contentType should ===(ContentTypes.`application/json`)

      val transaction = transactionService.findAll.head.asInstanceOf[WithdrawalTransactionInfo]

      transaction.from should === (withdraw.from)
      transaction.amount should === (amount)
      transaction.status should === (TransactionStatus.COMPLETED)

      accountService.getAccountInfo(transaction.from).map(_.balance).get should === (amount)

      entityAs[WithdrawalTransactionInfo] should === (transaction)
    }
  }

  it should s"fail withdrawal if specified account doesn't exist POST /$transactionsPrefix/withdraw" in {
    val randomUuid = Info.randomUuid
    val amount = 100
    val withdraw = TransactionManager.Withdraw(randomUuid, amount)
    val withdrawEntity = Marshal(withdraw).to[MessageEntity].futureValue

    val request = Post(s"/$transactionsPrefix/withdraw").withEntity(withdrawEntity)

    request ~> route ~> check {
      status should === (StatusCodes.NotFound)

      contentType should ===(ContentTypes.`text/plain(UTF-8)`)

      transactionService.findAll.head.status should === (TransactionStatus.ERROR)

      entityAs[String] should === (NoSuchAccountException(randomUuid).getMessage)
    }
  }

  it should s"fail withdrawal if account has not enough funds  POST /$transactionsPrefix/withdraw" in {
    val account = accountService createAccount
    val amount = 100
    val withdraw = TransactionManager.Withdraw(account.id, amount)
    val withdrawEntity = Marshal(withdraw).to[MessageEntity].futureValue

    val request = Post(s"/$transactionsPrefix/withdraw").withEntity(withdrawEntity)

    request ~> route ~> check {
      status should === (StatusCodes.BadRequest)

      contentType should ===(ContentTypes.`text/plain(UTF-8)`)

      val transaction = transactionService.findAll.head

      transaction.status should === (TransactionStatus.ERROR)

      entityAs[String] should === (InsufficientFundsException(account, transaction.id).getMessage)
    }
  }

  it should s"transfer specified amount of money from specified account to specified account if there is enough on first accounts balance POST /$transactionsPrefix/transfer" in {
    val first = accountService createAccount
    val second = accountService createAccount
    val amount = 100
    val transfer = TransactionManager.Transfer(first.id, second.id, amount)
    val transferEntity = Marshal(transfer).to[MessageEntity].futureValue

    accountService updateAccount first.copy(balance = amount * 2)

    val request = Post(s"/$transactionsPrefix/transfer").withEntity(transferEntity)

    request ~> route ~> check {
      status should === (StatusCodes.OK)

      contentType should ===(ContentTypes.`application/json`)

      val transaction = transactionService.findAll.head.asInstanceOf[TransferTransactionInfo]

      transaction.from should === (transfer.from)
      transaction.to should === (transfer.to)
      transaction.amount should === (amount)
      transaction.status should === (TransactionStatus.COMPLETED)

      accountService.getAccountInfo(transaction.from).map(_.balance).get should === (amount)
      accountService.getAccountInfo(transaction.to).map(_.balance).get should === (amount)

      entityAs[TransferTransactionInfo] should === (transaction)
    }
  }

  it should s"fail transfer if either of specified accounts doesn't exist POST /$transactionsPrefix/transfer" in {
    val randomUuid = Info.randomUuid
    val account = accountService createAccount
    val amount = 100
    var transfer = TransactionManager.Transfer(randomUuid, account.id, amount)
    var transferEntity = Marshal(transfer).to[MessageEntity].futureValue

    var request = Post(s"/$transactionsPrefix/transfer").withEntity(transferEntity)

    request ~> route ~> check {
      status should === (StatusCodes.NotFound)

      contentType should ===(ContentTypes.`text/plain(UTF-8)`)

      transactionService.findAll.head.status should === (TransactionStatus.ERROR)

      entityAs[String] should === (NoSuchAccountException(randomUuid).getMessage)
    }


    transfer = TransactionManager.Transfer(account.id, randomUuid, amount)
    transferEntity = Marshal(transfer).to[MessageEntity].futureValue

    request = Post(s"/$transactionsPrefix/transfer").withEntity(transferEntity)

    request ~> route ~> check {
      status should === (StatusCodes.NotFound)

      contentType should ===(ContentTypes.`text/plain(UTF-8)`)

      transactionService.findAll.head.status should === (TransactionStatus.ERROR)

      entityAs[String] should === (NoSuchAccountException(randomUuid).getMessage)
    }

  }

  it should s"fail transfer if source account has not enough funds  POST /$transactionsPrefix/withdraw" in {
    val fromAccount = accountService createAccount
    val toAccount = accountService createAccount
    val amount = 100
    val transfer = TransactionManager.Transfer(fromAccount.id, toAccount.id, amount)
    val transferEntity = Marshal(transfer).to[MessageEntity].futureValue

    val request = Post(s"/$transactionsPrefix/transfer").withEntity(transferEntity)

    request ~> route ~> check {
      status should === (StatusCodes.BadRequest)

      contentType should ===(ContentTypes.`text/plain(UTF-8)`)

      val transaction = transactionService.findAll.head
      transaction.status should === (TransactionStatus.ERROR)

      entityAs[String] should === (InsufficientFundsException(fromAccount, transaction.id).getMessage)
    }
  }

  it should s"validate request body on transaction operations" in {
    var deposit = TransactionManager.Deposit(Info.randomUuid, -100)
    var operationEntity = Marshal(deposit).to[MessageEntity].futureValue

    var request = Post(s"/$transactionsPrefix/deposit").withEntity(operationEntity)

    request ~> route ~> check {
      rejection.asInstanceOf[ValidationRejection].message should === (AmountIsNotPositive.message)
    }

    deposit = TransactionManager.Deposit("", 100)
    operationEntity = Marshal(deposit).to[MessageEntity].futureValue

    request = Post(s"/$transactionsPrefix/deposit").withEntity(operationEntity)

    request ~> route ~> check {
      rejection.asInstanceOf[ValidationRejection].message should === (IdIsEmpty.message)
    }

    deposit = TransactionManager.Deposit("lkjsGLk", 100)
    operationEntity = Marshal(deposit).to[MessageEntity].futureValue

    request = Post(s"/$transactionsPrefix/deposit").withEntity(operationEntity)

    request ~> route ~> check {
      rejection.asInstanceOf[ValidationRejection].message should === (IdFormatIsInvalid.message)
    }

    var withdraw = TransactionManager.Withdraw(Info.randomUuid, -100)
    operationEntity = Marshal(withdraw).to[MessageEntity].futureValue

    request = Post(s"/$transactionsPrefix/withdraw").withEntity(operationEntity)

    request ~> route ~> check {
      rejection.asInstanceOf[ValidationRejection].message should === (AmountIsNotPositive.message)
    }

    withdraw = TransactionManager.Withdraw("", 100)
    operationEntity = Marshal(withdraw).to[MessageEntity].futureValue

    request = Post(s"/$transactionsPrefix/withdraw").withEntity(operationEntity)

    request ~> route ~> check {
      rejection.asInstanceOf[ValidationRejection].message should === (IdIsEmpty.message)
    }

    withdraw = TransactionManager.Withdraw("lkjsGLk", 100)
    operationEntity = Marshal(withdraw).to[MessageEntity].futureValue

    request = Post(s"/$transactionsPrefix/withdraw").withEntity(operationEntity)

    request ~> route ~> check {
      rejection.asInstanceOf[ValidationRejection].message should === (IdFormatIsInvalid.message)
    }

    var transfer = TransactionManager.Transfer(Info.randomUuid, Info.randomUuid, -100)
    operationEntity = Marshal(transfer).to[MessageEntity].futureValue

    request = Post(s"/$transactionsPrefix/transfer").withEntity(operationEntity)

    request ~> route ~> check {
      rejection.asInstanceOf[ValidationRejection].message should === (AmountIsNotPositive.message)
    }

    transfer = TransactionManager.Transfer("", Info.randomUuid, 100)
    operationEntity = Marshal(transfer).to[MessageEntity].futureValue

    request = Post(s"/$transactionsPrefix/transfer").withEntity(operationEntity)

    request ~> route ~> check {
      rejection.asInstanceOf[ValidationRejection].message should === (IdIsEmpty.message)
    }

    transfer = TransactionManager.Transfer(Info.randomUuid, "", 100)
    operationEntity = Marshal(transfer).to[MessageEntity].futureValue

    request = Post(s"/$transactionsPrefix/transfer").withEntity(operationEntity)

    request ~> route ~> check {
      rejection.asInstanceOf[ValidationRejection].message should === (IdIsEmpty.message)
    }

    transfer = TransactionManager.Transfer("lkjsGLk", Info.randomUuid, 100)
    operationEntity = Marshal(transfer).to[MessageEntity].futureValue

    request = Post(s"/$transactionsPrefix/transfer").withEntity(operationEntity)

    request ~> route ~> check {
      rejection.asInstanceOf[ValidationRejection].message should === (IdFormatIsInvalid.message)
    }

    transfer = TransactionManager.Transfer(Info.randomUuid, "lkjsGLk", 100)
    operationEntity = Marshal(transfer).to[MessageEntity].futureValue

    request = Post(s"/$transactionsPrefix/transfer").withEntity(operationEntity)

    request ~> route ~> check {
      rejection.asInstanceOf[ValidationRejection].message should === (IdFormatIsInvalid.message)
    }

    val id = Info.randomUuid
    transfer = TransactionManager.Transfer(id, id, 100)
    operationEntity = Marshal(transfer).to[MessageEntity].futureValue

    request = Post(s"/$transactionsPrefix/transfer").withEntity(operationEntity)

    request ~> route ~> check {
      rejection.asInstanceOf[ValidationRejection].message should === (SourceAndDestinationAreTheSame.message)
    }
  }

}
