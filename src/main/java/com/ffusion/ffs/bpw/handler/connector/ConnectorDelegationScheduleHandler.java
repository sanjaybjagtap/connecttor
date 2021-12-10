package com.ffusion.ffs.bpw.handler.connector;

import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.InstructionType;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.sap.banking.bpw.beans.EventWrapperBean;
import com.sap.banking.connector.beans.message.RemoteActionMessageBean;
import com.sap.banking.connector.bo.interfaces.ConnectorInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectorDelegationScheduleHandler implements BPWScheduleHandler {
    Logger logger = LoggerFactory.getLogger(ConnectorDelegationScheduleHandler.class);

    InstructionType instructionType;
    ConnectorInterface connectorInterface;

    public ConnectorDelegationScheduleHandler() {
    }

    public ConnectorDelegationScheduleHandler(InstructionType instructionType, ConnectorInterface connectorInterface) {
        this.instructionType = instructionType;
        this.connectorInterface = connectorInterface;
        logger.info("Delegation handler created for instruction : "+instructionType.InstructionType);
    }

    @Override
    public void eventHandler(int eventSequence, EventInfoArray evts, FFSConnectionHolder dbh) throws Exception {
        EventWrapperBean eventWrapperBean = new EventWrapperBean(eventSequence,evts);
        eventWrapperBean.setInstructionType(instructionType.InstructionType);
        eventWrapperBean.setHandlerClassName(instructionType.HandlerClassName);
        RemoteActionMessageBean remoteActionMessageBean = new RemoteActionMessageBean();
        remoteActionMessageBean.withMessage(eventWrapperBean);
        remoteActionMessageBean.setModuleName("bpw");
        remoteActionMessageBean.setActionName("submitScheduleEvent");
        remoteActionMessageBean.setUserId("BPW_CLUSTER");
        connectorInterface.submitSyncRequest(remoteActionMessageBean);
    }

    @Override
    public void resubmitEventHandler(int eventSequence, EventInfoArray evts, FFSConnectionHolder dbh) throws Exception {
        EventWrapperBean eventWrapperBean = new EventWrapperBean(eventSequence,evts);
        eventWrapperBean.setInstructionType(instructionType.InstructionType);
        eventWrapperBean.setHandlerClassName(instructionType.HandlerClassName);
        RemoteActionMessageBean remoteActionMessageBean = new RemoteActionMessageBean();
        remoteActionMessageBean.withMessage(eventWrapperBean);
        remoteActionMessageBean.setModuleName("bpw");
        remoteActionMessageBean.setActionName("reSubmitScheduleEvent");
        remoteActionMessageBean.setUserId("BPW_CLUSTER");
        connectorInterface.submitSyncRequest(remoteActionMessageBean);
    }
}
