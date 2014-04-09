package com.identityblitz.login.glue.servlet;

import com.identityblitz.login.LoginContext$;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Initialize a new login process by creating a new login context and forward to login servlet.
 * Created login context is added to the current request.
 */
@WebServlet("/login")
public final class LoginEndpointServlet extends HttpServlet {

    private static final String REDIRECT_URI_PARAM_NAME = "redirect_uri";
    private static final String METHOD_PARAM_NAME = "method";

    private static final String LC_ATTR_NAME = "loginContext";

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final String redirectUri;
        final String method;
        switch (req.getDispatcherType()) {
            case REQUEST:
                redirectUri = req.getParameter(REDIRECT_URI_PARAM_NAME);
                method = req.getParameter(METHOD_PARAM_NAME);
                break;
            case FORWARD:
                redirectUri = (String) req.getAttribute(REDIRECT_URI_PARAM_NAME);
                method = (String) req.getAttribute(METHOD_PARAM_NAME);
                break;
            default:
                throw new UnsupportedOperationException("Unsupported dispatcher type: " + req.getDispatcherType());
        }

        req.setAttribute(LC_ATTR_NAME, LoginContext$.MODULE$.apply(redirectUri));
        req.getRequestDispatcher("/login/" + method).forward(req, resp);
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

