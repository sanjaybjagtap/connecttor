package com.sap.connector.server.framework.service.beans;

import java.util.Objects;

public class EventTargetLookUpBean {
    private final String event;
    private final String module;

    public EventTargetLookUpBean(String event, String module) {
        this.event = event;
        this.module = module;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof EventTargetLookUpBean) {
            EventTargetLookUpBean eventTargetBean = (EventTargetLookUpBean)obj;
            return Objects.equals(this.event, eventTargetBean.event) && Objects.equals(this.module, eventTargetBean.module);
        } else {
            return obj == this;
        }
    }

    @Override
    public int hashCode() {
        return event.hashCode()+module.hashCode();
    }

    @Override
    public String toString() {
        return "EventTargetLookUpBean{" +
                "event='" + event + '\'' +
                ", module='" + module + '\'' +
                '}';
    }

    public String getEvent() {
        return event;
    }

    public String getModule() {
        return module;
    }

}
