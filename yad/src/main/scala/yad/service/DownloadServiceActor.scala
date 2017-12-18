/*
 * Copyright 2017 Iaroslav Zeigerman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package yad.service

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.typesafe.config.Config
import yad.api._
import yad.downloader._
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import DownloadId._
import DownloadServiceActor._

class DownloadServiceActor extends Actor with ActorLogging {

  private val maxParallelTasks: Int = context.system.settings
    .config.getInt("yad.max-active-tasks")
  private val pendingTasks: mutable.Queue[(DownloadId, DownloadRecord)] = mutable.Queue.empty
  private val pausedTasks: mutable.Map[DownloadId, ActorRef] = mutable.Map.empty
  private val activeTasks: mutable.Map[DownloadId, ActorRef] = mutable.Map.empty

  private val storageService: ActorRef = DownloadRecordStorageActor()

  private implicit val dispatcher: ExecutionContext = context.dispatcher
  private implicit val storageResponseTimeout: Timeout = StorageReceiveTimeout

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy()(TaskDecider)

  override def receive: Receive = {
    addTaskReceive orElse downloadControlsReceive orElse terminatedReceive
  }

  private def addTaskReceive: Receive = {
    case AddDownloadTask(record) =>
      val taskId = record.id.getOrElse(DownloadId())
      submitTask(taskId, record)
      storageService ! AddDownloadTask(record.copy(id = Some(taskId)))
      sender() ! OperationSucceeded(taskId)
    case AddMultipleDownloadTasks(tasks) =>
      var numTasks: Int = 0
      tasks.sortBy(_.record.id).foreach(recordWithStatus => {
        val record = recordWithStatus.record
        if (recordWithStatus.status != DownloadStatusPaused) {
          submitTask(record.id.get, record)
          numTasks += 1
        } else {
          val taskActor = createDownloadTaskActor(record.id.get, record)
          taskActor ! PauseDownloadTask(record.id.get)
          pausedTasks.put(record.id.get, taskActor)
        }
      })
      log.info(s"Submitted $numTasks tasks from the storage")
  }

  private def terminatedReceive: Receive = {
    case Terminated(ref) =>
      val id = DownloadId.fromString(ref.path.name)
      log.debug(s"The download task $id has been terminated")
      activeTasks.remove(id)
      pausedTasks.remove(id)
      if (activeTasks.size < maxParallelTasks && pendingTasks.nonEmpty) {
        val (nextTaskId, nextTaskRequest) = pendingTasks.dequeue()
        submitTask(nextTaskId, nextTaskRequest)
      }
  }

  private def downloadControlsReceive: Receive = {
    case c @ CancelDownloadTask(id) => forwardControlMessage(id, c)
    case p: PauseDownloadTask => pauseTask(p)
    case r: ResumeDownloadTask => resumeTask(r)
    case g @ GetDownloadTaskProgress(id) => forwardControlMessage(id, g)
    case GetAllRecords => storageService.forward(GetAllRecords)
    case GetUnfinishedRecords => storageService.forward(GetUnfinishedRecords)
    case getRecord: GetRecord => storageService.forward(getRecord)
  }

  private def pauseTask(p: PauseDownloadTask): Unit = {
    val id = p.id
    forwardControlMessage(id, p)
    if (activeTasks.contains(id)) {
      pausedTasks.put(id, activeTasks(id))
      activeTasks.remove(id)
      val (nextTaskId, nextTaskRequest) = pendingTasks.dequeue()
      submitTask(nextTaskId, nextTaskRequest)
    }
  }

  private def resumeTask(r: ResumeDownloadTask): Unit = {
    val id = r.id
    if (pausedTasks.contains(id)) {
      if (activeTasks.size < maxParallelTasks) {
        // We can just reuse the existing download task actor since there are available slots.
        log.info(s"Resuming the download task $id")
        activeTasks.put(id, pausedTasks(id))
        forwardControlMessage(id, r)
      } else {
        // There are no available slots. We should kill the existing download task actor and submit
        // this task from scratch.
        log.info(s"Can't resume the download task $id, since there are no available slots. " +
          "Submitting this task from scratch")
        val taskActor = pausedTasks(id)
        context.stop(taskActor)
        (storageService ? GetRecord(id))
          .mapTo[DownloadRecordWithStatus]
          .map(r => AddDownloadTask(r.record))
          .pipeTo(self)
        sender() ! OperationSucceeded(id)
      }
      pausedTasks.remove(id)
    } else {
      sender() ! TaskNotFound(id, "The are no paused tasks with the specified ID")
    }
  }

  private def forwardControlMessage(id: DownloadId, msg: Any): Unit = {
    if (activeTasks.contains(id)) {
      activeTasks(id).forward(msg)
    } else {
      sender() ! TaskNotFound(id, "The task doesn't exist or has been finished already")
    }
  }

  private def createDownloadTaskActor(taskId: DownloadId, record: DownloadRecord): ActorRef = {
    val config = context.system.settings.config
    DownloadTaskActor(
      () => createDownloader(record, config),
      record.destinationPath, record.sourcePath,
      storageService, Some(taskId))
  }

  private def submitTask(taskId: DownloadId, record: DownloadRecord): Unit = {
    if (activeTasks.size < maxParallelTasks) {
      log.debug(s"Launching the download task $taskId")
      val taskActor = createDownloadTaskActor(taskId, record)
      activeTasks.put(taskId, taskActor)
      context.watch(taskActor)
    } else {
      log.debug(s"Adding the download task $taskId to the queue")
      pendingTasks.enqueue(taskId -> record)
    }
  }

  override def preStart(): Unit = {
    val unfinished = (storageService ? GetUnfinishedRecords)
      .mapTo[scala.collection.Seq[DownloadRecordWithStatus]]
    unfinished.map(tasks => AddMultipleDownloadTasks(tasks)).pipeTo(self)
  }
}

object DownloadServiceActor {
  private val StorageReceiveTimeoutMs = 30000
  private val StorageReceiveTimeout = Timeout(StorageReceiveTimeoutMs, TimeUnit.MILLISECONDS)

  def apply()(implicit factory: ActorRefFactory): ActorRef = {
    factory.actorOf(Props(classOf[DownloadServiceActor]), name = "downloadService")
  }

  private def createDownloader(record: DownloadRecord, config: Config): FileDownloader = {
    FileDownloader(record.protocol, record.host, record.port, record.authMethod, config)
  }

  private val CustomTaskDecider: SupervisorStrategy.Decider = {
    case e: HashMismatchException => SupervisorStrategy.Stop
  }

  private val TaskDecider: SupervisorStrategy.Decider = {
    CustomTaskDecider orElse SupervisorStrategy.defaultDecider
  }

  private case class AddMultipleDownloadTasks(records: Seq[DownloadRecordWithStatus])
}
