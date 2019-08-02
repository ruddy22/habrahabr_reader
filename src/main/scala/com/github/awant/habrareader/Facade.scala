package com.github.awant.habrareader

import cats.instances.future._
import cats.syntax.functor._
import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.api.declarative.Commands
import com.bot4s.telegram.clients.FutureSttpClient
import com.bot4s.telegram.future.{Polling, TelegramBot}
import slogging.{LogLevel, LoggerConfig, PrintLoggerFactory}
import scala.concurrent.Future

class Facade(val token: String) extends TelegramBot with Polling with Commands[Future] {
  LoggerConfig.factory = PrintLoggerFactory()
  LoggerConfig.level = LogLevel.TRACE

  implicit val backend = SttpBackends.default
  override val client: RequestHandler[Future] = new FutureSttpClient(token)

  onCommand("new_articles") { implicit msg =>
    reply("Take these articles").void
  }

  onMessage { implicit msg =>
    reply("echo: " + msg.text).void
  }

}
