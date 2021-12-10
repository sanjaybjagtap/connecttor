//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2001.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.math.BigDecimal;

import com.ffusion.beans.wiretransfers.WireDefines;
import com.ffusion.ffs.bpw.audit.TransAuditLog;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.db.Wire;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.OFXException;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.WireInfo;
import com.ffusion.ffs.bpw.interfaces.util.AccountUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.scheduling.constants.ScheduleConstants;
import com.ffusion.ffs.scheduling.db.ScheduleInfo;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.ffusion.util.logging.AuditLogRecord;
import com.ffusion.util.logging.AuditLogTranTypes;
import com.sap.banking.common.interceptors.PerfLoggerUtil;


//=====================================================================
//
// This class contains a callback handler that is registered in the
// InstructionType table.  The registered callback handler will be called
// by the Scheduling engine for the Recurring Funds Approval Processing.
//  1.  Check the status of the InterInstruction, if the status is
//      FUNDSALLOCATED, FAILEDON, NOFUNDSON, quit.
//
//==============================================================================

public class RecWireApprovalHandler implements FFSConst, DBConsts, BPWResource, BPWScheduleHandler {


    private String          _batchNum = null;
    private final String BATCHKEYPREFIX = "RecWireApproval_";

    // private static  HashMap _achInfoList = new HashMap();
    private PropertyConfig  _propertyConfig = null;
    private final boolean         _okToCall = false;

    private int audit_Level = 0;


    public RecWireApprovalHandler() {
        _propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);
        // get a handle to the backend system

