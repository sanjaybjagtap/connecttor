//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2001.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.util.ArrayList;
import java.util.List;

import com.ffusion.ffs.bpw.db.CustPayee;
import com.ffusion.ffs.bpw.db.CustPayeeRoute;
import com.ffusion.ffs.bpw.db.Payee;
import com.ffusion.ffs.bpw.db.PmtInstruction;
import com.ffusion.ffs.bpw.fulfill.FulfillAgent;
import com.ffusion.ffs.bpw.interfaces.BPWException;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.CustomerPayeeInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfo;
import com.ffusion.ffs.bpw.interfaces.InstructionType;
import com.ffusion.ffs.bpw.interfaces.PayeeInfo;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.util.BPWRegistryUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.scheduling.constants.ScheduleConstants;
import com.ffusion.ffs.scheduling.db.Event;
import com.ffusion.ffs.scheduling.db.EventInfoLog;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.sap.banking.common.interceptors.PerfLoggerUtil;



public class PayeeHandler implements DBConsts,FFSConst, BPWResource {
    /**
	 *
	 */
	private static final long serialVersionUID = 3723091843596353195L;


	public PayeeHandler() {

    }

    // handlePayees()
    //
    // This method passes the CustomerPayee information and the Payee information
    // to the fulfillment system.
    // The are two parts of the process.
    // First it checks for performing crash recovery from the EventInfo record.
    // Second, in normal processing, it saves the data in the EventInfo record in case
    // it needs to do crash recovery.
    // The record from the EventInfo is copied over to the EventInfoLog for the purpose
    // of event resubmitting.
    // The EventInfo and EventInfoLog table are defined as follow:
    //      EventID - save the EventID of the EventInfo
    //      InstructionID - save the CustomerID of the CustomerPayee record
    //                      or the PayeeID of the Payee record
    //      InstructionType - save the InstructionType of the schedule event
    //      ScheduleFlag - save the following data for the CustomerPayee
    //                      ScheduleConstants.SCH_FLAG_CUSTPAYEE_NEW
    //                      ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODACCT
    //                      ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODPAYEE
    //                      ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODBOTH
    //                      ScheduleConstants.SCH_FLAG_CUSTPAYEE_CAN
    //                     or the following data for the Payee
    //                      ScheduleConstants.SCH_FLAG_PAYEE_NEW
    //      LogID - save the PayeeListID of the CustomerPayee record
    //

