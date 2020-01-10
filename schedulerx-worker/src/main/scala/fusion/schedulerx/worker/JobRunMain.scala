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

package fusion.schedulerx.worker

import akka.actor.typed.ActorRefResolver
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import fusion.json.jackson.Jackson
import fusion.schedulerx.protocol.{ JobInstanceData, Worker }
import fusion.schedulerx.worker.job.JobRun
import fusion.schedulerx.{ FileNames, SchedulerX }

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.NonFatal

object JobRunMain extends StrictLogging {
  def main(args: Array[String]): Unit = {
    val schedulerX = SchedulerX.fromOriginalConfig(ConfigFactory.load())
    try {
      val workerSettings = WorkerSettings(schedulerX.system)
      val runDir =
        workerSettings.runDir.getOrElse(throw new ExceptionInInitializerError("'runDir' not set."))
      val worker =
        ActorRefResolver(schedulerX.system).resolveActorRef[Worker.Command](
          workerSettings.runJobWorkerActor.getOrElse(
            throw new ExceptionInInitializerError("'runJobWorkerActor' not set.")))
      val instData =
        Jackson.defaultObjectMapper.treeToValue[JobInstanceData](
          Jackson.defaultObjectMapper.readTree(runDir.resolve(FileNames.INSTANCE_JSON).toFile))
      schedulerX.spawn(JobRun(worker, instData, workerSettings), instData.instanceId)
    } catch {
      case NonFatal(e) =>
        logger.error(s"Startup JobRun failure.", e)
        schedulerX.system.terminate()
        Await.ready(schedulerX.system.whenTerminated, 60.seconds)
        System.exit(-1)
    }
  }
}
