// Copyright (c) 2002 Financial Fusion, Inc.  All Rights Reserved.

package com.ffusion.ffs.bpw.fulfill.rpps;


import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import com.ffusion.ffs.bpw.achagent.ACHAgent;
import com.ffusion.ffs.bpw.audit.FMLogAgent;
import com.ffusion.ffs.bpw.db.Customer;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.db.PayeeEditMask;
import com.ffusion.ffs.bpw.db.PmtInstruction;
import com.ffusion.ffs.bpw.db.RPPSBiller;
import com.ffusion.ffs.bpw.db.RPPSDB;
import com.ffusion.ffs.bpw.db.RPPSFI;
import com.ffusion.ffs.bpw.handler.OSGIUtil;
import com.ffusion.ffs.bpw.interfaces.ACHConsts;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.CustomerInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.FulfillmentInfo;
import com.ffusion.ffs.bpw.interfaces.PayeeRouteInfo;
import com.ffusion.ffs.bpw.interfaces.PmtInfo;
import com.ffusion.ffs.bpw.interfaces.PmtTrnRslt;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.RPPSBillerInfo;
import com.ffusion.ffs.bpw.interfaces.RPPSFIInfo;
import com.ffusion.ffs.bpw.interfaces.RPPSPmtFileInfo;
import com.ffusion.ffs.bpw.interfaces.RPPSPmtInfo;
import com.ffusion.ffs.bpw.interfaces.RPPSRecordInfo;
import com.ffusion.ffs.bpw.interfaces.handlers.FulfillmentBase;
import com.ffusion.ffs.bpw.master.BackendProcessor;
import com.ffusion.ffs.bpw.util.ACHAdapterConsts;
import com.ffusion.ffs.bpw.util.ACHAdapterUtil;
import com.ffusion.ffs.bpw.util.BPWRegistryUtil;
import com.ffusion.ffs.bpw.util.BPWUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.ffusion.ffs.util.FFSUtil;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.RPPSMsgSet.TypeBatchControlRecord;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.RPPSMsgSet.TypeBatchHeaderRecord;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.RPPSMsgSet.TypeCSPPmtEntryDetailRecord;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.RPPSMsgSet.TypeFileControlRecord;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.RPPSMsgSet.TypeFileHeaderRecord;
import com.ffusion.util.beans.filemonitor.FMLogRecord;
import com.sap.banking.common.interceptors.PerfLoggerUtil;
import com.sap.banking.io.beans.File;
import com.sap.banking.io.provider.interfaces.FileHandlerProvider;

/**
 * Main class of the RPPS connector.
 * It is also the center for response file handlers.
 */
public class RPPSHandler extends FulfillmentBase implements RPPSConsts {


    // RPPS Handler properties

    // static variables
    // These fields need to be static because they 
    // are used by other handlers (Biller Confirmation File)
    private static int _routeID = -1;
    private static double _paymentCost = 0;


    // non static
    private BackendProcessor _backendProcessor   = null;

    private String _exportFileBase       = null;
    private String _tempExportFileBase       = null;
    private String _errorFileBase        = null;

    private String _tempExportFileName        = null;

    private ACHAgent _achAgent = null;
    private boolean _started = false;

    private String  _fiId           = null;
    private String  _fiRPPSId       = null;
    private RPPSFIInfo _rppsFIInfo  = null;

    // this object includes fileId
    private RPPSPmtFileInfo _rppsPmtFileInfo = null;

    // Hashmap to hold all the payeeid and RPPS Biller Info objects
    // this schedule will handle
    // It can speed up when processing pmts in addPayments method
    // and it will be used in endPmts when generating RPPS files.

    // RISK: If the number of billers (For exmaple, more than 0.5M) 
    // is very big, we could see java.lang.OutOfMemoryError
    // However, the chane is very low.
    private HashMap  _rppsBillers     = new HashMap();


    private int _batchCount;
    private int _totalEntryCount;
    private BigDecimal _totalDebit = BPWUtil.getBigDecimal("0", 0);
    private BigDecimal _totalCredit = BPWUtil.getBigDecimal("0", 0);

    private int _batchSize;

    public RPPSHandler() throws Exception
    {


    }// RPPSHandler()




