import com.typesafe.config.ConfigFactory
import io.getquill.context.ZioJdbc.DataSourceLayer
import model.{DbContext, Post, PostContext}
import zio.Console.printLine
import zio.ZIO
import zio._

object MoraleOfficer extends zio.ZIOAppDefault {

  val program = for {
    _ <- printLine("Initiliaizing DB")
    _ <- DbContext.initializeDb()
    _ <- printLine("constructing post list")
    newPosts <- constructPostList()
    _ <- ZIO.foreachDiscard(newPosts)(update_and_open)
    _ <- if (newPosts.isEmpty) {
      printLine("There are no more quality cats, please wait until more content is generated").delay(2.seconds)
    } else {
      printLine("Morale successfully officered!").delay(2.seconds)
    }
    _ <- printLine("")
  } yield ()

  private def constructPostList() = {
    printLine("getting top 10 posts") *>
    PostContext.getTop10UnopenedPosts.flatMap(posts =>
      if (posts.isEmpty || posts.length < 5) {
        ZIO.fail(posts)
      } else {
        ZIO.succeed(posts)
      })
      .orElse(ScrapingService.fetchNewPosts *> PostContext.getTop10UnopenedPosts)
  }

  private def update_and_open(post: Post) = {
//    PostContext.updatePostWithOpenedTime(post) *> BrowserService.open(post.url)
    PostContext.updatePostWithOpenedTime(post)
  }

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = program.provideLayer(DbContext.layer)
}
