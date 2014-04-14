package com.identityblitz.login.authn.bind

import com.identityblitz.login.Conf

/**
 */
object BindProviders {

  val providerMap = Conf.binds.map{case (k, v) => k -> new BindProviderMeta(k, v).newInstance}

}
