//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.ffusion.beans.SecureUser;
import com.ffusion.beans.util.StringUtil;
import com.ffusion.ffs.bpw.audit.AuditAgent;
import com.ffusion.ffs.bpw.audit.TransAuditLog;
import com.ffusion.ffs.bpw.custimpl.interfaces.BackendProvider;
import com.ffusion.ffs.bpw.custimpl.interfaces.TransactionStatusProvider;
import com.ffusion.ffs.bpw.db.Customer;
import com.ffusion.ffs.bpw.db.DBConnCache;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.db.SrvrTrans;
import com.ffusion.ffs.bpw.db.XferInstruction;
import com.ffusion.ffs.bpw.interfaces.AuditLogConsts;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.CustomerInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfo;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.IntraTrnInfo;
import com.ffusion.ffs.bpw.interfaces.IntraTrnRslt;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.ToBackend;
import com.ffusion.ffs.bpw.interfaces.TransferStatus;
import com.ffusion.ffs.bpw.serviceMsg.BPWMsgBroker;
import com.ffusion.ffs.bpw.serviceMsg.RsCmBuilder;
import com.ffusion.ffs.bpw.util.BPWUtil;
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
import com.ffusion.util.ILocalizable;
import com.ffusion.util.enums.UserAssignedAmount;
import com.ffusion.util.logging.AuditLogTranTypes;
import com.sap.banking.common.constants.ConfigConstants;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

//=====================================================================
//
// This class contains a callback handler that is registered in the
// IntructionType table.  The registered callback handler will be called
// by the Scheduling engine for the Intra-bank Fund Transfer Processing.
//
//=====================================================================

