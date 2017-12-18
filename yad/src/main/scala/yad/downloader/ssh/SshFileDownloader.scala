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
package yad.downloader.ssh

import com.typesafe.config.Config
import net.schmizz.sshj.SSHClient
import yad.downloader._
import yad.downloader.ssh.SshFileDownloader._
import scala.concurrent.Future
import scala.util.control.NonFatal


class SshFileDownloader(host: String, port: Option[Int],
                        authMethod: AuthMethod, config: Config) extends SingleThreadFileDownloader {

  override protected val readBufferSize: Long = config.getMemorySize("yad.ssh.buffer-size").toBytes

  override protected def createRemoteFile(src: String): Future[RemoteFile] = {
    createSSHClient.map(c => new SshRemoteFile(c, src))
  }

  private def createSSHClient: Future[SSHClient] = {
    Future {
      val client = new SSHClient()
      try {
        client.loadKnownHosts()
        client.connect(host, port.getOrElse(DefaultPort))
        authMethod match {
          case PublicKeyAuthMethod(username, location) =>
            client.authPublickey(username, location)
          case PasswordAuthMethod(username, password) =>
            client.authPassword(username, password)
        }
        client
      } catch {
        case NonFatal(e) =>
          client.close()
          throw e
      }
    }
  }
}

object SshFileDownloader {
  val DefaultPort = 22
}
