package com.identityblitz.login.authn.method

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.{Conf, Handler}
import com.identityblitz.login.error.LoginException
import com.identityblitz.login.authn.method.AuthnMethod._
import com.identityblitz.login.authn.bind.BindProviders
import com.identityblitz.login.LoggingUtils._

/**
 *
 * important: an implementation must have the constructor with following signature ''(options: Map[String, String])''.
  */
abstract class AuthnMethod(val options: Map[String, String]) extends Handler {

  protected val bindFunctions = options.filter(_._1.startsWith(BINDS_CONF_PREFIX))
    .map{case (k,v) => k.stripPrefix(BINDS_CONF_PREFIX + ".") -> v}
    .groupBy(_._1.split('.')(0)).map(entry => {
    (entry._1, entry._2.map({case (k, v) => (k.stripPrefix(entry._1 + "."), v)}))
  }).map{case (k, v) =>
    val order = v.get(ORDER_PARAM_NAME).fold(Int.MaxValue)(augmentString(_).toInt)
    order -> BindProviders.providerMap.get(k).getOrElse({
      val err = s"Specified an unknown '$k' bind provider for the '$name' authentication method"
      logger.error(err)
      throw new IllegalStateException(err)
    }).bind(v)_}.toArray.sortBy(_._1).map(_._2)


/*  protected def bind(data: Map[String, String]) = for (
    f <- bindFunctions;
    res <- f(data)
  ) yield res*/

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


private object AuthnMethod {

  val BINDS_CONF_PREFIX = "binds"

  val ORDER_PARAM_NAME = "order"

}