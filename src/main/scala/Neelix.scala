import zio.{Chunk, ZIO, ZLayer}

import scala.util.Random

case class Neelix(postContext: PostService, scrapingService: ScrapingService, browserService: BrowserService) {
  def constructSubredditStatistics: ZIO[Any, Throwable, Chunk[SubredditDataSummary]] = {
    for {
      postCounts <- postContext.getUnopenedPostCounts
    } yield {
      Chunk.fromArray(
        postCounts
          .map(pc => SubredditDataSummary(name = pc._1, postCount = pc._2))
          .toArray
      )
    }
  }

  def fetchImages = for {
     _ <- scrapingService.updatePostsDatabase()
  } yield ()

  def openForSubreddits(subreddits: List[String]) = {
    for {
      unopenedPosts <- postContext.getUnopenedPostsForSubreddits(subreddits)
      unopenedShuffled = Random.shuffle(unopenedPosts).take(Random.between(4, 9))
      _ <- ZIO.foreach(unopenedShuffled) { post =>
        updateAndOpen(post)
      }
    } yield ()
  }

  private def updateAndOpen(post: Post) = {
    postContext.updatePostWithOpenedTime(post) *> browserService.open(post.url)
  }
}

object Neelix {
  val live: ZLayer[PostService with ScrapingService with BrowserService, Nothing, Neelix] =
    ZLayer.fromFunction(apply _)
}
