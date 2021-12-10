package com.sap.connector.server.plugins.factory.ocb.mock;

import com.sap.banking.common.cluster.interfaces.ClusterService;

import java.net.InetAddress;

public class ClusterMockImpl implements ClusterService {
    @Override
    public void init() throws Exception {

    }

    @Override
    public boolean isMCEMode() {
        return false;
    }

    @Override
    public String getCurrentClusterName() throws Exception {
        return "NO_OP_CLUSTER";
    }

    @Override
    public String getLocalNodeIPAddress() throws Exception {
        return InetAddress.getLocalHost().getHostAddress();
    }

    @Override
    public String getLocalNodeIdentifier() throws Exception {
        return null;
    }
}
