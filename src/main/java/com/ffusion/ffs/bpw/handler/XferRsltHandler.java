//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2001.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.ffusion.beans.DateTime;
import com.ffusion.beans.SecureUser;
import com.ffusion.beans.accounts.Account;
import com.ffusion.beans.banking.Transaction;
import com.ffusion.beans.banking.TransactionTypes;
import com.ffusion.beans.banking.Transactions;
import com.ffusion.beans.util.StringUtil;
import com.ffusion.ffs.bpw.custimpl.interfaces.BackendProvider;
import com.ffusion.ffs.bpw.custimpl.interfaces.TransactionStatusProvider;
import com.ffusion.ffs.bpw.custimpl.interfaces.XferResult;
import com.ffusion.ffs.bpw.db.DBConnCache;
import com.ffusion.ffs.bpw.db.XferInstruction;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfo;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.IntraTrnInfo;
import com.ffusion.ffs.bpw.interfaces.IntraTrnRslt;
import com.ffusion.ffs.bpw.interfaces.TransferStatus;
import com.ffusion.ffs.bpw.master.BPWExternalProcessor;
import com.ffusion.ffs.bpw.util.BPWUtil;
import com.ffusion.ffs.bpw.util.TransactionAlertUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.scheduling.constants.ScheduleConstants;
import com.ffusion.ffs.scheduling.db.Event;
import com.ffusion.ffs.scheduling.db.EventInfoLog;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.sap.banking.common.bo.interfaces.CommonConfig;
import com.sap.banking.common.constants.ConfigConstants;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

//=====================================================================
//
// This class contains a callback handler that is registered in the 
// InstructionType table.  The registered callback handler will be called
// by the Scheduling engine for the IntraTrn Result Processing.
//
//=====================================================================

