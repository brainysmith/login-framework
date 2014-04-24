package com.identityblitz.login.transport

import com.identityblitz.json.{Json, JStr, JObj}
import com.identityblitz.login.FlowAttrName._

/** 
 */
trait JsonResponse {
  
  def jObj: JObj

  def asString = jObj.toJson

}

case class AjaxRedirectResponse(location: String) extends JsonResponse {
  override val jObj: JObj = JObj(REDIRECT -> JStr(location))
}

case class CommandResponse(base64Command: String, command_name: String, error: Option[String] = None) extends JsonResponse {
  override val jObj: JObj = error.map(err => Json.obj(ERROR -> JStr(err))).getOrElse(JObj()) ++!
    Json.obj(COMMAND -> JStr(base64Command), COMMAND_NAME -> JStr(command_name))
}
