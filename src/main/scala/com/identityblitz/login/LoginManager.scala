package com.identityblitz.login

import scala.util.{Try, Success, Failure}
import scala.annotation.implicitNotFound
import scala.Option
import com.identityblitz.login.api.LoginStatus

/**
 * The login manager which controls a login context.
 */
@implicitNotFound("No implicit login context of request found.")
object LoginManager {

  //todo: change it
  private val tmpCompleteRedirectCall = routes.Login.getPage()

  private val loginModulesMeta =
    (for ((clazz, params) <- conf.loginModules) yield new LoginModuleMeta(clazz, params)).toArray.sorted

  if (loginModulesMeta.isEmpty) {
    appLogWarn("authentication will not work: there aren't login modules found in the configuration")
    throw new RuntimeException("authentication will not work: there aren't login modules found in the configuration")
  } else {
    appLogDebug("the following login modules has been read from the configuration: {}", loginModulesMeta)
  }

  private[login] val loginModules = loginModulesMeta.map(_.newInstance)

  private val loginFlow = conf.loginFlow.fold[LoginFlow]({
    appLogDebug("use the build in login flow")
    BuildInLoginFlow
  })(className => {
    appLogDebug("getting a custom login flow [class = {}]", className)
    this.getClass.getClassLoader.loadClass(className + "$").asInstanceOf[LoginFlow]
  })

  /**
   * Perform a preparing to authentication:
   *  - move the current login context into processing state with specified method;
   *  - pools all available login modules and adds into the current login context those which are ready to perform
   *    authentication again the current login context. Login modules can add its necessary for the subsequent
   *    authentication parameters into the login context.
   *
   * @param method the method of the authentication.
   * @param lc the login context. If it is absent then new will be created.
   * @param request the current request.
   * @throws IllegalStateException if:
   *                               - the current login context has already completed;
   *                               - there aren't login modules which is able to perform the authentication with
   *                                 specified method.
   */
  def start(method: Int)(implicit lc: LoginContext = LoginContext(), request: Request[AnyContent]) = {
    appLogTrace("start a new authentication process [method = {}, lc = {}]", method, lc)

    lc match {
      case lcImpl: LoginContextImpl => {
        Try({
          //check that the current state of the login process is appropriate for the current operation
          if (lcImpl.getStatus == LoginStatus.SUCCESS) {
            appLogError("the authentication process is stopped: the login process has already completed " +
              "[login context = {}]", lcImpl)
            throw new IllegalStateException("the login process has already completed")
          }

          lcImpl.setCurrentMethod(method)
          lcImpl.clearLoginModule
          lcImpl.clearLoginModulesToProcess
          //find the login modules to process
          loginModules.filter(lm => lm.start).foreach(lm => {
            appLogTrace("a new login module has been added to process [method = {}, login module = {}]",
              method, lm)
            lcImpl.addLoginModuleToProcess(lm)
          })

          if (lcImpl.getLoginModulesToProcess().isEmpty) {
            appLogError("there isn't login module which is able to perform the authentication with specified method " +
              "[method = {}, lc = {}]", method, lc)
            throw new IllegalStateException("there aren't login modules which is able to perform the authentication " +
              "with specified method")
          }
        }) match {
          case Success(_) => {
            lcImpl.setStatus(LoginStatus.PROCESSING)
            appLogDebug("a new authentication process for the specified method has been started [method={}, login context = {}]", method, lcImpl)
          }
          case Failure(e) => {
            lcImpl.setStatus(LoginStatus.FAIL)
            appLogError("a new authentication can't be started [method = {}, runtime exception = {}]", method, e)
            throw e
          }

        }
      }
      case _ => {
        appLogError("an unknown implementation of the login context: {}", lc)
        throw new RuntimeException("an unknown implementation of the login context, please, email to the technical support")
      }
    }
  }


