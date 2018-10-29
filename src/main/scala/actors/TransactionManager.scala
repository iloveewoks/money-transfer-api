package actors

import actors.AccountManager.InvalidUuidFormat
import akka.actor.{Actor, ActorLogging, ActorRef}
import model.Info.{Uuid, generateUuid}
import model._
import service.TransactionService
import service.validator.{InvalidUuidFormatException, NoSuchTransactionException}

import scala.collection.mutable
import scala.util.{Failure, Success}

class TransactionManager(accountManager: ActorRef)(implicit val transactionService: TransactionService)
  extends Actor
    with ActorLogging {

  import TransactionManager._

  var requests: mutable.Map[Uuid, ActorRef] = mutable.Map[Uuid, ActorRef]()

  override def receive: Receive = {
    case GetTransactionInfo(id) =>
      transactionService getTransactionInfo id match {
        case Success(info) => sender ! info

        case Failure(ex: InvalidUuidFormatException) => sender ! InvalidUuidFormat(ex, id)

        case Failure(ex: NoSuchTransactionException) => sender ! NoSuchTransaction(ex, id)
      }

    case GetAllTransactions =>
      sender ! AllTransactionsInfo(transactionService findAll)

    case Deposit(to, amount) =>
      val transaction = DepositTransactionInfo(generateUuid, to, amount, TransactionStatus.CREATED)
      transactionService save transaction
      requests += transaction.id -> sender
      accountManager ! AccountManager.Deposit(to, transaction.id, amount)

    case Withdraw(from, amount) =>
      val transaction = WithdrawalTransactionInfo(generateUuid, from, amount, TransactionStatus.CREATED)
      transactionService save transaction
      requests += transaction.id -> sender
      accountManager ! AccountManager.Withdraw(from, transaction.id, amount)

    case Transfer(from, to, amount) =>
      val transaction = TransferTransactionInfo(generateUuid, from, to, amount, TransactionStatus.CREATED)
      transactionService save transaction
      requests += transaction.id -> sender
      accountManager ! AccountManager.Withdraw(from, transaction.id, amount)

    case AccountManager.DepositSuccess(transactionId, _) =>
      processRequest(transactionId,
        { requester =>
          transactionService.getTransactionInfo(transactionId) match {
            case Success(transaction: DepositTransactionInfo) =>
              val updatedTransaction =
                transactionService updateTransaction transaction.copy(status = TransactionStatus.COMPLETED)

              requests -= transactionId
              requester ! TransactionCompleted(updatedTransaction.get)

            case Success(transaction: TransferTransactionInfo) =>
              val updatedTransaction =
                transactionService updateTransaction transaction.copy(status = TransactionStatus.COMPLETED)

              requests -= transactionId
              requester ! TransactionCompleted(updatedTransaction.get)

            case Failure(ex: InvalidUuidFormatException) => requester ! InvalidUuidFormat(ex, transactionId)

            case Failure(ex: NoSuchTransactionException) => requester ! NoSuchTransaction(ex, transactionId)
          }
        }
      )

    case AccountManager.WithdrawalSuccess(transactionId, info) =>
      processRequest(transactionId,
        { requester =>
          transactionService.getTransactionInfo(transactionId) match {
            case Success(transaction: WithdrawalTransactionInfo) =>
              val updatedTransaction =
                transactionService updateTransaction transaction.copy(status = TransactionStatus.COMPLETED)

              requests -= transactionId
              requester ! TransactionCompleted(updatedTransaction.get)

            case Success(transaction: TransferTransactionInfo) =>
              transactionService updateTransaction transaction.copy(status = TransactionStatus.WITHDRAWN)

              accountManager ! AccountManager.Deposit(transaction.to, transaction.id, transaction.amount)

            case Failure(ex: InvalidUuidFormatException) => requester ! InvalidUuidFormat(ex, transactionId)

            case Failure(ex: NoSuchTransactionException) => requester ! NoSuchTransaction(ex, transactionId)
          }
        }
      )

    case msg @ AccountManager.InsufficientFunds(transactionId, accountInfo) =>
      processRequest(transactionId,
        { requester =>
          transactionService getTransactionInfo transactionId match {
            case Success(transaction: WithdrawalTransactionInfo) =>
              transactionService updateTransaction transaction.copy(status = TransactionStatus.ERROR)

              requester ! msg

            case Success(transaction: TransferTransactionInfo) =>
              transactionService updateTransaction transaction.copy(status = TransactionStatus.ERROR)

              requester ! msg

            case Failure(ex: InvalidUuidFormatException) => requester ! InvalidUuidFormat(ex, transactionId)

            case Failure(ex: NoSuchTransactionException) => requester ! NoSuchTransaction(ex, transactionId)
          }
        }
      )

    case msg @ AccountManager.NoSuchAccount(_, _, transactionIdOp) =>
      transactionIdOp match {
        case Some(transactionId) =>
          processRequest(transactionId,
            { requester =>
              transactionService getTransactionInfo transactionId match {
                case Success(transaction: WithdrawalTransactionInfo) =>
                  transactionService updateTransaction transaction.copy(status = TransactionStatus.ERROR)

                  requester ! msg

                case Success(transaction: TransferTransactionInfo) =>
                  transactionService updateTransaction transaction.copy(status = TransactionStatus.ERROR)

                  requester ! msg

                case Failure(ex: InvalidUuidFormatException) => requester ! InvalidUuidFormat(ex, transactionId)

                case Failure(ex: NoSuchTransactionException) => requester ! NoSuchTransaction(ex, transactionId)
              }
            }
          )
      }

    case msg @ AccountManager.InvalidUuidFormat(_, _, transactionIdOp) =>
      transactionIdOp match {
        case Some(transactionId) =>
          processRequest(transactionId,
            { requester =>
              transactionService getTransactionInfo transactionId match {
                case Success(transaction: WithdrawalTransactionInfo) =>
                  transactionService updateTransaction transaction.copy(status = TransactionStatus.ERROR)

                  requester ! msg

                case Success(transaction: TransferTransactionInfo) =>
                  transactionService updateTransaction transaction.copy(status = TransactionStatus.ERROR)

                  requester ! msg

                case Failure(ex: InvalidUuidFormatException) => requester ! InvalidUuidFormat(ex, transactionId)

                case Failure(ex: NoSuchTransactionException) => requester ! NoSuchTransaction(ex, transactionId)
              }
            }
          )
      }


  }

  private def processRequest(transactionId: Uuid, callback: ActorRef => Unit): Unit =
    requests get transactionId match {
      case Some(requester) =>
        callback(requester)

      case None =>
        log.warning("No request found for transaction {}", transactionId)
    }

}

object TransactionManager {
  case class GetTransactionInfo(id: Uuid)
  case class GetAllTransactions()
  case class AllTransactionsInfo(transactions: Iterable[TransactionInfo])
  case class NoSuchTransaction(ex: NoSuchTransactionException, id: Uuid)
  case class Deposit(to: Uuid, amount: BigDecimal)
  case class Withdraw(from: Uuid, amount: BigDecimal)
  case class Transfer(from: Uuid, to: Uuid, amount: BigDecimal)
  case class TransactionCompleted(transactionInfo: TransactionInfo)
}
