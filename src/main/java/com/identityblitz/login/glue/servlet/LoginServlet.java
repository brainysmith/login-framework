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

