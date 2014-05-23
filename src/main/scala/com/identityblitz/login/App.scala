package com.identityblitz.login

import com.identityblitz.login.service.ServiceProvider
import com.identityblitz.login.provider.Provider
import scala.language.implicitConversions
import org.slf4j.LoggerFactory

/**
 */
object App {
  import ServiceProvider.confService

  val logger = LoggerFactory.getLogger("com.identityblitz.login-framework")

  //val sessionCookieName = confService.getOptString("sessionCookieName").getOrElse("blitz_session")

/*  val providers = confService.getDeepMapString("providers").map{
    case (name, options) =>
      val meta = new ProviderMeta(name, options)
      name -> (meta.newInstance -> meta)
  }*/

/*  val methods = confService.getDeepMapString("authnMethods").map{
    case (name, options) =>
      val meta = AuthnMethodMeta(name, options, resolveProvider)
      name -> (meta.newInstance -> meta)
  }*/

/*  def resolveProvider(name: String) = providers.get(name).getOrElse({
    val err = s"Provider with name $name is not found"
    logger.error(err)
    throw new IllegalArgumentException(err)
  })

  def resolveMethod(name: String) = methods.get(name).getOrElse({
    val err = s"Authentication method with name $name is not found"
    logger.error(err)
    throw new IllegalArgumentException(err)
  })*/

  lazy val providers: Map[String, Provider] = confService.getDeepMapString("providers").map(t => t._1 -> Provider(t))

  lazy val methods = confService.getDeepMapString("authn-methods").map(t => t._1 -> AuthnMethod(t._1, t._2, providers))

  lazy val loginFlow = LoginFlow(confService.getMapString("login-flow"), providers)

}

/*
case class ProviderMeta(name: String, options: Map[String, String]) extends Instantiable[Provider] {

  private object Options extends Enumeration {
    import scala.language.implicitConversions
    type Options = Value

    val CLASS = Value("class")

    implicit def valueToString(v: Value): String = v.toString

    val optionNames = values.map(_.toString)
  }

  import Options._

  override val initArgs = Array[Any](name, options.filter(entry => !optionNames.contains(entry._1)))

  override def toString: String = {
    val sb =new StringBuilder("BindProviderMeta(")
    sb.append("name -> ").append(name).append(",")
      .append("options -> ").append(options)
      .append(")").toString()
  }
}

case class AuthnMethodMeta(name: String, options: Map[String, String],
                           private val resolveProvider: (String) => Provider) extends Instantiable[ActiveMethodProvider] {

  private object Options extends Enumeration {
    import scala.language.implicitConversions
    type Options = Value

    val CLASS = Value("class")
    val DEFAULT = Value("default")
    val BIND_PROVIDERS = Value("bind-providers")
    val ATTRIBUTES_PROVIDER = Value("attributes-providers")


    implicit def valueToString(v: Value): String = v.toString

    val optionNames = values.map(_.toString)
  }

  import Options._
  
  val isDefault = options.get(DEFAULT).fold(false)(_.toBoolean)

  val bindProviders = options.get(BIND_PROVIDERS).map(_.split(",")).getOrElse[Array[String]]({
    logger.warn(s"Configuration warning: 'bind-providers' is not specified for '$name' authentication method.")
    Array()})
    .map(pName => resolveProvider(pName))
    .filter(bp => {classOf[WithBind].isAssignableFrom(bp.getClass)})
    .map(_.asInstanceOf[Provider with WithBind])

  val attributesProviders = options.get(ATTRIBUTES_PROVIDER).map(_.split(",")).getOrElse[Array[String]](Array())
    .map(pName => resolveProvider(pName))
    .filter(bp => {classOf[WithAttributes].isAssignableFrom(bp.getClass)})
    .map(_.asInstanceOf[Provider with WithAttributes])

  override val initArgs = Array[Any](name, options.filter(entry => !optionNames.contains(entry._1)))

  override def toString: String = {
    val sb =new StringBuilder("AuthnMethodMeta(")
    sb.append("name -> ").append(name).append(",")
      .append("options -> ").append(options)
    .append(")").toString()
  }
}

sealed trait Instantiable[A] {

  def options: Map[String, String]

  protected lazy val className = options.get("class").getOrElse({
    val error = "parameter 'class' is not specified in the options"
    logger.error(error)
    throw new IllegalStateException(error)
  })

  private lazy val classConstructor = Reflection.getConstructor(className)

  def initArgs: Array[Any]

  def newInstance: A = classConstructor.apply(initArgs:_*).asInstanceOf[A]

}*/
