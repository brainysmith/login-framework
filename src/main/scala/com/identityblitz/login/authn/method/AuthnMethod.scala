package com.identityblitz.login.authn.method

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.{Conf, FlowAttrName, Handler}
import com.identityblitz.login.error.{CommandException, LoginException}
import com.identityblitz.login.authn.cmd.Command

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
    iTr.getParameter(FlowAttrName.COMMAND)
      .map[Command](Command(_))
      .map(_.execute)
      .map(res => res.left.flatMap(recover) match {
          case Left(cmdException) => Conf.loginFlow.fail(name, cmdException.errorKey)
          case Right(Some(cmd)) => sendCommand(cmd)
          case Right(None) => Conf.loginFlow.success(name)
      })
  }
}