//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2001.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Hashtable;

import com.ffusion.ffs.bpw.audit.TransAuditLog;
import com.ffusion.ffs.bpw.db.BPWExtraInfo;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.db.ExternalTransferAccount;
import com.ffusion.ffs.bpw.interfaces.ACHConsts;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.CustomerInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.ExtTransferAcctInfo;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.TransferInfo;
import com.ffusion.ffs.bpw.interfaces.util.AccountUtil;
import com.ffusion.ffs.bpw.util.resources.BPWLocaleUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.scheduling.constants.ScheduleConstants;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.ffusion.ffs.util.FFSUtil;
import com.ffusion.util.ILocalizable;
import com.ffusion.util.logging.AuditLogRecord;

public abstract class TransferFundsBaseHandler implements BPWResource, FFSConst, DBConsts
{
    // values that need to be set on the transfers obtained from the database
    protected static final String PROCESSID = "ProcessId";
    protected static final String POSSIBLEDUPLICATE = "PossibleDuplicate";
    protected static final String DBTRANSKEY = "DBTransKey";

    // note: no constant has been defined for file based schedules as of yet
    // and this will need to be changed when one is defined
    protected static final int FILE_BASED_SCHEDULE = 1;

    // initialize the class
    protected void init()
    {
        _propertyConfig = ( PropertyConfig )FFSRegistry.lookup( PROPERTYCONFIG );
        if( _propertyConfig.LogLevel >= AUDITLOGLEVEL_STATUSUPDATE ) {
	    _doAuditLogging = true;
	}
	_batchSize = _propertyConfig.getBatchSize();
	if( _batchSize < 0 ) {
	    _batchSize = 0;
	}
    }

    // This method is invoked by processEventHandler() and processResubmitEventHandler()
    // which are both called by the scheduling engine
    // @param eventSequence contains the event sequence
    // @param evts contains information about the events that occured
    // @param dbh contains information about the database connection
    // @param possibleDuplicate contains information about whether the transactions may have
    //		been sent to BPW before. This will be set to true in case of crash recovery
    // @param methodName contains the name of the method invoking this method
    //
    // Semantics
    // ---------
    // 1. if it is the first sequence we store the FIId, process Id and determine if the
    //		schedule is file-based or not
    // 2. if this is the last event we do the following
    //		i.  	if the FIId and process Id were not stored, we read it from the
    //			evts object and we also determine if the schedule is file based or not
    //		ii. 	depending on whether it is a resubmit or not we either call processTrans()
    //			or processResubmitTrans() to process the transfers
    protected void processEvent( int eventSequence, EventInfoArray evts, FFSConnectionHolder dbh, boolean possibleDuplicate, String methodName ) throws Exception
    {
	try {
	    FFSDebug.log( methodName, " begin ", BPWLocaleUtil.getMessage( ACHConsts.TRANSFER_HANDLER_EVENT_INFO, new String[] {  String.valueOf( eventSequence ), String.valueOf( evts._array.length ) }, BPWLocaleUtil.TRANSFER_MESSAGE ), PRINT_DEV );
	} catch( Throwable th ) {
	    FFSDebug.log( methodName, FFSDebug.stackTrace( th ), PRINT_ERR );
	}

        if( eventSequence == ScheduleConstants.EVT_SEQUENCE_FIRST ) {
	    // FIRST sequence
            _transferFIId = evts._array[0].FIId;
	    _processId = evts._array[0].processId;
	    if( possibleDuplicate && evts._array[0].fileBasedRecovery == FILE_BASED_SCHEDULE ) {
		_isScheduleFileBased = true;
	    }

        // initialize backend for processing
        initProcessTrans(dbh, methodName);

        } else if( eventSequence == ScheduleConstants.EVT_SEQUENCE_NORMAL ) {
        	_srvrtid = evts._array[0].InstructionID;
        } else if( eventSequence == ScheduleConstants.EVT_SEQUENCE_LAST ) {
	    // LAST sequence
	    if( _transferFIId == null ) {
		_transferFIId = evts._array[0].FIId;
	    }
	    if( _processId == null ) {
		_processId = evts._array[0].processId;
	    }
	    if( possibleDuplicate && evts._array[0].fileBasedRecovery == FILE_BASED_SCHEDULE ) {
		_isScheduleFileBased = true;
	    }

	    // ensure that the process Id and FIId are not null before
	    // processing the transfers
	    if( _transferFIId == null || ( _transferFIId != null && _transferFIId.trim().length() == 0 ) ) {
		FFSDebug.log( methodName, BPWLocaleUtil.getMessage( ACHConsts.SCHEDULE_NULL_FIID, null, BPWLocaleUtil.TRANSFER_MESSAGE ), PRINT_ERR );
		return;
	    }
	    if( _processId == null || ( _processId != null && _processId.trim().length() == 0 ) ) {
		FFSDebug.log( methodName, BPWLocaleUtil.getMessage( ACHConsts.SCHEDULE_NULL_PROCESSID, null, BPWLocaleUtil.TRANSFER_MESSAGE ), PRINT_ERR );
		return;
	    }

	    // process the transfers
	    if( possibleDuplicate ) {
		processResubmitTrans( dbh, methodName );
	    } else {
		processTrans( dbh, methodName );
	    }

	    // reset the fields to their default values
            _transferFIId = null;
	    _processId = null;
	    _isScheduleFileBased = false;
	}

	FFSDebug.log( methodName, " end ", PRINT_DEV );
    }

