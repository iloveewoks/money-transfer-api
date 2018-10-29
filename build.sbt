name := "money-transfer-api"

version := "0.1"

scalaVersion := "2.12.7"
val akkaVersion = "2.5.17"
val akkaHttpVersion = "10.1.5"

resolvers += "dnvriend" at "http://dl.bintray.com/dnvriend/maven"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-persistence" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-http"   % akkaHttpVersion
libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion
libraryDependencies += "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.15.1"
