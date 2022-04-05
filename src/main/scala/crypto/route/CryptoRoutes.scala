package crypto.route

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.pattern.AskTimeoutException
import akka.util.Timeout
import crypto.actor.CryptoActor._
import crypto.actor.CryptoActor

import scala.concurrent.Future

class CryptoRoutes(cryptoShardedActor: ActorRef[ShardingEnvelope[CryptoActor.Command]])(implicit val system: ActorSystem[_]) extends CommonRoutes(system){

  import JsonFormats._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  def getPrice(id: String): Future[PriceResponse] = {
    cryptoShardedActor.ask(replyTo => ShardingEnvelope(id,GetPrice(replyTo)))
  }
  val cryptoRoutes: Route =
    pathPrefix("coins") {
      concat(
        path(Segment) { name =>
          get {
            onSuccess(getPrice(name)) { response =>
              complete(response)
            }
          }
        })
    }
}
