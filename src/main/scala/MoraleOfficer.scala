import zio.ZIO
import zio.console.putStrLn
import zio.duration.durationInt

object MoraleOfficer extends zio.App {

  override def run(args: List[String]) = (for {
    _ <- PostContext.initializeDb()
    newPosts <- constructPostList()
    _ <- ZIO.foreachPar_(newPosts)(update_and_open)
    _ <- if (newPosts.isEmpty) {
      putStrLn("There are no more quality cats, please wait until more content is generated").delay(2.seconds)
    } else {
      putStrLn("Morale successfully officered!").delay(2.seconds)
    }
    _ <- putStrLn("")
  } yield ()).exitCode

  private def constructPostList() = {
    PostContext.getTop5UnopenedPosts.flatMap(posts =>
      if (posts.isEmpty || posts.length < 5) {
        ZIO.fail(posts)
      } else {
        ZIO.succeed(posts)
      })
      .orElse(ScrapingService.fetchNewPosts *> PostContext.getTop5UnopenedPosts)
  }

  private def update_and_open(post: Post) = {
    PostContext.updatePostWithOpenedTime(post) *> BrowserService.open(post.url)
  }
}

