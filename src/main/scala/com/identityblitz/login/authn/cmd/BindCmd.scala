package com.identityblitz.login.authn.cmd

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.json._
import com.identityblitz.login.LoggingUtils._
import com.identityblitz.login.Conf
import com.identityblitz.login.error.CommandException

/**
 */

sealed abstract class BindCmd(val methodName: String, val params: Seq[String],
                              val attempts: Int = 0, val errorKey: String = null) extends Command {
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

  override def saveState: JVal = {
    val json = Json.obj("method" -> JStr(methodName),
      "params" -> JArr(params.map(JStr(_)).toArray),
      "attempts" -> JNum(attempts))
    if (attempts > 0) {
      json + ("attempts" -> JNum(attempts)) + ("errorKey" -> JStr(errorKey))
    } else {
      json
    }
  }

  override def execute(implicit iTr: InboundTransport, oTr: OutboundTransport): Either[CommandException, Option[Command]] = {
    logger.trace("executing bind command against following bind providers: {}", bindProviders)
    val data = params.map(name => name -> iTr.getParameter(name).getOrElse(null)).toMap
    val bindRes = bindProviders.foldLeft[Either[String, (JObj, Option[Command])]]{
      Left("no_bind_provider_found")
    }{
      case (ok @ Right(_), bp) =>
        logger.trace("Skip the '{}' binding provider because the binding has already completed successfully", bp.name)
        ok
      case (Left(err), bp) =>
        logger.trace("Attempting to bind with '{}' bind provider", bp.name)
        val iRes = bp.bind(data)
        logger.trace("Result of attempting to bind with '{}' bind provider: {}", bp.name, iRes)
        iRes
    }

    logger.debug("The final result of the binding command: {}", bindRes)
    bindRes match {
      case Right((claims, command)) =>
        iTr.updatedLoginCtx(iTr.getLoginCtx.get.withClaims(claims))
        Right(command)
      case Left(errKey) =>
        Left(new CommandException(this, errKey))
    }
  }

}

final case class FirstBindCmd(override val methodName: String, override val params: Seq[String])
  extends BindCmd(methodName, params) {}

final case class RebindCmd(override val methodName: String, override val params: Seq[String],
                           override val attempts: Int, override val errorKey: String)
  extends BindCmd(methodName, params, attempts, errorKey) {}

object BindCmd {

  private[cmd] val COMMAND_NAME = "bind"

  def apply(methodName: String, params: Seq[String]) = FirstBindCmd(methodName, params)

  def apply(bindCmd: BindCmd, errorKey: String) =
    RebindCmd(bindCmd.methodName, bindCmd.params, bindCmd.attempts + 1, errorKey)

  private[cmd] def apply(state: JVal) = Right[String, (String, Seq[String], Int)](Tuple3(null, Seq(), 0))
    .right.flatMap(acm => (state \ "method").asOpt[String].fold[Either[String, (String, Seq[String], Int)]]{
      Left(s"Deserialization of the bind command`s state [${state.toJson}] failed: method is not specified")
      }{Right(_, acm._2, acm._3)})
    .right.flatMap(acm => (state \ "params").asOpt[Array[String]].fold[Either[String, (String, Seq[String], Int)]]{
      Left(s"Deserialization of the bind command`s state [${state.toJson}] failed: params is not specified")
      }{Right(acm._1, _, acm._3)})
    .right.flatMap(acm => (state \ "attempts").asOpt[Int].fold[Either[String, (String, Seq[String], Int)]]{
    Right(acm._1, acm._2, 0)}{Right(acm._1, acm._2, _)}) match {
    case Left(err) =>
      logger.error(err)
      throw new IllegalArgumentException(err)
    case Right((method, params, attempt)) =>
      if (attempt == 0)
        FirstBindCmd(method, params)
      else
        RebindCmd(method, params, attempt, (state \ "errorKey").asOpt[String].getOrElse(null))
  }
}
