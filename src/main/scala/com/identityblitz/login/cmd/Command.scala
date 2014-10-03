package com.identityblitz.login.cmd

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.json.{Json, JVal}
import com.identityblitz.login.LoginFramework.logger
import com.identityblitz.login.error.CommandException
import com.identityblitz.login.util.Base64Util._

/**
  */
trait Command {

  def name: String

  def execute(implicit iTr: InboundTransport, oTr: OutboundTransport): Either[CommandException, Option[Command]]

  def saveState: JVal

  final def asString(): String = encode(Json.obj("name" -> name, "state" -> saveState).toJson)

}

object Command {

  private val cmdMap = Map[String, (JVal => Command)](
    BindCommand.COMMAND_NAME -> {(state: JVal) => BindCommand(state)},
    ChangePswdCmd.COMMAND_NAME -> {(state: JVal) => ChangePswdCmd(state)}
  )

  def apply[T <: Command](base64Cmd: String): T = {
    val jVal = JVal.parse(decodeAsString(base64Cmd))
    (jVal \ "name").asOpt[String].fold[T]({
      val err = s"Deserialization of the command [$base64Cmd] failed: the name attribute is not found"
      logger.error(err)
      throw new IllegalArgumentException(err)
    })(name => cmdMap.get(name).map(_(jVal \ "state")).getOrElse({
      val err = s"Deserialization of the command [$base64Cmd] failed: unknown command`s name [$name]"
      logger.error(err)
      throw new IllegalArgumentException(err)
    }).asInstanceOf[T])
  }

}
