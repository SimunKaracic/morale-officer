import zio.Schedule
import zio.blocking.effectBlocking
import zio.console.putStrLn
import zio.duration.durationInt

import java.awt.Desktop
import java.io.IOException
import java.net.URI

object BrowserService {
  def open(link: String) = {
    val url = link.replace("/old.", "")
    effectBlocking(
      if (Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop.browse(new URI(url))
      } else {
        println("There's no desktop browser support?? Open a issue, I guess")
      }).refineToOrDie[IOException]
      .retry(Schedule.recurs(30) && Schedule.spaced(150.millis))
      .orElse(putStrLn(s"Failed to open $url in browser"))
  }


}
