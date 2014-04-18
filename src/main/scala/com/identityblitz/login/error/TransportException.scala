package com.identityblitz.login.error


/**
  */

class TransportException(message: String, cause: Throwable) extends LoginException(message, cause) {
  def this(cause: Throwable) = this(null, cause)
  def this(message: String) = this(message, null)
}

object TransportException {
  def apply(cause: Throwable) = new TransportException(cause)
  def apply(message: String) = new TransportException(message)
  def apply(message: String, cause: Throwable) = new TransportException(message, cause)
}