    // This method is invoked by processEvent() whenever the first sequence is encountered.
    // This method will be leveraged when initialization is required before transfer
    // processing begins.
    // @param dbh contains information about the database connection
    // @param methodName contains the name of the method invoking this method
    protected void initProcessTrans( FFSConnectionHolder dbh, String methodName ) throws Exception
    {
        // empty implementation, will be overridden as necessary
    }

    // This method is invoked by processEvent() whenever the last sequence is encountered.
    // This is the method where we retrieve the transfers from the database and then send
    // them to the backend for processing. Any class that extends this class will need
    // to implement this method
    // @param dbh contains information about the database connection
    // @param methodName contains the name of the method invoking this method
    protected abstract void processTrans( FFSConnectionHolder dbh, String methodName ) throws Exception;

    // This method is invoked by processEvent() whenever the last sequence is encountered
    // in the case of event resubmission. This is the method where we retrieve the transfers from
    // the database and then send them to the backend for processing. Any class that extends this
    // class will need to implement this method
    // @param dbh contains information about the database connection
    // @param methodName contains the name of the method invoking this method
    protected abstract void processResubmitTrans( FFSConnectionHolder dbh, String methodName ) throws Exception;


    // This method reads the result set to build a TransferInfo object
    // which is then returned by this method
    // @param dbh contains the database connection
    // @param rs contains the result set
    // @param extra contains information about the additional parameters that need to
    //		be set on the transfers object
    // @param methodName contains the name of the method invoking this method
    protected static TransferInfo getTransferInfo( FFSConnectionHolder dbh, ResultSet rs, HashMap extra, String methodName ) throws Exception
    {
	TransferInfo transInfo = new TransferInfo();

	String val = ( String )extra.get( DBTRANSKEY );
	if( val != null ) {
	    transInfo.setDbTransKey( val );
	}

	val = ( String )extra.get( PROCESSID );
	if( val != null ) {
	    transInfo.setLastProcessId( val );
	} else{
	    transInfo.setLastProcessId( rs.getString( BPW_TRANSFER_LASTPROCESSID ) );
	}

	val = ( String )extra.get( POSSIBLEDUPLICATE );
	if( val != null ) {
	    try {
		transInfo.setPossibleDuplicate( Boolean.valueOf( val ).booleanValue() );
	    } catch( Throwable t ) {
		FFSDebug.log( FFSDebug.stackTrace( t ), PRINT_ERR );
	    }
	}

    transInfo.setEventId(String.valueOf(ScheduleConstants.EVT_SEQUENCE_NORMAL));
    transInfo.setSrvrTId( rs.getString( BPW_TRANSFER_SRVRTID ) );
	transInfo.setCustomerId( rs.getString( BPW_TRANSFER_CUSTOMERID ) );
	transInfo.setFIId( rs.getString( BPW_TRANSFER_FIID ) );
	transInfo.setTransferType( rs.getString( BPW_TRANSFER_TRANSFERTYPE ) );
	transInfo.setTransferDest( rs.getString( BPW_TRANSFER_TRANSFERDEST ) );
	transInfo.setTransferGroup( rs.getString( BPW_TRANSFER_TRANSFERGROUP ) );
	transInfo.setTransferCategory( rs.getString( BPW_TRANSFER_TRANSFERCATEGORY ) );
	transInfo.setBankFromRtn( rs.getString( BPW_TRANSFER_BANKFROMRTN ) );
	transInfo.setAccountFromNum( rs.getString( BPW_TRANSFER_ACCOUNTFROMNUM ) );
	transInfo.setAccountFromType( rs.getString( BPW_TRANSFER_ACCOUNTFROMTYPE ) );
	transInfo.setAccountToId( rs.getString( BPW_TRANSFER_EXTERNALACCTID ) );
	transInfo.setAmount( rs.getString( BPW_TRANSFER_AMOUNT ) );
	transInfo.setAmountCurrency( rs.getString( BPW_TRANSFER_AMOUNTCURRENCY ) );
	transInfo.setOrigAmount( rs.getString( BPW_TRANSFER_ORIGAMOUNT ) );
	transInfo.setOrigCurrency( rs.getString( BPW_TRANSFER_ORIGCURRENCY ) );
	transInfo.setDateCreate( rs.getString( BPW_TRANSFER_DATECREATE ) );
	transInfo.setDateDue( rs.getString( BPW_TRANSFER_DATEDUE ) );
	transInfo.setDateToPost( rs.getString( BPW_TRANSFER_DATETOPOST ) );
	transInfo.setDatePosted( rs.getString( BPW_TRANSFER_DATEPOSTED ) );
	transInfo.setPrcStatus( rs.getString( BPW_TRANSFER_STATUS ) );
	transInfo.setMemo( rs.getString( BPW_TRANSFER_MEMO ) );
	transInfo.setTemplateScope( rs.getString( BPW_TRANSFER_TEMPLATESCOPE ) );
	transInfo.setTemplateNickName( rs.getString( BPW_TRANSFER_TEMPLATENICKNAME ) );
	transInfo.setSourceTemplateId( rs.getString( BPW_TRANSFER_SOURCETEMPLATEID ) );
	transInfo.setSourceRecSrvrTId( rs.getString( BPW_TRANSFER_SOURCERECSRVRTID ) );
	transInfo.setSubmittedBy( rs.getString( BPW_TRANSFER_SUBMITTEDBY ) );
	transInfo.setLogId( rs.getString( BPW_TRANSFER_LOGID ) );
	transInfo.setOriginatingUserId( rs.getString( BPW_TRANSFER_ORIGINATINGUSERID ) );
	transInfo.setConfirmNum( rs.getString( BPW_TRANSFER_CONFIRMNUM ) );
	transInfo.setConfirmMsg( rs.getString( BPW_TRANSFER_CONFIRMMSG ) );
	transInfo.setTypeDetail( rs.getString( BPW_TRANSFER_TYPEDETAIL ) );
	transInfo.setLastChangeDate( rs.getString( BPW_TRANSFER_LASTCHANGEDATE ) );
	transInfo.setProcessType( rs.getInt( BPW_TRANSFER_PROCESSTYPE ) );
	transInfo.setProcessLeadNumber( rs.getInt( BPW_RECTRANSFER_PROCESSLEADNUMBER ) );
	transInfo.setProcessDate( rs.getString( BPW_TRANSFER_PROCESSDATE ) );
	transInfo.setAction( rs.getString( BPW_TRANSFER_ACTION ) );
	transInfo.setFundsRetry( rs.getInt( BPW_TRANSFER_FUNDSRETRY ) );
	transInfo.setFundsProcessing( rs.getInt( BPW_TRANSFER_FUNDSPROCESSING ) );
	transInfo.setAccountFromId( rs.getString( BPW_RECTRANSFER_ACCOUNTFROMID ) );
	transInfo.setBankFromRtnType( rs.getString( BPW_TRANSFER_BANKFROMRTNTYPE ) );
	transInfo.setProcessNumber( rs.getInt( BPW_TRANSFER_PROCESSNUMBER ) );

	// populate the transfer info with the account and extra info
	populateAccountAndExtraInfo( transInfo, dbh, methodName );

	return transInfo;
    }

