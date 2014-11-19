package com.identityblitz.login

import com.identityblitz.login.FlowAttrName._
import com.identityblitz.login.Platform.Platform
import com.identityblitz.login.transport.{DiscardingCookie, OutboundTransport, Cookie, InboundTransport}
import org.scalatest.{FlatSpec, Matchers}

class LoginFlowTest extends FlatSpec with Matchers {

  behavior of "Login flow"

  it should "load config properly" in {
    val flow = LoginFramework.loginFlow

    val itr = new InboundTransport {
      var attrs = Map(CALLBACK_URI_NAME -> "some_url")
      var lgnCtx: Option[LoginContext] = _
      val cookies = Map[String, Cookie]()
      var pathToForward: String = _

      override def getCookie(name: String): Option[_ <: Cookie] = cookies.get(name)
      override def getParameter(name: String): Option[String] = ???
      override def platform: Platform = ???
      override def containsParameter(name: String): Boolean = ???
      override def updatedLoginCtx(loginCtx: Option[LoginContext]): Unit = { lgnCtx = loginCtx }
      override def getAttribute(name: String): Option[String] = attrs.get(name)
      override def removeAttribute(name: String): Unit = ???
      override def getLoginCtx: Option[LoginContext] = lgnCtx
      override def unwrap: AnyRef = ???
      override def forward(path: String): Unit = pathToForward = path
      override def setAttribute(name: String, value: String): Unit = attrs += name -> value
    }

    val otr = new OutboundTransport {
      var cookies = Map[String, Cookie]()
      var pathToRedirect: String = _
      override def platform: Platform = ???
      override def addCookie(cookie: Cookie): Unit = cookie + cookie.name -> cookie
      override def unwrap: AnyRef = ???
      override def redirect(location: String): Unit = pathToRedirect = location
      override def discardCookie(cookie: DiscardingCookie): Unit = ???
    }

    flow.start(itr, otr)

    import scala.language.reflectiveCalls
    itr.attrs.keys should contain ("callback_uri")
    itr.attrs.keys should contain ("command_name")
    itr.attrs.keys should contain ("command")
    itr.attrs.values should contain ("bind")
    itr.pathToForward should be ("/blitz/lgpage")
  }

}
