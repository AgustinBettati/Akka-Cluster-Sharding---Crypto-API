package crypto.route

import crypto.actor.CryptoActor.PriceResponse
import crypto.actor.UserActor.{UserSummary, Wallet}
import crypto.service.CryptoPrice
import spray.json.DefaultJsonProtocol

object JsonFormats {
  // import the default encoders for primitive types (Int, String, Lists etc)

  import DefaultJsonProtocol._

  implicit val cryptoPriceFormat = jsonFormat2(CryptoPrice)
  implicit val priceRespFormat = jsonFormat1(PriceResponse)

  implicit val walletFormat = jsonFormat4(Wallet)
  implicit val userSummaryFormat = jsonFormat1(UserSummary)

  implicit val newWalletReqFormat = jsonFormat2(NewWallet)


}
