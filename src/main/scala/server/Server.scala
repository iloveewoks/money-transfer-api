package server

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives.{complete, get, path}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

import scala.concurrent.{ExecutionContextExecutor, Future}

class Server(interface: String, port: Int,
             accountManager: ActorRef,
             transactionManager: ActorRef)(implicit actorSystem: ActorSystem) {

  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher

  val route: Route =
    path("hello") {
      get {
        complete("Hello, World!")
      }
    }

  private val binding: Future[ServerBinding] =
    Http().bindAndHandle(route, interface, port)

  def stop: Future[Done] = binding.flatMap(_.unbind())

}
