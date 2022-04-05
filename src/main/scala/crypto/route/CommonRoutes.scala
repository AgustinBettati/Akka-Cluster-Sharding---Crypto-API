package crypto.route

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.ExceptionHandler
import akka.pattern.AskTimeoutException
import akka.util.Timeout

abstract class CommonRoutes(system: ActorSystem[_]) {


  // If ask takes more time than this to complete the request is failed
  implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))

  implicit val myExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case _: AskTimeoutException =>
        complete(HttpResponse(InternalServerError, entity = "ask timeout exception"))
    }

}
