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

import java.util.concurrent.Executors

import akka.actor.ActorRef
import scala.concurrent.{ExecutionContext, Future}

private[downloader] abstract class SingleThreadFileDownloader extends FileDownloader {

  private val executorService = Executors.newSingleThreadExecutor()
  protected implicit val executionContext: ExecutionContext =
    ExecutionContext.fromExecutor(executorService)

  /** The maximum size of the read buffer. */
  protected def readBufferSize(): Long

  /** Creates a future instance of a concrete [[RemoteFile]].
    *
    * @param src the path to the file on the remote server.
    * @return a future instance of the remote file.
    */
  protected def createRemoteFile(src: String): Future[RemoteFile]

  override def download(dst: String, src: String,
                        statusReceiver: ActorRef,
                        id: Option[DownloadId]): DownloadTask = {
    val task = new DownloadTask(dst, id, createRemoteFile(src),
      readBufferSize, statusReceiver, true)
    task.run()
    task
  }

  override def close(): Unit = {
    executorService.shutdown()
  }
}
