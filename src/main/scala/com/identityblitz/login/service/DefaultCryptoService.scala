package com.identityblitz.login.service

import java.security.SecureRandom
import com.identityblitz.login.service.spi.CryptoService
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.prng.DigestRandomGenerator
import org.bouncycastle.crypto.prng.RandomGenerator

class DefaultCryptoService extends CryptoService {

  override def generateHash(value: Array[Byte], salt: Array[Byte]): Array[Byte] = {
    val toHash = new Array[Byte](value.length + salt.length)
    System.arraycopy(salt, 0, toHash, 0, salt.length)
    System.arraycopy(value, 0, toHash, salt.length, value.length)

    val digest = new SHA256Digest()
    digest.update(toHash, 0, toHash.length)
    val hash = new Array[Byte](digest.getDigestSize)
    digest.doFinal(hash, 0)
    hash
  }

  override def generateRandomString(allowedChars: Array[Char], length: Int): String = {
    if(allowedChars.length < 2 || allowedChars.length > 255)
      throw new IllegalArgumentException("The size of array of allowed chars must be in range from 2 to 255")
    val seed = new Array[Byte](32)
    new SecureRandom().nextBytes(seed)
    generator.addSeedMaterial(seed)

    val randomArray = new Array[Byte](length)
    generator.nextBytes(randomArray)

    val password: StringBuilder = new StringBuilder(8)
    for (elem <- randomArray) {
      password.append(allowedChars((elem & 0xFF) % allowedChars.length))
    }
    password.toString()
  }

  private val generator: RandomGenerator = new DigestRandomGenerator(new SHA256Digest)

  override def generateRandomBytes(length: Int): Array[Byte] = {
    val seed = new Array[Byte](32)
    new SecureRandom().nextBytes(seed)
    generator.addSeedMaterial(seed)
    val randomArray = new Array[Byte](length)
    generator.nextBytes(randomArray)
    randomArray
  }

}
