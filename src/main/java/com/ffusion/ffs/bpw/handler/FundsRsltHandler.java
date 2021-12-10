//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2001.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.ffusion.ffs.bpw.custimpl.interfaces.BackendProvider;
import com.ffusion.ffs.bpw.custimpl.interfaces.FundsResult;
import com.ffusion.ffs.bpw.db.DBConnCache;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.util.BPWUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.sap.banking.common.interceptors.PerfLoggerUtil;


//=====================================================================
//
// This class contains a callback handler that is registered in the
// InstructionType table.  The registered callback handler will be called
// by the Scheduling engine for the Funds Result Processing.
//
//=====================================================================

public class FundsRsltHandler implements
   com.ffusion.ffs.bpw.interfaces.DBConsts,FFSConst,
   com.ffusion.ffs.bpw.interfaces.BPWResource, BPWScheduleHandler
{
   private FundsResult _fundsRslt = null;
   private boolean _okToCall;
  // private static int _batchNum = 1;
   private static final Map batchNumbers = new HashMap();

   private final String BATCHKEYPREFIX = "FundsRslt_";

   public FundsRsltHandler()
   {
	    try{
	  	   BackendProvider backendProvider = getBackendProviderService();
	  	 _fundsRslt = backendProvider.getFundsResultInstance();
	  		   
	  	   } catch(Throwable t){
	  		   FFSDebug.log("Unable to get FundsResult Instance" , PRINT_ERR);
	  	   }
   }

   /**
    * We want to maintain the batch number based on the FIId to
    * support parallel bill payment processing for multiple FIs
    * @param fiId
    * @return
    */
   private String getBatchNumber(String fiId) {
   	final String methodName = "FundsAllocHandler.getBatchNumber: ";

   	FFSDebug.log(methodName, "Starts..... FIId: " + fiId, ", batchNumber: ",
   			(String)batchNumbers.get(fiId), PRINT_DEV);
   	String batchNum = "1";
   	if (batchNumbers.get(fiId) == null) {
   		batchNumbers.put(fiId, batchNum);
   	}
   	FFSDebug.log(methodName, "Ends..... FIId: " + fiId, ", batchNumber: ",
   			(String)batchNumbers.get(fiId), PRINT_DEV);
   	return (String)batchNumbers.get(fiId);
   }

   /**
    * Increase batch number for this FI by one
    * @param fiId
    */
   private void pumpBatchNumber(String fiId) {
   	final String methodName = "FundsAllocHandler.pumpBatchNumber: ";

   	FFSDebug.log(methodName, "Starts..... FIId: " + fiId, ", batchNumber: ",
   			(String)batchNumbers.get(fiId), PRINT_DEV);
   	String batchNum = "1";
   	if (batchNumbers.get(fiId) == null) {
   		batchNumbers.put(fiId, batchNum);
   	}
   	int batchNumInt = Integer.parseInt((String)batchNumbers.get(fiId));

   	batchNumInt++;
   	batchNumbers.put(fiId,  Integer.toString(batchNumInt));
   	FFSDebug.log(methodName, "Ends..... FIId: " + fiId, ", batchNumber: ",
   			(String)batchNumbers.get(fiId), PRINT_DEV);
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
	   String methodName = "FundsRsltHandler.eventHandler";
       long start = System.currentTimeMillis();
       int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
      FFSDebug.log("=== FundsRsltHandler.eventHandler: begin, eventSeq=" + eventSequence, PRINT_DEV);
      if ( eventSequence == 0 ) {         // FIRST sequence
          _okToCall = false;
      }
      else if ( eventSequence == 1 ) {    // NORMAL sequence
          _okToCall = true;
      }
      else if ( eventSequence == 2 ) {    // LAST sequence
         if (_okToCall) {
            // Tell the backend which FIID owns this schedule
            ArrayList fiIdList = new ArrayList();
            String fiId = evts._array[0].FIId;
            fiIdList.add(evts._array[0].FIId);

            BPWUtil.setFIIDList(fiIdList, _fundsRslt);

            DBConnCache dbConnCache = (DBConnCache)FFSRegistry.lookup(DBCONNCACHE);
            String batchKey = BATCHKEYPREFIX + "_" + fiId + "_" + getBatchNumber(fiId);
            pumpBatchNumber(fiId);
            //_batchNum++;
            DBConnCache.bind(batchKey, dbh);
            _fundsRslt.ProcessFundsRslt(batchKey);
            DBConnCache.unbind(batchKey);
         }
      }
      else if ( eventSequence == 3 ) {    // Batch-Start sequence
      }
      else if ( eventSequence == 4 ) {    // Batch-End sequence
      }
      FFSDebug.log("=== FundsRsltHandler.eventHandler: end", PRINT_DEV);
      PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
   }

//=====================================================================
// resubmitEventHandler()
// Description: This method is called by the Scheduling engine during
//    event resubmission.  It will set the possibleDuplicate to true
//    before calling the ToBackend handler.
// Arguments: none
// Returns:	  none
// Note:
//=====================================================================
   public void resubmitEventHandler(
                            int eventSequence,
                            EventInfoArray evts,
                            FFSConnectionHolder dbh )
   throws Exception
   {
	   String methodName = "FundsRsltHandler.resubmitEventHandler";
       long start = System.currentTimeMillis();
       int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
      FFSDebug.log("==== FundsRsltHandler.resubmitEventHandler: begin, eventSeq=" + eventSequence);
      if ( eventSequence == 0 ) {         // FIRST sequence
          _okToCall = false;
      }
      else if ( eventSequence == 1 ) {    // NORMAL sequence
          _okToCall = true;
      }
      else if ( eventSequence == 2 ) {    // LAST sequence
         if (_okToCall) {
            // Tell the backend which FIID owns this schedule
            ArrayList fiIdList = new ArrayList();
            fiIdList.add(evts._array[0].FIId);
            BPWUtil.setFIIDList(fiIdList, _fundsRslt);

            DBConnCache dbConnCache = (DBConnCache)FFSRegistry.lookup(DBCONNCACHE);
            String batchKey = BATCHKEYPREFIX +  "_" + evts._array[0].FIId + "_" + getBatchNumber(evts._array[0].FIId);
            pumpBatchNumber(evts._array[0].FIId);
            //_batchNum++;
            DBConnCache.bind(batchKey, dbh);
            _fundsRslt.ProcessFundsRslt(batchKey);
            DBConnCache.unbind(batchKey);
         }
      }
      else if ( eventSequence == 3 ) {    // Batch-Start sequence
      }
      else if ( eventSequence == 4 ) {    // Batch-End sequence
      }
      FFSDebug.log("==== FundsRsltHandler.resubmitEventHandler: end");
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
