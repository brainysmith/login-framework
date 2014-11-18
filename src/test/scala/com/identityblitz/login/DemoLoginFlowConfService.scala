package com.identityblitz.login

import com.identityblitz.login.service.spi.LoginConfService
import com.typesafe.config.{Config, ConfigFactory}
import scala.collection.JavaConverters._

class DemoLoginFlowConfService extends LoginConfService {

  val confUrl = this.getClass.getClassLoader.getResource("login_flow_test.conf")
  val conf = ConfigFactory.parseURL(confUrl).getConfig("login")

  override def getDeepMapString(prefix: String): Map[String, Map[String, String]] = Some(conf.getConfig(prefix))
    .fold[Map[String, Map[String, String]]](Map.empty){c => {
    c.entrySet().asScala.map(_.getKey.split('.')(0)).map{case key => key -> _toMapString(c.getConfig(key))}.toMap}}

  override def getMapString(prefix: String): Map[String, String] = Some(conf.getConfig(prefix))
    .fold[Map[String, String]](Map.empty)(_toMapString)

  override def getOptBoolean(implicit name: String): Option[Boolean] = _safeUnwrap(_.getBoolean(_))

  override def getOptString(implicit name: String): Option[String] = _safeUnwrap(_.getString(_))

  override def getOptLong(implicit name: String): Option[Long] = _safeUnwrap(_.getLong(_))

  @inline private[this] def _toMapString(cnf: Config): Map[String, String] = cnf.entrySet().asScala.map(_.getKey match {case key => key -> cnf.getString(key)}).toMap

  @inline private[this] def _safeUnwrap[B](func:(Config, String) => B)(implicit name2: String): Option[B] = conf.hasPath(name2) match {
    case true => Option(func(conf, name2))
    case false => None
  }

}

