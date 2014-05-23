package com.identityblitz.login.cmd

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.json._
import com.identityblitz.login.App.logger
import com.identityblitz.login.{LoginContext, App}
import com.identityblitz.login.error.{CustomLoginError, LoginError, CommandException}
import com.identityblitz.login.provider.{WithBind, Provider}

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

  lazy val bindProviders = App.methods(methodName).bindProviders.ensuring(!_.isEmpty, {
    val err = s"No bind provider found [authentication method = $methodName]. Check the configuration."
    logger.error(err)
    throw new IllegalStateException(err)
  })

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

  override def execute(implicit itr: InboundTransport, otr: OutboundTransport): Either[CommandException, Option[Command]] = {
    logger.trace("Executing bind command against following bind providers: {}", bindProviders)

    val data = (for {
      name <- params
      value <- itr.getParameter(name)
    } yield (name, value)).toMap

    def doBind(providers: Seq[Provider with WithBind],
               data: Map[String, String]) :Either[Seq[(String, LoginError)], (JObj, Option[Command])] = {
      providers.foldLeft[Either[Seq[(String, LoginError)], (JObj, Option[Command])]]{
        Left(Seq(("", CustomLoginError("no_provider_found"))))}{
        case (ok @ Right(_), bp) => return ok
        case (Left(errs), bp) => bp.bind(data).left.map(e => errs :+ (bp.name, e))
      }
    }

    doBind(bindProviders, data).right.map( t => {
      itr.updatedLoginCtx(itr.getLoginCtx.fold[LoginContext]{
        throw new IllegalStateException("Login context not found.")}(_ addClaims t._1))
      t._2
    }).left.map(e => {
      if(logger.isDebugEnabled)
        logger.debug(e map(l => l._1 + " -> " + l._2) mkString("Errors while binding: ", ",","."))
      new CommandException(this, e.last._2)
  })

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
