package com.identityblitz.login.service

import java.util.ServiceLoader
import com.identityblitz.login.service.spi.{SecurityEventService, CryptoService, LoginConfService}

/**
 */
object ServiceProvider {

  lazy val confService = {
    val csItr = ServiceLoader.load(classOf[LoginConfService]).iterator()
    if(!csItr.hasNext)
      throw new RuntimeException("log configuration service is undefined.")
    csItr.next()
  }

  lazy val cryptoService = {
    val csItr = ServiceLoader.load(classOf[CryptoService]).iterator()
    if(!csItr.hasNext)
      throw new RuntimeException("crypto service is undefined.")
    csItr.next()
  }

  lazy val securityService: Option[SecurityEventService] = {
    val csItr = ServiceLoader.load(classOf[SecurityEventService]).iterator()
    if(!csItr.hasNext)
      None
    Some(csItr.next())
  }



}
