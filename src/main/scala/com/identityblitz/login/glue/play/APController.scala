package com.identityblitz.login.glue.play

import play.api.mvc._
import scala.concurrent.{Promise, Future}
import play.api.libs.iteratee.Done
import com.identityblitz.scs.glue.play.{SCSEnabledAction, SCSRequest}
import com.identityblitz.login.transport.{RedirectResponse, OutboundTransport, InboundTransport}
import com.identityblitz.login.error.TransportException
import play.api.Play
import com.identityblitz.login._
import play.api.mvc.Results._
import play.api.Play.current
import java.util.regex.Pattern
import com.identityblitz.login.authn.method.AuthnMethod
import play.api.mvc.SimpleResult
import scala.util.{Failure, Success, Try}

/**
 * The Authentication Point (AP) controller is the endpoint for Play applications written on Scala language
 * to launch authentication process. Supported two ways to call these actions:
 * <li>
 *  <ul>by direct call to the appropriate action</ul>
 *  <ul>by request</ul>
 * </li>
 * For the first way it is necessary to specify the parameters described in the table below. The specified parameters
 * must be put into the tags map of the request header [[play.api.mvc.RequestHeader.tags]].
 * <table>
 *  <col width="25%"/>
 *  <col width="50%"/>
 *  <col width="25%"/>
 *  <thead>
 *    <tr><th>Name</th><th>Description</th><th>Mandatory</th></tr>
 *  </thead>
 *  <tbody>
 *    <tr><td>callback_uri</td><td>URI to return to when authentication process completes.</td><td>true</td></tr>
 *    <tr><td>method</td><td>Desired authentication method.</td><td>true</td></tr>
 *  </tbody>
 * </table>
 * For the second way it is necessary to specify the parameters described in the table below.
 * <table>
 *  <col width="25%"/>
 *  <col width="50%"/>
 *  <col width="25%"/>
 *  <thead>
 *    <tr><th>Name</th><th>Description</th><th>Mandatory</th></tr>
 *  </thead>
 *  <tbody>
 *    <tr><td>callback_uri</td><td>URI to return to when authentication process completes.</td><td>true</td></tr>
 *    <tr><td>method</td><td>Desired authentication method.</td><td>true</td></tr>
 *  </tbody>
 * </table>
 */
object APController extends Controller {
  implicit val reflective = scala.language.reflectiveCalls

  val loginPath = "/login/"

  val authMethods: Map[String, AuthnMethod] = Conf.methods.mapValues(_._1)
  val flowEngine = Conf.loginFlow

  /**
   * The entry point action of the AP for request made by HTTP method GET. For this action the specific route
   * must be added to the routes. The route to add is given below.
   * GET  /login/\*handler  com.identityblitz.login.glue.play.APController.doGet(handler)
   * @param handler - name of handler to process incoming request on.
   * @return - response.
   */
  def doGet(handler: String) = SCSEnabledAction.async {
    implicit request => {
      implicit val itr = PlayInboundTransport.apply
      implicit val otr = itr.outboundTransport

      itr.setAttribute(FlowAttrName.HTTP_METHOD, "GET")
      invokeHandler(handler, ifDirectCall(request)) match {
        case Success(_) => otr.result
        case Failure(e) =>
          e.printStackTrace()
          Future.successful(Results.InternalServerError(e.getMessage))
      }
    }
  }

  /**
   * The entry point action of the AP for request made by HTTP method POST. For this action the specific route
   * must be added to the routes. The route to add is given below.
   * POST  /login/\*handler  com.identityblitz.login.glue.play.APController.doPost(handler)
   * @param handler - name of handler to process incoming request on.
   * @return - response.
   */
  def doPost(handler: String) = SCSEnabledAction.async(parse.urlFormEncoded) {
    implicit request => {
      implicit val postParams = request.body
      implicit val itr = PlayInboundTransport.apply
      implicit val otr = itr.outboundTransport

      itr.setAttribute(FlowAttrName.HTTP_METHOD, "POST")
      invokeHandler(handler, ifDirectCall(request)) match {
        case Success(_) => otr.result
        case Failure(e) =>
          e.printStackTrace()
          Future.successful(Results.InternalServerError(e.getMessage))
      }
    }
  }

  private def ifDirectCall(req: RequestHeader): Boolean = !req.path.startsWith(loginPath)

  private def invokeHandler(handler: String, directCall: Boolean)(implicit itr: InboundTransport, otr: OutboundTransport): Try[Unit] = {
    Try[Unit] {
      Call(handler, directCall) match {
        case Call("flow", null, true) => flowEngine.start
        case Call("flow", null, false) =>
          itr.setAttribute(FlowAttrName.CALLBACK_URI_NAME, itr.getParameter(FlowAttrName.CALLBACK_URI_NAME).fold[String]{
            LoggingUtils.logger.error("callback_uri is not specified.")
            throw new IllegalArgumentException("callback_uri is not specified.")
          }(s => s))
          itr.getParameter(FlowAttrName.AUTHN_METHOD_NAME).foreach(itr.setAttribute(FlowAttrName.AUTHN_METHOD_NAME, _))
          flowEngine.start
        case Call(m, "/do", _) => authMethods(m).DO
        case c @ Call(_, _, _) =>
          LoggingUtils.logger.error("Got a wrong call: {}.", c)
          throw new IllegalArgumentException("Got a wrong call: " + c + ".")
      }
    }
  }

  private class Call(private val handler: String, private val directCall: Boolean) {
    private final val pattern: Pattern = Pattern.compile("([^/]+)(/do)?", Pattern.CASE_INSENSITIVE)

    val (method, action) = Option(pattern.matcher(handler)).filter(_.find())
      .fold[(String, String)]{
      LoggingUtils.logger.error("Got wrong handler: {}.", handler)
      throw new Error("Got wrong handler: " + handler + ".")
    }(m => (m.group(1), m.group(2)))
  }

