package com.sap.connector.server.plugins.factory;
import com.ffusion.ffs.scheduling.ScheduleRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Configuration
public class BPWFactory {
Logger logger = LoggerFactory.getLogger(BPWFactory.class);

    @Bean
    Map<String, ScheduleRunnable> handlerMap() {
        return new ConcurrentHashMap<>();
    }
}
