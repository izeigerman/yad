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
import com.typesafe.config.Config
import yad.downloader.ssh.SshFileDownloader

/** The file downloader interface. */
trait FileDownloader {
  /** Downloads the remote file which is located at "src" and saves it to
    * the local file located at "dst".
    *
    * @param dst the local path where the remote file will be saved.
    * @param src the location of the file on a remote server.
    * @param statusReceiver the actor reference that is going to
    *                       receive download status updates.
    * @param id the optional download task ID. If not specified a new
    *           ID will be generated.
    * @return a new download task.
    */
  def download(dst: String, src: String,
               statusReceiver: ActorRef,
               id: Option[DownloadId]): DownloadTask

  /** Cleans up downloader's allocated resources and active sessions. */
  def close(): Unit
}

object FileDownloader {

  case class DownloaderNotFound(msg: String) extends Exception(msg)

  def apply(protocol: String, host: String, port: Option[Int],
            authMethod: AuthMethod, config: Config): FileDownloader = {
    protocol.toLowerCase match {
      case "ssh" => new SshFileDownloader(host, port, authMethod, config)
      case _ => throw DownloaderNotFound(
        s"Failed to create downloader instance for the protocol $protocol")
    }
  }
}
