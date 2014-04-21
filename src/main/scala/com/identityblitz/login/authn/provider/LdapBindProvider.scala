package com.identityblitz.login.authn.provider

import com.identityblitz.login.LoggingUtils._
import com.unboundid.ldap.sdk._
import com.unboundid.util.ssl.{TrustAllTrustManager, SSLUtil}
import com.identityblitz.login.authn.provider.LdapBindProvider._
import scala.collection
import com.identityblitz.json.{JStr, JArr, JVal, JObj}
import scala.util.Try
import com.identityblitz.login.authn.provider.AttrType.AttrType
import com.identityblitz.login.authn.cmd.{ChangePswdCmd, Command}
import com.unboundid.ldap.sdk.controls.{PasswordExpiredControl, PasswordExpiringControl}
import com.identityblitz.login.authn.method.PasswordBaseMethod.FormParams._
import com.identityblitz.login.error.BuiltInError
import scala.util.Failure
import scala.Some
import scala.util.Success

/**
 */
class LdapBindProvider(name:String, options: Map[String, String]) extends Provider(name, options) with WithBind {

  logger.trace("initializing the ldap authentication module [options={}]", options)

  private val confMap = paramsMeta.foldLeft[(List[String], collection.mutable.Map[String, Any])]((List(), collection.mutable.Map()))(
    (res, f) => f(options) match {
      case Left(err) => (err :: res._1) -> res._2
      case Right(t) => res._1 -> (res._2 + t)
    }
  ) match {
    case (Nil, m) => m.toMap
    case (errors, m) =>
      errors map logger.error
      throw new IllegalArgumentException(errors mkString ", ")
  }

  private val host = confMap("host").asInstanceOf[String]

  private val port = confMap("port").asInstanceOf[Int]

  private val userDnPtn = confMap("userDn").asInstanceOf[String]

  private val useSSL = confMap("useSSL").asInstanceOf[Boolean]

  private val autoReconnect = confMap("autoReconnect").asInstanceOf[Boolean]

  private val initialConnections = confMap("initialConnections").asInstanceOf[Int]

  private val maxConnections = confMap("maxConnections").asInstanceOf[Int]

  private val connectionOption = new LDAPConnectionOptions()

  connectionOption.setAutoReconnect(autoReconnect)

  val connection: LDAPConnection = useSSL match {
    case true =>
      val sslUtil = new SSLUtil(new TrustAllTrustManager())
      new LDAPConnection(sslUtil.createSSLSocketFactory(), connectionOption, host, port)
    case false =>
      new LDAPConnection(connectionOption, host, port)
  }

  val pool = new LDAPConnectionPool(connection, initialConnections, maxConnections)


