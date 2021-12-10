package com.sap.connector.server.plugins.handler;

import com.sap.banking.connector.beans.enums.RemoteActionMessageIntention;
import com.sap.connector.server.framework.service.annotation.EventExecutor;
import com.sap.connector.server.framework.service.plugins.AbstractPlugin;
import com.sap.connector.server.framework.service.annotation.EventTarget;
import com.sap.connector.server.framework.service.beans.RemoteActionMessageBean;
import com.sap.connector.server.framework.service.beans.SimpleMessageBean;
import com.sap.connector.server.framework.service.beans.MetadataBean;
import com.sap.connector.server.framework.service.exception.ActionFailedException;
import org.springframework.stereotype.Component;

/**
 * Sample plugin class
 */
@EventExecutor(module = "calc")
@Component
@Deprecated
public class CalculatorPlugin extends AbstractPlugin{

    @EventTarget(event = "sum")
    public Integer addNumber(MetadataBean metadataBean, SimpleMessageBean simpleMessageBean) throws Exception {
        String ip = simpleMessageBean.getMessage();
        int x = Integer.parseInt(ip.split(":")[0]) + Integer.parseInt(ip.split(":")[1]);

        simpleMessageBean.setMessage("Calculation complete " + x);

        //sendStatusResponse(userBean, new RemoteActionMessageBean().withMessage(simpleMessageBean));

        return x;
    }

    @EventTarget(event = "dummyWait")
    public void waitForNothing(MetadataBean metadataBean, SimpleMessageBean simpleMessageBean) throws InterruptedException, ActionFailedException {
        RemoteActionMessageBean remoteActionMessageBean = new RemoteActionMessageBean()
                .withMessageIntention(RemoteActionMessageIntention.STATUS_UPDATE);

        simpleMessageBean.setMessage("Starting to wait");
        sendStatusResponse(metadataBean, remoteActionMessageBean.withMessage(simpleMessageBean));

        Thread.sleep(500);
        simpleMessageBean.setMessage("waited 5000 ms");
        sendStatusResponse(metadataBean, remoteActionMessageBean.withMessage(simpleMessageBean));

        Thread.sleep(500);
        simpleMessageBean.setMessage("waited 5000 ms");
        sendStatusResponse(metadataBean, remoteActionMessageBean.withMessage(simpleMessageBean));

        Thread.sleep(500);
        simpleMessageBean.setMessage("waiting done");
        //Send a notification
        sendNotificationResponse(metadataBean, remoteActionMessageBean.withMessage(simpleMessageBean));
        sendStatusResponse(metadataBean, remoteActionMessageBean.withMessage(simpleMessageBean));
    }

    public CalculatorPlugin() {
    }
}
