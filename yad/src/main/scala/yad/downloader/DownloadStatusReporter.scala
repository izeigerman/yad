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
package yad.downloader

import akka.actor.ActorRef

class DownloadStatusReporter(id: DownloadId, receiver: ActorRef) {

  def onDownloadStarted(hash: Option[String], totalBytes: Long): DownloadStarted = {
    val msg = DownloadStarted(id, System.currentTimeMillis(), hash, totalBytes)
    receiver ! msg
    msg
  }

  def onDownloadProgressUpdate(downloadedBytes: Long): DownloadProgressUpdate = {
    val msg = DownloadProgressUpdate(id, System.currentTimeMillis(), downloadedBytes)
    receiver ! msg
    msg
  }

  def onDownloadPaused(): DownloadPaused = {
    val msg = DownloadPaused(id, System.currentTimeMillis())
    receiver ! msg
    msg
  }

  def onDownloadResumed(): DownloadResumed = {
    val msg = DownloadResumed(id, System.currentTimeMillis())
    receiver ! msg
    msg
  }

  def onDownloadFinished(): DownloadFinished = {
    val msg = DownloadFinished(id, System.currentTimeMillis())
    receiver ! DownloadFinished(id, System.currentTimeMillis())
    msg
  }

  def onDownloadCanceled(): DownloadCanceled = {
    val msg = DownloadCanceled(id, System.currentTimeMillis())
    receiver ! DownloadCanceled(id, System.currentTimeMillis())
    msg
  }

  def onDownloadFailed(reason: Throwable): DownloadFailed = {
    val msg = DownloadFailed(id, System.currentTimeMillis(), reason)
    receiver ! DownloadFailed(id, System.currentTimeMillis(), reason)
    msg
  }
}
