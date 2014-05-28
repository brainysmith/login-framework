package com.identityblitz.login.session

import com.identityblitz.json._
import com.identityblitz.login.transport.{CookieScala, OutboundTransport, InboundTransport}
import com.identityblitz.login.App.{logger, sessionConf}
import com.identityblitz.scs.SCSService

/**
  */
trait LoginSession {

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
    scs.init(false)
    scs
  }
  
  
  implicit def jwriter: JWriter[LoginSession] = new JWriter[LoginSession] {       
    def write(o: LoginSession): JVal = Json.obj(
      "completedMethods" -> JArr(o.completedMethods.toIterable.map(JStr(_)).toArray),
      "claims" -> o.claims,
      "createdOn" -> JNum(o.createdOn)
    )    
  }

  implicit def jreader: JReader[LoginSession] = new JReader[LoginSession] {
    def read(v: JVal): JResult[LoginSession] = {
      Right[String, LoginSessionBuilder[_,_,_]](lsBuilder).right.flatMap{lsb =>
        (v \ "completedMethods").asOpt[Array[String]].fold[Either[String, LoginSessionBuilder[READY, _, _]]](
          Left("completedMethods.notFound"))(m => Right(lsb.withMethods(m: _*)))}
    }.right.flatMap{lsb => (v \ "claims").asOpt[JVal].fold[Either[String, LoginSessionBuilder[READY, READY, _]]](
      Left("claims.notFound"))(clm => Right(lsb withClaims clm.asInstanceOf[JObj]))
    }.right.flatMap{lsb => (v \ "createdOn").asOpt[Long].fold[Either[String, LoginSession]](
      Left("createdOn.notFound"))(crtOn => Right(lsb.withCreatedOn(crtOn).build()))
    }
    match {
      case Left(err) => JError(err)
      case Right(ls) => JSuccess(ls)
    }
  }

  def getLs(implicit iTr: InboundTransport, oTr: OutboundTransport) = iTr.getCookie(sessionConf.cookieName)
    .map(sc => scsService.decode(sc.value).getData)
    .map(fromString)
    .filter(ls => {
    val isExpired = (ls.createdOn + sessionConf.ttl) > System.currentTimeMillis()
    if (logger.isTraceEnabled)
      logger.trace("A result of the login session's [{}] expiration check: {}", ls.asString, isExpired)
    isExpired
  })

  def updateLs(ls: LoginSession)(implicit iTr: InboundTransport, oTr: OutboundTransport) {
    if (logger.isTraceEnabled)
      logger.trace("Updating current login session to '{}'", ls.asString)
    val encLs = scsService.encode(ls.asString).asString()
    oTr.addCookie(CookieScala(sessionConf.cookieName, encLs, None, sessionConf.path, sessionConf.domain,
      sessionConf.secure, sessionConf.httpOnly))
  }


  private def fromString(str: String) :LoginSession = Json.fromJson[LoginSession](JVal.parseStr(str))
  
  abstract class READY
  abstract class NOT_READY

  class LoginSessionBuilder[M, C, D](val completedMethods: Set[String], val claims: JObj, val createdOn: Long) {
    def addMethod(m: String) = new LoginSessionBuilder[READY, C, D](completedMethods + m, claims, createdOn)
    def withMethods(methods: String*) = new LoginSessionBuilder[READY, C, D](completedMethods ++ methods, claims, createdOn)
    def withClaims(_claims: JObj) = new LoginSessionBuilder[M, READY, D](completedMethods, claims ++! _claims, createdOn)
    private[session] def withCreatedOn(c: Long) = new LoginSessionBuilder[M, C, READY](completedMethods, claims, c)
  }

  implicit def enableBuild(b: LoginSessionBuilder[READY, READY, READY]) = new {
    def build() = new LoginSessionImpl(b.completedMethods, b.claims, b.createdOn)
  }

  def lsBuilder = new LoginSessionBuilder[NOT_READY, NOT_READY, READY](Set(), JObj(), System.currentTimeMillis())
  def lsBuilder(s: LoginSession) = new LoginSessionBuilder[READY, READY, READY](s.completedMethods, s.claims, s.createdOn)
}


case class LoginSessionImpl(completedMethods: Set[String], claims: JObj, createdOn: Long) extends LoginSession

case class LoginSessionConf(cookieName: String, ttl: Long, path: String, domain: Option[String], secure: Boolean, httpOnly: Boolean)