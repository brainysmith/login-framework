package com.identityblitz.login.session

import com.identityblitz.json._
import com.identityblitz.login.transport._
import com.identityblitz.login.LoginFramework.{logger, sessionConf}
import com.identityblitz.scs.SCSService
import scala.util.Try
import com.identityblitz.login.transport.CookieScala
import com.identityblitz.login.transport.DiscardingCookieScala
import com.identityblitz.json.JSuccess

/**
  */
trait LoginSession {

  /**
   * Defines an identifier of login session.
   * @return string represented an identifier of login session.
   */
  val id: String

  /**
   * Defines login methods that have been successfully completed.
   * @return array of login methods that have been successfully completed.
   */
  val completedMethods: Set[String]

  /**
   * Retrieve claims about the current subject.
   * @return json with claims
   */
  val claims: JObj

  /**
   * Indicates the created time.
   * @return the difference, measured in milliseconds, between the created time and midnight, January 1, 1970 UTC.
   */
  val createdOn: Long


  def asString: String = Json.toJson(this).toJson

}

object LoginSession {
  implicit val implicits = scala.language.implicitConversions
  implicit val refCalls = scala.language.reflectiveCalls
  implicit val postOps =  scala.language.postfixOps

  protected lazy val scsService = {
    val scs = new SCSService()
    scs.init(false, sessionConf.sInactivityPeriod)
    scs
  }


  implicit def jwriter: JWriter[LoginSession] = new JWriter[LoginSession] {
    def write(o: LoginSession): JVal = Json.obj(
      "id" -> JStr(o.id),
      "completedMethods" -> JArr(o.completedMethods.toIterable.map(JStr(_)).toArray),
      "claims" -> o.claims,
      "createdOn" -> JNum(o.createdOn)
    )
  }

  implicit def jreader: JReader[LoginSession] = new JReader[LoginSession] {
    def read(v: JVal): JResult[LoginSession] = {
      Right[String, LoginSessionBuilder[_,_,_,_]](lsBuilder).right.flatMap{lsb =>
        (v \ "completedMethods").asOpt[Array[String]].fold[Either[String, LoginSessionBuilder[_, READY, _, _]]](
          Left("completedMethods.notFound"))(m => Right(lsb.withMethods(m: _*)))}

    }.right.flatMap{lsb => (v \ "id").asOpt[String].fold[Either[String, LoginSessionBuilder[READY, READY, _, _]]](
      Left("identifier.notFound"))(id => Right(lsb.withIdentifier(id)))
    }.right.flatMap{lsb => (v \ "claims").asOpt[JVal].fold[Either[String, LoginSessionBuilder[READY, READY, READY, _]]](
      Left("claims.notFound"))(clm => Right(lsb withClaims clm.asInstanceOf[JObj]))
    }.right.flatMap{lsb => (v \ "createdOn").asOpt[Long].fold[Either[String, LoginSession]](
      Left("createdOn.notFound"))(crtOn => Right(lsb.withCreatedOn(crtOn).build()))
    }
    match {
      case Left(err) => JError(err)
      case Right(ls) => JSuccess(ls)
    }
  }

  def getLs(implicit iTr: InboundTransport, oTr: OutboundTransport) = iTr.getCookie(sessionConf.cookieName).orElse[Cookie]({
    if (logger.isDebugEnabled)
      logger.debug("A session cookie with name '{}' not found,", sessionConf.cookieName)
    None
  }).flatMap(sc => Try(scsService.decode(sc.value).getData).map(fromString).toOption)
    .filter(
      ls => {
        val isExpired = (ls.createdOn + sessionConf.ttl) < System.currentTimeMillis()
        if (logger.isTraceEnabled)
          logger.trace("Is the login session's ttl [{}] has been expired: {}", ls.asString, isExpired)
        !isExpired
      }
    )

  def updateLs(ls: LoginSession)(implicit iTr: InboundTransport, oTr: OutboundTransport) {
    if (logger.isTraceEnabled)
      logger.trace("Updating current login session to '{}'", ls.asString)
    val encLs = scsService.encode(ls.asString).asString()
    oTr.addCookie(CookieScala(sessionConf.cookieName, encLs, None, sessionConf.path, sessionConf.domain,
      sessionConf.secure, sessionConf.httpOnly))
  }

  def removeLs(implicit iTr: InboundTransport, oTr: OutboundTransport) {
    logger.trace("Removing the current login session")
    oTr.discardCookie(DiscardingCookieScala(sessionConf.cookieName, sessionConf.path, sessionConf.domain,sessionConf.secure))
  }


  private def fromString(str: String) :LoginSession = Json.fromJson[LoginSession](JVal.parse(str))

  abstract class READY
  abstract class NOT_READY

  class LoginSessionBuilder[I, M, C, D](val identifier: String, val completedMethods: Set[String], val claims: JObj, val createdOn: Long) {
    def withIdentifier(id: String) = new LoginSessionBuilder[READY, M, C, D](id, completedMethods, claims, createdOn)
    def addMethod(m: String) = new LoginSessionBuilder[I, READY, C, D](identifier, completedMethods + m, claims, createdOn)
    def withMethods(methods: String*) = new LoginSessionBuilder[I, READY, C, D](identifier, completedMethods ++ methods, claims, createdOn)
    def withClaims(_claims: JObj) = new LoginSessionBuilder[I, M, READY, D](identifier, completedMethods, claims ++! _claims, createdOn)
    private[session] def withCreatedOn(c: Long) = new LoginSessionBuilder[I, M, C, READY](identifier, completedMethods, claims, c)
  }

  implicit def enableBuild(b: LoginSessionBuilder[READY, READY, READY, READY]) = new {
    def build() = new LoginSessionImpl(b.identifier, b.completedMethods, b.claims, b.createdOn)
  }

  def lsBuilder = new LoginSessionBuilder[NOT_READY, NOT_READY, NOT_READY, READY](null, Set(), JObj(), System.currentTimeMillis())
  def lsBuilder(s: LoginSession) = new LoginSessionBuilder[READY, READY, READY, READY](s.id, s.completedMethods, s.claims, s.createdOn)
}


case class LoginSessionImpl(id: String, completedMethods: Set[String], claims: JObj, createdOn: Long) extends LoginSession

case class LoginSessionConf(cookieName: String, ttl: Long, path: String, domain: Option[String], secure: Boolean,
                            httpOnly: Boolean, sInactivityPeriod: Long)