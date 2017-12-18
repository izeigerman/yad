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

import spray.json.{DefaultJsonProtocol, JsObject, JsValue, RootJsonFormat}

trait AuthMethod
case class PublicKeyAuthMethod(username: String, keyLocation: String) extends AuthMethod
case class PasswordAuthMethod(username: String, password: String) extends AuthMethod
case object NoAuth extends AuthMethod

trait AuthMethodJsonProtocol extends DefaultJsonProtocol {

  implicit val publicKeyFormat = jsonFormat2(PublicKeyAuthMethod.apply)
  implicit val passwordFormat = jsonFormat2(PasswordAuthMethod.apply)

  implicit val authMethodFormat = new RootJsonFormat[AuthMethod] {
    override def read(json: JsValue): AuthMethod = {
      val fields = json.asJsObject.fields
      if (fields.contains("password")) {
        passwordFormat.read(json)
      } else if (fields.contains("keyLocation")) {
        publicKeyFormat.read(json)
      } else {
        NoAuth
      }
    }

    override def write(obj: AuthMethod): JsValue = {
      obj match {
        case pubKey: PublicKeyAuthMethod =>
          publicKeyFormat.write(pubKey)
        case password: PasswordAuthMethod =>
          val censored = password.copy(password = password.password.replaceAll(".", "*"))
          passwordFormat.write(censored)
        case _ => JsObject.empty
      }
    }
  }
}
