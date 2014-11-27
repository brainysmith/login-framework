package com.identityblitz.login

import com.identityblitz.login.service.ServiceProvider
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
      val lcb = lcBuilder
        .withCallbackUri(cb)
        .withSessionKey(LoginFlow.cryptoSrv.generateRandomBytes(32))
      iTr.getAttribute(RELYING_PARTY).fold(lcb.build())(lcb.withRelayingParty(_).build())
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
   * Not successfully completes the current authentication method and calls the method [[onFail]] to define next point
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
   * Skips the current step and calls the method [[onSkip]] to jump on next point of the login flow.
   * @param method - the current method
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  final def skip(method: String)(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    logger.trace("An authentication method '{}' has been skipped.", method)
    onSkip(method)
  }

  /**
   * Switch the current method and calls the method [[onSwitchTo]] to jump to alternative method of the same point of the login flow.
   * @param method - the new method
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  final def switchTo(method: String)(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    logger.trace("An authentication method has been switched to {}.", method)
    onSwitchTo(method)
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
   * The method must redirect or forward request to next point of the login flow.
   * For complete the current login flow and return result to consumer call [[endWithSuccess]] or [[endWithError]].
   *
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  protected def onSuccess(method: String)(implicit iTr: InboundTransport, oTr: OutboundTransport)

  /**
   * Calls when current authentication method is completed not successfully.
   * The method must redirect or forward request to next point of the login flow.
   * For complete the current login flow and return result to consumer call [[endWithSuccess]] or [[endWithError]].
   *
   * @param cause - string with error cause
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  protected def onFail(method: String, cause: LoginError)(implicit iTr: InboundTransport, oTr: OutboundTransport)

  /**
   * Calls when the current authentication method and respectively the current step must be skipped. The method must
   * redirect or forward request to next step of the login flow.
   *
   * @param method - the current method being skipped
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  protected def onSkip(method: String)(implicit iTr: InboundTransport, oTr: OutboundTransport)

  /**
   * Calls when the current authentication method must be switched to an alternative method in the same step. The method must
   * redirect or forward request to start a new method of the same point of the login flow.
   *
   * @param method - the new method
   * @param iTr - inbound transport
   * @param oTr - outbound transport
   */
  protected def onSwitchTo(method: String)(implicit iTr: InboundTransport, oTr: OutboundTransport)

}

object LoginFlow {

  val cryptoSrv = ServiceProvider.cryptoService

  def apply(options: Map[String, String]): LoginFlow = if (options.contains("class")) {
    Handler(options)
  } else {
    Handler(options + ("class" -> classOf[DefaultLoginFlow].getName))
  }

}



class DefaultLoginFlow(val config: Map[String, String]) extends LoginFlow {

  protected object StepOption extends Enumeration {
    type StepOption = Value
    val sufficient, required, optional = Value
  }

  import StepOption._

  protected case class Step(n: Int,
                            alternateMethods: Array[AuthnMethod],
                            option: StepOption)


  private lazy val steps = configSteps

  private def configSteps = {
    val processed = config.toSeq.filter(_._1.startsWith("steps.")).sortBy(_._1).map(_._2).map(_.split(":") match {
      case Array(opt, mtds) => mtds.split(",") match {
        case Array() => Left(Seq(s"Authentication methods not specified"))
        case _@ams =>
          val resolved = ams.map(name => LoginFramework.methods.get(name)
            .toRight(s"Authentication method '$name' not found"))
          val errors = resolved.collect { case Left(e) => e}.foldLeft(Seq[String]())(_ :+ _)
          if (errors.isEmpty) Right((StepOption.withName(opt), resolved.collect { case Right(v) => v})) else Left(errors)
      }
      case _@item => Left(Seq(s"Got wrong step item '$item'"))
    })

    if(processed.isEmpty)
      throw new IllegalStateException("No steps defined")

    val errors = processed.collect{case Left(e) => e}.fold(Seq())(_ ++ _)
    if(errors.isEmpty) TreeMap(processed.collect{case Right(v) => v}.zipWithIndex.map(p => (p._2, Step(p._2, p._1._2, p._1._1))) :_*)
    else throw new IllegalStateException(errors.mkString(";"))
  }


  protected lazy val lastStep = steps.last._2

