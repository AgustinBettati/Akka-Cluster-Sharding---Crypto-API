package crypto.actor

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import crypto.actor.UserActor.{AmountDetailReceived, Command, FailedFetch, ObtainSummary, RegisterWallet, StoredWallet, UnknownChain, UserSummary, Wallet}
import crypto.service.AggregatedWalletService

import scala.util.{Failure, Success}

object UserActor {

  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("UserActor")

  sealed trait Command
  case class RegisterWallet(chain: String, address: String, replyTo: ActorRef[RegisterWalletResponse]) extends Command
  case class ObtainSummary(replyTo: ActorRef[UserSummary]) extends Command
  private case class AmountDetailReceived(chain: String, address: String, amt: Double) extends Command
  private case class FailedFetch(chain: String, address: String) extends Command

  case class UserSummary(wallets: List[Wallet])
  sealed trait RegisterWalletResponse
  case class StoredWallet() extends RegisterWalletResponse
  case class UnknownChain() extends RegisterWalletResponse

  case class Wallet(chain: String, address: String, amt: Option[Double], value: Option[Double])

  def apply(service: AggregatedWalletService): Behavior[Command] = Behaviors.setup { ctx => UserActor(ctx, service).running(Nil) }

}

case class UserActor private(ctx: ActorContext[Command], walletService: AggregatedWalletService) {
  def running(wallets: List[Wallet]): Behavior[Command] = Behaviors.receiveMessage {
    case RegisterWallet(chain, address, replyTo) =>
      val newWallet = Wallet(chain, address, None, None)
      walletService.getAmountInAddress(chain, address) match {
        case Some(future) =>
          ctx.pipeToSelf(future) {
            case Success(amt) => AmountDetailReceived(chain, address, amt)
            case Failure(ex) => FailedFetch(chain, address)
          }
          replyTo ! StoredWallet()
        case None =>
          replyTo ! UnknownChain()
      }
      running(newWallet :: wallets)
    case ObtainSummary(replyTo) =>
      replyTo ! UserSummary(wallets)
      Behaviors.same
    case AmountDetailReceived(chain, address, amt) =>
      val newWallets = wallets.collect {
        case w: Wallet if w.chain == chain && w.address == address => w.copy(amt = Some(amt))
      }
      running(newWallets)
  }
}
