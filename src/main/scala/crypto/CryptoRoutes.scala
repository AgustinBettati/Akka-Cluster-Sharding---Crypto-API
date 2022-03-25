package crypto

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{ExceptionHandler, Route}

import scala.concurrent.Future
import crypto.CryptoActor._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.pattern.AskTimeoutException
import akka.util.Timeout

//#import-json-formats
//#user-routes-class
class CryptoRoutes(cryptoShardedActor: ActorRef[ShardingEnvelope[CryptoActor.Command]])(implicit val system: ActorSystem[_]) {

  //#user-routes-class
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import JsonFormats._
  //#import-json-formats

  // If ask takes more time than this to complete the request is failed
  private implicit val timeout = Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))

  implicit def myExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case _: AskTimeoutException =>
        complete(HttpResponse(InternalServerError, entity = "ask timeout exception"))
    }

  def getPrice(id: String): Future[PriceResponse] = {
    cryptoShardedActor.ask(replyTo => ShardingEnvelope(id,GetPrice(id, replyTo)))
  }
  //#all-routes
  val cryptoRoutes: Route =
    pathPrefix("coins") {
      concat(
        path(Segment) { name =>
          get {
            //#retrieve-crypto-info
            onSuccess(getPrice(name)) { response =>
              complete(response)
            }
            //#retrieve-user-info
          }
        })
      //#users-get-delete
    }
  //#all-routes
}
