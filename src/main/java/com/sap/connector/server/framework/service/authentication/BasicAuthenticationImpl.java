package com.sap.connector.server.framework.service.authentication;

import com.sap.connector.server.framework.service.interfaces.AuthenticationProvider;
import org.apache.commons.codec.digest.DigestUtils;

public class BasicAuthenticationImpl implements AuthenticationProvider {

    private final String hashedToken;

    public BasicAuthenticationImpl(String hashedToken) {
        this.hashedToken = hashedToken;
    }

    @Override
    public boolean isAuthenticated(String key) {
        String sha256hex = DigestUtils.sha256Hex(key);
        return sha256hex.equals(hashedToken);
    }
}
