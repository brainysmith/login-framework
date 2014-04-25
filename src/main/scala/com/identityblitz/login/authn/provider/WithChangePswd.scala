package com.identityblitz.login.authn.provider

import com.identityblitz.login.authn.cmd.Command
import com.identityblitz.login.error.LoginError
import com.identityblitz.json.JObj

/**
  */
trait WithChangePswd {

  def changePswd(userId: String, curPswd: String, newPswd: String): Either[LoginError, (JObj, Option[Command])]

}
