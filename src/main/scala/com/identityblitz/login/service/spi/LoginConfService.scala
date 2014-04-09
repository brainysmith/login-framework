package com.identityblitz.login.service.spi

/**
 * The service provides access to the configuration.
 */
trait LoginConfService {
  /**
   * Returns a configuration value corresponding to the specified name.
   * @param name - name of configuration parameter.
   * @return - parameter value.
   */
  def getOptLong(implicit name: String): Option[Long]

  /**
   * Returns a configuration value corresponding to the specified name.
   * @param name - name of configuration parameter.
   * @return - parameter value.
   */
  def getOptString(implicit name: String): Option[String]

  /**
   * Returns a configuration value corresponding to the specified name.
   * @param name - name of configuration parameter.
   * @return - parameter value.
   */
  def getOptBoolean(implicit name: String): Option[Boolean]

}
