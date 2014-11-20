package com.identityblitz.login.service.spi

/**
 * The service provides access to the configuration.
 */
trait CryptoService {

  /**
   * Generate a hash for given data
   * @param value - plaintext used for hash
   * @param salt - random data used for hash
   * @return - random string.
   */
  def generateHash(value: Array[Byte], salt:  Array[Byte]): Array[Byte]

  /**
   * Generate a random string of a given length in a given alphabet
   * @param allowedChars - alphabet  of generated string.
   * @param length - length of generated string.
   * @return - random string.
   */
  def generateRandomString(allowedChars: Array[Char], length: Int): String

  /**
   * Generate a array of random bytes.
   * @param length - length of the array being generated
   * @return - generated array
   */
  def generateRandomBytes(length: Int): Array[Byte]

}
