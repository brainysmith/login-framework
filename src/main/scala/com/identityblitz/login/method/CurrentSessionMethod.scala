package com.identityblitz.login.method

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.AuthnMethod
import com.identityblitz.login.error.LoginException

/**
  */
class CurrentSessionMethod(val name: String, val options: Map[String, String]) extends AuthnMethod {

  @throws(classOf[LoginException])
  override def start(implicit req: InboundTransport, resp: OutboundTransport): Unit = ???

  override def DO(implicit iTr: InboundTransport, oTr: OutboundTransport): Unit = ???
}
