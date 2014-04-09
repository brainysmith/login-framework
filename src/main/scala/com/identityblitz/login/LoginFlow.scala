package com.identityblitz.login

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.LoggingUtils._
import com.identityblitz.login.builtin.BuiltInLoginFlow
import com.identityblitz.login.authn.{AuthnMethod, AuthnMethodMeta}
import scala.annotation.implicitNotFound
import com.identityblitz.login.error.LoginException
import com.identityblitz.login.FlowAttrName._

/**
 * Defines a flow of the login process.
 * The implementation must be a singleton.
 */
@implicitNotFound("No implicit inbound or outbound found.")
abstract class LoginFlow {

  private val authnMethodsMeta = 
    (for ((clazz, params) <- Conf.authnMethods) yield AuthnMethodMeta(clazz, params)).toArray

  if (authnMethodsMeta.isEmpty) {
    logger.warn("authentication will not work: there aren't authentication methods found in the configuration")
    throw new RuntimeException("authentication will not work: there aren't authentication methods found in the configuration")
  } else {
    logger.debug("the following authentication methods has been read from the configuration: {}", authnMethodsMeta)
  }

  protected val authnMethodsMap = authnMethodsMeta.foldLeft[scala.collection.mutable.Map[String, AuthnMethod]](
      scala.collection.mutable.Map())(
      (res, meta) => {
        val instance = meta.newInstance
        if (meta.default) {
          res += ("default" -> instance)
        }
        res += (instance.name -> instance)
      }
    )

  protected def crtLc(implicit iTr: InboundTransport) = {
    val method = Option(iTr.getAttribute(AUTHN_METHOD_NAME)).orElse(authnMethodsMap.get("default")).orElse({
      logger.error("A default login method not specified in the configuration. To fix this fix add a parameter " +
        "'default = true' to an one authentication method")
      throw new LoginException("A default login method not specified in the configuration. To fix this fix add a" +
        " parameter 'default = true' to an one authentication method")
    }).get

    val callbackUri = Option(iTr.getAttribute(CALLBACK_URI_NAME)).orElse({
      logger.error("parameter {} not found in the inbound transport", CALLBACK_URI_NAME)
      throw new LoginException(s"parameter $CALLBACK_URI_NAME not found in the inbound transport")
    }).get

/*    LoginContext(method, callbackUri)*/
  }

  def start(implicit iTr: InboundTransport, oTr: OutboundTransport) =
    (Option(iTr), Option(oTr)) match {
      case (Some(_), Some(_)) =>
        logger.trace("starting a new login flow")
/*        iTr.getLoginContext.
        authnMethodsMap.get(method).map(_.start).orElse({
          logger.error("the specified authentication method [{}] is not configured", method)
          throw new LoginException(s"the specified authentication method [$method] is not configured")
        })*/
      case _ =>
        logger.error("some of the input parameter is null [iTr = {}, oTr = {}]", iTr, oTr)
        throw new LoginException(s"some of the input parameter is null [iTr = $iTr, oTr = $oTr]")
    }

  /**
   * Defines a next step of the login process to redirect.
   * @return [[Some]] of a location to redirect. If result is [[None]] depending on the current login status:
   *        <ul>
   *          <li>redirect to the complete end point with resulting of the login process if the status is [[LoginStatus.SUCCESS]];</li>
   *          <li>redirect to the complete end point with error if the status is [[LoginStatus.FAIL]];</li>
   *          <li>do nothing if the status is [[LoginStatus.PROCESSING]].</li>
   *        </ul>
   */
  def next(implicit iTr: InboundTransport, oTr: OutboundTransport): Option[String] = ???
/*    (Option(iTr), Option(oTr)) match {
    case (Some(_), Some(_)) =>
      logger.trace("getting the next authentication step")
      authnMethodsMap.get(method).map(_.start).orElse({
        logger.error("the specified authentication method [{}] is not configured", method)
        throw new LoginException(s"the specified authentication method [$method] is not configured")
      })
    case _ =>
      logger.error("some of the input parameter is null [iTr = {}, oTr = {}]", iTr, oTr)
      throw new LoginException(s"some of the input parameter is null [iTr = $iTr, oTr = $oTr]")
  }  */
}

object LoginFlow {

  private val loginFlow = Conf.loginFlow.fold[LoginFlow]({
    logger.debug("will use the build in login flow: {}", BuiltInLoginFlow.getClass.getSimpleName)
    BuiltInLoginFlow
  })(className => {
    logger.debug("find in the configuration a custom login flow [class = {}]", className)
    this.getClass.getClassLoader.loadClass(className).asInstanceOf[LoginFlow]
  })

  def apply() = loginFlow
}
