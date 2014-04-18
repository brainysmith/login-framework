package com.identityblitz.login.authn.cmd

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.json.JVal
import com.identityblitz.login.error.CommandException


/**
 
 */
case class ChangePswdCmd(login: String, curPswd: String, newPswd: String) extends Command {
  import ChangePswdCmd._

  override val name: String = COMMAND_NAME

  override def execute(implicit req: InboundTransport, resp: OutboundTransport): Either[CommandException, Option[Command]]= ???

  override def saveState: JVal = ???
}

private[cmd] object ChangePswdCmd {

  val COMMAND_NAME = "changePswd"

}
