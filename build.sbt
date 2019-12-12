import Commons._
import Dependencies._
import Environment._

ThisBuild / buildEnv := {
  sys.props
    .get("build.env")
    .orElse(sys.env.get("BUILD_ENV"))
    .flatMap {
      case "prod"  => Some(BuildEnv.Production)
      case "stage" => Some(BuildEnv.Stage)
      case "test"  => Some(BuildEnv.Test)
      case "dev"   => Some(BuildEnv.Developement)
      case _       => None
    }
    .getOrElse(BuildEnv.Developement)
}

ThisBuild / scalaVersion := versionScala213

ThisBuild / crossScalaVersions := Seq(versionScala212, versionScala213)

ThisBuild / scalafmtOnCompile := true

ThisBuild / sonarUseExternalConfig := true

lazy val root = Project(id = "fusion-schedulerx", base = file("."))
  .aggregate(fusionDocs, schedulerxFunctest, schedulerxServer, schedulerxWorker, schedulerxCommon)
  .settings(Publishing.noPublish: _*)
  .settings(Environment.settings: _*)
  .settings(aggregate in sonarScan := false)

lazy val fusionDocs = _project("schedulerx-docs")
  .enablePlugins(ParadoxMaterialThemePlugin)
  .dependsOn(schedulerxFunctest, schedulerxServer, schedulerxWorker, schedulerxCommon)
  .settings(Publishing.noPublish: _*)
  .settings(
    Compile / paradoxMaterialTheme ~= {
      _.withLanguage(java.util.Locale.SIMPLIFIED_CHINESE)
        .withColor("indigo", "red")
        .withRepository(uri("https://github.com/akka-fusion/fusion-schedulerx"))
        .withSocial(
          uri("http://akka-fusion.github.io/fusion-schedulerx/"),
          uri("https://github.com/akka-fusion"),
          uri("https://weibo.com/yangbajing"))
    },
    paradoxProperties ++= Map(
        "github.base_url" -> s"https://github.com/akka-fusion/fusion-schedulerx/tree/${version.value}",
        "version" -> version.value,
        "scala.version" -> scalaVersion.value,
        "scala.binary_version" -> scalaBinaryVersion.value,
        "scaladoc.akka.base_url" -> s"http://doc.akka.io/api/$versionAkka",
        "akka.version" -> versionAkka))

lazy val schedulerxFunctest = _project("schedulerx-functest")
  .enablePlugins(MultiJvmPlugin)
  .dependsOn(schedulerxWorker, schedulerxServer)
  .settings(Publishing.noPublish)
  .configs(MultiJvm)
  .settings(jvmOptions in MultiJvm := Seq("-Xmx512M"), libraryDependencies ++= Seq(_akkaMultiNodeTestkit % Test))

lazy val schedulerxServer = _project("schedulerx-server")
  .enablePlugins(AkkaGrpcPlugin, JavaAgent, JavaAppPackaging)
  .dependsOn(schedulerxWorker, schedulerxCommon)
  .settings(Publishing.noPublish)
  .settings(
    javaAgents += _alpnAgent % "runtime;test",
    akkaGrpcCodeGeneratorSettings += "server_power_apis",
    libraryDependencies ++= Seq(
        "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
        _fusionJdbc,
        _akkaGrpcRuntime,
        _postgresql,
        _quartz))

lazy val schedulerxWorker = _project("schedulerx-worker")
  .enablePlugins(AkkaGrpcPlugin, JavaAgent)
  .dependsOn(schedulerxCommon)
  .settings(
    akkaGrpcCodeGeneratorSettings += "server_power_apis",
    libraryDependencies ++= Seq(
        "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
        _fusionJson,
        _akkaGrpcRuntime,
        _akkaDiscovery,
        _akkaHttp))

lazy val schedulerxCommon = _project("schedulerx-common").settings(
  libraryDependencies ++= Seq(
      _fusionCommon,
      _h2,
      _akkaSerializationJackson,
      "com.typesafe.akka" %% "akka-cluster-typed" % versionAkka,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % versionAkka,
      _oshiCore))

def _project(name: String, _base: String = null) =
  Project(id = name, base = file(if (_base eq null) name else _base))
    .enablePlugins(AutomateHeaderPlugin)
    .settings(basicSettings: _*)
    .settings(Publishing.publishing: _*)
    .settings(libraryDependencies ++= Seq(_fusionTestkit % Test))
