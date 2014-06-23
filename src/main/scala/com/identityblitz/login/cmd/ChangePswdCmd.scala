package com.identityblitz.login.cmd

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.json._
import com.identityblitz.login.error.CommandException
import com.identityblitz.login.error.BuiltInErrors._
import com.identityblitz.login.LoginFramework.logger
import com.identityblitz.login.LoginFramework
import com.identityblitz.login.provider.WithChangePassword


/**
 
 */
case class ChangePswdCmd(providerName: String, userId: String, attempts: Int = 0) extends Command {
  import ChangePswdCmd._

  require(providerName != null, {
    val err = "Provider's name can't be null"
    logger.error(err)
    err
  })

  require(userId != null, {
    val err = "User identificator can't be null"
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
  
  override val name: String = COMMAND_NAME


  override def execute(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    logger.trace("Executing change password command against following bind provider: {}", providerName)
    (iTr.getParameter("currentPassword"), iTr.getParameter("newPassword")) match {
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

  override def saveState: JVal = {
    val json = Json.obj("provider" -> JStr(providerName),
      "userId" -> JStr(userId))
    if (attempts > 0) {
      json + ("attempts" -> JNum(attempts))
    } else {
      json
    }
  }
}

object ChangePswdCmd {

  private[cmd] val COMMAND_NAME = "changePassword"

  def apply(cmd: ChangePswdCmd) = new ChangePswdCmd(cmd.providerName, cmd.userId, cmd.attempts + 1)

  private[cmd] def apply(state: JVal) = Right[String, (String, String, Int)](Tuple3(null, null, 0))
    .right.flatMap(acm => (state \ "provider").asOpt[String].fold[Either[String, (String, String, Int)]]{
    Left(s"Deserialization of the bind command`s state [${state.toJson}] failed: provider is not specified")
  }{Right(_, acm._2, acm._3)})
    .right.flatMap(acm => (state \ "userId").asOpt[String].fold[Either[String, (String, String, Int)]]{
    Left(s"Deserialization of the bind command`s state [${state.toJson}] failed: userId is not specified")
  }{Right(acm._1, _, acm._3)})
    .right.flatMap(acm => (state \ "attempts").asOpt[Int].fold[Either[String, (String, String, Int)]]{
    Right(acm._1, acm._2, 0)}{Right(acm._1, acm._2, _)}) match {
    case Left(err) =>
      logger.error(err)
      throw new IllegalArgumentException(err)
    case Right((provider, userId, attempts)) => new ChangePswdCmd(provider, userId, attempts)
  }

}
