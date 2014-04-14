package com.identityblitz.login.authn.bind

import com.identityblitz.login.LoggingUtils._
import com.unboundid.ldap.sdk._
import com.unboundid.util.ssl.{TrustAllTrustManager, SSLUtil}
import com.identityblitz.login.authn.bind.LdapBindProvider._
import scala.collection
import scala.util.Try

/**
 */
class LdapBindProvider(options: Map[String, String]) extends BindProvider(options) {

  logger.trace("initializing the ldap authentication module [options={}]", options)

  private val staticConf = paramsMeta.foldLeft[(List[String], collection.mutable.Map[String, Any])]((List(), collection.mutable.Map()))(
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

  private val host = staticConf("host").asInstanceOf[String]

  private val port = staticConf("port").asInstanceOf[Int]

  private val userDn = staticConf("userDn").asInstanceOf[String]

  private val useSSL = staticConf("useSSL").asInstanceOf[Boolean]

  private val autoReconnect = staticConf("autoReconnect").asInstanceOf[Boolean]

  private val initialConnections = staticConf("initialConnections").asInstanceOf[Int]

  private val maxConnections = staticConf("maxConnections").asInstanceOf[Int]

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

  //todo: do it
  override def bind(bindOptions: Map[String, String])(data: Map[String, String]): Try[Map[String, Any]] = {
    ???
  }


  /** other functions **/

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

}
