package com.sap.connector.server.plugins.factory.ocb.mock;

import com.sap.banking.connector.beans.enums.RemoteActionMessageStatus;
import com.sap.banking.connector.beans.message.RemoteActionMessageBean;
import com.sap.banking.connector.bo.interfaces.ConnectorInterface;
import com.sap.banking.connector.exception.ActionFailedException;

import java.util.Collection;
import java.util.concurrent.TimeoutException;

public class ConnectorBOMock implements ConnectorInterface {
    @Override
    public RemoteActionMessageBean submitAsyncRequest(RemoteActionMessageBean remoteActionMessageBean) throws ActionFailedException {
        return null;
    }

    @Override
    public RemoteActionMessageBean submitSyncRequest(RemoteActionMessageBean remoteActionMessageBean) throws TimeoutException, ActionFailedException {
        return null;
    }

    @Override
    public RemoteActionMessageStatus getTransactionStatus(String s) {
        return null;
    }

    @Override
    public Collection<RemoteActionMessageBean> getTransactionMessages(String s) {
        return null;
    }

    @Override
    public Collection<RemoteActionMessageBean> getUserMessages(String s) {
        return null;
    }
}
