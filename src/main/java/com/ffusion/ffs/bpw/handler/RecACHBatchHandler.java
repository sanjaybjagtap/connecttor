//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;

import com.ffusion.csil.beans.entitlements.EntChannelOps;
import com.ffusion.ffs.bpw.audit.TransAuditLog;
import com.ffusion.ffs.bpw.db.ACHBatch;
import com.ffusion.ffs.bpw.db.ACHPayee;
import com.ffusion.ffs.bpw.db.AdjustedInstruction;
import com.ffusion.ffs.bpw.db.SmartCalendar;
import com.ffusion.ffs.bpw.interfaces.ACHBatchInfo;
import com.ffusion.ffs.bpw.interfaces.ACHConsts;
import com.ffusion.ffs.bpw.interfaces.ACHPayeeInfo;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.RecACHBatchInfo;
import com.ffusion.ffs.bpw.master.LimitCheckApprovalProcessor;
import com.ffusion.ffs.bpw.master.channels.ChannelStore;
import com.ffusion.ffs.bpw.util.BPWUtil;
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
// by the Scheduling engine for the Recurring Payment Processing.
//
//=====================================================================

public class RecACHBatchHandler implements BPWScheduleHandler {

    private final int _logLevel;

    public RecACHBatchHandler(){
        PropertyConfig propertyConfig = (PropertyConfig)FFSRegistry.lookup( BPWResource.PROPERTYCONFIG);
        _logLevel = propertyConfig.LogLevel;
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
                            FFSConnectionHolder dbh )
    throws FFSException
    {
    	String methodName = "RecACHBatchHandler.eventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        eventHandler( eventSequence, evts, dbh, false ); // resubmit false
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
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
                            FFSConnectionHolder dbh,
                            boolean resubmit )
    throws FFSException
    {
    	String methodName = "RecACHBatchHandler.eventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log("=== RecACHBatchHandler.eventHander: begin, eventSeq=" + eventSequence
                     + ",length=" + evts._array.length, FFSConst.PRINT_DEV);
        try {
            // =================================
            // Do event sequence
            // =================================
            if (eventSequence == 0) {// FIRST sequence no data
                //do nothing

            } else if (eventSequence == 1) {// NORMAL sequence batch data

                for (int i = 0; i < evts._array.length; i++) { // process each batch

                    String recBatchId = evts._array[i].InstructionID;
                    FFSDebug.log("=== RecACHBatchHandler.eventHander: eventSeq=" + eventSequence
                                 + ",RecBatchId=" + recBatchId, FFSConst.PRINT_DEV);


                    ScheduleInfo sinfo = ScheduleInfo.getScheduleInfo(evts._array[i].FIId,
                                                                      DBConsts.RECACHBATCHTRN, recBatchId, dbh);

                    ACHBatchInfo recBatchInfo = getACHBatchById(dbh, recBatchId, true);

                    if (sinfo != null &&
                        sinfo.StatusOption != ScheduleConstants.SCH_STATUSOPTION_CLOSE_SCHEDULED) {
                        // Update the transaction history for closed RecBatchInfo
                        if (sinfo.Status.compareTo(ScheduleConstants.SCH_STATUS_CLOSED) == 0) {

                            //close the recBatch model
                            boolean isRecurring = true;
                            RecACHBatchInfo recBatch = new RecACHBatchInfo();
                            recBatch.setBatchId(recBatchId);
                            ACHBatch.updateACHBatchStatus(recBatch, DBConsts.POSTEDON, dbh, isRecurring);

                            if (_logLevel >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE) {
                                // before do transaction auditlog, we need to get the batch
                                recBatch = (RecACHBatchInfo)ACHBatch.getACHBatch(recBatch,dbh,isRecurring,
                                                                                 false,   // don't load records
                                                                                 false);  // don't parse records
                                doTransAuditLog(dbh,recBatch,"ScheduleInfo for an ACH recurring batch is already closed");
                            }
                        } else {
                            // QTS 622728: if there was a problem with getting the recBatchInfo,
                            // don't assume everything is all right.  Instead terminate processing this
                            // RECACHBATCHTRN.
                            if (recBatchInfo == null || recBatchInfo.getStatusCode() != DBConsts.SUCCESS) { // No record in RecACHBatch table, ignore this entry
                                String msg = "*** RecACHBatchHandler.eventHandler failed: " +
                                             "could not find the RecBatchId= " + recBatchId + " in ACH_RecBatch table";
                                FFSDebug.log(msg, FFSConst.PRINT_ERR);

                                // Cancel this schedule since no data for it in ACH_RecBatch table
                                ScheduleInfo.cancelSchedule(dbh, DBConsts.RECACHBATCHTRN, recBatchId); // close the scheduleInfo
                                continue;
                            }
                            // Check if instance about to be created has been adjusted.
							if (adjustedCurrentInstance(recBatchId, sinfo.CurInstanceNum, dbh)) {
								FFSDebug.log("RecACHBatchHandler.eventHander: Current instance has been adjusted. Skipping creation of Instruction.",
										FFSConst.PRINT_DEV);
								continue;
							} else {
								FFSDebug.log("RecACHBatchHandler.eventHander: Current instance has not adjusted. Proceed to create Instruction.",
										FFSConst.PRINT_DEV);
							}
                            if ( resubmit == false ) {  // not resubmit, we need to check limit
                                // do entitlement check first,
                                // if not entitled, do not create any thing
                                // Get the recurring model information                                
                                if ( LimitCheckApprovalProcessor.checkEntitlementACHBatch( recBatchInfo, null ) == false ) {
                                    String msg = "RecACHBatchHandler.eventHandler failed to process: " +
                                                 "Entitlement check failed. RecBatchId= " + recBatchId;
                                    FFSDebug.log(msg, FFSConst.PRINT_DEV);

                                    // Cancel this recurring model
                                    // Update ACH_RecBatch
                                    boolean isRecurring = true;
                                    RecACHBatchInfo recBatch = new RecACHBatchInfo();
                                    recBatch.setBatchId(recBatchId);
                                    ACHBatch.updateACHBatchStatus(recBatch, DBConsts.FAILEDON, dbh, isRecurring);
                                    // Close the RECACHBATCHTRN
                                    ScheduleInfo.delete(dbh, recBatchId, DBConsts.RECACHBATCHTRN);
                                    // Log in Audit Log
                                    if (_logLevel >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE) {
                                        // before do transaction auditlog, we need to get the batch
                                        recBatch = (RecACHBatchInfo)ACHBatch.getACHBatch(recBatch,dbh,isRecurring,
                                                                                         false,   // don't load records
                                                                                         false);  // don't parse records
                                        doTransAuditLog(dbh,recBatch,"Processing ACH recurring batch failed.  Not entitled to submit an ACH batch.");
                                    }

                                    continue;
                                }
                                // They passed the entitlement check, fall through and
                                // create the INSTANCE object from the model.
                            }

                            // compute recurring effectiveEntryDate
                            // get EffectiveEntryDate in format yyyyMMdd
                            String effEntryDate = ACHBatch.getEffectiveDateFromBatch(recBatchInfo);
                            // add 00 at the end
                            int effEntryDateInt = Integer.parseInt(effEntryDate) * 100; 
                            effEntryDateInt = ScheduleInfo.computeFutureDate(effEntryDateInt,sinfo.Frequency,sinfo.CurInstanceNum-1,sinfo.FIId , null, sinfo.InstructionType);
                            // remove 00 at the end
                            effEntryDateInt /= 100;
                            // ScheduleInfo.computeFutureDay doesn't guarantee that the date is a business date
                            // Hence, we need to make sure it is a business day
                            int payday = effEntryDateInt;
                            effEntryDateInt = SmartCalendar.getACHPayday(recBatchInfo.getFiId(),effEntryDateInt);

                            // If EffectiveEntryDate is a holiday, check if Credit or Debit and move forward or backward
                            // accordingly (ACH Origination DDD - Commerce Bank)

                            if (payday != effEntryDateInt) {
                                boolean direction = true;           // move forward
                                String actionPropertyName = DBConsts.BPW_RECACH_HOLIDAY_MIXED_ACTION;
                                String actionDefault = DBConsts.BPW_RECACH_HOLIDAY_MIXED_ACTION_DEFAULT;
                                int srvClassCode = recBatchInfo.getBatchHeaderFieldValueShort(ACHConsts.SERVICE_CLASS_CODE);
                                if (srvClassCode == ACHConsts.ACH_MIXED_DEBIT_CREDIT)
                                {
                                    actionPropertyName = DBConsts.BPW_RECACH_HOLIDAY_MIXED_ACTION;
                                    actionDefault = DBConsts.BPW_RECACH_HOLIDAY_MIXED_ACTION_DEFAULT;
                                } else if (srvClassCode == ACHConsts.ACH_CREDITS_ONLY)
                                {
                                    actionPropertyName = DBConsts.BPW_RECACH_HOLIDAY_CREDIT_ACTION;
                                    actionDefault = DBConsts.BPW_RECACH_HOLIDAY_CREDIT_ACTION_DEFAULT;
                                } else if (srvClassCode == ACHConsts.ACH_DEBITS_ONLY)
                                {
                                    actionPropertyName = DBConsts.BPW_RECACH_HOLIDAY_DEBIT_ACTION;
                                    actionDefault = DBConsts.BPW_RECACH_HOLIDAY_DEBIT_ACTION_DEFAULT;
                                }

                                // get value from servers' properties
                                PropertyConfig propertyConfig = (PropertyConfig) FFSRegistry.lookup(BPWResource.PROPERTYCONFIG);
                                String actionStr = propertyConfig.otherProperties.getProperty(actionPropertyName,
                                                                                               actionDefault);
                                if ("forward".equalsIgnoreCase(actionStr) || "backward".equalsIgnoreCase(actionStr))
                                {
                                    // backwards
                                    direction = "forward".equalsIgnoreCase(actionStr);       // forwards
                                    effEntryDateInt = SmartCalendar.getACHBusinessDay(recBatchInfo.getFiId(),
                                                                                   payday,
                                                                                   direction);
                                }
                            }

                            // remove the first 2 digits: yyMMdd
                            effEntryDate = Integer.toString(effEntryDateInt);                            
                            effEntryDate = effEntryDate.substring(2);
        
                            // set duedate as effective entry date but in diff format yyyyMMdd
                            String dueDate = Integer.toString(effEntryDateInt);

                            //copy the recmodel batch info to the batch table

                            //create new ACHBatchInfo and insert it in ACHBatch table
							ACHBatchInfo batchInfo = generateACHBatchFromACHRecBatch(dbh, recBatchId, dueDate,
									effEntryDate, recBatchInfo.getChannelId(), recBatchInfo.getChannelGroupId());

                            if (batchInfo == null || batchInfo.getStatusCode() != DBConsts.SUCCESS) { // No record in RecACHBatch table, ignore this entry
                                String msg = "*** RecACHBatchHandler.eventHandler failed: " +
                                             "could not find the RecBatchId= " + recBatchId + " in ACH_RecBatch table";
                                FFSDebug.log(msg, FFSConst.PRINT_ERR);

                                // Cancel this schedule since no data for it in ACH_RecBatch table
                                ScheduleInfo.cancelSchedule(dbh, DBConsts.RECACHBATCHTRN, recBatchId); // close the scheduleInfo
                                continue;
                            }
                            // create a record in BPW_ADJ_INSTR_INFO
                            if (!createAdjustedInstrInfo(recBatchId, batchInfo.getBatchId(), sinfo.CurInstanceNum, dbh)) {
                            	FFSDebug.log("RecACHBatchHandler.eventHander: Current instance has been adjusted. Skipping creation of Instruction.",
										FFSConst.PRINT_ERR);
								continue;
                            }
                            //modify ScheduleInfo in the table with the earlier NextInstanceDate
                            // nextInstaDate was calculated in ScheduleRunable before thie method called
                            String nextInstanceDate = BPWUtil.getDateBeanFormat(sinfo.NextInstanceDate);
                            int srvClassCode = batchInfo.getBatchHeaderFieldValueShort(ACHConsts.SERVICE_CLASS_CODE);
                            HashMap infos = ACHBatch.getInfosForBatch(dbh, batchInfo);
							if (infos == null) {
								String msg = "*** RecACHBatchHandler.eventHandler failed: could not find the ACH Company, Customer or BPW FIID for RecBatchId= " + recBatchId;
								FFSDebug.log(msg, FFSConst.PRINT_ERR);

								// Cancel this schedule since some data for it is missing
								ScheduleInfo.cancelSchedule(dbh, DBConsts.RECACHBATCHTRN, recBatchId); // close the scheduleInfo
								continue;
							}

                            // NextInstanceDate for recurring schedule doesn't depend on Bank warehouse type.
                            // That's why we call getNextInstanceDateInBPWWarehouse
                            sinfo.NextInstanceDate = ACHBatch.getNextInstanceDateInBPWWarehouse(nextInstanceDate,
                                                                                                srvClassCode,
                                                                                                infos,
                                                                                                batchInfo.getClassCode(),
                                                                                                batchInfo.getBatchCategory());
                            ScheduleInfo.modifySchedule(dbh, sinfo.ScheduleID, sinfo);


                            String status = DBConsts.WILLPROCESSON;
                            if ( resubmit == false ) {  // not resubmit, we need to check limit
                                // get payeeInfos in this batch
                                ACHPayeeInfo[] payeeInfos = ACHPayee.getACHPayeeInfoInBatch(dbh,batchInfo);
                                HashMap extraInfo = new HashMap();
                                extraInfo.put(ACHConsts.ACH_PAYEE_INFOS, payeeInfos);
                                // Send this batch for limitchecking
                                int result = LimitCheckApprovalProcessor.processACHBatchAdd( dbh,
                                                                                             batchInfo,
                                                                                             extraInfo );//extra info
                                // map to BPW status
                                status = LimitCheckApprovalProcessor.mapToStatus( result );
								// the limit check failed - manually rollback the limit since the running total will be updated as part of the limit
								// check and this is part of a larger transaction that can't be rolled back.
								// save/restore the status code and the message, since they will be modified by the call to processIntraTrnDelete.
								if (result == LimitCheckApprovalProcessor.LIMIT_CHECK_FAILED ||
									result == LimitCheckApprovalProcessor.APPROVAL_NOT_ALLOWED) {
									int batchStatusCode = batchInfo.getStatusCode();
									String batchStatusMsg = batchInfo.getStatusMsg();
									LimitCheckApprovalProcessor.processACHBatchDelete(dbh, batchInfo, extraInfo);
									batchInfo.setStatusCode(batchStatusCode);
									batchInfo.setStatusMsg(batchStatusMsg);
								}
                                ACHBatch.updateACHBatchStatus(batchInfo, status, dbh, false );
	                            doTransAuditLog(dbh,batchInfo,"Add a next single batch for an ACH recurring batch");
                            }

                            if ( status.compareTo(DBConsts.WILLPROCESSON) == 0 ) {
                                ScheduleInfo info   = new ScheduleInfo();
                                info.Status         = ScheduleConstants.SCH_STATUS_ACTIVE;
                                info.Frequency      = ScheduleConstants.SCH_FREQ_ONCE;
                                // StartDate for generated single batch: depends on bank's transaction warehouse,
                                // lead days and calculated nextInstanceDate of scheduleInfo
                                //nextInstanceDate = BPWUtil.getDateBeanFormat(sinfo.NextInstanceDate);
                                info.StartDate      = ACHBatch.getNextInstanceDateForScheduleInfo(batchInfo.getDueDate(),
                                                                                                  srvClassCode,
                                                                                                  infos,
                                                                                                  batchInfo.getClassCode(),
                                                                                                  batchInfo.getBatchCategory());
                                info.InstanceCount  = 1;
                                info.LogID          = batchInfo.getLogId();
                                info.FIId           = sinfo.FIId;
                                ScheduleInfo.createSchedule(dbh, DBConsts.ACHBATCHTRN, batchInfo.getBatchId(), info);
                            } else {
                                //do not create schedule
                                FFSDebug.log("=== RecACHBatchHandler.eventHander: eventSeq=" + eventSequence
                                             + ",status =" + status, FFSConst.PRINT_DEV);
                            }

                        }
                    } else {
                        // SchedeuleInfo not found means that it is closed
                        //close the recBatch model
                        boolean isRecurring = true;
                        RecACHBatchInfo recBatch = new RecACHBatchInfo();
                        recBatch.setBatchId(recBatchId);
                        ACHBatch.updateACHBatchStatus(recBatch, DBConsts.POSTEDON, dbh, isRecurring);
                        if (_logLevel >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE) {
                            // before do transaction auditlog, we need to get the batch
                            recBatch = (RecACHBatchInfo)ACHBatch.getACHBatch(recBatch,dbh,isRecurring,
                                                                             false,   // don't load records
                                                                             false);  // don't parse records
                            doTransAuditLog(dbh,recBatch,"Complete an ACH recurring batch");
                        }
                        //mark all the ACH BATCH with SKIPPEDON status as CANCELLED
                        cancelSkippedInstances(dbh, recBatchId);
                    }
                } // end of for loop this batch job is done

            } else if (eventSequence == 2) { // LAST sequence in this schedule
                // do nothing
            }
        } catch (Exception exc) {
            String errDescrip = "*** RecACHBatchHandler.eventHandler failed. Error: " +
                                FFSDebug.stackTrace(exc);
            FFSDebug.log(errDescrip, FFSConst.PRINT_ERR);
            PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
            throw new FFSException(exc, errDescrip);
        }
        FFSDebug.log("==== RecACHBatchHandler.eventHander: end", FFSConst.PRINT_DEV);
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
    	String methodName = "RecACHBatchHandler.resubmitEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log("=== RecACHBatchHandler.resubmitEventHandler: begin, eventSeq="
                     + eventSequence
                     + ",length="
                     + evts._array.length
                     + ",instructionType="
                     + evts._array[0].InstructionType);
        eventHandler(eventSequence, evts, dbh, true); // resubmit : true
        FFSDebug.log("=== RecACHBatchHandler.resubmitEventHandler: end");
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    /**
     * Do transaction audit log which logs this information to
     * audit_log table in CB
     *
     * @param dbh
     * @param batchInfo
     * @param preDesc
     */
    private void doTransAuditLog(FFSConnectionHolder dbh, ACHBatchInfo batchInfo,
                                 String preDesc )
    throws FFSException
    {
        String currMethodName = "RecACHBatchHandler.doTransAuditLog:";
        if (_logLevel >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE) {
            //calculate total
            long totalAmountLong = batchInfo.getNonOffBatchCreditSum()
                                       + batchInfo.getNonOffBatchDebitSum();

            BigDecimal amount = new BigDecimal(totalAmountLong).movePointLeft(2);

            // get customerId
            // get customerId
            int customerId = 0 ;
            try {
                customerId = Integer.parseInt(batchInfo.getCustomerId());
            } catch (NumberFormatException nfe) {
                String errDescrip = currMethodName + " CustomerId is not an integer - "
                                    + batchInfo.getCustomerId() + " - " + nfe;
                FFSDebug.log(errDescrip + FFSDebug.stackTrace(nfe),FFSDebug.PRINT_ERR);
                throw new FFSException(nfe, errDescrip);
            }

            // get description
            String desc = preDesc + ", Batch Category = " + batchInfo.getBatchCategory()
                          + ", Batch type = " + batchInfo.getBatchType()
                          + ", Batch balanced type= " + batchInfo.getBatchBalanceType();
            FFSDebug.log(currMethodName + desc, FFSDebug.PRINT_DEV);

            AuditLogRecord _auditLogRec = new AuditLogRecord(batchInfo.getSubmittedBy(),
                                                             null,
                                                             null,
                                                             desc,
                                                             batchInfo.getLogId(),
                                                             AuditLogTranTypes.BPW_ACHRECBATCHTRN,
                                                             customerId,
                                                             amount,
                                                             null,
                                                             batchInfo.getBatchId(),
                                                             batchInfo.getBatchStatus(),
                                                             null,
                                                             null,
                                                             null,
                                                             null,
                                                             0);
            TransAuditLog.logTransAuditLog(_auditLogRec, dbh.conn.getConnection());
        }
    }
    /**
     * Get ach batch and its channel.
     * @param dbh DB connection.
     * @param batchId Batch Id.
     * @param isRecurring Recurring flag.
     * @return ACHBatchInfo having channel Id if found.
     * @throws FFSException
     */
    private ACHBatchInfo getACHBatchById(FFSConnectionHolder dbh, String batchId, boolean isRecurring) throws FFSException {
    	
    	ACHBatchInfo bi = ACHBatch.getACHBatchById(dbh, batchId, isRecurring);
    	EntChannelOps ops = ChannelStore.getChannelIdForTrn(null, batchId, (isRecurring ? DBConsts.ACH_RecBatch : 
    		DBConsts.ACH_Batch), dbh);
    	bi.setChannelId(ops.getChannelIDMod());
    	bi.setChannelGroupId(ops.getChannelGroupIdMod());
    	FFSDebug.log("RecACHBatchHandler.getACHBatchById: Channel Id: " + bi.getChannelId() + " read for batch Id: "
    			+ batchId, FFSConst.PRINT_DEV);
    	return bi;
    }

	/**
	 * Generate ACH batch from recurring ACH batch.
	 * 
	 * @param dbh
	 *            DB connection.
	 * @param recBatchId
	 *            Rec batch id.
	 * @param dueDate
	 *            Due date.
	 * @param effEntryDate
	 *            Effective entry date.
	 * @param channelId
	 *            Channel Id (optional! Can be null).
	 * @return ACHBatchInfo generated from recurring batch and containing channel from where recurring batch was
	 *         submitted.
	 * @throws FFSException
	 */
    private ACHBatchInfo generateACHBatchFromACHRecBatch(FFSConnectionHolder dbh, String recBatchId, String dueDate, 
    		String effEntryDate, String channelId, int channelGroupId) throws FFSException {
		ACHBatchInfo bi = ACHBatch.generateACHBatchFromACHRecBatch(dbh, recBatchId, dueDate, effEntryDate);
		if(channelId != null) {
			bi.setChannelId(channelId);
			bi.setChannelGroupId(channelGroupId);
			ChannelStore.addChannelInfoForTrn(bi.getSubmittedBy(), bi.getBatchId(), DBConsts.ACH_Batch, channelId, dbh, bi.getChannelGroupId());
		}
		FFSDebug.log("RecACHBatchHandler.generateACHBatchFromACHRecBatch: Channel Id: " + channelId +
				" stored for batch Id: " + bi.getBatchId(), FFSConst.PRINT_DEV);
		return bi;
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
		
		String methodName = "++_++ RecACHBatchHandler.createAdjustedInstrInfo";
        FFSDebug.log(methodName, ": starts....", FFSConst.PRINT_DEV);
        
		if(recSrvrTid == null)
			return false;
		try {
			instanceNum = Math.abs(instanceNum);
			AdjustedInstruction.createRecord(recSrvrTid, srvrTid, instanceNum, dbh);
			FFSDebug.log(methodName + " New  AdjustedInstruction record created. RecSrvrTid =  " + recSrvrTid +
					", SrvrTid = " + srvrTid + "InstanceNumber = " + instanceNum, FFSConst.PRINT_DEV);
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
	
	
	
	@SuppressWarnings("unchecked")
	public boolean cancelSkippedInstances(FFSConnectionHolder dbh, String recSrvrTID) {
		
		String methodName = "RecACHBatchHandler.cancelSkippedInstances";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        
		try {
			ArrayList<ACHBatchInfo> batchInfos=ACHBatch.getSkippedACHBatchesByRecSrvrTId(dbh, recSrvrTID);
			
			for (ACHBatchInfo batchInfo : batchInfos) {
				FFSDebug.log("Found " + batchInfo.getBatchId()+ " skipped payment", FFSConst.PRINT_DEV);
				//simply mark it as cancelled as already its schedule and limits would have been deleted.
				ACHBatch.updateACHBatchStatus(batchInfo,DBConsts.CANCELEDON,dbh,  false);
			}
			
		} catch (FFSException e1) {
			FFSDebug.log("++_++ RecACHBatchHandler.cancelSkippedInstances: Error in cancelling skipped records.",
					FFSConst.PRINT_ERR);
			return false;
		}catch (Exception e) {
			FFSDebug.log("++_++ RecACHBatchHandler.cancelSkippedInstances: Error in cancelling skipped records.",
					FFSConst.PRINT_ERR);
			return false;
		} 
		finally
		{
			PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
		}
		PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
		return true;
	}
	
}
