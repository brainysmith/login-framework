package com.identityblitz.login.provider

import com.identityblitz.json.{JArr, JVal, JObj}
import com.identityblitz.login.LoginFramework._
import com.identityblitz.login.provider.AttrType.AttrType

import scala.util.Try

trait AttrMeta {
  if (name == null || valType == null) throw new NullPointerException("name and valType can't ba null")

  def name: String

  def valType: AttrType

  override def toString: String = {
    val sb =new StringBuilder("AttrMeta(")
    sb.append("name -> ").append(name)
    sb.append(", ").append("valType -> ").append(valType)
    sb.append(")").toString()
  }
}

private case class AttrMetaImpl(name: String, valType: AttrType) extends AttrMeta {}

object AttrMeta {

  def apply(v: JObj): AttrMeta = {
    Right[String, Seq[Any]](Seq()).right.map(seq => {
      (v \ "name").asOpt[String].fold[Either[String, Seq[Any]]](Left("name.notFound"))(name => {
        Right(seq :+ name)
      })
    }).joinRight.right.map(seq => {
      (v \ "valType").asOpt[String].fold[Either[String, Seq[Any]]](Left("valType.notFound"))(valTypeStr => {
        Try(AttrType.withName(valTypeStr.toLowerCase)).toOption
          .fold[Either[String, Seq[Any]]](Left("valType.unknown"))(valType => {
          Right(seq :+ valType)
        })
      })
    }).joinRight match {
      case Left(err) =>
        logger.error("can't parse attrMeta [error = {}, json = {}]", Seq(err, v.toJson))
        throw new IllegalArgumentException("can't parse attrMeta")
      case Right(seq) =>
        val attrMeta = new AttrMetaImpl(seq(0).asInstanceOf[String], seq(1).asInstanceOf[AttrType])
        logger.error("the attrMeta has been parsed successfully [attrMeta = {}]", attrMeta)
        attrMeta
    }
  }

  def apply(jsonStr: String): AttrMeta = apply(JVal.parse(jsonStr).asInstanceOf[JObj])

  def apply(t: (String, String)): AttrMeta = new AttrMetaImpl(t._1, AttrType.withName(t._2))

  def parseArray(jsonStr: String): Seq[AttrMeta] = {
    JVal.parse(jsonStr).asInstanceOf[JArr].map(jv => apply(jv.asInstanceOf[JObj]))
  }
}

/**
 * Enumeration of attribute types.
 */
object AttrType extends Enumeration {
  type AttrType = Value
  val string, strings, boolean, number, bytes = Value
}