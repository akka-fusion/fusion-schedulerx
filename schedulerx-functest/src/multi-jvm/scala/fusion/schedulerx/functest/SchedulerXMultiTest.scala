package fusion.schedulerx.functest

import java.util.concurrent.TimeUnit

import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, STMultiNodeSpec, SchudulerXMultiNodeSpec}
import com.typesafe.config.ConfigFactory
import fusion.schedulerx.server.SchedulerXServer
import fusion.schedulerx.worker.WorkerServer
import fusion.schedulerx.{SchedulerX, server}

object SchedulerXMultiTestConfig extends MultiNodeConfig {
  val brokers: Vector[RoleName] = (0 until 1).map(i => role(s"broker$i")).toVector

  val workers: Vector[RoleName] = (0 until 3).map(i => role(s"worker$i")).toVector

  // this configuration will be used for all nodes
  // note that no fixed hostname names and ports are used
  commonConfig(ConfigFactory.parseString(s"""
    akka.loglevel = "DEBUG"
    akka.cluster.seed-nodes = ["127.0.0.1:9000"]
    """).withFallback(ConfigFactory.load()))

  for ((node, idx) <- brokers.zipWithIndex) {
    nodeConfig(node)(ConfigFactory.parseString(s"""schedulerx {
      |  akka.remote.artery.canonical.port = ${9000 + idx}
      |  akka.cluster.roles = [broker]
      |}""".stripMargin))
  }

  for ((node, idx) <- workers.zipWithIndex) {
    nodeConfig(node)(ConfigFactory.parseString(s"""schedulerx {
        |  akka.remote.artery.canonical.port = ${9090 + idx}
        |  akka.cluster.roles = [worker]
        |}""".stripMargin))
  }
}

abstract class SchedulerXMultiTest
    extends SchudulerXMultiNodeSpec(SchedulerXMultiTestConfig, config => SchedulerX.fromOriginalConfig(config))
    with STMultiNodeSpec {
  import SchedulerXMultiTestConfig._

  private var schedulerXWorker: WorkerServer = _
  private var schedulerXBroker: SchedulerXServer = _

  "SchedulerX" must {
    "wait for all nodes initialize finished" in {
      runOn(brokers: _*) {
        schedulerXBroker = server.SchedulerXServer(schedulerX).start()
        enterBarrier("brokers-init")
      }

      runOn(workers: _*) {
        enterBarrier("brokers-init")
        schedulerXWorker = WorkerServer(schedulerX).start()
      }

      enterBarrier("finished")
    }

    "timeout" in {
      TimeUnit.SECONDS.sleep(10)
      enterBarrier("timeout")
    }
  }
}

class SchedulerXMultiTestMultiJvmNode1 extends SchedulerXMultiTest
class SchedulerXMultiTestMultiJvmNode2 extends SchedulerXMultiTest
class SchedulerXMultiTestMultiJvmNode3 extends SchedulerXMultiTest
class SchedulerXMultiTestMultiJvmNode4 extends SchedulerXMultiTest
//class SchedulerXMultiTestMultiJvmNode5 extends SchedulerXMultiTest
//class SchedulerXMultiTestMultiJvmNode6 extends SchedulerXMultiTest
//class SchedulerXMultiTestMultiJvmNode7 extends SchedulerXMultiTest
