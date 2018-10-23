package repository

import java.util.UUID

import model.AccountInfo
import model.Info.Uuid

class AccountInMemoryRepository extends Repository[AccountInfo] {
  override def findById(id: Uuid): Option[AccountInfo] = ???

  override def save(entity: AccountInfo): AccountInfo = ???

  override def update(entity: AccountInfo): AccountInfo = ???


  def createAccount: AccountInfo = save(AccountInfo(UUID.randomUUID.toString))

}
