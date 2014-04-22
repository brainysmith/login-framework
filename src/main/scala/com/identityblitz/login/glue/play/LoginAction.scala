package com.identityblitz.login.glue.play

import play.api.mvc._
import scala.concurrent.{Promise, Future}
import play.api.libs.iteratee.Done
import com.identityblitz.scs.glue.play.{SCSEnabledAction, SCSRequest}
import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}
import com.identityblitz.login.error.{LoginException, TransportException}
import play.api.Play
import com.identityblitz.login.{Conf, FlowAttrName, LoginContext, Platform}
import play.api.mvc.Results._
import play.api.mvc.SimpleResult
import play.api.Play.current
import java.util.regex.{Pattern, Matcher}
import com.identityblitz.login.authn.method.AuthnMethod

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
  private final val pattern: Pattern = Pattern.compile("([^/]+)(/do)?", Pattern.CASE_INSENSITIVE)

  val loginPath = "/login/"

  val handlers: Map[String, com.identityblitz.login.Handler] = Conf.methods.mapValues(_._1
    .asInstanceOf[com.identityblitz.login.Handler]) +
    ("flow" -> Conf.loginFlow.asInstanceOf[com.identityblitz.login.Handler])

  /**
   * The entry point action of the AP for request made by HTTP method GET. For this action the specific route
   * must be added to the routes. The route to add is given below.
   * GET  /login/\*handler  com.identityblitz.login.glue.play.APController.doGet(handler)
   * @param handler - name of handler to process incoming request on.
   * @return - response.
   */
  def doGet(handler: String) = SCSEnabledAction.async{
    implicit request => {
      implicit val otr = PlayOutboundTransport.apply
      implicit val itr = PlayInboundTransport(otr)

      if(ifNotDirectCall(request)) {
        itr.setAttribute(FlowAttrName.CALLBACK_URI_NAME, itr.getParameter(FlowAttrName.CALLBACK_URI_NAME).get)
        itr.setAttribute(FlowAttrName.AUTHN_METHOD_NAME, itr.getParameter(FlowAttrName.AUTHN_METHOD_NAME).get)
      }
      itr.setAttribute(FlowAttrName.HTTP_METHOD, "GET")
      invokeHandler(handler)
      otr.result
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
      implicit val otr = PlayOutboundTransport.apply
      implicit val itr = PlayInboundTransport(otr)

      if(ifNotDirectCall(request)) {
        itr.setAttribute(FlowAttrName.CALLBACK_URI_NAME, itr.getParameter(FlowAttrName.CALLBACK_URI_NAME).get)
        itr.setAttribute(FlowAttrName.AUTHN_METHOD_NAME, itr.getParameter(FlowAttrName.AUTHN_METHOD_NAME).get)
      }
      itr.setAttribute(FlowAttrName.HTTP_METHOD, "POST")
      invokeHandler(handler)
      otr.result
    }
  }

  private def ifNotDirectCall(req: RequestHeader): Boolean = req.path.startsWith(loginPath)

  private def invokeHandler(handler: String)(implicit itr: InboundTransport, otr: OutboundTransport) {
    val matcher: Matcher = pattern.matcher(handler)
    if (matcher.find) {
      val method: String = matcher.group(1)
      val action: String = matcher.group(2)
      try {
        if ("/do".equalsIgnoreCase(action))
          handlers(method).asInstanceOf[AuthnMethod].DO
        else
          handlers(method).start
      }
      catch {
        case e: LoginException => {
          throw new Error(e)
        }
      }
    }
    else {
      throw new IllegalStateException("No matches for handler: " + handler)
    }
  }

}

/**
 * The action to add another action ability to be forwarded to.
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
private class PlayInboundTransport[A](private val otr: PlayOutboundTransport,
                                      private val req: SCSRequest[A],
                                      private val formParams: Map[String, Seq[String]]) extends InboundTransport {
  @volatile private var forwarded = false
  private val attributes = scala.collection.concurrent.TrieMap[String, String]()
  private val params = req.queryString.mapValues(seq => seq(0)) ++ formParams.mapValues(seq => seq(0))

  def getParameter(name: String): Option[String] = params.get(name)

  def containsParameter(name: String): Boolean = params.contains(name)

  @throws(classOf[TransportException])
  def forward(path: String): Unit = {
    forwarded = true
    otr.resultPromise.completeWith {
      Play.application.routes.get.routes.apply(DispatchRequestHeader(path)) match {
        case a: EssentialAction => {
          a.apply(Request(req.copy(tags = req.tags ++ attributes + ("FORWARDED" -> "true")), req.body)).run
        }
        case e: Throwable => throw new TransportException(e)
      }
    }
  }

  def unwrap: AnyRef = req

  def platform: Platform.Platform = Platform.PLAY

  def getLoginCtx: Option[LoginContext] = req.getSCS.map(s => LoginContext.fromString(s))

  def updatedLoginCtx(loginCtx: LoginContext): Unit = { req.changeSCS(Option(loginCtx).map(_.asString)) }

  def getAttribute(name: String): Option[String] = attributes.get(name)

  def setAttribute(name: String, value: String): Unit = {attributes(name) = value}

  private[play] def isForwarded = forwarded


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

  def apply[A](otr: PlayOutboundTransport)
              (implicit req: SCSRequest[A], params: Map[String, Seq[String]] = Map.empty): PlayInboundTransport[A] =
    new PlayInboundTransport[A](otr, req, params)

}

/**
 * Play outbound transport implementation
 */
private class PlayOutboundTransport extends OutboundTransport {
  private[play] val resultPromise = Promise[SimpleResult]
  private val futureResult = resultPromise.future

  @throws(classOf[TransportException])
  def redirect(location: String): Unit = {resultPromise.success(Redirect(location))}

  def unwrap: AnyRef = {
    throw new UnsupportedOperationException("OutboundTransport for Play application does not supported unwrap operation.")
  }

  def platform: Platform.Platform = Platform.PLAY

  private[play] def result[A](implicit itr: PlayInboundTransport[A]): Future[SimpleResult] = {
    if(!itr.isForwarded && !resultPromise.isCompleted)
      resultPromise.success(NotFound)
    futureResult
  }
}


private object PlayOutboundTransport {

  def apply: PlayOutboundTransport = new PlayOutboundTransport

}
