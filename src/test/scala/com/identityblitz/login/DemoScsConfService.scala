package com.identityblitz.login

import java.lang.{Boolean, Long}

import com.identityblitz.scs.service.spi.ConfigurationService
import com.typesafe.config.{Config, ConfigFactory}

class DemoScsConfService extends ConfigurationService {
  val confUrl = this.getClass.getClassLoader.getResource("login_flow_test.conf")
  val conf = ConfigFactory.parseURL(confUrl).getConfig("scs")

  private def stripName(name: String) = name.stripPrefix("com.identityblitz.scs.")

  override def getBoolean(name: String, defaultValue: Boolean): Boolean = _safeUnwrap((c, n) => scala.Boolean.box(c.getBoolean(n)))(stripName(name))
    .getOrElse(defaultValue)

  override def getBoolean(name: String): Boolean = _safeUnwrap((c, n) => scala.Boolean.box(c.getBoolean(n)))(stripName(name)).orNull

  override def getString(name: String, defaultValue: String): String = _safeUnwrap(_.getString(_))(stripName(name))
    .getOrElse(defaultValue)

  override def getString(name: String): String = _safeUnwrap(_.getString(_))(stripName(name)).orNull[String]

  override def getLong(name: String, defaultValue: Long): Long = _safeUnwrap((c, n) => scala.Long.box(c.getLong(n)))(stripName(name))
    .getOrElse(defaultValue)

  override def getLong(name: String): Long = _safeUnwrap((c, n) => scala.Long.box(c.getLong(n)))(stripName(name)).orNull

  @inline private[this] def _safeUnwrap[B](func:(Config, String) => B)(name: String): Option[B] = conf.hasPath(name) match {
    case true => Option(func(conf, name))
    case false => None
  }

}
