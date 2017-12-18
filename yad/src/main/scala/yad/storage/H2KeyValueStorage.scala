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

import java.io.File

import org.h2.mvstore._
import yad.api.{ApiJsonProtocol, DownloadRecordWithStatus}
import yad.downloader.DownloadId
import spray.json._


class H2KeyValueStorage(storageLocation: String)
  extends DownloadRecordStorage with ApiJsonProtocol {

  private val store: MVStore = {
    val location =
      if (storageLocation.startsWith("~")) {
        storageLocation.replaceFirst("~", System.getProperty("user.home"))
      } else {
        storageLocation
      }
    val storageFile = new File(location)
    storageFile.getParentFile.mkdirs()
    MVStore.open(location)
  }
  private val map: MVMap[String, String] = store.openMap("records")

  override def putRecord(recordWithStatus: DownloadRecordWithStatus,
                         forceCommit: Boolean): DownloadId = {
    val record = recordWithStatus.record
    val id = record.id.getOrElse(DownloadId())
    val recordWithId = if (record.id.isEmpty) record.copy(id = Some(id)) else record
    val jsonRecord = recordWithStatus.copy(record = recordWithId).toJson.compactPrint
    map.put(id.toString, jsonRecord)
    if (forceCommit) store.commit()
    id
  }

  override def removeRecord(id: DownloadId): Unit = {
    map.remove(id.toString)
  }

  override def getRecord(id: DownloadId): DownloadRecordWithStatus = {
    if (map.containsKey(id.toString)) {
      map.get(id.toString).parseJson.convertTo[DownloadRecordWithStatus]
    } else {
      throw new NoSuchElementException(s"Failed to find a record with id $id")
    }
  }

  override def getRecords(): scala.collection.Seq[DownloadRecordWithStatus] = {
    val values = map.values()
    val result = new scala.collection.mutable.ArrayBuffer[DownloadRecordWithStatus](values.size())
    val mapIterator = values.iterator()
    var idx: Int = 0
    while (mapIterator.hasNext) {
      val str = mapIterator.next()
      result += str.parseJson.convertTo[DownloadRecordWithStatus]
      idx += 1
    }
    result
  }

  override def close(): Unit = {
    store.close()
  }
}
