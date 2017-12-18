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
package yad.storage

import com.typesafe.config.Config
import yad.api.DownloadRecordWithStatus
import yad.downloader.DownloadId

trait DownloadRecordStorage {
  def putRecord(record: DownloadRecordWithStatus, forceCommit: Boolean): DownloadId

  def removeRecord(id: DownloadId): Unit

  def getRecord(id: DownloadId): DownloadRecordWithStatus

  def getRecords(): scala.collection.Seq[DownloadRecordWithStatus]

  def close(): Unit
}

object DownloadRecordStorage {
  def apply(config: Config): DownloadRecordStorage = {
    val storageType = config.getString("yad.storage.type")
    storageType match {
      case "h2" =>
        val location = config.getString("yad.storage.h2.location")
        new H2KeyValueStorage(location)
      case _ =>
        throw new IllegalArgumentException(s"Unknown storage type $storageType")
    }
  }
}
