package com.identityblitz.login.error

/**
  */
class LoginException(message: String, cause: Throwable) extends Exception(message, cause) {
  def this(cause: Throwable) = this(null, cause)
  def this(message: String) = this(message, null)
}


object LoginException {
  def apply(cause: Throwable) = new LoginException(cause)
  def apply(message: String) = new LoginException(message)
  def apply(message: String, cause: Throwable) = new LoginException(message, cause)
}
