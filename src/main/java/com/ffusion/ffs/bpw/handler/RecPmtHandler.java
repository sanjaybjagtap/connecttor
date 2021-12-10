//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

import com.ffusion.csil.beans.entitlements.EntChannelOps;
import com.ffusion.ffs.bpw.audit.AuditAgent;
import com.ffusion.ffs.bpw.audit.TransAuditLog;
import com.ffusion.ffs.bpw.db.AdjustedInstruction;
import com.ffusion.ffs.bpw.db.BPWExtraInfo;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.db.Payee;
import com.ffusion.ffs.bpw.db.PmtInstruction;
import com.ffusion.ffs.bpw.db.RecPmtInstruction;
import com.ffusion.ffs.bpw.db.RecSrvrTIDToSrvrTID;
import com.ffusion.ffs.bpw.db.RecSrvrTrans;
import com.ffusion.ffs.bpw.db.SmartCalendar;
import com.ffusion.ffs.bpw.fulfill.FulfillAgent;
import com.ffusion.ffs.bpw.interfaces.ACHConsts;
import com.ffusion.ffs.bpw.interfaces.AuditLogConsts;
import com.ffusion.ffs.bpw.interfaces.BPWException;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.OFXConsts;
import com.ffusion.ffs.bpw.interfaces.PayeeInfo;
import com.ffusion.ffs.bpw.interfaces.PmtInfo;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.RecPmtInfo;
import com.ffusion.ffs.bpw.master.LimitCheckApprovalProcessor;
import com.ffusion.ffs.bpw.master.channels.ChannelStore;
import com.ffusion.ffs.bpw.serviceMsg.BPWMsgBroker;
import com.ffusion.ffs.bpw.serviceMsg.MsgBuilder;
import com.ffusion.ffs.bpw.util.BPWTrackingIDGenerator;
import com.ffusion.ffs.bpw.util.BPWUtil;
import com.ffusion.ffs.bpw.util.resources.BPWLocaleUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.scheduling.constants.ScheduleConstants;
import com.ffusion.ffs.scheduling.db.Event;
import com.ffusion.ffs.scheduling.db.EventInfoLog;
import com.ffusion.ffs.scheduling.db.ScheduleInfo;
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
// by the Scheduling engine for the Recurring Payment Processing.
//
//=====================================================================

public class RecPmtHandler implements BPWScheduleHandler{
	// FIXME TODO for testing remove me
	// private static int batchNum = 0;
	private final PropertyConfig _propertyConfig;
	
	// This flag is used to support schedule payment when fullfillment system is configured for immediate fund allocation and processing.
	private boolean _supportFuturePmtWithImmediateConfig = false;
	
	// This flag is used to skip fund allocation process
	private boolean _supportFundAllocation = true;
	
	public RecPmtHandler() {
		_propertyConfig = (PropertyConfig) FFSRegistry.lookup(BPWResource.PROPERTYCONFIG);
		
		String supportFuturePmtWithImmediateConfig = _propertyConfig.otherProperties.getProperty(DBConsts.SUPPORT_FUTURE_PMT_WITH_IMMEDIATE_CONFIG,
        		DBConsts.TRUE);
        _supportFuturePmtWithImmediateConfig = Boolean.valueOf(supportFuturePmtWithImmediateConfig);

		String supportFundAllocation = _propertyConfig.otherProperties.getProperty(DBConsts.SUPPORT_FUND_ALLOCATION,
        		DBConsts.TRUE);
        _supportFundAllocation = Boolean.valueOf(supportFundAllocation);
	}

	// =====================================================================
	// eventHandler()
	// Description: This method is called by the Scheduling engine
	// Arguments: none
	// Returns: none
	// Note:
	// =====================================================================
	public void eventHandler(int eventSequence, EventInfoArray evts, FFSConnectionHolder dbh) throws FFSException {

		String methodName = "RecPmtHandler.eventHandler: ";
		long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);

		FFSDebug.log(methodName, "=== FundsAllocHandler.eventHandler: begin, eventSeq=" + eventSequence + ",length="
				+ evts._array.length, FFSConst.PRINT_DEV);

