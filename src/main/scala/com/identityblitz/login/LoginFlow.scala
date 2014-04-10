package com.identityblitz.login

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.LoggingUtils._
import com.identityblitz.login.builtin.BuiltInLoginFlow
import scala.annotation.implicitNotFound
import com.identityblitz.login.authn.AuthnMethods._
import com.identityblitz.login.error.LoginException

/**
 * Defines a flow of the login process.
 * The implementation must be a singleton.
 */
@implicitNotFound("No implicit inbound or outbound found.")
abstract class LoginFlow extends Handler {

  /**
   * Starts a new login flow.
   * @param iTr
   * @param oTr
   */
  def start(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    logger.trace("starting a new login flow")
    val lc = LoginContext(iTr)
    iTr.updatedLoginCtx(lc)
    authnMethodsMap.get(lc.method).map(_.start).orElse({
      logger.error("the specified authentication method [{}] is not configured", lc.method)
      throw new LoginException(s"the specified authentication method [${lc.method}] is not configured")
    })
  }

  /**
   * Defines a next step of the login process to redirect.
   * @return [[Some]] of a location to redirect. If result is [[None]] depending on the current login status:
   *        <ul>
   *          <li>redirect to the complete end point with resulting of the login process if the status is [[LoginStatus.SUCCESS]];</li>
   *          <li>redirect to the complete end point with error if the status is [[LoginStatus.FAIL]];</li>
   *          <li>do nothing if the status is [[LoginStatus.PROCESSING]].</li>
   *        </ul>
   */
  def next(implicit iTr: InboundTransport, oTr: OutboundTransport): Option[String] = {
    logger.trace("getting the next authentication step")
    val lc = iTr.getLoginCtx.getOrElse[LoginContext]({
      logger.error("The specified inbound transport doesn't have a login context. Be sure that start was be called before.")
      throw new LoginException("The specified inbound transport doesn't have a login context. Be sure that start was be " +
        "called before.")
    })



/*    authnMethodsMap.get(lc.method).map(_.start).orElse({
      logger.error("the specified authentication method [{}] is not configured", lc.method)
      throw new LoginException(s"the specified authentication method [${lc.method}] is not configured")
    })*/

    ???
  }
}

object LoginFlow {

  private val loginFlow = Conf.loginFlow.fold[LoginFlow]({
    logger.debug("will use the build in login flow: {}", BuiltInLoginFlow.getClass.getSimpleName)
    BuiltInLoginFlow
  })(className => {
    logger.debug("find in the configuration a custom login flow [class = {}]", className)
    this.getClass.getClassLoader.loadClass(className).asInstanceOf[LoginFlow]
  })

  def apply() = loginFlow
}
