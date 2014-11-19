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

  def attrs: Map[String,String] = Map.empty

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
