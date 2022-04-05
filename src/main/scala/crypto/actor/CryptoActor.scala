package crypto.actor

import akka.actor.typed.scaladsl.{Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import crypto.actor.CryptoActor._
import crypto.service.{CryptoPrice, CryptoPriceService}
import org.slf4j.Logger

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}


object CryptoActor {

  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("CryptoActor")

  // actor protocol
  sealed trait Command
  final case class GetPrice(replyTo: ActorRef[PriceResponse]) extends Command
  private final case class SetPrice(price: CryptoPrice) extends Command
  private final case class FailedUpdate(e: Throwable) extends Command

  private final case object FetchPrice extends Command // internal message triggered by timer

  final case class PriceResponse(price: CryptoPrice)

  def apply(coin: String, priceService: CryptoPriceService): Behavior[Command] = {
    Behaviors.withTimers{ timers =>
      Behaviors.withStash(20) { buffer =>
        timers.startTimerAtFixedRate(FetchPrice, 0 seconds, 10 seconds)
        CryptoActor(coin, priceService, buffer).running(None)
      }
    }
  }

  def fetchPrice(priceService: CryptoPriceService, id: String, log: Logger)(implicit ex: ExecutionContext): Future[CryptoPrice] = {
    val eventualPrice = priceService.fetchPrice(id)
    eventualPrice.foreach( price => log.info(s"external request for price of $id returned ${price.currentPrice}"))
    eventualPrice
  }

}

case class CryptoActor private (coin: String, priceService: CryptoPriceService, stash: StashBuffer[Command]){

  private def running(stored: Option[CryptoPrice] = None): Behavior[Command] = Behaviors.receive { (context, msg) =>
    implicit val ec: ExecutionContextExecutor = context.executionContext
    msg match {
      case GetPrice(replyTo) =>
        context.log.info(s"handling GetPrice message of $coin")
        stored match {
          case Some(cached) =>
            context.log.info(s"returning cached value $coin")
            replyTo ! PriceResponse(cached)
          case None =>
            context.log.info(s"stashed request as no value is present")
            stash.stash(msg)
        }
        Behaviors.same
      case FetchPrice =>
        context.log.info(s"Scheduled fetch price $coin")
        context.pipeToSelf(fetchPrice(priceService, coin, context.log)){
          case Success(price) => SetPrice(price)
          case Failure(exception) => FailedUpdate(exception)
        }
        running(stored)
      case SetPrice(updatedPrice) =>
        stash.unstashAll(
          running(Some(updatedPrice))
        )
      case FailedUpdate(_) =>
        // will not restart actor as it is preferred to maintain the previous cached value.
        context.log.info(s"failed to fetch new value for $coin")
        Behaviors.same
    }
  }

}