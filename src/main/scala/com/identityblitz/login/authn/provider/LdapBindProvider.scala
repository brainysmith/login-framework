package com.identityblitz.login.authn.provider

import com.identityblitz.login.LoggingUtils._
import com.unboundid.ldap.sdk._
import com.unboundid.util.ssl.{TrustAllTrustManager, SSLUtil}
import com.identityblitz.login.authn.provider.LdapBindProvider._
import scala.collection
import com.identityblitz.json._
import scala.util.Try
import com.identityblitz.login.authn.provider.AttrType.AttrType
import com.identityblitz.login.authn.cmd.{ChangePswdCmd, Command}
import com.unboundid.ldap.sdk.controls.PasswordExpiredControl
import com.identityblitz.login.authn.method.PasswordBaseMethod.FormParams
import com.identityblitz.login.error.{LoginError, BuiltInError}
import scala.util.Failure
import scala.Some
import scala.util.Success
import com.identityblitz.login.error.CustomLoginError
import com.unboundid.ldap.sdk.extensions.{PasswordModifyExtendedResult, PasswordModifyExtendedRequest}

/**
 */
class LdapBindProvider(name:String, options: Map[String, String]) extends Provider(name, options) with WithBind with WithChangePswd {

  logger.trace("initializing the ldap bind provider [options={}]", options)

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

  private val userDnPtn = confMap("userDn").asInstanceOf[String]

  private val pool = {
    val host = confMap("host").asInstanceOf[String]
    val port = confMap("port").asInstanceOf[Int]
    val connectionOption = new LDAPConnectionOptions()
    connectionOption.setAutoReconnect(confMap("autoReconnect").asInstanceOf[Boolean])

    val connection: LDAPConnection = confMap("useSSL").asInstanceOf[Boolean] match {
      case true =>
        val sslUtil = new SSLUtil(new TrustAllTrustManager())
        new LDAPConnection(sslUtil.createSSLSocketFactory(), connectionOption, host, port)
      case false =>
        new LDAPConnection(connectionOption, host, port)
    }

    val initialConnections = confMap("initialConnections").asInstanceOf[Int]
    val maxConnections = confMap("maxConnections").asInstanceOf[Int]
    new LDAPConnectionPool(connection, initialConnections, maxConnections)
  }

  /** bossiness functions **/

  override def bind(data: Map[String, String]): Either[LoginError, (Option[JObj], Option[Command])] = {
    val userDnOpt = data.get(FormParams.login).map(lgnVal => interpolate(userDnPtn, Map(FormParams.login.toString -> lgnVal)))
    logger.trace("Try to bind to '{}' LDAP [userDN = {}]", name, userDnOpt)
    userDnOpt -> data.get(FormParams.password) match {
      case (Some(userDn), Some(pswd)) =>
        Try(pool.getConnection) match {
          case Success(c) =>
            logger.trace("LDAP connection has been got from the pool [userDn = {}]", userDn)
            implicit val connection = c
            val resWrapped = Try {
              bindToLdap(userDn, pswd) match {
                case Right(bindRes) =>
                  logger.trace("Bind is successfully. Analyzing LDAP server's response controls " +
                    "[userDn = {}, bindRes = {}]", Array(userDn, bindRes.getResponseControls, bindRes.getMessageID))
                  bindRes.getResponseControls.find(
                    control =>classOf[PasswordExpiredControl].isAssignableFrom(control.getClass)
                  ) match {
                      //todo: add logic for expiring password
                    case Some(control) =>
                      logger.trace("The user's password has expired [userDn = {}]", userDn)
                      Right[LoginError, (Option[JObj], Option[Command])](None -> Some(new ChangePswdCmd(name, userDn)))
                    case None =>
                      logger.trace("Try to get user's attributes [userDn = {}]", userDn)
                      val claims = Option(connection.getEntry(userDn)).fold[JObj]({
                        logger.error("Can't get the subject's entry: check the LDAP access rules. The subject must have " +
                          "an access to his entry [userDn = {}].", userDn)
                        throw new IllegalAccessException("Can't get the subject's entry: check the LDAP access rules. " +
                          "The subject must have an access to his entry.")
                      })(entry => {
                        /*todo: getting the real attributtes*/
                        JObj("attribute1" -> JStr("value1"))
                      })
                      logger.trace("Got following claims [userDn = {}]: {}", userDn, claims.toJson)
                      Right[LoginError, (Option[JObj], Option[Command])](Some(claims) -> None)
                  }
                case Left(err) =>
                  logger.debug("Bind to LDAP is performed unsuccessfully [userDn = {}]: {}", userDn, err)
                  Left[LoginError, (Option[JObj], Option[Command])](errorMapper.get(err.getResultCode).getOrElse({
                    CustomLoginError(err.getResultCode.getName.replaceAll(" ", "_"))
                  }))
              }
            }

            /** release the connection **/
            pool.releaseConnection(connection)
            logger.trace("LDAP connection has been released [userDn = {}]", userDn)

            logger.debug("The result of the binding to LDAP [userDn = {}]: {}", userDn, resWrapped)
            /** analyze the result **/
            resWrapped match {
              case Success(res) => res
              case Failure(e) =>
                logger.error("Internal exception has occurred [userDn = {}]: {}", userDn, e.getMessage)
                throw e
            }
          case Failure(e) =>
            logger.error("Can't get a LDAP connection from the pool [userDn = {}]", userDn)
            throw e
        }
      case _ =>
        logger.warn("Can't perform authentication by LDAP: login or password is not specified in the data [data = {}]", data)
        Left(BuiltInError.NO_CREDENTIALS_FOUND)
    }    
  }

