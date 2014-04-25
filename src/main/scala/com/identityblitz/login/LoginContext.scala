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
   * Adds a new completed method to the already added ones.
   * @param method - method name.
   * @return - updated login context
   */
  def addMethod(method :String): LoginContext = lcBuilder(this) withMethod(method) build

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
      "callbackUri" -> JStr(o.callbackUri),
      "completedMethods" -> JArr(o.completedMethods.map(JStr(_)).toArray),
      "claims" -> o.claims
    )
  }

  implicit def jreader: JReader[LoginContext] = new JReader[LoginContext] {
    def read(v: JVal): JResult[LoginContext] = {
      Right[String, LoginContextBuilder[_]](lcBuilder).right
        .flatMap(lcb => (v \ "callbackUri").asOpt[String]
        .fold[Either[String, LoginContextBuilder[READY]]](Left("callbackUri.notFound"))(cb => Right(lcb withCallbackUri cb)))}
      .right.map(lcb => (v \ "completedMethods").asOpt[Array[String]]
      .fold[LoginContextBuilder[READY]](lcb)(m => m.foldLeft(lcb)(_ withMethod _)))
      .right.map(lcb => (v \ "claims").asOpt[JVal].fold[LoginContextBuilder[READY]](lcb)(clm => lcb withClaims clm.asInstanceOf[JObj]))
      .right.map(lcb => lcb build) match {
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

  class LoginContextBuilder[R](val callbackUri: String, val completedMethods: Seq[String], val claims: JObj) {

    def withCallbackUri(cb: String) = new LoginContextBuilder[READY](cb, completedMethods, claims)

    def withMethod(method: String) = new LoginContextBuilder[R](callbackUri, completedMethods :+ method, claims)

    def withClaim[T](claim: (String, T))(implicit writer: JWriter[T]) =
      new LoginContextBuilder[R](callbackUri, completedMethods, claims +! claim)

    def withClaims(_claims: JObj) = new LoginContextBuilder[R](callbackUri, completedMethods, claims ++! _claims)

  }

  implicit def enableBuild(builder: LoginContextBuilder[READY]) = new {
    def build() = new LoginContextImpl(builder.callbackUri, builder.completedMethods, builder.claims)
  }

  def lcBuilder = new LoginContextBuilder[NOT_READY](null, Seq(), JObj())

  def lcBuilder(lc: LoginContext) = new LoginContextBuilder[READY](lc.callbackUri, lc.completedMethods, lc.claims)

}

case class LoginContextImpl(callbackUri: String, completedMethods: Seq[String], claims: JObj) extends LoginContext