		FulfillAgent fagent = (FulfillAgent) FFSRegistry.lookup(BPWResource.FULFILLAGENT);
		if (fagent == null) {
			PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
			throw new FFSException("FulfillAgent is null!!");
		}
		FFSConnectionHolder dbhDirty = null;
		try {
			// =================================
			// Do event sequence
			// =================================
			if (eventSequence == 0) { // FIRST sequence
				/*
				 * // FIXME TODO for testing remove me batchNum = 1; FFSDebug.log(methodName, "eventSeq : "+
				 * eventSequence, ", batchNum: " +batchNum, FFSConst.PRINT_DEV); batchNum++;
				 */

			} else if (eventSequence == 1) { // NORMAL sequence
				/*
				 * // FIXME TODO for testing remove me FFSDebug.log(methodName, "eventSeq : "+ eventSequence,
				 * ", batchNum: " +batchNum, FFSConst.PRINT_DEV); batchNum++;
				 */

				try {
					dbhDirty = new FFSConnectionHolder();
					dbhDirty.conn = DBUtil.getDirtyReadConnection();

					if (dbhDirty.conn == null) {
						throw new Exception("Null dirty connection returned");
					}
				} catch (Exception ex) {
					String err = methodName + "Failed to get DB connection ";
					FFSDebug.log(ex, err, FFSConst.PRINT_ERR);
					PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
					throw new Exception(err, ex);
				}
				String recSrvrTID = null;
				for (int i = 0; i < evts._array.length; i++) {
					try {

						if (evts._array[i] == null) {

							FFSDebug.log(methodName, " ++--++ Invalid Transaction in this batch: " + evts._array[i],
									", Transcaction will be ignored, Transaction counter: " + i, FFSConst.PRINT_DEV);
							continue;
						}
						recSrvrTID = evts._array[i].InstructionID;
						String fiId = evts._array[i].FIId;
						FFSDebug.log(methodName, "eventSeq: " + eventSequence, ", RecSrvrTID: ", recSrvrTID,
								FFSConst.PRINT_DEV);
						//
						// create a record in PmtInstruction table
						//
						RecPmtInstruction rpinstr = RecPmtInstruction.getRecPmtInstr(recSrvrTID, dbhDirty);

						// FFSDebug.log(methodName, " ++--++ rpinstr: " + rpinstr,
						// FFSConst.PRINT_DEV);

						String[] res = RecSrvrTrans.findResponseByRecSrvrTID(recSrvrTID, dbhDirty);
						// FFSDebug.log(methodName, " ++--++ res: " + res,
						// FFSConst.PRINT_DEV);
						dbhDirty.conn.commit();
						if (rpinstr == null || (rpinstr != null && DBConsts.CANCELEDON.equals(rpinstr.getStatus()))) {
							String msg = "*** RecPmtHandler.eventHandler failed: could not find the RecSrvrTID="
									+ recSrvrTID + " in BPW_RecPmtInstruction";
							FFSDebug.console(msg);
							FFSDebug.log(msg);
							ScheduleInfo.cancelSchedule(dbh, DBConsts.RECPMTTRN, recSrvrTID, fiId); // close
																									// the
																									// scheduleInfo
							if (res[0] != null)
								RecSrvrTrans.cancelRecSrvrTrans(recSrvrTID, dbh); // close
							// the
							// recSrvrTrans
							
							dbh.conn.commit();
							continue;
						}

						String pmtStatus = rpinstr.getStatus();
						FFSDebug.log(methodName, " ++--++ pmtStatus-0: ", pmtStatus, FFSConst.PRINT_DEV);
						
						RecPmtInfo rpi = rpinstr.getRecPmtInfo();
						// FFSDebug.log(methodName, " ++--++ rpi0: " + rpi,
						// FFSConst.PRINT_DEV);
						// Date date = new Date();
						// make sure we use new tracking ID (logID)
						// we need to generate a new tracking Id for each
						// instance
						// generated from the recurring model
						String logId = BPWTrackingIDGenerator.getNextId();

						BPWMsgBroker broker = (BPWMsgBroker) FFSRegistry.lookup(BPWResource.BPWMSGBROKER);
						if (broker == null)
							throw new FFSException("RecPmtHandler.eventHandler:BPWMsgBroker is null!!");

						if (res[0] == null) {
							String msg = "*** RecPmtHandler.eventHandler failed: could not find the RecSrvrTID="
									+ recSrvrTID + " in BPW_RecSrvrTrans";
							FFSDebug.console(msg);
							FFSDebug.log(msg);
							ScheduleInfo.cancelSchedule(dbh, DBConsts.RECPMTTRN, recSrvrTID, fiId); // close
																									// the
																									// scheduleInfo
							rpinstr.removeFromDB(dbh); // close the
							// RecPmtInstruction

							// Remove all info pertaining to adjusted instructions.

							dbh.conn.commit();
							continue;
						}
						FFSDebug.log(methodName, " ++--++ Getting schedule for recSrvrTID: ", recSrvrTID,
								FFSConst.PRINT_DEV);

						ScheduleInfo sinfo = ScheduleInfo.getScheduleInfo(evts._array[i].FIId, DBConsts.RECPMTTRN,
								recSrvrTID, dbhDirty);
						// FFSDebug.log(methodName, " ++--++ sinfo: " + sinfo,
						// FFSConst.PRINT_DEV);

						dbhDirty.conn.commit();
						if (sinfo != null) {
							FFSDebug.log(methodName, " ++--++ sinfo.Status: ", sinfo.Status, FFSConst.PRINT_DEV);

						}

						AuditAgent audit = (AuditAgent) FFSRegistry.lookup(BPWResource.AUDITAGENT);
						if (audit == null)
							throw new BPWException("RecPmtHandler.eventHandler: AuditAgent is null!!");

						int msgKey = AuditLogConsts.AUDITLOG_MSG_BILLPAY_PROCESSING_REC_BILLPMT;

						if ((sinfo != null)
								&& (sinfo.StatusOption != ScheduleConstants.SCH_STATUSOPTION_CLOSE_SCHEDULED)) {
							// Update the transaction history for closed
							// RecPmtInstruction

							PropertyConfig propertyConfig = (PropertyConfig) FFSRegistry
									.lookup(BPWResource.PROPERTYCONFIG);

							if (res[0].startsWith("OFX151")) {
								com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeRecPmtTrnRsV1 oldRecRs = (com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeRecPmtTrnRsV1) broker
										.parseMsg(res[1], "RecPmtTrnRsV1", "OFX151");

								if (sinfo.Status.compareTo(ScheduleConstants.SCH_STATUS_CLOSED) == 0) {
									// mark skipped instances to cancelled...
									cancelSkippedInstances(dbh, recSrvrTID);
									RecPmtInstruction.updateStatus(dbh, recSrvrTID, DBConsts.POSTEDON);
									pmtStatus = DBConsts.POSTEDON;
									oldRecRs.RecPmtTrnRs.RecPmtTrnRsV1Un.RecPmtRs.RecurrInst.NInsts = 0;
									oldRecRs.RecPmtTrnRs.RecPmtTrnRsV1Un.RecPmtRs.PmtInfo.DtDue = sinfo.NextInstanceDate + "0000";
									audit.updateRecPmtRsV1(oldRecRs, dbh, DBConsts.POSTEDON);
									dbh.conn.commit();

								} else {
									try {
										// Check if instance about to be created has been adjusted.
										if (adjustedCurrentInstance(recSrvrTID, sinfo.CurInstanceNum, dbh)) {
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
										PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
									}
									// create PmtInstruction
									PmtInfo pi = new PmtInfo("", rpi.CustomerID, rpi.FIID, rpi.PayeeID, rpi.PayAcct,
											rpi.PayeeListID, rpi.getAmt(), rpi.BankID, rpi.AcctDebitID,
											rpi.AcctDebitType, rpi.Memo, rpi.ExtdPmtInfo, rpi.DateCreate, rpi.CurDef,
											DBConsts.WILLPROCESSON, sinfo.NextInstanceDate, DBConsts.PMTTYPE_RECURRING,
											recSrvrTID, null, null, rpi.submittedBy);
									if (sinfo.CurInstanceNum == sinfo.InstanceCount) {
										pi.setAmt(rpi.getFinalAmount());
									}
									
									// Set Account debit country code in payment info
									pi.setAcctDebitCountryCode(rpi.getAcctDebitCountryCode());
																		
									// do entitlement check first,
									// if not entitled, do not create any thing
									// Currently entitlement check is done on entitlement group. No profile
									// information required.
									if (doEntitlementCheck(pi) == false) {
										String msg = "RecPmtHandler.eventHandler failed to process: "
												+ "Entitlement check failed. Customer Id= " + pi.CustomerID;
										FFSDebug.log(msg, FFSConst.PRINT_DEV);

										// Cancel this recurring model
										// mark skipped instances to cancelled...
										cancelSkippedInstances(dbh, recSrvrTID);
										// Update BPW_RecPmtInstruction
										RecPmtInstruction.updateStatus(dbh, recSrvrTID, DBConsts.FAILEDON);
										pmtStatus = DBConsts.FAILEDON;
										// Close the RECPMTTRN
										ScheduleInfo.delete(dbh, recSrvrTID, DBConsts.RECPMTTRN);
										// Update BPW_RecSrvrTrans
										oldRecRs.RecPmtTrnRs.RecPmtTrnRsV1Un.RecPmtRs.RecurrInst.NInsts = -1;
										oldRecRs.RecPmtTrnRs.RecPmtTrnRsV1Un.RecPmtRs.RecurrInst.NInstsExists = true;
										audit.updateRecPmtRsV1(oldRecRs, dbh, pmtStatus);
										// Log to audit log, this is done at the
										// bottom,
										// before going back to the top of the
										// for
										// loop
										msgKey = AuditLogConsts.AUDITLOG_MSG_BILLPAY_PROCESSING_REC_BILLPMT_ENT_FAILED;

									} else {
										// omer: new tracking ID for this
										// instance
										pi.LogID = logId; // make sure we use
															// new
										// tracking ID (logID)
										PmtInstruction instr = new PmtInstruction();
										instr.setPmtInfo(pi);
										instr.setSrvrTID();
										instr.getPmtInfo().LogID = logId;
										PayeeInfo payee = Payee.findPayeeByID(rpi.PayeeID, dbhDirty);
										dbhDirty.conn.commit();
										instr.setRouteID(payee.getRouteID());

										// Note the instance number in adjusted instruction info before storing the
										// instruction.
										try {
											AdjustedInstruction.createRecord(recSrvrTID, instr.getSrvrTID(),
													sinfo.CurInstanceNum, dbh);
										} catch (FFSException e) {
											// Possible unique constraint violation.
											// Don't create this payment.
											FFSDebug.log(
													"RecPmtHandler.eventHandler: Error in creating a record in adjusted"
															+ " instruction table. Instance " + sinfo.CurInstanceNum
															+ " of RecSrvrTid: " + recSrvrTID + " skipped.",
													e.getMessage(), FFSConst.PRINT_ERR);
											PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
											continue;
										}
										instr.storeToDB(dbh);

										// create RecSrvrTIDToSrvrTID
										String srvrTid = instr.getSrvrTID();
										RecSrvrTIDToSrvrTID.create(dbh, recSrvrTID, srvrTid);

										// Do limit checking for payment and
										// create
										// schedules

										pi.SrvrTID = srvrTid;
										pi.payeeInfo = payee; // Payee needs to
																// set,
										// required by bean
										// converter
										HashMap extraInfo = new HashMap();
										
										
										doLimitChecking(dbh, pi, extraInfo, sinfo,
												propertyConfig.getFundsAllocRetries());

										// save pmt history
										oldRecRs.RecPmtTrnRs.RecPmtTrnRsV1Un.RecPmtRs.RecurrInst.NInsts = sinfo.InstanceCount
												- sinfo.CurInstanceNum + 1;
										if (oldRecRs.RecPmtTrnRs.RecPmtTrnRsV1Un.RecPmtRs.RecurrInst.NInsts == 1) {
											if (oldRecRs.RecPmtTrnRs.RecPmtTrnRsV1Un.RecPmtRs.FinalAmtExists)
												oldRecRs.RecPmtTrnRs.RecPmtTrnRsV1Un.RecPmtRs.PmtInfo.TrnAmt = oldRecRs.RecPmtTrnRs.RecPmtTrnRsV1Un.RecPmtRs.FinalAmt;
										}
										oldRecRs.RecPmtTrnRs.RecPmtTrnRsV1Un.RecPmtRs.PmtInfo.DtDue = sinfo.NextInstanceDate + "0000";
										oldRecRs.RecPmtTrnRs.RecPmtTrnRsV1Un.RecPmtRs.InitialAmtExists = false;
										audit.updateRecPmtRsV1(oldRecRs, dbh, DBConsts.WILLPROCESSON); // omer:
										com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.EnumAccountEnum accttype = MsgBuilder
												.getOFX151AcctEnum(pi.AcctDebitType);

										com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypePmtTrnRsV1 rs = buildPmtAddRsV1(
												pi, oldRecRs.RecPmtTrnRs.RecPmtTrnRsV1Un.RecPmtRs.PmtInfo, payee,
												recSrvrTID, accttype);
										audit.savePmtRsV1(rs, rpi.CustomerID, pi.Status, dbh);
									}
								}
							} else if (res[0].startsWith("OFX200")) {
								com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeRecPmtTrnRsV1 oldRecRs = (com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeRecPmtTrnRsV1) broker
										.parseMsg(res[1], "RecPmtTrnRsV1", "OFX200");

								if (sinfo.Status.compareTo(ScheduleConstants.SCH_STATUS_CLOSED) == 0) {
									// mark skipped instances to cancelled...
									cancelSkippedInstances(dbh, recSrvrTID);
									RecPmtInstruction.updateStatus(dbh, recSrvrTID, DBConsts.POSTEDON);
									pmtStatus = DBConsts.POSTEDON;
									oldRecRs.RecPmtTrnRs.RecPmtTrnRsUn.RecPmtRs.RecurrInst.NInsts = 0;
									oldRecRs.RecPmtTrnRs.RecPmtTrnRsUn.RecPmtRs.PmtInfo.DtDue = sinfo.NextInstanceDate + "0000";
									audit.updateRecPmtRsV1(oldRecRs, dbh, DBConsts.POSTEDON);
									dbh.conn.commit();

								} else {
									try {
										// Check if instance about to be created has been adjusted.
										if (adjustedCurrentInstance(recSrvrTID, sinfo.CurInstanceNum, dbh)) {
											FFSDebug.log(
													methodName
													+ ": Current instance has been adjusted. Skipping creation of Pmt Instruction.",
													FFSConst.PRINT_DEV);

											// FIXME: Audit this?
											continue;
										} else {
											FFSDebug.log(
													methodName
													+ ": Current instance has not adjusted. Proceed to create Pmt Instruction.",
													FFSConst.PRINT_DEV);
										}
									}
									catch(Exception e) {
										// We are not able to check if this instance is adjusted or not. Still go ahead and
										// try to create one.
										// If we try to insert a duplicate record in BPW_Adj_Instr_Info table the unique
										// constraint will save us.
										FFSDebug.log(methodName + ": " + e.getMessage(), FFSConst.PRINT_INF);
										PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
									}
									// create PmtInstruction
									PmtInfo pi = new PmtInfo("", rpi.CustomerID, rpi.FIID, rpi.PayeeID, rpi.PayAcct,
											rpi.PayeeListID, rpi.getAmt(), rpi.BankID, rpi.AcctDebitID,
											rpi.AcctDebitType, rpi.Memo, rpi.ExtdPmtInfo, rpi.DateCreate, rpi.CurDef,
											DBConsts.WILLPROCESSON, sinfo.NextInstanceDate, DBConsts.PMTTYPE_RECURRING,
											recSrvrTID, null, null, rpi.submittedBy);
									
									pi.setPaymentSubType(rpi.getPaymentSubType());
									if (sinfo.CurInstanceNum == sinfo.InstanceCount) {
										pi.setAmt(rpi.getFinalAmount());
									}
									
									// Set Account debit country code in payment info
									pi.setAcctDebitCountryCode(rpi.getAcctDebitCountryCode());
									
									// FFSDebug.log(methodName, " ++--++ pi: "
									// + pi, FFSConst.PRINT_DEV);
									// do entitlement check first,
									// if not entitled, do not create any thing
									if (doEntitlementCheck(pi) == false) {
										String msg = "RecPmtHandler.eventHandler failed to process: "
												+ "Entitlement check failed. Customer Id= " + pi.CustomerID;
										FFSDebug.log(msg, FFSConst.PRINT_DEV);

										// Cancel this recurring model
										// mark skipped instances to cancelled...
										cancelSkippedInstances(dbh, recSrvrTID);
										// Update BPW_RecPmtInstruction
										RecPmtInstruction.updateStatus(dbh, recSrvrTID, DBConsts.FAILEDON);
										pmtStatus = DBConsts.FAILEDON;
										// Close the RECPMTTRN
										ScheduleInfo.delete(dbh, recSrvrTID, DBConsts.RECPMTTRN);
										// Update BPW_RecSrvrTrans
										oldRecRs.RecPmtTrnRs.RecPmtTrnRsUn.RecPmtRs.RecurrInst.NInsts = -1;
										oldRecRs.RecPmtTrnRs.RecPmtTrnRsUn.RecPmtRs.RecurrInst.NInstsExists = true;
										audit.updateRecPmtRsV1(oldRecRs, dbh, pmtStatus);
										// Log to audit log, this is done at the
										// bottom,
										// before going back to the top of the
										// for
										// loop
										msgKey = AuditLogConsts.AUDITLOG_MSG_BILLPAY_PROCESSING_REC_BILLPMT_ENT_FAILED;

									} else {
										pi.LogID = logId;
										PmtInstruction instr = new PmtInstruction();
										instr.setPmtInfo(pi);
										instr.setSrvrTID();
										PayeeInfo payee = Payee.findPayeeByID(rpi.PayeeID, dbhDirty);
										dbhDirty.conn.commit();
										instr.setRouteID(payee.getRouteID());

										// FFSDebug.log(methodName,
										// " ++--++ instr: " + instr,
										// FFSConst.PRINT_DEV);
										
										// Note the instance number in adjusted instruction info before storing the
										// instruction.
										try {
											AdjustedInstruction.createRecord(recSrvrTID, instr.getSrvrTID(),
													sinfo.CurInstanceNum, dbh);
										} catch (FFSException e) {
											// Possible unique constraint violation.
											// Don't create this payment.
											FFSDebug.log(
													"RecPmtHandler.eventHandler: Error in creating a record in adjusted"
															+ " instruction table. Instance " + sinfo.CurInstanceNum
															+ " of RecSrvrTid: " + recSrvrTID + " skipped.",
													e.getMessage(), FFSConst.PRINT_ERR);
											PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
											continue;
										}
										instr.storeToDB(dbh);
										// Add ExtdInfo to BPW_ExtraInfo
										if ((pi.ExtdPmtInfo != null) && (!pi.ExtdPmtInfo.trim().equals(""))) {
											if (BPWExtraInfo.processXtraInfoAsString(dbh, pi.FIID, instr.getSrvrTID(), // recordId
													DBConsts.IFXPMT, // recordYype
													pi.ExtdPmtInfo, // ExtraInfo
													// as
													// ','
													// separated
													// string
													ACHConsts.ACTION_ADD) != DBConsts.SUCCESS) {
												String errDescrip = "*** RecPmtHandler.eventHandler failed to process ExtraInfo:";
												FFSDebug.log(errDescrip, FFSConst.PRINT_ERR);
												PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
												throw new FFSException(errDescrip);
											}
										}
										// create RecSrvrTIDToSrvrTID
										String srvrTid = instr.getSrvrTID();
										FFSDebug.log(methodName, " ++--++ srvrTid: ", srvrTid, FFSConst.PRINT_DEV);

										RecSrvrTIDToSrvrTID.create(dbh, recSrvrTID, srvrTid);

										// Do limit Checking
										HashMap extraInfo = new HashMap();
										pi.SrvrTID = srvrTid;
										pi.payeeInfo = payee; // needs to set,
										// required by bean
										// converter
										doLimitChecking(dbh, pi, extraInfo, sinfo,
												propertyConfig.getFundsAllocRetries());

										// save pmt history
										oldRecRs.RecPmtTrnRs.RecPmtTrnRsUn.RecPmtRs.RecurrInst.NInsts = sinfo.InstanceCount
												- sinfo.CurInstanceNum + 1;
										if (oldRecRs.RecPmtTrnRs.RecPmtTrnRsUn.RecPmtRs.RecurrInst.NInsts == 1) {
											if (oldRecRs.RecPmtTrnRs.RecPmtTrnRsUn.RecPmtRs.FinalAmtExists)
												oldRecRs.RecPmtTrnRs.RecPmtTrnRsUn.RecPmtRs.PmtInfo.TrnAmt = oldRecRs.RecPmtTrnRs.RecPmtTrnRsUn.RecPmtRs.FinalAmt;
										}
										oldRecRs.RecPmtTrnRs.RecPmtTrnRsUn.RecPmtRs.PmtInfo.DtDue = sinfo.NextInstanceDate + "0000";
										oldRecRs.RecPmtTrnRs.RecPmtTrnRsUn.RecPmtRs.InitialAmtExists = false;
										audit.updateRecPmtRsV1(oldRecRs, dbh, DBConsts.WILLPROCESSON); // omer:
										com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.EnumAccountEnum accttype = MsgBuilder
												.getOFX200AcctEnum(pi.AcctDebitType);

										com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypePmtTrnRsV1 rs = buildPmtAddRsV1(
												pi, oldRecRs.RecPmtTrnRs.RecPmtTrnRsUn.RecPmtRs.PmtInfo, payee,
												recSrvrTID, accttype);
										FFSDebug.log(methodName, " ++--++ audit.savePmtRsV1,pi.Status : ", pi.Status,
												FFSConst.PRINT_DEV);

										audit.savePmtRsV1(rs, rpi.CustomerID, pi.Status, dbh);
									}
								}

							} else
								throw new FFSException("Not supported OFXversion!");
						} else if (sinfo != null) {
							// SchedeuleInfo not found means that it is closed
							if (res[0].startsWith("OFX151")) {
								com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeRecPmtTrnRsV1 oldRecRs = (com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeRecPmtTrnRsV1) broker
										.parseMsg(res[1], "RecPmtTrnRsV1", "OFX151");
								// mark skipped instances to cancelled...
								cancelSkippedInstances(dbh, recSrvrTID);
								RecPmtInstruction.updateStatus(dbh, recSrvrTID, DBConsts.POSTEDON);
								pmtStatus = DBConsts.POSTEDON;
								oldRecRs.RecPmtTrnRs.RecPmtTrnRsV1Un.RecPmtRs.RecurrInst.NInsts = 0;
								oldRecRs.RecPmtTrnRs.RecPmtTrnRsV1Un.RecPmtRs.PmtInfo.DtDue = sinfo.NextInstanceDate + "0000";
								audit.updateRecPmtRsV1(oldRecRs, dbh, DBConsts.POSTEDON);
							} else if (res[0].startsWith("OFX200")) {
								com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeRecPmtTrnRsV1 oldRecRs = (com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeRecPmtTrnRsV1) broker
										.parseMsg(res[1], "RecPmtTrnRsV1", "OFX200");
								// mark skipped instances to cancelled...
								cancelSkippedInstances(dbh, recSrvrTID);
								RecPmtInstruction.updateStatus(dbh, recSrvrTID, DBConsts.POSTEDON);
								pmtStatus = DBConsts.POSTEDON;
								oldRecRs.RecPmtTrnRs.RecPmtTrnRsUn.RecPmtRs.RecurrInst.NInsts = 0;
								oldRecRs.RecPmtTrnRs.RecPmtTrnRsUn.RecPmtRs.PmtInfo.DtDue = sinfo.NextInstanceDate + "0000";
								audit.updateRecPmtRsV1(oldRecRs, dbh, DBConsts.POSTEDON);
							}
							dbh.conn.commit();
							
						} else {
							// sinfo == null
							// For debug only
							FFSDebug.log("Inconsistent data found: " + "the ScheduleInfo object is null for "
									+ "recurring payment RecSrvrTID = " + recSrvrTID + "Please contact support.",
									FFSConst.PRINT_DEV);
						}

						// log into AuditLog
						PropertyConfig propertyConfig = (PropertyConfig) FFSRegistry.lookup(BPWResource.PROPERTYCONFIG);

						// FFSDebug.log(methodName, " ++--++ propertyConfig: "
						// + propertyConfig, FFSConst.PRINT_DEV);
						if (propertyConfig.LogLevel >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE) {
							// FFSDebug.log(methodName,
							// " ++--++ propertyConfig.LogLevel: "
							// + propertyConfig.LogLevel,
							// FFSConst.PRINT_DEV);
							Object[] dynamicContent = new Object[1];
							int tranType = AuditLogTranTypes.BPW_GENERIC_PMTTRN;
							if (evts._array[0].InstructionType.equals(DBConsts.CHECKFREE_PMTTRN)) {
								dynamicContent[0] = BPWLocaleUtil.getLocalizableMessage(
										AuditLogConsts.AUDITLOG_MSG_CHECKFREE, null,
										BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
								tranType = AuditLogTranTypes.BPW_CHECKFREE_PMTTRN;
							} else if (evts._array[0].InstructionType.equals(DBConsts.METAVANTE_PMTTRN)) {
								dynamicContent[0] = BPWLocaleUtil.getLocalizableMessage(
										AuditLogConsts.AUDITLOG_MSG_METAVANTE, null,
										BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
								tranType = AuditLogTranTypes.BPW_METAVANTE_PMTTRN;
							} else if (evts._array[0].InstructionType.equals(DBConsts.ON_US_PMTTRN)) {
								dynamicContent[0] = BPWLocaleUtil.getLocalizableMessage(
										AuditLogConsts.AUDITLOG_MSG_ONUS, null, BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
								tranType = AuditLogTranTypes.BPW_ONUS_PMTTRN;
							} else if (evts._array[0].InstructionType.equals(DBConsts.ORCC_PMTTRN)) {
								dynamicContent[0] = BPWLocaleUtil.getLocalizableMessage(
										AuditLogConsts.AUDITLOG_MSG_ORCC, null, BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
								tranType = AuditLogTranTypes.BPW_ORCC_PMTTRN;
							} else if (evts._array[0].InstructionType.equals(DBConsts.RPPS_PMTTRN)) {
								dynamicContent[0] = BPWLocaleUtil.getLocalizableMessage(
										AuditLogConsts.AUDITLOG_MSG_RPPS, null, BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
								tranType = AuditLogTranTypes.BPW_RPPS_PMTTRN;
							} else if (evts._array[0].InstructionType.equals(DBConsts.BANKSIM_PMTTRN)) {
								dynamicContent[0] = BPWLocaleUtil.getLocalizableMessage(
										AuditLogConsts.AUDITLOG_MSG_BANKSIM, null,
										BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
								tranType = AuditLogTranTypes.BPW_BANKSIM_PMTTRN;
							} else if (evts._array[0].InstructionType.equals(DBConsts.SAMPLE_PMTTRN)) {
								dynamicContent[0] = BPWLocaleUtil.getLocalizableMessage(
										AuditLogConsts.AUDITLOG_MSG_SAMPLE, null,
										BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
								tranType = AuditLogTranTypes.BPW_SAMPLE_PMTTRN;
							} else {
								dynamicContent[0] = evts._array[0].InstructionType;
							}
							// FFSDebug.log(methodName,
							// " ++--++ dynamicContent[0]: "
							// + dynamicContent[0],
							// FFSConst.PRINT_DEV);
							// FFSDebug.log(methodName, " ++--++ rpi: " + rpi,
							// FFSConst.PRINT_DEV);

							int bizID = Integer.parseInt(rpi.CustomerID);
							BigDecimal pmtAmount = BPWUtil.getBigDecimal(rpi.getAmt());
							String debitAcct = BPWUtil.getAccountIDWithAccountType(rpi.AcctDebitID, rpi.AcctDebitType);

							// FFSDebug.log(methodName, " ++--++ debitAcct: "
							// + debitAcct, FFSConst.PRINT_DEV);

							ILocalizable msg = BPWLocaleUtil.getLocalizableMessage(msgKey, dynamicContent,
									BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);

							// FFSDebug.log(methodName, " ++--++ msg: " + msg,
							// FFSConst.PRINT_DEV);

							TransAuditLog.logTransAuditLog(dbh.conn.getConnection(), rpi.submittedBy, null, null, msg,
									rpi.LogID, tranType, bizID, pmtAmount, rpi.CurDef, recSrvrTID, pmtStatus,
									rpi.PayAcct, null, debitAcct, rpi.getBankID(), 0);

							// FFSDebug.log(methodName, " ++--++ msg: " + msg,
							// ", pmtStatus: ", pmtStatus,
							// FFSConst.PRINT_DEV);

						}
						FFSDebug.log(methodName, " ++--++ Done processing ReSrvrTId: ", recSrvrTID, FFSConst.PRINT_DEV);

					} catch (Throwable t) {
						String err = " ++--++ FAILED TO PROCESS TRANSACTION #: " + i;
						FFSDebug.log(t, methodName + err + "\n" + FFSDebug.stackTrace(t), FFSConst.PRINT_DEV);
						PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
						throw new FFSException(t, err);
					}

				}

				FFSDebug.log(methodName, " ++--++ Done Batch Processing ", FFSConst.PRINT_DEV);

				// FIXME TODO remove
				// sleepForAWhile() ;

			} else if (eventSequence == 2) { // LAST sequence
				/*
				 * // FIXME TODO for testing remove me batchNum = 0; FFSDebug.log(methodName, "eventSeq : "+
				 * eventSequence, ", batchNum: " +batchNum, FFSConst.PRINT_DEV);
				 */
			}
		} catch (Throwable exc) {

			if (dbhDirty != null && dbhDirty.conn != null) {
				dbhDirty.conn.rollback();
				PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
			}

			String errDescrip = "*** RecPmtHandler.eventHandler failed:";
			FFSDebug.log(exc, errDescrip + exc, FFSConst.PRINT_ERR);
			throw new FFSException(exc, exc.toString());

		} finally {
			if (dbhDirty != null) {
				DBUtil.freeConnection(dbhDirty.conn);
				PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
			}
			dbhDirty = null;
		}

		FFSDebug.log("==== RecPmtHandler.eventHander: end", FFSConst.PRINT_DEV);
		PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
	}

	@SuppressWarnings("unchecked")
	private boolean cancelSkippedInstances(FFSConnectionHolder dbh, String recSrvrTID) {
		
		try {
			List<PmtInfo> PmtInfos = PmtInstruction.getPmtsByRecSrvrTIdAndStatus(dbh, recSrvrTID, "'" + DBConsts.SKIPPEDON + "'");
			for (PmtInfo pmtInfo : PmtInfos) {
				FFSDebug.log("Found " + pmtInfo.getSrvrTID() + " skipped payment", FFSConst.PRINT_DEV);
				//simply mark it as cancelled as already its schedule and limits would have been deleted.
				PmtInstruction.updateStatus(dbh, pmtInfo.getSrvrTID(), DBConsts.CANCELEDON);
			}
		} catch (BPWException e) {
			FFSDebug.log("++_++ RecPmtHandler.cancelSkippedInstances: Error in cancelling skipped records.",
					FFSConst.PRINT_ERR);
			return false;
		} catch (FFSException e1) {
			FFSDebug.log("++_++ RecPmtHandler.cancelSkippedInstances: Error in cancelling skipped records.",
					FFSConst.PRINT_ERR);
			return false;
		}
		return true;
	}

	// =====================================================================
	// resubmitEventHandler()
	// Description: This method is called by the Scheduling engine during
	// event resubmission. It will set the possibleDuplicate to true
	// before calling the ToBackend handler.
	// Arguments: none
	// Returns: none
	// Note:
	// =====================================================================
	public void resubmitEventHandler(int eventSequence, EventInfoArray evts, FFSConnectionHolder dbh) throws Exception {
		FFSDebug.log("=== RecPmtHandler.resubmitEventHandler: begin, eventSeq=" + eventSequence + ",length="
				+ evts._array.length + ",instructionType=" + evts._array[0].InstructionType);
		eventHandler(eventSequence, evts, dbh);
		FFSDebug.log("=== RecPmtHandler.resubmitEventHandler: end");
	}

	// =====================================================================
	// buildPmtAddRsV1()
	// Description: Build the response message for pmt Add request.
	// Arguments:
	// Returns:
	// Note:
	// =====================================================================
	private com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypePmtTrnRsV1 buildPmtAddRsV1(PmtInfo pmtinfo,
			com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypePmtInfoV1Aggregate pmtaggregate,
			PayeeInfo payeeinfo, String recsrvrTID,
			com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.EnumAccountEnum accttype) throws Exception {
		com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypePmtTrnRsV1Aggregate PmtTrnRs = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypePmtTrnRsV1Aggregate();
		PmtTrnRs.PmtTrnRsV1UnExists = true;
		PmtTrnRs.PmtTrnRsV1Un = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypePmtTrnRsV1Un();
		PmtTrnRs.PmtTrnRsV1Un.__memberName = OFXConsts.PMTRS;
		PmtTrnRs.PmtTrnRsV1Un.PmtRs = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypePmtRsV1Aggregate();
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.SrvrTID = pmtinfo.SrvrTID;
		String payeelistid = String.valueOf(pmtinfo.PayeeListID);
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.PayeeLstID = payeelistid;		
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.CurDef = MsgBuilder.getOFX151CurrencyEnum(pmtinfo.CurDef);
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.ExtdPayeeExists = false;
		PropertyConfig propertyConfig = (PropertyConfig) FFSRegistry.lookup(BPWResource.PROPERTYCONFIG);
		boolean useExtdPayeeID = propertyConfig.UseExtdPayeeID;
		if (!useExtdPayeeID || !payeeinfo.ExtdPayeeID.equals("0")) {
			PmtTrnRs.PmtTrnRsV1Un.PmtRs.ExtdPayeeExists = true;
			PmtTrnRs.PmtTrnRsV1Un.PmtRs.ExtdPayee = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeExtdPayeeV1Aggregate();
			PmtTrnRs.PmtTrnRsV1Un.PmtRs.ExtdPayee.ExtdPayeeV1CmExists = true;
			PmtTrnRs.PmtTrnRsV1Un.PmtRs.ExtdPayee.ExtdPayeeV1Cm = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeExtdPayeeV1Cm();
			PmtTrnRs.PmtTrnRsV1Un.PmtRs.ExtdPayee.ExtdPayeeV1Cm.PayeeID = (!useExtdPayeeID) ? payeeinfo.PayeeID
					: payeeinfo.ExtdPayeeID;
			PmtTrnRs.PmtTrnRsV1Un.PmtRs.ExtdPayee.ExtdPayeeV1Cm.Name = payeeinfo.PayeeName;
			if (payeeinfo.PayeeType == DBConsts.PERSONAL)
				PmtTrnRs.PmtTrnRsV1Un.PmtRs.ExtdPayee.ExtdPayeeV1Cm.IDScope = com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.EnumIDScopeEnum.USER;
			else
				PmtTrnRs.PmtTrnRsV1Un.PmtRs.ExtdPayee.ExtdPayeeV1Cm.IDScope = com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.EnumIDScopeEnum.GLOBAL;
		}
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.CheckNumExists = false;
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.RecSrvrTIDExists = true;
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.RecSrvrTID = recsrvrTID;
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.PmtInfo = pmtaggregate;
		// PmtTrnRs.PmtTrnRsV1Un.PmtRs.PmtInfo.TrnAmt = (float) pmtinfo.Amount;
		/*
		 * String fieldType = PmtTrnRs.PmtTrnRsV1Un.PmtRs.PmtInfo.getClass().getField ("TrnAmt").getType().getName(); if
		 * (fieldType.equalsIgnoreCase("float")) { PmtTrnRs.PmtTrnRsV1Un.PmtRs.PmtInfo.TrnAmt = (float) pmtinfo.Amount;
		 * } else if (fieldType.equalsIgnoreCase("String")) { PmtTrnRs.PmtTrnRsV1Un.PmtRs.PmtInfo.TrnAmt = "" +
		 * pmtinfo.Amount; } else if (fieldType.equalsIgnoreCase("double")) { PmtTrnRs.PmtTrnRsV1Un.PmtRs.PmtInfo.TrnAmt
		 * = pmtinfo.Amount; } else { throw new Exception("RecPmtHandler.buildPmtAddRsV1: unsupported field type " +
		 * fieldType + " for TrnAmt"); }
		 */
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.PmtPrcSts = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypePmtPrcStsAggregate();
		PmtTrnRs.TrnRsV1Cm = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeTrnRsV1Cm();
		PmtTrnRs.TrnRsV1Cm.Status = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeStatusV1Aggregate();
		String due = String.valueOf(pmtinfo.StartDate);
		int dueInt = Integer.parseInt(due.substring(0, 8));
		int smartPayDay = SmartCalendar.getPayday(pmtinfo.FIID, dueInt);
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.PmtInfo.DtDue = due.substring(0, 8);
		PmtTrnRs.TrnRsV1Cm.TrnUID = "0";
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.PmtPrcSts.DtPmtPrc = "" + smartPayDay + "000000";
		// PmtTrnRs.PmtTrnRsV1Un.PmtRs.PmtPrcSts.PmtPrcCode =
		// com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.EnumPmtProcessStatusEnum.WILLPROCESSON;
		if (pmtinfo.Status.equals(DBConsts.WILLPROCESSON)) {
			PmtTrnRs.PmtTrnRsV1Un.PmtRs.PmtPrcSts.PmtPrcCode = com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.EnumPmtProcessStatusEnum.WILLPROCESSON;
			PmtTrnRs.TrnRsV1Cm.Status.Code = MsgBuilder.CODE_Success;
		} else {
			PmtTrnRs.PmtTrnRsV1Un.PmtRs.PmtPrcSts.PmtPrcCode = com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.EnumPmtProcessStatusEnum.FAILEDON;
			PmtTrnRs.TrnRsV1Cm.Status.Code = MsgBuilder.CODE_GeneralError;

			// Add the failure message.
			PmtTrnRs.TrnRsV1Cm.Status.MessageExists = true;
			PmtTrnRs.TrnRsV1Cm.Status.Message = MsgBuilder.getCmStatusMessage(PmtTrnRs.TrnRsV1Cm.Status.Code);
		}
		PmtTrnRs.TrnRsV1Cm.Status.Severity = MsgBuilder.getOFX151CmStatusSeverity(PmtTrnRs.TrnRsV1Cm.Status.Code);

		return new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypePmtTrnRsV1(PmtTrnRs);
	}

	private com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypePmtTrnRsV1 buildPmtAddRsV1(PmtInfo pmtinfo,
			com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypePmtInfoAggregate pmtaggregate,
			PayeeInfo payeeinfo, String recsrvrTID,
			com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.EnumAccountEnum accttype) throws Exception {
		com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypePmtTrnRsV1Aggregate PmtTrnRs = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypePmtTrnRsV1Aggregate();
		PmtTrnRs.PmtTrnRsV1UnExists = true;
		PmtTrnRs.PmtTrnRsV1Un = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypePmtTrnRsV1Un();
		PmtTrnRs.PmtTrnRsV1Un.__memberName = OFXConsts.PMTRS;
		PmtTrnRs.PmtTrnRsV1Un.PmtRs = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypePmtRsAggregate();
		PmtTrnRs.TrnRsCm = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeTrnRsCm();
		PmtTrnRs.TrnRsCm.Status = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeStatusAggregate();
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.SrvrTID = pmtinfo.SrvrTID;
		String payeelistid = String.valueOf(pmtinfo.PayeeListID);
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.PayeeLstID = payeelistid;
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.CurDef = MsgBuilder.getOFX200CurrencyEnum(pmtinfo.CurDef);
		
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.ExtdPayeeExists = false;
		PropertyConfig propertyConfig = (PropertyConfig) FFSRegistry.lookup(BPWResource.PROPERTYCONFIG);
		boolean useExtdPayeeID = propertyConfig.UseExtdPayeeID;
		if (!useExtdPayeeID || !payeeinfo.ExtdPayeeID.equals("0")) {
			PmtTrnRs.PmtTrnRsV1Un.PmtRs.ExtdPayeeExists = true;
			PmtTrnRs.PmtTrnRsV1Un.PmtRs.ExtdPayee = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeExtdPayeeAggregate();
			PmtTrnRs.PmtTrnRsV1Un.PmtRs.ExtdPayee.ExtdPayeeCmExists = true;
			PmtTrnRs.PmtTrnRsV1Un.PmtRs.ExtdPayee.ExtdPayeeCm = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeExtdPayeeCm();
			PmtTrnRs.PmtTrnRsV1Un.PmtRs.ExtdPayee.ExtdPayeeCm.PayeeID = (!useExtdPayeeID) ? payeeinfo.PayeeID
					: payeeinfo.ExtdPayeeID;
			PmtTrnRs.PmtTrnRsV1Un.PmtRs.ExtdPayee.ExtdPayeeCm.Name = payeeinfo.PayeeName;
			if (payeeinfo.PayeeType == DBConsts.PERSONAL)
				PmtTrnRs.PmtTrnRsV1Un.PmtRs.ExtdPayee.ExtdPayeeCm.IDScope = com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.EnumIDScopeEnum.USER;
			else
				PmtTrnRs.PmtTrnRsV1Un.PmtRs.ExtdPayee.ExtdPayeeCm.IDScope = com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.EnumIDScopeEnum.GLOBAL;
		}
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.CheckNumExists = false;
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.RecSrvrTIDExists = true;
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.RecSrvrTID = recsrvrTID;
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.PmtInfo = pmtaggregate;
		// PmtTrnRs.PmtTrnRsV1Un.PmtRs.PmtInfo.TrnAmt = (float) pmtinfo.Amount;
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.PmtPrcSts = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypePmtPrcStsAggregate();
		String due = String.valueOf(pmtinfo.StartDate);
		int dueInt = Integer.parseInt(due.substring(0, 8));
		int smartPayDay = SmartCalendar.getPayday(pmtinfo.FIID, dueInt);
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.PmtPrcSts.DtPmtPrc = "" + smartPayDay + "000000";
		PmtTrnRs.PmtTrnRsV1Un.PmtRs.PmtInfo.DtDue = due.substring(0, 8);
		PmtTrnRs.TrnRsCm.TrnUID = "0";
		if (pmtinfo.Status.equals(DBConsts.WILLPROCESSON)) {
			PmtTrnRs.PmtTrnRsV1Un.PmtRs.PmtPrcSts.PmtPrcCode = com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.EnumPmtProcessStatusEnum.WILLPROCESSON;
			PmtTrnRs.TrnRsCm.Status.Code = MsgBuilder.CODE_Success;
		} else {
			PmtTrnRs.PmtTrnRsV1Un.PmtRs.PmtPrcSts.PmtPrcCode = com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.EnumPmtProcessStatusEnum.FAILEDON;
			PmtTrnRs.TrnRsCm.Status.Code = MsgBuilder.CODE_GeneralError;

			// Add the failure message.
			PmtTrnRs.TrnRsCm.Status.MessageExists = true;
			PmtTrnRs.TrnRsCm.Status.Message = MsgBuilder.getCmStatusMessage(PmtTrnRs.TrnRsCm.Status.Code);
		}
		PmtTrnRs.TrnRsCm.Status.Severity = MsgBuilder.getOFX200CmStatusSeverity(PmtTrnRs.TrnRsCm.Status.Code);

		return new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypePmtTrnRsV1(PmtTrnRs);
	}

	/**
	 * Does Limit checking for single transaction created from the reurring model. update PmtInstruction with the status
	 * we get from the limit checking. if the status we get from the limit checking is "WILLPROCESSON" create two
	 * schedules for this transaction, FUNDTRN and RECFUNDTRN.
	 * 
	 * @param dbh
	 * @param pmtInfo
	 * @param extraInfo
	 * @param sinfo
	 * @param fundsAllocRetries
	 * @return
	 * @throws Exception
	 */
	private String doLimitChecking(final FFSConnectionHolder dbh, final PmtInfo pmtInfo, final HashMap extraInfo,
			final ScheduleInfo sinfo, final int fundsAllocRetries) throws Exception {

		final String methodName = "RecPmtHandler.doLimitChecking";

		FFSDebug.log(methodName, ": starts....", FFSConst.PRINT_DEV);
		String status = null;
		
		// Before limit checks, we need to set channel
		// from recurring instruction.
		EntChannelOps channelOps = getChannelId(pmtInfo.getRecSrvrTID(), dbh);
		pmtInfo.setChannelId(channelOps.getChannelIDMod());
		pmtInfo.setChannelGroupId(channelOps.getChannelGroupIdMod());
		FFSDebug.log("++_++ RecPmtHandler.doLimitChecking: RecSrvrTID = " + pmtInfo.getRecSrvrTID() + ", channelId = " +
				pmtInfo.getChannelId());
		// Send this batch for limitchecking
		int result = LimitCheckApprovalProcessor.processPmtAdd(dbh, pmtInfo, extraInfo);// extra info
		// map to BPW status
		status = LimitCheckApprovalProcessor.mapToStatus(result);

		if (DBConsts.APPROVAL_PENDING.equalsIgnoreCase(status)) {
			pmtInfo.Status = DBConsts.APPROVAL_PENDING;
			PmtInstruction.updateStatus(dbh, pmtInfo.SrvrTID, status);
			pmtInfo.Status = DBConsts.WILLPROCESSON; // to be used by MB
		} else if (DBConsts.WILLPROCESSON.equalsIgnoreCase(status)) {
			// create a record in ScheduleInfo table
			// create FUNDTRN schedule
			pmtInfo.Status = DBConsts.WILLPROCESSON;
			ScheduleInfo info = new ScheduleInfo();
			info.FIId = pmtInfo.FIID;
			info.Status = ScheduleConstants.SCH_STATUS_ACTIVE;
			info.Frequency = ScheduleConstants.SCH_FREQ_ONCE;
			info.StartDate = sinfo.NextInstanceDate;
			info.InstanceCount = 1;
			info.LogID = pmtInfo.LogID;
			
	        // This is 2nd or more recurring instance so it should be schedule state. So only need to check support fund allocations.
			if(_supportFundAllocation) {
				FFSDebug.log(methodName, ": creating FUNDTRN schedule. srvrTID: ", pmtInfo.SrvrTID, FFSConst.PRINT_DEV);
				ScheduleInfo.createSchedule(dbh, DBConsts.FUNDTRN, pmtInfo.SrvrTID, info);
				// create RECFUNDTRN schedule
				info.Frequency = ScheduleConstants.SCH_FREQ_DAILY;
				info.InstanceCount = fundsAllocRetries;
				info.NextInstanceDate = info.StartDate;
				ScheduleInfo.moveNextInstanceDate(info);
				// the StartDate should be one day ahead and is the same as
				// NextInstanceDate
				info.StartDate = info.NextInstanceDate;
				FFSDebug.log(methodName, ": creating RECFUNDTRN schedule. srvrTID: ", pmtInfo.SrvrTID, FFSConst.PRINT_DEV);
				ScheduleInfo.createSchedule(dbh, DBConsts.RECFUNDTRN, pmtInfo.SrvrTID, info);
			} else {
				// Create schedule PMTTRN batch
				FulfillAgent fagent = (FulfillAgent)FFSRegistry.lookup(BPWResource.FULFILLAGENT);
		    	if ( fagent == null) {
		            throw new BPWException("FulfillAgent is null!!");
		        }
		    	String pmttype = fagent.findPaymentInstructionType( pmtInfo.FIID, pmtInfo.getRouteId() );
		    	
		    	// for event-resubmit, there may already have a row of pmt ScheduleInfo
	            // only generate a pmt scheduleInfo if no scheduleInfo, eventInfo or
	            // eventInfoLog is found for the pmt with the srvrTID
	            boolean sinfoExist = ScheduleInfo.checkExist(pmtInfo.FIID, pmttype, pmtInfo.getSrvrTID(), dbh );
	            boolean eveExist = Event.checkExist(dbh, pmttype, pmtInfo.getSrvrTID(),
	                                                    ScheduleConstants.SCH_FLAG_NORMAL);
	            boolean evtLogExist = EventInfoLog.checkExist(dbh, pmttype, pmtInfo.getSrvrTID(),
	                                                          ScheduleConstants.SCH_FLAG_NORMAL);
	            if (!sinfoExist && !eveExist && !evtLogExist) {
	            	ScheduleInfo.createSchedule( dbh, pmttype, pmtInfo.getSrvrTID(), info);
	            } else {
	            	FFSDebug.log("*** RecPmtHandler.doLimitChecking:" +
                            " duplicate results of pmt scheduleInfo" +
                            " SrvrTID=" + pmtInfo.getSrvrTID() + ", no new pmt" +
                            " schedule is created.");
	            }
			}
		} else {
			// the limit check failed - manually rollback the limit since the
			// running total will be updated as part of the limit
			// check and this is part of a larger transaction that can't be
			// rolled back.
			// save/restore the status code and the message, since they will be
			// modified by the call to processPmtDelete.
			int pmtStatusCode = pmtInfo.getStatusCode();
			String pmtStatusMsg = pmtInfo.getStatusMsg();
			LimitCheckApprovalProcessor.processPmtDelete(dbh, pmtInfo, extraInfo);
			pmtInfo.setStatusCode(pmtStatusCode);
			pmtInfo.setStatusMsg(pmtStatusMsg);

			// update the status in PmtInstruction
			PmtInstruction.updateStatus(dbh, pmtInfo.SrvrTID, status);
			pmtInfo.Status = DBConsts.FAILEDON; // OFX does not support refined
			// status
			// do not create schedule
			FFSDebug.log(methodName, ": srvrTid: " + pmtInfo.SrvrTID + ", status: " + status, FFSConst.PRINT_DEV);
		}
		FFSDebug.log(methodName, ": ends....", FFSConst.PRINT_DEV);
		return status;
	}

	/**
	 * Does entitlement checking before creating single transaction created from the reurring model.
	 * 
	 * @param pmtInfo
	 * @return
	 * @exception Exception
	 */
	private boolean doEntitlementCheck(PmtInfo pmtInfo) throws Exception {

		final String methodName = "RecPmtHandler.doEntitlementCheck";

		FFSDebug.log(methodName, ": starts....", FFSConst.PRINT_DEV);

		// Send this batch for limitchecking

		return LimitCheckApprovalProcessor.checkEntitlementPmt(pmtInfo, null);// extra
		// info
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
	
	private EntChannelOps getChannelId(String recSrvrTid, FFSConnectionHolder dbh) throws FFSException {
		
		return ChannelStore.getChannelIdForTrn(null, recSrvrTid, DBConsts.BPW_RecPmtInstruction, dbh);
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

	/*
	 * //FIXME TODO Omarabi remove for test only private void sleepForAWhile() { final String methodName =
	 * "RecPmtHandler.sleepForAWhile: "; FFSDebug.log(methodName, ": starts.... batchNum: " + batchNum,
	 * FFSConst.PRINT_DEV);
	 * 
	 * 
	 * 
	 * PropertyConfig propertyConfig = (PropertyConfig) FFSRegistry .lookup(BPWResource.PROPERTYCONFIG);
	 * 
	 * String sleepBatch = propertyConfig.otherProperties.getProperty("sleepBatch", "3"); FFSDebug.log(methodName,
	 * ": sleepBatch: ", sleepBatch, FFSConst.PRINT_DEV); int sleepBatchInt = Integer.parseInt(sleepBatch);
	 * 
	 * if (batchNum != sleepBatchInt) { FFSDebug.log(methodName, " NO NEED TO SLEEP SINCE THIS IS BATCH #: " + batchNum,
	 * " WHILE WE SHOULD SLEEP AT BATCH #: ", sleepBatch, FFSConst.PRINT_DEV); return; } FFSDebug.log(methodName,
	 * " PREPARING TO SLEEP IN BATCH #: " + batchNum, FFSConst.PRINT_DEV);
	 * 
	 * String sleepTime = propertyConfig.otherProperties.getProperty("sleepTime", "120"); FFSDebug.log(methodName,
	 * ": sleepTime: ", sleepTime, FFSConst.PRINT_DEV); int sleepFor = Integer.parseInt(sleepTime);
	 * 
	 * 
	 * 
	 * try { FFSDebug.log(methodName, ": Sleeping for: " + sleepFor, ", Time: " + new java.util.Date(),
	 * FFSConst.PRINT_DEV); Thread.sleep(sleepFor*1000); FFSDebug.log(methodName, ": Wake up from Sleeping for: " +
	 * sleepFor, ", Time: " + new java.util.Date(), FFSConst.PRINT_DEV);
	 * 
	 * } catch (InterruptedException e) { FFSDebug.log(e, methodName + ": Failed to sleep for for: " + sleepTime +
	 * ", Time: " + new java.util.Date(), FFSConst.PRINT_DEV); e.printStackTrace(); } FFSDebug.log(methodName,
	 * ": Done....", FFSConst.PRINT_DEV); }
	 */

}
