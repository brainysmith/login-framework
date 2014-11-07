package com.identityblitz.login.service.spi

/**
 * The service provides access to the configuration.
 */
trait RandomStringService {

  /**
   * Generate a random string of a given length in a given alphabet
   * @param allowedChars - alphabet  of generated string.*
   * @param length - length of generated string.
   * @return - [[Some]] with a parameter value if it specified otherwise [[None]].
   */
  def generate(allowedChars: Array[Char], length: Int): String


}
