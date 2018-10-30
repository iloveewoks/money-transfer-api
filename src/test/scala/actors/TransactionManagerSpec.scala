package actors

import actors.AccountManager.NoSuchAccount
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import cats.data.Validated.{Invalid, Valid}
import model._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpecLike, Matchers}
import repository.{AccountInMemoryRepository, TransactionInMemoryRepository}
import service.{AccountService, TransactionService}

class TransactionManagerSpec extends TestKit(ActorSystem("actor-test"))
  with FlatSpecLike with ImplicitSender
  with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

  import TransactionManager._

  implicit val accountRepository = new AccountInMemoryRepository
  implicit val accountService = new AccountService
  val accountManager = system.actorOf(Props(new AccountManager()), "test-account-manager")

  implicit val transactionRepository = new TransactionInMemoryRepository
  implicit val transactionService = new TransactionService
  val transactionManager = system.actorOf(Props(new TransactionManager(accountManager)), "test-transaction-manager")

  override def afterAll(): Unit = shutdown(system)

  override def afterEach(): Unit = {
    accountRepository.entities.clear
    transactionRepository.entities.clear
  }

  "TransactionManager" should "check accounts exist before each transaction" in {
    var randomId = Info.randomUuid
    transactionManager ! Valid(Deposit(randomId, 100))

    expectMsgPF() {
      case NoSuchAccount(_, id, _) if id == randomId =>
    }

    randomId = Info.randomUuid
    transactionManager ! Valid(Withdraw(randomId, 100))

    expectMsgPF() {
      case NoSuchAccount(_, id, _) if id == randomId =>
    }

    randomId = Info.randomUuid
    transactionManager ! Valid(Transfer(randomId, Info.randomUuid, 100))

    expectMsgPF() {
      case NoSuchAccount(_, id, _) if id == randomId =>
    }

    accountManager ! AccountManager.CreateAccount

    expectMsgPF() {
      case AccountInfo(from, _) =>
        randomId = Info.randomUuid
        transactionManager ! Valid(Transfer(from, randomId, 100))

        expectMsgPF() {
          case NoSuchAccount(_, id, _) if id == randomId =>
        }
    }
  }

  it should "forward operations to account manager and save corresponding transaction info" in {
    accountManager ! AccountManager.CreateAccount

    expectMsgPF() {
      case AccountInfo(firstAccountId, firstAcoountBseBalance) =>
        val amount = 100
        transactionManager ! Valid(Deposit(firstAccountId, amount))

        expectMsgPF() {
          case TransactionCompleted(DepositTransactionInfo(depositId, depositTo, depositAmount, depositStatus, _))
            if transactionRepository.findById(depositId).isDefined
              && depositTo == firstAccountId
              && depositAmount == amount
              && depositStatus == TransactionStatus.COMPLETED =>

            accountManager ! AccountManager.GetAccountInfo(firstAccountId)

            expectMsg(AccountManager.AccountInfoMsg(AccountInfo(firstAccountId, amount), None))
        }

        transactionManager ! Valid(Withdraw(firstAccountId, amount / 2))

        expectMsgPF() {
          case TransactionCompleted(WithdrawalTransactionInfo(withdrawalId, withdrawalFrom, withdrawalAmount, withdrawalStatus, _))
            if transactionRepository.findById(withdrawalId).isDefined
              && withdrawalFrom == firstAccountId
              && withdrawalAmount == amount / 2
              && withdrawalStatus == TransactionStatus.COMPLETED =>


            accountManager ! AccountManager.GetAccountInfo(firstAccountId)

            expectMsg(AccountManager.AccountInfoMsg(AccountInfo(firstAccountId, amount / 2), None))
        }

        accountManager ! AccountManager.CreateAccount

        expectMsgPF() {
          case AccountInfo(secondAccountId, _) =>
            transactionManager ! Valid(Transfer(firstAccountId, secondAccountId, amount / 2))

            expectMsgPF() {
              case TransactionCompleted(TransferTransactionInfo(transferId, transferFrom, transferTo, transferAmount, transferStatus, _))
                if transactionRepository.findById(transferId).isDefined
                  && transferFrom == firstAccountId
                  && transferTo == secondAccountId
                  && transferAmount == amount / 2
                  && transferStatus == TransactionStatus.COMPLETED =>


                accountManager ! AccountManager.GetAccountInfo(firstAccountId)

                expectMsg(AccountManager.AccountInfoMsg(AccountInfo(firstAccountId, 0), None))


                accountManager ! AccountManager.GetAccountInfo(secondAccountId)

                expectMsg(AccountManager.AccountInfoMsg(AccountInfo(secondAccountId, amount / 2), None))
            }
        }
    }
  }

  it should "respond with NoSuchTransaction for unknown transaction id" in {
    val id = Info.randomUuid
    transactionManager ! GetTransactionInfo(id)

    expectMsgPF() {
      case NoSuchTransaction(_, trId) if trId == id =>
    }
  }

  it should "respond with collection of all transactions when asked" in {
    transactionManager ! Valid(Deposit(Info.randomUuid, 100))

    expectMsgPF() {
      case NoSuchAccount(_, _, Some(firstTransactionId)) =>
        transactionManager ! Valid(Deposit(Info.randomUuid, 100))

        expectMsgPF() {
          case NoSuchAccount(_, _, Some(secondTransactionId)) =>

            transactionManager ! GetTransactionInfo(firstTransactionId)

            expectMsgPF() {
              case firstInfo: TransactionInfo =>

                transactionManager ! GetTransactionInfo(secondTransactionId)

                expectMsgPF() {
                  case secondInfo: TransactionInfo =>

                    transactionManager ! GetAllTransactions

                    expectMsgPF() {
                      case AllTransactionsInfo(transactions)
                        if transactions.size == 2
                          && transactions.toSeq.contains(firstInfo)
                          && transactions.toSeq.contains(secondInfo) =>
                    }
                }
            }
        }
    }
  }

  it should "accept only valid operations" in {

    transactionManager ! Deposit("", 100).validate
    expectMsgPF() {
      case _: Invalid[IllegalArgumentException] =>
    }


    transactionManager ! Deposit("142124", 100).validate
    expectMsgPF() {
      case _: Invalid[IllegalArgumentException] =>
    }


    transactionManager ! Deposit(Info.randomUuid, -100).validate
    expectMsgPF() {
      case _: Invalid[IllegalArgumentException] =>
    }


    transactionManager ! Withdraw("", 100).validate
    expectMsgPF() {
      case _: Invalid[IllegalArgumentException] =>
    }


    transactionManager ! Withdraw("142124", 100).validate
    expectMsgPF() {
      case _: Invalid[IllegalArgumentException] =>
    }


    transactionManager ! Withdraw(Info.randomUuid, -100).validate
    expectMsgPF() {
      case _: Invalid[IllegalArgumentException] =>
    }


    transactionManager ! Transfer(Info.randomUuid, "", 100).validate
    expectMsgPF() {
      case _: Invalid[IllegalArgumentException] =>
    }


    transactionManager ! Transfer(Info.randomUuid, "12414", 100).validate
    expectMsgPF() {
      case _: Invalid[IllegalArgumentException] =>
    }


    transactionManager ! Transfer("", Info.randomUuid, 100).validate
    expectMsgPF() {
      case _: Invalid[IllegalArgumentException] =>
    }


    transactionManager ! Transfer("12414", Info.randomUuid, 100).validate
    expectMsgPF() {
      case _: Invalid[IllegalArgumentException] =>
    }

    transactionManager ! Transfer(Info.randomUuid, Info.randomUuid, -100).validate
    expectMsgPF() {
      case _: Invalid[IllegalArgumentException] =>
    }

  }

}
