package com.identityblitz.login.service

import java.util.ServiceLoader
import com.identityblitz.login.service.spi.{RandomStringService, LoginConfService}

/**
 */
object ServiceProvider {

  lazy val confService = {
    val csItr = ServiceLoader.load(classOf[LoginConfService]).iterator()
    if(!csItr.hasNext)
      throw new RuntimeException("log configuration service is undefined.")
    csItr.next()
  }

  lazy val randomStrService = {
    val csItr = ServiceLoader.load(classOf[RandomStringService]).iterator()
    if(!csItr.hasNext)
      throw new RuntimeException("random string generation service is undefined.")
    csItr.next()
  }



}
