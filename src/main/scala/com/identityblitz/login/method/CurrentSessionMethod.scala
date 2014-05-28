package com.identityblitz.login.method

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.error.{BuiltInErrors, LoginException}
import com.identityblitz.login.App.loginFlow
import com.identityblitz.login.session.LoginSession.getLs

/**
  */
class CurrentSessionMethod(val name: String, val options: Map[String, String]) extends AuthnMethod {

  @throws(classOf[LoginException])
  override def start(implicit req: InboundTransport, resp: OutboundTransport): Unit = DO

  override def DO(implicit iTr: InboundTransport, oTr: OutboundTransport): Unit = {
    getLs.fold{loginFlow.fail(name, BuiltInErrors.NO_SUBJECT_SESSION_FOUND)}{ls => loginFlow.success(name)}
  }
}
