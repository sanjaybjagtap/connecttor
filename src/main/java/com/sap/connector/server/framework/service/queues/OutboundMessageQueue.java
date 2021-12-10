package com.sap.connector.server.framework.service.queues;

import java.io.Serializable;
import java.util.concurrent.LinkedBlockingDeque;

public class OutboundMessageQueue implements MessageQueue {

    private final LinkedBlockingDeque<Serializable> queue;

    public OutboundMessageQueue(LinkedBlockingDeque<Serializable> queue) {
        this.queue = queue;
    }

    @Override
    public <T extends Serializable> T getMessage() throws InterruptedException {
        return (T)queue.take();
    }

    @Override
    public <T extends Serializable> boolean addMessage(T message) {
        return queue.add(message);
    }

    @Override
    public int getMessageCount() {
        return queue.size();
    }

    @Override
    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

}