  private object Call {
    def apply(handler: String, directCall: Boolean) = new Call(handler, directCall)
    def unapply(call: Call): Option[(String, String, Boolean)] = {
      Option(call).map(c => (call.method, call.action, call.directCall))
    }
  }

}

/**
 * The action to add another action ability to be forwardable.
 */
object Forwardable extends ActionBuilder[Request] {

  def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[SimpleResult]): Future[SimpleResult] = {
    block(request)
  }

  override protected def composeParser[A](bodyParser: BodyParser[A]): BodyParser[A] = BodyParser("Transient") {
    request =>
      request.tags.get("FORWARDED") map {
        str => Done.apply[Array[Byte], Either[SimpleResult, A]](Right(request.asInstanceOf[Request[A]].body))
      } getOrElse{bodyParser(request)}
  }

}

/**
 * Play inbound transport implementation
 * @param req
 * @tparam A
 */
private class PlayInboundTransport[A](private val req: SCSRequest[A],
                                      private val formParams: Map[String, Seq[String]]) extends InboundTransport {
  self =>

  val outboundTransport = makeOutboundTransport

  @volatile private var forwarded = false
  private val attributes = scala.collection.concurrent.TrieMap[String, String](req.tags.filter(t => FlowAttrName.set.contains(t._1)).toSeq: _*)
  private val params = req.queryString.mapValues(seq => seq(0)) ++ formParams.mapValues(seq => seq(0))

  def getParameter(name: String): Option[String] = params.get(name)

  def containsParameter(name: String): Boolean = params.contains(name)

  @throws(classOf[TransportException])
  def forward(path: String): Unit = {
    implicit val reflective = scala.language.reflectiveCalls
    forwarded = true
    outboundTransport.resultPromise.completeWith {
      Play.application.routes.get.routes.apply(DispatchRequestHeader(path)) match {
        case a: EssentialAction =>
          a.apply(Request(req.copy(tags = req.tags ++ attributes + ("FORWARDED" -> "true")), req.body)).run
        case e: Throwable => throw new TransportException(e)
      }
    }
  }

  val isAjax = "XMLHttpRequest" == req.headers.get("X-Requested-With").getOrElse("")

  def unwrap: AnyRef = req

  def platform: Platform.Platform = Platform.PLAY

  def getLoginCtx: Option[LoginContext] = req.getSCS.map(s => LoginContext.fromString(s))

  def updatedLoginCtx(loginCtx: LoginContext): Unit = { req.changeSCS(Option(loginCtx).map(_.asString)) }

  def getAttribute(name: String): Option[String] = attributes.get(name)

  def setAttribute(name: String, value: String): Unit = {attributes(name) = value}

  private def isForwarded = forwarded

  private def makeOutboundTransport = new OutboundTransport {
    val resultPromise = Promise[SimpleResult]
    private val futureResult = resultPromise.future

    /**
     * Redirect the user agent to the specified location.
     * @param location - location to redirect to.
     * @throws TransportException - if any error occurred while redirecting.
     */
    def redirect(location: String): Unit = if(self.isAjax) {
      import com.identityblitz.login.glue.play.JUtil._
      resultPromise.success(Ok(new RedirectResponse(location).jObj))
    }
    else {
      resultPromise.success(Redirect(location))
    }

    /**
     * Returns unwrapped transport.
     * @return - unwrapped transport.
     */
    def unwrap: AnyRef = {
      throw new UnsupportedOperationException("OutboundTransport for Play application does not supported unwrap operation.")
    }

    /**
     * Return the platform of the transport.
     * @return
     */
    def platform: Platform.Platform = self.platform

    def result: Future[SimpleResult] = {
      if(!self.isForwarded && !resultPromise.isCompleted)
        resultPromise.success(NotFound)
      futureResult
    }

  }

  /**
   * To dispatch
   * @param to - URL to dispatch
   */
  case class DispatchRequestHeader(private val to: String) extends RequestHeader {
    override def id: Long = req.id

    override def tags: Map[String, String] = Map.empty

    override def uri: String = to

    override def path: String = to

    override def method: String = req.method

    override def version: String = req.version

    override def queryString: Map[String, Seq[String]] = Map.empty

    override def headers: Headers = req.headers

    override def remoteAddress: String = req.remoteAddress
  }

}

private object PlayInboundTransport {

  def apply[A](implicit req: SCSRequest[A], params: Map[String, Seq[String]] = Map.empty): PlayInboundTransport[A] =
    new PlayInboundTransport[A](req, params)

}

/*/**
 * Play outbound transport implementation
 */
private class PlayOutboundTransport extends OutboundTransport {
  private[play] val resultPromise = Promise[SimpleResult]
  private val futureResult = resultPromise.future

  @throws(classOf[TransportException])
  def redirect[A](location: String)(implicit itr: InboundTransport): Unit = {
    if("XMLHttpRequest" == itr.unwrap.asInstanceOf[RequestHeader].headers("X-Requested-With")) {
      import com.identityblitz.login.glue.play.JUtil._
      resultPromise.success(Ok(new AjaxRedirectResponse(location).jObj))
    }
    else {
      resultPromise.success(Redirect(location))
    }
  }

  def unwrap: AnyRef = {
    throw new UnsupportedOperationException("OutboundTransport for Play application does not supported unwrap operation.")
  }

  def platform: Platform.Platform = Platform.PLAY

  private[play] def result[A](implicit itr: PlayInboundTransport[A]): Future[SimpleResult] = {
    if(!itr.isForwarded && !resultPromise.isCompleted)
      resultPromise.success(NotFound)
    futureResult
  }
}*/
