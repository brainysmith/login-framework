package com.identityblitz.login.method

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.LoginFramework.logger
import com.identityblitz.login.cmd.{ChangePswdCmd, BindCommand, Command}
import com.identityblitz.login.error.CommandException
import com.identityblitz.login.error.BuiltInErrors._
import com.identityblitz.login.method.PasswordBaseMethod.FormParams
import com.identityblitz.login.LoginFramework

/**
 */
class PasswordBaseMethod(val name: String, val options: Map[String, String]) extends AuthnMethod {

  private val pageController = options.getOrElse("page-controller", {
    val err = "The password base method can't be instantiate because a page controller path not specified. " +
      "To fix it specify the option 'page-controller' in the configuration for this method."
    logger.error(err)
    throw new IllegalStateException(err)
  })

  private val invoker = Invoker
    .withRecover(recover)
    .withOnSuccess(onSuccess)
    .build()

  protected def recover(cmdException: CommandException, iTr: InboundTransport, oTr: OutboundTransport) = {
    cmdException.cmd -> cmdException.error match {
      case (bindCmd: BindCommand, INVALID_CREDENTIALS | NO_SUBJECT_FOUND | NO_CREDENTIALS_FOUND) =>
        Right(BindCommand(bindCmd))
      case (changePswdCmd: ChangePswdCmd, INVALID_CREDENTIALS | NO_CREDENTIALS_FOUND) =>
        Right(ChangePswdCmd(changePswdCmd))
      case _ =>
        Left(cmdException)
    }
  }
  
  protected def onSuccess(cmd: Option[Command], iTr: InboundTransport, oTr: OutboundTransport) =
    cmd.fold(LoginFramework.loginFlow.success(name)(iTr, oTr))(sendCommand(_, pageController)(iTr, oTr))


  override def start(implicit iTr: InboundTransport, oTr: OutboundTransport): Unit = {
    sendCommand(BindCommand(name, Map(), FormParams.allParams), pageController)
  }

  override def DO(implicit iTr: InboundTransport, oTr: OutboundTransport): Unit = {
    invoker(getCommand, iTr, oTr)
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
