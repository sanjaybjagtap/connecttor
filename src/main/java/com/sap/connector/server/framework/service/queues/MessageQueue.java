package com.sap.connector.server.framework.service.queues;

import java.io.Serializable;

public interface MessageQueue {
    <T extends Serializable> T getMessage() throws InterruptedException;
    <T extends Serializable> boolean addMessage(T message);
    int getMessageCount();
    int remainingCapacity();
}
