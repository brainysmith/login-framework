package com.identityblitz.login

import com.identityblitz.login.service.ServiceProvider
import org.scalatest.{FlatSpec, Matchers}

class CryptoServiceTest extends FlatSpec with Matchers {

  behavior of "Crypto service"

  it should "generate random string" in {
    val cryptoSrv = ServiceProvider.cryptoService
    val allowedChars = Array[Char]('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
    val result = cryptoSrv.generateRandomString(allowedChars, 7)
    result.forall(c => allowedChars.contains(c)) should be (true)
  }

  it should "hash must work properly" in {
    val cryptoSrv = ServiceProvider.cryptoService
    val salt = Array[Byte](0x03, 0x03, 0x03)
    val data = Array[Byte](0x07, 0x07, 0x07, 0x07)
    val result = cryptoSrv.generateHash(data, salt)

    val expected = Array(0xd2.asInstanceOf[Byte], 0x11.asInstanceOf[Byte], 0x8a.asInstanceOf[Byte], 0x16.asInstanceOf[Byte],
      0xf3.asInstanceOf[Byte], 0x3c.asInstanceOf[Byte], 0x3e.asInstanceOf[Byte], 0x00.asInstanceOf[Byte],
      0x9a.asInstanceOf[Byte], 0x55.asInstanceOf[Byte], 0x7e.asInstanceOf[Byte], 0xe0.asInstanceOf[Byte],
      0x9c.asInstanceOf[Byte], 0x4e.asInstanceOf[Byte], 0xdd.asInstanceOf[Byte], 0x66.asInstanceOf[Byte],
      0xfd.asInstanceOf[Byte], 0x83.asInstanceOf[Byte], 0xae.asInstanceOf[Byte], 0xe3.asInstanceOf[Byte],
      0xa4.asInstanceOf[Byte], 0x80.asInstanceOf[Byte], 0x8e.asInstanceOf[Byte], 0x45.asInstanceOf[Byte],
      0x89.asInstanceOf[Byte], 0x7a.asInstanceOf[Byte], 0x4d.asInstanceOf[Byte], 0xf5.asInstanceOf[Byte],
      0x74.asInstanceOf[Byte], 0xb9.asInstanceOf[Byte], 0xb0.asInstanceOf[Byte], 0x07.asInstanceOf[Byte])
    result should be (expected)
  }

}
