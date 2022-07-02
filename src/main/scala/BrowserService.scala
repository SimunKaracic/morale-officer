import zio.Console.printLine
import zio.{Schedule, ZIO, ZLayer, durationInt}

import java.awt.Desktop
import java.io.IOException
import java.net.URI

case class BrowserService() {
  def open(link: String) =
    ZIO
      .attempt(if (Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop.browse(new URI(link))
      } else {
        println("There's no desktop browser support?? Open a issue, I guess")
      })
      .refineToOrDie[IOException]
      .retry(Schedule.recurs(30) && Schedule.spaced(150.millis))
      .orElse(printLine(s"Failed to open $link in browser"))
}

object BrowserService {
  val live: ZLayer[Any, Nothing, BrowserService] =
    ZLayer.fromFunction(apply _)
}
