import tui.TUI
import zio.{ZIO, _}

object Main extends zio.ZIOAppDefault {
  val tuiProgram = ZIO
    .serviceWithZIO[CLI](_.run)
    .provide(
      CLI.live,
      Neelix.live,
      ScrapingService.live,
      BrowserService.live,
      PostService.live,
      SqliteService.live,
      TUI.live(true)
    )

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    tuiProgram
}
