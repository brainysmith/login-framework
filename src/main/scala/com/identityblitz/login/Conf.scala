package com.identityblitz.login

import com.identityblitz.login.service.ServiceProvider
import com.identityblitz.login.LoggingUtils._
import com.identityblitz.login.authn.method.AuthnMethod
import com.identityblitz.login.authn.provider.{WithAttributes, WithBind, Provider}
import com.identityblitz.login.util.Reflection
import scala.language.implicitConversions

/**
 */
object Conf {
  import ServiceProvider.confService

  val providers = confService.getPropsDeepGrouped("providers").map{
    case (name, options) =>
      val meta = new ProviderMeta(name, options)
      name -> (meta.newInstance -> meta)
  }

  def resolveProvider(name: String) = providers.get(name).getOrElse({
    val err = s"Provider with name $name is not found"
    logger.error(err)
    throw new IllegalArgumentException(err)
  })._1

  val methods = confService.getPropsDeepGrouped("authnMethods")
    .map({case (name, options) => AuthnMethodMeta(name, options, resolveProvider)})
    .foldLeft[collection.mutable.Map[String, (AuthnMethod, AuthnMethodMeta)]](collection.mutable.Map())(
      (acm, meta) => {
        val value = meta.newInstance -> meta
        if (meta.isDefault) {
          acm += ("default" -> value)
        }
        acm += (meta.name -> value)
      }
    ).toMap

  val loginFlow = confService.getOptString("loginFlow").fold[LoginFlow]({
    logger.debug("Will use the built-in login flow: {}", BuiltInLoginFlow.getClass.getSimpleName)
    BuiltInLoginFlow
  })(className => {
    logger.debug("Find in the configuration a custom login flow [class = {}]", className)
    Reflection.getConstructor(className).apply().asInstanceOf[LoginFlow]
  })

  def resolveMethod(name: String) = methods.get(name).getOrElse({
    val err = s"Authentication method with name $name is not found"
    logger.error(err)
    throw new IllegalArgumentException(err)
  })._1
}

case class ProviderMeta(name: String, options: Map[String, String]) extends Instantiable[Provider] {

  private object Options extends Enumeration {
    import scala.language.implicitConversions

    type Options = Value
    val `class` = Value

    implicit def valueToString(v: Value): String = v.toString

    val builtInOptions = values.map(_.toString)
  }

  import Options._

  override val initArgs = Array[Any](name, options.filter(entry => !builtInOptions.contains(entry._1)))

  override def toString: String = {
    val sb =new StringBuilder("BindProviderMeta(")
    sb.append("name -> ").append(name).append(",")
      .append("options -> ").append(options)
      .append(")").toString()
  }
}

case class AuthnMethodMeta(name: String, options: Map[String, String],
                           private val resolveProvider: (String) => Provider) extends Instantiable[AuthnMethod] {

  private object Options extends Enumeration {
    import scala.language.implicitConversions

    type Options = Value
    val `class`, default, `bind-providers`, `attributes-providers` = Value

    implicit def valueToString(v: Value): String = v.toString

    val builtInOptions = values.map(_.toString)
  }

  import Options._
  
  val isDefault = options.get(default).fold(false)(_.toBoolean)
  /*todo: restore*/
  val bindProviders = options.get("bind-providers").map(_.split(",")).getOrElse[Array[String]]({
    logger.warn(s"Configuration warning: 'bind-providers' is not specified for '$name' authentication method.")
    Array()})
    .map(pName => resolveProvider(pName))
    .filter(bp => {classOf[WithBind].isAssignableFrom(bp.getClass)})
    .map(_.asInstanceOf[Provider with WithBind])

  val attributesProviders = options.get(`attributes-providers`).map(_.split(",")).getOrElse[Array[String]](Array())
    .map(pName => resolveProvider(pName))
    .filter(bp => {classOf[WithAttributes].isAssignableFrom(bp.getClass)})
    .map(_.asInstanceOf[Provider with WithAttributes])

  override val initArgs = Array[Any](name, options.filter(entry => !builtInOptions.contains(entry._1)))

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

}