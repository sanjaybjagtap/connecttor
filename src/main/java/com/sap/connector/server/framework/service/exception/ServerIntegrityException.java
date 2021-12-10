package com.sap.connector.server.framework.service.exception;

/**
 * Exception that represents any violation that leaves the server in an ambiguous state
 * Errors like duplicate events, malformed plugins etc qualify for this exception
 */
public class ServerIntegrityException extends RuntimeException{
    public ServerIntegrityException() {
    }

    public ServerIntegrityException(String message) {
        super(message);
    }

    public ServerIntegrityException(String message, Throwable cause) {
        super(message, cause);
    }
}
