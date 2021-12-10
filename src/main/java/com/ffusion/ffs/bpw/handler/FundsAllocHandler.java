//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2001.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.ffusion.ffs.bpw.audit.TransAuditLog;
import com.ffusion.ffs.bpw.custimpl.interfaces.BackendProvider;
import com.ffusion.ffs.bpw.db.Customer;
import com.ffusion.ffs.bpw.db.DBConnCache;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.db.Payee;
import com.ffusion.ffs.bpw.db.PmtInstruction;
import com.ffusion.ffs.bpw.db.SrvrTrans;
import com.ffusion.ffs.bpw.interfaces.AuditLogConsts;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.BankInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.FundsAllocInfo;
import com.ffusion.ffs.bpw.interfaces.FundsAllocator;
import com.ffusion.ffs.bpw.interfaces.PayeeInfo;
import com.ffusion.ffs.bpw.interfaces.PmtInfo;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.util.BPWRegistryUtil;
import com.ffusion.ffs.bpw.util.BPWUtil;
import com.ffusion.ffs.bpw.util.resources.BPWLocaleUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.ffusion.util.ILocalizable;
import com.ffusion.util.logging.AuditLogTranTypes;
import com.sap.banking.common.interceptors.PerfLoggerUtil;
//import com.ffusion.ffs.bpw.master.*;

//=====================================================================
//
// This class contains a callback handler that is registered in the
// InstructionType table.  The registered callback handler will be called
// by the Scheduling engine for the Funds Allocation Processing.
//
//=====================================================================

