package com.identityblitz.login.provider.method

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.FlowAttrName
import com.identityblitz.login.error.{BuiltInErrors, CommandException, LoginException}
import com.identityblitz.login.cmd.Command
import com.identityblitz.login.App.{logger, methods, loginFlow}
import scala.util.control.NonFatal
import com.identityblitz.login.provider.{WithDo, WithStart, Provider}


trait PassiveMethodProvider extends Provider with WithDo {

  def authnMethod(implicit iTr: InboundTransport, oTr: OutboundTransport) = methods(iTr.getLoginCtx.get.method)

}

/**
 *
  */
trait ActiveMethodProvider extends PassiveMethodProvider with WithStart {
 
  /**
   *
   * @param cmd
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   * @return
   */
  protected def route(cmd:Command)(implicit iTr: InboundTransport, oTr: OutboundTransport): String

  /**
   *
   * @param cmdException
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   * @return
   */
  protected def recover(cmdException: CommandException)(implicit iTr: InboundTransport, oTr: OutboundTransport): Either[CommandException, Option[Command]]


  /**
   *
   * @param cmd
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   * @return
   */
  final protected def sendCommand(cmd: Command)(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    iTr.setAttribute(FlowAttrName.COMMAND_NAME, cmd.name)
    iTr.setAttribute(FlowAttrName.COMMAND, cmd.asString())
    iTr.forward(route(cmd))
  }

  /**
   * The method is called to perform a command.
   *
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  @throws(classOf[LoginException])
  final def DO(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    try {
      val command = iTr.getParameter(FlowAttrName.COMMAND).fold[Command]{
        val err = s"The request parameter '${FlowAttrName.COMMAND}' not specified"
        logger.error(err)
        throw new IllegalArgumentException(err)
      }(Command[Command])

      logger.trace("Try to execute a command [authnMethod = {}]: {}", name, command)

      command.execute.left.flatMap(cmdException => {
        logger.debug("Execution of the command fails [authnMethod = {}, command = {}]: {}. Try to recover.",
          Array(name, command, cmdException))
        iTr.setAttribute(FlowAttrName.ERROR, cmdException.error.name)
        recover(cmdException)
      }) match {
        case Left(cmdException) =>
          logger.debug("No any recovery command specified for the command exception [authnMethod = {}]: {}",
            name, cmdException)
          loginFlow.provider.fail(cmdException.error)
        case Right(Some(cmd)) =>
          logger.debug("Authentication method '{}' is not completed yet. Send a new command: {}", name, cmd)
          sendCommand(cmd)
        case Right(None) =>
          logger.debug("Authentication method '{}' is completed successfully.", name)
          loginFlow.provider.success
      }
    } catch {
      case NonFatal(e) =>
        logger.error("During executing command an internal error has occurred [authnMethod = {}]: {}. ", name, e)
        loginFlow.provider.fail(BuiltInErrors.INTERNAL)
    }
  }
}