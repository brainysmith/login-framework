package com.identityblitz.login

import play.api.mvc.{AnyContent, Request}

import com.unboundid.util.ssl.{TrustAllTrustManager, SSLUtil}
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import com.unboundid.ldap.sdk.controls.PasswordExpiredControl
import services.login.BuildInError._
import com.unboundid.ldap.sdk.extensions.PasswordModifyExtendedRequest
import services.login.BasicLoginModule.Obligation
import com.identityblitz.login.api.BuildInError

/**
 * Implementation of the basic login module by LDAP server.
  */
class LdapLoginModule extends BasicLoginModule {
  import LdapLoginModule._

  private var options: Map[String, String] = _
  private var pool: LDAPConnectionPool = null

  //configurable options //todo: thinking about it
  private var host: String = _
  private var port: Int = _
  private var useSSL: Boolean = _
  private var autoReconnect: Boolean = _
  private var initialConnections: Int = _
  private var maxConnections: Int = _
  private var userDnPattern: String = _


  //todo: change implementation
  override def init(options: Map[String, String]): LoginModule = {
    appLogTrace("initializing the ldap login module [options={}]", options)

    host = options.getOrElse("host", null)
    port = 1636
    useSSL = true
    autoReconnect = true

    var connection: LDAPConnection = null
    val connectionOption = new LDAPConnectionOptions()
    connectionOption.setAutoReconnect(autoReconnect)
    if (useSSL) {
      val sslUtil = new SSLUtil(new TrustAllTrustManager())
      connection = new LDAPConnection(sslUtil.createSSLSocketFactory(), connectionOption, host, port)
    } else {
      connection = new LDAPConnection(connectionOption, host, port)
    }

    initialConnections = 1
    maxConnections = 3
    pool = new LDAPConnectionPool(connection, initialConnections, maxConnections)
    userDnPattern = options.getOrElse("userDn", null)

    this.options = options
    this
  }

  override def start(implicit lc: LoginContext, request: Request[AnyContent]): Boolean = {
    lc.getCurrentMethod.fold(false)(_ == BuildInMethods.BASIC.id)
  }

