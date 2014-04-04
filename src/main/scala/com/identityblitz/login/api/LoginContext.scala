package com.identityblitz.login.api

import com.blitz.idm.app.json.{JWriter, JStr, JObj}

/**
 * Context's interface of the login process.
 */
trait LoginContext {
  import scala.Predef._

  /**
   * Retrieve the current authentication method.
   * See the build in method in [[com.identityblitz.login.BuildInMethods]].
   * @return the identifier of the current authentication method.
   */
  def getCurrentMethod: Option[Int]

  /**
   * Retrieve login methods that have been successfully completed.
   * See the build in method in [[com.identityblitz.login.BuildInMethods]].
   * @return the bitmask of the login methods.
   */
  def getCompletedMethods: Option[Int]

  /**
   * Retrieve of the current status.
   * See the possible statuses in [[com.identityblitz.login.api.LoginStatus]].
   * @return the current status of the login process.
   */
  def getStatus: LoginStatus

  /**
   * Retrieve the array of credentials associated with the current login request.
   * After the request has been processed credentials will be discarding (they will not be serialized) and
   * the next login request with this login context it wouldn't be able to get one.
   * @return array of the credentials associated with current http request.
   */
  def getCredentials: Seq[JObj]

  /**
   * Appends the specified credential to the credential's array.
   * @param crls the credentials which must be added.
   * @return the current login context.
   */
  def withCredentials(crls: JObj): LoginContext

  /**
   * Retrieve claims about the current subject which was added by login modules.
   * @return json with claims
   */
  def getClaims: JObj

  /**
   * Add a specified claim to the current claims. If the claim with the same name already exists it is
   * overwritten.
   * @param claim the tuple with the claim name and value.
   * @tparam T type of the claim value, for example: Int, Boolean, String and others type of the JSON.
   * @return the current login context.
   */
  def withClaim[T](claim: (String, T))(implicit writer: JWriter[T]): LoginContext

  /**
   * Add a specified claims to the current claims. All claims with the same name will be overwritten.
   * @param claims the json object with additional claims.
   * @return the current login context.
   */
  def withClaims(claims: JObj): LoginContext

  /**
   * Retrieve the parameters associated with the current login process.
   * @return json parameters.
   */
  def getParams: JObj

  /**
   * Adds a specified parameter to the current parameters. If the parameter with the same name already exists it is
   * overwritten.
   * @param param the tuple with the parameter name and value.
   * @tparam T type of the parameter value, for example: Int, Boolean, String and others type of the JSON.
   * @return the current login context.
   */
  def withParam[T](param: (String, T))(implicit writer: JWriter[T]): LoginContext

  /**
   * Add a specified parameters to the current parameters. All parameters with the same name will be overwritten.
   * @param params the json object with additional parameters.
   * @return the current login context.
   */
  def withParams(params: JObj): LoginContext

  /**
   * Retrieve the login module which has performed the current authentication successfully.
   * @return None if an authentication has not performed yet for the current authentication method and some login
   *         module otherwise.
   */
  def getLoginModule[B <: LoginModule]: Option[B]

  /**
   * Retrieve the obligation which must be executed before the login process will be continued. After the request has
   * been processed the obligation will be discarding (it will not be serialized) and the next login request with this
   * login context it wouldn't be able to get one.
   * @return None if there isn't obligation ans some string representing the obligation otherwise.
   */
  def getObligation: Option[Any]

  /**
   * Sets an obligation which must be executed before the login process will be continued.
   * @param obligation the obligation.
   * @return the current login context.
   */
  def withObligation(obligation: Any): LoginContext

  /**
   * Retrieve all errors associated with the current login context and request. After the request has been processed the
   * all errors will be discarding (they will not be serialized) and the next login request with this login context it
   * wouldn't be able to get one.
   * @return the sequence of the tuple with error's key and error's localized message.
   * */
  def getErrors: Seq[(String, String)]

  /**
   * Retrieve an error's localized message for the specified error's key.
   * @param key error's key.
   * @return the error's localized message.
   * */
  def getError(key: String): Option[String]

  /**
   * Appends an error to the error's array of the current login context.
   * @param key the error's key.
   * @param msg the error's localized message.
   * @return the current login context.
   */
  def withError(key: String, msg: String): LoginContext


  /**
   * Appends a common login error to the error's array of the current login context. The error's key will be:
   * "BuildInError." + common login error's name.
   * @param error the common login error.
   * @param msg the localized error's message.
   * @return the current login context.
   */
  def withError(error: BuildInError, msg: String): LoginContext

  /**
   * Appends a common login error to the error's array of the current login context. The error's key will be:
   * "BuildInError." + common login error's name. The error's localized message will be got from the build in bundle.
   * @param error - common login error.
   * @param args the error arguments.
   * @return the current login context.
   */
  def withError(error: BuildInError, args: Any*)(implicit request: Request[AnyContent]): LoginContext


  /**
   * Retrieve warnings associated with the current login context and request. After the request has been processed the
   * all warnings will be discarding (they will not be serialized) and the next login request with this login context
   * it wouldn't be able to get one.
   * @return sequence of the tuple with warning's key and warning's localized message.
   * */
  def getWarns: Seq[(String, String)]

  /**
   * Appends a warn to the warnings.
   * @param key the warning's key.
   * @param msg the warning's localized message.
   * @return the current login context.
   */
  def withWarn(key: String, msg: String): LoginContext
}

object LoginContext {

  def basic(lgnAndPwd: (String, String)): LoginContext = {
    LoginContext() withCredentials JObj(Seq("lgn" -> JStr(lgnAndPwd._1), "pswd" -> JStr(lgnAndPwd._2)))
  }

  def apply(): LoginContext = {
    new LoginContextImpl()
  }
}