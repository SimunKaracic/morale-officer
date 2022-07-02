import tui.TerminalApp.Step
import tui._
import view._
import zio.Chunk

case class SubredditDataSummary(name: String, postCount: Long)

final case class CliState(
  catSubs: Chunk[SubredditDataSummary],
  index: Int = 0,
  selected: Set[Int] = Set.empty,
  shouldRefresh: Boolean = false
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

object CliApp extends TerminalApp[Nothing, CliState, CliState] {
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

    val title = if (state.shouldRefresh) {
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
    event: TerminalEvent[Nothing]
  ): TerminalApp.Step[CliState, CliState] =
    event match {
      case TerminalEvent.UserEvent(event) =>
        ???
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
            Step.succeed(state.copy(catSubs = chosen))
          case KeyEvent.Up | KeyEvent.Character('k') =>
            Step.update(state.moveUp)
          case KeyEvent.Down | KeyEvent.Character('j') =>
            Step.update(state.moveDown)
          case KeyEvent.Escape | KeyEvent.Exit | KeyEvent.Character('q') =>
            Step.exit
          case KeyEvent.Character('r') =>
            Step.succeed(state.copy(shouldRefresh = true))
          case _ =>
            Step.update(state)
        }
    }
}
