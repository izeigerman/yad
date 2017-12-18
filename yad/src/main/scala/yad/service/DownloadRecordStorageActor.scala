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

import akka.actor._
import yad.api._
import yad.downloader._
import yad.storage.DownloadRecordStorage

import scala.util.Try


class DownloadRecordStorageActor extends Actor {

  private val storage: DownloadRecordStorage = DownloadRecordStorage(
    context.system.settings.config)

  override def receive: Receive = {
    downloadStatusUpdate orElse retrieveDownloadStatus
  }

  private def downloadStatusUpdate: Receive = {
    case AddDownloadTask(record) =>
      storage.putRecord(DownloadRecordWithStatus(record, DownloadStatusPending, None), true)

    case DownloadStarted(id, _, _, _) =>
      updateRecordStatus(id, DownloadStatusInProgress, forceCommit = false)

    case DownloadPaused(id, _) =>
      updateRecordStatus(id, DownloadStatusPaused)

    case DownloadResumed(id, _) =>
      updateRecordStatus(id, DownloadStatusInProgress)

    case DownloadFinished(id, _) =>
      updateRecordStatus(id, DownloadStatusFinished)

    case DownloadFailed(id, _, reason) =>
      updateRecordStatus(id, DownloadStatusFailed, Some(reason.getMessage))

    case DownloadCanceled(id, _) =>
      updateRecordStatus(id, DownloadStatusCanceled, None)
  }

  private def retrieveDownloadStatus: Receive = {
    case GetUnfinishedRecords =>
      sender() ! storage.getRecords()
        .filter(r => Seq(DownloadStatusInProgress,
          DownloadStatusPending, DownloadStatusPaused).contains(r.status)
        )

    case GetAllRecords =>
      sender() ! storage.getRecords()

    case GetRecord(id) =>
      val result = Try(storage.getRecord(id)).recover {
        case _: NoSuchElementException =>
          TaskNotFound(id, "The download task doesn't exist")
      }.get
      sender() ! result
  }

  private def updateRecordStatus(id: DownloadId, newStatus: DownloadStatus,
                                 diagnostic: Option[String] = None,
                                 forceCommit: Boolean = true): Unit = {
    val oldRecord = storage.getRecord(id)
    if (oldRecord.status != newStatus || oldRecord.diagnostic != diagnostic) {
      val updatedRecord = oldRecord.copy(status = newStatus, diagnostic = diagnostic)
      storage.putRecord(updatedRecord, forceCommit)
    }
  }

  override def postStop(): Unit = {
    storage.close()
  }
}

object DownloadRecordStorageActor {
  def apply()(implicit factory: ActorRefFactory): ActorRef = {
    factory.actorOf(Props(classOf[DownloadRecordStorageActor]), name = "downloadRecordStorage")
  }
}
