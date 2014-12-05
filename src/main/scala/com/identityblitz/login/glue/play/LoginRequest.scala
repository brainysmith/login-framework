package com.identityblitz.login.glue.play

import com.identityblitz.login.{RelyingParty, FlowAttrName}
import com.identityblitz.login.LoginFramework.logger

trait LoginRequest {

  self =>

  def cb: String

  def rp: RelyingParty

  def am: Option[String]

  def toMap = {
    val mMap = scala.collection.mutable.HashMap[String, String](FlowAttrName.CALLBACK_URI_NAME -> this.cb)
    this.am.foreach(method => mMap += (FlowAttrName.AUTHN_METHOD_NAME -> method))
    mMap += (FlowAttrName.RELYING_PARTY -> rp.asString())
    mMap.toMap
  }

}

object LoginRequest {

  implicit val implicits = scala.language.implicitConversions
  implicit val refCalls = scala.language.reflectiveCalls
  implicit val postOps =  scala.language.postfixOps

  private case class LoginRequestImpl(cb: String, rp: RelyingParty, am: Option[String]) extends LoginRequest

  /**
   * Login request builder
   */
  abstract class READY
  abstract class NOT_READY

  class LoginRequestBuilder[CB, RP](val callbackUri: String, val relyingParty: RelyingParty, val authnMethod: Option[String]) {

    def withCallbackUri(cb: String) = {
      require(cb != null && !cb.trim.isEmpty, {
        val err = "callback uri can't be null"
        logger.error(err)
        err
      })
      new LoginRequestBuilder[READY, RP](cb, relyingParty, authnMethod)
    }

    def withRelyingParty(rp: RelyingParty) = new LoginRequestBuilder[CB, READY](callbackUri, rp, authnMethod)

    def withAuthnMethod(am: String) = {
      require(am != null && !am.trim.isEmpty, {
        val err = "authentication method can't be null"
        logger.error(err)
        err
      })
      new LoginRequestBuilder[CB, RP](callbackUri, relyingParty, Some(am))
    }

  }

  implicit def enableBuild(b: LoginRequestBuilder[READY, READY]) = new {
    def build(): LoginRequest = LoginRequestImpl(b.callbackUri, b.relyingParty, b.authnMethod)
  }

  def lrBuilder = new LoginRequestBuilder[NOT_READY, NOT_READY](null, null, None)

}
