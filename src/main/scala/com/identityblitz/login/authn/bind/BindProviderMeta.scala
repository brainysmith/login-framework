package com.identityblitz.login.authn.bind

import com.identityblitz.login.util.Reflection
import com.identityblitz.login.LoggingUtils._

private[login] case class BindProviderMeta(name: String, options: Map[String, String]) {

  val className = options.get("class").getOrElse({
    val error = s"parameter 'class' is not specified for '$name' provider"
    logger.error(error)
    throw new IllegalStateException(error)
  })


  val classConstructor = Reflection.getConstructor(className)

  def newInstance: BindProvider = classConstructor.apply(options).asInstanceOf[BindProvider]

  override def toString: String = {
    val sb =new StringBuilder("BindProviderMeta(")
    sb.append("name -> ").append(name).append(",")
      .append("class -> ").append(className).append(",")
      .append(", ").append("options -> ").append(options)
      .append(")").toString()
  }

}


