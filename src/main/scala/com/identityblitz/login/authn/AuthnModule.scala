package com.identityblitz.login.authn

/**
  */
trait AuthnModule {

  /**
   * This method is called exactly once after instantiating the authentication module. The method must be completed
   * successfully before the login module is asked to do any work.
   * @param options the map of the options which was specified in the configuration.
   * @return the initialized instance of the login module.
   */
  def init(options: Map[String, String]): AuthnModule


  def bind()

}
