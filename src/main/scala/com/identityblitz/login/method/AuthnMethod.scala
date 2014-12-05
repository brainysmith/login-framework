package com.identityblitz.login.method

import com.identityblitz.login.provider.{WithBind, Provider}
import com.identityblitz.login.cmd.{Command, CommandTools}
import com.identityblitz.login.error.{LoginException, CommandException, BuiltInErrors}
import com.identityblitz.login.service.ServiceProvider
import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login._
import com.identityblitz.login.LoginFramework._


/**
  */
trait AuthnMethod extends Handler with WithName with WithStart with WithDo with CommandTools {

  lazy val bindProviders =  options.get("bind-providers").map(_.split(","))
    .getOrElse[Array[String]](Array.empty)
    .flatMap(pName => LoginFramework.findProvider[Provider with WithBind](Some(pName), classOf[Provider], classOf[WithBind]))

  protected val invoker = new InvokeBuilder()
    .withOnSuccess(onSuccess)
    .withRecover(recover)
    .withOnFail(onFail)
    .withOnFatal(onFatal)
    .build()

  private def recover(cmdException: CommandException, iTr: InboundTransport, oTr: OutboundTransport): Either[CommandException, Command] = {
    ServiceProvider.securityService.map { s => iTr.getLoginCtx.map{ l => s.onLoginFail(cmdException.error, l, iTr)}}
    onRecover(cmdException, iTr, oTr)
  }

  protected def onRecover(cmdException: CommandException, iTr: InboundTransport, oTr: OutboundTransport): Either[CommandException, Command] = Left(cmdException)

  protected def onSuccess(cmd: Option[Command], iTr: InboundTransport, oTr: OutboundTransport) = {}

  protected def onFail(cmdException: CommandException, iTr: InboundTransport, oTr: OutboundTransport) = {
    loginFlow.fail(name, cmdException.error)(iTr, oTr)
  }

  protected def onFatal(cmdException: Throwable, iTr: InboundTransport, oTr: OutboundTransport) = {
    loginFlow.fail(name, BuiltInErrors.INTERNAL)(iTr, oTr)
  }

  def onDo(implicit iTr: InboundTransport, oTr: OutboundTransport): Unit

  def onStart(implicit iTr: InboundTransport, oTr: OutboundTransport): Unit

  @throws(classOf[LoginException])
  override def start(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    saveCurMethod
    onStart
  }

  override def DO(implicit iTr: InboundTransport, oTr: OutboundTransport): Unit = {
    validateMethod
    onDo
  }

  protected def sendCommand(cmd: Command, path: String)(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    saveCurCmd(cmd.name)
    iTr.setAttribute(FlowAttrName.COMMAND_NAME, cmd.name)
    iTr.setAttribute(FlowAttrName.COMMAND, cmd.selfpack)
    cmd.leftAttempts.map(la => iTr.setAttribute(FlowAttrName.COMMAND_LEFT_ATTEMPTS, la.toString))

    for (a <- cmd.attrs) {
      iTr.setAttribute(a._1, a._2)
    }
    iTr.forward(path)
  }

  protected def getBase64Cmd(implicit iTr: InboundTransport, oTr: OutboundTransport): String =
    iTr.getParameter(FlowAttrName.COMMAND).getOrElse{
      val err = s"The request parameter '${FlowAttrName.COMMAND}' not specified"
      logger.error(err)
      throw new IllegalArgumentException(err)
    }


  private def validateMethod(implicit iTr: InboundTransport, oTr: OutboundTransport): Unit = {
    getCurMethod.fold {
      val err = s"The method '$name' unexpected"
      logger.error(err)
      throw new IllegalArgumentException(err)
    }{e =>
      if (e != name) {
        val err = s"The method '$name' not match to expected '$e'"
        logger.error(err)
        throw new IllegalArgumentException(err)
      }
    }
  }

  protected def getCurCmd(implicit iTr: InboundTransport, oTr: OutboundTransport): Option[String] =
    iTr.getLoginCtx.fold {
      val err = "Login context not found."
      logger.error(err)
      throw new IllegalStateException(err)
    }(_.currentCommand)


  private def saveCurCmd(cmd: String)(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    iTr.updatedLoginCtx(iTr.getLoginCtx.map(_ setCurrentCommand cmd).orElse({
      val err = "Login context not found."
      logger.error(err)
      throw new IllegalStateException(err)
    }))
  }


  private def getCurMethod(implicit iTr: InboundTransport, oTr: OutboundTransport): Option[String] =
    iTr.getLoginCtx.fold {
      val err = "Login context not found."
      logger.error(err)
      throw new IllegalStateException(err)
    }(_.currentMethod)


  private def saveCurMethod(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    iTr.updatedLoginCtx(iTr.getLoginCtx.map(_ setCurrentMethod name).orElse({
      val err = "Login context not found."
      logger.error(err)
      throw new IllegalStateException(err)
    }))
  }

}
