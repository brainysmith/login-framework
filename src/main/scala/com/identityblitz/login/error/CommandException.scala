package com.identityblitz.login.error

import com.identityblitz.login.authn.cmd.Command
import com.identityblitz.login.LoggingUtils._

/**
  */
case class CommandException(cmd: Command, errorKey: String) extends LoginException(errorKey, null){

  require(cmd != null, {
    val err = "Command can't be null"
    logger.error(err)
    err
  })

  require(errorKey != null, {
    val err = "Error key can't be null"
    logger.error(err)
    err
  })

}

object CommandException {

  def apply(cmd: Command, builtInError: BuiltInError) = new CommandException(cmd, builtInError.getKey)

}
