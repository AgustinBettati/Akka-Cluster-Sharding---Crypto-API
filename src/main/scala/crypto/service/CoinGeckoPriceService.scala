package crypto.service
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import java.util.Date

case class CoinGeckoPriceService()(implicit val actorSystem: ActorSystem, ec: ExecutionContext) extends CryptoPriceService {

  implicit val marketDataFormat: RootJsonFormat[MarketDataDTO] = jsonFormat1(MarketDataDTO)
  case class MarketDataDTO(`current_price`: Map[String, Double])

  implicit val respFormat: RootJsonFormat[CoinGeckoResultDTO] = jsonFormat2(CoinGeckoResultDTO)
  case class CoinGeckoResultDTO(`market_data`: MarketDataDTO, `last_updated`: String) {
    def toCryptoPrice: CryptoPrice = {
      val usdPrice: Double = `market_data`.`current_price`("usd")
      CryptoPrice(usdPrice, `last_updated`)
    }
  }


  override def fetchPrice(id: String): Future[CryptoPrice] = {
    val apiResp: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = s"https://api.coingecko.com/api/v3/coins/$id"))
    apiResp.flatMap { resp =>
      Unmarshal(resp).to[CoinGeckoResultDTO].map(_.toCryptoPrice)
    }

  }
}


