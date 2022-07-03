import ammonite.ops.home
import tui.TUI
import view.View
import zio.stream.ZStream
import zio.{Chunk, Queue, ZIO, ZLayer}

final case class CLI(tui: TUI, neelix: Neelix) {
  val run = {
    for {
      // whoah I need way better names for this
      _                  <- SqliteService.initializeDb()
      appOutput          <- Queue.unbounded[AppMessage]
      appInput           <- Queue.unbounded[AppMessage]
      subredditSummaries <- neelix.constructSubredditStatistics
      _ <- if (subredditSummaries.nonEmpty) {
             runSelector(subredditSummaries, appOutput, appInput)
           } else {
             for {
               _     <- ZIO.debug(foundNoCatPicturesMessage)
               _     <- neelix.scrapingService.updatePostsDatabase()
               stats <- neelix.constructSubredditStatistics

               _ <- runSelector(stats, appOutput, appInput)
             } yield ()
           }
    } yield ()
  }
  private def handleAppMessages(message: AppMessage, appInput: Queue[AppMessage]) = message match {
    case RefreshingStarted =>
      for {
        _          <- neelix.fetchImages
        x           = os.write.append(home / "debug.log", "\nFETCHED IMAGES")
        statistics <- neelix.constructSubredditStatistics
        x           = os.write.append(home / "debug.log", s"\nCONSTRUCTED STATISTICS ${statistics}")
        _          <- ZIO.debug()
        _          <- appInput.offer(RefreshingStopped(statistics))
        x           = os.write.append(home / "debug.log", s"\nSENDING REFRESH STOPPED")
      } yield message
    case SubsChosen(catSubs) =>
      for {
        _          <- neelix.openForSubreddits(catSubs.map(_.name).toList)
        statistics <- neelix.constructSubredditStatistics
        x           = os.write.append(home / "debug.log", s"\nCONSTRUCTED STATISTICS ${statistics}")
        _          <- appInput.offer(UpdatedStats(statistics))
      } yield message
    case RefreshingStopped(catSubs) => ZIO.attempt(message)
    case UpdatedStats(catSubs)      => ZIO.attempt(message)
  }

  private lazy val foundNoCatPicturesMessage: String =
    View
      .text("I've found no cat pictures! 🎉, Fetching...")
      .blue
      .padding(1)
      .renderNow

  case class CLIResult(shouldQuit: Boolean, subredditDataSummaries: Chunk[SubredditDataSummary])

  private def runSelector(
    subredditDataSummaries: Chunk[SubredditDataSummary],
    appOutput: Queue[AppMessage],
    appInput: Queue[AppMessage]
  ): ZIO[TUI, Throwable, Unit] =
    for {
      // here's the problem. It's two different streams
      _ <- ZIO.raceFirst(
             TUI
               .runWithEvents(CliApp)(
                 ZStream.fromQueue(appInput),
                 CliState(
                   catSubs = subredditDataSummaries,
                   index = 0,
                   selected = Set.empty,
                   refreshing = false,
                   outQueue = appOutput
                 )
               ),
             Seq(ZStream
               .fromQueue(appOutput)
               .mapZIO(message => handleAppMessages(message, appInput))
               .runDrain)
           )
      _ = os.write.append(home / "debug.log", s"\nFINISHED TUI and stream")
    } yield ()
}

object CLI {
  type CliEnvironment = Neelix with TUI
  val live: ZLayer[CliEnvironment, Nothing, CLI] =
    ZLayer.fromFunction(apply _)
}
