package sttp.tapir.server.akkahttp

import akka.http.scaladsl.server._
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.typelevel.ReplaceFirstInTuple

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

trait TapirAkkaHttpServer {
  implicit class RichAkkaHttpEndpoint[I, E, O](e: Endpoint[I, E, O, AkkaStream])(implicit serverOptions: AkkaHttpServerOptions) {
    def toDirective: Directive[(I, Future[Either[E, O]] => Route)] =
      new EndpointToAkkaServer(serverOptions).toDirective(e)

    def toRoute(logic: I => Future[Either[E, O]]): Route =
      new EndpointToAkkaServer(serverOptions).toRoute(e.serverLogic(logic))

    def toRouteRecoverErrors(
        logic: I => Future[O]
    )(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): Route = {
      new EndpointToAkkaServer(serverOptions).toRoute(e.serverLogicRecoverErrors(logic))
    }
  }

  implicit class RichAkkaHttpServerEndpoint[I, E, O](serverEndpoint: ServerEndpoint[I, E, O, AkkaStream, Future])(implicit
      serverOptions: AkkaHttpServerOptions
  ) {
    def toDirective: Directive[(I, Future[Either[E, O]] => Route)] =
      new EndpointToAkkaServer(serverOptions).toDirective(serverEndpoint.endpoint)

    def toRoute: Route = new EndpointToAkkaServer(serverOptions).toRoute(serverEndpoint)
  }

  implicit class RichAkkaHttpServerEndpoints(serverEndpoints: List[ServerEndpoint[_, _, _, AkkaStream, Future]])(implicit
      serverOptions: AkkaHttpServerOptions
  ) {
    def toRoute: Route = {
      new EndpointToAkkaServer(serverOptions).toRoute(serverEndpoints)
    }
  }

  implicit class RichToFutureFunction[T, U](a: T => Future[U])(implicit ec: ExecutionContext) {
    @deprecated
    def andThenFirst[U_TUPLE, T_TUPLE, O](
        l: U_TUPLE => Future[O]
    )(implicit replaceFirst: ReplaceFirstInTuple[T, U, T_TUPLE, U_TUPLE]): T_TUPLE => Future[O] = { tTuple =>
      val t = replaceFirst.first(tTuple)
      a(t).flatMap { u =>
        val uTuple = replaceFirst.replace(tTuple, u)
        l(uTuple)
      }
    }
  }

  implicit class RichToFutureOfEitherFunction[T, U, E](a: T => Future[Either[E, U]])(implicit ec: ExecutionContext) {
    @deprecated
    def andThenFirstE[U_TUPLE, T_TUPLE, O](
        l: U_TUPLE => Future[Either[E, O]]
    )(implicit replaceFirst: ReplaceFirstInTuple[T, U, T_TUPLE, U_TUPLE]): T_TUPLE => Future[Either[E, O]] = { tTuple =>
      val t = replaceFirst.first(tTuple)
      a(t).flatMap {
        case Left(e) => Future.successful(Left(e))
        case Right(u) =>
          val uTuple = replaceFirst.replace(tTuple, u)
          l(uTuple)
      }
    }
  }
}
