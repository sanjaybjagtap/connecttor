
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2001.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
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
import com.ffusion.ffs.bpw.custimpl.interfaces.BackendProvider;
import com.ffusion.ffs.bpw.custimpl.interfaces.TransactionStatusProvider;
import com.ffusion.ffs.bpw.custimpl.transfers.interfaces.TransferBackendResult;
import com.ffusion.ffs.bpw.db.DBConnCache;
import com.ffusion.ffs.bpw.db.Transfer;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfo;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.ExtTransferStatus;
import com.ffusion.ffs.bpw.interfaces.TransferInfo;
import com.ffusion.ffs.bpw.util.BPWUtil;
import com.ffusion.ffs.bpw.util.TransactionAlertUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.scheduling.constants.ScheduleConstants;
import com.ffusion.ffs.scheduling.db.Event;
import com.ffusion.ffs.scheduling.db.EventInfoLog;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.sap.banking.common.bo.interfaces.CommonConfig;
import com.sap.banking.common.constants.ConfigConstants;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

public class TransferRsltHandler implements
com.ffusion.ffs.bpw.interfaces.DBConsts,FFSConst,
com.ffusion.ffs.bpw.interfaces.BPWResource, BPWScheduleHandler {

    private TransferBackendResult _transferBackendResult = null;
    private boolean _okToCall = false;

    // Reference to the External Transfer Status instance
 	private transient ExtTransferStatus extTransferStatusRef = null;
 	
    public TransferRsltHandler() {
        try{
    		BackendProvider backendProvider = getBackendProviderService();
    		_transferBackendResult = backendProvider.getTransferBackendResultInstance();
    	} catch(Exception ex){
    		FFSDebug.log(ex, "Unable to get TransferBackendResult Instance" , FFSConst.PRINT_ERR);
    	}
        
        try {
			TransactionStatusProvider transactionStatusProvider = getTransactionStatusProviderService();
			extTransferStatusRef = transactionStatusProvider.getExtTransferStatusInstance();
		} catch (Exception ex) {
			FFSDebug.log(ex, "Unable to get External Transfer Status Instance", PRINT_ERR);
		}
    }

    public void eventHandler(int eventSequence, EventInfoArray evts,
                             FFSConnectionHolder dbh) throws Exception {
        String methodName = "TransferRsltHandler.eventHandler: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log(methodName + " start", FFSDebug.PRINT_DEV);
        boolean possibleDuplicate = false;
        processEvent(eventSequence, evts, dbh, possibleDuplicate);

        FFSDebug.log(methodName + " done.", FFSDebug.PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    } // end of eventHandler

    public void resubmitEventHandler(int eventSequence, EventInfoArray evts,
                                     FFSConnectionHolder dbh) throws Exception {
        String methodName = "TransferRsltHandler.resubmitEventHandler: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log(methodName + " start", FFSDebug.PRINT_DEV);

        boolean possibleDuplicate = true;
        processEvent(eventSequence,evts,dbh, possibleDuplicate);

        FFSDebug.log(methodName + " done.", FFSDebug.PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    public void processEvent(int eventSequence, EventInfoArray evts,
                             FFSConnectionHolder dbh,
                             boolean possibleDuplicate) throws Exception {
    	String methodName = "TransferRsltHandler.processEvent: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
    	if (eventSequence == ScheduleConstants.EVT_SEQUENCE_FIRST) {
            // read FIID, processId and set them in transferBackendRslt
            String fiId = evts._array[0].FIId;
            String processId = evts._array[0].processId;
            _transferBackendResult.setFIID(fiId);
            _transferBackendResult.setProcessId(processId);
            _okToCall = true;
        } else if (eventSequence == ScheduleConstants.EVT_SEQUENCE_LAST) {
        	if (_okToCall && evts != null && evts._array[0] != null) {
        		String batchKey = DBConnCache.getNewBatchKey();
                DBConnCache.bind(batchKey, dbh);
        		int fileBasedRecovery = evts._array[0].fileBasedRecovery;
				if (fileBasedRecovery == 0) {
					updateTransactionStatus(evts, dbh, batchKey);
				} else {	// If schedule is enable for file based recovery
	                Hashtable extInfo = new Hashtable();
	                _transferBackendResult.processTransferRslt(batchKey,extInfo);
				}
				// Remove the binding of the db connection and the batch key.
		        DBConnCache.unbind(batchKey);
        	}
        }
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
		}
		if(transactionStatusProvider == null) {
			FFSDebug.log("Invalid backend type." + backendType, FFSConst.PRINT_ERR);
			throw new Exception("Invalid backend type." + backendType);
		}
		return transactionStatusProvider;
	}
	
	/**
	 * Update transaction status from the back end
	 * @param evts EventInfoArray
	 * @param dbh FFSConnectionHolder with DB connection
	 * @param batchKey 
	 * @throws Exception
	 */
	protected void updateTransactionStatus(EventInfoArray evts, FFSConnectionHolder dbh, String batchKey) throws Exception {
		String thisMethod = "TransferRsltHandler.updateTransactionStatus: ";
		
		com.sap.banking.common.bo.interfaces.CommonConfig commonConfigBO = (com.sap.banking.common.bo.interfaces.CommonConfig) OSGIUtil
				.getBean(com.sap.banking.common.bo.interfaces.CommonConfig.class);
		
		// Return configured system user ID, which is used for read audit logging in BaS
		int systemUserID = getSystemUserID(commonConfigBO);
		
		// Get the BaS status
		boolean isBaSBackendEnabled = commonConfigBO.isBasBackendEnabled(); 
		
		// Retrieve eventInfo details for the external transfer
		// which transaction status is not yet received.
		// String[] instTypes = { DBConsts.ETFTRN };
		String instTypes = DBConsts.ETFTRN;
		List<EventInfo> eventinfoList = Event.retrieveEventInfoList(dbh,
				ScheduleConstants.EVT_STATUS_INPROCESS, evts._array[0].FIId, instTypes, true);
		
		try {
			while (eventinfoList != null && !eventinfoList.isEmpty()) {
				FFSDebug.log(thisMethod + "Start external transfer result handler batch.....");
				// Create TransferInfo collection to retrieve the status
				// from back end which is not received.
				List<TransferInfo> transInfoList = new ArrayList<>();
				TransferInfo transInfo = null;
				for (EventInfo evtInfo : eventinfoList) {
					// Find the transfer record in database that will
		            // contain all information.
					transInfo = new TransferInfo();
					transInfo.setSrvrTId(evtInfo.InstructionID);
					transInfo = Transfer.getTransferInfo(dbh, transInfo, false);
					
					// Set the batch key
					transInfo.setDbTransKey(batchKey);
		
					// Set user type to system, as schedule is initiated
					// by the system.
					transInfo.setPostingUserType(SecureUser.TYPE_SYSTEM);
		
					// Set system user Id in TransferInfo object which
					// is used by banking services for read audit
					// logging.
					transInfo.setPostingUserId(systemUserID);
		
					transInfoList.add(transInfo);
				}
				
				if (!transInfoList.isEmpty()) {
					try {
						TransferInfo[] transInfos = transInfoList.toArray(new TransferInfo[transInfoList.size()]);
						TransferInfo[] transInfoRsltArray = extTransferStatusRef.getExernalTransferStatus(transInfos, new HashMap<String, Object>());

						if(isBaSBackendEnabled) {
							processBaSTransferResult(evts, dbh, transInfoRsltArray, batchKey);
						} else {
							com.ffusion.ffs.bpw.master.BPWExternalProcessor bpwExtProcessor = new com.ffusion.ffs.bpw.master.BPWExternalProcessor();
							bpwExtProcessor.processTransferBackendlRslt(transInfoRsltArray);
							
							// Create event info log for the transfer, which status received from the back end.
							createEventInfoLog(dbh, transInfoRsltArray, evts._array[0]);
						}
						// Commit the batch processing changes
						dbh.conn.commit();
					} catch (Exception ex) {
						FFSDebug.log(thisMethod + "Error in updating external transfer result", ex);
					}
				}
				
				FFSDebug.log(thisMethod + "End external transfer result handler batch.....");
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
			}
		catch (NumberFormatException ex) {
			FFSDebug.log("Invalid user Id", ex);
		} catch (Exception ex) {
			FFSDebug.log("Invalid user Id", ex);
		}
		return systemUserID;
	}
	
	/**
	 * Create an event log entry for Transfer result returned by the back end
	 * @param dbh	FFSConnectionHolder with DB connection
	 * @param TransferInfo	transInfoRslt result object
	 * @param eventInfo	EventInfo
	 * @throws Exception
	 */
	private void createEventInfoLog(FFSConnectionHolder dbh, TransferInfo[] transInfoRslt, EventInfo eventInfo) throws Exception{
		EventInfo evt = null;
		for (int idx = 0; idx < transInfoRslt.length; idx++) {
			evt = new EventInfo();
			evt.EventID = com.ffusion.ffs.bpw.db.DBUtil.getNextIndexString(com.ffusion.ffs.bpw.interfaces.DBConsts.EVENTID);
			evt.FIId = eventInfo.FIId;
			evt.InstructionID = transInfoRslt[idx].getSrvrTId();
			evt.InstructionType = eventInfo.InstructionType;
			evt.LogID = eventInfo.LogID;
			evt.processId = eventInfo.processId;
			EventInfoLog.createEventInfoLog(dbh, evt);
		}
	}

	
	/**
	 * This API perform below tasks,
	 * 1.	If the external transfer is received by payment engine but not yet processed then Payment Engine will send the result with �in-process� status and confirmation number. 
	 * 		In this case, OCB will update only confirmation number.
	 * 2.	If the Payment engine has processed the external transfer then OCB process external transfer results in BPW.
	 * 3.	OCB send immediate transaction alert if external transfer is successfully processed.
	 * 
	 * @param evts EventInfoArray
	 * @param dbh FFSConnectionHolder with DB connection
	 * @param transInfoRsltArray
	 *            array of external transfer result objects
	 * @param batchKey
	 *            DB batch key
	 * @return TransferInfo[] Filtered transactions which are processed by the
	 *         payment engine. either success or failure.
	 * @throws Exception
	 */
	private void processBaSTransferResult(EventInfoArray evts, FFSConnectionHolder dbh, TransferInfo[] transInfoRsltArray, String batchKey)
			throws Exception {
		String thismethod = "TransferRsltHandler.processBaSTransferResult : ";

		com.sap.banking.bpw.transfer.interfaces.ExternalTransferResultProcessor externalTransferResultProcessorRef = (com.sap.banking.bpw.transfer.interfaces.ExternalTransferResultProcessor) OSGIUtil
				.getBean(com.sap.banking.bpw.transfer.interfaces.ExternalTransferResultProcessor.class);

		List<TransferInfo> transInfoRsltList = new ArrayList<>();

		TransferInfo transInfoRslt = null;
		for (int idx = 0; idx < transInfoRsltArray.length; idx++) {
			transInfoRslt = transInfoRsltArray[idx];
			if (transInfoRslt != null) {
				// Remove in-process transaction from result list, BPW only
				// process valid transaction status. e.g. success or failure
				if (DBConsts.INPROCESS.equals(transInfoRslt.getPrcStatus())) {
					try {
						// update transfer confirmation number to database
						externalTransferResultProcessorRef.updateTransferConfirmationNumber(transInfoRslt);
					} catch (Exception ex) {
						FFSDebug.log(thismethod + "Failed to update confirmation number for srvrTid = "
								+ transInfoRslt.getSrvrTId(), ex);
						throw ex;
					}
				} else if (DBConsts.POSTEDON.equals(transInfoRslt.getPrcStatus())
						|| DBConsts.BACKENDFAILED.equals(transInfoRslt.getPrcStatus())) {
					transInfoRsltList.add(transInfoRslt);
				}
			}
		}
		
		// If Payment engine processed the internal transfer then process result in BPW.
		if (!transInfoRsltList.isEmpty()) {
			TransferInfo[] rsltArray = transInfoRsltList.toArray(new TransferInfo[transInfoRsltList.size()]);
			com.ffusion.ffs.bpw.master.BPWExternalProcessor bpwExtProcessor = new com.ffusion.ffs.bpw.master.BPWExternalProcessor();
			bpwExtProcessor.processTransferBackendlRslt(rsltArray);
			
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
	 *            array of external transfer result objects
	 */
	private void sendImmediateTransactionAlert(FFSConnectionHolder dbh, TransferInfo[] rsltArray) {
		String thismethod = "TransferRsltHandler.sendImmediateTransactionAlert : ";
		TransferInfo transferInfo = null;
		Transactions transactions = null;
		SecureUser sUser = new SecureUser();
		for(int idx=0; idx<rsltArray.length;idx++) {
			try {
				transferInfo = Transfer.getTransferInfo(dbh, rsltArray[idx], false);
				if (transferInfo != null && DBConsts.POSTEDON.equals(transferInfo.getPrcStatus())) {
					transactions = new Transactions();
					// Set from account transaction alerts details
					Transaction fromAcctTrans = transactions.create();
					fromAcctTrans.setAmount(transferInfo.getAmount());
					fromAcctTrans.setDescription(transferInfo.getMemo());
					fromAcctTrans.setPostingDate(new DateTime());
					fromAcctTrans.setType(TransactionTypes.TYPE_DEBIT);
					Account fromAcct = new Account();
					String acctDebitType = String
							.valueOf(BPWUtil.getAccountType(transferInfo.getAccountFromType()));
					fromAcct.setID(transferInfo.getAccountFromNum(), acctDebitType);
					fromAcct.setDirectoryID(transferInfo.getSubmittedBy());
					fromAcct.setCurrencyCode(transferInfo.getAmountCurrency());
					fromAcctTrans.setAccount(fromAcct);

					sUser.setProfileID(transferInfo.getSubmittedBy());

					Map<String, Object> extraMap = new HashMap<>();
					TransactionAlertUtil.sendImmediateTransactionAlert(sUser, transactions, extraMap);
				}

			} catch (Exception ex) {
				FFSDebug.log(thismethod + " Error in sending transaction alert for srvrTID :  " + rsltArray[idx].getSrvrTId());
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
} // end of class TransferRsltHandler
