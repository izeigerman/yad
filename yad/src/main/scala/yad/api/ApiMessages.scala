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

import yad.downloader.DownloadId
import yad.downloader.AuthMethod

case class DownloadRecord(id: Option[DownloadId],
                          protocol: String,
                          host: String, port: Option[Int],
                          authMethod: AuthMethod,
                          sourcePath: String, destinationPath: String)

case class DownloadRecordWithStatus(record: DownloadRecord, status: DownloadStatus,
                                    diagnostic: Option[String])

case class AddDownloadTask(record: DownloadRecord)
case class CancelDownloadTask(id: DownloadId)
case class PauseDownloadTask(id: DownloadId)
case class ResumeDownloadTask(id: DownloadId)
case class GetDownloadTaskProgress(id: DownloadId)

case class TaskNotFound(id: DownloadId, details: String)
case class OperationSucceeded(id: DownloadId)

case class DownloadTaskProgressResponse(id: DownloadId, downloadedBytes: Long,
                                        totalBytes: Long, progress: Double,
                                        bytesPerSecond: Double, estimatedTimeSeconds: Long)
