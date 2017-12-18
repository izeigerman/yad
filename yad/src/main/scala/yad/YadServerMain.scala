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
package yad

import java.io.File
import java.util.concurrent.TimeUnit

import akka.actor._
import akka.http.scaladsl.server.PathMatchers
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import yad.controller.DownloadTaskController
import yad.service.DownloadServiceActor
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object YadServerMain extends App {

  val config =
    if (args.nonEmpty) {
      ConfigFactory.parseFile(new File((args(0))))
    } else {
      ConfigFactory.load()
    }

  val actorSystemName = config.getString("yad.actor-system-name")
  implicit val actorSystem: ActorSystem = ActorSystem(actorSystemName, config)
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val dispatcher = actorSystem.dispatcher
  implicit val timeout = Timeout(
    config.getDuration("yad.api.client-timeout", TimeUnit.MILLISECONDS),
    TimeUnit.MILLISECONDS)

  val serviceRef = DownloadServiceActor()
  val controller = new DownloadTaskController(serviceRef)
  val baseRoute = pathPrefix(PathMatchers.separateOnSlashes("api/v1")) {
    controller.route
  }

  Http().bindAndHandle(baseRoute, "0.0.0.0", config.getInt("yad.api.port")).onFailure {
    case _: Exception =>
      actorSystem.terminate()
  }

  Await.result(actorSystem.whenTerminated, Duration.Inf)
}
