package com.identityblitz.login.provider.method

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.App.loginFlow

/**
  */
class PreviousSessionMethodProvider(val name: String, val options: Map[String, String]) extends PassiveMethodProvider {

  override def DO(implicit iTr: InboundTransport, oTr: OutboundTransport): Unit = {
    /*loginFlow.provider.fail()*/
  }
}
