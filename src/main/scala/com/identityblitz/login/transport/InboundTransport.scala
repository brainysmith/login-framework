package com.identityblitz.login.transport

/**
 */
trait InboundTransport {

  def getParameter(name: String): Option[String]

  def getParameterMap: Map[String, Array[String]]

  def forward

  def unwrap: AnyRef
}
