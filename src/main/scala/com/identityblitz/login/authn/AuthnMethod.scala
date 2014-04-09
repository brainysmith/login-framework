package com.identityblitz.login.authn

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}

/**
  */
trait AuthnMethod {

  /**
   * The name of the authentication method.
   * @return
   */
  def name:String

  /**
   * The method is called each time before a new authentication method is started. The method makes it possible to do
   * some preparatory actions (e.g. generate challenge) and return forward or redirect request to a specific for
   * the current method login url.
   * @param req
   * @param resp
   */
  def start(implicit req: InboundTransport, resp: OutboundTransport)


  /**
   * The method is called to perform the authentication. All parameters passed from a login page stored in an
   * [[InboundTransport]].
   *
   * @param req
   * @param resp
   */
  def `do`(implicit req: InboundTransport, resp: OutboundTransport): Int

}

/**
 */
//todo: thinking about it
object AuthnResult {

  val SUCCESS = 0

  val FAIL = -1

  val PROCESSING = 1

}
