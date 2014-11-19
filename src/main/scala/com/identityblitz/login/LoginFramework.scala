package com.identityblitz.login

import com.identityblitz.login.service.ServiceProvider
import com.identityblitz.login.provider.Provider
import scala.language.implicitConversions
import org.slf4j.LoggerFactory
import com.identityblitz.login.method.AuthnMethod
import com.identityblitz.login.session.LoginSessionConf

/**
 */
object LoginFramework {
  import ServiceProvider.confService

  val logger = LoggerFactory.getLogger("com.identityblitz.login-framework")

  lazy val providers: Map[String, Provider] = confService.getDeepMapString("providers").map(t => t._1 -> Handler(t))

  private lazy val commandsOptions: Map[String, Map[String,String]] = confService.getDeepMapString("commands")

  def commandConf(name: String): Option[Map[String,String]] = commandsOptions.get(name)

  lazy val methods: Map[String, AuthnMethod] = confService.getDeepMapString("authn-methods").map(t => t._1 -> Handler(t))

  lazy val loginFlow = LoginFlow(confService.getMapString("login-flow"))

  lazy val logoutFlow = LogoutFlow(confService.getMapString("logout-flow"))

  lazy val sessionConf = LoginSessionConf(confService.getOptString("session.cookie.name").getOrElse("bs"),
    confService.getOptLong("session.ttl").getOrElse(1800L) * 1000L,
    confService.getOptString("session.cookie.path").getOrElse("/"),
    confService.getOptString("session.cookie.domain"),
    confService.getOptBoolean("session.cookie.secure").getOrElse(true),
    confService.getOptBoolean("session.cookie.httpOnly").getOrElse(true),
    confService.getOptLong("session.inactive-period").getOrElse(1800L)
  )

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
