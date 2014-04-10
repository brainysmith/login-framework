package com.identityblitz.login.authn

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.LoggingUtils._
import com.identityblitz.login.error.BuildInError._
import com.identityblitz.login.LoginFlow

/**
 */
class PasswordBaseMethod(private val options: Map[String, String]) extends AuthnMethod {

  private val loginPage = options.get("page").getOrElse({
    logger.error("The password base method can't be instantiate because a login page path not specified. To fix it " +
      "specify the option 'page' in the configuration for this method.")
    throw new IllegalStateException("The password base method can't be instantiate because a login page path not " +
      "specified. To fix it specify the option 'page' in the configuration for this method.")
  })

  override val name: String = "pswd"

  override def start(implicit req: InboundTransport, resp: OutboundTransport): Unit = req.forward(loginPage)

  override def DO(implicit req: InboundTransport, resp: OutboundTransport): Unit = {
    logger.trace("Try to authenticate by {}", name)
    (req.getParameter("login"), req.getParameter("password")) match {
      case (Some(login), Some(pswd)) =>
        if ("mike".equalsIgnoreCase(login) && "oracle_1".equals(pswd)) {
          LoginFlow().success(name)
        } else {
          //todo: thinking about error
          req.setAttribute("error", INVALID_CREDENTIALS)
          req.forward(loginPage)
        }
      case _ =>
        logger.debug("parameter login or password not specified")
        //todo: thinking about error
        req.setAttribute("error", NO_CREDENTIALS_FOUND)
        req.forward(loginPage)
    }

  }
}

object PasswordBaseMethod {
}
