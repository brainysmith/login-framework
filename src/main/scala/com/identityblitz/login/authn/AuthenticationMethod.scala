package com.identityblitz.login.authn

import com.identityblitz.login.api.LoginContext

/**
  */
trait AuthenticationMethod {

  /**
   * The method is called each time before a new authentication method is started. If the login module accept the
   * current login request against the input login context to perform authentication it must do some preparatory
   * actions (e.g. generate challenge) and return true otherwise false.
   * @param lc the context of the current authentication process.
   * @param request the HTTP request.
   * @return true if the login module accept the current login request and false otherwise.
   */
  def start(implicit lc: LoginContext): Boolean


}