  override def `do`(implicit lc: LoginContext, request: Request[AnyContent]): Result = {
    appLogTrace("attempting to authenticate subject by LDAP server [lc: {}]", lc)

    val userDnOpt: Option[String] = lc.getCredentials.find(_("login").asOpt[String].isDefined).fold[Option[String]]({
      appLogTrace("cannot find the login within login context's credentials, try to get the USER_DN from claims.")
      lc.getClaims.apply(USER_DN_CLAIM_NAME).asOpt[String].fold[Option[String]]({
        appLogError("cannot find the USER_DN within login context's claims.")
        None
      })(userDn => Some(userDn))
    })(crl => {
      Some(interpolate(userDnPattern, Map("USERNAME" -> crl("login").as[String])))
    })

    val pswdOpt: Option[String] =  lc.getCredentials.find(_("pswd").asOpt[String].isDefined).fold[Option[String]]({
      appLogError("cannot find the password into login context's credentials.")
      None
    })(crl => Some(crl("pswd").as[String]))

    (userDnOpt, pswdOpt) match {
      case (Some(userDn), Some(pswd)) => {
        Try(pool.getConnection) match {
          case Success(c) => {
            implicit val connection = c
            appLogTrace("LDAP connection has been got from the pool")
            val authRes = Try({
              val bindRes = bind(userDn, pswd)
              //put the matched user dn into the login context for future using
              lc withClaim (USER_DN_CLAIM_NAME -> userDn)

              appLogTrace("analyzing LDAP server's response controls [controls: {}, messageId: {}]",
                bindRes.getResponseControls, bindRes.getMessageID)
              var isPswdExpired = false
              bindRes.getResponseControls.foreach(control => {
                appLogTrace("got a control [name={}, oid={}]", control.getControlName, control.getOID)
                control match {
                  case expiringControl: PasswordExpiringControl => {
                    appLogTrace("the subject's password will expire in the near future [after {} sec]",
                      expiringControl.getSecondsUntilExpiration)
                    lc.withWarn(NEAR_PSWD_EXPIRE_WARN_KEY, Messages(NEAR_PSWD_EXPIRE_WARN_KEY,
                      expiringControl.getSecondsUntilExpiration/60/60/24))
                  }
                  case expiredControl: PasswordExpiredControl => {
                    //todo: analise what is it "isCritical"
                    appLogTrace("bind is successful, but the subject's password has expired: to continue the " +
                      "authentication the subject must change its password. Adding the {} obligation to the login " +
                      "context.", Obligation.CHANGE_PASSWORD)
                    lc.withObligation(Obligation.CHANGE_PASSWORD)
                    isPswdExpired = true
                  }
                  case _ => {
                    appLogDebug("unknown control, do nothing [name={}, oid={}]", control.getControlName,
                      control.getOID)
                  }
                }
              })

              if (!isPswdExpired) {
                appLogTrace("getting the subject's entry [userDn: {}]", userDn)
                //todo: add requested attributes from the configuration
                Option(connection.getEntry(userDn)).fold[Result]({
                  appLogError("can't get the subject's entry: check the LDAP access rules. The subject must have " +
                    "an access to his entry.")
                  throw new IllegalAccessException("can't get the subject's entry: check the LDAP access rules. " +
                    "The subject must have an access to his entry.")
                })(entry => {
                  //todo: add user's attributes to claims of the lc
/*                  for (attr: Attribute <- entry.getAttributes) {
                    attr.get
                  }*/
                  Result.SUCCESS
                })
              } else {
                Result.PARTIALLY_COMPLETED
              }
            })

            //release the connection
            pool.releaseConnection(connection)
            appLogTrace("LDAP connection has been released")

            authRes match {
              case Success(res) => {
                appLogTrace("authentication by LDAP is completed successfully [userDn: {}, res={}]", userDn, res)
                res
              }
              case Failure(e) => {
                appLogDebug("authentication by LDAP is failed [userName: {}, error: {}]", userDn, e)
                e match {
                  case le: LDAPException => {
                    handleLdapError(le.toLDAPResult)
                  }
                  case _ => {
                    appLogError("can't perform authentication by LDAP server. Internal error has occurred: {}", e)
                    lc withError BuildInError.INTERNAL
                  }
                }
                Result.FAIL
              }
            }
          }
          case Failure(e) => {
            appLogError("can't perform authentication by LDAP: can't get a LDAP connection from the pool")
            throw e
          }
        }
      }
      case _ => {
        appLogWarn("can't perform authentication by LDAP: required credentials not found [lc: {}]", lc)
        lc withError NO_CREDENTIALS_FOUND
        Result.FAIL
      }
    }
  }

  override def changePassword(curPswd: String, newPswd: String)
                             (implicit lc: LoginContext, request: Request[AnyContent]): Boolean = {
    appLogTrace("changing the subject's password [login context: {}]", lc)

    lc.getClaims(USER_DN_CLAIM_NAME).asOpt[String].fold[Boolean]({
      //in empty
      appLogError("change password is failed: couldn't find the {} claim in the login context. Ensure that you have " +
        "performed the authentication before.", USER_DN_CLAIM_NAME)
      throw new IllegalStateException("change password is failed: couldn't find the " + USER_DN_CLAIM_NAME +
        " claim in the login context. Ensure that you have performed the authentication before.")
    })(userDn => {
      Try(pool.getConnection) match {
        case Success(c) => {
          implicit val connection = c
          appLogTrace("LDAP connection has been got from the pool")
          val pswdMdfRes = Try({
            bind(userDn, curPswd)
            appLogTrace("try to change password in LDAP Server [userDn: {}]", userDn)
            val pswdMdfReq = new PasswordModifyExtendedRequest(curPswd, newPswd)
            connection.processExtendedOperation(pswdMdfReq).asInstanceOf[PasswordModifyExtendedResult]
          })
          pool.releaseConnection(connection)
          appLogTrace("LDAP connection has been released")

          pswdMdfRes match {
            case Success(res) => {
              if (res.getResultCode == ResultCode.SUCCESS) {
                appLogDebug("the password change was successful [userDn: {}, messageId: {}]",
                  userDn, res.getMessageID)
                true
              } else {
                appLogDebug("the password change failed [userDn: {}, resultCode: {}, messageId: {}, " +
                  "diagnosticMessage: {}]", userDn, res.getResultCode.getName, res.getMessageID, res.getDiagnosticMessage)
                handleLdapError(res)
                false
              }
            }
            case Failure(e) => {
              appLogTrace("An error occurred while attempting to process the password modify extended request: {}", e)
              e match {
                case le: LDAPException => {
                  handleLdapError(le.toLDAPResult)
                }
                case _ => {
                  appLogError("can't perform authentication by LDAP server. Internal error has occurred: {}", e)
                  lc withError BuildInError.INTERNAL
                }
              }
              false

            }
          }
        }
        case Failure(e) => {
          appLogError("can't get a ldap connection from the pool")
          throw e
        }
      }
    })
  }

