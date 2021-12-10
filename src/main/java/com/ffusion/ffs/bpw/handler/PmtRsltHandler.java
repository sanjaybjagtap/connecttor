//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2001.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import com.ffusion.ffs.bpw.custimpl.interfaces.BackendProvider;
import com.ffusion.ffs.bpw.custimpl.interfaces.PmtResult;
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
// by the Scheduling engine for the Pmt Result Processing.
//
//=====================================================================

public class PmtRsltHandler implements
   com.ffusion.ffs.bpw.interfaces.DBConsts,FFSConst,
   com.ffusion.ffs.bpw.interfaces.BPWResource, BPWScheduleHandler
{
   private PmtResult _pmtRslt = null;
   private boolean _okToCall;

   public PmtRsltHandler()
   {
	   _pmtRslt = null;
	   try{
		   BackendProvider backendProvider = getBackendProviderService();
		   _pmtRslt = backendProvider.getPmtResultInstance();

	   } catch(Throwable t){
		   FFSDebug.log("Unable to get PmtResult Instance" , PRINT_ERR);
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
	   String methodName = "PmtRsltHandler.eventHandler";
       long start = System.currentTimeMillis();
       int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
      FFSDebug.log("=== PmtRsltHandler.eventHandler: begin, eventSeq=" + eventSequence, PRINT_DEV);
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
            BPWUtil.setFIIDList(fiIdList, _pmtRslt);
            
            DBConnCache dbConnCache = (DBConnCache)FFSRegistry.lookup(DBCONNCACHE);
            String batchKey = DBConnCache.save(dbh);
            _pmtRslt.ProcessPmtRslt(batchKey);
            DBConnCache.unbind(batchKey);
         }
      }
      else if ( eventSequence == 3 ) {    // Batch-Start sequence
      }
      else if ( eventSequence == 4 ) {    // Batch-End sequence
      }
      FFSDebug.log("=== PmtRsltHandler.eventHandler: end", PRINT_DEV);
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
	   String methodName = "PmtRsltHandler.resubmitEventHandler";
       long start = System.currentTimeMillis();
       int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
      FFSDebug.log("==== PmtRsltHandler.resubmitEventHandler: begin, eventSeq=" + eventSequence);
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
            BPWUtil.setFIIDList(fiIdList, _pmtRslt);
            
            DBConnCache dbConnCache = (DBConnCache)FFSRegistry.lookup(DBCONNCACHE);
            String batchKey = DBConnCache.save(dbh);
            _pmtRslt.ProcessPmtRslt(batchKey);
            DBConnCache.unbind(batchKey);
         }
      }
      else if ( eventSequence == 3 ) {    // Batch-Start sequence
      }
      else if ( eventSequence == 4 ) {    // Batch-End sequence
      }
      FFSDebug.log("==== PmtRsltHandler.resubmitEventHandler: end");
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
