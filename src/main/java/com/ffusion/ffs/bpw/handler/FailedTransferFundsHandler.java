//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2001.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import com.ffusion.ffs.bpw.custimpl.interfaces.BackendProvider;
import com.ffusion.ffs.bpw.custimpl.transfers.interfaces.FailedTransfers;
import com.ffusion.ffs.bpw.db.DBConnCache;
import com.ffusion.ffs.bpw.db.SQLConsts;
import com.ffusion.ffs.bpw.db.Transfer;
import com.ffusion.ffs.bpw.interfaces.ACHConsts;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.TransferInfo;
import com.ffusion.ffs.bpw.util.resources.BPWLocaleUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.scheduling.constants.ScheduleConstants;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.util.ILocalizable;
import com.ffusion.util.logging.AuditLogTranTypes;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

/**
 * This class passes all failed transfers to the bank backend for processing.
 * This handler will be triggered by "FAILEDETFTRN" schedule
 */
public class FailedTransferFundsHandler extends TransferFundsBaseHandler implements BPWScheduleHandler
{

    private static final String _NOTIF = "_NOTIF";

    public FailedTransferFundsHandler()
    {
	init();
    try{
    		BackendProvider backendProvider = getBackendProviderService();
    		_failedTransfers = backendProvider.getFailedTransfersInstance();

    	} catch(Throwable t){
    		FFSDebug.log("Unable to get FailedTransfers Instance" , PRINT_ERR);
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
     * 	1. If this is the first event store the FIId and the processId
     *	2. If this is the last event the transfers will be processed in a
     *	   predetermined batchsize as follows:
     *  	i.	Retrieve from the database all transfers with the given FIId,
     *			FundsProcessing=BPW_TRANSFER_FUNDS_PROCESSING_SUPPORTED,
     *			Status=FAILEDON or FUNDSREVERTFAILED or NOFUNDS and
     *			Action=BPW_TRANSFER_ACTION_FAILED
     *		ii.  	Update the LastProcessId to the procsess Id of the schedule
     *			and the ProcessNumber to 0 and commit this change to the
     *			database as this will help recover transfers during a crash
     *			recovery.
     *		iii. 	Update the status of the transfers from FAILEDON or NOFUNDS or
     *			FUNDSREVERTFAILED to FAILEDON_NOTIF, NOFUNDS_NOTIF or
     *			FUNDSREVERTFAILED_NOTIF respectively
     *		iv.	Call the backend to process the transfers
     * 		iv.  	If errors occur in any of the above steps, log this fact and
     *			return from this method
     */ 
    public void eventHandler( int eventSequence, EventInfoArray evts, FFSConnectionHolder dbh ) throws Exception
    {
	final String methodName = "FailedTransferFundsHandler.eventHandler:";
	processEvent( eventSequence, evts, dbh, false, methodName );
    }

    /**
     * This method is called by the scheduling engine during event resubmission
     * @param eventSequence contains the sequence of the event
     * @param evts contains information about the events
     * @param dbh contains information about the database connection
     *
     * Semantics:
     * ---------
     * 	1. If this is the first event store the FIId, processId and determine
     *	   if the schedule is file-based.
     *  2. Retrieve the maximum value for processNumber and increment it by 1
     *	3. If this is the last event the transfers will be processed in a
     *	   predetermined batchsize as follows:
     *		i.   	Depending on whether the schedule is file-based or not
     *			retrieve the transfers from the database as outlined
     *			below:
     *			- For file-based schedules retrieve from the database all
     *			transfers with the given FIId,
     *			FundsProcessing=BPW_TRANSFER_FUNDS_PROCESSING_SUPPORTED,
     *			LastProcessId = process id of schedule and
     *			ProcessNumber < max ProcessNumber read from database incremented
     *			by 1
     *			- For non file-based schedules retrieve from the database all
     *			transfers with the given FIId,
     * 			FundsProcessing=BPW_TRANSFER_FUNDS_PROCESSING_SUPPORTED,
     *			Status=FAILEDON or FUNDSREVERTFAILED or NOFUNDS,
     *			Action=BPW_TRANSFER_ACTION_FAILED and LastProcessId =
     *			process Id of schedule.
     *		ii. 	Set the process Id on the transfers to the process Id of the
     *			schedule and mark the transfers as a possible duplicate.
     *		iii.  	Update the LastProcessId to the process Id of the schedule
     *			and ProcessNumber to max value of ProcessNumber read from
     *			the database incremented by 1, and commit this change to the
     *			database as this will help recover transfers during a crash
     *			recovery.
     *		iv. 	Update the status of the transfers from FAILEDON or NOFUNDS or
     *			FUNDSREVERTFAILED to FAILEDON_NOTIF, NOFUNDS_NOTIF or
     *			FUNDSREVERTFAILED_NOTIF respectively
     *		 v.  	Call the backend to process the transfers
     * 		vi.  	If errors occur in any of the above steps, log this fact and
     *			return from this method
     *  	vii.	Retrieve from the database all transfers with the given FIId,
     *			FundsProcessing=BPW_TRANSFER_FUNDS_PROCESSING_SUPPORTED,
     *			Status=FAILEDON or FUNDSREVERTFAILED or NOFUNDS and
     *			Action=BPW_TRANSFER_ACTION_FAILED
     *		viii.  	Update the LastProcessId to the process Id of the schedule
     *			and ProcessNumber to max value of ProcessNumber read from
     *			the database incremented by 1, and commit this change to the
     *			database as this will help recover transfers during a crash
     *			recovery.
     *		ix. 	Update the status of the transfers from FAILEDON or NOFUNDS or
     *			FUNDSREVERTFAILED to FAILEDON_NOTIF, NOFUNDS_NOTIF or
     *			FUNDSREVERTFAILED_NOTIF respectively
     *		x.	Call the backend to process the transfers
     * 		xi.  	If errors occur in any of the above steps, log this fact and
     *			return from this method
     */ 
    public void resubmitEventHandler( int eventSequence, EventInfoArray evts, FFSConnectionHolder dbh )throws Exception
    {
    	
        
	final String methodName = "FailedTransferFundsHandler.resubmitEventHandler:";
	long start = System.currentTimeMillis();
    int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
	
	processEvent( eventSequence, evts, dbh, true, methodName );
    }

    public void initProcessTrans(FFSConnectionHolder dbh, String methodName) throws Exception
    {
    	String method = "FailedTransferFundsHandler.initProcessTrans";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
        
        _batchKey = DBConnCache.getNewBatchKey();
        DBConnCache.bind(_batchKey, dbh);

        TransferInfo[] transferInfos = { createDummyTransferInfo(ScheduleConstants.EVT_SEQUENCE_FIRST) };
        _failedTransfers.processFailedTransfers(transferInfos, null);
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
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
	// for regular processing set the processNumber to 0 (default)
	doProcessTrans( dbh, false, 0, methodName );
    }

    // This method is invoked by processEvent() whenever the last sequence is encountered
    // in the case of event resubmission. This is the method where we retrieve the
    // transfers from the database and then send them to the backend for processing.
    // @param dbh contains information about the database connection
    // @param methodName contains the name of the method invoking this method
    //
    // Semantics
    // ---------
    // 1. Get the max ProcessNumber from the database and increment it by 1
    // 2. Call doProcessTrans() with possibleDuplicate set to true to process the
    //		transfers that were processed by the schedule just before it
    //		crashed.
    // 3. Call doProcessTrans() with possibleDuplicate set to false to process
    //		transfers that were never processed by this schedule before the crash
    protected void processResubmitTrans( FFSConnectionHolder dbh, String methodName ) throws Exception
    {
	// get the max process number from the database and increment
	// it by 1
	int processNumber = Transfer.getMaxProcessNumberByProcessId( dbh, _processId );
	processNumber++;
	// get all the transfers that were sent to the backend before
	// this schedule failed and thus could be a possible duplicate
	doProcessTrans( dbh, true, processNumber, methodName );
	// get all the other transfers that were not sent to the backend
	// when this schedule failed. note that the processNumber for
	// these transfers will still be the max process number
	// read from the database incremented by 1
	doProcessTrans( dbh, false, processNumber, methodName );
    }

    // This method is invoked by processTrans() and processResubmitTrans()
    // This is the method where we retrieve the transfers from the database and then send
    // them to the backend for processing
    // @param dbh contains information about the database connection
    // @param possibleDuplicate contains information about whether the transactions may have
    // 		been sent to BPW before. This will be set to true in case of crash recovery
    // @param processNumber contains the value for the process number which will be 0 for
    //		regular processing and max(ProcessNumber) read from database + 1 for
    //		resubmission
    // @param methodName contains the name of the method invoking this method
    //
    // Semantics
    // ---------
    // 1. generate a new batch key and bind the db connection using the batch key so that the
    //		backend can retrieve this same db connection
    // 2. add the generated key to the extra hashmap which will be used
    //		to populate transfers retrieved from the database
    // 3. if this processing represents a resubmission populate the extra map with the appropriate
    // 		values
    // 4. dynamically generate the SQL for querying the transfers in the database
    //		based on whether this method is processing a resubmission or
    //		not as follows
    //		- for file-based resubmission, the transfers are looked up in the database
    //		based on the FIId, FundsProcessing=BPW_TRANSFER_FUNDS_PROCESSING_SUPPORTED,
    //		LastProcessId=process Id of schedule processing the transaction and
    //		ProcessNumber < max processNumber read from database + 1
    //		- for non file-based resubmission the transfers are looked up in the database
    //		based on the FIId, FundsProcessing=BPW_TRANSFER_FUNDS_PROCESSING_SUPPORTED,
    //		Status=FAILEDON or FUNDSREVERTFAILED or NOFUNDS, Action=BPW_TRANSFER_ACTION_FAILED
    //		and LastProcessId = process Id of schedule
    //		- for regular processing the transfers are looked up in the database based
    //		on the FIId, FundsProcessing=BPW_TRANSFER_FUNDS_PROCESSING_SUPPORTED,
    //		Status=FAILEDON or FUNDSREVERTFAILED or NOFUNDS and
    //		Action=BPW_TRANSFER_ACTION_FAILED
    // 5. for a predetermined batchSize do the following each in a transaction
    // 		i.	retrive the transfers from the database based on the generated SQL
    //		ii.  	set the LatProcessId to be the process Id of the current schedule and
    //			ProcessNumber to be 0 for regular processing and max value in database
    //			+ 1 for resubmission and commit this change to the database so that
    //			incase there is a crash, the transfers can be retrieved correctly
    //		iii. 	update the status of the transfers from FAILEDON or FUNDSREVERTFAILED or
    //			NOFUNDS to FAILEDON_NOTIF, FUNDSREVERTFAILED_NOTIF or NOFUNDS_NOTIF
    //			respectively. note that this is done BEFORE the backend call and the backend
    // 			may change the status again which will make that change take precedence
    //		iv. 	call the backend to process the transfer
    //		 v. 	if errors occur then rollback, else commit the work done in this transaction
    // 6. Unbind the db connection from the cache
    private void doProcessTrans( FFSConnectionHolder dbh, boolean possibleDuplicate, int processNumber, String methodName ) throws Exception
    {
	Connection con = null;
	if( dbh != null ) {
	    con = dbh.conn.getConnection();
	}
	if( con == null ) {
	    return;
	}
	
	PreparedStatement stmt1 = null;
	PreparedStatement stmt2 = null;
	PreparedStatement stmt3 = null;
	ResultSet rset = null;
	TransferInfo[] transferInfos = null;
	try {
	    // add the key to the extra map which will be used
	    // to populate the transfer object
	    HashMap extraMap = new HashMap();
	    extraMap.put( DBTRANSKEY, _batchKey );

	    // if this processing represents a resubmission
	    // populate the extra map with the appropriate values
	    if( possibleDuplicate ) {
		// populate the hashmap with the process Id and
		// possible duplicate set to true
		extraMap.put( PROCESSID, _processId );
		extraMap.put( POSSIBLEDUPLICATE, String.valueOf( Boolean.TRUE ) );
	    }

	    // Generate the dynamic SQLs and prepared statements
	    StringBuffer stmt1SQL = new StringBuffer();
	    if( possibleDuplicate && _isScheduleFileBased ) {
		// if this method is processing a resubmission and the schedule
		// is not file-based, we need to look for transfers in the database
		// based on FIId, FundsProcessing=2, LastProcessId=process Id
		// of schedule and ProcessNumber < max ProcessNumber read from database
		// incremented by 1
		stmt1SQL.append( SQLConsts.GET_RESUBMIT_TRANSFER_FOR_FILEBASED_SCHEDULE );
	    } else if( possibleDuplicate && !_isScheduleFileBased ) {
		// if this method is processing a resubmission and the schedule
		// is not file-based, we need to look for transfers in the database
		// based on FIId, FundsProcessing=2, Status=FAILEDON or NOFUNDS
		// or FUNDSREVERTFAILED, Action='Failed' and LastProcessId=process Id
		// of schedule
		stmt1SQL.append( SQLConsts.GET_RESUBMIT_TRANSFER_FOR_NONFILEBASED_FAILEDETFTRN );
	    } else {
		// if this method is processing a regular (non-resubmission) transfer
		// we need to look for transfers in the database based on FIId,
		// FundsProcessing=2, Status=FAILEDON or NOFUNDS or FUNDSREVERTFAILED
		// and Action='Failed'
		stmt1SQL.append( SQLConsts.GET_TRANSFER_FOR_FAILEDETFTRN );
	    }
	    stmt1SQL.append( " order by " ).append( BPW_TRANSFER_TYPEDETAIL );
	    stmt1 = con.prepareStatement( stmt1SQL.toString() );

	    stmt2 = con.prepareStatement( SQLConsts.UPDATE_TRANSFER_STATUS );
	    stmt3 = con.prepareStatement( SQLConsts.UPDATE_TRANSFER_PROCESSNUMBER_AND_PROCESSID );

	    Hashtable tab = new Hashtable();
	    int resultCount = 1;
	    ILocalizable msg = null;
	    while( resultCount > 0 ) {
		resultCount = 0;
		
		stmt1.clearParameters();
		if( _batchSize != 0 ) {
		    // select rows based on batchSize
		    stmt1.setMaxRows( _batchSize );
		}
		stmt1.setString( 1, _transferFIId );
		if( possibleDuplicate && _isScheduleFileBased ) {
		    stmt1.setString( 2, _processId );
		    stmt1.setInt( 3, processNumber );
		} else if( possibleDuplicate && !_isScheduleFileBased ) {
		    stmt1.setString( 2, _processId );
		}
		rset = stmt1.executeQuery();
		
		// based on the result set for each row returned
		// - create a TransferInfo object
		// - audit log the information stored in the TransferInfo object
		ArrayList transfers = new ArrayList();
		StringBuffer buf = new StringBuffer();
		while( rset.next() )
		{
		    TransferInfo transferInfo = getTransferInfo( dbh, rset, extraMap, methodName );
		    msg = BPWLocaleUtil.getLocalizableMessage(ACHConsts.PROCESSING_TRANSFER,
		    										  new Object[] {transferInfo.getSrvrTId()},
													  BPWLocaleUtil.TRANSFER_MESSAGE);
		    auditLog( dbh, transferInfo, AuditLogTranTypes.BPW_FAILEDETFTRN, msg, methodName );
		    transfers.add( transferInfo );
		    resultCount++;
		}

		if( resultCount > 0 ) {
		    int size = transfers.size();
		    transferInfos = new TransferInfo[size];
		    transferInfos = ( TransferInfo[] )transfers.toArray( transferInfos );
		
		    // update the LastProcessId to be the process id of the schedule
		    // and ProcessNumber to be the 0 or a value read from the database
		    // during crash recovery and commit this change to the database as
		    // this will help during crash receovery
		    for( int i = 0; i < size; i++ ) {
			stmt3.clearParameters();
			stmt3.setInt( 1, processNumber );
			stmt3.setString( 2, _processId );
			stmt3.setString( 3, transferInfos[i].getSrvrTId() );
			stmt3.executeUpdate();
		    }
		    con.commit();

		    // update the status of the transfers from FAILEDON, NOFUNDS,
		    // FUNDSREVERTFAILED to FAILEDON_NOTIF, NOFUNDS_NOTIF,
		    // FUNDSREVERTFAILED_NOTIF respectively, but do not commit
		    // this change
		    for( int i = 0; i < size; i++ ) {
			stmt2.clearParameters();
			buf.delete( 0, buf.length() );
			buf.append( transferInfos[i].getPrcStatus() ).append( _NOTIF );
			stmt2.setString( 1, buf.toString() );
			stmt2.setString( 2, transferInfos[i].getSrvrTId() );
			stmt2.executeUpdate();
		    }

		    // call the backend to revert the transfers
		    _failedTransfers.processFailedTransfers( transferInfos, tab );
    
		    // commit work
		    con.commit();
		}
	    }

        transferInfos = new TransferInfo[1];
        transferInfos[0] = createDummyTransferInfo(ScheduleConstants.EVT_SEQUENCE_LAST);
        _failedTransfers.processFailedTransfers(transferInfos, tab);

    } catch( Throwable t ) {
	    // rollback
	    con.rollback();

	    // log the error to the debug/audit log
	    if( transferInfos != null && transferInfos.length > 0 )
	    {
			int size = transferInfos.length;
			String msg = null;
			for( int i = 0; i < size; i++ )
			{
			    msg = BPWLocaleUtil.getMessage( ACHConsts.PROCESSING_TRANSFER_FAILED,
												new String[] { transferInfos[i].getSrvrTId(), t.toString() },
												BPWLocaleUtil.TRANSFER_MESSAGE );
			    FFSDebug.log( msg, PRINT_ERR );
			    if( _doAuditLogging )
			    {
					ILocalizable auditMsg = BPWLocaleUtil.getLocalizableMessage(ACHConsts.PROCESSING_TRANSFER_FAILED,
																				new Object[] { transferInfos[i].getSrvrTId(), t.toString() },
																				BPWLocaleUtil.TRANSFER_MESSAGE );
					auditLogError( transferInfos[i], AuditLogTranTypes.BPW_FAILEDETFTRN, auditMsg, methodName );
			    }
			}
	    }

	    // throw an exception
	    throw new FFSException( FFSDebug.stackTrace( t ) );

	} finally {
	    // close the statments and result set
	    if( rset != null ) {
		rset.close();
	    }
	    if( stmt1 != null ) {
		stmt1.close();
	    }
	    if( stmt2 != null ) {
		stmt2.close();
	    }
	    if( stmt3 != null ) {
		stmt3.close();
	    }
	    
	    // Remove the binding of the db connection and the batch key.
	    if( _batchKey != null ) {
		DBConnCache.unbind( _batchKey );
	    }
	}
    }

    private FailedTransfers _failedTransfers = null;

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