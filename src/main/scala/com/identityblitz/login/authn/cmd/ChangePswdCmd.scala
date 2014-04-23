package com.identityblitz.login.authn.cmd

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.json._
import com.identityblitz.login.error.CommandException
import com.identityblitz.login.error.BuiltInErrors._
import com.identityblitz.login.LoggingUtils._
import com.identityblitz.login.Conf
import com.identityblitz.login.authn.provider.{Provider, WithChangePswd}


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

  lazy val provider = Conf.providers.get(providerName).map(_.asInstanceOf[Provider with WithChangePswd]).getOrElse({
    val err = s"Provider with specified name '$providerName' is not configured."
    logger.error(err)
    throw new IllegalArgumentException(err)
  })
  
  override val name: String = COMMAND_NAME


  override def execute(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    logger.trace("executing change password command against following bind provider: {}", providerName)
    (iTr.getParameter("currentPassword"), iTr.getParameter("newPassword")) match {
      case (Some(curPswd), Some(newPswd)) =>
        provider.changePswd(userId, curPswd, newPswd).left.map(CommandException(this, _)).right.map{
          case (claimsWrapped, cmd) =>
            claimsWrapped.map(claims => iTr.updatedLoginCtx(iTr.getLoginCtx.get.withClaims(claims)))
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

  private[cmd] val COMMAND_NAME = "changePswd"

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
