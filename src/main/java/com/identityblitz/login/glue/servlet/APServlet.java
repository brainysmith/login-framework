package com.identityblitz.login.glue.servlet;

import com.identityblitz.login.*;
import com.identityblitz.login.error.LoginException;
import com.identityblitz.login.error.TransportException;
import com.identityblitz.login.method.AuthnMethod;
import com.identityblitz.login.transport.*;
import com.identityblitz.login.transport.Cookie;
import com.identityblitz.scs.SCSService;
import scala.Enumeration;
import scala.Option;
import scala.collection.convert.WrapAsJava$;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.identityblitz.login.App.logger;

/**
 * The Authentication Point (AP) servlet is the endpoint for Java Servlet based application
 * to launch authentication process. Supported two ways to call this servlet:
 * <li>
 *     <ul>by froward</ul>
 *     <ul>by request</ul>
 * </li>
 * For the first way it is necessary to specify the parameters described in the table below.
 * <table>
 *    <col width="25%"/>
 *    <col width="50%"/>
 *    <col width="25%"/>
 *    <thead>
 *        <tr><th>Name</th><th>Description</th><th>Mandatory</th></tr>
 *    </thead>
 *    <tbody>
 *        <tr><td>callback_uri</td><td>URI to return to when authentication process completes.</td><td>true</td></tr>
 *        <tr><td>method</td><td>Desired authentication method.</td><td>true</td></tr>
 *    </tbody>
 * </table>
 * For the second way it is necessary to specify the parameters described in the table below.
 * <table>
 *    <col width="25%"/>
 *    <col width="50%"/>
 *    <col width="25%"/>
 *    <thead>
 *        <tr><th>Name</th><th>Description</th><th>Mandatory</th></tr>
 *    </thead>
 *    <tbody>
 *        <tr><td>callback_uri</td><td>URI to return to when authentication process completes.</td><td>true</td></tr>
 *        <tr><td>method</td><td>Desired authentication method.</td><td>true</td></tr>
 *    </tbody>
 * </table>
 */

@WebServlet("/login/*")
public class APServlet extends HttpServlet {
    private static final Pattern pattern = Pattern.compile("login/([^/]+)(/do)?", Pattern.CASE_INSENSITIVE);

    private Map<String, WithStart> handlers;

    @Override
    public void init() throws ServletException {
        handlers = new HashMap<String, WithStart>(App$.MODULE$.methods().size() + 1);

        for(Map.Entry<String, AuthnMethod> entry : WrapAsJava$.MODULE$.mapAsJavaMap(
                App$.MODULE$.methods()).entrySet()) {
            handlers.put(entry.getKey(), entry.getValue());
        }
        handlers.put("flow", App.loginFlow());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final InboundTransport itr = new ServletInboundTransport(req, resp);
        final OutboundTransport otr = new ServletOutboundTransport(req, resp);
        extractParameters(itr);
        itr.setAttribute(FlowAttrName$.MODULE$.HTTP_METHOD(), "GET");
        invokeHandler(itr, otr);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final InboundTransport itr = new ServletInboundTransport(req, resp);
        final OutboundTransport otr = new ServletOutboundTransport(req, resp);
        extractParameters(itr);
        itr.setAttribute(FlowAttrName$.MODULE$.HTTP_METHOD(), "POST");
        invokeHandler(itr, otr);
    }

    private void extractParameters(InboundTransport itr) {
        final HttpServletRequest req = (HttpServletRequest)itr.unwrap();

        switch (req.getDispatcherType()) {
            case REQUEST:
                itr.setAttribute(FlowAttrName$.MODULE$.CALLBACK_URI_NAME(),
                        req.getParameter(FlowAttrName$.MODULE$.CALLBACK_URI_NAME()));
                itr.setAttribute(FlowAttrName$.MODULE$.AUTHN_METHOD_NAME(),
                        req.getParameter(FlowAttrName$.MODULE$.AUTHN_METHOD_NAME()));
                break;
            case FORWARD:
                break;
            default:
                throw new UnsupportedOperationException("Unsupported dispatcher type: " + req.getDispatcherType());
        }
    }

    private void invokeHandler(final InboundTransport itr, final OutboundTransport otr)
            throws ServletException {
        final HttpServletRequest req = (HttpServletRequest)itr.unwrap();
        final Matcher matcher = pattern.matcher(req.getRequestURI());
        if (matcher.find()) {
            final String method = matcher.group(1);
            final String action = matcher.group(2);

            try {
                if ("/do".equalsIgnoreCase(action)) {
                    ((AuthnMethod)handlers.get(method)).DO(itr, otr);
                } else {
                    handlers.get(method).start(itr, otr);
                }
            }
            catch (LoginException e) {
                throw new ServletException(e);
            }

        } else {
            throw new IllegalStateException("No matches for url: " + req.getServletPath());
        }

    }

}

