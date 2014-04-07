package com.identityblitz.login.glue.servlet

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}

/**
  */
private[servlet] class ServletInboundTransport(val req: HttpServletRequest) extends InboundTransport {

  override def getParameter(name: String): Option[String] = Option(req.getParameter(name))

  override def getParameterMap:Map[String, Array[String]] = req.getParameterMap

  override def forward: Unit = ???

  override def unwrap: AnyRef = req
}

private[servlet] object ServletInboundTransport {
  def apply(req: HttpServletRequest) = new ServletInboundTransport(req)
}

private[servlet] class ServletOutboundTransport(val resp: HttpServletResponse) extends OutboundTransport {
  override def sendRedirect(location: String) = resp.sendRedirect(location)

  override def unwrap: AnyRef = resp
}

private[servlet] object ServletOutboundTransport {
  def apply(resp: HttpServletResponse) = new ServletOutboundTransport(resp)
}
