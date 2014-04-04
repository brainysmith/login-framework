package com.identityblitz.login.api

import com.identityblitz.json.{JArr, JObj, JVal}
import com.identityblitz.build.sbt.EnumerationMacros._
import com.identityblitz.lang.scala.CustomEnumeration

/**
 */
trait AttrMeta {
  if (baseName == null || valType == null) throw new NullPointerException("baseName and valType can't ba null")

  def baseName: String

  def valType: AttrType

  override def toString: String = {
    val sb =new StringBuilder("AttrMeta(")
    sb.append("baseName -> ").append(baseName)
    sb.append(", ").append("valType -> ").append(valType.name)
    sb.append(")").toString()
  }
}

case class AttrMetaImpl(val baseName: String, valType: AttrType) extends AttrMeta {}

object AttrMeta {

  def apply(v: JObj): AttrMeta = {
    Right[String, Seq[Any]](Seq()).right.map(seq => {
      (v \ "baseName").asOpt[String].fold[Either[String, Seq[Any]]](Left("baseName.notFound"))(baseName => {
        Right(seq :+ baseName)
      })
    }).joinRight.right.map(seq => {
      (v \ "valType").asOpt[String].fold[Either[String, Seq[Any]]](Left("valType.notFound"))(valTypeStr => {

        AttrType.optValueOf(valTypeStr.toLowerCase).fold[Either[String, Seq[Any]]](Left("valType.unknown"))(valType => {
          Right(seq :+ valType)
        })
      })
    }).joinRight match {
      case Left(err) => {
        appLogError("can't parse attrMeta [error = {}, json = {}]", err, v.toJson)
        throw new IllegalArgumentException("can't parse attrMeta")
      }
      case Right(seq) => {
        val attrMeta = new AttrMetaImpl(seq(0).asInstanceOf[String], seq(1).asInstanceOf[AttrType])
        appLogTrace("the attrMeta has been parsed successfully [attrMeta = {}]", attrMeta)
        attrMeta
      }
    }
  }

  def apply(jsonStr: String): AttrMeta = apply(JVal.parseStr(jsonStr).asInstanceOf[JObj])

  def parseArray(jsonStr: String): Seq[AttrMeta] = {
    JVal.parseStr(jsonStr).asInstanceOf[JArr].map(jv => apply(jv.asInstanceOf[JObj]))
  }
}

sealed abstract class AttrType(private val _name: String) extends AttrType.Val {
  def name = _name
}

/**
 * Enumeration of the login statuses.
 */
object AttrType extends CustomEnumeration[AttrType] {
  INIT_ENUM_ELEMENTS()

  case object STRING extends AttrType("string")
  case object STRINGS extends AttrType("strings")
  case object BOOLEAN extends AttrType("boolean")
  case object NUMBER extends AttrType("number")
  case object BYTES extends AttrType("bytes")
}
