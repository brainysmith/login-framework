package com.identityblitz.login.builtin

import com.identityblitz.login.{LoginContext, LoginFlow}


/**
 * Build in implementation of the login flow.
 */
object BuildInLoginFlow extends LoginFlow {

  //todo: realise it
  override def next(implicit lc: LoginContext): Option[String] = {
    throw new UnsupportedOperationException("Hasn't realized yet.")
  }
}
