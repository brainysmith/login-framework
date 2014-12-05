package com.identityblitz.login.method

import com.identityblitz.login.LoginFramework
import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.LoginFramework.logger
import com.identityblitz.login.cmd._
import com.identityblitz.login.error.{LoginException, CommandException}
import com.identityblitz.login.error.BuiltInErrors._

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

  def getCommand(implicit iTr: InboundTransport, oTr: OutboundTransport): Either[LoginException, Command] = getCurCmd.getOrElse {
    val err = s"Can't parse command '$getBase64Cmd' for method '$name'."
    logger.error(err)
    throw new IllegalStateException(err)
  } match {
    case BindCommand.name => Command.unpack[BindCommand](getBase64Cmd)
    case ChangePswdCmd.name => Command.unpack[ChangePswdCmd](getBase64Cmd)
    case c =>
      val err = s"Occurred unexpected command '$c' for method '$name'."
      logger.error(err)
      throw new IllegalStateException(err)
  }


  override protected def onSuccess(cmd: Option[Command], iTr: InboundTransport, oTr: OutboundTransport) = cmd.fold(LoginFramework.loginFlow.success(name)(iTr, oTr))(sendCommand(_, pageController)(iTr, oTr))


  override def onStart(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    sendCommand(BindCommand(name), pageController)
  }

  override def onDo(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    getCommand match {
      case Left(le) =>
        val err = s"Can't unpack command for method '$name' because of error '$le'."
        logger.error(err)
        throw new IllegalStateException(err)
      case Right(cmd) => invoker(cmd, iTr, oTr)
    }
  }
}

