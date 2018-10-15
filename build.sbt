enablePlugins(JavaAppPackaging)

name := "akka-http-microservice"

organization := "com.theiterators"

version := "1.0"

scalaVersion := "2.12.6"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= {
  val akkaV       = "2.5.16"
  val akkaHttpV   = "10.1.4"
  val scalaTestV  = "3.0.5"
  val elastic4sV  = "6.1.2"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-testkit" % akkaV,
    "com.typesafe.akka" %% "akka-http" % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV,
    "com.sksamuel.elastic4s" %% "elastic4s-http" % elastic4sV excludeAll ExclusionRule(organization = "org.apache.logging.log4j"),
    "org.scalatest"     %% "scalatest" % scalaTestV % "test"
  )
}

Revolver.settings