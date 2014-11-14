package com.identityblitz.login.cmd

import com.identityblitz.jwt.{SimpleCryptoService, JwsToolkit, AlgorithmsKit}
import com.identityblitz.login.error.{LoginException, CommandException}
import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.json.{Json, JVal}
import com.identityblitz.login.LoginFramework.logger
import com.identityblitz.login.util.Base64Util._
import scala.annotation.implicitNotFound

trait Command {

  def name: String

  def execute(implicit iTr: InboundTransport, oTr: OutboundTransport): Either[CommandException, Option[Command]]

  def selfpack(implicit itr: InboundTransport): String

}

object Command {

  trait JwtToolkit extends AlgorithmsKit with JwsToolkit with SimpleCryptoService {
    val octKey: Array[Byte]
    override val kidsRegister = new KidsRegister {
      override def get(kid: String): Option[JWK] = kid match {
        case "lgn_ctx" => Some(SymmetricKey(octKey))
        case _ => None
      }
      override def defaultKids(alg: String): Seq[JWK] =
        throw new UnsupportedOperationException("defaultKids(alg: String)")
      override def defaultKids(alg: String, ctx: Map[String, String]): Seq[JWK] =
        throw new UnsupportedOperationException("defaultKids(alg: String, ctx: Map[String, String])")
      override def apply(kid: String): JWK = throw new UnsupportedOperationException("apply()")
    }
  }

  def unpack[T <: Command](str: String)(implicit reader: CmdReader[T], itr: InboundTransport): Either[LoginException, T] = {
    object UnpackToolKit extends JwtToolkit {
      override val octKey: Array[Byte] = itr.getLoginCtx.get.sessionKey
    }
    import UnpackToolKit._
    reader.read(JWT[JVal](str).payload)
  }

  def pack[T <: Command](cmd: T)(implicit writer: CmdWriter[T], itr: InboundTransport): String = {
    object PackToolKit extends JwtToolkit {
      override val octKey: Array[Byte] = itr.getLoginCtx.get.sessionKey
    }
    import PackToolKit._
    import JWSNameKit._
    builder
      .alg(HS256)
      .header(
        typ % "JWT",
        kid % "lgn_ctx")
      .payload(writer.write(cmd))
      .build.asBase64
  }



  /*private val cmdMap = Map[String, (JVal => Command)](
    BindCommand.COMMAND_NAME -> {(state: JVal) => BindCommand(state)},
    ChangePswdCmd.COMMAND_NAME -> {(state: JVal) => ChangePswdCmd(state)}
  )*/

  /*def apply[T <: Command](base64Cmd: String): T = {
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
  }*/

}

@implicitNotFound("No reader found for command of type ${C}. Try to implement an implicit CmdReader.")
trait CmdReader[C <: Command] {
  def read(str: JVal): Either[LoginException, C]
}

@implicitNotFound("No writer found for command of type ${C}. Try to implement an implicit CmdWriter.")
trait CmdWriter[C <: Command] {
  def write(cmd: C): JVal
}
