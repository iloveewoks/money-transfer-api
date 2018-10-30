package repository

import model.AccountInfo
import org.scalatest.{BeforeAndAfterEach, FlatSpecLike, Matchers}
import service.validator.Validator.uuidRegEx

class AccountRepositorySpec extends FlatSpecLike with Matchers with BeforeAndAfterEach {
  val accountRepository = new AccountInMemoryRepository

  override def afterEach(): Unit = accountRepository.entities.clear

  "AccountRepository" should "create new account" in {
    assert {
      accountRepository.createAccount match {
        case AccountInfo(id, balance) =>
          id.matches(uuidRegEx) && balance == 0
        case _ => false
      }
    }
  }

  it should "save account" in {
    val account = AccountInfo()
    accountRepository save account

    assert {
      accountRepository.findById(account.id).isDefined
    }
  }

  it should "find account by id" in {
    val account = accountRepository createAccount

    assert(accountRepository.findById(account.id).isDefined)
  }

  it should "return every account" in {
    val first = accountRepository createAccount
    val second = accountRepository createAccount

    val accounts = accountRepository.findAll.toSeq
    assert {
      accounts.contains(first) &&
        accounts.contains(second) &&
        accounts.size == 2
    }
  }

  it should "find nothing when empty" in {
    assert {
      accountRepository.findAll.isEmpty
    }
  }

  it should "update account" in {
    val account = AccountInfo()
    val newBalance = 100

    accountRepository update account.copy(balance = newBalance)

    assert {
      accountRepository.findById(account.id) match {
        case Some(AccountInfo(_, balance)) => balance == newBalance
        case _ => false
      }
    }
  }

}
