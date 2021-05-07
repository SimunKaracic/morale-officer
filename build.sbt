name := "morale-officer"

version := "2.0"

scalaVersion := "2.13.3"

libraryDependencies ++= Seq(
  "org.jsoup" % "jsoup" % "1.13.1",
  "dev.zio" %% "zio" % "1.0.7",
  "dev.zio" %% "zio-streams" % "1.0.7",
  "com.lihaoyi" %% "ammonite-ops" % "2.3.8",
  "org.xerial" % "sqlite-jdbc" % "3.28.0",
  "io.getquill" %% "quill-jdbc-zio" % "3.7.1"
)
