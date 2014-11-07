package com.identityblitz.login.cmd

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.json._
import com.identityblitz.login.LoginFramework.logger
import com.identityblitz.login.LoginFramework
import com.identityblitz.login.error.{CustomLoginError, LoginError, CommandException}
import com.identityblitz.login.provider.{WithBind, Provider}

/**
  */

sealed abstract class BindCommand(val methodName: String, val atrs: Map[String,String], val params: Seq[String],
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

  require(atrs != null, {
    val err = "Attributes can't be null"
    logger.error(err)
    err
  })

  lazy val bindProviders = LoginFramework.methods(methodName).bindProviders.ensuring(!_.isEmpty, {
    val err = s"No bind provider found [authentication method = $methodName]. Check the configuration."
    logger.error(err)
    throw new IllegalStateException(err)
  })

  override val name: String = COMMAND_NAME

  override def saveState: JVal = {
    val json = Json.obj("method" -> JStr(methodName),
      "atrs" -> JObj(atrs.map(e => e._1 -> JStr(e._2)).toList),
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
    } yield (name, value)).toMap ++ atrs

    def doBind(providers: Seq[Provider with WithBind],
               data: Map[String, String]) :Either[Seq[(String, LoginError)], (JObj, Option[Command])] = {
      providers.foldLeft[Either[Seq[(String, LoginError)], (JObj, Option[Command])]]{
        Left(Seq(("", CustomLoginError("no_provider_found"))))}{
        case (ok @ Right(_), bp) => return ok
        case (Left(errs), bp) => bp.bind(data).left.map(e => errs :+ (bp.name, e))
      }
    }

    doBind(bindProviders, data).right.map( t => {
      itr.updatedLoginCtx(itr.getLoginCtx.map(_ addClaims t._1).orElse({
        val err = "Login context not found."
        logger.error(err)
        throw new IllegalStateException(err)
      }))
      t._2
    }).left.map(e => {
      if(logger.isDebugEnabled)
        logger.debug(e map(l => l._1 + " -> " + l._2) mkString("Errors while binding: ", ",","."))
      new CommandException(this, e.last._2)
    })
  }

  def updated(newAtrs : Map[String,String]): BindCommand = this match {
    case FirstBindCommand(m, a, p) => FirstBindCommand(m, a ++ newAtrs, p)
    case RebindCommand(m, a, p, at) => RebindCommand(m, a ++ newAtrs, p, at)
  }

  override def toString: String = new StringBuilder(this.getClass.getSimpleName).append("(")
    .append(saveState.toJson).append(")").toString()
}

final case class FirstBindCommand(override val methodName: String, override val atrs: Map[String,String], override val params: Seq[String])
  extends BindCommand(methodName, atrs, params) {}

final case class RebindCommand(override val methodName: String, override val atrs: Map[String,String], override val params: Seq[String],
                               override val attempts: Int)
  extends BindCommand(methodName, atrs, params, attempts) {}

object BindCommand {

  private[cmd] val COMMAND_NAME = "bind"

  def apply(methodName: String, atrs: Map[String,String], params: Seq[String]) = FirstBindCommand(methodName, atrs, params)

  def apply(bindCmd: BindCommand) = RebindCommand(bindCmd.methodName, bindCmd.atrs, bindCmd.params, bindCmd.attempts + 1)

  private[cmd] def apply(state: JVal) = Right[String, (String, Map[String,String], Seq[String], Int)](Tuple4(null, Map(), Seq(), 0))
    .right.flatMap(acm => (state \ "method").asOpt[String].fold[Either[String, (String, Map[String,String], Seq[String], Int)]]{
    Left(s"Deserialization of the bind command`s state [${state.toJson}] failed: method is not specified")
  }{Right(_, acm._2, acm._3, acm._4)})
    .right.flatMap(acm => (state \ "atrs").asOpt[Map[String,String]].fold[Either[String, (String, Map[String,String], Seq[String], Int)]]{
    Left(s"Deserialization of the bind command`s state [${state.toJson}] failed: atrs is not specified")
  }{Right(acm._1, _, acm._3, acm._4)})
    .right.flatMap(acm => (state \ "params").asOpt[Array[String]].fold[Either[String, (String, Map[String,String], Seq[String], Int)]]{
    Left(s"Deserialization of the bind command`s state [${state.toJson}] failed: params is not specified")
  }{Right(acm._1, acm._2, _, acm._4)})
    .right.flatMap(acm => (state \ "attempts").asOpt[Int].fold[Either[String, (String, Map[String,String], Seq[String], Int)]]{
    Right(acm._1, acm._2, acm._3, 0)}{Right(acm._1, acm._2, acm._3, _)}) match {
    case Left(err) =>
      logger.error(err)
      throw new IllegalArgumentException(err)
    case Right((method, atrs, params, attempt)) =>
      if (attempt == 0)
        FirstBindCommand(method, atrs, params)
      else
        RebindCommand(method, atrs, params, attempt)
  }
}
