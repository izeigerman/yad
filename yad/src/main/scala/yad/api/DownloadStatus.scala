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
package yad.api

import spray.json._

trait DownloadStatus {
  def value: String
}

abstract class AbstractDownloadStatus(val value: String) extends DownloadStatus
case object DownloadStatusPending extends AbstractDownloadStatus("PENDING")
case object DownloadStatusInProgress extends AbstractDownloadStatus("IN_PROGRESS")
case object DownloadStatusPaused extends AbstractDownloadStatus("PAUSED")
case object DownloadStatusFinished extends AbstractDownloadStatus("FINISHED")
case object DownloadStatusFailed extends AbstractDownloadStatus("FAILED")
case object DownloadStatusCanceled extends AbstractDownloadStatus("CANCELED")


object DownloadStatus {
  def fromString(str: String): DownloadStatus = {
    str match {
      case DownloadStatusPending.value => DownloadStatusPending
      case DownloadStatusInProgress.value => DownloadStatusInProgress
      case DownloadStatusPaused.value => DownloadStatusPaused
      case DownloadStatusFailed.value => DownloadStatusFailed
      case DownloadStatusFinished.value => DownloadStatusFinished
      case DownloadStatusCanceled.value => DownloadStatusCanceled
    }
  }
}

trait DownloadStatusJsonProtocol extends DefaultJsonProtocol {
  implicit val downloadStatusFormat = new RootJsonFormat[DownloadStatus] {
    override def read(json: JsValue): DownloadStatus = {
      DownloadStatus.fromString(json.convertTo[String])
    }

    override def write(obj: DownloadStatus): JsValue = {
      JsString(obj.value)
    }
  }
}

object DownloadStatusJsonProtocol extends DownloadStatusJsonProtocol
