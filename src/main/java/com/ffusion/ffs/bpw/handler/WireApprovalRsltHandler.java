//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2001.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import com.ffusion.ffs.bpw.custimpl.interfaces.BackendProvider;
import com.ffusion.ffs.bpw.custimpl.interfaces.WireApprovalResult;
import com.ffusion.ffs.bpw.db.DBConnCache;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
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

public class WireApprovalRsltHandler implements
   com.ffusion.ffs.bpw.interfaces.DBConsts,FFSConst,
   com.ffusion.ffs.bpw.interfaces.BPWResource, BPWScheduleHandler {

   private WireApprovalResult _wireApprovalResult = null;
   private boolean _okToCall;
   private final ArrayList wireList = null;
   private boolean supportFundsApprov = false;


   public WireApprovalRsltHandler() {
        PropertyConfig propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);
        
        try{
      		BackendProvider backendProvider = getBackendProviderService();
      		_wireApprovalResult = backendProvider.getWireApprovalResultInstance();

      	} catch(Throwable t){
      		FFSDebug.log("Unable to get WireApprovalResult Instance" , PRINT_ERR);
      	}

        supportFundsApprov = Boolean.valueOf(
                propertyConfig.otherProperties.getProperty(
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
    public void eventHandler(int eventSequence,
                            EventInfoArray evts,
                            FFSConnectionHolder dbh)  throws Exception  {

        final String methodName = "WireApprovalRsltHandler.eventHandler: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        
        //Wire Funds approval is not supported
        if (!supportFundsApprov) {
            FFSDebug.log(methodName, "Funds approval is not supported", 
                         PRINT_DEV);
            return;
        }

        FFSDebug.log(methodName, "begin, eventSeq: " + eventSequence, PRINT_DEV);
        boolean possibleDuplicate = false;
        processEvents(eventSequence, evts, dbh, possibleDuplicate);
        FFSDebug.log(methodName,  "end", PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    //=====================================================================
    // Description: This method is called by the Scheduling engine during
    //    event resubmission.  It will set the possibleDuplicate to true
    //    before calling the ToBackend handler.
    //=====================================================================
    public void resubmitEventHandler(int eventSequence,
                            EventInfoArray evts,
                            FFSConnectionHolder dbh) throws Exception  {
        final String methodName = "WireApprovalRsltHandler.resubmitEventHandler: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log(methodName, "begin, eventSeq: " + eventSequence, PRINT_DEV);
        boolean possibleDuplicate = true;
        processEvents(eventSequence, evts, dbh, possibleDuplicate);
        FFSDebug.log(methodName,  "end", PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    //==========================================================================
    public void processEvents(int eventSequence,
                            EventInfoArray evts,
                            FFSConnectionHolder dbh,
                            boolean possibleDuplicate)  throws Exception  {

        final String methodName = "WireApprovalRsltHandler.processEvents: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        
        //Wire Funds approval is not supported
        if (!supportFundsApprov) {
            FFSDebug.log(methodName, "Funds approval is not supported", 
                         PRINT_DEV);
            PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
            return;
        }
        
        FFSDebug.log(methodName, "begin, eventSeq: " + eventSequence, PRINT_DEV);
        if (eventSequence == 0) {         // FIRST sequence
            _okToCall = false;
        }else if (eventSequence == 1) {    // NORMAL sequence
            _okToCall = true;
        }else if (eventSequence == 2) {    // LAST sequence
            if (_okToCall) {
                // Tell the backend which FIID owns this schedule
                ArrayList fiIdList = new ArrayList();
                fiIdList.add(evts._array[0].FIId);
                BPWUtil.setFIIDList(fiIdList, _wireApprovalResult);

                processTrans(dbh);
            }
        }else if (eventSequence == 3) {    // Batch-Start sequence
        }else if (eventSequence == 4) {    // Batch-End sequence
        }
        FFSDebug.log(methodName,  "end", PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }


    /**
     * push the transaction key to backend handler to pass the result to BPW
     *
     * @param dbh
     */
    private void processTrans(FFSConnectionHolder dbh)
    throws Exception {

        // Generate a new batch key and bind the db connection
        // using the batch key.
        String batchKey = DBConnCache.getNewBatchKey();
        DBConnCache.bind(batchKey, dbh);
        Hashtable extInfo = new Hashtable(); // default no extra
        _wireApprovalResult.processWireApprovalRslt(batchKey, extInfo);
        // Remove the binding of the db connection and the batch key.
        DBConnCache.unbind(batchKey);
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
