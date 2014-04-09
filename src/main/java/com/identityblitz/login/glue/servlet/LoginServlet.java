package com.identityblitz.login.glue.servlet;

import com.identityblitz.login.LoginFlow$;
import com.identityblitz.login.transport.InboundTransport;
import com.identityblitz.login.transport.OutboundTransport;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filter expected that a login context already exists.
 */

@WebServlet("/login/*")
public final class LoginServlet extends HttpServlet {

    private Pattern pattern = Pattern.compile("login/([^/]+)(/do)?", Pattern.CASE_INSENSITIVE);

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final Matcher matcher = pattern.matcher(req.getRequestURI());
        if (matcher.find()) {
            final String method = matcher.group(1);
            final String action = matcher.group(2);
            final InboundTransport inTransport = new ServletInboundTransport(req, resp);
            final OutboundTransport outTransport = new ServletOutboundTransport(resp);
            if ("do".equalsIgnoreCase(action)) {
                /** do action **/
                LoginFlow$.MODULE$.apply().next(inTransport, outTransport);
            } else {
                /** start action **/
                LoginFlow$.MODULE$.apply().start(method, inTransport, outTransport);
            }
        } else {
            throw new IllegalStateException("No matches for url: " + req.getServletPath());
        }
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

