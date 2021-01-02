import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import zio.blocking.effectBlocking
import zio.console.putStrLn
import zio.{ExitCode, Schedule, URIO, ZIO}
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
      get_subreddit(sub)
    }
    _ <- putStrLn("Gathering cattiest posts")
    posts = subs.flatMap(extract_top_posts).toSet
    _ <- putStrLn("Frantically opening browser tabs so you don't see the errors in the output")
    _ <- ZIO.foreachPar(posts) { post =>
      open_in_browser(s"https://old.reddit.com${post}")
    }
    _ <- putStrLn("Morale successfully officered!")
  } yield ()).exitCode

  def get_subreddit(sub: String) = {
    get_html(s"https://old.reddit.com/r/${sub}")
  }

  def extract_top_posts(doc: Document) = {
    val thumbnails = doc
      .getElementsByAttributeValue("data-event-action", "thumbnail")
    val links = (for (tn <- thumbnails.asScala if !tn.html().contains("- announcement")) yield tn.attr("href"))
      .filter(x => x.contains("/r/"))
      .filter(x => x.contains("comments"))
      .slice(1, 3)

    links
  }

  def open_in_browser(link: String) = {
    effectBlocking(
      if (Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop.browse(new URI(link))
      }).refineToOrDie[IOException]
      .retry(Schedule.recurs(10) && Schedule.exponential(100.millis))
      .orDie
  }

  def get_html(url: String) = {
    val doc = Jsoup.connect(url)
      .header("user-agent", "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:47.0) Gecko/20100101 Firefox/47.0").get()

    effectBlocking(doc).refineToOrDie[IOException]
      .delay(Random.nextInt(3).seconds)
      .retry((Schedule.exponential(100.millis) && Schedule.recurs(20)).jittered)
      .orDie
  }
  val catSubs: List[String] = List("CatLoaf",
    "Floof",
    "Blep",
    "CatsStandingUp",
    "CatBellies",
    "DelightfullyChubby",
    "CatsLookingSeductive",
    "CatConspiracy",
    "CatLogic",
    "Meow_Irl",
    "Cats",
    "CatSpotting",
    "CatGifs",
    "KittenGifs",
    "CatVideos",
    "CatsWhoYell",
    "TuckedInKitties",
    "NorwegianForestCats",
    "BabyBigCatGifs",
    "BigCats",
  )
}
