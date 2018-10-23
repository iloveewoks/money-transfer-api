package repository

import model.Info.Uuid

trait Repository[T] {
  def findById(id: Uuid): Option[T]

  def save(entity: T): T

  def update(entity: T): T
}