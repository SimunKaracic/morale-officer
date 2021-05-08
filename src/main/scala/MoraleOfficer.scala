import ammonite.ops.home
import com.typesafe.config.ConfigFactory
import io.getquill.{JdbcContextConfig, Ord, SnakeCase, SqliteZioJdbcContext}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import zio.blocking.effectBlocking
import zio.console.putStrLn
import zio.duration.durationInt
import zio.{Schedule, Task, ZIO, ZLayer, ZManaged}

import java.awt.Desktop
import java.io.IOException
import java.net.URI
import java.time.LocalDateTime
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.control.NonFatal

object MoraleOfficer extends zio.App {

  override def run(args: List[String]) = (for {
    _ <- initializeDb()
    newPosts <- constructPostList()
    _ <- ZIO.foreachPar_(newPosts)(update_and_open)
  } yield ()).exitCode

  private def constructPostList() = {
    getTop5UnopenedPosts.flatMap(posts =>
      if (posts.isEmpty || posts.length < 5) {
        ZIO.fail(posts)
      } else {
        ZIO.succeed(posts)
      })
      .orElse(scrapeAndSave)
  }

  private def scrapeAndSave = {
    for {
      posts <- ZIO.foreachParN(4)(CatSubs.list) { sub =>
        putStrLn(s"Gathering posts from ${sub}").zipRight(extract_posts(sub))
      }
      newPosts <- ZIO.filter(posts.flatten.filter(_.upvotes > 100)) { post =>
        getPostByUrl(post).flatMap(p => ZIO.succeed(p.isEmpty))
      }
      _ <- ZIO.foreach_(newPosts)(insertPost)
    } yield newPosts.sortBy(_.upvotes)(Ordering.Int.reverse).take(5)
  }

  private def update_and_open(post: Post) = {
    updatePostWithOpenedTime(post) *> open_in_browser(post.url)
  }

  ///////////////
  // filthy stuff
  ///////////////

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

  //////////////////////////
  // DB SETTINGS AND QUERIES
  //////////////////////////
  object SqliteContext extends SqliteZioJdbcContext(SnakeCase)
  import SqliteContext._

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

  case class Post(url: String, upvotes: Int, scraped_at: LocalDateTime, opened_at: Option[LocalDateTime] = None)
  object Post {
    def withOpenedTime(p: Post) = {
      Post(p.url, p.upvotes, p.scraped_at, Some(LocalDateTime.now))
    }
  }


  def getTop5UnopenedPosts: ZIO[zio.ZEnv, Throwable, List[Post]] = {
    val q = quote {
      query[Post]
        .filter(_.opened_at.isEmpty)
        .sortBy(_.upvotes)(Ord.descNullsLast)
        .distinct.take(5)
    }
    SqliteContext.run(q).provideCustomLayer(connection)
  }

  def updatePostWithOpenedTime(post: Post) = {
    val q = quote {
      query[Post].filter(_.url == lift(post.url)).update(_.opened_at -> lift(Option(LocalDateTime.now())))
    }
    SqliteContext.run(q).provideCustomLayer(connection)
  }

  def insertPost(post: Post) = {
    SqliteContext.run(query[Post].insert(lift(post)).onConflictIgnore).provideCustomLayer(connection)
  }

  def getPostByUrl(post: Post): ZIO[zio.ZEnv, Throwable, List[Post]] = {
    val q = quote {
      query[Post].filter(p => lift(post.url) == p.url)
    }
    SqliteContext.run(q).provideCustomLayer(connection)
  }

  def unopenedPosts() = {
    val q = quote {
      query[Post].filter(_.opened_at.isEmpty).sortBy(_.upvotes)(Ord.descNullsLast)
    }
    SqliteContext.run(q).provideCustomLayer(connection)
  }

  def deletePost(post: Post) = {
    val q = quote {
      query[Post].filter(_.url == lift(post.url)).delete
    }
    SqliteContext.run(q).provideCustomLayer(connection)
  }

  def deleteOldPosts(): ZIO[zio.ZEnv, Throwable, Unit] = {
    for {
      unOpenedPosts <- unopenedPosts()
      deleteablePosts <- ZIO.filter(unOpenedPosts) { p =>
        ZIO.succeed(p.opened_at.exists(_.isBefore(LocalDateTime.now().minus(8.hours))))
      }
      _ <- ZIO.foreach_(deleteablePosts)(deletePost)
    } yield ()
  }

  def initializeDb() = {
    SqliteContext.executeAction(
      """CREATE TABLE IF NOT EXISTS "POST"(
        | url varchar NOT NULL UNIQUE,
        | upvotes integer NOT NULL,
        | scraped_at time NOT NULL,
        | opened_at time)""".stripMargin).provideCustomLayer(connection) *>
      deleteOldPosts()
  }
}

