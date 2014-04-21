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

  protected def sendCommand(cmd: Command)(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    iTr.setAttribute(FlowAttrName.COMMAND, cmd.asString())
    iTr.forward(route(cmd))
  }

  protected def route(cmd:Command)(implicit iTr: InboundTransport, oTr: OutboundTransport): String

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
   * The method is called to perform the authentication. All parameters passed from a login page stored in an
   * [[InboundTransport]].
   *
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  @throws(classOf[LoginException])
  def DO(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    iTr.getAttribute(FlowAttrName.COMMAND).map(_.asInstanceOf[String])
      .map[Command](Command(_))
      .map(_.execute)
      .map{
      case Left(cmdException) =>
        //todo: call recover and send command if necessary
        ???
      case Right(Some(cmd)) =>
        //todo: call send command
        sendCommand(cmd)
        ???
      case Right(None) =>
        //todo: call LoginFlow.success
        Conf.loginFlow.success(name)
        ???
    }
  }


  def recover(cmdException: CommandException)

}