    /**
     * Initial RPPSHandler. This method could be called by
     * response file handlers since RPPSHandler is the
     * center of RPPS fulfillment.
     * 
     * RPPSBillerFileHandlerImpl needs routeId, paymentcost
     */
    public static void init() 
    {
        String mName = "RPPSHandler.init: "; 
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(mName,start);
        
        FFSDebug.log( mName + " start...", FFSDebug.PRINT_DEV );
        try {


            // get routeID

            // Create temporary object to get the full class name of this class
            // Can not use "this" because this method is static

            RPPSHandler rppsHandler = new RPPSHandler();
            // Class rppsClass = new Class( "RPPSHandler" );
            FulfillmentInfo fulfill = BPWRegistryUtil.getFulfillmentInfo( rppsHandler.getClass() );
            if ( fulfill == null ) {
            	PerfLoggerUtil.stopPerfLogging(mName, start, uniqueIndex);
                throw new FFSException( mName + "FulfillmentInfo not found for "
                                        + rppsHandler.getClass().getName() );
            }
            // toss it away.
            rppsHandler = null;

            _routeID = fulfill.RouteID;
            _paymentCost = fulfill.PaymentCost;

        } catch ( Exception e ) {
            FFSDebug.log( e, mName + "RPPS handler failed to initialize!");
            PerfLoggerUtil.stopPerfLogging(mName, start, uniqueIndex);
        }
        FFSDebug.log( mName + " end.", FFSDebug.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(mName, start, uniqueIndex);
    }// init()

    /**
     * Return route id. RPPS response file handlers need it.
     * @return 
     */
    protected static int getRouteID()
    {
        return _routeID;
    }

    /**
     * Return payment cost. RPPS response file handlers need it.
     * @return 
     */
    protected static double getPaymentCost()
    {
        return _paymentCost;
    }

    /**
     * Start RPPS fulfillment connector. It must be called
     * before other methods can be called.
     * 
     * @exception Exception
     */
    public void start() throws Exception
    {
        String mName = "RPPSHandler.start: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(mName,start);
        
        FFSDebug.log("Starting RPPS connector...");


        // get ACHAgent from FFSResitory. This agent must have been started.
        _achAgent = (ACHAgent)FFSRegistry.lookup( BPWResource.BPWACHAGENT );
        if ( _achAgent == null ) {
            FFSDebug.log( mName + "ACHAgent has not been started! Terminating process!" , FFSDebug.PRINT_ERR);
            throw new FFSException( mName + "ACHAgent has not been started! Terminating process!" );
        }

        String exportDir = RPPSUtil.getProperty( DBConsts.RPPS_EXPORT_DIR,
                                                 RPPSConsts.DEFAULT_EXPORT_DIR );

        // Create exportDir it does not exist, add File.separator
        _exportFileBase = ACHAdapterUtil.getFileNameBase( exportDir );

        // Create exportDir it does not exist, add File.separator
        _tempExportFileBase = ACHAdapterUtil.getFileNameBase( _exportFileBase + ACHAdapterConsts.STR_TEMP_DIR_NAME );


        String errorDir = RPPSUtil.getProperty( DBConsts.RPPS_ERROR_DIR,
                                                RPPSConsts.DEFAULT_ERROR_DIR );

        // Create errorDir it does not exist, add File.separator
        _errorFileBase = ACHAdapterUtil.getFileNameBase( errorDir );


        // get batch size from BPW properties
        PropertyConfig propertyConfig = (PropertyConfig)FFSRegistry.lookup(BPWResource.PROPERTYCONFIG);

        _batchSize =  propertyConfig.getBatchSize();

        if ( _backendProcessor == null ) {
            _backendProcessor = new BackendProcessor();
        }

        init();

        _started = true;
        FFSDebug.log("RPPS connector started");
        PerfLoggerUtil.stopPerfLogging(mName, start, uniqueIndex);
    }// start()

    /**
     * Stop RPPS fulfillment connector.
     * 
     * @exception Exception
     */
    public void shutdown() throws Exception
    {
        FFSDebug.log("Shutting down RPPS connector... ");

        _started = false;

        _achAgent = null;


        FFSDebug.log("RPPS connector shut down ");
    }// shutdown()


    /**
     * Starts handling payment related requests. This is the start of a new
     * fulfillment cycle i.e. the first method to be called when a payment
     * schedule is executed.
     *
     * @param dbh - Database connection holder
     * @param FId - FI's Id, whose payments will be started processing
     * @param extra - Holds extra information which FulfillAgent wants 
     *               to pass to Fulfillment. It is empty for now.
     *
     * NB: Do NOT modify the object dbh, or commit the changes with dbh.conn
     * in ANY part of the FulfillmentAPI implementation. This could cause
     * unexpected results and behaviors with the entire BPW system.
     *
     * !!! For FFI developers only:  We are violating this rule. !!!
     *
     * dbh is committed in this method. It is safe so far because we know that 
     * ScheduleRunnable does not pass open TX to here in this call ( SEQUENCE_FIRST)
     * However, we don't want to share this information with our customers. So we can 
     * keep the right to pass open TX from ScheduleRunnable if we have to in the future.
     *
     * The reason we want to commit is to avoid big TX when removing temp entry records
     * The other choice is that we create a db conneciton. However, this approach will
     * increase the possiblity of deadlocking
     *
     *
     */
    public void startPmtBatch( FFSConnectionHolder dbh, String fiId, HashMap extra ) throws Exception
    {
        // check started
        String mName = "RPPHandler.startPmtBatch: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(mName,start);
        
        if ( _started == false ) {
        	PerfLoggerUtil.stopPerfLogging(mName, start, uniqueIndex);
            throw new FFSException( mName + "RPPS connector is not started!" );
        }

        FFSDebug.log( mName + "start...", FFSDebug.PRINT_DEV);

        try {
            if (fiId == null || fiId.length() == 0) {
            	PerfLoggerUtil.stopPerfLogging(mName, start, uniqueIndex);
                throw new FFSException( mName + "fiId is null!" );
            }
            // call RPPSDB to remove temp record from EntryTmp table
            // which is left from previous schedule.
            // dbh is committed by this method
            RPPSDB.removeAllRPPSEntryTmps(dbh, fiId);    

        } catch (Exception e) {
            FFSDebug.log( mName +  "error:" + e.toString(), FFSConst.PRINT_ERR );
            PerfLoggerUtil.stopPerfLogging(mName, start, uniqueIndex);
            throw e;
        }

        _fiId = fiId;

        _rppsFIInfo =  RPPSFI.getRPPSFIInfoByFIId( dbh, _fiId );
        _fiRPPSId = _rppsFIInfo.getFiRPPSId();

        _rppsPmtFileInfo = new RPPSPmtFileInfo();
        _rppsBillers.clear();

        _batchCount = 0;
        _totalEntryCount = 0;
        _totalDebit = BPWUtil.getBigDecimal("0", 0);
        _totalCredit = BPWUtil.getBigDecimal("0", 0);

        FFSDebug.log( mName + "done.", FFSDebug.PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(mName, start, uniqueIndex);

    } // startPmtBatch()

    /**
     * Starts handling the payment requests. 
     *
     * @param pmtInfo - information about the payments
     * @param routeinfo - information about the payee registration with the service
     * being connected by this handler.
     * @param dbh - Database connection holder
     * The parameters pmtInfo and routeinfo are matched up by array indices so that
     * pmtInfo[i] corresponds to routeinfo[i].
     * This method is called after all the customer, payee, and customer-payee
     * batches are complete.
     *
     * NB: Do NOT modify the object dbh, or commit the changes with dbh.conn
     * in ANY part of the FulfillmentAPI implementation. This could cause
     * unexpected results and behaviors with the entire BPW system.
     */
    public void addPayments( PmtInfo[] pmtInfo,
                             PayeeRouteInfo[] routeinfo,
                             FFSConnectionHolder dbh )
    throws Exception
    {
        // check started
        String mName = "RPPHandler.addPayments: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(mName,start);
        
        if ( _started == false ) {
        	PerfLoggerUtil.stopPerfLogging(mName, start, uniqueIndex);
            throw new FFSException( mName + "RPPS connector is not started!" );
        }

        RPPSRecordInfo recordInfo = null;

        int txCode = 0;

        FFSDebug.log( mName + "start.", FFSConst.PRINT_DEV );

        int len = pmtInfo.length; // total pmts.

        try {
            if (pmtInfo == null || len <= 0) {
                return;
            }

            // process pmts. one by one
            for (int i = 0; i < len; ++i ) {

                PmtInfo aPmtInfo = pmtInfo[i];
                //check whether the pmt can be sent to that Biller or not 
                // three checks: 
                //      1. must exists 
                //      2. if amount is negtive value, guarpayonly must be false
                //      3. accout match mask

                //      1. must exists 

                //Use payeeId to get RPPSBillerInfo
                // try to find the biller infor from cache first
                // We don't need to use FIID because _rppsBiller is member of this handler
                // It is not shared between different instances of RPPSHandler
                RPPSBillerInfo billerInfo = ( RPPSBillerInfo ) _rppsBillers.get( aPmtInfo.PayeeID );

                if ( billerInfo == null ) {



                    billerInfo = RPPSBiller.getRPPSBillerInfoByFIRPPSIdPayeeId(dbh, 
                                                                               _fiRPPSId, 
                                                                               aPmtInfo.PayeeID );

                    // if there is not or its status is not ACTIVE
                    if ( ( billerInfo == null ) 
                         || ( billerInfo.getStatusCode() != DBConsts.SUCCESS ) 
                         || ( DBConsts.ACTIVE.compareToIgnoreCase( billerInfo.getBillerStatus() ) != 0 ) ) {

                        FFSDebug.log( mName + "Failed to process this payment. " 
                                      + RPPSConsts.PAYEE_IS_NOT_RPPS_BILLER 
                                      + ". payment SrvrTID = " + aPmtInfo.SrvrTID
                                      + ", payee id = " + aPmtInfo.PayeeID , FFSDebug.PRINT_ERR );
                        // can not send this pmt to that payee
                        this.processFailedPmt(dbh, 
                                              aPmtInfo, 
                                              DBConsts.STATUS_DEST_ACCOUNT_NOT_FOUND, 
                                              RPPSConsts.PAYEE_IS_NOT_RPPS_BILLER );
                        continue;
                    } else {

                        _rppsBillers.put( aPmtInfo.PayeeID, billerInfo );
                    }
                }

                //      2. if amount is negtive value, guarpayonly must be false
                // Biller will not accept negtive value if the gurantee pay is true
                if ( BPWUtil.isNegative(aPmtInfo.getAmt()) && ( billerInfo.isGuarPayOnly() == true ) ) {
                    // Biller will not accept negtive value if the gurantee pay is true
                    FFSDebug.log( mName + "Failed to process this payment. " 
                                  + RPPSConsts.BILLER_GUARPAYONLY 
                                  + ". payment SrvrTID = " + aPmtInfo.SrvrTID
                                  + ", payment amount = " + aPmtInfo.getAmt()
                                  + ", payee id = " + aPmtInfo.PayeeID , FFSDebug.PRINT_ERR );

                    // can not send this pmt to that payee
                    this.processFailedPmt(dbh, 
                                          aPmtInfo, 
                                          DBConsts.STATUS_INVALID_AMOUNT, 
                                          RPPSConsts.BILLER_GUARPAYONLY);
                    continue;
                }

                // else

                //      3. accout match mask
                // invoke checkPayeeEditMask(FFSConnectionHodler dbh, String payeeID, String acctNum) 
                // to check payAcct is valid or not
                if (!PayeeEditMask.checkPayeeEditMask(dbh, 
                                                      aPmtInfo.PayeeID, 
                                                      aPmtInfo.PayAcct)) {
                    FFSDebug.log( mName + "Failed to process this payment. " 
                                  + RPPSConsts.INVALID_PAYACCOUNT 
                                  + ". payment SrvrTID = " + aPmtInfo.SrvrTID
                                  + ", pay account = " + aPmtInfo.PayAcct
                                  + ", payee id = " + aPmtInfo.PayeeID , FFSDebug.PRINT_ERR );

                    // can not send this pmt to that payee
                    this.processFailedPmt(dbh, 
                                          aPmtInfo, 
                                          DBConsts.STATUS_INVALID_ACCOUNT, 
                                          RPPSConsts.INVALID_PAYACCOUNT);
                    continue;
                }




                // create RPPSRecordInfo object 
                recordInfo = new RPPSRecordInfo();
                recordInfo.setSrvrTId(aPmtInfo.SrvrTID);
                recordInfo.setFiId(aPmtInfo.FIID);
                recordInfo.setPayeeId(aPmtInfo.PayeeID);

                // if amount is minus, set pmtType to be debit
                // set tx. code accordingly
                txCode = BPWUtil.isNegative(aPmtInfo.getAmt()) ? TX_CODE_DEBIT : TX_CODE_CREDIT;
                recordInfo.setTxCode(txCode);

                // entry id is going to be generated and set by RPPSDB
                // We can not use trace num here
                // because trace num must be ascend we have to 
                // generate when putting them into a file

                // save the record string to RPPS_EntryTmp table
                RPPSDB.addRPPSEntryTmp(dbh, recordInfo);

            } // end for

            // Caller (ScheduleRunnable) will commit for me.

        } catch (Exception e) {
            String errorMsg = mName + " Failed. Error:" + e.toString();
            FFSDebug.log( errorMsg, FFSConst.PRINT_ERR );
            PerfLoggerUtil.stopPerfLogging(mName, start, uniqueIndex);
            throw new FFSException( e, errorMsg );
        }

        FFSDebug.log( mName + "done.", FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(mName, start, uniqueIndex);
    }



    /**
     * Ends handling payment related requests. This is the end of a new
     * fulfillment cycle i.e. the last method to be called when a payment
     * schedule is executed.
     * 
     * NB: Do NOT modify the object dbh, or commit the changes with dbh.conn
     * in ANY part of the FulfillmentAPI implementation. This could cause
     * unexpected results and behaviors with the entire BPW system.
     * 
     * !!! For FFI developers only:  We are violating this rule. !!!
     * 
     * dbh is committed in this method. It is safe so far because we know that
     * ScheduleRunnable does not pass open TX to here in this call ( SEQUENCE_FIRST)
     * However, we don't want to share this information with our customers. So we can
     * keep the right to pass open TX from ScheduleRunnable if we have to in the future.
     *
     * The reason we want to commit is to avoid big TX when removing temp entry records
     * The other choice is that we create a db conneciton. However, this approach will
     * increase the possiblity of deadlocking
     * 
     * @param dbh    - Database connection holder
     * @param fiId
     * @param extra  - Holds extra information which FulfillAgent wants
     *               to pass to Fulfillment. It is empty for now.
     * @exception Exception
     */
    public void endPmtBatch(FFSConnectionHolder dbh, String fiId, HashMap extra) 
    throws FFSException
    {

        // check started
        String mName = "RPPHandler.endPmtBatch: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(mName,start);
        
        if ( _started == false ) {
        	PerfLoggerUtil.stopPerfLogging(mName, start, uniqueIndex);
            throw new FFSException( mName + "RPPS connector is not started!" );
        }

        FFSDebug.log( mName + "start...", FFSDebug.PRINT_DEV );

        // fiId must be same as the fi id passed through startPmtBatch
        if ( _fiId.compareTo( fiId ) != 0 ) {
        	PerfLoggerUtil.stopPerfLogging(mName, start, uniqueIndex);
            throw new FFSException( mName + "FI id is not same as the FI id passed through startPmtBatch method! " + fiId + "-" + _fiId );
        }


        // prepare export file
        //  1. compose export file name
        //  2. write header record
        this.prepareRPPSExportFile();

        // log msg. if total credit exceeds this FI's credit cap

        // create a record in RPPS_PmtFileMap table

        // set field
        _rppsPmtFileInfo.setFiId( _fiId );
        _rppsPmtFileInfo.setConfirmed( "N" );
        _rppsPmtFileInfo.setCompleted( "N" );


        // we have to add this record first 
        // and then update it later
        // because this table is PmtEntryMap's parent table 

        // file id is generated 
        RPPSDB.addRPPSPmtFileMap(dbh, _rppsPmtFileInfo);

        // go through each payee in _rppsBillers HashMap 

        Iterator payeeIT = _rppsBillers.keySet().iterator();


        // process payee one by one
        while ( payeeIT.hasNext() ) {

            String payeeId = (String) payeeIT.next();

            // process one payee -- two batches, one for credit, one for debit

            processOnePayee( dbh, payeeId ); 


        }

        // create file control
        String msgStr = this.createFileControl();

        // write into export file
        ACHAdapterUtil.writeRecordContents( _tempExportFileName,
                                            msgStr,
                                            true); // append

        // Update the record in RPPS_PmtFileMap table


        // log msg. if total credit exceeds this FI's credit cap
        if (_totalCredit.compareTo(new BigDecimal(_rppsFIInfo.getCreditCapLong())) > 0) {
            FFSDebug.log( mName + "Total credit of this payment file exceeds bank's credit cap! "  
                          + "Payment file name: " + _tempExportFileName 
                          + "Total credit: " + _totalCredit 
                          + "Credit cap: " + _rppsFIInfo.getCreditCap(), FFSDebug.PRINT_ERR );
        }

        _rppsPmtFileInfo.setTotalCredit(_totalCredit.toString());
        _rppsPmtFileInfo.setTotalDebit(_totalDebit.toString());
        _rppsPmtFileInfo.setTotalEntryCount( this._totalEntryCount );
        _rppsPmtFileInfo.setCompleted( "Y" );

        RPPSDB.updateRPPSPmtFileMap(dbh, _rppsPmtFileInfo);

        // clean up
        RPPSDB.removeAllRPPSEntryTmps(dbh,fiId);

        // move file from temp folder to export folder
        moveFileToExport(dbh);

        FFSDebug.log( mName + "done.", FFSDebug.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(mName, start, uniqueIndex);
    }

    /**
     * Process one payee. Save all the pmts to this 
     * payee to two batches, one for credit, one for debit
     * 
     * @param dbh
     * @param payeeId
     */
    private void processOnePayee( FFSConnectionHolder dbh, String payeeId )
    throws FFSException
    {

        processOneBatch( dbh, payeeId, RPPSConsts.TX_CODE_CREDIT );
        processOneBatch( dbh, payeeId, RPPSConsts.TX_CODE_DEBIT );

    }


    /**
     * Process one batch. One biller with specified TX code.
     * 
     * @param dbh
     * @param payeeId
     * @param txCode
     */
    private void processOneBatch( FFSConnectionHolder dbh, String payeeId, int txCode )
    throws FFSException
    {
        String mName = "RPPSHandler.processOneBatch: ";
        int totalEntryCount = 0;
        
        BigDecimal totalDebit = BPWUtil.getBigDecimal("0", 0);
        BigDecimal totalCredit = BPWUtil.getBigDecimal("0", 0);
        
        int batchNum = 0;
        int traceNum = 0;
        int serviceCode = 0;
        int startEntryId = 0; // from where read a chunk of EntryTmp records

        FFSDebug.log( mName + "start...", FFSDebug.PRINT_DEV );
        // read records from EntryTmp table chunk by chunk
        RPPSRecordInfo[] recordsInfo = null;  // temp. entries in DB

        try {
            // read first chunk
            // start entry id
            recordsInfo = RPPSDB.getRPPSEntryTmps(dbh, _fiId, payeeId, txCode, 0);        

            if ( ( recordsInfo == null ) || ( recordsInfo.length == 0 ) ) {
                return; // there is not any pmt for this payee id with this txCode 
            }

            RPPSBillerInfo billerInfo = ( RPPSBillerInfo )_rppsBillers.get( payeeId );
            if ( billerInfo == null ) { // sainity check, should not be null

                FFSDebug.log( mName + "Can not find the biller from cache!", FFSDebug.PRINT_ERR );
                return;
            }

            // generate batch num
            // Don't pass dbh, this method will do commit
            batchNum = DBUtil.getNextIndex(DBConsts.RPPS_BATCH_NUM, 
                                           DBConsts.RPPS_BATCH_NUM_DIGITS );

            if ( txCode == RPPSConsts.TX_CODE_CREDIT ) {
                serviceCode = ACHConsts.ACH_CREDITS_ONLY;
            } else {
                serviceCode = ACHConsts.ACH_DEBITS_ONLY;
            }

            // create and write batch header
            String msgStr = this.createBatchHeader( billerInfo, serviceCode, batchNum );
            // create batch header for payeeId

            // write into export file
            ACHAdapterUtil.writeRecordContents( _tempExportFileName,
                                                msgStr,
                                                true); // append

            while ( ( recordsInfo != null ) && ( recordsInfo.length != 0 ) ) {
                for (int i = 0; i < recordsInfo.length; i++) {
                    // go through each payment
                    // create EntryDetail object and write into the file

                    // get detailed pmt infor


                    PmtInfo pmtInfo = PmtInstruction.getPmtInfo( recordsInfo[i].getSrvrTId(), dbh );

                    // Sainity check. 
                    // Suppose no one would / could touch this pmt when the schedule is 
                    // running
                    if ( ( pmtInfo == null ) || ( DBConsts.CANCELEDON.equals(pmtInfo.Status) ) ) {
                        FFSDebug.log( mName 
                                      + "Can not find the payment information from db! SrvrTID: " 
                                      + recordsInfo[i].getSrvrTId(), 
                                      FFSDebug.PRINT_ERR );
                        continue; // skip this pmt
                    }

                    // create entry record according this pmt information
                    CustomerInfo customerInfo = Customer.getCustomerByID( pmtInfo.CustomerID,dbh );
                    if ( ( customerInfo == null ) || ( DBConsts.CANCELEDON.equals(customerInfo.status) ) ) {
                        FFSDebug.log( mName 
                                      + "Can not find the customer information for payment information from db! SrvrTID: " 
                                      + recordsInfo[i].getSrvrTId(), 
                                      FFSDebug.PRINT_ERR );

                        // fail this pmt
                        this.processFailedPmt(dbh, 
                                              pmtInfo, 
                                              DBConsts.STATUS_SOURCE_ACCOUNT_NOT_FOUND, 
                                              RPPSConsts.INVALID_CUSTOMER );
                        continue; 
                    }

                    String consumerName = customerInfo.firstName + " " +  customerInfo.lastName;

                    // Generate trace num here

                    traceNum =  DBUtil.getNextIndex( DBConsts.RPPS_TRACE_NUM, RPPSConsts.RPPS_RECORD_TRACENUM_DIGITS );

                    msgStr = createEntryDetailRecord( traceNum,
                                                      txCode,
                                                      pmtInfo, 
                                                      consumerName );
                    // write it into the export file

                    // write into export file
                    ACHAdapterUtil.writeRecordContents( _tempExportFileName,
                                                        msgStr,
                                                        true); // append

                    // update start date, copied from CheckFreeHandler
                    int startdate = DBUtil.getCurrentStartDate() + pmtInfo.StartDate % 100;
                    PmtInstruction.updateStartDate(dbh, recordsInfo[i].getSrvrTId(), startdate);


                    // create RPPSPmtInfo for record/pmt mapping
                    createPmtEntryMap( dbh, traceNum, pmtInfo, batchNum, consumerName );

                    // update totalCredit and total debit, entry count
                    totalEntryCount ++;
                    BigDecimal amt = FFSUtil.setScale(new BigDecimal(pmtInfo.getAmt()).movePointRight(2),0);
                    		
                    if ( txCode == RPPSConsts.TX_CODE_CREDIT ) {                    	
                    	totalCredit = totalCredit.add(amt);                         
                    } else {
                        // the amount could -10
                        // - - 10 is + 10
                        totalDebit = totalDebit.subtract(amt);
                    }

                }

                if ( totalEntryCount > _batchSize ) {
                    // !!!! We have to commit in order to avoid dd
                    dbh.conn.commit();

                }
                // get the last trace num 
                // we know recordsInfo is not null here
                startEntryId = recordsInfo[ recordsInfo.length - 1 ].getEntryId () + 1;
                // read another chunck
                recordsInfo = RPPSDB.getRPPSEntryTmps(dbh, _fiId, payeeId, txCode, startEntryId);        

            } // process all the pmts for this payeeid with this TXCode


            // create and write batch control
            msgStr = createBatchControl( billerInfo, serviceCode, totalEntryCount, 
            	totalDebit.longValue(), totalCredit.longValue(), batchNum);


            ACHAdapterUtil.writeRecordContents( _tempExportFileName,
                                                msgStr,
                                                true); // append


            // update file total 
            _batchCount ++;
            _totalCredit = _totalCredit.add(totalCredit);
            _totalDebit = _totalDebit.add(totalDebit);
            _totalEntryCount += totalEntryCount;

            // !!!! We have to commit in order to avoid dd
            dbh.conn.commit();

        } catch (Exception e) {
            FFSDebug.log( mName + "error: " + e.getMessage(), FFSDebug.PRINT_DEV );
            throw new FFSException(e.getMessage());        
        }

        FFSDebug.log( mName + "done.", FFSDebug.PRINT_DEV );
    }
    /**
     * For the failed pmts, create FAILEDON results and pass
     * them to BackendProcessor which mark these pmts as
     * FAILEDON and create revert fund schedules.
     * 
     * @param dbh
     * @param failedPmt
     * @param errorCode
     * @exception ResponseRecordException
     */
    private void processFailedPmt( FFSConnectionHolder dbh, 
                                   PmtInfo failedPmt, 
                                   int errorCode, 
                                   String errorMsg )
    throws FFSException
    {
        try {

            // init to successful state
            PmtTrnRslt rslt = new PmtTrnRslt( failedPmt.CustomerID,
                                              failedPmt.SrvrTID,
                                              errorCode,
                                              errorMsg,
                                              failedPmt.ExtdPmtInfo );
            rslt.logID = failedPmt.LogID;

            // add payment failed
            // rslt.status  = DBConsts.STATUS_DEST_ACCOUNT_NOT_FOUND;

            // update the database
            // revert funds
            _backendProcessor.processOneFailedPmt(rslt, 
                                                  failedPmt.LogID, 
                                                  failedPmt.FIID, 
                                                  dbh);

        } catch ( Exception e ) {
            throw new FFSException( "Failed to process failed pmt: " + failedPmt.SrvrTID + " error: " + FFSDebug.stackTrace( e ) );
        }
    }

    /**
     * Get export full file name
     * Check whether this file exist or not, if exists, move it error folder
     * 
     * @param rppsFIInfo
     * @exception FFSException
     */
    private void prepareRPPSExportFile()
    throws FFSException
    {

        //Fomular: ODFIID.CRATION_DATE.CREATION_TIME.<A-Z,0-9).ACH
        String[] datetime = ACHAdapterUtil.generateFileCreationDateTime();
        String fileCreationDate = datetime[0];
        String fileCreationTime = datetime[1];

        // Compose partial file name without modifier
        String partialExportFileName = _rppsFIInfo.getFiRPPSId() + STR_RPPS_FILE_SEPARATOR +
                                       fileCreationDate + STR_RPPS_FILE_SEPARATOR +
                                       fileCreationTime + STR_RPPS_FILE_SEPARATOR;
        // 4. Find next available modifer A-Z 0-9
        // Learn from CheckFree 
        String modifier = ACHAdapterUtil.getNextModifier( _tempExportFileBase, partialExportFileName ); 

        // Compose file name, without folder names
        String fileName = partialExportFileName + modifier;

        // Full file name
        _tempExportFileName = _tempExportFileBase + fileName + STR_EXPORT_FILE_SUFFIX;

        // check whether this file exists or not
        File tempExportFile = new File( _tempExportFileName );
        tempExportFile.setFileHandlerProvider(RPPSUtil.getFileHandlerProvider());

        tempExportFile.deleteOnExit();

        // Write File Header into this file.


        // build record string
        String headerRecordStr = this.createFileHeader( fileCreationDate, 
                                                        fileCreationTime,
                                                        modifier );


        // write into export file
        // Write the built header into file.
        ACHAdapterUtil.writeRecordContents( _tempExportFileName, headerRecordStr, true);


        // set this file information to rppsPmtFileInfo object

        _rppsPmtFileInfo.setFileIdModifier( modifier );
        _rppsPmtFileInfo.setTransDate( fileCreationDate );
        _rppsPmtFileInfo.setTransTime( fileCreationTime );
        _rppsPmtFileInfo.setFileName( fileName + STR_EXPORT_FILE_SUFFIX );
    }


    /**
     * Create FileHeader object and build into string.
     * 
     * @param fileCreationDate
     * @param fileCreationTime
     * @param modifier
     * @return 
     */
    private String createFileHeader( String fileCreationDate, 
                                     String fileCreationTime, 
                                     String modifier )
    throws FFSException
    {
        // Create FileHeader object

        TypeFileHeaderRecord msgObject = new TypeFileHeaderRecord();

        msgObject.Record_Type_Code = (short) ACHConsts.FILE_HEADER;
        msgObject.Priority_Code = ACHConsts.DEFAULT_PRIORITY_CODE;

        // MB does not pad space at the beginning 
        // work around is we pad it manually for now
        msgObject.Immediate_Destination = " " + _rppsFIInfo.getRppsId(); // imm. destination

        // check digist
        msgObject.Immediate_Origin = " " + _rppsFIInfo.getFiRPPSId() +
                                     BPWUtil.calculateCheckDigit( _rppsFIInfo.getFiRPPSId() ) ; // imm. origin
        msgObject.Transmission_Date = fileCreationDate; // transmission date
        msgObject.Transmission_Time = fileCreationTime; // transmission Time
        msgObject.File_Identification_Modifier = modifier; // file id modifier
        msgObject.Record_Size = ACHConsts.ACH_RECORD_LEN; // record size
        msgObject.Blocking_Factor = ACHConsts.DEFAULT_BLOCKING_FACTOR;
        msgObject.Format_Code = ACHConsts.DEFAULT_FORMAT_CODE;
        msgObject.Destination = substring( _rppsFIInfo.getRppsName(), RPPSConsts.RPPS_NAME_LENGTH ); // destination
        msgObject.Origin = substring( _rppsFIInfo.getFiRPPSName(), RPPSConsts.FI_RPPS_NAME_LENGTH ); // origin
        msgObject.Reference_Code = RPPSConsts.DEFAULT_REFERENCE_CODE; // reference code

        // build record string
        return ACHAgent.buildPureRecord(MB_RPPS_SET_NAME, MB_RPPS_TYPE_FILE_HEADER, msgObject); 
    }
    /**
     * Create BatchHeaderRecord and build into String
     * 
     * Record_Type_Code ACHConsts.BATCH_HEADER
     * Service_Class_Code ACHConsts.ACH_CREDITS_ONLY, ACH_DEBITS_ONLY     220/225
     * Biller_Name RPPSBillerInfo.billerName       RPPS_Biller.BillerNae
     * Reserved20   BLANK
     * Biller_Identification_Number RPPSBillerInfo.billerRPPSId
     * Standard_Entry_Class_Code ACHConsts.ACH_CIE_STR   CIE
     * Entry_Description RPPSConsts.RPPSPAYMENT/REVERAL   RPPSPAYMENT/REVERSAL
     * Biller_Descriptive_Date  ACHAdapterUtil
     * Biller_Descriptive_DateExists   TRUE
     * Transmission_Date Current Date    YYMMDD  ACHAdapterUtil.java line 603-614
     * Error_Code BLANK
     * Originator_Status_Code RPPSConsts.ORIGNIATOR_STATUS_CODE        "1"
     * RPPS_Identification_Number RPPSBillerInfo.fiRPPSId RPPS_Biller.FIRPPSID
     * Batch_Number getNextIndex
     * 
     * @param billerInfo
     * @param serviceCode
     * @return 
     * @exception Exception
     */
    private String createBatchHeader( RPPSBillerInfo billerInfo,
                                      int serviceCode,
                                      int batchNum  )
    throws FFSException 
    {

        // 2. Generate CREATION_DATE
        SimpleDateFormat formatter = new SimpleDateFormat("yyMMdd");

        String strDate = formatter.format(new Date());


        String entryDescription;

        if ( serviceCode == ACHConsts.ACH_DEBITS_ONLY ) {
            entryDescription = RPPSConsts.ENTRY_DESCRIPTION_REVERSAL;
        } else {
            entryDescription = RPPSConsts.ENTRY_DESCRIPTION_RPPSPAYMENT;
        }

        TypeBatchHeaderRecord batchHeader = new TypeBatchHeaderRecord();

        batchHeader.Record_Type_Code = ACHConsts.BATCH_HEADER;
        batchHeader.Service_Class_Code = (short) serviceCode;
        batchHeader.Biller_Name = substring(billerInfo.getBillerName(), RPPSConsts.RPPS_BILLER_NAME_LENGTH ); // java.lang.String Biller_Name,
        batchHeader.Reserved20 = RPPSConsts.DEFAULT_RESERVED20; // java.lang.String Reserved20,
        batchHeader.Biller_Identification_Number = billerInfo.getBillerRPPSId(); //java.lang.String Biller_Identification_Number,
        batchHeader.Standard_Entry_Class_Code = ACHConsts.ACH_CIE_STR; // java.lang.String Standard_Entry_Class_Code,
        batchHeader.Entry_Description = entryDescription; // java.lang.String Entry_Description,
        batchHeader.Biller_Descriptive_Date = strDate; // java.lang.String Biller_Descriptive_Date,
        batchHeader.Biller_Descriptive_DateExists = true;
        batchHeader.Transmission_Date = strDate; // java.lang.String Transmission_Date,
        batchHeader.Error_Code = RPPSConsts.DEFAULT_RESERVED3; // java.lang.String Error_Code,
        batchHeader.Originator_Status_Code = RPPSConsts.DEFAULT_ORIGNIATOR_STATUS_CODE; //java.lang.String Originator_Status_Code,
        batchHeader.RPPS_Identification_Number = billerInfo.getFiRPPSId(); //java.lang.String RPPS_Identification_Number,
        batchHeader.Batch_Number = batchNum;

        return ACHAgent.buildPureRecord(MB_RPPS_SET_NAME, MB_RPPS_TYPE_BATCH_HEADER, batchHeader); 

    }
    /**
     *   /**
     * Create Entry Detail Record and build it into String
     * 
     * Record_Type_Code  ACHConsts.ENTRY_DETAIL
     * Transaction_Code  RPPSConsts.TX_CODE_CREDIT/DEBIT, PRENOTE        TXCode 22/27:Credit/Debit
     * Reserved26      BLANKS
     * Amount  PmtInfo.Amount          Convert to $$$$$$$$cc format
     * Consumer_Name   CustomerInfo.firstName lastName         Customer.getCustomerByID(PmtInfo.custmerId)
     * Consumer_Account_Number PmtInfo.PayAccount Match account against consumer account Format in the RPPS Biller Directory
     * Reserved2       BLANKS
     * Addendum_Record_Indicator   RPPSConsts.ADDENDA_INDICATOR_NO_ADDENDA  0
     * Trace_Number    EntryDetailRecord sequence
     * 
     * @param recordInfo
     * @param pmtInfo
     * @param consumerName
     * @return 
     */
    private String createEntryDetailRecord( int traceNum,
                                            int txCode,
                                            PmtInfo pmtInfo, 
                                            String consumerName )
    throws FFSException
    {
    	BigDecimal amt = new BigDecimal(pmtInfo.getAmt()).movePointRight(2).setScale(0).abs();
    	
        TypeCSPPmtEntryDetailRecord entryRecord = new TypeCSPPmtEntryDetailRecord();

        entryRecord.Record_Type_Code = ACHConsts.ENTRY_DETAIL; // short Record_Type_Code,
        entryRecord.Transaction_Code =( short ) txCode; // short Transaction_Code,
        entryRecord.Reserved26 = RPPSConsts.DEFAULT_RESERVED26; // java.lang.String Reserved26,
        entryRecord.Amount = amt.longValue();
        entryRecord.Consumer_Name = substring(consumerName, RPPSConsts.RPPS_CONSUMER_NAME_LENGTH);
        entryRecord.Consumer_Account_Number = pmtInfo.PayAcct; // java.lang.String Consumer_Account_Number,
        entryRecord.Reserved2 = RPPSConsts.DEFAULT_RESERVED2; // java.lang.String Reserved2,
        entryRecord.Addendum_Record_Indicator = RPPSConsts.ADDENA_INDICATOR_NO_ADDENDA; //short Addendum_Record_Indicator,

        // _fiRPPSId + 00000006
        // trace number from 6 to "0000006"

        // change to 10000006 first
        String traceNumStr = ( new Long ( 10000000 + traceNum ) ).toString(); 
        traceNumStr = traceNumStr.substring( 1 ); // remove "1"
        // add _fiRPPSId
        traceNumStr = _fiRPPSId + traceNumStr;

        // sanity check, just in case the length exceeds 15
        entryRecord.Trace_Number = traceNumStr.substring( 0, RPPSConsts.TRACE_NUM_LENGTH ); // remove "1"

        return ACHAgent.buildPureRecord(MB_RPPS_SET_NAME, MB_RPPS_TYPE_ENTRY_DETAIL, entryRecord); 
    }


    /**
     *  Creates a batch control record from a payee id.
     *  Batch Control Record
     * 
     * Record_Type_Code ACHConsts.BATCH_CONTROL
     * Service_Class_Code ACHConsts.ACH_CREDITS_ONLY, ACH_DEBITS_ONLY     220/225
     * Entry_Addenda_Count6  Number of Entry and addenda this batch includes
     * Entry_Hash      BLANKS
     * Total_Debits    Total debits
     * Total_Credits   Total credits
     * Biller_Identification_Number    RPPSBillerInfo.billerRPPSId
     * Reserved25      BLANKS
     * RPPS_Identification_Number     RPPSBillerInfo.fiRPPSId
     * Batch_Number   Sequence value from database
     * 
     * @param rppsBillerInfo
     * @param entryAddendaCount
     * @param totalDebit
     * @param totalCredit
     * @return 
     * @exception Exception
     */
    private String createBatchControl( RPPSBillerInfo rppsBillerInfo,
                                       int serviceClassCode,
                                       int entryAddendaCount,
                                       long totalDebit,
                                       long totalCredit,
                                       int batchNum) 
    throws FFSException
    {

        // set up MB record
        TypeBatchControlRecord msgObject = new TypeBatchControlRecord();

        msgObject.Record_Type_Code = ACHConsts.BATCH_CONTROL;
        msgObject.Service_Class_Code = (short)serviceClassCode; 
        msgObject.Entry_Addenda_Count6 = entryAddendaCount;
        msgObject.Entry_Hash = RPPSConsts.DEFAULT_ENTRY_HASH;
        msgObject.Total_Debits = totalDebit;
        msgObject.Total_Credits = totalCredit;
        msgObject.Biller_Identification_Number = rppsBillerInfo.getBillerRPPSId();
        msgObject.Reserved25 = RPPSConsts.DEFAULT_RESERVED25;
        msgObject.RPPS_Identification_Number = rppsBillerInfo.getFiRPPSId();
        msgObject.Batch_Number = batchNum; 

        // build record string
        return ACHAgent.buildPureRecord(MB_RPPS_SET_NAME, MB_RPPS_TYPE_BATCH_CONTROL, msgObject); 
    }      


    /**
     *  Create File Control object and build into string
     * 
     * File Control Record
     * 
     * Record_Type_Code    short   ACHConsts.FILE_CONTROL
     * Batch_Count int Total number forbatches
     * Block_Count String  BLANKS
     * Entry_Addenda_Count8    long    Number of Entry and addenda this file includes
     * Entry_Hash  String  BLANKS
     * Total_Debits    long    Total debits
     * Total_Credits   long    Total credits
     * Reserved39  String  BLANKS
     * 
     *  @return
     * @return 
     */
    private String createFileControl()
    throws FFSException
    {
        // Create FileControl object

        TypeFileControlRecord msgObject = new TypeFileControlRecord();

        msgObject.Record_Type_Code = (short) ACHConsts.FILE_CONTROL;
        msgObject.Batch_Count = _batchCount;
        msgObject.Block_Count = RPPSConsts.DEFAULT_BLOCK_COUNT;
        msgObject.Entry_Addenda_Count8 = _totalEntryCount;
        msgObject.Entry_Hash = RPPSConsts.DEFAULT_ENTRY_HASH;
        msgObject.Total_Debits = _totalDebit.longValue();
        msgObject.Total_Credits = _totalCredit.longValue();
        msgObject.Reserved39 = RPPSConsts.DEFAULT_RESERVED26;

        // build record string
        return ACHAgent.buildPureRecord(MB_RPPS_SET_NAME, MB_RPPS_TYPE_FILE_CONTROL, msgObject); 

    }
    /**
     * Create a record in PmtEntryMap table for this pmt info. This record is used 
     * to map back to PmtInfo when processing response file
     *  1. Check whether this is already a record for this pmt, if there is check file id
     *      and  get fileName and move this is file to error foler
     *      and then update the file id with current file id
     *  2. If not exist, insert one
     * @param recordInfo
     * @param pmtInfo
     * @param batchNum
     */
    private void createPmtEntryMap( FFSConnectionHolder dbh, 
                                    int traceNum,
                                    PmtInfo pmtInfo, 
                                    int batchNum,
                                    String consumerName )
    throws FFSException
    {

        // create RPPSPmtInfo for record/pmt mapping
        RPPSPmtInfo newRppsPmtInfo = new RPPSPmtInfo();

        newRppsPmtInfo.setTraceNum( traceNum );
        newRppsPmtInfo.setBatchNum(batchNum);
        newRppsPmtInfo.setPayAccount(pmtInfo.PayAcct);
        newRppsPmtInfo.setSrvrTId(pmtInfo.SrvrTID);
        newRppsPmtInfo.setFileId( _rppsPmtFileInfo.getFileId() );
        newRppsPmtInfo.setConsumerName(consumerName);


        // try to get RPPSPmtInfo first 
        RPPSPmtInfo oldRppsPmtInfo = RPPSDB.getRPPSPmtEntryMap( dbh, pmtInfo.SrvrTID );
        if ( ( oldRppsPmtInfo != null ) && ( oldRppsPmtInfo.getStatusCode() == DBConsts.SUCCESS) ) {
            // This pmt has been processed before
            // check which file this pmt has been put into
            RPPSPmtFileInfo rppsPmtFileInfo = RPPSDB.getRPPSPmtFileMapByFileId( dbh, oldRppsPmtInfo.getFileId() );
            if ( ( rppsPmtFileInfo != null ) 
                 && ( rppsPmtFileInfo.getStatusCode() == DBConsts.SUCCESS) ) {

                // get file name, move this file to error folder
                this.checkPreviousExportFile( rppsPmtFileInfo.getFileName(), pmtInfo.SrvrTID ); 

            }

            // update this rpps pmt record with new fileId and submit date
            RPPSDB.updateRPPSPmtEntryMap( dbh, newRppsPmtInfo );

        } else {

            // save this object into RPPS_PmtEntryMap table
            RPPSDB.addRPPSPmtEntryMap(dbh, newRppsPmtInfo);
        }

    }

    /**
     * Check the whether this payment has been processed or not
     * if yes, move this file to error folder
     * 
     * @param oldFileName
     * @exception FFSException
     */
    private void checkPreviousExportFile( String oldFileName, String srvrTId ) 
    throws FFSException
    {
        String mName = "RPPSHandler.checkPreviousExportFile: ";

        if ( ( oldFileName != null ) && ( oldFileName.length() != 0 ) ) {

            // This pmt has been processed before, move this file
            // to error folder

            // check whether this file exists or not
            File oldExportFile = new File( _exportFileBase + oldFileName );
            oldExportFile.setFileHandlerProvider(RPPSUtil.getFileHandlerProvider());
            if ( oldExportFile.exists() ) {
                FFSDebug.log( mName + "this payment has been exported to RPPS File: " 
                              + oldFileName + ". payment id: " + srvrTId, FFSDebug.PRINT_WRN );

                // Move this file to error, and add System.getCurrentMis to the end of this file

                String fullErrorFileName = _errorFileBase
                                           + oldFileName
                                           + RPPSConsts.STR_RPPS_FILE_SEPARATOR
                                           + String.valueOf( System.currentTimeMillis() ) ;

                File errorFile = new File( fullErrorFileName );
                errorFile.setFileHandlerProvider(RPPSUtil.getFileHandlerProvider());
                oldExportFile.renameTo( errorFile );

                FFSDebug.log( mName + "the existing file has been moved to  " + fullErrorFileName, FFSDebug.PRINT_WRN );
            }
        }

    }

    /**
   * Copy files in temp dir to export dir
   * 
   * @param tempFileNames contains file names in temp dir
   * @exception Exception
   */
    private void moveFileToExport( FFSConnectionHolder dbh ) throws FFSException
    {        
        try {
            // copy it to the file in export dir
            // get file name in export directory
            File tempFile = new File( _tempExportFileName );
            tempFile.setFileHandlerProvider(RPPSUtil.getFileHandlerProvider());

            File exportFile = new File( _exportFileBase + tempFile.getName() );
            exportFile.setFileHandlerProvider(RPPSUtil.getFileHandlerProvider());

            // if exportFile exists, move it to folder error
            if (exportFile.exists()) {

                // export file exists, move it to folder error
                FFSDebug.log( "Export file exists " + exportFile.getCanonicalPath(), FFSDebug.PRINT_ERR );

                // Move this file to error, and add System.getCurrentMis to the end of this file
                String fullErrorFileName = _errorFileBase
                                           + exportFile.getName()
                                           + ACHAdapterConsts.STR_ACH_FILE_SEPARATOR
                                           + String.valueOf( System.currentTimeMillis() ) ;

                File errorFile = new File( fullErrorFileName );
                errorFile.setFileHandlerProvider(RPPSUtil.getFileHandlerProvider());

                exportFile.renameTo( errorFile );

                // create a new one
                exportFile = new File( _exportFileBase + tempFile.getName() );
                exportFile.setFileHandlerProvider(RPPSUtil.getFileHandlerProvider());
                FFSDebug.log( "The existing export file has been moved to  " + errorFile.getCanonicalPath(), FFSDebug.PRINT_ERR );                        
            }

            // move file from tempFile to exportFile
            tempFile.renameTo( exportFile );

            // Log to File Monitor Log
            FMLogAgent.writeToFMLog(dbh,
                                    DBConsts.BPW_RPPS_FILETYPE_ORIGSENDPMT,
                                    exportFile.getPath(),
                                    DBConsts.BPTW,
                                    DBConsts.RPPS,
                                    FMLogRecord.STATUS_COMPLETE);
        } catch ( Exception e ) {
            throw new FFSException ( "Can not move the file from temp folder to export file: " + e.toString() );
        }
    }    

    /**
     * If the source string's length is bigger than len, 
     * substring to len, otherwise return original.
     * 
     * @param sourceStr
     * @param len
     * @return 
     */
    private String substring( String sourceStr, int len )
    {
        if ( ( sourceStr == null ) || ( sourceStr.length() <= len ) ) {
            return sourceStr;
        } else {
            return sourceStr.substring( 0, len ); // destination
        }
    }


}
