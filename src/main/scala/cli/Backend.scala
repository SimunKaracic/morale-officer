package cli

import data.UnopenedPostsForSubreddit
import messages._
import neelix.Neelix
import services.SqliteService
import tui.TUI
import view.View
import zio.stream.ZStream
import zio.{Chunk, Queue, ZIO, ZLayer}

final case class Backend(tui: TUI, neelix: Neelix) {
  val run = {
    for {
      _                  <- SqliteService.initializeDb()
      appOutputQueue     <- Queue.unbounded[Request]
      appInputQueue      <- Queue.unbounded[Response]
      subredditSummaries <- neelix.constructSubredditStatistics
      _ <- if (subredditSummaries.nonEmpty) {
             runServer(subredditSummaries, appOutputQueue, appInputQueue)
           } else {
             for {
               _     <- ZIO.debug(foundNoCatPicturesMessage)
               _     <- neelix.scrapingService.updatePostsDatabase()
               stats <- neelix.constructSubredditStatistics

               _ <- runServer(stats, appOutputQueue, appInputQueue)
             } yield ()
           }
    } yield ()
  }

  private def handleAppMessages(request: Request, appInputQueue: Queue[Response]) =
    neelix.handleAppMessages(request, appInputQueue)

  private lazy val foundNoCatPicturesMessage: String =
    View
      .text("I've found no cat pictures! 🎉, Fetching...")
      .blue
      .padding(1)
      .renderNow

  private def runServer(
    subredditDataSummaries: Chunk[UnopenedPostsForSubreddit],
    appOutput: Queue[Request],
    appInput: Queue[Response]
  ): ZIO[TUI, Throwable, Unit] =
    for {
      _ <- ZStream
             .fromQueue(appOutput)
             .mapZIO(message => handleAppMessages(message, appInput))
             .runDrain
             .fork
      _ <- TUI
             .runWithEvents(Frontend)(
               ZStream.fromQueue(appInput),
               AppState(
                 catSubs = subredditDataSummaries,
                 index = 0,
                 selected = Set.empty,
                 refreshing = false,
                 requestQueue = appOutput
               )
             )
    } yield ()
}

object Backend {
  type CliEnvironment = Neelix with TUI
  val live: ZLayer[CliEnvironment, Nothing, Backend] =
    ZLayer.fromFunction(apply _)
}
