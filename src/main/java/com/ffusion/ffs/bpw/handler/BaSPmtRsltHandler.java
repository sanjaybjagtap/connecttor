package com.ffusion.ffs.bpw.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.ffusion.beans.DateTime;
import com.ffusion.beans.SecureUser;
import com.ffusion.beans.accounts.Account;
import com.ffusion.beans.banking.Transaction;
import com.ffusion.beans.banking.TransactionTypes;
import com.ffusion.beans.banking.Transactions;
import com.ffusion.beans.util.StringUtil;
import com.ffusion.ffs.bpw.custimpl.interfaces.TransactionStatusProvider;
import com.ffusion.ffs.bpw.db.DBConnCache;
import com.ffusion.ffs.bpw.db.PmtInstruction;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfo;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.PaymentStatus;
import com.ffusion.ffs.bpw.interfaces.PmtInfo;
import com.ffusion.ffs.bpw.interfaces.PmtTrnRslt;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
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

public class BaSPmtRsltHandler implements BPWScheduleHandler, FFSConst, BPWResource {

	private boolean _okToCall;

	// Reference to the PaymentStatus instance
	private transient PaymentStatus paymentStatusRef = null;
	
	// Configuration properties from BPW_PropertyConfig table
	private final PropertyConfig _propertyConfig;
	
	// supported instruction types to retrieve events 
	private final String instTypeValues;

	public BaSPmtRsltHandler() {
		_propertyConfig = (PropertyConfig) FFSRegistry.lookup(BPWResource.PROPERTYCONFIG);
		instTypeValues = _propertyConfig.otherProperties.getProperty(DBConsts.BILLPAY_INSTRUCTION_TYPES, null);
		try {
			TransactionStatusProvider transactionStatusProvider = getTransactionStatusProviderService();
			paymentStatusRef = transactionStatusProvider.getPaymentStatusInstance();
		} catch (Exception ex) {
			FFSDebug.log(ex, "Unable to get TransferStatus Instance", PRINT_ERR);
		}
	}

	@Override
	public void eventHandler(int eventSequence, EventInfoArray evts, FFSConnectionHolder dbh) throws Exception {
		String thisMethod = "BaSPmtRsltHandler.eventHandler: ";
		long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(thisMethod,start);
		FFSDebug.log(thisMethod + "begin, eventSeq=" + eventSequence, PRINT_DEV);
		if (eventSequence == 0) { // FIRST sequence
			_okToCall = false;
		} else if (eventSequence == 1) { // NORMAL sequence
			_okToCall = true;
		} else if (eventSequence == 2 && _okToCall && evts != null && evts._array[0] != null) { // LAST sequence
			DBConnCache dbConnCache = (DBConnCache) FFSRegistry.lookup(DBCONNCACHE);
			String batchKey = DBConnCache.save(dbh);
			int fileBasedRecovery = evts._array[0].fileBasedRecovery;
			if (fileBasedRecovery == 0) {
				updatePaymentStatus(evts, dbh, batchKey);
			}
			// Remove the binding of the db connection and the batch key.
	        DBConnCache.unbind(batchKey);
		} else if (eventSequence == 3) { // Batch-Start sequence
		} else if (eventSequence == 4) { // Batch-End sequence
		}
		FFSDebug.log(thisMethod + "end", PRINT_DEV);
		PerfLoggerUtil.stopPerfLogging(thisMethod, start, uniqueIndex);
	}

	@Override
	public void resubmitEventHandler(int eventSequence, EventInfoArray evts, FFSConnectionHolder dbh) throws Exception {
		String thisMethod = "BaSPmtRsltHandler.resubmitEventHandler: ";
		long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(thisMethod,start);
		FFSDebug.log(thisMethod + "begin, eventSeq=" + eventSequence, PRINT_DEV);
		if (eventSequence == 0) { // FIRST sequence
			_okToCall = false;
		} else if (eventSequence == 1) { // NORMAL sequence
			_okToCall = true;
		} else if (eventSequence == 2 && _okToCall) { // LAST sequence
			eventHandler(eventSequence, evts, dbh);
		} else if (eventSequence == 3) { // Batch-Start sequence
		} else if (eventSequence == 4) { // Batch-End sequence
		}
		FFSDebug.log(thisMethod + "end", PRINT_DEV);
		PerfLoggerUtil.stopPerfLogging(thisMethod, start, uniqueIndex);
	}

