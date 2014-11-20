package com.identityblitz.login

/**
 */
object FlowAttrName {

  val CALLBACK_URI_NAME = "callback_uri"

  val AUTHN_METHOD_NAME = "authn_method"

  val COMMAND = "command"

  val COMMAND_NAME = "command_name"

  val COMMAND_ATTEMPTS = "command_attempts"

  val HTTP_METHOD = "http_method"

  val ERROR = "error"

  val REDIRECT = "redirect"

  val LOGIN_SESSION = "ls"

  val RELYING_PARTY = "rp"

  val PASSIVE_PROVIDER_ATTR_NAME = "authnPassiveProviders"

  lazy val set = getClass.getDeclaredMethods.filter(_.getReturnType == classOf[String]).map(_.invoke(this).asInstanceOf[String]).toSet
}
