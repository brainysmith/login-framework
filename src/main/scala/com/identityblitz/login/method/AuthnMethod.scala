package com.identityblitz.login.method

import com.identityblitz.login.provider.{WithBind, Provider}
import com.identityblitz.login.cmd.{Command, CommandTools}
import com.identityblitz.login.error.{LoginError, BuiltInErrors}
import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login._
import com.identityblitz.login.LoginFramework._
import scala.Some


/**
  */
trait AuthnMethod extends Handler with WithName with WithStart with WithDo with CommandTools {

  lazy val bindProviders =  options.get("bind-providers").map(_.split(","))
    .getOrElse[Array[String]](Array.empty)
    .flatMap(pName => LoginFramework.findProvider[Provider with WithBind](Some(pName), classOf[Provider], classOf[WithBind]))

  protected def Invoker: InvokeBuilder = new InvokeBuilder()
    .withOnFatal(
      (e, iTr, oTr) => {
        loginFlow.fail(name, BuiltInErrors.INTERNAL)(iTr, oTr)
      }
    ).withOnFail(
      (cmdException, iTr, oTr) => {
        loginFlow.fail(name, cmdException.error)(iTr, oTr)
      }
    )


  protected def sendCommand(cmd: Command, path: String)(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    iTr.setAttribute(FlowAttrName.COMMAND_NAME, cmd.name)
    iTr.setAttribute(FlowAttrName.COMMAND, cmd.asString())
    iTr.forward(path)
  }


  protected def getCommand(implicit iTr: InboundTransport, oTr: OutboundTransport) = iTr.getParameter(FlowAttrName.COMMAND).fold[Command]{
    val err = s"The request parameter '${FlowAttrName.COMMAND}' not specified"
    logger.error(err)
    throw new IllegalArgumentException(err)
  }(Command[Command])

}