    //
    // Populates the transfer with the Account and Extra Information
    // @param info contains the transfer that needs to be populated
    // @param dbh contains the database connection
    // @param currMethodName contains the name of the method invoking this method
    private static void populateAccountAndExtraInfo( TransferInfo info, FFSConnectionHolder dbh, String curMethodName ) throws FFSException
    {
        ExtTransferAcctInfo extAcctInfo = null;
        CustomerInfo custInfo = null;
        Hashtable xtraInfo = null;

        try {
            // Populate corresponding From and To Account information
            // FromAccount
            String acctFromId = info.getAccountFromId();
            extAcctInfo = new ExtTransferAcctInfo();
            if ( acctFromId != null && acctFromId.trim().length() > 0 ) {
                extAcctInfo.setAcctId(acctFromId);
                // Get account and don't exclude unmanaged
                extAcctInfo
                = ExternalTransferAccount.getExternalTransferAccount(dbh,
                                                                     extAcctInfo,
                                                                     false, false);
                if (extAcctInfo.getStatusCode() != ACHConsts.SUCCESS) {
                    info.setStatusCode(extAcctInfo.getStatusCode());
                    info.setStatusMsg(extAcctInfo.getStatusMsg());
                    return;
                } else {
                    info.setAccountFromInfo(extAcctInfo);
                }
            } else {
                // FromId will be null for ItoE transfers
                // Create a acctInfo object and populate with rtn, num, type
                extAcctInfo.setAcctBankRtn(info.getBankFromRtn());
                extAcctInfo.setBankRtnType(info.getBankFromRtnType());
                extAcctInfo.setAcctNum(info.getAccountFromNum());
                extAcctInfo.setAcctType(info.getAccountFromType());
                info.setAccountFromInfo(extAcctInfo);
            }
            // To Account
            String acctToId = info.getAccountToId();
            if ( acctToId != null && acctToId.trim().length() > 0 ) {
                extAcctInfo = new ExtTransferAcctInfo();
                extAcctInfo.setAcctId(acctToId);
                // Get account and don't exclude unmanaged
                extAcctInfo
                = ExternalTransferAccount.getExternalTransferAccount(dbh,
                                                                     extAcctInfo,
                                                                     false, false);
                if (extAcctInfo.getStatusCode() != ACHConsts.SUCCESS) {
                    info.setStatusCode(extAcctInfo.getStatusCode());
                    info.setStatusMsg(extAcctInfo.getStatusMsg());
                    return;
                } else {
                    info.setAccountToNum(extAcctInfo.getAcctNum());
                    info.setAccountToType(extAcctInfo.getAcctType());
                    info.setBankToRtn(extAcctInfo.getAcctBankRtn());
                    info.setAccountToInfo(extAcctInfo);
                }
            } // we should always have ToAcctId saved

            // Populate ExtraInfo
            xtraInfo = BPWExtraInfo.getXtraInfo(dbh, info.getFIId(),
                                                info.getSrvrTId(), info.getTransferType().trim()+ "_" + DBConsts.HISTTYPE_TRANSFER);
            info.setExtInfo(xtraInfo);
            FFSDebug.log(curMethodName, "populated XtraInfo = " + xtraInfo, PRINT_DEV);

            info.setStatusCode(SUCCESS);
            info.setStatusMsg(SUCCESS_MSG);

        } catch ( Throwable exc ) {
            String msg = curMethodName + "failed: ";
            String err = FFSDebug.stackTrace(exc);
            FFSDebug.log(msg, err, PRINT_ERR);
            throw new FFSException(exc, msg);
        }
    }

