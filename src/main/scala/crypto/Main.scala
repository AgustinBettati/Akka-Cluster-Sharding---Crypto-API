package crypto

import akka.actor
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.{Behaviors, PoolRouter, Routers}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, Route}
import akka.routing.RoundRobinPool
import crypto.actor.{CryptoActor, UserActor}
import crypto.route.{CryptoRoutes, UserRoutes}
import crypto.service.{AggregatedWalletService, CoinGeckoPriceService, CryptoPriceService, EtherscanWalletService}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor
import scala.util.Failure
import scala.util.Success

//#main-class
object Main {
  //#start-http-server
  private def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {
    // Akka HTTP still needs a classic ActorSystem to start
    import system.executionContext

    val port = system.settings.config.getInt("akka.http.server.default-http-port")
    val futureBinding = Http().newServerAt("localhost", port).bind(routes)
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

    setValuesOfRuntimeArguments(args)

    //#server-bootstrapping
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      implicit val ec: ExecutionContextExecutor = context.executionContext
      implicit val actorSystem: actor.ActorSystem = context.system.classicSystem

      val walletService = AggregatedWalletService(List(EtherscanWalletService()))
      val priceService: CryptoPriceService = CoinGeckoPriceService()

      val sharding = ClusterSharding(context.system)
      val cryptoActor = sharding.init(Entity(CryptoActor.TypeKey)
        (createBehavior = ctx => withResumeSupervisor(CryptoActor(ctx.entityId, priceService))))
      val userActor = sharding.init(Entity(UserActor.TypeKey)
        (createBehavior = ctx => withResumeSupervisor(UserActor(walletService))))

      val cryptoRoutes = new CryptoRoutes(cryptoActor)(context.system)
      val userRoutes = new UserRoutes(userActor)(context.system)

      startHttpServer(Directives.concat(cryptoRoutes.cryptoRoutes, userRoutes.userRoutes))(context.system)

      Behaviors.empty
    }
    val system = ActorSystem[Nothing](rootBehavior, "HttpServer")
    //#server-bootstrapping
  }

  private def setValuesOfRuntimeArguments(args: Array[String]): Unit = {
    val log = LoggerFactory.getLogger(this.getClass)
    val Opt = """-D(\S+)=(\S+)""".r
    args.toList.foreach {
      case Opt(key, value) =>
        log.info(s"Config Override: $key = $value")
        System.setProperty(key, value)
    }
  }

  private def withResumeSupervisor[T](b: Behavior[T]): Behavior[T] = Behaviors.supervise(b).onFailure[Throwable](SupervisorStrategy.resume)
}
//#main-class
