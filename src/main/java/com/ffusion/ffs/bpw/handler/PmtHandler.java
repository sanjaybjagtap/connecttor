//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.ffusion.beans.SecureUser;
import com.ffusion.beans.util.StringUtil;
import com.ffusion.ffs.bpw.BPWServer;
import com.ffusion.ffs.bpw.audit.TransAuditLog;
import com.ffusion.ffs.bpw.custimpl.interfaces.TransactionStatusProvider;
import com.ffusion.ffs.bpw.db.CustPayeeRoute;
import com.ffusion.ffs.bpw.db.Customer;
import com.ffusion.ffs.bpw.db.DBConnCache;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.db.Payee;
import com.ffusion.ffs.bpw.db.PayeeToRoute;
import com.ffusion.ffs.bpw.db.PmtInstruction;
import com.ffusion.ffs.bpw.db.SrvrTrans;
import com.ffusion.ffs.bpw.fulfill.FulfillAgent;
import com.ffusion.ffs.bpw.interfaces.AuditLogConsts;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.CustomerInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfo;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.PayeeInfo;
import com.ffusion.ffs.bpw.interfaces.PayeeRouteInfo;
import com.ffusion.ffs.bpw.interfaces.PaymentStatus;
import com.ffusion.ffs.bpw.interfaces.PmtInfo;
import com.ffusion.ffs.bpw.interfaces.PmtTrnRslt;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.master.BackendProcessor;
import com.ffusion.ffs.bpw.serviceMsg.MsgBuilder;
import com.ffusion.ffs.bpw.util.BPWUtil;
import com.ffusion.ffs.bpw.util.reconciliation.ReconciliationUtil;
import com.ffusion.ffs.bpw.util.resources.BPWLocaleUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.scheduling.constants.ScheduleConstants;
import com.ffusion.ffs.scheduling.db.Event;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.ffusion.util.ILocalizable;
import com.ffusion.util.logging.AuditLogTranTypes;
import com.sap.banking.common.constants.ConfigConstants;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

//=====================================================================
//
// This class contains a callback handler that is registered in the
// InstructionType table.  The registered callback handler will be called
// by the Scheduling engine for the CheckFree Payment Processing.
//
//=====================================================================


