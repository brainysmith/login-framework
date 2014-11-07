package com.identityblitz.login.service

import com.identityblitz.login.service.spi.RandomStringService

class SimpleRandomStrService extends RandomStringService {


  override def generate(allowedChars: Array[Char], length: Int) = {
    import com.identityblitz.login.service.SimpleRandomStrService._

      val sb = new StringBuilder
      for (i <- 1 to length) {
        val randomIndex = random.nextInt(allowedChars.length)
        sb.append(allowedChars(randomIndex))
      }
      sb.toString
  }
}

private object SimpleRandomStrService {
  private val random = util.Random

}
