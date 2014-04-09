package com.identityblitz.login.transport

import com.identityblitz.login.error.TransportException
import com.identityblitz.login.LoginContext

/**
 */
trait InboundTransport {

  def getLoginContext: LoginContext

  def getParameter(name: String): Option[String]

  def getParameterMap: Map[String, Array[String]]

  @throws(classOf[TransportException])
  def forward(path: String)

  def unwrap: AnyRef
}
