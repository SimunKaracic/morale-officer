package messages

import data.UnopenedPostsForSubreddit
import zio.Chunk

trait Request
case object RefreshData extends Request
case class SubsChosen(catSubs: Chunk[UnopenedPostsForSubreddit]) extends Request
