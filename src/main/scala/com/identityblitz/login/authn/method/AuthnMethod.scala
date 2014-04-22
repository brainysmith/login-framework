package com.identityblitz.login.authn.method

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.{Conf, FlowAttrName, Handler}
import com.identityblitz.login.error.{BuiltInError, CommandException, LoginException}
import com.identityblitz.login.authn.cmd.Command
import com.identityblitz.login.LoggingUtils._
import scala.util.control.NonFatal

/**
 *
 * important: an implementation must have the constructor with following signature:
 * ''(name:String, options: Map[String, String])''.
  */
abstract class AuthnMethod(val name:String, val options: Map[String, String]) extends Handler {

  /**
   * The method is called each time before a new authentication method is started. The method makes it possible to do
   * some preparatory actions (e.g. generate challenge) and return forward or redirect request to a specific for
   * the current method login url.
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  @throws(classOf[LoginException])
  def start(implicit iTr: InboundTransport, oTr: OutboundTransport)

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
        logger.error("The request parameter '{}' not specified", FlowAttrName.COMMAND)
        throw new IllegalArgumentException("")
      }(Command[Command])

      logger.trace("Try to execute a command [authnMethod = {}]: {}", name, command)

      command.execute.left.flatMap(cmdException => {
        logger.debug("Execution of the command fails [authnMethod = {}, command = {}]: {}. Try to recover.",
          Array(name, command, cmdException))
        recover(cmdException)
      }) match {
        case Left(cmdException) =>
          logger.debug("No any recovery command specified for the command exception [authnMethod = {}]: {}",
            name, cmdException)
          Conf.loginFlow.fail(name, cmdException.error)
        case Right(Some(cmd)) =>
          logger.debug("Authentication method '{}' is not completed yet. Send a new command: {}", name, cmd)
          sendCommand(cmd)
        case Right(None) =>
          logger.debug("Authentication method '{}' is completed successfully.", name)
          Conf.loginFlow.success(name)
      }
    } catch {
      case NonFatal(e) =>
        logger.error("During executing command an internal error has occurred [authnMethod = {}]: {}. ", name, e)
        Conf.loginFlow.fail(name, BuiltInError.INTERNAL)
    }
  }
}