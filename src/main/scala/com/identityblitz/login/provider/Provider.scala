package com.identityblitz.login.provider

import com.identityblitz.login.error.LoginError
import scala.util.Try
import com.identityblitz.json.JObj
import com.identityblitz.login.cmd.Command
import com.identityblitz.login.{WithName, Handler}

trait Provider extends Handler with WithName

trait WithBind {
  this: Provider =>

  def bind(data: Map[String, String]): Either[LoginError, (JObj, Option[Command])]

}

trait WithAttributes {
  this: Provider =>

  def populate(data: Map[String, String]): Try[JObj]

}

trait WithChangePassword {
  this: Provider =>

  def changePswd(userId: String, curPswd: String, newPswd: String): Either[LoginError, (JObj, Option[Command])]

}