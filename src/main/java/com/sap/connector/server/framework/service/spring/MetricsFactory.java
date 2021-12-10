package com.sap.connector.server.framework.service.spring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class MetricsFactory {

    @Bean("activeConnectionCount")
    public AtomicInteger connectionCount() {
        return new AtomicInteger();
    }

    @Bean("clientConnectionCount")
    public Map<String,Integer> clientConnectionCount() {
        return new ConcurrentHashMap<>();
    }

    @Bean(name="heapHistory")
    public List<Double> getHeapHistory() {
        return new LinkedList<>();
    }
}
