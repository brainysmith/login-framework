package com.identityblitz.login.error

import scala.util.Try


trait LoginError {

  def name: String

  def params: Seq[String]

  override def toString: String = new StringBuilder("LoginError(name=").append(name)
    .append(", params=").append(params)
    .append(")")
    .toString()
}

case class CustomLoginError(name: String, params: Seq[String] = Seq()) extends LoginError

/**
 * Enumeration of build in errors of a login process.
 */
object BuiltInErrors extends Enumeration {
  import scala.language.implicitConversions

  case class BuiltInError(name: String, params: Seq[String] = Seq()) extends Val(name) with LoginError

  val INTERNAL = BuiltInError("internal")
  val INVALID_CREDENTIALS = BuiltInError("invalid_credentials")
  val NO_SUBJECT_FOUND = BuiltInError("no_subject_found")
  val NO_CREDENTIALS_FOUND = BuiltInError("no_credential_found")
  val ACCOUNT_IS_LOCKED = BuiltInError("account_is_locked")
  val PASSWORD_EXPIRED = BuiltInError("password_expired")
  val WRONG_CURRENT_PASSWORD = BuiltInError("wrong_current_password")
  val INAPPROPRIATE_NEW_PASSWORD = BuiltInError("inappropriate_new_password")
  val FLOW_NOT_COMPLETED_PROPERLY = BuiltInError("flow_not_completed_properly")
  val NO_SUBJECT_SESSION_FOUND = BuiltInError("no_subject_session_found")

  implicit def valueToBuiltInError(v: Value): BuiltInError = v.asInstanceOf[BuiltInError]

  implicit def valueToString(err: BuiltInError): String = err.name

  def valueOf(name: String): BuiltInError = this.withName(name)

  def optValueOf(name: String): Option[BuiltInError] = Try(valueOf(name)).toOption
}