        //Get Audit level
        String level = null;
        try {
            level = _propertyConfig.otherProperties.getProperty(
                                                               WIRE_AUDIT_OPTION);

            if (level == null) {
                audit_Level = DBConsts.AUDITLOGLEVEL_NONE;
            } else {
                audit_Level = Integer.parseInt( level );
            }
        } catch (Exception e) {
            FFSDebug.log("WireApprovalHandler. Invalid Audit log level value",
                         level, PRINT_ERR);
            audit_Level = DBConsts.AUDITLOGLEVEL_NONE;
        }
    }

    /**
      * Callback method for event processing
      * @param eventSequence event sequence number
      * @param evts array of event information objects
      * @param dbh Database connection holder
      * @exception Exception
      */
    public void eventHandler(int eventSequence, EventInfoArray evts,
                             FFSConnectionHolder dbh) throws Exception {
    	String methodName = "RecWireApprovalHandler.eventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);

        FFSDebug.log("RecWireApprovalHandler.eventHandler: begin, eventSeq: "
                     + eventSequence
                     + ",length: " + evts._array.length, PRINT_DEV);
        processEvents(eventSequence,evts,dbh, false); // possible dup false:
        FFSDebug.log("RecWireApprovalHandler.eventHandler: end", PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    /**
      * Callback method for handling resubmitted events
      * @param eventSequence event sequence number
      * @param evts array of event information objects
      * @param dbh Database connection holder
      * @exception Exception
      */
    public void resubmitEventHandler(int eventSequence, EventInfoArray evts,
                                     FFSConnectionHolder dbh) throws Exception {
    	String methodName = "RecWireApprovalHandler.resubmitEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);

        FFSDebug.log("RecWireApprovalHandler.resubmitEventHandler: begin, eventSeq: "
                     + eventSequence
                     + ",length: " + evts._array.length, PRINT_DEV);

        processEvents(eventSequence,evts,dbh, true); // possible dup true:
        FFSDebug.log("RecWireApprovalHandler.resubmitEventHandler: end", PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }


    /**
      * Callback method for event processing
      * @param eventSequence event sequence number
      * @param evts array of event information objects
      * @param dbh Database connection holder
      * @exception Exception
      */
    private void processEvents(int eventSequence,
                               EventInfoArray evts,
                               FFSConnectionHolder dbh,
                               boolean possibleDup) throws Exception {


        final String methodName = "RecWireApprovalHandler.processEvents: ";

        FFSDebug.log(methodName,  "begin, eventSeq: " + eventSequence
                     + ",length: " + evts._array.length, PRINT_DEV);

        try {

            _batchNum = DBUtil.getNextIndexString(DBConsts.BATCH_DB_TRANSID);

            // Do event sequence
            if (eventSequence == 0) {   // FIRST sequence
            } else if (eventSequence == 1) {   // NORMAL sequence

                PropertyConfig propertyConfig =
                (PropertyConfig)FFSRegistry.lookup(BPWResource.PROPERTYCONFIG);

                for (int i = 0; i < evts._array.length; i++) {
                    String srvrTID  = evts._array[i].InstructionID;
                    String eventID  = evts._array[i].EventID;
                    String batchKey = BATCHKEYPREFIX + _batchNum;

                    WireInfo wireInfo = new WireInfo();
                    wireInfo.setSrvrTid(srvrTID);
                    wireInfo.setEventId(eventID);
                    wireInfo.setEventSequence(eventSequence);
                    wireInfo.setPossibleDuplicate(possibleDup);
                    wireInfo.setDbTransKey(batchKey);
                    wireInfo.setFiID(evts._array[i].FIId);
                    wireInfo.setWireType(evts._array[i].InstructionType);

                    wireInfo = Wire.getWireInfo(dbh, wireInfo);

                    if (wireInfo == null) {
                        String err = methodName + " Failed: could not find " +
                                     "WireInfo for srvrTID: " + srvrTID + " in BPW_WireInfo";
                        FFSDebug.log(err, PRINT_ERR);
                        continue;
                    }

                    //populate CustomerInfo and FIInfo members of the WireInfo
                    //object
                    wireInfo = Wire.populateCustomerAndFIInfo(dbh, wireInfo);

                    ScheduleInfo sinfo = ScheduleInfo.getScheduleInfo(evts._array[i].FIId,
                                                                      RECWIREAPPROVALTRN, srvrTID, dbh);
                    if (sinfo != null &&
                        sinfo.StatusOption != ScheduleConstants.SCH_STATUSOPTION_CLOSE_SCHEDULED) {

                        String status = wireInfo.getPrcStatus();
                        if (WILLPROCESSON.equalsIgnoreCase(status)) {
                            continue;
                        } else if (!NOFUNDS.equalsIgnoreCase(status) &&
                                   !NOFUNDSON_NOTIF.equalsIgnoreCase(status)) {
                            ScheduleInfo.cancelSchedule(dbh, RECWIREAPPROVALTRN, srvrTID);

                            if (audit_Level >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE) {

                                //Do audit logging
                                doAuditLogging( dbh, wireInfo,
                                                DBConsts.RECWIREAPPR_HANDLER_RECWIREAPPRTRN_CANCELED_DESC);
                            }
                            continue;
                        }

                        // create WIREAPPROVALTRN
                        ScheduleInfo.moveNextInstanceDate(sinfo, -1);  // take yesterday of sinfo
                        ScheduleInfo info = createScheduleInfo(sinfo.NextInstanceDate, sinfo.LogID, wireInfo.getFiID());
                        String scheduleID = ScheduleInfo.createSchedule(dbh, WIREAPPROVALTRN, srvrTID, info);

                        if (audit_Level >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE) {

                            //Do audit logging
                            doAuditLogging( dbh, wireInfo,
                                            DBConsts.RECWIREAPPR_HANDLER_WIREAPPRTRN_CREATE_DESC);
                        }
                    }
                }

            } else if (eventSequence == 2) {   // LAST sequence
            } else if (eventSequence == 3) {   // Batch-Start sequence
            } else if (eventSequence == 4) {   // Batch-End sequence
            }
        } catch (Throwable exc) {
            String err = methodName + " Faield. error: " + FFSDebug.stackTrace(exc);
            FFSDebug.log(err, PRINT_ERR);
            throw new FFSException(err);
        }

        FFSDebug.log(methodName,  " end", PRINT_DEV);
    }

    //=====================================================================

    private ScheduleInfo createScheduleInfo(int startdate,
                                            String logID, String fiID) throws Exception {

        ScheduleInfo info = new ScheduleInfo();
        info.FIId = fiID;
        info.Status = ScheduleConstants.SCH_STATUS_ACTIVE;
        info.Frequency = ScheduleConstants.SCH_FREQ_ONCE;
        info.StartDate = startdate;
        info.NextInstanceDate = startdate;

        info.InstanceCount = 1;
        info.LogID = logID;
        info.CurInstanceNum = 1;
        return info;
    }

    /**
     * Do Audit logging
     *
     * @param wireInfo
     * @param error - If logging for error or exception
     * @exception Exception
     */
    private void doAuditLogging( FFSConnectionHolder dbh, WireInfo wireInfo,
                                 String desc)
    throws Exception {

        String curMethodName = "RecWireApprovalHandler.doAuditLogging";

        String logDesc = desc;
        String toAcctId = null;
        String toAcctRTN = null;
        String fromAcct = null;
        String amount = null;
        int businessId = 0;
        AuditLogRecord auditLogRecord = null;

        if (wireInfo == null) {
            return;
        }

        //Do Audit logging here
        try {
            amount = wireInfo.getAmount();

            if ( (amount == null) || (amount.trim().length() == 0) ) {
                amount = "-1";
            }

            if (wireInfo.getWirePayeeInfo() != null) {
                if (wireInfo.getWireDest().equals(WireDefines.WIRE_HOST)) {
                    toAcctId = WireDefines.WIRE_HOST;
                } else {
                    toAcctId = AccountUtil.buildWirePayeeBankAcctId(wireInfo.getWirePayeeInfo());
                }

                if (wireInfo.getWirePayeeInfo().getBeneficiaryBankInfo() != null) {
                    toAcctRTN = wireInfo.getWirePayeeInfo().getBeneficiaryBankInfo().getFedRTN();
                }
            }

            if (wireInfo.getWireDest().equals(WireDefines.WIRE_HOST)) {
                fromAcct = WireDefines.WIRE_HOST;
            } else {
                fromAcct = AccountUtil.buildWireFromAcctId(wireInfo);
            }

            //Differentiate between consumer and business
            if ( (wireInfo.getCustomerID().equals(wireInfo.getUserId())) ||
                 (wireInfo.getCustomerID().equals(wireInfo.getSubmitedBy())) ) { //Consumer

                businessId = 0;
            } else { //Business
                businessId = Integer.parseInt(wireInfo.getCustomerID());
            }

            auditLogRecord = new AuditLogRecord(wireInfo.getUserId(), //userId
                                                null, //do not log agentId
                                                null, //do not log agentType
                                                logDesc,               //description
                                                wireInfo.getExtId(),
                                                AuditLogTranTypes.BPW_RECWIREAPPROVALTRN, //tranType
                                                businessId, //BusinessId
                                                new BigDecimal(amount),
                                                wireInfo.getOrigCurrency(),
                                                wireInfo.getSrvrTid(),
                                                wireInfo.getPrcStatus(),
                                                toAcctId,
                                                toAcctRTN,
                                                fromAcct,
                                                wireInfo.getFromBankId(),
                                                -1);

            TransAuditLog.logTransAuditLog( auditLogRecord,
                                            dbh.conn.getConnection());

        } catch (Exception ex) {
            String errDescrip = curMethodName + " failed " + ex;
            FFSDebug.log(errDescrip + FFSDebug.stackTrace(ex), PRINT_ERR);
            throw new FFSException(ex, errDescrip);
        }
    }
}
