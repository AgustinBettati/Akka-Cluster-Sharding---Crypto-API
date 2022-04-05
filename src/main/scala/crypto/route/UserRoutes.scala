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
import crypto.actor.UserActor
import crypto.actor.UserActor.{ObtainSummary, RegisterWallet, RegisterWalletResponse, StoredWallet, UnknownChain, UserSummary, Wallet}

import scala.concurrent.Future

case class UserRoutes(userShardedActor: ActorRef[ShardingEnvelope[UserActor.Command]])(implicit val system: ActorSystem[_]) extends CommonRoutes(system) {

  //#user-routes-class
  import JsonFormats._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  //#import-json-formats

  def getUserSummary(username: String): Future[UserSummary] = {
    userShardedActor.ask(replyTo => ShardingEnvelope(username, ObtainSummary(replyTo)))
  }

  def registerNewWallet(username: String, wallet: NewWallet): Future[RegisterWalletResponse] = {
    userShardedActor.ask(replyTo => ShardingEnvelope(username, RegisterWallet(wallet.chain, wallet.address,replyTo)))
  }

  //#all-routes
  val userRoutes: Route =
    pathPrefix("users") {
      concat(
        path(Segment) { name =>
          get {
            onSuccess(getUserSummary(name)) { response =>
              complete(response)
            }
        }
      },
        path(Segment / "wallets") { name =>
          post {
            entity(as[NewWallet]) { walletInfo =>
              onSuccess(registerNewWallet(name, walletInfo)) {
                case StoredWallet() => complete("stored")
                case UnknownChain() => complete("unknown chain")
              }
            }

          }
        }
      )
    }
  //#all-routes
}

case class NewWallet(chain: String, address: String)
