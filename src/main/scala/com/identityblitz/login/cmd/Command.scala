package com.identityblitz.login.cmd

import com.identityblitz.jwt.{SimpleCryptoService, JwsToolkit, AlgorithmsKit}
import com.identityblitz.login.LoginFramework._
import com.identityblitz.login.error.{LoginException, CommandException}
import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.json.JVal
import scala.annotation.implicitNotFound
import com.identityblitz.login.error.BuiltInErrors._

trait Command {

  def name: String

  def attempts: Int

  def maxAttempts: Option[Int] = None

  def leftAttempts: Option[Int] = maxAttempts.map(max => max - attempts)

  def noAttempts = leftAttempts.fold(false)(_ <= 0)

  def isExpired: Boolean = false

  def attrs: Map[String,String] = Map.empty

  def execute(implicit iTr: InboundTransport, oTr: OutboundTransport): Either[CommandException, Option[Command]] = {
    if (isExpired) {
      logger.error(s"Command '$name' is expired")
      Left(CommandException(this, COMMAND_EXPIRED))
    } else if (noAttempts) {
      logger.error(s"Command '$name' no attempts")
      Left(CommandException(this, COMMAND_NO_ATTEMPTS))
    } else {
      onExecute
    }
  }

  def onExecute(implicit iTr: InboundTransport, oTr: OutboundTransport): Either[CommandException, Option[Command]]


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
      override def defaultKids(alg: String): Seq[JWK] = Seq()
      override def defaultKids(alg: String, ctx: Map[String, String]): Seq[JWK] = Seq()
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

}

@implicitNotFound("No reader found for command of type ${C}. Try to implement an implicit CmdReader.")
trait CmdReader[C <: Command] {
  def read(str: JVal): Either[LoginException, C]
}

@implicitNotFound("No writer found for command of type ${C}. Try to implement an implicit CmdWriter.")
trait CmdWriter[C <: Command] {
  def write(cmd: C): JVal
}
