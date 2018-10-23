package model

import model.Info.Uuid

object Info {
  type Uuid = String
}

case class AccountInfo(id: Uuid, balance: BigDecimal)

trait UpdateAccountInfo {
  def info: AccountInfo
}
