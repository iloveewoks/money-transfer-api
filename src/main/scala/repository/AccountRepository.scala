package repository

import java.util.UUID

import model.AccountInfo
import model.Info.Uuid

import scala.collection.mutable

abstract class AccountRepository extends Repository[AccountInfo] {
  abstract def createAccount: AccountInfo
}

class AccountInMemoryRepository extends AccountRepository {
  val accounts: mutable.Map[Uuid, AccountInfo] = mutable.Map[Uuid, AccountInfo]()

  override def findById(id: Uuid): Option[AccountInfo] = accounts get id

  override def findAll: Iterable[AccountInfo] = accounts.values

  override def save(entity: AccountInfo): AccountInfo = accounts + entity.id -> entity _2

  override def update(entity: AccountInfo): AccountInfo = {
    accounts.update(entity.id, entity)
    entity
  }

  override def createAccount: AccountInfo = save(AccountInfo(UUID.randomUUID.toString))
}
