package com.identityblitz.login.authn

import com.identityblitz.login.Conf

/**
 */
object AuthnMethods {

  private val authnMethodsMeta =
    (for ((clazz, params) <- Conf.authnMethods) yield AuthnMethodMeta(clazz, params)).toArray

  val authnMethodsMap = authnMethodsMeta.foldLeft[scala.collection.mutable.Map[String, AuthnMethod]](
    scala.collection.mutable.Map())(
      (res, meta) => {
        val instance = meta.newInstance
        if (meta.default) {
          res += ("default" -> instance)
        }
        res += (instance.name -> instance)
      }
    )

}