public class FundsAllocHandler implements
com.ffusion.ffs.bpw.interfaces.DBConsts, FFSConst,
com.ffusion.ffs.bpw.interfaces.BPWResource, BPWScheduleHandler {
	/**
	 *
	 */
	private static final long serialVersionUID = 1245741359032033335L;
	private ArrayList _vec = null;
	// private static int _batchNum = 1;
	private final Map batchKeys = new HashMap();
	private final String BATCHKEYPREFIX = "FundsAlloc_";


	// FIXME TODO for testing remove me
	//private static int batchNum = 0;

	public FundsAllocHandler() {
	}

	/**
	 * Get the batch key for this FI if it does not exists create it
	 *
	 * @param fiId
	 * @return
	 */
	private synchronized String getBatchKey(String fiId) throws Exception {
		final String methodName = "FundsAllocHandler.getBatchKey: ";

		FFSDebug.log(methodName, "Starts..... FIId: " + fiId, ", batchKey: ",
				(String) batchKeys.get(fiId), PRINT_DEV);

		String batchKey = null;
		if (batchKeys.get(fiId) == null) {

			batchKey = BATCHKEYPREFIX + DBConnCache.getNewBatchKey();

			batchKeys.put(fiId, batchKey);
		}
		FFSDebug.log(methodName, "Ends..... FIId: " + fiId, ", batchKey: ",
				(String) batchKeys.get(fiId), PRINT_DEV);
		return (String) batchKeys.get(fiId);
	}

	/**
	 * Create a new Batch key for this FI
	 *
	 * @param fiId
	 * @return
	 * @throws Exception
	 */
	private synchronized String getNewBatchKey(String fiId) throws Exception {
		final String methodName = "FundsAllocHandler.getNewBatchKey: ";

		FFSDebug.log(methodName, "Starts..... FIId: " + fiId, ", batchKey: ",
				(String) batchKeys.get(fiId), PRINT_DEV);

		String batchKey = BATCHKEYPREFIX + DBConnCache.getNewBatchKey();

		batchKeys.put(fiId, batchKey);

		FFSDebug.log(methodName, "Ends..... FIId: " + fiId, ", batchKey: ",
				(String) batchKeys.get(fiId), PRINT_DEV);
		return (String) batchKeys.get(fiId);
	}

	/**
	 * Refresh the batch key for this FI
	 *
	 * @param fiId
	 */
	private synchronized void updateBatchKey(String fiId) throws Exception {
		final String methodName = "FundsAllocHandler.updateBatchKey: ";

		FFSDebug.log(methodName, "Starts..... FIId: " + fiId, ", batchKey: ",
				(String) batchKeys.get(fiId), PRINT_DEV);

		String batchKey = BATCHKEYPREFIX + DBConnCache.getNewBatchKey();
		batchKeys.put(fiId, batchKey);
		FFSDebug.log(methodName, "Ends..... FIId: " + fiId, ", batchNumber: ",
				(String) batchKeys.get(fiId), PRINT_DEV);
	}

	/**
	 * This method is called by the Scheduling engine to process FUNDTRN
	 * Schedule
	 *
	 * @param eventSequence
	 * @param evts
	 * @param dbh
	 * @throws Exception
	 */
	public void eventHandler(int eventSequence, EventInfoArray evts,
			FFSConnectionHolder dbh) throws Exception {
		
		String methodName = "FundAllocHandler.eventHandler: ";
		 long start = System.currentTimeMillis();
	        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);

		FFSDebug.log(methodName,
				"=== FundsAllocHandler.eventHandler: begin, eventSeq="
						+ eventSequence + ",length=" + evts._array.length,
				PRINT_DEV);
		String fiId = evts._array[0].FIId;

		FFSConnectionHolder dbhDirty = null;

		BackendProvider backendProvider = getBackendProviderService();
		FundsAllocator _fundsAllocator = backendProvider.getFundsAllocatorInstance();

        if ( eventSequence == 0 ) {         // FIRST sequence
			_vec = new ArrayList();
			String batchKey = getNewBatchKey(fiId);

			FFSDebug.log("Got batchKey: [", batchKey, "], for entSeq: "
					+ eventSequence, PRINT_DEV);
			String currency = BPWUtil.validateCurrencyString(null);
			FundsAllocInfo info = new FundsAllocInfo(fiId, "", "", "", "", "",currency,
                                                    "", //srvrTID,
					"", "", eventSequence, false, // possibleDuplicate false
					"", batchKey,null);
            _vec.add( info );
			FFSDebug.log(methodName, "info batchKey: [", info.batchKey,
					"for entSeq: " + eventSequence, PRINT_DEV);

			// convert vector to array and call Backend interface
			// ==FundsAllocInfo[] ar = (FundsAllocInfo[]) _vec.toArray( new
			// FundsAllocInfo[0] );
            //==FundsAllocator.addFundsVerification( ar );
            //==_vec.clear();

			/*
			// FIXME TODO for testing remove me
			batchNum = 1;
			FFSDebug.log(methodName,
					"eventSeq : "+ eventSequence, ", batchNum: " +batchNum,
					FFSConst.PRINT_DEV);
			batchNum++;

*/

        } else if ( eventSequence == 1 ) {    // NORMAL sequence
/*
			// FIXME TODO for testing remove me
			FFSDebug.log(methodName,
					"eventSeq : "+ eventSequence, ", batchNum: " +batchNum,
					FFSConst.PRINT_DEV);
			batchNum++;
*/
			try {
				dbhDirty = new FFSConnectionHolder();
				dbhDirty.conn = DBUtil.getDirtyReadConnection();

				if (dbhDirty.conn == null) {
					throw new Exception("Null dirty connection returned");
				}

				for (int i = 0; i < evts._array.length; i++) {
					if (evts._array[i] == null) {

						FFSDebug.log(methodName,
								" ++--++ Invalid Transaction in this batch: "
										+ evts._array[i],
								", Transcaction will be ignored, Transaction counter: "
										+ i, FFSConst.PRINT_DEV);
						continue;
					}

					String srvrTID = evts._array[i].InstructionID;
					FFSDebug.log(methodName, "eventSeq: " + eventSequence,
							", srvrTID: ", srvrTID, PRINT_DEV);

					FFSDebug.log(methodName, " getting payment for srvrTID: ",
							srvrTID, PRINT_DEV);

					PmtInstruction pmt = PmtInstruction.getPmtInstr(srvrTID,
							dbhDirty);
					dbhDirty.conn.commit();
					if (pmt == null) {
						FFSDebug.log(methodName,
								" Failed: could not find the SrvrTID: ",
								srvrTID, ", in BPW_PmtInstruction", PRINT_ERR);
						continue;
					}
					PmtInfo pmtinfo = pmt.getPmtInfo();
                    if (pmtinfo.getCustomerID() != null && !Customer.isActive(pmtinfo.getCustomerID(), dbhDirty))
                    {
                        FFSDebug.log(methodName, " Customer is not active for srvrTID: ",
                                srvrTID, ", and payee: ", pmtinfo.PayeeID,
                                PRINT_DEV);
                        continue;
                    }
					FFSDebug.log(methodName, " getting payee for srvrTID: ",
							srvrTID, ", and payee: ", pmtinfo.PayeeID,
							PRINT_DEV);
					PayeeInfo payeeinfo = Payee.findPayeeByID(pmtinfo.PayeeID,
							dbhDirty);
					dbhDirty.conn.commit();
					if (payeeinfo == null) {

						FFSDebug.log(methodName,
								" Failed: could not find the PayeeID: ",
								pmtinfo.PayeeID,
								", in BPW_Payee for payment of SrvrTID: ",
								srvrTID, PRINT_ERR);

						continue;
					}

					BankInfo binfo = BPWRegistryUtil
							.getBankInfo(pmtinfo.BankID);
					String acctCreditId = null;
					PropertyConfig propertyConfig = (PropertyConfig) FFSRegistry
							.lookup(BPWResource.PROPERTYCONFIG);

					if (binfo == null) {
						// Get Bpw bank look up level
						String checkingLevel = propertyConfig.otherProperties
								.getProperty(
										DBConsts.BANKINFO_LOOKUP_LEVEL,
										DBConsts.BPW_BANK_LOOKUP_LEVEL_STRICT);
						if (checkingLevel
								.equalsIgnoreCase(DBConsts.BPW_BANK_LOOKUP_LEVEL_STRICT)) {
                            String msg = "*** FundsAllocHandler.eventHandler failed:could not find the BankID in BPW_Bank, bpw.pmt.bankinfo.lookup.level = STRICT " ;
							FFSDebug.console(msg);
                            FFSDebug.log(msg,FFSConst.PRINT_ERR);
							throw new FFSException(msg);
						} else if (checkingLevel
								.equalsIgnoreCase(DBConsts.BPW_BANK_LOOKUP_LEVEL_LENIENT)) {
                            String msg = "*** FundsAllocHandler.eventHandler : bpw.pmt.bankinfo.lookup.level = LENIENT " ;
							// FFSDebug.console(msg);
                            FFSDebug.log(msg,FFSConst.PRINT_WRN);

						} else if (checkingLevel
								.equalsIgnoreCase(DBConsts.BPW_BANK_LOOKUP_LEVEL_NONE)) {
                            String msg = "*** FundsAllocHandler.eventHandler : bpw.pmt.bankinfo.lookup.level = NONE " ;
							// FFSDebug.console(msg);
                            FFSDebug.log(msg,FFSConst.PRINT_DEV);
						} else {
							// None of above type
							String msg = "*** FundsAllocHandler.eventHandler failed: Incorrect value for bpw.pmt.bankinfo.lookup.level ="
									+ checkingLevel;
							FFSDebug.console(msg);
							FFSDebug.log(msg);
							throw new Exception(msg);
						}
						acctCreditId = String.valueOf(-1);
					} else {
						acctCreditId = binfo.debitGLAcct;
					}
					boolean possibleDuplicate;
					//
					// assemble FundsAllocInfo
					//
					possibleDuplicate = !pmtinfo.Status
							.equalsIgnoreCase(com.ffusion.ffs.bpw.interfaces.DBConsts.WILLPROCESSON);

					String batchKey = getBatchKey(fiId);

					FFSDebug.log(methodName, "Got batchKey: [", batchKey,
							"], for pmt: ", srvrTID, ", for entSeq: "
									+ eventSequence, PRINT_DEV);
					String currency = BPWUtil.validateCurrencyString(pmtinfo.CurDef);
					FundsAllocInfo info = new FundsAllocInfo(pmtinfo.FIID,
							pmtinfo.CustomerID, pmtinfo.BankID,
							pmtinfo.AcctDebitID, pmtinfo.AcctDebitType, pmtinfo
									.getAmt(), currency , srvrTID,
							pmtinfo.PayeeID, payeeinfo.PayeeName,
							eventSequence, possibleDuplicate, // possibleDuplicate
							// false
							acctCreditId, batchKey, pmtinfo.RecSrvrTID, // Server
							// transaction
							// ID of
							// parent
							// recurring
							// model
							pmtinfo // added to provide additional info
					// to backend for one step payment processing
					// Turoa release
					);

					//
					// update PmtInstruction table with status
					//

					// retrieve the extrainfo info from BPW_PmtExtraInfo
					// and assign it to extraFields of FundsAllocInfo
					if (pmtinfo.extraFields != null) {
						info.extraFields = pmtinfo.extraFields;
					}
					FFSDebug.log(methodName,
							" Updating payment status for for srvrTID: ",
							srvrTID, ", and new status: ", INFUNDSALLOC,
							PRINT_DEV);

					String existingStatuses = "'" + WILLPROCESSON + "','"
							+ INFUNDSALLOC + "','" + FUNDSALLOCACTIVE + "'";

					int updated = PmtInstruction.updatePmtStatus(dbh, srvrTID,
							INFUNDSALLOC, existingStatuses);

					FFSDebug.log(methodName,
							" Updated payment status for for srvrTID: ",
							srvrTID, ", and updated count: " + updated,
							PRINT_DEV);

					// PmtInstruction.updateStatus( dbh, srvrTID, INFUNDSALLOC
					// );

					// cache the information
					//
                    _vec.add( info );
					// log into AuditLog
					if (propertyConfig.LogLevel >= AUDITLOGLEVEL_STATUSUPDATE) {
						int bizID = Integer.parseInt(pmtinfo.CustomerID);
						BigDecimal pmtAmount = BPWUtil.getBigDecimal(pmtinfo
								.getAmt());

						String debitAcct = BPWUtil.getAccountIDWithAccountType(
								pmtinfo.AcctDebitID, pmtinfo.AcctDebitType);
						ILocalizable msg = BPWLocaleUtil.getLocalizedMessage(
								AuditLogConsts.AUDITLOG_MSG_FUND_ALLOCATION,
								null, BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
						TransAuditLog.logTransAuditLog(
								dbh.conn.getConnection(), pmtinfo.submittedBy,
								null, null, msg, pmtinfo.LogID,
								AuditLogTranTypes.BPW_FUNDTRN, bizID,
								pmtAmount, pmtinfo.CurDef, srvrTID,
								INFUNDSALLOC, pmtinfo.PayAcct, null, debitAcct,
								pmtinfo.getBankID(), 0);
					}

					FFSDebug.log(methodName,
							" Done processing info of the batch", PRINT_DEV);

				}

				//FIXME TODO remove me for testing only
				//sleepForAWhile();

				dbhDirty.conn.commit();

			} catch (Throwable exc) {
				if (dbhDirty != null && dbhDirty.conn != null) {
					dbhDirty.conn.rollback();
					}

				FFSDebug.log(exc, "*** FundsAllocHandler.eventHandler failed:",
						PRINT_ERR);
				PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
                    throw new FFSException( exc.toString() );
			} finally {
				if (dbhDirty != null && dbhDirty.conn!= null) {
					DBUtil.freeConnection(dbhDirty.conn);
				}
				dbhDirty = null;
				}

        } else if ( eventSequence == 2 ) {    // LAST sequence
/*
			// FIXME TODO for testing remove me
			batchNum = 1;
			FFSDebug.log(methodName,
					"eventSeq : "+ eventSequence, ", batchNum: " +batchNum,
					FFSConst.PRINT_DEV);
*/

			String batchKey = getBatchKey(fiId);
			 String currency = BPWUtil.validateCurrencyString(null);
			FundsAllocInfo info = new FundsAllocInfo(fiId, "", "", "", "", "",currency,
                                                    "", //srvrTID,
					"", "", eventSequence, false, // possibleDuplicate false
					"", batchKey,null);
            _vec.add( info );

			// convert vector to array and call Backend interface
			FundsAllocInfo[] ar = (FundsAllocInfo[]) _vec
					.toArray(new FundsAllocInfo[0]);
			DBConnCache.bind(batchKey, dbh);
			FFSDebug
					.log(
							methodName,
							"Calling back-end to process funds allocation for end of schedule",
							PRINT_DEV);
            _fundsAllocator.addFundsVerification( ar );
			FFSDebug.log(methodName,
					"Back-end processing done for the current schedule",
					PRINT_DEV);

			// The following call of passing eventSequence=100 is not necessary
			// in this
			// version of BPW. It is here to maintain backward compatibility
			// with BPW4.5.3
			// Commit after sending the last data and
			// Call the backend for signalling done with committing the data
			dbh.conn.commit();
			info.eventSequence = 100; // eventSequence=100 is a signal of data
			// committed
			FundsAllocInfo[] ar2 = new FundsAllocInfo[1];
			ar2[0] = info;
            _fundsAllocator.addFundsVerification( ar2 );

			DBConnCache.unbind(batchKey);
			updateBatchKey(fiId); // added on 10/28 after testing
			_vec = null;
        } else if ( eventSequence == 3 ) {    // Batch-Start sequence
        } else if ( eventSequence == 4 ) {    // Batch-End sequence
			String batchKey = getBatchKey(fiId);

			// convert vector to array and call Backend interface
			FundsAllocInfo[] ar = (FundsAllocInfo[]) _vec
					.toArray(new FundsAllocInfo[0]);

            DBConnCache.bind(batchKey, dbh);

			FFSDebug.log(methodName,
							"Calling back-end to process funds allocation for end of batch",
							PRINT_DEV);
            _fundsAllocator.addFundsVerification( ar );
			FFSDebug.log(methodName,
							"Back-end processing done for the current batch",
							PRINT_DEV);

			DBConnCache.unbind(batchKey);
			_vec.clear();
		}

		FFSDebug.log(methodName, "Done .... ", PRINT_DEV);
		PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
	}

//=====================================================================
// resubmitEventHandler()
// Description: This method is called by the Scheduling engine during
//    event resubmission.  It will set the possibleDuplicate to true
//    before calling the ToBackend handler.
// Arguments: none
// Returns:      none
// Note:
//=====================================================================
	public void resubmitEventHandler(int eventSequence, EventInfoArray evts,
			FFSConnectionHolder dbh) throws Exception {
		String methodName = "FundAllocHandler.resubmitEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
		FFSDebug
				.log("==== FundsAllocHandler.resubmitEventHandler: begin, eventSeq="
						+ eventSequence + ",length=" + evts._array.length);
		String fiId = evts._array[0].FIId;

		BackendProvider backendProvider = getBackendProviderService();
		FundsAllocator _fundsAllocator = backendProvider.getFundsAllocatorInstance();

        if ( eventSequence == 0 ) {         // FIRST sequence
			_vec = new ArrayList();
			String batchKey = getNewBatchKey(fiId);
			FFSDebug.log("Got batchKey: [", batchKey, "], for entSeq: "
					+ eventSequence, PRINT_DEV);
			String currency = BPWUtil.validateCurrencyString(null);
			FundsAllocInfo info = new FundsAllocInfo(fiId, "", "", "", "", "",currency,
                                                    "", //srvrTID,
					"", "", eventSequence, true, // possibleDuplicate false
					"", batchKey,null);
            _vec.add( info );

			// convert vector to array and call Backend interface
			// ==FundsAllocInfo[] ar = (FundsAllocInfo[]) _vec.toArray( new
			// FundsAllocInfo[0] );
            //==FundsAllocator.addFundsVerification( ar );
            //==_vec.clear();
        } else if ( eventSequence == 1 ) {    // NORMAL sequence
			for (int i = 0; i < evts._array.length; i++) {
				String srvrTID = evts._array[i].InstructionID;
				FFSDebug
						.log("==== FundsAllocHandler.resubmitEventHandler: eventSeq="
								+ eventSequence + ",srvrTID=" + srvrTID);
				try {
					PmtInstruction pmt = PmtInstruction.getPmtInstr(srvrTID,
							dbh);
					if (pmt == null) {
						String msg = "*** FundsAllocHandler.resubmitEventHandler failed: could not find the SrvrTID="
								+ srvrTID + " in BPW_PmtInstruction";
						FFSDebug.console(msg);
						FFSDebug.log(msg);
						continue;
					}
					PmtInfo pmtinfo = pmt.getPmtInfo();
					PayeeInfo payeeinfo = Payee.findPayeeByID(pmtinfo.PayeeID,
							dbh);
					if (payeeinfo == null) {
						String msg = "*** FundsAllocHandler.resubmitEventHandler failed: could not find the PayeeID="
								+ pmtinfo.PayeeID
								+ " in BPW_Payee for pmt of SrvrTID=" + srvrTID;
						FFSDebug.console(msg);
						FFSDebug.log(msg);
						continue;
					}

					BankInfo binfo = BPWRegistryUtil
							.getBankInfo(pmtinfo.BankID);
					PropertyConfig propertyConfig = (PropertyConfig) FFSRegistry
							.lookup(BPWResource.PROPERTYCONFIG);
					String acctCreditId = null;
					if (binfo == null) {
						String checkingLevel = propertyConfig.otherProperties
								.getProperty(
										DBConsts.BANKINFO_LOOKUP_LEVEL,
										DBConsts.BPW_BANK_LOOKUP_LEVEL_STRICT);
						if (checkingLevel
								.equalsIgnoreCase(DBConsts.BPW_BANK_LOOKUP_LEVEL_STRICT)) {
                            String msg = "*** FundsAllocHandler.resubmitEventHandler failed:could not find the BankID in BPW_Bank, bpw.pmt.bankinfo.lookup.level = STRICT " ;
							FFSDebug.console(msg);
                            FFSDebug.log(msg,FFSConst.PRINT_ERR);
							throw new FFSException(msg);
						} else if (checkingLevel
								.equalsIgnoreCase(DBConsts.BPW_BANK_LOOKUP_LEVEL_LENIENT)) {
                            String msg = "*** FundsAllocHandler.resubmitEventHandler : bpw.pmt.bankinfo.lookup.level = LENIENT " ;
							// FFSDebug.console(msg);
                            FFSDebug.log(msg,FFSConst.PRINT_WRN);
						} else if (checkingLevel
								.equalsIgnoreCase(DBConsts.BPW_BANK_LOOKUP_LEVEL_NONE)) {
                            String msg = "*** FundsAllocHandler.resubmitEventHandler : bpw.pmt.bankinfo.lookup.level = NONE " ;
							// FFSDebug.console(msg);
                            FFSDebug.log(msg,FFSConst.PRINT_DEV);
						} else {
							// None of above type
							String msg = "*** FundsAllocHandler.resubmitEventHandler failed: Incorrect value for bpw.pmt.bankinfo.lookup.level ="
									+ checkingLevel;
							FFSDebug.console(msg);
							FFSDebug.log(msg);
							throw new Exception(msg);
						}
						acctCreditId = String.valueOf(-1);
					} else {
						acctCreditId = binfo.debitGLAcct;
					}
					//
					// assemble FundsAllocInfo
					//
					String batchKey = getBatchKey(fiId);
					FFSDebug.log("Got batchKey: [", batchKey, "], for pmt: ",
							srvrTID, ", for entSeq: " + eventSequence,
							PRINT_DEV);
					String currency = BPWUtil.validateCurrencyString( pmtinfo.CurDef);
					FundsAllocInfo info = new FundsAllocInfo(pmtinfo.FIID,
							pmtinfo.CustomerID, pmtinfo.BankID,
							pmtinfo.AcctDebitID, pmtinfo.AcctDebitType, pmtinfo
									.getAmt(),currency, srvrTID,
							pmtinfo.PayeeID, payeeinfo.PayeeName,
							eventSequence, true, // possibleDuplicate false
							acctCreditId, batchKey, pmtinfo.RecSrvrTID, // Server
							// transaction
							// ID of
							// parent
							// recurring
							// model
							pmtinfo // added to provide additional info
					// to backend for one step payment processing
					// Turoa release
					);

					// retrieve the extrainfo info from BPW_PmtExtraInfo
					// and assign it to extraFields of FundsAllocInfo
					if (pmtinfo.extraFields != null) {
						info.extraFields = pmtinfo.extraFields;
					}

					//
					// update PmtInstruction table with status
					//
                    PmtInstruction.updateStatus( dbh, srvrTID, INFUNDSALLOC );
                    //update history
					SrvrTrans.updatePmtStatus(dbh, srvrTID, WILLPROCESSON);
					//
					// cache the information
					//
                    _vec.add( info );
					// log into AuditLog
					if (propertyConfig.LogLevel >= AUDITLOGLEVEL_STATUSUPDATE) {
						int bizID = Integer.parseInt(pmtinfo.CustomerID);
						BigDecimal pmtAmount = BPWUtil.getBigDecimal(pmtinfo
								.getAmt());
						String debitAcct = BPWUtil.getAccountIDWithAccountType(
								pmtinfo.AcctDebitID, pmtinfo.AcctDebitType);
						ILocalizable msg = BPWLocaleUtil
								.getLocalizedMessage(
										AuditLogConsts.AUDITLOG_MSG_FUND_ALLOCATION_RESUBMIT,
										null,
										BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
                        TransAuditLog.logTransAuditLog(dbh.conn.getConnection(),
                                                       pmtinfo.submittedBy,
                                                       null,
                                                       null,
                                                       msg,
                                                       pmtinfo.LogID,
                                                       AuditLogTranTypes.BPW_RECFUNDTRN,
                                                       bizID,
                                                       pmtAmount,
                                                       pmtinfo.CurDef,
                                                       srvrTID,
                                                       INFUNDSALLOC,
                                                       pmtinfo.PayAcct,
                                                       null,
                                                       debitAcct,
                                                       pmtinfo.getBankID(),
                                                       0);
					}
                } catch ( Exception exc ) {
                	PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
                    throw new FFSException( exc.getMessage() );
				}
			}
        } else if ( eventSequence == 2 ) {    // LAST sequence            
			String batchKey = getBatchKey(fiId);
			FFSDebug.log("Got batchKey: [", batchKey, "]for entSeq: "
					+ eventSequence, PRINT_DEV);
			 String currency = BPWUtil.validateCurrencyString(null);
			FundsAllocInfo info = new FundsAllocInfo(fiId, "", "", "", "", "",currency,
                                                    "", //srvrTID,
					"", "", eventSequence, true, // possibleDuplicate false
					"", batchKey,null);
            _vec.add( info );

			// convert vector to array and call Backend interface
			FundsAllocInfo[] ar = (FundsAllocInfo[]) _vec
					.toArray(new FundsAllocInfo[0]);
			DBConnCache.bind(batchKey, dbh);

            _fundsAllocator.addFundsVerification( ar );

			// The following call of passing eventSequence=100 is not necessary
			// in this
			// version of BPW. It is here to maintain backward compatibility
			// with BPW4.5.3
			// Commit after sending the last data and
			// Call the backend for signalling done with committing the data
			dbh.conn.commit();
			info.eventSequence = 100; // eventSequence=100 is a signal of data
			// committed
			FundsAllocInfo[] ar2 = new FundsAllocInfo[1];
			ar2[0] = info;
            _fundsAllocator.addFundsVerification( ar2 );

			DBConnCache.unbind(batchKey);
			updateBatchKey(fiId); // added on 10/28 after testing
			_vec = null;
        } else if ( eventSequence == 3 ) {    // Batch-Start sequence
        } else if ( eventSequence == 4 ) {    // Batch-End sequence
			String batchKey = getBatchKey(fiId);

			FFSDebug.log("Got batchKey: [", batchKey, "], for entSeq: "
					+ eventSequence, PRINT_DEV);

			DBConnCache.bind(batchKey, dbh);

			// convert vector to array and call Backend interface
			FundsAllocInfo[] ar = (FundsAllocInfo[]) _vec
					.toArray(new FundsAllocInfo[0]);
            _fundsAllocator.addFundsVerification( ar );

			DBConnCache.unbind(batchKey);
			_vec.clear();
		}
		FFSDebug.log("==== FundsAllocHandler.resubmitEventHandler: end");
		PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
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

	/*

	//FIXME TODO Omarabi remove
	private void sleepForAWhile() {
		final String methodName = "FundsAllocHandler.sleepForAWhile: ";
		FFSDebug.log(methodName, ": starts....", FFSConst.PRINT_DEV);

		PropertyConfig propertyConfig = (PropertyConfig) FFSRegistry
		.lookup(BPWResource.PROPERTYCONFIG);


		String sleepBatch = propertyConfig.otherProperties.getProperty("sleepBatch", "3");
		FFSDebug.log(methodName, ": sleepBatch: ", sleepBatch, FFSConst.PRINT_DEV);
		int sleepBatchInt = Integer.parseInt(sleepBatch);

		if (batchNum != sleepBatchInt) {
			FFSDebug.log(methodName, " NO NEED TO SLEEP SINCE THIS IS BATCH #: " +
					batchNum, " WHILE WE SHOULD SLEEP AT BATCH #: ", sleepBatch,
					FFSConst.PRINT_DEV);
			return;
		}
		FFSDebug.log(methodName, " PREPARING TO SLEEP IN BATCH #: " +
				batchNum, FFSConst.PRINT_DEV);

		String sleepTime = propertyConfig.otherProperties.getProperty("sleepTime", "120");
		FFSDebug.log(methodName, ": sleepTime: ", sleepTime, FFSConst.PRINT_DEV);
		int sleepFor = Integer.parseInt(sleepTime);
		try {
			FFSDebug.log(methodName, ": Sleeping for: " + sleepFor,
					", Time: " + new java.util.Date(), FFSConst.PRINT_DEV);
			Thread.sleep(sleepFor *1000);
			FFSDebug.log(methodName, ": Wake up from Sleeping for: " + sleepFor,
					", Time: " + new java.util.Date(), FFSConst.PRINT_DEV);

		} catch (InterruptedException e) {
			FFSDebug.log(e, methodName + ": Failed to sleep for for: " + sleepTime +
					", Time: " + new java.util.Date(), FFSConst.PRINT_DEV);
			e.printStackTrace();
		}
		FFSDebug.log(methodName, ": Done....", FFSConst.PRINT_DEV);
	}

	*/
}
