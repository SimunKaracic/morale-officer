import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import zio.blocking.effectBlocking
import zio.console.putStrLn
import zio.duration.durationInt
import zio.{Schedule, ZIO}

import java.io.IOException
import scala.util.control.NonFatal
import java.time.LocalDateTime
import scala.jdk.CollectionConverters.CollectionHasAsScala

object ScrapingService {
  private def sadPostsKeywords = List("dead", "dying", "mourning", "sick", "pass", "still_cute", "put_down", "some_love")
  def scrapeAndSave = {
    for {
      posts <- ZIO.foreachParN(4)(CatSubs.list) { sub =>
        putStrLn(s"Gathering posts from ${sub}").zipRight(extract_posts(sub))
      }
      validPosts = posts.flatten.filter(_.upvotes > 300)
        .filterNot(p => {
          sadPostsKeywords.exists(p.url.contains)
        })
      newPosts <- ZIO.filter(validPosts) { post =>
        PostContext.getPostByUrl(post).flatMap(p => ZIO.succeed(p.isEmpty))
      }
      _ <- ZIO.foreach_(newPosts)(PostContext.insertPost)
    } yield newPosts.sortBy(_.upvotes)(Ordering.Int.reverse).take(5)
  }

  private def posts_from_document(doc: Document): List[Post] = {
    val posts = doc.getElementsByClass("thing").asScala
      .flatMap(x => {
        val upvotes = x.select("div.score.unvoted").text()
          .replace(".", "")
          .replace("k", "000")

        val link = x.select("a.bylink.comments[href]").attr("href").toString

        upvotesToInt(upvotes).map(Post(link, _, LocalDateTime.now()))
      }).toList
    posts
  }

  private def extract_posts(sub: String) = {
    val doc = Jsoup.connect(s"https://old.reddit.com/r/${sub}")
      .header("user-agent", "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:47.0) Gecko/20100101 Firefox/47.0")
    val retryPolicy = (Schedule.exponential(20.millis) && Schedule.recurs(20)).jittered

    effectBlocking(doc.get())
      .refineToOrDie[IOException]
      .retry(retryPolicy)
      .map(posts_from_document)
      .orElse(putStrLn(s"Couldn't get posts from ${sub}").as(List.empty[Post]))
  }

  private def upvotesToInt(s: String): Option[Int] = {
    try {
      Some(s.toInt)
    } catch {
      case NonFatal(e) =>
        None
    }
  }
}
