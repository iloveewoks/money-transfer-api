package service

import model.{AccountInfo, Info}
import org.scalatest.{BeforeAndAfterEach, FlatSpecLike, Matchers}
import repository.AccountInMemoryRepository
import service.validator.NoSuchAccountException
import service.validator.Validator.uuidRegEx

import scala.util.{Failure, Success}

class AccountServiceSpec extends FlatSpecLike with Matchers with BeforeAndAfterEach {
  implicit val accountRepository = new AccountInMemoryRepository
  val accountService = new AccountService

  override def afterEach(): Unit = accountRepository.entities.clear

  "AccountService" should "create account" in {
    assert {
      accountRepository.createAccount match {
        case AccountInfo(id, balance) =>
          id.matches(uuidRegEx) && balance == 0
      }
    }
  }

  it should "get info of specified account" in {
    val account = accountService createAccount

    assert {
      accountService.getAccountInfo(account.id) match {
        case Success(AccountInfo(id, balance)) => id == account.id && balance == account.balance
      }
    }
  }

  it should "return failure if specified account not found" in {
    assert {
      accountService.getAccountInfo(Info.randomUuid) match {
        case Failure(NoSuchAccountException(_)) => true
      }
    }
  }

  it should "return every account" in {
    val first = accountService createAccount
    val second = accountService createAccount

    val accounts = accountService.findAll.toSeq
    assert {
      accounts.contains(first) &&
        accounts.contains(second) &&
        accounts.size == 2
    }
  }

  it should "update account" in {
    val account = accountService createAccount
    val newBalance = 100

    accountService updateAccount account.copy(balance = newBalance)

    assert {
      accountService.getAccountInfo(account.id) match {
        case Success(AccountInfo(_, balance)) => balance == newBalance
      }
    }
  }

}
