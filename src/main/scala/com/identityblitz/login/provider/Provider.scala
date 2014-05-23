package com.identityblitz.login.provider

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.error.{LoginError, LoginException}
import scala.util.Try
import com.identityblitz.json.JObj
import com.identityblitz.login.cmd.Command
import com.identityblitz.login.util.Reflection
import com.identityblitz.login.App.logger

trait Provider {

  def name: String

  def options: Map[String, String]

}

trait WithStart {
  this: Provider =>

  @throws(classOf[LoginException])
  def start(implicit req: InboundTransport, resp: OutboundTransport)  

}

trait WithDo {
  this: Provider =>

  def DO(implicit iTr: InboundTransport, oTr: OutboundTransport)

}

trait WithBind {
  this: Provider =>

  def bind(data: Map[String, String]): Either[LoginError, (JObj, Option[Command])]

}

trait WithAttributes {
  this: Provider =>

  def populate(data: Map[String, String]): Try[JObj]

}

trait WithChangePassword {
  this: Provider =>

  def changePswd(userId: String, curPswd: String, newPswd: String): Either[LoginError, (JObj, Option[Command])]

}

object Provider {

  private def constructor(options: Map[String, String]) = options.get("class").map(Reflection.getConstructor).getOrElse({
    val error = "Parameter 'class' is not specified in the options"
    logger.error(error)
    throw new IllegalStateException(error)
  })

  def apply[A <: Provider](name: String, options: Map[String, String]): A = constructor(options).apply(Array(name, options): _*).asInstanceOf[A]

  def apply[A <: Provider](t: (String, Map[String, String])): A = apply(t._1, t._2)

}