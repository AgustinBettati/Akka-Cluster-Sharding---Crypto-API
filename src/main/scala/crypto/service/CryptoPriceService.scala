package crypto.service

import java.util.Date
import scala.concurrent.Future

trait CryptoPriceService {
  def fetchPrice(id: String): Future[CryptoPrice]
}

/**
 * @param currentPrice: expressed in usd
 */
case class CryptoPrice(currentPrice: Double, lastUpdate: String)
