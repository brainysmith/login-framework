package com.identityblitz.login.authn.method

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.LoggingUtils._
import com.identityblitz.login.authn.cmd.{BindCommand, Command}
import com.identityblitz.login.error.CommandException
import com.identityblitz.login.authn.method.PasswordBaseMethod.FormParams

/**
 */
class PasswordBaseMethod(name: String, options: Map[String, String]) extends AuthnMethod(name, options) {

  private val loginPage = options.get("page").getOrElse({
    logger.error("The password base method can't be instantiate because a login page path not specified. To fix it " +
      "specify the option 'page' in the configuration for this method.")
    throw new IllegalStateException("The password base method can't be instantiate because a login page path not " +
      "specified. To fix it specify the option 'page' in the configuration for this method.")
  })

  override def start(implicit req: InboundTransport, resp: OutboundTransport): Unit = {
    sendCommand(BindCommand(name, FormParams.allParams))
  }

  override protected def route(cmd: Command)(implicit iTr: InboundTransport, oTr: OutboundTransport): String = {
    loginPage
  }

  override protected def recover(cmdException: CommandException)
                                (implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    addError(cmdException.errorKey)
    cmdException.cmd match {
      case bindCmd: BindCommand => Right(Some(BindCommand(bindCmd)))
    }
  }

  private def addError(error: String)(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    iTr.setAttribute(FormParams.error, error)
  }

/*  override def DO(implicit req: InboundTransport, resp: OutboundTransport): Unit = {
    logger.trace("Try to authenticate by {}", name)
    (req.getParameter("login"), req.getParameter("password")) match {
      case (Some(login), Some(pswd)) =>

        /*bind(Map("USERNAME" -> login, "PASSWORD" -> pswd))*/

        if ("mike".equalsIgnoreCase(login) && "oracle_1".equals(pswd)) {
          Conf.loginFlow.success(name)
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

  }*/
}

object PasswordBaseMethod {

  object FormParams extends Enumeration {
    import scala.language.implicitConversions

    type Options = Value
    val login, password, error = Value

    implicit def valueToString(v: Value): String = v.toString

    val allParams = values.map(_.toString).toSeq
  }

}
