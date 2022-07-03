package data

import java.time.LocalDateTime

case class Post(
  url: String,
  subreddit: String,
  title: String,
  upvotes: Int,
  scraped_at: LocalDateTime,
  opened_at: Option[LocalDateTime] = None
)
