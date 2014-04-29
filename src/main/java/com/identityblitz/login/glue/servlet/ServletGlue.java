package com.identityblitz.login.glue.servlet;

import com.identityblitz.login.FlowAttrName;
import com.identityblitz.login.transport.CommandResponse;
import com.identityblitz.login.transport.JsonResponse;
import com.identityblitz.login.transport.RedirectResponse;
import scala.Option;

import static com.identityblitz.login.LoggingUtils.logger;
import javax.servlet.http.HttpServletRequest;

/**
 */
public class ServletGlue {

    public static JsonResponse extractResponse(final HttpServletRequest req) {
        final String location = (String) req.getAttribute(FlowAttrName.REDIRECT());
        if (location != null) {
            return new RedirectResponse(location);
        }

        final String base64Command = (String) req.getAttribute(FlowAttrName.COMMAND());
        final String command_name = (String) req.getAttribute(FlowAttrName.COMMAND_NAME());
        if (base64Command == null || command_name == null) {
            logger().error("Required attributes [{}] not specified into the http request " +
                    "[base64Command = {}, command_name = {}]", new Object[] {
                    FlowAttrName.COMMAND() + "," + FlowAttrName.COMMAND_NAME(), base64Command, command_name});
            throw new IllegalStateException("Required attributes not specified into the http request");
        }

        return new CommandResponse(base64Command, command_name,
                Option.apply((String) req.getAttribute(FlowAttrName.ERROR())));
    }

}
