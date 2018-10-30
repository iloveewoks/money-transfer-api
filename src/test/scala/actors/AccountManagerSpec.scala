package actors

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import model.{AccountInfo, Info}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpecLike, Matchers}
import repository.AccountInMemoryRepository
import service.AccountService
import service.validator.Validator.uuidRegEx

class AccountManagerSpec extends TestKit(ActorSystem("actor-test"))
  with FlatSpecLike with ImplicitSender
  with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

  import AccountManager._

  implicit var accountRepository = new AccountInMemoryRepository
  implicit var accountService = new AccountService
  var accountManager = system.actorOf(Props(new AccountManager()), "test-account-manager")

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  override def afterEach(): Unit = {
    accountRepository.entities.clear
  }

  "AccountManager" should "create a new account with empty balance" in {
    accountManager ! CreateAccount

    expectMsgPF() {
      case AccountInfo(id, balance)
        if balance == 0
          && id.matches(uuidRegEx)
          && accountRepository.findById(id).isDefined =>
    }
  }

  it should "respond with info about account with specified id when asked" in {
    accountManager ! CreateAccount


    expectMsgPF() {
      case AccountInfo(id, _) =>
        accountManager ! GetAccountInfo(id)

        expectMsgPF() {
          case AccountInfoMsg(info, _) if id == info.id =>
        }

    }
  }

  it should "respond with NoSuchAccount message if no account with specified id is found" in {
    val randomId = Info.generateUuid
    accountManager ! GetAccountInfo(randomId)

    expectMsgPF() {
      case NoSuchAccount(_, id, _) if id == randomId =>
    }
  }

  it should "update account with provided info" in {
    accountManager ! CreateAccount

    expectMsgPF() {
      case newAccount: AccountInfo =>
        val updatedAccount = newAccount.copy(balance = 1000)
        accountManager ! UpdateAccount(updatedAccount)

        expectMsg(AccountUpdated(updatedAccount))
    }
  }

  it should "respond with collection of all accounts when asked" in {
    accountManager ! CreateAccount

    expectMsgPF() {
      case first: AccountInfo =>
        accountManager ! CreateAccount

        expectMsgPF() {
          case second: AccountInfo =>
            accountManager ! GetAllAccounts

            expectMsgPF() {
              case AllAccountsInfo(accounts)
                if accounts.size == 2
                  && accounts.toSeq.contains(first)
                  && accounts.toSeq.contains(second) =>
            }
        }
    }
  }

  it should "forward deposits and withdrawals to specified account" in {
    accountManager ! CreateAccount

    expectMsgPF() {
      case AccountInfo(baseId, baseBalance) =>
        val amount = 100
        accountManager ! Deposit(baseId, Info.generateUuid, amount)

        expectMsgPF() {
          case DepositSuccess(_, AccountInfo(depositId, depositBalance))
            if depositId == baseId
              && depositBalance == baseBalance + amount =>

            accountManager ! Withdraw(baseId, Info.generateUuid, amount)

            expectMsgPF() {
              case WithdrawalSuccess(_, AccountInfo(withdrawalId, withdrawalBalance))
                if withdrawalId == baseId
                  && withdrawalBalance == baseBalance =>

                val trId = Info.generateUuid
                accountManager ! Withdraw(baseId, trId, amount)

                expectMsg(InsufficientFunds(trId, AccountInfo(baseId, baseBalance)))
            }
        }
    }
  }

}
