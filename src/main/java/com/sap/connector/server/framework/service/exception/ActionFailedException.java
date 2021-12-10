package com.sap.connector.server.framework.service.exception;

public class ActionFailedException extends Exception {
    public ActionFailedException(String message) {
        super(message);
    }

    public ActionFailedException(String message,Throwable e) {
        super(message,e);
    }
}