public class PmtHandler implements com.ffusion.ffs.bpw.interfaces.DBConsts,
		FFSConst, BPWResource, BPWScheduleHandler {

	/**
	 *
	 */
	private static final long serialVersionUID = -8805636928014324304L;

	// failAssociatedPmts: If true, fail any payment that is associated with a
	// failed payee.
	private boolean failAssociatedPmts = true; // Default to "true" for

	// backwards compatibility.
	
	// This flag is used to validate customer and cust payee route
	private boolean _validateCustPayeeRoute = true;
	
	private PropertyConfig _propertyConfig;
	
	// Reference to the common config BO
    private transient com.sap.banking.common.bo.interfaces.CommonConfig commonConfigBO;
    
    // Flag to identify BaS status
 	private boolean isBasBackendEnabled;
	
	//	This map is used to store event details for BaS resubmit payment
    private final HashMap<String, Object> _basResubmitEvtsMap = new HashMap<>();
	
	public PmtHandler() {
		// Default this configurable to true to maintain backwards
		// compatibility.
		try {
			_propertyConfig = (PropertyConfig) FFSRegistry.lookup(PROPERTYCONFIG);
			String validateCustPayeeRoute = _propertyConfig.otherProperties.getProperty(DBConsts.VALIDATE_CUST_PAYEE_ROUTE,
	        		DBConsts.TRUE);
			_validateCustPayeeRoute = Boolean.valueOf(validateCustPayeeRoute);
			
			String failPayeeFailPmt = BPWServer.getPropertyValue(
					BPW_FAILPAYEE_TOFAILPMT, DBConsts.TRUE);
			// This next statement may look strange, but remember that we want
			// to default to true. i.e. false = false, everything else = true
			failAssociatedPmts = !(failPayeeFailPmt
					.equalsIgnoreCase(DBConsts.FALSE));
			
			commonConfigBO = (com.sap.banking.common.bo.interfaces.CommonConfig) OSGIUtil
					.getBean(com.sap.banking.common.bo.interfaces.CommonConfig.class);
			isBasBackendEnabled = commonConfigBO.isBasBackendEnabled();
			
		} catch (Throwable t) {
			failAssociatedPmts = true;
		}
	}

	// =====================================================================
	// eventHandler()
	// Description: This method is called by the Scheduling engine
	// Arguments: none
	// Returns: none
	// eventSequence:
	// 0 first, 1, normal, 2 last, 3 one batch start, 4 one batch end
	// Note:
	// =====================================================================
	public void eventHandler(int eventSequence, EventInfoArray evts,
			FFSConnectionHolder dbh) throws Exception {
		String methodName = "PmtHandler.eventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
		eventHandler(eventSequence, evts, dbh, false);
		PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
	}


	public void eventHandler(int eventSequence, EventInfoArray evts,
			FFSConnectionHolder dbh, boolean isResubmit) throws Exception {

		String methodName = "PmtHandler.eventHandler: ";
		long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);

		FFSDebug.log("=== PmtHandler.eventHandler: begin, eventSeq="
				+ eventSequence + ",length=" + evts._array.length
				+ ",instructionType=" + evts._array[0].InstructionType,
				PRINT_DEV);

		String fiId = evts._array[0].FIId;
		String instructionType = evts._array[0].InstructionType;
		FulfillAgent fagent = (FulfillAgent) FFSRegistry.lookup(FULFILLAGENT);
		if (fagent == null)
		{
			PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
			throw new Exception("FulfillAgent is null!!");
		}
		// Find the key for this instruction type
		String cacheKey = FulfillAgent.findCacheKey(fiId, instructionType);

		FFSDebug.log(methodName, "cacheKey: ", cacheKey, FFSConst.PRINT_DEV);

		if (null == cacheKey || cacheKey.trim().length() == 0) {
			throw new Exception("Invalid instruction type: " + instructionType);
		}

		List pmtCache = FulfillAgent.getPmtCache(cacheKey);
		FFSDebug.log(methodName, "pmtCache: "+ pmtCache, FFSConst.PRINT_DEV);
		List payeeRouteCache = FulfillAgent.getPayeeRouteCache(cacheKey);

		FFSDebug.log(methodName, "payeeRouteCache: "+ payeeRouteCache, FFSConst.PRINT_DEV);
		
		if (eventSequence == 0) {
			// FIRST sequence
			if ((pmtCache != null && pmtCache.size() > 0)
					|| (payeeRouteCache != null && payeeRouteCache.size() > 0)) {
				throw new Exception("Payment cache is not empty! "
						+ "Time interval maybe too short.");
			}

			try {
				fagent.startPmtBatch(fiId, instructionType, dbh);
			} catch (Exception exc) {
				dbh.conn.rollback();
				PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
				throw new Exception(exc.toString());
			}

			validateCustomerPayeeRoute(dbh, fiId, instructionType);
		} else if (eventSequence == 1) {

			// Return configured system user, which is used for read audit logging in BaS
			int systemUserID = getSystemUserID();
			
			// Use this connection to update event details in DB.
    		FFSConnectionHolder dbhEvent = new FFSConnectionHolder();
    		dbhEvent.conn = DBUtil.getConnection();
    		try {
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
					FFSDebug.log("=== PmtHandler.eventHandler: eventSeq="
							+ eventSequence + ",srvrTID=" + srvrTID, PRINT_DEV);
					
					PmtInstruction pmt = PmtInstruction.getPmtInstr(srvrTID,
							dbh);
					if (pmt == null) {
						String msg = "*** PmtHandler.eventHandler failed: could not find the SrvrTID="
								+ srvrTID + " in BPW_PmtInstruction";
						FFSDebug.console(msg);
						FFSDebug.log(msg);
						continue;
					}
					
					int fileBasedRecovery = evts._array[i].fileBasedRecovery;
					// In non-file based approach, only resubmit payment
					// which are "in-process" state.
					if (fileBasedRecovery == 0 && isResubmit && !(DBConsts.BATCH_INPROCESS.equals(pmt.getStatus())
							|| DBConsts.IMMED_INPROCESS.equals(pmt.getStatus()))) {
						String msg = methodName + " Unable to resubmit bill payment for the SrvrTID=" + srvrTID;
						FFSDebug.log(msg);
						continue;
					}
					PmtInfo pmtinfo = pmt.getPmtInfo();

					// Check the payment's customer-payee status
					int routeId = fagent.getRouteId(fiId, instructionType);

					CustPayeeRoute custPayRoute = CustPayeeRoute
							.getCustPayeeRoute2(pmtinfo.CustomerID,
									pmtinfo.PayeeListID, routeId, dbh);
					String pmtStatus = pmtinfo.Status;
					if ((failAssociatedPmts)
							&& ((custPayRoute == null)
									|| (custPayRoute.Status.equals(FAILEDON)) || (custPayRoute.Status
									.equals(ERROR)))) {

						// Customer Payee is bad (null, in ERROR state, or in
						// FAILEDON state)
						// and the system is configured to fail payments that
						// are associated
						// with this customer payee.
						PmtTrnRslt rslt = new PmtTrnRslt(pmtinfo.CustomerID,
								srvrTID, DBConsts.STATUS_GENERAL_ERROR,
								MsgBuilder.MESSAGE_10501, // Invalid Payee
								pmtinfo.ExtdPmtInfo);
						rslt.logID = pmtinfo.LogID;
						BackendProcessor backendProcessor = new BackendProcessor();

						// Can not process this pmt, give the money back to
						// customer
						// Create revert fund schedule
						backendProcessor.processOneFailedPmt(rslt,
								pmtinfo.LogID, pmtinfo.FIID, dbh);
						
						// perform audit logging
			        	doAuditLog(evts._array[i], dbh, srvrTID, pmtinfo, pmtStatus);
					} else {
                        pmtStatus = BATCH_INPROCESS;
                        if (pmt.getCustomerID() != null && !Customer.isActive(pmt.getCustomerID(), dbh))
                        {
                            pmtStatus = CANCELEDON;
                        }
						PmtInstruction.updateStatus(dbh, srvrTID,
								pmtStatus);
                        if (CANCELEDON.equals(pmtStatus))
                        {
                            continue;
                        }
						// update history
						if (isResubmit) {
							SrvrTrans.updatePmtStatus(dbh, srvrTID,
									WILLPROCESSON);
							
							// Set resubmit flag
		                    evts._array[i].ScheduleFlag = ScheduleConstants.SCH_FLAG_RESUBMIT;
		                    
		                    // Possible duplicate record
		                    pmtinfo.setPossibleDuplicate(true);
						}

						String payeeID = pmt.getPayeeID();

						String linkpayeeid = Payee
								.findLinkPayeeID(payeeID, dbh);
						if (linkpayeeid != null)
							payeeID = linkpayeeid;

						PayeeRouteInfo routeinfo = PayeeToRoute.getPayeeRoute(
								payeeID, pmt.getRouteID(), dbh);
						pmtinfo.payeeInfo = Payee.findPayeeByID(payeeID, dbh);
						if (pmtinfo.payeeInfo == null) {
							String msg = "*** PmtHandler.eventHandler failed: could not find the PayeeID="
									+ payeeID
									+ " in BPW_Payee for pmt of SrvrTID="
									+ srvrTID;
							FFSDebug.console(msg);
							FFSDebug.log(msg);
							continue;
						}
						
						// Populate customer information
						CustomerInfo custInfo = Customer.getCustomerByID(pmtinfo.getCustomerID(), dbh);
						pmtinfo.setCustomerInfo(custInfo);
						
						// Set immediate transaction flag to false for the schedule bill payment.
						HashMap extraMap = null;
						if (pmtinfo.extraFields != null) {
							extraMap = (HashMap) pmtinfo.extraFields;
						} else {
							extraMap = new HashMap();
						}
						extraMap.put(IS_IMMEDIATE_TRANSACTION, DBConsts.FALSE);
						
						// Set extra info to the extraFields of PmtInfo object
						pmtinfo.extraFields = extraMap;
						
						// Set user type to system, as schedule is initiated by the system.
						pmtinfo.setPostingUserType(SecureUser.TYPE_SYSTEM);
						
						// Set system user Id in PmtInfo object which is used by banking services for read audit logging.
						pmtinfo.setPostingUserId(systemUserID);
						
						// In case of BaS, Do not resubmit transaction if OCB received confirmation number from Payment Engine.
	                    // Receiving a Confirmation Number for the transaction would mean that the transaction has reached the Payment Engine. 
				        if (isBasBackendEnabled && isResubmit) {
				        	if(StringUtil.isEmpty(pmtinfo.getConfirmationNumber())) {
				        		pmtCache.add(pmtinfo);
								pmtinfo.payeeInfo.setPayeeRouteInfo(routeinfo);
								payeeRouteCache.add(routeinfo);
				        		_basResubmitEvtsMap.put(srvrTID, evts._array[i]);
				        	}
				        } else {
				        	
				        	pmtCache.add(pmtinfo);
							pmtinfo.payeeInfo.setPayeeRouteInfo(routeinfo);
							payeeRouteCache.add(routeinfo);
							
				        	// Store event details to database. The result handler will use this data to pull status from the back end.
				        	createEvent(dbhEvent, pmtinfo, evts._array[i], extraMap);
		                    
		                    // perform audit logging
				        	doAuditLog(evts._array[i], dbh, srvrTID, pmtinfo, pmtStatus);
				        }
					}
				}	
			// Persist event details to DB
			dbhEvent.conn.commit();
		} catch ( Exception exc ) {
			dbhEvent.conn.rollback();
			FFSDebug.log(methodName + " failed. "+ exc);
            throw exc;
        } finally {
        	PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
        	DBUtil.freeConnection(dbhEvent.conn);
        }
	}else if (eventSequence == 4) {

			// Last in one batch sequence
			try {
				// In case of BaS, Only resubmit payment which status is in process and confirmation number has not received.
				if (isBasBackendEnabled && isResubmit) {
		        	verifyResubmitBaSPayment(dbh, pmtCache, payeeRouteCache);
		        }
				PmtInfo[] pmts = (PmtInfo[]) pmtCache.toArray(new PmtInfo[0]);
				PayeeRouteInfo[] routes = (PayeeRouteInfo[]) payeeRouteCache
						.toArray(new PayeeRouteInfo[0]);
				pmtCache.clear();
				payeeRouteCache.clear();
				fagent.addPayments(pmts, routes, fiId, instructionType, dbh);
			} catch (Exception exc) {
				FFSDebug.log("*** PmtHandler.eventHandler failed:"
						+ exc);
				PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
				throw new Exception(exc.toString());
			}
		} else if (eventSequence == 2) {// LAST sequence
			try {

				FFSDebug.log(methodName, "Ending payment Batch for, FIID: ",
						fiId, ", and Schedule: ", instructionType,
						FFSConst.PRINT_DEV);

				fagent.endPmtBatch(fiId, instructionType, dbh);
			} catch (Exception exc) {
				FFSDebug.log("PmtHandler.eventHandler failed:"
						+ FFSDebug.stackTrace(exc), PRINT_ERR);
				PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
				throw new Exception("PmtHandler.eventHandler failed:"
						+ FFSDebug.stackTrace(exc));
			}
		}
		FFSDebug.log("=== PmtHandler.eventHandler: end", PRINT_DEV);
		PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
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

	public void resubmitEventHandler(int eventSequence, EventInfoArray evts,
			FFSConnectionHolder dbh) throws Exception {
		String methodName = "PmtHandler.resubmitEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
		FFSDebug.log("=== PmtHandler.resubmitEventHandler: begin, eventSeq="
				+ eventSequence + ",length=" + evts._array.length
				+ ",instructionType=" + evts._array[0].InstructionType,
				PRINT_DEV);

		String fiId = evts._array[0].FIId;
		String instructionType = evts._array[0].InstructionType;
		FulfillAgent fagent = (FulfillAgent) FFSRegistry.lookup(FULFILLAGENT);
		if (fagent == null)
			throw new Exception("FulfillAgent is null!!");

		if (eventSequence == 0) {
			// FIRST sequence

			// Only the first sequence requires special handling for
			// crash recovery and resubmit.

			// Check ScheduleFlag to perform event resubmit or crash recovery:
			// - do resubmit event when scheduleFlag=SCH_FLAG_RESUBMIT
			// - do crash recovery when scheduleFlag=SCH_FLAG_NORMAL
			int scheduleFlag = evts._array[0].ScheduleFlag;
			if (scheduleFlag == ScheduleConstants.SCH_FLAG_RESUBMIT) {
				try {
					fagent.startPmtBatch(fiId, instructionType, dbh);
				} catch (Exception exc) {
					dbh.conn.rollback();
					PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
					throw new Exception(exc.toString());
				}

				// retrieve logDate from InstructionID
				String logDate = evts._array[0].InstructionID;

				if (_validateCustPayeeRoute) {
					// Process customers using resubmit.
					try {
						CustomerHandler custhandler = new CustomerHandler();
						custhandler.resubmitCustomers(fiId, instructionType,
								logDate, dbh);
					} catch (Exception exc) {
						FFSDebug.log("Failed to handle customers. Error: "
								+ FFSDebug.stackTrace(exc), PRINT_ERR);
						PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
						throw new Exception("Failed to handle customers. Error: "
								+ FFSDebug.stackTrace(exc));
					}
					
					// Process payees and links using resubmit.
					try {
						PayeeHandler payeehandler = new PayeeHandler();
						payeehandler.resubmitPayees(fiId, instructionType, logDate,
								dbh);
					} catch (Exception exc) {
						FFSDebug.log("PmtHandler.resubmitEventHandler failed:"
								+ FFSDebug.stackTrace(exc), PRINT_ERR);
						PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
						throw new Exception(
								"PmtHandler.resubmitEventHandler failed:"
										+ FFSDebug.stackTrace(exc));
					}
				}
			} else {
				// Do crash recovery when scheduleFlag=SCH_FLAG_NORMAL
				eventHandler(eventSequence, evts, dbh);
			}
		} else {
			// All other phases of crash recovery/resubmit processing
			// is handled in the same way as a normal schedule run.
			eventHandler(eventSequence, evts, dbh, true);
		}

		FFSDebug.log("=== PmtHandler.resubmitEventHandler: end", PRINT_DEV);
		PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
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
	    	FFSDebug.log("Unable to load system user Id");
	    }
		return systemUserID;
	}

	/**	Validate customer payee route information
	 * @param dbh	FFSConnectionHolder with DB connection
	 * @param fiId	Financial Institution ID
	 * @param instructionType	Instruction type
	 * @throws Exception
	 */
	private void validateCustomerPayeeRoute(FFSConnectionHolder dbh, String fiId, String instructionType)
			throws Exception {
		if (_validateCustPayeeRoute) {
			// Process customers
			try {
				CustomerHandler custhandler = new CustomerHandler();
				custhandler.handleCustomers(fiId, instructionType, dbh);
			} catch (Exception exc) {
				FFSDebug.log("Failed to handle customers. Error: "
						+ FFSDebug.stackTrace(exc), PRINT_ERR);
				throw new Exception("Failed to handle customers. Error: "
						+ FFSDebug.stackTrace(exc));
			}
			
			// Process payees and links
			try {
				PayeeHandler payeehandler = new PayeeHandler();
				payeehandler.handlePayees(fiId, instructionType, dbh);
			} catch (Exception exc) {
				FFSDebug.log("PmtHandler.eventHandler failed:"
						+ FFSDebug.stackTrace(exc), PRINT_ERR);
				throw new Exception("PmtHandler.eventHandler failed:"
						+ FFSDebug.stackTrace(exc));
			}
		}
	}

	/** Perform audit logging for bill payment
	 * @param evts	EventInfo
	 * @param dbh	FFSConnectionHolder with DB connection
	 * @param srvrTID	srvrTID
	 * @param pmtinfo	Payment object
	 * @param pmtStatus	Payment Status
	 * @throws FFSException
	 */
	private void doAuditLog(EventInfo evt, FFSConnectionHolder dbh, String srvrTID, PmtInfo pmtinfo,
			String pmtStatus) throws FFSException {
		// log into AuditLog
		if (_propertyConfig.LogLevel >= AUDITLOGLEVEL_STATUSUPDATE) {
			Object[] dynamicContent = new Object[1];
			int tranType = AuditLogTranTypes.BPW_GENERIC_PMTTRN;
			if (evt.InstructionType
					.equals(CHECKFREE_PMTTRN)) {
				dynamicContent[0] = BPWLocaleUtil
						.getLocalizableMessage(
								AuditLogConsts.AUDITLOG_MSG_CHECKFREE,
								null,
								BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
				tranType = AuditLogTranTypes.BPW_CHECKFREE_PMTTRN;
			} else if (evt.InstructionType
					.equals(METAVANTE_PMTTRN)) {
				dynamicContent[0] = BPWLocaleUtil
						.getLocalizableMessage(
								AuditLogConsts.AUDITLOG_MSG_METAVANTE,
								null,
								BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
				tranType = AuditLogTranTypes.BPW_METAVANTE_PMTTRN;
			} else if (evt.InstructionType
					.equals(ON_US_PMTTRN)) {
				dynamicContent[0] = BPWLocaleUtil
						.getLocalizableMessage(
								AuditLogConsts.AUDITLOG_MSG_ONUS,
								null,
								BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
				tranType = AuditLogTranTypes.BPW_ONUS_PMTTRN;
			} else if (evt.InstructionType
					.equals(ORCC_PMTTRN)) {
				dynamicContent[0] = BPWLocaleUtil
						.getLocalizableMessage(
								AuditLogConsts.AUDITLOG_MSG_ORCC,
								null,
								BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
				tranType = AuditLogTranTypes.BPW_ORCC_PMTTRN;
			} else if (evt.InstructionType
					.equals(RPPS_PMTTRN)) {
				dynamicContent[0] = BPWLocaleUtil
						.getLocalizableMessage(
								AuditLogConsts.AUDITLOG_MSG_RPPS,
								null,
								BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
				tranType = AuditLogTranTypes.BPW_RPPS_PMTTRN;
			} else if (evt.InstructionType
					.equals(BANKSIM_PMTTRN)) {
				dynamicContent[0] = BPWLocaleUtil
						.getLocalizableMessage(
								AuditLogConsts.AUDITLOG_MSG_BANKSIM,
								null,
								BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
				tranType = AuditLogTranTypes.BPW_BANKSIM_PMTTRN;
			} else if (evt.InstructionType
					.equals(SAMPLE_PMTTRN)) {
				dynamicContent[0] = BPWLocaleUtil
						.getLocalizableMessage(
								AuditLogConsts.AUDITLOG_MSG_SAMPLE,
								null,
								BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
				tranType = AuditLogTranTypes.BPW_SAMPLE_PMTTRN;
			} else {
				dynamicContent[0] = evt.InstructionType;
			}
			int bizID = Integer.parseInt(pmtinfo.CustomerID);
			BigDecimal pmtAmount = BPWUtil.getBigDecimal(pmtinfo
					.getAmt());
			String debitAcct = BPWUtil.getAccountIDWithAccountType(
					pmtinfo.AcctDebitID, pmtinfo.AcctDebitType);
			ILocalizable msg = BPWLocaleUtil
					.getLocalizableMessage(
							AuditLogConsts.AUDITLOG_MSG_BILLPAY_PROCESSING_BILLPMT,
							dynamicContent,
							BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
			TransAuditLog.logTransAuditLog(
					dbh.conn.getConnection(), pmtinfo.submittedBy,
					null, null, msg, pmtinfo.LogID, tranType,
					bizID, pmtAmount, pmtinfo.CurDef, srvrTID,
					pmtStatus, pmtinfo.PayAcct, null, debitAcct,
					pmtinfo.getBankID(), 0);
		}
	}
	
	/**
	 * Create an event info and persist to database.
	 *
	 * @param dbhEvent FFSConnectionHolder object
	 * @param pmtInfo the Bill Payment object
	 * @param evt event info details
	 * @param extraMap the extra map
	 * @throws FFSException the FFS exception
	 */
	protected void createEvent(FFSConnectionHolder dbhEvent, PmtInfo pmtInfo, EventInfo evt, HashMap extraMap) throws FFSException{
		String thisMethod = "PmtHandler.createEvent : ";
		String reconciliationID = null;
        // Create an entry in EventInfo table which is used for the retry status check and reconciliation.
        try {
        	reconciliationID = ReconciliationUtil.getReconciliationId(pmtInfo, extraMap);
        	pmtInfo.setReconciliationId(reconciliationID);
			evt.reconciliationId = reconciliationID;
			if(ScheduleConstants.SCH_FLAG_RESUBMIT == evt.ScheduleFlag) {
				String eventId = com.ffusion.ffs.bpw.db.DBUtil.getNextIndexString(com.ffusion.ffs.bpw.interfaces.DBConsts.EVENTID);
				EventInfo eventInfo = new EventInfo();
				eventInfo.EventID = eventId;
				eventInfo.ScheduleFlag = evt.ScheduleFlag;
	            eventInfo.InstructionID = evt.InstructionID; // save logDate in InstructionID for EVT_SEQUENCE_FIRST
	            eventInfo.FIId = evt.FIId;
	            eventInfo.InstructionType = evt.InstructionType;
	            eventInfo.cutOffId = evt.cutOffId;
	            eventInfo.processId = evt.processId;
	            eventInfo.createEmptyFile = evt.createEmptyFile;
	            eventInfo.fileBasedRecovery = evt.fileBasedRecovery;
	            eventInfo.reconciliationId = reconciliationID;
	            eventInfo.Status = ScheduleConstants.EVT_STATUS_INPROCESS;
	            Event.createEvent(dbhEvent, eventInfo);
			} else {
				evt.Status = ScheduleConstants.EVT_STATUS_INPROCESS;
				Event.createEvent(dbhEvent, evt);
			}
		} catch (Exception ex) {
			FFSDebug.log(ex, thisMethod + " Unable to create an event.");
			throw new FFSException(ex, "Unable to create an event.");
		}
	}
	
	/**
	 * Validate resubmit bill payment for BaS
	 * @param dbh FFSConnectionHolder object
	 * @param payeeRouteCache List of PayeeRouteInfo
	 * @param pmtCache List of PmtInfo object
	 * @throws Exception
	 */
	private void verifyResubmitBaSPayment(FFSConnectionHolder dbh, List pmtCache, List payeeRouteCache) throws Exception {
		String thisMethod = "PmtHandler.verifyResubmitBaSPayment: ";
		
		if (pmtCache != null && !pmtCache.isEmpty()) {
			// Use this connection to update event details in DB.
    		FFSConnectionHolder dbhLocal = new FFSConnectionHolder();
    		dbhLocal.conn = DBUtil.getConnection();
			try {
				// Get the transfer status from BaS
				Map<String, Object> resultMap = getTransferStatusFromBaS(dbh, pmtCache);
				
				// Valid payment which has to resubmit to the back end for
				// processing.
				PmtInfo resubmitPmtInfo = null;
	
				// Check valid payment which has to resubmit to the back end for
				// processing.
				// If payment status is in process and confirmation is not
				// received from back end, then send payment to the back end for
				// processing.
				Iterator<PmtInfo> it = pmtCache.iterator();
				while (it.hasNext()) {
					resubmitPmtInfo = it.next();
					String srvrTID = resubmitPmtInfo.getSrvrTID();
					if (StringUtil.isNotEmpty(srvrTID)) {
						// If transaction is received or processed by the payment engine then map will have response entry for the corresponding srvrTID
						if (resultMap.containsKey(srvrTID)) {
							// Do not resubmit transfer which status or
							// confirmation number has received.
							it.remove();
							PayeeInfo payeeInfo = resubmitPmtInfo.getPayeeInfo();
							if(payeeInfo != null) {
								PayeeRouteInfo payeeRouteInfo = payeeInfo.getPayeeRouteInfo(); 
								payeeRouteCache.remove(payeeRouteInfo);
							}
						} else {
							EventInfo evtInfo = (EventInfo) _basResubmitEvtsMap.get(srvrTID);
							// Store event details to database. The result handler will use this data to pull status from the back end.
							if(evtInfo != null) {
								// Store event details to database. The result handler will use this data to pull status from the back end.
					        	createEvent(dbhLocal, resubmitPmtInfo, evtInfo, (HashMap) resubmitPmtInfo.extraFields);
			                    
			                    // perform audit logging
					        	doAuditLog(evtInfo, dbh, srvrTID, resubmitPmtInfo, resubmitPmtInfo.getStatus());
							}
						}
					}
				}
				// Persist event details to DB
				dbhLocal.conn.commit();
			} catch (Exception ex) {
				if (pmtCache != null) {
					pmtCache.clear();
				}
				if (payeeRouteCache != null) {
					payeeRouteCache.clear();
				}
				FFSDebug.log(thisMethod + ex);
				throw ex;
			} finally {
				// Do not clear pmtCache here, It contains valid resubmit payment to BaS. 
				if(_basResubmitEvtsMap != null) {
					_basResubmitEvtsMap.clear();
				}
				DBUtil.freeConnection(dbhLocal.conn);
			}
		}
	}
	
	/**
	 * Update payments status from the BaS
	 * @param 		dbh			FFSConnectionHolder with DB connection
	 * @param 		pmtCache	List of PmtInfo object
	 * @return		Map			Collection of transaction
	 * @throws Exception
	 */
	private Map<String, Object> getTransferStatusFromBaS(FFSConnectionHolder dbh, List pmtCache) throws Exception {
        // Generate a new batch key and bind the db connection
        // using the batch key.
		DBConnCache dbConnCache = (DBConnCache) FFSRegistry.lookup(DBCONNCACHE);
		String batchKey = DBConnCache.save(dbh);
		
		// convert ArrayList to array and call Backend interface
		PmtInfo[] pmtInfos = (PmtInfo[]) pmtCache.toArray(new PmtInfo[0]);
		
		// Set batch key to payments
        for(int idx=0; idx<pmtInfos.length; idx++) {
        	pmtInfos[idx].setBatchKey(batchKey);
        }
        
		// Invoke the customer implemented code.
		TransactionStatusProvider transactionStatusProvider = getTransactionStatusProviderService();
		PaymentStatus paymentStatusRef = transactionStatusProvider.getPaymentStatusInstance();

		// Get the status from back end, before resubmitting payment to the
		// processing.
		PmtTrnRslt[] pmtTrnRslts = paymentStatusRef.getPaymentStatus(pmtInfos,
				new HashMap<String, Object>());
		
        // Remove the binding of the db connection and the batch key.
        DBConnCache.unbind(batchKey);
        
        // This map is used to identify resubmit transactions.
 		// If Payment Engine received or processed transaction then result map will contain entry for the same.
 		// OCB will not resubmit transaction if result object is available for corresponding srvrTID in map.
 		Map<String, Object> resultMap = new HashMap<>();

 		// Update the result map with SrvrTID and IntraTrnRslt object
 		if (pmtTrnRslts != null && pmtTrnRslts.length > 0) {
 			PmtTrnRslt rslt = null;
 			for (int idx = 0; idx < pmtTrnRslts.length; idx++) {
 				rslt = pmtTrnRslts[idx];
 				if (rslt != null && StringUtil.isNotEmpty(rslt.srvrTid)) {
 					resultMap.put(rslt.srvrTid, rslt);
 				}
 			}
 		}

 		return resultMap;
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
					if (backendType.equals(transactionStatusProviderRef.getBankingBackendType())) {
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
