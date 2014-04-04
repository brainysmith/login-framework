package com.identityblitz.login

/**
 * Enumeration of the build in authentication methods.
 */
object BuildInMethods extends Enumeration {
  type BuildInMethods = Value

  //the identifier must be power of two
  val BASIC = Value(1)
  val OTP = Value(2)
  val SMART_CARD = Value(4)

  implicit def valueToInt(v: Value): Int = v.id
}
