lazy val commonSettings = Seq(
  organization := "com.xogroupinc",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.11.11"
)

lazy val root = (project in file("."))
  .settings(
  commonSettings,
  name := "com.xogroupinc.play-aws-signer",
  resolvers += Resolver.jcenterRepo,
    libraryDependencies ++= Seq(
      "org.asynchttpclient" %  "async-http-client"  % "2.0.11",
      "com.amazonaws"       %  "aws-java-sdk-core"  % "1.11.165",
      "com.typesafe.play"   %% "play"               % "2.5.10",
      "com.typesafe.play"   %% "play-ws"            % "2.5.10",
      "io.ticofab"          %% "aws-request-signer" % "0.5.1"
    )
)
