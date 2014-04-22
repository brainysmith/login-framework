package com.identityblitz.login.error

import com.identityblitz.login.authn.cmd.Command
import com.identityblitz.login.LoggingUtils._

/**
  */
case class CommandException(cmd: Command, error: LoginError) extends LoginException(error.name, null){

  require(cmd != null, {
    val err = "Command can't be null"
    logger.error(err)
    err
  })

  require(error != null, {
    val err = "Error can't be null"
    logger.error(err)
    err
  })

  override def toString: String = new StringBuilder("CommandException(command=")
    .append(cmd)
    .append(", error=").append(error)
    .append(")")
    .toString()
}
