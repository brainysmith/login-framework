package com.identityblitz.login.transport

import com.identityblitz.login.error.TransportException

/**
  */
trait OutboundTransport {

  @throws(classOf[TransportException])
  def sendRedirect(location: String)

  def unwrap: AnyRef
 }
