package com.identityblitz.login

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.error.LoginException

trait Handler {

  @throws(classOf[LoginException])
  def start(itr: InboundTransport, otr: OutboundTransport)

  @throws(classOf[LoginException])
  def `do`(itr: InboundTransport, otr: OutboundTransport)

}
