package com.sap.connector.server.plugins.factory.ocb.mock;

import com.sap.banking.common.notification.exceptions.NotifierServiceException;
import com.sap.banking.common.notification.interfaces.NotificationMessage;
import com.sap.banking.common.notification.services.interfaces.NotifierService;

import java.io.Serializable;

public class NotifierMockImpl implements NotifierService {

    @Override
    public <V extends Serializable> void notify(NotificationMessage<V> notificationMessage) throws NotifierServiceException {

    }

    @Override
    public <V extends Serializable> void notifyClusters(String[] strings, NotificationMessage<V> notificationMessage) throws NotifierServiceException {

    }

    @Override
    public void shutdown() {

    }

    @Override
    public void forceShutdownNow() {

    }

    @Override
    public int purgeNotificationMessages(int i) throws NotifierServiceException {
        return 0;
    }

    @Override
    public int deleteNotificationMessages(int i) throws NotifierServiceException {
        return 0;
    }
}
