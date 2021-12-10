//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2001.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.util.Calendar;
import java.util.Date;

import com.ffusion.ffs.bpw.db.DBInstructionType;
import com.ffusion.ffs.bpw.db.ExternalTransferAccount;
import com.ffusion.ffs.bpw.db.SmartCalendar;
import com.ffusion.ffs.bpw.interfaces.AuditLogConsts;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.CutOffInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.ExtTransferAcctInfo;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.handlers.IExternalTransferAdapter;
import com.ffusion.ffs.bpw.util.resources.BPWLocaleUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.scheduling.constants.ScheduleConstants;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.ffusion.util.ILocalizable;
import com.ffusion.util.logging.AuditLogTranTypes;
import com.sap.banking.common.interceptors.PerfLoggerUtil;




//=====================================================================
//
// This class contains a callback handler that is registered in the 
// InstructionType table.  The registered callback handler will be called
// by the Scheduling engine for the ACH files Processing.
//
//=====================================================================

public class ExternalTransferHandler implements
com.ffusion.ffs.bpw.interfaces.DBConsts,FFSConst,
com.ffusion.ffs.bpw.interfaces.BPWResource {

    private final String TRANSFER_ADAPTER_IMPL_NAME = "com.ffusion.ffs.bpw.fulfill.achadapter.ExternalTransferAdapter";
    private IExternalTransferAdapter _externalXferAdapter = null;
    private boolean _okToCall = false;  
    private final int _logLevel;
    private final PropertyConfig propertyConfig;

    public ExternalTransferHandler(){
        propertyConfig = (PropertyConfig)FFSRegistry.lookup( BPWResource.PROPERTYCONFIG);
        _logLevel = propertyConfig.LogLevel;
    }

//=====================================================================
// Description: This method is called by the Scheduling engine
//=====================================================================
    public void eventHandler(
                            int eventSequence,
                            EventInfoArray evts,
                            FFSConnectionHolder dbh )
    throws Exception
    {
        String currMethodName = "ExternalTransferHandler.eventHandler: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(currMethodName,start);
        FFSDebug.log(currMethodName + " begin, eventSeq=" + eventSequence, PRINT_DEV);

        if ( eventSequence == ScheduleConstants.EVT_SEQUENCE_FIRST ) {
            firstEventHandler();
        } else if ( eventSequence == ScheduleConstants.EVT_SEQUENCE_LAST ) {
            lastEventHandler(dbh,evts, false, false); // false:  a new submit; false, not crash recover
        }
        FFSDebug.log(currMethodName + " end", PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(currMethodName, start, uniqueIndex);
    }


    public void resubmitEventHandler(
                                    int eventSequence,
                                    EventInfoArray evts,
                                    FFSConnectionHolder dbh )
    throws Exception
    {
    	String methodName = "ExternalTransferHandler.resubmitEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log("=== ExternalTransferHandler.resubmitEventHandler: begin, eventSeq="
                     + eventSequence
                     + ",length="
                     + evts._array.length
                     + ",instructionType="
                     + evts._array[0].InstructionType);
        if ( eventSequence == ScheduleConstants.EVT_SEQUENCE_FIRST ) {
            firstEventHandler();
        } else if ( eventSequence == ScheduleConstants.EVT_SEQUENCE_LAST ) {

            // check whether this method is called by re-run cut off or crash recovery
            boolean reRunCutOff = false;
            boolean crashRecovery = false;

            if (evts._array[0].ScheduleFlag == ScheduleConstants.SCH_FLAG_RESUBMIT ) {

                reRunCutOff = true;
            } else {
                crashRecovery = true;
            }


            lastEventHandler(dbh,evts, reRunCutOff, crashRecovery ); // true:  a re-run cutoff
        }
        FFSDebug.log("=== ExternalTransferHandler.resubmitEventHandler: end");
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    } 

    public void firstEventHandler() throws Exception
    {
    	String methodName = "ExternalTransferHandler.firstEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        _okToCall = true;            
        Class classDefinition = Class.forName( TRANSFER_ADAPTER_IMPL_NAME );
        _externalXferAdapter = (IExternalTransferAdapter) classDefinition.newInstance();

        // initialize external trasnfer adapter
        _externalXferAdapter.start();
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    public void lastEventHandler(FFSConnectionHolder dbh,
                                 EventInfoArray evts,
                                 boolean reRunCutOff,
                                 boolean crashRecovery ) throws Exception
    {
    	String methodName = "ExternalTransferHandler.lastEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
    	
        if (_okToCall) {
            this.updateMaturedExtAccountPrenoteStatus(dbh, evts._array[0].FIId );
            try {
                // Invoke only when there are events
                if ( ( evts != null ) && ( evts._array != null )  ) {

                    if ( evts._array[0] != null ) {
                        if ( ( evts._array[0].InstructionType != null ) &&
                             ( evts._array[0].InstructionType.compareTo(  ETFTRN ) == 0 ) ) {
                            if (evts._array[0].cutOffId != null) {
                                CutOffInfo cutOffInfo = new CutOffInfo();                                    
                                cutOffInfo.setCutOffId( evts._array[0].cutOffId );
                                cutOffInfo = DBInstructionType.getCutOffById(dbh,cutOffInfo);
                                if (cutOffInfo.getStatusCode() == DBConsts.SUCCESS) {
                                    _externalXferAdapter.processOneCutOff(dbh, cutOffInfo, 
                                                                          evts._array[0].FIId, 
                                                                          evts._array[0].processId,
                                                                          evts._array[0].createEmptyFile,
                                                                          reRunCutOff,
                                                                          crashRecovery );
                                } else {
                                }

                            } else {
                                // Run Now request
                                _externalXferAdapter.processRunNow(dbh, 
                                                                   evts._array[0].FIId, 
                                                                   evts._array[0].processId,
                                                                   evts._array[0].createEmptyFile,
                                                                   reRunCutOff,
                                                                   crashRecovery );
                            }
                        } else {
                            FFSDebug.log("Invalid InstructionType = " + evts._array[0].InstructionType, PRINT_ERR);
                            FFSDebug.log("This instruction is skipped. Id: " + evts._array[0].InstructionID, PRINT_ERR);                               
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
                throw e;
            }
        }
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    /**
     * Update all fiid's matured ext account prenote status.
     * 
     * @param dbh
     * @param fiId   FIID
     * @exception Exception
     */
    public void updateMaturedExtAccountPrenoteStatus(FFSConnectionHolder dbh,
                                                     String fiId)
    throws Exception {

        String currMethodName = "ExternalTransferHandler.updateMaturedExtAccountPrenoteStatus: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(currMethodName,start);
        FFSDebug.log(currMethodName + "begins", PRINT_DEV);
        // get today
        Calendar cal = Calendar.getInstance();
        java.text.SimpleDateFormat s = new java.text.SimpleDateFormat("yyyyMMdd");
        String todayStr = s.format(cal.getTime());
        int startDateInt = Integer.parseInt( todayStr );
        int prenoteBusinessDays = DBConsts.DEFAULT_BPW_ACH_PAYEE_PRENOTE_BUSINESS_DAYS;
        try {
            // Default is BPW managed customer
            String prenoteBusinessDaysSTR =
            propertyConfig.otherProperties.getProperty(
                                DBConsts.BPW_ACH_PAYEE_PRENOTE_BUSINESS_DAYS,
                                "" + DBConsts.DEFAULT_BPW_ACH_PAYEE_PRENOTE_BUSINESS_DAYS);

            prenoteBusinessDays = new Integer(prenoteBusinessDaysSTR).intValue();

        } catch (Exception ex) {
        	PerfLoggerUtil.stopPerfLogging(currMethodName, start, uniqueIndex);
        }

        // move to 6 previous business day
        for ( int i = 0; i < prenoteBusinessDays; i++ ) {
            startDateInt = SmartCalendar.getBusinessDay(fiId, startDateInt, false ); // false: previous
        }

        // String: 20031011
        String matureDateStr = ( new Integer(startDateInt ) ).toString();

        // Date: 20031011
        Date matureDate = s.parse( matureDateStr );

        // parse to validate format of duedate
        // Date: 2003/10/11
        java.text.SimpleDateFormat s2 = new java.text.SimpleDateFormat(DBConsts.LASTREQUESTTIME_DATE_FORMAT );
        s2.setLenient(false);
        String formattedMatureDateStr = s2.format( matureDate );

        // get all mature Deposit Location if the log level is statusupdate
        ExtTransferAcctInfo [] acctInfos = null;
        if (_logLevel >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE) {
            //acctInfos = ExternalTransferAccount.getMaturedExtTransferAcctInfo(dbh, matureDateStr);
        }

        //int matureExtAcctPrenote = ExternalTransferAccount.updateMaturedExtAcctPrenoteStatus( dbh,  formattedMatureDateStr );
        //FFSDebug.log(currMethodName + "matured ext account prenotes: " + matureExtAcctPrenote, PRINT_DEV);

        // log into auditLog: one by one
        int len = 0;
        if (acctInfos != null) {
            len = acctInfos.length;
        }
        if (_logLevel >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE && len > 0 ) {

            for (int i = 0; i < len; i++ )
            {
				ILocalizable msg = BPWLocaleUtil.getLocalizableMessage(AuditLogConsts.AUDITLOG_MSG_EXTERNALTRANSFER_EXT_XFER_HANDLER_UPDATE_PRENOTE_STATUS,
														  			   null,
														  			   BPWLocaleUtil.EXTERNALTRANSFER_AUDITLOG_MESSAGE);
                ExternalTransferAccount.logExtTransferAccountTransAuditLog(dbh,
                                                                           acctInfos[i],
                                                                           msg,
                                                                           "Update prenote status of an External Account",
                                                                           AuditLogTranTypes.BPW_EXTERNALTRANSFERHANDLER); 

            }
        }

        FFSDebug.log(currMethodName + " ends", PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(currMethodName, start, uniqueIndex);
    }
}
