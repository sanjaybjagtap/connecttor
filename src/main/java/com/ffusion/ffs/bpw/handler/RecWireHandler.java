//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.math.BigDecimal;

import com.ffusion.beans.wiretransfers.WireDefines;
import com.ffusion.csil.beans.entitlements.EntChannelOps;
import com.ffusion.ffs.bpw.audit.TransAuditLog;
import com.ffusion.ffs.bpw.db.AdjustedInstruction;
import com.ffusion.ffs.bpw.db.Wire;
import com.ffusion.ffs.bpw.interfaces.ACHConsts;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.RecWireInfo;
import com.ffusion.ffs.bpw.interfaces.WireInfo;
import com.ffusion.ffs.bpw.interfaces.util.AccountUtil;
import com.ffusion.ffs.bpw.master.LimitCheckApprovalProcessor;
import com.ffusion.ffs.bpw.master.WireProcessor;
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
// by the Scheduling engine for the Recurring wire Processing.
//
//=====================================================================

public class RecWireHandler implements DBConsts, FFSConst,
ScheduleConstants, BPWResource, BPWScheduleHandler {
    private PropertyConfig  _propertyConfig = null;

    private WireProcessor _wireProcessor = null;

    private int audit_Level = 0;

    private boolean wireSupportRelease = true;

    public RecWireHandler(){
        _propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);

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

            String supportRelease =
            _propertyConfig.otherProperties.getProperty(
                                                       DBConsts.WIRE_SUPPORT_RELEASE,
                                                       DBConsts.DEFAULT_WIRE_SUPPORT_RELEASE);
            wireSupportRelease = supportRelease.equalsIgnoreCase("TRUE");
        } catch (Exception e) {
            FFSDebug.log("WireApprovalHandler. Invalid Audit log level value",
                         level, PRINT_ERR);
            audit_Level = DBConsts.AUDITLOGLEVEL_NONE;
        }

        //In auto release mode
        if (!wireSupportRelease) {
            _wireProcessor = new WireProcessor();
        }
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

        final String methodName = " RecWireHandler.eventHander: ";
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

        final String methodName = " RecWireHandler.processEvent: ";

        FFSDebug.log(methodName, " begin, eventSeq: " + eventSequence
                     + ",length: " + evts._array.length, PRINT_DEV);

        WireInfo wInfo = null; //for audit logging

        try {
            // process recurring wires
            if (eventSequence == 0) {// FIRST sequence no data
                //do nothing
            } else if (eventSequence == 1) {// NORMAL sequence wire data
                // process each wire in this batch
                for (int i = 0; i < evts._array.length; i++) {

                    String recSrvrTid = evts._array[i].InstructionID;
                    FFSDebug.log(methodName, "processing eventSeq: " +
                                 eventSequence + ",RecSrvrTid: " + recSrvrTid, PRINT_DEV);
                    // get schedule info for this recurring model
                    ScheduleInfo sinfo =
                    ScheduleInfo.getScheduleInfo(evts._array[i].FIId,
                                                 RECWIRETRN, recSrvrTid, dbh);
                    String recChannel = null;
                    int recChannelGroupId = -1;
                    if ( (sinfo != null) &&
                         (sinfo.StatusOption != ScheduleConstants.SCH_STATUSOPTION_CLOSE_SCHEDULED) ) {
                        // we found schedule for the recmodel

                        // Update the transaction history for closed RecWireInfo
                        if (SCH_STATUS_CLOSED.equalsIgnoreCase(sinfo.Status)) {
                            FFSDebug.log(methodName, "schedule closed ",
                                         ",for recwire: ", recSrvrTid, PRINT_DEV);

                            //close the recWire model
                            WireInfo recWireInfo = new RecWireInfo();
                            recWireInfo.setSrvrTid(recSrvrTid);
                            recWireInfo = getWireInfo(dbh, recWireInfo);
                            recChannel = recWireInfo.getChannelId();
                            recChannelGroupId = recWireInfo.getChannelGroupId();
                            recWireInfo.setFiID(evts._array[0].FIId);
                            String wireType = evts._array[0].InstructionType;
                            recWireInfo.setWireType(wireType);
                            recWireInfo.setPrcStatus(POSTEDON);

                            //For audit logging
                            wInfo = recWireInfo;

                            // this wire model is completed update its status
                            boolean isRecurring = true;
                            updateStatus(dbh, recWireInfo,
                                    POSTEDON, isRecurring);
                            
                            //deleteAdjustedInstrInfo(recSrvrTid, dbh);
                            
                        } else {
                        	
                        	try {
								// Check if instance about to be created has been adjusted.
								if (adjustedCurrentInstance(recSrvrTid, sinfo.CurInstanceNum, dbh)) {
									FFSDebug.log(
											methodName
													+ ": Current instance has been adjusted. Skipping creation of Pmt Instruction.",
											FFSConst.PRINT_INF);

									continue;
								} else {
									FFSDebug.log(
											methodName
													+ ": Current instance has not adjusted. Proceed to create Pmt Instruction.",
											FFSConst.PRINT_DEV);
								}
								
							} catch (Exception e) {
								// We are not able to check if this instance is adjusted or not. Still go ahead and
								// try to create one.
								// If we try to insert a duplicate record in BPW_Adj_Instr_Info table the unique
								// constraint will save us.
								FFSDebug.log(methodName + ": " + e.getMessage(), FFSConst.PRINT_INF);
							}
                        	
                            // don't check entitlement for resubmit
                            if (possibleDuplicate == false) {
                                // do entitlement check first,
                                // if not entitled, do not create any thing
                                // Get the recurring model information
                                WireInfo wireInfo = new WireInfo();
                                wireInfo.setSrvrTid(recSrvrTid);
                                wireInfo = getWireInfo(dbh, wireInfo);
                                recChannel = wireInfo.getChannelId();
                                recChannelGroupId = wireInfo.getChannelGroupId();
                                if ( LimitCheckApprovalProcessor.checkEntitlementWire( dbh, wireInfo, null ) == false ) {
                                    String msg = "RecWireHandler.processEvents failed to process: " +
                                                 "Entitlement check failed. RecWireId = " + recSrvrTid;
                                    FFSDebug.log(msg, FFSConst.PRINT_DEV);

                                    // Cancel this recurring model
                                    // Update BPW_RecWireInfo
                                    WireInfo recWireInfo = new RecWireInfo();
                                    recWireInfo.setSrvrTid(recSrvrTid);
                                    recWireInfo = getWireInfo(dbh, recWireInfo);
                                    recWireInfo.setFiID(evts._array[0].FIId);
                                    String wireType = evts._array[0].InstructionType;
                                    recWireInfo.setWireType(wireType);
                                    recWireInfo.setPrcStatus(DBConsts.FAILEDON);

                                    // This wire model failed, update its status.
                                    boolean isRecurring = true;
                                    updateStatus(dbh, recWireInfo,
                                            DBConsts.FAILEDON, isRecurring);
                                    // Close the RECWIRETRN
                                    ScheduleInfo.delete(dbh, recSrvrTid, DBConsts.RECWIRETRN);
                                    // Log in Audit Log
                                    if (audit_Level >= DBConsts.AUDITLOGLEVEL_SYSERROR) {
                                        wireInfo.setFiID(recWireInfo.getFiID());
                                        wireInfo.setWireType(recWireInfo.getWireType());
                                        wireInfo.setPrcStatus(recWireInfo.getPrcStatus());
                                        logError(dbh, wireInfo,
                                                 "Recurring wire processing failed, "
                                                 + "not entitled to submit wires.");
                                    }

                                    continue;
                                }
                            }

                            //create a single instance from the recurring model
                            // create new WireInfo and insert it in WireInfo table
                            FFSDebug.log(methodName, "creating single ",
                                         "intance from recurring model: ",
                                         recSrvrTid, PRINT_DEV);
                            // for last wire amount may be diffrent
                            // tell the generater if this is the last wire
                            WireInfo wireInfo = null;
                            if (sinfo.CurInstanceNum == sinfo.InstanceCount) {
                                wireInfo =
                                Wire.generateSingleWireFromRecWire(dbh, recSrvrTid,
                                                                   BPWUtil.getDateBeanFormat(sinfo.NextInstanceDate), WIRELAST, sinfo.CurInstanceNum);
                                if(recChannel != null) {
                                	ChannelStore.addChannelInfoForTrn(wireInfo.getSubmittedBy(), wireInfo.getSrvrTid(),
                                			DBConsts.BPW_WireInfo, recChannel, dbh, recChannelGroupId);
                                	FFSDebug.log("RecWireHandler.processEvents: Channel Id: " + wireInfo.getChannelId() 
                                			+ " stored for wire Id: " + wireInfo.getSrvrTid(), FFSConst.PRINT_DEV);
                                } else {
                                	FFSDebug.log("RecWireHandler.processEvents: No channel found for wire Id: " 
                                			+ wireInfo.getSrvrTid(), FFSConst.PRINT_DEV);
                                }
                            } else {
                                wireInfo =
                                Wire.generateSingleWireFromRecWire(dbh, recSrvrTid,
                                                                   BPWUtil.getDateBeanFormat(sinfo.NextInstanceDate), WIREMIDDLE, sinfo.CurInstanceNum);
                                if(recChannel != null) {
                                	ChannelStore.addChannelInfoForTrn(wireInfo.getSubmittedBy(), wireInfo.getSrvrTid(),
                                			DBConsts.BPW_WireInfo, recChannel, dbh, recChannelGroupId);
                                	FFSDebug.log("RecWireHandler.processEvents: Channel Id: " + wireInfo.getChannelId() 
                                			+ " stored for wire Id: " + wireInfo.getSrvrTid(), FFSConst.PRINT_DEV);
                                } else {
                                	FFSDebug.log("RecWireHandler.processEvents: No channel found for wire Id: " 
                                			+ wireInfo.getSrvrTid(), FFSConst.PRINT_DEV);
                                }
                            }
                            wireInfo.setChannelId(recChannel);
                            wireInfo.setChannelGroupId(recChannelGroupId);
                            //don't call limits for resubmit
                            if (!possibleDuplicate) {

                                FFSDebug.log(methodName,
                                             "wireInfo for processWireAdd"
                                             + wireInfo, PRINT_DEV);

                                //Do limit check processing
                                WireProcessor.limitCheckWireAdd(dbh, wireInfo, null);
								if (DBConsts.LIMIT_CHECK_FAILED.equalsIgnoreCase(wireInfo.getPrcStatus())) {
									// the limit check failed - manually rollback the limit since the running total will be updated as part of the limit
									// check and this is part of a larger transaction that can't be rolled back.
									// save/restore the status code and the message, since they will be modified by the call to processWireDelete.
									int statusCode = wireInfo.getStatusCode();
									String statusMsg = wireInfo.getStatusMsg();
									LimitCheckApprovalProcessor.processWireDelete(dbh, wireInfo, null);
									wireInfo.setStatusCode(statusCode);
									wireInfo.setStatusMsg(statusMsg);
								}
                            }

                            FFSDebug.log(methodName,
                                         "wireInfo before status update"
                                         + wireInfo, PRINT_DEV);
                            Wire.updateStatus(dbh, wireInfo);


                            //Following implements wire auto-release feature
                            //If BPTW dose not support wire release and the
                            //single wire instance passed Limit Check and
                            //Approval process successfully, do auto-release
                            //mode processing
                            if ( !(wireSupportRelease) &&
                                 (wireInfo.getPrcStatus().equals(DBConsts.CREATED)) ) {

                                wireInfo = _wireProcessor.processSingleWireInAutoReleaseMode(dbh, wireInfo);
                            }

                            //
                            //For audit logging
                            wInfo = wireInfo;

                            if (wireInfo == null ||
                                wireInfo.getStatusCode() != SUCCESS) {
                                // No record in RecWireInfo table
                                // cancel the schedule for  this recurring model
                                String err = methodName + "FAILED: " +
                                             "COULD NOT FIND THE RecSrvrTid: "
                                             + recSrvrTid + " in BPW_RecWireInfo TABLE";
                                FFSDebug.log("ERRORCODE:" +
                                             ACHConsts.RECWIREINFO_NOT_FOUND_IN_DB, err,
                                             PRINT_ERR);

                                // Cancel this shedule since no data for it in
                                // BPW_RecWireInfo table
                                FFSDebug.log(methodName, "canceling schedule ",
                                             "for recurring model: ",
                                             recSrvrTid, PRINT_DEV);
                                ScheduleInfo.cancelSchedule(dbh, RECWIRETRN,
                                                            recSrvrTid); // close the scheduleInfo

                                // log into AuditLog
                                if (audit_Level >= DBConsts.AUDITLOGLEVEL_SYSERROR) {
                                    logError(dbh, wireInfo,
                                             "Recurring wire processing failed, "
                                             + "wire transfer is not found in database");
                                }
                                continue;
                            }

                            //set "BPTW" to ProcessedBy field
                            wireInfo.setProcessedBy(DBConsts.BPTW);

                            // No schedule for the single instance is required at
                            // this point since the schedule will be created after
                            // this instance is released

                            // log into AuditLog
                            if (audit_Level >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE) {
                                if (SCH_STATUS_CLOSED.equalsIgnoreCase(sinfo.Status)) {
                                    doAuditLogging(dbh, wireInfo,
                                                   RECWIRE_HANDLER_POSTEDON);
                                } else {
                                    doAuditLogging(dbh, wireInfo,
                                                   RECWIRE_HANDLER_NEXT_INSTANCE_CREATED);
                                }
                            }

                        }
                    } else {
                        // SchedeuleInfo not found means that it is closed
                        //close the recWire model
                        FFSDebug.log(methodName, "no schedule found for the ",
                                     "recurringrecurring model: " + recSrvrTid,
                                     ". This model will be closed", PRINT_DEV);

                        boolean isRecurring = true;
                        WireInfo recWireInfo = new RecWireInfo();
                        recWireInfo.setSrvrTid(recSrvrTid);
                        recWireInfo = getWireInfo(dbh, recWireInfo);
                        recWireInfo.setFiID(evts._array[0].FIId);
                        recWireInfo.setWireType(evts._array[0].InstructionType);

                        //For audit logging
                        wInfo = recWireInfo;

                        updateStatus(dbh, recWireInfo, POSTEDON,
                                isRecurring);
                        
                        // delete adjusted instruction
                        //deleteAdjustedInstrInfo(recSrvrTid, dbh);
                    }
                } // end of for loop this wire job is done
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
                logError(dbh, wInfo, null);
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
    	String methodName = "RecWireHandler.resubmitEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log(" RecWireHandler.resubmitEventHandler: begin, eventSeq: "
                     + eventSequence
                     + ",length: "
                     + evts._array.length
                     + ",instructionType: "
                     + evts._array[0].InstructionType);
        //omarabi:
        boolean possibleDuplicate = true;
        processEvents(eventSequence, evts, dbh, possibleDuplicate);
        FFSDebug.log(" RecWireHandler.resubmitEventHandler: end");
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
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

        String curMethodName = "FailedWireHandler.doAuditLogging";

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

        if (desc == null) {
            logDesc = RECWIRE_HANDLER_POSTEDON;
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
                                                AuditLogTranTypes.BPW_RECWIREHANDLER, //tranType
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

    /**
     * Do Audit logging
     *
     * @param wireInfo
     * @param error - If logging for error or exception
     * @exception Exception
     */
    private void logError( FFSConnectionHolder dbh,
                           WireInfo wireInfo,
                           String desc) {

        String curMethodName = "FailedWireHandler.logError";

        try {
            if (desc != null) {
                doAuditLogging(dbh, wireInfo, desc);
            } else {
                doAuditLogging(dbh, wireInfo, "Recurring Wire processing failed,"
                               + " unknown error occurred");
            }
        } catch (Exception ex) {
            String errDescrip = curMethodName + " failed " + ex;
            FFSDebug.log(errDescrip + FFSDebug.stackTrace(ex), PRINT_ERR);
        }
    }
    
    /**
	 * Checks if current instance of a recurring schedule has been adjusted by user. Queries BPW_Adj_Instr_Info table.
	 * 
	 * @param recSrvrTid
	 *            RecSrvrTID of recurring model.
	 * @param currentInstance
	 *            The instance number of instance that is about to be created.
	 * @param dbh
	 *            DB connection.
	 * @return <code>true</code> if current instance is adjusted, <code>false</code>.
	 */
	private boolean adjustedCurrentInstance(String recSrvrTid, int currentInstance, FFSConnectionHolder dbh)
			throws FFSException {

		try {
			return AdjustedInstruction.isInstanceAdjusted(recSrvrTid, currentInstance, dbh);
		} catch (FFSException e) {
			FFSDebug.log("++_++ RecPmtHandler.adjustedCurrentInstance: Error in checking for records.",
					FFSConst.PRINT_ERR);
			throw e;
		}
		
	}
	
	/**
	 * Delete all records with specified RecSrvrTID from BPW_Adj_Instr_Info table.
	 * 
	 * @param recSrvrTid
	 *            RecSrvrTID of recurring model.
	 * @param dbh
	 *            Database connection. This methods committs the delete.
	 */
	/*private void deleteAdjustedInstrInfo(String recSrvrTid, FFSConnectionHolder dbh) {

		if (recSrvrTid == null)
			return;
		try {
			AdjustedInstruction.deleteRecords(recSrvrTid, dbh);
			dbh.conn.commit();
		} catch (Exception e) {
			FFSDebug.log("++_++ RecPmtHandler.deleteAdjustedInstrInfo: Error in deleting records.", FFSConst.PRINT_ERR);
			dbh.conn.rollback();
		}
	}*/
	
	/**
	 * Get wire info and its channel id.
	 * 
	 * @param dbh
	 *            DB connection.
	 * @param recWireInfo
	 *            Recurring wire info containing SrvrTid.
	 * @return WireInfo.
	 * @throws FFSException
	 */
    private WireInfo getWireInfo(FFSConnectionHolder dbh, WireInfo recWireInfo) throws FFSException {
    	
    	final String method = "RecWireHandler.getWireInfo: ";
    	WireInfo wi = Wire.getWireInfo(dbh, recWireInfo, true);
    	EntChannelOps channelOps = ChannelStore.getChannelIdForTrn(wi.getSubmittedBy(), recWireInfo.getSrvrTid(), 
    			DBConsts.BPW_RecWireInfo, dbh);
    	wi.setChannelId(channelOps.getChannelIDMod());
    	wi.setChannelGroupId(channelOps.getChannelGroupIdMod());
    	if(wi.getChannelId() == null) {
    		FFSDebug.log(method + "No channel found for recurring wire Id: "
    				+ wi.getSrvrTid(), FFSConst.PRINT_DEV);
    	} else {
    		FFSDebug.log(method + "Read channel Id: " + wi.getChannelId() + " for recurring wire Id: "
    				+ wi.getSrvrTid(), FFSConst.PRINT_DEV);
    	}
    	return wi;
    }
    /**
     * Update wire status, wire channel. Log to debug log.
     * @param dbh DB connection.
     * @param recWireInfo Wire info.
     * @param status Status
     * @param isRec Recurring flag.
     * @throws FFSException
     */
    private void updateStatus(FFSConnectionHolder dbh, WireInfo recWireInfo, String status, boolean isRec) throws FFSException {
    	
    	final String method = "RecWireHandler.updateStatus: ";
    	Wire.updateStatus(dbh, recWireInfo,
                status, isRec);
    	if(recWireInfo.getChannelId() == null) {
    		FFSDebug.log(method + "No channel found for update. Recurring wire Id: "
    				+ recWireInfo.getSrvrTid(), FFSConst.PRINT_DEV);
    	}
    	else {
    		ChannelStore.updateChannelInfoForTrn(recWireInfo.getSubmittedBy(), recWireInfo.getSrvrTid(), 
    				null, (isRec ? DBConsts.BPW_RecWireInfo : DBConsts.BPW_WireInfo), recWireInfo.getChannelId(), recWireInfo.getChannelGroupId(), dbh);
    		FFSDebug.log(method + "Updated channel Id: " + recWireInfo.getChannelId() + " for recurring wire Id: "
    				+ recWireInfo.getSrvrTid(), FFSConst.PRINT_DEV);
    	}
    }
}
