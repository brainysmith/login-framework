package com.identityblitz.login.cmd

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.json._
import com.identityblitz.login.LoginFramework.logger
import com.identityblitz.login.LoginFramework
import com.identityblitz.login.error.{LoginException, CustomLoginError, LoginError, CommandException}
import com.identityblitz.login.provider.{WithBind, Provider}

/**
  */

import scala.language.postfixOps

class BindCommand private (val methodName: String, val attempts: Int) extends Command {

  require(methodName != null, {
    val err = "Authentication method name can't be null"
    logger.error(err)
    err
  })

  lazy val bindProviders = LoginFramework.methods(methodName).bindProviders.ensuring(!_.isEmpty, {
    val err = s"No bind provider found [authentication method = $methodName]. Check the configuration."
    logger.error(err)
    throw new IllegalStateException(err)
  })

  import BindCommand.FormParams._
  override def onExecute(implicit itr: InboundTransport, otr: OutboundTransport): Either[CommandException, Option[Command]] = {
    if (logger.isTraceEnabled)
      logger.trace("Executing bind command against following bind providers: {}", bindProviders)

    val data = (for {
      name <- allParams
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

  override val name: String = BindCommand.name
  override def selfpack(implicit itr: InboundTransport): String = BindCommand._selfpack(this)

}



object BindCommand {
  import JsonTools._

  val name: String = "bind"

  object FormParams extends Enumeration {
    import scala.language.implicitConversions

    type Options = Value
    val login, password = Value

    implicit def valueToString(v: Value): String = v.toString

    val allParams = values.map(_.toString).toSeq
  }

  implicit def bindCommandJreader = new JReader[BindCommand] {
    override def read(v: JVal): JResult[BindCommand] =
      ((v \ "method").read[String] and
        (v \ "attempts").read[Int] $).lift((m,a) => new BindCommand(m,a))
  }

  implicit def bindCmdReader = new CmdReader[BindCommand] {
    override def read(str: JVal): Either[LoginException, BindCommand] = str.read[BindCommand] match {
      case JSuccess(o) => Right(o)
      case JError(e) => Left(LoginException(e.mkString(",")))
    }
  }

  implicit def bindCmdWriter = new CmdWriter[BindCommand] {
    override def write(cmd: BindCommand): JVal = Json.obj(
      "method" -> JStr(cmd.methodName),
      "attempts" -> JNum(cmd.attempts))
  }

  def _selfpack(cmd: BindCommand)(implicit itr: InboundTransport): String = Command.pack(cmd)

  def apply(methodName: String) = new BindCommand(methodName, 0)

  def apply(bindCmd: BindCommand) = new BindCommand(bindCmd.methodName, bindCmd.attempts + 1)

  def unapply(bindCmd: BindCommand): Option[(String, Int)] =
    Some((bindCmd.methodName, bindCmd.attempts))

}
