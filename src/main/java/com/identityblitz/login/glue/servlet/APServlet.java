package com.identityblitz.login.glue.servlet;

import com.identityblitz.login.*;
import com.identityblitz.login.authn.method.AuthnMethod;
import com.identityblitz.login.error.LoginException;
import com.identityblitz.login.error.TransportException;
import com.identityblitz.login.transport.InboundTransport;
import com.identityblitz.login.transport.OutboundTransport;
import com.identityblitz.scs.SCSService;
import scala.Enumeration;
import scala.Option;
import scala.Tuple2;
import scala.collection.convert.WrapAsJava$;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.identityblitz.login.LoggingUtils.logger;

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

    private Map<String, Handler> handlers;

    @Override
    public void init() throws ServletException {
        handlers = new HashMap<String, Handler>(Conf$.MODULE$.methods().size() + 1);

        for(Map.Entry<String, Tuple2<AuthnMethod, AuthnMethodMeta>> entry : WrapAsJava$.MODULE$.mapAsJavaMap(
                Conf$.MODULE$.methods()).entrySet()) {
            handlers.put(entry.getKey(), entry.getValue()._1());
        }
        handlers.put("flow", LoginFlow.apply());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final InboundTransport itr = new ServletInboundTransport(req, resp);
        final OutboundTransport otr = new ServletOutboundTransport(resp);
        extractParameters(itr);
        itr.setAttribute(FlowAttrName$.MODULE$.HTTP_METHOD(), "GET");
        invokeHandler(itr, otr);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final InboundTransport itr = new ServletInboundTransport(req, resp);
        final OutboundTransport otr = new ServletOutboundTransport(resp);
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
    public void updatedLoginCtx(LoginContext loginCtx) {
        SCSService.changeSCS(req, loginCtx.asString());
    }

    @Override
    public Option<Object> getAttribute(String name) {
        return Option.apply(req.getAttribute(name));
    }

    @Override
    public void setAttribute(String name, Object value) {
        req.setAttribute(name, value);
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
}

class ServletOutboundTransport implements OutboundTransport {
    private final HttpServletResponse resp;

    ServletOutboundTransport(final HttpServletResponse resp) {
        this.resp = resp;
    }

    @Override
    public void redirect(String location) throws TransportException {
        try {
            resp.sendRedirect(location);
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
}
