//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2001.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import com.ffusion.ffs.bpw.db.TempHist;
import com.ffusion.ffs.bpw.db.TempHistUtil;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.master.PagedACH;
import com.ffusion.ffs.bpw.master.PagedBillPay;
import com.ffusion.ffs.bpw.master.PagedCashCon;
import com.ffusion.ffs.bpw.master.PagedTmpTable;
import com.ffusion.ffs.bpw.master.PagedTransfer;
import com.ffusion.ffs.bpw.master.PagedWire;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.sap.banking.common.interceptors.PerfLoggerUtil;


//=====================================================================
//
// This class contains represents the schedule that cleans up the 
// TempHist table
//
//=====================================================================

public class TempHistCleanupHandler implements DBConsts, FFSConst, 
    BPWResource, BPWScheduleHandler {
  
    private PropertyConfig propertyConfig = null;
    public TempHistCleanupHandler(){}

    //=====================================================================
    // Description: This method is called by the Scheduling engine
    //=====================================================================
    public void eventHandler( 
                            int eventSequence,
                            EventInfoArray evts,
                            FFSConnectionHolder dbh )throws Exception {
    	String methodName = "TempHistCleanupHandler.eventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);


       propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);
        
       String cleanupInterval = propertyConfig.otherProperties.getProperty(
                                 DBConsts.TEMPHIST_CLEANUP_INTERVAL,
							     DBConsts.TEMPHIST_CLEANUP_INTERVAL_VALUE);
       int interval           = 60; // one hour
       if (cleanupInterval == null || cleanupInterval.trim().length() == 0) {
            String err = "No cleanup interval specified default "
            + "value (one hour) will be used";
            FFSDebug.log(err);
            interval = 60; // one hour
       } else {
           try{
              interval = Integer.parseInt(cleanupInterval);
           }catch(Throwable t){
               interval = 60;
               PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
           }
       }


       FFSDebug.log("TempHistCleanupHandler.eventHandler: begin", PRINT_DEV);
       summaryCleanup(dbh, interval);
       FFSDebug.log("TempHistCleanupHandler.eventHandler: end", PRINT_DEV);
       PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
   }

//=====================================================================
// resubmitEventHandler()
// Description: This method is called by the Scheduling engine during
//    event resubmission. 
//=====================================================================
   public void resubmitEventHandler( 
                            int eventSequence,
                            EventInfoArray evts,
                            FFSConnectionHolder dbh) throws Exception  {
	   String methodName = "TempHistCleanupHandler.resubmitEventHandler";
       long start = System.currentTimeMillis();
       int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
      FFSDebug.log("TempHistCleanupHandler.resubmitEventHandler: begin",
          PRINT_DEV);
      eventHandler(eventSequence, evts, dbh);
      FFSDebug.log("TempHistCleanupHandler.resubmitEventHandler: end", 
          PRINT_DEV);
      PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
   }

//=====================================================================
// create instances of PagedACH, PagedWire,
// PagedTransfer, PagedCashCon, PagedBillPay, then call cleanup method from
// each of them.
//=====================================================================
    public void summaryCleanup(FFSConnectionHolder dbh, int interval)
    throws FFSException {
    	String methodName = "TempHistCleanupHandler.summaryCleanup";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        PagedTmpTable data = null;

        //Cleanup ACH
        data = new PagedACH();
        data.cleanup(dbh, interval);

        //Cleanup CashCon
        data = new PagedCashCon();
        data.cleanup(dbh, interval);

        //Cleanup Wire
        data = new PagedWire();
        data.cleanup(dbh, interval);

        //Cleanup Transfer
        data = new PagedTransfer();
        data.cleanup(dbh, interval);

        //Cleanup Bill Pay
        data = new PagedBillPay();
        data.cleanup(dbh, interval);

		// Cleanup old temp hist table
		TempHistUtil.cleanAll(interval);
		PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }
}
