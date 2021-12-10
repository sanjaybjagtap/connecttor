package com.sap.connector.server.framework.service.beans;

import java.io.Serializable;
import java.util.UUID;

/**
 * Contains information about connection and message source information.
 */
public class MetadataBean implements Serializable {

    private String user;
    private UUID transactionID;
    private String connectionId;

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public UUID getTransactionID() {
        return transactionID;
    }

    public void setTransactionID(UUID transactionID) {
        this.transactionID = transactionID;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }
}
