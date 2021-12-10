//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2001.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import com.ffusion.beans.wiretransfers.WireDefines;
import com.ffusion.ffs.bpw.audit.TransAuditLog;
import com.ffusion.ffs.bpw.custimpl.interfaces.BackendProvider;
import com.ffusion.ffs.bpw.custimpl.interfaces.WireApproval;
import com.ffusion.ffs.bpw.db.DBConnCache;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.db.Wire;
import com.ffusion.ffs.bpw.interfaces.ACHConsts;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.WireInfo;
import com.ffusion.ffs.bpw.interfaces.util.AccountUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.ffusion.util.logging.AuditLogRecord;
import com.ffusion.util.logging.AuditLogTranTypes;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

//=====================================================================
//
// This class contains a callback handler that is registered in the
// InstructionType table.  The registered callback handler will be called
// by the Scheduling engine for the Funds Approval Processing.
//
//=====================================================================

public class WireApprovalHandler implements BPWResource, FFSConst, DBConsts, BPWScheduleHandler {

    private WireApproval _wireApproval = null;
    private ArrayList wireList = null;
    private PropertyConfig  _propertyConfig = null;
    private int audit_Level = 0;
    private boolean supportFundsApprov = false;


    public WireApprovalHandler() {
        _propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);
       try{
    		BackendProvider backendProvider = getBackendProviderService();
    		_wireApproval = backendProvider.getWireApprovalInstance();

    	} catch(Throwable t){
    		FFSDebug.log("Unable to get WireApproval Instance" , PRINT_ERR);
    	}
        //Get Audit level
        String level = null;
        try {
            level = _propertyConfig.otherProperties.getProperty(
                                                               WIRE_AUDIT_OPTION);

            if (level == null) {
                audit_Level = DBConsts.AUDITLOGLEVEL_NONE;
            } else {
                audit_Level = Integer.parseInt( level );
            }
        } catch (Exception e) {
            FFSDebug.log("WireApprovalHandler. Invalid Audit log level value",
                         level, PRINT_ERR);
            audit_Level = DBConsts.AUDITLOGLEVEL_NONE;
        }

