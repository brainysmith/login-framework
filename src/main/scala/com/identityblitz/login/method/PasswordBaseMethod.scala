package com.identityblitz.login.method

import com.identityblitz.login.LoginFramework
import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.LoginFramework.logger
import com.identityblitz.login.cmd._
import com.identityblitz.login.error.{LoginException, CommandException}
import com.identityblitz.login.error.BuiltInErrors._
import com.identityblitz.login.method.PasswordBaseMethod.FormParams

/**
  */
class PasswordBaseMethod(val name: String, val options: Map[String, String]) extends AuthnMethod {

  private val pageController = options.getOrElse("page-controller", {
    val err = "The password base method can't be instantiate because a page controller path not specified. " +
      "To fix it specify the option 'page-controller' in the configuration for this method."
    logger.error(err)
    throw new IllegalStateException(err)
  })

  override protected def onRecover(cmdException: CommandException, iTr: InboundTransport, oTr: OutboundTransport) = {
    cmdException.cmd -> cmdException.error match {
      case (_, INTERNAL) =>
        Left(cmdException)
      case (bindCmd: BindCommand, _) =>
        Right(BindCommand(bindCmd))
      case (changePswdCmd: ChangePswdCmd, _) =>
        Right(ChangePswdCmd(changePswdCmd))
      case _ =>
        Left(cmdException)
    }
  }

  def getCommand(implicit iTr: InboundTransport, oTr: OutboundTransport): Either[LoginException, Command] = getCtxCmd.getOrElse {
    val err = s"The method '$name' can't unpack unexpected command '$getBase64Cmd'."
    logger.error(err)
    throw new IllegalStateException(err)
  } match {
    case FirstBindCommand.name => Command.unpack[FirstBindCommand](getBase64Cmd)
    case RebindCommand.name => Command.unpack[RebindCommand](getBase64Cmd)
    case ChangePswdCmd.name => Command.unpack[ChangePswdCmd](getBase64Cmd)
    case c =>
      val err = s"The method '$name' can't unpack unexpected command '$c'."
      logger.error(err)
      throw new IllegalStateException(err)
  }


  override protected def onSuccess(cmd: Option[Command], iTr: InboundTransport, oTr: OutboundTransport) = cmd.fold(LoginFramework.loginFlow.success(name)(iTr, oTr))(sendCommand(_, pageController)(iTr, oTr))


  override def onStart(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    sendCommand(BindCommand(name, FormParams.allParams), pageController)
  }

  override def onDo(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    getCommand match {
      case Left(le) =>
        val err = s"The method '$name' can't unpack because of error '$le'."
        logger.error(err)
        throw new IllegalStateException(err)
      case Right(cmd) => invoker(cmd, iTr, oTr)
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
