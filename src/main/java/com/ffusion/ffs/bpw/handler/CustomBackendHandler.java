//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2001.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import com.ffusion.ffs.bpw.custimpl.interfaces.BackendProvider;
import com.ffusion.ffs.bpw.custimpl.interfaces.CustomBackend;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.sap.banking.common.interceptors.PerfLoggerUtil;


//=====================================================================
//
// This class contains a callback handler that is registered in the 
// InstructionType table.  The registered callback handler will be called
// by the Scheduling engine for the customer files Processing.
//
//=====================================================================

public class CustomBackendHandler implements
   com.ffusion.ffs.bpw.interfaces.DBConsts,FFSConst,
   com.ffusion.ffs.bpw.interfaces.BPWResource,BPWScheduleHandler
{
   public CustomBackend _custImpl = null;
   private boolean _okToCall;

   public CustomBackendHandler(){
	  
	   try{
		   BackendProvider backendProvider = getBackendProviderService();
		   _custImpl = backendProvider.getCustomBackendInstance();
		   
	   } catch(Throwable t){
		   FFSDebug.log("Unable to get CustoBackend Instance" , PRINT_ERR);
	   }
	     
   }

//=====================================================================
// Description: This method is called by the Scheduling engine
//=====================================================================
   public void eventHandler( 
                            int eventSequence,
                            EventInfoArray evts,
                            FFSConnectionHolder dbh )
   throws Exception
   {
	   String methodName = "CustomBackendHandler.eventHandler";
       long start = System.currentTimeMillis();
       int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
      FFSDebug.log("CustomBackendHandler.eventHandler: begin, eventSeq=" + eventSequence, PRINT_DEV);
      if ( eventSequence == 0 ) {         // FIRST sequence
          _okToCall = false;
      }
      else if ( eventSequence == 1 ) {    // NORMAL sequence
          _okToCall = true;
      }
      else if ( eventSequence == 2 ) {    // LAST sequence
          if (_okToCall) 
            _custImpl.processCustomImpl();
      }
      else if ( eventSequence == 3 ) {    // Batch-Start sequence
      }
      else if ( eventSequence == 4 ) {    // Batch-End sequence
      }
      FFSDebug.log("CustomBackendHandler.eventHandler: end", PRINT_DEV);
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
   public void resubmitEventHandler(int eventSequence, EventInfoArray evts, FFSConnectionHolder dbh)
   throws Exception
   {
	   String methodName = "CustomBackendHandler.resubmitEventHandler";
       long start = System.currentTimeMillis();
       int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
      FFSDebug.log("==== CustomBackendHandler.resubmitEventHandler: begin, eventSeq=" + eventSequence);
      if ( eventSequence == 0 ) {         // FIRST sequence
          _okToCall = false;
      }
      else if ( eventSequence == 1 ) {    // NORMAL sequence
          _okToCall = true;
      }
      else if ( eventSequence == 2 ) {    // LAST sequence
          if (_okToCall) 
            _custImpl.processCustomImpl();
      }
      else if ( eventSequence == 3 ) {    // Batch-Start sequence
      }
      else if ( eventSequence == 4 ) {    // Batch-End sequence
      }
      FFSDebug.log("==== CustomBackendHandler.resubmitEventHandler: end");
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
