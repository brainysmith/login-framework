package com.identityblitz.login.authn.provider

/**
 *
 * @param options the map of the options which was specified in the configuration.
 */
abstract class Provider(val name: String, protected val options: Map[String, String]) {

}