package com.identityblitz.login.authn.cmd

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.json.{Json, JVal}
import com.identityblitz.login.LoggingUtils._
import com.identityblitz.login.error.CommandException
import org.apache.commons.codec.binary.Base64

/**
  */
trait Command {

  def name: String

  def execute(implicit iTr: InboundTransport, oTr: OutboundTransport): Either[CommandException, Option[Command]]

  def saveState: JVal

  final def asString(): String =
    Base64.encodeBase64String(Json.obj("name" -> name, "state" -> saveState).toJson.getBytes("UTF-8"))

}

object Command {

  private val cmdMap = Map[String, (JVal => Command)](
    BindCommand.COMMAND_NAME -> {(state: JVal) => BindCommand(state)}
  )

  def apply[T <: Command](cmdStr: String): T = {
    val jval = JVal.parseStr(new String(Base64.decodeBase64(cmdStr), "UTF-8"))
    (jval \ "name").asOpt[String].fold[T]({
      val err = s"Deserialization of the command [$cmdStr] failed: the name attribute is not found"
      logger.error(err)
      throw new IllegalArgumentException(err)
    })(name => cmdMap.get(name).map(_(jval \ "state")).getOrElse({
      val err = s"Deserialization of the command [$cmdStr] failed: unknown command`s name [$name]"
      logger.error(err)
      throw new IllegalArgumentException(err)
    }).asInstanceOf[T])
  }

}
