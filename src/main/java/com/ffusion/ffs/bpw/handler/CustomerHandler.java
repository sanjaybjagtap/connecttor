//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2001.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.util.ArrayList;
import java.util.List;

import com.ffusion.ffs.bpw.db.CustRoute;
import com.ffusion.ffs.bpw.db.Customer;
import com.ffusion.ffs.bpw.fulfill.FulfillAgent;
import com.ffusion.ffs.bpw.interfaces.BPWException;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.CustomerInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfo;
import com.ffusion.ffs.bpw.interfaces.InstructionType;
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



public class CustomerHandler implements DBConsts,FFSConst, BPWResource {
    /**
	 *
	 */
	private static final long serialVersionUID = 1372785144547062602L;


	public CustomerHandler(){
    }


    // handleCustomers()
    //
    // This method passes the Customer information to the fulfillment system.
    // The are two parts of the process.
    // First it checks for performing crash recovery from the EventInfo record.
    // Second, in normal processing, it saves the data in the EventInfo record in case
    // it needs to do crash recovery.
    // The record from the EventInfo is copied over to the EventInfoLog for the purpose
    // of event resubmitting.
    // The EventInfo and EventInfoLog table are defined as follow:
    //      EventID - save the EventID of the EventInfo
    //      InstructionID - save the CustomerID of the Customer record
    //      InstructionType - save the InstructionType of the schedule event
    //      ScheduleFlag - save the following data for the Customer
    //                      ScheduleConstants.SCH_FLAG_CUST_NEW
    //                      ScheduleConstants.SCH_FLAG_CUST_MOD
    //                      ScheduleConstants.SCH_FLAG_CUST_CAN
    //      LogID - null
    //

