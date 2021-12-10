package com.sap.connector.server.framework.service.beans;

import com.sap.banking.connector.beans.enums.RemoteActionMessageIntention;
import com.sap.banking.connector.beans.enums.RemoteActionMessageStatus;

import java.sql.Timestamp;
import java.util.UUID;

public abstract class MessageBean {
    private String originServer;
    private String userId;
    private UUID transactionId;
    private String connectionId;
    private boolean sync;
    private int retyCount;
    private Timestamp messageReceivedTimeStamp;
    private com.sap.banking.connector.beans.enums.RemoteActionMessageStatus status;
    private com.sap.banking.connector.beans.enums.RemoteActionMessageIntention messageIntention;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(UUID transactionId) {
        this.transactionId = transactionId;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    public boolean isSync() {
        return sync;
    }

    public void setSync(boolean sync) {
        this.sync = sync;
    }

    public com.sap.banking.connector.beans.enums.RemoteActionMessageIntention getMessageIntention() {
        return messageIntention;
    }

    public void setMessageIntention(RemoteActionMessageIntention messageIntention) {
        this.messageIntention = messageIntention;
    }

    public com.sap.banking.connector.beans.enums.RemoteActionMessageStatus getStatus() {
        return status;
    }

    public void setStatus(RemoteActionMessageStatus status) {
        this.status = status;
    }

    public String getOriginServer() {
        return originServer;
    }

    public void setOriginServer(String originServer) {
        this.originServer = originServer;
    }

    public int getRetyCount() {
        return retyCount;
    }

    public void incrementRetryCount() {
        this.retyCount++;
    }

    public void setRetyCount(int retyCount) {
        this.retyCount = retyCount;
    }

    public Timestamp getMessageReceivedTimeStamp() {
        return messageReceivedTimeStamp;
    }

    public void setMessageReceivedTimeStamp(Timestamp messageReceivedTimeStamp) {
        this.messageReceivedTimeStamp = messageReceivedTimeStamp;
    }
}
