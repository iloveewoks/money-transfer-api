name := "money-transfer-api"

version := "0.1"

scalaVersion := "2.12.7"

resolvers += "migesok at bintray" at "http://dl.bintray.com/migesok/maven"

libraryDependencies +=
  "com.migesok" %% "akka-persistence-in-memory-snapshot-store" % "0.1.1"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.17"
libraryDependencies += "com.typesafe.akka" %% "akka-persistence" % "2.5.17"
libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % "2.5.17"
//libraryDependencies += "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.1.1" % Test
