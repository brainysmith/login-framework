package com.identityblitz.login

import com.identityblitz.login.api.LoginContext
import play.api.mvc.{Call, AnyContent, Request}

/**
 * Build in implementation of the login flow.
 */
object BuildInLoginFlow extends LoginFlow {

  //todo: realise it
  def getNextPoint(implicit lc: LoginContext, request: Request[AnyContent]): Option[Call] = {
    throw new UnsupportedOperationException("Hasn't realized yet.")
  }
}
