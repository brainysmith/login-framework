package com.identityblitz.login.glue.servlet;

import com.identityblitz.lang.java.ToScalaConverter;
import com.identityblitz.login.LoginContext;
import com.identityblitz.login.error.TransportException;
import com.identityblitz.login.transport.InboundTransport;
import scala.Option;
import scala.collection.immutable.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.identityblitz.login.LoggingUtils.logger;

/**
  */
class ServletInboundTransport implements InboundTransport {

    private final HttpServletRequest req;
    private final HttpServletResponse resp;
    private final LoginContext lc;

    ServletInboundTransport(final HttpServletRequest req, final HttpServletResponse resp) {
        this.req = req;
        this.resp = resp;
        //todo: do it
        this.lc = null;
    }

    @Override
    public LoginContext getLoginContext() {
        return lc;
    }

    @Override
    public Option<String> getParameter(String name) {
        return Option.apply(req.getParameter(name));
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return ToScalaConverter.toScalaImmMap(req.getParameterMap());
    }

    @Override
    public Object unwrap() {
        return req;
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
