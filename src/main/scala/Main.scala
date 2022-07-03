import cli.Backend
import neelix.Neelix
import services.{BrowserService, PostService, ScrapingService, SqliteService}
import tui.TUI
import zio.{ZIO, _}

object Main extends zio.ZIOAppDefault {
  val tuiProgram = ZIO
    .serviceWithZIO[Backend](_.run)
    .provide(
      Backend.live,
      Neelix.live,
      ScrapingService.live,
      BrowserService.live,
      PostService.live,
      SqliteService.live,
      TUI.live(false)
    )

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    tuiProgram
}
