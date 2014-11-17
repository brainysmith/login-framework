package com.identityblitz.login.cmd

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.json._
import com.identityblitz.login.LoginFramework.logger
import com.identityblitz.login.LoginFramework
import com.identityblitz.login.error.{LoginException, CustomLoginError, LoginError, CommandException}
import com.identityblitz.login.provider.{WithBind, Provider}

/**
  */

sealed abstract class BindCommand(val methodName: String, val params: Seq[String],
                                  val attempts: Int = 0) extends Command {

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

  lazy val bindProviders = LoginFramework.methods(methodName).bindProviders.ensuring(!_.isEmpty, {
    val err = s"No bind provider found [authentication method = $methodName]. Check the configuration."
    logger.error(err)
    throw new IllegalStateException(err)
  })

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

}

import scala.language.postfixOps

final case class FirstBindCommand(override val methodName: String,
                                  override val params: Seq[String]) extends BindCommand(methodName, params) {
  override val name: String = FirstBindCommand.name
  override def selfpack(implicit itr: InboundTransport): String = FirstBindCommand._selfpack(this)
}

object FirstBindCommand {
  import JsonTools._

  val name: String = "firstBind"

  implicit def firstBindCommandJreader = new JReader[FirstBindCommand] {
    override def read(v: JVal): JResult[FirstBindCommand] =
      ((v \ "method").read[String] and
        (v \ "params").read[Seq[String]] $).lift(FirstBindCommand.apply)
  }

  implicit def firstBindCmdReader = new CmdReader[FirstBindCommand] {
    override def read(str: JVal): Either[LoginException, FirstBindCommand] = str.read[FirstBindCommand] match {
      case JSuccess(o) => Right(o)
      case JError(e) => Left(LoginException(e.mkString(",")))
    }
  }

  implicit def firstBindCmdWriter = new CmdWriter[FirstBindCommand] {
    override def write(cmd: FirstBindCommand): JVal = Json.obj(
      "method" -> JStr(cmd.methodName),
      "params" -> JArr(cmd.params.map(JStr(_)).toArray))
  }

  def _selfpack(cmd: FirstBindCommand)(implicit itr: InboundTransport): String = Command.pack(cmd)

}

final case class RebindCommand(override val methodName: String,
                               override val params: Seq[String],
                               override val attempts: Int) extends BindCommand(methodName, params, attempts) {
  override val name: String = RebindCommand.name
  override def selfpack(implicit itr: InboundTransport): String = RebindCommand._selfpack(this)
}

object RebindCommand {
  import JsonTools._

  val name: String = "rebind"

  implicit def rebindCommandJreader = new JReader[RebindCommand] {
    override def read(v: JVal): JResult[RebindCommand] =
      ((v \ "method").read[String] and
        (v \ "params").read[Seq[String]] and
        (v \ "attempts").read[Int] $).lift(RebindCommand.apply)
  }

  implicit def rebindCmdReader = new CmdReader[RebindCommand] {
    override def read(str: JVal): Either[LoginException, RebindCommand] = str.read[RebindCommand] match {
      case JSuccess(o) => Right(o)
      case JError(e) => Left(LoginException(e.mkString(",")))
    }
  }

  implicit def rebindCmdWriter = new CmdWriter[RebindCommand] {
    override def write(cmd: RebindCommand): JVal = Json.obj(
      "method" -> JStr(cmd.methodName),
      "params" -> JArr(cmd.params.map(JStr(_)).toArray),
      "attempts" -> JNum(cmd.attempts))
  }

  def _selfpack(cmd: RebindCommand)(implicit itr: InboundTransport): String = Command.pack(cmd)

}

object BindCommand {

  val name: String = "bind"

  def apply(methodName: String, params: Seq[String]) = FirstBindCommand(methodName, params)

  def apply(bindCmd: BindCommand) = RebindCommand(bindCmd.methodName, bindCmd.params, bindCmd.attempts + 1)

}
