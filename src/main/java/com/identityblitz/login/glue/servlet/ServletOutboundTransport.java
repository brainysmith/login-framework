package com.identityblitz.login.glue.servlet;

import com.identityblitz.login.error.TransportException;
import com.identityblitz.login.transport.OutboundTransport;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.identityblitz.login.LoggingUtils.logger;

/**
 */
class ServletOutboundTransport implements OutboundTransport {

    private final HttpServletResponse resp;

    ServletOutboundTransport(final HttpServletResponse resp) {
        this.resp = resp;
    }

    @Override
    public void sendRedirect(String location) throws TransportException {
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
}
