package service

import model._
import org.scalatest.{BeforeAndAfterEach, FlatSpecLike, Matchers}
import repository.TransactionInMemoryRepository
import service.validator.NoSuchTransactionException

import scala.util.{Failure, Success}

class TransactionServiceSpec  extends FlatSpecLike with Matchers with BeforeAndAfterEach {
  implicit val transactionRepository = new TransactionInMemoryRepository
  val transactionService = new TransactionService

  override def afterEach(): Unit = transactionRepository.entities.clear

  "TransactionService" should "save transaction" in {
    val transaction = DepositTransactionInfo(to = Info.randomUuid, amount = 100)

    transactionService save transaction

    assert {
      transactionRepository.findById(transaction.id) match {
        case Some(DepositTransactionInfo(_, to, amount, status, dateTime)) =>
          to == transaction.to && amount == transaction.amount &&
            status == transaction.status && dateTime == transaction.dateTime
      }
    }
  }

  it should "get info of specified transaction" in {
    val transaction = DepositTransactionInfo(to = Info.randomUuid, amount = 100)

    transactionService save transaction

    assert {
      transactionService getTransactionInfo transaction.id match {
        case Success(DepositTransactionInfo(_, to, amount, status, dateTime)) =>
          to == transaction.to && amount == transaction.amount &&
            status == transaction.status && dateTime == transaction.dateTime
      }
    }
  }

  it should "return failure if specified transaction is not found" in {
    assert {
      transactionService getTransactionInfo Info.randomUuid match {
        case Failure(NoSuchTransactionException(_)) => true
      }
    }
  }

  it should "return every transaction" in {
    val first = DepositTransactionInfo(to = Info.randomUuid, amount = 100)
    val second = WithdrawalTransactionInfo(from = Info.randomUuid, amount = 100)

    transactionService save first
    transactionService save second

    val transactions = transactionService.findAll.toSeq
    assert {
      transactions.contains(first) &&
        transactions.contains(second) &&
        transactions.size == 2
    }
  }

  it should "update transaction" in {
    val transaction = DepositTransactionInfo(to = Info.randomUuid, amount = 100)
    val oldStatus = transaction.status
    val newStatus = TransactionStatus.COMPLETED

    transactionService save transaction

    transactionService updateTransaction transaction.copy(status = newStatus)

    assert {
      transactionService getTransactionInfo transaction.id match {
        case Success(DepositTransactionInfo(_, _, _, status, _)) =>
          status == newStatus && newStatus != oldStatus
      }
    }
  }

  it should "update transaction status" in {
    val deposit = DepositTransactionInfo(to = Info.randomUuid, amount = 100)
    val withdrawal = WithdrawalTransactionInfo(from = Info.randomUuid, amount = 100)
    val transfer = TransferTransactionInfo(from = Info.randomUuid, to = Info.randomUuid, amount = 100)

    transactionService save deposit

    transactionService.updateTransactionStatus(deposit.id, TransactionStatus.COMPLETED)

    assert {
      transactionService getTransactionInfo deposit.id match {
        case Success(DepositTransactionInfo(_, _, _, status, _)) =>
          status == TransactionStatus.COMPLETED
      }
    }

    transactionService save withdrawal

    transactionService.updateTransactionStatus(withdrawal.id, TransactionStatus.ERROR)

    assert {
      transactionService getTransactionInfo withdrawal.id match {
        case Success(WithdrawalTransactionInfo(_, _, _, status, _)) =>
          status == TransactionStatus.ERROR
      }
    }

    transactionService save transfer

    transactionService.updateTransactionStatus(transfer.id, TransactionStatus.WITHDRAWN)

    assert {
      transactionService getTransactionInfo transfer.id match {
        case Success(TransferTransactionInfo(_, _, _, _, status, _)) =>
          status == TransactionStatus.WITHDRAWN
      }
    }

  }

}
