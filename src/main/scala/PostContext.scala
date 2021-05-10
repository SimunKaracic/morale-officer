import ammonite.ops.home
import com.typesafe.config.ConfigFactory
import io.getquill.{JdbcContextConfig, Ord, SnakeCase, SqliteZioJdbcContext}
import zio.duration.durationInt
import zio.{Task, ZIO, ZLayer, ZManaged}

import java.time.LocalDateTime

case class Post(url: String, upvotes: Int, scraped_at: LocalDateTime, opened_at: Option[LocalDateTime] = None)

object Post {
  def withOpenedTime(p: Post): Post = {
    Post(p.url, p.upvotes, p.scraped_at, Some(LocalDateTime.now))
  }
}

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

  def updatePostWithOpenedTime(post: Post): ZIO[zio.ZEnv, Throwable, Long] = {
    val q = quote {
      query[Post].filter(_.url == lift(post.url)).update(_.opened_at -> lift(Option(LocalDateTime.now())))
    }
    this.run(q).provideCustomLayer(connection)
  }

  def insertPost(post: Post): ZIO[zio.ZEnv, Throwable, Long] = {
    this.run(query[Post].insert(lift(post)).onConflictIgnore).provideCustomLayer(connection)
  }

  def getPostByUrl(post: Post): ZIO[zio.ZEnv, Throwable, List[Post]] = {
    val q = quote {
      query[Post].filter(p => lift(post.url) == p.url)
    }
    this.run(q).provideCustomLayer(connection)
  }

  private def unopenedPosts() = {
    val q = quote {
      query[Post].filter(_.opened_at.isEmpty).sortBy(_.upvotes)(Ord.descNullsLast)
    }
    this.run(q).provideCustomLayer(connection)
  }

  private def deletePost(post: Post) = {
    val q = quote {
      query[Post].filter(_.url == lift(post.url)).delete
    }
    this.run(q).provideCustomLayer(connection)
  }

  private def deleteOldPosts() = {
    // filtering done in Scala because it
    // is horrible in Quill queries
    for {
      unOpenedPosts <- unopenedPosts()
      deletablePosts <- ZIO.filter(unOpenedPosts) { p =>
        ZIO.succeed(p.scraped_at.isBefore(LocalDateTime.now().minus(8.hours)))
      }
      _ <- ZIO.foreach_(deletablePosts)(deletePost)
    } yield ()
  }

  def initializeDb() = {
    this.executeAction(
      """CREATE TABLE IF NOT EXISTS "POST"(
        | url varchar NOT NULL UNIQUE,
        | upvotes integer NOT NULL,
        | scraped_at time NOT NULL,
        | opened_at time)""".stripMargin).provideCustomLayer(connection) *>
      deleteOldPosts()
  }
}
