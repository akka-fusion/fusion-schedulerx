import Commons._
import Dependencies._
import Environment._
import fusion.sbt.gen.BuildInfo

ThisBuild / offline := true

ThisBuild / updateOptions := updateOptions.value.withCachedResolution(true).withLatestSnapshots(false)

ThisBuild / buildEnv := {
  sys.props
    .get("build.env")
    .orElse(sys.env.get("BUILD_ENV"))
    .flatMap {
      case "prod"  => Some(BuildEnv.Production)
      case "stage" => Some(BuildEnv.Stage)
      case "test"  => Some(BuildEnv.Test)
      case "dev"   => Some(BuildEnv.Development)
      case _       => None
    }
    .getOrElse(BuildEnv.Development)
}

ThisBuild / scalaVersion := versionScala213

ThisBuild / crossScalaVersions := Seq(versionScala212, versionScala213)

ThisBuild / scalafmtOnCompile := true

ThisBuild / sonarUseExternalConfig := true

ThisBuild / resolvers ++= Seq(Resolver.bintrayRepo("akka-fusion", "maven"), Resolver.jcenterRepo)

lazy val root = Project(id = "fusion-schedulerx", base = file("."))
  .aggregate(schedulerxDocs, schedulerxFunctest, schedulerxServer, schedulerxWorker, schedulerxCommon)
  .settings(Environment.settings: _*)
  .settings(skip in publish := true, aggregate in sonarScan := false)

lazy val schedulerxDocs = _project("schedulerx-docs")
  .enablePlugins(AkkaParadoxPlugin)
  .dependsOn(schedulerxFunctest, schedulerxServer, schedulerxWorker, schedulerxCommon)
  .settings(
    skip in publish := true,
    paradoxGroups := Map("Language" -> Seq("Scala", "Java")),
    sourceDirectory in Compile in paradoxTheme := sourceDirectory.value / "main" / "paradox" / "_template",
    paradoxProperties ++= Map(
        "project.name" -> "Fusion DiscoveryX",
        "canonical.base_url" -> "http://akka-fusion.github.io/akka-schedulerx/",
        "github.base_url" -> s"https://github.com/akka-fusion/fusion-schedulerx/tree/${version.value}",
        "scala.version" -> scalaVersion.value,
        "scala.binary_version" -> scalaBinaryVersion.value,
        "scaladoc.akka.base_url" -> s"http://doc.akka.io/api/${BuildInfo.versionAkka}",
        "akka.version" -> BuildInfo.versionAkka,
        "play.ahc-ws-standalone.version" -> "2.1.2",
        "akka.persistence.couchbase.version" -> "1.0",
        "akka.persistence.mongo.version" -> "2.3.2",
        "akka.persistence.dynamodb.version" -> "1.1.1",
        "version" -> version.value))

lazy val schedulerxFunctest = _project("schedulerx-functest")
  .enablePlugins(MultiJvmPlugin)
  .dependsOn(schedulerxWorker, schedulerxServer)
  .configs(MultiJvm)
  .settings(
    skip in publish := true,
    jvmOptions in MultiJvm := Seq("-Xmx512M"),
    libraryDependencies ++= Seq(_akkaMultiNodeTestkit % Test))

lazy val schedulerxServer = _project("schedulerx-server")
  .enablePlugins(JavaAgent, JavaAppPackaging)
  .dependsOn(schedulerxWorker, schedulerxCommon)
  .settings(
    skip in publish := true,
    javaAgents += _alpnAgent % "runtime;test",
    libraryDependencies ++= Seq(
        fusionJdbc,
        fusionMail,
        _postgresql,
        _quartz,
        _akkaPersistenceTyped,
        _akkaHttpTestkit % Test))

lazy val schedulerxWorker = _project("schedulerx-worker")
  .enablePlugins(JavaAgent)
  .dependsOn(schedulerxCommon)
  .settings(libraryDependencies ++= Seq(_osLib, _requests, fusionJson, _akkaHttp2, _akkaHttpTestkit % Test))

lazy val schedulerxCommon = _project("schedulerx-common").settings(
  libraryDependencies ++= Seq(fusionCommon, _h2, _akkaSerializationJackson, _oshiCore, _akkaClusterShardingTyped))

def _project(name: String, _base: String = null) =
  Project(id = name, base = file(if (_base eq null) name else _base))
    .enablePlugins(AutomateHeaderPlugin, FusionPlugin)
    .settings(basicSettings: _*)
    .settings(Publishing.publishing: _*)
    .settings(libraryDependencies ++= Seq(fusionTestkit % Test))
