package com.identityblitz.login.authn.module

/**
 *
 * @param options the map of the options which was specified in the configuration.
 */
abstract class AuthnModule(val options: Map[String, String]) {

  def bind()

}
