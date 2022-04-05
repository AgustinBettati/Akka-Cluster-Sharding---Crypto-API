package crypto.service

import scala.annotation.tailrec
import scala.concurrent.Future

case class AggregatedWalletService private(services: Map[String, ChainSpecificWalletService]) {
  def getAmountInAddress(chain: String, address: String): Option[Future[Double]] = services.get(chain).map(_.getAmountInAddress(address))
}

object AggregatedWalletService {
  def apply(list: List[ChainSpecificWalletService]): AggregatedWalletService = {
    val map = list.foldLeft[Map[String, ChainSpecificWalletService]](Map()) { (map, service) => map + (service.chainName -> service) }
    AggregatedWalletService(map)
  }
}
