package com.sap.connector.server.framework.service.config.STOMPSecurity;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.ArrayList;
import java.util.List;


/**
 * This is a custom authentication handler for Spring web socket Security, not active
 */
@Configuration
//@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE+99)
public class WebsocketAuthenticationConfig implements WebSocketMessageBrokerConfigurer {
private static final String HEADER_KEY = "authorization";

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message,StompHeaderAccessor.class);
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    //This changes between spring client and refClient
                    //if(accessor.getNativeHeader(HEADER_KEY)!=null && accessor.getNativeHeader(HEADER_KEY).size()>0) {
                    if(accessor.getUser()!=null && accessor.getUser().getName()!=null) {
                        //String auth = (String)accessor.getNativeHeader(HEADER_KEY).get(0);
                        String hostAddress = null;
                        if(message.getHeaders().containsKey("simpUser") && message.getHeaders().get("simpUser") instanceof StompPrincipal) {
                            hostAddress = ((StompPrincipal)message.getHeaders().get("simpUser")).getIp();
                        }
                        String token = accessor.getUser().getName();
                        GrantedAuthority grantedAuthority = new SimpleGrantedAuthority("ROLE_USER");
                        List<GrantedAuthority> authorities = new ArrayList<>();
                        authorities.add(grantedAuthority);
                        Authentication authentication = new UsernamePasswordAuthenticationToken(token,new STOMPCredentials(hostAddress),authorities);
                        accessor.setUser(authentication);
                        accessor.setLogin("logged-in");
                    } else {
                        accessor.setUser(null);
                    }
                }
                return message;
            }
        });
    }
}
