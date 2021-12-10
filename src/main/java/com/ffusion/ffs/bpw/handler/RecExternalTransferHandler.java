//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.math.BigDecimal;

import com.ffusion.csil.beans.entitlements.EntChannelOps;
import com.ffusion.ffs.bpw.audit.TransAuditLog;
import com.ffusion.ffs.bpw.db.AdjustedInstruction;
import com.ffusion.ffs.bpw.db.BPWFI;
import com.ffusion.ffs.bpw.db.Customer;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.db.ExternalTransferAccount;
import com.ffusion.ffs.bpw.db.Transfer;
import com.ffusion.ffs.bpw.interfaces.ACHConsts;
import com.ffusion.ffs.bpw.interfaces.AuditLogConsts;
import com.ffusion.ffs.bpw.interfaces.BPWFIInfo;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.CustomerInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.ExtTransferAcctInfo;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.RecTransferInfo;
import com.ffusion.ffs.bpw.interfaces.TransferCalloutHandler;
import com.ffusion.ffs.bpw.interfaces.TransferInfo;
import com.ffusion.ffs.bpw.interfaces.util.AccountUtil;
import com.ffusion.ffs.bpw.master.LimitCheckApprovalProcessor;
import com.ffusion.ffs.bpw.master.channels.ChannelStore;
import com.ffusion.ffs.bpw.util.BPWUtil;
import com.ffusion.ffs.bpw.util.resources.BPWLocaleUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.scheduling.constants.ScheduleConstants;
import com.ffusion.ffs.scheduling.db.ScheduleInfo;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.ffusion.util.ILocalizable;
import com.ffusion.util.logging.AuditLogRecord;
import com.ffusion.util.logging.AuditLogTranTypes;
import com.sap.banking.common.interceptors.PerfLoggerUtil;


//=====================================================================
//
// This class contains a callback handler that is registered in the
// InstructionType table.  The registered callback handler will be called
// by the Scheduling engine for the Recurring transfer Processing.
//
//=====================================================================

