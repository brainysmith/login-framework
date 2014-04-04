package com.identityblitz.login.api

import play.api.mvc.Call

/**
 * Defines a flow of the login process.
 * {@note the implementation must be a singleton}
 */
trait LoginFlow {

  /**
   * Retrieve the next point of the login process to redirect the current subject.
   * @return if the current state is not a PROCESSING then returns None otherwise a next point.
   */
  def getNextPoint(implicit lc: LoginContext, request: Request[AnyContent]): Option[Call]
}
