package cli

import data.UnopenedPostsForSubreddit
import messages._
import tui.TerminalApp.Step
import tui._
import view._
import zio.{Chunk, Queue}

// add error message to state and display it at the bottom
// e.g. no subs selected, wrong key pressed, etc
final case class AppState(
  catSubs: Chunk[UnopenedPostsForSubreddit],
  index: Int,
  selected: Set[Int],
  refreshing: Boolean,
  requestQueue: Queue[Request]
) {
  def toggle: AppState = {
    val newSelected =
      if (selected(index)) selected - index
      else selected + index
    copy(selected = newSelected)
  }

  def toggleAll: AppState = {
    val newSelected =
      if (selected.isEmpty) catSubs.indices.toSet
      else Set.empty[Int]
    copy(selected = newSelected)
  }

  def moveUp: AppState =
    if (index == 0) this
    else copy(index = index - 1)

  def moveDown: AppState =
    if (index == catSubs.size - 1) this
    else copy(index = index + 1)
}

object Frontend extends TerminalApp[Response, AppState, Request] {
  override def render(state: AppState): View = {
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
    state: AppState,
    event: TerminalEvent[Response]
  ): TerminalApp.Step[AppState, Request] =
    event match {
      case TerminalEvent.UserEvent(message) =>
        message match {
          case RefreshingStopped(catSubs) =>
            Step.update(state.copy(catSubs = catSubs, refreshing = false))
          case UpdatedStats(catSubs) =>
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
            zio.Unsafe.unsafe { implicit u =>
              zio.Runtime.default.unsafe.run(state.requestQueue.offer(SubsChosen(chosen)))
            }
            Step.update(state)
          case KeyEvent.Up | KeyEvent.Character('k') =>
            Step.update(state.moveUp)
          case KeyEvent.Down | KeyEvent.Character('j') =>
            Step.update(state.moveDown)
          case KeyEvent.Escape | KeyEvent.Exit | KeyEvent.Character('q') =>
            zio.Unsafe.unsafe { implicit u =>
              zio.Runtime.default.unsafe.run(state.requestQueue.shutdown)
            }
            Step.exit
          case KeyEvent.Character('r') =>
            zio.Unsafe.unsafe { implicit u =>
              zio.Runtime.default.unsafe.run(state.requestQueue.offer(RefreshData))
            }

            Step.update(state.copy(refreshing = true))
          case _ =>
            Step.update(state)
        }
    }
}
