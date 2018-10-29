package service

import model.Info.Uuid
import model.TransactionInfo
import repository.TransactionRepository
import service.validator.{InvalidUuidFormatException, NoSuchTransactionException}
import service.validator.Validator.uuidRegEx

import scala.util.{Failure, Success, Try}

class TransactionService()(implicit val transactionRepository: TransactionRepository) {

  def getTransactionInfo(id: Uuid): Try[TransactionInfo] = {
    if (id matches uuidRegEx) {
      transactionRepository.findById(id) match {
        case Some(info) => Success(info)
        case None => Failure(NoSuchTransactionException(s"Transaction with UUID $id not found"))
      }
    } else Failure(InvalidUuidFormatException(s"UUID $id is invalid"))
  }

  def findAll: Iterable[TransactionInfo] = transactionRepository findAll

  def save(transaction: TransactionInfo): TransactionInfo = transactionRepository save transaction

  def updateTransaction(newTransactionInfo: TransactionInfo): Try[TransactionInfo] =
    getTransactionInfo(newTransactionInfo.id) map { _ => transactionRepository.update(newTransactionInfo) }


}
