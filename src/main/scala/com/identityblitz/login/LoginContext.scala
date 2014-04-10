package com.identityblitz.login

import com.identityblitz.json._
import scala.collection.mutable
import com.identityblitz.json.JSuccess
import com.identityblitz.login.LoggingUtils._

/**
 */
trait LoginContext {

  /**
   * Defines a callback uri to redirect when login flow is completed.
   * @return string represented callback uri.
   */
  def callbackUri: String
  
  /**
   * Defines login methods that have been successfully completed.
   * @return array of login methods that have been successfully completed.
   */
  def completedMethods: Seq[String]

  /**
   * Defines the parameters associated with the current login process.
   * @return json parameters.
   */
  def params: JObj

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
   * Retrieve claims about the current subject.
   * @return json with claims
   */
  def claims: JObj

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
   * Returns a string representation of this [[com.identityblitz.login.LoginContext]].
   * @return - string representation of this [[com.identityblitz.login.LoginContext]].
   */
  def asString: String

}

object LoginContext {

  /**
   * Creates a new implementation of the [[LoginContext]] in accordance with the parameters.
   *
   * @param callbackUri - string representing the callback uri.
   * @return login context
   */
  def apply(callbackUri: String): LoginContext = new LoginContextImpl(callbackUri)

  /**
   * Creates a new implementation of the [[LoginContext]]] from string representation.
   *
   * @param str - string representation of [[LoginContext]]].
   * @return login context.
   */
  def fromString(str: String) :LoginContext = LoginContextImpl.fromString(str)

}


private[login] class LoginContextImpl extends LoginContext {

  private var _callbackUri: String = _
  private val _completedMethods = new mutable.ArrayBuffer[String]()
  private var _claims = JObj(Seq())
  private var _params = JObj(Seq())

  def this(callbackUri: String) = {
    this()
    if (callbackUri == null || callbackUri.isEmpty) {
      logger.error("callbackUri can't be null or empty")
      throw new IllegalArgumentException("callbackUri can't be null or empty")
    }

    _callbackUri = callbackUri
  }

  override def callbackUri: String = _callbackUri

  override def completedMethods: Seq[String] = _completedMethods.toSeq

  /**
   * Adds a specified authentication method to the methods which has already been completed successfully.
   * @param method - a name of the authentication method which has been completed.
   * @return updated login context.
   */
  def addCompletedMethod(method: String): LoginContextImpl = {
    _completedMethods += method
    this
  }

  override def claims: JObj = _claims

  override def withClaim[T](claim: (String, T))(implicit writer: JWriter[T]): LoginContext = {
    _claims = _claims +! claim
    this
  }

  override def withClaims(claims: JObj): LoginContext = {
    this._claims = this._claims ++! claims
    this
  }

  override def params: JObj = _params

  override def withParam[T](param: (String, T))(implicit writer: JWriter[T]): LoginContext = {
    _params = _params +! param
    this
  }

  override def withParams(params: JObj): LoginContext = {
    this._params = this._params ++! params
    this
  }

  def asString: String = Json.toJson(this).toJson
}


private[login] object LoginContextImpl {

  implicit def jwriter: JWriter[LoginContextImpl] = new JWriter[LoginContextImpl] {
    def write(o: LoginContextImpl): JVal = Json.obj(
      "callbackUri" -> JStr(o.callbackUri),
      "completedMethods" -> JArr(o.completedMethods.map(JStr(_)).toArray),
      "params" -> o.params,
      "claims" -> o.claims
    )
  }

  implicit def jreader: JReader[LoginContextImpl] = new JReader[LoginContextImpl] {
    def read(v: JVal): JResult[LoginContextImpl] = {
      Right[String, LoginContextImpl](new LoginContextImpl()).right.map(lc => {
        (v \ "callbackUri").asOpt[String].fold[Either[String, LoginContextImpl]](Left("callbackUri.notFound"))(s => {
          lc._callbackUri = s
          Right(lc)
        })
      }).joinRight.right.map(lc => {
        (v \ "completedMethods").asOpt[Array[String]].fold[Either[String, LoginContextImpl]](Right(lc))(m => {
          lc._completedMethods ++= m
          Right(lc)
        })
      }).joinRight.right.map(lc => {
        (v \ "params").asOpt[JVal].fold[Either[String, LoginContextImpl]](Right(lc))(jp => {
          lc._params = jp.asInstanceOf[JObj]
          Right(lc)
        })
      }).joinRight.right.map(lc => {
        (v \ "claims").asOpt[JVal].fold[Either[String, LoginContextImpl]](Right(lc))(jc => {
          lc._claims = jc.asInstanceOf[JObj]
          Right(lc)
        })
      }).joinRight match {
        case Left(err) => {
          logger.error("can't read login context from json [error = {}, json = {}]", err, v.toJson)
          JError(err)
        }
        case Right(lc) => {
          logger.trace("the login context has been read from json successfully [lc = {}]", lc)
          JSuccess(lc)
        }
      }
    }
  }

  def fromString(str: String) :LoginContextImpl = Json.fromJson[LoginContextImpl](JVal.parseStr(str))

}