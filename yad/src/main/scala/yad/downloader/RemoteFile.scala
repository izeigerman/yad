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

/** The remote file representation. */
trait RemoteFile {
  /** The location of the resource on a remote endpoint.
    *
    * @return a remote location of the resource.
    */
  def remotePath: String

  /** Retrieves a size of this remote file.
    *
    * @return the size of the remote file.
    */
  def size(): Long

  /** Retrieves an MD5 hash of this remote file.
    *
    * Note: this operation might not be supported by a concrete
    * implementation. In this case None is returned.
    *
    * @return the MD5 hash of the remote file if this operation
    *         is supported, or None - otherwise.
    */
  def hash(): Option[String]

  /** Reads data from the remote file into the given byte buffer.
    *
    * @param fileOffset the offset of the remote file.
    * @param to the byte buffer.
    * @param offset the offset in the buffer.
    * @param len the size of the buffer.
    * @return a number of retrieved bytes.
    */
  def read(fileOffset: Long, to: Array[Byte], offset: Int, len: Int): Int

  /** Closes this remote file. */
  def close(): Unit
}
