package com.identityblitz.login

import com.identityblitz.json._

trait LoginContext {
  import com.identityblitz.login.LoginContext._

  /**
   * Defines a callback uri to redirect when login flow is completed.
   * @return string represented callback uri.
   */
  val callbackUri: String

  /**
   * Current authentication method.
   */
  val method: String
  
  /**
   * Defines login methods that have been successfully completed.
   * @return array of login methods that have been successfully completed.
   */
  val completedMethods: Seq[String]

  /**
   * Retrieve claims about the current subject.
   * @return json with claims
   */
  val claims: JObj

  /**
   * Indicates the last updated time.
   * @return the difference, measured in milliseconds, between the last updated time and midnight, January 1, 1970 UTC.
   */
  val updatedOn: Long

  /**
   * Adds a new completed method to the already added ones.
   * @param method - method name.
   * @return - updated login context
   */
  def addMethod(method :String): LoginContext = lcBuilder(this) withCompletedMethod(method) build

  /**
   * Adds or replace, if claim already exists, new claim.
   * @param claim - new claim.
   * @param writer - writer to convert claim value to JSON value.
   * @tparam T - claim value type
   * @return - update login context
   */
  def addClaim[T](claim: (String, T))(implicit writer: JWriter[T]): LoginContext = lcBuilder(this) withClaim(claim) build

  /**
   * Adds or replace, if claims already exists. new claims.
   * @param _claims - new claims.
   * @return - update login context.
   */
  def addClaims(_claims: JObj): LoginContext = lcBuilder(this) withClaims(_claims) build

  def asString: String = Json.toJson(this).toJson

}

object LoginContext {
  implicit val implicits = scala.language.implicitConversions
  implicit val refCalls = scala.language.reflectiveCalls
  implicit val postOps =  scala.language.postfixOps

  implicit def jwriter: JWriter[LoginContext] = new JWriter[LoginContext] {
    def write(o: LoginContext): JVal = Json.obj(
      "updatedOn" -> JNum(o.updatedOn),
      "callbackUri" -> JStr(o.callbackUri),
      "method" -> JStr(o.method),
      "completedMethods" -> JArr(o.completedMethods.map(JStr(_)).toArray),
      "claims" -> o.claims
    )
  }

  implicit def jreader: JReader[LoginContext] = new JReader[LoginContext] {
    def read(v: JVal): JResult[LoginContext] = {
      Right[String, LoginContextBuilder[_, _]](lcBuilder).right
        .flatMap(lcb => (v \ "callbackUri").asOpt[String]
        .fold[Either[String, LoginContextBuilder[READY, _]]](Left("callbackUri.notFound"))(cb => Right(lcb withCallbackUri cb)))}
      .right.flatMap(lcb => (v \ "method").asOpt[String]
        .fold[Either[String, LoginContextBuilder[READY, READY]]](Left("method.notFound"))(m => Right(lcb withMethod m)))
      .right.map{lcb => (v \ "completedMethods").asOpt[Array[String]]
      .fold[LoginContextBuilder[READY, READY]](lcb)(m => m.foldLeft(lcb)(_ withCompletedMethod _))}
      .right.map(lcb => (v \ "claims").asOpt[JVal].fold[LoginContextBuilder[READY, READY]](lcb)(clm => lcb withClaims clm.asInstanceOf[JObj]))
      .right.flatMap(lcb => (v \ "updatedOn").asOpt[Long].fold[Either[String, LoginContext]](Left("updatedOn.notFound"))(updatedOn => Right(lcb.build(updatedOn)))) match {
      case Left(err) => JError(err)
      case Right(lc) => JSuccess(lc)
    }
  }

  /**
   * Creates a new implementation of the [[com.identityblitz.login.LoginContext]]] from string representation.
   *
   * @param str - string representation of [[com.identityblitz.login.LoginContext]]].
   * @return login context.
   */
  def fromString(str: String) :LoginContext = Json.fromJson[LoginContext](JVal.parseStr(str))

  /**
   * Login context builder
   */
  abstract class READY
  abstract class NOT_READY

  class LoginContextBuilder[CB, M](val callbackUri: String, val method: String, val completedMethods: Seq[String], 
                               val claims: JObj, val updatedOn:Long) {

    def withCallbackUri(cb: String) = new LoginContextBuilder[READY, M](cb, method, completedMethods, claims, System.currentTimeMillis())

    def withMethod(m: String) = new LoginContextBuilder[CB, READY](callbackUri, m, completedMethods, claims, System.currentTimeMillis())

    def withCompletedMethod(method: String) = new LoginContextBuilder[CB, M](callbackUri, method, completedMethods :+ method, claims, System.currentTimeMillis())

    def withClaim[T](claim: (String, T))(implicit writer: JWriter[T]) =
      new LoginContextBuilder[CB, M](callbackUri, method, completedMethods, claims +! claim, System.currentTimeMillis())

    def withClaims(_claims: JObj) = new LoginContextBuilder[CB, M](callbackUri, method, completedMethods, claims ++! _claims, System.currentTimeMillis())

  }

  implicit def enableBuild(builder: LoginContextBuilder[READY, READY]) = new {
    def build() = new LoginContextImpl(builder.callbackUri, builder.method, builder.completedMethods, builder.claims, builder.updatedOn)

    def build(updatedOn:Long) = new LoginContextImpl(builder.callbackUri, builder.method, builder.completedMethods, builder.claims, updatedOn)
  }

  def lcBuilder = new LoginContextBuilder[NOT_READY, NOT_READY](null, null, Seq(), JObj(), System.currentTimeMillis())

  def lcBuilder(lc: LoginContext) = new LoginContextBuilder[READY, READY](lc.callbackUri, lc.method, lc.completedMethods, lc.claims, lc.updatedOn)

}

case class LoginContextImpl(callbackUri: String, method: String, completedMethods: Seq[String], claims: JObj, updatedOn:Long) extends LoginContext
