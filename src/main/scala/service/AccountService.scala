package service

import model.AccountInfo
import model.Info.Uuid
import repository.AccountInMemoryRepository
import service.validator.{InvalidUuidFormatException, NoSuchAccountException}
import service.validator.Validator.uuidRegEx

import scala.util.{Failure, Success, Try}

class AccountService()(implicit val accountRepository: AccountInMemoryRepository) {

  def getAccountInfo(id: Uuid): Try[AccountInfo] = {
    if (id matches uuidRegEx) {
      accountRepository.findById(id) match {
        case Some(info) => Success(info)
        case None => Failure(NoSuchAccountException(s"Account with UUID $id not found"))
      }
    } else Failure(InvalidUuidFormatException(s"UUID $id is invalid"))
  }

  def createAccount = accountRepository createAccount

  def updateAccount(newAccountInfo: AccountInfo): Try[AccountInfo] =
    getAccountInfo(newAccountInfo.id) map accountRepository.update

}
