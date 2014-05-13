package com.identityblitz.login

/**
 */
object FlowAttrName {

  val CALLBACK_URI_NAME = "callback_uri"

  val AUTHN_METHOD_NAME = "authn_method"

  val COMMAND = "command"

  val COMMAND_NAME = "command_name"

  val HTTP_METHOD = "http_method"

  val ERROR = "error"

  val REDIRECT = "redirect"

  lazy val set = getClass.getDeclaredMethods.filter(_.getReturnType == classOf[String]).map(_.invoke(this).asInstanceOf[String]).toSet
}
