package com.sap.connector.server.framework.service.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.connector.server.framework.service.beans.EventTargetBean;
import com.sap.connector.server.framework.service.beans.EventTargetLookUpBean;
import com.sap.connector.server.framework.service.beans.RemoteActionMessageBean;
import com.sap.connector.server.framework.service.exception.ActionFailedException;
import com.sap.connector.server.framework.service.plugins.AbstractPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PluginUtils {
    Logger logger = LoggerFactory.getLogger(PluginUtils.class);

    @Autowired
    @Qualifier("availableTargets")
    private Map<EventTargetLookUpBean, Method> eventMap;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public EventTargetBean getTargetForEvent(RemoteActionMessageBean remoteActionMessageBean) throws ActionFailedException {
        EventTargetLookUpBean eventTargetLookUpBean = new EventTargetLookUpBean(remoteActionMessageBean.getActionName(),remoteActionMessageBean.getModuleName());
        Method targetMethod = null;
        if(eventMap.containsKey(eventTargetLookUpBean)) {
            EventTargetBean eventTargetBean = new EventTargetBean();
            targetMethod = eventMap.get(eventTargetLookUpBean);
            ObjectMapper mapper = new ObjectMapper();
            Serializable message = (Serializable) mapper.convertValue(remoteActionMessageBean.getMessage(),
                    targetMethod.getParameterTypes()[1]);
            eventTargetBean.setMessageObject(message);
            AbstractPlugin abstractPlugin = null;
            try {
                abstractPlugin = (AbstractPlugin) applicationContext.getBean(targetMethod.getDeclaringClass());
            } catch (BeansException e) {
                logger.warn("Couldn't find plugin in spring context! : "+ targetMethod.getDeclaringClass());
                try {
                    abstractPlugin = (AbstractPlugin) targetMethod.getDeclaringClass().newInstance();
                } catch (InstantiationException|IllegalAccessException instantiationException) {
                    throw new ActionFailedException("Couldn't instantiate the bean",instantiationException);
                }
                abstractPlugin.setMessagingTemplate(messagingTemplate);
            }
            eventTargetBean.setMethod(targetMethod);
            eventTargetBean.setPluginObject(abstractPlugin);
            return eventTargetBean;
        }
        logger.error("No suitable target for event found!");
        throw new ActionFailedException("No suitable target for event found!");
    }

    /**
     * Prettify an exception
     * @param ex
     * @return
     */
    public String[] populateStacktraceForTracking(Exception ex) {
        StackTraceElement[] stackTraceElements;
        Throwable exc;
        if(ex.getCause()!=null) {
            stackTraceElements = ex.getCause().getStackTrace();
            exc = ex.getCause();
        } else {
            stackTraceElements = ex.getStackTrace();
            exc =ex;
        }
        List<String> s = Arrays.asList(stackTraceElements).stream().limit(4).map(e->e.getClassName()+" :: "+e.getMethodName()+"(Line "+e.getLineNumber()+")").collect(Collectors.toList());
        s.add(0,exc.toString());
        return s.toArray(new String[0]);
    }

}
