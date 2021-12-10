package com.sap.connector.server.framework.service.beans;

import java.io.Serializable;

public class SimpleMessageBean extends MessageBean implements Serializable {
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public SimpleMessageBean() {
    }

    public SimpleMessageBean(String message) {
        this.message = message;
    }

}
