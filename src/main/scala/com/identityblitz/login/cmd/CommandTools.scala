package com.identityblitz.login.cmd

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.error.{BuiltInErrors, CommandException}
import com.identityblitz.login.LoginFramework._
import scala.util.control.NonFatal
import com.identityblitz.login.FlowAttrName

/**
 */
trait CommandTools {

  protected class InvokeBuilder(val recover: (CommandException, InboundTransport, OutboundTransport) => Either[CommandException, Command] =
                      (cmdException: CommandException, iTr: InboundTransport, oTr: OutboundTransport) => Left(cmdException),
                      val onSuccess: (Option[Command], InboundTransport, OutboundTransport) => Unit = (cmd, iTr, oTr) => {},
                      val onFail: (CommandException,  InboundTransport, OutboundTransport) => Unit = (e, iTr, oTr) => {},
                      val onFatal: (Throwable, InboundTransport, OutboundTransport) => Unit = (e, iTr, oTr) => {}) {
    
    def withRecover(rf: (CommandException, InboundTransport, OutboundTransport) => Either[CommandException, Command]) = new InvokeBuilder(rf, onSuccess, onFail, onFatal)
    def withOnSuccess(success: (Option[Command], InboundTransport, OutboundTransport) => Unit) = new InvokeBuilder(recover, success, onFail, onFatal)
    def withOnFail(fail: (CommandException, InboundTransport, OutboundTransport) => Unit) = new InvokeBuilder(recover, onSuccess, fail, onFatal)
    def withOnFatal(fatal: (Throwable, InboundTransport, OutboundTransport) => Unit) = new InvokeBuilder(recover, onSuccess, onFail, fatal)
    
    def build(): (Command, InboundTransport, OutboundTransport) => Unit = (cmd: Command, iTr:InboundTransport, oTr: OutboundTransport) => {
      try {
        if (logger.isTraceEnabled)
          logger.trace("Try to execute a command: {}", cmd)
        cmd.execute(iTr, oTr).left.flatMap(cmdException => {
          if (logger.isDebugEnabled)
            logger.debug("Execution of the command fails [command = {}]: {}. Try to recover.", Array(cmd, cmdException))
          iTr.setAttribute(FlowAttrName.ERROR, cmdException.error.name)
          recover(cmdException, iTr, oTr).right.flatMap(cmd => Right(Some(cmd)))
        }) match {
          case Left(cmdException) =>
            logger.debug("No any recovery specified for the command exception: {}", cmdException.error)
            onFail(cmdException, iTr, oTr)
          case Right(res) =>
            logger.debug("Execution of the command is completed successfully or the command was recovered. Getting command: {}", res)
            onSuccess(res, iTr, oTr)
        }
      } catch {
        case NonFatal(e) =>
          logger.error("During executing command an internal error has occurred: {}. ", e)
          iTr.setAttribute(FlowAttrName.ERROR, BuiltInErrors.INTERNAL.name)
          onFatal(e, iTr, oTr)
          Left(CommandException(cmd, BuiltInErrors.INTERNAL))
      }      
    }
  } 

}