	public void handleCustomers( String fiId, String instructionType, FFSConnectionHolder dbh )
    throws Exception
    {
		String methodName = "CustomerHandler.handleCustomers";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log("=== CustomerHandler.handleCustomers: begin, instructionType=" + instructionType, PRINT_DEV );

        FulfillAgent fagent = (FulfillAgent)FFSRegistry.lookup(FULFILLAGENT);
        if ( fagent == null) {
            throw new BPWException("FulfillAgent is null!!");
        }

        // Find the key for this instruction type
		String cacheKey = FulfillAgent.findCacheKey(fiId, instructionType);
		int routeID = fagent.getRouteId( fiId, instructionType );

        try {

            PropertyConfig _propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);
            int size =  _propertyConfig.getBatchSize();
            String _runInParallel = _propertyConfig.otherProperties.getProperty(
            		DBConsts.SUPPORT_PARALLEL_BILLPAY_PROCESS,
            		DBConsts.DEFAULT_SUPPORT_PARALLEL_BILLPAY);

            Customer.setBatchSize(size);

            fagent.startCustomerBatch(fiId, instructionType, dbh );

            // Retrieve EventInfo for those with status of "SUBMITTED" and
            // with "SCH_FLAG_CUST_NEW" in ScheduleFlag in batch.
            // Send the batch to the fulfillment system
            List customerCache = FulfillAgent.getCustomerCache( cacheKey );
            recoverFromCrash(dbh, fiId, instructionType, routeID,
                             ScheduleConstants.SCH_FLAG_CUST_NEW,
                             fagent, customerCache);

            // Retrieve a batch of Customer record with NEW status.
            // Save in EventInfo with ScheduleFlag=SCH_FLAG_CUST_NEW.
            // Copy the EventInfo into EventInfoLog.
            // Send the batch to the fulfillment system
            doCustomers(dbh, fiId, instructionType, NEW, routeID, fagent, customerCache);

            // Retrieve EventInfo for those with status of "SUBMITTED" and
            // with "SCH_FLAG_CUST_MOD" in ScheduleFlag in batch.
            // Send the batch to the fulfillment system
            List customerModCache = FulfillAgent.getCustomerModCache( cacheKey );
            recoverFromCrash(dbh, fiId, instructionType, routeID,
                             ScheduleConstants.SCH_FLAG_CUST_MOD,
                             fagent, customerModCache);

            // Retrieve a batch of Customer record with MOD status.
            // Save in EventInfo with ScheduleFlag=SCH_FLAG_CUST_MOD.
            // Copy the EventInfo into EventInfoLog.
            // Send the batch to the fulfillment system
            doCustomers(dbh, fiId, instructionType, MOD, routeID, fagent, customerModCache);

            // Retrieve EventInfo for those with status of "SUBMITTED" and
            // with "SCH_FLAG_CUST_CAN" in ScheduleFlag in batch.
            // Send the batch to the fulfillment system
            List customerCanCache = FulfillAgent.getCustomerCancCache( cacheKey );
            recoverFromCrash(dbh, fiId, instructionType, routeID,
                             ScheduleConstants.SCH_FLAG_CUST_CAN,
                             fagent, customerCanCache);

            // Retrieve a batch of Customer record with CANC status.
            // Save in EventInfo with ScheduleFlag=SCH_FLAG_CUST_CAN.
            // Copy the EventInfo into EventInfoLog.
            // Send the batch to the fulfillment system
            doCustomers(dbh, fiId, instructionType, CANC, routeID, fagent, customerCanCache);

            CustomerInfo[] customerDisList = new CustomerInfo[0];
            customerDisList = Customer.findCustomersWithPaymentByStatus(DISABLE, routeID, dbh);
            FFSDebug.log("CustomerHandler: got de-activated customers: " + customerDisList.length, PRINT_DEV);
            int disLen = customerDisList.length;
            while ( disLen != 0 ) {
                for ( int i = 0; i < disLen; i++ ) {
                    CustRoute.updateCustRouteStatus( customerDisList[i].customerID,
                                                     routeID,  INACTIVE,  dbh);
                    //customerCanCache.add( customerDisList[i] );
                }


                dbh.conn.commit();  // commit one batch at a time
                if ( Customer.isBatchDone( DISABLE, routeID ) ) {
                    disLen = 0;
                } else {
                    //customerDisList = Customer.findCustomersWithPaymentByStatus(DISABLE, routeID, dbh);

                    if (_runInParallel != null && _runInParallel.equalsIgnoreCase("Y")){

                        customerDisList = Customer.findCustomersWithPaymentByStatus(DISABLE, fiId, routeID, dbh);
                    }else {

                        customerDisList = Customer.findCustomersWithPaymentByStatus(DISABLE, routeID, dbh);
                    }

                    disLen = customerDisList.length;
                }
            }

            CustomerInfo[] customerEnaList = new CustomerInfo[0];

            //customerEnaList = Customer.findCustomersWithPaymentByStatus(ENABLE, routeID, dbh);
            if (_runInParallel != null && _runInParallel.equalsIgnoreCase("Y")){

                customerEnaList = Customer.findCustomersWithPaymentByStatus(ENABLE, fiId, routeID, dbh);
            }else {

                customerEnaList = Customer.findCustomersWithPaymentByStatus(ENABLE, routeID, dbh);
            }

            FFSDebug.log("CustomerHandler: got activated customers: " + customerEnaList.length, PRINT_DEV);
            int enaLen = customerEnaList.length;
            while ( enaLen != 0 ) {
                for ( int i = 0; i < enaLen; i++ ) {
                    CustRoute.updateCustRouteStatus( customerEnaList[i].customerID,
                                                    routeID,  ACTIVE,  dbh);
                }
                dbh.conn.commit();  // commit one batch at a time
                if ( Customer.isBatchDone( ENABLE, routeID ) ) {
                    enaLen = 0;
                } else {

                    //customerEnaList = Customer.findCustomersWithPaymentByStatus(ENABLE, routeID, dbh);
                    if (_runInParallel != null && _runInParallel.equalsIgnoreCase("Y")){

                        customerEnaList = Customer.findCustomersWithPaymentByStatus(ENABLE, fiId, routeID, dbh);
                    }else {

                        customerEnaList = Customer.findCustomersWithPaymentByStatus(ENABLE, routeID, dbh);
                    }

                    enaLen = customerEnaList.length;
                }
            }

            fagent.endCustomerBatch(fiId, instructionType, dbh );
        } catch (Exception exc) {
            FFSDebug.log("CustomerHandler.handleCustomers failed. Error: " + FFSDebug.stackTrace(exc));
            PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
            throw new Exception( exc.toString());
        } finally {
            Customer.clearBatch( DISABLE, routeID );
            Customer.clearBatch( ENABLE, routeID );
        }

