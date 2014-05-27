package com.identityblitz.login

import com.identityblitz.login.service.ServiceProvider
import com.identityblitz.login.provider.Provider
import scala.language.implicitConversions
import org.slf4j.LoggerFactory
import com.identityblitz.login.method.AuthnMethod

/**
 */
object App {
  import ServiceProvider.confService

  val logger = LoggerFactory.getLogger("com.identityblitz.login-framework")

  //val sessionCookieName = confService.getOptString("sessionCookieName").getOrElse("blitz_session")

  lazy val providers: Map[String, Provider] = confService.getDeepMapString("providers").map(t => t._1 -> Handler(t))

  lazy val methods: Map[String, AuthnMethod] = confService.getDeepMapString("authn-methods").map(t => t._1 -> Handler(t))

  lazy val loginFlow = LoginFlow(confService.getMapString("flow"))


  def findProvider[A](name: Option[String], classes: Class[_]*) = name.flatMap(providers.get)
    .filter(p => classes.forall(cls => {cls.isAssignableFrom(p.getClass)}))
    .map(_.asInstanceOf[A])
    .orElse{
    if (logger.isDebugEnabled)
      logger.warn("Authentication method '{}': '{}' not specified or it not implements '{}'",
        name, classes.map(_.getSimpleName))
    None
  }
}
