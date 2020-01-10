/*
 * Copyright 2019 akka-fusion.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fusion.schedulerx.worker.util

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }

import akka.NotUsed
import akka.actor.typed.{ ActorRef, ActorRefResolver, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.scaladsl.FileIO
import com.typesafe.config.ConfigFactory
import fusion.json.jackson.Jackson
import fusion.schedulerx.job.ProcessResult
import fusion.schedulerx.protocol.{ JobInstanceData, Worker }
import fusion.schedulerx.{ Constants, FileNames, NodeRoles }
import helloscala.common.IntStatus

import scala.concurrent.ExecutionContext

object WorkerUtils {
  def writeJobInstanceDir(runDir: Path, instanceData: JobInstanceData, worker: ActorRef[Worker.Command])(
      implicit system: ActorSystem[_]): Unit = {
    val config = system.settings.config
    val configFile = runDir.resolve(FileNames.APPLICATION_CONF)
    val workerFormat = ActorRefResolver(system).toSerializationFormat(worker)

    val mergeableConfig = ConfigFactory.parseString(s"""worker {
                      |  runOnce = true
                      |  runJobWorkerActor = "$workerFormat"
                      |  runDir = $runDir
                      |}
                      |akka.remote.artery.canonical.port = 0
                      |akka.cluster.roles = [${NodeRoles.WORKER}]
                      |""".stripMargin).withFallback(config.getConfig(Constants.SCHEDULERX))

    val content = s"${Constants.SCHEDULERX} { ${mergeableConfig.root().render()} }"
    Files.write(configFile, content.getBytes(StandardCharsets.UTF_8))
    Files.write(runDir.resolve(FileNames.INSTANCE_JSON), Jackson.defaultObjectMapper.writeValueAsBytes(instanceData))
  }

  def runJar(runDir: Path, jarUrl: String)(implicit system: ActorSystem[_], ec: ExecutionContext) = {
    val path = runDir.resolve(FileNames.RUN_WORKER_JAR)
    Http(system)
      .singleRequest(HttpRequest(uri = jarUrl))
      .flatMap { resp =>
        val cl = resp.entity.contentLengthOption
        resp.entity.dataBytes.runWith(FileIO.toPath(path)).map {
          case ioResult if cl.contains(ioResult.count) => NotUsed
          case _                                       => throw new IllegalStateException(s"Jar包未下载完成，jar url: $jarUrl")
        }
      }
      .map { _ =>
        executeCommand(
          os.proc(
            "java",
            s"-Dconfig.file=${FileNames.APPLICATION_CONF}",
            "-jar",
            os.Path(path),
            "fusion.schedulerx.worker.JobRunMain"),
          os.Path(runDir))
      }
      .recover { case e => ProcessResult(IntStatus.INTERNAL_ERROR, e.getLocalizedMessage) }
  }

  def executeCommand(proc: os.proc, cwd: os.Path): ProcessResult = {
    val invoked = proc.call(cwd = cwd)
    if (invoked.exitCode == 0) {
      ProcessResult(IntStatus.OK, "")
    } else {
      var message = invoked.out.trim()
      if (message.isBlank) {
        message = invoked.err.trim()
      }
      ProcessResult(IntStatus.INTERNAL_ERROR, s"Exit code is ${invoked.exitCode}, $message.")
    }
  }
}