  /**
   * Perform the authentication again the login modules which was selected on start step with "FIRST SUCCESS" strategy.
   * If the authentication is complete successfully or partially completed it is called a specified <b>complete</b>
   * function with <b>nextPoint</b> argument which describing a HTTP request that needs to be done. If the
   * authentication is partially completed the nextPoint is None otherwise Some(nextPoint). The obligation and the login
   * module which has performed the authentication can be obtained from the login context. If the authentication fails
   * it's called a specified <b>fail</b> function that retrieves a list of the errors occurred.
   * @param complete the function that will be called if the authentication is complete successfully or partially
   *                 completed.
   * @param fail the function that will be called if the authentication fails.
   * @param lc the current login context.
   * @param request the current HTTP request.
   * @tparam A the result type.
   */
  def `do`[A](complete: Option[Call] => A)(fail: Seq[(String, String)] => A)
          (implicit lc: LoginContext, request: Request[AnyContent]) = {
    appLogTrace("performing the authentication [login context = {}]", lc)

    lc match {
      case lcImpl: LoginContextImpl => {
        //check the current status
        if (lcImpl.getStatus != LoginStatus.PROCESSING) {
          appLogError("authentication can't be performed: a wrong status. Ensure that your perform start on login " +
            "manager before [lc = {}]", lc)
          throw new IllegalStateException("authentication can't be performed: a wrong status. Ensure that your perform " +
            "start on login manager before")
        }

        //perform the authentication with "FIRST SUCCESS" strategy
        Try({
          lcImpl.getLoginModule[LoginModule].fold({
            appLogTrace("performing authentication by the defined login modules in the start stage [lms = {}]",
              lcImpl.getLoginModulesToProcess())
            lcImpl.getLoginModulesToProcess().foldLeft[Result](Result.FAIL)((prevRes, lm) => {
              if (prevRes == Result.FAIL) {
                appLogTrace("try to authenticate by a following login module: {}", lm)
                val iRes = lm.`do`
                if (iRes != Result.FAIL) {
                  appLogTrace("authentication by login module is successfully [lm = {}]", lm)
                  lcImpl.setLoginModule(lm)
                } else {
                  appLogTrace("fail authentication by login module [lm = {}]", lm)
                }
                iRes
              } else {
                prevRes
              }
            })
          })(lm => {
            appLogTrace("continue authentication by login module from the login context [lm = {}]", lm)
            lm.`do`
          })
        }) match {
          case Success(result) => {
            result match {
              case Result.SUCCESS => {
                appLogDebug("authentication is successful [lc = {}]", lcImpl)
                //todo: add completed authentication method to the login context
                loginFlow.getNextPoint.fold[A]({
                  lcImpl.setStatus(LoginStatus.SUCCESS)
                  appLogDebug("the login process is completed successfully [lc = {}]", lcImpl)
                  complete(Some(tmpCompleteRedirectCall))
                })(nextPoint => {
                  appLogDebug("go to the next point [lc = {}]", lcImpl)
                  complete(Some(nextPoint))
                })
              }
              case Result.PARTIALLY_COMPLETED => {
                appLogDebug("authentication is partially completed [lc = {}]", lcImpl)
                complete(None)
              }
              case Result.FAIL => {
                lcImpl.setStatus(LoginStatus.FAIL)
                appLogDebug("authentication is failed [lc = {}]", lcImpl)
                fail(lcImpl.getErrors)
              }
              case _ @ unknownRes => {
                appLogError("authentication can't be perform: unknown result code of the authentication [code = {}, " +
                  "lc = {}]", unknownRes, lcImpl)
                throw new RuntimeException("authentication can't be perform: unknown result code of the authentication")
              }
            }
          }
          case Failure(e) => {
            lcImpl.setStatus(LoginStatus.FAIL)
            appLogError("authentication can't be perform [runtime exception = {}]", e)
            throw e
          }
        }
      }
      case _ => {
        appLogError("an unknown implementation of the login context: {}", lc)
        throw new RuntimeException("an unknown implementation of the login context, please, email to the technical support")
      }
    }
  }

  /**
   * Prepares to the authentication with specified method (start) and subsequent authentication (do) in one call.
   *
   * @param method the method of the authentication.
   * @param complete the function that will be called if the authentication is complete successfully or partially
   *                 completed.
   * @param fail the function that will be called if the authentication fails.
   * @param lc the current login context.
   * @param request the current HTTP request.
   */
  def apply[A](method: Int)(complete: Option[Call] => A)(fail: Seq[(String, String)] => A)
           (implicit lc: LoginContext = LoginContext(), request: Request[AnyContent]) = {
    start(method)
    `do`(complete)(fail)
  }
}