  protected lazy val requiredSteps = steps.values.filter(_.option == required).toSet

  protected def isLastStep(step: Step) = step.n == lastStep.n

  protected lazy val methodToStep = steps.toSeq.map(a => a._2.alternateMethods.toSeq.map(m => (m.name, a._2))).flatten.toMap

  protected def currentMethod(implicit iTr: InboundTransport, oTr: OutboundTransport): Option[String] =
    iTr.getLoginCtx.fold {
      val err = "Login context not found."
      logger.error(err)
      throw new IllegalStateException(err)
    }(_.currentMethod)

  protected def completedSteps(implicit iTr: InboundTransport, oTr: OutboundTransport) = iTr.getLoginCtx.get.completedMethods.map(methodToStep(_)).toSet

  protected def flowResult(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    val completed = completedSteps
    requiredSteps.nonEmpty && requiredSteps.subsetOf(completed)
  }

  protected def nextStep(currentStep: Step)(implicit iTr: InboundTransport, oTr: OutboundTransport) {
    steps.from(currentStep.n).tail.headOption.fold{
      if (logger.isTraceEnabled) logger.trace("It was the last step: [{}]", currentStep)
      flowResult match {
        case false => endWithError(BuiltInErrors.FLOW_NOT_COMPLETED_PROPERLY)
        case true => endWithSuccess
      }
    }{case (i, nextStep) =>
      if (logger.isTraceEnabled) logger.trace("Go to the next step: [{}]", nextStep)
      nextStep.alternateMethods(0).start
    }
  }

  override protected def onStart(implicit iTr: InboundTransport,
                                 oTr: OutboundTransport): Unit = steps.head._2.alternateMethods.head.start

  override protected def onFail(method: String, cause: LoginError)(implicit iTr: InboundTransport,
                                                                   oTr: OutboundTransport): Unit = endWithError(cause)

  override protected def onSuccess(method: String)(implicit iTr: InboundTransport,
                                                   oTr: OutboundTransport): Unit = {
    val step = methodToStep(method)
    if(step.option == sufficient) endWithSuccess else nextStep(step)
  }

  override protected def onSkip(method: String)(implicit iTr: InboundTransport, oTr: OutboundTransport): Unit = {
    val step = methodToStep(method)
    if(step.option == required)
      throw new IllegalStateException(s"Authentication method '$method' on step with option [required] can not be skipped.")
    nextStep(step)
  }

  override protected def onSwitchTo(method: String)(implicit iTr: InboundTransport, oTr: OutboundTransport) = {
    val currentStep = currentMethod.fold {
      val err = s"Authentication process can not be switched to method '$method' hence it is not started"
      logger.error(err)
      throw new IllegalStateException(err)
    }(methodToStep(_))

    val suitableMethods = currentStep.alternateMethods.filter(_.name == method)

    if(suitableMethods.isEmpty){
      val err = s"Authentication process can not be switched to method '$method' that is not belong to the current step '${currentStep.n}'"
      logger.error(err)
      throw new IllegalStateException(err)
    }
    suitableMethods(0).start
  }

  override def options: Map[String, String] = throw new UnsupportedOperationException

  /*  protected case class MethodMeta(method: AuthnMethod, level: Int = 1) {
      if(level < 1 && level > 15)
        throw new IllegalArgumentException("Authentication level has wrong value: " + level)
    }

    protected object MethodMeta {
      def apply(raw: String): MethodMeta = {
        val parts = raw.split(":").map(_.trim).filter(!_.isEmpty)
        val m = LoginFramework.methods(parts(0))
        val f = if (parts.length > 1) MethodFlag.withName(parts(1)) else MethodFlag.required
        MethodMeta(m, f)
      }
    }*/
  /*protected object Step {
    /** attention about t._1.replaceAll("\"",""): i don't no why but the number of step contains double quote **/
    def apply(t: (String, String)): Step = Step(t._1.replaceAll("\"","").toInt,
      t._2.split(",").map(_.trim).filter(!_.isEmpty).map(MethodMeta(_)))
  }*/

  //protected def flowResult(implicit iTr: InboundTransport, oTr: OutboundTransport) = !requiredSteps.isEmpty && requiredSteps.forall(completedSteps contains)

}
