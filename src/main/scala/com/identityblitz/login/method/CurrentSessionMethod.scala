package com.identityblitz.login.method

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.error.{BuiltInErrors, LoginException}
import com.identityblitz.login.LoginFramework.loginFlow
import com.identityblitz.login.session.LoginSession.getLs

/**
  */
class CurrentSessionMethod(val name: String, val options: Map[String, String]) extends AuthnMethod {

  override def onStart(implicit iTr: InboundTransport, oTr: OutboundTransport) = DO

  override def onDo(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    getLs.fold{loginFlow.skip(name)}{ls => loginFlow.success(name)}
  }
}
