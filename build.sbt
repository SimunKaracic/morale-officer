name := "morale-officer"

version := "2.0"

scalaVersion := "2.13.4"

val zioMagicVersion = "0.3.2"
libraryDependencies ++= Seq(
  "org.jsoup" % "jsoup" % "1.14.3",
  "dev.zio" %% "zio" % "2.0.0",
  "dev.zio" %% "zio-streams" % "2.0.0",
  "com.lihaoyi" %% "ammonite-ops" % "2.4.1",
  "org.xerial" % "sqlite-jdbc" % "3.36.0.3",
  "io.getquill" %% "quill-jdbc-zio" % "4.0.0",
  "io.github.kitlangton" %% "zio-tui" % "0.1.2",
)
