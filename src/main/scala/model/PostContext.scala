package model

import io.getquill.{Ord, SnakeCase, SqliteZioJdbcContext}
import zio.{ZIO, durationInt}

import java.sql.SQLException
import java.time.LocalDateTime
import javax.sql.DataSource

case class Post(url: String, subreddit: String, title: String,
                upvotes: Int, scraped_at: LocalDateTime,
                opened_at: Option[LocalDateTime] = None)

object PostContext {
  import DbContext.ctx._

  def getTop10UnopenedPosts = {
    val q = quote(
      query[Post]
        .filter(_.opened_at.isEmpty)
        .sortBy(_.upvotes)(Ord.descNullsLast)
        .distinct.take(10)
    )
    run(q)
  }

  def updatePostWithOpenedTime(post: Post) = {
    val q = quote(
      query[Post].filter(_.url == lift(post.url)).update(_.opened_at -> lift(Option(LocalDateTime.now())))
    )
    run(q)
  }


  def getPostByUrl(post: Post) = {
    val q = quote(
      query[Post].filter(p => lift(post.url) == p.url)
    )
    run(q)
  }

  def insertPosts(posts: List[Post]) = {
    val q = quote {
      liftQuery(posts).foreach(p => query[Post].insertValue(p).onConflictIgnore)
    }
    run(q)
  }

  private def unopenedPosts() = {
    val q = quote {
      query[Post]
        .filter(_.opened_at.isEmpty)
        .sortBy(_.upvotes)(Ord.descNullsLast)
    }
    run(q)
  }

  private def deletePosts(posts: List[Post]) = {
    val q = quote {
      liftQuery(posts).foreach(p => query[Post].filter(_.url == p.url).delete)
    }
    run(q)
  }

  def deleteOldUnopenedPosts(someTimeAgo: LocalDateTime = LocalDateTime.now().minus(8.hours)): ZIO[DataSource, SQLException, Unit] = {
    for {
      unOpenedPosts <- unopenedPosts()
      deletablePosts = unOpenedPosts.filter(_.scraped_at.isBefore(someTimeAgo))
      _ <- deletePosts(deletablePosts)
    } yield ()
  }


}
