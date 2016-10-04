name := "RecruitmentSystem"

version := "1.0"

scalaVersion := "2.11.8"

scalacOptions := Seq(
  "-encoding", "utf8",
  "-feature",
  "-unchecked",
  "-deprecation",
  "-target:jvm-1.8",
  "-Ymacro-debug-lite",
  "-language:_",
  "-Xexperimental")

libraryDependencies += "org.scalactic" %% "scalactic" % "2.2.6"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "test"

libraryDependencies += "org.mockito" % "mockito-core" % "1.9.5" % "test"

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.12.5" % "test"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.10"

libraryDependencies += "com.typesafe.akka" %% "akka-cluster" % "2.4.10"

libraryDependencies += "com.typesafe.akka" %% "akka-remote" % "2.4.10"

libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % "2.4.10"

libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.4.10"

libraryDependencies += "com.typesafe.akka" %% "akka-stream-testkit" % "2.4.10"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.4.10"

libraryDependencies += "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.5"

libraryDependencies += "org.ccil.cowan.tagsoup" % "tagsoup" % "1.2.1"
    