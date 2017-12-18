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

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.sftp.{SFTPClient, RemoteFile => SshjRemoteFile}
import yad.downloader.RemoteFile
import scala.util.Try

private[ssh] final class SshRemoteFile(client: SSHClient, file: String) extends RemoteFile {

  private val sftpClient: SFTPClient = client.newSFTPClient()
  private val remoteFile: SshjRemoteFile = sftpClient.open(file)
  private var isClosed: Boolean = false

  override def remotePath: String = file

  override def size(): Long = {
    checkIsClosed()
    sftpClient.size(file)
  }

  override def hash(): Option[String] = {
    checkIsClosed()
    withSession(client) { session =>
      val hashCmd = session.exec(s"md5sum -b $file")
      val result = IOUtils.readFully(hashCmd.getInputStream()).toString().split(" ")(0)
      if (result.nonEmpty) Some(result) else None
    }
  }

  override def read(fileOffset: Long, to: Array[Byte], offset: Int, len: Int): Int = {
    checkIsClosed()
    remoteFile.read(fileOffset, to, offset, len)
  }

  override def close(): Unit = {
    Try(remoteFile.close())
    Try(sftpClient.close())
    Try(client.close())
    isClosed = true
  }

  private def withSession[T](client: SSHClient)(f: Session => T): T = {
    val session = client.startSession()
    try {
      f(session)
    } finally {
      session.close()
    }
  }

  private def checkIsClosed(): Unit = {
    if (isClosed) throw new IllegalStateException("The SSH Remote File is closed")
  }
}
