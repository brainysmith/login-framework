package com.identityblitz.login.glue.servlet

import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import javax.servlet.annotation.WebServlet

/**
 * Initialize a new login process.
 */

@WebServlet("/login")
class LoginEndPointServlet extends HttpServlet {


  override def service(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
    /** todo:
      * 1) preparing callback for successful login.
      * 2) start login flow.
     */
  }

}

/**
 * This filter perform http flow control during the all authentication process.
 */

/** todo:
  * 1) getting the current login context.
  * 2.1) if login context (lc) is not found:
  *   - init the new one (one of the param should be requested url) and start an authentication process;
  * ask: how getting the principals and credentials passed from the html form? put all http param into lc?          !
  *   - continue an authentication procedure;
  * 2.2) if lc found - continue the authentication procedure.
  * 3) check the authentication result:
  * 4.1) if the authentication is successful completed forward or redirect request to the requested url
  * 4.2) if the authentication is not completed - perform the obligation (forward, redirect to the specified url
  * or stay at the current url). Obligation example:
  *   - show error, attention;
  *   - continue authentication with OTP;
  *   - change password.
  * ask: how return the some parameters to the html page.                                                           !
  *(4 can be call back functions for step 3).
  **/

/** notes for session maintenance:                                                                                      !
  * 1) check if session is authenticated:
  *   - it must be perform by call getHttpSession(false)? If a http session is exist then user has been authenticated?
  *   - what about anonymous session in this case?
  *
 */
