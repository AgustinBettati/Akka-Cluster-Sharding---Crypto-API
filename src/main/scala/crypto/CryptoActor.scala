package crypto

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import crypto.service.{CryptoPrice, CryptoPriceService}
import org.slf4j.Logger

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}


object CryptoActor {
  // actor protocol
  sealed trait Command
  final case class GetPrice(id: String, replyTo: ActorRef[PriceResponse]) extends Command

  final case class PriceResponse(price: CryptoPrice)

  def apply(priceService: CryptoPriceService): Behavior[Command] = Behaviors.receive { (context, msg) =>
    implicit val ec: ExecutionContextExecutor = context.executionContext

    msg match {
      case GetPrice(cryptoId, replyTo) =>
        context.log.info(s"handling GetPrice message of $cryptoId")
        fetchPrice(priceService, cryptoId, context.log).foreach(price => replyTo ! PriceResponse(price))
        Behaviors.same
    }
  }

  def fetchPrice(priceService: CryptoPriceService, id: String, log: Logger)(implicit ex: ExecutionContext): Future[CryptoPrice] = {
    val eventualPrice = priceService.fetchPrice(id)
    eventualPrice.foreach( price => log.info(s"external request for price of $id returned ${price.currentPrice}"))
    eventualPrice
  }

}