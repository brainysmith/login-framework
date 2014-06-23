package com.identityblitz.login

import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.LoginFramework.logger
import scala.annotation.implicitNotFound
import com.identityblitz.login.error.{BuiltInErrors, LoginError, LoginException}
import com.identityblitz.login.FlowAttrName._
import com.identityblitz.login.LoginContext._
import java.net.URLEncoder
import com.identityblitz.login.method.AuthnMethod
import scala.collection.immutable.TreeMap
import com.identityblitz.login.session.LoginSession
import com.identityblitz.login.session.LoginSession.LoginSessionBuilder

/**
 * Defines a flow of the login process.
 * The implementation must be a singleton.
 */
@implicitNotFound("No implicit inbound or outbound found.")
trait LoginFlow extends Handler with WithStart with FlowTools {

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
      logger.trace("Starting a new login flow with incoming parameters: callback_uri = {}",
        iTr.getAttribute(CALLBACK_URI_NAME))
    }

    iTr.updatedLoginCtx(iTr.getAttribute(CALLBACK_URI_NAME).map(cb => {
      lcBuilder.withCallbackUri(cb)
        .build()
    }).fold[Option[LoginContext]]{
      logger.error("Parameter {} not found in the inbound transport", CALLBACK_URI_NAME)
      throw new LoginException(s"Parameter $CALLBACK_URI_NAME not found in the inbound transport")
    }(lc => Some(lc)))

    onStart
  }

  /**
   * Successfully completes the current authentication method and call the method [[onSuccess]] to define the next
   * point of the login flow.
   *
   * @param method - successfully completed authentication method
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  final def success(method: String)(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    logger.trace("An authentication method {} has been completed successfully. Getting a nex point ...", method)
    iTr.updatedLoginCtx(iTr.getLoginCtx.map(_ addMethod method).orElse({
      val err = "Login context not found."
      logger.error(err)
      throw new IllegalStateException(err)
    }))
    onSuccess(method)
  }

  /**
   * Not successfully completes the current authentication method and call the method [[onFail]] to define the next point
   * of the login flow.
   *
   * @param method - not successfully completed authentication method
   * @param cause - string with error cause
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  final def fail(method: String, cause: LoginError)(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    logger.trace("An authentication method '{}' has been completed not successfully. Getting a nex point ...", method)
    onFail(method, cause)
  }


  /**
   * Completes the login flow and returns to callback uri with successfully result.
   *
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  final protected def endWithSuccess(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    import LoginSession.{getLs, updateLs, lsBuilder, READY}
    val lc = iTr.getLoginCtx.get
    if (logger.isDebugEnabled)
      logger.debug("The login flow is completed successfully [lc = {}].", lc.asString)

    /** create a login session **/
    val ls = getLs.fold[LoginSessionBuilder[_, _, READY]](lsBuilder)(ls => lsBuilder(ls))
      .withClaims(lc.claims)
      .withMethods(lc.completedMethods: _*)
      .build()
    updateLs(ls)

    /** remove the current login context **/
    iTr.updatedLoginCtx(None)

    /** call back **/
    val cbUrl = lc.callbackUri
    crackCallbackUrl(cbUrl) match {
      case ("forward", u) =>
        iTr.setAttribute(FlowAttrName.LOGIN_SESSION, ls.asString)
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

    /** remove the current login context **/
    iTr.updatedLoginCtx(None)

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


  protected def onStart(implicit iTr: InboundTransport, oTr: OutboundTransport)

  /**
   * Calls when current authentication method is completed successfully.
   * The method must redirect or forward request to the next point of the login flow.
   * For complete the current login flow and return result to consumer call [[endWithSuccess]] or [[endWithError]].
   *
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  protected def onSuccess(method: String)(implicit iTr: InboundTransport, oTr: OutboundTransport)

  /**
   * Calls when current authentication method is completed not successfully.
   * The method must redirect or forward request to the next point of the login flow.
   * For complete the current login flow and return result to consumer call [[endWithSuccess]] or [[endWithError]].
   *
   * @param cause - string with error cause
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  protected def onFail(method: String, cause: LoginError)(implicit iTr: InboundTransport, oTr: OutboundTransport)
}

object LoginFlow {

  def apply(options: Map[String, String]): LoginFlow = if (options.contains("class")) {
    Handler(options)
  } else {
    Handler(options + ("class" -> classOf[DefaultLoginFlow].getName))
  }

}



class DefaultLoginFlow(val options: Map[String, String]) extends LoginFlow {

  protected object MethodFlag extends Enumeration {
    type MethodFlag = Value
    val sufficient, required, optional = Value
  }

  import MethodFlag._

  protected case class MethodMeta(method: AuthnMethod, flag: MethodFlag)

  protected object MethodMeta {
    def apply(raw: String): MethodMeta = {
      val parts = raw.split(":").map(_.trim).filter(!_.isEmpty)
      val m = LoginFramework.methods(parts(0))
      val f = if (parts.length > 1) MethodFlag.withName(parts(1)) else MethodFlag.required
      MethodMeta(m, f)
    }
  }

  protected case class Step(n: Int, methods: Array[MethodMeta])

  protected object Step {
    /** attention about t._1.replaceAll("\"",""): i don't no why but the number of step contains double quote **/
    def apply(t: (String, String)): Step = Step(t._1.replaceAll("\"","").toInt,
      t._2.split(",").map(_.trim).filter(!_.isEmpty).map(MethodMeta(_)))
  }


  protected lazy val steps = TreeMap(options.toList.filter(_._1.startsWith("steps"))
    .map(t => t._1.stripPrefix("steps.") -> t._2)
    .map(Step(_))
    .map(step => step.n -> step): _*)

  protected lazy val lastStep = steps.last._2

  protected lazy val requiredSteps = steps.values
    .filter(_.methods.exists(mm => mm.flag == MethodFlag.required))
    .map(_.n)

  protected lazy val methodIndex = steps.values.map(step => {
    step.methods.map(mm => mm.method.name -> (step -> mm))
  }).flatten.toMap

  protected def isLastStep(step: Step) = step.n == lastStep.n

  protected def completedSteps(implicit iTr: InboundTransport, oTr: OutboundTransport) = iTr.getLoginCtx.get.completedMethods.map(methodIndex(_)._1.n).toSet

  protected def flowResult(implicit iTr: InboundTransport, oTr: OutboundTransport) = !requiredSteps.isEmpty && requiredSteps.forall(completedSteps contains)

  protected def nextStep(currentStep: Step)(implicit iTr: InboundTransport, oTr: OutboundTransport) {
    steps.from(currentStep.n).tail.headOption.fold{
      if (logger.isTraceEnabled)
        logger.trace("It was the last step: [{}]", currentStep)
      flowResult match {
        case false => endWithError(BuiltInErrors.FLOW_NOT_COMPLETED_PROPERLY)
        case true => endWithSuccess
      }
    }{case (i, nextStep) =>
      if (logger.isTraceEnabled)
        logger.trace("Go to the next step: [{}]", nextStep)
      nextStep.methods.head.method.start
    }
  }

  override protected def onStart(implicit iTr: InboundTransport, oTr: OutboundTransport): Unit = {
    steps.head._2.methods.head.method.start
  }

  override protected def onFail(method: String, cause: LoginError)
                               (implicit iTr: InboundTransport, oTr: OutboundTransport): Unit = {
    val (step, methodMeta) = methodIndex(method)
    methodMeta.flag match {
      case MethodFlag.required  => endWithError(cause)
      case _ => nextStep(step)
    }
  }

  override protected def onSuccess(method: String)
                                  (implicit iTr: InboundTransport, oTr: OutboundTransport): Unit = {
    val (step, methodMeta) = methodIndex(method)
    methodMeta.flag match {
      case MethodFlag.sufficient => endWithSuccess
      case _ => nextStep(step)
    }
  }
}
