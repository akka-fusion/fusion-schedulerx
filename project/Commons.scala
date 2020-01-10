import bintray.BintrayKeys._
import com.typesafe.sbt.SbtNativePackager.autoImport.maintainer
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.{ HeaderLicense, headerLicense }
import sbt.Keys._
import sbt._

object Commons {
  import Environment.{ BuildEnv, buildEnv }

  def basicSettings =
    Seq(
      organization := "com.akka-fusion",
      organizationName := "akka-fusion.com",
      organizationHomepage := Some(url("https://github.com/akka-fusion/fusion-schedulerx")),
      homepage := Some(url("https://akka-fusion.github.io/fusion-schedulerx")),
      startYear := Some(2019),
      licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
      headerLicense := Some(HeaderLicense.ALv2("2019", "akka-fusion.com")),
      scalacOptions ++= {
        var list = Seq(
          "-encoding",
          "UTF-8", // yes, this is 2 args
          "-feature",
          "-deprecation",
          "-unchecked",
          "-Ywarn-dead-code",
          "-Xlint")
        if (buildEnv.value != BuildEnv.Development) {
          list ++= Seq("-Xelide-below", "2001")
        }
        list
      },
      javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
      javaOptions in run ++= Seq("-Xms128m", "-Xmx1024m", "-Djava.library.path=./target/native"),
      shellPrompt := { s =>
        Project.extract(s).currentProject.id + " > "
      },
      resolvers += Resolver.bintrayRepo("akka", "snapshots"),
      resolvers += "hongkazhijiz.com sbt".at("https://artifactory.hongkazhijia.com/artifactory/sbt-release"),
      fork in run := true,
      fork in Test := true,
      parallelExecution in Test := false) ++ Environment.settings
}

object Publishing {
  lazy val publishing = Seq(
    bintrayOrganization := Some("akka-fusion"),
    bintrayRepository := "maven",
    maintainer := "Yang Jing <yangbajing@gmail.com>",
    scmInfo := Some(
        ScmInfo(
          url("https://github.com/akka-fusion/fusion-discoveryx.git"),
          "scm:git@github.com:akka-fusion/fusion-discoveryx.git")),
    developers := List(
        Developer(
          id = "yangbajing",
          name = "Yang Jing",
          email = "yangbajing@gmail.com",
          url = url("https://github.com/yangbajing"))))
}

object Environment {
  object BuildEnv extends Enumeration {
    val Production, Stage, Test, Development = Value
  }

  val buildEnv = settingKey[BuildEnv.Value]("The current build environment")

  val settings = Seq(onLoadMessage := {
    // old message as well
    val defaultMessage = onLoadMessage.value
    val env = buildEnv.value
    s"""|$defaultMessage
        |Working in build environment: $env""".stripMargin
  })
}
