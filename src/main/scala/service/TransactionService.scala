package service

import model.Info.Uuid
import model._
import repository.TransactionRepository
import service.validator.{InvalidUuidFormatException, NoSuchTransactionException}
import service.validator.Validator.uuidRegEx

import scala.util.{Failure, Success, Try}

class TransactionService()(implicit val transactionRepository: TransactionRepository) {

  def getTransactionInfo(id: Uuid): Try[TransactionInfo] = {
    if (id matches uuidRegEx) {
      transactionRepository.findById(id) match {
        case Some(info) => Success(info)
        case None => Failure(NoSuchTransactionException(id))
      }
    } else Failure(InvalidUuidFormatException(id))
  }

  def findAll: Iterable[TransactionInfo] = transactionRepository findAll

  def findAll(sortWith: (TransactionInfo, TransactionInfo) => Boolean): Iterable[TransactionInfo] =
    transactionRepository findAll sortWith

  def findAll[B](sortBy: TransactionInfo => B)(implicit ordering: Ordering[B]): Iterable[TransactionInfo] =
    transactionRepository findAll sortBy

  def save(transaction: TransactionInfo): TransactionInfo = transactionRepository save transaction

  def updateTransaction(newTransactionInfo: TransactionInfo): Try[TransactionInfo] =
    getTransactionInfo(newTransactionInfo.id) map { _ => transactionRepository.update(newTransactionInfo) }

  def updateTransactionStatus(id: Uuid, newStatus: TransactionStatus.Value): Try[TransactionInfo] =
    getTransactionInfo(id) map {
      case info: DepositTransactionInfo =>
        transactionRepository.update(info.copy(status = newStatus))

      case info: WithdrawalTransactionInfo =>
        transactionRepository.update(info.copy(status = newStatus))

      case info: TransferTransactionInfo =>
        transactionRepository.update(info.copy(status = newStatus))
    }



}
