package com.identityblitz.login.glue.play

import com.identityblitz.login.LoginFramework.logger
import com.identityblitz.login.FlowAttrName

/**
 */
trait LogoutRequest {

  def cb: String

  def toMap = Map(FlowAttrName.CALLBACK_URI_NAME -> this.cb)

}

object LogoutRequest {

  implicit val implicits = scala.language.implicitConversions
  implicit val refCalls = scala.language.reflectiveCalls
  implicit val postOps =  scala.language.postfixOps

  private case class LogoutRequestImpl(cb: String) extends LogoutRequest

  /**
   * Login request builder
   */
  abstract class READY
  abstract class NOT_READY

  class LogoutRequestBuilder[CB](val callbackUri: String) {

    def withCallbackUri(cb: String) = {
      require(cb != null && !cb.trim.isEmpty, {
        val err = "callback uri can't be null"
        logger.error(err)
        err
      })
      new LogoutRequestBuilder[READY](cb)
    }

  }

  implicit def enableBuild(b: LogoutRequestBuilder[READY]) = new {
    def build(): LogoutRequest = LogoutRequestImpl(b.callbackUri)
  }

  def builder = new LogoutRequestBuilder[NOT_READY](null)

}
