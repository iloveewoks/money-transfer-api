package server

import actors.AccountManager
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
import model.AccountInfo
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val accountInfoFormat = jsonFormat2(AccountInfo)
  implicit val allAccountsInfoFormat = jsonFormat1(AccountManager.AllAccountsInfo)
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

  private val binding: Future[ServerBinding] =
    Http().bindAndHandle(route ~ accountsRoute, interface, port)

  def stop: Future[Done] = binding.flatMap(_.unbind())

}
