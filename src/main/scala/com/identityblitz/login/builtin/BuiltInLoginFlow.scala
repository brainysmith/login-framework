package com.identityblitz.login.builtin

import com.identityblitz.login.{LoginContext, LoginFlow}
import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}


/**
 * Build in implementation of the login flow.
 */
object BuiltInLoginFlow extends LoginFlow {

  //todo: realise it
  override def next(implicit req: InboundTransport, resp: OutboundTransport): Option[String] = ???
}