  override def bind(data: Map[String, String]): Either[String, (JObj, Option[Command])] = {
    data.get(login).map(lgnVal => interpolate(userDnPtn, Map(login.toString -> lgnVal))) -> data.get(password) match {
      case (Some(userDn), Some(pswd)) =>
        Try(pool.getConnection) match {
          case Success(c) =>
            implicit val connection = c
            logger.trace("LDAP connection has been got from the pool")
            val authRes = Try({
              val bindRes = bindToLdap(userDn, pswd)
              logger.trace("Analyzing LDAP server's response controls [controls: {}, messageId: {}]",
                bindRes.getResponseControls, bindRes.getMessageID)
              var isPswdExpired = false
              bindRes.getResponseControls.foreach(control => {
                logger.trace("Got a control [name={}, oid={}]", control.getControlName, control.getOID)
                control match {
                  case expiringControl: PasswordExpiringControl =>
                    logger.trace("The subject's password will expire in the near future [after {} sec]",
                      expiringControl.getSecondsUntilExpiration)
                    /*todo: restore warn*/
                    /*                    lc.withWarn(NEAR_PSWD_EXPIRE_WARN_KEY, Messages(NEAR_PSWD_EXPIRE_WARN_KEY,
                                                              expiringControl.getSecondsUntilExpiration/60/60/24))*/
                  case expiredControl: PasswordExpiredControl =>
                    //todo: analise what is it "isCritical"
                    logger.trace("Bind is successful, but the subject's password has expired: to continue the " +
                      "authentication the subject must change its password.")
                    isPswdExpired = true
                  case _ =>
                    logger.debug("Unknown control, do nothing [name={}, oid={}]", control.getControlName,
                      control.getOID)
                }
              })

              logger.trace("Getting the subject's entry [userDn: {}]", userDn)
              val claims = Option(connection.getEntry(userDn)).fold[JObj]({
                logger.trace("Can't get the subject's entry: check the LDAP access rules. The subject must have " +
                  "an access to his entry.")
                throw new IllegalAccessException("Can't get the subject's entry: check the LDAP access rules. " +
                  "The subject must have an access to his entry.")
              })(entry => {
                /*todo: getting the real attributtes*/
                JObj("attribute1" -> JStr("value1"))
              })

              if (isPswdExpired) {
                Right((claims, Some(new ChangePswdCmd(userDn))))
              } else {
                Right((claims, None))
              }
            })

            //release the connection
            pool.releaseConnection(connection)
            logger.trace("LDAP connection has been released")

            authRes match {
              case Success(res) =>
                logger.trace("authentication by LDAP is completed successfully [userDn: {}, res={}]", userDn, res)
                res
              case Failure(e) =>
                logger.trace("authentication by LDAP is failed [userName: {}, error: {}]", userDn, e)
                e match {
                  case le: LDAPException =>
                    Left(handleLdapError(le.toLDAPResult))
                  case _ =>
                    logger.error("can't perform authentication by LDAP server. Internal error has occurred: {}", e)
                    Left(BuiltInError.INTERNAL)
                }
            }
          case Failure(e) =>
            logger.error("Bind to LDAP error: can't get a LDAP connection from the pool")
            throw e
        }
      case _ =>
        logger.warn("Can't perform authentication by LDAP: login or password is not specified in the data [data = {}]", data)
        Left(BuiltInError.NO_CREDENTIALS_FOUND)
    }    
  }


  /** other functions **/
  private def bindToLdap(userDn: String, pswd: String)(implicit connection: LDAPConnection): BindResult = {
    logger.trace("Try to bind to LDAP server with following userDn: {}", userDn)
    
    Try[BindResult](connection.bind(new SimpleBindRequest(userDn, pswd))).map[BindResult](bindRes => {
      if (!bindRes.getResultCode.isConnectionUsable) {
        logger.error("Can't bind to LDAP server: the connection isn't usable [resultCode: {}, messageId: {}, " +
          "diagnosticMessage: {}]", Array(bindRes.getResultCode, bindRes.getMessageID, bindRes.getDiagnosticMessage))
        throw new RuntimeException("Can't bind to LDAP server: the connection isn't usable")
      } else {
        bindRes
      }      
    }) match {
      case Success(bindRes) =>
        logger.debug("Bind to LDAP server is successful [userDn: {}, messageId: {}]", userDn, bindRes.getMessageID)
        bindRes
      case Failure(e) =>
        logger.error("Can't bind to LDAP server: internal error has occurred [{}]", e.getMessage)
        throw e
    }
  }

  private def handleLdapError(err: LDAPResult): String = {
    logger.debug("Handling ldap error: [{resultCode: {}, messageId: {}, diagnosticMessage: {}}]",
      Array(err.getResultCode.getName, err.getMessageID, err.getDiagnosticMessage))
    errorMapper.get(err.getResultCode).map(_.toString).getOrElse(err.getResultCode.getName.replaceAll(" ", "_"))

    /*todo: restore logic*/
/*    var isErrorResolvedByControl = false
    err.getResponseControls.map(control => {
      logger.trace("got a control [name: {}, oid: {}]", control.getControlName, control.getOID)
      control match {
        case expiredControl: PasswordExpiredControl =>
          //todo: check is it possible to change password in this case
          logger.trace("the subject's password has expired, adding the appropriate error")
          lc withError PASSWORD_EXPIRED
          isErrorResolvedByControl = true
        case _ => {
          logger.debug("unknown control, do nothing [name={}, oid={}]", control.getControlName, control.getOID)
        }
      }
    })

    if (!isErrorResolvedByControl) {
      logger.trace("map result code to error [result code ={}]", err.getResultCode)
      errorMapper.get(err.getResultCode).getOrElse(err.getResultCode.getName.replaceAll(" ", "_"))
    }*/
  }


