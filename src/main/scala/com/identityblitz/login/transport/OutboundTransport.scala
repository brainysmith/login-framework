package com.identityblitz.login.transport

/**
  */
trait OutboundTransport {

  def sendRedirect(location: String)

  def unwrap: AnyRef
 }
