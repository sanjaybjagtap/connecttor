package com.sap.connector.server.framework.service.beans.ocb;

import java.io.Serializable;
import java.util.Properties;

public class ConfigurationWrapperBean implements Serializable {
    private Properties commonProps;
    private Properties adaptorCommonProps;

    public ConfigurationWrapperBean() {
    }

    public ConfigurationWrapperBean(Properties commonProps, Properties adaptorCommonProps) {
        this.commonProps = commonProps;
        this.adaptorCommonProps = adaptorCommonProps;
    }

    public Properties getCommonProps() {
        return commonProps;
    }

    public void setCommonProps(Properties commonProps) {
        this.commonProps = commonProps;
    }

    public Properties getAdaptorCommonProps() {
        return adaptorCommonProps;
    }

    public void setAdaptorCommonProps(Properties adaptorCommonProps) {
        this.adaptorCommonProps = adaptorCommonProps;
    }
}
