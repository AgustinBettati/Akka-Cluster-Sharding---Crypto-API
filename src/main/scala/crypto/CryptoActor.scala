package crypto

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import crypto.service.{CryptoPrice, CryptoPriceService}
import org.slf4j.Logger

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}


object CryptoActor {

  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("CryptoActor")

  // actor protocol
  sealed trait Command
  final case class GetPrice(id: String, replyTo: ActorRef[PriceResponse]) extends Command
  final case class SetPrice(price: CryptoPrice) extends Command

  final case class PriceResponse(price: CryptoPrice)

  def apply(priceService: CryptoPriceService): Behavior[Command] = running(priceService)

  def running(priceService: CryptoPriceService, stored: Option[CryptoPrice] = None): Behavior[Command] = Behaviors.receive { (context, msg) =>
    implicit val ec: ExecutionContextExecutor = context.executionContext

    msg match {
      case GetPrice(cryptoId, replyTo) =>
        context.log.info(s"handling GetPrice message of $cryptoId")
        stored match {
          case Some(cached) =>
            context.log.info(s"returning cached value $cryptoId")
            replyTo ! PriceResponse(cached)
          case None =>
            fetchPrice(priceService, cryptoId, context.log).foreach { price =>
              replyTo ! PriceResponse(price)
              context.self ! SetPrice(price)
            }
        }
        Behaviors.same
      case SetPrice(updatedPrice) =>
        running(priceService, Some(updatedPrice))
    }
  }

  def fetchPrice(priceService: CryptoPriceService, id: String, log: Logger)(implicit ex: ExecutionContext): Future[CryptoPrice] = {
    val eventualPrice = priceService.fetchPrice(id)
    eventualPrice.foreach( price => log.info(s"external request for price of $id returned ${price.currentPrice}"))
    eventualPrice
  }

}