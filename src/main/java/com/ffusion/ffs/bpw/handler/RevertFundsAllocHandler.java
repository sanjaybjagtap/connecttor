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
import java.util.logging.Level;

import com.ffusion.ffs.bpw.audit.TransAuditLog;
import com.ffusion.ffs.bpw.custimpl.interfaces.BackendProvider;
import com.ffusion.ffs.bpw.db.DBConnCache;
import com.ffusion.ffs.bpw.db.Payee;
import com.ffusion.ffs.bpw.db.PmtInstruction;
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


//=====================================================================
//
// This class contains a callback handler that is registered in the
// InstructionType table.  The registered callback handler will be called
// by the Scheduling engine for processing revert funds allocation.
//
//=====================================================================

public class RevertFundsAllocHandler implements
com.ffusion.ffs.bpw.interfaces.DBConsts, FFSConst,
com.ffusion.ffs.bpw.interfaces.BPWResource, BPWScheduleHandler {
    private ArrayList _vec  = null;
    
    private static int _batchNum = 1;
    private final String BATCHKEYPREFIX = "RevertFunds";


    public RevertFundsAllocHandler()
    {
        
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
    	 String methodName = "RevertFundsAllocHandler.eventHandler";
         long start = System.currentTimeMillis();
         int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log("=== RevertFundsAllocHandler.eventHandler: begin, eventSeq=" + eventSequence, PRINT_DEV);
        BackendProvider backendProvider = getBackendProviderService();
        FundsAllocator _fundsAllocator = backendProvider.getFundsAllocatorInstance();
        String fiId = evts._array[0].FIId;
        if ( eventSequence == 0 ) {         // FIRST sequence
            _vec = new ArrayList();
            String batchKey = BATCHKEYPREFIX +  "_" + evts._array[0].FIId + "_" + _batchNum;
            String currency = BPWUtil.validateCurrencyString(null);
            FundsAllocInfo info = new FundsAllocInfo(
                                                    fiId,
                                                    "",
                                                    "",
                                                    "",
                                                    "",
                                                    "",
                                                    currency,
                                                    "", //srvrTID,
                                                    "",
                                                    "",
                                                    eventSequence,
                                                    false,   // possibleDuplicate false
                                                    "",
                                                    batchKey,null );
            _vec.add( info );
        } else if ( eventSequence == 1 ) {    // NORMAL sequence
            for (int i = 0; i < evts._array.length; i++) {
            	if (evts._array[i] == null) {

					FFSDebug.log("RevertFundsAllocHandler.eventHandler: ",
							" ++--++ Invalid Transaction in this batch: "
									+ evts._array[i],
							", Transcaction will be ignored, Transaction counter: "
									+ i, FFSConst.PRINT_DEV);
					continue;
				}
                String srvrTID = evts._array[i].InstructionID;
                FFSDebug.log("=== RevertFundsAllocHandler.eventHandler: eventSeq=" + eventSequence
                             + ",srvrTID=" + srvrTID, PRINT_DEV);
                try {
                    PmtInstruction pmt = PmtInstruction.getPmtInstr( srvrTID, dbh );
                    if (pmt == null) {
                        String msg = "*** RevertFundsAllocHandler.eventHandler failed: could not find the SrvrTID="+srvrTID+" in BPW_PmtInstruction";
                        FFSDebug.console(msg);
                        FFSDebug.log(msg);
                        continue;
                    }
                    PmtInfo pmtinfo = pmt.getPmtInfo();
                    PayeeInfo payeeinfo = Payee.findPayeeByID(pmtinfo.PayeeID, dbh);
                    if (payeeinfo == null) {
                        String msg = "*** RevertFundsAllocHandler.eventHandler failed: could not find the PayeeID="+pmtinfo.PayeeID+" in BPW_Payee for pmt of SrvrTID="+srvrTID;
                        FFSDebug.console(msg);
                        FFSDebug.log(msg);
                        continue;
                    }

                    BankInfo binfo = BPWRegistryUtil.getBankInfo(pmtinfo.BankID);
                    PropertyConfig propertyConfig = (PropertyConfig)FFSRegistry.lookup(BPWResource.PROPERTYCONFIG);
                    String acctCreditId = null;
                    if (binfo == null) {
                        // Get Bpw bank look up level
                        String checkingLevel = propertyConfig.otherProperties.getProperty(
                                                                                         DBConsts.BANKINFO_LOOKUP_LEVEL,
                                DBConsts.BPW_BANK_LOOKUP_LEVEL_STRICT);
                        if (checkingLevel.equalsIgnoreCase(DBConsts.BPW_BANK_LOOKUP_LEVEL_STRICT)) {
                            String msg = "*** RevertFundsAllocHandler.eventHandler failed:could not find the BankID in BPW_Bank, bpw.pmt.bankinfo.lookup.level = STRICT " ;
                            FFSDebug.console(msg);
                            FFSDebug.log(msg,FFSConst.PRINT_ERR);
                            throw new FFSException(msg);
                        } else if (checkingLevel.equalsIgnoreCase(DBConsts.BPW_BANK_LOOKUP_LEVEL_LENIENT)) {
                            String msg = "*** RevertFundsAllocHandler.eventHandler : bpw.pmt.bankinfo.lookup.level = LENIENT " ;
                            // FFSDebug.console(msg);
                            FFSDebug.log(msg,FFSConst.PRINT_WRN);
                        } else if (checkingLevel.equalsIgnoreCase(DBConsts.BPW_BANK_LOOKUP_LEVEL_NONE)) {
                            String msg = "*** RevertFundsAllocHandler.eventHandler : bpw.pmt.bankinfo.lookup.level = NONE ";
                            // FFSDebug.console(msg);
                            FFSDebug.log(msg,FFSConst.PRINT_DEV);
                        } else {
                            // None of above type
                            String msg = "*** RevertFundsAllocHandler.eventHandler failed: Incorrect value for bpw.pmt.bankinfo.lookup.level =" + checkingLevel ;
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
                    String batchKey = BATCHKEYPREFIX +   "_" + pmtinfo.FIID + "_" + _batchNum;
                    
                    String currency = BPWUtil.validateCurrencyString(pmtinfo.CurDef);
                    
                    FundsAllocInfo info = new FundsAllocInfo(
                                                            pmtinfo.FIID,
                                                            pmtinfo.CustomerID,
                                                            pmtinfo.BankID,
                                                            pmtinfo.AcctDebitID,
                                                            pmtinfo.AcctDebitType,
                                                            pmtinfo.getAmt(),
                                                            currency,
                                                            srvrTID,
                                                            pmtinfo.PayeeID,
                                                            payeeinfo.PayeeName,
                                                            eventSequence,
                                                            false,   // possibleDuplicate false
                                                            acctCreditId,
                                                            batchKey ,
                                                            pmtinfo.RecSrvrTID //Server transaction ID of parent recurring model
                                                            );

                    // retrieve the extrainfo info from BPW_PmtExtraInfo
                    // and assign it to extraFields of FundsAllocInfo
                    if (pmtinfo.extraFields != null) {
                        info.extraFields = pmtinfo.extraFields;
                    }

                    //
                    // cache the information
                    //
                    _vec.add( info );
                    // change status to INFUNDSREVERT
                    PmtInstruction.updateStatus( dbh, srvrTID, INFUNDSREVERT );
                    // log into AuditLog
                    if (propertyConfig.LogLevel >= AUDITLOGLEVEL_STATUSUPDATE) {
                        int bizID = Integer.parseInt(pmtinfo.CustomerID);
                        BigDecimal pmtAmount = BPWUtil.getBigDecimal(pmtinfo.getAmt());
                        String debitAcct = BPWUtil.getAccountIDWithAccountType(pmtinfo.AcctDebitID, pmtinfo.AcctDebitType);
                        ILocalizable msg = BPWLocaleUtil.getLocalizedMessage(
                                            AuditLogConsts.AUDITLOG_MSG_FUND_ALLOCATION_REVERT,
                                            null,
                                            BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
                        TransAuditLog.logTransAuditLog(dbh.conn.getConnection(),
                                                       pmtinfo.submittedBy,
                                                       null,
                                                       null,
                                                       msg,
                                                       pmtinfo.LogID,
                                                       AuditLogTranTypes.BPW_REVERTFUNDTRN,
                                                       bizID,
                                                       pmtAmount,
                                                       pmtinfo.CurDef,
                                                       srvrTID,
                                                       INFUNDSREVERT,
                                                       pmtinfo.PayAcct,
                                                       null,
                                                       debitAcct,
                                                       pmtinfo.getBankID(),
                                                       0);
                    }
                } catch ( Exception exc ) {
                    FFSDebug.log("*** RevertFundsAllocHandler.eventHandler failed:" + exc);
                    PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
                    throw new FFSException( exc.toString() );
                }
            }
        } else if ( eventSequence == 2 ) {    // LAST sequence
            String batchKey = BATCHKEYPREFIX +   "_" + fiId + "_" + _batchNum;
            _batchNum++;
            DBConnCache.bind(batchKey, dbh);
            String currency = BPWUtil.validateCurrencyString(null);
            FundsAllocInfo info = new FundsAllocInfo(
                                                    fiId,
                                                    "",
                                                    "",
                                                    "",
                                                    "",
                                                    "",
                                                    currency,
                                                    "", //srvrTID,
                                                    "",
                                                    "",
                                                    eventSequence,
                                                    false,   // possibleDuplicate false
                                                    "",
                                                    batchKey,
                                                    null);
            _vec.add( info );

            // convert ArrayList to array and call Backend interface
            FundsAllocInfo[] ar = (FundsAllocInfo[]) _vec.toArray( new FundsAllocInfo[0] );
            _fundsAllocator.revertFundsAllocation( ar );

            DBConnCache.unbind(batchKey);
            _vec.clear();
            _vec = null;
        } else if ( eventSequence == 3 ) {    // Batch-Start sequence
        } else if ( eventSequence == 4 ) {    // Batch-End sequence
            String batchKey = BATCHKEYPREFIX +    "_" + fiId + "_" + _batchNum;
            _batchNum++;
            DBConnCache.bind(batchKey, dbh);

            // convert ArrayList to array and call Backend interface
            FundsAllocInfo[] ar = (FundsAllocInfo[]) _vec.toArray( new FundsAllocInfo[0] );
            _fundsAllocator.revertFundsAllocation( ar );

            DBConnCache.unbind(batchKey);
            _vec.clear();
        }
        FFSDebug.log("=== RevertFundsAllocHandler.eventHandler: end", PRINT_DEV);
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
    public void resubmitEventHandler(
                                    int eventSequence,
                                    EventInfoArray evts,
                                    FFSConnectionHolder dbh )
    throws Exception
    {
    	String methodName = "RevertFundsAllocHandler.resubmitEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
    	BackendProvider backendProvider = getBackendProviderService();
    	FundsAllocator _fundsAllocator = backendProvider.getFundsAllocatorInstance();
        FFSDebug.log("=== RevertFundsAllocHandler.resubmitEventHandler: begin, eventSeq="
                     + eventSequence
                     + ",length="
                     + evts._array.length);
        String fiId = evts._array[0].FIId;
        if ( eventSequence == 0 ) {         // FIRST sequence
            _vec = new ArrayList();
            String batchKey = BATCHKEYPREFIX +    "_" + fiId + "_" + _batchNum;
            String currency = BPWUtil.validateCurrencyString(null);
            FundsAllocInfo info = new FundsAllocInfo(
                                                    fiId,
                                                    "",
                                                    "",
                                                    "",
                                                    "",
                                                    "",
                                                    currency,
                                                    "", //srvrTID,
                                                    "",
                                                    "",
                                                    eventSequence,
                                                    true,   // possibleDuplicate false
                                                    "",
                                                    batchKey,null );
            _vec.add( info );
        } else if ( eventSequence == 1 ) {    // NORMAL sequence
            for (int i = 0; i < evts._array.length; i++) {
                String srvrTID = evts._array[i].InstructionID;
                FFSDebug.log("=== RevertFundsAllocHandler.resubmitEventHandler: eventSeq="
                             + eventSequence
                             + ",srvrTID="
                             + srvrTID);
                try {
                    PmtInstruction pmt = PmtInstruction.getPmtInstr( srvrTID, dbh );
                    if (pmt == null) {
                        String msg = "*** RevertFundsAllocHandler.resubmitEventHandler failed: could not find the SrvrTID="+srvrTID+" in BPW_PmtInstruction";
                        FFSDebug.console(msg);
                        FFSDebug.log(msg);
                        continue;
                    }
                    PmtInfo pmtinfo = pmt.getPmtInfo();
                    PayeeInfo payeeinfo = Payee.findPayeeByID(pmtinfo.PayeeID, dbh);
                    if (payeeinfo == null) {
                        String msg = "*** RevertFundsAllocHandler.resubmitEventHandler failed: could not find the PayeeID="+pmtinfo.PayeeID+" in BPW_Payee for pmt of SrvrTID="+srvrTID;
                        FFSDebug.console(msg);
                        FFSDebug.log(msg);
                        continue;
                    }
                    BankInfo binfo = BPWRegistryUtil.getBankInfo(pmtinfo.BankID);
                    String acctCreditId = null;
                    PropertyConfig propertyConfig = (PropertyConfig)FFSRegistry.lookup(BPWResource.PROPERTYCONFIG);
                    if (binfo == null) {
                        String checkingLevel = propertyConfig.otherProperties.getProperty(
                                                                                         DBConsts.BANKINFO_LOOKUP_LEVEL,
                                DBConsts.BPW_BANK_LOOKUP_LEVEL_STRICT);
                        if (checkingLevel.equalsIgnoreCase(DBConsts.BPW_BANK_LOOKUP_LEVEL_STRICT)) {
                            String msg = "*** RevertFundsAllocHandler.resubmitEventHandler failed:could not find the BankID in BPW_Bank, bpw.pmt.bankinfo.lookup.level = STRICT " ;
                            FFSDebug.console(msg);
                            FFSDebug.log(msg,FFSConst.PRINT_ERR);
                            throw new FFSException(msg);
                        } else if (checkingLevel.equalsIgnoreCase(DBConsts.BPW_BANK_LOOKUP_LEVEL_LENIENT)) {
                            String msg = "*** RevertFundsAllocHandler.resubmitEventHandler : bpw.pmt.bankinfo.lookup.level = LENIENT " ;
                            // FFSDebug.console(msg);
                            FFSDebug.log(msg,FFSConst.PRINT_WRN);
                        } else if (checkingLevel.equalsIgnoreCase(DBConsts.BPW_BANK_LOOKUP_LEVEL_NONE)) {
                            String msg = "*** RevertFundsAllocHandler.resubmitEventHandler : bpw.pmt.bankinfo.lookup.level = NONE " ;
                            // FFSDebug.console(msg);
                            FFSDebug.log(msg,FFSConst.PRINT_DEV);
                        } else {
                            // None of above type
                            String msg = "*** RevertFundsAllocHandler.resubmitEventHandler failed: Incorrect value for bpw.pmt.bankinfo.lookup.level =" + checkingLevel ;
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
                    String batchKey = BATCHKEYPREFIX +    "_" + pmtinfo.FIID + "_" + _batchNum;
                    String currency = BPWUtil.validateCurrencyString(pmtinfo.CurDef);
                    FundsAllocInfo info = new FundsAllocInfo(
                                                            pmtinfo.FIID,
                                                            pmtinfo.CustomerID,
                                                            pmtinfo.BankID,
                                                            pmtinfo.AcctDebitID,
                                                            pmtinfo.AcctDebitType,
                                                            pmtinfo.getAmt(),
                                                            currency,
                                                            srvrTID,
                                                            pmtinfo.PayeeID,
                                                            payeeinfo.PayeeName,
                                                            eventSequence,
                                                            true,   // possibleDuplicate false
                                                            acctCreditId,
                                                            batchKey ,
                                                            pmtinfo.RecSrvrTID //Server transaction ID of parent recurring model
                                                            );

                    // retrieve the extrainfo info from BPW_PmtExtraInfo
                    // and assign it to extraFields of FundsAllocInfo
                    if (pmtinfo.extraFields != null) {
                        info.extraFields = pmtinfo.extraFields;
                    }

                    // cache the information
                    //
                    _vec.add( info );
                    // change status to INFUNDSREVERT
                    PmtInstruction.updateStatus( dbh, srvrTID, INFUNDSREVERT );
                    // log into AuditLog
                    if (propertyConfig.LogLevel >= AUDITLOGLEVEL_STATUSUPDATE) {
                        int bizID = Integer.parseInt(pmtinfo.CustomerID);
                        BigDecimal pmtAmount = BPWUtil.getBigDecimal(pmtinfo.getAmt());
                        String debitAcct = BPWUtil.getAccountIDWithAccountType(pmtinfo.AcctDebitID, pmtinfo.AcctDebitType);
                        ILocalizable msg = BPWLocaleUtil.getLocalizedMessage(
                                            AuditLogConsts.AUDITLOG_MSG_FUND_ALLOCATION_REVERT_RESUBMIT,
                                            null,
                                            BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
                        TransAuditLog.logTransAuditLog(dbh.conn.getConnection(),
                                                       pmtinfo.submittedBy,
                                                       null,
                                                       null,
                                                       msg,
                                                       pmtinfo.LogID,
                                                       AuditLogTranTypes.BPW_REVERTFUNDTRN,
                                                       bizID,
                                                       pmtAmount,
                                                       pmtinfo.CurDef,
                                                       srvrTID,
                                                       INFUNDSREVERT,
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
            String batchKey = BATCHKEYPREFIX + "_" + fiId + "_" + _batchNum;
            _batchNum++;
            DBConnCache.bind(batchKey, dbh);
            String currency = BPWUtil.validateCurrencyString(null);
            FundsAllocInfo info = new FundsAllocInfo(
                                                    fiId,
                                                    "",
                                                    "",
                                                    "",
                                                    "",
                                                    "",
                                                    currency,
                                                    "", //srvrTID,
                                                    "",
                                                    "",
                                                    eventSequence,
                                                    true,   // possibleDuplicate false
                                                    "",
                                                    batchKey ,null);
            _vec.add( info );

            // convert ArrayList to array and call Backend interface
            FundsAllocInfo[] ar = (FundsAllocInfo[]) _vec.toArray( new FundsAllocInfo[0] );        
            _fundsAllocator.revertFundsAllocation( ar );

            DBConnCache.unbind(batchKey);
            _vec = null;
        } else if ( eventSequence == 3 ) {    // Batch-Start sequence
        } else if ( eventSequence == 4 ) {    // Batch-End sequence
            String batchKey = BATCHKEYPREFIX +  "_" + fiId + "_" + _batchNum;
            _batchNum++;
            DBConnCache.bind(batchKey, dbh);

            // convert ArrayList to array and call Backend interface
            FundsAllocInfo[] ar = (FundsAllocInfo[]) _vec.toArray( new FundsAllocInfo[0] );
            _fundsAllocator.revertFundsAllocation( ar );

            DBConnCache.unbind(batchKey);
            _vec.clear();
        }
        FFSDebug.log("==== RevertFundsAllocHandler.resubmitEventHandler: end");
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

}