	public void handlePayees( String fiId, String instructionType, FFSConnectionHolder dbh )
    throws Exception
    {
		String methodName = "PayeeHandler.handlePayees";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log("=== PayeeHandler.handlePayees: begin, instructionType=" + instructionType, PRINT_DEV );

        FulfillAgent fagent = (FulfillAgent)FFSRegistry.lookup(FULFILLAGENT);
        if ( fagent == null) {
             PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
            throw new BPWException("FulfillAgent is null!!");
        }
        // Find the key for this instruction type
        String cacheKey = FulfillAgent.findCacheKey(fiId, instructionType);
        int routeID = fagent.getRouteId( fiId, instructionType );

        try {
            PropertyConfig _propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);
            int size =  _propertyConfig.getBatchSize();
            CustPayee.setBatchSize( size );
            Payee.setBatchSize( size );


            List cache = FulfillAgent.getPayeeCache(cacheKey);
            fagent.startCustomerPayeeBatch(fiId, instructionType, dbh );

            // Retrieve EventInfo for those with status of "SUBMITTED" and
            // with "SCH_FLAG_CUSTPAYEE_NEW" in ScheduleFlag in batch.
            // Send the batch to the fulfillment system
            List custPayeeCache = FulfillAgent.getCustomerPayeeCache( cacheKey );
            recoverCustPayeeFromCrash(dbh, fiId, instructionType, routeID,
                                      ScheduleConstants.SCH_FLAG_CUSTPAYEE_NEW,
                             fagent, custPayeeCache, cache);

            // Retrieve a batch of Customer-Payee record with NEW status.
            // Save in EventInfo with ScheduleFlag=SCH_FLAG_CUSTPAYEE_NEW.
            // Copy the EventInfo into EventInfoLog.
            // Send the batch to the fulfillment system
            doCustPayees(dbh, fiId, instructionType, NEW, routeID, fagent, custPayeeCache, cache);

            // Retrieve EventInfo for those with status of "SUBMITTED" and
            // with "SCH_FLAG_CUSTPAYEE_MODACCT" in ScheduleFlag in batch.
            // Send the batch to the fulfillment system
            custPayeeCache = FulfillAgent.getCustomerPayeeModCache( cacheKey );
            recoverCustPayeeFromCrash(dbh, fiId, instructionType, routeID,
                                      ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODACCT,
                             fagent, custPayeeCache, cache);
            // Retrieve EventInfo for those with status of "SUBMITTED" and
            // with "SCH_FLAG_CUSTPAYEE_MODPAYEE" in ScheduleFlag in batch.
            // Send the batch to the fulfillment system
            recoverCustPayeeFromCrash(dbh, fiId, instructionType, routeID,
                                      ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODPAYEE,
                             fagent, custPayeeCache, cache);
            // Retrieve EventInfo for those with status of "SUBMITTED" and
            // with "SCH_FLAG_CUSTPAYEE_MODBOTH" in ScheduleFlag in batch.
            // Send the batch to the fulfillment system
            recoverCustPayeeFromCrash(dbh, fiId, instructionType, routeID,
                                      ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODBOTH,
                             fagent, custPayeeCache, cache);

            // Retrieve a batch of Customer-Payee record with MOD status.
            // Save in EventInfo with ScheduleFlag=SCH_FLAG_CUSTPAYEE_MODACCT,
            // SCH_FLAG_CUSTPAYEE_MODPAYEE or SCH_FLAG_CUSTPAYEE_MODBOTH.
            // Copy the EventInfo into EventInfoLog.
            // Send the batch to the fulfillment system
            doCustPayees(dbh, fiId, instructionType, MOD, routeID, fagent, custPayeeCache, cache);

            // Retrieve EventInfo for those with status of "SUBMITTED" and
            // with "SCH_FLAG_CUSTPAYEE_CAN" in ScheduleFlag in batch.
            // Send the batch to the fulfillment system
            custPayeeCache = FulfillAgent.getCustomerPayeeCancCache( cacheKey );
            recoverCustPayeeFromCrash(dbh, fiId, instructionType, routeID,
                                      ScheduleConstants.SCH_FLAG_CUSTPAYEE_CAN,
                             fagent, custPayeeCache, cache);

            // Retrieve a batch of Customer-Payee record with CAN status.
            // Save in EventInfo with ScheduleFlag=SCH_FLAG_CUSTPAYEE_CAN.
            // Copy the EventInfo into EventInfoLog.
            // Send the batch to the fulfillment system
            doCustPayees(dbh, fiId, instructionType, CANC, routeID, fagent, custPayeeCache, cache);

            // Retrieve a batch of Customer-Payee record with PENDING status.
            // Save in EventInfo with ScheduleFlag=SCH_FLAG_CUSTPAYEE_CAN.
            // Copy the EventInfo into EventInfoLog.
            // Send the batch to the fulfillment system
            doPendingCustPayees(dbh, fiId, instructionType, routeID, fagent, custPayeeCache, cache);

            fagent.endCustomerPayeeBatch(fiId, instructionType, dbh );

            fagent.startPayeeBatch(fiId, instructionType, dbh );
            // Retrieve EventInfo for those with status of "SUBMITTED" and
            // with "SCH_FLAG_PAYEE_NEW" in ScheduleFlag in batch.
            // Send the batch to the fulfillment system
            recoverPayeeFromCrash(dbh, fiId, instructionType, ScheduleConstants.SCH_FLAG_PAYEE_NEW,
                                  fagent, cache);

            doPayees(dbh, fiId, instructionType, NEW, fagent, cache);

            fagent.endPayeeBatch(fiId, instructionType, dbh );
        } catch (Exception exc) {
            FFSDebug.log("PayeeHandler.handlePayees failed. Error: " + FFSDebug.stackTrace(exc));
            
            throw new Exception( exc.toString());
        } finally {
            PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
        }

