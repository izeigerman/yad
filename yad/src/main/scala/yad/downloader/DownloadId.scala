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

import java.util.concurrent.atomic.AtomicInteger

import spray.json._

case class DownloadId(timestamp: Long, counter: Int) {
  override def toString: String = s"$timestamp-$counter"
}

case object DownloadId {
  private val downloadCounter: AtomicInteger = new AtomicInteger(0)
  private val timestamp: Long = System.currentTimeMillis()

  def apply(): DownloadId = {
    val nextCounter = downloadCounter.getAndIncrement()
    DownloadId(timestamp, nextCounter)
  }

  def fromString(str: String): DownloadId = {
    val split = str.trim.split("-")
    DownloadId(split(0).toLong, split(1).toInt)
  }

  implicit object DownloadIdOrdering extends Ordering[DownloadId] {
    override def compare(x: DownloadId, y: DownloadId): Int = {
      val tsCompare = x.timestamp.compareTo(y.timestamp)
      if (tsCompare == 0) {
        x.counter.compareTo(y.counter)
      } else {
        tsCompare
      }
    }
  }

}

trait DownloadIdJsonProtocol extends DefaultJsonProtocol {
  implicit val downloadIdFormat = new RootJsonFormat[DownloadId] {
    override def read(json: JsValue): DownloadId = {
      DownloadId.fromString(json.convertTo[String])
    }

    override def write(obj: DownloadId): JsValue = {
      JsString(obj.toString)
    }
  }
}

object DownloadIdJsonProtocol extends DownloadIdJsonProtocol
