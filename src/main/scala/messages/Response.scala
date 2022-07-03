package messages

import data.UnopenedPostsForSubreddit
import zio.Chunk

trait Response
case class RefreshingStopped(catSubs: Chunk[UnopenedPostsForSubreddit]) extends Response
case class UpdatedStats(catSubs: Chunk[UnopenedPostsForSubreddit]) extends Response
