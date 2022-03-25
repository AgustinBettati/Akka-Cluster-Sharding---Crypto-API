package crypto

import crypto.CryptoActor.PriceResponse
import crypto.service.CryptoPrice

//#json-formats
import spray.json.DefaultJsonProtocol

object JsonFormats  {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit val cryptoPriceFormat = jsonFormat2(CryptoPrice)
  implicit val priceRespFormat = jsonFormat1(PriceResponse)

}
//#json-formats
