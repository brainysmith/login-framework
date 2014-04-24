package com.identityblitz.login.authn.provider

import com.identityblitz.json.JObj
import com.identityblitz.login.authn.cmd.Command
import com.identityblitz.login.error.LoginError


/**
 */
trait WithBind {

  def bind(data: Map[String, String]): Either[LoginError, (JObj, Option[Command])]

}