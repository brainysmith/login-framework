package com.identityblitz.login

import com.identityblitz.login.service.ServiceProvider
import com.identityblitz.json.{JArr, JVal, JObj}
import com.identityblitz.login.LoggingUtils._
import com.identityblitz.login.AttrType.AttrType
import scala.util.Try
import com.identityblitz.login.authn.method.AuthnMethod
import com.identityblitz.login.authn.bind.{BindFlag, BindProvider}
import com.identityblitz.login.util.Reflection
import scala.language.implicitConversions

/**
 */
object Conf {
  import ServiceProvider.confService

  val loginFlow = confService.getOptString("loginFlow").fold[LoginFlow]({
    logger.debug("will use the built-in login flow: {}", BuiltInLoginFlow.getClass.getSimpleName)
    BuiltInLoginFlow
  })(className => {
    logger.debug("find in the configuration a custom login flow [class = {}]", className)
    Reflection.getConstructor(className).apply().asInstanceOf[LoginFlow]
  })

  val bps = confService.getPropsDeepGrouped("bps").map{
    case (name, options) => name -> new BindProviderMeta(name, options).newInstance
  }

  val binds = confService.getPropsGrouped("binds").mapValues(bindSchema => {
    bindSchema.trim.split(";").map(bindMeta => {
      val parts = bindMeta.trim.split(" ")
      bps.getOrElse(parts(0), {
        val err = s"Can't parse binds configuration: specified '${parts(0)}' bind provider is not configured"
        logger.error(err)
        throw new IllegalStateException(err)
      }) -> {
        if (parts.size > 1)
          BindFlag.withName(parts(1))
        else BindFlag.sufficient
      }
    }).ensuring(_.length > 0, {
      val err = "Binds configuration error: some of the bind`s schema is blank"
      logger.error(err)
      throw new IllegalStateException(err)
    })
  })


  val methods = confService.getPropsDeepGrouped("authnMethods")
    .map({case (name, options) => AuthnMethodMeta(name, options)})
    .foldLeft[collection.mutable.Map[String, AuthnMethod]](collection.mutable.Map())(
      (res, meta) => {
        val instance = meta.newInstance
        if (meta.isDefault) {
          res += ("default" -> instance)
        }
        res += (instance.name -> instance)
      }
    ).toMap
}

trait Instantiable[A] {

  def options: Map[String, String]

  protected lazy val className = options.get("class").getOrElse({
    val error = "parameter 'class' is not specified in the options"
    logger.error(error)
    throw new IllegalStateException(error)
  })

  private lazy val classConstructor = Reflection.getConstructor(className)

  def args: Array[Any]

  def newInstance: A = classConstructor.apply(args _).asInstanceOf[A]

}


private case class BindProviderMeta(name: String, options: Map[String, String]) extends Instantiable[BindProvider] {

  override val args: Array[Any] = Array(options)

  override def toString: String = {
    val sb =new StringBuilder("BindProviderMeta(")
    sb.append("name -> ").append(name).append(",")
      .append("options -> ").append(options)
      .append(")").toString()
  }
}

private case class AuthnMethodMeta(name: String, options: Map[String, String]) extends Instantiable[AuthnMethod] {

  private object Options extends Enumeration {
    import scala.language.implicitConversions

    type Options = Value
    val default, bind, `class` = Value

    implicit def valueToString(v: Value): String = v.toString

    val builtInOptions = values.map(_.toString)
  }

  import Options._

  val isDefault = options.get(default).fold(false)(_.toBoolean)

  override val args: Array[Any] = Array(name, options.filter(entry => !builtInOptions.contains(entry._1)))

  override def toString: String = {
    val sb =new StringBuilder("AuthnMethodMeta(")
    sb.append("name -> ").append(name).append(",")
      .append("options -> ").append(options)
    .append(")").toString()
  }
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
