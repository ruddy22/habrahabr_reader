package com.github.kright.habrareader

import akka.actor.ActorSystem
import com.github.kright.habrareader.actors.{ArticlesUpdaterActor, LibraryActor, TgBotActor}
import com.github.kright.habrareader.models.StateSaverFS

import scala.concurrent.ExecutionContext


object HabraReader extends App {
  assert(AppConfig().tgbot.token.nonEmpty, "Empty bot token")

  val actorSystem = ActorSystem("system", AppConfig.asUntyped)

  implicit val ec: ExecutionContext = actorSystem.dispatcher

  val saver = new StateSaverFS(AppConfig().library.savesDir, actorSystem.log.debug(_))
  val libraryActor = actorSystem.actorOf(LibraryActor.props(AppConfig().library, saver), "library")
  val shopActor = actorSystem.actorOf(ArticlesUpdaterActor.props(AppConfig().articlesUpdater, libraryActor), "articlesUpdater")
  val tgBotActor = actorSystem.actorOf(TgBotActor.props(AppConfig().tgbot, libraryActor), "tgBot")
}
