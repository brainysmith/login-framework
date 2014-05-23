package com.identityblitz.login.provider.flow

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.App._
import scala.annotation.implicitNotFound
import com.identityblitz.login.error.{BuiltInErrors, LoginError, LoginException}
import com.identityblitz.login.FlowAttrName._
import com.identityblitz.login.LoginContext._
import java.net.URLEncoder
import com.identityblitz.scs.SCSService
import com.identityblitz.login.provider.{WithStart, Provider}
import com.identityblitz.login.{AuthnMethod, FlowAttrName, LoginContext}

/**
 * Defines a flow of the login process.
 * The implementation must be a singleton.
 */
@implicitNotFound("No implicit inbound or outbound found.")
trait LoginFlowProvider extends Provider with WithStart {

  protected val scsService = new SCSService()
  scsService.init(false)


  protected val defaultAuthnMethod = methods.find(_._2.default).map(_._1).orElse{
    logger.warn("A default login method not specified in the configuration. To fix this fix add a parameter " +
      "'default = true' to an one authentication method")
    None
  }

  /**
   * Starts a new authentication method. If login context (LC) is not found it will be created.
   * Calls the method 'start' on the according instance of the authentication method.
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
      logger.trace("Starting a new login flow with incoming parameters: callback_uri = {}, authn_method = {}",
        iTr.getAttribute(CALLBACK_URI_NAME), iTr.getAttribute(AUTHN_METHOD_NAME))
    }

    val method = iTr.getAttribute(AUTHN_METHOD_NAME).orElse(defaultAuthnMethod).flatMap(methods.get).getOrElse{
      logger.error("Found no authentication methods to start processing an incoming request")
      throw new LoginException("Found no authentication methods to start processing an incoming request")
    }

    iTr.updatedLoginCtx(iTr.getAttribute(CALLBACK_URI_NAME).map(cb => {
      lcBuilder.withCallbackUri(cb)
        .withMethod(method.name)
        .build()
    }).fold[LoginContext]{
      logger.error("Parameter {} not found in the inbound transport", CALLBACK_URI_NAME)
      throw new LoginException(s"Parameter $CALLBACK_URI_NAME not found in the inbound transport")
    }(lc => lc))

    method.passiveProviders match {
      case Nil => method.activeProvider.fold{
        val err = s"Can't start login. Authentication method [$method] doesn't have active provider"
        logger.error(err)
        endWithError(BuiltInErrors.INTERNAL)
        }{_.start}
      case l =>
        val pp = l.head
        iTr.setAttribute(PASSIVE_PROVIDER_ATTR_NAME, pp.name)
        if (logger.isDebugEnabled)
          logger.debug("Performing '{}' authentication method by '{}' passive provider.", method.name, pp.name)
        pp.DO
    }
  }

  /**
   * Successfully completes the current authentication method and call the method [[nextForSuccess]] to define the next
   * point of the login flow.
   *
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  final def success(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    val lc = iTr.getLoginCtx.get
    logger.trace("An authentication method {} has been completed successfully. Getting a nex point ...", lc.method)
    iTr.updatedLoginCtx(iTr.getLoginCtx.fold[LoginContext]{
      throw new IllegalStateException("Login context not found.")
    }(_ addMethod lc.method))
    nextForSuccess(methods(lc.method))
  }

  /**
   * Successfully completes the current authentication method and call the method [[nextForFail]] to define the next point
   * of the login flow.
   *
   * @param cause - string with error cause
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  final def fail(cause: LoginError)(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    val lc = iTr.getLoginCtx.get
    val method = methods(lc.method)
    iTr.getAttribute(PASSIVE_PROVIDER_ATTR_NAME).fold{
      logger.debug("An authentication method {} has been completed not successfully. Getting a nex point ...", method.name)
      nextForFail(method, cause)
    }(ppName => {
      val ind = method.passiveProviders.indexWhere(_.name == ppName)
      if (ind > 0 && ind < (method.passiveProviders.size - 1)) {
        val pp = method.passiveProviders(ind + 1)
        if (logger.isDebugEnabled)
          logger.debug("Performing '{}' authentication method by the next '{}' passive provider.", method.name, pp.name)
        iTr.setAttribute(PASSIVE_PROVIDER_ATTR_NAME, pp.name)
        pp.DO
      } else {
        iTr.removeAttribute(PASSIVE_PROVIDER_ATTR_NAME)
        method.activeProvider.fold(endWithError(cause))(_.start)
      }
    })

  }


  /**
   * Completes the login flow and redirect to callback uri with successfully result.
   *
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  final protected def endWithSuccess(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    val cbUrl = iTr.getLoginCtx.get.callbackUri
    logger.debug("The login flow is completed successfully [lc = {}]. Redirect to the following callback url: {}",
      iTr.toString, cbUrl)

    //todo: create session cookie

    crackCallbackUrl(cbUrl) match {
      case ("forward", u) =>
        iTr.getLoginCtx.map(lc => iTr.setAttribute(FlowAttrName.LOGIN_CONTEXT, lc.asString))
        iTr.forward(u)
      case ("redirect", u) => oTr.redirect(u)
    }
  }

  /**
   * Completes the login flow and redirect to callback uri with specified error.
   *
   * @param cause - string with error cause
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  final protected def endWithError(cause: LoginError)(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    val callbackUri = iTr.getLoginCtx.get.callbackUri
    logger.debug("The login flow complete with error [{}] redirect to the following location: {}", cause, callbackUri)
    crackCallbackUrl(callbackUri) match {
      case ("forward", u) =>
        iTr.setAttribute("error", cause.name)
        iTr.forward(u)
      case ("redirect", u) =>
        val errorParam = URLEncoder.encode("error=" + cause.name, "UTF-8")
        val location = callbackUri + (if (callbackUri.contains("?")) "&" else "?") + errorParam
        oTr.redirect(location)
    }
  }

  /**
   * Calls when current authentication method is completed successfully.
   * The method must redirect or forward request to the next point of the login flow.
   * For complete the current login flow and return result to consumer call [[endWithSuccess]] or [[endWithError]].
   *
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  protected def nextForSuccess(method: AuthnMethod)(implicit iTr: InboundTransport, oTr: OutboundTransport)

  /**
   * Calls when current authentication method is completed not successfully.
   * The method must redirect or forward request to the next point of the login flow.
   * For complete the current login flow and return result to consumer call [[endWithSuccess]] or [[endWithError]].
   *
   * @param cause - string with error cause
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  protected def nextForFail(method: AuthnMethod, cause: LoginError)(implicit iTr: InboundTransport, oTr: OutboundTransport)

  private val crackCallbackUrl = (s: String) => {
    val extractor  = """^fwd:(.*)$""".r
    extractor findFirstIn s match {
      case Some(extractor(url)) => ("forward", url)
      case _ => ("redirect", s)
    }
  }
}



class DefaultLoginFlowProvider(val name:String, val options: Map[String, String]) extends LoginFlowProvider {

  override protected def nextForFail(method: AuthnMethod, cause: LoginError)(implicit iTr: InboundTransport, oTr: OutboundTransport): Unit = {
    endWithError(cause)
  }

  override protected def nextForSuccess(method: AuthnMethod)(implicit iTr: InboundTransport, oTr: OutboundTransport): Unit = endWithSuccess
}