	/**
	 * Gets the transaction status provider service instance.
	 * 
	 * @return the transaction status provider service instance
	 */
	protected TransactionStatusProvider getTransactionStatusProviderService() throws Exception{
		TransactionStatusProvider transactionStatusProvider = null;
		Object obj = OSGIUtil.getBean("transactionStatusProviderService");
		String backendType = null;
		if (obj != null && obj instanceof List) {
			List<TransactionStatusProvider> transactionStatusProviderList = (List<TransactionStatusProvider>) obj;

			// get backend type property from config_master
			com.sap.banking.common.bo.interfaces.CommonConfig commonConfigBO = (com.sap.banking.common.bo.interfaces.CommonConfig) OSGIUtil
					.getBean(com.sap.banking.common.bo.interfaces.CommonConfig.class);
			backendType = getBankingBackendType(commonConfigBO);

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
			if(transactionStatusProvider == null) {
				FFSDebug.log("Invalid backend type." + backendType, FFSConst.PRINT_ERR);
				throw new Exception("Invalid backend type." + backendType);
			}
		}
		return transactionStatusProvider;
	}
	
	/**
	 * Update payment status from the back end
	 * @param evts EventInfoArray
	 * @param dbh FFSConnectionHolder with DB connection
	 * @throws Exception
	 */
	protected void updatePaymentStatus(EventInfoArray evts, FFSConnectionHolder dbh, String batchKey) throws Exception {
		String thisMethod = "BaSPmtRsltHandler.updatePaymentStatus: ";
		
		com.sap.banking.common.bo.interfaces.CommonConfig commonConfigBO = (com.sap.banking.common.bo.interfaces.CommonConfig) OSGIUtil
				.getBean(com.sap.banking.common.bo.interfaces.CommonConfig.class);
		
		// Return configured system user ID, which is used for read audit logging in BaS
		int systemUserID = getSystemUserID(commonConfigBO);
		
		// Get the BaS status
		boolean isBaSBackendEnabled = commonConfigBO.isBasBackendEnabled(); 

		// Retrieve eventInfo details for the bill payment
		// which transaction status is not yet received.
		List<EventInfo> eventinfoList = Event.retrieveEventInfoList(dbh,
				ScheduleConstants.EVT_STATUS_INPROCESS, evts._array[0].FIId, instTypeValues, true);

		try {
			while (eventinfoList != null && !eventinfoList.isEmpty()) {
				FFSDebug.log(thisMethod + "Start bill payment result handler batch.....");
				// Create PmtInfo collection to retrieve the status
				// from back end which is not received.
				List<PmtInfo> pmtInfoList = new ArrayList<>();
				PmtInfo pmtInfo = null;
				for (EventInfo evtInfo : eventinfoList) {
					pmtInfo = PmtInstruction.getPmtInfo(evtInfo.InstructionID, dbh);
		
					// Set user type to system, as schedule is initiated
					// by the system.
					pmtInfo.setPostingUserType(SecureUser.TYPE_SYSTEM);
		
					// Set system user Id in PmtInfo object which
					// is used by banking services for read audit
					// logging.
					pmtInfo.setPostingUserId(systemUserID);
		
					// Set batch key
					pmtInfo.setBatchKey(batchKey);
					
					pmtInfoList.add(pmtInfo);
				}
		
				if (!pmtInfoList.isEmpty()) {
					try {
						PmtInfo[] pmtInfos = pmtInfoList.toArray(new PmtInfo[pmtInfoList.size()]);
						PmtTrnRslt[] pmtTrnRsltArray = paymentStatusRef.getPaymentStatus(pmtInfos,
								new HashMap<String, Object>());

						if (pmtTrnRsltArray != null && pmtTrnRsltArray.length > 0) {
							if (isBaSBackendEnabled) {
								processBaSPaymentResult(evts, dbh, pmtTrnRsltArray, batchKey);
							} else {
								// Create an instance of the
								// BPWExternalProcessor class and push the
								// results.
								BPWExternalProcessor bpw = new BPWExternalProcessor();
								bpw.processPmtTrnRslt(pmtTrnRsltArray);
								
								// Create event info log for the payments, which status received from the back end.
								createEventInfoLog(dbh, pmtTrnRsltArray, evts._array[0]);
							}
							// Commit the batch processing changes
							dbh.conn.commit();
						}
					} catch (Exception ex) {
						FFSDebug.log(thisMethod + "Error in updating bill payment result", ex);
					}
				}
				
				FFSDebug.log(thisMethod + "End bill payment result handler batch.....");
				
				if (Event.isBatchDone(evts._array[0].FIId, instTypeValues)) {
					eventinfoList = null;
				} else {
					eventinfoList = Event.retrieveEventInfoList(dbh, ScheduleConstants.EVT_STATUS_INPROCESS,
							evts._array[0].FIId, instTypeValues, true);
				}
			}
		} finally {
			Event.clearBatch(evts._array[0].FIId, instTypeValues);
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
		String userID = null;
		try {
			userID = commonConfigBO.getConfigProperty(ConfigConstants.SYSTEM_USER_ID);
		} catch (Exception e) {
			FFSDebug.log("Invalid user Id", e);
		}
		int systemUserID = -1;
		if (StringUtil.isNotEmpty(userID)) {
			try {
				systemUserID = Integer.parseInt(userID);
			} catch (NumberFormatException ex) {
				FFSDebug.log("Invalid user Id", ex);
			}
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
	private void createEventInfoLog(FFSConnectionHolder dbh, PmtTrnRslt[] pmtTrnRslt, EventInfo eventInfo) throws Exception{
		EventInfo evt = null;
		for (int idx = 0; idx < pmtTrnRslt.length; idx++) {
			evt = new EventInfo();
			evt.EventID = com.ffusion.ffs.bpw.db.DBUtil.getNextIndexString(com.ffusion.ffs.bpw.interfaces.DBConsts.EVENTID);
			evt.FIId = eventInfo.FIId;
			evt.InstructionID = pmtTrnRslt[idx].srvrTid;
			evt.InstructionType = eventInfo.InstructionType;
			evt.LogID = eventInfo.LogID;
			evt.processId = eventInfo.processId;
			EventInfoLog.createEventInfoLog(dbh, evt);
		}
	}
	
	/**
	 * This API perform below tasks,
	 * 1.	If the payment is received by payment engine but not yet processed then Payment Engine will send the result with 'in-process' status and confirmation number.
	 * 		In this case, OCB will update only confirmation number.
	 * 2.	If the Payment engine has processed the payments then OCB process payment results in BPW.
	 * 3.	OCB send immediate transaction alert if payment is successfully processed.
	 * 
	 * @param evts EventInfoArray
	 * @param dbh FFSConnectionHolder with DB connection
	 * @param pmtTrnRsltArray
	 *            array of bill payment result objects
	 * @param batchKey
	 *            DB batch key
	 * @return PmtTrnRslt[] Filtered transactions which are processed by the
	 *         payment engine. either success or failure.
	 * @throws Exception
	 */
	private void processBaSPaymentResult(EventInfoArray evts, FFSConnectionHolder dbh, PmtTrnRslt[] pmtTrnRsltArray, String batchKey) throws Exception {
		String thismethod = "BaSPmtRsltHandler.processBaSPaymentResult : ";

		com.sap.banking.bpw.billpay.interfaces.BillPayResultProcessor billPayResultProcessorRef = (com.sap.banking.bpw.billpay.interfaces.BillPayResultProcessor) OSGIUtil
				.getBean(com.sap.banking.bpw.billpay.interfaces.BillPayResultProcessor.class);
		List<PmtTrnRslt> pmtTrnRsltList = new ArrayList<>();

		PmtTrnRslt pmtTrnRslt = null;
		for (int idx = 0; idx < pmtTrnRsltArray.length; idx++) {
			pmtTrnRslt = pmtTrnRsltArray[idx];
			if (pmtTrnRslt != null) {
				pmtTrnRslt.batchKey = batchKey;
				// Remove in-process transaction from result list, BPW only
				// process valid transaction status. e.g. success or failure
				if (DBConsts.STATUS_INPROCESS == pmtTrnRslt.status) {
					try {
						// update payment confirmation number to database
						billPayResultProcessorRef.updateBillPayConfirmationNumber(pmtTrnRslt);
					} catch (Exception ex) {
						FFSDebug.log(thismethod + "Failed to update confirmation number for srvrTid = "
								+ pmtTrnRslt.srvrTid, ex);
						throw ex;
					}
				} else if ((DBConsts.STATUS_OK == pmtTrnRslt.status)
						|| (DBConsts.STATUS_GENERAL_ERROR == pmtTrnRslt.status)) {
					pmtTrnRsltList.add(pmtTrnRslt);
				}
			}
		}
		
		// If Payment engine processed the bill payments then process result in BPW.
		if (!pmtTrnRsltList.isEmpty()) {
			PmtTrnRslt[] rsltArray = pmtTrnRsltList.toArray(new PmtTrnRslt[pmtTrnRsltList.size()]);
			
			BPWExternalProcessor bpw = new BPWExternalProcessor();
			bpw.processPmtTrnRslt(rsltArray);
			
			// Create event info log for the payments, which status received from the back end.
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
	 *            array of bill payment result objects
	 */
	private void sendImmediateTransactionAlert(FFSConnectionHolder dbh, PmtTrnRslt[] rsltArray){
		String thismethod = "BaSPmtRsltHandler.sendImmediateTransactionAlert : ";
		PmtInfo pmtInfo = null;
		Transactions transactions = null;
		SecureUser sUser = new SecureUser();
		
		for(int idx=0; idx<rsltArray.length;idx++) {
			try {
				pmtInfo = PmtInstruction.getPmtInfo(rsltArray[idx].srvrTid, dbh);
				// Send immediate transaction alerts to user if payment is successful.
				if (pmtInfo != null && DBConsts.PROCESSEDON.equals(pmtInfo.getStatus())) {
					transactions = new Transactions();
	
					// Set from account transaction alerts details
					Transaction fromAcctTrans = transactions.create();
					fromAcctTrans.setAmount(pmtInfo.getAmt());
					fromAcctTrans.setDescription(pmtInfo.getMemo());
					fromAcctTrans.setPostingDate(new DateTime());
					fromAcctTrans.setType(TransactionTypes.TYPE_DEBIT);
					Account fromAcct = new Account();
					String acctDebitType = String.valueOf(BPWUtil.getAccountType(pmtInfo.getAcctDebitType()));
					fromAcct.setID(pmtInfo.getAcctDebitID(), acctDebitType);
					fromAcct.setDirectoryID(pmtInfo.getSubmittedBy());
					fromAcct.setCurrencyCode(pmtInfo.getCurDef());
					fromAcctTrans.setAccount(fromAcct);
	
					sUser.setProfileID(pmtInfo.getSubmittedBy());
	
					Map<String, Object> extraMap = new HashMap<>();
					TransactionAlertUtil.sendImmediateTransactionAlert(sUser, transactions, extraMap);
				}
			} catch (Exception ex) {
				FFSDebug.log(thismethod + " Error in sending transaction alert for srvrTID :  " + rsltArray[idx].srvrTid);
				FFSDebug.log(thismethod + ex);
			}
		}
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