  override def changePswd(userDn: String, curPswd: String, newPswd: String): Either[LoginError, Option[Command]] = {
    Try(pool.getConnection) match {
      case Success(c) =>
        logger.trace("LDAP connection has been got from the pool [userDn = {}]", userDn)
        implicit val connection = c
        val pswdMdfRes = Try({
          /** first of all try to bind **/
          bindToLdap(userDn, curPswd) match {
            case Right(bindRes) =>
              logger.trace("Bind to LDAP is performed successfully. Try to change password [userDn: {}]", userDn)
              val pswdMdfReq = new PasswordModifyExtendedRequest(curPswd, newPswd)
              connection.processExtendedOperation(pswdMdfReq).asInstanceOf[PasswordModifyExtendedResult]
            case Left(err) =>
              //todo: think how to unify with bind
              logger.debug("Bind to LDAP is performed unsuccessfully [userDn = {}]: {}", userDn, err)
              Left[LoginError, (Option[JObj], Option[Command])](errorMapper.get(err.getResultCode).getOrElse({
                CustomLoginError(err.getResultCode.getName.replaceAll(" ", "_"))
              }))
          }
        })



        pool.releaseConnection(connection)
        logger.trace("LDAP connection has been released [userDn = {}]", userDn)

        ???
      case Failure(e) =>
        logger.error("Can't get a ldap connection from the pool: {}", e.getMessage)
        throw e
    }

  }

  /** other functions **/
  private def bindToLdap(userDn: String, pswd: String)(implicit connection: LDAPConnection): Either[LDAPException, BindResult] = {
    Try[BindResult](connection.bind(new SimpleBindRequest(userDn, pswd))).map[BindResult](bindRes => {
      if (!bindRes.getResultCode.isConnectionUsable) {
        logger.error("Can't bind to LDAP server: the connection isn't usable [userDn = {}, resultCode = {}, " +
          "messageId = {}, diagnosticMessage = {}]",
          Array(userDn, bindRes.getResultCode, bindRes.getMessageID, bindRes.getDiagnosticMessage))
        throw new RuntimeException("Can't bind to LDAP server: the connection isn't usable")
      } else {
        bindRes
      }      
    }) match {
      case Success(bindRes) => Right(bindRes)
      case Failure(ldapException:LDAPException) => Left(ldapException)
      case Failure(e) =>
        logger.error("Can't perform bind to LDAP. Internal error has occurred: {}", e.getMessage)
        throw e
    }
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
    rOrL(_)("useSSL", Right(true), str => str.toBoolean),
    rOrL(_)("initialConnections", Right(1), str => str.toInt),
    rOrL(_)("maxConnections", Right(5), str => str.toInt)  
  )

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
