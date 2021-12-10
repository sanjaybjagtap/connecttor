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
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import com.ffusion.beans.wiretransfers.WireDefines;
import com.ffusion.ffs.bpw.audit.TransAuditLog;
import com.ffusion.ffs.bpw.custimpl.interfaces.BackendProvider;
import com.ffusion.ffs.bpw.custimpl.interfaces.WireBackendHandler;
import com.ffusion.ffs.bpw.db.DBConnCache;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.db.Wire;
import com.ffusion.ffs.bpw.interfaces.ACHConsts;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfo;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.WireInfo;
import com.ffusion.ffs.bpw.interfaces.util.AccountUtil;
import com.ffusion.ffs.bpw.util.reconciliation.ReconciliationUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.scheduling.constants.ScheduleConstants;
import com.ffusion.ffs.scheduling.db.Event;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.ffusion.ffs.util.FFSUtil;
import com.ffusion.util.logging.AuditLogRecord;
import com.ffusion.util.logging.AuditLogTranTypes;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

/**
  * <P>This class contains a callback handler that is registered in the
  * IntructionType table.  The registered callback handler will be called
  * by the Scheduling engine for the transaction Processing.
  *
  *
  *
  */

public class WireHandler implements
com.ffusion.ffs.bpw.interfaces.DBConsts,FFSConst,
com.ffusion.ffs.bpw.interfaces.BPWResource, BPWScheduleHandler {

    private WireBackendHandler    _wireBackendHandler   = null;
    private ArrayList wireList = null;

    private PropertyConfig  _propertyConfig = null;
    private boolean         _okToCall = false;

    private int audit_Level = 0;

    public WireHandler() {
        _propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);
        // get a handle to the backend system
       
        try{
      		BackendProvider backendProvider = getBackendProviderService();
      		_wireBackendHandler = backendProvider.getWireBackendHandlerInstance();

      	} catch(Throwable t){
      		FFSDebug.log("Unable to get WireBackendHandler Instance" , PRINT_ERR);
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
    }

    /**
      * Callback method for event processing
      * @param eventSequence event sequence number
      * @param evts array of event information objects
      * @param dbh Database connection holder
      * @exception Exception
      */
    public void eventHandler(int eventSequence, EventInfoArray evts,
                             FFSConnectionHolder dbh) throws Exception {
    	String methodName = "WireHandler.eventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);

        FFSDebug.log("WireHandler.eventHandler: begin, eventSeq: "
                     + eventSequence
                     + ",length: " + evts._array.length, PRINT_DEV);
        processEvents(eventSequence,evts,dbh, false); // possible dup false:
        FFSDebug.log("WireHandler.eventHandler: end", PRINT_DEV);
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
                                     FFSConnectionHolder dbh) throws Exception {
    	
    	String methodName = "WireHandler.resubmitEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log("WireHandler.resubmitEventHandler: begin, eventSeq: "
                     + eventSequence
                     + ",length: " + evts._array.length, PRINT_DEV);

        processEvents(eventSequence,evts,dbh, true); // possible dup true:
        FFSDebug.log("WireHandler.resubmitEventHandler: end", PRINT_DEV);
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

        FFSDebug.log("WireHandler.processEvents: begin, eventSeq: "
                     + eventSequence
                     + ", possible dup: " + possibleDup
                     + ",length: " + evts._array.length, PRINT_DEV);

        if (eventSequence == 0) { // FIRST sequence
            _okToCall = false; // dont call backend if we dont have real
            // wireList, i.e.e wireList with
            // eventSequence == 1

            wireList = new ArrayList();
            String srvrTID  = evts._array[0].InstructionID;
            String eventID  = evts._array[0].EventID;


            // add the starting sequence to the transaction list
            WireInfo wireInfo = new WireInfo();
            wireInfo.setSrvrTid(srvrTID);
            wireInfo.setEventId(eventID);
            wireInfo.setEventSequence(eventSequence);
            wireInfo.setPossibleDuplicate(possibleDup);
            wireInfo.setFiID(evts._array[0].FIId);
            wireInfo.setWireType(evts._array[0].InstructionType);
            wireList.add(wireInfo);

        } else if (eventSequence == 1) {    // NORMAL sequence
            _okToCall = true;
            for (int i = 0; i < evts._array.length; i++) {
                WireInfo wireInfo = null;
                String srvrTID = evts._array[i].InstructionID;
                String eventID = evts._array[i].EventID;
                FFSDebug.log("WireHandler.processEvents: eventSeq: "
                             + eventSequence
                             + ",srvrTID: " + srvrTID +
                             ", FIID: " + evts._array[i].FIId, PRINT_DEV);
                try {
                    wireInfo = new WireInfo();
                    wireInfo.setSrvrTid(evts._array[i].InstructionID);
                    wireInfo.setEventId(evts._array[i].EventID);
                    wireInfo.setFiID(evts._array[i].FIId);
                    wireInfo.setWireState(evts._array[i].InstructionType);
                    wireInfo.setPrcStatus(evts._array[i].Status);
                    // allow DB to return null
                    wireInfo = Wire.getWireInfo(dbh, wireInfo);

                    if (wireInfo == null) {
                        String msg = "ERRORCODE:"
                                     + ACHConsts.WIREINFO_NOT_FOUND_IN_DB
                                     + " *** WireHandler.processEvents FAILED: "
                                     + "CAN NOT FIND THE WIREINFO: "
                                     + wireInfo + " IN BPTW DATABASE";

                        FFSDebug.log(msg, PRINT_ERR);

                        // log into AuditLog
                        if (audit_Level >= DBConsts.AUDITLOGLEVEL_SYSERROR) {
                            logError(wireInfo,
                                     "Wire backend processing failed, "
                                     + "wire transfer is not found in database");
                        }
                        continue;
                    }

                    //populate CustomerInfo and FIInfo members of the WireInfo
                    //object
                    wireInfo = Wire.populateCustomerAndFIInfo(dbh, wireInfo);

                    //set "BPTW" to ProcessedBy field
                    wireInfo.setProcessedBy(DBConsts.BPTW);

                    // if the current status of this wire is not FUNDSAPPROVED
                    // set the status to possible duplicate
                    FFSDebug.log("WireHandler.processEvents, wire status: ",
                                 wireInfo.getPrcStatus(), PRINT_DEV);
                    if (!FUNDSAPPROVED.equalsIgnoreCase(wireInfo.getPrcStatus())) {
                        wireInfo.setPossibleDuplicate(true);
                    } else {
                        wireInfo.setPossibleDuplicate(possibleDup);
                    }
                    String changeToStatus = INPROCESS;
                    if (wireInfo.getCustomerInfo() != null && !DBConsts.ACTIVE.equals(wireInfo.getCustomerInfo().status))
                        changeToStatus = FAILEDON;
                    wireInfo.setPrcStatus(changeToStatus);
                    String datePosted = FFSUtil.getDateString("yyyyMMddHHmmss");
                    wireInfo.setDatePosted(datePosted);
                    wireInfo.setEventId(evts._array[i].EventID);
                    wireInfo.setFiID(evts._array[i].FIId);
                    wireInfo.setWireState(evts._array[i].InstructionType);
                    wireInfo.setEventSequence(eventSequence);
                    Wire.updateToBackendStatus(dbh, wireInfo);
                    if (INPROCESS.equals(changeToStatus))
                    {
                    	
                    	// Create an event which is used for the retry status
						// check and reconciliation.
                    	createEvent(dbh, wireInfo, evts._array[i], new HashMap());
                    	
                        wireList.add(wireInfo);

                        // log into AuditLog
                        if (audit_Level >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE) {

                            doAuditLogging(dbh, wireInfo, null);
                        }
                    } else {
                        // log into AuditLog
                        if (audit_Level >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE) {

                            doAuditLogging(dbh, wireInfo, "Wire backend processing failed, customer is inactive");
                        }
                    }
                } catch (Throwable exc) {
                    String err = "WireHandler.processEvents failed:" +
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


            String srvrTID = evts._array[0].InstructionID;
            String eventID = evts._array[0].EventID;
            // add the last sequence to the transaction list end
            WireInfo wireInfo = new WireInfo();
            wireInfo.setSrvrTid(srvrTID);
            wireInfo.setEventId(eventID);
            wireInfo.setEventSequence(eventSequence);
            wireInfo.setPossibleDuplicate(possibleDup);
            wireInfo.setFiID(evts._array[0].FIId);
            wireInfo.setWireState(evts._array[0].InstructionType);
            wireList.add(wireInfo);
            if (_okToCall) { // ignore schedule with empty data

                // push the WireInfo list to backend handler
                processTrans(dbh, wireList);
            }
            wireList = null;

        } else if (eventSequence == 3) {    // Batch-Start sequence
        } else if (eventSequence == 4) {    // Batch-End sequence
            if (_okToCall) { // ignore schedule with empty data
                // push the WireInfo list to backend handler
                processTrans(dbh, wireList);
            }
        }
        FFSDebug.log("WireHandler.processEvents: end", PRINT_DEV);
    }


    /**
     * push the WireInfos to backend handler
     *
     * @param dbh
     */
    private void processTrans(FFSConnectionHolder dbh, ArrayList wireList)
    throws Exception {

        // convert ArrayList to array and call Backend interface
        WireInfo[] wireInfoList =
        (WireInfo[]) wireList.toArray(new WireInfo[0]);
        Hashtable extraInfo = new Hashtable(); // default no extra

        // Generate a new batch key and bind the db connection
        // using the batch key.
        String batchKey = DBConnCache.getNewBatchKey();
        DBConnCache.bind(batchKey, dbh);

        // Populate each entry with our batch key.
        for (int arIdx = 0; arIdx < wireInfoList.length; arIdx++) {
            wireInfoList[arIdx].setDbTransKey(batchKey);
        } // End for-loop


        _wireBackendHandler.processWire(wireInfoList, extraInfo);
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

        String curMethodName = "WireHandler.doAuditLogging: ";

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
            logDesc = WIRE_HANDLER_IN_PROCESS;
        }

        //Do Audit logging here
        try {

            FFSDebug.log(curMethodName, "wire: " + wireInfo, PRINT_DEV);

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
                                                AuditLogTranTypes.BPW_WIRETOBACKEND,                //tranType
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

        String curMethodName = "WireHandler.logError";

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
                doAuditLogging(logDbh, wireInfo, "Wire backend processing failed,"
                               + " unknown error occurred");
            }
            // 9/16 - Was not committing changes to audit log
            logDbh.conn.commit();
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
	
	/**
	 * Update event info and persist to database.
	 *
	 * @param dbh FFSConnectionHolder object
	 * @param wireInfo the Wire object
	 * @param evt event info details
	 * @param extra the extra
	 * @throws FFSException the FFS exception
	 */
	protected void createEvent(FFSConnectionHolder dbh, WireInfo wireInfo, EventInfo evt, HashMap extra) throws FFSException {
		String thisMethod = "WireHandler.createEvent : ";
		String reconciliationID = null;
        // Create an entry in EventInfo table which is used for the retry status check and reconciliation.
        try {
        	reconciliationID = ReconciliationUtil.getReconciliationId(wireInfo, extra);
        	wireInfo.setReconciliationId(reconciliationID);
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
	            Event.createEvent(dbh, eventInfo);
			} else {
				evt.Status = ScheduleConstants.EVT_STATUS_INPROCESS;
				Event.createEvent(dbh, evt);
			}
			dbh.conn.commit();
		} catch (Exception ex) {
			FFSDebug.log(ex, thisMethod + " Unable to create event.");
			throw new FFSException(ex, "Unable to create event.");
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
