package com.sap.connector.server.framework.service.config.STOMPSecurity;

import com.sap.connector.server.framework.service.interfaces.AuthenticationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Set anonymous user (Principal) in WebSocket messages by using UUID
 * This is necessary to avoid broadcasting messages but sending them to specific user sessions
 */
public class CustomHandshakeHandler extends DefaultHandshakeHandler {

    @Autowired
    AuthenticationProvider authenticationProvider;

    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        // generate user name by UUID
        if(request.getHeaders()!=null && !request.getHeaders().containsKey("authorization")) {
            logger.warn("Received CONNECT, but has not authentication!");
            return null;
        }
        try {
            String processedToken = processBasicToken(request.getHeaders().get("authorization").get(0));
            if(authenticationProvider.isAuthenticated(processedToken)) {
                return new StompPrincipal(UUID.randomUUID().toString(),request.getRemoteAddress().getHostString());
            }
        } catch (UnsupportedEncodingException e) {
            logger.error("Authentication failed",e);
        }
        return null;
    }

    private String processBasicToken(String basicHeader) throws UnsupportedEncodingException {
        if(basicHeader.startsWith("Basic")) {
            basicHeader = basicHeader
                    .replace("Basic","")
                    .replace("basic","")
                    .trim();
            return new String(Base64.getDecoder().decode(basicHeader.getBytes()));
        }
        return null;
    }
}
