package com.sap.connector.server.framework.service.threads;

import com.sap.connector.server.framework.controller.MessageActionController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

public class OutboundMessageWatcher implements Runnable{

    MessageActionController messageActionController;

    Logger logger = LoggerFactory.getLogger(OutboundMessageWatcher.class);

    public OutboundMessageWatcher(MessageActionController controller) {
        this.messageActionController = controller;
    }

    @Override
    public void run() {
        while (true) {
            try {
                logger.debug("getting message from queue");
                Serializable serializable = messageActionController.getFromOutboundMessageQueue();
                logger.debug("got message from queue");
                messageActionController.sendMessage(serializable);
            } catch (InterruptedException e) {

            } catch (Exception e) {
                logger.error(Thread.currentThread().getName(), e);
            }
        }
    }
}
