package com.identityblitz.login

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.LoginFramework._
import com.identityblitz.login.FlowAttrName._
import com.identityblitz.login.error.LoginException
import com.identityblitz.login.session.LoginSession.removeLs

/**
 */
trait LogoutFlow extends Handler with WithStart with FlowTools {

  @inline protected def callBack(f: String => Unit)(implicit iTr: InboundTransport, oTr: OutboundTransport) =
    iTr.getAttribute(CALLBACK_URI_NAME).fold[Unit] {
      val err = s"Parameter $CALLBACK_URI_NAME not found in the inbound transport"
      logger.error(err)
      throw new LoginException(err)
    }{f}


  final def start(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    callBack { cbUrl =>
      if(logger.isDebugEnabled) {
        logger.debug("Starting a logout flow with incoming parameters: callback_uri = {}", cbUrl)
      }
      onStart
    }
  }

  final protected def endWithSuccess(implicit iTr: InboundTransport, oTr: OutboundTransport) = {

    callBack { cbUrl =>
      if(logger.isDebugEnabled) {
        logger.debug("The logout flow is completed successfully. Returns to the callback_uri: {}", cbUrl)
      }

      removeLs

      crackCallbackUrl(cbUrl) match {
        case ("forward", u) => iTr.forward(u)
        case ("redirect", u) => oTr.redirect(u)
      }
    }
  }

  protected def onStart(implicit iTr: InboundTransport, oTr: OutboundTransport)

}

object LogoutFlow {

  def apply(options: Map[String, String]): LogoutFlow = if (options.contains("class")) {
    Handler(options)
  } else {
    Handler(options + ("class" -> classOf[DefaultLogoutFlow].getName))
  }

}

class DefaultLogoutFlow(val options: Map[String, String]) extends LogoutFlow {

  override def onStart(implicit req: InboundTransport, resp: OutboundTransport): Unit = endWithSuccess

}
