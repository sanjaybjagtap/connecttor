package com.sap.connector.server.framework.service.beans;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class TransactionStatusBean {
    UUID txnId;
    String sourceServer;
    String status;
    Serializable inbound;
    RemoteActionMessageBean remoteActionMessageBean;
    LocalDateTime recievedTime;
    LocalDateTime processingCompleteTime;
    String recievedTimeForDisplay;
    String processingCompleteTimeForDisplay;
    String[] exceptionStack;
    long timeTookForProcessing;

    public TransactionStatusBean() {
    }

    public UUID getTxnId() {
        return txnId;
    }

    public void setTxnId(UUID txnId) {
        this.txnId = txnId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }


    public Serializable getInbound() {
        return inbound;
    }

    public void setInbound(Serializable inbound) {
        this.inbound = inbound;
    }

    public RemoteActionMessageBean getRemoteActionMessageBean() {
        return remoteActionMessageBean;
    }

    public void setRemoteActionMessageBean(RemoteActionMessageBean remoteActionMessageBean) {
        this.remoteActionMessageBean = remoteActionMessageBean;
    }

    public LocalDateTime getRecievedTime() {
        return recievedTime;
    }

    public void setRecievedTime(LocalDateTime recievedTime) {
        this.recievedTime = recievedTime;
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        this.recievedTimeForDisplay = recievedTime.format(dateTimeFormatter);
    }

    public LocalDateTime getProcessingCompleteTime() {
        return processingCompleteTime;
    }

    public void setProcessingCompleteTime(LocalDateTime processingCompleteTime) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        this.processingCompleteTime = processingCompleteTime;
        this.processingCompleteTimeForDisplay = processingCompleteTime.format(dateTimeFormatter);
    }

    public long getTimeTookForProcessing() {
        return timeTookForProcessing;
    }

    public void setTimeTookForProcessing(long timeTookForProcessing) {
        this.timeTookForProcessing = timeTookForProcessing;
    }

    public String getRecievedTimeForDisplay() {
        return recievedTimeForDisplay;
    }

    public void setRecievedTimeForDisplay(String recievedTimeForDisplay) {
        this.recievedTimeForDisplay = recievedTimeForDisplay;
    }

    public String getProcessingCompleteTimeForDisplay() {
        return processingCompleteTimeForDisplay;
    }

    public void setProcessingCompleteTimeForDisplay(String processingCompleteTimeForDisplay) {
        this.processingCompleteTimeForDisplay = processingCompleteTimeForDisplay;
    }

    public String getSourceServer() {
        return sourceServer;
    }

    public void setSourceServer(String sourceServer) {
        this.sourceServer = sourceServer;
    }

    public String[] getExceptionStack() {
        return exceptionStack;
    }

    public void setExceptionStack(String[] exceptionStack) {
        this.exceptionStack = exceptionStack;
    }
}
