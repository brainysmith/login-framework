package com.identityblitz.login

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.LoggingUtils._
import com.identityblitz.login.builtin.BuildInLoginFlow


/**
 * Defines a flow of the login process.
 * The implementation must be a singleton.
 */
trait LoginFlow {

  def start(method: String)(implicit req: InboundTransport, resp: OutboundTransport)

  /**
   * Defines a next step of the login process to redirect.
   * @return [[Some]] of a location to redirect. If result is [[None]] depending on the current login status:
   *        <ul>
   *          <li>redirect to the complete end point with resulting of the login process if the status is [[LoginStatus.SUCCESS]];</li>
   *          <li>redirect to the complete end point with error if the status is [[LoginStatus.FAIL]];</li>
   *          <li>do nothing if the status is [[LoginStatus.PROCESSING]].</li>
   *        </ul>
   */
  def next(implicit req: InboundTransport, resp: OutboundTransport): Option[String]
}

object LoginFlow {

  private val loginFlow = Conf.loginFlow.fold[LoginFlow]({
    logger.debug("will use the build in login flow: {}", BuildInLoginFlow.getClass.getSimpleName)
    BuildInLoginFlow
  })(className => {
    logger.debug("find in the configuration a custom login flow [class = {}]", className)
    this.getClass.getClassLoader.loadClass(className).asInstanceOf[LoginFlow]
  })

  def apply() = loginFlow
}
