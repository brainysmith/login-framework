package com.identityblitz.login.util

import java.security.SecureRandom

object RandomUtil {

  def secureRandomBytes(size: Int) = {
    val random = new SecureRandom()
    val bytes = new Array[Byte](size)
    random.nextBytes(bytes)
    bytes
  }

}
