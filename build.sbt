name := "morale-officer"

version := "2.0"

scalaVersion := "2.13.4"

libraryDependencies ++= Seq(
  "org.jsoup" % "jsoup" % "1.13.1",
  "dev.zio" %% "zio" % "1.0.3",
  "dev.zio" %% "zio-streams" % "1.0.3",
  "com.lihaoyi" %% "ammonite-ops" % "2.3.8"
)
