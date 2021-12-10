package com.sap.connector.server.framework.service.threads;

import com.sap.connector.server.framework.service.beans.MessageBean;
import com.sap.connector.server.framework.controller.MessageActionController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InboundMessageWatcher implements Runnable{

    MessageActionController messageActionController;

    Logger logger = LoggerFactory.getLogger(InboundMessageWatcher.class);

    public InboundMessageWatcher(MessageActionController controller) {
        this.messageActionController = controller;
    }

    @Override
    public void run() {
        try {
            while (true) {
                logger.debug("getting message from queue");
                MessageBean serializable = (MessageBean) messageActionController.getFromInboundMessageQueue();
                logger.debug("got message from queue");
                messageActionController.processMessage(serializable);
            }
        } catch (InterruptedException e) {
            logger.info(Thread.currentThread().getName()+" : Interrupted!");
        }
    }
}
