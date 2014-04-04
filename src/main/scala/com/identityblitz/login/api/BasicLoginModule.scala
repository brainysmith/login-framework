package com.identityblitz.login.api

/**
 * Interface of the login module for the basic authentication. It extends the [[services.login.LoginModule]] interface
 * to process the specific for this authentication method obligations.
 */
trait BasicLoginModule extends LoginModule {

  /**
   * Changes a subject's password.
   * @param curPswd the current subject's password.
   * @param newPswd the new subject's password.
   * @param lc the current login context.
   * @param request the current HTTP request.
   * @return the result of the operation. True if password has been changed successfully and false otherwise. Errors can
   *         be obtained from the current login context.
   */
  def changePassword(curPswd: String, newPswd: String)(implicit lc: LoginContext, request: Request[AnyContent]): Boolean
}

/**
 * Enumeration of obligations for the basic authentication.
 */
object BasicLoginModule extends Enumeration {

  object Obligation extends Enumeration {
    type Obligation = Value

    val CHANGE_PASSWORD = Value("change_password")

    class ObligationsVal(obligation: Value)  {
    }

    implicit def valueToObligations(obligation: Value) = new ObligationsVal(obligation)
  }
}
