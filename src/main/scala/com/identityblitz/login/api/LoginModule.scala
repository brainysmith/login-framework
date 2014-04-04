package com.identityblitz.login.api

import com.identityblitz.build.sbt.EnumerationMacros._
import com.identityblitz.lang.scala.CustomEnumeration

/**
 * Interface of the login module which performs the authentication and do obligations.
 */
trait LoginModule {

  var attrMeta: Option[Seq[AttrMeta]] = None

  /**
   * The application calls this method exactly once after instantiating the login module.The method must be completed
   * successfully before the login module is asked to do any work.
   * @param options the map of the options which was specified into the configuration.
   * @return the initialized instance of the login module.
   */
  def init(options: Map[String, String]): LoginModule


  /**
   * The method is called each time before a new authentication method is started. If the login module accept the
   * current login request against the input login context to perform authentication it must do some preparatory
   * actions (e.g. generate challenge) and return true otherwise false.
   * @param lc the context of the current authentication process.
   * @param request the HTTP request.
   * @return true if the login module accept the current login request and false otherwise.
   */
  def start(implicit lc: LoginContext, request: Request[AnyContent]): Boolean

  /**
   * The method is called each time need to perform the authentication. The result controls subsequent authentication
   * process. The following represents a description their respective semantics:
   *    - [[com.identityblitz.login.api.Result.SUCCESS]] if the subject successfully authenticated. All claims will be
   *    included in assertions. An obligation in the login context must be done before continue the process. If there
   *    are warning in the login context the controller must shows it before continue the process.
   *    - [[com.identityblitz.login.api.Result.PARTIALLY_COMPLETED]] - if the authentication process is completed
   *    partially. In this case the login process stays at the current step. The next request with the same
   *    authentication method will be processed by the same login module. The login module can store the specific state
   *    of its authentication process by the parameters of the current login context.
   *    - [[com.identityblitz.login.api.Result.FAIL]] if the subject's authentication is failed. The authentication
   *    process is interrupted. A cause can be added to the list of the login context's errors.
   * @param lc the context of the current authentication process.
   * @param request the HTTP request.
   * @return the result of the authentication.
   */
  def `do`(implicit lc: LoginContext, request: Request[AnyContent]): Result
}

sealed abstract class Result(private val _name: String) extends Result.Val {
  def name = _name
}

/**
 * Enumeration of the authentication's results.
 */
object Result extends CustomEnumeration[Result] {
  INIT_ENUM_ELEMENTS()

  case object SUCCESS extends Result("success")
  case object PARTIALLY_COMPLETED extends Result("partially_completed")
  case object FAIL extends Result("fail")
}


object LoginModule {
  def apply(className: String, params: Map[String, String]): LoginModule = {
    new LoginModuleMeta(className, params).newInstance
  }
}