public class XferRsltHandler implements com.ffusion.ffs.bpw.interfaces.DBConsts, FFSConst,
		com.ffusion.ffs.bpw.interfaces.BPWResource, BPWScheduleHandler {
	private XferResult _xferRslt = null;
	private boolean _okToCall;

	// Reference to the TransferStatus instance
	private transient TransferStatus transferStatusRef = null;

	public XferRsltHandler() {
		try {
			BackendProvider backendProvider = getBackendProviderService();
			_xferRslt = backendProvider.getXferResultInstance();
		} catch (Exception ex) {
			FFSDebug.log(ex, "Unable to get XferResult Instance", PRINT_ERR);
		}

		try {
			TransactionStatusProvider transactionStatusProvider = getTransactionStatusProviderService();
			transferStatusRef = transactionStatusProvider.getTransferStatusInstance();
		} catch (Exception ex) {
			FFSDebug.log(ex, "Unable to get TransferStatus Instance", PRINT_ERR);
		}
	}

	// =====================================================================
	// eventHandler()
	// Description: This method is called by the Scheduling engine
	// Arguments: none
	// Returns: none
	// Note:
	// =====================================================================
	public void eventHandler(int eventSequence, EventInfoArray evts, FFSConnectionHolder dbh) throws Exception {
		String thisMethod = "XferRsltHandler.eventHandler:";
		long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(thisMethod,start);
		FFSDebug.log(thisMethod + " begin, eventSeq=" + eventSequence, PRINT_DEV);
		if (eventSequence == 0) { // FIRST sequence
			_okToCall = false;
		} else if (eventSequence == 1) { // NORMAL sequence
			_okToCall = true;
		} else if (eventSequence == 2) { // LAST sequence
			if (_okToCall && evts != null && evts._array[0] != null) {
				DBConnCache dbConnCache = (DBConnCache) FFSRegistry.lookup(DBCONNCACHE);
				String batchKey = DBConnCache.save(dbh);
				int fileBasedRecovery = evts._array[0].fileBasedRecovery;
				// If schedule is enable for file based recovery
				if (fileBasedRecovery == 1) {
					// Tell the backend which FIID owns this schedule
					ArrayList fiIdList = new ArrayList();
					fiIdList.add(evts._array[0].FIId);
					BPWUtil.setFIIDList(fiIdList, _xferRslt);
					_xferRslt.ProcessXferRslt(batchKey);
				} else if (fileBasedRecovery == 0) {
					updateTransactionStatus(evts, dbh, batchKey);
				}

		        // Remove the binding of the db connection and the batch key.
		        DBConnCache.unbind(batchKey);
			}
		} else if (eventSequence == 3) { // Batch-Start sequence
		} else if (eventSequence == 4) { // Batch-End sequence
		}
		FFSDebug.log(thisMethod + " end", PRINT_DEV);
		PerfLoggerUtil.stopPerfLogging(thisMethod, start, uniqueIndex);
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
		String thisMethod = "XferRsltHandler.resubmitEventHandler:";
		long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(thisMethod,start);
		FFSDebug.log(thisMethod +" begin, eventSeq=" + eventSequence, PRINT_DEV);
		if (eventSequence == 0) { // FIRST sequence
			_okToCall = false;
		} else if (eventSequence == 1) { // NORMAL sequence
			_okToCall = true;
		} else if (eventSequence == 2) { // LAST sequence
			if (_okToCall && evts != null && evts._array[0] != null) {
				int fileBasedRecovery = evts._array[0].fileBasedRecovery;
				
				DBConnCache dbConnCache = (DBConnCache) FFSRegistry.lookup(DBCONNCACHE);
				String batchKey = DBConnCache.save(dbh);
				
				// If schedule is enable for file based recovery
				if (fileBasedRecovery == 1) {
					// Tell the backend which FIID owns this schedule
					ArrayList fiIdList = new ArrayList();
					fiIdList.add(evts._array[0].FIId);
					BPWUtil.setFIIDList(fiIdList, _xferRslt);

					_xferRslt.ProcessXferRslt(batchKey);
				} else if (fileBasedRecovery == 0) {
					updateTransactionStatus(evts, dbh, batchKey);
				}
				// Remove the binding of the db connection and the batch key.
		        DBConnCache.unbind(batchKey);
			}
		} else if (eventSequence == 3) { // Batch-Start sequence
		} else if (eventSequence == 4) { // Batch-End sequence
		}
		FFSDebug.log("==== XferRsltHandler.resubmitEventHandler: end");
		PerfLoggerUtil.stopPerfLogging(thisMethod, start, uniqueIndex);
		
	}

	/**
	 * Gets the backend provider service instance.
	 *
	 * @return the backend provider service instance
	 */
	protected BackendProvider getBackendProviderService() throws Exception{
		String thisMethod = "getBackendProviderService";
		BackendProvider backendProvider = null;
		Object obj = OSGIUtil.getBean("backendProviderServices");
		String backendType = null;
		if (obj != null && obj instanceof List) {
			List<BackendProvider> backendProviders = (List<BackendProvider>) obj;

			// get backend type property from config_master
			com.sap.banking.common.bo.interfaces.CommonConfig commonConfigBO = (com.sap.banking.common.bo.interfaces.CommonConfig) OSGIUtil
					.getBean(com.sap.banking.common.bo.interfaces.CommonConfig.class);
			
			try {
				backendType = commonConfigBO.getBankingBackendType();
			} catch (Exception e) {
				FFSDebug.log(thisMethod + "Error getting backenType", e);
			}

			// iterate through the list of service refs and return ref based on
			// configuration
			if (!backendProviders.isEmpty()) {
				Iterator<BackendProvider> iteratorBackendProvider = backendProviders.iterator();
				while (iteratorBackendProvider.hasNext()) {
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

	/**
	 * Gets the transaction status provider service instance.
	 * 
	 * @return the transaction status provider service instance
	 */
	protected TransactionStatusProvider getTransactionStatusProviderService() throws Exception{
		String thisMethod = "getTransactionStatusProviderService";
		TransactionStatusProvider transactionStatusProvider = null;
		Object obj = OSGIUtil.getBean("transactionStatusProviderService");
		String backendType = null;
		if (obj != null && obj instanceof List) {
			List<TransactionStatusProvider> transactionStatusProviderList = (List<TransactionStatusProvider>) obj;

			// get backend type property from config_master
			com.sap.banking.common.bo.interfaces.CommonConfig commonConfigBO = (com.sap.banking.common.bo.interfaces.CommonConfig) OSGIUtil
					.getBean(com.sap.banking.common.bo.interfaces.CommonConfig.class);
			
			try {
				backendType = commonConfigBO.getBankingBackendType();
			} catch (Exception e) {
				FFSDebug.log(thisMethod + "Error getting backenType", e);
			}

			// iterate through the list of service refs and return ref based on
			// configuration
			if (!transactionStatusProviderList.isEmpty()) {
				Iterator<TransactionStatusProvider> itTransactionStatusProvider = transactionStatusProviderList
						.iterator();
				while (itTransactionStatusProvider.hasNext()) {
					TransactionStatusProvider transactionStatusProviderRef = itTransactionStatusProvider.next();
					if (backendType != null && backendType.equals(transactionStatusProviderRef.getBankingBackendType())) {
						transactionStatusProvider = transactionStatusProviderRef;
						break;
					}
				}
			}
		}
		if(transactionStatusProvider == null) {
			FFSDebug.log("Invalid backend type." + backendType, FFSConst.PRINT_ERR);
			throw new Exception("Invalid backend type." + backendType);
		}
		return transactionStatusProvider;
	}
	
	/**
	 * Update transaction result returned by the back end
	 * @param evts EventInfoArray
	 * @param dbh FFSConnectionHolder with DB connection
	 * @throws Exception
	 */
	protected void updateTransactionStatus(EventInfoArray evts, FFSConnectionHolder dbh, String batchKey) throws Exception {
		String thisMethod = "XferRsltHandler.updateTransactionStatus: ";
		
		com.sap.banking.common.bo.interfaces.CommonConfig commonConfigBO = (com.sap.banking.common.bo.interfaces.CommonConfig) OSGIUtil
				.getBean(com.sap.banking.common.bo.interfaces.CommonConfig.class);
		
		// Return configured system user ID, which is used for read audit logging in BaS
		int systemUserID = getSystemUserID(commonConfigBO);
		
		// Get the BaS status
		boolean isBaSBackendEnabled = commonConfigBO.isBasBackendEnabled();

		// Retrieve eventInfo details for the internal transfer
		// which transaction status is not yet received.
		// String[] instTypes = { DBConsts.INTRATRN };
		String instTypes = DBConsts.INTRATRN;
		List<EventInfo> eventinfoList = Event.retrieveEventInfoList(dbh, ScheduleConstants.EVT_STATUS_INPROCESS,
				evts._array[0].FIId, instTypes, true);
		try {
			while (eventinfoList != null && !eventinfoList.isEmpty()) {
				FFSDebug.log(thisMethod + "Start internal transfer result handler batch.....");
				// Create IntraTrnInfo collection to retrieve the status
				// from back end which is not received.
				List<IntraTrnInfo> intraTrnInfoList = new ArrayList<>();
				IntraTrnInfo intraTrnInfo = null;
				for (EventInfo evtInfo : eventinfoList) {
					intraTrnInfo = XferInstruction.getIntraById(evtInfo.InstructionID, dbh);
	
					// Set user type to system, as schedule is initiated
					// by the system.
					intraTrnInfo.setPostingUserType(SecureUser.TYPE_SYSTEM);
	
					// Set system user Id in IntraTrnInfo object which
					// is used by banking services for read audit
					// logging.
					intraTrnInfo.setPostingUserId(systemUserID);
					
					// Set batch key
					intraTrnInfo.batchKey = batchKey;
	
					intraTrnInfoList.add(intraTrnInfo);
				}
	
				if (!intraTrnInfoList.isEmpty()) {
					try {
						IntraTrnInfo[] intraTrnInfos = intraTrnInfoList.toArray(new IntraTrnInfo[intraTrnInfoList.size()]);
						
						// Get the transaction status from back end
						IntraTrnRslt[] intraTrnRsltArray = transferStatusRef.getTransferStatus(intraTrnInfos, new HashMap<String, Object>());
						
						if (isBaSBackendEnabled) {
							processBaSTransferResult(evts, dbh, intraTrnRsltArray, batchKey);
						} else {
							// Process transaction result returned by the back end
							BPWExternalProcessor bpw = new BPWExternalProcessor();
							bpw.processIntraTrnRslt(intraTrnRsltArray);
							
							// Create event info log for the transfer, which status received from the back end.
							createEventInfoLog(dbh, intraTrnRsltArray, evts._array[0]);
						}

						// Commit the batch processing changes
						dbh.conn.commit();
					} catch (Exception ex) {
						FFSDebug.log(thisMethod + "Error in updating transaction results", ex);
					}
				}
				
				FFSDebug.log(thisMethod + "End internal transfer result handler batch.....");
				if (Event.isBatchDone(evts._array[0].FIId, instTypes)) {
					eventinfoList = null;
				} else {
					eventinfoList = Event.retrieveEventInfoList(dbh, ScheduleConstants.EVT_STATUS_INPROCESS,
							evts._array[0].FIId, instTypes, true);
				}
			}
		} finally {
			Event.clearBatch(evts._array[0].FIId, instTypes);
		} 
	}

	/** Return configured system user ID, which is used for read audit logging in BaS
	 * @param commonConfigBO 
	 * @return int	System User ID
	 */
	private int getSystemUserID(CommonConfig commonConfigBO) {
		// Read system user id from configuration property. This
		// value will be use by banking services for read audit
		// logging.
		int systemUserID = -1;
		try {
		String userID = commonConfigBO.getConfigProperty(ConfigConstants.SYSTEM_USER_ID);
		if (StringUtil.isNotEmpty(userID)) {

				systemUserID = Integer.parseInt(userID);
			} 
		} catch (NumberFormatException ex) {
			FFSDebug.log("Invalid user Id", ex);
		} catch (Exception ex) {
			FFSDebug.log("Cannot get System user Id", ex);
		}
		return systemUserID;
	}

	/**
	 * Create event log entry for payment
	 * @param dbh	FFSConnectionHolder with DB connection
	 * @param pmtTrnRslt	Payment result object
	 * @param eventInfo	EventInfo
	 * @throws Exception
	 */
	private void createEventInfoLog(FFSConnectionHolder dbh, IntraTrnRslt[] intraTrnRslt, EventInfo eventInfo) throws Exception{
		EventInfo evt = null;
		for (int idx = 0; idx < intraTrnRslt.length; idx++) {
			evt = new EventInfo();
			evt.EventID = com.ffusion.ffs.bpw.db.DBUtil.getNextIndexString(com.ffusion.ffs.bpw.interfaces.DBConsts.EVENTID);
			evt.FIId = eventInfo.FIId;
			evt.InstructionID = intraTrnRslt[idx].srvrTid;
			evt.InstructionType = eventInfo.InstructionType;
			evt.LogID = eventInfo.LogID;
			evt.processId = eventInfo.processId;
			EventInfoLog.createEventInfoLog(dbh, evt);
		}
	}
	
	/**
	 * This API perform below tasks,
	 * 1.	If the internal transfer is received by payment engine but not yet processed then Payment Engine will send the result with �in-process� status and confirmation number. 
	 * 		In this case, OCB will update only confirmation number.
	 * 2.	If the Payment engine has processed the internal transfer then OCB process internal transfer results in BPW.
	 * 3.	OCB send immediate transaction alert if internal transfer is successfully processed.
	 * 
	 * @param evts EventInfoArray
	 * @param dbh FFSConnectionHolder with DB connection
	 * @param intraTrnRsltArray
	 *            array of internal transfer result objects
	 * @param batchKey
	 *            DB batch key
	 * @return IntraTrnRslt[] Filtered transactions which are processed by the
	 *         payment engine. either success or failure.
	 * @throws Exception
	 */
	private void processBaSTransferResult(EventInfoArray evts, FFSConnectionHolder dbh, IntraTrnRslt[] intraTrnRsltArray, String batchKey)
			throws Exception {
		String thismethod = "XferRsltHandler.processBaSTransferResult : ";

		com.sap.banking.bpw.transfer.interfaces.InternalTransferResultProcessor internalTransferResultProcessorRef = (com.sap.banking.bpw.transfer.interfaces.InternalTransferResultProcessor) OSGIUtil
				.getBean(com.sap.banking.bpw.transfer.interfaces.InternalTransferResultProcessor.class);

		List<IntraTrnRslt> intraTrnRsltList = new ArrayList<>();
		IntraTrnRslt intraTrnRslt = null;
		for (int idx = 0; idx < intraTrnRsltArray.length; idx++) {
			intraTrnRslt = intraTrnRsltArray[idx];
			if (intraTrnRslt != null) {
				intraTrnRslt.batchKey = batchKey;
				// Remove in-process transaction from result list, BPW only
				// process valid transaction status. e.g. success or failure
				if (DBConsts.STATUS_INPROCESS == intraTrnRslt.status) {
					try {
						// update transfer confirmation number to database
						internalTransferResultProcessorRef.updateTransferConfirmationNumber(intraTrnRslt);
					} catch (Exception ex) {
						FFSDebug.log(thismethod + "Failed to update confirmation number for srvrTid = "
								+ intraTrnRslt.srvrTid, ex);
						throw ex;
					}
				} else if ((DBConsts.STATUS_OK == intraTrnRslt.status)
						|| (DBConsts.STATUS_GENERAL_ERROR == intraTrnRslt.status)
						|| (DBConsts.STATUS_INSUFFICIENT_FUNDS == intraTrnRslt.status)) {
					intraTrnRsltList.add(intraTrnRslt);
				}
			}
		}

		// If Payment engine processed the internal transfer then process result in BPW.
		if (!intraTrnRsltList.isEmpty()) {
			IntraTrnRslt[] rsltArray = intraTrnRsltList.toArray(new IntraTrnRslt[intraTrnRsltList.size()]);
			
			// Process transaction result returned by the back end
			BPWExternalProcessor bpw = new BPWExternalProcessor();
			bpw.processIntraTrnRslt(rsltArray);
			
			// Create event info log for the transfer, which status received from the back end.
			createEventInfoLog(dbh, rsltArray, evts._array[0]);
			
			sendImmediateTransactionAlert(dbh, rsltArray);
		}
	}

	/**
	 * Send immediate transaction alerts to the subscribed user
	 * 
	 * @param dbh
	 *            FFSConnectionHolder with DB connection
	 * @param rsltArray
	 *            array of internal transfer result objects
	 */
	private void sendImmediateTransactionAlert(FFSConnectionHolder dbh, IntraTrnRslt[] rsltArray) {
		String thismethod = "XferRsltHandler.sendImmediateTransactionAlert : ";
		XferInstruction xferInfo = null;
		Transactions transactions = null;
		SecureUser sUser = new SecureUser();
		String memo = "";
		HashMap extraDBMap = null;
		
		for(int idx=0; idx<rsltArray.length;idx++) {
			try {
				xferInfo = XferInstruction.getXferInstruction(dbh, rsltArray[idx].srvrTid);
				// Send immediate transaction alerts to the subscribed user, if the internal transfer is successful.
				if(xferInfo != null && DBConsts.POSTEDON.equals(xferInfo.Status)) {
					extraDBMap = (HashMap) xferInfo.extraFields;
					if (extraDBMap != null && !extraDBMap.isEmpty()) {
						memo = (String) extraDBMap.get(DBConsts.BPW_TRANSFER_MEMO);
					}
					
					transactions = new Transactions();
					
					// Set from account transaction alerts details
					Transaction fromAcctTrans = transactions.create();
					fromAcctTrans.setAmount(xferInfo.Amount);
					Account fromAcct = new Account();
					String acctDebitType = String.valueOf(BPWUtil.getAccountType(xferInfo.AcctDebitType));
					fromAcct.setID(xferInfo.AcctDebitID, acctDebitType);
					fromAcct.setDirectoryID(xferInfo.SubmittedBy);
					fromAcct.setCurrencyCode(xferInfo.CurDef);
					fromAcctTrans.setAccount(fromAcct);
					fromAcctTrans.setDescription(memo);
					fromAcctTrans.setPostingDate(new DateTime());
					fromAcctTrans.setType(TransactionTypes.TYPE_DEBIT);

					// Set To account transaction alerts details
					Transaction toAcctTrans = transactions.create();
					toAcctTrans.setAmount(xferInfo.Amount);
					Account toAcct = new Account();
					String acctCreditType = String.valueOf(BPWUtil.getAccountType(xferInfo.AcctCreditType));
					toAcct.setID(xferInfo.AcctCreditID, acctCreditType);
					toAcct.setDirectoryID(xferInfo.SubmittedBy);
					toAcct.setCurrencyCode(xferInfo.CurDef);
					toAcctTrans.setAccount(toAcct);
					toAcctTrans.setDescription(memo);
					toAcctTrans.setPostingDate(new DateTime());
					toAcctTrans.setType(TransactionTypes.TYPE_CREDIT);

					sUser.setProfileID(xferInfo.SubmittedBy);
					Map<String, Object> extraMap = new HashMap<>();
					TransactionAlertUtil.sendImmediateTransactionAlert(sUser, transactions, extraMap);
				}
			} catch (Exception ex) {
				FFSDebug.log(thismethod + " Error in sending transaction alert for srvrTID :  " + rsltArray[idx].srvrTid);
				FFSDebug.log(thismethod + ex);
			}
		}
	}
}
