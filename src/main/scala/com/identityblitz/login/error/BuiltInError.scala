package com.identityblitz.login.error

import com.identityblitz.lang.scala.CustomEnumeration


sealed abstract class BuiltInError(private val _name: String) extends BuiltInError.Val {
  def name = _name
  def getKey: String = "BuildInErrors." + name
}

/**
 * Enumeration of build in errors of a login process.
 */
object BuiltInError extends CustomEnumeration[BuiltInError] {
  import scala.language.implicitConversions

  case object INTERNAL extends BuiltInError("internal"); INTERNAL()
  case object NO_CREDENTIALS_FOUND extends BuiltInError("no_credential_found"); NO_CREDENTIALS_FOUND()
  case object NO_SUBJECT_FOUND extends BuiltInError("no_subject_found"); NO_SUBJECT_FOUND()
  case object INVALID_CREDENTIALS extends BuiltInError("invalid_credentials"); INVALID_CREDENTIALS()
  case object ACCOUNT_IS_LOCKED extends BuiltInError("account_is_locked"); ACCOUNT_IS_LOCKED()
  case object PASSWORD_EXPIRED extends BuiltInError("password_expired"); PASSWORD_EXPIRED()
  case object WRONG_OLD_PASSWORD extends BuiltInError("wrong_old_password"); WRONG_OLD_PASSWORD()
  case object INAPPROPRIATE_NEW_PASSWORD extends BuiltInError("inappropriate_new_password"); INAPPROPRIATE_NEW_PASSWORD()

  implicit def valueToString(err: BuiltInError): String = err.name
}
