import tui.TerminalApp.Step
import tui._
import view._
import zio.{Chunk, Queue, ZIO}

case class SubredditDataSummary(name: String, postCount: Long)
sealed trait AppMessage
case object RefreshingStarted extends AppMessage
case class RefreshingStopped(catSubs: Chunk[SubredditDataSummary]) extends AppMessage
case class SubsChosen(catSubs: Chunk[SubredditDataSummary]) extends AppMessage
case class UpdatedStats(catSubs: Chunk[SubredditDataSummary]) extends AppMessage

final case class CliState(
  catSubs: Chunk[SubredditDataSummary],
  index: Int,
  selected: Set[Int],
  refreshing: Boolean,
  outQueue: Queue[AppMessage]
) {
  def toggle: CliState = {
    val newSelected =
      if (selected(index)) selected - index
      else selected + index
    copy(selected = newSelected)
  }

  def toggleAll: CliState = {
    val newSelected =
      if (selected.isEmpty) catSubs.indices.toSet
      else Set.empty[Int]
    copy(selected = newSelected)
  }

  def moveUp: CliState =
    if (index == 0) this
    else copy(index = index - 1)

  def moveDown: CliState =
    if (index == catSubs.size - 1) this
    else copy(index = index + 1)
}

object CliApp extends TerminalApp[AppMessage, CliState, AppMessage] {
  override def render(state: CliState): View = {
    val longestSubNameLength = state.catSubs.map(_.name.length).max
    val renderedSubs = state.catSubs.zipWithIndex.map { case (sub, idx) =>
      val selected: View =
        if (state.selected.contains(idx)) {
          View.text("▣").green
        } else {
          View.text("☐").cyan.bold
        }

      val isActive = idx == state.index

      val cursor: View =
        if (isActive) {
          View.text("❯").cyan
        } else {
          View.text(" ")
        }

      View.horizontal(1, VerticalAlignment.top)(
        Chunk(
          View.horizontal(0)(cursor, selected)
        ) ++ Chunk(
          View.horizontal(
            View.text(sub.name.padTo(longestSubNameLength, ' ')).cyan,
            View.text(" "),
            View.text(sub.postCount.toString)
          )
        ): _*
      )
    }

    val confirmBinding =
      if (state.selected.nonEmpty) {
        View.horizontal(0)(
          "  ",
          View.text("enter").green,
          " ",
          View.text("open pictures").green.bold
        )
      } else {
        View.text("")
      }

    val keybindings =
      View
        .horizontal(0)(
          View.text("space").green,
          " ",
          View.text("toggle").green.bold,
          "  ",
          View.text("a").green,
          " ",
          View.text("toggle all").green.bold,
          "  ",
          View.text("↑/↓").green,
          " ",
          View.text("move up/down").green.bold,
          confirmBinding,
          "  ",
          View.text("r").green,
          " ",
          View.text("refresh").green.bold,
          " ",
          View.text("q").green,
          " ",
          View.text("quit").green.bold
        )
        .padding(top = 1)

    val title = if (state.refreshing) {
      "Morale Officer - refreshing..."
    } else {
      "Morale Officer"
    }

    View
      .vertical(
        Chunk(
          View.text(title).green,
          View.text("────────────────────────").green.bold
        ) ++
          renderedSubs ++
          Chunk(
            keybindings
          ): _*
      )
      .padding(1)
  }

  override def update(
    state: CliState,
    event: TerminalEvent[AppMessage]
  ): TerminalApp.Step[CliState, AppMessage] =
    event match {
      case TerminalEvent.UserEvent(message) =>
        message match {
          case RefreshingStarted =>
            os.write.append(os.home / "debug.log", s"\nSTARTED REFRESHING INSIDE APP")
            Step.update(state.copy(refreshing = true))
          case RefreshingStopped(catSubs) =>
            os.write.append(os.home / "debug.log", s"\nGOT REFRESHIGN STOPPED ${catSubs}")
            Step.update(state.copy(catSubs = catSubs, refreshing = false))
          case SubsChosen(catSubs) =>
            os.write.append(os.home / "debug.log", s"\nGOT SUBS CHOSEN ${catSubs} INSIDE APP")
            ZIO.debug(s"GOT SUBS CHOSEN WITH ${catSubs}")
            Step.update(state)
          case UpdatedStats(catSubs) =>
            os.write.append(os.home / "debug.log", s"\nGOT UPDATED STATS ${catSubs} INSIDE APP")
            Step.update(state.copy(catSubs = catSubs))
        }
      case TerminalEvent.SystemEvent(keyEvent) =>
        keyEvent match {
          case KeyEvent.Character(' ') =>
            Step.update(state.toggle)
          case KeyEvent.Character('a') =>
            Step.update(state.toggleAll)
          case KeyEvent.Enter =>
            val chosen =
              Chunk.fromArray(state.selected.toList.sorted.map { idx =>
                state.catSubs(idx)
              }.toArray)
            // I'm just ignoring this exit?
            zio.Unsafe.unsafe { implicit u =>
              os.write.append(os.home / "debug.log", s"\nSENDING SUBS CHOSEN INSIDE  APP")
              zio.Runtime.default.unsafe.run(state.outQueue.offer(SubsChosen(chosen)))
              os.write.append(os.home / "debug.log", s"\nSENT SUBS CHOSEN INSIDE  APP")
            }
            Step.update(state)
          case KeyEvent.Up | KeyEvent.Character('k') =>
            Step.update(state.moveUp)
          case KeyEvent.Down | KeyEvent.Character('j') =>
            Step.update(state.moveDown)
          case KeyEvent.Escape | KeyEvent.Exit | KeyEvent.Character('q') =>
            zio.Unsafe.unsafe { implicit u =>
              zio.Runtime.default.unsafe.run(state.outQueue.shutdown)
              os.write.append(os.home / "debug.log", s"\nSENT SHUT DOWN INSIDE  APP")
            }
            Step.exit
          case KeyEvent.Character('r') =>
            zio.Unsafe.unsafe { implicit u =>
              os.write.append(os.home / "debug.log", s"\nSENDING REFRESHING STARTED INSIDE  APP")
              zio.Runtime.default.unsafe.run(state.outQueue.offer(RefreshingStarted))
              os.write.append(os.home / "debug.log", s"SENT SUBS CHOSEN INSIDE  APP\n")
            }

            Step.update(state.copy(refreshing = true))
          case _ =>
            Step.update(state)
        }
    }
}
