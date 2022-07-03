package services

import data.Post
import zio.ZLayer

import java.time.LocalDateTime



case class PostService() {
  import SqliteService.ctx._

  def getUnopenedPostCounts = {
    val q = quote {
      query[Post]
        .filter(_.opened_at.isEmpty)
        .groupBy(_.subreddit)
        .map { case (name, posts) => (name, posts.size) }
    }

    run(q).provide(SqliteService.live)
  }

  def getUnopenedPostsForSubreddits(subreddits: List[String]) = {
    val q = quote {
      query[Post]
        .filter(_.opened_at.isEmpty)
        .filter(post => liftQuery(subreddits).contains(post.subreddit))
        .distinct
        .take(10)
    }
    run(q).provide(SqliteService.live)
  }

  def updatePostWithOpenedTime(post: Post) = {
    val q = quote {
      query[Post].filter(_.url == lift(post.url)).update(_.opened_at -> lift(Option(LocalDateTime.now())))
    }
    run(q).provide(SqliteService.live)
  }

  def insertPosts(posts: List[Post]) = {
    val q = quote {
      liftQuery(posts).foreach(p => query[Post].insertValue(p).onConflictIgnore)
    }
    run(q).provide(SqliteService.live)
  }

}

object PostService {
  val live: ZLayer[Any, Nothing, PostService] =
    ZLayer.fromFunction(apply _)
}
