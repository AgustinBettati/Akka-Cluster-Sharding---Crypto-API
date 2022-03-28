package crypto

import akka.actor.typed.scaladsl.StashBuffer
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import crypto.service.{CryptoPrice, CryptoPriceService}
import org.slf4j.Logger

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.language.postfixOps


object CryptoActor {

  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("CryptoActor")

  // actor protocol
  sealed trait Command
  final case class GetPrice(replyTo: ActorRef[PriceResponse]) extends Command
  final case class SetPrice(price: CryptoPrice) extends Command
  final case object FetchPrice extends Command

  final case class PriceResponse(price: CryptoPrice)

  def apply(coin: String, priceService: CryptoPriceService): Behavior[Command] = {
    Behaviors.withTimers{ timers =>
      Behaviors.withStash(20) { buffer =>
        timers.startTimerAtFixedRate(FetchPrice, 0 seconds, 10 seconds)
        running(coin, priceService, stash = buffer)
      }
    }
  }

  def running(coin: String, priceService: CryptoPriceService, stored: Option[CryptoPrice] = None, stash: StashBuffer[Command]): Behavior[Command] = Behaviors.receive { (context, msg) =>
    implicit val ec: ExecutionContextExecutor = context.executionContext
    msg match {
      case GetPrice(replyTo) =>
        context.log.info(s"handling GetPrice message of $coin")
        stored match {
          case Some(cached) =>
            context.log.info(s"returning cached value $coin")
            replyTo ! PriceResponse(cached)
          case None =>
            context.log.info(s"stashed request")
            stash.stash(msg)
        }
        Behaviors.same
      case FetchPrice =>
        context.log.info(s"Scheduled fetch price $coin")
        fetchPrice(priceService, coin, context.log).foreach { price =>
          context.self ! SetPrice(price)
        }
        running(coin, priceService, stored, stash)
      case SetPrice(updatedPrice) =>
        stash.unstashAll(
          running(coin, priceService, Some(updatedPrice), stash)
        )
    }
  }

  def fetchPrice(priceService: CryptoPriceService, id: String, log: Logger)(implicit ex: ExecutionContext): Future[CryptoPrice] = {
    val eventualPrice = priceService.fetchPrice(id)
    eventualPrice.foreach( price => log.info(s"external request for price of $id returned ${price.currentPrice}"))
    eventualPrice
  }

}