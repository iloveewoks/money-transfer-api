package server

import java.time.Instant

import actors.TransactionManager
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import cats.data.Validated.{Invalid, Valid}
import model._
import service.validator.Validatable
import spray.json.{DefaultJsonProtocol, JsNumber, JsObject, JsString, JsValue, RootJsonFormat, deserializationError}

import scala.concurrent.Future

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  class EnumerationFormat[A](enum: Enumeration) extends RootJsonFormat[A] {
    def write(obj: A): JsValue = JsString(obj.toString)

    def read(json: JsValue): A = json match {
      case JsString(str) => enum.withName(str).asInstanceOf[A]
      case x => throw new RuntimeException(s"unknown enumeration value: $x")
    }
  }

  implicit object transactionStatusFormat extends EnumerationFormat[TransactionStatus.Value](TransactionStatus)
  implicit object transactionTypeFormat extends EnumerationFormat[TransactionType.Value](TransactionType)
  implicit val accountInfoFormat = jsonFormat2(AccountInfo)
  implicit val depositCommandFormat = jsonFormat2(TransactionManager.Deposit)
  implicit val withdrawalCommandFormat = jsonFormat2(TransactionManager.Withdraw)
  implicit val transferCommandFormat = jsonFormat3(TransactionManager.Transfer)

  implicit object depositTransactionInfoFormat extends RootJsonFormat[DepositTransactionInfo] {
    override def read(json: JsValue): DepositTransactionInfo = {
      json.asJsObject.getFields("id", "to", "amount", "status", "date-time") match {
        case Seq(JsString(id), JsString(to), JsNumber(amount), JsString(status), JsString(dateTime)) =>
          DepositTransactionInfo(id, to, amount.bigDecimal, TransactionStatus.withName(status), Instant.parse(dateTime))
        case _ => deserializationError(s"Unknown transaction object: $json")
      }
    }

    override def write(obj: DepositTransactionInfo): JsValue = JsObject(
      "id" -> JsString(obj.id),
      "to" -> JsString(obj.to),
      "amount" -> JsNumber(obj.amount),
      "status" -> JsString(obj.status.toString),
      "date-time" -> JsString(obj.dateTime.toString),
      "type" -> JsString(obj.transactionType.toString)
    )
  }

  implicit object withdrawalTransactionInfoFormat extends RootJsonFormat[WithdrawalTransactionInfo] {
    override def read(json: JsValue): WithdrawalTransactionInfo = {
      json.asJsObject.getFields("id", "from", "amount", "status", "date-time") match {
        case Seq(JsString(id), JsString(from), JsNumber(amount), JsString(status), JsString(dateTime)) =>
          WithdrawalTransactionInfo(id, from, amount.bigDecimal, TransactionStatus.withName(status), Instant.parse(dateTime))
        case _ => deserializationError(s"Unknown transaction object: $json")
      }
    }

    override def write(obj: WithdrawalTransactionInfo): JsValue = JsObject(
      "id" -> JsString(obj.id),
      "from" -> JsString(obj.from),
      "amount" -> JsNumber(obj.amount),
      "status" -> JsString(obj.status.toString),
      "date-time" -> JsString(obj.dateTime.toString),
      "type" -> JsString(obj.transactionType.toString)
    )
  }

  implicit object transferTransactionInfoFormat extends RootJsonFormat[TransferTransactionInfo] {
    override def read(json: JsValue): TransferTransactionInfo = {
      json.asJsObject.getFields("id", "from", "to", "amount", "status", "date-time") match {
        case Seq(JsString(id), JsString(from), JsString(to), JsNumber(amount), JsString(status), JsString(dateTime)) =>
          TransferTransactionInfo(id, from, to, amount.bigDecimal, TransactionStatus.withName(status), Instant.parse(dateTime))
        case _ => deserializationError(s"Unknown transaction object: $json")
      }
    }

    override def write(obj: TransferTransactionInfo): JsValue = JsObject(
      "id" -> JsString(obj.id),
      "from" -> JsString(obj.from),
      "to" -> JsString(obj.to),
      "amount" -> JsNumber(obj.amount),
      "status" -> JsString(obj.status.toString),
      "date-time" -> JsString(obj.dateTime.toString),
      "type" -> JsString(obj.transactionType.toString)
    )
  }

  implicit object transactionFormat extends RootJsonFormat[TransactionInfo] {
    override def read(json: JsValue): TransactionInfo = {
      json.asJsObject.fields.get("transactionType") match {
        case Some(TransactionType.DEPOSIT) => depositTransactionInfoFormat.read(json)
        case Some(TransactionType.WITHDRAWAL) => withdrawalTransactionInfoFormat.read(json)
        case Some(TransactionType.TRANSFER) => transferTransactionInfoFormat.read(json)
        case _ => deserializationError(s"Unknown transaction object: $json")
      }
    }

    override def write(obj: TransactionInfo): JsValue = obj match {
      case deposit: DepositTransactionInfo => depositTransactionInfoFormat.write(deposit)
      case withdrawal: WithdrawalTransactionInfo => withdrawalTransactionInfoFormat.write(withdrawal)
      case transfer: TransferTransactionInfo => transferTransactionInfoFormat.write(transfer)
    }
  }

  implicit def validatedEntityUnmarshaller[A <: Validatable[A]]
    (implicit um: FromRequestUnmarshaller[A]): FromRequestUnmarshaller[Valid[A]] =
    um.flatMap { _ => _ => entity =>
      entity.validate match {
        case v @ Valid(_) =>
          Future.successful(v)
        case Invalid(failures) =>
          val message = failures.toList.map(_.message).mkString(";\n")
          Future.failed(new IllegalArgumentException(message))
      }
    }

}
