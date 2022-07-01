import model.{Post, PostContext}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import zio.Console.printLine
import zio.{Schedule, ZIO, durationInt}

import java.io.IOException
import java.time.LocalDateTime
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.chaining.scalaUtilChainingOps
import scala.util.control.NonFatal

object ScrapingService {
  def fetchNewPosts = {
    for {
      _ <- printLine("getting new posts")
      posts <- ZIO.foreachPar(CatSubs.list) { sub => {
        for {
          _ <- printLine("seinding request")
          document <- getHtml(sub)
          _ <- printLine(s"got response")
        } yield posts_from_document(document, sub)
      }
      }.flatMap(x => ZIO.succeed(x.flatten))

      newPosts <- ZIO.filter(posts) { post =>
        PostContext.getPostByUrl(post)
          .flatMap(p => ZIO.succeed(p.isEmpty))
      }
      _ <- PostContext.insertPosts(newPosts)
    } yield newPosts
  }

  private def posts_from_document(document: Document, subreddit: String): List[Post] = {
    val posts = document.getElementsByClass("thing").asScala
      .flatMap(element => {
        val title = element.select(".title").text().toLowerCase
        val link = element.select("a.bylink.comments[href]").attr("href")
        val upvotes = element.select("div.score.unvoted").text()
          .replace(".", "")
          .replace("k", "000")
          .pipe(upvotesToInt)

        if (upvotes.exists(_ < 300)) {
          None
        } else {
          upvotes.map(Post(link, subreddit, title, _, LocalDateTime.now()))
        }
      }).toList
    posts
  }

  private def upvotesToInt(s: String): Option[Int] = {
    try {
      Some(s.toInt)
    } catch {
      case NonFatal(_e) =>
        None
    }
  }
  private def getHtml(sub: String) = {
    val soup = Jsoup.connect(s"https://old.reddit.com/r/${sub}")
      .header("user-agent", "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:47.0) Gecko/20100101 Firefox/47.0")
    val retryPolicy = (Schedule.exponential(20.millis) && Schedule.recurs(20)).jittered

    ZIO.blocking(ZIO.attempt(soup.get()))
      .refineToOrDie[IOException]
      .retry(retryPolicy)
  }
}
