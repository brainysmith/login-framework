package com.identityblitz.login.authn.provider

import com.identityblitz.login.authn.cmd.Command

/**
  */
trait WithChangePswd {

  def changePswd(login: String, curPswd: String, newPswd: String): Either[String, Command]

}
