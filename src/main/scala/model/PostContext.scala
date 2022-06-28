package model

import ammonite.ops.home
import com.typesafe.config.ConfigFactory
import io.getquill.{JdbcContextConfig, Ord, SnakeCase, SqliteZioJdbcContext}
import zio.blocking.Blocking
import zio.duration.durationInt
import zio.{Has, Task, ZIO, ZLayer, ZManaged}

import java.sql.{Connection, SQLException}
import java.time.LocalDateTime

case class Post(url: String, subreddit: String,
                upvotes: Int, scraped_at: LocalDateTime,
                opened_at: Option[LocalDateTime] = None)
object PostContext extends SqliteZioJdbcContext(SnakeCase) {
  def getTop10UnopenedPosts: ZIO[Has[Connection] with Blocking, SQLException, List[Post]] = {
    val q = quote {
      query[Post]
        .filter(_.opened_at.isEmpty)
        .sortBy(_.upvotes)(Ord.descNullsLast)
        .distinct.take(10)
    }
    this.run(q)
  }

  def updatePostWithOpenedTime(post: Post) = {
    val q = quote {
      query[Post].filter(_.url == lift(post.url)).update(_.opened_at -> lift(Option(LocalDateTime.now())))
    }
    this.run(q)
  }


  def getPostByUrl(post: Post): ZIO[Has[Connection] with Blocking, SQLException, List[Post]] = {
    val q = quote {
      query[Post].filter(p => lift(post.url) == p.url)
    }
    this.run(q)
  }

  def insertPosts(posts: List[Post]) = {
    this.run(liftQuery(posts).foreach(p => query[Post].insert(p).onConflictIgnore))
  }

  private def unopenedPosts() = {
    val q = quote {
      query[Post]
        .filter(_.opened_at.isEmpty)
        .sortBy(_.upvotes)(Ord.descNullsLast)
    }
    this.run(q)
  }

  private def deletePosts(posts: List[Post]) = {
    this.run(liftQuery(posts).foreach(p => query[Post].filter(_.url == p.url).delete))
  }

  def deleteOldUnopenedPosts(someTimeAgo: LocalDateTime = LocalDateTime.now().minus(8.hours)) = {
    for {
      unOpenedPosts <- unopenedPosts()
      deletablePosts = unOpenedPosts.filter(_.scraped_at.isBefore(someTimeAgo))
      _ <- deletePosts(deletablePosts)
    } yield ()
  }


}
