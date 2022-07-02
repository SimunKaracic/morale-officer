import tui.TUI
import view.View
import zio.Console.printLine
import zio.{Chunk, ZEnvironment, ZIO, ZLayer}

final case class CLI(tui: TUI, neelix: Neelix) {
  val run = {
    for {
      _                  <- SqliteService.initializeDb()
      subredditSummaries <- neelix.constructSubredditStatistics
      _ <- if (subredditSummaries.nonEmpty) {
             runSelector(subredditSummaries)
           } else {
             for {
               _     <- ZIO.debug(foundNoCatPicturesMessage)
               _     <- neelix.scrapingService.updatePostsDatabase()
               stats <- neelix.constructSubredditStatistics

               _ <- runSelector(stats)
             } yield ()
           }
    } yield ()
  }

  private lazy val foundNoCatPicturesMessage: String =
    View
      .text("I've found no cat pictures! 🎉, Fetching...")
      .blue
      .padding(1)
      .renderNow

  case class CLIResult(shouldQuit: Boolean, subredditDataSummaries: Chunk[SubredditDataSummary])

  private def runSelector(
    subredditDataSummaries: Chunk[SubredditDataSummary]
  ): ZIO[CLI.CliEnvironment, Throwable, CliState] =
    for {
      result <- CliApp.run(CliState(subredditDataSummaries, 0, Set.empty)).provideEnvironment(ZEnvironment(tui))
      _ <- ZIO.when(result.selected.nonEmpty) {
             for {
               _          <- neelix.openForSubreddits(result.catSubs.map(_.name).toList)
               statistics <- neelix.constructSubredditStatistics
               _          <- runSelector(statistics)
             } yield ()
           }
      _ <- ZIO.when(result.shouldRefresh) {
             for {
               _          <- neelix.fetchImages
               statistics <- neelix.constructSubredditStatistics
               _          <- runSelector(statistics)
             } yield ()
           }
      subredditSummaries <- neelix.constructSubredditStatistics
      _                  <- printLine("What? I'm up to the last yield")
    } yield result.copy(catSubs = subredditSummaries)
}

object CLI {
  type CliEnvironment = Neelix with TUI
  val live: ZLayer[CliEnvironment, Nothing, CLI] =
    ZLayer.fromFunction(apply _)
}
