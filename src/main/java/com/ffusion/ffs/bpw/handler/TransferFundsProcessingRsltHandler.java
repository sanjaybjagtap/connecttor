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
import com.ffusion.ffs.bpw.custimpl.transfers.interfaces.TransferFundsResult;
import com.ffusion.ffs.bpw.db.DBConnCache;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.util.BPWUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.sap.banking.common.interceptors.PerfLoggerUtil;


/*
 * This class triggers tha bank backend (by calling "TransferFundsResult.processTransferFundsRslt()"
 * to pass back to BPW the results of transfer funds processing. The handler will be triggered by
 * ETFFUNDRSLTTRN schedule
 */
public class TransferFundsProcessingRsltHandler extends TransferFundsBaseHandler implements BPWScheduleHandler
{

    public TransferFundsProcessingRsltHandler()
    {
	init();
	 
	try{
		BackendProvider backendProvider = getBackendProviderService();
		_transferFundsRslt = backendProvider.getTransferFundsResultInstance();

	} catch(Throwable t){
		FFSDebug.log("Unable to get TransferFundsResult Instance" , FFSConst.PRINT_ERR);
	}

    }
    
    /**
     * This method is called by the scheduler engine to handle events
     * @param eventSequence contains the sequence of the event
     * @param evts contains information about the events
     * @param dbh contains information about the database connection
     *
     * Semantics:
     * ---------
     * 1. if the event sequence represents the last event, call into the backend to
     *		get the results of transfer funds processing
     */ 
    public void eventHandler( int eventSequence, EventInfoArray evts, FFSConnectionHolder dbh ) throws Exception
    {
	final String methodName = "TransferFundsProcessingRsltHandler.eventHandler:";
	long start = System.currentTimeMillis();
    int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
	processEvent( eventSequence, evts, dbh, false, methodName );
	PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }
    
    /**
     * This method is called by the scheduling engine during event resubmission
     * @param eventSequence contains the sequence of the event
     * @param evts contains information about the events
     * @param dbh contains information about the database connection
     *
     * Semantics:
     * ---------
     * 1. if the event sequence represents the last event, call into the backend to
     *		get the results of transfer funds processing
     */ 
    public void resubmitEventHandler( int eventSequence, EventInfoArray evts, FFSConnectionHolder dbh )throws Exception
    {
	final String methodName = "TransferFundsProcessingRsltHandler.resubmitEventHandler:";
	long start = System.currentTimeMillis();
    int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
	processEvent( eventSequence, evts, dbh, true, methodName );
	PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    // This method is invoked by processEvent() whenever the last sequence is encountered.
    // This is the method where we retrieve the transfers from the database and then send
    // them to the backend for processing.
    // @param dbh contains information about the database connection
    // @param methodName contains the name of the method invoking this method
    //
    // Semantics
    // ---------
    // Call doProcessTrans() with possibleDuplicate set to false to indicate that
    // this is not a resubmission and represents regular transfer processing
    protected void processTrans( FFSConnectionHolder dbh, String methodName ) throws Exception
    {
	doProcessTrans( dbh, false, methodName );
    }

    // This method is invoked by processEvent() whenever the last sequence is encountered
    // in the case of event resubmission. This is the method where we retrieve the
    // transfers from the database and then send them to the backend for processing.
    // @param dbh contains information about the database connection
    // @param methodName contains the name of the method invoking this method
    //
    // Semantics
    // ---------
    // Call doProcessTrans() with possibleDuplicate set to true to indicate that
    // this is a resubmission.
    protected void processResubmitTrans( FFSConnectionHolder dbh, String methodName ) throws Exception
    {
	doProcessTrans( dbh, true, methodName );
    }

    // This method is invoked by processEvents() whenever the last sequence is
    // encountered. This is the method where the backend handler is invoked
    // to pass the results to BPW
    // @param dbh contains information about the database connection
    // @param possibleDuplicate contains information about whether the transactions may have
    //		been sent to BPW before. This will be set to true in case of crash recovery
    // @param methodName contains the name of the method invoking this method
    //
    //
    // Semantics
    // ---------
    // 1. generate a new batch key and bind the db connection using the batch key so
    //		that the backend can retrieve this same db connection
    // 2. Tell the backend with FIID owns this schedule
    // 3. call into the backend handler to pass the results of transfer processing
    //		to BPW.
    private void doProcessTrans( FFSConnectionHolder dbh, boolean possibleDuplicate, String methodName ) throws Exception
    {
	// Generate a new batch key and bind the db connection using the batch key.
	String batchKey = DBConnCache.getNewBatchKey();
	DBConnCache.bind( batchKey, dbh );

	// Tell the backend which FIID owns this schedule
	ArrayList list = new ArrayList();
	list.add( _transferFIId );
	BPWUtil.setFIIDList( list, _transferFundsRslt );

    _transferFundsRslt.setProcessId(_processId);

    // Call into the backend handler to pass results of
	// transfer processing to BPW
        _transferFundsRslt.processTransferFundsRslt( batchKey, new Hashtable() );
	
        // Remove the binding of the db connection and the batch key.
        DBConnCache.unbind( batchKey );
    }
    
    private TransferFundsResult _transferFundsRslt = null;
    
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
