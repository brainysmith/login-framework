package com.identityblitz.login.authn.method

import com.identityblitz.login.util.Reflection
import com.identityblitz.login.authn.method.AuthnMethodMeta._

private[login] case class AuthnMethodMeta(private val _className: String, options: Map[String, String]) {

  val className = _className.replace('>', '.').replaceAll("\"", "")

  val default = options.get(DEFAULT_PARAM_NAME).fold(false)(_.toBoolean)

  val classConstructor = Reflection.getConstructor(className)

  def newInstance: AuthnMethod = classConstructor.apply(options).asInstanceOf[AuthnMethod]

  override def toString: String = {
    val sb =new StringBuilder("AuthnMethodMeta(")
    sb.append("class -> ").append(className)
    sb.append(", ").append("options -> ").append(options)
    sb.append(")").toString()
  }

}

private[login] object AuthnMethodMeta {

  val DEFAULT_PARAM_NAME = "default"

}
