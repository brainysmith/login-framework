package com.identityblitz.login.authn

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.Handler
import com.identityblitz.login.error.LoginException

/**
 *
 * important: an implementation must have the constructor with following signature ''(options: Map[String, String])''.
  */
trait AuthnMethod extends Handler {

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
  @throws(classOf[LoginException])
  def start(implicit req: InboundTransport, resp: OutboundTransport)


  /**
   * The method is called to perform the authentication. All parameters passed from a login page stored in an
   * [[InboundTransport]].
   *
   * @param req
   * @param resp
   */
  @throws(classOf[LoginException])
  def DO(implicit req: InboundTransport, resp: OutboundTransport)

}
