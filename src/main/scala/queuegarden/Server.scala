package queuegarden

import cats.effect.{ ConcurrentEffect, ContextShift, ExitCode, Timer }
import cats.implicits._
import fs2.Stream
import org.http4s.HttpRoutes
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import queuegarden.config.ServerConfig
import routes.{ Login, ParamPageTest, Welcome }

import scala.concurrent.ExecutionContext.global

class Server(config: ServerConfig) {

  def stream[F[_]: ConcurrentEffect](
      otherRoutes: HttpRoutes[F]
    )(implicit
      T: Timer[F],
      C: ContextShift[F]
    ): Stream[F, ExitCode] =
    for {
      client       <- BlazeClientBuilder[F](global).stream
      helloWorldAlg = HelloWorld.impl[F]
      jokeAlg       = Jokes.impl[F](client)

      // Combine Service Routes into an HttpApp.
      // Can also be done via a Router if you
      // want to extract a segments not checked
      // in the underlying routes.
      httpApp      = (
                       otherRoutes <+>
                         ParamPageTest.route[F] <+>
                         Routes.helloWorldRoutes[F](helloWorldAlg) <+>
                         Routes.jokeRoutes[F](jokeAlg) <+>
                         Login.route[F] <+>
                         Welcome.routes[F]
                     ).orNotFound

      // With Middlewares in place
      finalHttpApp = Logger.httpApp(true, true)(httpApp)

      exitCode <- BlazeServerBuilder[F](global)
                    .bindHttp(config.port, "0.0.0.0")
                    .withHttpApp(finalHttpApp)
                    .serve
    } yield exitCode

}
