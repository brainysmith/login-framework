package com.identityblitz.login.authn.bind

import scala.language.implicitConversions

/**
 * http://docs.oracle.com/javase/1.5.0/docs/api/javax/security/auth/login/Configuration.html
 */
object BindFlag extends Enumeration {
  type BindFlag = Value
  val required, requisite, sufficient, optional = Value

  class BindFlagValue(bindFlag: Value) {
    def isRequired = bindFlag match {
      case `required` | `requisite` => true
      case _                        => false
    }
  }

  implicit def value2BindFlagValue(bindFlag: Value) = new BindFlagValue(bindFlag)
}
