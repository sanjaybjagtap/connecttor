//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

import com.ffusion.beans.banking.TransferDefines;
import com.ffusion.csil.beans.entitlements.EntChannelOps;
import com.ffusion.ffs.bpw.audit.AuditAgent;
import com.ffusion.ffs.bpw.audit.TransAuditLog;
import com.ffusion.ffs.bpw.db.AdjustedInstruction;
import com.ffusion.ffs.bpw.db.BPWXferExtraInfo;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.db.RecSrvrTIDToSrvrTID;
import com.ffusion.ffs.bpw.db.RecSrvrTrans;
import com.ffusion.ffs.bpw.db.RecXferInstruction;
import com.ffusion.ffs.bpw.db.SmartCalendar;
import com.ffusion.ffs.bpw.db.XferInstruction;
import com.ffusion.ffs.bpw.interfaces.AuditLogConsts;
import com.ffusion.ffs.bpw.interfaces.BPWException;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.IntraTrnInfo;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.TransferCalloutHandler;
import com.ffusion.ffs.bpw.interfaces.TransferInfo;
import com.ffusion.ffs.bpw.interfaces.ifx.TransferIntraMap;
import com.ffusion.ffs.bpw.master.LimitCheckApprovalProcessor;
import com.ffusion.ffs.bpw.master.RecXferProcessor2;
import com.ffusion.ffs.bpw.master.channels.ChannelStore;
import com.ffusion.ffs.bpw.serviceMsg.BPWMsgBroker;
import com.ffusion.ffs.bpw.serviceMsg.MsgBuilder;
import com.ffusion.ffs.bpw.serviceMsg.RsCmBuilder;
import com.ffusion.ffs.bpw.util.BPWTrackingIDGenerator;
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
import com.ffusion.util.enums.UserAssignedAmount;
import com.ffusion.util.logging.AuditLogTranTypes;
import com.sap.banking.common.interceptors.PerfLoggerUtil;
//Begin Modify TCG for I18N
//End Modify TCG for I18N

//import com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.*;

//=====================================================================
//
// This class contains a callback handler that is registered in the
// IntructionType table.  The registered callback handler will be called
// by the Scheduling engine for the Recurring Intra-bank Fund Transfer
// Processing.
//
//=====================================================================

