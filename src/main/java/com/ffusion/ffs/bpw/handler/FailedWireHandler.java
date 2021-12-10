//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2002.
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
import com.ffusion.ffs.bpw.custimpl.interfaces.FailedWire;
import com.ffusion.ffs.bpw.db.DBConnCache;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.db.Wire;
import com.ffusion.ffs.bpw.interfaces.ACHConsts;
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
// by the Scheduling engine for the Failed Transfer Processing.
//
//=====================================================================

public class FailedWireHandler implements
com.ffusion.ffs.bpw.interfaces.DBConsts, FFSConst,
com.ffusion.ffs.bpw.interfaces.BPWResource, BPWScheduleHandler {



    private FailedWire          _failedWire     = null;
    private String          _batchNum               = null;
    private final String              BATCHKEYPREFIX          = "Wire_";
    private PropertyConfig      _propertyConfig         = null;
    private boolean             _okToCall               = false;
    private ArrayList wireList = null;
    private int audit_Level = 0;


    public FailedWireHandler() {
        _propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);

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

        // get a handle to the backend system
         _failedWire = null;
        
        try{
  		   BackendProvider backendProvider = getBackendProviderService();
  		   _failedWire = backendProvider.getFailedWireInstance();
  		   
  	   } catch(Throwable t){
  		   FFSDebug.log("Unable to get FailedWire Instance" , PRINT_ERR);
  	   }

    }

    //=====================================================================
    // eventHandler()
    // Description: This method is called by the Scheduling engine
    // Arguments: none
    // Returns:   none
    // Note:
    //=====================================================================
    //
    // This method passes the failed wire information to the backend.
    // The are two parts of the process.
    //                      ScheduleConstants.SCH_FLAG_FUNDSAPPROVREVERTED
    //                      ScheduleConstants.SCH_FLAG_FUNDSAPPROVREVERTFAILED
    //                      ScheduleConstants.SCH_FLAG_FUNDSFAILEDON
    //                      ScheduleConstants.SCH_FLAG_NOFUNDSON
    //
    public void eventHandler(int eventSequence,
                             EventInfoArray evts,
                             FFSConnectionHolder dbh) throws Exception {
    	String methodName = "FailedWireHandler.eventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);

        FFSDebug.log("FailedWireHandler.eventHandler: begin, eventSeq: "
                     + eventSequence
                     + ",length: " + evts._array.length, PRINT_DEV);

        boolean possibleDup = false;
        processEvents(eventSequence, evts, dbh, possibleDup);
        FFSDebug.log("FailedWireHandler.eventHandler: end", PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    /**
      * Callback method for handling resubmitted events
      * @param eventSequence event sequence number
      * @param evts array of event information objects
      * @param dbh Database connection holder
      * @exception Exception
      */
    public void resubmitEventHandler(int eventSequence, EventInfoArray evts,
                                     FFSConnectionHolder dbh)
    throws Exception {
    	String methodName = "FailedWireHandler.resubmitEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log("FailedWireHandler.resubmitEventHandler: begin, eventSeq: "
                     + eventSequence
                     + ",length: " + evts._array.length, PRINT_DEV);

        boolean possibleDup = true;
        processEvents(eventSequence, evts, dbh, possibleDup);
        FFSDebug.log("FailedWireHandler.resubmitEventHandler: end", PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    /**
      * Callback method for event processing
      * @param eventSequence event sequence number
      * @param evts array of event information objects
      * @param dbh Database connection holder
      * @exception Exception
      */
    private void processEvents(int eventSequence,
                               EventInfoArray evts,
                               FFSConnectionHolder dbh,
                               boolean possibleDup)
    throws Exception {

        final String methodName = "FailedWireHandler.processEvents: ";

        FFSDebug.log(methodName, "begin, eventSeq: "
                     + eventSequence
                     + ", possible dup: " + possibleDup
                     + ",length: " + evts._array.length, PRINT_DEV);
        _batchNum = DBUtil.getNextIndexString(DBConsts.BATCH_DB_TRANSID);

        if (eventSequence == 0) { // FIRST sequence
            _okToCall = false; // dont call backend if we dont have real
            // wireList, i.e.e wireList with
            // eventSequence == 1
            wireList = new ArrayList();

            String srvrTID  = evts._array[0].InstructionID;
            String eventID  = evts._array[0].EventID;
            String batchKey = BATCHKEYPREFIX + _batchNum;
            // add the starting sequence to the transaction list
            WireInfo wireInfo = new WireInfo();
            wireInfo.setSrvrTid(srvrTID);
            wireInfo.setEventId(eventID);
            wireInfo.setEventSequence(eventSequence);
            wireInfo.setPossibleDuplicate(possibleDup);
            wireInfo.setDbTransKey(batchKey);
            wireInfo.setFiID(evts._array[0].FIId);
            wireInfo.setWireType(evts._array[0].InstructionType);
            wireList.add(wireInfo);

        } else if (eventSequence == 1) {    // NORMAL sequence
            _okToCall = true;
            for (int i = 0; i < evts._array.length; i++) {
                WireInfo wireInfo = null;
                String srvrTID = evts._array[i].InstructionID;
                String eventID = evts._array[i].EventID;
                FFSDebug.log("FailedWireHandler.processEvents: eventSeq: "
                             + eventSequence
                             + ",srvrTID: " + srvrTID, PRINT_DEV);
                try {
                    wireInfo = new WireInfo();
                    wireInfo.setSrvrTid(evts._array[i].InstructionID);
                    wireInfo.setEventId(evts._array[i].EventID);
                    wireInfo.setFiID(evts._array[i].FIId);
                    wireInfo.setWireType(evts._array[i].InstructionType);
                    wireInfo.setPrcStatus(evts._array[i].Status);
                    // allow DB to return null
                    wireInfo = Wire.getWireInfo(dbh, wireInfo);

                    if (wireInfo == null) {
                        String msg = "*** FailedWireHandler.processEvents FAILED: "
                                     + "CAN NOT FIND THE WIREINFO: "
                                     + wireInfo + " IN BPTW DATABASE";

                        FFSDebug.log("ERRORCODE:" +
                                     ACHConsts.WIREINFO_NOT_FOUND_IN_DB, msg,
                                     PRINT_ERR);

                        // log into AuditLog
                        if (audit_Level >= DBConsts.AUDITLOGLEVEL_SYSERROR) {
                            logError(wireInfo,
                                     "Failed wire processing failed, "
                                     + "wire transfer is not found in database");
                        }
                        continue;
                    }

                    //populate CustomerInfo and FIInfo members of the WireInfo
                    //object
                    wireInfo = Wire.populateCustomerAndFIInfo(dbh, wireInfo);

                    //set "BPTW" to ProcessedBy field
                    wireInfo.setProcessedBy(DBConsts.BPTW);

                    String batchKey = BATCHKEYPREFIX + _batchNum;
                    // update WireInfo  table with status "INPROCESS"

                    // set the status to either FUNDSREVERTED_NOTIF or
                    // FUNDSREVERTFAILED_NOTIF
                    String changeToStatus = wireInfo.getPrcStatus() + "_NOTIF";
                    wireInfo.setPrcStatus(changeToStatus);
                    wireInfo.setDbTransKey(batchKey);
                    wireInfo.setEventId(evts._array[i].EventID);
                    wireInfo.setFiID(evts._array[i].FIId);
                    wireInfo.setWireType(evts._array[i].InstructionType);
                    wireInfo.setEventSequence(eventSequence);



                    Wire.updateStatus(dbh, wireInfo);
                    wireList.add(wireInfo);

                    // log into AuditLog
                    if (audit_Level >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE) {

                        doAuditLogging(dbh, wireInfo, null);
                    }
                } catch (Throwable exc) {
                    String err = "FailedWireHandler.processEvents failed:" +
                                 FFSDebug.stackTrace(exc);
                    FFSDebug.log(err);

                    // log into AuditLog
                    if (audit_Level >= DBConsts.AUDITLOGLEVEL_SYSERROR) {
                        logError(wireInfo, null);
                    }
                    throw new FFSException(exc, err);
                }
            } // End of for
        } else if (eventSequence == 2) {    // LAST sequence
            String batchKey = BATCHKEYPREFIX + _batchNum;
            _batchNum = DBUtil.getNextIndexString(DBConsts.BATCH_DB_TRANSID);


            String srvrTID = evts._array[0].InstructionID;
            String eventID = evts._array[0].EventID;
            // add the last sequence to the transaction list end
            WireInfo wireInfo = new WireInfo();
            wireInfo.setSrvrTid(srvrTID);
            wireInfo.setEventId(eventID);
            wireInfo.setEventSequence(eventSequence);
            wireInfo.setPossibleDuplicate(possibleDup);
            wireInfo.setDbTransKey(batchKey);
            wireInfo.setFiID(evts._array[0].FIId);
            wireInfo.setWireType(evts._array[0].InstructionType);
            wireList.add(wireInfo);

            if (_okToCall) { // ignore schedule with empty data
                // push the WireInfo list to backend handler
                processFailedWires(dbh, batchKey, wireList);
            }

        } else if (eventSequence == 3) {    // Batch-Start sequence
        } else if (eventSequence == 4) {    // Batch-End sequence
            String batchKey = BATCHKEYPREFIX + _batchNum;
            _batchNum = DBUtil.getNextIndexString(DBConsts.BATCH_DB_TRANSID);


            if (_okToCall) { // ignore schedule with empty data
                // push the WireInfo list to backend handler
                processFailedWires(dbh, batchKey, wireList);
            }
        }
        FFSDebug.log("FailedWireHandler.processEvents: end", PRINT_DEV);
    }


    /**
     * push the InterTranInfos to backend handler
     *
     * @param dbh
     */
    private void processFailedWires(FFSConnectionHolder dbh, String batchKey, ArrayList wireList)
    throws Exception {

        DBConnCache dbConnCache = (DBConnCache)FFSRegistry.lookup(DBCONNCACHE);
        DBConnCache.bind(batchKey, dbh);

        WireInfo[] wireInfoList =
        (WireInfo[]) wireList.toArray(new WireInfo[0]);
        Hashtable extraInfo = new Hashtable(); // default no extra

        _failedWire.processFailedWires(wireInfoList, extraInfo);
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

        String curMethodName = "FailedWireHandler.doAuditLogging";

        String logDesc = desc;
        String toAcctId = null;
        String toAcctRTN = null;
        String fromAcct = null;
        String amount = null;
        int businessId = 0;
        AuditLogRecord auditLogRecord = null;

        if (wireInfo == null) {
            return;
        }

        if (desc == null) {
            logDesc = WIRE_FAILED_HANDLER_NOTIF;
        }

        //Do Audit logging here
        try {

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

            auditLogRecord = new AuditLogRecord(wireInfo.getUserId(), //userId
                                                null, //do not log agentId
                                                null, //do not log agentType
                                                logDesc,               //description
                                                wireInfo.getExtId(),
                                                AuditLogTranTypes.BPW_FAILEDWIRETRN, //tranType
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
    private void logError( WireInfo wireInfo, String desc) {

        String curMethodName = "FailedWireHandler.logError";

        FFSConnectionHolder logDbh = null;
        try {

            // Get a connection handle for the Eror logging
            logDbh = new FFSConnectionHolder();
            logDbh.conn = DBUtil.getConnection();
            if (logDbh.conn == null) {
                String err = curMethodName + "Can not get DB Connection.";
                FFSDebug.log(err, PRINT_ERR);
            }

            if (desc != null) {
                doAuditLogging(logDbh, wireInfo, desc);
            } else {
                doAuditLogging(logDbh, wireInfo, "Failed Wire processing failed,"
                               + " unknown error occurred");
            }
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
