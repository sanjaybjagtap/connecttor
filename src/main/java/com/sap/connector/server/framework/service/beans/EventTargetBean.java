package com.sap.connector.server.framework.service.beans;

import com.sap.connector.server.framework.service.plugins.AbstractPlugin;

import java.io.Serializable;
import java.lang.reflect.Method;

//@Data
public class EventTargetBean {
    private AbstractPlugin pluginObject;
    private Method method;
    private Serializable messageObject;

    public AbstractPlugin getPluginObject() {
        return pluginObject;
    }

    public void setPluginObject(AbstractPlugin pluginObject) {
        this.pluginObject = pluginObject;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public Serializable getMessageObject() {
        return messageObject;
    }

    public void setMessageObject(Serializable messageObject) {
        this.messageObject = messageObject;
    }
}
