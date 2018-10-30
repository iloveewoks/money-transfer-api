package repository

import model.Info
import model.Info.Uuid

import scala.collection.mutable

trait Repository[T] {
  def findById(id: Uuid): Option[T]

  def findAll: Iterable[T]

  def findAll(sortWith: (T, T) => Boolean): Iterable[T]

  def findAll[B](sortBy: T => B)(implicit ordering: Ordering[B]): Iterable[T]

  def save(entity: T): T

  def update(entity: T): T
}

class InMemoryRepository[T <: Info] extends Repository[T] {
  var entities: mutable.Map[Uuid, T] = mutable.Map[Uuid, T]()

  override def findById(id: Uuid): Option[T] = entities get id

  override def findAll: Iterable[T] = entities.values

  override def findAll(sortWith: (T, T) => Boolean): Iterable[T] = entities.values.toSeq.sortWith(sortWith)

  override def findAll[B](sortBy: T => B)(implicit ordering: Ordering[B]): Iterable[T] = entities.values.toSeq.sortBy(sortBy)

  override def save(entity: T): T = {
    entities += entity.id -> entity
    entity
  }

  override def update(entity: T): T = {
    entities.update(entity.id, entity)
    entity
  }
}