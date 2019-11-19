package com.github.kright.habrareader.actors

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.github.kright.habrareader.AppConfig.LibraryActorConfig
import com.github.kright.habrareader.actors.LibraryActor._
import com.github.kright.habrareader.actors.TgBotActor.SendMessageToTg
import com.github.kright.habrareader.models._
import com.github.kright.habrareader.utils.{DateUtils, Saver}

import scala.concurrent.ExecutionContextExecutor


object LibraryActor {
  def props(config: LibraryActorConfig, saver: Saver[State]): Props = Props(new LibraryActor(config, saver))

  final case class PostWasSentToTg(chatId: Long, sentArticle: SentArticle)
  final case class GetSettings(chatId: Long)
  final case class UpdateChat(chatId: Long, updater: Chat => Chat, isSilent: Boolean = false)
  final case class RequestUpdates(chatId: Long)
  final case class RequestUpdatesForAll(updateExistingMessages: Boolean)
  final case class UpdateArticle(articles: HabrArticle)
  final case class SaveState(chatId: Option[Long] = None)
  final case class GetStats(chatId: Long)
  final case object GetArticles
  final case class AllArticles(articles: Iterable[HabrArticle])
}

class LibraryActor(config: LibraryActorConfig, saver: Saver[State]) extends Actor with ActorLogging {
  implicit val executionContext: ExecutionContextExecutor = context.dispatcher

  private val chatData = saver.load()
  private var chatDataLastTime: Date = DateUtils.now

  override def preStart(): Unit = {
    context.system.scheduler.schedule(config.stateSaveInterval, config.stateSaveInterval, self, SaveState())
  }

  override def receive: Receive = {
    case UpdateChat(chatId, updater, isSilent) =>
      chatData.updateChat(chatId)(updater)
      if (!isSilent) {
        sender ! SendMessageToTg(chatId, "ok")
      }
    case GetSettings(chatId) =>
      sender ! SendMessageToTg(chatId, chatData.getChat(chatId).getSettingsAsCmd)
    case RequestUpdatesForAll(updateExistingMessages) =>
      processNewPostSending(sender, updateExistingMessages)
    case UpdateArticle(article) =>
      chatData.updateArticle(article)
    case PostWasSentToTg(chatId, sentArticle) =>
      chatData.addSentArticle(chatId, sentArticle)
    case SaveState(chatIdOption) =>
      rmOldArticles()
      saver.save(chatData)
      chatIdOption.foreach { chatId =>
        sender ! SendMessageToTg(chatId, "saved!")
      }
    case RequestUpdates(chatId) =>
      requestUpdates(chatId, sender)
    case GetArticles =>
      sender ! AllArticles(chatData.articles.values.toVector)
    case GetStats(chatId) =>
      sender ! SendMessageToTg(chatId, getStatsMsg)
    case unknownMessage =>
      log.error(s"unknown message: $unknownMessage")
  }

  private def rmOldArticles(): Unit = {
    val threshold = DateUtils.now.getTime - config.timeBeforeRmOldArticles.toMillis
    rmArticles(chatData.articles.values.filter(_.publicationDate.getTime < threshold).map(_.id).toSeq)
  }

  private def rmArticles(idsToRm: Seq[Int]): Unit = {
    chatData.articles --= idsToRm

    chatData.chats.keys.foreach { id =>
      chatData.updateChat(id) { chat =>
        chat.copy(sentArticles = chat.sentArticles -- idsToRm)
      }
    }
  }

  private def getStatsMsg: String =
    s"""articles: <b>${chatData.articles.size}</b>
       |users: <b>${chatData.chats.size}</b>
       |subscribed users: <b>${chatData.chats.values.count(_.filterSettings.updateAsSoonAsPossible)}</b>
      """.stripMargin

  private def processNewPostSending(tgBot: ActorRef, updateExistingMessages: Boolean): Unit =
    for ((id, chat) <- chatData.chats) {
      if (chat.filterSettings.updateAsSoonAsPossible) {
        chatData.getNewArticles(id).foreach(tgBot ! _)
      }
      if (chat.filterSettings.updateAsSoonAsPossible && updateExistingMessages) {
        chatData.getSentArticleUpdates(id).foreach(tgBot ! _)
      }
    }

  private def requestUpdates(chatId: Long, tgBot: ActorRef): Unit = {
    val updates = chatData.getNewArticles(chatId)

    updates.view.take(3).foreach(tgBot ! _)

    if (updates.isEmpty) {
      tgBot ! SendMessageToTg(chatId, "no new articles :(")
    }
  }
}
