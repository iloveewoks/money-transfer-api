package repository

import java.util.UUID

import model.AccountInfo
import model.Info.Uuid

import scala.collection.mutable

abstract class AccountRepository extends InMemoryRepository[AccountInfo] {
  def createAccount: AccountInfo
}

class AccountInMemoryRepository extends AccountRepository {

  override def createAccount: AccountInfo = save(AccountInfo(UUID.randomUUID.toString))
  
}