        supportFundsApprov = Boolean.valueOf(
                                            _propertyConfig.otherProperties.getProperty(
                                                                                       WIRE_SUPPORT_FUNDSAPPROVAL,
                                                                                       DEFAULT_WIRE_SUPPORT_FUNDSAPPROVAL)).booleanValue();
    }

    //==========================================================================
    // eventHandler()
    // Description: This method is called by the Scheduling engine
    // Arguments: none
    // Returns:   none
    // Note:
    //==========================================================================
    //
    public void eventHandler(int eventSequence,
                             EventInfoArray evts,
                             FFSConnectionHolder dbh)throws Exception {
    	String methodName = "WireApprovalHandler.eventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);

        //Wire Funds approval is not supported
        if (!supportFundsApprov) {
            FFSDebug.log("WireApprovalHandler.eventHandler: ",
                         "Funds approval is not supported", PRINT_DEV);
            PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
            return;
        }

        FFSDebug.log("WireApprovalHandler.eventHandler: begin, eventSeq: " +
                     eventSequence  + ",length: " + evts._array.length, PRINT_DEV);
        processEvent(eventSequence, evts, dbh, false);
        FFSDebug.log("WireApprovalHandler.eventHandler: end", PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    //==========================================================================
    // resubmitEventHandler()
    // Description: This method is called by the Scheduling engine during
    //    event resubmission.  It will set the possibleDuplicate to true
    //    before calling the ToBackend handler.
    // Arguments: none
    // Returns:   none
    // Note:
    //==========================================================================
    public void resubmitEventHandler(int eventSequence,
                                     EventInfoArray evts, FFSConnectionHolder dbh)throws Exception {
    	String methodName = "WireApprovalHandler.resubmitEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);

        if (!supportFundsApprov) {
            FFSDebug.log("WireApprovalHandler.eventHandler: ",
                         "Funds approval is not supported", PRINT_DEV);
            PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
            return;
        }

        FFSDebug.log("WireApprovalHandler.resubmitEventHandler: begin, ",
                     "eventSeq: " + eventSequence + ",length: " +
                     evts._array.length, PRINT_DEV);
        processEvent(eventSequence, evts, dbh, false);
        FFSDebug.log("WireApprovalHandler.resubmitEventHandler: end", PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    //==========================================================================
    //
    private void processEvent(int eventSequence,
                              EventInfoArray evts,
                              FFSConnectionHolder dbh,
                              boolean possibleDuplicate) throws Exception {

        final String methodName = "WireApprovalHandler.processEvent: ";


        FFSDebug.log(methodName, " begin, eventSeq: " +
                     eventSequence + ",length: " + evts._array.length, PRINT_DEV);

        if (eventSequence == 0) {         // FIRST sequence
            wireList = new ArrayList();
            String srvrTID = evts._array[0].InstructionID;
            String eventID = evts._array[0].EventID;
            WireInfo wireInfo = new WireInfo();
            wireInfo.setSrvrTid(srvrTID);
            wireInfo.setEventId(eventID);
            wireInfo.setEventSequence(eventSequence);
            wireInfo.setPossibleDuplicate(possibleDuplicate);
            wireInfo.setFiID(evts._array[0].FIId);
            wireInfo.setWireState(evts._array[0].InstructionType);
            wireList.add(wireInfo);

        } else if (eventSequence == 1) {    // NORMAL sequence
            for (int i = 0; i < evts._array.length; i++) {
                String srvrTID = evts._array[i].InstructionID;
                String eventID = evts._array[i].EventID;
                FFSDebug.log(methodName, "eventSeq: " + eventSequence
                             + ",srvrTID: " + srvrTID, PRINT_DEV);
                WireInfo wireInfo = null;
                try {
                    wireInfo = new WireInfo();
                    wireInfo.setSrvrTid(srvrTID);
                    wireInfo.setFiID(evts._array[0].FIId);
                    wireInfo.setWireState(evts._array[0].InstructionType);

                    wireInfo = Wire.getWireInfo(dbh, wireInfo);
                    if (wireInfo == null) {
                        String err = "ERRORCODE:" +
                                     ACHConsts.WIREINFO_NOT_FOUND_IN_DB + methodName +
                                     " FAILED: CAN NOT FIND THE SrvrTID: " +
                                     srvrTID + " in BPW_WireInfo";
                        FFSDebug.log(err, PRINT_ERR);
                        continue;
                    }

                    //populate CustomerInfo and FIInfo members of the WireInfo
                    //object
                    wireInfo = Wire.populateCustomerAndFIInfo(dbh, wireInfo);

                    //set "BPTW" to ProcessedBy field
                    wireInfo.setProcessedBy(DBConsts.BPTW);

                    // assemble WireInfo
                    //In case of not resubmit possible duplicate may occur if the status
                    // is not WILLPROCESSON
                    String currStatus = wireInfo.getPrcStatus();
                    FFSDebug.log("WireHandler.processEvents, wire currStatus: ",
                                 currStatus, PRINT_DEV);

                    // if not WILLPROCESSON  or NOFUNDS, means possible duplicated
                    if (!WILLPROCESSON.equalsIgnoreCase(currStatus) &&
                        !NOFUNDS.equalsIgnoreCase(currStatus)) {
                        wireInfo.setPossibleDuplicate(true);
                    } else {
                        wireInfo.setPossibleDuplicate(possibleDuplicate);
                    }

                    wireInfo.setSrvrTid(srvrTID);
                    wireInfo.setEventId(eventID);
                    wireInfo.setEventSequence(eventSequence);
                    wireInfo.setFiID(evts._array[0].FIId);
                    wireInfo.setWireState(evts._array[0].InstructionType);
                    wireInfo.setPrcStatus(INFUNDSAPPROVAL);
                    // udate the record in the database
                    Wire.updateStatus(dbh, wireInfo);

                    // cache the information
                    wireList.add(wireInfo);

                    // log into AuditLog
                    if (audit_Level >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE) {

                        doAuditLogging(dbh, wireInfo, null);
                    }
                } catch (Throwable exc) {
                    String err = methodName + "Failed:" + FFSDebug.stackTrace(exc);
                    FFSDebug.log(err, PRINT_ERR);

                    // log into AuditLog
                    if (audit_Level >= DBConsts.AUDITLOGLEVEL_SYSERROR) {
                        logError(wireInfo);
                    }
                    throw new FFSException(err);
                }
            }
        } else if (eventSequence == 2) {    // LAST sequence

            String srvrTID = evts._array[0].InstructionID;
            String eventID = evts._array[0].EventID;
            WireInfo wireInfo = new WireInfo();
            wireInfo.setSrvrTid(srvrTID);
            wireInfo.setEventId(eventID);
            wireInfo.setEventSequence(eventSequence);
            wireInfo.setPossibleDuplicate(possibleDuplicate);
            wireInfo.setFiID(evts._array[0].FIId);
            wireList.add(wireInfo);

            // convert vector to array and call Backend wireface
            processTrans(dbh, wireList);
            wireList = null;
        } else if (eventSequence == 3) {    // Batch-Start sequence
        } else if (eventSequence == 4) {    // Batch-End sequence
            processTrans(dbh, wireList);
        }
        FFSDebug.log(methodName, " end", PRINT_DEV);
    }


    /**
     * push the WireInfos to backend handler
     *
     * @param dbh
     */
    private void processTrans(FFSConnectionHolder dbh, ArrayList wireList)
    throws Exception {

        // Generate a new batch key and bind the db connection
        // using the batch key.
        String batchKey = DBConnCache.getNewBatchKey();
        DBConnCache.bind(batchKey, dbh);

        WireInfo[] wireInfoList =
        (WireInfo[]) wireList.toArray(new WireInfo[0]);
        Hashtable extInfo = new Hashtable(); // default no extra

        // Populate each entry with our batch key.
        for (int arIdx = 0; arIdx < wireInfoList.length; arIdx++) {
            wireInfoList[arIdx].setDbTransKey(batchKey);
        } // End for-loop

        _wireApproval.addWiresApproval(wireInfoList, extInfo);
        // Remove the binding of the db connection and the batch key.
        DBConnCache.unbind(batchKey);
        wireList.clear();
    }

    /**
     * Do Audit logging
     *
     * @param wireInfo
     * @param error - If logging for error or exception
     * @exception Exception
     */
    private void doAuditLogging( FFSConnectionHolder dbh, WireInfo wireInfo,
                                 String desc)
    throws Exception {

        String curMethodName = "WireApprovalHandler.doAuditLogging";

        String logDesc = desc;
        String toAcctId = null;
        String toAcctRTN = null;
        String fromAcct = null;
        String userId = null;
        String amount = null;
        int businessId = 0;
        AuditLogRecord auditLogRecord = null;

        if (wireInfo == null) {
            return;
        }

        if (desc == null) {
            logDesc = WIRE_IN_FUNDSAPPROVAL;
        }

        //Do Audit logging here
        try {

            userId = wireInfo.getUserId();
            amount = wireInfo.getAmount();

            if ( (amount == null) || (amount.trim().length() == 0) ) {
                amount = "-1";
            }

            if (wireInfo.getWirePayeeInfo() != null) {
                if (wireInfo.getWireDest().equals(WireDefines.WIRE_HOST)) {
                    toAcctId = WireDefines.WIRE_HOST;
                } else {
                    toAcctId = AccountUtil.buildWirePayeeBankAcctId(wireInfo.getWirePayeeInfo());
                }

                if (wireInfo.getWirePayeeInfo().getBeneficiaryBankInfo() != null) {
                    toAcctRTN = wireInfo.getWirePayeeInfo().getBeneficiaryBankInfo().getFedRTN();
                }
            }

            if (wireInfo.getWireDest().equals(WireDefines.WIRE_HOST)) {
                fromAcct = WireDefines.WIRE_HOST;
            } else {
                fromAcct = AccountUtil.buildWireFromAcctId(wireInfo);
            }

            //Differentiate between consumer and business
            if ( (wireInfo.getCustomerID().equals(wireInfo.getUserId())) ||
                 (wireInfo.getCustomerID().equals(wireInfo.getSubmitedBy())) ) { //Consumer

                businessId = 0;
            } else { //Business
                businessId = Integer.parseInt(wireInfo.getCustomerID());
            }

            auditLogRecord = new AuditLogRecord(userId, //userId
                                                null, //do not log agentId
                                                null, //do not log agentType
                                                logDesc, //description
                                                wireInfo.getExtId(),
                                                AuditLogTranTypes.BPW_WIRETOAPPROVAL,                //tranType
                                                businessId, //BusinessId
                                                new BigDecimal(amount),
                                                wireInfo.getOrigCurrency(),
                                                wireInfo.getSrvrTid(),
                                                wireInfo.getPrcStatus(),
                                                toAcctId,
                                                toAcctRTN,
                                                fromAcct,
                                                wireInfo.getFromBankId(),
                                                -1);

            TransAuditLog.logTransAuditLog( auditLogRecord,
                                            dbh.conn.getConnection());

        } catch (Exception ex) {
            String errDescrip = curMethodName + " failed " + ex;
            FFSDebug.log(errDescrip + FFSDebug.stackTrace(ex), PRINT_ERR);
            throw new FFSException(ex, errDescrip);
        }
    }

    /**
     * Do Audit logging
     *
     * @param wireInfo
     * @param error - If logging for error or exception
     * @exception Exception
     */
    private void logError( WireInfo wireInfo) {

        String curMethodName = "WireApprovalHandler.logError";

        FFSConnectionHolder logDbh = null;
        try {

            // Get a connection handle for the Eror logging
            logDbh = new FFSConnectionHolder();
            logDbh.conn = DBUtil.getConnection();
            if (logDbh.conn == null) {
                String err = curMethodName + "Can not get DB Connection.";
                FFSDebug.log(err, PRINT_ERR);
            }

            doAuditLogging(logDbh, wireInfo, "Wire approval processing failed,"
                           + " unknown error occurred");
        } catch (Exception ex) {
            String errDescrip = curMethodName + " failed " + ex;
            FFSDebug.log(errDescrip + FFSDebug.stackTrace(ex), PRINT_ERR);
        } finally {
            DBUtil.freeConnection(logDbh.conn);
        }
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