  private def interpolate(text: String, vars: Map[String, String]) =
    (text /: vars) { (t, kv) => t.replace("${"+kv._1+"}", kv._2)  }

  override def toString: String = {
    val sb =new StringBuilder("LdapBindProvider(")
    sb.append("options -> ").append(options)
    sb.append(")").toString()
  }
}




private object LdapBindProvider {

  private def rOrL(prms: Map[String, String])(prm: String, default: => Either[String, Any], convert: String => Any) =
    prms.get(prm).fold[Either[String, (String, Any)]](default match {
      case Right(v) => Right(prm -> v)
      case Left(err) => Left(err)
    })(v => Right(prm -> convert(v)))
  
  
  private val paramsMeta = Set[(Map[String, String] => Either[String, (String, Any)])](
    rOrL(_)("host", Left("host is not specified in the configuration"), str => str),
    rOrL(_)("port", Left("port is not specified in the configuration"), str => str.toInt),
    rOrL(_)("userDn", Left("userDn is not specified in the configuration"), str => str),
    rOrL(_)("autoReconnect", Right(true), str => str.toBoolean),
    rOrL(_)("initialConnections", Right(1), str => str.toInt),
    rOrL(_)("maxConnections", Right(5), str => str.toInt)  
  )

  //todo: add mapper for password expired
  val errorMapper = Map(ResultCode.NO_SUCH_OBJECT -> BuiltInError.NO_SUBJECT_FOUND,
    ResultCode.INVALID_CREDENTIALS -> BuiltInError.INVALID_CREDENTIALS,
    ResultCode.UNWILLING_TO_PERFORM -> BuiltInError.ACCOUNT_IS_LOCKED)

}


trait AttrMeta {
  if (baseName == null || valType == null) throw new NullPointerException("baseName and valType can't ba null")

  def baseName: String

  def valType: AttrType

  override def toString: String = {
    val sb =new StringBuilder("AttrMeta(")
    sb.append("baseName -> ").append(baseName)
    sb.append(", ").append("valType -> ").append(valType)
    sb.append(")").toString()
  }
}

case class AttrMetaImpl(baseName: String, valType: AttrType) extends AttrMeta {}

object AttrMeta {

  def apply(v: JObj): AttrMeta = {
    Right[String, Seq[Any]](Seq()).right.map(seq => {
      (v \ "baseName").asOpt[String].fold[Either[String, Seq[Any]]](Left("baseName.notFound"))(baseName => {
        Right(seq :+ baseName)
      })
    }).joinRight.right.map(seq => {
      (v \ "valType").asOpt[String].fold[Either[String, Seq[Any]]](Left("valType.notFound"))(valTypeStr => {
        Try(AttrType.withName(valTypeStr.toLowerCase)).toOption
          .fold[Either[String, Seq[Any]]](Left("valType.unknown"))(valType => {
          Right(seq :+ valType)
        })
      })
    }).joinRight match {
      case Left(err) => {
        logger.error("can't parse attrMeta [error = {}, json = {}]", Seq(err, v.toJson))
        throw new IllegalArgumentException("can't parse attrMeta")
      }
      case Right(seq) => {
        val attrMeta = new AttrMetaImpl(seq(0).asInstanceOf[String], seq(1).asInstanceOf[AttrType])
        logger.error("the attrMeta has been parsed successfully [attrMeta = {}]", attrMeta)
        attrMeta
      }
    }
  }

  def apply(jsonStr: String): AttrMeta = apply(JVal.parseStr(jsonStr).asInstanceOf[JObj])

  def parseArray(jsonStr: String): Seq[AttrMeta] = {
    JVal.parseStr(jsonStr).asInstanceOf[JArr].map(jv => apply(jv.asInstanceOf[JObj]))
  }
}

/**
 * Enumeration of attribute types.
 */
object AttrType extends Enumeration {
  type AttrType = Value
  val string, strings, boolean, number, bytes = Value
}
