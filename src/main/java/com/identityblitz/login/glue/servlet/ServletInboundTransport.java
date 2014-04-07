package com.identityblitz.login.glue.servlet;

import com.identityblitz.login.transport.InboundTransport;
import scala.Option;
import scala.collection.immutable.Map;

/**
 * Created by szaytsev on 4/7/14.
 */
public class ServletInboundTransport implements InboundTransport {

    @Override
    public Option<String> getParameter(String name) {
        return null;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return null;
    }

    @Override
    public void forward() {

    }

    @Override
    public Object unwrap() {
        return null;
    }
}
