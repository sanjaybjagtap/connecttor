package com.sap.connector.server.framework.service.interfaces;

import com.sap.connector.server.framework.service.beans.TransactionStatusBean;

import java.util.List;
import java.util.Map;

public interface DiagnosticsService {
    Map<String,Object> getServerInfo();

    Map<String, TransactionStatusBean> getStatusBeanMap();

    Map<String, Integer> getClientList();

    List<TransactionStatusBean> getPendingTxn();

    List<String> getEvents();

    void snapHeapMemory();
}
