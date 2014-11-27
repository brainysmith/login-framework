package com.identityblitz.login

import com.identityblitz.json._
import com.identityblitz.login.util.Base64Util

trait LoginContext {
  import com.identityblitz.login.LoginContext._

  /**
   * Defines a callback uri to redirect when login flow is completed.
   * @return string represented callback uri.
   */
  val callbackUri: String

  /**
   * Defines a relaying party that initiated login flow.
   * @return string represented relaying party identifier.
   */
  val relayingParty: Option[String]

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
   * Session key
   * @return session key in octet representation
   */
  val sessionKey: Array[Byte]

  /**
   * Defines a the started method.
   * @return started method.
   */
  val currentMethod: Option[String]

  /**
   * Defines a the last sent command.
   * @return string sent command.
   */
  val currentCommand: Option[String]

  /**
   * Save relaying party.
   * @param rp - relaying party identifier.
   * @return - updated login context
   */
  def setRelayingParty(rp :String): LoginContext = lcBuilder(this) withRelayingParty (rp) build

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

  /**
   * Save new current method.
   * @param method - method name.
   * @return - updated login context
   */
  def setCurrentMethod(method :String): LoginContext = lcBuilder(this) withCurrentMethod (method) build

  /**
   * Set new current command.
   * @param command - command name.
   * @return - updated login context
   */
  def setCurrentCommand(command :String): LoginContext = lcBuilder(this) withCurrentCommand  (command) build


  def asString: String = Json.toJson(this).toJson

}

object LoginContext {
  implicit val implicits = scala.language.implicitConversions
  implicit val refCalls = scala.language.reflectiveCalls
  implicit val postOps =  scala.language.postfixOps

  implicit def jwriter: JWriter[LoginContext] = new JWriter[LoginContext] {
    def write(o: LoginContext): JVal =
      Some(Json.obj(
        "updatedOn" -> JNum(o.updatedOn),
        "callbackUri" -> JStr(o.callbackUri),
        "sessionKey" -> JStr(Base64Util.encode(o.sessionKey)),
        "completedMethods" -> JArr(o.completedMethods.map(JStr(_)).toArray),
        "claims" -> o.claims
      )).map{j => o.relayingParty.fold[JObj](j)(r => j + ("rp", JStr(r)))}
        .map{j => o.currentMethod.fold[JObj](j)(m => j + ("curMethod", JStr(m)))}
        .map{j => o.currentCommand.fold[JObj](j)(c => j + ("curCommand", JStr(c)))}.get
  }

  implicit def jreader: JReader[LoginContext] = new JReader[LoginContext] {
    def read(v: JVal): JResult[LoginContext] = {
      Right[String, LoginContextBuilder[_, _]](lcBuilder).right
        .flatMap(lcb => (v \ "callbackUri").asOpt[String]
        .fold[Either[String, LoginContextBuilder[READY, _]]](Left("callbackUri.notFound"))(cb => Right(lcb withCallbackUri cb)))}
      .right.flatMap{lcb => (v \ "sessionKey").asOpt[String]
      .fold[Either[String, LoginContextBuilder[READY, READY]]](Left("sessionKey.notFound"))(sk => Right(lcb withSessionKey Base64Util.decode(sk)))}
      .right.map{lcb => (v \ "rp").asOpt[String]
      .fold[LoginContextBuilder[READY, READY]](lcb)(r => lcb withRelayingParty r)}
      .right.map{lcb => (v \ "curMethod").asOpt[String]
      .fold[LoginContextBuilder[READY, READY]](lcb)(m => lcb withCurrentMethod  m)}
      .right.map{lcb => (v \ "curCommand").asOpt[String]
      .fold[LoginContextBuilder[READY, READY]](lcb)(c => lcb withCurrentCommand c)}
      .right.map{lcb => (v \ "completedMethods").asOpt[Array[String]].fold[LoginContextBuilder[READY, READY]](lcb)(m => m.foldLeft(lcb)(_ withCompletedMethod _))}
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
  def fromString(str: String) :LoginContext = Json.fromJson[LoginContext](JVal.parse(str))

  /**
   * Login context builder
   */
  abstract class READY
  abstract class NOT_READY

  class LoginContextBuilder[C, S](val callbackUri: String,
                                  val relayingParty: Option[String],
                                  val completedMethods: Seq[String],
                                  val claims: JObj,
                                  val updatedOn: Long,
                                  val key: Array[Byte],
                                    val currentMethod: Option[String],
                                  val currentCommand: Option[String] ) {

    def withCallbackUri(cb: String) = new LoginContextBuilder[READY, S](cb, relayingParty, completedMethods, claims, System.currentTimeMillis(), key, currentMethod, currentCommand)

    def withRelayingParty(rp: String) = new LoginContextBuilder[C, S](callbackUri, Some(rp), completedMethods, claims, System.currentTimeMillis(), key, currentMethod, currentCommand)

    def withSessionKey(sessionKey: Array[Byte]) = new LoginContextBuilder[C, READY](callbackUri, relayingParty, completedMethods, claims, System.currentTimeMillis(), sessionKey, currentMethod, currentCommand)

    def withCurrentMethod(method: String) = new LoginContextBuilder[C, S](callbackUri, relayingParty, completedMethods, claims, System.currentTimeMillis(), key, Some(method), currentCommand)

    def withCurrentCommand(command: String) = new LoginContextBuilder[C, S](callbackUri, relayingParty, completedMethods, claims, System.currentTimeMillis(), key, currentMethod, Some(command))

    def withCompletedMethod(method: String) = new LoginContextBuilder[C, S](callbackUri, relayingParty, completedMethods :+ method, claims, System.currentTimeMillis(), key, currentMethod, currentCommand)

    def withClaim[T](claim: (String, T))(implicit writer: JWriter[T]) =
      new LoginContextBuilder[C, S](callbackUri, relayingParty, completedMethods, claims +! claim, System.currentTimeMillis(), key, currentMethod, currentCommand)

    def withClaims(_claims: JObj) = new LoginContextBuilder[C, S](callbackUri, relayingParty, completedMethods, claims ++! _claims, System.currentTimeMillis(), key, currentMethod, currentCommand)

  }

  implicit def enableBuild(builder: LoginContextBuilder[READY, READY]) = new {
    def build() = new LoginContextImpl(builder.callbackUri, builder.relayingParty, builder.completedMethods, builder.claims, builder.updatedOn, builder.key, builder.currentMethod, builder.currentCommand)

    def build(updatedOn:Long) = new LoginContextImpl(builder.callbackUri, builder.relayingParty, builder.completedMethods, builder.claims, updatedOn, builder.key, builder.currentMethod, builder.currentCommand)
  }

  def lcBuilder = new LoginContextBuilder[NOT_READY, NOT_READY](null, None, Seq(), JObj(), System.currentTimeMillis(), null, None, None)

  def lcBuilder(lc: LoginContext) = new LoginContextBuilder[READY, READY](lc.callbackUri, lc.relayingParty, lc.completedMethods, lc.claims, lc.updatedOn, lc.sessionKey, lc.currentMethod, lc.currentCommand)

}

case class LoginContextImpl(callbackUri: String,
                            relayingParty: Option[String],
                            completedMethods: Seq[String],
                            claims: JObj,
                            updatedOn: Long,
                            sessionKey: Array[Byte],
                            currentMethod: Option[String],
                            currentCommand: Option[String]) extends LoginContext