package model

import ammonite.ops.home
import com.typesafe.config.ConfigFactory
import io.getquill.context.ExecutionInfo
import io.getquill.context.ZioJdbc.DataSourceLayer
import io.getquill.{JdbcContextConfig, SnakeCase, SqliteZioJdbcContext}
import zio.ZLayer

import java.sql.Connection

// rename this since it's a refactor leftover?
object DbContext {
  lazy val ctx = new SqliteZioJdbcContext(SnakeCase)
  lazy val layer =
    DataSourceLayer.fromConfig(ConfigFactory.parseString(
      s"""
         |driverClassName=org.sqlite.JDBC
         |jdbcUrl="jdbc:sqlite:${home}/.neelix.db"
         |""".stripMargin
    ))

  def initializeDb() = {
    ctx.executeAction(
      """CREATE TABLE IF NOT EXISTS "POST"(
        | url varchar NOT NULL UNIQUE,
        | subreddit varchar NOT NULL,
        | title varchar NOT NULL,
        | upvotes integer NOT NULL,
        | scraped_at time NOT NULL,
        | opened_at time)""".stripMargin)(ExecutionInfo.unknown, ()) *>
      PostContext.deleteOldUnopenedPosts()
  }
}
