import model.{DbContext, Post, PostContext}
import zio.ZIO
import zio.console.putStrLn
import zio.duration.durationInt
import zio.magic._

object MoraleOfficer extends zio.App {

  val program = for {
    _ <- DbContext.initializeDb()
    newPosts <- constructPostList()
    _ <- ZIO.foreachPar_(newPosts)(update_and_open)
    _ <- if (newPosts.isEmpty) {
      putStrLn("There are no more quality cats, please wait until more content is generated").delay(2.seconds)
    } else {
      putStrLn("Morale successfully officered!").delay(2.seconds)
    }
    _ <- putStrLn("")
  } yield ()

  override def run(args: List[String]) = program
    .injectCustom(DbContext.live)
    .exitCode

  private def constructPostList() = {
    PostContext.getTop10UnopenedPosts.flatMap(posts =>
      if (posts.isEmpty || posts.length < 5) {
        ZIO.fail(posts)
      } else {
        ZIO.succeed(posts)
      })
      .orElse(ScrapingService.fetchNewPosts *> PostContext.getTop10UnopenedPosts)
  }

  private def update_and_open(post: Post) = {
    PostContext.updatePostWithOpenedTime(post) *> BrowserService.open(post.url)
  }
}
