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

import java.io.{File, FileInputStream, FileOutputStream, OutputStream}
import java.nio.file.Paths
import java.security.MessageDigest

import akka.actor.ActorRef
import org.apache.commons.codec.binary.Hex
import org.slf4j.LoggerFactory
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import DownloadTask._

class DownloadTask(dst: String, taskId: Option[DownloadId],
                   remoteFile: => Future[RemoteFile], readBufferSize: Long,
                   statusReceiver: ActorRef, append: Boolean) {

  val id: DownloadId = taskId.getOrElse(DownloadId())

  private val reporter = new DownloadStatusReporter(id, statusReceiver)
  private val stateLock: Object = new Object
  private var paused: Boolean = false
  private var canceled: Boolean = false

  private val logger = LoggerFactory.getLogger(classOf[DownloadTask])

  private[downloader] def run()(implicit executionContext: ExecutionContext): Unit = {
    remoteFile.map { rFile =>
      try {
        downloadFile(rFile)
      } finally {
        rFile.close()
      }
    }.onComplete {
      case Success(_) =>
        if (!canceled) {
          reporter.onDownloadFinished()
        } else {
          reporter.onDownloadCanceled()
        }
      case Failure(e) =>
        if (!canceled) {
          reporter.onDownloadFailed(e)
        } else {
          reporter.onDownloadCanceled()
        }
    }
  }

  def pause(): Unit = {
    stateLock.synchronized {
      paused = true
      logger.debug(s"Paused task $id")
      reporter.onDownloadPaused()
    }
  }

  def isPaused(): Boolean = {
    paused
  }

  def resume(): Unit = {
    stateLock.synchronized {
      paused = false
      stateLock.notify()
      logger.debug(s"Resumed task $id")
      reporter.onDownloadResumed()
    }
  }

  def cancel(): Unit = {
    stateLock.synchronized {
      canceled = true
      paused = false
      stateLock.notify()
      logger.debug(s"Canceled task $id")
    }
  }

  private def downloadFile(rFile: RemoteFile): Unit = {
    val expectedHash = rFile.hash()
    logger.debug(s"Calculated a remote file hash: $expectedHash")
    val expectedSize = rFile.size()
    logger.debug(s"Retrieved a remote file size: $expectedSize")
    reporter.onDownloadStarted(expectedHash, expectedSize)
    val copyingPath = saveRemoteFileToTempLocation(rFile)
    val copyingFile = new File(copyingPath)
    if (!canceled) {
      copyingFile.renameTo(new File(dst))
      if (expectedHash.isDefined) {
        val actualHash = calculateLocalFileHash
        if (expectedHash.get != actualHash) {
          val srcPath = rFile.remotePath
          throw HashMismatchException(id,
            s"Hash mismatch for file $srcPath (expected=${expectedHash.get}, actual=$actualHash)")
        }
      }
    } else {
      copyingFile.delete()
    }
  }

  private def saveRemoteFileToTempLocation(rFile: RemoteFile): String = {
    val copyingPath = getCopyingPath(dst)
    val copyingFile = new File(copyingPath)

    val offset =
      if (append && copyingFile.exists()) {
        copyingFile.length()
      } else {
        0
      }

    val outStream = new FileOutputStream(copyingPath, append)
    try {
      readRemoteFile(rFile, outStream, offset)
    } finally {
      outStream.close()
    }
    copyingPath
  }

  private def readRemoteFile(rFile: RemoteFile,
                             outputStream: OutputStream,
                             offset: Long): Unit = {
    val buffer = new Array[Byte](readBufferSize.toInt)

    @tailrec
    def doRead(offset: Long): Unit = {
      val localIsCanceled = stateLock.synchronized {
        while (paused) stateLock.wait()
        canceled
      }
      if (!localIsCanceled) {
        val readResult = rFile.read(offset, buffer, 0, readBufferSize.toInt)
        if (readResult >= 0) {
          outputStream.write(buffer, 0, readResult)
          val downloadedBytes = offset + readResult
          reporter.onDownloadProgressUpdate(downloadedBytes)
          doRead(downloadedBytes)
        }
      } else {
        logger.info(s"The task $id has been canceled")
      }
    }

    try {
      doRead(offset)
    } finally {
      rFile.close()
    }
  }

  private def calculateLocalFileHash: String = {
    val md5Digest = MessageDigest.getInstance("MD5")
    val stream = new FileInputStream(dst)
    val buffer = new Array[Byte](readBufferSize.toInt)

    @tailrec
    def doCalculate(): Unit = {
      val readResult = stream.read(buffer)
      if (readResult >= 0) {
        md5Digest.update(buffer, 0, readResult)
        doCalculate()
      }
    }

    try {
      doCalculate()
      Hex.encodeHexString(md5Digest.digest())
    } finally {
      stream.close()
    }
  }
}

object DownloadTask {
  private def getCopyingPath(dst: String): String = {
    val dstPath = Paths.get(dst)
    val copyingFileName = s".${dstPath.getFileName}.copying"
    val copyingPath = dstPath.getParent.resolve(copyingFileName)
    copyingPath.toString
  }
}