        FFSDebug.log("=== CustomerHandler.handleCustomers: end, instructionType=" + instructionType,PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }



	public void resubmitCustomers( String fiId, String instructionType, String logDate, FFSConnectionHolder dbh )
    throws Exception
    {
		String methodName = "CustomerHandler.resubmitCustomers";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log("=== CustomerHandler.resubmitCustomers: begin, instructionType=" + instructionType, PRINT_DEV );

        try {
            FulfillAgent fagent = (FulfillAgent)FFSRegistry.lookup(FULFILLAGENT);
            if ( fagent == null) {
                throw new BPWException("FulfillAgent is null!!");
            }

            PropertyConfig _propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);
            int size =  _propertyConfig.getBatchSize();
            Customer.setBatchSize(size);

            // Find the key for this instruction type
    		String cacheKey = FulfillAgent.findCacheKey(fiId, instructionType);
    		int routeID = fagent.getRouteId( fiId, instructionType );

            fagent.startCustomerBatch(fiId, instructionType, dbh );

            // Retrieve EventInfoLog for those
            // with "SCH_FLAG_CUST_NEW" in ScheduleFlag in batch.
            // Send the batch to the fulfillment system
            List customerCache = FulfillAgent.getCustomerCache( cacheKey );
            resubmitEventInfoLog(dbh, fiId, instructionType, routeID,
                                 ScheduleConstants.SCH_FLAG_CUST_NEW,
                                 logDate, fagent, customerCache);

            // Retrieve EventInfoLog for those
            // with "SCH_FLAG_CUST_MOD" in ScheduleFlag in batch.
            // Send the batch to the fulfillment system
            List customerModCache = FulfillAgent.getCustomerModCache( cacheKey );
            resubmitEventInfoLog(dbh, fiId, instructionType, routeID,
                                 ScheduleConstants.SCH_FLAG_CUST_MOD,
                                 logDate, fagent, customerModCache);

            // Retrieve EventInfoLog for those
            // with "SCH_FLAG_CUST_CAN" in ScheduleFlag in batch.
            // Send the batch to the fulfillment system
            List customerCanCache = FulfillAgent.getCustomerCancCache( cacheKey );
            resubmitEventInfoLog(dbh, fiId, instructionType, routeID,
                                 ScheduleConstants.SCH_FLAG_CUST_CAN,
                                 logDate, fagent, customerCanCache);

            fagent.endCustomerBatch(fiId, instructionType, dbh );
        } catch (Exception exc) {
            FFSDebug.log("CustomerHandler.resubmitCustomers failed. Error: " + FFSDebug.stackTrace(exc));
            PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
            throw new Exception( exc.toString());
        } finally {
        }

        FFSDebug.log("=== CustomerHandler.resubmitCustomers: end, instructionType=" + instructionType,PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }


