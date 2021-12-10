/*
 *     Copyright (c) 2000 Financial Fusion, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of
 * Financial Fusion, Inc. You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of
 * the license agreement you entered into with Financial Fusion, Inc.
 * No part of this software may be reproduced in any form or by any
 * means - graphic, electronic or mechanical, including photocopying,
 * recording, taping or information storage and retrieval systems -
 * except with the written permission of Financial Fusion, Inc.
 *
 * CopyrightVersion 1.0
 *
 */

package com.ffusion.ffs.bpw.handler;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.ffusion.beans.SecureUser;
import com.ffusion.beans.util.StringUtil;
import com.ffusion.ffs.bpw.audit.TransAuditLog;
import com.ffusion.ffs.bpw.custimpl.interfaces.BackendProvider;
import com.ffusion.ffs.bpw.custimpl.interfaces.TransactionStatusProvider;
import com.ffusion.ffs.bpw.custimpl.transfers.interfaces.ITransferBackendHandler;
import com.ffusion.ffs.bpw.db.ACHFI;
import com.ffusion.ffs.bpw.db.BPWFI;
import com.ffusion.ffs.bpw.db.Customer;
import com.ffusion.ffs.bpw.db.DBConnCache;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.db.ExternalTransferAccount;
import com.ffusion.ffs.bpw.db.ExternalTransferCompany;
import com.ffusion.ffs.bpw.db.SmartCalendar;
import com.ffusion.ffs.bpw.db.Transfer;
import com.ffusion.ffs.bpw.interfaces.ACHConsts;
import com.ffusion.ffs.bpw.interfaces.ACHFIInfo;
import com.ffusion.ffs.bpw.interfaces.AuditLogConsts;
import com.ffusion.ffs.bpw.interfaces.BPWException;
import com.ffusion.ffs.bpw.interfaces.BPWFIInfo;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.CustomerInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfo;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.ExtTransferAcctInfo;
import com.ffusion.ffs.bpw.interfaces.ExtTransferCompanyInfo;
import com.ffusion.ffs.bpw.interfaces.ExtTransferStatus;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.TransferInfo;
import com.ffusion.ffs.bpw.interfaces.util.AccountUtil;
import com.ffusion.ffs.bpw.util.reconciliation.ReconciliationUtil;
import com.ffusion.ffs.bpw.util.resources.BPWLocaleUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.scheduling.constants.ScheduleConstants;
import com.ffusion.ffs.scheduling.db.Event;
import com.ffusion.ffs.scheduling.db.EventInfoLog;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.ffusion.ffs.util.FFSUtil;
import com.ffusion.util.ILocalizable;
import com.ffusion.util.logging.AuditLogRecord;
import com.ffusion.util.logging.AuditLogTranTypes;
import com.sap.banking.common.constants.ConfigConstants;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

/**
 * <P>
 * This class contains a callback handler that is registered in the
 * IntructionType table. The registered callback handler will be called by the
 * Scheduling engine for the transaction Processing.
 *
 *
 *
 */