        FFSDebug.log("=== PayeeHandler.handlePayees: end, instructionType=" + instructionType,PRINT_DEV );
       
    }



	public void resubmitPayees(String fiId, String instructionType, String logDate, FFSConnectionHolder dbh )
    throws Exception
    {
        FFSDebug.log("=== PayeeHandler.resubmitPayees: begin, instructionType=" + instructionType +
                     ",logDate=" + logDate, PRINT_DEV );

        try {
            FulfillAgent fagent = (FulfillAgent)FFSRegistry.lookup(FULFILLAGENT);
            if ( fagent == null) {
                throw new BPWException("FulfillAgent is null!!");
            }

            PropertyConfig _propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);
            int size =  _propertyConfig.getBatchSize();
            CustPayee.setBatchSize( size );
            Payee.setBatchSize( size );

            // Find the key for this instruction type

            int routeID = fagent.getRouteId(fiId, instructionType);
            String cacheKey = FulfillAgent.findCacheKey(fiId, instructionType);

            List cache = FulfillAgent.getPayeeCache(cacheKey);
            List custPayeeCache = FulfillAgent.getCustomerPayeeCache( cacheKey );

            fagent.startCustomerPayeeBatch(fiId, instructionType, dbh );
            // Retrieve EventInfoLog for those
            // with "SCH_FLAG_CUSTPAYEE_NEW" in ScheduleFlag in batch.
            // Send the batch to the fulfillment system
            resubmitCustPayeeFromEventInfoLog(dbh, fiId, instructionType, routeID,
                                              ScheduleConstants.SCH_FLAG_CUSTPAYEE_NEW,
                                              logDate, fagent, custPayeeCache, cache );
            // Retrieve EventInfoLog for those
            // with "SCH_FLAG_CUSTPAYEE_MODACCT" in ScheduleFlag in batch.
            // Send the batch to the fulfillment system
            resubmitCustPayeeFromEventInfoLog(dbh, fiId, instructionType, routeID,
                                              ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODACCT,
                                              logDate, fagent, custPayeeCache, cache );
            // Retrieve EventInfoLog for those
            // with "SCH_FLAG_CUSTPAYEE_MODPAYEE" in ScheduleFlag in batch.
            // Send the batch to the fulfillment system
            resubmitCustPayeeFromEventInfoLog(dbh, fiId, instructionType, routeID,
                                              ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODPAYEE,
                                              logDate, fagent, custPayeeCache, cache );
            // Retrieve EventInfoLog for those
            // with "SCH_FLAG_CUSTPAYEE_MODBOTH" in ScheduleFlag in batch.
            // Send the batch to the fulfillment system
            resubmitCustPayeeFromEventInfoLog(dbh, fiId, instructionType, routeID,
                                              ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODBOTH,
                                              logDate, fagent, custPayeeCache, cache );
            // Retrieve EventInfoLog for those
            // with "SCH_FLAG_CUSTPAYEE_CAN" in ScheduleFlag in batch.
            // Send the batch to the fulfillment system
            resubmitCustPayeeFromEventInfoLog(dbh, fiId, instructionType, routeID,
                                              ScheduleConstants.SCH_FLAG_CUSTPAYEE_CAN,
                                              logDate, fagent, custPayeeCache, cache );
            fagent.endCustomerPayeeBatch(fiId, instructionType, dbh );

            fagent.startPayeeBatch(fiId, instructionType, dbh );
            // Retrieve EventInfoLog for those
            // with "SCH_FLAG_PAYEE_NEW" in ScheduleFlag in batch.
            // Send the batch to the fulfillment system
            resubmitPayeeFromEventInfoLog(dbh, fiId, instructionType,
                                          ScheduleConstants.SCH_FLAG_PAYEE_NEW,
                                          logDate, fagent, cache) ;
            fagent.endPayeeBatch(fiId, instructionType, dbh );
        } catch (Exception exc) {
            FFSDebug.log("PayeeHandler.resubmitPayees failed. Error: " + FFSDebug.stackTrace(exc));
            throw new Exception( exc.toString());
        } finally {
        }

        FFSDebug.log("=== PayeeHandler.resubmitPayees: end, instructionType=" + instructionType,PRINT_DEV );

    }


	private void recoverCustPayeeFromCrash(FFSConnectionHolder dbh,
                                  String fiId,
                                  String instructionType,
                                           int routeID,
                                  int schFlag,
                                  FulfillAgent fagent,
                                  List custPayeeCache,
                                  List payeeCache )
    throws Exception
    {
        try {
            // Retrieve EventInfo for those with status of "SUBMITTED" and
            // in ScheduleFlag in batch.
            // Send the batch to the fulfillment system
            EventInfo [] evts = null;
            evts = Event.retrieveEventInfo(dbh, ScheduleConstants.EVT_STATUS_SUBMIT,
                                               fiId, instructionType,
                                               schFlag);
            if (evts != null) {
                InstructionType it = BPWRegistryUtil.getInstructionType(fiId, instructionType);
                int fileBasedRecovery = it.FileBasedRecovery;
                while (evts != null && evts.length > 0) {
                    boolean isToCallHandler = true;
                    int queueLength = evts.length;
                    int processedRecords = 0;
                    if (fileBasedRecovery == 0) { // not file-based recovery
                        EventInfoLog evLog = EventInfoLog.getByEventID(dbh,  evts[queueLength-1].EventID);
                        if (evLog != null) { // do not send the batch if EventInfoLog found
                            isToCallHandler = false;
                        }
                    }
                    if (isToCallHandler) {
                        while (processedRecords < queueLength) {
                            EventInfoLog.updateEventInfoLog(dbh,
                                                        evts[processedRecords].EventID,
                                                        evts[processedRecords].InstructionID,
                                                        evts[processedRecords].FIId,
                                                        evts[processedRecords].InstructionType,
                                                        evts[processedRecords].ScheduleFlag,
                                                        evts[processedRecords].LogID);
                            CustPayee cp = new CustPayee();
                            cp.findCustPayeeByPayeeListID( evts[processedRecords].InstructionID,
                                    Integer.parseInt(evts[processedRecords].LogID), dbh );
                            PayeeInfo payeeinfo = Payee.findPayeeByID( cp.getCustPayeeInfo().PayeeID, dbh );
                            CustomerPayeeInfo custpayeeinfo = cp.getCustPayeeInfo();
                            if (payeeinfo != null && custpayeeinfo != null) {
                                payeeCache.add( payeeinfo );
                                // Restore the original status. This step is
                                // important for "replay-ability". Bill
                                // Payments may be dependant upon the status
                                // of the Customer Payee. So, we want to set
                                // the status to what it was set to during the
                                // original schedule run.
                                String origStatus = INPROCESS;
                                if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_CUSTPAYEE_NEW) {
                                    custpayeeinfo.Status = NEW;
                                    origStatus = INPROCESS;
                                } else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODACCT) {
                                    custpayeeinfo.Status = MODACCT;
                                    origStatus = INPROCESS;
                                } else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODPAYEE) {
                                    custpayeeinfo.Status = MODPAYEE;
                                    origStatus = INPROCESS;
                                } else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODBOTH) {
                                    custpayeeinfo.Status = MODBOTH;
                                    origStatus = INPROCESS;
                                } else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_CUSTPAYEE_CAN) {
                                    custpayeeinfo.Status = CANC;
                                    origStatus = CANC_INPROCESS;
                                }
                                CustPayeeRoute.updateCustPayeeRouteStatus( custpayeeinfo.CustomerID,
                                                                           custpayeeinfo.PayeeListID,
                                                                           routeID, origStatus, dbh);

                                // Add this custPayee into our storage buffer.
                                custPayeeCache.add( custpayeeinfo );
                            }
                            processedRecords++;
                        }
                        PayeeInfo[] payees = (PayeeInfo[]) payeeCache.toArray( new PayeeInfo[0] );
                        CustomerPayeeInfo[] info = (CustomerPayeeInfo[])custPayeeCache.toArray( new CustomerPayeeInfo[0]);
                        if (schFlag == ScheduleConstants.SCH_FLAG_CUSTPAYEE_NEW) {
                            fagent.addCustomerPayees( info, payees, fiId, instructionType, dbh );
                        } else if (schFlag == ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODACCT) {
                            fagent.modCustomerPayees( info, payees, fiId, instructionType, dbh );
                        } else if (schFlag == ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODPAYEE) {
                            fagent.modCustomerPayees( info, payees, fiId, instructionType, dbh );
                        } else if (schFlag == ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODBOTH) {
                            fagent.modCustomerPayees( info, payees, fiId, instructionType, dbh );
                        } else if (schFlag == ScheduleConstants.SCH_FLAG_CUSTPAYEE_CAN) {
                            fagent.deleteCustomerPayees( info, payees, fiId, instructionType, dbh );
                        }
                        payeeCache.clear();
                        custPayeeCache.clear();
                        dbh.conn.commit();  // commit one batch at a time
                    }
                    if ( Event.isBatchDone(fiId, instructionType)) {
                        evts = new EventInfo [0];
                    } else {
                        evts = Event.retrieveEventInfo(dbh, ScheduleConstants.EVT_STATUS_SUBMIT,
                                                           fiId, instructionType,
                                                           schFlag);
                    }
                }
            }
        } finally {
        	Event.clearBatch(fiId, instructionType);
        }
    }


	private void resubmitCustPayeeFromEventInfoLog(FFSConnectionHolder dbh,
                                  String fiId,
                                  String instructionType,
                                                   int routeID,
                                  int schFlag,
                                  String logDate,
                                  FulfillAgent fagent,
                                  List custPayeeCache,
                                  List payeeCache )
    throws Exception
    {
        try {
            // Retrieve EventInfoLog in batch.
            // Send the batch to the fulfillment system
            EventInfo [] evts = null;
            evts = EventInfoLog.retrieveEventInfoLogs( dbh,
                                                       schFlag,
                                                       fiId, instructionType, logDate);
            if (evts != null) {
                while (evts != null && evts.length > 0) {
                    int queueLength = evts.length;
                    int processedRecords = 0;
                    while (processedRecords < queueLength) {
                        CustPayee cp = new CustPayee();
                        cp.findCustPayeeByPayeeListID( evts[processedRecords].InstructionID,
                                Integer.parseInt(evts[processedRecords].LogID), dbh );
                        PayeeInfo payeeinfo = Payee.findPayeeByID( cp.getCustPayeeInfo().PayeeID, dbh );
                        CustomerPayeeInfo custpayeeinfo = cp.getCustPayeeInfo();
                        if (payeeinfo != null && custpayeeinfo != null) {
                            payeeCache.add( payeeinfo );
                            // Restore the original status. This step is
                            // important for "replay-ability". Bill
                            // Payments may be dependant upon the status
                            // of the Customer Payee. So, we want to set
                            // the status to what it was set to during the
                            // original schedule run.
                            String origStatus = INPROCESS;
                            if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_CUSTPAYEE_NEW) {
                                custpayeeinfo.Status = NEW;
                                origStatus = INPROCESS;
                            } else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODACCT) {
                                custpayeeinfo.Status = MODACCT;
                                origStatus = INPROCESS;
                            } else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODPAYEE) {
                                custpayeeinfo.Status = MODPAYEE;
                                origStatus = INPROCESS;
                            } else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODBOTH) {
                                custpayeeinfo.Status = MODBOTH;
                                origStatus = INPROCESS;
                            } else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_CUSTPAYEE_CAN) {
                                custpayeeinfo.Status = CANC;
                                origStatus = CANC_INPROCESS;
                            }
                            CustPayeeRoute.updateCustPayeeRouteStatus( custpayeeinfo.CustomerID,
                                                                       custpayeeinfo.PayeeListID,
                                                                       routeID, origStatus, dbh);

                            // Add this custPayee into our storage buffer.
                            custPayeeCache.add( custpayeeinfo );
                        }
                        processedRecords++;
                    }
                    PayeeInfo[] payees = (PayeeInfo[]) payeeCache.toArray( new PayeeInfo[0] );
                    CustomerPayeeInfo[] info = (CustomerPayeeInfo[])custPayeeCache.toArray( new CustomerPayeeInfo[0]);
                    if (schFlag == ScheduleConstants.SCH_FLAG_CUSTPAYEE_NEW) {
                        fagent.addCustomerPayees( info, payees, fiId, instructionType, dbh );
                    } else if (schFlag == ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODACCT) {
                        fagent.modCustomerPayees( info, payees, fiId, instructionType, dbh );
                    } else if (schFlag == ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODPAYEE) {
                        fagent.modCustomerPayees( info, payees, fiId, instructionType, dbh );
                    } else if (schFlag == ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODBOTH) {
                        fagent.modCustomerPayees( info, payees, fiId, instructionType, dbh );
                    } else if (schFlag == ScheduleConstants.SCH_FLAG_CUSTPAYEE_CAN) {
                        fagent.deleteCustomerPayees( info, payees, fiId, instructionType, dbh );
                    }
                    payeeCache.clear();
                    custPayeeCache.clear();
                    dbh.conn.commit();  // commit one batch at a time
                    if ( EventInfoLog.isBatchDone(fiId, instructionType)) {
                        evts = new EventInfo [0];
                    } else {
                        evts = EventInfoLog.retrieveEventInfoLogs( dbh,
                                                                   schFlag,
                                                                   fiId, instructionType, logDate);
                    }
                }
            }
        } finally {
            EventInfoLog.clearBatch(fiId, instructionType);
        }
    }


	private void doCustPayees( FFSConnectionHolder dbh,
                               String fiId,
                               String instructionType,
                               String status,
                               int    routeID,
                               FulfillAgent fagent,
                               List custPayeeCache,
                               List payeeCache )
    throws Exception
    {
        try {
            int schFlag = ScheduleConstants.SCH_FLAG_CUSTPAYEE_NEW;
            String newStatus = INPROCESS;
            if (status.equals(NEW)) {
                newStatus = INPROCESS;
            } else if (status.equals(MOD)) {
                newStatus = INPROCESS;
            } else if (status.equals(CANC)) {
                newStatus = CANC_INPROCESS;
            }

            PropertyConfig _propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);


            String _runInParallel = _propertyConfig.otherProperties.getProperty(
            		DBConsts.SUPPORT_PARALLEL_BILLPAY_PROCESS,
            		DBConsts.DEFAULT_SUPPORT_PARALLEL_BILLPAY);




            // Retrieve a batch of Customer-Payee record with status.
            // Save in EventInfo with ScheduleFlag.
            // Copy the EventInfo into EventInfoLog.
            // Send the batch to the fulfillment system
            CustomerPayeeInfo[] custPayeeList;

            //custPayeeList = CustPayee.findCustPayeesWithPaymentByStatus( status, routeID, dbh );
            if (_runInParallel != null && _runInParallel.equalsIgnoreCase("Y")){

                custPayeeList  = CustPayee.findCustPayeesWithPaymentByStatus(status, fiId, routeID, dbh);
            }else {

                custPayeeList  = CustPayee.findCustPayeesWithPaymentByStatus( status, routeID, dbh );
            }


            int len1 = custPayeeList.length;
            while ( len1!= 0 ) {
                ArrayList alist = new ArrayList();
                dbh.conn.commit();  // commit one batch at a time
                for ( int i = 0; i < len1; i++ ) {
                    String payeeID = custPayeeList[i].PayeeID ;
                    String linkpayeeid = Payee.findLinkPayeeID( payeeID, dbh );
                    if ( linkpayeeid != null ) {
                        payeeID = linkpayeeid;
                    }
                    PayeeInfo payeeinfo = Payee.findPayeeByID( payeeID, dbh );
                    CustPayeeRoute.updateCustPayeeRouteStatus( custPayeeList[i].CustomerID,
                                                               custPayeeList[i].PayeeListID,
                                                               routeID, newStatus, dbh);
                    if (custPayeeList[i].Status.equals(NEW))
                        schFlag = ScheduleConstants.SCH_FLAG_CUSTPAYEE_NEW;
                    if (custPayeeList[i].Status.equals(MODACCT))
                        schFlag = ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODACCT;
                    else if (custPayeeList[i].Status.equals(MODPAYEE))
                        schFlag = ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODPAYEE;
                    else if (custPayeeList[i].Status.equals(MODBOTH))
                        schFlag = ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODBOTH;
                    else if (custPayeeList[i].Status.equals(CANC))
                        schFlag = ScheduleConstants.SCH_FLAG_CUSTPAYEE_CAN;
                    String eventID = Event.createEvent( dbh,
                                                            custPayeeList[i].CustomerID,
                                                            fiId, instructionType,
                                                            ScheduleConstants.EVT_STATUS_SUBMIT,
                                                            schFlag,
                                                            Integer.toString(custPayeeList[i].PayeeListID) );
                    alist.add(eventID);
                    payeeCache.add( payeeinfo );
                    custPayeeCache.add( custPayeeList[i] );
                }
                dbh.conn.commit();  // commit one batch at a time
                PayeeInfo[] payees = (PayeeInfo[]) payeeCache.toArray( new PayeeInfo[0] );
                CustomerPayeeInfo[] info = (CustomerPayeeInfo[])custPayeeCache.toArray( new CustomerPayeeInfo[0]);
                if (status.equals(NEW)) {
                    fagent.addCustomerPayees( info, payees, fiId, instructionType, dbh );
                } else if (status.equals(MOD)) {
                    fagent.modCustomerPayees( info, payees, fiId, instructionType, dbh );
                } else if (status.equals(CANC)) {
                    fagent.deleteCustomerPayees( info, payees, fiId, instructionType, dbh );
                }
                payeeCache.clear();
                custPayeeCache.clear();
                for ( int i = 0; i < len1; i++ ) {
                    if (custPayeeList[i].Status.equals(NEW))
                        schFlag = ScheduleConstants.SCH_FLAG_CUSTPAYEE_NEW;
                    if (custPayeeList[i].Status.equals(MODACCT))
                        schFlag = ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODACCT;
                    else if (custPayeeList[i].Status.equals(MODPAYEE))
                        schFlag = ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODPAYEE;
                    else if (custPayeeList[i].Status.equals(MODBOTH))
                        schFlag = ScheduleConstants.SCH_FLAG_CUSTPAYEE_MODBOTH;
                    else if (custPayeeList[i].Status.equals(CANC))
                        schFlag = ScheduleConstants.SCH_FLAG_CUSTPAYEE_CAN;
                    EventInfoLog.createEventInfoLog(dbh,
                                                   (String)alist.get(i),
                                                    custPayeeList[i].CustomerID,
                                                    fiId, instructionType,
                                                    schFlag,
                                                    Integer.toString(custPayeeList[i].PayeeListID) );
                }
                dbh.conn.commit();  // commit one batch at a time
                if ( CustPayee.isBatchDone( status, routeID ) ) {
                    len1 = 0;
                } else {

                    //custPayeeList = CustPayee.findCustPayeesWithPaymentByStatus( status, routeID, dbh );
                    if (_runInParallel != null && _runInParallel.equalsIgnoreCase("Y")){

                        custPayeeList  = CustPayee.findCustPayeesWithPaymentByStatus(status, fiId, routeID, dbh);
                    }else {

                        custPayeeList  = CustPayee.findCustPayeesWithPaymentByStatus( status, routeID, dbh );
                    }

                    len1 = custPayeeList.length;
                }
            }
        } finally {
            CustPayee.clearBatch( status, routeID );
        }
    }


	private void doPendingCustPayees( FFSConnectionHolder dbh,
                               String fiId,
                               String instructionType,
                               int    routeID,
                               FulfillAgent fagent,
                               List custPayeeCache,
                               List payeeCache )
    throws Exception
    {
        try {

            PropertyConfig _propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);
            String _runInParallel = _propertyConfig.otherProperties.getProperty(
            		DBConsts.SUPPORT_PARALLEL_BILLPAY_PROCESS,
            		DBConsts.DEFAULT_SUPPORT_PARALLEL_BILLPAY);

            // Retrieve a batch of Customer-Payee record with status=PENDING.
            // Save in EventInfo with ScheduleFlag.
            // Copy the EventInfo into EventInfoLog.
            // Send the batch to the fulfillment system
            CustomerPayeeInfo[] custPayeePendList;

            //custPayeePendList = CustPayee.findCustPayeesWithPaymentByStatus( PENDING, routeID, dbh );
            if (_runInParallel != null && _runInParallel.equalsIgnoreCase("Y")){

                custPayeePendList  = CustPayee.findCustPayeesWithPaymentByStatus(PENDING, fiId, routeID, dbh);
            }else {

                custPayeePendList  = CustPayee.findCustPayeesWithPaymentByStatus(PENDING, routeID, dbh );
            }

            int len4 = custPayeePendList.length;
            while ( len4 != 0 ) {
                boolean toDeletePendingPmt = false;
                for ( int i = 0; i < len4; i++ ) {

                    PayeeInfo payeeinfo = Payee.findPayeeByID( custPayeePendList[i].PayeeID, dbh );
                    if ( !PmtInstruction.hasPendingPmt( custPayeePendList[i].CustomerID,
                                                        custPayeePendList[i].PayeeListID, dbh  ) ) {
                        CustPayeeRoute.updateCustPayeeRouteStatus( custPayeePendList[i].CustomerID,
                                                                   custPayeePendList[i].PayeeListID,
                                                                   routeID, CANC_INPROCESS, dbh);
                        String eventID = Event.createEvent( dbh,
                                                                custPayeePendList[i].CustomerID,
                                                                fiId, instructionType,
                                                                ScheduleConstants.EVT_STATUS_SUBMIT,
                                                                ScheduleConstants.SCH_FLAG_CUSTPAYEE_CAN,
                                                                Integer.toString(custPayeePendList[i].PayeeListID) );
                        EventInfoLog.createEventInfoLog(dbh,
                                                        eventID,
                                                        custPayeePendList[i].CustomerID,
                                                        fiId, instructionType,
                                                        ScheduleConstants.SCH_FLAG_CUSTPAYEE_CAN,
                                                        Integer.toString(custPayeePendList[i].PayeeListID) );
                        payeeCache.add( payeeinfo );
                        custPayeeCache.add( custPayeePendList[i] );
                        toDeletePendingPmt = true;
                    }
                }
                if (toDeletePendingPmt) {
                    PayeeInfo[] payeescanc = (PayeeInfo[]) payeeCache.toArray( new PayeeInfo[0] );
                    CustomerPayeeInfo[] cancinfo = (CustomerPayeeInfo[])custPayeeCache.toArray( new CustomerPayeeInfo[0]);
                    fagent.deleteCustomerPayees( cancinfo, payeescanc, fiId, instructionType, dbh );
                }
                payeeCache.clear();
                custPayeeCache.clear();
                dbh.conn.commit();  // commit one batch at a time
                if ( CustPayee.isBatchDone( PENDING, routeID ) ) {
                    len4 = 0;
                } else {

                    //custPayeePendList = CustPayee.findCustPayeesWithPaymentByStatus( PENDING, routeID, dbh );
                    if (_runInParallel != null && _runInParallel.equalsIgnoreCase("Y")){

                        custPayeePendList  = CustPayee.findCustPayeesWithPaymentByStatus(PENDING, fiId, routeID, dbh);
                    }else {

                        custPayeePendList  = CustPayee.findCustPayeesWithPaymentByStatus( PENDING, routeID, dbh );
                    }

                    len4 = custPayeePendList.length;
                }
            }
        } finally {
            CustPayee.clearBatch( PENDING, routeID );
        }
    }


	private void recoverPayeeFromCrash(FFSConnectionHolder dbh,
                                  String fiId,
                                  String instructionType,
                                  int schFlag,
                                  FulfillAgent fagent,
                                  List payeeCache )
    throws Exception
    {
        try {
            // Retrieve EventInfo for those with status of "SUBMITTED" and
            // with "SCH_FLAG_PAYEE_NEW" in ScheduleFlag in batch.
            // Send the batch to the fulfillment system
            EventInfo [] evts = null;
            evts = Event.retrieveEventInfo(dbh, ScheduleConstants.EVT_STATUS_SUBMIT,
                                               fiId, instructionType,
                                               schFlag);
            if (evts != null) {
                while (evts != null && evts.length > 0) {
                    int queueLength = evts.length;
                    int processedRecords = 0;
                    while (processedRecords < queueLength) {
                        EventInfoLog.updateEventInfoLog(dbh,
                                                        evts[processedRecords].EventID,
                                                        evts[processedRecords].InstructionID,
                                                        evts[processedRecords].FIId,
                                                        evts[processedRecords].InstructionType,
                                                        evts[processedRecords].ScheduleFlag,
                                                        evts[processedRecords].LogID);

                        PayeeInfo payeeinfo = Payee.findPayeeByID( evts[processedRecords].InstructionID, dbh );
                        if (payeeinfo != null) {
                            payeeinfo.Status = NEW;

                            // Restore the original status. This step is
                            // important for "replay-ability". Bill
                            // Payments may be dependant upon the status
                            // of the Payee. So, we want to set the status
                            // to what it was set to during the original
                            // schedule run.
                            String origStatus = INPROCESS;
                            Payee.updateStatus( payeeinfo.PayeeID, origStatus, dbh );

                            // Add the payee object into our storage buffer.
                            payeeCache.add( payeeinfo );
                        }
                        processedRecords++;
                    }
                    PayeeInfo[] payees = (PayeeInfo[]) payeeCache.toArray( new PayeeInfo[0] );
                    fagent.addPayees( payees, fiId, instructionType, dbh );
                    payeeCache.clear();
                    dbh.conn.commit();  // commit one batch at a time
                    if ( Event.isBatchDone(fiId, instructionType)) {
                        evts = new EventInfo [0];
                    } else {
                        evts = Event.retrieveEventInfo(dbh, ScheduleConstants.EVT_STATUS_SUBMIT,
                                                           fiId, instructionType,
                                                           schFlag);
                    }
                }
            }

        } finally {
        	Event.clearBatch(fiId, instructionType);
        }
    }


	private void resubmitPayeeFromEventInfoLog(FFSConnectionHolder dbh,
                                  String fiId,
                                  String instructionType,
                                  int schFlag,
                                  String logDate,
                                  FulfillAgent fagent,
                                  List payeeCache )
    throws Exception
    {
        try {
            // Retrieve EventInfoLog in batch.
            // Send the batch to the fulfillment system
            EventInfo [] evts = null;
            evts = EventInfoLog.retrieveEventInfoLogs( dbh,
                                                       schFlag,
                                                       fiId, instructionType, logDate);
            if (evts != null) {
                while (evts != null && evts.length > 0) {
                    int queueLength = evts.length;
                    int processedRecords = 0;
                    while (processedRecords < queueLength) {
                        PayeeInfo payeeinfo = Payee.findPayeeByID( evts[processedRecords].InstructionID, dbh );
                        if (payeeinfo != null) {
                            payeeinfo.Status = NEW;

                            // Restore the original status. This step is
                            // important for "replay-ability". Bill
                            // Payments may be dependant upon the status
                            // of the Payee. So, we want to set the status
                            // to what it was set to during the original
                            // schedule run.
                            String origStatus = INPROCESS;
                            Payee.updateStatus( payeeinfo.PayeeID, origStatus, dbh );

                            // Add the payee object into our storage buffer.
                            payeeCache.add( payeeinfo );
                        }
                        processedRecords++;
                    }
                    PayeeInfo[] payees = (PayeeInfo[]) payeeCache.toArray( new PayeeInfo[0] );
                    fagent.addPayees( payees, fiId, instructionType, dbh );
                    payeeCache.clear();
                    dbh.conn.commit();  // commit one batch at a time
                    if ( EventInfoLog.isBatchDone(fiId, instructionType)) {
                        evts = new EventInfo [0];
                    } else {
                        evts = EventInfoLog.retrieveEventInfoLogs( dbh,
                                                                   schFlag,
                                                                   fiId, instructionType, logDate);
                    }
                }
            }
        } finally {
            EventInfoLog.clearBatch(fiId, instructionType);
        }
    }


	private void doPayees( FFSConnectionHolder dbh,
                               String fiId,
                               String instructionType,
                               String status,
                               FulfillAgent fagent,
                               List payeeCache )
    throws Exception
    {
        try {
            int schFlag = ScheduleConstants.SCH_FLAG_PAYEE_NEW;
            String newStatus = INPROCESS;
            if (status.equals(NEW)) {
                newStatus = INPROCESS;
                schFlag = ScheduleConstants.SCH_FLAG_PAYEE_NEW;
            }
            PayeeInfo[] payeeList = Payee.findPayeesByStatus( status, dbh );
            int len5 = payeeList.length;
            while ( len5!= 0 ) {
                List alist = new ArrayList();
                for ( int i = 0; i < len5; i++ ) {
                    Payee.updateStatus( payeeList[i].PayeeID, newStatus, dbh );
                    String eventID = Event.createEvent( dbh,
                                                            payeeList[i].PayeeID,
                                                            fiId, instructionType,
                                                            ScheduleConstants.EVT_STATUS_SUBMIT,
                                                            schFlag,
                                                            null );
                    alist.add(eventID);
                    payeeCache.add( payeeList[i] );
                }
                dbh.conn.commit();  // commit one batch at a time
                PayeeInfo[] payees = (PayeeInfo[]) payeeCache.toArray( new PayeeInfo[0] );
                fagent.addPayees( payees, fiId, instructionType, dbh );
                payeeCache.clear();
                for ( int i = 0; i < len5; i++ ) {
                    EventInfoLog.createEventInfoLog(dbh,
                                                   (String)alist.get(i),
                                                    payeeList[i].PayeeID,
                                                    fiId, instructionType,
                                                    schFlag,
                                                    null );
                }
                dbh.conn.commit();  // commit one batch at a time
                if ( Payee.isBatchDone( status ) ) {
                    len5 = 0;
                } else {
                    payeeList = Payee.findPayeesByStatus( status, dbh );
                    len5 = payeeList.length;
                }
            }
        } finally {
            Payee.clearBatch( status );
        }
    }

}
