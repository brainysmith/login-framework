package com.identityblitz.login.service.spi

/**
 * The service provides access to the configuration.
 */
trait LoginConfService {
  /**
   * Returns a configuration value corresponding to the specified name.
   * @param name - name of configuration parameter.
   * @return - [[Some]] with a parameter value if it specified otherwise [[None]].
   */
  def getOptLong(implicit name: String): Option[Long]

  /**
   * Returns a configuration value corresponding to the specified name.
   * @param name - name of configuration parameter.
   * @return - [[Some]] with a parameter value if it specified otherwise [[None]].
   */
  def getOptString(implicit name: String): Option[String]

  /**
   * Returns a configuration value corresponding to the specified name.
   * @param name - name of configuration parameter.
   * @return - [[Some]] with a parameter value if it specified otherwise [[None]].
   */
  def getOptBoolean(implicit name: String): Option[Boolean]

  /**
   * Returns a [[Map]] with parameter name and value grouped by keys.
   * @param name - name of group configuration parameter.
   * @return [[Map]] with parameter name and value grouped by keys. If there isn't configuration with the specified name
   *        returns empty [[Map]].
   */
  def getDeepMapString(name: String): Map[String, Map[String, String]]
}
