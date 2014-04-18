package com.identityblitz.login.authn.provider

import com.identityblitz.json.JObj
import scala.util.Try

/**
 */
trait WithAttributes {

  def populate(data: Map[String, String]): Try[JObj]

}
