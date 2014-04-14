package com.identityblitz.login.util

import scala.reflect.runtime.{universe => ru}

object Reflection {

  private val mirror = ru.runtimeMirror(getClass.getClassLoader)

  def getConstructor(className: String) = {
    val classSymbol = mirror.classSymbol(Class.forName(className))
    mirror.reflectClass(classSymbol).reflectConstructor(classSymbol.toType.declaration(ru.nme.CONSTRUCTOR).asMethod)
  }

}
