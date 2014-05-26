package com.identityblitz.login.cmd

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.error.{BuiltInErrors, CommandException}
import com.identityblitz.login.App._
import scala.util.control.NonFatal

/**
 */
trait CommandTools {

  protected class InvokeBuilder(val recover: (CommandException, InboundTransport, OutboundTransport) => Either[CommandException, Command] =
                      (cmdException: CommandException, iTr: InboundTransport, oTr: OutboundTransport) => Left(cmdException),
                      val onSuccess: (Option[Command], InboundTransport, OutboundTransport) => Unit = {},
                      val onFail: (CommandException,  InboundTransport, OutboundTransport) => Unit = {},
                      val onFatal: (Throwable, InboundTransport, OutboundTransport) => Unit = {}) {
    
    def withRecover(rf: (CommandException, InboundTransport, OutboundTransport) => Either[CommandException, Command]) = new InvokeBuilder(rf, onSuccess, onFail, onFatal)
    def onSuccess(success: (Option[Command], InboundTransport, OutboundTransport) => Unit) = new InvokeBuilder(recover, success, onFail, onFatal)
    def onFail(fail: (CommandException, InboundTransport, OutboundTransport) => Unit) = new InvokeBuilder(recover, onSuccess, fail, onFatal)
    def onFatal(fatal: (Throwable, InboundTransport, OutboundTransport) => Unit) = new InvokeBuilder(recover, onSuccess, onFail, fatal)    
    
    def build(): (Command, InboundTransport, OutboundTransport) => Unit = (cmd: Command, iTr:InboundTransport, oTr: OutboundTransport) => {
      try {
        if (logger.isTraceEnabled)
          logger.trace("Try to execute a command: {}", cmd.asString())
        cmd.execute.left.flatMap(cmdException => {
          if (logger.isDebugEnabled)
            logger.debug("Execution of the command fails [command = {}]: {}. Try to recover.", Array(cmd, cmdException))
          recover(cmdException, iTr, oTr).right.map(Some)
        }) match {
          case Left(cmdException) =>
            logger.debug("No any recovery specified for the command exception: {}", cmdException)
            onFail(cmdException, iTr, oTr)
          case Right(res) =>
            logger.debug("Execution of the command is completed successfully. Getting command: {}", res)
            onSuccess(res, iTr, oTr)
        }
      } catch {
        case NonFatal(e) =>
          logger.error("During executing command an internal error has occurred: {}. ", e)
          onFatal(e, iTr, oTr)
          Left(CommandException(cmd, BuiltInErrors.INTERNAL))
      }      
    }
  } 

}
