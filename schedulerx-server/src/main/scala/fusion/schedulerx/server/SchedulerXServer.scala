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

package fusion.schedulerx.server

import java.util.concurrent.TimeoutException

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardingEnvelope
import fusion.common.FusionProtocol
import fusion.schedulerx._
import fusion.schedulerx.server.repository.BrokerRepository

class SchedulerXServer(schedulerX: SchedulerX) {
  implicit val system: ActorSystem[FusionProtocol.Command] = schedulerX.system
  private var _guardianIds: Set[String] = Set()

  def brokerIds: Set[String] = _guardianIds

  @throws[TimeoutException]
  def start(): SchedulerXServer = {
    val guardianRegion = BrokerImpl.init(system)
    _guardianIds = BrokerRepository(system)
      .listBroker()
      .map { brokerInfo =>
        guardianRegion ! ShardingEnvelope(
          brokerInfo.namespace,
          BrokerImpl.InitParameters(brokerInfo.namespace, brokerInfo))
        brokerInfo.namespace
      }
      .toSet
    SchedulerXServer._instance = this
    this
  }
}

object SchedulerXServer {
  private var _instance: SchedulerXServer = _

  def instance: SchedulerXServer = _instance
  def apply(schedulerX: SchedulerX): SchedulerXServer = new SchedulerXServer(schedulerX)
}
