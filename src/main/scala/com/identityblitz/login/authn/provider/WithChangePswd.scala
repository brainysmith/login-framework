package com.identityblitz.login.authn.provider

import com.identityblitz.login.authn.cmd.Command
import com.identityblitz.login.error.LoginError

/**
  */
trait WithChangePswd {

  def changePswd(userId: String, curPswd: String, newPswd: String): Either[LoginError, Option[Command]]

}
