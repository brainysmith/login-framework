package com.identityblitz.login.authn.cmd

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.json._
import com.identityblitz.login.LoggingUtils._
import com.identityblitz.login.Conf
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import com.identityblitz.login.error.CommandException

/**
 */

case class BindCmd(methodName: String, params: Seq[String]) extends Command {
  import BindCmd._

  require(methodName != null, {
    val err = "Authentication method name can't be null"
    logger.error(err)
    err
  })

  require(params != null, {
    val err = "Params can't be null"
    logger.error(err)
    err
  })  

  lazy val bindProviders = Conf.methods(methodName)._2.bindProviders

  override val name: String = COMMAND_NAME

  override def saveState: JVal = Json.obj("method" -> JStr(methodName),
    "params" -> JArr(params.map(JStr(_)).toArray))

  override def execute(implicit iTr: InboundTransport, oTr: OutboundTransport): Either[CommandException, Option[Command]] = {
    val data = params.map(name => name -> iTr.getParameter(name).getOrElse(null)).toMap
    val bindRes = bindProviders.foldLeft[(String, Try[(JObj, Command)])](
      "none" -> Failure(new IllegalStateException(s"No bind provider found for '$methodName' authentication method"))){
      case (ok @ (bpName, Success(_)), _) =>
        logger.trace("Skip the '{}' binding provider because the binding already complete successfully", bpName)
        ok
      case ((bpName, err @ Failure(_)), bp) =>
        logger.trace("Attempting to bind with '{}' bind provider", bpName)
        val bindRes = bp.bind(data)
        logger.trace("Result of attempting to bind with '{}' bind provider: {}", bpName, bindRes)
        bpName -> bindRes
    }

    logger.debug("The final result of the binding command: {}", bindRes)

    bindRes match {
      case (bpName, Success((claims, command))) =>
        iTr.updatedLoginCtx(iTr.getLoginCtx.get.withClaims(claims))
        Success(command)
      case (_, Failure(err)) => Failure(err)
    }
  }
}

private[cmd] object BindCmd {

  val COMMAND_NAME = "bind"

  def apply(state: JVal) = new BindCmd((state \ "method").asOpt[String].getOrElse({
    val err = s"Deserialization of the bind command`s state [${state.toJson}] failed: method is not specified"
    logger.error(err)
    throw new IllegalArgumentException(err)
  }), (state \ "params").asOpt[Array[String]].getOrElse({
    val err = s"Deserialization of the bind command`s state [${state.toJson}] failed: params is not specified"
    logger.error(err)
    throw new IllegalArgumentException(err)
  }))


}
