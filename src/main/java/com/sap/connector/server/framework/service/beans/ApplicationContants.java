package com.sap.connector.server.framework.service.beans;

public interface ApplicationContants {
    String OUTBOUND_QUEUE = "/outbound/response_queue";
    String PRIMARY_INBOUND_QUEUE = "/inbound/recieve";
    String PREFIXED_PRIMARY_INBOUND_QUEUE = "app/"+PRIMARY_INBOUND_QUEUE;
    String ROLE_CONSOLE_USER = "CONSOLEUSER";
}
