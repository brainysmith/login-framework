package com.identityblitz.login.authn.cmd

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.json.{Json, JVal}
import com.identityblitz.login.LoggingUtils._
import scala.util.Try
import com.identityblitz.login.error.CommandException

/**
  */
trait Command {

  def name: String

  def execute(implicit iTr: InboundTransport, oTr: OutboundTransport): Either[CommandException, Option[Command]]

  def saveState: JVal

  final def asString(): String = Json.obj("name" -> name, "state" -> saveState).toJson

}

object Command {

  private val cmdMap = Map[String, (JVal => Command)](
    BindCommand.COMMAND_NAME -> {(state: JVal) => BindCommand(state)}
  )

  def apply[T <: Command](cmdStr: String): T = {
    val jval = JVal.parseStr(cmdStr)
    (jval \ "name").asOpt[String].fold[T]({
      val err = s"Deserialization of the command [$cmdStr] failed: the name attribute is not found"
      logger.error(err)
      throw new IllegalArgumentException(err)
    })(name => cmdMap.get(name).map(_(jval \ "state")).getOrElse({
      val err = s"Deserialization of the command [$cmdStr] failed: unknown command`s name [$name]"
      logger.error(err)
      throw new IllegalArgumentException(err)
    }).asInstanceOf)
  }

}
