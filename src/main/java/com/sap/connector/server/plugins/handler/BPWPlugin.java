package com.sap.connector.server.plugins.handler;

import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.interfaces.ScheduleCalloutInfo;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.scheduling.BPWSchedulerImpl;
import com.ffusion.ffs.scheduling.ScheduleRunnable;
import com.sap.banking.bpw.beans.EventWrapperBean;
import com.sap.connector.server.framework.service.beans.MessageBean;
import com.sap.connector.server.framework.service.beans.SimpleMessageBean;
import com.sap.connector.server.framework.service.plugins.AbstractPlugin;
import com.sap.connector.server.framework.service.annotation.EventExecutor;
import com.sap.connector.server.framework.service.annotation.EventTarget;
import com.sap.connector.server.framework.service.beans.MetadataBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@EventExecutor(module = "bpw")
public class BPWPlugin extends AbstractPlugin {
    Logger logger = LoggerFactory.getLogger(BPWPlugin.class);

    @Autowired
    Map<String, ScheduleRunnable> scheduleRunnableMap;

    @EventTarget(event = "scheduleBatchRun")
    public ScheduleCalloutInfo submitScheduleRun(MetadataBean metadataBean, EventWrapperBean eventWrapperBean) throws Exception {
        logger.info("BPWEvent scheduleBatchRUn received : "+eventWrapperBean.getInstructionType());
        String scheduleKey = eventWrapperBean.getFiid()+eventWrapperBean.getInstructionType();
        ScheduleCalloutInfo scheduleCalloutInfo = new ScheduleCalloutInfo();
        ScheduleRunnable scheduleRunnable = null;
        if(!scheduleRunnableMap.containsKey(scheduleKey)) {
            scheduleRunnable = new ScheduleRunnable(eventWrapperBean.getFiid(),
                    eventWrapperBean.getInstructionType(),
                    scheduleCalloutInfo,
                    false);
            scheduleRunnableMap.put(scheduleKey,scheduleRunnable);
        } else {
            scheduleRunnable = scheduleRunnableMap.get(scheduleKey);
        }
        scheduleRunnable.batchRun(Boolean.parseBoolean(eventWrapperBean.getPerformRecovery()),
                eventWrapperBean.getCutOffId(),
                eventWrapperBean.getProcessId());
        MessageBean messageBean = new SimpleMessageBean("Transfer is done!");
        this.sendNotificationResponse(metadataBean,messageBean);
        return scheduleCalloutInfo;
    }

    @EventTarget(event = "doImmediateProcessing")
    public String doImmediateProcessing(MetadataBean metadataBean, EventWrapperBean eventWrapperBean) throws Exception {
        FFSConnectionHolder ffsConnectionHolder = new FFSConnectionHolder();
        ffsConnectionHolder.conn = DBUtil.getConnection();

        this.sendNotificationResponse(metadataBean,
                new SimpleMessageBean("Starting Processing the instruction "+eventWrapperBean.getInstructionType()));

        try {
            new BPWSchedulerImpl().doImmediateProcessing(ffsConnectionHolder,
                    eventWrapperBean.getFiid(),
                    eventWrapperBean.getInstructionType(),
                    eventWrapperBean.getSrvrtids());
        } finally {
            DBUtil.freeConnection(ffsConnectionHolder.conn);
        }
        this.sendNotificationResponse(metadataBean, new SimpleMessageBean("Transfer is done!"));
        return "Done!";
    }

}
