package com.identityblitz.login

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.error.LoginException

trait Handler {

  @throws(classOf[LoginException])
  def start(implicit req: InboundTransport, resp: OutboundTransport)

}
