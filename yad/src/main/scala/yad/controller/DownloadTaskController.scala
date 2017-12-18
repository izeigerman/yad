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
package yad.controller

import akka.actor.ActorRef
import akka.pattern.ask
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.PathMatcher.{Matched, Matching, Unmatched}
import akka.http.scaladsl.server.{Directives, PathMatcher1, Route}
import akka.util.Timeout
import spray.json.PrettyPrinter
import yad.api._
import yad.downloader.DownloadId
import yad.service.{GetAllRecords, GetRecord, GetUnfinishedRecords}

import scala.concurrent.ExecutionContext
import scala.util.Try
import DownloadTaskController._

class DownloadTaskController(service: ActorRef)(implicit dispatcher: ExecutionContext,
                                                timeout: Timeout)
  extends Directives with SprayJsonSupport with ApiJsonProtocol {

  private implicit val printer = PrettyPrinter

  val route: Route =
    pathPrefix("tasks") {
      pathEndOrSingleSlash {
        get {
          val result = (service ? GetAllRecords).mapTo[Seq[DownloadRecordWithStatus]]
          onSuccess(result)(statuses => complete(StatusCodes.OK -> statuses))
        } ~
        post {
          entity(as[DownloadRecord]) { record =>
            val result = (service ? AddDownloadTask(record)).mapTo[OperationSucceeded]
            onSuccess(result)(r => complete(StatusCodes.Accepted -> r))
          }
        }
      } ~
      path("unfinished") {
        get {
          val result = (service ? GetUnfinishedRecords).mapTo[Seq[DownloadRecordWithStatus]]
          onSuccess(result)(statuses => complete(StatusCodes.OK -> statuses))
        }
      } ~
      pathPrefix(DownloadIdMatcher) { taskId =>
        pathEndOrSingleSlash {
          get {
            val result = (service ? GetRecord(taskId))
            onSuccess(result) {
              case success: DownloadRecordWithStatus =>
                complete(StatusCodes.OK -> success)
              case notFound: TaskNotFound =>
                complete(StatusCodes.NotFound -> notFound)
            }
          }
        } ~
        path(TaskOperationMatcher) { op =>
          get {
            val message = taskOperationToMessage(op, taskId)
            val result = (service ? message)
            onSuccess(result) {
              case success: OperationSucceeded =>
                complete(StatusCodes.OK -> success)
              case progress: DownloadTaskProgressResponse =>
                complete(StatusCodes.OK -> progress)
              case notFound: TaskNotFound =>
                complete(StatusCodes.NotFound -> notFound)
            }
          }
        }
      }
    }
}

object DownloadTaskController {

  sealed trait TaskOperation
  case object CancelTask extends TaskOperation
  case object PauseTask extends TaskOperation
  case object ResumeTask extends TaskOperation
  case object GetTaskProgress extends TaskOperation

  private object DownloadIdMatcher extends PathMatcher1[DownloadId] {
    override def apply(path: Path): Matching[Tuple1[DownloadId]] = path match {
      case Path.Segment(segment, tail) =>
        val instanceId = Try(DownloadId.fromString(segment))
        instanceId.map(id => Matched(tail, Tuple1(id))).getOrElse(Unmatched)
      case _ =>
        Unmatched
    }
  }

  private object TaskOperationMatcher extends PathMatcher1[TaskOperation] {
    override def apply(v: Path): Matching[Tuple1[TaskOperation]] = {
      if (!v.isEmpty) {
        val opString = v.head.toString
        val op = opString match {
          case "cancel" => Some(CancelTask)
          case "pause" => Some(PauseTask)
          case "resume" => Some(ResumeTask)
          case "progress" => Some(GetTaskProgress)
          case _ => None
        }
        op.map(o => Matched(Path.Empty, Tuple1(o))).getOrElse(Unmatched)
      } else {
        Unmatched
      }
    }
  }

  private def taskOperationToMessage(op: TaskOperation, taskId: DownloadId): Any = {
    op match {
      case CancelTask => CancelDownloadTask(taskId)
      case PauseTask => PauseDownloadTask(taskId)
      case ResumeTask => ResumeDownloadTask(taskId)
      case GetTaskProgress => GetDownloadTaskProgress(taskId)
    }
  }
}
