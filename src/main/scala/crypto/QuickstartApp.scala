package crypto

import akka.actor
import akka.actor.typed.{ActorRef, ActorSystem, SupervisorStrategy}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.routing.RoundRobinPool
import crypto.service.CoinGeckoPriceService

import scala.concurrent.ExecutionContextExecutor
import scala.util.Failure
import scala.util.Success

//#main-class
object QuickstartApp {
  //#start-http-server
  private def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {
    // Akka HTTP still needs a classic ActorSystem to start
    import system.executionContext

    val futureBinding = Http().newServerAt("localhost", 8080).bind(routes)
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }
  //#start-http-server
  def main(args: Array[String]): Unit = {
    //#server-bootstrapping
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      implicit val ec = context.executionContext
      implicit val actorSystem = context.system.classicSystem

      val sharding = ClusterSharding(context.system)
      val shardingActor = sharding.init(Entity(CryptoActor.TypeKey)(createBehavior = entityContext => CryptoActor(CoinGeckoPriceService())))

      val routes = new CryptoRoutes(shardingActor)(context.system)
      startHttpServer(routes.cryptoRoutes)(context.system)

      Behaviors.empty
    }
    val system = ActorSystem[Nothing](rootBehavior, "HttpServer")
    //#server-bootstrapping
  }
}
//#main-class
