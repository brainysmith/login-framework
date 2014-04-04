package com.identityblitz.build.sbt

import language.experimental.macros
import reflect.macros.Context

/**
 *
 */
object EnumerationMacros {

  def INIT_ENUM_ELEMENTS(): Unit = macro init_enum_elements_impl

  def init_enum_elements_impl(c: Context)(): c.Expr[Unit] = {
    import c.universe._
    val marker = c.enclosingClass.symbol.name.decoded
    val elements = c.enclosingClass.children(0).children.filter(_.symbol.isModule)
      .filter(e => e.children(0).children.filter(_.isType).exists(_.toString().equals(marker)))
    if (elements.isEmpty) {
      c.warning(c.enclosingPosition, "Enumeration object '" + marker + "' has no elements or the name of the class the elements inherited from does not match '" + marker + "'.")
      reify {
      }
    }
    else {
      val initExp = c.Expr[Unit](Block(elements.map(o => Apply(Select(Ident(o.symbol.asModule), newTermName("apply")), List.empty)).toSeq : _*))
      reify {
        initExp.splice
      }
    }
  }

}