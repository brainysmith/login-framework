package com.identityblitz.login.transport

import com.identityblitz.login.error.TransportException

/**
 */
trait InboundTransport {

  def getParameter(name: String): Option[String]

  def getParameterMap: Map[String, Array[String]]

  @throws(classOf[TransportException])
  def forward(path: String)

  def unwrap: AnyRef
}
