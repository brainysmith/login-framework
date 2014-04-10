package com.identityblitz.login.error

import com.identityblitz.lang.scala.CustomEnumeration


sealed abstract class BuildInError(private val _name: String) extends BuildInError.Val {
  def name = _name
  def getKey: String = "BuildInErrors." + name
}

/**
 * Enumeration of build in errors of a login process.
 */
object BuildInError extends CustomEnumeration[BuildInError] {
  case object INTERNAL extends BuildInError("internal")
  case object NO_CREDENTIALS_FOUND extends BuildInError("no_credential_found")
  case object NO_SUBJECT_FOUND extends BuildInError("no_subject_found")
  case object INVALID_CREDENTIALS extends BuildInError("invalid_credentials")
  case object ACCOUNT_IS_LOCKED extends BuildInError("account_is_locked")
  case object PASSWORD_EXPIRED extends BuildInError("password_expired")
  case object WRONG_OLD_PASSWORD extends BuildInError("wrong_old_password")
  case object INAPPROPRIATE_NEW_PASSWORD extends BuildInError("inappropriate_new_password")
}
