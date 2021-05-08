import ammonite.ops.home
import com.typesafe.config.ConfigFactory
import io.getquill.{JdbcContextConfig, SnakeCase, SqliteZioJdbcContext}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import zio.blocking.effectBlocking
import zio.console.putStrLn
import zio.duration.durationInt
import zio.{Schedule, Task, ZIO, ZLayer, ZManaged}

import java.awt.Desktop
import java.io.IOException
import java.net.URI
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.control.NonFatal

object MoraleOfficer extends zio.App {
  object SqliteContext extends SqliteZioJdbcContext(SnakeCase)

  import SqliteContext._

  override def run(args: List[String]) = (for {
    _ <- initializeDb()
    posts <- ZIO.foreachParN(10)(CatSubs.list) { sub =>
      putStrLn(s"Gathering posts from ${sub}").zipRight(extract_posts(sub))
    }.flatMap(pp => ZIO.succeed(pp.flatten))

    // wow, this is not good
    // I really need more practice with this
    newPosts <- ZIO.filter(posts) { post =>
      getPostByUrl(post)
        .flatMap(p => ZIO.succeed(p.isEmpty))
    }.flatMap(np => ZIO.succeed(np.sortBy(_.upvotes)(Ordering.Int.reverse).take(5)))

    _ <- ZIO.foreachPar_(newPosts)(open_post)
    _ <- putStrLn("Morale successfully officered!")
  } yield ()).exitCode


  private def open_post(post: Post) = {
    insertPost(post) *> open_in_browser(post.link)
  }

  private def open_in_browser(link: String) = {
    effectBlocking(
      if (Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop.browse(new URI(link))
      }).refineToOrDie[IOException]
      .retry(Schedule.recurs(30) && Schedule.spaced(150.millis))
      .orElse(putStrLn(s"Failed to open $link in browser"))
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

        upvotesToInt(upvotes).map(Post(link, _))
      }).toList
    // get rid of this toList

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

  //////////////////////////
  // DB SETTINGS AND QUERIES
  //////////////////////////

  private val connection = {
    // config defined here because
    // i cant figure out relative file paths in the conf
    val conf = ConfigFactory.parseString(
      s"""
         | db {
         |   driverClassName=org.sqlite.JDBC
         |   jdbcUrl="jdbc:sqlite:${home}/.neelix.db"
         | }
    """.stripMargin)

    ZLayer.fromManaged(for {
      ds <- ZManaged.fromAutoCloseable(Task(JdbcContextConfig(conf.getConfig("db")).dataSource))
      conn <- ZManaged.fromAutoCloseable(Task(ds.getConnection))
    } yield conn)
  }

  case class Post(link: String, upvotes: Int)

  def getPostByUrl(post: Post) = {
    val q = quote {
      query[Post].filter(p => lift(post.link) == p.link)
    }
    SqliteContext.run(q)
      .provideCustomLayer(connection)
  }

  def insertPost(post: Post) = {
    SqliteContext.run(query[Post].insert(lift(post))).provideCustomLayer(connection)
  }

  def initializeDb() = {
    SqliteContext.executeAction(
      """CREATE TABLE IF NOT EXISTS "POST"(
        | link varchar NOT NULL UNIQUE,
        | upvotes integer NOT NULL)""".stripMargin).provideCustomLayer(connection)
  }
}
