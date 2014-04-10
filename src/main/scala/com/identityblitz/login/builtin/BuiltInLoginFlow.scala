package com.identityblitz.login.builtin

import com.identityblitz.login.{LoginContext, LoginFlow}
import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}


/**
 * Build in implementation of the login flow.
 */
object BuiltInLoginFlow extends LoginFlow {

  override protected def nextForFail(cause: String)(implicit iTr: InboundTransport, oTr: OutboundTransport): Unit = ???

  override protected def nextForSuccess(implicit iTr: InboundTransport, oTr: OutboundTransport): Unit = ???
}
