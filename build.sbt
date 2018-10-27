name := "money-transfer-api"

version := "0.1"

scalaVersion := "2.12.7"

resolvers += "dnvriend" at "http://dl.bintray.com/dnvriend/maven"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.17"
libraryDependencies += "com.typesafe.akka" %% "akka-persistence" % "2.5.17"
libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % "2.5.17"
libraryDependencies += "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.15.1"
