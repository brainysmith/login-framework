package com.identityblitz.login.authn.method

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.LoggingUtils._
import com.identityblitz.login.authn.cmd.{ChangePswdCmd, BindCommand, Command}
import com.identityblitz.login.error.CommandException
import com.identityblitz.login.error.BuiltInErrors._
import com.identityblitz.login.authn.method.PasswordBaseMethod.FormParams

/**
 */
class PasswordBaseMethod(name: String, options: Map[String, String]) extends AuthnMethod(name, options) {

  private val pageController = options.get("page-controller").getOrElse({
    val err = "The password base method can't be instantiate because a page controller path not specified. " +
      "To fix it specify the option 'page-controller' in the configuration for this method."
    logger.error(err)
    throw new IllegalStateException(err)
  })

  override def start(implicit req: InboundTransport, resp: OutboundTransport): Unit = {
    sendCommand(BindCommand(name, FormParams.allParams))
  }

  override protected def route(cmd: Command)(implicit iTr: InboundTransport, oTr: OutboundTransport): String = pageController

  override protected def recover(cmdException: CommandException)
                                (implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    cmdException.cmd -> cmdException.error match {
      case (bindCmd: BindCommand, INVALID_CREDENTIALS | NO_SUBJECT_FOUND | NO_CREDENTIALS_FOUND) =>
        Right(Some(BindCommand(bindCmd)))
      case (changePswdCmd: ChangePswdCmd, INVALID_CREDENTIALS | NO_CREDENTIALS_FOUND) =>
        Right(Some(ChangePswdCmd(changePswdCmd)))
      case _ =>
        Left(cmdException)
    }
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
