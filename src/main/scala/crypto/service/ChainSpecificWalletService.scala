package crypto.service

import scala.concurrent.Future

trait ChainSpecificWalletService {
  def chainName: String
  def getAmountInAddress(address: String): Future[Double]
}
