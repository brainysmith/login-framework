package com.identityblitz.login.authn.method

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.LoggingUtils._
import com.identityblitz.login.authn.cmd.{BindCommand, Command}
import com.identityblitz.login.error.{BuiltInError, CommandException}
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
    import BuiltInError._
    addError(cmdException.error.name)
    cmdException.cmd -> cmdException.error match {
      case (bindCmd: BindCommand, INVALID_CREDENTIALS | NO_SUBJECT_FOUND | NO_CREDENTIALS_FOUND) =>
        Right(Some(BindCommand(bindCmd)))
      case _ =>
        Left(cmdException)
    }
  }

  private def addError(error: String)(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    iTr.setAttribute("error", error)
  }
}

object PasswordBaseMethod {

  object FormParams extends Enumeration {
    import scala.language.implicitConversions

    type Options = Value
    val login, password = Value

    implicit def valueToString(v: Value): String = v.toString

    val allParams = values.map(_.toString).toSeq
  }

}
