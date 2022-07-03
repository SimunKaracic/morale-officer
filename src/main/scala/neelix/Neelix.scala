package neelix

import data.{Post, UnopenedPostsForSubreddit}
import messages._
import services.{BrowserService, PostService, ScrapingService}
import zio.{Chunk, Queue, ZIO, ZLayer}

import scala.util.Random

case class Neelix(postService: PostService, scrapingService: ScrapingService, browserService: BrowserService) {
  def handleAppMessages(request: Request, appInputQueue: Queue[Response]) = request match {
    case RefreshData =>
      for {
        _          <- fetchImages
        statistics <- constructSubredditStatistics
        _          <- appInputQueue.offer(RefreshingStopped(statistics))
      } yield request
    case SubsChosen(animalSubs) =>
      for {
        _          <- openForSubreddits(animalSubs.map(_.name).toList)
        statistics <- constructSubredditStatistics
        _          <- appInputQueue.offer(UpdatedStats(statistics))
      } yield request
  }

  def constructSubredditStatistics: ZIO[Any, Throwable, Chunk[UnopenedPostsForSubreddit]] =
    for {
      postCounts <- postService.getUnopenedPostCounts
    } yield
    // Figure this out man
    Chunk.fromArray(
      postCounts
        .map(pc => UnopenedPostsForSubreddit(name = pc._1, postCount = pc._2))
        .toArray
    )

  private def fetchImages = for {
    _ <- scrapingService.updatePostsDatabase()
  } yield ()

  private def openForSubreddits(subreddits: List[String]) =
    for {
      unopenedPosts   <- postService.getUnopenedPostsForSubreddits(subreddits)
      unopenedShuffled = Random.shuffle(unopenedPosts).take(Random.between(4, 9))
      _ <- ZIO.foreachParDiscard(unopenedShuffled) { post =>
             updateAndOpen(post)
           }
    } yield ()

  private def updateAndOpen(post: Post) =
    postService.updatePostWithOpenedTime(post) *> browserService.open(post.url)
}

object Neelix {
  val live: ZLayer[PostService with ScrapingService with BrowserService, Nothing, Neelix] =
    ZLayer.fromFunction(apply _)
}