  private def bind(userDn: String, pswd: String)(implicit connection: LDAPConnection): BindResult = {
    appLogTrace("try to bind to LDAP server with following userDn: {}", userDn)
    val bindReq = new SimpleBindRequest(userDn, pswd)
    //if bind failed it rise LdapException
    val bindRes = connection.bind(bindReq)
    if (!bindRes.getResultCode.isConnectionUsable) {
      appLogError("cannot bind to LDAP server: the connection isn't usable [resultCode: {}, messageId: {}, " +
        "diagnosticMessage: {}]", bindRes.getResultCode, bindRes.getMessageID, bindRes.getDiagnosticMessage)
      throw new RuntimeException("cannot bind to LDAP server: the connection isn't usable")
    }

    appLogDebug("bind to LDAP server is successful [userDn: {}, messageId: {}]", userDn, bindRes.getMessageID)
    bindRes
  }

  private def handleLdapError(err: LDAPResult)(implicit lc: LoginContext, request: Request[AnyContent]) = {
    appLogDebug("handling ldap error: [{resultCode: {}, messageId: {}, diagnosticMessage: {}}]",
      err.getResultCode.getName, err.getMessageID, err.getDiagnosticMessage)
    var isErrorResolvedByControl = false
    err.getResponseControls.foreach(control => {
      appLogTrace("got a control [name: {}, oid: {}]", control.getControlName, control.getOID)
      control match {
        case expiredControl: PasswordExpiredControl => {
          //todo: check is it possible to change password in this case
          appLogTrace("the subject's password has expired, adding the appropriate error")
          lc withError PASSWORD_EXPIRED
          isErrorResolvedByControl = true
        }
        case _ => {
          appLogDebug("unknown control, do nothing [name={}, oid={}]", control.getControlName,
            control.getOID)
        }
      }
    })

    if (!isErrorResolvedByControl) {
      appLogTrace("map result code to error [result code ={}]", err.getResultCode)
      errorMapper.get(err.getResultCode).fold({
        val errorKey = UNMAPPED_ERROR_MSG_PREFIX + err.getResultCode.getName.replaceAll(" ", "_")
        lc.withError(errorKey, Messages(errorKey))
      })(lc withError _)
    }
  }

  private def interpolate(text: String, vars: Map[String, String]) =
    (text /: vars) { (t, kv) => t.replace("${"+kv._1+"}", kv._2)  }

  override def toString: String = {
    val sb =new StringBuilder("LdapLoginModule(")
    sb.append("options -> ").append(options)
    sb.append(")").toString()
  }
}

object LdapLoginModule {
  val USER_DN_CLAIM_NAME = "USER_DN"

  val UNMAPPED_ERROR_MSG_PREFIX = "LdapLoginModule.error."
  val NEAR_PSWD_EXPIRE_WARN_KEY = "LdapLoginModule.warn.nearPswdExpire"

  //todo: add mapper for password expired
  val errorMapper = Map(ResultCode.NO_SUCH_OBJECT -> BuildInError.NO_SUBJECT_FOUND,
                        ResultCode.INVALID_CREDENTIALS -> BuildInError.INVALID_CREDENTIALS,
                        ResultCode.UNWILLING_TO_PERFORM -> BuildInError.ACCOUNT_IS_LOCKED)
}
