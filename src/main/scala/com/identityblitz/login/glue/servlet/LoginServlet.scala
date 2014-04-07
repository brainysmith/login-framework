package com.identityblitz.login.glue.servlet

import javax.servlet.annotation.WebServlet
import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}

/**
 * Process login for servlet implementation.
 */
@WebServlet("/login/*")
class LoginServlet extends HttpServlet {

  override def service(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
    /** todo:
      * 1) init servlet transport;
      * 2) path control to login flow
     */
  }
}



