import ammonite.ops.home
import com.typesafe.config.ConfigFactory
import io.getquill.{JdbcContextConfig, Ord, SnakeCase, SqliteZioJdbcContext}
import zio.duration.durationInt
import zio.{Task, ZIO, ZLayer, ZManaged}

import java.time.LocalDateTime

case class Post(url: String, upvotes: Int, scraped_at: LocalDateTime, opened_at: Option[LocalDateTime] = None)
object PostContext extends SqliteZioJdbcContext(SnakeCase) {
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

  def getTop5UnopenedPosts: ZIO[zio.ZEnv, Throwable, List[Post]] = {
    val q = quote {
      query[Post]
        .filter(_.opened_at.isEmpty)
        .sortBy(_.upvotes)(Ord.descNullsLast)
        .distinct.take(5)
    }
    this.run(q).provideCustomLayer(connection)
  }

  // still gotta bulkify this
  def updatePostWithOpenedTime(post: Post): ZIO[zio.ZEnv, Throwable, Long] = {
    val q = quote {
      query[Post].filter(_.url == lift(post.url)).update(_.opened_at -> lift(Option(LocalDateTime.now())))
    }
    this.run(q).provideCustomLayer(connection)
  }


  def getPostByUrl(post: Post): ZIO[zio.ZEnv, Throwable, List[Post]] = {
    val q = quote {
      query[Post].filter(p => lift(post.url) == p.url)
    }
    this.run(q).provideCustomLayer(connection)
  }

  def insertPosts(posts: List[Post]) = {
    this.run(liftQuery(posts).foreach(p => query[Post].insert(p).onConflictIgnore))
      .provideCustomLayer(connection)
  }

  private def unopenedPosts() = {
    val q = quote {
      query[Post].filter(_.opened_at.isEmpty).sortBy(_.upvotes)(Ord.descNullsLast)
    }
    this.run(q).provideCustomLayer(connection)
  }

  private def deletePosts(posts: List[Post]) = {
    this.run(liftQuery(posts).foreach(p => query[Post].filter(_.url == p.url).delete))
      .provideCustomLayer(connection)
  }

  // now that's a type signature
  private def deleteOldUnopenedPosts(someTimeAgo: LocalDateTime = LocalDateTime.now().minus(8.hours)) = {
    for {
      unOpenedPosts <- unopenedPosts()
      deletablePosts <- ZIO.filter(unOpenedPosts) { p =>
        ZIO.succeed(p.scraped_at.isBefore(someTimeAgo))
      }
      _ <- deletePosts(deletablePosts)
    } yield ()
  }

  def initializeDb() = {
    this.executeAction(
      """CREATE TABLE IF NOT EXISTS "POST"(
        | url varchar NOT NULL UNIQUE,
        | upvotes integer NOT NULL,
        | scraped_at time NOT NULL,
        | opened_at time)""".stripMargin).provideCustomLayer(connection) *>
      deleteOldUnopenedPosts()
  }
}
