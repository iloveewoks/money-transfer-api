package actors

import actors.Account.GetInfo
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import model.{AccountInfo, Info}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

class AccountSpec extends TestKit(ActorSystem("actor-test"))
  with FlatSpecLike with ImplicitSender
  with Matchers with BeforeAndAfterAll {

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "Account" should "respond with current account information" in {
    val info = AccountInfo()
    val accountActor = system.actorOf(Props(new Account(info, self)), info.id)

    accountActor ! GetInfo
    expectMsgPF() {
      case AccountInfo(id, balance) if id == info.id && balance == info.balance =>
    }
  }

  it should "be debited for specified amount" in {
    val info = AccountInfo()
    val accountActor = system.actorOf(Props(new Account(info, self)), info.id)
    val transactionId = Info.randomUuid

    accountActor ! AccountManager.Deposit(info.id, transactionId, 100)

    ignoreMsg {
      case _: AccountManager.UpdateAccount => true
    }

    expectMsgPF() {
      case AccountManager.DepositSuccess(trId, AccountInfo(id, balance))
        if transactionId == trId && info.id == id && balance == 100 =>
    }
  }

  it should "not withdraw money if the balance is insufficient" in {
    val info = AccountInfo()
    val accountActor = system.actorOf(Props(new Account(info, self)), info.id)
    val transactionId = Info.randomUuid

    accountActor ! AccountManager.Withdraw(info.id, transactionId, 100)

    ignoreMsg {
      case _: AccountManager.UpdateAccount => true
    }

    expectMsgPF() {
      case AccountManager.InsufficientFunds(_, trId, AccountInfo(id, balance))
        if transactionId == trId && info.id == id && balance == info.balance =>
    }
  }

  it should "withdraw specified amount if the balance is sufficient" in {
    val info = AccountInfo(balance = 100)
    val accountActor = system.actorOf(Props(new Account(info, self)), info.id)
    val transactionId = Info.randomUuid
    val amount = 50

    accountActor ! AccountManager.Withdraw(info.id, transactionId, amount)

    ignoreMsg {
      case _: AccountManager.UpdateAccount => true
    }

    expectMsgPF() {
      case AccountManager.WithdrawalSuccess(trId, AccountInfo(id, balance))
        if trId == transactionId && id == info.id && balance == info.balance - amount =>
    }
  }
}
