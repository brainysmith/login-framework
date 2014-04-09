package com.identityblitz.login

import com.identityblitz.login.LoggingUtils._
import com.identityblitz.login.buildin.BuildInLoginFlow
import com.identityblitz.login.transport.{OutboundTransport, InboundTransport}

/**
 * The login manager which controls a login context.
 */
/*@implicitNotFound("No implicit login context of request found.")*/
object LoginManager {

  //todo: change it
  private val tmpCompleteRedirectCall = ???

/*  private val loginModulesMeta =
    (for ((clazz, params) <- conf.loginModules) yield new LoginModuleMeta(clazz, params)).toArray.sorted

  if (loginModulesMeta.isEmpty) {
    appLogWarn("authentication will not work: there aren't login modules found in the configuration")
    throw new RuntimeException("authentication will not work: there aren't login modules found in the configuration")
  } else {
    appLogDebug("the following login modules has been read from the configuration: {}", loginModulesMeta)
  }

  private[login] val loginModules = loginModulesMeta.map(_.newInstance)*/

  private val loginFlow = Conf.loginFlow.fold[LoginFlow]({
    logger.debug("will use the build in login flow: {}", BuildInLoginFlow.getClass.getSimpleName)
    BuildInLoginFlow
  })(className => {
    logger.debug("find in the configuration a custom login flow [class = {}]", className)
    this.getClass.getClassLoader.loadClass(className).asInstanceOf[LoginFlow]
  })

  def start(method: String)(implicit req: InboundTransport, resp: OutboundTransport) = {
    /**todo:
      * 1) check that input parameters is correct;
      * 2) create a new login context;
      *
     */
  }


  def `do`[A](implicit inReq: InboundTransport, outResp: OutboundTransport) = {
  }
}
