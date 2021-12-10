package com.sap.connector.server.framework.service.spring;

import com.sap.connector.server.framework.service.annotation.EventExecutor;
import com.sap.connector.server.framework.service.annotation.EventTarget;
import com.sap.connector.server.framework.service.authentication.BasicAuthenticationImpl;
import com.sap.connector.server.framework.service.beans.EventTargetLookUpBean;
import com.sap.connector.server.framework.service.beans.TransactionStatusBean;
import com.sap.connector.server.framework.controller.MessageActionController;
import com.sap.connector.server.framework.service.config.STOMPSecurity.CustomHandshakeHandler;
import com.sap.connector.server.framework.service.exception.ServerIntegrityException;
import com.sap.connector.server.framework.service.interfaces.AuthenticationProvider;
import com.sap.connector.server.framework.service.plugins.AbstractPlugin;
import com.sap.connector.server.framework.service.queues.InboundMessageQueue;
import com.sap.connector.server.framework.service.queues.MessageQueue;
import com.sap.connector.server.framework.service.queues.OutboundMessageQueue;
import com.sap.connector.server.framework.service.utils.AutoFIFOConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.*;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.*;

@Configuration
public class ApplicationDefaultFactory {
    Logger log = LoggerFactory.getLogger(ApplicationDefaultFactory.class);

    @Value("${ocb.basic.hashedToken}")
    String hashedToken;

    @Value("${app.inbound.maxthreadpoolsize}")
    String inboundPoolSize;

    @Value("${app.outbound.maxthreadpoolsize}")
    String outboundPoolSize;

    @Bean(name = "inboundQueue")
    public MessageQueue inboundMsgQueue() {
        return new InboundMessageQueue(new LinkedBlockingDeque<>(10000));
    }

    @Bean(name = "outboundQueue")
    public MessageQueue outboundMsgQueue() {
        return new OutboundMessageQueue(new LinkedBlockingDeque<>(10000));
    }

    @Bean()
    @DependsOn({"inboundWatcherPool","outboundWatcherPool"})
    public MessageActionController messageActionController() {
        MessageActionController messageActionController = new MessageActionController(incomingMessageProcessorPool(),
                outgoingMessageProcessorPool(),
                inboundMsgQueue(),
                outboundMsgQueue(),
                inboundPoolSize,
                outboundPoolSize);
        messageActionController.prepareWatcherThreads();
        return messageActionController;
    }

    @Bean(name = "inboundWatcherPool")
    public ExecutorService incomingMessageProcessorPool() {
        ThreadPoolExecutor executorService = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        executorService.setMaximumPoolSize(Integer.parseInt(inboundPoolSize));
        executorService.setCorePoolSize(Integer.parseInt(inboundPoolSize));
        return executorService;
    }

    @Bean(name = "outboundWatcherPool")
    public ExecutorService outgoingMessageProcessorPool() {
        ThreadPoolExecutor executorService = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        executorService.setMaximumPoolSize(Integer.parseInt(outboundPoolSize));
        executorService.setCorePoolSize(Integer.parseInt(outboundPoolSize));
        return executorService;
    }

    @Bean
    CustomHandshakeHandler customHandshakeHandler(){
        return new CustomHandshakeHandler();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        return new BasicAuthenticationImpl(hashedToken);
    }

    @Bean(name = "availableTargets")
    public Map<EventTargetLookUpBean,Method> getMappedTargets() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        Class<? extends AbstractPlugin> clazz = null;
        scanner.addIncludeFilter(new AnnotationTypeFilter(EventExecutor.class));
        Map<EventTargetLookUpBean, Method> targets = new ConcurrentHashMap<>();
        EventTargetLookUpBean eventTargetBean;
        for (BeanDefinition bd : scanner.findCandidateComponents("com.sap")) {
            if(!AbstractPlugin.class.isAssignableFrom(Class.forName(bd.getBeanClassName()))) {
                log.error("Class "+bd.getBeanClassName()+" is annotated as a plugin class but doesn't extend com.sap.connector.server.framework.service.plugins.AbstractPlugin");
                log.error("The plugin wouldn't be registered");
            }
            clazz = (Class<? extends AbstractPlugin>) Class.forName(bd.getBeanClassName());
            for (final Method method : clazz.getDeclaredMethods()) {
                String module = clazz.getAnnotation(EventExecutor.class).module();
                if (method.isAnnotationPresent(EventTarget.class)) {
                    EventTarget annotInstance = method.getAnnotation(EventTarget.class);
                    eventTargetBean = new EventTargetLookUpBean(annotInstance.event(),module);
                    if(targets.containsKey(eventTargetBean)) {
                        log.error("Startup failed, duplicate event registration -> "+eventTargetBean);
                        throw new ServerIntegrityException("Duplicate event "+eventTargetBean);
                    }
                    targets.put(eventTargetBean,method);
                }
            }
        }
        log.info("Plugins registered, "+targets.size()+" targets found!");
        return targets;
    }

    @Bean(name="txnStatusMap")
    public Map<String, TransactionStatusBean> pendingMessagesMap() {
        return new AutoFIFOConcurrentMap<>(10);
    }

    /**
     * For demo purposes only
     * @return
     */
    @Bean
    @Profile("dev")
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurerAdapter() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**").allowedMethods("GET", "POST", "PUT", "DELETE").allowedOrigins("*")
                        .allowedHeaders("*");
            }
        };
    }

}
