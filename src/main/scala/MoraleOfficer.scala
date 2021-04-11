import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import sttp.client3.{RequestT, UriContext, basicRequest}
import zio.blocking.{Blocking, effectBlocking}
import zio.clock.Clock
import zio.console.putStrLn
import zio.{ExitCode, Schedule, URIO, ZIO, random}
import zio.duration.durationInt

import java.awt.Desktop
import java.io.IOException
import java.net.URI
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.Random

object MoraleOfficer extends zio.App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = (for {
    _ <- putStrLn("Checking out subs...")
    subs <- ZIO.foreachPar(catSubs) { sub =>
      get_subreddit(sub).zipLeft(putStrLn(s"Gathering posts for ${sub}"))
    }
    _ <- putStrLn("Gathering cattiest posts")
    posts = subs.flatMap(extract_top_posts).toSet
    _ <- putStrLn("Frantically opening browser tabs so you don't see the errors in the output")
    _ <- ZIO.foreachPar_(posts) { post =>
      open_in_browser(s"https://old.reddit.com${post}")
    }
    _ <- putStrLn("Morale successfully officered!")
  } yield ()).exitCode

  private def get_subreddit(sub: String) = {
    get_html(s"https://old.reddit.com/r/${sub}")
  }

  private def extract_top_posts(doc: Document) = {
    val thumbnails = doc
      .getElementsByAttributeValue("data-event-action", "thumbnail")

    val links = (for (tn <- thumbnails.asScala if !tn.html().contains("- announcement")) yield tn.attr("href"))
      .filter(x => x.contains("/r/"))
      .filter(x => x.contains("comments"))
      .slice(1, 3)

    links
  }

  private def open_in_browser(link: String) = {
    effectBlocking(
      if (Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop.browse(new URI(link))
      }).refineToOrDie[IOException]
      .retry(Schedule.recurs(10) && Schedule.exponential(100.millis))
      .orDie
  }

  private def get_html(url: String): URIO[Any with random.Random with Blocking with Clock, Document] = {
    val doc = Jsoup.connect(url)
      .header("user-agent", "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:47.0) Gecko/20100101 Firefox/47.0")

    effectBlocking(doc.get())
      .refineToOrDie[IOException]
      .delay(Random.nextInt(3).seconds)
      .retry((Schedule.exponential(100.millis) && Schedule.recurs(20)).jittered)
      .orDie
  }

  private val catSubs = List("CatLoaf",
    "Floof",
    "Blep",
    "CatsStandingUp",
    "CatBellies",
    "DelightfullyChubby",
    "Meow_Irl",
    "Cats",
    "CatGifs",
    "CatsWhoYell",
    "TuckedInKitties",
    "BabyBigCatGifs",
    "BigCats",
    "catdimension",
  )
}
