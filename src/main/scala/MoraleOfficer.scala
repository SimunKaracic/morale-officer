import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import zio.blocking.effectBlocking
import zio.console.putStrLn
import zio.duration.durationInt
import zio.{Schedule, ZIO}

import java.awt.Desktop
import java.io.IOException
import java.net.URI
import java.time.LocalDateTime
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.control.NonFatal

object MoraleOfficer extends zio.App {

  override def run(args: List[String]) = (for {
    _ <- PostContext.initializeDb()
    newPosts <- constructPostList()
    _ <- ZIO.foreachPar_(newPosts)(update_and_open)
    _ <- putStrLn("Morale successfully officered!").delay(2.seconds)
    _ <- putStrLn("")
  } yield ()).exitCode

  private def constructPostList() = {
    PostContext.getTop5UnopenedPosts.flatMap(posts =>
      if (posts.isEmpty || posts.length < 5) {
        ZIO.fail(posts)
      } else {
        ZIO.succeed(posts)
      })
      .orElse(scrapeAndSave)
  }

  private def sadPostsKeywords = List("dead", "dying", "sick", "pass", "still cute", "put down")
  private def scrapeAndSave = {
    for {
      posts <- ZIO.foreachParN(4)(CatSubs.list) { sub =>
        putStrLn(s"Gathering posts from ${sub}").zipRight(extract_posts(sub))
      }
      validPosts = posts.flatten.filter(_.upvotes > 100)
        .filterNot(p => {
          sadPostsKeywords.exists(p.url.contains)
        })
      newPosts <- ZIO.filter(validPosts) { post =>
        PostContext.getPostByUrl(post).flatMap(p => ZIO.succeed(p.isEmpty))
      }
      _ <- ZIO.foreach_(newPosts)(PostContext.insertPost)
    } yield newPosts.sortBy(_.upvotes)(Ordering.Int.reverse).take(5)
  }

  private def update_and_open(post: Post) = {
    PostContext.updatePostWithOpenedTime(post) *> open_in_browser(post.url)
  }

  ///////////////
  // filthy stuff
  ///////////////

  private def open_in_browser(link: String) = {
    val url = link.replace("/old.", "")
    effectBlocking(
      if (Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop.browse(new URI(url))
      }).refineToOrDie[IOException]
      .retry(Schedule.recurs(30) && Schedule.spaced(150.millis))
      .orElse(putStrLn(s"Failed to open $url in browser"))
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

  private def upvotesToInt(s: String): Option[Int] = {
    try {
      Some(s.toInt)
    } catch {
      case NonFatal(e) =>
        None
    }
  }

  case class Post(url: String, upvotes: Int, scraped_at: LocalDateTime, opened_at: Option[LocalDateTime] = None)

  object Post {
    def withOpenedTime(p: Post) = {
      Post(p.url, p.upvotes, p.scraped_at, Some(LocalDateTime.now))
    }
  }
}