	private void recoverFromCrash(FFSConnectionHolder dbh,
                                  String fiId,
                                  String instructionType,
                                  int routeID,
                                  int schFlag,
                                  FulfillAgent fagent,
                                  List customerCache )
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
                            CustomerInfo custinfo = Customer.getCustomerByID(evts[processedRecords].InstructionID, dbh);
                            if (custinfo != null) {
                                // Restore the original status. This step is
                                // important for "replay-ability". Customer
                                // Payees and Bill Payments may be dependant
                                // upon the status of the Customer. So, we want
                                // to set the status to what it was set to
                                // during to the original schedule run.
                                String origStatus = null;
                                if (schFlag == ScheduleConstants.SCH_FLAG_CUST_NEW) {
                                    custinfo.status = NEW;
                                    origStatus = INPROCESS;
                                } else if (schFlag == ScheduleConstants.SCH_FLAG_CUST_MOD) {
                                    custinfo.status = MOD;
                                    origStatus = INPROCESS;
                                } else if (schFlag == ScheduleConstants.SCH_FLAG_CUST_CAN) {
                                    custinfo.status = CANC;
                                    origStatus = CANC_INPROCESS;
                                }
                                CustRoute.updateCustRouteStatus( custinfo.customerID,
                                                                 routeID, origStatus, dbh);

                                // Add this customer into our buffer.
                                customerCache.add(custinfo);
                            }
                            processedRecords++;
                        }
                        CustomerInfo[] info = (CustomerInfo[])customerCache.toArray(new CustomerInfo[0]);
                        if (schFlag == ScheduleConstants.SCH_FLAG_CUST_NEW) {
                            fagent.addCustomers( info, fiId, instructionType, dbh );
                        } else if (schFlag == ScheduleConstants.SCH_FLAG_CUST_MOD) {
                            fagent.modCustomers( info, fiId, instructionType, dbh );
                        } else if (schFlag == ScheduleConstants.SCH_FLAG_CUST_CAN) {
                            fagent.deleteCustomers( info, fiId, instructionType, dbh );
                        }
                        customerCache.clear();
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

    /**
     * Re-process the customers. The list of customers to
     * reprocess is retrieved from the SCH_EventInfoLog table.
     *
     * @param dbh     Database connection to use for any database operations
     *                that need to be transactional.
     * @param fiId    Financial Institution ID that the targeted customers
     *                belong to.
     * @param instructionType
     *                Instruction Type to retrieve from the SCH_EventInfoLog
     *                table.
     * @param routeID The route ID value to target when updating statuses in
     *                the BPW_CustomerRoute table.
     * @param schFlag Schedule Flag value to retrieve from the SCH_EventInfoLog
     *                table.
     * @param logDate date to retrieve from the SCH_EventInfoLog table.
     * @param fagent  Fulfillment Agent instance to use in processing.
     * @param customerCache Storage Buffer use for collecting the customer objects.
     * @exception Exception
     */

	private void resubmitEventInfoLog(FFSConnectionHolder dbh,
                                  String fiId,
                                  String instructionType,
                                      int routeID,
                                  int schFlag,
                                  String logDate,
                                  FulfillAgent fagent,
                                  List customerCache )
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
                        CustomerInfo custinfo = Customer.getCustomerByID(evts[processedRecords].InstructionID, dbh);
                        if (custinfo != null) {
                            // Restore the original status. This step is
                            // important for "replay-ability". Customer
                            // Payees and Bill Payments may be dependent
                            // upon the status of the Customer. So, we want
                            // to set the status to what it was set to
                            // during the original schedule run.
                            String origStatus = null;
                            if (schFlag == ScheduleConstants.SCH_FLAG_CUST_NEW) {
                                custinfo.status = NEW;
                                origStatus = INPROCESS;
                            } else if (schFlag == ScheduleConstants.SCH_FLAG_CUST_MOD) {
                                custinfo.status = MOD;
                                origStatus = INPROCESS;
                            } else if (schFlag == ScheduleConstants.SCH_FLAG_CUST_CAN) {
                                custinfo.status = CANC;
                                origStatus = CANC_INPROCESS;
                            }
                            CustRoute.updateCustRouteStatus( custinfo.customerID,
                                                             routeID, origStatus, dbh);

                            // Add this customer into our buffer.
                            customerCache.add(custinfo);
                        }
                        processedRecords++;
                    }
                    CustomerInfo[] info = (CustomerInfo[])customerCache.toArray(new CustomerInfo[0]);
                    if (schFlag == ScheduleConstants.SCH_FLAG_CUST_NEW) {
                        fagent.addCustomers( info, fiId, instructionType, dbh );
                    } else if (schFlag == ScheduleConstants.SCH_FLAG_CUST_MOD) {
                        fagent.modCustomers( info, fiId, instructionType, dbh );
                    } else if (schFlag == ScheduleConstants.SCH_FLAG_CUST_CAN) {
                        fagent.deleteCustomers( info, fiId, instructionType, dbh );
                    }
                    customerCache.clear();
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


	private void doCustomers( FFSConnectionHolder dbh,
                               String fiId,
                               String instructionType,
                               String status,
                               int    routeID,
                               FulfillAgent fagent,
                               List customerCache )
    throws Exception
    {
        try {
            int schFlag = ScheduleConstants.SCH_FLAG_CUST_NEW;
            String newStatus = null;
            if (status.equals(NEW)) {
                schFlag = ScheduleConstants.SCH_FLAG_CUST_NEW;
                newStatus = INPROCESS;
            } else if (status.equals(MOD)) {
                schFlag = ScheduleConstants.SCH_FLAG_CUST_MOD;
                newStatus = INPROCESS;
            } else if (status.equals(CANC)) {
                schFlag = ScheduleConstants.SCH_FLAG_CUST_CAN;
                newStatus = CANC_INPROCESS;
            }

            PropertyConfig _propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);
            String _runInParallel = _propertyConfig.otherProperties.getProperty(
            		DBConsts.SUPPORT_PARALLEL_BILLPAY_PROCESS,
            		DBConsts.DEFAULT_SUPPORT_PARALLEL_BILLPAY);


            // Retrieve a batch of Customer record with status.
            // Save in EventInfo with ScheduleFlag=schFlag.
            // Copy the EventInfo into EventInfoLog.
            // Send the batch to the fulfillment system
            CustomerInfo[] customerList = new CustomerInfo[0];

	    //customerList = Customer.findCustomersWithPaymentByStatus(status, routeID, dbh);
            if (_runInParallel != null && _runInParallel.equalsIgnoreCase("Y")){

                customerList = Customer.findCustomersWithPaymentByStatus(status, fiId, routeID, dbh);
            }else {

                customerList = Customer.findCustomersWithPaymentByStatus(status, routeID, dbh);
            }

            FFSDebug.log("CustomerHandler: got " + status + " customers: " + customerList.length, PRINT_DEV);
            int addLen = customerList.length;
            while ( addLen!= 0 ) {
                ArrayList alist = new ArrayList();
                for ( int i = 0; i < addLen; i++ ) {
                    CustRoute.updateCustRouteStatus( customerList[i].customerID,
                                                     routeID, newStatus, dbh);
                    String eventID = Event.createEvent( dbh,
                                           customerList[i].customerID,
                                           fiId,
                                           instructionType,
                                           ScheduleConstants.EVT_STATUS_SUBMIT,
                                           schFlag,
                                           null );
                    alist.add(eventID);
                    customerCache.add( customerList[i] );
                }
                dbh.conn.commit();  // commit one batch at a time
                CustomerInfo[] info = (CustomerInfo[])customerCache.toArray(new CustomerInfo[0]);
                if (schFlag == ScheduleConstants.SCH_FLAG_CUST_NEW) {
                    fagent.addCustomers( info, fiId, instructionType, dbh );
                } else if (schFlag == ScheduleConstants.SCH_FLAG_CUST_MOD) {
                    fagent.modCustomers( info, fiId, instructionType, dbh );
                } else if (schFlag == ScheduleConstants.SCH_FLAG_CUST_CAN) {
                    fagent.deleteCustomers( info, fiId, instructionType, dbh );
                }
                customerCache.clear();
                for ( int i = 0; i < addLen; i++ ) {
                    EventInfoLog.createEventInfoLog(dbh,
                                                (String)alist.get(i),
                                                customerList[i].customerID,
                                                fiId, instructionType,
                                                schFlag,
                                                null );
                }
                dbh.conn.commit();  // commit one batch at a time
                if ( Customer.isBatchDone( status, routeID ) ) {
                    addLen = 0;
                } else {

                    //customerList = Customer.findCustomersWithPaymentByStatus(status, routeID, dbh);
                    if (_runInParallel != null && _runInParallel.equalsIgnoreCase("Y")){

                        customerList = Customer.findCustomersWithPaymentByStatus(status, fiId, routeID, dbh);
                    }else {

                        customerList = Customer.findCustomersWithPaymentByStatus(status, routeID, dbh);
                    }

                    addLen = customerList.length;
                }
            }
        } finally {
            Customer.clearBatch( status, routeID );
        }
    }

}
