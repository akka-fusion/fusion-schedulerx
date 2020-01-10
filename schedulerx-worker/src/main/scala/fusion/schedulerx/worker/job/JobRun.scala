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

package fusion.schedulerx.worker.job

import java.nio.file.Files
import java.time.OffsetDateTime

import akka.actor.typed._
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, TimerScheduler }
import akka.http.scaladsl.model.StatusCodes
import fusion.common.FusionProtocol
import fusion.json.jackson.Jackson
import fusion.schedulerx.Constants
import fusion.schedulerx.job.ProcessResult
import fusion.schedulerx.protocol.{ JobInstanceData, JobType, Worker }
import fusion.schedulerx.worker.job.internal.WorkerJobContextImpl
import fusion.schedulerx.worker.util.WorkerUtils
import fusion.schedulerx.worker.{ WorkerImpl, WorkerSettings }
import helloscala.common.IntStatus

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

object JobRun {
  sealed trait JobRunCommand extends Worker.Command

  private case class JobResult(result: Try[ProcessResult]) extends JobRunCommand
  private case object JobTimeout extends JobRunCommand

  def apply(
      worker: ActorRef[Worker.Command],
      instanceData: JobInstanceData,
      workerSettings: WorkerSettings): Behavior[JobRunCommand] =
    Behaviors.setup(context =>
      Behaviors.withTimers(timers =>
        new JobRun(worker, instanceData, workerSettings, timers, context).runJob(context.system)))
}

import fusion.schedulerx.worker.job.JobRun._
class JobRun private (
    worker: ActorRef[Worker.Command],
    instanceData: JobInstanceData,
    workerSettings: WorkerSettings,
    timers: TimerScheduler[JobRunCommand],
    context: ActorContext[JobRunCommand]) {
  private val runDir = workerSettings.createOrGetRunDir(instanceData.instanceId)
  timers.startSingleTimer(JobTimeout, instanceData.timeout)

  def cleanup(): Unit = {
    if (workerSettings.runOnce) {
      context.system.terminate()
    }
  }

  def runJob(system: ActorSystem[_]): Behavior[JobRunCommand] = {
    implicit val ec = system.dispatchers.lookup(DispatcherSelector.fromConfig(Constants.Dispatcher.WORKER_BLOCK))

    val jobContext = createJobContext()
    instanceData.`type` match {
      case JobType.JAVA =>
        if (workerSettings.runOnce)
          context.pipeToSelf(runClassJob(jobContext))(JobResult)
        else
          instanceData.jarUrl match {
            case Some(jarUrl) =>
              runJarJob(jarUrl, jobContext).onComplete {
                case Success(result) if result.status == 200 => // do nothing
                case value                                   => context.self ! JobResult(value)
              }
            case None =>
              context.pipeToSelf(runClassJob(jobContext))(JobResult)
          }
      case _ =>
        context.pipeToSelf(runShellJob())(JobResult)
    }
    awaitReceive()
  }

  private def awaitReceive(): Behavior[JobRunCommand] =
    Behaviors
      .receiveMessage[JobRunCommand] {
        case JobTimeout =>
          val result = ProcessResult(StatusCodes.InternalServerError.intValue, "Timeout")
          worker ! WorkerImpl.JobInstanceResult(instanceData.instanceId, result, context.self)
          resultWriteToDir(result)
          Behaviors.stopped
        case JobResult(value) =>
          val result = value match {
            case Success(v)         => v
            case Failure(exception) => ProcessResult(500, exception.toString)
          }
          worker ! WorkerImpl.JobInstanceResult(instanceData.instanceId, result, context.self)
          resultWriteToDir(result)
          Behaviors.stopped
      }
      .receiveSignal {
        case (_, PostStop) =>
          cleanup()
          Behaviors.same
      }

  private def runShellJob()(implicit ec: ExecutionContext): Future[ProcessResult] =
    Future {
      val codeContent = instanceData.codeContent.getOrElse(
        throw new IllegalAccessException(s"${instanceData.`type`} 任务需要指定 'codeContent'。"))
      val path = Files.createTempFile(instanceData.instanceId, s".${instanceData.`type`.ext}")
      Files.write(path, codeContent.getBytes())
      WorkerUtils.executeCommand(os.proc(instanceData.`type`.name, os.Path(path)), os.Path(runDir))
    }.recover { case e => ProcessResult(IntStatus.INTERNAL_ERROR, e.getLocalizedMessage) }

  private def runJarJob(jarUrl: String, jobContext: WorkerJobContextImpl)(
      implicit ec: ExecutionContext): Future[ProcessResult] = {
    implicit val system = context.system
    try {
      WorkerUtils.writeJobInstanceDir(runDir, instanceData, worker)
      WorkerUtils.runJar(runDir, jarUrl)
    } catch {
      case e: Throwable => Future.successful(ProcessResult(IntStatus.INTERNAL_ERROR, e.getLocalizedMessage))
    }
  }

  private def runClassJob(jobContext: WorkerJobContextImpl)(implicit ec: ExecutionContext): Future[ProcessResult] =
    Future {
      val mainClass =
        instanceData.mainClass.getOrElse(
          throw new IllegalAccessException(s"${instanceData.`type`} 作业需要指定 'mainClass'。"))
      val clz = Class.forName(mainClass)
      val system = context.system
      val tryValue =
        if (classOf[MapReduceJobProcessor].isAssignableFrom(clz))
          system.dynamicAccess.createInstanceFor[MapReduceJobProcessor](clz, Nil)
        else if (classOf[MapJobProcessor].isAssignableFrom(clz))
          system.dynamicAccess.createInstanceFor[MapJobProcessor](clz, Nil)
        else system.dynamicAccess.createInstanceFor[JobProcessor](clz, Nil)
      val processor = tryValue match {
        case Success(value) => value
        case Failure(e)     => throw e
      }
      // TODO MapJobProcessor 和 MapReduceJobProcessor 怎么执行？
      processor.preStart(jobContext)
      val result = processor.execute(jobContext)
      processor.postStop(jobContext) match {
        case ProcessResult.Empty => result
        case value               => value
      }
    }.recover { case e => ProcessResult(IntStatus.INTERNAL_ERROR, e.getLocalizedMessage) }

  private def createJobContext(): WorkerJobContextImpl = {
    // TODO
    WorkerJobContextImpl(
      instanceData.instanceId,
      instanceData.name,
      instanceData.`type`,
      Map(),
      Map(),
      Nil,
      instanceData.schedulerTime,
      OffsetDateTime.now(),
      "",
      0,
      null,
      Map())(context.system.asInstanceOf[ActorSystem[FusionProtocol.Command]])
  }

  def resultWriteToDir(result: ProcessResult): Unit = {
    Files.write(runDir.resolve("result.json"), Jackson.defaultObjectMapper.writeValueAsBytes(result))
  }
}
