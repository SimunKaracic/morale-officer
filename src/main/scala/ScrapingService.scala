import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import zio.blocking.effectBlocking
import zio.console.putStrLn
import zio.duration.durationInt
import zio.{Schedule, ZIO}

import java.io.IOException
import java.time.LocalDateTime
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.chaining.scalaUtilChainingOps
import scala.util.control.NonFatal

object ScrapingService {
  def fetchNewPosts = {
    for {
      posts <- ZIO.foreachParN(4)(CatSubs.list) { sub =>
        putStrLn(s"Gathering posts from ${sub}").zipRight(extract_posts(sub))
      }.flatMap(x => ZIO.succeed(x.flatten))

      newPosts <- ZIO.filter(posts) { post =>
        PostContext.getPostByUrl(post)
          .flatMap(p => ZIO.succeed(p.isEmpty))
      }
      _ <- PostContext.insertPosts(newPosts)
    } yield newPosts
  }

  // sadpost filtering is getting out of hand
  // it should be it's own service, and as I find titles
  // i should add them to a test
  // first, write some tests i guess
  private def sadPostKeywords = List(
    "dead", "mangy", "dying",
    "mourning", "sick",
    "disabled", "tumor", "RIP",
    "cancer",
  ).map(_.toLowerCase)

  private def sadPostPhrases = List(
    "still cute", "put down", "some love",
    "antibiotics", "rainbow bridge",
    "positive vibes", "passed away",
    "urinary blockage",
  ).map(_.toLowerCase)

  private def posts_from_document(doc: Document): List[Post] = {
    // maybe i wanna track which ones were filtered out by sadposting
    val posts = doc.getElementsByClass("thing").asScala
      .flatMap(element => {
        val title = element.select(".title").text().toLowerCase
        val link = element.select("a.bylink.comments[href]").attr("href").toString
        val upvotes = element.select("div.score.unvoted").text()
          .replace(".", "")
          .replace("k", "000")
          .pipe(upvotesToInt)

        val titleWords = title.split(" ").map(_.toLowerCase)
        if (sadPostPhrases.exists(title.contains)
          || titleWords.intersect(sadPostKeywords).nonEmpty
          || upvotes.exists(_ < 300)) {
          None
        } else {
          upvotes.map(Post(link, _, LocalDateTime.now()))
        }
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
