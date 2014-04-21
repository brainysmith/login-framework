package com.identityblitz.login

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.LoggingUtils._
import scala.annotation.implicitNotFound
import com.identityblitz.login.error.LoginException
import com.identityblitz.login.FlowAttrName._
import com.identityblitz.login.Conf.methods

/**
 * Defines a flow of the login process.
 * The implementation must be a singleton.
 */
@implicitNotFound("No implicit inbound or outbound found.")
abstract class LoginFlow extends Handler {

  private val defaultAuthnMethod = methods.get("default").map(_._2.name)

  /**
   * Starts a new authentication method. If login context (LC) is not found it will be created.
   * Call the method [[com.identityblitz.login.authn.method.AuthnMethod.start]] on the according instance of the
   * authentication method.
   *
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   * @throws LoginException if:
   *         <ul>
   *            <li>a default authentication method not specified in the configuration;</li>
   *            <li>any mandatory attributes is not specified in the inbound transport;</li>
   *            <li>the specified authentication method to start is not configured.</li>
   *         </ul>
   */
  final def start(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    if (iTr.getLoginCtx.isEmpty) {
      logger.trace("Starting a new login flow")
      val callbackUri = iTr.getAttribute(CALLBACK_URI_NAME).getOrElse({
        logger.error("Parameter {} not found in the inbound transport", CALLBACK_URI_NAME)
        throw new LoginException(s"Parameter $CALLBACK_URI_NAME not found in the inbound transport")
      }).asInstanceOf[String]
      iTr.updatedLoginCtx(LoginContext(callbackUri))
    }

    val method = iTr.getAttribute(AUTHN_METHOD_NAME).orElse(defaultAuthnMethod).getOrElse({
      logger.error("A default login method not specified in the configuration. To fix this fix add a parameter " +
        "'default = true' to an one authentication method")
      throw new LoginException("A default login method not specified in the configuration. To fix this fix add a" +
        " parameter 'default = true' to an one authentication method")
    }).asInstanceOf[String]

    logger.debug("Starting a new authentication method: {}", method)
    methods.get(method).map(_._1.start).orElse({
      logger.error("The specified authentication method [{}] is not configured. Configured methods: {}",
        method, methods.keySet)
      throw new LoginException(s"the specified authentication method [$method] is not configured. Configured " +
        s"methods: ${methods.keySet}")
    })
  }

  /**
   * Successfully completed the current authentication method and call the method [[nextForSuccess]] to define the next
   * point of the login flow.
   *
   * @param method - successfully completed authentication method
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  final def success(method: String)(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    val lc = iTr.getLoginCtx.get
    logger.trace("An authentication method {} has been completed successfully. Getting a nex point ...", method)
    lc.asInstanceOf[LoginContextImpl].addCompletedMethod(method)
    iTr.updatedLoginCtx(lc) //todo: temporary
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
  final def fail(method: String, cause: String)(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    implicit val lc = iTr.getLoginCtx.get
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
    logger.debug("The login flow complete successfully redirect to the following callback url: {}",
      iTr.getLoginCtx.get.callbackUri)
    //todo: add the result of the login flow
    oTr.redirect(iTr.getLoginCtx.get.callbackUri)
  }

  /**
   * Completes the login flow and redirect to callback uri with specified error.
   *
   * @param cause - string with error cause
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  final protected def endWithError(cause: String)(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    logger.debug("The login flow complete with error [{}] redirect to the following callback url: {}",
      cause, iTr.getLoginCtx.get.callbackUri)
    //todo: add the error to the result
    oTr.redirect(iTr.getLoginCtx.get.callbackUri)
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
  protected def nextForFail(cause: String)(implicit iTr: InboundTransport, oTr: OutboundTransport)
}

object LoginFlow {

  /*def apply() = Conf.loginFlow*/

}

private[login] object BuiltInLoginFlow extends LoginFlow {

  override protected def nextForFail(cause: String)(implicit iTr: InboundTransport, oTr: OutboundTransport): Unit = {
    endWithError(cause)
  }

  override protected def nextForSuccess(implicit iTr: InboundTransport, oTr: OutboundTransport): Unit = endWithSuccess
}
