package com.identityblitz.login.glue.play

import play.api.mvc.Request
import com.identityblitz.login.FlowAttrName._
import com.identityblitz.login.LoggingUtils._
import com.identityblitz.login.transport.{JsonResponse, RedirectResponse, CommandResponse}
import scala.Some

/**
 */
object CommandUtil {

  def extractResponse[A](req: Request[A]): JsonResponse = {
    val params = req.tags
    (params.get(COMMAND), params.get(COMMAND_NAME), params.get(REDIRECT)) match {
      case (_, _, Some(location)) => 
        RedirectResponse(location)
      case (Some(command), Some(command_name), None) =>
        CommandResponse(command, command_name, params.get(ERROR))
      case (c @ _, n @ _, r @ _) =>
        val err = s"Can't extract command response. Neccessary parameters is not found. Request params: $params"
        logger.error(err)
        throw new IllegalStateException(err)        
    }
  } 
  
}
