package model

import model.Info.Uuid

object Info {
  type Uuid = String
}

case class AccountInfo(id: Uuid, balance: BigDecimal = 0)

trait UpdateAccountInfo {
  def info: AccountInfo
}
