package com.identityblitz.login.authn.method

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.Handler
import com.identityblitz.login.error.LoginException
import scala.language.implicitConversions

/**
 *
 * important: an implementation must have the constructor with following signature ''(options: Map[String, String])''.
  */
abstract class AuthnMethod(val name:String, val options: Map[String, String]) extends Handler {

/*  val bindSchema = options.get("bind").orElse({
    val err = s"Bind schema of '$name' authentication method is not specified"
    logger.error(err)
    throw new IllegalStateException(err)
  }).flatMap(Conf.binds.get).getOrElse({
    val err = s"specified bind schema in the '$name' authentication method is not configured"
    logger.error(err)
    throw new IllegalStateException(err)
  })*/
  
  /**
   * The method is called each time before a new authentication method is started. The method makes it possible to do
   * some preparatory actions (e.g. generate challenge) and return forward or redirect request to a specific for
   * the current method login url.
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  @throws(classOf[LoginException])
  def start(implicit iTr: InboundTransport, oTr: OutboundTransport)


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