package com.identityblitz.login

import com.identityblitz.login.error.LoginException
import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.util.Reflection
import com.identityblitz.login.LoginFramework._

/**
  */
trait Handler {

  def options: Map[String, String]

}

trait WithName {
  this: Handler =>

  def name: String

}


trait WithStart {
  this: Handler =>

  @throws(classOf[LoginException])
  def start(implicit req: InboundTransport, resp: OutboundTransport)

}

trait WithDo {
  this: Handler =>

  def DO(implicit iTr: InboundTransport, oTr: OutboundTransport)

}

trait HandlerTools {

  protected def getClassName(options: Map[String, String]) = options.get("class").getOrElse({
    val error = "Parameter 'class' is not specified in the options"
    logger.error(error)
    throw new IllegalStateException(error)
  })

  protected def constructor(className: String) = Reflection.getConstructor(className)

}

object Handler extends HandlerTools {

  def apply[A <: Handler](className: String, args: Any*): A = constructor(className).apply(args: _*).asInstanceOf[A]

  def apply[A <: Handler](options: Map[String, String]): A = apply(getClassName(options), options)

  def apply[A <: Handler](t: (String, Map[String, String])): A = apply(getClassName(t._2), t._1, t._2)

}
