package actors

import actors.AccountManager.InvalidUuidFormat
import akka.actor.{Actor, ActorLogging, ActorRef}
import cats.data.Validated.Valid
import cats.implicits._
import model.Info.{Uuid, generateUuid}
import model._
import service.TransactionService
import service.validator.Validator.{ValidationResult, uuidRegEx}
import service.validator._

import scala.collection.mutable
import scala.util.{Failure, Success}

class TransactionManager(accountManager: ActorRef)(implicit val transactionService: TransactionService)
  extends Actor
    with ActorLogging {

  import TransactionManager._

  var requests: mutable.Map[Uuid, ActorRef] = mutable.Map[Uuid, ActorRef]()

  override def receive: Receive = mainContext

  private def mainContext: Receive = {
    case GetTransactionInfo(id) =>
      transactionService getTransactionInfo id match {
        case Success(info) => sender ! info

        case Failure(ex: InvalidUuidFormatException) => sender ! InvalidUuidFormat(ex, id)

        case Failure(ex: NoSuchTransactionException) => sender ! NoSuchTransaction(ex, id)
      }

    case GetAllTransactions =>
      sender ! AllTransactionsInfo(transactionService findAll)

    case Valid(Deposit(to, amount)) =>
      val transaction = DepositTransactionInfo(generateUuid, to, amount)
      transactionService save transaction
      requests += transaction.id -> sender
      accountManager ! AccountManager.GetAccountInfo(to, Some(transaction.id))
      context.become(accountCheckContext)

    case Valid(Withdraw(from, amount)) =>
      val transaction = WithdrawalTransactionInfo(generateUuid, from, amount)
      transactionService save transaction
      requests += transaction.id -> sender
      accountManager ! AccountManager.GetAccountInfo(from, Some(transaction.id))
      context.become(accountCheckContext)

    case Valid(Transfer(from, to, amount)) =>
      val transaction = TransferTransactionInfo(generateUuid, from, to, amount)
      transactionService save transaction
      requests += transaction.id -> sender
      accountManager ! AccountManager.GetAccountInfo(from, Some(transaction.id))
      context.become(accountCheckContext)

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

    case AccountManager.WithdrawalSuccess(transactionId, _) =>
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
      processRequest(transactionId, { _ forward msg})
      transactionService.updateTransactionStatus(transactionId, TransactionStatus.ERROR)

    case msg @ AccountManager.NoSuchAccount(_, _, Some(transactionId)) =>
      processRequest(transactionId, { _ forward msg })
      transactionService.updateTransactionStatus(transactionId, TransactionStatus.ERROR)


    case msg @ AccountManager.InvalidUuidFormat(_, _, Some(transactionId)) =>
      processRequest(transactionId, { _ forward msg })
      transactionService.updateTransactionStatus(transactionId, TransactionStatus.ERROR)

  }

  private def accountCheckContext: Receive = {
    case AccountManager.AccountInfoMsg(accountInfo, Some(transactionId)) =>
      transactionService getTransactionInfo transactionId match {
        case Success(DepositTransactionInfo(_, to, amount, _, _)) =>
          accountManager ! AccountManager.Deposit(to, transactionId, amount)
          context.become(mainContext)

        case Success(WithdrawalTransactionInfo(_, from, amount, _, _)) =>
          accountManager ! AccountManager.Withdraw(from, transactionId, amount)
          context.become(mainContext)

        case Success(TransferTransactionInfo(_, from, to, _, _, _)) if from equals accountInfo.id =>
          accountManager ! AccountManager.GetAccountInfo(to, Some(transactionId))

        case Success(TransferTransactionInfo(_, from, _, amount, _, _)) =>
          accountManager ! AccountManager.Withdraw(from, transactionId, amount)
          context.become(mainContext)
      }

    case msg @ InvalidUuidFormat(_, _, Some(transactionId)) =>
      processRequest(transactionId, { _ forward msg })
      transactionService.updateTransactionStatus(transactionId, TransactionStatus.ERROR)
      context.become(mainContext)

    case msg @ AccountManager.NoSuchAccount(_, _, Some(transactionId)) =>
      processRequest(transactionId, { _ forward msg })
      transactionService.updateTransactionStatus(transactionId, TransactionStatus.ERROR)
      context.become(mainContext)
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
  case class GetTransactionInfo(id: Uuid) {
    require(id matches uuidRegEx)
  }
  case class GetAllTransactions()
  case class AllTransactionsInfo(transactions: Iterable[TransactionInfo])
  case class NoSuchTransaction(ex: NoSuchTransactionException, id: Uuid)
  case class TransactionCompleted(transactionInfo: TransactionInfo)

  //ToDo abstract logic to reduce code duplication
  case class Deposit(to: Uuid, amount: BigDecimal) extends Validatable[Deposit] {
    override def validate: ValidationResult[Deposit] = (
      validateId,
      validateAmount
    ).mapN(Deposit)

    private def validateId: ValidationResult[Uuid] =
      if (to.isEmpty) IdIsEmpty.invalidNel
      else if (to matches uuidRegEx) to.validNel
      else IdFormatIsInvalid.invalidNel

    private def validateAmount: ValidationResult[BigDecimal] =
      if (amount > 0) amount.validNel
      else AmountIsNotPositive.invalidNel
  }

  case class Withdraw(from: Uuid, amount: BigDecimal) extends Validatable[Withdraw] {
    override def validate: ValidationResult[Withdraw] = (
      validateId,
      validateAmount
    ).mapN(Withdraw)

    private def validateId: ValidationResult[Uuid] =
      if (from.isEmpty) IdIsEmpty.invalidNel
      else if (from matches uuidRegEx) from.validNel
      else IdFormatIsInvalid.invalidNel

    private def validateAmount: ValidationResult[BigDecimal] =
      if (amount > 0) amount.validNel
      else AmountIsNotPositive.invalidNel
  }
  case class Transfer(from: Uuid, to: Uuid, amount: BigDecimal) extends Validatable[Transfer] {
    override def validate: ValidationResult[Transfer] = (
      validateTo,
      validateFrom,
      validateAmount
    ).mapN(Transfer)

    private def validateTo: ValidationResult[Uuid] =
      if (to.isEmpty) IdIsEmpty.invalidNel
      else if (!to.matches(uuidRegEx)) IdFormatIsInvalid.invalidNel
      else if (to equals from) SourceAndDestinationAreTheSame.invalidNel
      else to.validNel

    private def validateFrom: ValidationResult[Uuid] =
      if (from.isEmpty) IdIsEmpty.invalidNel
      else if (!from.matches(uuidRegEx)) IdFormatIsInvalid.invalidNel
      else from.validNel

    private def validateAmount: ValidationResult[BigDecimal] =
      if (amount > 0) amount.validNel
      else AmountIsNotPositive.invalidNel
  }
}
