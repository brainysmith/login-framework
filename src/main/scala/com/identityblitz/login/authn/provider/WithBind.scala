package com.identityblitz.login.authn.provider

import com.identityblitz.json.JObj
import com.identityblitz.login.authn.cmd.Command


/**
 */
trait WithBind {

  def bind(data: Map[String, String]): Either[String, (JObj, Command)]

}