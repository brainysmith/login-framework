package com.identityblitz.login.authn

private[login] case class AuthnMethodMeta(private val className: String,
                                     private val options: Map[String, String]) {
  import com.identityblitz.login.authn.AuthnMethodMeta._
  import scala.reflect.runtime.{universe => ru}

  private val mirror = ru.runtimeMirror(getClass.getClassLoader)

  private val clsSymbol = mirror.classSymbol(Class.forName(normalizeClassName))

  private val clsMirror = mirror.reflectClass(clsSymbol)

  private val clsConstructor = clsMirror.reflectConstructor(clsSymbol.toType.declaration(ru.nme.CONSTRUCTOR).asMethod)

  val default = options.get(DEFAULT_PARAM_NAME).fold(false)(_.toBoolean)


  def newInstance: AuthnMethod = clsConstructor.apply(options).asInstanceOf[AuthnMethod]

  override def toString: String = {
    val sb =new StringBuilder("LoginModuleMeta(")
    sb.append("class -> ").append(normalizeClassName)
    sb.append(", ").append("options -> ").append(options)
    sb.append(")").toString()
  }

  private def normalizeClassName = className.replace('>', '.').replaceAll("\"", "") //todo: temporary
}

private[login] object AuthnMethodMeta {
  val DEFAULT_PARAM_NAME = "default"
}
