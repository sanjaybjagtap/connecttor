package com.sap.connector.server.framework.view;

import com.sap.connector.server.framework.service.beans.ApplicationContants;
import com.sap.connector.server.framework.service.beans.RemoteActionMessageBean;
import com.sap.connector.server.framework.service.beans.SimpleMessageBean;
import com.sap.connector.server.framework.controller.MessageActionController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class RoutingController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MessageActionController messageActionController;

    @MessageMapping(ApplicationContants.PRIMARY_INBOUND_QUEUE)
    public SimpleMessageBean receiveMessage(Principal principal,
                                            RemoteActionMessageBean remoteActionMessageBean) {

        remoteActionMessageBean.setConnectionId(principal.getName());
        messageActionController.addToInboundMessageQueue(remoteActionMessageBean);
        return new SimpleMessageBean("Acknowledged message!");
    }
}
