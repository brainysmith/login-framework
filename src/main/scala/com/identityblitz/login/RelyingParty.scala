package com.identityblitz.login

import com.identityblitz.json.{JVal, JStr, JArr, Json}

/**
  */
trait RelyingParty {

  def id: String

  def protocol: String

  def params: Seq[String]

  def asString(): String
}

case class BuiltInRelyingParty(id: String, protocol: String, params: Seq[String] = Seq.empty) extends RelyingParty {
  override def asString(): String = Json.obj("id" -> JStr(id),
    "protocol" -> JStr(protocol),
    "params" -> JArr(params.map(JStr(_)).toArray)
  ).toJson
}

object BuiltInRelyingParty {
  def apply(rp: String): BuiltInRelyingParty = {
    val jv = JVal.parse(rp)
    BuiltInRelyingParty((jv \ "id").as[String],
      (jv \ "protocol").as[String],
      (jv \ "params").asOpt[Array[String]].getOrElse(Array.empty).toSeq)
  }
}
