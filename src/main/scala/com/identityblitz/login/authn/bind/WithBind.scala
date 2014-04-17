package com.identityblitz.login.authn.bind

import com.identityblitz.login.authn.BindRes


/**
 */
trait WithBind {

  def bind(data: Map[String, String]): BindRes

}