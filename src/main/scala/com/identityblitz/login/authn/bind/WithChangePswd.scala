package com.identityblitz.login.authn.bind

import com.identityblitz.login.authn.cmd.Command

/**
  */
trait WithChangePswd {

  def changePswd(login: String, curPswd: String, newPswd: String): Command

}
