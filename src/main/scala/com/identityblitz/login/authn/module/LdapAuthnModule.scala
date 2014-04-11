package com.identityblitz.login.authn.module

import com.identityblitz.login.LoggingUtils._
import com.unboundid.ldap.sdk._
import com.unboundid.util.ssl.{TrustAllTrustManager, SSLUtil}

/**
 */
class LdapAuthnModule(options: Map[String, String]) extends AuthnModule(options) {

  logger.trace("initializing the ldap authentication module [options={}]", options)

  val host:String = options.get("host").getOrElse({
    logger.error("Parameter 'host' not specified in the configuration for the ldap authentication method")
    throw new IllegalStateException("parameter 'host' not specified in the configuration for the ldap authentication method")
  })

  val port = options.get("port").getOrElse({
    logger.error("Parameter 'port' not specified in the configuration for the ldap authentication method")
    throw new IllegalStateException("parameter 'port' not specified in the configuration for the ldap authentication method")
  }).toInt

  val userDnPattern = options.get("userDn").getOrElse({
    logger.error("Parameter 'userDn' not specified in the configuration for the ldap authentication method")
    throw new IllegalStateException("parameter 'userDn' not specified in the configuration for the ldap authentication method")
  })

  val useSSL = options.get("useSSL").map(_.toBoolean).getOrElse({
    logger.debug("Parameter 'useSSL' not specified in the configuration for the ldap authentication method. " +
      "Will use the default value 'true'")
    true
  })

  val autoReconnect = options.get("autoReconnect").map(_.toBoolean).getOrElse({
    logger.debug("Parameter 'autoReconnect' not specified in the configuration for the ldap authentication method. " +
      "Will use the default value 'true'")
    true
  })

  val initialConnections = options.get("initialConnections").map(_.toInt).getOrElse({
    logger.debug("Parameter 'autoReconnect' not specified in the configuration for the ldap authentication method. " +
      "Will use the default value '1'")
    1
  })

  val maxConnections = options.get("maxConnections").map(_.toInt).getOrElse({
    logger.debug("Parameter 'autoReconnect' not specified in the configuration for the ldap authentication method. " +
      "Will use the default value '3'")
    3
  })

  val connectionOption = {
    val opt = new LDAPConnectionOptions()
    opt.setAutoReconnect(autoReconnect)
    opt
  }

  val connection: LDAPConnection = useSSL match {
    case true =>
      val sslUtil = new SSLUtil(new TrustAllTrustManager())
      new LDAPConnection(sslUtil.createSSLSocketFactory(), connectionOption, host, port)
    case false =>
      new LDAPConnection(connectionOption, host, port)
  }

  val pool = new LDAPConnectionPool(connection, initialConnections, maxConnections)

  //todo: do it
  override def bind(): Unit = ???


  private def interpolate(text: String, vars: Map[String, String]) =
    (text /: vars) { (t, kv) => t.replace("${"+kv._1+"}", kv._2)  }

  override def toString: String = {
    val sb =new StringBuilder("LdapAuthnModule(")
    sb.append("options -> ").append(options)
    sb.append(")").toString()
  }
}

object LdapAuthnModule {
  val metaOpt = Map[String, Option[Any]]("host" -> None, "port" -> None)
}
