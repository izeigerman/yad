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
import sbt._
import Keys._
import scoverage.ScoverageKeys._
import sbtassembly._
import AssemblyKeys._

object YadBuild extends Build {

  val AkkaVersion = "2.4.19"
  val AkkaHttpVersion = "10.0.10"
  val SprayJsonVersion = "1.3.3"
  val ScalaTestVersion = "2.2.6"
  val ScalamockVersion = "3.4.2"
  val Slf4jVersion = "1.7.19"
  val ScoptsVersion = "3.5.0"
  val Ssh4jVersion = "0.21.1"
  val CommonsCodecVersion = "1.10"
  val H2Version = "1.4.196"

  val CommonSettings = Seq(
    organization := "com.github.izeigerman",
    scalaVersion := "2.11.11",
    version := "0.1",

    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-feature",
      "-language:postfixOps",
      "-language:implicitConversions",
      "-language:higherKinds"),

    parallelExecution in Test := false,

    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-core" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      "io.spray" %% "spray-json" % SprayJsonVersion,
      "org.slf4j" % "slf4j-api" % Slf4jVersion,
      "org.slf4j" % "slf4j-log4j12" % Slf4jVersion,
      "com.github.scopt" %% "scopt" % ScoptsVersion,
      "com.hierynomus" % "sshj" % Ssh4jVersion,
      "commons-codec" % "commons-codec" % CommonsCodecVersion,
      "com.h2database" % "h2" % H2Version,
      "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % "test->*",
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test->*",
      "org.scalatest" %% "scalatest" % ScalaTestVersion % "test->*",
      "org.scalamock" %% "scalamock-scalatest-support" % ScalamockVersion % "test->*"
    ),

    test in assembly := {}
  )

  val YadSettings = CommonSettings ++ Seq(
    mainClass in Compile := Some("yad.YadServerMain"),
    assemblyMergeStrategy in assembly := {
      case "log4j.properties" => MergeStrategy.concat
      case "reference.conf" => MergeStrategy.concat
      case "application.conf" => MergeStrategy.concat
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )

  val NoPublishSettings = CommonSettings ++ Seq(
    publishArtifact := false,
    publish := {},
    coverageEnabled := false
  )

  lazy val yadRoot = Project(id = "yad-root", base = file("."))
    .settings(NoPublishSettings: _*)
    .aggregate(yad)
    .disablePlugins(sbtassembly.AssemblyPlugin)

  lazy val yad = Project(id = "yad", base = file("yad"))
    .settings(YadSettings: _*)
}

