package com.sap.connector.server.framework.service.config.STOMPSecurity;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.messaging.MessageSecurityMetadataSourceRegistry;
import org.springframework.security.config.annotation.web.socket.AbstractSecurityWebSocketMessageBrokerConfigurer;

@Configuration
public class STOMPSecurityConfigurator extends AbstractSecurityWebSocketMessageBrokerConfigurer {


        //TODO : Security using X509
        @Override
        protected void configureInbound(MessageSecurityMetadataSourceRegistry messages) {
            messages
                    .anyMessage()
                        .authenticated()
                    .nullDestMatcher()
                        .authenticated()
                    .simpDestMatchers("/app/inbound/**")
                        .authenticated()
                    .simpDestMatchers("**/outbound/**")
                        .denyAll();
        }


    //disable CSRF
    @Override
    protected boolean sameOriginDisabled() {
        return true;
    }

}
