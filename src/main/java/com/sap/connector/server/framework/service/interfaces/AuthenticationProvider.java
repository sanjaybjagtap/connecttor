package com.sap.connector.server.framework.service.interfaces;

public interface AuthenticationProvider {
    boolean isAuthenticated(String key);
}
