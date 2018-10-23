package actors

import actors.AccountManager.{AccountInfoMsg, GetAccountInfo}
import akka.actor.ActorLogging
import akka.persistence.PersistentActor
import model.{AccountInfo, UpdateAccountInfo}

class Account(var info: AccountInfo) extends PersistentActor with ActorLogging {
  override def persistenceId: String = info.id

  override def receiveRecover: Receive = {
    case infoUpdate: UpdateAccountInfo => {
      log.debug(s"Updating account state from {} to {}", info, infoUpdate.info)
      info = infoUpdate.info
    }
  }

  override def receiveCommand: Receive = awaitingCommand

  def awaitingCommand: Receive = {
    case GetAccountInfo => {
      log.debug("Getting account info: {}", info)
      sender() ! AccountInfoMsg(info)
    }
  }
}
