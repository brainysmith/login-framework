package com.identityblitz.login.util

import org.apache.commons.codec.binary.Base64

/**
 */
object Base64Util {

  def decode(base64: String) = Base64.decodeBase64(base64)

  def decodeAsString(base64: String) = new String(Base64.decodeBase64(base64), "UTF-8")

  def encode(source: Array[Byte]): String = Base64.encodeBase64String(source)

  def encode(source: String): String = Base64.encodeBase64String(source.getBytes("UTF-8"))

}
