package com.identityblitz.login.authn.cmd

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.json._
import com.identityblitz.login.LoggingUtils._
import com.identityblitz.login.Conf
import com.identityblitz.login.error.{CustomLoginError, LoginError, CommandException}

/**
 */

sealed abstract class BindCommand(val methodName: String, val params: Seq[String],
                                  val attempts: Int = 0) extends Command {
  import BindCommand._

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
      "params" -> JArr(params.map(JStr(_)).toArray))
    if (attempts > 0) {
      json + ("attempts" -> JNum(attempts))
    } else {
      json
    }
  }

  override def execute(implicit iTr: InboundTransport, oTr: OutboundTransport): Either[CommandException, Option[Command]] = {
    logger.trace("executing bind command against following bind providers: {}", bindProviders)
    val data = params.map(name => name -> iTr.getParameter(name).getOrElse(null)).toMap
    val bindRes = bindProviders.ensuring(!_.isEmpty, {
        val err = s"No bind provider found [authentication method = $methodName]. Check the configuration."
        logger.error(err)
        throw new IllegalStateException(err)}
    ).foldLeft[Either[LoginError, (Option[JObj], Option[Command])]]{Left(CustomLoginError("no_provider_found"))}{
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
      case Right((claimsWrapped, command)) =>
        claimsWrapped.map(claims => iTr.updatedLoginCtx(iTr.getLoginCtx.get.withClaims(claims)))
        Right(command)
      case Left(errKey) =>
        Left(new CommandException(this, errKey))
    }
  }

  override def toString: String = new StringBuilder(this.getClass.getSimpleName).append("(")
    .append(saveState.toJson).append(")").toString()
}

final case class FirstBindCommand(override val methodName: String, override val params: Seq[String])
  extends BindCommand(methodName, params) {}

final case class RebindCommand(override val methodName: String, override val params: Seq[String],
                               override val attempts: Int)
  extends BindCommand(methodName, params, attempts) {}

object BindCommand {

  private[cmd] val COMMAND_NAME = "bind"

  def apply(methodName: String, params: Seq[String]) = FirstBindCommand(methodName, params)

  def apply(bindCmd: BindCommand) = RebindCommand(bindCmd.methodName, bindCmd.params, bindCmd.attempts + 1)

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
        FirstBindCommand(method, params)
      else
        RebindCommand(method, params, attempt)
  }
}
