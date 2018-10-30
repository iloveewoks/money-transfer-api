package server

import actors.{AccountManager, TransactionManager}
import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.pattern._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import cats.data.Validated.Valid
import model._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

trait RestService extends JsonSupport {
  implicit val actorSystem: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val executionContext: ExecutionContextExecutor
  implicit val timeout: Timeout = 150.seconds

  val accountManager: ActorRef
  val transactionManager: ActorRef

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
              case AccountManager.AccountInfoMsg(info, _) => complete(info)
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
            entity(as[Valid[TransactionManager.Deposit]]) { deposit =>
              onSuccess(transactionManager ? deposit) {
                case TransactionManager.TransactionCompleted(transaction) => complete(transaction)
                case TransactionManager.NoSuchTransaction(ex, _) => complete(StatusCodes.NotFound, ex.message)
                case AccountManager.NoSuchAccount(ex, _, _) => complete(StatusCodes.NotFound, ex.message)
                case AccountManager.InvalidUuidFormat(ex, _, _) => complete(StatusCodes.BadRequest, ex.message)
              }
            }
          }
        }
      } ~
      pathPrefix(transactionsPrefix) {
        path("withdraw") {
          post {
            entity(as[Valid[TransactionManager.Withdraw]]) { withdraw =>
              onSuccess(transactionManager ? withdraw) {
                case TransactionManager.TransactionCompleted(transaction) => complete(transaction)
                case AccountManager.InsufficientFunds(transactionId, accountInfo) =>
                  complete(StatusCodes.BadRequest, s"Not enough funds on account $accountInfo during transaction $transactionId")
                case TransactionManager.NoSuchTransaction(ex, _) => complete(StatusCodes.NotFound, ex.message)
                case AccountManager.NoSuchAccount(ex, _, _) => complete(StatusCodes.NotFound, ex.message)
                case AccountManager.InvalidUuidFormat(ex, _, _) => complete(StatusCodes.BadRequest, ex.message)
              }
            }
          }
        }
      } ~
      pathPrefix(transactionsPrefix) {
        path("transfer") {
          post {
            entity(as[Valid[TransactionManager.Transfer]]) { transfer =>
              onSuccess(transactionManager ? transfer) {
                case TransactionManager.TransactionCompleted(transaction) => complete(transaction)
                case AccountManager.InsufficientFunds(transactionId, accountInfo) =>
                  complete(StatusCodes.BadRequest, s"Not enough funds on account $accountInfo during transaction $transactionId")
                case TransactionManager.NoSuchTransaction(ex, _) => complete(StatusCodes.NotFound, ex.message)
                case AccountManager.NoSuchAccount(ex, _, _) => complete(StatusCodes.NotFound, ex.message)
                case AccountManager.InvalidUuidFormat(ex, _, _) => complete(StatusCodes.BadRequest, ex.message)
              }
            }
          }
        }
      }

  val route = accountsRoute ~ transactionsRoute
}

class Server(override val accountManager: ActorRef,
             override val transactionManager: ActorRef)
            (implicit val actorSystem: ActorSystem,
             implicit val materializer: ActorMaterializer,
             implicit val executionContext: ExecutionContextExecutor) extends RestService {

  private var binding: Future[ServerBinding] = Future.never

  def start(interface: String, port: Int): Unit = {
    stop
    binding = Http().bindAndHandle(route, interface, port)
  }

  def stop: Future[Done] = binding.flatMap(_.unbind())

}
