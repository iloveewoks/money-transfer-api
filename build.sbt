name := "money-transfer-api"

version := "0.1"

scalaVersion := "2.12.7"
val akkaVersion = "2.5.17"

resolvers += "dnvriend" at "http://dl.bintray.com/dnvriend/maven"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % s"$akkaVersion"
libraryDependencies += "com.typesafe.akka" %% "akka-persistence" % s"$akkaVersion"
libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % s"$akkaVersion"
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % s"$akkaVersion"
libraryDependencies += "com.typesafe.akka" %% "akka-http"   % "10.1.5"
libraryDependencies += "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.15.1"
