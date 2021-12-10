package com.sap.connector.server.plugins.handler;

import com.sap.connector.server.framework.service.plugins.AbstractPlugin;
import com.sap.connector.server.framework.service.annotation.EventExecutor;
import com.sap.connector.server.framework.service.annotation.EventTarget;
import com.sap.connector.server.framework.service.beans.MetadataBean;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
@EventExecutor(module = "core")
public class OCBStartupHandler extends AbstractPlugin {

    @EventTarget(event = "startup")
    public void doStartUpActions(MetadataBean metadataBean, Properties messageBean) {
    }
}