public class TransferHandler implements
		com.ffusion.ffs.bpw.interfaces.DBConsts, FFSConst,
		com.ffusion.ffs.bpw.interfaces.BPWResource, BPWScheduleHandler {

	private ITransferBackendHandler _transferBackendHandler = null;

	private ArrayList transferList = null;
	private ArrayList vendorPmtList = null;
	private ArrayList employeePmtList = null;

	private boolean _doAuditLog = false;
	private PropertyConfig _propertyConfig = null;
	private String _processId = null;
	BPWFIInfo _bpwFIInfo = null;
	ACHFIInfo _achFIInfo = null;
	boolean _createEmptyFile = false;
	String _batchKey = null;
	int _batchSize = 0;
	int _processNumber = 1;
	int _fileBasedRecovery = 0;
	ArrayList prenoteTransferList = null;
	private boolean supportItoEImmediate = false;
	private boolean supportEtoIImmediate = false;
	private boolean supportItoEImmediateFunds = false;
    private boolean supportItoEScheduledFunds = false;
    private boolean supportEtoIImmediateFunds = false;
    private boolean supportEtoIScheduledFunds = false;
    private String externalTransferBackendType = null;
    
    private boolean isSameDayEnabledForETF = false;

	private final String BPW_FI_INFO = "BPW_FI_INFO";
	private final String ACH_FI_INFO = "ACH_FI_INFO";
	private final String DO_AUDIT_LOG = "DO_AUDIT_LOG";
	private final String PROCESS_ID = "PROCESS_ID";
	
	// Reference to the common config BO
	private final transient com.sap.banking.common.bo.interfaces.CommonConfig commonConfigBO;
	
	// Flag to identify BaS status
	private final boolean isBasBackendEnabled;

	public TransferHandler() throws Exception {
		
		_propertyConfig = (PropertyConfig) FFSRegistry.lookup(PROPERTYCONFIG);
		int logLevel = _propertyConfig.LogLevel;

		_batchSize = _propertyConfig.BatchSize;
		
		String SameDayEnabledForETF = _propertyConfig.otherProperties.getProperty(DBConsts.SAME_DAY_ENABLED_FOR_ETF, String.valueOf(isSameDayEnabledForETF));
		
		if (SameDayEnabledForETF.equalsIgnoreCase(String.valueOf(true))) {
			this.isSameDayEnabledForETF = true;
		}
		
		// Support immediate ItoE
        String supportImmd =
        		_propertyConfig.otherProperties.getProperty(DBConsts.BPW_TRN_ITOE_SUPPORT_IMMED,
                                                   DBConsts.BPW_TRN_ITOE_SUPPORT_IMMED_DEFAULT);
        if (supportImmd.equals(BPW_TRN_ITOE_SUPPORT_IMMED_ENABLED)) {
            this.supportItoEImmediate = true;
        }

        // Support immediate EtoI
        supportImmd =
        		_propertyConfig.otherProperties.getProperty(DBConsts.BPW_TRN_ETOI_SUPPORT_IMMED,
                                                   DBConsts.BPW_TRN_ETOI_SUPPORT_IMMED_DEFAULT);
        if (supportImmd.equals(BPW_TRN_ETOI_SUPPORT_IMMED_ENABLED)) {
            this.supportEtoIImmediate = true;
        }
		
		// Support funds approval for ItoE Immediate
        String supportFunds =
        		_propertyConfig.otherProperties.getProperty(
                                                  DBConsts.BPW_TRN_ITOE_IMMED_FUNDS_APPROVAL,
                                                  DBConsts.BPW_TRN_ITOE_IMMED_FUNDS_APPROVAL_DEFAULT);
        if (supportFunds.equals(BPW_TRN_ITOE_IMMED_FUNDS_APPROVAL_ENABLED)) {
            this.supportItoEImmediateFunds = true;
        }

        // Support funds approval for ItoE Scheduled
        supportFunds =
        		_propertyConfig.otherProperties.getProperty(
                                                  DBConsts.BPW_TRN_ITOE_SCHED_FUNDS_APPROVAL,
                                                  DBConsts.BPW_TRN_ITOE_SCHED_FUNDS_APPROVAL_DEFAULT);

        if (supportFunds.equalsIgnoreCase(DBConsts.BPW_TRN_ITOE_SCHED_FUNDS_APPROVAL_ENABLED)) {
            this.supportItoEScheduledFunds = true;
        }

        // Support funds approval for EtoI Immediate
        supportFunds =
        		_propertyConfig.otherProperties.getProperty(
                                                  DBConsts.BPW_TRN_ETOI_IMMED_FUNDS_APPROVAL,
                                                  DBConsts.BPW_TRN_ETOI_IMMED_FUNDS_APPROVAL_DEFAULT);


        if (supportFunds.equalsIgnoreCase(DBConsts.BPW_TRN_ETOI_IMMED_FUNDS_APPROVAL_ENABLED)) {
            this.supportEtoIImmediateFunds = true;
        }

        // Support funds approval for EtoI Scheduled
        supportFunds =
        		_propertyConfig.otherProperties.getProperty(
                                                  DBConsts.BPW_TRN_ETOI_SCHED_FUNDS_APPROVAL,
                                                  DBConsts.BPW_TRN_ETOI_SCHED_FUNDS_APPROVAL_DEFAULT);
        if (supportFunds.equalsIgnoreCase(DBConsts.BPW_TRN_ETOI_SCHED_FUNDS_APPROVAL_ENABLED)) {
            this.supportEtoIScheduledFunds = true;
        }
        
        externalTransferBackendType = _propertyConfig.otherProperties.getProperty(
				DBConsts.BPW_EXTERNAL_TRANSFER_BACKEND_TYPE, DBConsts.BPW_EXTERNAL_TRANSFER_BACKEND_TYPE_DEFAULT);

		_doAuditLog = (logLevel >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE);

		// get a handle to the backend system
		try {
			// Use TransferBackendHandlerFactory to load Backend Handler
			// mentioned in the bpwconfig.xml
			BackendProvider backendProvider = getBackendProviderService();
			_transferBackendHandler = backendProvider.getExternalTransferBackendHandlerInstance(externalTransferBackendType);
		} catch (BPWException e) {
			String error = "Error while getting ITransferBackendHandler implementation";
			FFSDebug.throwing(error, e);
			throw e;
		}
		
		prenoteTransferList = new ArrayList();
		
		commonConfigBO = (com.sap.banking.common.bo.interfaces.CommonConfig) OSGIUtil
				.getBean(com.sap.banking.common.bo.interfaces.CommonConfig.class);
		isBasBackendEnabled = commonConfigBO.isBasBackendEnabled();
	}

	/**
	 * Callback method for event processing
	 *
	 * @param eventSequence
	 *            event sequence number
	 * @param evts
	 *            array of event information objects
	 * @param dbh
	 *            Database connection holder
	 * @exception Exception
	 */
	public void eventHandler(int eventSequence, EventInfoArray evts,
			FFSConnectionHolder dbh) throws Exception {
		String methodName = "TransferHandler.eventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);

		FFSDebug.log("TransferHandler.eventHandler: begin, eventSeq: "
				+ eventSequence + ",length: " + evts._array.length, PRINT_DEV);

		processEvents(eventSequence, evts, dbh, false, false); // reRunCutOff
																// and
																// crashRecovery
																// both false

		FFSDebug.log("TransferHandler.eventHandler: end", PRINT_DEV);
		 PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
	}

	/**
	 * Callback method for handling resubmitted events
	 *
	 * This method is called when an ETFTRN cutoff is re-run or crash recovery
	 * happens. It calls processEvents(). If evts._array[0].ScheduleFlag ==
	 * ScheduleConstants.SCH_FLAG_RESUBMIT, reRunCutOff is true and
	 * crashRecovery is false. Otherwise, reRunCutOff is false and crashRecovery
	 * is true.
	 *
	 * @param eventSequence
	 *            event sequence number
	 * @param evts
	 *            array of event information objects
	 * @param dbh
	 *            Database connection holder
	 * @exception Exception
	 */
	public void resubmitEventHandler(int eventSequence, EventInfoArray evts,
			FFSConnectionHolder dbh) throws Exception {
		String methodName = "TransferHandler.resubmitEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);

		FFSDebug.log("TransferHandler.resubmitEventHandler: begin, eventSeq: "
				+ eventSequence + ",length: " + evts._array.length, PRINT_DEV);
		boolean reRunCutOff = false;
		boolean crashRecovery = false;

		if (evts._array[0].ScheduleFlag == ScheduleConstants.SCH_FLAG_RESUBMIT) {
			reRunCutOff = true;
		} else {
			crashRecovery = true;
		}

		FFSDebug.log("TransferHandler.resubmitEventHandler: reRunCutOff ="
				+ reRunCutOff + ", crashRecovery =" + crashRecovery, PRINT_DEV);

		processEvents(eventSequence, evts, dbh, reRunCutOff, crashRecovery);

		FFSDebug.log("TransferHandler.resubmitEventHandler: end", PRINT_DEV);
		 PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
	}

	/**
	 * Callback method for event processing
	 *
	 * @param eventSequence
	 *            event sequence number
	 * @param evts
	 *            array of event information objects
	 * @param dbh
	 *            Database connection holder
	 * @exception Exception
	 */
	private void processEvents(int eventSequence, EventInfoArray evts,
			FFSConnectionHolder dbh, boolean reRunCutOff, boolean crashRecovery)
			throws Exception {
		String thisMethod = "TransferHandler.processEvents : ";
		FFSDebug.log("TransferHandler.processEvents: begin, eventSeq: "
				+ eventSequence + ", reRunCutOff: " + reRunCutOff
				+ ", crashRecovery: " + crashRecovery + ", length: "
				+ evts._array.length, PRINT_DEV);
		
		_processId = evts._array[0].processId;
		
		if( _processId != null && _processId.equals("0") ) {
			
			HashMap extraInfo = new HashMap(); // default no extra
			
			if (eventSequence == ScheduleConstants.EVT_SEQUENCE_FIRST) { // FIRST
				
				_batchKey = DBConnCache.getNewBatchKey();
				DBConnCache.bind(_batchKey, dbh);
				
				// sequence
				_transferBackendHandler.startProcessTransfer(evts._array[0], extraInfo);
				
				
			} else if (eventSequence == ScheduleConstants.EVT_SEQUENCE_NORMAL) { // NORMAL
				
				String fIId = evts._array[0].FIId;
				String srvrtid = evts._array[0].InstructionID;
				
				TransferInfo info = Transfer.getUnProcessedTransferForFIIdAndSrvrTid(dbh, fIId, srvrtid);
				
				info.setEventId("" + ScheduleConstants.EVT_SEQUENCE_NORMAL);
				info.setDbTransKey(_batchKey);
				info.setPrcStatus(DBConsts.INPROCESS);
				
				// Populate customer information
				CustomerInfo custInfo = Customer.getCustomerByID(info.getCustomerId(), dbh);
				info.setCustomerInfo(custInfo);
				
				TransferInfo[] transferInfoArray = new TransferInfo[1];				
				transferInfoArray[0] = info;
				
				Transfer.updateStatusAndProcessInfo(dbh, info);
				
				// if transfer is immediate then set IS_IMMEDIATE_TRANSACTION flag to true
				HashMap extraFields = null;
				if(info.extraFields != null) {
					extraFields = (HashMap) info.extraFields;
				} else {
					extraFields = new HashMap();
				}
				extraFields.put(IS_IMMEDIATE_TRANSACTION, DBConsts.TRUE);
				info.extraFields = extraFields;
				
				// In case of immediate transfer set user type to customer and which is used by banking services for read audit logging.
				info.setPostingUserType(SecureUser.TYPE_CUSTOMER);
				
				// Set user Id in TransferInfo object which is used by banking services for read audit logging.
				int userId = -1;
                try {
                    userId = Integer.parseInt(info.getSubmittedBy());
                } catch (NumberFormatException ex) {
                	FFSDebug.log("Invalid user Id");
                }
                info.setPostingUserId(userId);
                
                // Use this connection to update event details in DB.
        		FFSConnectionHolder dbhLocal = new FFSConnectionHolder();
        		dbhLocal.conn = DBUtil.getConnection();
        		try {
	                // Create an event which is used for the retry status
					// check and reconciliation.
					createEvent(dbhLocal, info, evts._array[0], extraInfo);
					// Persist event details to DB
					dbhLocal.conn.commit();
        		} catch (Exception ex) {
        			FFSDebug.log(thisMethod + ex);
        			throw ex;
        		} finally {
        			DBUtil.freeConnection(dbhLocal.conn);
        		}
        		
				// sequence
				_transferBackendHandler.processTransfer(transferInfoArray, extraInfo);

			} else if (eventSequence == ScheduleConstants.EVT_SEQUENCE_LAST) { // LAST
				
				// sequence
				_transferBackendHandler.endProcessTransfer(evts._array[0], extraInfo);
				
			}
			
		} else {

			if (eventSequence == ScheduleConstants.EVT_SEQUENCE_FIRST) { // FIRST
																			// sequence
	
				firstEventHandler(dbh, evts, reRunCutOff);
			} else if (eventSequence == ScheduleConstants.EVT_SEQUENCE_LAST) { // LAST
																				// sequence
	
				lastEventHandler(dbh, evts, reRunCutOff, crashRecovery);
			}
			
		}
		FFSDebug.log("TransferHandler.processEvents: end", PRINT_DEV);
	}
	
    // Determines the value to be used for the column dateToProcess
    protected static String getProcessDate() {
		// the format for the date is yyyyMMdd00
		StringBuffer dateBuf = new StringBuffer( FFSUtil.getDateString(DUE_DATE_FORMAT));
		dateBuf.append( "00" );
		return dateBuf.toString();
    }

	/**
	 * get values for _processId, _createEmptyFile find BPWFIInfo and ACHFIInfo
	 * for this schedule start transferBackenHandler with some values create a
	 * dummy TransferInfo with eventId as ...FIRST
	 *
	 * @param dbh
	 *            database connection holder to get BPWFIINfo and ACHFIInfo
	 * @param evts
	 *            array of one event containing processId, createEmptyFile and
	 *            FIID
	 * @exception Exception
	 */
	public void firstEventHandler(FFSConnectionHolder dbh, EventInfoArray evts,
			boolean reRunCutOff) throws Exception {
		String methodName = "TransferHandler.firstEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
		/*
		 * Get the following evts.array[0].FIID, evts.array[0].processed,
		 * evts.array[0].createEmptyFile, BPWFIInfo, ACHFIInfo
		 */

		String fIId = evts._array[0].FIId;
		_processId = evts._array[0].processId;
		_createEmptyFile = evts._array[0].createEmptyFile;
		_bpwFIInfo = BPWFI.getBPWFIInfo(dbh, fIId);
		_achFIInfo = getCutOffACHFIInfo(dbh, fIId);
		_fileBasedRecovery = evts._array[0].fileBasedRecovery;

		HashMap extra = new HashMap();
		extra.put(BPW_FI_INFO, _bpwFIInfo);
		extra.put(ACH_FI_INFO, _achFIInfo);
		extra.put(DO_AUDIT_LOG, new Boolean(_doAuditLog));
		extra.put("RERUN_CUTOFF", new Boolean(reRunCutOff));
		extra.put(PROCESS_ID, _processId);
		this.startProcessTransfer(evts._array[0], extra);
		// Generate a new batch key and bind the db connection
		// using the batch key.
		_batchKey = DBConnCache.getNewBatchKey();
		DBConnCache.bind(_batchKey, dbh);

		// initialize a list of transfers
		transferList = new ArrayList();
		// initialize a list of transfers
		vendorPmtList = new ArrayList();
		// initialize a list of transfers
		employeePmtList = new ArrayList();

		// Create a dummy TransferInfo, set its eventId as eventSequence
		TransferInfo transInfo = createDummyTransferInfo(evts, _bpwFIInfo,
				ScheduleConstants.EVT_SEQUENCE_FIRST);
		// Create a dummy employeePmtInfo, set its eventId as eventSequence
		TransferInfo employeePmtInfo = createDummyTransferInfo(evts,
				_bpwFIInfo, ScheduleConstants.EVT_SEQUENCE_FIRST);
		employeePmtInfo.setCategory(ACHConsts.EMPLOYEE_CATEGORY);
		// Create a dummy vendorPmtInfo, set its eventId as eventSequence
		TransferInfo vendorPmtInfo = createDummyTransferInfo(evts, _bpwFIInfo,
				ScheduleConstants.EVT_SEQUENCE_FIRST);
		vendorPmtInfo.setCategory(ACHConsts.VENDOR_CATEGORY);
		// add the starting sequence to the transaction list
		transferList.add(transInfo);
		// add the starting sequence to the vendor payment list
		vendorPmtList.add(vendorPmtInfo);
		// add the starting sequence to the transaction list
		employeePmtList.add(employeePmtInfo);
		PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
	}

	/**
	 * Update status of matured external transfer account which require prenote
	 * If reRunCutOff is true, get ONLY processed transfers by fiid and
	 * processId If crashRecovery is true, get processed transfers by fiid and
	 * processId Get unprocessed transfers by fiid, dateToPost and processDate
	 *
	 * @param dbh
	 * @param reRunCutOff
	 * @param crashRecovery
	 * @exception Exception
	 */
	public void getTransfers(FFSConnectionHolder dbh, boolean reRunCutOff,
			boolean crashRecovery, String category) throws Exception {
		String methodName = "TransferHandler.getTransfers";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
		ArrayList normalTransfers = new ArrayList();
		boolean needToSetDuplicate = false;
		int cnt = 0;

		// secondary db connection
		// FFSConnectionHolder dbh2 = new FFSConnectionHolder();
		// dbh2.conn = DBUtil.getConnection();
		// if (dbh2.conn == null) {
		// String err =
		// "TransferHandler.getTransfers():Failed to obtain a connection from the connection pool";
		// System.out.println( err );
		// throw new FFSException(err);
		// }

		/*
		 * There can be 3 cases here- 1. It is a re-run cutoff 2. It is a case
		 * of crash recovery 3. It is a normal transfer
		 *
		 * To support retrieving transfers in batches we will do the following -
		 * a. For processed tranfers - Along with our normal criterias, we will
		 * use the column BPW_Transfer.LastChangeDate to determine if we should
		 * retrieve that row or not. For each batch, we save the time it was
		 * processed by BPW in the column LastChangeDate b. For un-processed
		 * transfers - Since we change the status to POSTEDON or BACKENDFAILED,
		 * we dont need to do anything extra
		 *
		 * For re-run cutoff - We will get all the INPROCESS transfers.
		 *
		 * For crash recovery - We will get both the processed (Prenote
		 * included) and unprocessed transfers together.
		 *
		 * For normal transfer - We will get the unprocessed transfers and the
		 * prenote transfers as well.
		 */
		// try{
		if (reRunCutOff) {
			// re-run cutoff only gets INPROCESS transfers
			ArrayList inProcessTransfers = getInProcessTransfersForFIId(dbh, _processId, _bpwFIInfo
					.getFIId(), _processNumber, _batchSize, category);
			
			// In case of BaS, Do not resubmit transaction if OCB received confirmation number from Payment Engine.
            // Receiving a Confirmation Number for the transaction would mean that the transaction has reached the Payment Engine.
			if(isBasBackendEnabled) {
				TransferInfo transInfo = null;
				Iterator itrTransInfo = inProcessTransfers.iterator();
				while(itrTransInfo.hasNext()) {
					transInfo = (TransferInfo) itrTransInfo.next();
					// Resubmit transfer which confirmation number has not received.
					if(transInfo != null && StringUtil.isEmpty(transInfo.getConfirmNum())) {
						normalTransfers.add(transInfo);
					}
				}
			} else {
				normalTransfers.addAll(inProcessTransfers);
			}
		} else if (crashRecovery == true) {

			if (_fileBasedRecovery == 1) {

				// secondary db connection
				FFSConnectionHolder dbh2 = new FFSConnectionHolder();
				dbh2.conn = DBUtil.getConnection();
				if (dbh2.conn == null) {
					String err = "TransferHandler.getTransfers():Failed to obtain a connection from the connection pool";
					System.out.println(err);
					PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
					throw new FFSException(err);
				}
				try {
					// get prenote transfers by processId
					TransferInfo[] prenoteTransfers = getPrenoteTransferByProcessId(dbh, _processId, _processNumber, category);
					int prenoteLen = prenoteTransfers.length;
					for (int i = 0; i < prenoteLen; i++) {
						// cancel this prenote entry
						// set its status to be CANCLEDON
						prenoteTransfers[i].setPrcStatus(DBConsts.CANCELEDON);
						Transfer.updateStatus(dbh2, prenoteTransfers[i], false);
						// dbh.conn.commit(); // commit the change

						// AuditLog
						ILocalizable msg = BPWLocaleUtil
								.getLocalizableMessage(
										AuditLogConsts.AUDITLOG_MSG_EXTERNALTRANSFER_TRANSFER_HANDLER_CANCEL_AND_REPROCES_PRENOTE,
										null,
										BPWLocaleUtil.EXTERNALTRANSFER_AUDITLOG_MESSAGE);

						this
								.doTransAuditLog(
										dbh2,
										prenoteTransfers[i],
										reRunCutOff,
										msg,
										"Server crashed last time. Cancel this entry first and then re-process prenote.");

						ExtTransferAcctInfo extTransferAcct = null;
						if (prenoteTransfers[i].getTransferDest().compareTo(
								DBConsts.BPW_TRANSFER_DEST_ITOE) == 0) {
							extTransferAcct = prenoteTransfers[i]
									.getAccountToInfo();
						} else {
							extTransferAcct = prenoteTransfers[i]
									.getAccountFromInfo();
						}

						// update PrenoteStatus but not its submitted date
						this.updateAcctPrenote(dbh2, extTransferAcct, null,
								null);
						// dbh.conn.commit(); // commit the change

						// create a new prenote transfer in db
						createPrenoteTransfer(dbh2, extTransferAcct);
					}
					dbh2.conn.commit();

					// prenote processing over.
					// now get the transfers - both processed and unprocessed.
					// Processed transfers will include prenote transfers we
					// just created.

					ArrayList allTransfer = getAllTransfersForBackendByFIId(dbh, _processId,
									_bpwFIInfo.getFIId(), _processNumber,
									_batchSize, category);

					normalTransfers.addAll(allTransfer);

					// this is an indicator which will be used to set the
					// possibleDuplicate to be true, if transfer's lastProcessId
					// == _processId
					needToSetDuplicate = true;
				} finally {
					PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
					DBUtil.freeConnection(dbh2.conn);
					dbh2 = null;
				}

			} else { // file based recovery is not supported
				// get only the unprocessed ones
				// get unprocessed transfers by fiid , dateToPost and
				// ProcessDate ordered by CompACHID
				// and transferDest
				normalTransfers.addAll(getUnProcessedTransfersForFIId(dbh,_bpwFIInfo.getFIId(), _batchSize, category));
			}

		} else { // normal transfer

			// get only the unprocessed ones
			// get unprocessed transfers by fiid , dateToPost and ProcessDate
			// ordered by CompACHID
			// and transferDest
			normalTransfers.addAll(getUnProcessedTransfersForFIId(dbh,_bpwFIInfo.getFIId(), _batchSize, category));
		}
		// }finally{
		// DBUtil.freeConnection(dbh2.conn);
		// dbh2=null;
		// }

		if (normalTransfers != null) {
			/*
			 * We are filtering out of date transfers from normalTransfer list
			 * We will walk through each transfer. If it is not out of date or
			 * the processOldEntries flag is on, put it in good transfer list
			 */
			ArrayList goodNormalTransfers = new ArrayList();
			int len = normalTransfers.size();
			String today = "" + DBUtil.getCurrentStartDate();
			boolean processOldEntries = getProcessOldEntries();

			for (int i = 0; i < len; i++) {
				TransferInfo transfer = (TransferInfo) (normalTransfers.get(i));

				if (needToSetDuplicate == true) {
					// this means this we are now in recovery mode and
					// fileBasedRecovery is ON
					// Now, if transfer's lastProcessId == _processId, we set
					// possibleDuplicate as true.
					if (transfer.getLastProcessId() != null
							&& transfer.getLastProcessId().equals(_processId)) {

						transfer.setPossibleDuplicate(true);
					}
				}
				
				// Set possibleDuplicate as true when run resubmit external transfer
				if(reRunCutOff) {
					transfer.setPossibleDuplicate(true);
				}

				// update lastProcessId and processedTime in database
				transfer.setLastProcessId(_processId);
				transfer.setProcessNumber(_processNumber);
				// Transfer.updateProcessInfo(dbh, transfer);
				// dbh.conn.commit(); // commit the change

				// set eventId, db trans key and status before passing it to
				// backend handler
				transfer.setEventId("" + ScheduleConstants.EVT_SEQUENCE_NORMAL);
				transfer.setDbTransKey(_batchKey);
				transfer.setPrcStatus(DBConsts.INPROCESS);
				// Transfer.updateStatus(dbh,transfer,false);

				// Update Status and ProcessInfo in one call. Rather than 2
				// separate calls
				Transfer.updateStatusAndProcessInfo(dbh, transfer);

				// DO NOT COMMIT AFTER THIS
				/*
				 * We don't do audit log here because the transfer might be old
				 * If it is the case, failTransfer will do auditLog. We will do
				 * auditlog when this is a good normal transfer
				 */
                if (!Customer.isActive(transfer.getCustomerId(), dbh))
                {
                    // fail the transfer and don't add it into the good
                    // transfer list
                    failTransfer(dbh, transfer, true);
                    // go to the next transfer in the list. skip the rest of
                    // code.
                    continue;
                }
				if (transfer.getDateToPost().compareTo(today) < 0) {
					if (!processOldEntries) {
						// fail the transfer and don't add it into the good
						// transfer list
						failTransfer(dbh, transfer, false);
						// go to the next transfer in the list. skip the rest of
						// code.
						continue;
					} else {
						/*
						 * this is out of date transfer, but we still add it
						 * into the list of good transfer for processing because
						 * the flag processOldEntries is on
						 */
						goodNormalTransfers.add(transfer);
					}
				}
                else {
					// this transfer is not out of date, add it into good
					// transfer list
					goodNormalTransfers.add(transfer);
				}
				// AuditLog
				ILocalizable msg = BPWLocaleUtil
						.getLocalizableMessage(
								AuditLogConsts.AUDITLOG_MSG_EXTERNALTRANSFER_TRANSFER_HANDLER_PROCESSED_EXT_TRANSFER,
								null,
								BPWLocaleUtil.EXTERNALTRANSFER_AUDITLOG_MESSAGE);
				this.doTransAuditLog(dbh, transfer, reRunCutOff, msg,
						"Successfully processed an external transfer.");

			}
			dbh.conn.commit(); // commit the change for batch
			if (category == null)
				transferList.addAll(goodNormalTransfers);
			else if (category != null
					&& category.equals(ACHConsts.EMPLOYEE_CATEGORY))
				employeePmtList.addAll(goodNormalTransfers);
			else if (category != null
					&& category.equals(ACHConsts.VENDOR_CATEGORY))
				vendorPmtList.addAll(goodNormalTransfers);
		}
		PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
	}

	/**
	 * Fail a transfer is called when the transfer's datetopost is in the past
	 *
	 * @param transferInfo
	 * @exception Exception
	 */
	private void failTransfer(FFSConnectionHolder dbh, TransferInfo transferInfo, boolean isInactive)
			throws Exception {
		transferInfo.setPrcStatus(DBConsts.BACKENDFAILED);
		//Transfer.updateStatus(dbh, transferInfo, false);

		transferInfo.setAction(DBConsts.BPW_TRANSFER_ACTION_FAILED);
		Transfer.updateStatusAction(dbh, transferInfo, false);

        int auditLogMsgID = AuditLogConsts.AUDITLOG_MSG_EXTERNALTRANSFER_TRANSFER_HANDLER_PROCESSED_TRANSFER_IS_OLD;
        if (isInactive)
            auditLogMsgID = AuditLogConsts.AUDITLOG_MSG_EXTERNALTRANSFER_TRANSFER_HANDLER_CUSTOMER_IS_INACTIVE;
		ILocalizable msg = BPWLocaleUtil
				.getLocalizableMessage(
						auditLogMsgID,
						null, BPWLocaleUtil.EXTERNALTRANSFER_AUDITLOG_MESSAGE);
		doAuditLog(dbh, transferInfo, msg);
	}

	/**
	 * Check if old transfers are allowed to be processed base on a server's
	 * property
	 *
	 * @return true or false
	 */
	private boolean getProcessOldEntries() {
		PropertyConfig propertyConfig = (PropertyConfig) FFSRegistry
				.lookup(PROPERTYCONFIG);

		String processOldEntries = propertyConfig.otherProperties.getProperty(
				DBConsts.BPW_TRANSFER_BACKEND_PROCESS_OLD_ENTRIES,
				DBConsts.BPW_TRANSFER_BACKEND_PROCESS_OLD_ENTRIES_DEFAULT);
		// If processOldEntries is the same as default (which is false), then
		// return true
		// Otherwise, return false
		return (!processOldEntries
				.equals(DBConsts.BPW_TRANSFER_BACKEND_PROCESS_OLD_ENTRIES_DEFAULT));
	}

	/**
	 *
	 * @param dbh
	 * @param evts
	 * @param reRunCutOff
	 * @param crashRecovery
	 * @throws Exception
	 */
	public void lastEventHandler(FFSConnectionHolder dbh, EventInfoArray evts,
			boolean reRunCutOff, boolean crashRecovery) throws Exception {
		String methodName = "TransferHandler.lastEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);

		try {

			String fIId = _bpwFIInfo.getFIId();
			this.processExtAccountWithPrenote(dbh, fIId);

			/*
			 * for re-run cutoff or crash recovery, we need to find the max
			 * process number of transfers with processId and the new
			 * processNumber will be one more than the max number. for normal
			 * cutoff, _processNumber is intialized as 1
			 */
			if (reRunCutOff || crashRecovery) {
				_processNumber = Transfer.getMaxProcessNumberByProcessId(dbh,
						_processId) + 1;
			}
			// get the transfers. We will get the transfers from transferList
			// The transferList in the first call will also contain the first
			// dummy
			// record that we have added earlier.
            boolean done = false;
			while (!done) {

                getTransfers(dbh, reRunCutOff, crashRecovery, null);
                if (transferList.size() < _batchSize)
                {
                    done = true;
                    // At the end, create a dummy TransferInfo with eventId as
                    // eventSequence.
                    TransferInfo transInfo = createDummyTransferInfo(evts, _bpwFIInfo,
                            ScheduleConstants.EVT_SEQUENCE_LAST);
                    transferList.add(transInfo);
                }
				// Call processTransfers()
				this.processTrans(dbh, transferList, evts._array[0]);
				transferList.clear();
			}

			// get the transfers. We will get the transfers from transferList
			// The transferList in the first call will also contain the first
			// dummy
			// record that we have added earlier.
            done = false;
			while (!done) {

                getTransfers(dbh, reRunCutOff, crashRecovery,
                        ACHConsts.EMPLOYEE_CATEGORY);
                if (employeePmtList.size() < _batchSize)
                {
                    done = true;
                    // At the end, create a dummy TransferInfo with eventId as
                    // eventSequence.
                    TransferInfo transInfo = createDummyTransferInfo(evts, _bpwFIInfo,
                            ScheduleConstants.EVT_SEQUENCE_LAST);
                    employeePmtList.add(transInfo);
                }
				// Call processTransfers()
				this.processTrans(dbh, employeePmtList, evts._array[0]);
				employeePmtList.clear();
            }

			// get the transfers. We will get the transfers from transferList
			// The transferList in the first call will also contain the first
			// dummy
			// record that we have added earlier.

            done = false;
			while (!done) {

                getTransfers(dbh, reRunCutOff, crashRecovery,
                        ACHConsts.VENDOR_CATEGORY);
                if (vendorPmtList.size() < _batchSize)
                {
                    done = true;
                    // At the end, create a dummy TransferInfo with eventId as
                    // eventSequence.
                    TransferInfo transInfo = createDummyTransferInfo(evts, _bpwFIInfo,
                            ScheduleConstants.EVT_SEQUENCE_LAST);
                    vendorPmtList.add(transInfo);
                }
				// Call processTransfers()
				this.processTrans(dbh, vendorPmtList, evts._array[0]);
				vendorPmtList.clear();
			}

			// Remove the binding of the db connection and the batch key.
			DBConnCache.unbind(_batchKey);

		} catch (Exception e) {
			try {
				dbh.conn.rollback();
			} catch (Exception re) {
				PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
			}
			PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
			throw e;
		}
		PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
	}

	/**
	 * push the transfer Infos to backend handler
	 *
	 * @param dbh
	 */
	private void processTrans(FFSConnectionHolder dbh, ArrayList transferList, EventInfo eventInfo)
			throws Exception {
		String methodName = "com.ffusion.ffs.bpw.handler.TransferHandler.processTrans";

		// convert ArrayList to array and call Backend interface
		TransferInfo[] infos = (TransferInfo[]) transferList
				.toArray(new TransferInfo[0]);

		HashMap extraInfo = new HashMap(); // default no extra

		// process the transfers in the array
		try {
			this.processTransfer(infos, extraInfo, eventInfo);
		} catch (Exception e) {
			String error = methodName + ":Exception thrown by "
					+ _transferBackendHandler.getClass().getName();
			FFSDebug.log(e, error);
			dbh.conn.rollback();
            throw (e);          // QTS 694621 - throw the exception so that the handler fails
                                // so it can be rerun from crash recovery
		}
		// the second commit this batch of transfers
		dbh.conn.commit();

	}
	
	public void processTransfer(TransferInfo[] transferInfos, HashMap extra, EventInfo eventInfo)throws Exception{
		String thisMethod = "com.ffusion.ffs.bpw.handler.TransferHandler.processTransfer : ";
		long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(thisMethod,start);
		HashMap extraFields = null;
		TransferInfo transInfo = null;
		CustomerInfo custInfo = null;
		
		// Use this connection to update event details in DB.
		FFSConnectionHolder dbhEvent = new FFSConnectionHolder();
		dbhEvent.conn = DBUtil.getConnection();
		
		try {
			// Get system user ID from common configuration
			int systemUserID = getSystemUserID();
    		
			for(int transInfoIndex = 0; transInfoIndex <transferInfos.length; transInfoIndex++) {
				transInfo = transferInfos[transInfoIndex];
				if(transInfo != null && "1".equals(transInfo.getEventId())) {
					// Populate customer information
					custInfo = Customer.getCustomerByID(transInfo.getCustomerId(), dbhEvent);
					transInfo.setCustomerInfo(custInfo);
					
					// Set "IS_IMMEDIATE_TRANSACTION" to false as it is schedule transfer. This will be used to identify batch transfer in back end.
					if(transferInfos[transInfoIndex].extraFields != null) {
						extraFields = (HashMap) transInfo.extraFields;
					} else {
						extraFields = new HashMap();
					}
					extraFields.put(IS_IMMEDIATE_TRANSACTION, DBConsts.FALSE);
					transInfo.extraFields = extraFields;
					
					// Set user type to system, as schedule is initiated by the system.
					transInfo.setPostingUserType(SecureUser.TYPE_SYSTEM);
					
					// Set user Id in TransferInfo object which is used by banking services for read audit logging.
					transInfo.setPostingUserId(systemUserID);
					
					if (isBasBackendEnabled && eventInfo.ScheduleFlag == ScheduleConstants.SCH_FLAG_RESUBMIT) {
						// Do not create an event for resubmit transfer if BaS is enabled. 
					} else {
						// Create an event which is used for the retry status
						// check and reconciliation.
						createEvent(dbhEvent, transInfo, eventInfo, extraFields);
					}
				}
			}
			
			// Persist event details to DB
			dbhEvent.conn.commit();
		} catch (Exception ex) {
			dbhEvent.conn.rollback();
			FFSDebug.log(thisMethod + ex);
			PerfLoggerUtil.stopPerfLogging(thisMethod, start, uniqueIndex);
			throw ex;
		} finally {
			DBUtil.freeConnection(dbhEvent.conn);
		}
		
		// Get the valid transfers which will be resubmitting to the payment engine.
		if (isBasBackendEnabled && eventInfo.ScheduleFlag == ScheduleConstants.SCH_FLAG_RESUBMIT) {
			transferInfos = getvalidResubmitBaSTransfers(eventInfo, transferInfos, extra);
		}
		
		_transferBackendHandler.processTransfer(transferInfos, extra);
		PerfLoggerUtil.stopPerfLogging(thisMethod, start, uniqueIndex);
	}

	/** Return configured system user ID, which is used for read audit logging in BaS
	 * @return int Configured system user ID
	 */
	private int getSystemUserID() {
		int systemUserID = -1;
		try {
			String userID = commonConfigBO.getConfigProperty(ConfigConstants.SYSTEM_USER_ID);

			if(StringUtil.isNotEmpty(userID)) {

				systemUserID = Integer.parseInt(userID);
		    } 
		} catch (NumberFormatException ex) {
	    	FFSDebug.log("Invalid user Id");
	    } catch (Exception ex) {
	    	FFSDebug.log("Invalid user Id", ex);
	    }
		return systemUserID;
	}
	
	public void startProcessTransfer(EventInfo evtInfo, HashMap extra) {	
		_transferBackendHandler.startProcessTransfer(evtInfo, extra);			
	}

	/**
	 * Get the first ACHFIInfo of the CC company(compId) whose flag CashConDFI
	 * is on
	 *
	 * @param dbh
	 * @param FIId
	 * @return
	 * @exception FFSException
	 */
	private ACHFIInfo getCutOffACHFIInfo(FFSConnectionHolder dbh, String FIId)
			throws FFSException {
		ACHFIInfo achFIInfo = ACHFI.getCutOffACHFIInfo(dbh, FIId);
		if (achFIInfo == null || achFIInfo.getStatusCode() != DBConsts.SUCCESS) {
			throw new FFSException(achFIInfo.getStatusCode(),
					"Failed to get ACHFI with CutOff for FIId: " + FIId + ". "
							+ achFIInfo.getStatusMsg());
		}
		return achFIInfo;
	}

	/**
	 * For matured external accounts, update its prenote status to success For
	 * unmatured external accounts, fail all transfers by the external account
	 * Ids
	 *
	 * @param dbh
	 * @param fiId
	 *            FIID
	 * @exception Exception
	 */
	public void processExtAccountWithPrenote(FFSConnectionHolder dbh,
			String fiId) throws Exception {

		String currMethodName = "TransferHandler.processExtAccountWithPrenote: ";
		long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(currMethodName,start);
		FFSDebug.log(currMethodName + "begins", PRINT_DEV);
		// get today
		Calendar cal = Calendar.getInstance();
		java.text.SimpleDateFormat s = new java.text.SimpleDateFormat(
				"yyyyMMdd");
		String todayStr = s.format(cal.getTime());
		int startDateInt = Integer.parseInt(todayStr);
        int prenoteBusinessDays = getPrenoteBusinessDays();
       

		
		// move to 6 previous business day
		for (int i = 0; i < prenoteBusinessDays; i++) {

			startDateInt = SmartCalendar.getACHBusinessDay(fiId, startDateInt,
					false); // false: previous
		}

		// String: 20031011
		String matureDateStr = (new Integer(startDateInt)).toString();

		// Date: 20031011
		Date matureDate = s.parse(matureDateStr);

		// parse to validate format of duedate
		// Date: 2003/10/11
		java.text.SimpleDateFormat s2 = new java.text.SimpleDateFormat(
				DBConsts.START_TIME_FORMAT);
		s2.setLenient(false);
		String formattedMatureDateStr = s2.format(matureDate);


		updateMaturedExtAccountWithPrenote(dbh, fiId, formattedMatureDateStr);

		// We should get all unmatured
		// external accounts, change their statuses from null to
		// PENDING. create a prenote transfers, put them in a
		// different array list called prenoteTransferList.
		processUnMaturedExtAccountWithPrenote(dbh, fiId);

		// commit the transaction
		dbh.conn.commit();

		FFSDebug.log(currMethodName + " ends", PRINT_DEV);
		PerfLoggerUtil.stopPerfLogging(currMethodName, start, uniqueIndex);
	}
	
	/**
	 * Gets the number of business days a prenote before the payee can be used.
	 * 
	 * @return prenoteBusinessDays
	 */
	public int getPrenoteBusinessDays() {
		String methodName = "TransferHandler.getPrenoteBusinessDays";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
		int prenoteBusinessDays = DBConsts.DEFAULT_BPW_ACH_PAYEE_PRENOTE_BUSINESS_DAYS;
		try {
			prenoteBusinessDays = getPrenoteBusinessDays(DBConsts.BPW_ACH_PAYEE_PRENOTE_BUSINESS_DAYS, 
					DBConsts.DEFAULT_BPW_ACH_PAYEE_PRENOTE_BUSINESS_DAYS);
		} catch (Exception ex) {
			PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
			// Do nothing
		}
		PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
		return prenoteBusinessDays;
	}

	/**
	 * Gets prenote or same day prenote business days.
	 * 
	 * @param prenoteBusinessDays
	 * @param defaultPrenoteBusinessDays
	 * @return prenoteBusinessDays
	 */
	public int getPrenoteBusinessDays(String prenoteBusinessDays, int defaultPrenoteBusinessDays) {
		// Default is BPW managed customer
		return Integer.valueOf(_propertyConfig.otherProperties.getProperty(prenoteBusinessDays, String.valueOf(defaultPrenoteBusinessDays)));
	}

	/**
	 * Update prenote status of external accounts who require prenote, statuses
	 * are pending and matured
	 *
	 * @param dbh
	 * @param fiId
	 * @param formattedMatureDateStr
	 * @exception Exception
	 */
	private void updateMaturedExtAccountWithPrenote(FFSConnectionHolder dbh,
			String fiId, String formattedMatureDateStr) throws Exception {
		// get all mature Deposit Location if the log level is statusupdate
		ExtTransferAcctInfo[] acctInfos = null;
		if (_doAuditLog) {
			acctInfos = getMaturedExtTransferAcctInfo(dbh, fiId, formattedMatureDateStr);
		}

		if (acctInfos == null) {
			return;
		}
		
		updateMaturedExtAcctPrenoteStatus(dbh, fiId, formattedMatureDateStr);

		// log into auditLog: one by one
		int len = 0;
		if (acctInfos != null) {
			len = acctInfos.length;
		}
		if (_doAuditLog && len > 0) {

			for (int i = 0; i < len; i++) {
				ILocalizable msg = BPWLocaleUtil
						.getLocalizableMessage(
								AuditLogConsts.AUDITLOG_MSG_EXTERNALTRANSFER_EXT_XFER_HANDLER_UPDATE_PRENOTE_STATUS,
								null,
								BPWLocaleUtil.EXTERNALTRANSFER_AUDITLOG_MESSAGE);
				ExternalTransferAccount.logExtTransferAccountTransAuditLog(dbh,
						acctInfos[i], msg,
						"Update prenote status of an External Account",
						AuditLogTranTypes.BPW_EXTERNALTRANSFERHANDLER);

			}
		}
		dbh.conn.commit();
	}

	/**
	 *
	 * @param evts
	 * @param bpwFI
	 * @param eventSequence
	 * @return
	 */
	private TransferInfo createDummyTransferInfo(EventInfoArray evts,
			BPWFIInfo bpwFI, int eventSequence) {

		String fIId = evts._array[0].FIId;
		String processId = evts._array[0].processId;
		String srvrTID = evts._array[0].InstructionID;

		TransferInfo transInfo = new TransferInfo();
		transInfo.setAmount("0");
		transInfo.setSrvrTId(srvrTID);
		transInfo.setFIId(fIId);
		transInfo.setDateCreate(FFSUtil.getDateString("yyyyMMdd"));
		transInfo.setDateDue(FFSUtil.getDateString("yyyyMMdd"));
		transInfo.setDateToPost(FFSUtil.getDateString("yyyyMMdd"));
		transInfo.setLastProcessId(processId);
		transInfo.setTransferType(DBConsts.PMTTYPE_CURRENT);
		transInfo.setTransferDest(DBConsts.BPW_TRANSFER_DEST_ETOI);
		transInfo.setBankFromRtn(bpwFI.getFIRTN());
		transInfo.setAccountFromNum("DummyAccount");
		transInfo.setPrcStatus(DBConsts.POSTEDON);
		transInfo.setStatusCode(ACHConsts.SUCCESS);
		transInfo.setStatusMsg(ACHConsts.SUCCESS_MSG);

		// set its eventId as eventSequence
		transInfo.setEventId("" + eventSequence);
		transInfo.setDbTransKey(_batchKey);
		
		return transInfo;
	}

	/**
	 *
	 * @param dbh
	 * @param transfer
	 * @param reRunCutOff
	 * @param msg
	 * @param debugDesc
	 * @exception FFSException
	 */
	private void doTransAuditLog(FFSConnectionHolder dbh,
			TransferInfo transfer, boolean reRunCutOff, ILocalizable msg,
			String debugDesc) throws FFSException {
		String currMethodName = "TransferHandler.doTransAuditLog:";

		try {

			if (reRunCutOff == true) {
				// don't log anything if it is rerun cut off
				return;
			}
			if (_doAuditLog) {
				// convert amount to BigDecimal with 2 decimal places
				java.math.BigDecimal amount = new java.math.BigDecimal(transfer
						.getAmount());

				// get customerId
				int customerId = 0;
				try {
					customerId = Integer.parseInt(transfer.getCustomerId());
				} catch (NumberFormatException nfe) {
					String errDescrip = currMethodName
							+ " CustomerId is not an integer - "
							+ transfer.getCustomerId() + " - " + nfe;
					FFSDebug.log(errDescrip + FFSDebug.stackTrace(nfe),
							FFSDebug.PRINT_ERR);
					throw new FFSException(nfe, errDescrip);
				}

				// get description
				String desc = debugDesc + " Transfer server TID  = "
						+ transfer.getSrvrTId();
				FFSDebug.log(currMethodName + desc, FFSDebug.PRINT_DEV);

				int tranType = AuditLogTranTypes.BPW_EXTERNALTRANSFERHANDLER;

				if (transfer.getPrcStatus().equals(DBConsts.INPROCESS) == true) {
					if (transfer.getCategory() == null)
						tranType = AuditLogTranTypes.BPW_EXTERNAL_TRANSFER_SENT;
					else if (transfer.getCategory() != null
							&& transfer.getCategory().equals(
									ACHConsts.EMPLOYEE_CATEGORY))
						tranType = AuditLogTranTypes.BPW_EMPLOYEE_PAYMENT_SENT;
					else if (transfer.getCategory() != null
							&& transfer.getCategory().equals(
									ACHConsts.VENDOR_CATEGORY))
						tranType = AuditLogTranTypes.BPW_VENDOR_PAYMENT_SENT;
				}

				String toAcctId = null;
				String toAcctRTN = null;
				String fromAcctId = null;
				String fromAcctRTN = null;

				// Get the ToAccountNum and ToAccountType given ExtAcctId
				ExtTransferAcctInfo extTransferAcctInfo = new ExtTransferAcctInfo();
				String acctId = null;
				if (DBConsts.BPW_TRANSFER_DEST_ITOE.equalsIgnoreCase(transfer
						.getTransferDest())) {
					// Then ToAccount is external
					acctId = transfer.getAccountToId();
				} else if (DBConsts.BPW_TRANSFER_DEST_ETOI
						.equalsIgnoreCase(transfer.getTransferDest())) {
					// Then FromAccount is external
					acctId = transfer.getAccountFromId();
				} else {
					// Add support for other destinations in the future
				}
				extTransferAcctInfo.setAcctId(acctId);
				extTransferAcctInfo = ExternalTransferAccount
						.getExternalTransferAccount(dbh, extTransferAcctInfo);

				if (extTransferAcctInfo.getStatusCode() == ACHConsts.SUCCESS) {
					if (DBConsts.BPW_TRANSFER_DEST_ITOE.equals(transfer
							.getTransferDest())) {
						toAcctId = AccountUtil.buildExtTransferAcctId(extTransferAcctInfo);
						toAcctRTN = extTransferAcctInfo.getAcctBankRtn();
					} else if (DBConsts.BPW_TRANSFER_DEST_ETOI.equals(transfer
							.getTransferDest())) {
						fromAcctId = AccountUtil.buildExtTransferAcctId(extTransferAcctInfo);
						fromAcctRTN = extTransferAcctInfo.getAcctBankRtn();
					}
				}
				// Just use whatever is in TransferInfo
				if (toAcctId == null) {
					toAcctId = AccountUtil.buildTransferToAcctId(transfer);
					toAcctRTN = transfer.getBankToRtn();
				}
				if (fromAcctId == null) {
					fromAcctId = AccountUtil.buildTransferFromAcctId(transfer);
					fromAcctRTN = transfer.getBankFromRtn();
				}

				Object[] dynamicContent = new Object[2];
				dynamicContent[0] = msg;
				dynamicContent[1] = transfer.getSrvrTId();
				msg = BPWLocaleUtil
						.getLocalizableMessage(
								AuditLogConsts.AUDITLOG_MSG_EXTERNALTRANSFER_TRANSFER_HANDLER_DO_TRANS_AUDIT,
								dynamicContent,
								BPWLocaleUtil.EXTERNALTRANSFER_AUDITLOG_MESSAGE);

				AuditLogRecord _auditLogRec = new AuditLogRecord(transfer
						.getSubmittedBy(), null, null, msg,
						transfer.getLogId(), tranType, customerId, amount,
						transfer.getAmountCurrency(), transfer.getSrvrTId(),
						transfer.getPrcStatus(), toAcctId, toAcctRTN,
						fromAcctId, fromAcctRTN, 0);
				TransAuditLog.logTransAuditLog(_auditLogRec, dbh.conn
						.getConnection());
			}
		} catch (Exception ex) {
			String errDescrip = currMethodName + " failed " + ex;
			FFSDebug.log(errDescrip + FFSDebug.stackTrace(ex),
					FFSDebug.PRINT_ERR);
			throw new FFSException(ex, errDescrip);
		}

	}

	/**
	 * Get all un-matured external accounts for the fiid. change status for each
	 * account to PENDING
	 *
	 * @param dbh
	 * @param fiId
	 * @exception Exception
	 */
	private void processUnMaturedExtAccountWithPrenote(FFSConnectionHolder dbh,
			String fiId) throws Exception {

		ExtTransferAcctInfo[] acctInfos = getUnMaturedExtTransferAcctInfo(dbh, fiId, false); // false: include unmanaged accounts
																	
		TransferInfo info = null;

		if (acctInfos != null) {
			int len = acctInfos.length;
			String submitDate = FFSUtil
					.getDateString(DBConsts.START_TIME_FORMAT);

			for (int i = 0; i < len; i++) {

				ExtTransferAcctInfo acctInfo = acctInfos[i];

				if (needToProcessPrenote(acctInfo) == true) {
					/*
					 * this account required prenote update acct's status (to
					 * PENDING) and prenote submit date. create prenote transfer
					 */
					this.updateAcctPrenote(dbh, acctInfo,
							DBConsts.ACH_PAYEE_PRENOTE_PENDING, submitDate);

					// create a prenote transfer and add in the list.
					info = createPrenoteTransfer(dbh, acctInfo);
					prenoteTransferList.add(info);
				}

			}
			dbh.conn.commit();
		}
	}

	/**
	 * Create/add a prenote transfer in DB Add status as WILLPROCESSON don't add
	 * processId now. We will update processId later
	 *
	 * @param dbh
	 * @param extTransferAcct
	 * @return
	 * @exception FFSException
	 */
	private TransferInfo createPrenoteTransfer(FFSConnectionHolder dbh,
			ExtTransferAcctInfo extTransferAcct) throws FFSException {
		TransferInfo prenoteInfo = new TransferInfo();
		prenoteInfo.setAmount("0");
		prenoteInfo.setCustomerId(extTransferAcct.getCustomerId());
		prenoteInfo.setFIId(_bpwFIInfo.getFIId());
		prenoteInfo.setDateCreate(FFSUtil.getDateString("yyyyMMdd"));
		prenoteInfo.setDateDue(FFSUtil.getDateString("yyyyMMdd"));
		String today = "" + DBUtil.getCurrentStartDate(); // yyyyMMdd00

		// DateToPost and ProcessDate are set as today's date.
		prenoteInfo.setDateToPost(today);
		prenoteInfo.setProcessDate(today);

		prenoteInfo.setExternalAcctId(extTransferAcct.getAcctId());
		prenoteInfo.setLogId(extTransferAcct.getLogId());
		prenoteInfo.setSubmittedBy(extTransferAcct.getSubmittedBy());
		prenoteInfo.setOriginatingUserId(extTransferAcct.getSubmittedBy());
		prenoteInfo.setTransferType(DBConsts.PMTTYPE_CURRENT);
		prenoteInfo.setTransferCategory(DBConsts.PRENOTE_ENTRY);
		prenoteInfo.setBankFromRtn(_bpwFIInfo.getFIRTN());
		prenoteInfo.setAccountFromNum("PrenoteAccount");
		prenoteInfo.setAccountFromType(extTransferAcct.getAcctType());
		prenoteInfo.setPrcStatus(DBConsts.WILLPROCESSON);

		// No matter it is managed or unmanaged ETF account, prenoteType is
		// already set
		String prenoteType = extTransferAcct.getPrenoteType();
		if (prenoteType != null) {

			if (prenoteType
					.equals(DBConsts.BPW_EXTERNAL_ACCOUNT_PRENOTE_TYPE_CCD_CREDIT)) {
				prenoteInfo.setTypeDetail(ACHConsts.ACH_RECORD_SEC_CCD);
				prenoteInfo.setTransferDest(DBConsts.BPW_TRANSFER_DEST_ITOE); // credit
			} else if (prenoteType
					.equals(DBConsts.BPW_EXTERNAL_ACCOUNT_PRENOTE_TYPE_CCD_DEBIT)) {
				prenoteInfo.setTypeDetail(ACHConsts.ACH_RECORD_SEC_CCD);
				prenoteInfo.setTransferDest(DBConsts.BPW_TRANSFER_DEST_ETOI); // debit
			} else if (prenoteType
					.equals(DBConsts.BPW_EXTERNAL_ACCOUNT_PRENOTE_TYPE_PPD_CREDIT)) {
				prenoteInfo.setTypeDetail(ACHConsts.ACH_RECORD_SEC_PPD);
				prenoteInfo.setTransferDest(DBConsts.BPW_TRANSFER_DEST_ITOE); // credit
			} else if (prenoteType
					.equals(DBConsts.BPW_EXTERNAL_ACCOUNT_PRENOTE_TYPE_PPD_DEBIT)) {
				prenoteInfo.setTypeDetail(ACHConsts.ACH_RECORD_SEC_PPD);
				prenoteInfo.setTransferDest(DBConsts.BPW_TRANSFER_DEST_ETOI); // debit
			} else if (prenoteType
					.equals(DBConsts.BPW_EXTERNAL_ACCOUNT_PRENOTE_TYPE_WEB)) {
				prenoteInfo.setTypeDetail(ACHConsts.ACH_RECORD_SEC_WEB);
				prenoteInfo.setTransferDest(DBConsts.BPW_TRANSFER_DEST_ETOI); // since
																				// WEB
																				// is
																				// Debit
																				// ONLY
			} else if (prenoteType
					.equals(DBConsts.BPW_EXTERNAL_ACCOUNT_PRENOTE_TYPE_TEL)) {
				prenoteInfo.setTypeDetail(ACHConsts.ACH_RECORD_SEC_TEL);
				prenoteInfo.setTransferDest(DBConsts.BPW_TRANSFER_DEST_ETOI); // since
																				// WEB
																				// is
																				// Debit
																				// ONLY
			}
		} else {
			/*
			 * In case prenote type is not provided prenote transfer is credit,
			 * if recepientType is Busniess, SEC code is CCD, and if
			 * recepientType is Personal, SEC code is PPD.
			 */

			// credit
			prenoteInfo.setTransferDest(DBConsts.BPW_TRANSFER_DEST_ITOE); // credit

			if (extTransferAcct.getRecipientType().equals(
					DBConsts.EXT_TRN_ACCT_RECIPIENT_TYPE_BUSINESS)) {
				prenoteInfo.setTypeDetail(ACHConsts.ACH_RECORD_SEC_CCD);
			} else { // personal
				prenoteInfo.setTypeDetail(ACHConsts.ACH_RECORD_SEC_PPD);
			}
		}

		if (prenoteInfo.getTransferDest().compareTo(
				DBConsts.BPW_TRANSFER_DEST_ITOE) == 0) {
			prenoteInfo.setAccountToInfo(extTransferAcct);
		} else {
			prenoteInfo.setAccountFromInfo(extTransferAcct);
		}

		//
		// get the external company and put it in the prenote trasfer
		//

		// get ext companies by FIID / customerid
		ExtTransferCompanyInfo[] etfComps = ExternalTransferCompany
				.getETFCompanyByFIIDAndCustomerId(dbh, _bpwFIInfo.getFIId(),
						extTransferAcct.getCustomerId());

		// If there is only one object in the array, get that one. Otherwise,
		// get the
		// default external transfer company whose customerId = -1
		if (etfComps != null) {
			int len = etfComps.length;
			if (len == 1) {
				prenoteInfo.setExtTransferCompanyInfo(etfComps[0]);
			} else {
				for (int i = 0; i < len; i++) {
					if (etfComps[i].getCustomerId().equals(
							ETF_COMPANY_CUSTOMERID_NULL)) {
						prenoteInfo.setExtTransferCompanyInfo(etfComps[i]);
						break;
					}
				}
			}
		}

		//
		// get the customerInfo
		//
		CustomerInfo customerInfo = Customer.getCustomerInfo(extTransferAcct
				.getCustomerId(), dbh, extTransferAcct);

        prenoteInfo.setCategory(extTransferAcct.getAcctCategory());
        setTransferFundsProcessing(prenoteInfo);
        prenoteInfo.setAction(DBConsts.BPW_TRANSFER_ACTION_ADD);
        //
		// add the prenote in db
		//
        
        // Set Prenote transfer info - 0 = normal Prenote, 1= SameDay Prenote
        setPrenoteTransferInfo(prenoteInfo);
        
		prenoteInfo = Transfer.addTransferFromAdapter(dbh, prenoteInfo, false,
				_bpwFIInfo, customerInfo); // false: single

		if (prenoteInfo.getStatusCode() != DBConsts.SUCCESS) {
			throw new FFSException(prenoteInfo.getStatusCode(), prenoteInfo
					.getStatusMsg());
		}

		ILocalizable msg = BPWLocaleUtil
				.getLocalizableMessage(
						AuditLogConsts.AUDITLOG_MSG_EXTERNALTRANSFER_TRANSFER_HANDLER_CREATED_PRENOTE_FOR_EXT_ACCT,
						null, BPWLocaleUtil.EXTERNALTRANSFER_AUDITLOG_MESSAGE);

		this
				.doTransAuditLog(dbh, prenoteInfo, false, msg,
						"Successfully created prenote entry for this external account.");

		return prenoteInfo;
	}
	
	
	/**
     * This method figures out if this transfer will
     * support FundsProcessing or not. If funds processing
     * is supported, it will also figure out if the
     * processing type is immediate or scheduled.
     * The algorithm is:
     * if FundsProcessing == 0, use system configuration to determine
     * if FundsProcessing == 1, do not support funds processing
     * if FundsProcessing == 2, support funds processing regardless of system
     *                          configuration
     *
     * @param transferInfo
     *               The transfer.
     */
    private void setTransferFundsProcessing(TransferInfo transferInfo) {

        // Set the useFundsProcessing value
        String trnDest = transferInfo.getTransferDest();
        int procType = transferInfo.getProcessType();
        int fundsProc = transferInfo.getFundsProcessing();

        if (trnDest.equalsIgnoreCase(DBConsts.BPW_TRANSFER_DEST_ITOE)) {
            // Determine processType if not provided
            if (procType == DBConsts.BPW_TRANSFER_PROCESS_TYPE_PER_SYSTEM) {
                // Determine using system configuration
                if (supportItoEImmediate == true) {
                    procType = DBConsts.BPW_TRANSFER_PROCESS_TYPE_IMMED;
                } else {
                    procType = DBConsts.BPW_TRANSFER_PROCESS_TYPE_SCHED;
                }
            }
            // Determine fundsProcessing if not provided
            if (fundsProc == DBConsts.BPW_TRANSFER_FUNDS_PROCESSING_PER_SYSTEM) {
                if (procType == DBConsts.BPW_TRANSFER_PROCESS_TYPE_IMMED) {
                    if (supportItoEImmediateFunds == true) {
                        fundsProc = DBConsts.BPW_TRANSFER_FUNDS_PROCESSING_SUPPORTED;
                    } else {
                        fundsProc = DBConsts.BPW_TRANSFER_FUNDS_PROCESSING_NOT_SUPPORTED;
                    }
                } else if (procType == DBConsts.BPW_TRANSFER_PROCESS_TYPE_SCHED) {
                    if (supportItoEScheduledFunds == true) {
                        fundsProc = DBConsts.BPW_TRANSFER_FUNDS_PROCESSING_SUPPORTED;
                    } else {
                        fundsProc = DBConsts.BPW_TRANSFER_FUNDS_PROCESSING_NOT_SUPPORTED;
                    }
                } else {
                    fundsProc = DBConsts.BPW_TRANSFER_FUNDS_PROCESSING_NOT_SUPPORTED;
                }
            }
        } else if (trnDest.equalsIgnoreCase(DBConsts.BPW_TRANSFER_DEST_ETOI)) {
            if (procType == DBConsts.BPW_TRANSFER_PROCESS_TYPE_PER_SYSTEM) {
                if (supportEtoIImmediate == true) {
                    procType = DBConsts.BPW_TRANSFER_PROCESS_TYPE_IMMED;
                } else {
                    procType = DBConsts.BPW_TRANSFER_PROCESS_TYPE_SCHED;
                }
            }
            if (fundsProc == DBConsts.BPW_TRANSFER_FUNDS_PROCESSING_PER_SYSTEM) {
                if (procType == DBConsts.BPW_TRANSFER_PROCESS_TYPE_IMMED) {
                    if (supportEtoIImmediateFunds == true) {
                        fundsProc = DBConsts.BPW_TRANSFER_FUNDS_PROCESSING_SUPPORTED;
                    } else {
                        fundsProc = DBConsts.BPW_TRANSFER_FUNDS_PROCESSING_NOT_SUPPORTED;
                    }
                } else if (procType == DBConsts.BPW_TRANSFER_PROCESS_TYPE_SCHED) {
                    if (supportEtoIScheduledFunds == true) {
                        fundsProc = DBConsts.BPW_TRANSFER_FUNDS_PROCESSING_SUPPORTED;
                    } else {
                        fundsProc = DBConsts.BPW_TRANSFER_FUNDS_PROCESSING_NOT_SUPPORTED;
                    }
                } else {
                    fundsProc = DBConsts.BPW_TRANSFER_FUNDS_PROCESSING_NOT_SUPPORTED;
                }
            }
        } else {
            // Invalid trnDest provided, don't do anything
        }

        transferInfo.setFundsProcessing(fundsProc);
        transferInfo.setProcessType(procType);        
    }// setTransferFundsProcessing

	/**
	 * Update prenote status and sub date for account
	 *
	 * @param dbh
	 * @param extTransferAcct
	 * @param prenoteStatus
	 * @param subDate
	 */
    private void updateAcctPrenote(FFSConnectionHolder dbh,
			ExtTransferAcctInfo extTransferAcct, String prenoteStatus,
			String subDate)

	throws FFSException {
		extTransferAcct.setPrenoteStatus(prenoteStatus);

		extTransferAcct.setPrenoteSubDate(subDate);

		extTransferAcct = ExternalTransferAccount.modifyPrenote(dbh,
				extTransferAcct, true);
	}

	/**
	 * Check whether we need to create prenote entry or not
	 *
	 * @param acctInfo
	 * @return
	 */
	private boolean needToProcessPrenote(ExtTransferAcctInfo acctInfo) {

		String methodName = "TransferHandler.needToProcessPrenote: ";
		String prenoteStatus = acctInfo.getPrenoteStatus();
		FFSDebug.log(methodName, " prenote=", acctInfo.getPrenote(),
				FFSDebug.PRINT_DEV);
		FFSDebug.log(methodName, " prenoteStatus=", prenoteStatus,
				FFSDebug.PRINT_DEV);

		if ((acctInfo.getPrenote() != null)
				&& (acctInfo.getPrenote().trim().equalsIgnoreCase("Y"))) {
			// no need to create prenote transfer
			return prenoteStatus == null; // need to create prenote transfer
		}
		return false; // do Prenote is not Y
	}

	private void doAuditLog(FFSConnectionHolder dbh, TransferInfo transferInfo,
			ILocalizable msg) {

		String curMethodName = "TransferHandler.doAuditLog: ";
		String amount = null;
		int businessId = 0;

		if (transferInfo == null) {
			return;
		}

		try {
			amount = transferInfo.getAmount();
			if ((amount == null) || (amount.trim().length() == 0)) {
				amount = "-1";
			}

			// Differentiate between consumer and business
			if ((transferInfo.getCustomerId() != null)
					&& (transferInfo.getCustomerId().length() > 0)) {

				if (transferInfo.getCustomerId().equals(
						transferInfo.getSubmittedBy())) { // Consumer

					businessId = 0;
				} else { // Business
					businessId = Integer.parseInt(transferInfo.getCustomerId());
				}
			}

			String toAcctId = null;
			String toAcctRTN = null;
			String fromAcctId = null;
			String fromAcctRTN = null;

			// Get the ToAccountNum and ToAccountType given ExtAcctId
			ExtTransferAcctInfo extTransferAcctInfo = new ExtTransferAcctInfo();
			String acctId = null;
			if (DBConsts.BPW_TRANSFER_DEST_ITOE.equalsIgnoreCase(transferInfo
					.getTransferDest())) {
				// Then ToAccount is external
				acctId = transferInfo.getAccountToId();
			} else if (DBConsts.BPW_TRANSFER_DEST_ETOI
					.equalsIgnoreCase(transferInfo.getTransferDest())) {
				// Then FromAccount is external
				acctId = transferInfo.getAccountFromId();
			} else {
				// Add support for other destinations in the future
			}
			extTransferAcctInfo.setAcctId(acctId);
			extTransferAcctInfo = ExternalTransferAccount
					.getExternalTransferAccount(dbh, extTransferAcctInfo);

			if (extTransferAcctInfo.getStatusCode() == ACHConsts.SUCCESS) {
				if (DBConsts.BPW_TRANSFER_DEST_ITOE.equals(transferInfo
						.getTransferDest())) {
					toAcctId = AccountUtil.buildExtTransferAcctId(extTransferAcctInfo);
					toAcctRTN = extTransferAcctInfo.getAcctBankRtn();
				} else if (DBConsts.BPW_TRANSFER_DEST_ETOI.equals(transferInfo
						.getTransferDest())) {
					fromAcctId = AccountUtil.buildExtTransferAcctId(extTransferAcctInfo);
					fromAcctRTN = extTransferAcctInfo.getAcctBankRtn();
				}
			}
			// Just use whatever is in TransferInfo
			if (toAcctId == null) {
				toAcctId = AccountUtil.buildTransferToAcctId(transferInfo);
				toAcctRTN = transferInfo.getBankToRtn();
			}
			if (fromAcctId == null) {
				fromAcctId = AccountUtil.buildTransferFromAcctId(transferInfo);
				fromAcctRTN = transferInfo.getBankFromRtn();
			}

			AuditLogRecord auditLogRecord = new AuditLogRecord(
					transferInfo.getSubmittedBy(), // userId
					null, // agentId
					null, // agentType
					msg, // description
					transferInfo.getLogId(),
					AuditLogTranTypes.BPW_FAILEDETFTRN,// tranType
					businessId, // BusinessId
					new BigDecimal(amount), transferInfo.getAmountCurrency(),
					transferInfo.getSrvrTId(), transferInfo.getPrcStatus(),
					toAcctId, // to acct
					toAcctRTN, fromAcctId, // from acct
					fromAcctRTN, -1);

			TransAuditLog.logTransAuditLog(auditLogRecord, dbh.conn
					.getConnection());
		} catch (Exception ex) {
			String errDescrip = curMethodName + " failed " + ex;
			FFSDebug.log(errDescrip + FFSDebug.stackTrace(ex), PRINT_ERR);
		} finally {
		}
	}
	
	 /**
     * Get transfers with status of INPROCESS and match the designated processId and fiid
     * @param dbh
     * @param processId
     * @param fIId
     * @param processNumber
     * @param batchSize
     * @param category
     * @return ArrayList containing TransferInfo objects
     * @throws FFSException
     */
	public ArrayList getInProcessTransfersForFIId(FFSConnectionHolder dbh, 
			String processId, 
			String fIId, 
			int processNumber, 
			int batchSize,
			String category) throws FFSException {
		// 
		return Transfer.getInProcessTransfersForFIId(dbh, processId, fIId, processNumber, batchSize, category, isSameDayEnabledForETF, false /*Not SameDayETFTran*/ );
	}
	
	/**
	 * Get prnote transfers by processId and processNumber
	 * @param dbh
	 * @param processId
	 * @param processNumber
	 * @param category
	 * @return
	 * @throws FFSException
	 */
	 public TransferInfo[] getPrenoteTransferByProcessId(FFSConnectionHolder dbh,
             String processId,
             int processNumber,
             String category) throws FFSException {

		 return Transfer.getPrenoteTransferByProcessId(dbh, processId, processNumber, category, isSameDayEnabledForETF, false /*Not SameDayETFTran*/);
	 }

	 /**
	  *  Get both processed and unprocessed transfers
	  * which have lastChangeDate > processedTime
	  * @param dbh
	  * @param processId
	  * @param fIId
	  * @param processNumber
	  * @param batchSize
	  * @param category
	  * @return ArrayList containing TransferInfo objects
	  * @throws FFSException
	  */
	 public ArrayList getAllTransfersForBackendByFIId(FFSConnectionHolder dbh,
             String processId,
             String fIId,
             int processNumber,
             int batchSize,
             String category) throws FFSException {
		 
		 return Transfer.getAllTransfersForBackendByFIId(dbh, processId, fIId, processNumber, batchSize, category, isSameDayEnabledForETF, false /*Not SameDayETFTran*/);
	 }
	 
	 /**
	  * /** Get all transfers by FIID = ? and DateToPost <= ?
      * order by Compid and TransferDest. Note: if FIID column is
      * null, get FIID of the customerId column. CompId is not in
      * BPW_Transfer table, we need to link it to ETF_Company table
      * by CustomerId. If customerId of BPW_Transfer table is null,
      * we will use its FIID.
      * SQL statement looks like:
      * SELECT transfer. ..., company.CompName, company.CompACHId FROM BPW_Transfer transfer,
      * ETF_Company company WHERE DateToPost <= ? AND
      * ( (transfer.CustomerId is not null AND transfer.CustomerId = company.CustomerId)
      * OR (transfer.CustomerId is null AND transfer.FIId is not null AND
      * transfer.FIId = company.FIId) ORDER BY company.CopmId, transfer.TransferDest
      *
      * Get transfer which have status = WILLPROCESSON
	  * 
	  * @param dbh
	  * @param fIId
	  * @param batchSize
	  * @param category
	  * @return
	  * @throws FFSException
	  */
	 public ArrayList getUnProcessedTransfersForFIId(FFSConnectionHolder dbh,
             String fIId,
             int batchSize,
             String category) throws FFSException {
		 
		return Transfer.getUnProcessedTransfersForFIId(dbh, fIId, batchSize, category, isSameDayEnabledForETF, false /*Not SameDayETFTran*/);
	 }	
	 
	 /**
     * Get the number of external accounts whose date is matured
     *
     * @param dbh
     * @param matureDate The date to be matured
     * @return The array of ExtTransferAcctInfo objects
     * @exception FFSException
     */
    public  ExtTransferAcctInfo[] getMaturedExtTransferAcctInfo(FFSConnectionHolder dbh,
		        String fiId,
		        String formattedMatureDateStr) throws FFSException {

    	return ExternalTransferAccount.getMaturedExtTransferAcctInfo(dbh, fiId, formattedMatureDateStr, isSameDayEnabledForETF, false /*Not SameDayETFTran*/);
    }
    
    /**
     * Update matured ext account prenote status
     *
     * @param dbh
     * @return number of records updated
     * @exception FFSException
     */
    public int updateMaturedExtAcctPrenoteStatus(FFSConnectionHolder dbh,
                                                        String fiId,
                                                        String formattedMatureDateStr) throws FFSException {
    	return ExternalTransferAccount.updateMaturedExtAcctPrenoteStatus(dbh, fiId, formattedMatureDateStr, isSameDayEnabledForETF, false /*Not SameDayETFTran*/);
    }
    
    /**
     * Get all un-matured external accounts for the fiid. change status for each account to PENDING
     * 
     * @param dbh
     * @param fiId
     * @param excludeUnmanaged
     * @return
     * @throws FFSException
     */
    public ExtTransferAcctInfo[] getUnMaturedExtTransferAcctInfo(FFSConnectionHolder dbh,
            String fiId,
            boolean excludeUnmanaged) throws FFSException {
    	// include unmanaged accounts
    	return ExternalTransferAccount.getUnMaturedExtTransferAcctInfo(dbh, fiId, false, isSameDayEnabledForETF, false /*Not SameDayETFTran*/);
    }
    
    /**
     * Set Prenote transfer info - 0 = normal Prenote, 1= SameDay Prenote
     */
    public void setPrenoteTransferInfo(TransferInfo prenoteInfo) {
    	prenoteInfo.setSameDayTransfer(0); // 0 = normal Prenote, 1= SameDay Prenote
    }
    
	public boolean isSameDayEnabledForETF() {
		return isSameDayEnabledForETF;
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
	 * Create event info and persist to database.
	 *
	 * @param dbh FFSConnectionHolder object
	 * @param transferInfo External Transfer object
	 * @param evt event info details
	 * @param extra the extra
	 * @throws FFSException the FFS exception
	 */
	protected void createEvent(FFSConnectionHolder dbhEvent, TransferInfo transferInfo, EventInfo eventInfo, HashMap extra) throws FFSException{
		String thisMethod = "TransferHandler.createEvent : ";
		String reconciliationID = null;
        // Create an entry in EventInfo table which is used for the retry status check and reconciliation.
        try {
        	EventInfo evt = new EventInfo();
        	evt.EventID = eventInfo.EventID;
			evt.FIId = eventInfo.FIId;
			
			if(StringUtil.isEmpty(eventInfo.InstructionID)) {
				evt.InstructionID = transferInfo.getSrvrTId();
			} else {
				evt.InstructionID = eventInfo.InstructionID;
			}
			
			evt.InstructionType = eventInfo.InstructionType;
			evt.processId = eventInfo.processId;
			evt.ScheduleFlag = eventInfo.ScheduleFlag;
			evt.ScheduleID = eventInfo.ScheduleID;
			evt.fileBasedRecovery = eventInfo.fileBasedRecovery;
			evt.createEmptyFile = eventInfo.createEmptyFile;
			evt.cutOffId = eventInfo.cutOffId;
			evt.LogID = transferInfo.getLogId();
        	// Generate reconciliation ID and persist to DB.
			reconciliationID = ReconciliationUtil.getReconciliationId(transferInfo, extra);
        	transferInfo.setReconciliationId(reconciliationID);
			evt.reconciliationId = reconciliationID;
			evt.Status = ScheduleConstants.EVT_STATUS_INPROCESS;
        	
			// Persist event details to DB
			Event.createEvent(dbhEvent, evt);
			
			// Log the event details for external transfer
			EventInfoLog.createEventInfoLog(dbhEvent, evt);
		} catch (Exception ex) {
			FFSDebug.log(ex, thisMethod + " Unable to create event.");
			throw new FFSException(ex, "Unable to create event.");
		}
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
	 * Get valid transfers which will resubmit to the payment engine
	 * @param eventInfo		EventInfo details
	 * @param transferInfos	Collection of transfers
	 * @param extra			HashMap
	 * @return collection of transfers to be resubmit
	 * @throws Exception
	 */
	private TransferInfo[] getvalidResubmitBaSTransfers(EventInfo eventInfo, TransferInfo[] transferInfos, HashMap extra) throws Exception {
		String thisMethod = "TransferHandler.getvalidResubmitBaSTransfers : ";
		// Use this connection to update event details in DB.
		FFSConnectionHolder dbhEvent = new FFSConnectionHolder();
		dbhEvent.conn = DBUtil.getConnection();
		List<TransferInfo> resubmitTransferList = new ArrayList<>();
		try {
			if(transferInfos != null && transferInfos.length > 0) {
				// Get the transfer status from BaS
				Map<String, Object> resultMap = getTransferStatusFromBaS(transferInfos, extra);
				
				// Check valid transfers which has to resubmit to the back end for
				// processing.
				TransferInfo resubmitTransferInfo = null;
				for (int idx=0; idx<transferInfos.length; idx++) {
					resubmitTransferInfo = transferInfos[idx];
					if(resubmitTransferInfo != null) {
						if(StringUtil.isEmpty(resubmitTransferInfo.getSrvrTId())) {
							// This may contains start and end sequence of transfers to be resubmitted. 
							resubmitTransferList.add(resubmitTransferInfo);
						} else {
							if(!resultMap.containsKey(resubmitTransferInfo.getSrvrTId())) {
								resubmitTransferList.add(resubmitTransferInfo);
								
								// Create an event which is used for the retry status
								// check and reconciliation.
								createEvent(dbhEvent, resubmitTransferInfo, eventInfo, (HashMap) resubmitTransferInfo.extraFields);
							}
						}
					}
				}
				// Persist event details to DB
				dbhEvent.conn.commit();
			}
		} catch (Exception ex) {
			FFSDebug.log(thisMethod + ex);
			throw ex;
		} finally {
			DBUtil.freeConnection(dbhEvent.conn);
		}
		return resubmitTransferList.toArray(new TransferInfo[0]);
	}

	/**
	 * Get the transfer status from Payment Engine
	 * @param dbh	FFSConnectionHolder object
	 * @return		Map	Collection of transaction
	 * @throws Exception
	 */
	private Map<String, Object> getTransferStatusFromBaS(TransferInfo[] transferInfos, HashMap extra) throws Exception {
		// Invoke the customer implemented code.
		TransactionStatusProvider transactionStatusProvider = getTransactionStatusProviderService();
		ExtTransferStatus extTransferStatusRef = transactionStatusProvider.getExtTransferStatusInstance();
		
		// The valid transfer list will send to Payment Engine to get the status. The payment engine will return result and update status to BPW.
		List<TransferInfo> transferList = new ArrayList<>();
		TransferInfo transInfo = null;
		for(int transInfoIndex = 0; transInfoIndex <transferInfos.length; transInfoIndex++) {
			transInfo = transferInfos[transInfoIndex];
			if(transInfo != null && "1".equals(transInfo.getEventId())) {
				transferList.add(transInfo);
			} 
		}
		
		// Get the transfer status from back end before resubmitting.
		TransferInfo[] transInfoRslts = extTransferStatusRef.getExernalTransferStatus(transferList.toArray(new TransferInfo[0]), extra);

		// Clear the list
		transferList.clear();
		
		// This map is used to identify resubmit transactions.
		// If Payment Engine received or processed transaction then result map will contain entry for the same.
		// OCB will not resubmit transaction if result object is available for corresponding srvrTID in map.
		Map<String, Object> resultMap = new HashMap<>();

		// Update the result map with SrvrTID and IntraTrnRslt object
		if (transInfoRslts != null && transInfoRslts.length > 0) {
			TransferInfo transInfoRslt = null;
			for (int idx = 0; idx < transInfoRslts.length; idx++) {
				transInfoRslt = transInfoRslts[idx];
				if (transInfoRslt != null && StringUtil.isNotEmpty(transInfoRslt.getSrvrTId())) {
					resultMap.put(transInfoRslt.getSrvrTId(), transInfoRslt);
				}
			}
		}

		return resultMap;
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
