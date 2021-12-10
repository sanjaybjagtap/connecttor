package com.sap.connector.server.framework.service.config.STOMPSecurity;

import java.security.Principal;

class StompPrincipal implements Principal {

    private final String name;
    private String ip;

    StompPrincipal(String name) {
        this.name = name;
    }

    StompPrincipal(String name,String ip) {
        this.name = name;
        this.ip = ip;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getIp() {
        return ip;
    }
}
