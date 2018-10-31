package service

import model.AccountInfo
import model.Info.Uuid
import repository.AccountRepository
import service.validator.{InvalidUuidFormatException, NoSuchAccountException}
import service.validator.Validator.uuidRegEx

import scala.util.{Failure, Success, Try}

class AccountService()(implicit val accountRepository: AccountRepository) {

  def getAccountInfo(id: Uuid): Try[AccountInfo] = {
    if (id matches uuidRegEx) {
      accountRepository.findById(id) match {
        case Some(info) => Success(info)
        case None => Failure(NoSuchAccountException(id))
      }
    } else Failure(InvalidUuidFormatException(id))
  }

  def findAll: Iterable[AccountInfo] = accountRepository findAll

  def findAll(sortWith: (AccountInfo, AccountInfo) => Boolean): Iterable[AccountInfo] =
    accountRepository findAll sortWith

  def findAll[B](sortBy: AccountInfo => B)(implicit ordering: Ordering[B]): Iterable[AccountInfo] =
    accountRepository findAll sortBy

  def createAccount: AccountInfo = accountRepository createAccount

  def updateAccount(newAccountInfo: AccountInfo): Try[AccountInfo] =
    getAccountInfo(newAccountInfo.id) map { _ => accountRepository.update(newAccountInfo) }

}