    // Determines the value to be used for the column dateToProcess
    protected static String getProcessDate()
    {
	// the format for the date is yyyyMMdd00
	StringBuffer dateBuf = new StringBuffer( FFSUtil.getDateString(DUE_DATE_FORMAT));
	dateBuf.append( "00" );
	return dateBuf.toString();
    }

    // Logs information about the transfer to the Audit log
    // @param dbh contains information about the database
    // @param transferInfo contains information about the transfer
    // @param transType contains the audit log transaction type
    // @param msg contains any description that needs to be added
    // @param methodName contains the name of the method invoking this method
    protected static void auditLog( FFSConnectionHolder dbh, TransferInfo transferInfo, int transType, ILocalizable msg, String methodName )
    {
	if( transferInfo == null || msg == null ) {
	    return;
	}
        String toAcctId = null;
        String fromAcct = null;
        String userId = null;
        String amount = null;
        int businessId = 0;
        AuditLogRecord auditLogRecord = null;


        // Do Audit logging here
        try {
            userId = transferInfo.getSubmittedBy();
            amount = transferInfo.getAmount();
            if( ( amount == null ) || ( amount.trim().length() == 0 ) ) {
                amount = "-1";
            }
	    toAcctId = AccountUtil.buildTransferToAcctId(transferInfo);
	    fromAcct = AccountUtil.buildTransferFromAcctId(transferInfo);
            if( transferInfo.getCustomerId().equals( transferInfo.getSubmittedBy() ) ) {
		// Consumer
                businessId = 0;
            } else {
		// Business
                businessId = Integer.parseInt( transferInfo.getCustomerId() );
            }

            auditLogRecord = new AuditLogRecord( userId, //userId
                                                 "", //agentId
                                                 "", //agentType
                                                 msg,               //description
                                                 transferInfo.getLogId(),
                                                 transType,                //tranType
                                                 businessId, //BusinessId
                                                 new BigDecimal( amount ),
                                                 transferInfo.getAmountCurrency(), 
                                                 transferInfo.getSrvrTId(),
                                                 transferInfo.getPrcStatus(),
                                                 toAcctId,
                                                 transferInfo.getBankToRtn(),
                                                 fromAcct,
                                                 transferInfo.getBankFromRtn(),
                                                 -1 );

            TransAuditLog.logTransAuditLog( auditLogRecord, dbh.conn.getConnection() );

        } catch( Throwable ex ) {
	    FFSDebug.log( methodName, FFSDebug.stackTrace( ex ), PRINT_ERR );
        }
    }

