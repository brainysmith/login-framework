package com.identityblitz.login.cmd

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.json._
import com.identityblitz.login.error.{LoginException, CommandException}
import com.identityblitz.login.error.BuiltInErrors._
import com.identityblitz.login.LoginFramework.logger
import com.identityblitz.login.LoginFramework
import com.identityblitz.login.provider.WithChangePassword

class ChangePswdCmd private (val providerName: String, val userId: String, val attempts: Int) extends Command {

  require(providerName != null, {
    val err = "Provider's name can't be null"
    logger.error(err)
    err
  })

  require(userId != null, {
    val err = "User identifier can't be null"
    logger.error(err)
    err
  })

  lazy val provider = LoginFramework.providers.get(providerName)
    .filter(p => classOf[WithChangePassword].isAssignableFrom(p.getClass))
    .map(_.asInstanceOf[WithChangePassword]).getOrElse({
    val err = s"Provider with specified name '$providerName' is not configured."
    logger.error(err)
    throw new IllegalArgumentException(err)
  })

  override val name: String = ChangePswdCmd.name
  import ChangePswdCmd.FormParams._
  override def onExecute(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    logger.trace("Executing change password command against following bind provider: {}", providerName)
    (iTr.getParameter(currentPassword), iTr.getParameter(newPassword)) match {
      case (Some(curPswd), Some(newPswd)) =>
        provider.changePswd(userId, curPswd, newPswd)
          .left.map(CommandException(this, _))
          .right.map{ case (claims, cmd) =>
          iTr.updatedLoginCtx(iTr.getLoginCtx.map(_ addClaims claims).orElse({
            val err = "Login context not found."
            logger.error(err)
            throw new IllegalStateException(err)
          }))
          cmd
        }
      case _ =>
        logger.warn("Can't perform change password [userId = {}]: current or new passwords is not specified in " +
          "the request", userId)
        Left(CommandException(this, NO_CREDENTIALS_FOUND))
    }
  }

  override def selfpack(implicit itr: InboundTransport): String = ChangePswdCmd._selfpack(this)
}

object ChangePswdCmd {

  val name = "changePassword"

  object FormParams extends Enumeration {
    import scala.language.implicitConversions

    type Options = Value
    val currentPassword, newPassword = Value

    implicit def valueToString(v: Value): String = v.toString

    val allParams = values.map(_.toString).toSeq
  }


  def apply(cmd: ChangePswdCmd) = new ChangePswdCmd(cmd.providerName, cmd.userId, cmd.attempts + 1)

  def apply(providerName: String, userId: String) = new ChangePswdCmd(providerName, userId, 0)

  def unapply(cmd: ChangePswdCmd): Option[(String, String, Int)] =
    Some((cmd.providerName, cmd.userId, cmd.attempts))

  import JsonTools._
  import scala.language.postfixOps

  implicit def changePswdCommandJreader = new JReader[ChangePswdCmd] {
    override def read(v: JVal): JResult[ChangePswdCmd] =
      ((v \ "provider").read[String] and
        (v \ "userId").read[String] and
        (v \ "attempts").read[Int] $).lift((p,u,a) => new ChangePswdCmd(p,u,a))
  }

  implicit def changePswdCmdReader = new CmdReader[ChangePswdCmd] {
    override def read(str: JVal): Either[LoginException, ChangePswdCmd] = str.read[ChangePswdCmd] match {
      case JSuccess(o) => Right(o)
      case JError(e) => Left(LoginException(e.mkString(",")))
    }
  }

  implicit def changePswdCmdWriter = new CmdWriter[ChangePswdCmd] {
    override def write(cmd: ChangePswdCmd): JVal = Json.obj(
      "provider" -> JStr(cmd.providerName),
      "userId" -> JStr(cmd.userId),
      "attempts" -> JNum(cmd.attempts))
  }

  def _selfpack(cmd: ChangePswdCmd)(implicit itr: InboundTransport): String = Command.pack(cmd)

}
