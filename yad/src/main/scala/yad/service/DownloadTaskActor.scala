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
import yad.api._
import yad.downloader._
import scala.concurrent.duration._
import DownloadTaskActor._

class DownloadTaskActor(downloaderFactory: => FileDownloader,
                        dst: String, src: String,
                        taskId: DownloadId,
                        storageService: ActorRef)
  extends Actor with ActorLogging with Stash {

  private val retryInterval: Long = context.system.settings.config
    .getDuration("yad.retry-interval", TimeUnit.MILLISECONDS)
  private val downloader: FileDownloader = downloaderFactory
  private var downloadTask: Option[DownloadTask] = None
  private var lastReportedProgress: Double = 0.0d
  private var lastReportedBytes: Long = 0
  private var lastReportedTs: Long = 0
  private var bytesSinceReportedTs: Long = 0
  private var lastReportedBytesPerSecond: Double = 0.0d
  private var downloadSize: Long = 0

  override def receive: Receive = {
    case StartDownload =>
      context.become(downloadInProgress)
      downloadTask = Some(downloader.download(dst, src, self, Some(taskId)))
      unstashAll()
    case _ =>
      stash()
  }

  private def downloadInProgress: Receive = {
    downloadControls orElse downloadStarted orElse downloadProgressUpdate orElse
      downloadFinished orElse pausedResumedUpdate
  }

  private def downloadControls: Receive = {
    case PauseDownloadTask(id) =>
      processControlOp(id, _.pause())
    case ResumeDownloadTask(id) =>
      processControlOp(id, _.resume())
    case CancelDownloadTask(id) =>
      processControlOp(id, _.cancel())
    case GetDownloadTaskProgress(id) =>
      val estimatedTimeSeconds =
        (downloadSize - lastReportedBytes).toDouble / lastReportedBytesPerSecond
      sender() ! DownloadTaskProgressResponse(id, lastReportedBytes,
        downloadSize, lastReportedProgress, lastReportedBytesPerSecond,
        estimatedTimeSeconds.toLong)
  }

  private def downloadStarted: Receive = {
    case msg @ DownloadStarted(id, ts, hash, size) =>
      log.info(s"Started the download task $id (hash=$hash size=$size)")
      downloadSize = size
      storageService ! msg
  }

  private def downloadProgressUpdate: Receive = {
    case DownloadProgressUpdate(id, ts, bytes) =>
      updateProgress(id, bytes)
      updateSpeed(id, bytes, ts)
  }

  private def downloadFinished: Receive = {
    case msg @ DownloadFinished(id, _) =>
      log.debug(s"The download task $id has been finished successfully")
      storageService ! msg
      context.stop(self)

    case msg @ DownloadFailed(_, _, reason) =>
      storageService ! msg
      throw reason

    case msg @ DownloadCanceled(id, _) =>
      log.debug(s"The download task $id has been canceled")
      storageService ! msg
      context.stop(self)
  }

  private def pausedResumedUpdate: Receive = {
    case msg @ (_: DownloadPaused | _: DownloadResumed) =>
      lastReportedBytesPerSecond = 0.0d
      storageService ! msg
  }

  private def updateProgress(id: DownloadId, bytes: Long): Unit = {
    lastReportedBytes = bytes
    lastReportedProgress =
      Math.round((bytes.toDouble / downloadSize.toDouble) * 10000).toDouble / 100
  }

  private def updateSpeed(id: DownloadId, bytes: Long, ts: Long): Unit = {
    if (lastReportedTs > 0) {
      val timeDiff = ts - lastReportedTs
      val bytesDiff = bytes - bytesSinceReportedTs
      val bytesPerSecond = bytesDiff.toDouble / (timeDiff.toDouble / 1000.0)
      lastReportedTs = ts
      bytesSinceReportedTs = bytes
      lastReportedBytesPerSecond = bytesPerSecond
    } else {
      lastReportedTs = ts
      bytesSinceReportedTs = bytes
    }
  }

  private def processControlOp[T](id: DownloadId, f: DownloadTask => T): Unit = {
    downloadTask.foreach(f)
    sender() ! OperationSucceeded(id)
  }

  override def preStart(): Unit = {
    self ! StartDownload
  }

  override def postRestart(reason: Throwable): Unit = {
    implicit val dispatcher = context.dispatcher
    log.debug(s"Restarting the download task $taskId in $retryInterval milliseconds")
    context.system.scheduler.scheduleOnce(retryInterval.milliseconds, self, StartDownload)
  }

  override def postStop(): Unit = {
    downloader.close()
  }
}

object DownloadTaskActor {
  private case object StartDownload

  def apply(downloaderFactory: () => FileDownloader, dst: String,
            src: String, storageService: ActorRef,
            id: Option[DownloadId] = None)
           (implicit factory: ActorRefFactory): ActorRef = {
    val taskId = id.getOrElse(DownloadId())
    factory.actorOf(
      Props(classOf[DownloadTaskActor], downloaderFactory, dst, src, taskId, storageService),
      name = taskId.toString)
  }
}
