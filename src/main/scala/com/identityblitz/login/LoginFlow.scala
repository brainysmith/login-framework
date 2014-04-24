package com.identityblitz.login

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.LoggingUtils._
import scala.annotation.implicitNotFound
import com.identityblitz.login.error.{LoginError, LoginException}
import com.identityblitz.login.FlowAttrName._
import com.identityblitz.login.Conf.methods
import com.identityblitz.login.LoginContext._

/**
 * Defines a flow of the login process.
 * The implementation must be a singleton.
 */
@implicitNotFound("No implicit inbound or outbound found.")
abstract class LoginFlow extends Handler {

  private val defaultAuthnMethod = methods.get("default").map(_._2.name).orElse{
    logger.warn("A default login method not specified in the configuration. To fix this fix add a parameter " +
      "'default = true' to an one authentication method")
    None
  }

  /**
   * Starts a new authentication method. If login context (LC) is not found it will be created.
   * Calls the method [[com.identityblitz.login.authn.method.AuthnMethod.start]] on the according instance of the
   * authentication method.
   *
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   * @throws LoginException if:
   *         <ul>
   *            <li>any mandatory attributes is not specified in the inbound transport;</li>
   *            <li>no authentication method to start processing of an incoming request..</li>
   *         </ul>
   */
  final def start(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    if(logger.isTraceEnabled) {
      logger.trace("Starting a new login flow with incoming parameters callback_uri = {}, authn_method = {}",
        iTr.getAttribute(CALLBACK_URI_NAME), iTr.getAttribute(AUTHN_METHOD_NAME))
    }

    iTr.updatedLoginCtx(iTr.getAttribute(CALLBACK_URI_NAME).map(lcBuilder withCallbackUri _ build).fold[LoginContext]{
      logger.error("Parameter {} not found in the inbound transport", CALLBACK_URI_NAME)
      throw new LoginException(s"Parameter $CALLBACK_URI_NAME not found in the inbound transport")
    }(lc => lc))

    iTr.getAttribute(AUTHN_METHOD_NAME).orElse(defaultAuthnMethod).flatMap(methods.get).orElse{
      logger.error("Found no authentication methods to start processing an incoming request")
      throw new LoginException("Found no authentication methods to start processing an incoming request")}
      .map(_._1.start)
  }

  /**
   * Successfully completes the current authentication method and call the method [[nextForSuccess]] to define the next
   * point of the login flow.
   *
   * @param method - successfully completed authentication method
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  final def success(method: String)(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    logger.trace("An authentication method {} has been completed successfully. Getting a nex point ...", method)
    iTr.updatedLoginCtx(iTr.getLoginCtx.fold[LoginContext]{
      throw new IllegalStateException("Login context not found.")
    }(_ addMethod method))
    nextForSuccess
  }

  /**
   * Successfully completes the current authentication method and call the method [[nextForFail]] to define the next point
   * of the login flow.
   *
   * @param method - not successfully completed authentication method
   * @param cause - string with error cause
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  final def fail(method: String, cause: LoginError)(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    logger.trace("An authentication method {} has been completed not successfully. Getting a nex point ...", method)
    nextForFail(cause)
  }


  /**
   * Completes the login flow and redirect to callback uri with successfully result.
   *
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  final protected def endWithSuccess(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    val cbUrl = iTr.getLoginCtx.get.callbackUri
    logger.debug("The login flow complete successfully redirect to the following callback url: {}", cbUrl)
    //todo: add the result of the login flow
    oTr.redirect(cbUrl)
  }

  /**
   * Completes the login flow and redirect to callback uri with specified error.
   *
   * @param cause - string with error cause
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  final protected def endWithError(cause: LoginError)(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    val cbUrl = iTr.getLoginCtx.get.callbackUri
    logger.debug("The login flow complete with error [{}] redirect to the following callback url: {}", cause, cbUrl)
    //todo: add the error to the result
    oTr.redirect(cbUrl)
  }

  /**
   * Calls when current authentication method is completed successfully.
   * The method must redirect or forward request to the next point of the login flow.
   * For complete the current login flow and return result to consumer call [[endWithSuccess]] or [[endWithError]].
   *
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  protected def nextForSuccess(implicit iTr: InboundTransport, oTr: OutboundTransport)

  /**
   * Calls when current authentication method is completed not successfully.
   * The method must redirect or forward request to the next point of the login flow.
   * For complete the current login flow and return result to consumer call [[endWithSuccess]] or [[endWithError]].
   *
   * @param cause - string with error cause
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  protected def nextForFail(cause: LoginError)(implicit iTr: InboundTransport, oTr: OutboundTransport)
}

object LoginFlow {

  /*def apply() = Conf.loginFlow*/

}

private[login] object BuiltInLoginFlow extends LoginFlow {

  override protected def nextForFail(cause: LoginError)(implicit iTr: InboundTransport, oTr: OutboundTransport): Unit = {
    endWithError(cause)
  }

  override protected def nextForSuccess(implicit iTr: InboundTransport, oTr: OutboundTransport): Unit = endWithSuccess
}
