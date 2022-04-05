package crypto.service

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import spray.json.RootJsonFormat
import spray.json.DefaultJsonProtocol._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import scala.concurrent.{ExecutionContext, Future}

case class EtherscanWalletService()(implicit val actorSystem: ActorSystem, ec: ExecutionContext) extends ChainSpecificWalletService {

  implicit val respFormat: RootJsonFormat[EtherscanResultDTO] = jsonFormat2(EtherscanResultDTO)

  case class EtherscanResultDTO(message: String, result: String) {
    def getEthValue: Double = {
      val weiValue = result.toDouble
      weiValue / Math.pow(10, 18)
    }
  }

  override def chainName: String = "ethereum"
  override def getAmountInAddress(address: String): Future[Double] = {
    val apiKey = "1MV97W7Q96TX2AIFZGP8UTRQDS3MAYGGYJ" // TODO: remove
    val uri = s"https://api.etherscan.io/api?module=account&action=balance&address=$address&tag=latest&apikey=$apiKey"
    val apiResp: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = uri))
    apiResp.flatMap { resp =>
      val result: Future[EtherscanResultDTO] = Unmarshal(resp).to[EtherscanResultDTO]
      result.map(_.getEthValue)
    }
  }

}
