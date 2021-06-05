package model

import ammonite.ops.home
import com.typesafe.config.ConfigFactory
import io.getquill.{JdbcContextConfig, SnakeCase, SqliteZioJdbcContext}
import zio.{Has, Task, ZLayer, ZManaged}

import java.sql.Connection

object DbContext extends SqliteZioJdbcContext(SnakeCase) {
  val live: ZLayer[Any, Throwable, Has[Connection]] = {
    // config defined here because
    // i cant figure out relative file paths in the conf
    val conf = ConfigFactory.parseString(
      s"""
         | db {
         |   driverClassName=org.sqlite.JDBC
         |   jdbcUrl="jdbc:sqlite:${home}/.neelix.db"
         | }
    """.stripMargin)

    ZLayer.fromManaged(for {
      ds <- ZManaged.fromAutoCloseable(Task(JdbcContextConfig(conf.getConfig("db")).dataSource))
      conn <- ZManaged.fromAutoCloseable(Task(ds.getConnection))
    } yield conn)
  }

  def initializeDb() = {
    this.executeAction(
      """CREATE TABLE IF NOT EXISTS "POST"(
        | url varchar NOT NULL UNIQUE,
        | upvotes integer NOT NULL,
        | scraped_at time NOT NULL,
        | opened_at time)""".stripMargin) *>
      PostContext.deleteOldUnopenedPosts()
  }
}
