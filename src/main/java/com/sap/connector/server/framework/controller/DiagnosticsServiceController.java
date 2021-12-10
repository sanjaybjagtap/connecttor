package com.sap.connector.server.framework.controller;

import com.sap.connector.server.framework.service.beans.EventTargetLookUpBean;
import com.sap.connector.server.framework.service.beans.TransactionStatusBean;
import com.sap.connector.server.framework.service.interfaces.DiagnosticsService;
import com.sap.connector.server.framework.service.queues.MessageQueue;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Controller class that collects and provides diagnostics information
 */
@Component
public class DiagnosticsServiceController implements DiagnosticsService {

    @Autowired
    @Qualifier("activeConnectionCount")
    AtomicInteger connectionCount;

    @Autowired
    @Qualifier("clientConnectionCount")
    Map<String,Integer> clientConnectionMap;

    @Autowired
    @Qualifier("txnStatusMap")
    Map<String, TransactionStatusBean> statusBeanMap;

    @Autowired
    @Qualifier("availableTargets")
    private Map<EventTargetLookUpBean, Method> eventMap;

    @Autowired
    @Qualifier("systemProperties")
    Properties properties;

    @Value("${app.servernode}")
    String serverName;

    @Autowired
    DataSource dataSource;

    @Autowired
    @Qualifier("inboundQueue")
    MessageQueue inMessageQueue;

    @Autowired
    @Qualifier("outboundQueue")
    MessageQueue outMssageQueue;

    @Autowired
    @Qualifier("inboundWatcherPool")
    ExecutorService inboundThreadPool;

    @Autowired
    @Qualifier("outboundWatcherPool")
    ExecutorService outboundThreadPool;

    @Autowired
    @Qualifier("heapHistory")
    List<Double> heapHistory;

    @Override
    public Map<String,Object> getServerInfo() {
        Map<String,Object> serverInfo = new HashMap<>();
        serverInfo.put("nodename",serverName);
        serverInfo.put("vmvendor",properties.get("java.vm.vendor"));
        serverInfo.put("vmname", properties.get("java.vm.name"));
        serverInfo.put("rtversion", properties.get("java.runtime.version"));
        serverInfo.put("vmpid", properties.get("PID"));

        if(dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource)dataSource;
            serverInfo.put("dbpoolname", String.valueOf(hikariDataSource.getHikariConfigMXBean().getPoolName()));
            serverInfo.put("dbmaxlifetime", String.valueOf(hikariDataSource.getHikariConfigMXBean().getMaxLifetime()));
            serverInfo.put("dbsize", String.valueOf(((HikariDataSource) dataSource).getHikariPoolMXBean().getTotalConnections()));
            serverInfo.put("dbactive", String.valueOf(((HikariDataSource) dataSource).getHikariPoolMXBean().getActiveConnections()));
            serverInfo.put("dbidle", String.valueOf(((HikariDataSource) dataSource).getHikariPoolMXBean().getIdleConnections()));
            serverInfo.put("dbawaiting", String.valueOf(((HikariDataSource) dataSource).getHikariPoolMXBean().getThreadsAwaitingConnection()));
        }

        serverInfo.put("inQSize", String.valueOf(inMessageQueue.getMessageCount()));
        serverInfo.put("inQRemaining", String.valueOf(inMessageQueue.remainingCapacity()));
        serverInfo.put("outQSize", String.valueOf(outMssageQueue.getMessageCount()));
        serverInfo.put("outQRemaining", String.valueOf(outMssageQueue.remainingCapacity()));
        serverInfo.put("activeConnection", String.valueOf(connectionCount.get()));

        long heapSize = Runtime.getRuntime().totalMemory();
        long heapMaxSize = Runtime.getRuntime().maxMemory();
        long heapFreeSize = Runtime.getRuntime().freeMemory();
        serverInfo.put("heapCurrent", formatSize(heapSize));
        serverInfo.put("heapMax", formatSize(heapMaxSize));
        serverInfo.put("heapFree", formatSize(heapFreeSize));

        long threadCount = ManagementFactory.getThreadMXBean().getThreadCount();
        serverInfo.put("threadCount", String.valueOf(threadCount));
        serverInfo.put("heapHistory",heapHistory);
        if(outboundThreadPool instanceof ThreadPoolExecutor) {
            serverInfo.put("outTPTotal", String.valueOf(((ThreadPoolExecutor)outboundThreadPool).getPoolSize()));
            serverInfo.put("outTPActive", String.valueOf(((ThreadPoolExecutor)outboundThreadPool).getActiveCount()));
        }

        if(inboundThreadPool instanceof ThreadPoolExecutor) {
            serverInfo.put("inTPTotal", String.valueOf(((ThreadPoolExecutor)inboundThreadPool).getPoolSize()));
            serverInfo.put("inTPActive", String.valueOf(((ThreadPoolExecutor)inboundThreadPool).getActiveCount()));
        }

        return serverInfo;
    }

    @Override
    public Map<String,TransactionStatusBean> getStatusBeanMap() {
        return statusBeanMap;
    }

    @Override
    public List<TransactionStatusBean> getPendingTxn() {
        return statusBeanMap.entrySet()
                .parallelStream()
                .filter(v -> !v.getValue().getStatus().equals("FINISHED"))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getEvents() {
        return eventMap.keySet()
                .stream()
                .map(e->e.getModule()+":"+e.getEvent()+":"+eventMap.get(e).getDeclaringClass()+" # "+eventMap.get(e).getName()+":"+ Arrays.asList(eventMap.get(e).getParameterTypes()))
                .collect(Collectors.toList());
    }

    @Override
    public void snapHeapMemory() {
        if (heapHistory.size() > 60) {
            heapHistory.remove(0);
        }
        long heapSize = Runtime.getRuntime().totalMemory();
        heapHistory.add(formatSize(heapSize));
    }

    @Override
    public Map<String, Integer> getClientList() {
        return clientConnectionMap;
    }

    private static double formatSize(long v) {
        if (v < 1024) return v;
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return Double.parseDouble(String.format("%.1f", (double)v / (1L << (z*10))));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startHeapSnapSchedule() {
        System.out.println("Starting metrics collection");
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
        executorService.scheduleAtFixedRate(()->{this.snapHeapMemory();},0,10, TimeUnit.SECONDS);
        System.out.println("Metrics collection is running");
    }
}
