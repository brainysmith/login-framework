package com.identityblitz.login

import com.identityblitz.login.provider.{WithBind, Provider}
import com.identityblitz.login.provider.method.{PassiveMethodProvider, ActiveMethodProvider}
import App.logger


/**
 */
case class AuthnMethod(name: String, activeProvider: Option[ActiveMethodProvider] = None,
                       passiveProviders: List[PassiveMethodProvider] = List.empty,
                       bindProviders: List[Provider with WithBind] = List.empty, default: Boolean = false)


object AuthnMethod {

  def apply(name: String, options: Map[String, String], providers: Map[String, Provider]): AuthnMethod = {
    def findProvider[A](name: Option[String], classes: Class[_]*) = name.flatMap(providers.get)
      .filter(p => classes.forall(cls => {cls.isAssignableFrom(p.getClass)}))
      .map(_.asInstanceOf[A])
      .orElse{
      if (logger.isDebugEnabled)
        logger.warn("Authentication method '{}': '{}' not specified or it not implements '{}'",
          name, classes.map(_.getSimpleName))
      None
    }

    val activeProvider = findProvider(options.get("active-provider"), classOf[ActiveMethodProvider])

    val passiveProvider = options.get("passive-provider").map(_.split(","))
      .getOrElse[Array[String]](Array())
      .flatMap(pName => findProvider[PassiveMethodProvider](Some(pName), classOf[PassiveMethodProvider]))

    val bindProviders =  options.get("bind-providers").map(_.split(","))
      .getOrElse[Array[String]]({
        logger.warn(s"Configuration warning: 'bind-providers' is not specified for '$name' authentication method.")
        Array()})
      .flatMap(pName => findProvider[Provider with WithBind](Some(pName), classOf[Provider], classOf[WithBind]))

    val default = options.get("default").exists(_.toBoolean)

    apply(name, activeProvider, passiveProvider.toList, bindProviders.toList, default)
  }

}
