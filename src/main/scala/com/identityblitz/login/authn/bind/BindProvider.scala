package com.identityblitz.login.authn.bind

/**
 *
 * @param options the map of the options which was specified in the configuration.
 */
abstract class BindProvider(val name: String, val options: Map[String, String]) extends WithBind {

}