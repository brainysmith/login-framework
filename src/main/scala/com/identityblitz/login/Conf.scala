package com.identityblitz.login

import com.identityblitz.login.service.ServiceProvider
import ServiceProvider.confService
import com.identityblitz.json.{JArr, JVal, JObj}
import com.identityblitz.login.LoggingUtils._
import com.identityblitz.login.AttrType.AttrType
import scala.util.Try

/**
 */
object Conf {
  private val BINDS_CONF_PREFIX = "binds"

  val loginFlow = confService.getOptString("loginFlow")

  val binds = confService.getPropsDeepGrouped("binds")

  val authnMethods = confService.getPropsDeepGrouped("authnMethods")

  def extractBindOptions(options: Map[String, String]) = options.filter(_._1.startsWith("binds"))
    .map({case (k,v) => k.stripPrefix(BINDS_CONF_PREFIX + ".") -> v})
    .groupBy(_._1.split('.')(0)).map(entry => {
    (entry._1, entry._2.map({case (k, v) => (k.stripPrefix(entry._1 + "."), v)}))
  })
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
 * Enumeration of the login attribute types.
 */
object AttrType extends Enumeration {
  type AttrType = Value
  val string, strings, boolean, number, bytes = Value
}
