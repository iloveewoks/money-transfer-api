package server

import actors.{AccountManager, TransactionManager}
import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import model._
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

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
  implicit val depositTransactionInfoFormat = jsonFormat4(DepositTransactionInfo)
  implicit val withdrawalTransactionInfoFormat = jsonFormat4(WithdrawalTransactionInfo)
  implicit val transferTransactionInfoFormat = jsonFormat5(TransferTransactionInfo)
  implicit val depositCommandFormat = jsonFormat2(TransactionManager.Deposit)
  implicit val withdrawalCommandFormat = jsonFormat2(TransactionManager.Withdraw)
  implicit val transferCommandFormat = jsonFormat3(TransactionManager.Transfer)

  implicit val transactionFormat = new RootJsonFormat[TransactionInfo] {
    override def read(json: JsValue): TransactionInfo = {
      val jsonObject = json.asJsObject
      jsonObject.fields.get("to") match {
        case Some(_) =>
          jsonObject.fields.get("from") match {
            case Some(_) => transferTransactionInfoFormat.read(json)
            case None => depositTransactionInfoFormat.read(json)
          }

        case None =>
          jsonObject.fields.get("from") match {
            case Some(_) => withdrawalTransactionInfoFormat.read(json)
            case None => deserializationError(s"Unknown transaction object: $json")
          }
      }
    }

    override def write(obj: TransactionInfo): JsValue = obj match {
      case deposit: DepositTransactionInfo => depositTransactionInfoFormat.write(deposit)
      case withdrawal: WithdrawalTransactionInfo => withdrawalTransactionInfoFormat.write(withdrawal)
      case transfer: TransferTransactionInfo => transferTransactionInfoFormat.write(transfer)
    }
  }

}

class Server(interface: String, port: Int,
             accountManager: ActorRef,
             transactionManager: ActorRef)(implicit actorSystem: ActorSystem) extends JsonSupport {

  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher

  implicit val timeout: Timeout = 150.seconds


  val route: Route =
    path("hello") {
      get {
        complete("Hello, World!")
      }
    }

  val accountsPrefix = "accounts"
  val accountsRoute =
    path(accountsPrefix) {
      get {
        onSuccess(accountManager ? AccountManager.GetAllAccounts) {
          case AccountManager.AllAccountsInfo(accounts) => complete(accounts)
        }
      }
    } ~
    path(accountsPrefix) {
      post {
        onSuccess(accountManager ? AccountManager.CreateAccount) {
          case info @ AccountInfo(_, _) => complete(info)
        }
      }
    } ~
    pathPrefix(accountsPrefix) {
      path(JavaUUID) { id =>
        get {
          onSuccess(accountManager ? AccountManager.GetAccountInfo(id.toString)) {
            case info @ AccountInfo(_, _) => complete(info)
            case AccountManager.NoSuchAccount(ex, _, _) => complete(StatusCodes.NotFound, ex.message)
            case AccountManager.InvalidUuidFormat(ex, _, _) => complete(StatusCodes.BadRequest, ex.message)
          }
        }
      }
    }

  val transactionsPrefix = "transactions"
  val transactionsRoute =
    path(transactionsPrefix) {
      get {
        onSuccess(transactionManager ? TransactionManager.GetAllTransactions) {
          case TransactionManager.AllTransactionsInfo(transactions) => complete(transactions)
        }
      }
    } ~
    pathPrefix(transactionsPrefix) {
      path(JavaUUID) {id =>
        get {
          onSuccess(transactionManager ? TransactionManager.GetTransactionInfo(id.toString)) {
            case info: TransactionInfo => complete(info)
            case TransactionManager.NoSuchTransaction(ex, _) => complete(StatusCodes.NotFound, ex.message)
            case AccountManager.InvalidUuidFormat(ex, _, _) => complete(StatusCodes.BadRequest, ex.message)
          }
        }
      }
    } ~
    pathPrefix(transactionsPrefix) {
      path("deposit") {
        post {
          entity(as[TransactionManager.Deposit]) { deposit =>
            onSuccess(transactionManager ? deposit) {
              case TransactionManager.TransactionCompleted(transaction) => complete(transaction)
              case TransactionManager.NoSuchTransaction(ex, _) => complete(StatusCodes.NotFound, ex.message)
              case AccountManager.InvalidUuidFormat(ex, _, _) => complete(StatusCodes.BadRequest, ex.message)
            }
          }
        }
      }
    } ~
      pathPrefix(transactionsPrefix) {
        path("withdraw") {
          post {
            entity(as[TransactionManager.Withdraw]) { withdraw =>
              onSuccess(transactionManager ? withdraw) {
                case TransactionManager.TransactionCompleted(transaction) => complete(transaction)
                case AccountManager.InsufficientFunds(transactionId, accountInfo) =>
                  complete(StatusCodes.BadRequest, s"Not enough funds on account $accountInfo during transaction $transactionId")
                case TransactionManager.NoSuchTransaction(ex, _) => complete(StatusCodes.NotFound, ex.message)
                case AccountManager.InvalidUuidFormat(ex, _, _) => complete(StatusCodes.BadRequest, ex.message)
              }
            }
          }
        }
      } ~
      pathPrefix(transactionsPrefix) {
        path("transfer") {
          post {
            entity(as[TransactionManager.Transfer]) { transfer =>
              onSuccess(transactionManager ? transfer) {
                case TransactionManager.TransactionCompleted(transaction) => complete(transaction)
                case AccountManager.InsufficientFunds(transactionId, accountInfo) =>
                  complete(StatusCodes.BadRequest, s"Not enough funds on account $accountInfo during transaction $transactionId")
                case TransactionManager.NoSuchTransaction(ex, _) => complete(StatusCodes.NotFound, ex.message)
                case AccountManager.InvalidUuidFormat(ex, _, _) => complete(StatusCodes.BadRequest, ex.message)
              }
            }
          }
        }
      }


  private val binding: Future[ServerBinding] =
    Http().bindAndHandle(route ~ accountsRoute ~ transactionsRoute, interface, port)

  def stop: Future[Done] = binding.flatMap(_.unbind())

}
