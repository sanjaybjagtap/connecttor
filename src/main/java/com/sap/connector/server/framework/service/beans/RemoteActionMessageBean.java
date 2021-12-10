package com.sap.connector.server.framework.service.beans;


import com.sap.banking.connector.beans.enums.RemoteActionMessageIntention;
import com.sap.banking.connector.beans.enums.RemoteActionMessageStatus;

import java.io.Serializable;
import java.util.UUID;

public class RemoteActionMessageBean extends MessageBean implements Serializable {

    private Serializable message;

    private String moduleName;

    private String actionName;

    public RemoteActionMessageBean() {
    }

    public Serializable getMessage() {
        return message;
    }

    public void setMessage(Serializable message) {
        this.message = message;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getActionName() {
        return actionName;
    }

    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    public RemoteActionMessageBean withUser(String userId) {
        this.setUserId(userId);
        return this;
    }

    public RemoteActionMessageBean targetModule(String module) {
        this.setModuleName(module);
        return this;
    }

    public RemoteActionMessageBean targetAction(String actionName) {
        this.setActionName(actionName);
        return this;
    }

    public RemoteActionMessageBean withTransactionId(UUID id) {
        this.setTransactionId(id);
        return this;
    }

    public RemoteActionMessageBean sync(boolean isSync) {
        this.setSync(isSync);
        return this;
    }

    public RemoteActionMessageBean withMessage(Serializable messageObj) {
        this.setMessage(messageObj);
        return this;
    }

    public RemoteActionMessageBean withMessageIntention(RemoteActionMessageIntention status) {
        this.setMessageIntention(status);
        return this;
    }

    public RemoteActionMessageBean withMessageStatus(RemoteActionMessageStatus status) {
        this.setStatus(status);
        return this;
    }

    public RemoteActionMessageBean withConnectionId(String connectionId) {
        this.setConnectionId(connectionId);
        return this;
    }

    public RemoteActionMessageBean cloneForReply() {
        RemoteActionMessageBean remoteActionMessageBean = new RemoteActionMessageBean();
        return remoteActionMessageBean.withTransactionId(this.getTransactionId()).withUser(this.getUserId()).withConnectionId(this.getConnectionId());
    }

}
