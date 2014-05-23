package com.identityblitz.login.provider.method

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.App.logger
import com.identityblitz.login.cmd.{ChangePswdCmd, BindCommand, Command}
import com.identityblitz.login.error.CommandException
import com.identityblitz.login.error.BuiltInErrors._
import com.identityblitz.login.provider.method.PasswordBaseMethodProvider.FormParams

/**
 */
class PasswordBaseMethodProvider(val name: String, val options: Map[String, String]) extends ActiveMethodProvider {

  private val pageController = options.get("page-controller").getOrElse({
    val err = "The password base method can't be instantiate because a page controller path not specified. " +
      "To fix it specify the option 'page-controller' in the configuration for this method."
    logger.error(err)
    throw new IllegalStateException(err)
  })

  override def start(implicit iTr: InboundTransport, oTr: OutboundTransport): Unit = {
    sendCommand(BindCommand(authnMethod.name, FormParams.allParams))
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

object PasswordBaseMethodProvider {

  object FormParams extends Enumeration {
    import scala.language.implicitConversions

    type Options = Value
    val login, password = Value

    implicit def valueToString(v: Value): String = v.toString

    val allParams = values.map(_.toString).toSeq
  }

}
