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

import spray.json.DefaultJsonProtocol
import yad.downloader._

trait ApiJsonProtocol extends DefaultJsonProtocol
                         with AuthMethodJsonProtocol
                         with DownloadIdJsonProtocol
                         with DownloadStatusJsonProtocol {
  implicit val downloadRecordFormat = jsonFormat7(DownloadRecord.apply)
  implicit val downloadRecordWithStatusFormat = jsonFormat3(DownloadRecordWithStatus.apply)
  implicit val addDownloadTaskFormat = jsonFormat1(AddDownloadTask.apply)

  implicit val operationSucceededFormat = jsonFormat1(OperationSucceeded.apply)
  implicit val taskNotFoundFormat = jsonFormat2(TaskNotFound.apply)
  implicit val progressResponseFormat = jsonFormat6(DownloadTaskProgressResponse.apply)
}

object ApiJsonProtocol extends ApiJsonProtocol