class ServletCookieWrapper implements Cookie {

    private javax.servlet.http.Cookie cookie;

    ServletCookieWrapper(javax.servlet.http.Cookie cookie) {
        this.cookie = cookie;
    }

    @Override
    public String name() {
        return cookie.getName();
    }

    @Override
    public String value() {
        return cookie.getValue();
    }

    @Override
    public Option<Object> maxAge() {
        return Option.apply((Object)cookie.getMaxAge());
    }

    @Override
    public String path() {
        return cookie.getPath();
    }

    @Override
    public Option<String> domain() {
        return Option.apply(cookie.getDomain());
    }

    @Override
    public boolean secure() {
        return cookie.getSecure();
    }

    @Override
    public boolean httpOnly() {
        return cookie.isHttpOnly();
    }
}

class ServletInboundTransport implements InboundTransport {
    private final HttpServletRequest req;
    private final HttpServletResponse resp;

    ServletInboundTransport(final HttpServletRequest req, final HttpServletResponse resp) {
        this.req = req;
        this.resp = resp;
    }

    @Override
    public Option<String> getParameter(String name) {
        return Option.apply(req.getParameter(name));
    }

    @Override
    public boolean containsParameter(String name) {
        return req.getParameterMap().containsKey(name);
    }

    @Override
    public Object unwrap() {
        return req;
    }

    @Override
    public Enumeration.Value platform() {
        return Platform.SERVLET();
    }

    @Override
    public Option<LoginContext> getLoginCtx() {
        final String lc = SCSService.getSCS(req);
        if(lc == null)
            return Option.empty();
        return Option.apply(LoginContext$.MODULE$.fromString(lc));
    }

    @Override
    public void updatedLoginCtx(Option<LoginContext> loginCtx) {
        SCSService.changeSCS(req, loginCtx.isDefined()? loginCtx.get().asString() : null);
    }

    @Override
    public Option<String> getAttribute(String name) {
        return Option.apply((String)req.getAttribute(name));
    }

    @Override
    public void setAttribute(String name, String value) {
        req.setAttribute(name, value);
    }

    @Override
    public void removeAttribute(String name) {
        req.removeAttribute(name);
    }

    @Override
    public void forward(String path) throws TransportException {
        try {
            req.getRequestDispatcher(path).forward(req, resp);
        } catch (ServletException e) {
            logger().error("Can't forward [path = {}]. ServletException has occurred: {}", path, e);
            throw new TransportException(e);
        } catch (IOException e) {
            logger().error("Can't forward [path = {}]. IOException has occurred: {}", path, e);
            throw new TransportException(e);
        }
    }

    @Override
    public Option<? extends Cookie> getCookie(String name) {
        for(javax.servlet.http.Cookie c: req.getCookies()) {
            if (c.getName().equals(name)) {
                return Option.apply(new ServletCookieWrapper(c));
            }
        }
        return Option.empty();
    }
}

class ServletOutboundTransport implements OutboundTransport {
    private final HttpServletResponse resp;

    private final boolean isAjax;

    ServletOutboundTransport(final HttpServletRequest req, final HttpServletResponse resp) {
        this.resp = resp;
        isAjax = "XMLHttpRequest".equals(req.getHeader("X-Requested-With"));
    }

    @Override
    public void redirect(String location) throws TransportException {
        try {
            if (isAjax) {
                resp.setContentType("application/json");
                resp.getWriter().write(new RedirectResponse(location).asString());
            } else {
                resp.sendRedirect(location);
            }
        } catch (IOException e) {
            logger().error("Can't send redirect [location = {}]. IOException has occurred: {}", location, e);
            throw new TransportException(e);
        }
    }

    @Override
    public Object unwrap() {
        return resp;
    }

    @Override
    public Enumeration.Value platform() {
        return Platform.SERVLET();
    }

    @Override
    public void addCookie(Cookie cookie) {
        resp.addCookie(convertCookie(cookie));
    }

    @Override
    public void discardCookie(DiscardingCookie cookie) {
        resp.addCookie(convertCookie(cookie.toCookie()));
    }

    private javax.servlet.http.Cookie convertCookie(Cookie cookie) {
        javax.servlet.http.Cookie servletCookie = new javax.servlet.http.Cookie(cookie.name(), cookie.value());

        servletCookie.setPath(cookie.path());
        servletCookie.setHttpOnly(cookie.httpOnly());
        servletCookie.setSecure(cookie.secure());

        if (cookie.domain().isDefined()) {
            servletCookie.setDomain(cookie.domain().get());
        }

        if (cookie.maxAge().isDefined()) {
            servletCookie.setMaxAge((Integer)cookie.maxAge().get());
        }

        return servletCookie;
    }

}