public class RecExternalTransferHandler implements DBConsts, FFSConst,
ScheduleConstants, BPWResource, BPWScheduleHandler {

    private PropertyConfig  _propertyConfig = null;

    private int audit_Level = 0;

    public RecExternalTransferHandler(){
        _propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);
        audit_Level = _propertyConfig.LogLevel;
    }

    //=====================================================================
    // eventHandler()
    // Description: This method is called by the Scheduling engine
    // Arguments: none
    // Returns:   none
    // Note:
    //=====================================================================
    public void eventHandler(
                            int eventSequence,
                            EventInfoArray evts,
                            FFSConnectionHolder dbh ) throws FFSException {

        final String methodName = " RecExternalTransferHandler.eventHander: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);

        FFSDebug.log(methodName, " begin, eventSeq: " + eventSequence
                     + ",length: " + evts._array.length, PRINT_DEV);

        boolean possibleDuplicate = false;
        processEvents(eventSequence, evts, dbh, possibleDuplicate);
        FFSDebug.log(methodName, " end", PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }


    private void processEvents(int eventSequence,
                               EventInfoArray evts,
                               FFSConnectionHolder dbh,
                               boolean possibleDuplicate)
    throws FFSException {

        final String methodName = " RecExternalTransferHandler.processEvent: ";

        FFSDebug.log(methodName, " begin, eventSeq: " + eventSequence
                     + ",length: " + evts._array.length, PRINT_DEV);

        TransferInfo transferInfo = null; //for audit logging
        
        TransferCalloutHandler calloutHandler = (TransferCalloutHandler)OSGIUtil.getBean(TransferCalloutHandler.class);
        
        TransferInfo calloutTranInfo = null; // clone of transferInfo, use for ALL callouts
        String calloutStatusMsg = "Transfer action aborted during begin callout.";

        try {
            calloutStatusMsg = BPWLocaleUtil.getMessage(TRANSFER_ACTION_ABORTED_BY_CALLOUT,
                                                        new String[] {BPW_TRANSFER_ACTION_ADD},
                                                        BPWLocaleUtil.TRANSFER_MESSAGE);
        } catch (Exception ex) {
        }

        try {
            // process recurring transfers
            if (eventSequence == 0) {// FIRST sequence no data
                //do nothing
            } else if (eventSequence == 1) {// NORMAL sequence transfer data
                // process each transfer in this batch
                for (int i = 0; i < evts._array.length; i++) {

                	if (evts._array[i] == null) {
                	   FFSDebug.log(methodName,
                		" ++--++ Invalid Transaction in this batch: "
                		+ evts._array[i],
                		", Transcaction will be ignored, Transaction counter: "
                		+ i, FFSConst.PRINT_DEV);
                		continue;
                	}



                    String recSrvrTid = evts._array[i].InstructionID;
                    FFSDebug.log(methodName, "processing eventSeq: " +
                                 eventSequence + ",RecSrvrTid: " + recSrvrTid, PRINT_DEV);
                    // get schedule info for this recurring model
                    ScheduleInfo sinfo =
                    ScheduleInfo.getScheduleInfo(evts._array[i].FIId,
                                                 RECETFTRN, recSrvrTid, dbh);

                    if ( (sinfo != null) &&
                         (sinfo.StatusOption != ScheduleConstants.SCH_STATUSOPTION_CLOSE_SCHEDULED) ) {
                        // we found schedule for the recmodel

                         FFSDebug.log(methodName, " sinfo.StatusOption: "
                                      + sinfo.StatusOption, PRINT_DEV);

                        // Update the transaction history for closed RecTransferInfo
                        if (SCH_STATUS_CLOSED.equalsIgnoreCase(sinfo.Status)) {
                            FFSDebug.log(methodName, "schedule closed ",
                                         ",for rectransfer: ", recSrvrTid, PRINT_DEV);

                            //close the recTransfer model
                            RecTransferInfo recTransferInfo = new RecTransferInfo();
                            recTransferInfo.setSrvrTId(recSrvrTid);
                            recTransferInfo.setFIId(evts._array[0].FIId);
                            String transferType = evts._array[0].InstructionType;
                            recTransferInfo.setTransferType(transferType);
                            recTransferInfo.setPrcStatus(POSTEDON);

                            //For audit logging
                            transferInfo = recTransferInfo;

                            // this transfer model is completed update its status
                            boolean isRecurring = true;
                            Transfer.updateStatus(dbh, recTransferInfo, isRecurring);
                        } else {

                        	// Check if instance about to be created has been adjusted.
							if (adjustedCurrentInstance(recSrvrTid, sinfo.CurInstanceNum, dbh)) {
								FFSDebug.log(methodName +
										": Current instance has been adjusted. Skipping creation of Instruction.",
										FFSConst.PRINT_DEV);
								continue;
							} else {
								FFSDebug.log(methodName +
										": Current instance has not adjusted. Proceed to create Instruction.",
										FFSConst.PRINT_DEV);
							}
                            transferInfo = new TransferInfo();
                            transferInfo.setSrvrTId(recSrvrTid);
                            transferInfo = getTransferInfo(dbh, transferInfo, true);

                            // don't check entitlement for resubmit
                            if (possibleDuplicate == false) {
                                // do entitlement check first,
                                // if not entitled, do not create any thing
                                // Get the recurring model information

                                if ( LimitCheckApprovalProcessor.checkEntitlementExtTrn( transferInfo, null ) == false ) {
                                    String msg = "RecExternalTransferHandler.processEvents failed to process: " +
                                                 "Entitlement check failed. RecTransferId = " + recSrvrTid;
                                    FFSDebug.log(msg, FFSConst.PRINT_DEV);

                                    // Cancel this recurring model
                                    // Update BPW_RecTransfer
                                    RecTransferInfo recTransferInfo = new RecTransferInfo();
                                    recTransferInfo.setSrvrTId(recSrvrTid);
                                    recTransferInfo.setFIId(evts._array[0].FIId);
                                    String transferType = evts._array[0].InstructionType;
                                    recTransferInfo.setTransferType(transferType);
                                    recTransferInfo.setPrcStatus(DBConsts.FAILEDON);

                                    // this transfer model failed, update its status
                                    boolean isRecurring = true;
                                    Transfer.updateStatus(dbh, recTransferInfo, isRecurring);
                                    // Close the RECETFTRN
                                    ScheduleInfo.delete(dbh, recSrvrTid, DBConsts.RECETFTRN);
                                    // Log in Audit Log
                                    if (audit_Level >= DBConsts.AUDITLOGLEVEL_SYSERROR)
                                    {
                                        transferInfo.setFIId(recTransferInfo.getFIId());
                                        transferInfo.setTransferType(recTransferInfo.getTransferType());
                                        transferInfo.setPrcStatus(recTransferInfo.getPrcStatus());
                                    	ILocalizable auditMsg = BPWLocaleUtil.getLocalizableMessage(AuditLogConsts.AUDITLOG_MSG_EXTERNALTRANSFER_REC_EXT_XFER_HANDLER_NOT_ENT_SUBMIT,
										  															null,
																									BPWLocaleUtil.EXTERNALTRANSFER_AUDITLOG_MESSAGE);
                                        logError(dbh, transferInfo, auditMsg);
                                    }

                                    continue;
                                }
                            }

                            //create a single instance from the recurring model
                            // create new TransferInfo and insert it in TransferInfo table
                            FFSDebug.log(methodName, "creating single ",
                                         "intance from recurring model: ",
                                         recSrvrTid, PRINT_DEV);

							// don't allow weekend or holiday future dated recurring instances
							String smartPayDay = BPWUtil.getDateBeanFormat(DBUtil.getPayday(sinfo.FIId, sinfo.NextInstanceDate, DBConsts.SCH_CATEGORY_EXTERNALTRANSFER));

                            // get BPWFIInfo
                            BPWFIInfo fiInfo = BPWFI.getBPWFIInfo(dbh,transferInfo.getFIId());
                            // get CustomerInfo
                            CustomerInfo customerInfo = Customer.getCustomerByID(transferInfo.getCustomerId(),dbh);
                            customerInfo.setSubmittedBy(transferInfo.getSubmittedBy());
                            transferInfo = generateSingleTransferFromRecTransfer(dbh, recSrvrTid, smartPayDay, fiInfo,
                            		customerInfo, transferInfo.getChannelId(), transferInfo.getChannelGroupId());
                            // create a record in BPW_ADJ_INSTR_INFO
                            if (!createAdjustedInstrInfo(recSrvrTid, transferInfo.getSrvrTId(), sinfo.CurInstanceNum, dbh)) {
                            	FFSDebug.log(methodName +
										": Current instance has been adjusted. Skipping creation of Instruction.",
										FFSConst.PRINT_ERR);
								continue;
                            }
                            try {
                                calloutTranInfo = (TransferInfo)transferInfo.clone();
                                calloutHandler.begin(calloutTranInfo, ADD_TRANSFER_FROM_SCHEDULE);
                            } catch (Throwable ex) {
                                transferInfo.setPrcStatus(FAILEDON);
                                Transfer.updateStatus(dbh, transferInfo, false);

                                if (calloutTranInfo.getStatusCode() != SUCCESS ) {
                                    transferInfo.setStatusCode(calloutTranInfo.getStatusCode());
                                    transferInfo.setStatusMsg(calloutTranInfo.getStatusMsg());
                                } else {
                                    transferInfo.setStatusCode(TRANSFER_ACTION_ABORTED_BY_CALLOUT);
                                    transferInfo.setStatusMsg(calloutStatusMsg);
                                }

                                try {
                                    calloutTranInfo = (TransferInfo)transferInfo.clone();
                                    calloutHandler.failure(calloutTranInfo, ADD_TRANSFER_FROM_SCHEDULE);
                                } catch (Throwable e) {
                                }
                                continue;
                            }

                            try {
                                //modify ScheduleInfo in the table with the earlier NextInstanceDate

                                sinfo.NextInstanceDate = Transfer.getNextInstanceDateInBPWWarehouse(dbh, smartPayDay, fiInfo, transferInfo.getTransferDest());
                                ScheduleInfo.modifySchedule(dbh, sinfo.ScheduleID, sinfo);

                                //don't call limits for resubmit
                                if (!possibleDuplicate) {

                                    FFSDebug.log(methodName,
                                                 "transferInfo for processExternalTransferAdd"
                                                 + transferInfo, PRINT_DEV);

                                    //Do limit check processing
                                    int ret = LimitCheckApprovalProcessor.processExternalTransferAdd(dbh, transferInfo, null);
									if (LimitCheckApprovalProcessor.LIMIT_CHECK_FAILED == ret) {
										// the limit check failed - manually rollback the limit since the running total will be updated as part of the limit
										// check and this is part of a larger transaction that can't be rolled back.
										// save/restore the status code and the message, since they will be modified by the call to processExternalTransferDelete.
										int xStatusCode = transferInfo.getStatusCode();
										String xStatusMsg = transferInfo.getStatusMsg();
										LimitCheckApprovalProcessor.processExternalTransferDelete(dbh, transferInfo, null);
										transferInfo.setStatusCode(xStatusCode);
										transferInfo.setStatusMsg(xStatusMsg);
									}

                                    //For return status LIMIT_CHECK_FAILED,
                                    //LIMIT_REVERT_FAILED and APPROVAL_FAILED update
                                    //the status of transfer record in database with the
                                    //corresponding returned status and for return status
                                    //NO_NEED_APPROVAL update the status of transfer record
                                    //in database with CREATED if approval is not required
                                    //or with APPROVAL_PENDING if the transfer is awaiting
                                    //approval
                                    String retStatus = LimitCheckApprovalProcessor.mapToStatus(ret);
                                    FFSDebug.log(methodName, "retStatus", retStatus,
                                                 PRINT_DEV);

                                    if ( LIMIT_CHECK_FAILED.equals(retStatus) ||
                                         LIMIT_REVERT_FAILED.equals(retStatus) ||
                                         APPROVAL_FAILED.equals(retStatus) ) {

                                        transferInfo.setPrcStatus(retStatus);
                                    } else { //NO_NEED_APPROVAL
                                    	if (APPROVAL_PENDING.equals(retStatus)) {
                                            transferInfo.setPrcStatus(DBConsts.APPROVAL_PENDING);
                                        } else {
                                            transferInfo.setPrcStatus(DBConsts.WILLPROCESSON);
                                        }
                                    }
                                }
                                FFSDebug.log(methodName,
                                             "transferInfo before status update"
                                             + transferInfo, PRINT_DEV);

                                //false: Single
                                Transfer.updateStatus(dbh, transferInfo, false);

                                if (transferInfo == null ||
                                    transferInfo.getStatusCode() != SUCCESS) {
                                    // No record in RecTransferInfo table
                                    // cancel the schedule for  this recurring model
                                    String err = methodName + "FAILED: " +
                                                 "COULD NOT FIND THE RecSrvrTid: "
                                                 + recSrvrTid + " in BPW_RecTransferInfo TABLE";
                                    FFSDebug.log("ERRORCODE:" +
                                                 ACHConsts.RECTRANSFER_NOT_FOUND_IN_DB, err,
                                                 PRINT_ERR);

                                    // Cancel this shedule since no data for it in
                                    // BPW_RecTransferInfo table
                                    FFSDebug.log(methodName, "canceling schedule ",
                                                 "for recurring model: ",
                                                 recSrvrTid, PRINT_DEV);
                                    ScheduleInfo.cancelSchedule(dbh, RECETFTRN,
                                                                recSrvrTid); // close the scheduleInfo

                                    // log into AuditLog
                                    if (audit_Level >= DBConsts.AUDITLOGLEVEL_SYSERROR)
                                    {
                                    	ILocalizable msg = BPWLocaleUtil.getLocalizableMessage(AuditLogConsts.AUDITLOG_MSG_EXTERNALTRANSFER_REC_EXT_XFER_HANDLER_XFER_NOT_FOUND,
										  													   null,
																							   BPWLocaleUtil.EXTERNALTRANSFER_AUDITLOG_MESSAGE);
                                        logError(dbh, transferInfo, msg);
                                    }

                                    // Add transfer unsuccessful
                                    try {
                                        calloutTranInfo = (TransferInfo)transferInfo.clone();
                                        calloutHandler.failure(calloutTranInfo, ADD_TRANSFER_FROM_SCHEDULE);
                                    } catch (Throwable ex) {
                                    }
                                    continue;
                                }

                                //set "BPTW" to ProcessedBy field
                                transferInfo.setLastProcessId(DBConsts.BPTW);

                                // No schedule for the single instance is required at
                                // this point since the schedule will be created after
                                // this instance is released

                                // log into AuditLog
                                if (audit_Level >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE)
                                {
                                	ILocalizable msg = null;

                                    if (SCH_STATUS_CLOSED.equalsIgnoreCase(sinfo.Status)) {
                                        //calloutStatusMsg = RECTRANSFER_HANDLER_POSTEDON;

                                        try {
                                            msg = BPWLocaleUtil.getLocalizableMessage(AuditLogConsts.AUDITLOG_MSG_EXTERNALTRANSFER_POSTEDON,
                                                                                      null,
                                                                                      BPWLocaleUtil.EXTERNALTRANSFER_AUDITLOG_MESSAGE);
                                        } catch (Exception ex) {
                                        }

                                        doAuditLogging(dbh, transferInfo, msg);
                                    } else {
                                        //calloutStatusMsg = RECTRANSFER_HANDLER_NEXT_INSTANCE_CREATED;

                                        try {
                                            msg = BPWLocaleUtil.getLocalizableMessage(AuditLogConsts.AUDITLOG_MSG_EXTERNALTRANSFER_NEXT_INSTANCE_CREATED,
                                                                                      null,
                                                                                      BPWLocaleUtil.EXTERNALTRANSFER_AUDITLOG_MESSAGE);
                                        } catch (Exception ex) {
                                        }

                                        doAuditLogging(dbh, transferInfo, msg);
                                    }
                                }
                                try {
                                    calloutTranInfo = (TransferInfo)transferInfo.clone();
                                    calloutHandler.end(calloutTranInfo, ADD_TRANSFER_FROM_SCHEDULE);
                                } catch (Throwable ex) {
                                }
                            } catch (Throwable exc) {
                                try {
                                    calloutTranInfo = (TransferInfo)transferInfo.clone();
                                    calloutHandler.failure(calloutTranInfo, ADD_TRANSFER_FROM_SCHEDULE);
                                } catch (Throwable ex) {
                                }
                                throw exc; // Throw original exception to preserve error handling
                            }

                        }
                    } else {

                        // SchedeuleInfo not found means that it is closed
                        //close the recTransfer model
                        FFSDebug.log(methodName, "no schedule found for the ",
                                     "recurringrecurring model: " + recSrvrTid,
                                     ". This model will be closed", PRINT_DEV);

                        boolean isRecurring = true;
                        RecTransferInfo recTransferInfo = new RecTransferInfo();
                        recTransferInfo.setSrvrTId(recSrvrTid);
                        recTransferInfo.setFIId(evts._array[0].FIId);
                        recTransferInfo.setTransferType(evts._array[0].InstructionType);
                        recTransferInfo.setPrcStatus(POSTEDON);

                        //For audit logging
                        transferInfo = recTransferInfo;

                        Transfer.updateStatus(dbh, recTransferInfo, isRecurring);
                    }
                } // end of for loop this transfer job is done
                FFSDebug.log(methodName, "batch processing completed" ,
                             PRINT_DEV);

            } else if (eventSequence == 2) { // LAST sequence in this schedule
                FFSDebug.log(methodName, "schedule processing completed",
                             "eventSequence: " + eventSequence, PRINT_DEV);
                // do nothing
            }
        } catch (Throwable exc) {
            String err = methodName + "Failed. Error: " +
                         FFSDebug.stackTrace(exc);
            FFSDebug.log(err, PRINT_ERR);

            // log into AuditLog
            if (audit_Level >= DBConsts.AUDITLOGLEVEL_SYSERROR) {
                logError(dbh, transferInfo, null);
            }
            throw new FFSException(exc, err);
        }
        FFSDebug.log(methodName, " end", PRINT_DEV);
    }


//=====================================================================
// resubmitEventHandler()
// Description: This method is called by the Scheduling engine during
// event resubmission.
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
        FFSDebug.log(" RecExternalTransferHandler.resubmitEventHandler: begin, eventSeq: "
                     + eventSequence
                     + ",length: "
                     + evts._array.length
                     + ",instructionType: "
                     + evts._array[0].InstructionType);
        //omarabi:
        boolean possibleDuplicate = true;
        processEvents(eventSequence, evts, dbh, possibleDuplicate);
        FFSDebug.log(" RecExternalTransferHandler.resubmitEventHandler: end");
    }

    /**
     * Do Audit logging
     *
     * @param transferInfo
     * @param error - If logging for error or exception
     * @exception Exception
     */
    private void doAuditLogging( FFSConnectionHolder dbh,
                                 TransferInfo transferInfo,
                                 ILocalizable msg)
    throws Exception {

        String curMethodName = "RecExternalTransferHandler.doAuditLogging";

        String toAcctId = null;
        String toAcctRTN = null;
        String fromAcctId = null;
        String fromAcctRTN = null;
        String amount = null;
        String userId = null;
        int businessId = 0;
        AuditLogRecord auditLogRecord = null;

        FFSDebug.log(curMethodName, "start", PRINT_DEV);
        FFSDebug.log(curMethodName, "transferInfo:", transferInfo.toString(),
                     PRINT_DEV);

        if (transferInfo == null) {
            return;
        }

        //Do Audit logging here
        try {

            amount = transferInfo.getAmount();

            if ((amount == null) || (amount.trim().length() == 0)) {
                amount = "-1";
            }

            // Get the ToAccountNum and ToAccountType given ExtAcctId
            ExtTransferAcctInfo extTransferAcctInfo = new ExtTransferAcctInfo();
	        String acctId = null;
	        if (DBConsts.BPW_TRANSFER_DEST_ITOE.equalsIgnoreCase(transferInfo.getTransferDest())) {
		        // Then ToAccount is external
		        acctId = transferInfo.getAccountToId();
	        } else if (DBConsts.BPW_TRANSFER_DEST_ETOI.equalsIgnoreCase(transferInfo.getTransferDest())) {
		        // Then FromAccount is external
		        acctId = transferInfo.getAccountFromId();
	        } else {
		        // Add support for other destinations in the future
	        }

            extTransferAcctInfo.setAcctId(acctId);
            extTransferAcctInfo =
            ExternalTransferAccount.getExternalTransferAccount(dbh,
                                                               extTransferAcctInfo, false, true);

            if (extTransferAcctInfo.getStatusCode() == ACHConsts.SUCCESS) {
                toAcctId = AccountUtil.buildExtTransferAcctId(extTransferAcctInfo);
                toAcctRTN = extTransferAcctInfo.getAcctBankRtn();

            } else {
                // Just use whatever is in TransferInfo
                toAcctId =  AccountUtil.buildTransferToAcctId(transferInfo);
            }

            fromAcctRTN = transferInfo.getBankFromRtn();
            fromAcctId = AccountUtil.buildTransferFromAcctId(transferInfo);

            //Differentiate between consumer and business
            if ((transferInfo.getCustomerId().equals(transferInfo.getSubmittedBy())) ||
                (transferInfo.getCustomerId().equals(transferInfo.getSubmittedBy()))) { //Consumer

                businessId = 0;
                FFSDebug.log("logActivity: Consumer", PRINT_DEV);
            } else { //Business
                businessId = Integer.parseInt(transferInfo.getCustomerId());
                FFSDebug.log("logActivity: Business", PRINT_DEV);
            }

            userId = transferInfo.getSubmittedBy();
            auditLogRecord = new AuditLogRecord(userId, //userId
                                                null, //agentId
                                                null, //agentType
                                                msg, //description
                                                transferInfo.getLogId(), //Tracking Id
                                                AuditLogTranTypes.BPW_RECTRANSFERHANDLER, //tranType
                                                businessId, //BusinessId
                                                new BigDecimal(amount),
                                                transferInfo.getAmountCurrency(),
                                                transferInfo.getSrvrTId(),
                                                transferInfo.getPrcStatus(),
                                                toAcctId,
                                                toAcctRTN,
                                                fromAcctId,
                                                fromAcctRTN,
                                                0);

            TransAuditLog.logTransAuditLog(auditLogRecord, dbh.conn.getConnection());

        } catch (Exception ex) {
            String errDescrip = curMethodName + " failed " + ex;
            FFSDebug.log(errDescrip + FFSDebug.stackTrace(ex), PRINT_ERR);
            throw new FFSException(ex, errDescrip);
        }
    }

    /**
     * Do Audit logging
     *
     * @param transferInfo
     * @param error - If logging for error or exception
     * @exception Exception
     */
    private void logError( FFSConnectionHolder dbh,
                           TransferInfo transferInfo,
                           ILocalizable msg) {

        String curMethodName = "FailedRecExternalTransferHandler.logError";

        try
        {
            if (msg == null)
            {
				msg = BPWLocaleUtil.getLocalizableMessage(AuditLogConsts.AUDITLOG_MSG_EXTERNALTRANSFER_REC_EXT_XFER_HANDLER_UNKNOWN_ERROR,
										  				  null,
														  BPWLocaleUtil.EXTERNALTRANSFER_AUDITLOG_MESSAGE);
            }

            doAuditLogging(dbh, transferInfo, msg);
        }
        catch (Exception ex)
        {
            String errDescrip = curMethodName + " failed " + ex;
            FFSDebug.log(errDescrip + FFSDebug.stackTrace(ex), PRINT_ERR);
        }
    }
    
	/**
	 * Get transfer info along with channel information.
	 * 
	 * @param dbh
	 *            DB connection.
	 * @param transferInfo
	 *            Transfer info containing primary key.
	 * @param isRec
	 *            Recurring flag.
	 * @return TransferInfo.
	 * @throws FFSException
	 */
    private TransferInfo getTransferInfo(FFSConnectionHolder dbh, TransferInfo transferInfo, boolean isRec) throws FFSException {
    	
    	TransferInfo ti = Transfer.getTransferInfo(dbh, transferInfo, isRec);
    	EntChannelOps channelOps = ChannelStore.getChannelIdForTrn(null, ti.getSrvrTId(), 
    			(isRec ? DBConsts.BPW_RecXferInstruction : DBConsts.BPW_XferInstruction), dbh);
    	ti.setChannelId(channelOps.getChannelIDMod());
    	ti.setChannelGroupId(channelOps.getChannelGroupIdMod());
    	FFSDebug.log("RecExternalTransferHandler.getTransferInfo: Read channel: " + ti.getChannelId() + " for Id: "
    			+ transferInfo.getSrvrTId());
    	return ti;
    }

	/**
	 * Generates instance of single transfer from recurring model.
	 * 
	 * @param dbh
	 *            DB connection.
	 * @param recSrvrTid
	 *            RecSrvrTid of recurring model.
	 * @param dueDate
	 *            Due date.
	 * @param fiInfo
	 *            FI info.
	 * @param customerInfo
	 *            Customer Info.
	 * @param channelId
	 *            Channel of recurring model.
	 * @return Single transfer instruction.
	 * @throws FFSException
	 */
	private TransferInfo generateSingleTransferFromRecTransfer(FFSConnectionHolder dbh, String recSrvrTid,
			String dueDate, BPWFIInfo fiInfo, CustomerInfo customerInfo, String channelId, int channelGroupId) throws FFSException {
		TransferInfo ti = Transfer.generateSingleTransferFromRecTransfer(dbh, recSrvrTid, dueDate, fiInfo, customerInfo, false, DBConsts.BPW_TRANSFER_PROCESS_TYPE_SCHED);
		if(channelId != null) {
			ti.setChannelId(channelId);
			ti.setChannelGroupId(channelGroupId);
			ChannelStore.addChannelInfoForTrn(customerInfo.getSubmittedBy(), ti.getSrvrTId(), 
					DBConsts.BPW_XferInstruction, channelId, dbh, ti.getChannelGroupId());
			FFSDebug.log("RecExternalTransferHandler.generateSingleTransferFromRecTransfer: Channel: " + ti.getChannelId() +
					" is set for single transfer instruction. RecSrvrTID = " + recSrvrTid + ", SrvrTid = " + ti.getSrvrTId(), PRINT_DEV);
	  
		} else {
			FFSDebug.log("RecExternalTransferHandler.generateSingleTransferFromRecTransfer: No channel" +
					" is set for single transfer instruction. RecSrvrTID = " + recSrvrTid + ", SrvrTid = " + ti.getSrvrTId(), PRINT_DEV);
		}
		return ti;
	}
	
	/**
	 * Create adjusted info with specified recSrvrTid and srvrTid in BPW_Adj_Instr_Info table.
	 * 
	 * @param recSrvrTid
	 *            RecSrvrTID of recurring model.
	 * @param dbh
	 *            Database connection
	 */
	private boolean createAdjustedInstrInfo(String recSrvrTid, String srvrTid, int instanceNum, FFSConnectionHolder dbh) {
		
		String methodName = "++_++ RecExternalTransferHandler.createAdjustedInstrInfo";
        FFSDebug.log(methodName, ": starts....", FFSConst.PRINT_DEV);
        
		if(recSrvrTid == null)
			return false;
		try {
			instanceNum = Math.abs(instanceNum);
			AdjustedInstruction.createRecord(recSrvrTid, srvrTid, instanceNum, dbh);
			FFSDebug.log(methodName + " New  AdjustedInstruction record created. RecSrvrTid =  " + recSrvrTid +
					", SrvrTid = " + srvrTid + "InstanceNumber = " + instanceNum, PRINT_DEV);
			return true;
		} catch(Exception e) {
			FFSDebug.log(methodName + ": Error in creating record.", FFSConst.PRINT_ERR);
			return false;
		}
	}
	
	/**
	 * Checks if current instance of a recurring schedule has been adjusted by user.
	 * Queries BPW_Adj_Instr_Info table. 
	 * 
	 * @param recSrvrTid
	 *            RecSrvrTID of recurring model.
	 * @param currentInstance
	 *            The instance number of instance that is about to be created.
	 * @param dbh
	 *            DB connection.
	 * @return <code>true</code> if current instance is adjusted, <code>false</code> otherwise.
	 */
	private boolean adjustedCurrentInstance(String recSrvrTid, int currentInstance, FFSConnectionHolder dbh) {
		
		try {
			return AdjustedInstruction.isInstanceAdjusted(recSrvrTid, currentInstance, dbh);
		} catch(Exception e) {
			FFSDebug.log("++_++ RecPmtHandler.adjustedCurrentInstance: Error in checking for records.", FFSConst.PRINT_ERR);
		}
		return false;
	}
	
}
