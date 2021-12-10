package com.sap.connector.server.framework.service.config;

import com.sap.connector.server.framework.service.config.STOMPSecurity.STOMPCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ConnectionEventListeners {
    Logger logger = LoggerFactory.getLogger(ConnectionEventListeners.class);

    @Autowired
    AtomicInteger activeConnection;

    @Autowired
    Map<String,Integer> clientConnectionMap;

    @EventListener
    private void handleSessionConnected(SessionConnectedEvent event) {
        activeConnection.incrementAndGet();

        STOMPCredentials stompCredentials = (STOMPCredentials) ((UsernamePasswordAuthenticationToken)event.getUser()).getCredentials();
        clientConnectionMap.put(stompCredentials.getClientAddress(),
                clientConnectionMap.getOrDefault(stompCredentials.getClientAddress(),0) + 1);
    }

    @EventListener
    private void handleSessionDisconnect(SessionDisconnectEvent event) {
        logger.warn("Disconnected !! "+event.getSessionId());
        if(activeConnection.get() > 0) {
            activeConnection.decrementAndGet();
        }

        STOMPCredentials stompCredentials = (STOMPCredentials) ((UsernamePasswordAuthenticationToken)event.getUser()).getCredentials();

        clientConnectionMap.put(stompCredentials.getClientAddress(),
                clientConnectionMap.getOrDefault(stompCredentials.getClientAddress(),1) - 1);
        if(clientConnectionMap.get(stompCredentials.getClientAddress()) == 0) {
            clientConnectionMap.remove(stompCredentials.getClientAddress());
        }
    }
}
