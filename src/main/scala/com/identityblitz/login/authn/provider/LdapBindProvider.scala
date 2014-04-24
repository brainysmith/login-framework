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
import com.identityblitz.login.error.LoginError
import com.identityblitz.login.error.BuiltInErrors._
import scala.util.Failure
import scala.Some
import scala.util.Success
import com.identityblitz.login.error.CustomLoginError
import com.unboundid.ldap.sdk.extensions.{PasswordModifyExtendedResult, PasswordModifyExtendedRequest}

/**
 */
class LdapBindProvider(name:String, options: Map[String, String]) extends Provider(name, options) with WithBind with WithChangePswd {

  logger.trace("initializing the '{}' LDAP bind provider [options={}]", name, options)

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

  override def bind(data: Map[String, String]): Either[LoginError, (JObj, Option[Command])] = {
    val userDnOpt = data.get(FormParams.login).map(lgnVal => interpolate(userDnPtn, Map(FormParams.login.toString -> lgnVal)))
    logger.trace("Try to bind to '{}' LDAP [userDN = {}]", name, userDnOpt)
    userDnOpt -> data.get(FormParams.password) match {
      case (Some(userDn), Some(pswd)) =>
        val resWrapped = ldap { implicit connection =>
          Try {
            _bind(userDn, pswd) match {
              case Right(bindRes) =>
                logger.trace("Bind to '{}' LDAP is successfully. Analyzing LDAP server's response controls " +
                  "[userDn = {}, bindRes = {}]", Array(name, userDn, bindRes))
                bindRes.getResponseControls.find(
                  control =>classOf[PasswordExpiredControl].isAssignableFrom(control.getClass)
                ) match {
                  //todo: add logic for near expiring password
                  case Some(control) =>
                    logger.trace("The user's password has expired [userDn = {}, ldap = {}]", userDn, name)
                    Right[LoginError, (JObj, Option[Command])](JObj() -> Some(new ChangePswdCmd(name, userDn)))
                  case None =>
                    logger.trace("Try to get user's attributes [userDn = {}, ldap = {}]", userDn, name)
                    val claims = Option(connection.getEntry(userDn)).fold[JObj]({
                      val err = s"Can't get the subject's entry: check the '$name' LDAP access rules. The subject " +
                        s"must have an access to his entry [userDn = $userDn]."
                      logger.error(err)
                      throw new IllegalAccessException(err)
                    })(entry => {
                      /*todo: getting the real attributes (correlate with change password operation)*/
                      JObj("attribute1" -> JStr("value1"))
                    })
                    logger.trace("Got following claims [userDn = {}, ldap = {}]: {}",
                      Array(userDn, name, claims.toJson))
                    Right[LoginError, (JObj, Option[Command])](claims -> None)
                }
              case Left(err) => Left(err)
            }
          }
        }

        logger.debug("The result of the binding to '{}' LDAP [userDn = {}]: {}", Array(name, userDn, resWrapped))

        /** analyze the result **/
        resWrapped match {
          case Success(res) => res
          case Failure(e) =>
            logger.error("Internal exception has occurred [userDn = {}, ldap = {}]: {}",
              Array(userDn, name, e.getMessage))
            throw e
        }
      case _ =>
        logger.warn("Can't perform bind to '{}' LDAP: login or password is not specified in the data [data = {}]",
          name, data)
        Left(NO_CREDENTIALS_FOUND)
    }    
  }

  override def changePswd(userDn: String, curPswd: String, newPswd: String) = {
    ldap { implicit connection =>
      _bind(userDn, curPswd) match {
        case Right(bindRes) =>
          Try {
            val pswdMdfReq = new PasswordModifyExtendedRequest(curPswd, newPswd)
            connection.processExtendedOperation(pswdMdfReq).asInstanceOf[PasswordModifyExtendedResult]
          } match {
            case Success(res) =>
              if (res.getResultCode == ResultCode.SUCCESS) {
                logger.debug("Change password is successful")
                /*todo: thinking about adding a command for getting attributes or make other decision*/
                Right(None, None)
              } else {
                logger.debug("Change password failed [userDn = {}]. Ldap result code: {}", userDn, res)
                Left(mapLdapError(res.getResultCode))
              }
            case Failure(ldapException:LDAPException) =>
              logger.debug("Change password failed [userDn = {}]: ldap exception has occurred: {}", userDn, ldapException)
              Left(mapLdapError(ldapException.getResultCode))
            case Failure(e) =>
              logger.error("Change password failed [userDn = {}]: internal exception has occurred: {}", userDn, e)
              throw e
          }
        case Left(lgnErr) => Left(lgnErr)
      }
    }


  }

  /** other functions **/
  protected def ldap[T](f: (LDAPConnection) => T): T = {
    Try(pool.getConnection) match {
      case Success(connection) =>
        logger.trace("'{}' LDAP connection has been got from the pool", name)
        try {
          f(connection)
        } finally {
          pool.releaseConnection(connection)
          logger.trace("'{}' LDAP connection has been released", name)
        }
      case Failure(e) =>
        logger.error("Can't get '{}' LDAP connection from the pool: {}", name, e.getMessage)
        throw e
    }
  }

  protected def _bind(userDn: String, pswd: String)(implicit connection: LDAPConnection): Either[LoginError, BindResult] = {
    Try[BindResult](connection.bind(new SimpleBindRequest(userDn, pswd))).map[BindResult](bindRes => {
      if (!bindRes.getResultCode.isConnectionUsable) {
        logger.error("Can't bind to '{}' LDAP server: the connection isn't usable [userDn = {}, resultCode = {}, " +
          "messageId = {}, diagnosticMessage = {}]",
          Array(name, userDn, bindRes.getResultCode, bindRes.getMessageID, bindRes.getDiagnosticMessage))
        throw new RuntimeException("Can't bind to LDAP server: the connection isn't usable")
      } else {
        bindRes
      }
    }) match {
      case Success(bindRes) =>
        logger.trace("Bind to '{}' LDAP is performed successfully [userDn: {}]", name, userDn)
        Right(bindRes)
      case Failure(ldapException:LDAPException) =>
        logger.debug("Bind to '{}' LDAP is performed unsuccessfully [userDn = {}]: {}",
          Array(name, userDn, ldapException))
        Left(mapLdapError(ldapException.getResultCode))
      case Failure(e) =>
        logger.error("Can't perform bind to '{}' LDAP. Internal error has occurred: {}", name, e.getMessage)
        throw e
    }
  }

  protected def mapLdapError(code: ResultCode) = errorMapper.get(code).getOrElse({
    CustomLoginError(code.getName.replaceAll(" ", "_"))
  })

  protected def interpolate(text: String, vars: Map[String, String]) =
    (text /: vars) { (t, kv) => t.replace("${"+kv._1+"}", kv._2)  }

  override def toString: String = {
    val sb =new StringBuilder("LdapBindProvider(")
    sb.append("name -> ").append(name).append(",")
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

  val errorMapper = Map(ResultCode.INVALID_CREDENTIALS -> INVALID_CREDENTIALS,
    ResultCode.NO_SUCH_OBJECT -> NO_SUBJECT_FOUND,
    ResultCode.UNWILLING_TO_PERFORM -> ACCOUNT_IS_LOCKED)

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
