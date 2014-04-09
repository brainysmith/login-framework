package com.identityblitz.login

import com.identityblitz.login.service.ServiceProvider
import ServiceProvider.confService

/**
 */
object Conf {

  val loginFlow = confService.getOptString("loginFlow")

}