public class XferHandler implements
com.ffusion.ffs.bpw.interfaces.DBConsts,FFSConst,
com.ffusion.ffs.bpw.interfaces.BPWResource, BPWScheduleHandler {
    private ArrayList _vec  = null;
    private final PropertyConfig _propertyConfig;
    private int _logLevel = 1;
    
    // Reference to the common config BO
    private final transient com.sap.banking.common.bo.interfaces.CommonConfig commonConfigBO;
    
    // Flag to identify BaS status
 	private boolean isBasBackendEnabled;
 	
 	//	This map is used to store event details for BaS resubmit transfer
    private final HashMap<String, Object> _basResubmitEvtsMap = new HashMap<>();
 	
    public XferHandler()
    {
        _propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);
        _logLevel = _propertyConfig.LogLevel;
        
        commonConfigBO = (com.sap.banking.common.bo.interfaces.CommonConfig) OSGIUtil
				.getBean(com.sap.banking.common.bo.interfaces.CommonConfig.class);
		try {
			isBasBackendEnabled = commonConfigBO.isBasBackendEnabled();
		} catch (Exception e) {
			FFSDebug.log(Level.SEVERE, "Invalid user Id", e);
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
                            FFSConnectionHolder dbh )
    throws Exception
    {
   String methodName = "XferHandler.eventHandler";
    long start = System.currentTimeMillis();
    int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log("=== XferHandler.eventHandler: begin, eventSeq=" + eventSequence
                     + ",length=" + evts._array.length, PRINT_DEV );
        if ( eventSequence == 0 ) {         // FIRST sequence
            _vec = new ArrayList();
            String srvrTID = evts._array[0].InstructionID;
            String eventID = evts._array[0].EventID;
            String currency = BPWUtil.validateCurrencyString(null);
            IntraTrnInfo info = new IntraTrnInfo( "",
                                                  "",
                                                  "",
                                                  "",
                                                  "",
                                                  "",
                                                  "",
                                                  "",
                                                  currency,
                                                  "",
                                                  srvrTID,
                                                  "", //logid
                                                  eventID,
                                                  eventSequence,
                                                  false,    // possibleDuplicate false
                                                  null,     // batch key. Assigned b4 sending.
                                                  null,
                                                  "",
                                                  evts._array[0].FIId,
                                                  "", // bank Id
                                                  "" // branch id.
                                                  ); 
            _vec.add( info );

            // convert ArrayList to array and call Backend interface
            //IntraTrnInfo[] ar = (IntraTrnInfo[]) _vec.toArray( new IntraTrnInfo[0] );
            //int retStatus = ToBackend.ProcessIntraTrn( ar );
            //_vec.clear();
        } else if ( eventSequence == 1 ) {    // NORMAL sequence
    		
        	// Read system user id from configuration property. This value will be use by banking services for read audit logging. 
    		int systemUserID = getSystemUserID();
    		
    		// Use this connection to update event details in DB.
    		FFSConnectionHolder dbhEvent = new FFSConnectionHolder();
    		dbhEvent.conn = DBUtil.getConnection();
    		try {
	            for (int i = 0; i < evts._array.length; i++) {
	                String srvrTID = evts._array[i].InstructionID;
	                String eventID = evts._array[i].EventID;
	                FFSDebug.log("=== XferHandler.eventHandler: eventSeq=" + eventSequence
	                             + ",srvrTID=" + srvrTID, PRINT_DEV );
	                               	
                    XferInstruction xinst = XferInstruction.getXferInstruction( dbh, srvrTID );
                    if (xinst == null) {
                        String msg = "*** XferHandler.eventHandler failed: could not find the SrvrTID="+srvrTID+" in BPW_XferInstruction";
                        FFSDebug.console(msg);
                        FFSDebug.log(msg);
                        continue;
                    }
                    boolean possibleDuplicate;
                    //
                    // assemble IntraTrnInfo
                    //
                    possibleDuplicate = xinst.Status.compareTo(WILLPROCESSON) != 0;
                    String currency =   BPWUtil.validateCurrencyString(xinst.CurDef);
                    IntraTrnInfo info = new IntraTrnInfo( xinst.CustomerID,
                                                          xinst.BankID,
                                                          xinst.BranchID,
                                                          xinst.AcctDebitID,
                                                          xinst.AcctDebitType,
                                                          xinst.AcctCreditID,
                                                          xinst.AcctCreditType,
                                                          xinst.Amount,
                                                          currency,
                                                          xinst.ToAmount,
                                                          xinst.ToAmtCurrency,
                                                          xinst.userAssignedAmount,
                                                          xinst.DateToPost,
                                                          srvrTID,
                                                          xinst.LogID,
                                                          eventID,
                                                          eventSequence,
                                                          possibleDuplicate,    // possibleDuplicate
                                                          null,     // batch key. Assigned b4 sending.
                                                          xinst.RecSrvrTID,
                                                          xinst.Status,
                                                          xinst.FIID,
                                                          xinst.fromBankID,
                                                          xinst.fromBranchID);                    	
                    info.submittedBy = xinst.SubmittedBy;
                    info.extraFields = xinst.extraFields;
                    
                    //Set from and to Account Country code in TransferInfo object for Core Banking system
                    info.setAccountFromCountryCode(xinst.acctFromCountryCode);
                    info.setAccountToCountryCode(xinst.acctToCountryCode);
                    
                    // Update immediate transaction flag status
                    updateImmediateTransactionFlag(info, evts._array[i]);
                    
                    // Update posting user id and user type
                    updateUserInfo(info, systemUserID);
                    
                    // Store event details to database. The result handler will use this data to pull the status from the backend.
                    createEvent(dbhEvent, info, evts._array[i]);
                    
                    //
                    // update XferInstruction table with status
                    //
                    String xferStatus = BATCH_INPROCESS;
                    if (!Customer.isActive(xinst.CustomerID, dbh))
                        xferStatus = FAILEDON;
                    XferInstruction.updateStatus( dbh, srvrTID, xferStatus );
                    // Log in Audit Log
                    if (_logLevel >= AUDITLOGLEVEL_STATUSUPDATE) {
                        String debitAcct = BPWUtil.getAccountIDWithAccountType(xinst.AcctDebitID, xinst.AcctDebitType);
                        String creditAcct = BPWUtil.getAccountIDWithAccountType(xinst.AcctCreditID, xinst.AcctCreditType);
                        BigDecimal xferAmount = new BigDecimal(xinst.Amount);
                        String toAmount = xinst.ToAmount;
                        if (toAmount == null) toAmount = "0.00";
                        BigDecimal xferToAmount = new BigDecimal(toAmount);
                        ILocalizable msg = BPWLocaleUtil.getLocalizedMessage(
                                            AuditLogConsts.AUDITLOG_MSG_BOOKTRANSFER_START_PROCESS_XFER,
                                            null,
                                            BPWLocaleUtil.BOOKTRANSFER_AUDITLOG_MESSAGE);
                        if (xferStatus != null && FAILEDON.equalsIgnoreCase(xferStatus))
                        {
                            // not active customer
                            msg = BPWLocaleUtil.getLocalizedMessage(
                                AuditLogConsts.AUDITLOG_MSG_EXTERNALTRANSFER_TRANSFER_HANDLER_CUSTOMER_IS_INACTIVE,
                                null,
                                BPWLocaleUtil.EXTERNALTRANSFER_AUDITLOG_MESSAGE);                        }
                        this.logTransAuditLog(dbh,
                                              xinst.SubmittedBy,
                                              Integer.parseInt(xinst.CustomerID),
                                              xinst.LogID,
                                              AuditLogTranTypes.BPW_INTRATRN,
                                              xferAmount,
                                              xinst.CurDef,
                                              xferToAmount,
                                              xinst.ToAmtCurrency,
                                              xinst.userAssignedAmount,
                                              xinst.SrvrTID,
                                              debitAcct,
                                              creditAcct,
                                              xinst.BankID,
                                              xinst.fromBankID,
                                              xferStatus,
                                              msg);
                    }

                    //
                    // update transaction history with DTPOSTED
                    //
                    AuditAgent audit = (AuditAgent)FFSRegistry.lookup(AUDITAGENT);
                    if ( audit == null)
                        throw new FFSException("XferHandler.eventHandler:AuditAgent is null!!");
                    BPWMsgBroker broker = (BPWMsgBroker)FFSRegistry.lookup(BPWMSGBROKER);
                    if ( broker == null)
                        throw new FFSException("XferHandler.eventHandler:BPWMsgBroker is null!!");

                    String[] res = SrvrTrans.findResponseBySrvrTID( srvrTID, dbh );
                    if ( res[0] == null ) {
                        String msg = "*** XferHandler.eventHandler failed: could not find the SrvrTID="+srvrTID+" in BPW_SrvrTrans";
                        FFSDebug.console(msg);
                        FFSDebug.log(msg);
                        continue;
                    }
                    if (res[0].startsWith("OFX151")) {

                        com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151. TypeIntraTrnRsV1 oldrs
                        = ( com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypeIntraTrnRsV1 )broker.parseMsg(res[1], "IntraTrnRsV1", "OFX151");
                        if (oldrs != null) {
                            SimpleDateFormat formatter = new SimpleDateFormat( "yyyyMMddHHmmss" );
                            String prcDate = formatter.format(new Date());
                            RsCmBuilder.updateXferRsDatePosted( prcDate, oldrs.IntraTrnRs.IntraTrnRsV1Un.IntraRs );
                            audit.updateXferRsV1(oldrs, dbh, BATCH_INPROCESS);
                        }
                    } else if ( res[0].startsWith("OFX200")) {
                        com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200. TypeIntraTrnRsV1 oldrs
                        = ( com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX200.TypeIntraTrnRsV1 )broker.parseMsg(res[1], "IntraTrnRsV1", "OFX200");
                        if (oldrs != null) {
                            SimpleDateFormat formatter = new SimpleDateFormat( "yyyyMMddHHmmss" );
                            String prcDate = formatter.format(new Date());
                            RsCmBuilder.updateXferRsDatePosted( prcDate, oldrs.IntraTrnRs.IntraTrnRsUn.IntraRs );
                            audit.updateXferRsV1(oldrs, dbh, BATCH_INPROCESS);
                        }
                    }
                    if (BATCH_INPROCESS.equals(xferStatus))
                        _vec.add( info );
	
	                
	            // Persist event details to DB
				dbhEvent.conn.commit();
            }
    		}catch ( Exception exc ) {
            	 dbhEvent.conn.rollback();
            	 FFSDebug.log("*** XferHandler.eventHandler failed:" + exc);
                 throw new FFSException( exc.toString() );
            } finally {
            	DBUtil.freeConnection(dbhEvent.conn);
            }
        } else if ( eventSequence == 2 ) {    // LAST sequence
            String srvrTID = evts._array[0].InstructionID;
            String eventID = evts._array[0].EventID;
            String currency = BPWUtil.validateCurrencyString(null);
            IntraTrnInfo info = new IntraTrnInfo( "",
                                                  "",
                                                  "",
                                                  "",
                                                  "",
                                                  "",
                                                  "",
                                                  "",
                                                  currency,
                                                  "",
                                                  srvrTID,
                                                  "",
                                                  eventID,
                                                  eventSequence,
                                                  false,    // possibleDuplicate false
                                                  null,     // batch key. Assigned b4 sending.
                                                  null,
                                                  "",
                                                  evts._array[0].FIId,"","");
            _vec.add( info );

            sendToBackend(dbh);
        } else if ( eventSequence == 3 ) {    // Batch-Start sequence
        } else if ( eventSequence == 4 ) {    // Batch-End sequence
            sendToBackend(dbh);
        }
        FFSDebug.log("=== XferHandler.eventHandler: end", PRINT_DEV);
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
    	String thisMethod = "==== XferHandler.resubmitEventHandler:";
    	long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(thisMethod,start);
        FFSDebug.log(thisMethod + " begin, eventSeq=" + eventSequence
                     + ",length=" + evts._array.length);
        
        if ( eventSequence == 0 ) {         // FIRST sequence
            _vec = new ArrayList();
            String srvrTID = evts._array[0].InstructionID;
            String eventID = evts._array[0].EventID;
            String currency = BPWUtil.validateCurrencyString(null);
            IntraTrnInfo info = new IntraTrnInfo( "",
                                                  "",
                                                  "",
                                                  "",
                                                  "",
                                                  "",
                                                  "",
                                                  "",
                                                  currency,
                                                  "",
                                                  srvrTID,
                                                  evts._array[0].LogID,       //logid
                                                  eventID,
                                                  eventSequence,
                                                  true,     // possibleDuplicate
                                                  null,     // batch key. Assigned b4 sending.
                                                  null,
                                                  "",
                                                  evts._array[0].FIId,
                                                  "", // bankid
                                                  "" // branchid
                                                  );
            _vec.add( info );

            // convert ArrayList to array and call Backend interface
            //IntraTrnInfo[] ar = (IntraTrnInfo[]) _vec.toArray( new IntraTrnInfo[0] );
            //int retStatus = ToBackend.ProcessIntraTrn( ar );
            //_vec.clear();
        } else if ( eventSequence == 1 ) {    // NORMAL sequence
        	
        	// Read system user id from configuration property. This value will be use by banking services for read audit logging.
    		int systemUserID = getSystemUserID();
    		
    		// Use this connection to update event details in DB.
    		FFSConnectionHolder dbhEvent = new FFSConnectionHolder();
    		dbhEvent.conn = DBUtil.getConnection();
    		
    		try {
	            for (int i = 0; i < evts._array.length; i++) {
	                String srvrTID = evts._array[i].InstructionID;
	                String eventID = evts._array[i].EventID;
	                FFSDebug.log(thisMethod + " begin, eventSeq=" + eventSequence
	                             + ",srvrTID=" + srvrTID);
                    XferInstruction xinst = XferInstruction.getXferInstruction( dbh, srvrTID );
                    if (xinst == null) {
                        String msg = thisMethod + " failed: could not find the SrvrTID="+srvrTID+" in BPW_XferInstruction";
                        FFSDebug.console(msg);
                        FFSDebug.log(msg);
                        continue;
                    }
                    
                    int fileBasedRecovery = evts._array[i].fileBasedRecovery;
					// In non-file based approach, only resubmit transaction
					// which are "in-process" state.
					if (fileBasedRecovery == 0 && !(DBConsts.BATCH_INPROCESS.equals(xinst.Status)
							|| DBConsts.IMMED_INPROCESS.equals(xinst.Status))) {
						String msg = thisMethod + "Transfer is already processed by OCB. Unable to resubmit internal transfer for the SrvrTID=" + srvrTID;
						FFSDebug.log(msg);
						continue;
					}
					
                    //
                    // assemble IntraTrnInfo
                    //
					String currency =   BPWUtil.validateCurrencyString(xinst.CurDef);
			        IntraTrnInfo info = new IntraTrnInfo( xinst.CustomerID,
			                                              xinst.BankID,
			                                              xinst.BranchID,
			                                              xinst.AcctDebitID,
			                                              xinst.AcctDebitType,
			                                              xinst.AcctCreditID,
			                                              xinst.AcctCreditType,
			                                              xinst.Amount,
			                                              currency,
			                                              xinst.ToAmount,
			                                              xinst.ToAmtCurrency,
			                                              xinst.userAssignedAmount,
			                                              xinst.DateToPost,
			                                              xinst.SrvrTID,
			                                              xinst.LogID,
			                                              eventID,
			                                              eventSequence,
			                                              true,     // possibleDuplicate
			                                              null,     // batch key. Assigned b4 sending.
			                                              xinst.RecSrvrTID,
			                                              xinst.Status,
			                                              xinst.FIID,
			                                              xinst.fromBankID,
			                                              xinst.fromBranchID);
			        
			        info.submittedBy = xinst.SubmittedBy;
			        info.extraFields = xinst.extraFields;
			        
			        //Set from and to Account Country code in IntraTrnInfo object for Core Banking system
			        info.setAccountFromCountryCode(xinst.acctFromCountryCode);
			        info.setAccountToCountryCode(xinst.acctToCountryCode);
			        
			        // Update immediate transaction flag status 
			        HashMap extraMap = null;
					if (info.extraFields != null) {
						extraMap = (HashMap) info.extraFields;
					} else {
						extraMap = new HashMap();
					}
					extraMap.put(IS_IMMEDIATE_TRANSACTION, DBConsts.FALSE);
					info.extraFields = extraMap;
                    
					// Update posting user id and user type
			        // Set user type to system, as schedule is initiated by the system.
					info.setPostingUserType(SecureUser.TYPE_SYSTEM);

					// Set system user Id in IntraTrnInfo object which is used by
					// banking services for read audit logging.
					info.setPostingUserId(systemUserID);
			        
					// Set resubmit flag
                    evts._array[i].ScheduleFlag = ScheduleConstants.SCH_FLAG_RESUBMIT;
                    
                    // In case of BaS, Do not resubmit transaction if OCB received confirmation number from Payment Engine.
                    // Receiving a Confirmation Number for the transaction would mean that the transaction has reached the Payment Engine. 
			        if (isBasBackendEnabled) {
			        	if(StringUtil.isNotEmpty(xinst.getConfirmNum())) {
			        		// Do not persist event details and audit log in database as transaction is not resubmitted to the back end.
			        	} else {
			        		_vec.add(info);
			        		_basResubmitEvtsMap.put(info.srvrTid, evts._array[i]);
			        	}
			        } else {
			        	//
						// update XferInstruction table with status
						//
						String xferStatus = BATCH_INPROCESS;
						if (!Customer.isActive(xinst.CustomerID, dbh))
							xferStatus = FAILEDON;
						XferInstruction.updateStatus(dbh, srvrTID, xferStatus);
						if (BATCH_INPROCESS.equals(xferStatus))
							_vec.add(info);
						
			        	// Store event details to database. The result handler will use this data to pull status from the back end.
	                    createEvent(dbhEvent, info, evts._array[i]);
	                    
	                    // Log in Audit Log
						doAuditLog(dbh, xinst, xferStatus);
			        }
	            }
	            // Persist event details to DB
				dbhEvent.conn.commit();
    		} catch ( Exception exc ) {
    			dbhEvent.conn.rollback();
    			FFSDebug.log(thisMethod + " failed. "+ exc);
                throw new FFSException( exc.toString() );
            } finally {
            	DBUtil.freeConnection(dbhEvent.conn);
            }
        } else if ( eventSequence == 2 ) {    // LAST sequence
            String srvrTID = evts._array[0].InstructionID;
            String eventID = evts._array[0].EventID;
            String currency = BPWUtil.validateCurrencyString(null);
            IntraTrnInfo info = new IntraTrnInfo( "",
                                                  "",
                                                  "",
                                                  "",
                                                  "",
                                                  "",
                                                  "",
                                                  "",
                                                  currency,
                                                  "",
                                                  srvrTID,
                                                  "",
                                                  eventID,
                                                  eventSequence,
                                                  true,     // possibleDuplicate
                                                  null,     // batch key. Assigned b4 sending.
                                                  null,
                                                  "",
                                                  evts._array[0].FIId,
                                                  "","");
            _vec.add( info );
            sendToBackend(dbh);
        } else if ( eventSequence == 3 ) {    // Batch-Start sequence
        } else if ( eventSequence == 4 ) {    // Batch-End sequence
        	if(isBasBackendEnabled) {
        		resubmitTransferToBaSBackend(dbh);
        	} else {
        		sendToBackend(dbh);
        	}
        }
        FFSDebug.log(thisMethod + " end");
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

	/** Perform audit logging for internal transfer
	 * @param dbh			FFSConnectionHolder object
	 * @param xinst			Internal transfer details
	 * @param xferStatus	Internal Transfer status
	 * @throws FFSException
	 */
	private void doAuditLog(FFSConnectionHolder dbh, XferInstruction xinst, String xferStatus) throws FFSException {
		if (_logLevel >= AUDITLOGLEVEL_STATUSUPDATE) {
			String debitAcct = BPWUtil.getAccountIDWithAccountType(xinst.AcctDebitID, xinst.AcctDebitType);
			String creditAcct = BPWUtil.getAccountIDWithAccountType(xinst.AcctCreditID, xinst.AcctCreditType);
			BigDecimal xferAmount = new BigDecimal(xinst.Amount);
			String toAmount = xinst.ToAmount;
			if (toAmount == null)
				toAmount = "0.00";
			BigDecimal xferToAmount = new BigDecimal(toAmount);
			ILocalizable msg = BPWLocaleUtil.getLocalizedMessage(
					AuditLogConsts.AUDITLOG_MSG_BOOKTRANSFER_START_PROCESS_XFER, null,
					BPWLocaleUtil.BOOKTRANSFER_AUDITLOG_MESSAGE);
			this.logTransAuditLog(dbh, xinst.SubmittedBy, Integer.parseInt(xinst.CustomerID), xinst.LogID,
					AuditLogTranTypes.BPW_INTRATRN, xferAmount, xinst.CurDef, xferToAmount, xinst.ToAmtCurrency,
					xinst.userAssignedAmount, xinst.SrvrTID, debitAcct, creditAcct, xinst.BankID, xinst.fromBankID,
					xferStatus, msg);
		}
	}

	/**
     * Do transaction audit log which logs this information to
     * audit_log table in CB
     *
     * @param dbh
     * @param customerId
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
                                  String customerId,
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
                                  ILocalizable decription )
    throws FFSException {

        TransAuditLog.logTransAuditLog(dbh.conn.getConnection(),
                                       customerId,
                                       null,    // agentID,
                                       null,    // agentType,
                                       decription,  // desc,
                                       tranId,  // tranID,
                                       tranType,
                                       businessId,       // businessId (int)
                                       amount,
                                       curDef,    // currencyCode,
                                       toAmount,
                                       toAmtCurrency,
                                       userAssignedAmount,
                                       srvrTId,
                                       status,
                                       toAcctId,
                                       toBankId,  // toAcctRtgNum
                                       fromAcctId,
                                       fromBankId,  // fromAcctRtgNum
                                       0);     // module
    }

    /**
     * Pushes the transfer data to the customer implemented
     * backend code.
     *
     * @param dbh    Database connection holder.
     * @return Propagates the result status returned by the customer
     *         implemented backend code.
     * @exception Exception Propagates back any Exception thrown by the customer
     *                      implemented code.
     */
    private int sendToBackend(FFSConnectionHolder dbh)
    throws Exception
    {
    	CustomerInfo custInfo = null;
        // Generate a new batch key and bind the db connection
        // using the batch key.
        String batchKey = DBConnCache.getNewBatchKey();
        DBConnCache.bind(batchKey, dbh);

        // convert ArrayList to array and call Backend interface
        IntraTrnInfo[] ar = (IntraTrnInfo[]) _vec.toArray( new IntraTrnInfo[0] );
        
        // clear our collection.
        _vec.clear();
        
        // Populate each entry with our batch key.
        for (int arIdx = 0; arIdx < ar.length; arIdx++) {
        	custInfo = Customer.getCustomerByID(ar[arIdx].getCustomerId(), dbh);
        	ar[arIdx].setCustomerInfo(custInfo);
            ar[arIdx].batchKey = batchKey;
        } // End for-loop

        // Invoke the customer implemented code.
        BackendProvider backendProvider = getBackendProviderService();
        ToBackend _toBackend = backendProvider.getToBackendInstance();
        int retStatus = _toBackend.ProcessIntraTrn( ar );

        // Remove the binding of the db connection and the batch key.
        DBConnCache.unbind(batchKey);

        return retStatus;
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
  			if(!backendProviders.isEmpty()) {
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
	
	
	private void updateUserInfo(IntraTrnInfo info, int systemUserId)
			throws FFSException {

		if (isImmediateTransfer(info)) {
			// Set user type in IntraTrnInfo object which is used by banking
			// services for read audit logging.
			info.setPostingUserType(SecureUser.TYPE_CUSTOMER);

			// Set user Id in IntraTrnInfo object which is used by banking
			// services for read audit logging.
			try {
				int userId = Integer.parseInt(info.getSubmittedBy());
				info.setPostingUserId(userId);
			} catch (NumberFormatException ex) {
				FFSDebug.log("Invalid user Id");
			}
		} else {
			// Set user type to system, as schedule is initiated by the system.
			info.setPostingUserType(SecureUser.TYPE_SYSTEM);

			// Set system user Id in IntraTrnInfo object which is used by
			// banking services for read audit logging.
			info.setPostingUserId(systemUserId);
		}
	}
	
	protected void updateImmediateTransactionFlag(IntraTrnInfo info, EventInfo evt) {
		HashMap extraMap = null;
		if (info.extraFields != null) {
			extraMap = (HashMap) info.extraFields;
		} else {
			extraMap = new HashMap();
		}
		String processId = evt.processId;
		// if transfer is immediate then set IS_IMMEDIATE_TRANSACTION flag to
		// true and
		if (StringUtil.isNotEmpty(processId) && !processId.equals("0")) {
			extraMap.put(IS_IMMEDIATE_TRANSACTION, DBConsts.FALSE);
		} else {
			extraMap.put(IS_IMMEDIATE_TRANSACTION, DBConsts.TRUE);
		}
		info.extraFields = extraMap;
	}
	
	/**
	 * Identify transfer type i.e immediate or schedule
	 * @param info internal transfer object
	 * @return boolean 
	 */
	protected boolean isImmediateTransfer(IntraTrnInfo info) {
		boolean isImmediate = false;
		if (info.extraFields != null) {
			HashMap extraMap = (HashMap) info.extraFields;
			String immmediateTrans = (String)extraMap.get(IS_IMMEDIATE_TRANSACTION);
			if(immmediateTrans != null && DBConsts.TRUE.equals(immmediateTrans)) {
				isImmediate = true;
			}
		} 
		return isImmediate;
	}
	
	/**
	 * Create an event info and persist to the database.
	 * @param dbhEvent FFSConnectionHolder object
	 * @param info internal transfer object
	 * @param evt event info details
	 * @throws FFSException
	 */
	private void createEvent(FFSConnectionHolder dbhEvent, IntraTrnInfo info, EventInfo evt) throws FFSException{
		String reconciliationID = null;
        // Create an entry in EventInfo table which is used for the retry status check and reconciliation.
        try {
        	reconciliationID = ReconciliationUtil.getReconciliationId(info, null);
			info.setReconciliationId(reconciliationID);
			evt.reconciliationId = reconciliationID;
			if(ScheduleConstants.SCH_FLAG_RESUBMIT == evt.ScheduleFlag) {
				String eventId = com.ffusion.ffs.bpw.db.DBUtil.getNextIndexString(com.ffusion.ffs.bpw.interfaces.DBConsts.EVENTID);
				EventInfo eventInfo = new EventInfo();
				eventInfo.EventID = eventId;
				eventInfo.ScheduleFlag = evt.ScheduleFlag;
	            eventInfo.InstructionID = evt.InstructionID; // save logDate in InstructionID for EVT_SEQUENCE_FIRST
	            eventInfo.FIId = evt.FIId;
	            eventInfo.InstructionType = evt.InstructionType;
	            eventInfo.LogID = evt.LogID;
	            eventInfo.cutOffId = evt.cutOffId;
	            eventInfo.processId = evt.processId;
	            eventInfo.createEmptyFile = evt.createEmptyFile;
	            eventInfo.fileBasedRecovery = evt.fileBasedRecovery;
	            eventInfo.reconciliationId = reconciliationID;
	            eventInfo.Status = ScheduleConstants.EVT_STATUS_INPROCESS;
	            Event.createEvent(dbhEvent, eventInfo);
			} else {
				evt.Status = ScheduleConstants.EVT_STATUS_INPROCESS;
				evt.LogID = info.getLogId();
				Event.createEvent(dbhEvent, evt);
				if(isImmediateTransfer(info)) {
					EventInfoLog.createEventInfoLog(dbhEvent, evt);
				}
			}
		} catch (Exception ex) {
			FFSDebug.log(ex, "XferHandler.updateTransfer: Unable to create an event.");
			throw new FFSException(ex, "Unable to create an event.");
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
	 * Resubmit transfer to the Payment Engine for processing
	 * @param dbh FFSConnectionHolder object
	 * @return
	 * @throws Exception
	 */
	private int resubmitTransferToBaSBackend(FFSConnectionHolder dbh) throws Exception {
		String thisMethod = "XferHandler.resubmitTransferToBaSBackend: ";

		if (_vec != null && !_vec.isEmpty()) {

			// Use this connection to update event details in DB.
			FFSConnectionHolder dbhEvent = new FFSConnectionHolder();
			dbhEvent.conn = DBUtil.getConnection();

			try {

				// Get the transfer status from BaS
				Map<String, Object> resultMap = getTransferStatusFromBaS(dbh);

				// Valid payment which has to resubmit to the back end for
				// processing.
				IntraTrnInfo resubmitIntraTrnInfo = null;
				XferInstruction xinst = null;

				// Check valid transfers which has to resubmit to the back end
				// for
				// processing.
				// If transfer status is in process and confirmation is not
				// received from back end, then send transfer to the back end
				// for
				// processing.
				Iterator<IntraTrnInfo> it = _vec.iterator();
				while (it.hasNext()) {
					resubmitIntraTrnInfo = it.next();
					String srvrTId = resubmitIntraTrnInfo.srvrTid;
					if (StringUtil.isNotEmpty(srvrTId)) {
						
						// If transaction is received or processed by the payment engine then map will have response entry for the corresponding srvrTID
						if (resultMap.containsKey(srvrTId)) {
							// Do not resubmit transfer which status or
							// confirmation number has received.
							it.remove();
						} else {
							xinst = XferInstruction.getXferInstruction(dbh, srvrTId);
							if (xinst == null) {
								String msg = thisMethod + "failed: could not find the SrvrTID=" + srvrTId
										+ " in BPW_XferInstruction";
								FFSDebug.console(msg);
								FFSDebug.log(msg);
								continue;
							}
							EventInfo evtInfo = (EventInfo) _basResubmitEvtsMap.get(srvrTId);
							// Store event details to database. The result
							// handler will use this data to pull status from
							// the back end.
							if (evtInfo != null) {
								createEvent(dbhEvent, resubmitIntraTrnInfo, evtInfo);
								
								// update XferInstruction table with status
								String xferStatus = BATCH_INPROCESS;
								if (!Customer.isActive(xinst.CustomerID, dbh))
									xferStatus = FAILEDON;
								XferInstruction.updateStatus(dbh, srvrTId, xferStatus);

								// perform audit log
								doAuditLog(dbh, xinst, xinst.Status);
							}
						}
					}
				}

				// Persist event details to DB
				dbhEvent.conn.commit();
			} catch (Exception ex) {
				if (_vec != null) {
					_vec.clear();
				}
				FFSDebug.log(thisMethod + ex);
				throw ex;
			} finally {
				// Do not clear _vec here, It contains valid resubmit transfer
				// to BaS.
				if (_basResubmitEvtsMap != null) {
					_basResubmitEvtsMap.clear();
				}
				DBUtil.freeConnection(dbhEvent.conn);
			}
		}
		return sendToBackend(dbh);
	}
	
	/**
	 * Get the transfer status from Payment Engine
	 * @param dbh	FFSConnectionHolder object
	 * @return		Map	Collection of transaction
	 * @throws Exception
	 */
	private Map<String, Object> getTransferStatusFromBaS(FFSConnectionHolder dbh) throws Exception {
		// List of transfer which will send to back end to get the status
		List<IntraTrnInfo> intraTrnInfoList = new ArrayList<>();

		// Generate a new batch key and bind the db connection
		// using the batch key.
		DBConnCache dbConnCache = (DBConnCache) FFSRegistry.lookup(DBCONNCACHE);
		String batchKey = DBConnCache.save(dbh);

		Iterator itr = _vec.iterator();
		while (itr.hasNext()) {
			IntraTrnInfo info = (IntraTrnInfo) itr.next();
			if (info.eventSequence == 1) {
				info.batchKey = batchKey;
				intraTrnInfoList.add(info);
			}
		}

		// convert ArrayList to array and call Backend interface
		IntraTrnInfo[] intraTrnInfos = intraTrnInfoList.toArray(new IntraTrnInfo[0]);
		intraTrnInfoList.clear();

		// Invoke the customer implemented code.
		TransactionStatusProvider transactionStatusProvider = getTransactionStatusProviderService();
		TransferStatus transferStatusRef = transactionStatusProvider.getTransferStatusInstance();

		// Get the status from back end, before resubmitting transfer to the
		// processing.
		IntraTrnRslt[] intraTrnRslts = transferStatusRef.getTransferStatus(intraTrnInfos, new HashMap<String, Object>());

		// Remove the binding of the db connection and the batch key.
		DBConnCache.unbind(batchKey);
		
		// This map is used to identify resubmit transactions.
		// If Payment Engine received or processed transaction then result map will contain entry for the same.
		// OCB will not resubmit transaction if result object is available for corresponding srvrTID in map.
		Map<String, Object> resultMap = new HashMap<>();

		// Update the result map with SrvrTID and IntraTrnRslt object
		if (intraTrnRslts != null && intraTrnRslts.length > 0) {
			IntraTrnRslt rslt = null;
			for (int idx = 0; idx < intraTrnRslts.length; idx++) {
				rslt = intraTrnRslts[idx];
				if (rslt != null && StringUtil.isNotEmpty(rslt.srvrTid)) {
					resultMap.put(rslt.srvrTid, rslt);
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
