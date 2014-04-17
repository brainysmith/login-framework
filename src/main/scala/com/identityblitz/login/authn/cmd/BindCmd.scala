package com.identityblitz.login.authn.cmd

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.json._
import com.identityblitz.login.LoggingUtils._
import com.identityblitz.login.Conf
import scala.collection.mutable.ListBuffer
import com.identityblitz.login.authn._
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import scala.Some
import com.identityblitz.login.authn.bind.BindFlag

/**
 */

case class BindCmd(schemaName: String, params: Seq[String]) extends Command {
  import BindCmd._

  require(schemaName != null, {
    val err = "bind schema can't be null"
    logger.error(err)
    err
  })

  require(params != null, {
    val err = "params can't be null"
    logger.error(err)
    err
  })  

  lazy val schema = Conf.binds.getOrElse(schemaName, {
    val err = s"Specified bind schema [$schemaName] is not configured"
    logger.error(err)
    throw new IllegalArgumentException(err)
  })

  override val name: String = COMMAND_NAME

  override def saveState: JVal = Json.obj("schema" -> JStr(schemaName),
    "params" -> JArr(params.map(JStr(_)).toArray))

  override def execute(implicit iTr: InboundTransport, oTr: OutboundTransport): Try[Seq[Command]] = {
    val data = params.map(name => name -> iTr.getParameter(name).getOrElse(null)).toMap
    val resList = schema.foldLeft[(Boolean, ListBuffer[BindRes])](true ->ListBuffer()){(acm, bindMeta) =>
      if (acm._1) {
        val bindRes = bindMeta._1.bind(data)
        acm._2 += bindRes
        /** see: http://docs.oracle.com/javase/1.5.0/docs/api/javax/security/auth/login/Configuration.html **/
        val isSuccess = (bindMeta._2, bindRes) match {
          case (BindFlag.requisite, Failure(_)) | (BindFlag.sufficient, Success(_)) => false
          case _ => true
        }
        logger.trace("result of the binding attempt [bp = {}]: {}", bindMeta, bindRes)
        isSuccess -> acm._2
      } else {
        acm
      }
    }._2

      /** checks criteria to success:
        * 1. all required must be completed successfully
        * 2. at least one required must be completed successfully
        *
        * see:
        * 1. http://docs.oracle.com/javase/1.5.0/docs/api/javax/security/auth/login/Configuration.html
        * 2. http://docs.oracle.com/javase/1.5.0/docs/guide/security/jaas/JAASRefGuide.html
        *
        */
    (schema zip resList).foldLeft[(Boolean, Boolean, Option[Throwable])]((true, false, None)){
      (acm, zipElem) => zipElem match {
        case ((_, bindFlag), Success(_)) =>
          /** bind result is successfully **/
          if (bindFlag.isRequired) (acm._1, true, acm._3) else acm
        case ((_, bindFlag), Failure(err)) =>
          /** bind result is unsuccessfully **/
          if (bindFlag.isRequired) (false, acm._2, Some(err)) else acm
      }
    } match {
      case (true, true, _) =>
        /** authentication is successfully **/
        val cmds = resList.filter(_.isSuccess).map(_.get).map{case (claims, commands) =>
          iTr.updatedLoginCtx(iTr.getLoginCtx.get.withClaims(claims))
          commands
        }.flatten.toSeq
        logger.debug("Authentication is successfully. Returned commands: {}", cmds)
        Success(cmds)
      case (_, _, Some(err)) =>
        logger.debug("Authentication is not successful. Returned error: {}", err)
        Failure(err)
      case res @ _ =>
        val err = s"Internal error: unexpected result [$res] of the binds process"
        logger.error(err)
        throw new RuntimeException(err)
    }
  }
}

private[cmd] object BindCmd {

  val COMMAND_NAME = "bind"

  def apply(state: JVal) = new BindCmd((state \ "schema").asOpt[String].getOrElse({
    val err = s"Deserialization of the bind command`s state [${state.toJson}] failed: bindSchema is not specified"
    logger.error(err)
    throw new IllegalArgumentException(err)
  }), (state \ "params").asOpt[Array[String]].getOrElse({
    val err = s"Deserialization of the bind command`s state [${state.toJson}] failed: params is not specified"
    logger.error(err)
    throw new IllegalArgumentException(err)
  }))


}
