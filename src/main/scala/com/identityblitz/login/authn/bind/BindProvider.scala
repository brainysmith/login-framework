package com.identityblitz.login.authn.bind

import scala.util.Try

/**
 *
 * @param options the map of the options which was specified in the configuration.
 */
abstract class BindProvider(val options: Map[String, String]) {

  def bind(bindOptions: Map[String, String])(data: Map[String, String]): Try[Map[String, Any]]

}
