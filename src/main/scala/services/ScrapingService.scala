package services

import data.{AnimalSubreddits, Post}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import zio.{Schedule, ZIO, ZLayer, durationInt}

import java.io.IOException
import java.time.LocalDateTime
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.chaining.scalaUtilChainingOps
import scala.util.control.NonFatal

case class ScrapingService(postService: PostService) {
  def updatePostsDatabase(subreddits: List[String] = AnimalSubreddits.list) =
    for {
      posts <- extractPostsFromSubs(subreddits)
      _     <- postService.insertPosts(posts)
    } yield ()

  private def extractPostsFromSubs(subreddits: List[String]) =
    for {
      posts <- ZIO.foreachPar(subreddits) { sub =>
                 for {
                   document <- getHtml(sub)
                 } yield posts_from_document(document, sub)
               }
    } yield posts.flatten

  private def posts_from_document(document: Document, subreddit: String): List[Post] = {
    val posts = document
      .getElementsByClass("thing")
      .asScala
      .flatMap { element =>
        val title = element.select(".title").text()
        val link  = element.select("a.bylink.comments[href]").attr("href")
        val upvotes = element
          .select("div.score.unvoted")
          .text()
          .replace(".", "")
          .replace("k", "000")
          .pipe(upvotesToInt)

        if (upvotes.exists(_ < 200)) {
          None
        } else {
          upvotes.map(Post(link, subreddit, title, _, LocalDateTime.now()))
        }
      }
      .toList
    posts
  }

  private def upvotesToInt(s: String): Option[Int] =
    try Some(s.toInt)
    catch {
      case NonFatal(_e) =>
        None
    }

  private def getHtml(sub: String) = {
    val soup = Jsoup
      .connect(s"https://old.reddit.com/r/${sub}")
      .header("user-agent", "neelix.Neelix")
    val retryPolicy = (Schedule.exponential(20.millis) && Schedule.recurs(20)).jittered

    ZIO
      .blocking(ZIO.attempt(soup.get()))
      .refineToOrDie[IOException]
      .retry(retryPolicy)
  }
}

object ScrapingService {
  val live: ZLayer[PostService, Nothing, ScrapingService] =
    ZLayer.fromFunction(apply _)
}
