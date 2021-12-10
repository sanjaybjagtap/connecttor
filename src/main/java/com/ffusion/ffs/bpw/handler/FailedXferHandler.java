//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2002.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import com.ffusion.ffs.bpw.audit.TransAuditLog;
import com.ffusion.ffs.bpw.custimpl.interfaces.BackendProvider;
import com.ffusion.ffs.bpw.custimpl.interfaces.FailedTransfer;
import com.ffusion.ffs.bpw.db.DBConnCache;
import com.ffusion.ffs.bpw.db.XferInstruction;
import com.ffusion.ffs.bpw.interfaces.AuditLogConsts;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.EventInfo;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.InstructionType;
import com.ffusion.ffs.bpw.interfaces.IntraTrnInfo;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.util.BPWRegistryUtil;
import com.ffusion.ffs.bpw.util.BPWUtil;
import com.ffusion.ffs.bpw.util.resources.BPWLocaleUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.scheduling.constants.ScheduleConstants;
import com.ffusion.ffs.scheduling.db.Event;
import com.ffusion.ffs.scheduling.db.EventInfoLog;
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
// by the Scheduling engine for the Failed Transfer Processing.
//
//=====================================================================

public class FailedXferHandler implements
com.ffusion.ffs.bpw.interfaces.DBConsts, FFSConst,
com.ffusion.ffs.bpw.interfaces.BPWResource, BPWScheduleHandler {
    private boolean _okToCall;

    public FailedXferHandler()
    {
    }

    //=====================================================================
    // eventHandler()
    // Description: This method is called by the Scheduling engine
    // Arguments: none
    // Returns:   none
    // Note:
    //=====================================================================
    //
    // This method passes the failed xfer information to the backend.
    // The are two parts of the process.
    // First it checks for performing crash recovery from the EventInfo record.
    // Second, in normal processing, it saves the data in the EventInfo record in case
    // it needs to do crash recovery.
    // The record from the EventInfo is copied over to the EventInfoLog for the purpose
    // of event resubmitting.
    // The EventInfo and EventInfoLog table are defined as follow:
    //      EventID - save the EventID of the EventInfo
    //      InstructionID - save the SrvrTID of the record
    //      InstructionType - save the InstructionType of the schedule event
    //      ScheduleFlag - save the following data for the failed xfers
    //                      ScheduleConstants.SCH_FLAG_FAILEDON
    //                      ScheduleConstants.SCH_FLAG_FUNDSFAILEDON
    //                      ScheduleConstants.SCH_FLAG_NOFUNDSON
    //      LogID - null
    //
    public void eventHandler(
                            int eventSequence,
                            EventInfoArray evts,
                            FFSConnectionHolder dbh )
    throws Exception
    {
        eventHandler(eventSequence, evts, dbh, false );
    }

    public void eventHandler(
                            int eventSequence,
                            EventInfoArray evts,
                            FFSConnectionHolder dbh,
                            boolean isRecover )
    throws Exception
    {
    	String methodName = "FailedXferHandler.eventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log("=== FailedXferHandler.eventHandler: begin, eventSeq=" + eventSequence, PRINT_DEV);
        if ( eventSequence == 0 ) {         // FIRST sequence
            _okToCall = false;
        } else if ( eventSequence == 1 ) {    // NORMAL sequence
            _okToCall = true;
        } else if ( eventSequence == 2 ) {    // LAST sequence
            if (_okToCall || isRecover) {
                String fiId = evts._array[0].FIId;
                String instructionType = evts._array[0].InstructionType;

                FailedTransfer failedTransfer = null;
                
                try{
         		   BackendProvider backendProvider = getBackendProviderService();
         		   failedTransfer = backendProvider.getFailedTransferInstance();
         		   
         	   } catch(Throwable t){
         		   FFSDebug.log("Unable to get FailedTransfer Instance" , PRINT_ERR);
         		  PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
         	   }

                recoverFromCrash(dbh, fiId, instructionType, ScheduleConstants.SCH_FLAG_FAILEDON, failedTransfer);
                recoverFromCrash(dbh, fiId, instructionType, ScheduleConstants.SCH_FLAG_FUNDSFAILEDON, failedTransfer);
                recoverFromCrash(dbh, fiId, instructionType, ScheduleConstants.SCH_FLAG_NOFUNDSON, failedTransfer);
                recoverFromCrash(dbh, fiId, instructionType, ScheduleConstants.SCH_FLAG_LIMIT_REVERT_FAILED, failedTransfer);
                recoverFromCrash(dbh, fiId, instructionType, ScheduleConstants.SCH_FLAG_LIMIT_CHECK_FAILED, failedTransfer);


                doFailedXfers(dbh, fiId, instructionType, FAILEDON, failedTransfer);
                doFailedXfers(dbh, fiId, instructionType, NOFUNDSON, failedTransfer);
                doFailedXfers(dbh, fiId, instructionType, FUNDSFAILEDON, failedTransfer);
                doFailedXfers(dbh, fiId, instructionType, LIMIT_REVERT_FAILED, failedTransfer);
                doFailedXfers(dbh, fiId, instructionType, LIMIT_CHECK_FAILED, failedTransfer);
            }
        } else if ( eventSequence == 3 ) {    // Batch-Start sequence
        } else if ( eventSequence == 4 ) {    // Batch-End sequence
        }
        FFSDebug.log("=== FailedXferHandler.eventHandler: end", PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

//=====================================================================
// resubmitEventHandler()
// Description: This method is called by the Scheduling engine during
//    event resubmission.  It will set the possibleDuplicate to true
//    before calling the ToBackend handler.
// Arguments: none
// Returns:   none
// Note:
//=====================================================================
    public void resubmitEventHandler(
                                    int eventSequence,
                                    EventInfoArray evts,
                                    FFSConnectionHolder dbh )
    throws Exception
    {
    	String methodName = "FailedXferHandler.resubmitEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log("=== FailedXferHandler.resubmitEventHandler: begin, eventSeq="
                     + eventSequence
                     + ",length="
                     + evts._array.length
                     + ",instructionType="
                     + evts._array[0].InstructionType, PRINT_DEV);
        if ( eventSequence == 0 ) {         // FIRST sequence
            _okToCall = false;
        } else if ( eventSequence == 1 ) {    // NORMAL sequence
            _okToCall = true;
        } else if ( eventSequence == 2 ) {    // LAST sequence
            int scheduleFlag = evts._array[0].ScheduleFlag; // retrieve scheduleFlag
            if (scheduleFlag == ScheduleConstants.SCH_FLAG_RESUBMIT) {
                if (_okToCall) {
                    String fiId = evts._array[0].FIId;
                    String instructionType = evts._array[0].InstructionType;
                    String logDate = evts._array[0].InstructionID; // retrieve logDate from InstructionID

                    FailedTransfer failedTransfer = null;
                    
                    try{
              		   BackendProvider backendProvider = getBackendProviderService();
              		   failedTransfer = backendProvider.getFailedTransferInstance();
              		   
              	   } catch(Throwable t){
              		   FFSDebug.log("Unable to get FailedTransfer Instance" , PRINT_ERR);
              		 PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
              	   }

                    resubmitFailXfers(dbh, fiId, instructionType,
                                      ScheduleConstants.SCH_FLAG_FAILEDON, logDate, failedTransfer);
                    resubmitFailXfers(dbh, fiId, instructionType,
                                      ScheduleConstants.SCH_FLAG_FUNDSFAILEDON, logDate, failedTransfer);
                    resubmitFailXfers(dbh, fiId, instructionType,
                                      ScheduleConstants.SCH_FLAG_NOFUNDSON, logDate, failedTransfer);
                    resubmitFailXfers(dbh, fiId, instructionType,
                                      ScheduleConstants.SCH_FLAG_LIMIT_REVERT_FAILED, logDate, failedTransfer);
                    resubmitFailXfers(dbh, fiId, instructionType,
                                      ScheduleConstants.SCH_FLAG_LIMIT_CHECK_FAILED, logDate, failedTransfer);
                }
            } else {
                // do crash recovery when scheduleFlag=SCH_FLAG_NORMAL
                eventHandler(eventSequence, evts, dbh, true);
            }
        } else if ( eventSequence == 3 ) {    // Batch-Start sequence
        } else if ( eventSequence == 4 ) {    // Batch-End sequence
        }
        FFSDebug.log("=== FailedXferHandler.resubmitEventHandler: end", PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    private void doFailedXfers( FFSConnectionHolder dbh,
                                String fiId,
                                String instructionType,
                                String status,
                                FailedTransfer failedTransfer )
    throws Exception
    {
        try {
            // Retrieve a batch of failed xfer record with status.
            // Save in EventInfo with EVT_STATUS_SUBMIT status.
            // Copy the EventInfo into EventInfoLog.
            // Send the batch to the FailedTransfer.processFailedTransfers()

            //      ScheduleFlag - save the following data for the failed xfers
            //                      ScheduleConstants.SCH_FLAG_FUNDSFAILEDON
            //                      ScheduleConstants.SCH_FLAG_NOFUNDSON
            //                      ScheduleConstants.SCH_FLAG_FAILEDON
            int schFlag = ScheduleConstants.SCH_FLAG_FUNDSFAILEDON;
            if (status.equals(FUNDSFAILEDON)) {
                schFlag = ScheduleConstants.SCH_FLAG_FUNDSFAILEDON;
            } else if (status.equals(NOFUNDSON)) {
                schFlag = ScheduleConstants.SCH_FLAG_NOFUNDSON;
            } else if (status.equals(FAILEDON)) {
                schFlag = ScheduleConstants.SCH_FLAG_FAILEDON;
            } else if (status.equals(LIMIT_REVERT_FAILED)) {
                schFlag = ScheduleConstants.SCH_FLAG_LIMIT_REVERT_FAILED;
            } else if (status.equals(LIMIT_CHECK_FAILED)) {
                schFlag = ScheduleConstants.SCH_FLAG_LIMIT_CHECK_FAILED;
            }

            PropertyConfig propertyConfig = (PropertyConfig)FFSRegistry.lookup( BPWResource.PROPERTYCONFIG);
            int batchSize =  propertyConfig.getBatchSize();
            IntraTrnInfo[] xferlist = XferInstruction.getXferBatchByStatus( fiId, status, batchSize, dbh );
            int addLen = xferlist.length;
            while ( addLen!= 0 ) {
                ArrayList alist = new ArrayList();
                for ( int i = 0; i < addLen; i++ ) {
                    XferInstruction.updateStatus(dbh, xferlist[i].srvrTid, status+"_NOTIF" );
                    String eventID = Event.createEvent( dbh,
                                                            xferlist[i].srvrTid,
                                                            fiId,
                                                            instructionType,
                                                            ScheduleConstants.EVT_STATUS_SUBMIT,
                                                            schFlag,
                                                            null );
                    alist.add(eventID);


                    // log into AuditLog
                    if (propertyConfig.LogLevel >= AUDITLOGLEVEL_STATUSUPDATE) {
                        int bizID = Integer.parseInt(xferlist[i].customerId);
                        BigDecimal pmtAmount = new BigDecimal(xferlist[i].amount);
                        String fromAcct = BPWUtil.getAccountIDWithAccountType(xferlist[i].acctIdFrom, xferlist[i].acctTypeFrom);
                        String toAcct = BPWUtil.getAccountIDWithAccountType(xferlist[i].acctIdTo, xferlist[i].acctTypeTo);
                        String desc = BPWLocaleUtil.getMessage(AuditLogConsts.AUDITLOG_MSG_BOOKTRANSFER_START_PROCESS_FAILED_XFER,
                                                               null,
                                                               BPWLocaleUtil.BOOKTRANSFER_AUDITLOG_MESSAGE);
                        String [] msgFiller = {desc};
                        ILocalizable msg = BPWLocaleUtil.getLocalizedMessage(
                                            AuditLogConsts.AUDITLOG_MSG_PROCESSING,
                                            msgFiller,
                                            BPWLocaleUtil.BOOKTRANSFER_AUDITLOG_MESSAGE);
                        TransAuditLog.logTransAuditLog(dbh.conn.getConnection(),
                                                       xferlist[i].submittedBy,
                                                       null,
                                                       null,
                                                       msg,
                                                       xferlist[i].logId,
                                                       AuditLogTranTypes.BPW_INTRATRN,
                                                       bizID,
                                                       pmtAmount,
                                                       xferlist[i].curDef,
                                                       xferlist[i].srvrTid,
                                                       status+"_NOTIF",
                                                       toAcct,
                                                       xferlist[i].bankId,
                                                       fromAcct,
                                                       xferlist[i].fromBankId,
                                                       0);
                    }
                }
                dbh.conn.commit();  // commit one batch at a time
                failedTransfer.processFailedTransfers(xferlist);
                for ( int i = 0; i < addLen; i++ ) {
                    EventInfoLog.createEventInfoLog(dbh,
                                                    (String)alist.get(i),
                                                    xferlist[i].srvrTid,
                                                    fiId, instructionType,
                                                    schFlag,
                                                    null );
                }
                dbh.conn.commit();  // commit one batch at a time
                if ( XferInstruction.isStatusBatchDone( fiId, status ) ) {
                    addLen = 0;
                } else {
                    xferlist = XferInstruction.getXferBatchByStatus( fiId, status, batchSize, dbh );
                    addLen = xferlist.length;
                }
            }
        } finally {
            XferInstruction.clearStatusBatch(fiId, status);
        }

    }

    private void recoverFromCrash(FFSConnectionHolder dbh,
                                  String fiId,
                                  String instructionType,
                                  int schFlag,
                                  FailedTransfer failedTransfer )
    throws Exception
    {
        try {
            // Retrieve EventInfo for those with status of "SUBMITTED" and
            // in ScheduleFlag in batch.
            // Send the batch to the FailedTransfer.processFailedTransfers()
            EventInfo [] evts = null;
            evts = Event.retrieveEventInfo(dbh, ScheduleConstants.EVT_STATUS_SUBMIT,
                                               fiId, instructionType,
                                               schFlag);
            if (evts != null) {
                String batchKey = DBConnCache.save(dbh);
                InstructionType it = BPWRegistryUtil.getInstructionType(fiId, instructionType);
                int fileBasedRecovery = it.FileBasedRecovery;
                while (evts != null && evts.length > 0) {
                    boolean isToCallHandler = true;
                    ArrayList alist = new ArrayList();
                    int queueLength = evts.length;
                    int processedRecords = 0;
                    if (fileBasedRecovery == 0) { // not file-based recovery
                        EventInfoLog evLog = EventInfoLog.getByEventID(dbh,  evts[queueLength-1].EventID);
                        if (evLog != null) { // do not send the batch if EventInfoLog found
                            isToCallHandler = false;
                        }
                    }
                    if (isToCallHandler) {
                        while (processedRecords < queueLength) {
                            EventInfoLog.updateEventInfoLog(dbh,
                                                            evts[processedRecords].EventID,
                                                            evts[processedRecords].InstructionID,
                                                            evts[processedRecords].FIId,
                                                            evts[processedRecords].InstructionType,
                                                            evts[processedRecords].ScheduleFlag,
                                                            evts[processedRecords].LogID);
                            String srvrTID = evts[processedRecords].InstructionID;
                            XferInstruction xinst = XferInstruction.getXferInstruction( dbh, srvrTID );
                            if (xinst == null) {
                                String msg = "*** FailedXferHandler.recoverFromCrash failed: could not find the SrvrTID="+srvrTID+" in BPW_XferInstruction";
                                FFSDebug.console(msg);
                                FFSDebug.log(msg);
                                continue;
                            }
                            String currency =   BPWUtil.validateCurrencyString(xinst.CurDef);
                            IntraTrnInfo info = new IntraTrnInfo( xinst.CustomerID,
                                                                  xinst.BankID,
                                                                  xinst.BranchID,
                                                                  xinst.AcctDebitID,
                                                                  xinst.AcctDebitType,
                                                                  xinst.AcctCreditID,
                                                                  xinst.AcctCreditType,
                                                                  xinst.Amount,
                                                                  currency,
                                                                  xinst.DateToPost,
                                                                  srvrTID,
                                                                  xinst.LogID,
                                                                  evts[processedRecords].EventID,
                                                                  1,    // eventSequence,
                                                                  true, // possibleDuplicate
                                                                  batchKey,
                                                                  xinst.RecSrvrTID,
                                                                  xinst.Status,
                                                                  xinst.FIID,
                                                                  xinst.fromBankID,
                                                                  xinst.fromBranchID);
                            
                            //Set from and to Account Country code in IntraTrnInfo object for Core Banking system
                            info.setAccountFromCountryCode(xinst.acctFromCountryCode);
                            info.setAccountToCountryCode(xinst.acctToCountryCode);
                            
                            // populate the extraFields of info with the extraFields of xinst
                            if (xinst.extraFields != null) {
                                info.extraFields = xinst.extraFields;
                            }

                            if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_FUNDSFAILEDON) {
                                info.status = FUNDSFAILEDON;
                            } else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_NOFUNDSON) {
                                info.status = NOFUNDSON;
                            } else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_FAILEDON) {
                                info.status = FAILEDON;
                            } else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_LIMIT_REVERT_FAILED) {
                                info.status = LIMIT_REVERT_FAILED;
                            } else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_LIMIT_CHECK_FAILED) {
                                info.status = LIMIT_CHECK_FAILED;
                            }

                            alist.add(info);
                            processedRecords++;
                        }
                        IntraTrnInfo[] xferlist = (IntraTrnInfo[]) alist.toArray( new IntraTrnInfo[alist.size()] );
                        failedTransfer.processFailedTransfers(xferlist);
                        dbh.conn.commit();  // commit one batch at a time
                    }
                    if ( Event.isBatchDone(fiId, instructionType)) {
                        evts = new EventInfo [0];
                    } else {
                        evts = Event.retrieveEventInfo(dbh, ScheduleConstants.EVT_STATUS_SUBMIT,
                                                           fiId, instructionType,
                                                           schFlag);
                    }
                }
                DBConnCache.unbind(batchKey);
            }
        } finally {
            Event.clearBatch(fiId, instructionType);
        }
    }

    private void resubmitFailXfers(FFSConnectionHolder dbh,
                                   String fiId,
                                   String instructionType,
                                   int schFlag,
                                   String logDate,
                                   FailedTransfer failedTransfer )
    throws Exception
    {
        try {
            // Retrieve EventInfoLog in batch.
            // Send the batch to the FailedTransfer.processFailedTransfers()
            EventInfo [] evts = null;
            evts = EventInfoLog.retrieveEventInfoLogs( dbh,
                                                       schFlag,
                                                       fiId, instructionType, logDate);
            if (evts != null) {
                String batchKey = DBConnCache.save(dbh);
                while (evts != null && evts.length > 0) {
                    ArrayList alist = new ArrayList();
                    int queueLength = evts.length;
                    int processedRecords = 0;
                    while (processedRecords < queueLength) {
                        String srvrTID = evts[processedRecords].InstructionID;
                        XferInstruction xinst = XferInstruction.getXferInstruction( dbh, srvrTID );
                        if (xinst == null) {
                            String msg = "*** FailedXferHandler.resubmitFailXfers failed: could not find the SrvrTID="+srvrTID+" in BPW_XferInstruction";
                            FFSDebug.console(msg);
                            FFSDebug.log(msg);
                            continue;
                        }
                        String currency =   BPWUtil.validateCurrencyString(xinst.CurDef);
                        IntraTrnInfo info = new IntraTrnInfo( xinst.CustomerID,
                                                              xinst.BankID,
                                                              xinst.BranchID,
                                                              xinst.AcctDebitID,
                                                              xinst.AcctDebitType,
                                                              xinst.AcctCreditID,
                                                              xinst.AcctCreditType,
                                                              xinst.Amount,
                                                              currency,
                                                              xinst.DateToPost,
                                                              srvrTID,
                                                              xinst.LogID,
                                                              evts[processedRecords].EventID,
                                                              1,    // eventSequence,
                                                              true, // possibleDuplicate
                                                              batchKey,
                                                              xinst.RecSrvrTID,
                                                              xinst.Status,
                                                              xinst.FIID,
                                                              xinst.fromBankID,
                                                              xinst.fromBranchID);
                        
                        //Set from and to Account Country code in IntraTrnInfo object for Core Banking system
                        info.setAccountFromCountryCode(xinst.acctFromCountryCode);
                        info.setAccountToCountryCode(xinst.acctToCountryCode);
                        
                        // populate extraFields of info with the extraFields of xinst
                        if (xinst.extraFields != null) {
                            info.extraFields = xinst.extraFields;
                        }

                        if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_FUNDSFAILEDON) {
                            info.status = FUNDSFAILEDON;
                        } else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_NOFUNDSON) {
                            info.status = NOFUNDSON;
                        } else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_FAILEDON) {
                            info.status = FAILEDON;
                        } else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_LIMIT_REVERT_FAILED) {
                            info.status = LIMIT_REVERT_FAILED;
                        } else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_LIMIT_CHECK_FAILED) {
                            info.status = LIMIT_CHECK_FAILED;
                        }


                        alist.add(info);
                        processedRecords++;
                    }
                    IntraTrnInfo[] xferlist = (IntraTrnInfo[]) alist.toArray( new IntraTrnInfo[alist.size()] );
                    failedTransfer.processFailedTransfers(xferlist);
                    dbh.conn.commit();  // commit one batch at a time
                    if ( EventInfoLog.isBatchDone(fiId, instructionType)) {
                        evts = new EventInfo [0];
                    } else {
                        evts = EventInfoLog.retrieveEventInfoLogs( dbh,
                                                                   schFlag,
                                                                   fiId, instructionType, logDate);
                    }
                }
                DBConnCache.unbind(batchKey);
            }
        } finally {
            EventInfoLog.clearBatch(fiId, instructionType);
        }
    }
    
    /**
	 * Gets the backend provider service instance.
	 *
	 * @return the backend provider service instance
	 */
	protected BackendProvider getBackendProviderService() throws Exception{
	   BackendProvider backendProvider = null;
	   Object obj =  OSGIUtil.getBean("backendProviderServices");
	   String backendType = null;
	   if(obj != null && obj instanceof List) {
  			List<BackendProvider> backendProviders = (List<BackendProvider>)obj;
  			
  			// get backend type property from config_master
  			com.sap.banking.common.bo.interfaces.CommonConfig commonConfigBO = (com.sap.banking.common.bo.interfaces.CommonConfig)OSGIUtil.getBean(com.sap.banking.common.bo.interfaces.CommonConfig.class);
  			backendType = getBankingBackendType(commonConfigBO);
  			
  			// iterate through the list of service refs and return ref based on configuration
  			if(backendProviders != null && !backendProviders.isEmpty()) {
  				Iterator<BackendProvider> iteratorBackendProvider =  backendProviders.iterator();
  				while(iteratorBackendProvider.hasNext()) {
  					BackendProvider backendProviderRef = iteratorBackendProvider.next();
 					if(backendType != null && backendType.equals(backendProviderRef.getBankingBackendType())) {
 						backendProvider = backendProviderRef;
 						break;
 					}
  				}
  			}
	   }
	   if(backendProvider == null) {
			FFSDebug.log("Invalid backend type." + backendType, FFSConst.PRINT_ERR);
			throw new Exception("Invalid backend type." + backendType);
	   }
	   return backendProvider;
	}
	
	private String getBankingBackendType(com.sap.banking.common.bo.interfaces.CommonConfig commonConfigBO) {
		
		String backendType = null;
		try {
			 backendType = commonConfigBO.getBankingBackendType();
		} catch (Exception e) {
			FFSDebug.log(Level.SEVERE, "==== getBankingBackendType" , e);
		}
		
		return backendType;
	}
}