    // logs error information to the Audit log
    // @param transferInfo contains information about the transfer
    // @param transType contains the audit log transaction type
    // @param msg contains any description that needs to be added
    // @param methodName contains the name of the method invoking this method
    protected static void auditLogError( TransferInfo transferInfo, int transType, ILocalizable msg, String methodName )
    {
        FFSConnectionHolder logDbh = null;
        try {
            // Get a connection handle for the Eror logging
            logDbh = new FFSConnectionHolder();
            logDbh.conn = DBUtil.getConnection();
            if( logDbh.conn == null ) {
		FFSDebug.log( methodName, BPWLocaleUtil.getMessage( ACHConsts.FAILED_TO_GET_DB_CONNECTION, null, BPWLocaleUtil.WIRE_MESSAGE ), PRINT_ERR );
		return;
            }

            auditLog( logDbh, transferInfo, transType, msg, methodName );

        } catch( Throwable ex ) {
            FFSDebug.log(  methodName, FFSDebug.stackTrace( ex ), PRINT_ERR );

        } finally {
            DBUtil.freeConnection( logDbh.conn );
        }
    }

    protected TransferInfo createDummyTransferInfo(int eventSequence)
    {
        TransferInfo transInfo = new TransferInfo();
        transInfo.setSrvrTId("-1");
        transInfo.setAccountFromNum("DummyAccount");

        //set its eventId as eventSequence
        transInfo.setEventId(String.valueOf(eventSequence));
        transInfo.setDbTransKey(_batchKey);

        return transInfo;
    }


     protected PropertyConfig  _propertyConfig = null;
    protected boolean _doAuditLogging = false;
    protected int _batchSize = 0;
    protected String _batchKey = null;
    protected String _transferFIId = null;
    protected String _processId = null;
    protected boolean _isScheduleFileBased = false;
    protected String _srvrtid = null;

}
