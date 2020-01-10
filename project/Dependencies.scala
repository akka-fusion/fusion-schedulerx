import fusion.sbt.gen.BuildInfo
import sbt._

object Dependencies {
  val versionScala212 = "2.12.10"
  val versionScala213 = "2.13.1"
  val versionH2 = "1.4.200"
  val versionAkkaPersistenceCassandra = "0.101"

  val _akkaSerializationJackson = "com.typesafe.akka" %% "akka-serialization-jackson" % BuildInfo.versionAkka
  val _akkaPersistenceTyped = "com.typesafe.akka" %% "akka-persistence-typed" % BuildInfo.versionAkka
  val _akkaClusterShardingTyped = "com.typesafe.akka" %% "akka-cluster-sharding-typed" % BuildInfo.versionAkka
  val _akkaMultiNodeTestkit = "com.typesafe.akka" %% "akka-multi-node-testkit" % BuildInfo.versionAkka

  val _akkaHttpTestkit = ("com.typesafe.akka" %% "akka-http-testkit" % BuildInfo.versionAkkaHttp)
    .exclude("com.typesafe.akka", "akka-stream-testkit")
    .cross(CrossVersion.binary)
    .exclude("com.typesafe.akka", "akka-testkit")
    .cross(CrossVersion.binary)

  val _akkaHttp2 = ("com.typesafe.akka" %% "akka-http2-support" % BuildInfo.versionAkkaHttp)
    .exclude("com.typesafe.akka", "akka-http-core")
    .cross(CrossVersion.binary)
    .exclude("com.typesafe.akka", "akka-stream")
    .cross(CrossVersion.binary)

  val _akkaPersistenceCassandras =
    Seq("com.typesafe.akka" %% "akka-persistence-cassandra" % versionAkkaPersistenceCassandra).map(
      _.exclude("org.scala-lang", "scala-library")
        .cross(CrossVersion.binary)
        .exclude("com.typesafe.akka", "akka-cluster-tools")
        .cross(CrossVersion.binary)
        .exclude("com.typesafe.akka", "akka-cluster-tools")
        .cross(CrossVersion.binary)
        .exclude("com.typesafe.akka", "akka-persistence")
        .cross(CrossVersion.binary)
        .exclude("com.typesafe.akka", "akka-persistence-query")
        .cross(CrossVersion.binary))

  val _akkaPersistenceJdbc =
    ("com.github.dnvriend" %% "akka-persistence-jdbc" % "3.5.2")
      .excludeAll(ExclusionRule("com.typesafe.akka"))
      .cross(CrossVersion.binary)

  val _osLib = "com.lihaoyi" %% "os-lib" % BuildInfo.versionOsLib
  val _requests = "com.lihaoyi" %% "requests" % BuildInfo.versionRequests

  val _oshiCore = "com.github.oshi" % "oshi-core" % "4.2.1"

  val _quartz = ("org.quartz-scheduler" % "quartz" % BuildInfo.versionQuartz).exclude("com.zaxxer", "HikariCP-java7")
  val _postgresql = "org.postgresql" % "postgresql" % BuildInfo.versionPostgres
  val _h2 = "com.h2database" % "h2" % versionH2
  val _jsch = "com.jcraft" % "jsch" % BuildInfo.versionJsch
  val _commonsVfs = "org.apache.commons" % "commons-vfs2" % "2.2"
  val _alpnAgent = "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % BuildInfo.versionAlpnAgent
}
