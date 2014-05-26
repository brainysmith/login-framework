package com.identityblitz.login

import com.identityblitz.login.provider.{WithBind, Provider}
import App.logger
import com.identityblitz.login.cmd.{Command, CommandTools}
import com.identityblitz.login.error.BuiltInErrors
import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}


/**
 */
trait AuthnMethod extends Handler with WithName with WithStart with WithDo with CommandTools {

  protected lazy val loginFlow = App.loginFlow.provider

  protected lazy val bindProviders =  options.get("bind-providers").map(_.split(","))
    .getOrElse[Array[String]](Array.empty)
    .flatMap(pName => App.findProvider[Provider with WithBind](Some(pName), classOf[Provider], classOf[WithBind]))

  protected def InvokeBuilder = new InvokeBuilder()
    .onFatal((e, iTr, oTr) => {
    loginFlow.fail(BuiltInErrors.INTERNAL)
  })
    .onFail((cmdException, iTr, oTr) => {
    iTr.setAttribute(FlowAttrName.ERROR, cmdException.error.name)
    loginFlow.fail(cmdException.error)
  })


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
