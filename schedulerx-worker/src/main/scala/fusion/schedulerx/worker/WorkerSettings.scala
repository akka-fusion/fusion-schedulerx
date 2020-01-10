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

import java.util.concurrent.TimeUnit

import akka.actor.typed.ActorSystem
import com.typesafe.config.Config
import fusion.schedulerx.Constants

import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.jdk.DurationConverters._

case class WorkerSettings(
    namespace: String,
    groupId: String,
    jobMaxConcurrent: Int,
    healthInterval: FiniteDuration,
    registerDelay: FiniteDuration,
    registerDelayMax: FiniteDuration,
    registerDelayFactor: Double,
    runOnce: Boolean,
    runJobWorkerActor: Option[String],
    runDir: Option[String]) {
  def computeRegisterDelay(delay: FiniteDuration): FiniteDuration = {
    delay match {
      case Duration.Zero => registerDelay
      case _ if delay < registerDelayMax =>
        val d = delay * registerDelayFactor
        FiniteDuration(d.toNanos, TimeUnit.NANOSECONDS).toCoarsest
      case _ => registerDelayMax
    }
  }
}

object WorkerSettings {
  def apply(system: ActorSystem[_]): WorkerSettings = apply(system.settings.config)

  def apply(config: Config): WorkerSettings = {
    val c = config.getConfig(s"${Constants.SCHEDULERX}.worker")
    WorkerSettings(
      c.getString("namespace"),
      c.getString("groupId"),
      c.getInt("jobMaxConcurrent"),
      c.getDuration("healthInterval").toScala.toCoarsest,
      c.getDuration("registerDelay").toScala.toCoarsest,
      c.getDuration("registerDelayMax").toScala.toCoarsest,
      c.getDouble("registerDelayFactor"),
      c.getBoolean("runOnce"),
      if (c.hasPath("runJobWorkerActor")) Some(c.getString("runJobWorkerActor")) else None,
      if (c.hasPath("runDir")) Some(c.getString("runDir")) else None)
  }
}
