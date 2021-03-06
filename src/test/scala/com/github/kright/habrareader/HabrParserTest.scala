package com.github.kright.habrareader

import com.github.kright.habrareader.loaders.HabrArticlesDownloader
import com.github.kright.habrareader.utils.DateUtils
import org.scalatest.{FunSuite, Ignore}


@Ignore
class HabrParserTest extends FunSuite {
  test("loding rss articles") {
    val articles = HabrArticlesDownloader.downloadRSSArticles
    articles.foreach(println(_))
  }

  test("html parsing") {
    // Malformed input for articles:
    // https://habr.com/ru/post/465703/
    // todo test this
    val article = HabrArticlesDownloader.downloadArticle("https://habr.com/ru/post/465703/", DateUtils.now)
    println(article)
  }

  test("download articles") {
    val article = HabrArticlesDownloader.getArticles()
    article.foreach(println)
  }
}
