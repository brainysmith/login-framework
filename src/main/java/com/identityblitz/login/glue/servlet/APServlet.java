package com.identityblitz.login.glue.servlet;

import com.identityblitz.login.LoginContext;
import com.identityblitz.login.LoginContext$;
import com.identityblitz.login.Platform;
import com.identityblitz.login.error.TransportException;
import com.identityblitz.login.transport.InboundTransport;
import com.identityblitz.scs.SCSService;
import scala.Enumeration;
import scala.Option;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import static com.identityblitz.login.LoggingUtils.*;

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
public class APServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final String callWay = checkDispatchType(req);


    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final String callWay = checkDispatchType(req);



    }

    private String checkDispatchType(HttpServletRequest req) {
        switch (req.getDispatcherType()) {
            case REQUEST:
            case FORWARD:
                return req.getDispatcherType().name();
            default:
                throw new UnsupportedOperationException("Unsupported dispatcher type: " + req.getDispatcherType());
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
            return null;
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