public class RecXferHandler
implements com.ffusion.ffs.bpw.interfaces.DBConsts, FFSConst, com.ffusion.ffs.bpw.interfaces.BPWResource, BPWScheduleHandler {
    private class XferInfoDef {
        public int StartDate;   // Date Due in Scheduler format
        public String fiId;
        public String curDef; // transfer currency
        // info to be returned to the response structure
        public String srvrTid;
        public String recSrvrTid;
        public String prcDate;     // process date to be in the response
        public String prcSts;      // process status to be in the response
        public int prcCode;     // process code to be in the response
        public int code;        // code returned
    }

    private final PropertyConfig _propertyConfig;
    private int _logLevel = 1;

    public RecXferHandler() {
        _propertyConfig = (PropertyConfig) FFSRegistry.lookup(PROPERTYCONFIG);
        _logLevel = _propertyConfig.LogLevel;
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
                            FFSConnectionHolder dbh)
    throws FFSException {
    	
    	String curMethodName = "RecXferHandler.eventHandler: ";
    	long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(curMethodName,start);
        FFSDebug.log(curMethodName, "Start", PRINT_DEV);
        FFSDebug.log("=== RecXferHandler.eventHander: begin, eventSeq=" + eventSequence
                     + ",length=" + evts._array.length, FFSConst.PRINT_DEV);
        try {
            // =================================
            // Do event sequence
            // =================================
            if (eventSequence == 0) {         // FIRST sequence
            } else if (eventSequence == 1) {    // NORMAL sequence
                AuditAgent audit = (AuditAgent) FFSRegistry.lookup(BPWResource.AUDITAGENT);
                if (audit == null)
                    throw new BPWException("RecXferHandler.eventHandler:AuditAgent is null!!");
                //
                // 1. First check to see if this is the last instance of RecXfer
                // 2. If this is the last RecXfer, update the status of old response to POSTEDON in Audit Agent
                // 3. If this is not the last RecXfer
                //    3.1  Update the <DtXferPrj> field with "NextInstanceDate" in the transaction history of RecXferSync
                //    3.2  Create a ScheduleInfo with type DBConsts.INTRATRN with next instance date
                //    3.3  Create a XferInstruction
                //    3.4  Create a RecSrvrTIDToSrvrTID
                //    3.5  Save new transaction history of XferSync in Audit Agent
                //

                // =================================
                // Find the old response from recSrvrTID
                // =================================
                BPWMsgBroker broker = (BPWMsgBroker) FFSRegistry.lookup(BPWResource.BPWMSGBROKER);
                if (broker == null)
                    throw new FFSException("RecXferHandler.eventHandler:BPWMsgBroker is null!!");

                TransferCalloutHandler calloutHandler = (TransferCalloutHandler)OSGIUtil.getBean(TransferCalloutHandler.class);

                for (int i = 0; i < evts._array.length; i++) {
                    String recSrvrTID = evts._array[i].InstructionID;
                    FFSDebug.log("=== RecXferHandler.eventHander: eventSeq=" + eventSequence
                                 + ",RecSrvrTID=" + recSrvrTID, FFSConst.PRINT_DEV);
                    String[] res = RecSrvrTrans.findResponseByRecSrvrTID(recSrvrTID, dbh);
                    if (res[0] == null) {
                        String msg = "*** RecXferHandler.eventHandler failed: could not find the RecSrvrTID=" + recSrvrTID + " in BPW_RecSrvrTrans";
                        FFSDebug.console(msg);
                        FFSDebug.log(msg);
                        ScheduleInfo.cancelSchedule(dbh, DBConsts.RECINTRATRN, recSrvrTID); // close the scheduleInfo
                        continue;
                    }

                    ScheduleInfo sinfo = ScheduleInfo.getScheduleInfo(evts._array[i].FIId,
                                                                      DBConsts.RECINTRATRN, recSrvrTID, dbh);
                    if ((sinfo != null) &&
                        (sinfo.StatusOption != ScheduleConstants.SCH_STATUSOPTION_CLOSE_SCHEDULED)) {
                        if (res[0].startsWith("OFX151")) {
                            com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeRecIntraTrnRsV1 oldRecRs
                            = (com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeRecIntraTrnRsV1) broker.parseMsg(res[1], "RecIntraTrnRsV1", "OFX151");
                            if (sinfo.Status.compareTo(ScheduleConstants.SCH_STATUS_CLOSED) == 0) {
                            	// mark skipped instances to cancelled...
								cancelSkippedInstances(dbh, recSrvrTID);
                                // Update the transaction history for closed RecXferInstruction
                                RecXferInstruction.updateStatus(dbh, recSrvrTID, DBConsts.POSTEDON);

                                oldRecRs.RecIntraTrnRs.RecIntraRsV1Un.RecIntraRs.RecurrInst.NInsts = 0;
                                com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeRecIntraTrnRsV1 recRs
                                = buildRecXferRs(oldRecRs);
                                audit.updateRecXferRsV1(recRs, dbh, DBConsts.POSTEDON);
                            } else {
                                //
                                // Prepare ScheduleInfo structure
                                //
                                RecXferInstruction rti = getRecXferInstruction(dbh, recSrvrTID);
                                if (rti == null || (rti != null && DBConsts.CANCELEDON.equals(rti.getStatus()))) {
                                    String msg = "*** RecXferHandler.eventHandler failed: could not find the RecSrvrTID=" + recSrvrTID + " in BPW_RecXferInstruction";
                                    FFSDebug.console(msg);
                                    FFSDebug.log(msg);
                                    ScheduleInfo.cancelSchedule(dbh, DBConsts.RECINTRATRN, recSrvrTID); // close the scheduleInfo
                                    continue;
                                }
                                
                                // Check if instance about to be created has been adjusted.
								if (adjustedCurrentInstance(recSrvrTID, sinfo.CurInstanceNum, dbh)) {
									FFSDebug.log(curMethodName +
											": Current instance has been adjusted. Skipping creation of Instruction.",
											FFSConst.PRINT_DEV);
									continue;
								} else {
									FFSDebug.log(curMethodName +
											": Current instance has not adjusted. Proceed to create Instruction.",
											FFSConst.PRINT_DEV);
								}
								
                                // Generate a new tracking Id for each instance
                                // generated from the recurring model
                                String logId = BPWTrackingIDGenerator.getNextId();
                                ScheduleInfo info = new ScheduleInfo();
                                info.FIId = rti.FIID;
                                info.Status = ScheduleConstants.SCH_STATUS_ACTIVE;
                                info.Frequency = ScheduleConstants.SCH_FREQ_ONCE;
                                info.InstanceCount = 1;
                                info.LogID = logId;
                                
                                /* If the nextInstanceDate is current date, then compute future date.
                                 * BCP Internal Incident: 1780047375
                                */
                                computeNextInstanceDate(dbh, sinfo);
                                
                                info.NextInstanceDate = sinfo.NextInstanceDate;
                                info.StartDate = info.NextInstanceDate;
                                //
                                // Update the <DtXferPrj> field with "NextInstanceDate" in the transaction history of RecXferSync
                                //
                                RsCmBuilder.updateXferRsDateXferPrj(info.NextInstanceDate + "0000",
                                                                    oldRecRs.RecIntraTrnRs.RecIntraRsV1Un.RecIntraRs.IntraRs);
                                //
                                // Update the <NInsts> and <DtDue>
                                //
                                oldRecRs.RecIntraTrnRs.RecIntraRsV1Un.RecIntraRs.RecurrInst.NInsts = sinfo.InstanceCount - sinfo.CurInstanceNum + 1;

                                // QTS 644117: don't allow weekend or holiday future dated recurring instances
                                int smartPayDay = DBUtil.getPayday(info.FIId,
                                info.NextInstanceDate,
                                DBConsts.SCH_CATEGORY_BOOKTRANSFER	) / 100;
                                
                                oldRecRs.RecIntraTrnRs.RecIntraRsV1Un.RecIntraRs.IntraRs.XferInfo.DtDue = smartPayDay + "000000";
                                audit.updateRecXferRsV1(oldRecRs, dbh, DBConsts.WILLPROCESSON);
                                //
                                // create a record in XferInstruction table
                                //
                                //String dtToPost = String.valueOf(DBUtil.getCurrentStartDate());
                                String dtToPost = String.valueOf(smartPayDay);
                                XferInstruction ti = new XferInstruction("", rti.CustomerID, rti.FIID, rti.Amount,
                                                                         rti.CurDef, rti.ToAmount, rti.ToAmtCurrency,
                                                                         rti.userAssignedAmount, rti.BankID, rti.BranchID,
                                                                         rti.AcctDebitID, rti.AcctDebitType,
                                                                         rti.AcctCreditID, rti.AcctCreditType,
                                                                         "", dtToPost, DBConsts.WILLPROCESSON,
                                                                         logId, recSrvrTID, rti.SubmittedBy, rti.SubmittedBy,
                                                                         rti.fromBankID, rti.fromBranchID, rti.getChannelId(), rti.getChannelGroupId());

                                ti.setPaymentSubType(rti.paymentSubType);
                                
                                // do entitlement check first,
                                // if not entitled, do not create any thing

                                if ( doEntitlementCheck( ti ) == false ) {
                                    String msg = "RecXferHandler.eventHandler failed to process: " +
                                                 "Entitlement check failed. Customer Id= " + ti.CustomerID;
                                    FFSDebug.log(msg, FFSConst.PRINT_DEV);
                                    
                                    // mark skipped instances to cancelled...
    								cancelSkippedInstances(dbh, recSrvrTID);
                                    // Cancel this recurring model
                                    // Update BPW_RecXferInstruction
                                    RecXferInstruction.updateStatus(dbh, recSrvrTID, DBConsts.FAILEDON);
                                    // Close the RECINTRATRN
                                    ScheduleInfo.delete(dbh, recSrvrTID, DBConsts.RECINTRATRN);
                                    // Update BPW_RecSrvrTrans
                                    SimpleDateFormat formatter = new SimpleDateFormat( "yyyyMMddHHmmss" );
                                    String prcDate = formatter.format(new Date());
                                    RsCmBuilder.updateRsXferPrcSts( DBConsts.FAILEDON, prcDate,
                                                                    oldRecRs.RecIntraTrnRs.RecIntraRsV1Un.RecIntraRs.IntraRs.XferPrcSts );
                                    oldRecRs.RecIntraTrnRs.RecIntraRsV1Un.RecIntraRs.RecurrInst.NInsts = -1;
                                    audit.updateRecXferRsV1(oldRecRs, dbh, DBConsts.FAILEDON);
                                    // Log in Audit Log
                                    if (_logLevel >= AUDITLOGLEVEL_STATUSUPDATE) {
                                        String debitAcct = BPWUtil.getAccountIDWithAccountType(ti.AcctDebitID, ti.AcctDebitType);
                                        String creditAcct = BPWUtil.getAccountIDWithAccountType(ti.AcctCreditID, ti.AcctCreditType);
                                        BigDecimal xferAmount = new BigDecimal(ti.Amount);
                                        String toAmount = ti.ToAmount;
                                        if (toAmount == null) toAmount = "0.00";
                                        BigDecimal xferToAmount = new BigDecimal(toAmount);
                                        ILocalizable localizedMsg = BPWLocaleUtil.getLocalizedMessage(
                                                            AuditLogConsts.AUDITLOG_MSG_ENTITLEMENT_BOOKTRANSFER_FAILED,
                                                            null,
                                                            BPWLocaleUtil.BOOKTRANSFER_AUDITLOG_MESSAGE);

                                        this.logTransAuditLog(dbh,
                                                              ti.SubmittedBy,
                                                              Integer.parseInt(ti.CustomerID),
                                                              ti.LogID,
                                                              AuditLogTranTypes.BPW_INTRAXFER_ADD,
                                                              xferAmount,
                                                              ti.CurDef,
                                                              xferToAmount,
                                                              ti.ToAmtCurrency,
                                                              ti.userAssignedAmount,
                                                              recSrvrTID,
                                                              debitAcct,
                                                              creditAcct,
                                                              ti.BankID,
                                                              ti.fromBankID,
                                                              DBConsts.FAILEDON,
                                                              localizedMsg);
                                    }

                                    continue;
                                }

                                String srvrTid = createXferInstruction(dbh, ti);
                                ti.SrvrTID = srvrTid;

                                // Push Extra Info from RecXfer to Xfer
                                if ((rti.extraFields != null)  && (!((HashMap)rti.extraFields).isEmpty())) {
                                    BPWXferExtraInfo.insertHashtable(srvrTid, new Hashtable((HashMap)rti.extraFields), dbh);
                                }


                                // Log in Audit Log
                                if (_logLevel >= AUDITLOGLEVEL_STATUSUPDATE) {
                                    String debitAcct = BPWUtil.getAccountIDWithAccountType(ti.AcctDebitID, ti.AcctDebitType);
                                    String creditAcct = BPWUtil.getAccountIDWithAccountType(ti.AcctCreditID, ti.AcctCreditType);
                                    BigDecimal xferAmount = new BigDecimal(ti.Amount);
                                    String toAmount = ti.ToAmount;
                                    if (toAmount == null) toAmount = "0.00";
                                    BigDecimal xferToAmount = new BigDecimal(toAmount);
                                    ILocalizable msg = BPWLocaleUtil.getLocalizedMessage(
                                                        AuditLogConsts.AUDITLOG_MSG_BOOKTRANSFER_USER_ADD,
                                                        null,
                                                        BPWLocaleUtil.BOOKTRANSFER_AUDITLOG_MESSAGE);
                                    this.logTransAuditLog(dbh,
                                                          ti.SubmittedBy,
                                                          Integer.parseInt(ti.CustomerID),
                                                          ti.LogID,
                                                          AuditLogTranTypes.BPW_INTRAXFER_ADD,
                                                          xferAmount,
                                                          ti.CurDef,
                                                          xferToAmount,
                                                          ti.ToAmtCurrency,
                                                          ti.userAssignedAmount,
                                                          srvrTid,
                                                          debitAcct,
                                                          creditAcct,
                                                          ti.BankID,
                                                          ti.fromBankID,
                                                          DBConsts.WILLPROCESSON,
                                                          msg);
                                }


                                // create a record in RecSrvrTIDToSrvrTID table
                                RecSrvrTIDToSrvrTID.create(dbh, recSrvrTID, srvrTid);
                                // create a record in BPW_ADJ_INSTR_INFO
                                if (!createAdjustedInstrInfo(recSrvrTID, srvrTid, sinfo.CurInstanceNum, dbh)) {
                                	FFSDebug.log(curMethodName +
											": Current instance has been adjusted. Skipping creation of Instruction.",
											FFSConst.PRINT_DEV);
									continue;
                                }
                                // Do limit checking
                                // add all information that does not fit
                                // IntraTrnInfo into this hashmap
                                HashMap extraInfo = new HashMap();
                                final XferInfoDef xinfo = doLimitChecking(dbh, ti, extraInfo, info);

                                // save transaction history of XferSync
                                //CurDef argument added for I18N
                                com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeIntraTrnRsV1 rs
                                = buildXferAddRs(oldRecRs.RecIntraTrnRs.RecIntraRsV1Un.RecIntraRs.IntraRs.XferInfo, xinfo,
                                                 oldRecRs.RecIntraTrnRs.RecIntraRsV1Un.RecIntraRs.IntraRs.CurDef);
                                audit.saveXferRsV1(rs, rti.CustomerID, dbh, xinfo.prcSts);

                                // Log in Audit Log if limit check failed
                                if (xinfo.prcSts.equals(DBConsts.FAILEDON)) {
                                    if (_logLevel >= AUDITLOGLEVEL_STATUSUPDATE) {
                                        String debitAcct = BPWUtil.getAccountIDWithAccountType(ti.AcctDebitID, ti.AcctDebitType);
                                        String creditAcct = BPWUtil.getAccountIDWithAccountType(ti.AcctCreditID, ti.AcctCreditType);
                                        BigDecimal xferAmount = new BigDecimal(ti.Amount);
                                        String toAmount = ti.ToAmount;
                                        if (toAmount == null) toAmount = "0.00";
                                        BigDecimal xferToAmount = new BigDecimal(toAmount);
                                        ILocalizable localizedMsg = BPWLocaleUtil.getLocalizedMessage(
                                                            AuditLogConsts.AUDITLOG_MSG_LIMIT_CHECK_FAILED,
                                                            null,
                                                            BPWLocaleUtil.BOOKTRANSFER_AUDITLOG_MESSAGE);

                                        this.logTransAuditLog(dbh,
                                                              ti.SubmittedBy,
                                                              Integer.parseInt(ti.CustomerID),
                                                              ti.LogID,
                                                              AuditLogTranTypes.BPW_INTRAXFER_ADD,
                                                              xferAmount,
                                                              ti.CurDef,
                                                              xferToAmount,
                                                              ti.ToAmtCurrency,
                                                              ti.userAssignedAmount,
                                                              recSrvrTID,
                                                              debitAcct,
                                                              creditAcct,
                                                              ti.BankID,
                                                              ti.fromBankID,
                                                              DBConsts.FAILEDON,
                                                              localizedMsg);
                                    }
                                }
                            }

                        } else if (res[0].startsWith("OFX200")) {
                            com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeRecIntraTrnRsV1 oldRecRs
                            = (com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeRecIntraTrnRsV1) broker.parseMsg(res[1], "RecIntraTrnRsV1", "OFX200");
                            if (sinfo.Status.compareTo(ScheduleConstants.SCH_STATUS_CLOSED) == 0) {
                            	// mark skipped instances to cancelled...
								cancelSkippedInstances(dbh, recSrvrTID);
                                // Update the transaction history for closed RecXferInstruction
                                RecXferInstruction.updateStatus(dbh, recSrvrTID, DBConsts.POSTEDON);

                                oldRecRs.RecIntraTrnRs.RecIntraRsUn.RecIntraRs.RecurrInst.NInsts = 0;
                                com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeRecIntraTrnRsV1 recRs
                                = buildRecXferRs(oldRecRs);
                                audit.updateRecXferRsV1(recRs, dbh, DBConsts.POSTEDON);
                            } else {
                                //
                                // Prepare ScheduleInfo structure
                                //
                                RecXferInstruction rti = getRecXferInstruction(dbh, recSrvrTID);
                                if (rti == null || (rti != null && DBConsts.CANCELEDON.equals(rti.getStatus()))) {
                                    String msg = "*** RecXferHandler.eventHandler failed: could not find the RecSrvrTID=" + recSrvrTID + " in BPW_RecXferInstruction";
                                    FFSDebug.console(msg);
                                    FFSDebug.log(msg);
                                    ScheduleInfo.cancelSchedule(dbh, DBConsts.RECINTRATRN, recSrvrTID); // close the scheduleInfo
                                    continue;
                                }
                                // Check if instance about to be created has been adjusted.
								if (adjustedCurrentInstance(recSrvrTID, sinfo.CurInstanceNum, dbh)) {
									FFSDebug.log(curMethodName +
											": Current instance has been adjusted. Skipping creation of Instruction.",
											FFSConst.PRINT_DEV);
									continue;
								} else {
									FFSDebug.log(curMethodName +
											": Current instance has not adjusted. Proceed to create Instruction.",
											FFSConst.PRINT_DEV);
								}
                                // we need to generate a new tracking Id for each instance
                                // generated from the recurring model
                                String logId = BPWTrackingIDGenerator.getNextId();

                                ScheduleInfo info = new ScheduleInfo();
                                info.FIId = rti.FIID;
                                info.Status = ScheduleConstants.SCH_STATUS_ACTIVE;
                                info.Frequency = ScheduleConstants.SCH_FREQ_ONCE;
                                info.InstanceCount = 1;
                                info.LogID = logId;
                                
                                /* If the nextInstanceDate is current date, then compute future date.
                                 * BCP Internal Incident: 1780047375
                                */
                                computeNextInstanceDate(dbh, sinfo);
                                
                                info.NextInstanceDate = sinfo.NextInstanceDate;
                                info.StartDate = info.NextInstanceDate;
                                //
                                // Update the <DtXferPrj> field with "NextInstanceDate" in the transaction history of RecXferSync
                                //
                                RsCmBuilder.updateXferRsDateXferPrj(info.NextInstanceDate + "0000",
                                                                    oldRecRs.RecIntraTrnRs.RecIntraRsUn.RecIntraRs.IntraRs);
                                //
                                // Update the <NInsts> and <DtDue>
                                //
                                oldRecRs.RecIntraTrnRs.RecIntraRsUn.RecIntraRs.RecurrInst.NInsts = sinfo.InstanceCount - sinfo.CurInstanceNum + 1;

                                // QTS 644117: don't allow weekend or holiday future dated recurring instances
                                int smartPayDay = DBUtil.getPayday(info.FIId,
                                info.NextInstanceDate,
                                DBConsts.SCH_CATEGORY_BOOKTRANSFER	) / 100;
                                
                                oldRecRs.RecIntraTrnRs.RecIntraRsUn.RecIntraRs.IntraRs.XferInfo.DtDue = smartPayDay + "000000";
                                audit.updateRecXferRsV1(oldRecRs, dbh, DBConsts.WILLPROCESSON);
                                //
                                // create a record in XferInstruction table
                                //
                                //String dtToPost = String.valueOf(DBUtil.getCurrentStartDate());
                                String dtToPost = smartPayDay + "00" ;
                                XferInstruction ti = new XferInstruction("", rti.CustomerID, rti.FIID, rti.Amount,
                                                                         rti.CurDef, rti.ToAmount, rti.ToAmtCurrency,
                                                                         rti.userAssignedAmount, rti.BankID, rti.BranchID,
                                                                         rti.AcctDebitID, rti.AcctDebitType,
                                                                         rti.AcctCreditID, rti.AcctCreditType,
                                                                         "", dtToPost, DBConsts.WILLPROCESSON,
                                                                         logId, recSrvrTID, rti.SubmittedBy, rti.SubmittedBy,
                                                                         rti.fromBankID,
                                                                         rti.fromBranchID);
                                
                                ti.setPaymentSubType(rti.paymentSubType);
                                
                                // Set Transfer Account from and To country code for core banking
                                ti.acctFromCountryCode = rti.acctFromCountryCode;
                                ti.acctToCountryCode = rti.acctToCountryCode;

                                // do entitlement check first,
                                // if not entitled, do not create any thing
                                if ( doEntitlementCheck( ti ) == false ) {
                                    String msg = "RecXferHandler.eventHandler failed to process: " +
                                                 "Entitlement check failed. Customer Id= " + ti.CustomerID;
                                    FFSDebug.log(msg, FFSConst.PRINT_DEV);
                                    
                                    // mark skipped instances to cancelled...
    								cancelSkippedInstances(dbh, recSrvrTID);
                                    // Cancel this recurring model
                                    // Update BPW_RecXferInstruction
                                    RecXferInstruction.updateStatus(dbh, recSrvrTID, DBConsts.FAILEDON);
                                    // Close the RECINTRATRN
                                    ScheduleInfo.delete(dbh, recSrvrTID, DBConsts.RECINTRATRN);
                                    // Update BPW_RecSrvrTrans
                                    SimpleDateFormat formatter = new SimpleDateFormat( "yyyyMMddHHmmss" );
                                    String prcDate = formatter.format(new Date());
                                    RsCmBuilder.updateRsXferPrcSts( DBConsts.FAILEDON, prcDate,
                                                                    oldRecRs.RecIntraTrnRs.RecIntraRsUn.RecIntraRs.IntraRs.XferPrcSts );
                                    oldRecRs.RecIntraTrnRs.RecIntraRsUn.RecIntraRs.RecurrInst.NInsts = -1;
                                    audit.updateRecXferRsV1(oldRecRs, dbh, DBConsts.FAILEDON);
                                    // Log in Audit Log
                                    if (_logLevel >= AUDITLOGLEVEL_STATUSUPDATE) {
                                        String debitAcct = BPWUtil.getAccountIDWithAccountType(ti.AcctDebitID, ti.AcctDebitType);
                                        String creditAcct = BPWUtil.getAccountIDWithAccountType(ti.AcctCreditID, ti.AcctCreditType);
                                        BigDecimal xferAmount = new BigDecimal(ti.Amount);
                                        String toAmount = ti.ToAmount;
                                        if (toAmount == null) toAmount = "0.00";
                                        BigDecimal xferToAmount = new BigDecimal(toAmount);
                                        ILocalizable localizedMsg = BPWLocaleUtil.getLocalizedMessage(
                                                            AuditLogConsts.AUDITLOG_MSG_ENTITLEMENT_BOOKTRANSFER_FAILED,
                                                            null,
                                                            BPWLocaleUtil.BOOKTRANSFER_AUDITLOG_MESSAGE);
                                        this.logTransAuditLog(dbh,
                                                              ti.SubmittedBy,
                                                              Integer.parseInt(ti.CustomerID),
                                                              ti.LogID,
                                                              AuditLogTranTypes.BPW_INTRAXFER_ADD,
                                                              xferAmount,
                                                              ti.CurDef,
                                                              xferToAmount,
                                                              ti.ToAmtCurrency,
                                                              ti.userAssignedAmount,
                                                              recSrvrTID,
                                                              debitAcct,
                                                              creditAcct,
                                                              ti.BankID,
                                                              ti.fromBankID,
                                                              DBConsts.FAILEDON,
                                                              localizedMsg);
                                    }

                                    continue;
                                }


                                String srvrTid = createXferInstruction(dbh, ti);
                                ti.SrvrTID = srvrTid;

                                TransferInfo tranInfo = TransferIntraMap.mapXferInstToTransferInfo(ti,
                                                                            DBConsts.PMTTYPE_RECURRING,
                                                                            TransferDefines.TRANSFER_BOOK,
                                                                            BPW_TRANSFER_ACTION_ADD);

                                try{
                                    calloutHandler.begin(tranInfo, ADD_INTRA_TRANSFER_FROM_SCHEDULE_OFX200);
                                } catch (Throwable ex){
                                	PerfLoggerUtil.stopPerfLogging(curMethodName, start, uniqueIndex);
                                	
                                    // If callout signals abort, update transfer status to failed and continue
                                    XferInstruction.updateStatus(dbh, srvrTid, DBConsts.FAILED);


                                    try{
                                        tranInfo = TransferIntraMap.mapXferInstToTransferInfo(ti,
                                                                                    DBConsts.PMTTYPE_RECURRING,
                                                                                    TransferDefines.TRANSFER_BOOK,
                                                                                    BPW_TRANSFER_ACTION_ADD);

                                        calloutHandler.failure(tranInfo, ADD_INTRA_TRANSFER_FROM_SCHEDULE_OFX200);
                                    } catch (Throwable e){
                                    	PerfLoggerUtil.stopPerfLogging(curMethodName, start, uniqueIndex);
                                    	
                                    }

                                    continue;
                                }

                                try {
                                    // Push Extra Info from RecXfer to Xfer
                                    if ((rti.extraFields != null)  && (!((HashMap)rti.extraFields).isEmpty())) {
                                        BPWXferExtraInfo.insertHashtable(srvrTid, new Hashtable((HashMap)rti.extraFields), dbh);
                                    }

                                    // Log in Audit Log
                                    if (_logLevel >= AUDITLOGLEVEL_STATUSUPDATE) {
                                        String debitAcct = BPWUtil.getAccountIDWithAccountType(ti.AcctDebitID, ti.AcctDebitType);
                                        String creditAcct = BPWUtil.getAccountIDWithAccountType(ti.AcctCreditID, ti.AcctCreditType);
                                        BigDecimal xferAmount = new BigDecimal(ti.Amount);
                                        String toAmount = ti.ToAmount;
                                        if (toAmount == null) toAmount = "0.00";
                                        BigDecimal xferToAmount = new BigDecimal(toAmount);
                                        ILocalizable msg = BPWLocaleUtil.getLocalizedMessage(
                                                            AuditLogConsts.AUDITLOG_MSG_BOOKTRANSFER_USER_ADD,
                                                            null,
                                                            BPWLocaleUtil.BOOKTRANSFER_AUDITLOG_MESSAGE);
                                        this.logTransAuditLog(dbh,
                                                              ti.SubmittedBy,
                                                              Integer.parseInt(ti.CustomerID),
                                                              ti.LogID,
                                                              AuditLogTranTypes.BPW_INTRAXFER_ADD,
                                                              xferAmount,
                                                              ti.CurDef,
                                                              xferToAmount,
                                                              ti.ToAmtCurrency,
                                                              ti.userAssignedAmount,
                                                              srvrTid,
                                                              debitAcct,
                                                              creditAcct,
                                                              ti.BankID,
                                                              ti.fromBankID,
                                                              DBConsts.WILLPROCESSON,
                                                              msg);
                                    }

                                    // create a record in RecSrvrTIDToSrvrTID table
                                    RecSrvrTIDToSrvrTID.create(dbh, recSrvrTID, srvrTid);
                                    // create a record in BPW_ADJ_INSTR_INFO
                                    if (!createAdjustedInstrInfo(recSrvrTID, srvrTid, sinfo.CurInstanceNum, dbh)) {
                                    	FFSDebug.log(curMethodName +
    											": Current instance has been adjusted. Skipping creation of Instruction.",
    											FFSConst.PRINT_ERR);
    									continue;
                                    }
                                    // Do limit checking
                                    // add all information that does not fit
                                    // IntraTrnInfo into this hashmap
                                    final HashMap extraInfo = new HashMap();
                                    ti.extraFields = rti.extraFields;
                                    XferInfoDef xinfo = doLimitChecking(dbh, ti, extraInfo, info);

                                    // save transaction history of XferSync
                                    //CurDef argument added for I18N
                                    com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeIntraTrnRsV1 rs
                                    = buildXferAddRs(oldRecRs.RecIntraTrnRs.RecIntraRsUn.RecIntraRs.IntraRs.XferInfo, xinfo,
                                                     oldRecRs.RecIntraTrnRs.RecIntraRsUn.RecIntraRs.IntraRs.CurDef);
                                    audit.saveXferRsV1(rs, rti.CustomerID, dbh, xinfo.prcSts);

                                    // Log in Audit Log if limit check failed
                                    if (xinfo.prcSts.equals(DBConsts.FAILEDON)) {
                                        if (_logLevel >= AUDITLOGLEVEL_STATUSUPDATE) {
                                            String debitAcct = BPWUtil.getAccountIDWithAccountType(ti.AcctDebitID, ti.AcctDebitType);
                                            String creditAcct = BPWUtil.getAccountIDWithAccountType(ti.AcctCreditID, ti.AcctCreditType);
                                            BigDecimal xferAmount = new BigDecimal(ti.Amount);
                                            String toAmount = ti.ToAmount;
                                            if (toAmount == null) toAmount = "0.00";
                                            BigDecimal xferToAmount = new BigDecimal(toAmount);
                                            ILocalizable localizedMsg = BPWLocaleUtil.getLocalizedMessage(
                                                                AuditLogConsts.AUDITLOG_MSG_LIMIT_CHECK_FAILED,
                                                                null,
                                                                BPWLocaleUtil.BOOKTRANSFER_AUDITLOG_MESSAGE);

                                            this.logTransAuditLog(dbh,
                                                                  ti.SubmittedBy,
                                                                  Integer.parseInt(ti.CustomerID),
                                                                  ti.LogID,
                                                                  AuditLogTranTypes.BPW_INTRAXFER_ADD,
                                                                  xferAmount,
                                                                  ti.CurDef,
                                                                  xferToAmount,
                                                                  ti.ToAmtCurrency,
                                                                  ti.userAssignedAmount,
                                                                  recSrvrTID,
                                                                  debitAcct,
                                                                  creditAcct,
                                                                  ti.BankID,
                                                                  ti.fromBankID,
                                                                  DBConsts.LIMIT_CHECK_FAILED,
                                                                  localizedMsg);
                                        }
                                    }

                                    // Finished adding transfer from recurring model
                                    try{
                                        tranInfo = TransferIntraMap.mapXferInstToTransferInfo(ti,
                                                                                    DBConsts.PMTTYPE_RECURRING,
                                                                                    TransferDefines.TRANSFER_BOOK,
                                                                                    BPW_TRANSFER_ACTION_ADD);
                                        calloutHandler.end(tranInfo, ADD_INTRA_TRANSFER_FROM_SCHEDULE_OFX200);
                                    } catch (Throwable ex){
                                    	PerfLoggerUtil.stopPerfLogging(curMethodName, start, uniqueIndex);
                                    }
                                } catch (Exception exc) {
                                    try{
                                        tranInfo = TransferIntraMap.mapXferInstToTransferInfo(ti,
                                                                                    DBConsts.PMTTYPE_RECURRING,
                                                                                    TransferDefines.TRANSFER_BOOK,
                                                                                    BPW_TRANSFER_ACTION_ADD);

                                        calloutHandler.end(tranInfo, ADD_INTRA_TRANSFER_FROM_SCHEDULE_OFX200);
                                    } catch (Throwable ex){
                                    	PerfLoggerUtil.stopPerfLogging(curMethodName, start, uniqueIndex);
                                    }
                                    PerfLoggerUtil.stopPerfLogging(curMethodName, start, uniqueIndex);
                                    throw exc; // Throw original exception to preserve error handling
                                }
                            }
                        } else {
                        	PerfLoggerUtil.stopPerfLogging(curMethodName, start, uniqueIndex);
                            throw new FFSException("Not supported OFX version!");
                        }
                    } else {
                        // SchedeuleInfo not found means that it is closed
                        if (res[0].startsWith("OFX151")) {
                            com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeRecIntraTrnRsV1 oldRecRs
                            = (com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeRecIntraTrnRsV1) broker.parseMsg(res[1], "RecIntraTrnRsV1", "OFX151");
                            
                            // mark skipped instances to cancelled...
							cancelSkippedInstances(dbh, recSrvrTID);
                            // Update the transaction history for closed RecXferInstruction
                            RecXferInstruction.updateStatus(dbh, recSrvrTID, DBConsts.POSTEDON);

                            oldRecRs.RecIntraTrnRs.RecIntraRsV1Un.RecIntraRs.RecurrInst.NInsts = 0;
                            com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeRecIntraTrnRsV1 recRs
                            = buildRecXferRs(oldRecRs);
                            audit.updateRecXferRsV1(recRs, dbh, DBConsts.POSTEDON);
                        } else if (res[0].startsWith("OFX200")) {
                            com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeRecIntraTrnRsV1 oldRecRs
                            = (com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeRecIntraTrnRsV1) broker.parseMsg(res[1], "RecIntraTrnRsV1", "OFX200");
                            
                            // mark skipped instances to cancelled...
							cancelSkippedInstances(dbh, recSrvrTID);
                            // Update the transaction history for closed RecXferInstruction
                            RecXferInstruction.updateStatus(dbh, recSrvrTID, DBConsts.POSTEDON);

                            oldRecRs.RecIntraTrnRs.RecIntraRsUn.RecIntraRs.RecurrInst.NInsts = 0;
                            com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeRecIntraTrnRsV1 recRs
                            = buildRecXferRs(oldRecRs);
                            audit.updateRecXferRsV1(recRs, dbh, DBConsts.POSTEDON);
                        }
                    }
                }
            } else if (eventSequence == 2) {    // LAST sequence
            }
        } catch (Exception exc) {
            String errDescrip = "*** RecXferHandler.eventHandler failed:";
            FFSDebug.log(errDescrip + exc);
//            StringWriter sw = new StringWriter();
//            exc.printStackTrace(new PrintWriter(sw));
//            FFSDebug.log(sw.toString());
            PerfLoggerUtil.stopPerfLogging(curMethodName, start, uniqueIndex);
            throw new FFSException(exc.toString());
        }

        FFSDebug.log("=== RecXferHandler.eventHander: end", FFSConst.PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(curMethodName, start, uniqueIndex);
    }
    
	private void computeNextInstanceDate(FFSConnectionHolder dbh, ScheduleInfo sinfo) throws Exception {
		SimpleDateFormat formatter = new SimpleDateFormat( "yyyyMMdd" );
        int currentDate = Integer.parseInt(formatter.format(new Date()));
        int nextInstanceDate = sinfo.NextInstanceDate / 100;
        
        if(nextInstanceDate == currentDate){
        	sinfo.NextInstanceDate = ScheduleInfo.computeFutureDate(sinfo.NextInstanceDate,sinfo.Frequency,1, sinfo.FIId, DBConsts.SCH_CATEGORY_BOOKTRANSFER, sinfo.InstructionType);
        	ScheduleInfo.modifySchedule(dbh, sinfo.ScheduleID, sinfo);
        }
	}

	@SuppressWarnings("unchecked")
	private boolean cancelSkippedInstances(FFSConnectionHolder dbh, String recSrvrTID) {
		
		try {
			List<TransferInfo> transferInfos = XferInstruction.getPendingTransfersByRecSrvrTIdAndStatus(dbh, recSrvrTID, "'" + DBConsts.SKIPPEDON + "'");
			for (TransferInfo transferInfo : transferInfos) {
				FFSDebug.log("Found " + transferInfo.getSrvrTId() + " skipped payment", FFSConst.PRINT_DEV);
				//simply mark it as cancelled as already its schedule and limits would have been deleted.
				XferInstruction.updateStatus(dbh, transferInfo.getSrvrTId(), DBConsts.CANCELEDON);
			}
		} catch (BPWException e) {
			FFSDebug.log("++_++ RecXferHandler.cancelSkippedInstances: Error in cancelling skipped records.",
					FFSConst.PRINT_ERR);
			return false;
		} catch (FFSException e1) {
			FFSDebug.log("++_++ RecXferHandler.cancelSkippedInstances: Error in cancelling skipped records.",
					FFSConst.PRINT_ERR);
			return false;
		}
		return true;
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
                                    FFSConnectionHolder dbh)
    throws Exception {
    	 String methodName = "RecXferHandler.resubmitEventHandler";
         long start = System.currentTimeMillis();
         int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log("=== RecXferHandler.resubmitEventHandler: begin, eventSeq="
                     + eventSequence
                     + ",length="
                     + evts._array.length
                     + ",instructionType="
                     + evts._array[0].InstructionType);
        eventHandler(eventSequence, evts, dbh);
        FFSDebug.log("=== RecXferHandler.resubmitEventHandler: end");
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

//=====================================================================
// buildXferAddRs()
// Description: Build the response message for Transfer Add request.
// Arguments:
// Returns:
// Note:
//=====================================================================
    private com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeIntraTrnRsV1
    buildXferAddRs(com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeXferInfoV1Aggregate xferInfo,
                   XferInfoDef xinfo,
                   com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.EnumCurrencyEnum curdef )
    throws Exception {
        com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeIntraTrnRsV1Aggregate IntraTrnRs
        = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeIntraTrnRsV1Aggregate();
        IntraTrnRs.IntraTrnRsV1UnExists = true;
        com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeIntraTrnRsV1Un IntraTrnRsV1Un
        = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeIntraTrnRsV1Un();
        IntraTrnRs.IntraTrnRsV1Un = IntraTrnRsV1Un;
        IntraTrnRsV1Un.__memberName = "IntraRs";
        com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeIntraRsV1Aggregate IntraRs
        = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeIntraRsV1Aggregate();
        IntraTrnRsV1Un.IntraRs = IntraRs;
        //Begin Modify TCG for I18N
        if ( curdef != null ) {
            IntraRs.CurDef = curdef;
        } else {
            IntraRs.CurDef = MsgBuilder.getOFX151CurrencyEnum(BPWUtil.validateCurrencyEnum(curdef));
        }
        //End Modify TCG for I18N
        IntraRs.SrvrTID = xinfo.srvrTid;
        IntraRs.RecSrvrTIDExists = true;
        IntraRs.RecSrvrTID = xinfo.recSrvrTid;
        IntraRs.XferInfo = xferInfo;
        IntraRs.XferPrcStsExists = true;
        com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeXferPrcStsAggregate XferPrcSts =
        new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeXferPrcStsAggregate();
        IntraRs.XferPrcSts = XferPrcSts;
        String smartDate = SmartCalendar.getPayday(xinfo.fiId, xinfo.StartDate / 100, DBConsts.SCH_CATEGORY_BOOKTRANSFER) + "000000";
        RsCmBuilder.updateRsXferPrcSts(xinfo.prcSts, xinfo.prcDate,
                                       IntraTrnRs.IntraTrnRsV1Un.IntraRs.XferPrcSts);
        RsCmBuilder.updateXferRsDateXferPrj(smartDate, IntraTrnRs.IntraTrnRsV1Un.IntraRs);

        com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeTrnRqCm trnRqCm
        = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeTrnRqCm();
        trnRqCm.TrnUID = "0";
        trnRqCm.CltCookieExists = false;
        trnRqCm.TANExists = false;
        IntraTrnRs.TrnRsV1Cm = RsCmBuilder.buildTrnRsCmV1(trnRqCm, xinfo.code);

        return new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeIntraTrnRsV1(IntraTrnRs);
    }


    private com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeIntraTrnRsV1
    buildXferAddRs(com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeXferInfoAggregate xferInfo,
                   XferInfoDef xinfo,
                   com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.EnumCurrencyEnum curdef)
    throws Exception {
        com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeIntraTrnRsV1Aggregate IntraTrnRs
        = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeIntraTrnRsV1Aggregate();
        IntraTrnRs.IntraTrnRsUnExists = true;
        com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeIntraTrnRsUn IntraTrnRsUn
        = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeIntraTrnRsUn();
        IntraTrnRs.IntraTrnRsUn = IntraTrnRsUn;
        IntraTrnRsUn.__memberName = "IntraRs";
        com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeIntraRsAggregate IntraRs
        = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeIntraRsAggregate();
        IntraTrnRsUn.IntraRs = IntraRs;
        //Begin Modify TCG for I18N
        if ( curdef != null ) {
            IntraRs.CurDef = curdef;
        } else {
            IntraRs.CurDef = MsgBuilder.getOFX200CurrencyEnum(BPWUtil.validateCurrencyEnum(curdef));
        }
        //End Modify TCG for I18N
        IntraRs.SrvrTID = xinfo.srvrTid;
        IntraRs.RecSrvrTIDExists = true;
        IntraRs.RecSrvrTID = xinfo.recSrvrTid;
        IntraRs.XferInfo = xferInfo;
        IntraRs.XferPrcStsExists = true;
        com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeXferPrcStsAggregate XferPrcSts =
        new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeXferPrcStsAggregate();
        IntraRs.XferPrcSts = XferPrcSts;
        String smartDate = SmartCalendar.getPayday(xinfo.fiId, xinfo.StartDate / 100, DBConsts.SCH_CATEGORY_BOOKTRANSFER) + "000000";
        RsCmBuilder.updateRsXferPrcSts(xinfo.prcSts, xinfo.prcDate,
                                       IntraTrnRs.IntraTrnRsUn.IntraRs.XferPrcSts);
        RsCmBuilder.updateXferRsDateXferPrj(smartDate, IntraTrnRs.IntraTrnRsUn.IntraRs);

        com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeTrnRqCm trnRqCm
        = new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeTrnRqCm();
        trnRqCm.TrnUID = "0";
        trnRqCm.CltCookieExists = false;
        trnRqCm.TANExists = false;
        IntraTrnRs.TrnRsCm = RsCmBuilder.buildTrnRsCmV1(trnRqCm, xinfo.code);

        return new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeIntraTrnRsV1(IntraTrnRs);
    }


//=====================================================================
// buildRecXferRs()
// Description: Build the response message for last instance of RecXfer.
//    It modifies the following field
//    1. XferPrcCode
//    2. DtXferPrc
//    3. DtPosted
// Arguments:
// Returns:
// Note:
//=====================================================================
    private com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeRecIntraTrnRsV1
    buildRecXferRs(com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeRecIntraTrnRsV1 recRs) {
        com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeXferPrcStsAggregate xferPrcSts
        = recRs.RecIntraTrnRs.RecIntraRsV1Un.RecIntraRs.IntraRs.XferPrcSts;
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String prcDate = formatter.format(new Date());
        RsCmBuilder.updateRsXferPrcSts(DBConsts.POSTEDON, prcDate, xferPrcSts);
        RsCmBuilder.updateXferRsDatePosted(prcDate, recRs.RecIntraTrnRs.RecIntraRsV1Un.RecIntraRs.IntraRs);

        return new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeRecIntraTrnRsV1(recRs.RecIntraTrnRs);
    }

    private com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeRecIntraTrnRsV1
    buildRecXferRs(com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeRecIntraTrnRsV1 recRs) {
        com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeXferPrcStsAggregate xferPrcSts
        = recRs.RecIntraTrnRs.RecIntraRsUn.RecIntraRs.IntraRs.XferPrcSts;
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String prcDate = formatter.format(new Date());
        RsCmBuilder.updateRsXferPrcSts(DBConsts.POSTEDON, prcDate, xferPrcSts);
        RsCmBuilder.updateXferRsDatePosted(prcDate, recRs.RecIntraTrnRs.RecIntraRsUn.RecIntraRs.IntraRs);

        return new com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeRecIntraTrnRsV1(recRs.RecIntraTrnRs);
    }

    /**
     * Do transaction audit log which logs this information to
     * audit_log table in CB
     *
     * @param dbh
     * @param userId
     * @param businessId
     * @param tranId
     * @param tranType
     * @param amount
     * @param curDef
     * @param srvrTId
     * @param fromAcctId
     * @param toAcctId
     * @param toBankId
     * @param fromBankId
     * @param status
     * @param decription
     * @exception FFSException
     */
    private void logTransAuditLog(FFSConnectionHolder dbh,
                                  String userId,
                                  int businessId,
                                  String tranId,
                                  int tranType,
                                  BigDecimal amount,
                                  String curDef,
                                  BigDecimal toAmount,
                                  String toAmtCurrency,
                                  UserAssignedAmount userAssignedAmount,
                                  String srvrTId,
                                  String fromAcctId,
                                  String toAcctId,
                                  String toBankId,
                                  String fromBankId,
                                  String status,
                                  ILocalizable decription)
    throws FFSException {

        TransAuditLog.logTransAuditLog(dbh.conn.getConnection(),
                                       userId,
                                       null, // agentID,
                                       null, // agentType,
                                       decription, // desc,
                                       tranId, // tranID,
                                       tranType,
                                       businessId, // businessId (int)
                                       amount,
                                       curDef, // currencyCode,
                                       toAmount,
                                       toAmtCurrency,
                                       userAssignedAmount,
                                       srvrTId,
                                       status,
                                       toAcctId,
                                       toBankId, // toAcctRtgNum
                                       fromAcctId,
                                       fromBankId, // fromAcctRtgNum
                                       0);     // module
    }

    /**
     * Does Limit checking for single transaction created from the reurring
     * model. update XferInstruction with the status we get from the limit
     * checking. if the status we get from the limit checking is
     * "WILLPROCESSON" create schedule for this transaction.
     * @param dbh
     * @param ti
     * @param extraInfo
     * @param info
     * @return
     * @throws FFSException
     */
    private XferInfoDef doLimitChecking(final FFSConnectionHolder dbh,
                                        final XferInstruction ti,
                                        final HashMap extraInfo,
                                        final ScheduleInfo info)
    throws FFSException {

        final String methodName = "RecXferHandler.doLimitChecking";

        FFSDebug.log(methodName, ": starts....", FFSConst.PRINT_DEV);
        String status = null;
        // Create Transfer Object to be sent to limit checking
        IntraTrnInfo intraInfo = RecXferProcessor2.mapToIntraTrnInfo(ti, extraInfo);
        // Send this batch for limitchecking

        XferInfoDef xinfo = new XferInfoDef();
        xinfo.StartDate = info.StartDate;
        xinfo.fiId = info.FIId;
        xinfo.curDef = intraInfo.curDef;
        xinfo.srvrTid = intraInfo.srvrTid;
        xinfo.recSrvrTid = intraInfo.recSrvrTid;
        xinfo.prcDate = xinfo.StartDate + "0000";

        int result = LimitCheckApprovalProcessor.processIntraTrnAdd(dbh,
                                                                    intraInfo,
                                                                    extraInfo);//extra info
        // map to BPW status
        status = LimitCheckApprovalProcessor.mapToStatus(result);

        if (DBConsts.APPROVAL_PENDING.equalsIgnoreCase(status)) {
            ti.Status = DBConsts.APPROVAL_PENDING;
            XferInstruction.updateStatus(dbh,
                                         intraInfo.srvrTid, status);
            xinfo.code = MsgBuilder.CODE_Success;
            xinfo.prcSts = DBConsts.WILLPROCESSON;

        } else if (DBConsts.WILLPROCESSON.equalsIgnoreCase(status)) {
            // create a record in ScheduleInfo table
            ScheduleInfo.createSchedule(
                                       dbh,
                                       DBConsts.INTRATRN,
                                       intraInfo.srvrTid,
                                       info,
                                       DBConsts.SCH_CATEGORY_BOOKTRANSFER );

            xinfo.code = MsgBuilder.CODE_Success;
            xinfo.prcSts = DBConsts.WILLPROCESSON;
        } else {
			// the limit check failed - manually rollback the limit since the running total will be updated as part of the limit
			// check and this is part of a larger transaction that can't be rolled back.
			// save/restore the status code and the message, since they will be modified by the call to processIntraTrnDelete.
			int xInfoStatusCode = intraInfo.getStatusCode();
			String xInfoStatusMsg = intraInfo.getStatusMsg();
			LimitCheckApprovalProcessor.processIntraTrnDelete(dbh, intraInfo, extraInfo);
			intraInfo.setStatusCode(xInfoStatusCode);
			intraInfo.setStatusMsg(xInfoStatusMsg);

            // update XferInstruction with the status we get
            // from Limit checker
            XferInstruction.updateStatus(dbh,
                                         intraInfo.srvrTid, status);
            //do not create schedule
            FFSDebug.log(methodName, ": srvrTid: " + intraInfo.srvrTid
                         + ", status: " + status, FFSConst.PRINT_DEV);
            xinfo.code = MsgBuilder.CODE_GeneralError;
            xinfo.prcSts = DBConsts.FAILEDON; //DBConsts.WILLPROCESSON;
        }
        FFSDebug.log(methodName, ": ends....", FFSConst.PRINT_DEV);
        return xinfo;
    }


    /**
     *  Does Entitlement checking before creating single transaction created
     * from the reurring
     *  model.
     *
     * @param ti
     * @return
     * @exception FFSException
     */
    private boolean doEntitlementCheck( XferInstruction ti )
    throws FFSException {

        final String methodName = "RecXferHandler.doEntitlementCheck";

        FFSDebug.log(methodName, ": starts....", FFSConst.PRINT_DEV);
        HashMap extraInfo = new HashMap();
        // Create Transfer Object to be sent to limit checking
        IntraTrnInfo intraInfo = RecXferProcessor2.mapToIntraTrnInfo(ti, extraInfo);
        // Send this batch for limitchecking

        // XferInfoDef xinfo = new XferInfoDef();
        // xinfo.StartDate = info.StartDate; // we don't need startDate to do entitlement check
        // xinfo.prcDate = Integer.toString(xinfo.StartDate) + "0000";
        // xinfo.fiId = info.FIId; // we don't need startDate to do entitlement check
        // xinfo.srvrTid = intraInfo.srvrTid;
        // xinfo.recSrvrTid = intraInfo.recSrvrTid;

        return LimitCheckApprovalProcessor.checkEntitlementIntra( intraInfo,
                                                                  extraInfo);//extra info
    }
    
    /**
	 * Delete all records with specified RecSrvrTID from BPW_Adj_Instr_Info table.
	 * 
	 * @param recSrvrTid
	 *            RecSrvrTID of recurring model.
	 * @param dbh
	 *            Database connection
	 */
	/*private void deleteAdjustedInstrInfo(String recSrvrTid, FFSConnectionHolder dbh) {
		
		if(recSrvrTid == null)
			return;
		try {
			AdjustedInstruction.deleteRecords(recSrvrTid, dbh);
			dbh.conn.commit();
		} catch(Exception e) {
			FFSDebug.log("++_++ RecPmtHandler.deleteAdjustedInstrInfo: Error in deleting records.", FFSConst.PRINT_ERR);
			dbh.conn.rollback();
		}
	}*/
	
	/**
	 * Create adjusted info with specified recSrvrTid and srvrTid in BPW_Adj_Instr_Info table.
	 * 
	 * @param recSrvrTid
	 *            RecSrvrTID of recurring model.
	 * @param dbh
	 *            Database connection
	 */
	private boolean createAdjustedInstrInfo(String recSrvrTid, String srvrTid, int instanceNum, FFSConnectionHolder dbh) {
		
		String methodName = "RecXferHandler.createAdjustedInstrInfo";
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
			FFSDebug.log("++_++ RecXferHandler.createAdjustedInstrInfo: Error in creating record.", FFSConst.PRINT_ERR);
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

	/**
	 * Get recurring transfer instruction with channel information.
	 * 
	 * @param recSrvrTID
	 *            RecSrvrTID of rec instruction.
	 * @param dbh
	 *            DB connection.
	 * @return RecXferInstruction.
	 * @throws FFSException
	 */
    private RecXferInstruction getRecXferInstruction(FFSConnectionHolder dbh, String recSrvrTID) throws FFSException {
    	
    	final String method = "RecXferHandler:getRecXferInstruction: ";
    	RecXferInstruction rxfi = RecXferInstruction.getRecXferInstruction(dbh, recSrvrTID);
    	if(rxfi!=null && rxfi.getChannelId() != null) {
    		EntChannelOps channelOps = ChannelStore.getChannelIdForTrn(rxfi.getSubmittedBy(), recSrvrTID,
    				DBConsts.BPW_RecXferInstruction, dbh);
    		rxfi.setChannelId(channelOps.getChannelIDMod());
    		rxfi.setChannelGroupId(channelOps.getChannelGroupIdMod());
    		FFSDebug.log(method + " Channel info read from table: " + rxfi.getChannelId() + " for RecSrvrTid: "
    				+ recSrvrTID, PRINT_DEV);
    	} else {
    		FFSDebug.log(method + " Channel info not present for RecSrvrTid: " + recSrvrTID, PRINT_DEV);
    	}
    	return rxfi;
    }
    
    
	/**
	 * Create transfer instruction and store channel information.
	 * 
	 * @param dbh
	 *            DB connection.
	 * @param ti
	 *            Transfer instruction.
	 * @return SrvrTid of transfer instruction.
	 * @throws FFSException
	 */
    private String createXferInstruction(FFSConnectionHolder dbh, XferInstruction ti) throws FFSException {
    	
    	final String method = "RecXferHandler.createXferInstruction: ";
    	String srvrTid = XferInstruction.create(dbh, ti);
    	if(ti.getChannelId() != null) {
    		ChannelStore.addChannelInfoForTrn(ti.getSubmittedBy(), srvrTid, DBConsts.BPW_XferInstruction, 
    				ti.getChannelId(), dbh, ti.getChannelGroupId());
    		FFSDebug.log(method + " Stored channel info: " + ti.getChannelId() + " for SrvrTid: " + srvrTid, PRINT_DEV);
    	} else {
    		FFSDebug.log(method + " No channel info stored. SrvrTid: " + srvrTid, PRINT_DEV);
    	}
    	return srvrTid;
    }
}
