package com.identityblitz.login

private[login] class LoginModuleMeta(private val className: String,
                                     private val options: Map[String, String]) extends Ordered[LoginModuleMeta]{
  import LoginModuleMeta._

  private val mirror = ru.runtimeMirror(getClass.getClassLoader)
  private val clsSymbol = mirror.classSymbol(Class.forName(className.replace('>', '.').replaceAll("\"", ""))) //todo: temporary + if login modules params not set then it will be ignored
  private val clsMirror = mirror.reflectClass(clsSymbol)
  private val clsConstructor = clsMirror.reflectConstructor(clsSymbol.toType.declaration(ru.nme.CONSTRUCTOR).asMethod)

  private val order = options.get(ORDER_PARAM_NAME).fold(Int.MaxValue)(augmentString(_).toInt)

  def newInstance: LoginModule = {
    val instance: LoginModule = clsConstructor().asInstanceOf[LoginModule]
    options.find(t => t._1 == "attrs").map(t => instance.attrMeta = Some(AttrMeta.parseArray(t._2)))
    instance.init(options)
    instance
  }

  def getClassName = className.replace('>', '.').replaceAll("\"", "") //todo: temporary
  def getOptions = options
  def getOrder = order

  def compare(that: LoginModuleMeta): Int = this.getOrder - that.getOrder

  override def toString: String = {
    val sb =new StringBuilder("LoginModuleMeta(")
    sb.append("class -> ").append(getClassName)
    sb.append(", ").append("order -> ").append(getOrder)
    sb.append(", ").append("options -> ").append(getOptions)
    sb.append(")").toString()
  }
}

private[login] object LoginModuleMeta {
  val ORDER_PARAM_NAME = "order"
}
