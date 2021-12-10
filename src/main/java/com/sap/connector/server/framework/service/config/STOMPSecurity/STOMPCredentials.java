package com.sap.connector.server.framework.service.config.STOMPSecurity;

public class STOMPCredentials {
    String clientAddress;
    String extra;


    public STOMPCredentials(String clientAddress) {
        this.clientAddress = clientAddress;
    }

    public String getClientAddress() {
        return clientAddress;
    }

    public void setClientAddress(String clientAddress) {
        this.clientAddress = clientAddress;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }
}
