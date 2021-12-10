// Copyright (c) 2002 Financial Fusion, Inc.  All Rights Reserved.

package com.ffusion.ffs.bpw.fulfill.checkfree;


import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import com.ffusion.ffs.bpw.audit.FMLogAgent;
import com.ffusion.ffs.bpw.db.CustPayee;
import com.ffusion.ffs.bpw.db.CustPayeeRoute;
import com.ffusion.ffs.bpw.db.CustRoute;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.db.PmtExtraInfo;
import com.ffusion.ffs.bpw.db.PmtInstruction;
import com.ffusion.ffs.bpw.handler.OSGIUtil;
import com.ffusion.ffs.bpw.interfaces.BPWException;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.CustomerInfo;
import com.ffusion.ffs.bpw.interfaces.CustomerPayeeInfo;
import com.ffusion.ffs.bpw.interfaces.CustomerRouteInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.FulfillmentInfo;
import com.ffusion.ffs.bpw.interfaces.PayeeInfo;
import com.ffusion.ffs.bpw.interfaces.PayeeRouteInfo;
import com.ffusion.ffs.bpw.interfaces.PmtInfo;
import com.ffusion.ffs.bpw.interfaces.PmtInvoice;
import com.ffusion.ffs.bpw.interfaces.PmtTrnRslt;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.handlers.FulfillmentAPI;
import com.ffusion.ffs.bpw.master.BackendProcessor;
import com.ffusion.ffs.bpw.serviceMsg.BPWMsgBroker;
import com.ffusion.ffs.bpw.util.BPWRegistryUtil;
import com.ffusion.ffs.bpw.util.BPWUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.ffusion.ffs.util.FFSUtil;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.EnumAcctPriorityCode;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.EnumBankAcctType;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.EnumDebitAcctType;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.EnumRecordAction_A;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.EnumRecordAction_ACC;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.EnumRecordAction_ACI;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.EnumRecordType;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.EnumSWSpecID;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.EnumServiceCode_BBB;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.EnumServiceType;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.EnumStateCode;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.EnumStatus_ACI;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.EnumStatus_AI;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.EnumSubNameUsage;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.EnumSubType;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.EnumYesNoOpt;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.TypeBankAcctInfo;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.TypeContactInfo;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.TypeInfo;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.TypeInvInfo;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.TypePayeeAcctInfo;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.TypePayeeInfo;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.TypePmtHistory;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.TypePmtInfo;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.TypeSTHeader;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.TypeSTTrailer;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.TypeSettlement;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.TypeSrvcActInfo;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.TypeSrvcTrans;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.TypeSubInfo;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.ValueSetSWSpecID;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.ValueSetStateCode;
import com.ffusion.msgbroker.interfaces.MBException;
import com.ffusion.util.beans.filemonitor.FMLogRecord;
import com.sap.banking.io.beans.File;
import com.sap.banking.io.beans.FileInputStream;
import com.sap.banking.io.beans.FileOutputStream;
import com.sap.banking.io.provider.interfaces.FileHandlerProvider;


///////////////////////////////////////////////////////////////////////////////
// Main class of the CheckFree connector.
///////////////////////////////////////////////////////////////////////////////
public class CheckFreeHandler implements FulfillmentAPI, BPWResource {
	private static Logger logger = LoggerFactory.getLogger(CheckFreeHandler.class);
	
    // Constants
    private static final int MIN_SUBID_LENGTH   = 9;
    private static final int INV_SEQ_NUM_LENGTH = 10;
    private static final int AMOUNT_LENGTH      = 15;
    private static final int RATE_LENGTH        = 5;

    private static final String ZERO_CHAR       = "0";

    //Static variables
    private static BPWMsgBroker _mb         = null;
    private static String _fileID           = null;
    private static String _cachePath = CheckFreeConsts.DEFAULT_CACHE_PATH;
    private static String _exportDir = CheckFreeConsts.DEFAULT_EXPORT_DIR;
    //private static String _importDir = CheckFreeConsts.DEFAULT_IMPORT_DIR;

    // Good funds model or Risk model
    // default to Good Funds model for backward compatibility
    private static boolean _isGoodFundsModel = true;
    private static int _subInfoCount            = 0;
    private static int _bankInfoCount           = 0;
    private static int _payeeInfoCount          = 0;
    private static int _payeeAcctInfoCount      = 0;
    private static int _pmtInfoCount            = 0;
    private static int _pmtInvInfoCount         = 0;
    private static int _stheaderCount           = 1;
    private static int _sttrailerCount          = 1;
    private static long _totalPmtAmount         = 0L;

    // Variables for file splitting purposes
    private static int _currCount           = 0;
    private static ArrayList        _subInfoList    = null;
    private static ArrayList        _bankInfoList   = null;
    private static ArrayList        _payeeInfoList  = null;
    private static ArrayList        _payeeAcctInfoList  = null;
    private static ArrayList        _pmtInfoList    = null;

    // temp file lists used for FulfillmentAPI cycle
    private static ArrayList        _subInfoFiles   = new ArrayList( 4 );
    private static ArrayList        _bankInfoFiles  = new ArrayList( 4 );
    private static ArrayList        _payeeInfoFiles = new ArrayList( 4 );
    private static ArrayList        _payeeAcctInfoFiles = new ArrayList( 4 );
    private static ArrayList        _pmtInfoFiles   = new ArrayList( 4 );

    // variables used to maintain thread-safeness of this code
    private static Object  _mutex                = new Object();
    private static boolean _locked               = false;
    private static String _bankAcctType          = "DDA"; // either DDA or MMA
    private static String _bankRoutingNumStr     = null;    // checkfree.bank.routing.number
    private static int _bankRoutingNum           = 0;       // checkfree.bank.routing.number
    private static String _bankDesktopNum        = "000000"; // checkfree.bank.acct.type
    private static String _defaultTimeZone       = null;
    private static String _dateString            = null;
    private static TypeSrvcTrans _dummySrvcTrans = null;

    private static boolean _debug                = false;

    // get routeID for EnforcePayment option
    private static int _routeID = -1;

    // get properConfig for EnforcePayment option
    private static PropertyConfig _propConfig   = null;

    private static BackendProcessor _backendProcessor   = null;

    // Error code constant for SIS file generation failures.
    private static final String CHECKFREE_ERROR_CODE__INVALID_DATA = "00010";

    // Calendar object that is used for tracking the last used CspPmtID.
    private static Calendar _lastUsedCspPmtIdCal = null;

    //  Flag to indicate processing mode (normal/crash recovery)
    //  Default to normal processing 
    private static boolean _possibleDuplicate;
    
    private FileHandlerProvider fileHandlerProvider;
    
    public void setFileHandlerProvider(FileHandlerProvider fileHandlerProvider) {
		this.fileHandlerProvider = fileHandlerProvider;
		try {
			afterPropertiesSet();
		} catch (Exception e) {
//			e.printStackTrace();
		}
	}
    
    public void afterPropertiesSet() throws Exception {
        // get routeID
        FulfillmentInfo fulfill = BPWRegistryUtil.getFulfillmentInfo( this.getClass() );
        if ( fulfill == null ) {
            throw new Exception( "FulfillmentInfo not found for "
                                 + this.getClass().getName() );
        }
        _routeID = fulfill.RouteID;

        // Create the cache if it does not exist
        File cache = new File( _cachePath );
        cache.setFileHandlerProvider(fileHandlerProvider);
        _propConfig = (PropertyConfig)FFSRegistry.lookup(BPWResource.PROPERTYCONFIG);

        try {
        	logger.debug("afterPropertiesSet Checking if 'cache' folder exists.");
            if ( !cache.exists() ) {
            	logger.debug("afterPropertiesSet Checking if 'cache' folder does not exist. Creating the same.");
                cache.mkdir();
                logger.debug("afterPropertiesSet Checking if 'cache' folder created.");
                FFSDebug.log( "CheckFree cache directory created at "+cache.getAbsolutePath() );
            } else if ( !cache.isDirectory() ) {
            	logger.debug("afterPropertiesSet Checking if 'cache' exists.  However, is not a directory.");
                FFSDebug.log( "CheckFree cache path "+cache.getAbsolutePath()+" is not a directory. " 
                        ,FFSConst.PRINT_ERR);
            } else {
            	logger.debug("afterPropertiesSet Checking if 'cache' exists.  However, is a directory.");
            }
        } catch ( Exception e ) {
            FFSDebug.log( e.toString() );
        }
        
        start();
    }
    
    // Set processing mode (normal/crash recovery)
    public static void setPossibleDuplicate(boolean possibleDuplicate){
        _possibleDuplicate = possibleDuplicate;
    }
    //Get Processing mode (normal/crash recovery)
    public static boolean getPossibleDuplicate(){
        return _possibleDuplicate;
    }
    ///////////////////////////////////////////////////////////////////////////
    // Set lock so the scheduler and file checker do not run at the same time
    ///////////////////////////////////////////////////////////////////////////
    static final void lock()
    {
        synchronized ( _mutex ) {
        	 while ( _locked ) {
                try {
                	 _mutex.wait();
                	} catch (InterruptedException e ) {
                }
            } 
               _locked = true;           
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Release lock so the other one can run
    ///////////////////////////////////////////////////////////////////////////
    static final void unlock()
    {
        synchronized ( _mutex ) {
            _locked = false;
            _mutex.notify();
        }
    }


    private static void getProperties()
    {
        _exportDir = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_EXPORT_DIR,
                                                CheckFreeConsts.DEFAULT_EXPORT_DIR );
        //_importDir = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_IMPORT_DIR,
        //                                        CheckFreeConsts.DEFAULT_IMPORT_DIR);
        _cachePath = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_CACHE_PATH,
                                                CheckFreeConsts.DEFAULT_CACHE_PATH );
        _fileID = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_FILE_ID );

        // Find out if we are running in Good Funds Model or Risk Model
        String trueValue = "true";
        String isGoodFundsModel = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_GOODFUNDS_MODEL,
                                                             trueValue);
        if (isGoodFundsModel != null && trueValue.compareToIgnoreCase(isGoodFundsModel) != 0) {
            _isGoodFundsModel = false;
            FFSDebug.log( "CheckFree: Running in Risk Model",
                          FFSConst.PRINT_DEV );
        } else {
            _isGoodFundsModel = true;
            FFSDebug.log( "CheckFree: Running in Good Funds Model",
                          FFSConst.PRINT_DEV );
        }

        _bankRoutingNumStr = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_BANK_RTN );
        if ( _bankRoutingNumStr==null || _bankRoutingNumStr.length()<=0 ) {
            FFSDebug.log( "CheckFree: error - bank routing number is not set!",
                          FFSConst.PRINT_ERR );
            FFSDebug.log( "Please set the property '"+DBConsts.CHECKFREE_BANK_RTN,
                          FFSConst.PRINT_ERR );
        } else {
            _bankRoutingNum = Integer.parseInt( _bankRoutingNumStr );
        }

        _bankAcctType = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_BANK_ACCT_TYPE, _bankAcctType );
        _bankDesktopNum = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_BANK_ACCT_NUM );
        if ( _bankDesktopNum ==null || _bankDesktopNum .length()<=0 ) {
            FFSDebug.log( "CheckFree: error - bank desktop account number "
                          + "for checkfree payment clearing is not set!",
                          FFSConst.PRINT_ERR );
            FFSDebug.log( "Please set the property '"+DBConsts.CHECKFREE_BANK_ACCT_NUM,
                          FFSConst.PRINT_ERR );
        }

        String s = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_MB_DEBUG );
        if ( s!=null ) _debug=s.equalsIgnoreCase( DBConsts.TRUE );
    }// getProperties()

    public void start()
    throws Exception
    {
        FFSDebug.log("Starting CheckFree connector...");
        init();
        FFSDebug.log("CheckFree connector started");
    }// start()

    public void shutdown()
    throws Exception
    {
        FFSDebug.log("Shutting down CheckFree connector... ");
        _mb=null;
        FFSDebug.log("CheckFree connector shut down ");
    }// shutdown()

    static void init()
    {
        try {
            if ( _backendProcessor == null ) {
                _backendProcessor = new BackendProcessor();
            }
            getProperties();
            createMBInstance();
            CheckFreeUtil.init();
        } catch ( Exception e ) {
            FFSDebug.log( e, "CheckFree handler failed to initialize!");
        }
    }// init()

    private static final void createMBInstance()
    throws Exception
    {
        _mb = (BPWMsgBroker)FFSRegistry.lookup(BPWResource.BPWMSGBROKER);
    }// createMBInstance()


    public void addPayees(PayeeInfo[] payees, FFSConnectionHolder dbh)
    throws Exception
    {
    }


    public void modPayees(PayeeInfo[] payees, FFSConnectionHolder dbh)
    throws Exception
    {
    }

    public void deletePayees(PayeeInfo[] payees, FFSConnectionHolder dbh)
    throws Exception
    {
    }

    public void startPayeeBatch(FFSConnectionHolder dbh)
    throws Exception
    {
    }

    public void endPayeeBatch(FFSConnectionHolder dbh)
    throws Exception
    {
        // Flush any remaining data in the buffers out into temporary files.
        SISGenErrorList builderErrList = flushToTempRqFile();

        // Handle any MB build errors that were encountered.
        processBuildErrors(builderErrList, dbh);
    }



    public void addCustomerPayees( CustomerPayeeInfo[] custPayees,
                                   PayeeInfo[] payees,
                                   FFSConnectionHolder dbh)
    throws Exception
    {
        FFSDebug.log("CheckFree FulfillmentAPI.addCustomerPayee() start: number of customer-payee links="
                     +((custPayees==null) ?0 :custPayees.length) );

        if ( (custPayees == null) ||
             (custPayees.length <= 0) ) {
            return;
        }

        try {
            TypePayeeInfo payeeInfo = null;
            TypePayeeAcctInfo payeeAcctInfo = null;

            // Create a holder for any bad records that we might encounter
            // during our data mapping process.
            SISGenErrorList mappingErrList = new SISGenErrorList();

            int len = custPayees.length;
            for (int i=0; i<len; i++) {

                // PayeeInfo
                try {
                    // Populate the PayeeInfo object.
                    // The RecAction must be set before the rest of the
                    // data fill methods are called so that we can perform
                    // error handling.
                    payeeInfo = new TypePayeeInfo();
                    payeeInfo.RecAction = EnumRecordAction_ACC.add;
                    customerPayeeInfo2PayeeInfo(custPayees[i], payeeInfo);
                    payeeInfo2PayeeInfo(payees[i], payeeInfo);

                    // Place PayeeInfo object into the buffer and
                    // increment our counts.
                    _payeeInfoList.add(payeeInfo);
                    _payeeInfoCount++;
                    _currCount++;
                } catch (Throwable t) {
                    // We had an error while mapping this item.
                    // Populate the fields required for error handling
                    // and add the record to our list of invalid items.
                    payeeInfo.CspID = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_SPONSOR_ID );
                    payeeInfo.SubID = makeSubscriberID(custPayees[i].CustomerID);
                    payeeInfo.PayeeListID = custPayees[i].PayeeListID;

                    SISGenError sisGenError = new SISGenError(payeeInfo, t);
                    mappingErrList.addPayeeInfoError(sisGenError);
                }

                // PayeeAcctInfo
                try {
                    // Populate the PayeeAcctInfo object.
                    // The RecAction must be set before the rest of the
                    // data fill methods are called so that we can perform
                    // error handling.
                    payeeAcctInfo = new TypePayeeAcctInfo();
                    payeeAcctInfo.RecAction = EnumRecordAction_ACC.add;
                    customerPayeeInfo2PayeeAcctInfo(custPayees[i], payeeAcctInfo);

                    // Place PayeeAcctInfo object into the buffer and
                    // increment our counts.
                    _payeeAcctInfoList.add(payeeAcctInfo);
                    _payeeAcctInfoCount++;
                    _currCount++;
                } catch (Throwable t) {
                    // We had an error while mapping this item.
                    // Populate the fields required for error handling
                    // and add the record to our list of invalid items.
                    payeeAcctInfo.CspID = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_SPONSOR_ID );
                    payeeAcctInfo.SubID = makeSubscriberID(custPayees[i].CustomerID);
                    payeeAcctInfo.PayeeListID = custPayees[i].PayeeListID;

                    SISGenError sisGenError = new SISGenError(payeeAcctInfo, t);
                    mappingErrList.addPayeeAcctInfoError(sisGenError);
                }

                if (_currCount >= CheckFreeUtil._maxRecordCount) {
                    // Flush the data buffers out into temporary files.
                    SISGenErrorList builderErrList = flushToTempRqFile();

                    // Handle any MB build errors that were encountered.
                    processBuildErrors(builderErrList, dbh);
                    _currCount = 0;
                }
            }

            // Now handle any mapping errors that were encountered.
            processBuildErrors(mappingErrList,dbh);

        } catch (Exception e) {
            throw e;
        }

        FFSDebug.log("CheckFree FulfillmentAPI.addCustomerPayees() done");
    }


    public void modCustomerPayees( CustomerPayeeInfo[] custPayees,
                                   PayeeInfo[] payees,
                                   FFSConnectionHolder dbh )
    throws Exception
    {
        FFSDebug.log("CheckFree FulfillmentAPI.modCustomerPayees() start: number of customer-payee links="
                     +((custPayees==null) ?0 :custPayees.length) );

        if ( (custPayees == null) ||
             (custPayees.length <= 0) ) {
            return;
        }

        try {
            TypePayeeInfo payeeInfo = null;
            TypePayeeAcctInfo payeeAcctInfo = null;

            // Create a holder for any bad records that we might encounter
            // during our data mapping process.
            SISGenErrorList mappingErrList = new SISGenErrorList();

            int len = custPayees.length;
            for (int i=0; i<len; i++) {

                // PayeeInfo
                if ( (custPayees[i].Status.equals(DBConsts.MODPAYEE)) ||
                     (custPayees[i].Status.equals(DBConsts.MODBOTH)) ) {
                    try {
                        // Populate the PayeeInfo object.
                        // The RecAction must be set before the rest of the
                        // data fill methods are called so that we can perform
                        // error handling.
                        payeeInfo = new TypePayeeInfo();
                        payeeInfo.RecAction = EnumRecordAction_ACC.change;
                        customerPayeeInfo2PayeeInfo(custPayees[i], payeeInfo);
                        payeeInfo2PayeeInfo(payees[i], payeeInfo);

                        // Place PayeeInfo object into the buffer and
                        // increment our counts.
                        _payeeInfoList.add(payeeInfo);
                        _payeeInfoCount++;
                        _currCount++;
                    } catch (Throwable t) {
                        // We had an error while mapping this item.
                        // Populate the fields required for error handling
                        // and add the record to our list of invalid items.
                        payeeInfo.CspID = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_SPONSOR_ID );
                        payeeInfo.SubID = makeSubscriberID(custPayees[i].CustomerID);
                        payeeInfo.PayeeListID = custPayees[i].PayeeListID;

                        SISGenError sisGenError = new SISGenError(payeeInfo, t);
                        mappingErrList.addPayeeInfoError(sisGenError);
                    }
                }

                // PayeeAcctInfo
                if ( (custPayees[i].Status.equals(DBConsts.MODACCT)) ||
                     (custPayees[i].Status.equals(DBConsts.MODBOTH)) ) {
                    try {
                        // Populate the PayeeInfo object.
                        // The RecAction must be set before the rest of the
                        // data fill methods are called so that we can perform
                        // error handling.
                        payeeAcctInfo = new TypePayeeAcctInfo();
                        payeeAcctInfo.RecAction = EnumRecordAction_ACC.change;
                        customerPayeeInfo2PayeeAcctInfo(custPayees[i], payeeAcctInfo);

                        // Place PayeeInfo object into the buffer and
                        // increment our counts.
                        _payeeAcctInfoList.add(payeeAcctInfo);
                        _payeeAcctInfoCount++;
                        _currCount++;
                    } catch (Throwable t) {
                        // We had an error while mapping this item.
                        // Populate the fields required for error handling
                        // and add the record to our list of invalid items.
                        payeeAcctInfo.CspID = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_SPONSOR_ID );
                        payeeAcctInfo.SubID = makeSubscriberID(custPayees[i].CustomerID);
                        payeeAcctInfo.PayeeListID = custPayees[i].PayeeListID;

                        SISGenError sisGenError = new SISGenError(payeeAcctInfo, t);
                        mappingErrList.addPayeeAcctInfoError(sisGenError);
                    }
                }

                if (_currCount >= CheckFreeUtil._maxRecordCount) {
                    // Flush the data buffers out into temporary files.
                    SISGenErrorList builderErrList = flushToTempRqFile();

                    // Handle any MB build errors that were encountered.
                    processBuildErrors(builderErrList, dbh);
                    _currCount = 0;
                }
            }

            // Now handle any mapping errors that were encountered.
            processBuildErrors(mappingErrList,dbh);

        } catch (Exception e) {
            throw e;
        }

        FFSDebug.log("CheckFree FulfillmentAPI.modCustomerPayees() done");
    }


    public void deleteCustomerPayees( CustomerPayeeInfo[] custPayees,
                                      PayeeInfo[] payees,
                                      FFSConnectionHolder dbh )
    throws Exception
    {

        FFSDebug.log("CheckFree FulfillmentAPI.deleteCustomerPayees() start: number of customer-payee links="
                     +((custPayees==null) ?0 :custPayees.length) );
        if ( (custPayees == null) ||
             (custPayees.length <= 0) ) {
            return;
        }

        try {
            TypePayeeInfo payeeInfo = null;
            // Create a holder for any bad records that we might encounter
            // during our data mapping process.
            SISGenErrorList mappingErrList = new SISGenErrorList();

            int len = custPayees.length;
            for (int i=0; i<len; i++) {

                // PayeeInfo
                try {
                    // Populate the PayeeInfo object.
                    // The RecAction must be set before the rest of the
                    // data fill methods are called so that we can perform
                    // error handling.
                    payeeInfo = new TypePayeeInfo();
                    payeeInfo.RecAction = EnumRecordAction_ACC.cancel;
                    customerPayeeInfo2PayeeInfo(custPayees[i], payeeInfo);
                    payeeInfo2PayeeInfo(payees[i], payeeInfo);

                    // Place PayeeInfo object into the buffer and
                    // increment our counts.
                    _payeeInfoList.add(payeeInfo);
                    _payeeInfoCount++;
                    _currCount++;
                } catch (Throwable t) {
                    // We had an error while mapping this item.
                    // Populate the fields required for error handling
                    // and add the record to our list of invalid items.
                    payeeInfo.CspID = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_SPONSOR_ID );
                    payeeInfo.SubID = makeSubscriberID(custPayees[i].CustomerID);
                    payeeInfo.PayeeListID = custPayees[i].PayeeListID;

                    SISGenError sisGenError = new SISGenError(payeeInfo, t);
                    mappingErrList.addPayeeInfoError(sisGenError);
                }

                // PayeeAcctInfo
                // Since a 3000CAN record is sufficient for deletes,
                // we will not be creating/sending a 3010CAN record.

                // Set the custPayeeRoute status to "CLOSED".
                CustPayeeRoute.updateCustPayeeRouteStatus( custPayees[i].CustomerID,
                                                           custPayees[i].PayeeListID,
                                                           _routeID,
                                                           DBConsts.CLOSED,
                                                           dbh);
                // Set the custPayee status to "CLOSED".
                CustPayee.updateStatus(custPayees[i].CustomerID,
                                       custPayees[i].PayeeListID,
                                       DBConsts.CLOSED,
                                       dbh);

                if (_currCount >= CheckFreeUtil._maxRecordCount) {
                    // Flush the data buffers out into temporary files.
                    SISGenErrorList builderErrList = flushToTempRqFile();

                    // Handle any MB build errors that were encountered.
                    processBuildErrors(builderErrList, dbh);
                    _currCount = 0;
                }
            }

            // Now handle any mapping errors that were encountered.
            processBuildErrors(mappingErrList,dbh);

        } catch (Exception e) {
            throw e;
        }

        FFSDebug.log("CheckFree FulfillmentAPI.deleteCustomerPayees() done");
    }


    public void addPayments( PmtInfo[] billPmts,
                             PayeeRouteInfo[] routeinfo,
                             FFSConnectionHolder dbh )
    throws Exception
    {
        FFSDebug.log("CheckFree FulfillmentAPI.addPayments() start.",
                     FFSConst.PRINT_DEV);

        if ( (billPmts == null) ||
             (billPmts.length <= 0) ) {
            return;
        }

        try {
            // Create and initialize the calendar objects that will
            // be used for generating unique CspPmtIDs.
            if (_lastUsedCspPmtIdCal == null) {
                // lastUsedCspPmtIdCal hasn't been initialized yet.
                // Query the database to get the last persisted value.
                _lastUsedCspPmtIdCal = CheckFreeDBAPI.getLastUsedCspPmtId(dbh);
            }
            Calendar cspPmtIdCal = Calendar.getInstance();
            FFSDebug.log("CheckFree.addPayments(): Old CspPmtId = " +
                         _lastUsedCspPmtIdCal.getTime(), FFSConst.PRINT_DEV);
            FFSDebug.log("CheckFree.addPayments(): Proposed CspPmtId = " +
                         cspPmtIdCal.getTime(), FFSConst.PRINT_DEV);

            // Ensure cspPmtIdCal > _lastUsedCspPmtIdCal.
            initCspPmtIdCal(cspPmtIdCal, _lastUsedCspPmtIdCal);
            FFSDebug.log("CheckFree.addPayments(): Chosen CspPmtId = " +
                         cspPmtIdCal.getTime(), FFSConst.PRINT_DEV);


            // Create a holder for any bad records that we might encounter
            // during our data mapping process.
            SISGenErrorList mappingErrList = new SISGenErrorList();

            String cfGenInv = CheckFreeUtil.getProperty(DBConsts.CHECKFREE_GENERATE_4010,
                                                        "false");
            boolean generateInvoice = (cfGenInv != null) &&
                                      (cfGenInv.compareToIgnoreCase("true") == 0);


            int len = billPmts.length;
            for (int i=0; i<len; i++) {

                // Create a unique CspPmtId for this payment.
                // Note: While the CspPmtId is in a timestamp format
                // (yyyyMMddHHmmssSSS000), it is not an accurate
                // representation of the current time and should not be
                // used as such.
                cspPmtIdCal.add(Calendar.MILLISECOND, 1);
                String cspPmtId = CheckFreeUtil.getDateString(CheckFreeUtil.CF_DATE_FORMAT,
                                                              cspPmtIdCal.getTime());

                // Insert SrvrTID and CspPmtID into BPW_PmtHist table
                // Note:  In the original design, the CspPmtID = SrvrTID.
                //        After the Payment Invoice Record 4010 is introduced,
                //        the CspPmtID now has the format YYYYMMDDHHMMSSssssss.
                //        This insert is needed to map/link SrvrTID, CspPmtID,
                //        and CfPmtID together.
                //
                // Note:  The API returns a value. In the case of resubmit
                //        processing, the value returned is the cspPmtId that
                //        was originally assigned to this payment. In the case
                //        of standard processing, the value returned is the
                //        same CspPmtID that we push in. In either case, we
                //        want to use the value returned by this API.
                cspPmtId = CheckFreeDBAPI.insertCspPmtIDIntoPmtHist(
                                                                   billPmts[i].SrvrTID,
                                                                   cspPmtId,
                                                                   dbh);

                // Now that it has been (possibly) inserted into the database,
                // update the lastUsedCspPmtIdCal value.
                _lastUsedCspPmtIdCal.setTime(cspPmtIdCal.getTime());


                // Update the PmtInstruction's startDate to today.
                // Save the original trailing 2 digits(00 or 01).
                int startdate = DBUtil.getCurrentStartDate() +
                                (billPmts[i].StartDate % 100);
                PmtInstruction.updateStartDate(dbh,
                                               billPmts[i].SrvrTID,
                                               startdate);

                // Payment Information
                TypePmtInfo pmtInfo = null;
                try {
                    pmtInfo = new TypePmtInfo();
                    // Populate the PmtInfo object.
                    // The RecAction must be set before the rest of the
                    // data fill methods are called so that we can perform
                    // error handling.

                    // Payment Information 4000 record
                    pmtInfo.Info = new TypeInfo();
                    pmtInfo.Info.RecAction = EnumRecordAction_ACC.add;
                    pmtInfo2PmtInfo(billPmts[i], pmtInfo, cspPmtId);

                    if (generateInvoice == true) {
                        // Payment Invoice Information 4010 record
                        // This method will set the pmtInfo.InvInfoExists
                        // field to true or false, depending on the existence
                        // of PmtInvoice data in the payment object.
                        pmtInvoice2PmtInfo(billPmts[i], pmtInfo, cspPmtId);
                    }

                    // Place PmtInfo object into the buffer and
                    // increment our counts.
                    _pmtInfoList.add( pmtInfo );
                    _pmtInfoCount++;
                    _totalPmtAmount += pmtInfo.Info.PmtAmt;
                    _currCount++;

                    if (pmtInfo.InvInfoExists == true) {
                        // Increment our PmtInvoice counts, too.
                        _pmtInvInfoCount++;
                        _currCount++;
                    }
                } catch (Throwable t) {
                    // We had an error while mapping this item.
                    // Populate the fields required for error handling
                    // and add the record to our list of invalid items.
                    pmtInfo.Info.CspID = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_SPONSOR_ID );
                    pmtInfo.Info.SubID = makeSubscriberID(billPmts[i].CustomerID);
                    pmtInfo.Info.PayeeListID = billPmts[i].PayeeListID;
                    pmtInfo.Info.CspPmtID = cspPmtId;

                    SISGenError sisGenError = new SISGenError(pmtInfo, t);
                    mappingErrList.addPmtInfoError(sisGenError);
                }

                if (_currCount >= CheckFreeUtil._maxRecordCount) {
                    // Flush the data buffers out into temporary files.
                    SISGenErrorList builderErrList = flushToTempRqFile();

                    // Handle any MB build errors that were encountered.
                    processBuildErrors(builderErrList, dbh);
                    _currCount = 0;
                }
            }

            // Now handle any mapping errors that were encountered.
            processBuildErrors(mappingErrList,dbh);

        } catch (Exception e) {
            throw e;
        }
        FFSDebug.log("CheckFree FulfillmentAPI.addPayments() done.",
                     FFSConst.PRINT_DEV);
    }


    /**
     * Add Customer "add" records to the SIS file.
     *
     * @param customers Array of CustomerInfo objects, representing the customers
     *                  that should be enrolled to CheckFree.
     * @param dbh
     * @return The number of CustomerInfo objects that were processed by
     *         this method.
     * @exception Exception
     */
    public int addCustomers( CustomerInfo[] customers,
                             FFSConnectionHolder dbh )
    throws Exception
    {
        FFSDebug.log("CheckFree FulfillmentAPI.addCustomers() start.",
                     FFSConst.PRINT_DEV);

        if ( (customers == null) ||
             (customers.length <= 0) ) {
            return 0;
        }

        int len = customers.length;
        try {
            TypeSubInfo subInfo = null;
            TypeBankAcctInfo bInfo = null;

            // Create a holder for any bad records that we might encounter
            // during our data mapping process.
            SISGenErrorList mappingErrList = new SISGenErrorList();

            for (int i=0; i<len; i++) {
                if (customers[i] == null) {
                    continue;
                }

                // SubInfo
                try {
                    // Populate the SubInfo object.
                    // The RecAction must be set before the rest of the
                    // data fill methods are called so that we can perform
                    // error handling.
                    subInfo = new TypeSubInfo();
                    subInfo.RecAction = EnumRecordAction_ACI.add;
                    customerInfo2SubInfo(customers[i], subInfo);

                    // Place SubInfo object into the buffer and
                    // increment our counts.
                    _subInfoList.add(subInfo);
                    _subInfoCount++;
                    _currCount++;
                } catch (Throwable t) {
                    // We had an error while mapping this item.
                    // Populate the fields required for error handling
                    // and add the record to our list of invalid items.
                    subInfo.CspID = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_SPONSOR_ID );
                    subInfo.SubID = makeSubscriberID(customers[i].customerID);

                    SISGenError sisGenError = new SISGenError(subInfo, t);
                    mappingErrList.addSubInfoError(sisGenError);
                }

                // BankAcctInfo
                try {
                    // Populate the BankAcctInfo object.
                    // The RecAction must be set before the rest of the
                    // data fill methods are called so that we can perform
                    // error handling.
                    bInfo = new TypeBankAcctInfo();
                    bInfo.RecAction = EnumRecordAction_ACI.add;
                    customerInfo2BankInfo(customers[i], bInfo);

                    // Place the BankAcctInfo object into the buffer and
                    // increment our counts.
                    _bankInfoList.add(bInfo);
                    _bankInfoCount++;
                    _currCount++;
                } catch (Throwable t) {
                    // We had an error while mapping this item.
                    // Populate the fields required for error handling
                    // and add the record to our list of invalid items.
                    bInfo.SubID = makeSubscriberID(customers[i].customerID);

                    SISGenError sisGenError = new SISGenError(bInfo, t);
                    mappingErrList.addBankAcctInfoError(sisGenError);
                }

                if (_currCount >= CheckFreeUtil._maxRecordCount) {
                    // Flush the data buffers out into temporary files.
                    SISGenErrorList builderErrList = flushToTempRqFile();

                    // Handle any MB build errors that were encountered.
                    processBuildErrors(builderErrList, dbh);
                    _currCount = 0;
                }
            }

            // Now handle any mapping errors that were encountered.
            processBuildErrors(mappingErrList,dbh);

        } catch (Exception e) {
            throw e;
        }
        FFSDebug.log("CheckFree FulfillmentAPI.addCustomers() done.",
                     FFSConst.PRINT_DEV);

        return len;
    }

    /**
     * Add Customer "mod" records to the SIS file.
     *
     * @param customers Array of CustomerInfo objects, representing the customers
     *                  that should be modified at CheckFree.
     * @param dbh
     * @return The number of CustomerInfo objects that were processed by
     *         this method.
     * @exception Exception
     */
    public int modifyCustomers( CustomerInfo[] customers,
                                FFSConnectionHolder dbh )
    throws Exception
    {
        FFSDebug.log("CheckFree FulfillmentAPI.modifyCustomers() start.",
                     FFSConst.PRINT_DEV);

        if ( (customers == null) ||
             (customers.length <= 0) ) {
            return 0;
        }

        int len = customers.length;
        try {
            TypeSubInfo subInfo = null;

            // Create a holder for any bad records that we might encounter
            // during our data mapping process.
            SISGenErrorList mappingErrList = new SISGenErrorList();

            for (int i=0; i<len; i++) {
                if (customers == null) {
                    continue;
                }

                // SubInfo
                try {
                    // Populate the SubInfo object.
                    // The RecAction must be set before the rest of the
                    // data fill methods are called so that we can perform
                    // error handling.
                    subInfo = new TypeSubInfo();
                    subInfo.RecAction = EnumRecordAction_ACI.change;
                    customerInfo2SubInfo(customers[i], subInfo);

                    // Place SubInfo object into the buffer and
                    // increment our counts.
                    _subInfoList.add(subInfo);
                    _subInfoCount++;
                    _currCount++;
                } catch (Throwable t) {
                    // We had an error while mapping this item.
                    // Populate the fields required for error handling
                    // and add the record to our list of invalid items.
                    subInfo.CspID = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_SPONSOR_ID );
                    subInfo.SubID = makeSubscriberID(customers[i].customerID);

                    SISGenError sisGenError = new SISGenError(subInfo, t);
                    mappingErrList.addSubInfoError(sisGenError);
                }

                if (_currCount >= CheckFreeUtil._maxRecordCount) {
                    // Flush the data buffers out into temporary files.
                    SISGenErrorList builderErrList = flushToTempRqFile();

                    // Handle any MB build errors that were encountered.
                    processBuildErrors(builderErrList, dbh);
                    _currCount = 0;
                }
            }

            // Now handle any mapping errors that were encountered.
            processBuildErrors(mappingErrList,dbh);

        } catch (Exception e) {
            throw e;
        }
        FFSDebug.log("CheckFree FulfillmentAPI.modifyCustomers() done.",
                     FFSConst.PRINT_DEV);

        return len;
    }

    /**
     * Add Customer "cancel" records to the SIS file.
     *
     * @param customers Array of CustomerInfo objects, representing the customers
     *                  that should be cancelled at CheckFree.
     * @param dbh
     * @return The number of CustomerInfo objects that were processed by
     *         this method.
     * @exception Exception
     */
    public int deleteCustomers( CustomerInfo[] customers,
                                FFSConnectionHolder dbh )
    throws Exception
    {
        FFSDebug.log("CheckFree FulfillmentAPI.deleteCustomers() start.",
                     FFSConst.PRINT_DEV);

        if ( (customers == null) ||
             (customers.length <= 0) ) {
            return 0;
        }

        int len = customers.length;
        try {
            TypeSubInfo subInfo = null;
            TypeBankAcctInfo bInfo = null;

            // Create a holder for any bad records that we might encounter
            // during our data mapping process.
            SISGenErrorList mappingErrList = new SISGenErrorList();

            for (int i=0; i<len; i++) {
                if (customers == null) {
                    continue;
                }

                // SubInfo
                try {
                    // Populate the SubInfo object.
                    // The RecAction must be set before the rest of the
                    // data fill methods are called so that we can perform
                    // error handling.
                    subInfo = new TypeSubInfo();
                    subInfo.RecAction = EnumRecordAction_ACI.inactivate;
                    customerInfo2SubInfo(customers[i], subInfo);

                    _subInfoList.add(subInfo);
                    _subInfoCount++;
                    _currCount++;
                } catch (Throwable t) {
                    // We had an error while mapping this item.
                    // Populate the fields required for error handling
                    // and add the record to our list of invalid items.
                    subInfo.CspID = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_SPONSOR_ID );
                    subInfo.SubID = makeSubscriberID(customers[i].customerID);

                    SISGenError sisGenError = new SISGenError(subInfo, t);
                    mappingErrList.addSubInfoError(sisGenError);
                }

                // BankAcctInfo
                try {
                    // Populate the BankAcctInfo object.
                    // The RecAction must be set before the rest of the
                    // data fill methods are called so that we can perform
                    // error handling.
                    bInfo = new TypeBankAcctInfo();
                    bInfo.RecAction = EnumRecordAction_ACI.inactivate;
                    customerInfo2BankInfo(customers[i], bInfo);

                    // Place the BankAcctInfo object into the buffer and
                    // increment our counts.
                    _bankInfoList.add(bInfo);
                    _bankInfoCount++;
                    _currCount++;
                } catch (Throwable t) {
                    // We had an error while mapping this item.
                    // Populate the fields required for error handling
                    // and add the record to our list of invalid items.
                    bInfo.SubID = makeSubscriberID(customers[i].customerID);

                    SISGenError sisGenError = new SISGenError(bInfo, t);
                    mappingErrList.addBankAcctInfoError(sisGenError);
                }

                if (_currCount >= CheckFreeUtil._maxRecordCount) {
                    // Flush the data buffers out into temporary files.
                    SISGenErrorList builderErrList = flushToTempRqFile();

                    // Handle any MB build errors that were encountered.
                    processBuildErrors(builderErrList, dbh);
                    _currCount = 0;
                }
            }

            // Now handle any mapping errors that were encountered.
            processBuildErrors(mappingErrList,dbh);

        } catch (Exception e) {
            throw e;
        }
        FFSDebug.log( "CheckFree FulfillmentAPI.deleteCustomers() done.",
                      FFSConst.PRINT_DEV);

        return len;
    }// deleteCustomers()

    public void startCustBatch( FFSConnectionHolder dbh ) throws Exception
    {
    }

    public void endCustBatch(FFSConnectionHolder dbh)
    throws Exception
    {
        // Flush any remaining data in the buffers out into temporary files.
        SISGenErrorList builderErrList = flushToTempRqFile();

        // Handle any MB build errors that were encountered.
        processBuildErrors(builderErrList, dbh);
    }


    public void startPmtBatch( FFSConnectionHolder dbh ) throws Exception
    {
        FFSDebug.log("CheckFree FulfillmentAPI.startPmtBatch() start.");
        FFSDebug.log("Creating File Header Record");

        _dateString = CheckFreeUtil.getDateString( CheckFreeUtil.CF_DATE_FORMAT, new Date() );
        PropertyConfig propCfg = (PropertyConfig)FFSRegistry.lookup( BPWResource.PROPERTYCONFIG );
        //initializing record counts for a new Payment Batch
        _subInfoList            = new ArrayList( propCfg.BatchSize );
        _bankInfoList           = new ArrayList( propCfg.BatchSize );
        _payeeInfoList          = new ArrayList( propCfg.BatchSize );
        _payeeAcctInfoList      = new ArrayList( propCfg.BatchSize );
        _pmtInfoList            = new ArrayList( propCfg.BatchSize );

        _currCount              = 0;
        _subInfoCount           = 0;
        _bankInfoCount          = 0;
        _payeeInfoCount         = 0;
        _payeeAcctInfoCount     = 0;
        _pmtInfoCount           = 0;
        _pmtInvInfoCount        = 0;
        _totalPmtAmount         = 0L;
        _stheaderCount          = 1;
        _sttrailerCount         = 1;
        _dummySrvcTrans         = createBlankSrvcTrans( makeRecordHeader(), makeRecordTrailer() );

        TimeZone timeZone       = Calendar.getInstance().getTimeZone();

        int offset = timeZone.getRawOffset()/(1000*60*60); // Calculate hours from millisec's
        if ( offset>0 ) {
            _defaultTimeZone = "+";
            if ( offset<10 ) _defaultTimeZone += '0';
            _defaultTimeZone += offset;
        } else {
            _defaultTimeZone = "-";
            if ( offset>-10 ) _defaultTimeZone += '0';
            _defaultTimeZone += (-offset);
        }

        if ( _mb==null ) {
            Exception e = new Exception( "Message broker initialization has failed." );
            FFSDebug.log( e, "Unable to process CheckFree requests" );
            throw e;

        }

        deleteTempFiles();
        FFSDebug.log("CheckFree FulfillmentAPI.startPmtBatch() done.");

    }//startPmtBatch()

    public void endPmtBatch( FFSConnectionHolder dbh ) throws Exception
    {
        FFSDebug.log("CheckFree FulfillmentAPI.endPmtBatch() start.");

        try {
            // Flush any remaining data in the buffers out into temporary files.
            SISGenErrorList builderErrList = flushToTempRqFile();

            // Handle any MB build errors that were encountered.
            processBuildErrors(builderErrList, dbh);

            // Now assemble the entire SIS file.
            makeReqFile(dbh);
        } catch ( Exception e ) {

            FFSDebug.log("Failed to close cache for requests to CheckFree",FFSConst.PRINT_ERR);
            throw e;
        } finally {
            // Clear all the file lists
            clearFileLists();
            _dummySrvcTrans = null;
        }

        FFSDebug.log("CheckFree FulfillmentAPI.endPmtBatch() done.");
    }


    protected TypeSTHeader makeRecordHeader()
    {
        TypeSTHeader header = new TypeSTHeader();
        header.SenderID = CheckFreeUtil.getProperty(
                                                   DBConsts.CHECKFREE_SENDER_ID );
        header.ReceiverID = CheckFreeUtil.getProperty(
                                                     DBConsts.CHECKFREE_RECEIVER_ID );
        header.EntityName = CheckFreeUtil.getProperty(
                                                     DBConsts.CHECKFREE_ENTITY_NAME );
        int dateLen = CheckFreeConsts.STR_CHECKFREE_DATE_FORMAT.length();
        int timeLen = CheckFreeConsts.STR_CHECKFREE_TIME_FORMAT.length();
        header.FileCreationDate = _dateString.substring( 0, dateLen );
        header.FileCreationTime = _dateString.substring( dateLen, dateLen+timeLen );
        header.FileID = _fileID; //must "03"
        header.FileSpecVerNum = CheckFreeUtil.getProperty(
                                                         DBConsts.CHECKFREE_FILE_VERSION,
                                                         CheckFreeConsts.DEFAULT_FILE_VERSION );
        header.RS_ErrCode = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        header.RS_ErrMsg = CheckFreeConsts.MB_NULL_FIELD_VALUE;

        return header;
    }

    protected TypeSTTrailer makeRecordTrailer()
    {
        TypeSTTrailer trailer = new TypeSTTrailer();
        trailer.RecType1 = EnumRecordType.Header;
        trailer.NumRec1 = _stheaderCount;
        trailer.RecType2 = EnumRecordType.SubscriberInfo;
        trailer.NumRec2 = _subInfoCount;
        trailer.RecType3 = EnumRecordType.BankAcctInfo;
        trailer.NumRec3 = _bankInfoCount;
        trailer.RecType4 = EnumRecordType.PayeeInfo;
        trailer.NumRec4 = _payeeInfoCount;
        trailer.RecType5 = EnumRecordType.PayeeAcctInfo;
        trailer.NumRec5 = _payeeAcctInfoCount;
        trailer.RecType6 = EnumRecordType.OneTimePmtInfo;
        trailer.NumRec6 = _pmtInfoCount;
        trailer.RecType7 = EnumRecordType.OneTimePmtInvoiceInfo;
        trailer.NumRec7 = _pmtInvInfoCount;
        trailer.RecType8 = EnumRecordType.Trailer;
        trailer.NumRec8 = _sttrailerCount;
        trailer.RecType9 = EnumRecordType.Empty;
        trailer.NumRec9 = CheckFreeConsts.MB_NIL_FIELD_VALUE;
        trailer.RecType10 = EnumRecordType.Empty;
        trailer.NumRec10 = CheckFreeConsts.MB_NIL_FIELD_VALUE;
        trailer.RecType11 = EnumRecordType.Empty;
        trailer.NumRec11 = CheckFreeConsts.MB_NIL_FIELD_VALUE;
        trailer.RecType12 = EnumRecordType.Empty;
        trailer.NumRec12 = CheckFreeConsts.MB_NIL_FIELD_VALUE;
        trailer.RecType13 = EnumRecordType.Empty;
        trailer.NumRec13 = CheckFreeConsts.MB_NIL_FIELD_VALUE;
        trailer.RecType14 = EnumRecordType.Empty;
        trailer.NumRec14 = CheckFreeConsts.MB_NIL_FIELD_VALUE;
        trailer.RecType15 = EnumRecordType.Empty;
        trailer.NumRec15 = CheckFreeConsts.MB_NIL_FIELD_VALUE;
        trailer.RecType16 = EnumRecordType.Empty;
        trailer.NumRec16 = CheckFreeConsts.MB_NIL_FIELD_VALUE;
        trailer.RecType17 = EnumRecordType.OneTimePmtInfo;
        trailer.TotOneTimePmts = _totalPmtAmount;
        trailer.RS_ErrCode = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        trailer.RS_ErrMsg = CheckFreeConsts.MB_NULL_FIELD_VALUE;

        return trailer;
    }// makeRecordTrailer()


    public void startCustomerPayeeBatch( FFSConnectionHolder dbh ) throws Exception
    {
    }


    public void endCustomerPayeeBatch( FFSConnectionHolder dbh ) throws Exception
    {
    }


    /**
     * Initializes a calendar object by making sure that the
     * date/time value of the target Calendar object (cspPmtIdCal)
     * occurs after the date/time specified in the baseCalendar
     * calendar object.
     *
     * @param cspPmtIdCal
     *               Calendar object to initialize.
     * @param baseCalendar
     *               Basis Calendar for the comparison. The cspPmtIdCal will
     *               be set to a date/time that occurs after the date/time
     *               found in this parameter.
     */
    protected  void initCspPmtIdCal(Calendar cspPmtIdCal,
                                       Calendar baseCalendar)
    {
        // Make sure that the calendar object is set to a date/time
        // that is after the date/time value found in the baseCalendar
        // This protects us from scenarios where the System clock
        // is set backwards (which might occur due to the Day Light
        // Saving time shift).
        if (cspPmtIdCal.after(baseCalendar) == false) {
            // The current value in the cspPmtIdCal object does
            // not occur after the value in the _lastUsedDateTime
            // object. Set the cspPmtIdCal object to a value
            // that is one millisecond after the value held
            // by the _lastUsedDateTime object.
            cspPmtIdCal.setTime(baseCalendar.getTime());
            cspPmtIdCal.add(Calendar.MILLISECOND, 1);
        }
    }

    /**
     * Flushes cached records out to temporary files.
     *
     * For each CheckFree record cache (_subInfoList,
     * _bankInfoList, _payeeInfoList, _payeeAcctInfoList, and
     * _pmtInfoList) that contains unflushed data, this method
     * will generate a temporary file and populate it with the
     * unflushed data.
     *
     * @return SISGenErrorList object containing any records that were
     *         unable to be written out the a temporary file.
     * @exception Exception
     */
    protected SISGenErrorList flushToTempRqFile()
    throws Exception
    {
    	FileHandlerProvider fileHandlerProvider = (FileHandlerProvider)OSGIUtil.getBean(FileHandlerProvider.class);
        // Create a holder for any bad records that we might encounter
        // during our SIS file MB build process.
        SISGenErrorList builderErrList = new SISGenErrorList();

        File tempDir = new File( _cachePath + File.separator
                                 + CheckFreeConsts.STR_TEMP_DIR_NAME );
        tempDir.setFileHandlerProvider(fileHandlerProvider);
        if ( !tempDir.exists() ) {
            tempDir.mkdir();
        }

        try {
            // Check subscriber information
            if (_subInfoList.isEmpty() == false) {
                // render the cache
                _dummySrvcTrans.SubInfo = (TypeSubInfo[])_subInfoList.toArray(
                                                                             new TypeSubInfo[_subInfoList.size()]);
                _dummySrvcTrans.SubInfoExists = true;
                // clear the cache
                _subInfoList.clear();

                byte[] srvcTransBytes = null;
                try {
                    // Build the batch of records into a byte array.
                    srvcTransBytes = buildSrvcTrans(_dummySrvcTrans);
                } catch (BPWException mbe) {
                    // We had a MessageBroker builder exception. Perform
                    // special handling to weed out the bad records. The
                    // good records are incorporated into the returned
                    // byte array. The bad records are returned in the
                    // provided builderErrList object.
                    srvcTransBytes = handleSubInfoBuildError(_dummySrvcTrans,
                                                             builderErrList);
                }

                if ( (srvcTransBytes != null) &&
                     (srvcTransBytes.length > 0) ) {
                    // If any data exists, write it into a temp file.
                    File tempFile = writeTempRqFile(tempDir,srvcTransBytes);

                    // add this temp file into file list
                    _subInfoFiles.add( tempFile );
                }

                // clean up the dummy message for reuse
                _dummySrvcTrans.SubInfo= null;
                _dummySrvcTrans.SubInfoExists = false;
            }

            // Check bank information
            if (_bankInfoList.isEmpty() == false) {
                // render the cache
                _dummySrvcTrans.BankAcctInfo = (TypeBankAcctInfo[])_bankInfoList.toArray(
                                                                                        new TypeBankAcctInfo[_bankInfoList.size()]);
                _dummySrvcTrans.BankAcctInfoExists = true;
                // clear the cache
                _bankInfoList.clear();

                byte[] srvcTransBytes = null;
                try {
                    // Build the batch of records into a byte array.
                    srvcTransBytes = buildSrvcTrans(_dummySrvcTrans);
                } catch (BPWException mbe) {
                    // We had a MessageBroker builder exception. Perform
                    // special handling to weed out the bad records. The
                    // good records are incorporated into the returned
                    // byte array. The bad records are returned in the
                    // provided builderErrList object.
                    srvcTransBytes = handleBankAcctInfoBuildError(_dummySrvcTrans,
                                                                  builderErrList);
                }

                if ( (srvcTransBytes != null) &&
                     (srvcTransBytes.length > 0) ) {
                    // If any data exists, write it into a temp file.
                    File tempFile = writeTempRqFile(tempDir,srvcTransBytes);

                    // add this temp file into file list
                    _bankInfoFiles.add( tempFile );
                }

                // clean up the dummy message for reuse
                _dummySrvcTrans.BankAcctInfo = null;
                _dummySrvcTrans.BankAcctInfoExists = false;
            }

            // Check payee info
            if (_payeeInfoList.isEmpty() == false) {
                // render the cache
                _dummySrvcTrans.PayeeInfo = (TypePayeeInfo[])_payeeInfoList.toArray(
                                                                                   new TypePayeeInfo[_payeeInfoList.size()]);
                _dummySrvcTrans.PayeeInfoExists = true;
                // clear the cache
                _payeeInfoList.clear();

                byte[] srvcTransBytes = null;
                try {
                    // Build the batch of records into a byte array.
                    srvcTransBytes = buildSrvcTrans(_dummySrvcTrans);
                } catch (BPWException mbe) {
                    // We had a MessageBroker builder exception. Perform
                    // special handling to weed out the bad records. The
                    // good records are incorporated into the returned
                    // byte array. The bad records are returned in the
                    // provided builderErrList object.
                    srvcTransBytes = handlePayeeInfoBuildError(_dummySrvcTrans,
                                                               builderErrList);
                }

                if ( (srvcTransBytes != null) &&
                     (srvcTransBytes.length > 0) ) {
                    // If any data exists, write it into a temp file.
                    File tempFile = writeTempRqFile(tempDir,srvcTransBytes);

                    // add this temp file into file list
                    _payeeInfoFiles.add( tempFile );
                }

                // clean up the dummy message for reuse
                _dummySrvcTrans.PayeeInfo = null;
                _dummySrvcTrans.PayeeInfoExists = false;
            }

            // check payee account info
            if (_payeeAcctInfoList.isEmpty() == false) {
                // render the cache
                _dummySrvcTrans.PayeeAcctInfo = (TypePayeeAcctInfo[]) _payeeAcctInfoList.toArray(
                                                                                                new TypePayeeAcctInfo[_payeeAcctInfoList.size()]);
                _dummySrvcTrans.PayeeAcctInfoExists = true;

                // clear the cache
                _payeeAcctInfoList.clear();

                byte[] srvcTransBytes = null;
                try {
                    // Build the batch of records into a byte array.
                    srvcTransBytes = buildSrvcTrans(_dummySrvcTrans);
                } catch (BPWException mbe) {
                    // We had a MessageBroker builder exception. Perform
                    // special handling to weed out the bad records. The
                    // good records are incorporated into the returned
                    // byte array. The bad records are returned in the
                    // provided builderErrList object.
                    srvcTransBytes = handlePayeeAcctInfoBuildError(_dummySrvcTrans,
                                                                   builderErrList);
                }

                if ( (srvcTransBytes != null) &&
                     (srvcTransBytes.length > 0) ) {
                    // If any data exists, write it into a temp file.
                    File tempFile = writeTempRqFile(tempDir,srvcTransBytes);

                    // add this temp file into file list
                    _payeeAcctInfoFiles.add( tempFile );
                }

                // clean up the dummy message for reuse
                _dummySrvcTrans.PayeeAcctInfo = null;
                _dummySrvcTrans.PayeeAcctInfoExists = false;
            }

            // Check payment info
            if (_pmtInfoList.isEmpty() == false) {
                // render the cache
                _dummySrvcTrans.PmtInfo = (TypePmtInfo[])_pmtInfoList.toArray(
                                                                             new TypePmtInfo[_pmtInfoList.size()]);
                _dummySrvcTrans.PmtInfoExists = true;

                // clear the cache
                _pmtInfoList.clear();

                byte[] srvcTransBytes = null;
                try {
                    // Build the batch of records into a byte array.
                    srvcTransBytes = buildSrvcTrans(_dummySrvcTrans);
                } catch (BPWException mbe) {
                    // We had a MessageBroker builder exception. Perform
                    // special handling to weed out the bad records. The
                    // good records are incorporated into the returned
                    // byte array. The bad records are returned in the
                    // provided builderErrList object.
                    srvcTransBytes = handlePmtInfoBuildError(_dummySrvcTrans,
                                                             builderErrList);
                }

                if ( (srvcTransBytes != null) &&
                     (srvcTransBytes.length > 0) ) {
                    // If any data exists, write it into a temp file.
                    File tempFile = writeTempRqFile(tempDir,srvcTransBytes);

                    // add this temp file into file list
                    _pmtInfoFiles.add( tempFile );
                }

                // clean up the dummy message for reuse
                _dummySrvcTrans.PmtInfo = null;
                _dummySrvcTrans.PmtInfoExists = false;
            }
        } catch ( Exception e) {
            // Cleaning up the referencing to ensure good garbage collection
            cleanUpMsgBody( _dummySrvcTrans );
            throw e;
        }
        return builderErrList;
    }

    /**
     * Write out the bytes in the provided byte array to a
     * temporary file. The file will be located in the directory
     * referenced by the tempDir parameter.
     *
     * @param tempDir File object pointing to the directory that the generated
     *                temp file should be created in.
     * @param srvcTransBytes
     *                The array of bytes to use to populate the generated file.
     * @return File object pointing to the newly created temp file.
     * @exception Exception Propagates any thrown exception that is encountered
     *                      during the file generation process.
     */
    protected File writeTempRqFile(File tempDir, byte[] srvcTransBytes)
    throws Exception
    {
    	FileHandlerProvider fileHandlerProvider = (FileHandlerProvider)OSGIUtil.getBean(FileHandlerProvider.class);
        File tempFile = null;
        OutputStream out = null;
        FileOutputStream fileOutputStream = null;
        try {
            // Use the standard Java library to create a temp file object.
            tempFile = File.createTempFile(
                                          CheckFreeConsts.STR_TEMP_FILE_PREFIX,
                                          CheckFreeConsts.STR_TEMP_INFILE_SUFFIX,
                                          tempDir );
            tempFile.setFileHandlerProvider(fileHandlerProvider);

            // Open up a output stream to the file.
            fileOutputStream = new FileOutputStream( tempFile );
            out = new BufferedOutputStream( fileOutputStream );

            // Write the byte array out into the file.
            writeMsgContent(out, srvcTransBytes);
            out.flush();
        } catch (Exception e) {
            throw e;
        } finally {
            // Flush and close the output stream.
            if (null != out) {
            	try {
            		out.close();
            	} catch(Exception e) {
            		// ignore
            	}
            }
            if (null != fileOutputStream) {
            	try {
            		fileOutputStream.close();
            	} catch(Exception e) {
            		// ignore
            	}
            }
        }
        return tempFile;
    }

    /**
     * Use the common MB Builder instance to build a SrvcTrans
     * message using the data in the provided TypeSrvcTrans
     * parameter.
     *
     * @param srvcTrans TypeSrvcTrans instance that contains the data to build.
     * @return byte array resulting from the MessageBroker build
     *         operation.
     * @exception Exception MBException thrown by the build process.
     * @exception MBException
     *                      Any exception thrown by the build process.
     */
    protected  byte[] buildSrvcTrans(TypeSrvcTrans srvcTrans)
    throws BPWException
    {
        FFSDebug.log("CheckFree FulfillmentAPI.buildSrvcTrans() start.", FFSConst.PRINT_DEV);

        // Build the message into bytes
        byte[] srvcTransBytes = _mb.buildMsg(
                                            srvcTrans,
                                            CheckFreeConsts.MB_SRVC_TRANS_MESSAGE_NAME,
                                            CheckFreeConsts.MB_MESSAGE_SET_NAME ).getBytes();

        FFSDebug.log("CheckFree FulfillmentAPI.buildSrvcTrans() done.", FFSConst.PRINT_DEV);

        return srvcTransBytes;
    }

    protected  void writeMsgContent(OutputStream out,
                                       byte[] buff)
    throws Exception
    {
        FFSDebug.log("CheckFree FulfillmentAPI.writeMsgContent() start.", FFSConst.PRINT_DEV);

        // Write the message contents without header and trailer
        int contentStart = CheckFreeUtil.findStartContent( buff );
        int contentEnd = CheckFreeUtil.findEndContent( buff );

        out.write( buff, contentStart, contentEnd-contentStart+1 );
        out.flush();

        FFSDebug.log("CheckFree FulfillmentAPI.writeMsgContent() done.", FFSConst.PRINT_DEV);
    } //writeMsgContent()


    protected   void writeMsgHeader( OutputStream out,
                                              byte[] buff ) throws Exception
    {
        FFSDebug.log("CheckFree FulfillmentAPI.writeMsgHeader() start.", FFSConst.PRINT_DEV);
        // Write the message contents without header and trailer
        int contentStart = CheckFreeUtil.findStartContent( buff );

        out.write( buff, 0, contentStart );
        out.flush();

        FFSDebug.log("CheckFree FulfillmentAPI.writeMsgHeader() done.", FFSConst.PRINT_DEV);

    } //writeMsgTrailer()


    protected  void writeMsgTrailer( OutputStream out,
                                               byte[] buff ) throws Exception
    {
        FFSDebug.log("CheckFree FulfillmentAPI.writeMsgTrailer() start.", FFSConst.PRINT_DEV);
        // Write the message contents without header and trailer
        int contentEnd = CheckFreeUtil.findEndContent( buff );

        out.write( buff, contentEnd+1, buff.length-contentEnd-1 );
        out.flush();

        FFSDebug.log("CheckFree FulfillmentAPI.writeMsgTrailer() done.", FFSConst.PRINT_DEV);

    } //writeMsgTrailer()


    protected void makeReqFile( FFSConnectionHolder dbh )
    throws Exception
    {
        // Trivial return
        FFSDebug.log("CheckFree FulfillmentAPI.writeReqFile() start.", FFSConst.PRINT_DEV);

        //
        // There is no need to run an "empty file check". CheckFree wants
        // a SIS file, even if there are no records.
        //

        // Create an empty msg with good header and trailer and parse into bytes
        // to serve as message shell (1st and last lines of the produced file)
        TypeSrvcTrans srvcTrans = createBlankSrvcTrans( makeRecordHeader(), makeRecordTrailer() );
        byte[] msgShell = _mb.buildMsg(
                                      srvcTrans,
                                      CheckFreeConsts.MB_SRVC_TRANS_MESSAGE_NAME,
                                      CheckFreeConsts.MB_MESSAGE_SET_NAME ).getBytes();

        // get name for the new req file
        String fileName = getFileNameBase()+CheckFreeConsts.STR_EXPORT_FILE_SUFFIX;
        OutputStream out = null;
        FileOutputStream fileOutputStream = null;
        File tempFile = null;
        FileHandlerProvider fileHandlerProvider = (FileHandlerProvider)OSGIUtil.getBean(FileHandlerProvider.class);
        try {
            // Create a temp master file
            File tempDir = new File( _cachePath + File.separator
                                     +CheckFreeConsts.STR_TEMP_DIR_NAME );
            tempDir.setFileHandlerProvider(fileHandlerProvider);
            tempFile = File.createTempFile(
                                          CheckFreeConsts.STR_TEMP_FILE_PREFIX,
                                          CheckFreeConsts.STR_TEMP_INFILE_SUFFIX,
                                          tempDir );
            fileOutputStream = new FileOutputStream( tempFile );
            out = new BufferedOutputStream( fileOutputStream );

            // Write header into master temp file
            writeMsgHeader(    out,    msgShell    );

            // Go through all the file lists in the same order as they should appear
            // in a service transaction file. Merge the files in all the file lists
            // into master temp file.
            File[] subInfoFiles = (File[])_subInfoFiles.toArray( new File[_subInfoFiles.size()] );
            for (File file : subInfoFiles) {
            	file.setFileHandlerProvider(fileHandlerProvider);
            }
            CheckFreeUtil.mergeFiles( subInfoFiles, out  );
            
            File[] bankInfoFiles = (File[])_bankInfoFiles.toArray( new File[_bankInfoFiles.size()] );
            for (File file : bankInfoFiles) {
            	file.setFileHandlerProvider(fileHandlerProvider);
            }
            CheckFreeUtil.mergeFiles( bankInfoFiles, out );
            
            File[] payeeInfoFiles = (File[])_payeeInfoFiles.toArray( new File[_payeeInfoFiles.size()] );
            for (File file : payeeInfoFiles) {
            	file.setFileHandlerProvider(fileHandlerProvider);
            }
            CheckFreeUtil.mergeFiles( payeeInfoFiles, out );
            
            File[] payeeAcctInfoFiles = (File[])_payeeAcctInfoFiles.toArray( new File[_payeeAcctInfoFiles.size()] );
            for (File file : payeeAcctInfoFiles) {
            	file.setFileHandlerProvider(fileHandlerProvider);
            }
            CheckFreeUtil.mergeFiles( payeeAcctInfoFiles, out );
            
            File[] pmtInfoFiles = (File[])_pmtInfoFiles.toArray( new File[_pmtInfoFiles.size()] );
            for (File file : pmtInfoFiles) {
            	file.setFileHandlerProvider(fileHandlerProvider);
            }

            CheckFreeUtil.mergeFiles( pmtInfoFiles, out );

            // Write the trailer into the master temp file
            writeMsgTrailer( out, msgShell );

            // Closing output stream
            out.flush();

            // Moving the temp master file into export dir
            File exportFile = new File( _exportDir+File.separator+fileName );
            exportFile.setFileHandlerProvider(fileHandlerProvider);

            tempFile.renameTo( exportFile );

            // Log to File Monitor Log
            FMLogAgent.writeToFMLog(dbh,
                                    DBConsts.BPW_CHECKFREE_FILETYPE_SISSRVTRAN,
                                    exportFile.getPath(),
                                    DBConsts.BPTW,
                                    DBConsts.CHECKFREE,
                                    FMLogRecord.STATUS_COMPLETE);
            
        } catch (Exception e ) {
            if ( tempFile!=null && tempFile.exists() ) tempFile.delete();
            throw e;
        } finally {
        	if ( null != out ) {
        		try {
        			out.close();
        		} catch(Exception e) {
        			// ignore
        		}
        		try {
        			fileOutputStream.close();
        		} catch(Exception e) {
        			// ignore
        		}
            }
        	
            // House keeping
            deleteTempFiles();
        }

        // Nullify caches for garbage collection
        _subInfoList = null;
        _bankInfoList = null;
        _payeeInfoList = null;
        _payeeAcctInfoList = null;
        _pmtInfoList = null;
        FFSDebug.log("CheckFree FulfillmentAPI.writeReqFile() end.", FFSConst.PRINT_DEV);
    }


    protected void deleteTempFiles()
    {
        FFSDebug.log( "CheckFreeHandler.deleteTempFiles() start.", FFSConst.PRINT_DEV );
        deleteFiles( (File[])_subInfoFiles.toArray( new File[_subInfoFiles.size()] ) );
        deleteFiles( (File[])_bankInfoFiles.toArray( new File[_bankInfoFiles.size()]) );
        deleteFiles( (File[])_payeeInfoFiles.toArray( new File[_payeeInfoFiles.size()]) );
        deleteFiles( (File[])_payeeAcctInfoFiles.toArray( new File[_payeeAcctInfoFiles.size()]) );
        deleteFiles( (File[])_pmtInfoFiles.toArray( new File[_pmtInfoFiles.size()]) );

        // Flush the file caches
        _subInfoFiles.clear();
        _bankInfoFiles.clear();
        _payeeInfoFiles.clear();
        _payeeAcctInfoFiles.clear();
        _pmtInfoFiles.clear();
        FFSDebug.log( "CheckFreeHandler.deleteTempFiles() end.", FFSConst.PRINT_DEV );
    }


    protected void deleteFiles( File[] files )
    {
        FFSDebug.log( "CheckFreeHandler.deleteFiles() start.", FFSConst.PRINT_DEV );
        if ( files==null || files.length<=0 ) return;
        for ( int i=0; i<files.length; ++i ) {
            try {
                if ( !files[i].delete() ) {
                    throw new Exception( "Failed to delete file "
                                         + files[ i ].getAbsolutePath() );
                }
            } catch ( Exception e ) {
                FFSDebug.log( "Failed to delete file "+ files[i].getAbsolutePath(), FFSConst.PRINT_ERR );
                FFSDebug.log( FFSDebug.stackTrace( e ), FFSConst.PRINT_ERR );
            }
        }
        FFSDebug.log( "CheckFreeHandler.deleteFiles() end.", FFSConst.PRINT_DEV );
    }

    protected void clearFileLists()
    {
        FFSDebug.log( "CheckFreeHandler.clearFileLists() start.", FFSConst.PRINT_DEV );
        _subInfoFiles.clear();
        _bankInfoFiles.clear();
        _payeeInfoFiles.clear();
        _payeeAcctInfoFiles.clear();
        _pmtInfoFiles.clear();
        FFSDebug.log( "CheckFreeHandler.clearFileLists() end.", FFSConst.PRINT_DEV );
    }


    protected String getFileNameBase() throws Exception
    {
    	FileHandlerProvider fileHandlerProvider = (FileHandlerProvider)OSGIUtil.getBean(FileHandlerProvider.class);
        File exportDir = new File( _exportDir );
        exportDir.setFileHandlerProvider(fileHandlerProvider);
        
        String dayStr = CheckFreeUtil.getDateString(
                                                   CheckFreeUtil.OFX_DATE_FORMAT, new Date() ).substring( 0,
                                                                                                          "YYYYMMDD".length() );

        String nameBase = CheckFreeConsts.FILE_PREFIX+dayStr;

        int idx = CheckFreeUtil.getIntFromAlphabet( 'A' );
        if ( !exportDir.exists() ) {
            exportDir.mkdir();
            return(nameBase + CheckFreeUtil.getAlphabetFromInt( idx ) );
        } else if ( !exportDir.isDirectory() ) {
            Exception e = new Exception ( "Error: Export Directory"
                                          +exportDir.getAbsolutePath()
                                          +" is not a directory.");
            FFSDebug.log( e.getLocalizedMessage(),FFSDebug.PRINT_ERR );
            throw e;
        }

        String list[] = exportDir.list();
        if ( list.length>0 ) {
            for (int i=0; i<list.length; ++i ) {
                if ( list[i].startsWith( nameBase )
                     && ( list[i].endsWith( CheckFreeConsts.STR_EXPORT_FILE_SUFFIX ) ) ) {
                    char c = list[i].charAt( nameBase.length() );
                    int currIdx = CheckFreeUtil.getIntFromAlphabet( c );
                    if ( currIdx>=idx ) idx = currIdx+1;
                }
            }
        }

        nameBase = nameBase+CheckFreeUtil.getAlphabetFromInt( idx );

        return nameBase;
    }// getFileNameBase()

    protected  void customerInfo2SubInfo(
                                                  CustomerInfo info,
                                                  TypeSubInfo subInfo )
    {
        subInfo.CspID       = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_SPONSOR_ID );
        subInfo.SubID       = makeSubscriberID( info.customerID );
        subInfo.SubType     = EnumSubType.individual;
        subInfo.BusName     = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        subInfo.LastName    = info.lastName;
        subInfo.FirstName   = info.firstName;
        subInfo.MiddleName  = ( info.initial==null )
                              ?CheckFreeConsts.MB_NULL_FIELD_VALUE
                              :info.initial;
        subInfo.Nickname    = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        subInfo.SubNameUsage    = EnumSubNameUsage.Empty;
        subInfo.SubNamePrefix   = ( info.suffix==null )
                                  ?CheckFreeConsts.MB_NULL_FIELD_VALUE
                                  :info.suffix;
        subInfo.TaxID       = ( info.ssn==null )
                              ?CheckFreeConsts.TAX_NIL_FIELD_VALUE
                              :CheckFreeUtil.deleteSymbols( info.ssn );
        subInfo.SecurityName    = ( info.securityCode==null)
                                  ?CheckFreeConsts.MB_NULL_FIELD_VALUE
                                  :info.securityCode;

        // Set filler2 to "00000000" as required by CHKF - this is the case
        // when there is no record 1040 for this subscriber. -Xin
        subInfo.PIN     = CheckFreeConsts.MB_NULL_SUBINFO_FILLER2;
        subInfo.Filler3     = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        subInfo.SubDOB      = ( info.dateBirth==null )
                              ?CheckFreeConsts.MB_NULL_FIELD_VALUE
                              :info.dateBirth;
        subInfo.ContactInfo = new TypeContactInfo();
        subInfo.ContactInfo.Addr1   = info.addressLine1;
        subInfo.ContactInfo.Addr2   = ( info.addressLine2==null )
                                      ?CheckFreeConsts.MB_NULL_FIELD_VALUE
                                      :info.addressLine2;
        subInfo.ContactInfo.Filler  = CheckFreeConsts.MB_NULL_FIELD_VALUE;

        if ( info.country==null || info.country.length()<=0) {
            info.country=CheckFreeConsts.DEFAULT_COUNTRY;
        }

        subInfo.ContactInfo.City    = ( info.city==null &&
                                        !info.country.equalsIgnoreCase( "USA" ) )
                                      ?CheckFreeConsts.MB_NULL_FIELD_VALUE
                                      :info.city;
        subInfo.ContactInfo.State   = ( info.country.equalsIgnoreCase( "USA" ) )
                                      ?EnumStateCode.from_int(
                                                             ValueSetStateCode.getIndex( info.state ) )
                                      :EnumStateCode.Empty;
        if ( info.zipcode!=null && info.zipcode.length()>=5 &&
             info.country.equalsIgnoreCase( "USA" ) ) {
            subInfo.ContactInfo.Zip5    = info.zipcode.substring(0,5);
            int dashIndex   = info.zipcode.trim().indexOf('-');
            int spaceIndex  = info.zipcode.trim().indexOf(' ');
            if ( (dashIndex == 5) ) {// 12345-6789 zip code format
                subInfo.ContactInfo.Zip4    = (info.zipcode.trim().length()>=10)
                                              ? info.zipcode.substring(6,10)
                                              :CheckFreeConsts.MB_NULL_FIELD_VALUE;
            } else if ( (spaceIndex == 5) ) {// 12345 6789 zip code format
                subInfo.ContactInfo.Zip4    = (info.zipcode.trim().length()>=10)
                                              ? info.zipcode.substring(6,10)
                                              :CheckFreeConsts.MB_NULL_FIELD_VALUE;
            } else { // maybe of format 123456789
                subInfo.ContactInfo.Zip4    = (info.zipcode.trim().length()>=9)
                                              ? info.zipcode.substring(5,9)
                                              :CheckFreeConsts.MB_NULL_FIELD_VALUE;
            }
        } else {
            subInfo.ContactInfo.Zip5    = CheckFreeConsts.MB_NULL_FIELD_VALUE;
            subInfo.ContactInfo.Zip4    = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        }
        subInfo.ContactInfo.Zip2    = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        subInfo.ContactInfo.CountryCode = info.country;
        subInfo.ContactInfo.PostalCode  = ( !info.country.equalsIgnoreCase( "USA" )
                                            && info.zipcode!=null )
                                          ?info.zipcode
                                          :CheckFreeConsts.MB_NULL_FIELD_VALUE;
        subInfo.ContactInfo.ProvinceName    = ( !info.country.equalsIgnoreCase( "USA" )
                                                && info.state!=null )
                                              ?info.state
                                              :CheckFreeConsts.MB_NULL_FIELD_VALUE;
        subInfo.ContactInfo.DayPhone        = ( info.phone1==null )
                                              ?CheckFreeConsts.MB_NULL_FIELD_VALUE
                                              :CheckFreeUtil.deleteSymbols( info.phone1 );
        subInfo.ContactInfo.EveningPhone    = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        subInfo.FaxPhone        = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        subInfo.EnrollFormCode      = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        subInfo.EnrollMarketingSource   = CheckFreeConsts.MB_NULL_FIELD_VALUE;

        String SW_SPEC_ID       = CheckFreeUtil.getProperty(
                                                           DBConsts.CHECKFREE_SW_SPEC_ID,
                                                           CheckFreeConsts.DEFAULT_SW_SPEC_ID );
        if (SW_SPEC_ID.length()<32) {
            while (SW_SPEC_ID.length()<32) {
                SW_SPEC_ID = SW_SPEC_ID +" ";
            }

        }
        subInfo.SWSpecID        = EnumSWSpecID.from_int(
                                                       ValueSetSWSpecID.getIndex(
                                                                                SW_SPEC_ID ) );
        subInfo.SWSpecVer       = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_SW_SPEC_VER,
                                                             CheckFreeConsts.DEFAULT_SW_SPEC_VER );//Default value.
        subInfo.OrigSWAppID     = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_ORIG_APP_ID );
        subInfo.OrigSWAppVer        = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_ORIG_APP_VER );
        subInfo.TimeZone        = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_TIME_ZONE,
                                                             _defaultTimeZone );
        subInfo.EmailAddr       = ( info.email==null )
                                  ?CheckFreeConsts.MB_NULL_FIELD_VALUE
                                  :info.email;
        subInfo.SubCategory     = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        subInfo.EmployeeIndicator   = EnumYesNoOpt.empty;
        subInfo.Filler4         = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        subInfo.FulfillCode     = CheckFreeConsts.BYPASS;
        subInfo.BillingClass        = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        subInfo.ServiceType     = EnumServiceType.BillPaymentOnly;
        subInfo.SolicitFlag     = EnumYesNoOpt.empty;
        subInfo.Filler5         = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        subInfo.UserID          = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        subInfo.DriversLicenseNumber    = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        subInfo.DriversLicenseState = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        subInfo.VirtualUserID       = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        subInfo.CspBillingCategory  = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        subInfo.RS_ErrCode      = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        subInfo.RS_ErrMsg       = CheckFreeConsts.MB_NULL_FIELD_VALUE;

    }// customerInfo2SubInfo

    protected  void customerPayeeInfo2PayeeInfo( CustomerPayeeInfo info,
                                                           TypePayeeInfo payeeInfo )
    {
        payeeInfo.CspID         = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_SPONSOR_ID );
        payeeInfo.SubID         = makeSubscriberID(info.CustomerID);
        payeeInfo.PayeeListID       = info.PayeeListID;
        payeeInfo.PayeeShortName    = CheckFreeConsts.MB_NULL_FIELD_VALUE;
    }

    protected  void payeeInfo2PayeeInfo( PayeeInfo info,
                                                   TypePayeeInfo payeeInfo )
    {
        payeeInfo.PayeeName1        = info.PayeeName;
        payeeInfo.PayeeName2        = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        payeeInfo.PayeeName3        = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        payeeInfo.PayeeAddr1        = info.Addr1;
        payeeInfo.PayeeAddr2        = ( info.Addr2==null )
                                      ?CheckFreeConsts.MB_NULL_FIELD_VALUE
                                      :info.Addr2;
        payeeInfo.Filler1       = CheckFreeConsts.MB_NULL_FIELD_VALUE; // become filler as CF changed spec
        payeeInfo.PayeeCity     = info.City;

        //Set default country
        if (info.Country==null || info.Country.length()<=0 ) {
            info.Country=CheckFreeConsts.DEFAULT_COUNTRY;
        }

        payeeInfo.PayeeState        = ( info.Country.equalsIgnoreCase( "USA" ) )//gotta change this later, because country is required.
                                      ?EnumStateCode.from_int(
                                                             ValueSetStateCode.getIndex( info.State ) )
                                      :EnumStateCode.Empty;
        if ( info.Zipcode!=null && info.Zipcode.length()>=5
             && info.Country.equalsIgnoreCase("USA") ) {
            payeeInfo.PayeeZip5 = info.Zipcode.substring(0,5);
            int dashIndex   = info.Zipcode.trim().indexOf('-');
            int spaceIndex  = info.Zipcode.trim().indexOf(' ');
            if ( (dashIndex == 5) ) {// 12345-6789 zip code format
                payeeInfo.PayeeZip4 = (info.Zipcode.trim().length()>=10)
                                      ? info.Zipcode.substring(6,10)
                                      :CheckFreeConsts.MB_NULL_FIELD_VALUE;
            } else if ( (spaceIndex == 5) ) {// 12345 6789 zip code format
                payeeInfo.PayeeZip4 = (info.Zipcode.trim().length()>=10)
                                      ? info.Zipcode.substring(6,10)
                                      :CheckFreeConsts.MB_NULL_FIELD_VALUE;
            } else { // maybe of format 123456789
                payeeInfo.PayeeZip4 = (info.Zipcode.trim().length()>=9)
                                      ? info.Zipcode.substring(5,9)
                                      :CheckFreeConsts.MB_NULL_FIELD_VALUE;
            }
        } else {
            payeeInfo.PayeeZip5 = CheckFreeConsts.MB_NULL_FIELD_VALUE;
            payeeInfo.PayeeZip4 = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        }
        payeeInfo.PayeeZip2         = CheckFreeConsts.MB_NULL_FIELD_VALUE;

        payeeInfo.PayeeCountryCode  = info.Country;
        payeeInfo.PayeeProvinceName = ( !info.Country.equalsIgnoreCase( "USA" )
                                        && info.State!=null )
                                      ?info.State
                                      :CheckFreeConsts.MB_NULL_FIELD_VALUE;
        payeeInfo.PayeePostalCode   = ( !info.Country.equalsIgnoreCase( "USA" )
                                        && info.State!=null )
                                      ?info.Zipcode
                                      :CheckFreeConsts.MB_NULL_FIELD_VALUE;
        payeeInfo.PayeePhoneNumber  = CheckFreeUtil.deleteSymbols( info.Phone );
        payeeInfo.Filler2       = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        payeeInfo.CategoryNumber    = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        payeeInfo.RS_Status     = EnumStatus_AI.Empty;  //supposed to be handled by BPW SERVER!!  don't know how!!
        payeeInfo.RS_ErrCode        = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        payeeInfo.RS_ErrMsg     = CheckFreeConsts.MB_NULL_FIELD_VALUE;

    }// payeeInfo2PayeeInfo

    protected  void customerPayeeInfo2PayeeAcctInfo ( CustomerPayeeInfo info,
                                                                TypePayeeAcctInfo payeeAcctInfo )
    {
        payeeAcctInfo.CspID     = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_SPONSOR_ID );
        payeeAcctInfo.SubID     = makeSubscriberID(info.CustomerID);
        payeeAcctInfo.PayeeListID   = info.PayeeListID;
        payeeAcctInfo.SubPayeeAcct  = info.PayAcct;
        payeeAcctInfo.Filler        = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        payeeAcctInfo.AcctDescrip   = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        payeeAcctInfo.Filler2       = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        payeeAcctInfo.OldAcct       = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        payeeAcctInfo.Filler3       = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        payeeAcctInfo.RS_Status     = EnumStatus_ACI.Empty;
        payeeAcctInfo.RS_ErrCode    = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        payeeAcctInfo.RS_ErrMsg     = CheckFreeConsts.MB_NULL_FIELD_VALUE;

    }//customerPayeeInfo2PayeeAcctInfo


    /**
     * Populate a TypePmtInfo object with the data to create a
     * Payment Information record ("4000" record).
     *
     * @param info
     * @param pmtInfo
     * @exception BPWException
     */
    protected void pmtInfo2PmtInfo ( PmtInfo info,
                                                TypePmtInfo pmtInfo,
                                                String cspPmtID )
    throws BPWException
    {
        //Payment information record "4000"
        pmtInfo.Info.CspID = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_SPONSOR_ID );
        pmtInfo.Info.SubID = makeSubscriberID(info.CustomerID);
        pmtInfo.Info.PayeeListID = info.PayeeListID;
        pmtInfo.Info.SubPayeeAcct = info.PayAcct;
        pmtInfo.Info.Filler = CheckFreeConsts.MB_NULL_FIELD_VALUE;

        pmtInfo.Info.CspPmtID = cspPmtID;
        pmtInfo.Info.CFPmtID = CheckFreeConsts.MB_NULL_FIELD_VALUE;

        // Map the account type: only checking and money market accepted
        if ( _bankAcctType.equalsIgnoreCase( "DDA" ) ) {
            pmtInfo.Info.DebitAcctType = EnumDebitAcctType.DemandDeposit;
        } else if ( _bankAcctType.equalsIgnoreCase( "MMA" ) ) {
            pmtInfo.Info.DebitAcctType = EnumDebitAcctType.MoneyMarket;
        } else {
            throw new BPWException ("Unsupported account type for CheckFree payments: "
                                    + _bankAcctType );
        }

        pmtInfo.Info.DebitRoutingNum = _bankRoutingNumStr;
        if (_isGoodFundsModel == true) {
            pmtInfo.Info.DebitAcct = _bankDesktopNum;
        } else {
            pmtInfo.Info.DebitAcct = info.AcctDebitID;
        }

        pmtInfo.Info.PmtCategory = CheckFreeConsts.MB_NULL_FIELD_VALUE;

        String pmtMemo = ( info.Memo == null )
                         ? CheckFreeConsts.MB_NULL_FIELD_VALUE
                         : info.Memo;
        if ( pmtMemo.length() > CheckFreeConsts.MB_PMTMEMO_FIELD_LENGTH ) {
            pmtMemo = pmtMemo.substring( 0, CheckFreeConsts.MB_PMTMEMO_FIELD_LENGTH );
        }
        pmtInfo.Info.PmtMemo = pmtMemo;

        pmtInfo.Info.PmtAmt = getLongValueFromBigDecimal(
        	BPWUtil.getBigDecimal(info.getAmt()), 5, 0);      
        
        // Format of startDate: YYYYMMDD00. divide by 100 to get YYYYMMDD
        pmtInfo.Info.ProcessDate = Integer.toString( DBUtil.getCurrentStartDate()/100 );

        pmtInfo.Info.AdditionalPmtInfo = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        pmtInfo.Info.ExtdPmtIndicator = EnumYesNoOpt.empty;
        pmtInfo.Info.ElectronicBillingTimeStamp = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        pmtInfo.Info.Filler2 = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        pmtInfo.Info.RS_ErrCode = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        pmtInfo.Info.RS_ErrMsg = CheckFreeConsts.MB_NULL_FIELD_VALUE;

        pmtInfo.Info.CheckNum = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        if (info.extraFields != null &&
            (info.extraFields instanceof HashMap)) {

            // Payment check number.
            HashMap extraFields = (HashMap)info.extraFields;
            String checkNum = (String)extraFields.get(PmtInfo.EXTRAFIELDS_HASHKEY_CHECKNUM);
            if (checkNum != null) {
                pmtInfo.Info.CheckNum = checkNum;
            }
        }
    }

    /**
     * Populate a TypePmtInfo object with the data to create a
     * Payment Invoice Information record ("4010" record).
     *
     * @param info
     * @param pmtInfo
     * @exception BPWException
     */
    protected void pmtInvoice2PmtInfo ( PmtInfo info,
                                                   TypePmtInfo pmtInfo,
                                                   String cspPmtID )
    throws BPWException
    {

        // Retrieve the PmtInvoice data from the PmtInfo object.
        if (info.extraFields == null) {
            // There is no data, so there can be no PmtInvoice record.
            pmtInfo.InvInfoExists = false;
            return;
        }
        HashMap extraFields = (HashMap)info.extraFields;
        PmtInvoice pmtInv = (PmtInvoice)extraFields.get(PmtExtraInfo.NAME_INVOICE);
        if (pmtInv == null) {
            // There is no data, so there can be no PmtInvoice record.
            pmtInfo.InvInfoExists = false;
            return;
        }

        // Map the invoice data to the PmtInfo record.

        // Signal the existance of additional payment information.
        pmtInfo.Info.ExtdPmtIndicator = EnumYesNoOpt.yes;

        // Pmt Invoice record "4010"
        pmtInfo.InvInfo = new TypeInvInfo();
        pmtInfo.InvInfoExists = true;

        pmtInfo.InvInfo.RecAction = EnumRecordAction_A.add;
        pmtInfo.InvInfo.CspID = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_SPONSOR_ID );
        pmtInfo.InvInfo.SubID = makeSubscriberID(info.CustomerID);
        pmtInfo.InvInfo.CspPmtID = cspPmtID;
        pmtInfo.InvInfo.CFPmtID = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        pmtInfo.InvInfo.InvNum = pmtInv.getInvoiceNum();

        long tmpLValue = 0L;
        String tmpAmt = "";

        if (pmtInv.getInvoiceSeqNum().equals("")) {
            pmtInfo.InvInfo.InvSeqNum = fillUpString(tmpAmt,
                                                     INV_SEQ_NUM_LENGTH,
                                                     ZERO_CHAR);
        } else {
            pmtInfo.InvInfo.InvSeqNum = pmtInv.getInvoiceSeqNum();
        }

        pmtInfo.InvInfo.VoucherNum = pmtInv.getVoucherNum();

        if (!BPWUtil.isZero(pmtInv.getTotalInvoiceAmount())) {
            tmpLValue = getLongValueFromBigDecimal(new BigDecimal(
            	pmtInv.getTotalInvoiceAmount()), 5, 0);
            tmpAmt = Long.toString(tmpLValue);
        } else {
            tmpAmt = "";
        }
        pmtInfo.InvInfo.TotInvAmt = fillUpString(tmpAmt,
                                                 AMOUNT_LENGTH,
                                                 ZERO_CHAR);

        pmtInfo.InvInfo.AmtPaid = getLongValueFromBigDecimal(new BigDecimal(
        	pmtInv.getAmountPaid()), 5, 0);

        pmtInfo.InvInfo.InvDescrip = pmtInv.getInvoiceDesc();
        pmtInfo.InvInfo.InvDate = pmtInv.getInvoiceDate();

        if (!BPWUtil.isZero(pmtInv.getDiscountRateStr())) {
            tmpLValue = getLongValueFromBigDecimal(new BigDecimal(
            	pmtInv.getDiscountRateStr()), 4, 0);
            tmpAmt = Long.toString(tmpLValue);
        } else {
            tmpAmt = "";
        }
        pmtInfo.InvInfo.DiscRate = fillUpString(tmpAmt,
                                                RATE_LENGTH,
                                                ZERO_CHAR);

        if (!BPWUtil.isZero(pmtInv.getDiscountAmount())) {
            tmpLValue = getLongValueFromBigDecimal(new BigDecimal(
            	pmtInv.getDiscountAmount()), 5, 0);
            tmpAmt = Long.toString(tmpLValue);
        } else {
            tmpAmt = "";
        }
        pmtInfo.InvInfo.DiscAmt = fillUpString(tmpAmt,
                                               AMOUNT_LENGTH,
                                               ZERO_CHAR);

        pmtInfo.InvInfo.DiscDescrip = pmtInv.getDiscountDesc();
        pmtInfo.InvInfo.DiscDate = pmtInv.getDiscountDate();

        if (!BPWUtil.isZero(pmtInv.getAdjustmentAmount())) {
            tmpLValue = getLongValueFromBigDecimal(new BigDecimal(
            	pmtInv.getAdjustmentAmount()), 5, 0);
            tmpAmt = Long.toString(tmpLValue);
        } else {
            tmpAmt = "";
        }
        pmtInfo.InvInfo.AdjAmt = fillUpString(tmpAmt,
                                              AMOUNT_LENGTH,
                                              ZERO_CHAR);

        pmtInfo.InvInfo.AdjDate = pmtInv.getAdjustmentDate();
        pmtInfo.InvInfo.AdjDescrip = pmtInv.getAdjustmentDesc();

        pmtInfo.InvInfo.RS_ErrCode = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        pmtInfo.InvInfo.RS_ErrMsg = CheckFreeConsts.MB_NULL_FIELD_VALUE;
    }       
    
    protected long getLongValueFromBigDecimal(BigDecimal bdValue, int shift, int scale) {
        bdValue = bdValue.movePointRight(shift);
        bdValue = FFSUtil.setScale(bdValue, scale);
        long newLongValue = bdValue.longValue();
        newLongValue = (newLongValue + 5) / 10L;

        return newLongValue;
    }

    protected void customerInfo2BankInfo( CustomerInfo info,
                                                     TypeBankAcctInfo bInfo )
    throws BPWException
    {
        bInfo.CspID         = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_SPONSOR_ID );
        bInfo.SubID         = makeSubscriberID(info.customerID);
        bInfo.SrvcCode          = EnumServiceCode_BBB.BPP;
        bInfo.BankRoutingNum        = _bankRoutingNum;  //retrieved from database by xin
        bInfo.BankAcctDesktopNum    = _bankDesktopNum;  //retrieved from database by xin
        if ( _bankAcctType.equalsIgnoreCase( "DDA" ) ) {
            bInfo.BankAcctType      = EnumBankAcctType.DemandDeposit;
        } else if ( _bankAcctType.equalsIgnoreCase( "MMA" ) ) {
            bInfo.BankAcctType      = EnumBankAcctType.MoneyMarket;
        } else {
            throw new BPWException ( "Unsupported account type for CheckFree: "
                                     + _bankAcctType );
        }
        bInfo.PaperRoutingNum       = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.PaperAcct         = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.ElectronicRoutingNum  = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.ElectronicAcct        = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.AcctShortName     = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.PanAtmAcct        = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.PanExpDate        = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.StartingCheckNum      = CheckFreeConsts.STARTING_CHECK_NUM;
        bInfo.Name1         = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.Name2         = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.BusName           = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.Addr1         = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.Addr2         = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.City          = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.State         = EnumStateCode.Empty;
        bInfo.Zip5          = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.Zip4          = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.Zip2          = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.CountryCode       = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.PostalCode        = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.ProvinceName      = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.AcctDescription       = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.DownloadFlag      = EnumYesNoOpt.empty;
        bInfo.XferSrcAcctFlag       = EnumYesNoOpt.empty;
        bInfo.XferDestAcctFlag      = EnumYesNoOpt.empty;
        bInfo.AcctPriorityCode      = EnumAcctPriorityCode.Empty;
        bInfo.Filler            = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.AcctPhone         = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.RS_AcctStatus     = EnumStatus_AI.Empty;
        bInfo.RS_ErrCode        = CheckFreeConsts.MB_NULL_FIELD_VALUE;
        bInfo.RS_ErrMsg         = CheckFreeConsts.MB_NULL_FIELD_VALUE;


    }// customerInfo2BankInfo

    protected boolean newPayee( PayeeInfo info ) {
        return info.Status.equalsIgnoreCase("new");
    }// newPayee()


    //////////////////////////////////////////////////////////////////////////
    // Proccess an Srvc Trans response file from CheckFree
    //////////////////////////////////////////////////////////////////////////
    public static final boolean processSrvcTransRSFile( File file, FFSConnectionHolder dbh)
    throws Exception
    {
        boolean success = true;
        TypeSrvcTrans srvcTransRS = null;

        try {
            srvcTransRS = parseSrvcTransRSFile( file );
        } catch (Exception e ) {
            throw e;
        }
        int batchSize = _propConfig.getBatchSize();
        
        if ( srvcTransRS!=null ) {
            // process header and trailer
            try {
                processSrvcTransHeader( srvcTransRS.STHeader, dbh );
                processSrvcTransTrailer( srvcTransRS.STTrailer, dbh );
            } catch ( Exception e ) {
                CheckFreeUtil.warn( "Cannot continue processing Srvc Trans "
                                    + "file: " + e.getMessage() );
                throw e;
            }
            
            int processedBatchesCount = 1; // number of batches processed up to now
            int processedRecordsCount = 0; // number of records processed up to now

            // process each record
            if ( srvcTransRS.SubInfoExists ) {
                
                CheckFreeUtil.forcedLog("processSrvcTransRSFile(): Start processing Subscriber records in Service "+
                    "and Transaction File: "+ file.getName()+". Total Number of subscriber records "+
                    "in this file = "+srvcTransRS.SubInfo.length);
                
                for ( int i = 0; i < srvcTransRS.SubInfo.length; i++ ) {
                    try {
                        processSrvcTransSubInfo( srvcTransRS.SubInfo[i],
                                                 dbh );
                    } catch ( ResponseRecordException e ) {
                        CheckFreeUtil.warn( "Error processing Subscriber "
                                            + "Info record with ConsumerID="
                                            + e.getRecordID() + ": "
                                            + e.getMessage() );
                        success = false;
                    }
                    processedRecordsCount ++; // increase number of processed 
                                                  // records by one
                    //If we processed a batch size of records, commit the database
                    //transaction and reset the counter
                    if (processedRecordsCount >= batchSize) {
                       	dbh.conn.commit();

                        CheckFreeUtil.forcedLog("processSrvcTransRSFile(): Finished processing batch # " + 
                                         (processedBatchesCount++)+ ".Total number of " +
                                         "Subscriber records processed: " + processedRecordsCount );
                        processedRecordsCount = 0; // reset the records counter
                    }
                    
                }
             
                dbh.conn.commit();
                processedBatchesCount = 1; // reset the batch counter
                CheckFreeUtil.forcedLog("processSrvcTransRSFile(): Finished processing " +
		                "Subscriber records in Service "+
                        "and Transaction File: "+ file.getName()+ 
                        ". Total Number of subscriber records in this file = "+
                        srvcTransRS.SubInfo.length);
            }

            if ( srvcTransRS.BankAcctInfoExists ) {
                
                CheckFreeUtil.forcedLog("processSrvcTransRSFile(): Start processing Bank Account records in Service "+
                        "and Transaction File: "+ file.getName()+". Total Number of Bank Account records  "+
                        "in this file = "+srvcTransRS.BankAcctInfo.length);

                processedBatchesCount = 1; // reset counter            
            	processedRecordsCount = 0; // reset the records counter

                for ( int i = 0; i < srvcTransRS.BankAcctInfo.length; i++ ) {
                    try {
                        processSrvcTransBankAcctInfo(
                                                    srvcTransRS.BankAcctInfo[i],
                                                    dbh );
                    } catch ( ResponseRecordException e ) {
                        CheckFreeUtil.warn( "Error processing Bank Acct "
                                            + "Info record with ConsumerID="
                                            + e.getRecordID() + ": "
                                            + e.getMessage() );
                        success = false;
                    }
                    processedRecordsCount ++; // increase number of processed 
                    // records by one
                    //If we processed a batch size of records, commit the database
                    //transaction and reset the counter
                    if (processedRecordsCount >= batchSize) {
                        dbh.conn.commit();

                        CheckFreeUtil.forcedLog("processSrvcTransRSFile(): Finished processing batch # " + 
                                    		(processedBatchesCount++)+ ".Total number of " +
                                    		"Bank Account records processed: " + processedRecordsCount );
                        processedRecordsCount = 0; // reset the records counter
                    }
                    
                }
                
                dbh.conn.commit();
                processedBatchesCount = 1; // reset the batch counter           
                CheckFreeUtil.forcedLog("processSrvcTransRSFile(): Finished processing Bank Account records in Service "+
                                  "and Transaction File: "+ file.getName()+ 
                                  ". Total Number of Bank Account records in this file = "+
                                  srvcTransRS.BankAcctInfo.length);
            }

            if ( srvcTransRS.SrvcActInfoExists ) {

                CheckFreeUtil.forcedLog("processSrvcTransRSFile(): Start processing Subscriber Account records in Service "+
                        "and Transaction File: "+ file.getName()+". Total Number of Subscriber Account records  "+
                        "in this file = "+srvcTransRS.SrvcActInfo.length);

                processedBatchesCount = 1; // reset counter            
            	processedRecordsCount = 0; // reset the records counter

                for ( int i = 0; i < srvcTransRS.SrvcActInfo.length; i++ ) {
                    try {
                        processSrvcTransSrvcActInfo(
                                                   srvcTransRS.SrvcActInfo[i],
                                                   dbh );
                    } catch ( ResponseRecordException e ) {
                        CheckFreeUtil.warn( "Error processing Srvc Activation "
                                            + "Info record with ConsumerID="
                                            + e.getRecordID() + ": "
                                            + e.getMessage() );
                        success = false;
                    }
                    processedRecordsCount ++; // increase number of processed 
                    // records by one
                    //If we processed a batch size of records, commit the database
                    //transaction and reset the counter
                    if (processedRecordsCount >= batchSize) {
                        dbh.conn.commit();

                        CheckFreeUtil.forcedLog("processSrvcTransRSFile(): Finished processing batch # " + 
                                    		(processedBatchesCount++)+ ".Total number of " +
                                    		"Subscriber Account records processed: " + processedRecordsCount );
                        processedRecordsCount = 0; // reset the records counter
                    }
                        
                    
                }
                
                dbh.conn.commit();
                processedBatchesCount = 1; // reset the batch counter           
                CheckFreeUtil.forcedLog("processSrvcTransRSFile(): Finished processing Subscriber Account records in Service "+
                                  "and Transaction File: "+ file.getName()+ 
                                  ". Total Number of Subscriber Account records in this file = "+
                                  srvcTransRS.SrvcActInfo.length);
            }

            if ( srvcTransRS.PayeeInfoExists ) {
                
                CheckFreeUtil.forcedLog("processSrvcTransRSFile(): Start processing Payee records in Service "+
                        "and Transaction File: "+ file.getName()+". Total Number of Payee records  "+
                        "in this file = "+srvcTransRS.PayeeInfo.length);

                processedBatchesCount = 1; // reset counter            
            	processedRecordsCount = 0; // reset the records counter
            	
                for ( int i = 0; i < srvcTransRS.PayeeInfo.length; i++ ) {
                    try {
                        processSrvcTransPayeeInfo( srvcTransRS.PayeeInfo[i],
                                                   dbh );
                    } catch ( ResponseRecordException e ) {
                        CheckFreeUtil.warn( "Error processing Payee Info "
                                            + "record with "
                                            + e.getRecordID() + ": "
                                            + e.getMessage() );
                        success = false;
                    }   
                    processedRecordsCount ++; // increase number of processed 
                    // records by one
                    //If we processed a batch size of records, commit the database
                    //transaction and reset the counter
                    if (processedRecordsCount >= batchSize) {
                        dbh.conn.commit();

                        CheckFreeUtil.forcedLog("processSrvcTransRSFile(): Finished processing batch # " + 
                                    		(processedBatchesCount++)+ ".Total number of " +
                                    		"Payee records processed: " + processedRecordsCount );
                        processedRecordsCount = 0; // reset the records counter
                    }
                    
                }
                
                dbh.conn.commit();
                processedBatchesCount = 1; // reset the batch counter           
                CheckFreeUtil.forcedLog("processSrvcTransRSFile(): Finished processing Payee records in Service "+
                                  "and Transaction File: "+ file.getName()+ 
                                  ". Total Number of Payee records in this file = "+
                                  srvcTransRS.PayeeInfo.length);
            }

            if ( srvcTransRS.PayeeAcctInfoExists ) {
                
                CheckFreeUtil.forcedLog("processSrvcTransRSFile(): Start processing Payee Account records in Service "+
                        "and Transaction File: "+ file.getName()+". Total Number of Payee Account records  "+
                        "in this file = "+srvcTransRS.PayeeAcctInfo.length);

                processedBatchesCount = 1; // reset counter            
            	processedRecordsCount = 0; // reset the records counter
            	
                for ( int i = 0; i < srvcTransRS.PayeeAcctInfo.length; i++ ) {
                    try {
                        processSrvcTransPayeeAcctInfo(
                                                     srvcTransRS.PayeeAcctInfo[i],
                                                     dbh);
                    } catch ( ResponseRecordException e ) {
                        CheckFreeUtil.warn( "Error processing Payee Acct "
                                            + "Info record with "
                                            + e.getRecordID() + ": "
                                            + e.getMessage() );
                        success = false;
                    }
                    processedRecordsCount ++; // increase number of processed 
                    // records by one
                    //If we processed a batch size of records, commit the database
                    //transaction and reset the counter
                    if (processedRecordsCount >= batchSize) {
                        dbh.conn.commit();

                        CheckFreeUtil.forcedLog("processSrvcTransRSFile(): Finished processing batch # " + 
                                    		(processedBatchesCount++)+ ".Total number of " +
                                    		"Payee Account records processed: " + processedRecordsCount );
                        processedRecordsCount = 0; // reset the records counter
                    }
                        
                    
                }
                
                dbh.conn.commit();
                processedBatchesCount = 1; // reset the batch counter           
                CheckFreeUtil.forcedLog("processSrvcTransRSFile(): Finished processing Payee Account records in Service "+
                                  "and Transaction File: "+ file.getName()+ 
                                  ". Total Number of Payee Account records in this file = "+
                                  srvcTransRS.PayeeAcctInfo.length);
            }

            if ( srvcTransRS.PmtInfoExists ) {
                
                CheckFreeUtil.forcedLog("processSrvcTransRSFile(): Start processing Payment records in Service "+
                        "and Transaction File: "+ file.getName()+". Total Number of Payment records  "+
                        "in this file = "+srvcTransRS.PmtInfo.length);

                processedBatchesCount = 1; // reset counter            
            	processedRecordsCount = 0; // reset the records counter
            	
                for ( int i = 0; i < srvcTransRS.PmtInfo.length; i++ ) {
                    try {
                        processSrvcTransPmtInfo( srvcTransRS.PmtInfo[i],
                                                 dbh );
                    } catch ( ResponseRecordException e ) {
                        CheckFreeUtil.warn( "Error processing Payment Info "
                                            + "record with SrvrTID="
                                            + e.getRecordID() + ": "
                                            + e.getMessage() );
                        success = false;
                    }    
                    processedRecordsCount ++; // increase number of processed 
                    // records by one
                    //If we processed a batch size of records, commit the database
                    //transaction and reset the counter
                    if (processedRecordsCount >= batchSize) {
                        dbh.conn.commit();

                        CheckFreeUtil.forcedLog("processSrvcTransRSFile(): Finished processing batch # " + 
                                    		(processedBatchesCount++)+ ".Total number of " +
                                    		"Payment records processed: " + processedRecordsCount );
                        processedRecordsCount = 0; // reset the records counter
                    }
                        
                    
                }
                dbh.conn.commit();
                processedBatchesCount = 1; // reset the batch counter           
                CheckFreeUtil.forcedLog("processSrvcTransRSFile(): Finished processing Payment records in Service "+
                                  "and Transaction File: "+ file.getName()+ 
                                  ". Total Number of Payment records in this file = "+
                                  srvcTransRS.PmtInfo.length);
            }
        }

        return success;
    }

    protected static final TypeSrvcTrans parseSrvcTransRSFile( File file )
    throws Exception
    {
        FileInputStream in = null;
        byte[] buff;
        Object message = null;

        try {
            in = new FileInputStream( (File)file );
            buff = new byte[ in.available() ];
            in.read( buff );
            message = _mb.parseMsg(
                                  new String( buff),
                                  CheckFreeConsts.MB_SRVC_TRANS_MESSAGE_NAME,
                                  CheckFreeConsts.MB_MESSAGE_SET_NAME,
                                  _debug );

        } catch ( Exception e ) {
            FFSDebug.log ( "*** CheckFree Adapter: Error when processing srvc trans file "
                           + file.getName()
                           + " processing canceled." ,FFSDebug.PRINT_ERR);
            throw e;
        } finally {
            try {
                if ( in!=null ) in.close();
                in = null;
            } catch ( Exception e ) {
                // Ignore
            }
        }

        return(TypeSrvcTrans)message;
    }

    /**
     * Check the error code of the Header record in the
     * SIS Echo file.
     *
     * @param head
     * @param dbh    A database connection holder that has the database
     *               connection that we can use for database operations
     *               within the current transaction.
     * @exception Exception
     */
    protected static void processSrvcTransHeader( TypeSTHeader head, FFSConnectionHolder dbh )
    throws Exception
    {
        if ( !head.RS_ErrCode.equals( "00000" ) ) {
            // errors in the header are evil.  throw a general exception
            //  to indicate a problem with the entire file.
            throw new Exception( "Header failed" );
        }
    }

    /**
     * Process the results of a Subscriber Information record.
     *
     * This method is typically invoked during SIS Echo file
     * processing. However, it may also be called by the SIS
     * request file generation to handle errors encountered
     * during data mapping or MB building.
     *
     * @param si     A TypeSubInfo object representing the Subscriber
     *               Information record whose results are to be processed.
     *               Minimum field requirements include: CspID, SubID,
     *               RecAction, and RS_ErrCode.
     * @param dbh    A database connection holder that has the database
     *               connection that we can use for database operations
     *               within the current transaction.
     * @exception ResponseRecordException
     */
    protected static void processSrvcTransSubInfo( TypeSubInfo si,
                                                 FFSConnectionHolder dbh 
                                                 )
    throws ResponseRecordException
    {
        try {
            // check the CSP ID
            if ( !CheckFreeUtil.isCspIDEqual( si.CspID ) ) {
                throw new Exception( "Wrong CSP ID" );
            }

            String status = null;
            // check error code
            if ( si.RS_ErrCode.equals( "00000" ) ) {
                switch ( si.RecAction.value() ) {
                    case EnumRecordAction_ACI._add:
                        
                        status = DBConsts.ACTIVE;
                        break;
                    case EnumRecordAction_ACI._change:
                        // cust mod successful
                        status = DBConsts.ACTIVE;
                        break;
                    case EnumRecordAction_ACI._inactivate:
                        // canc customer successful
                        try{
                            CheckFreeDBAPI.deleteCustomerWithRouteID( subID2CustomerID( si.SubID ),
                                                                  _routeID,
                                                                  dbh );
                        } catch ( BPWException be ) {
                            // Ignore this exception if the reason for the
                            // exception is that the customer route not found and
                            // BPW Server is doing crash recovery and the action
                            // for this subscriberin CheckFree file is INACTIVE
                            if (_possibleDuplicate && be.getErrorCode() ==
                                    CheckFreeConsts.CUSTOMER_ROUTE_NOT_FOUND) {
                       	         FFSDebug.log(be.getMessage(),FFSDebug.PRINT_WRN);
                            } else {
                            	throw new ResponseRecordException(be.getMessage(),subID2CustomerID( si.SubID ));
                            }
                       } catch (Exception e) {
                           FFSDebug.log(FFSDebug.stackTrace(e),FFSDebug.PRINT_ERR);
                       	   throw new ResponseRecordException(e.getMessage(),subID2CustomerID( si.SubID ));
                       }
    
                        break;
                    default:
                        throw new Exception( "Record action is invalid" );
                }
            } else {
                FFSDebug.log("CheckFree Adapter: Enrollment fails! Error code=" +
                             si.RS_ErrCode + ", Subscriber ID=" + si.SubID);
                switch ( si.RecAction.value() ) {
                    case EnumRecordAction_ACI._add:
                        //Error 10519 and 00103 means status is already ACTIVE.
                        //Error 00086 means The request file contains enrollment information 
                        //for a subscriber who is already in CheckFree's database
                        //Error 13501 means subscriber information already exists in CheckFree's database
                        CustomerRouteInfo crInfo = CustRoute.
                                      getCustomerRoute(subID2CustomerID( si.SubID ),_routeID, dbh);
                    
	                    if( si.RS_ErrCode.equals( "10519" ) ||
	                        si.RS_ErrCode.equals( "13501" ) ||
	                        si.RS_ErrCode.equals( "00086" ) ||
	                        si.RS_ErrCode.equals( "00103" ) ) {
	                        
	                        if(crInfo.Status != null && (crInfo.Status.equals(DBConsts.INPROCESS)== false)){
                                //subscriber already exists in the database. Exit and do nothing
	                            return;
	                        }
	                    }
                        status = DBConsts.FAILEDON;
                        
                        break;
                    case EnumRecordAction_ACI._change:
                        // mod customer failed
                        status = DBConsts.FAILEDON;
                        break;
                    case EnumRecordAction_ACI._inactivate:
                        // canc customer failed
                        status = DBConsts.FAILEDON;
                        break;
                    default:
                        throw new Exception( "Record action is invalid" );
                }
            }

            // update database
            if (status != null) {
                CheckFreeDBAPI.updateCustomerStatusWithRouteID( subID2CustomerID( si.SubID ),
                                                                _routeID,
                                                                status,
                                                                dbh );
            }

        } catch ( Exception e ) {
            throw new ResponseRecordException( e.getMessage(), subID2CustomerID( si.SubID ) );
        }
    }

    /**
     * Process the results of a Bank Account Information record.
     *
     * This method is typically invoked during SIS Echo file
     * processing. However, it may also be called by the SIS
     * request file generation to handle errors encountered
     * during data mapping or MB building.
     *
     * @param bai    A TypeBankAcctInfo object representing the Bank Account
     *               Information record whose results are to be processed.
     * @param dbh    A database connection holder that has the database
     *               connection that we can use for database operations
     *               within the current transaction.
     * @exception ResponseRecordException
     */
    protected static void processSrvcTransBankAcctInfo( TypeBankAcctInfo bai,
                                                      FFSConnectionHolder dbh )
    throws ResponseRecordException
    {
        // handle in SrvcActInfo
    }

    /**
     * Action:
     *    ADD: Customer Add
     *
     * @param sai
     * @param dbh    A database connection holder that has the database
     *               connection that we can use for database operations
     *               within the current transaction.
     * @exception ResponseRecordException
     */
    protected static void processSrvcTransSrvcActInfo( TypeSrvcActInfo sai,
                                                     FFSConnectionHolder dbh )
    throws ResponseRecordException
    {
        /*
        try {
            // check the CSP ID
            if ( !CheckFreeUtil.isCspIDEqual( sai.CspID ) ) {
                throw new Exception( "Wrong CSP ID" );
            }

            // check the record action
            if ( sai.RecAction.value() != EnumRecordAction_AI._add ) {
                throw new Exception( "Record action is invalid" );
            }

            String status = null;

            // check error code
            if ( sai.RS_ErrCode.equals( "00000" ) ) {
                // add customer successful
                status = DBConsts.ACTIVE;
            }
            else {
                // add customer failed
                CustomerInfo ci = CheckFreeDBAPI.getCustomerInfo( subID2CustomerID( sai.SubID ),
                                                                  dbh );
                if ( ci.status.equals( DBConsts.INPROCESS ) ) {
                    status = DBConsts.FAILEDON;
                }
                else if ( ci.status.equals( DBConsts.CANC_INPROCESS ) ) {
                    // do nothing
                    return;
                }
                else {
                    throw new Exception( "The customer's status is invalid" );
                }
                status = DBConsts.FAILEDON;
            }

            // update database
            if (status != null) {
                //if ( _propConfig.EnforcePayment ) {
                    CheckFreeDBAPI.updateCustomerStatusWithRouteID( subID2CustomerID( sai.SubID ),
                                                                    _routeID,  status,  dbh );
                //}
                //else
                //    CheckFreeDBAPI.updateCustomerStatus( subID2CustomerID( sai.SubID ), status, dbh);
            }


        }
        catch ( Exception e ) {
            throw new ResponseRecordException( e.getMessage(), subID2CustomerID( sai.SubID ) );
        }
        */
    }

    /**
     * Process the results of a Payee Information record.
     *
     * This method is typically invoked during SIS Echo file
     * processing. However, it may also be called by the SIS
     * request file generation to handle errors encountered
     * during data mapping or MB building.
     *
     * @param pi     A TypePayeeInfo object representing the Payee Information
     *               record whose results are to be processed. Minimum field
     *               requirements include: CspID, SubID, PayeeListID,
     *               RecAction, RS_ErrCode, and RS_ErrMsg.
     * @param dbh    A database connection holder that has the database
     *               connection that we can use for database operations
     *               within the current transaction.
     * @param cancPmtsOnFailure
     * @exception ResponseRecordException
     */
    protected static void processSrvcTransPayeeInfo( TypePayeeInfo pi,
                                                   FFSConnectionHolder dbh )
    throws ResponseRecordException
    {
        try {
            // check the CSP ID
            if ( !CheckFreeUtil.isCspIDEqual( pi.CspID ) ) {
                throw new Exception( "Wrong CSP ID" );
            }

            String status  = null;
            String errMsg  = null;
            int    errCode = DBConsts.STATUS_OK;

            // check error code
            if ( pi.RS_ErrCode.equals( "00000" ) ) {
                switch ( pi.RecAction.value() ) {
                    case EnumRecordAction_ACC._add:
                    case EnumRecordAction_ACC._change:
                        // add/mod custpayee successful
                        status = DBConsts.ACTIVE;
                        break;
                    case EnumRecordAction_ACC._cancel:
                        // canc custpayee successful
                        //status = DBConsts.CLOSED;
                        return;
                    default:
                        throw new Exception( "Record action is invalid" );
                }
            } else {
                if (pi.RecAction.value() == EnumRecordAction_ACC._cancel)
                    return;
                if(pi.RecAction.value() == EnumRecordAction_ACC._add){
                    
                    
                    //Error 00203 means payee that already exists in the
                    //Subscriber�s payee list
                    //Error 01032 means payee already exists in CheckFree's database
                    //Error 01053 means payee is already ACTIVE in CheckFree's database
                    //Error 01060 means payee already exists in CheckFree's database 
                    
                    //get the CustPayeeRoute
                    CustPayeeRoute custPayeeRoute = CustPayeeRoute.getCustPayeeRoute2(subID2CustomerID(pi.SubID),
                            (int)pi.PayeeListID,_routeID, dbh);
                    
                    
                    if ( pi.RS_ErrCode.equals( "00203" ) ||
                         pi.RS_ErrCode.equals( "01032" ) ||
                         pi.RS_ErrCode.equals( "01053" ) ||
                         pi.RS_ErrCode.equals( "01060" ) ) {
                        if(custPayeeRoute.Status != null && 
                                (custPayeeRoute.Status.equals(DBConsts.INPROCESS) == false)){
                            //Duplicate CustPayee. exit and do nothing
                            return;
                        }
                    }
                    
                }
	            // add/mod/del custpayee failed
	            status  = DBConsts.FAILEDON;
	            errMsg  = pi.RS_ErrMsg;
	            errCode = DBConsts.STATUS_GENERAL_ERROR;
                    
                    
               
            }

            // update database
            CheckFreeDBAPI.updateCustPayeeStatus( subID2CustomerID(pi.SubID),
                                                  pi.PayeeListID, _routeID,
                                                  status, errCode, errMsg,
                                                  dbh );

        } catch ( Exception e ) {
            String id = "SubID=" + subID2CustomerID( pi.SubID ) + " PayeeListID="
                        + pi.PayeeListID;
            throw new ResponseRecordException( e.getMessage(), id );
        }
    }

    /**
     * Process the results of a Payee Account Information
     * record.
     *
     * This method is typically invoked during SIS Echo file
     * processing. However, it may also be called by the SIS
     * request file generation to handle errors encountered
     * during data mapping or MB building.
     *
     * @param pai    A TypePayeeAcctInfo object representing the Payee Account
     *               Information record whose results are to be processed.
     *               Minimum field requirements include: CspID, SubID,
     *               PayeeListID, RecAction, RS_ErrCode, and RS_ErrMsg.
     * @param dbh    A database connection holder that has the database
     *               connection that we can use for database operations
     *               within the current transaction.
     * @param cancPmtsOnFailure
     * @exception ResponseRecordException
     */
    protected static void processSrvcTransPayeeAcctInfo( TypePayeeAcctInfo pai,
                                                       FFSConnectionHolder dbh )
    throws ResponseRecordException
    {
        try {
            // check the CSP ID
            if ( !CheckFreeUtil.isCspIDEqual( pai.CspID ) ) {
                throw new Exception( "Wrong CSP ID" );
            }

            String status  = null;
            String errMsg  = null;
            int    errCode = DBConsts.STATUS_OK;

            // check error code
            if ( pai.RS_ErrCode.equals( "00000" ) ) {
                switch ( pai.RecAction.value() ) {
                    case EnumRecordAction_ACC._add:
                    case EnumRecordAction_ACC._change:
                        // add/mod custpayee successful
                        status = DBConsts.ACTIVE;
                        break;
                    case EnumRecordAction_ACC._cancel:
                        // canc custpayee successful
                        //status = DBConsts.CLOSED;
                        return;
                    default:
                        throw new Exception( "Record action is invalid" );
                }
            } else {
                if (pai.RecAction.value() == EnumRecordAction_ACC._cancel)
                    return;
                if(pai.RecAction.value() == EnumRecordAction_ACC._add){
                    
                    //Error 01062,00247 and 00846 means Payee account already exists in CheckFree's databas 
                    //Error 01078 means duplicate Payee account.The payee account already 
                    //exists in CheckFree's database 
                    
                    //Error 00853and 00873 means bank account that already exists for the subscriber
                    
                    //get the CustPayeeRoute
                    CustPayeeRoute custPayeeRoute = CustPayeeRoute.getCustPayeeRoute2(subID2CustomerID(pai.SubID),
                            (int)pai.PayeeListID,_routeID, dbh);
                    
                    if (( pai.RS_ErrCode.equals( "01062" ) ||
                           pai.RS_ErrCode.equals( "01078" ) ||
                           pai.RS_ErrCode.equals( "00247" ) ||
                           pai.RS_ErrCode.equals( "00853" ) ||
                           pai.RS_ErrCode.equals( "00873" ) ||
                           pai.RS_ErrCode.equals( "00846" ) ) ) {
                        
                        if(custPayeeRoute.Status != null && 
                                (custPayeeRoute.Status.equals(DBConsts.INPROCESS) == false)){
                            //Duplicate CustPayee. exit and do nothing
                            return;
                        }
                    }
                    
                }
	            // add/mod/del custpayee failed
	            status  = DBConsts.FAILEDON;
	            errMsg  = pai.RS_ErrMsg;
	            errCode = DBConsts.STATUS_GENERAL_ERROR;
                
            }

            // update database
            CheckFreeDBAPI.updateCustPayeeStatus( subID2CustomerID(pai.SubID),
                                                  pai.PayeeListID, _routeID,
                                                  status, errCode, errMsg,
                                                  dbh );

        } catch ( Exception e ) {
            String id = "SubID=" + subID2CustomerID( pai.SubID ) + " PayeeListID="
                        + pai.PayeeListID;
            throw new ResponseRecordException( e.getMessage(), id );
        }
    }

    /**
     * Process the results of a Payment Information record.
     *
     * This method is typically invoked during SIS Echo file
     * processing. However, it may also be called by the SIS
     * request file generation to handle errors encountered
     * during data mapping or MB building.
     *
     * This method only handles RecActions of type "add".
     *
     * @param pi     A TypePmtInfo object representing the Payment Information
     *               record whose results are to be processed.
     * @param dbh    A database connection holder that has the database
     *               connection that we can use for database operations
     *               within the current transaction..
     * @exception ResponseRecordException
     */
    protected static void processSrvcTransPmtInfo( TypePmtInfo pi, FFSConnectionHolder dbh )
    throws ResponseRecordException
    {
        String stid = null;
        try {
            // check CSP ID
            if ( !CheckFreeUtil.isCspIDEqual( pi.Info.CspID ) ) {
                throw new Exception( "Wrong CSP ID" );
            }

            // check action
            if ( pi.Info.RecAction.value() != EnumRecordAction_ACC._add ) {
                throw new Exception( "Record action is invalid" );
            }

            // obtain the SrvrTID associated with this CspPmtID
            // Note:    In the original design, the CspPmtID = SrvrTID.
            //          After the Payment Invoice Record 4010 is introduced,
            //          the CspPmtID now has the format YYYYMMDDHHMMSSssssss.

            stid = CheckFreeDBAPI.getSrvrTIDByLocalPmtID(pi.Info.CspPmtID, dbh);
            if (stid == null) {
                // This is for backward compatability
                // if the srvrTID is not found, then set it to CspPmtID
                stid = pi.Info.CspPmtID;
            }

            // init to successful state
            PmtInfo info = CheckFreeDBAPI.getPmtInfo( stid, dbh );
            PmtTrnRslt rslt = new PmtTrnRslt( info.CustomerID,
                                              info.SrvrTID,
                                              DBConsts.STATUS_OK,
                                              CheckFreeConsts.MSG_STATUS_OK,
                                              info.ExtdPmtInfo );
            CheckFreeUtil.log("pi.Info.RS_ErrCode="+pi.Info.RS_ErrCode+",info.Status="+info.Status, FFSDebug.PRINT_DEV);
            rslt.logID = info.LogID;

            //If we are doing crash recovery and the payment status is already 
            //updated (e.g. POSTEDON or FAILEDON) do nothing
            if (_possibleDuplicate && !info.Status.equals(DBConsts.BATCH_INPROCESS)){
               //If the status of the payment received from CheckFree is 
               //identical to the status of the payment in BPW database
               //do nothing if BPW Server is doing a crash recovery
                                   
                if (DBConsts.PROCESSEDON.equals(info.Status) || DBConsts.FAILEDON.equals(info.Status)) {//status from CheckFree matched BPW database status
         	         return; //No need to update since its already updated
                } else {
         	         CheckFreeUtil.forcedLog("Since status from CheckFree does not match BPW database status " +
         	         		"this condition may not be a result of a previous processing");
                }
         	} 

            String s = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_IMPORT_REPEATABLE, DBConsts.FALSE );
            boolean allowRepeat = s.equalsIgnoreCase( DBConsts.TRUE );
            if (!allowRepeat && !info.Status.equals(DBConsts.BATCH_INPROCESS)) {
                CheckFreeUtil.warn( "Duplicate payment processing not allowed, SrvrTID="
                                    + info.SrvrTID );
                return;
            }

            if ( pi.Info.RS_ErrCode.equals( "01022" ) && !info.Status.equals(DBConsts.BATCH_INPROCESS) ) { // duplicate payment
                // exit and do nothing
                CheckFreeUtil.warn( "Duplicate payment not processed, SrvrTID="
                                    + info.SrvrTID + ",Status=" + info.Status);
                return;
            }
            if ( !pi.Info.RS_ErrCode.equals( "00000" ) ) {
                // add payment failed
                rslt.status  = DBConsts.STATUS_GENERAL_ERROR;
                rslt.message = pi.Info.RS_ErrMsg;
            }
            
                // either normal processing or crash recovery processing for
                // records that was not processed before 
            // update the database
            _backendProcessor.processOnePmtRslt(rslt, info.LogID, info.Status, info.FIID, dbh);
            try {
                // insert a record in PmtHist table
                if (stid != null && stid.length() != 0 &&
                    pi.Info.CFPmtID != null &&  pi.Info.CFPmtID.length() != 0) {
                    CheckFreeDBAPI.insertCfPmtIDIntoPmtHist(stid, pi.Info.CFPmtID, dbh);
                }
            } catch (Exception ex) {
                CheckFreeUtil.log( "*** CheckFreeHandler.insertPmtHist failed: " + FFSDebug.stackTrace(ex) , FFSConst.PRINT_ERR);
            }

        } catch ( Exception e ) {
            throw new ResponseRecordException( FFSDebug.stackTrace( e ), stid );
        }
    }

    /**
     * Check the error code of the Trailer record in the
     * SIS Echo file.
     *
     * @param trail
     * @param dbh    A database connection holder that has the database
     *               connection that we can use for database operations
     *               within the current transaction.
     * @exception Exception
     */
    protected static void processSrvcTransTrailer( TypeSTTrailer trail,
                                                 FFSConnectionHolder dbh )
    throws Exception
    {
        if ( !trail.RS_ErrCode.equals( "00000" ) ) {
            // errors in the trailer are evil.  throw a general exception
            //  to indicate a problem with the entire file.
            throw new Exception( "Trailer failed" );
        }
        // TODO: check record counts
    }


    protected TypeSrvcTrans createBlankSrvcTrans( TypeSTHeader header,
                                                             TypeSTTrailer trailer )
    {
        // 1. creating a new instance, attach the header and trailer
        TypeSrvcTrans srvcTrans = new TypeSrvcTrans();
        srvcTrans.STHeader = header;
        srvcTrans.STTrailer = trailer;

        // 2. make all the other fields empty
        srvcTrans.SubInfoExists = false;
        srvcTrans.BankAcctInfoExists = false;
        srvcTrans.SrvcActInfoExists = false;
        srvcTrans.PayeeInfoExists = false;
        srvcTrans.PayeeAcctInfoExists = false;
        srvcTrans.PmtInfoExists = false;

        return srvcTrans;
    }


    protected  void clearCaches()
    {
        _subInfoList.clear();
        _bankInfoList.clear();
        _payeeInfoList.clear();
        _payeeAcctInfoList.clear();
        _pmtInfoList.clear();
    }

    protected void cleanUpMsgBody( TypeSrvcTrans srvcTrans )
    {
        if ( srvcTrans == null ) return;
        srvcTrans.SubInfo       = null;
        srvcTrans.BankAcctInfo      = null;
        srvcTrans.SrvcActInfo       = null;
        srvcTrans.PayeeInfo     = null;
        srvcTrans.PayeeAcctInfo     = null;
        srvcTrans.PmtInfo       = null;
        srvcTrans.SubInfoExists     = false;
        srvcTrans.BankAcctInfoExists    = false;
        srvcTrans.SrvcActInfoExists = false;
        srvcTrans.PayeeInfoExists   = false;
        srvcTrans.PayeeAcctInfoExists   = false;
        srvcTrans.PmtInfoExists     = false;
    }

    //////////////////////////////////////////////////////////////////////////
    // Proccess a PmtHistory response file from CheckFree
    //////////////////////////////////////////////////////////////////////////
    public static final boolean processPmtHistFile( File file, FFSConnectionHolder dbh )
    throws Exception
    {
        TypePmtHistory msg = null;

        msg = (TypePmtHistory)parseRSFile(
                                         file,
                                         CheckFreeConsts.MB_PMT_HIST_MESSAGE_NAME );
        ResponseFileProcessor.setBatchSize(_propConfig.getBatchSize());
        return ResponseFileProcessor.process( msg, dbh );
    }


    //////////////////////////////////////////////////////////////////////////
    // Proccess a Settlement response file from CheckFree
    //////////////////////////////////////////////////////////////////////////
    public static final boolean processSettlementFile( File file, FFSConnectionHolder dbh )
    throws Exception
    {
        TypeSettlement msg = null;

        msg = (TypeSettlement)parseRSFile(
                                         file,
                                         CheckFreeConsts.MB_SETTLEMENT_MESSAGE_NAME );

        return ResponseFileProcessor.process( msg, dbh );
    }

    protected static final Object parseRSFile( File file, String msgName )
    throws Exception
    {
        FileInputStream in = null;
        byte[] buff;
        Object message = null;

        try {
            in = new FileInputStream( (File)file );
            buff = new byte[ in.available() ];
            in.read( buff );
            message = _mb.parseMsg(
                                  new String( buff), msgName,
                                  CheckFreeConsts.MB_MESSAGE_SET_NAME,
                                  _debug );

        } catch ( Exception e ) {
            FFSDebug.log ("*** CheckFree Adapter: Error when processing " + msgName + " file "
                          + file.getName()
                          + ".  Processing canceled." ,FFSDebug.PRINT_ERR);
            throw e;
        } finally {
            try {
                if ( in!=null ) in.close();
                in = null;
            } catch ( Exception e ) {
                // Ignore
            }
        }

        return message;
    }


    /**
    * makeSubscriberID: making a CheckFree valid subscriberID if customerID
    * is shorter than 9 char's. Using 0 padding to do it.
    * @param cid - customer id
    * @return subscriber ID
    */
    protected  String makeSubscriberID( String cid )
    {
        int len = cid.length();

        // If ID is long enough, don't do anything
        if ( len>= MIN_SUBID_LENGTH ) return cid;

        // If ID is too short, pad 0's in left to make it long enough
        StringBuffer sb = new StringBuffer( MIN_SUBID_LENGTH );
        for ( int i=0; i<MIN_SUBID_LENGTH-len; ++i ) sb.append(0);
        sb.append( cid );

        return sb.toString();
    }


    /**
    * subID2CustomerID: Get a customer ID given a CheckFree accepted subscriber ID
    * @param sid - subscriber ID
    * @return customer ID
    */
    protected static final String subID2CustomerID( String sid )
    {
        // If sid is longer than 9 don't do anything
        if ( sid.length() >MIN_SUBID_LENGTH ) return sid;

        // If not, parse it to int to get rid of the leading 0's
        int idx=0;
        while (idx<sid.length() && sid.charAt(idx)=='0')++idx;

        // Return rest of string
        return sid.substring(idx);
    }


    public static int getRouteID() { return _routeID;}


    /**
    * fillUpString: fills a string with the given character if its length is
    * shorter then the defined constant.
    * @param shortString - string to be padded
    * @param sLength - defined string length
    * @param fChar - filler character
    * @return padded string
    */
    protected  String fillUpString( String shortString, int sLength, String fChar )
    {
        int len = 0;
        if (shortString != null) {
            len = shortString.length();
        } else {
            shortString = "";
        }

        // If string is long enough, don't do anything
        if ( len>= sLength ) return shortString;

        // If string is too short, pad fChar's on left to make it long enough
        StringBuffer sb = new StringBuffer( sLength );
        for ( int i=0; i<sLength-len; ++i ) {
            sb.append(fChar);
        }
        sb.append( shortString );

        return sb.toString();
    }


    /**
     * Create a new TypeSrvcTrans object using the Header
     * and Trailer from the TypeSrvcTrans parameter.
     *
     * @param original Master TypeSrvcTrans object.
     * @return TypeSrvcTrans object using the Header and Trailer found
     *         in the TypeSrvcTrans parameter.
     */
    protected TypeSrvcTrans getDummySrvcTrans(TypeSrvcTrans original)
    {
        TypeSrvcTrans dummy = new TypeSrvcTrans();
        dummy.STHeader = original.STHeader;
        dummy.STTrailer = original.STTrailer;

        return dummy;
    }


    /**
     * Error handling for failed build on the TypeSubInfo field.
     * Extracts the records that are failing the Message Broker
     * build and adds them to the SISGenErrorList. Then pushes
     * the remaining (good) records through Message Broker build
     * and returns the resulting byte[].
     *
     * @param srvcTrans Master TypeSrvcTrans object. This object will not be
     *                  modified.
     * @param builderErrList
     *                  Storage buffer for records that fail the Message Broker
     *                  build process.
     * @return The result of the Message Broker build process on the
     *         remaining, good, records.
     * @exception BPWException
     */
    protected byte[] handleSubInfoBuildError(TypeSrvcTrans srvcTrans,
                                           SISGenErrorList builderErrList)
    throws BPWException
    {
        // Lists for separating good from bad.
        ArrayList validSubInfo = new ArrayList();
        int dbgErrorCount = 0;

        // Create a dummy object to use for MB Building.
        TypeSrvcTrans dummySrvcTrans = getDummySrvcTrans(srvcTrans);
        dummySrvcTrans.SubInfo = new TypeSubInfo[1];
        dummySrvcTrans.SubInfoExists = true;

        // Iterate through each record, weeding out the bad guys.
        for (int idx=0; idx < srvcTrans.SubInfo.length; idx++) {
            try {
                dummySrvcTrans.SubInfo[0] = srvcTrans.SubInfo[idx];
                buildSrvcTrans(dummySrvcTrans);

                // Successful build. Add to our list of valid items.
                validSubInfo.add(srvcTrans.SubInfo[idx]);
            } catch (BPWException mbe) {
                // This is a bad record. Add to our list of invalid items.
                SISGenError sisGenError = new SISGenError(srvcTrans.SubInfo[idx], mbe);
                builderErrList.addSubInfoError(sisGenError);

                // Decrement the SubInfo count so that the trailer
                // values will be accurate.
                _subInfoCount--;

                // Increment out debug error count.
                dbgErrorCount++;
            }
        } // End for-loop

        // Now do a build of only the valid values (if there are any).
        byte[] srvcTransBytes = null;
        if (validSubInfo.size() > 0) {
            dummySrvcTrans.SubInfo = (TypeSubInfo[])validSubInfo.toArray(
                                                                        new TypeSubInfo[validSubInfo.size()]);
            // Build the good batch of records into a byte array.
            // Don't wrap with a try/catch. If this fails, then there is
            // a bigger error; let it throw all the way out.
            srvcTransBytes = buildSrvcTrans(dummySrvcTrans);
        }

        FFSDebug.log ("CheckFree Adapter: Error processing " + dbgErrorCount +
                      " Subscriber records.", FFSConst.PRINT_DEV);

        return srvcTransBytes;
    }

    /**
     * Error handling for failed build on the TypeBankAcctInfo field.
     * Extracts the records that are failing the Message Broker
     * build and adds them to the SISGenErrorList. Then pushes
     * the remaining (good) records through Message Broker build
     * and returns the resulting byte[].
     *
     * @param srvcTrans Master TypeSrvcTrans object. This object will not be
     *                  modified.
     * @param builderErrList
     *                  Storage buffer for records that fail the Message Broker
     *                  build process.
     * @return The result of the Message Broker build process on the
     *         remaining, good, records.
     * @exception BPWException
     */
    protected byte[] handleBankAcctInfoBuildError(TypeSrvcTrans srvcTrans,
                                                SISGenErrorList builderErrList)
    throws BPWException
    {
        // Lists for separating good from bad.
        ArrayList validBankAcctInfo = new ArrayList();
        int dbgErrorCount = 0;

        // Create a dummy object to use for MB Building.
        TypeSrvcTrans dummySrvcTrans = getDummySrvcTrans(srvcTrans);
        dummySrvcTrans.BankAcctInfo = new TypeBankAcctInfo[1];
        dummySrvcTrans.BankAcctInfoExists = true;

        // Iterate through each record, weeding out the bad guys.
        for (int idx=0; idx < srvcTrans.BankAcctInfo.length; idx++) {
            try {
                dummySrvcTrans.BankAcctInfo[0] = srvcTrans.BankAcctInfo[idx];
                buildSrvcTrans(dummySrvcTrans);

                // Successful build. Add to our list of valid items.
                validBankAcctInfo.add(srvcTrans.BankAcctInfo[idx]);
            } catch (BPWException mbe) {
                // This is a bad record. Add to our list of invalid items.
                SISGenError sisGenError = new SISGenError(srvcTrans.BankAcctInfo[idx], mbe);
                builderErrList.addBankAcctInfoError(sisGenError);

                // Decrement the BankAcctInfo count so that the trailer
                // values will be accurate.
                _bankInfoCount--;

                // Increment out debug error count.
                dbgErrorCount++;
            }
        } // End for-loop

        // Now do a build of only the valid values (if there are any).
        byte[] srvcTransBytes = null;
        if (validBankAcctInfo.size() > 0) {
            dummySrvcTrans.BankAcctInfo = (TypeBankAcctInfo[])validBankAcctInfo.toArray(
                                                                                       new TypeBankAcctInfo[validBankAcctInfo.size()]);
            // Build the good batch of records into a byte array.
            // Don't wrap with a try/catch. If this fails, then there is
            // a bigger error; let it throw all the way out.
            srvcTransBytes = buildSrvcTrans(dummySrvcTrans);
        }

        FFSDebug.log ("CheckFree Adapter: Error processing " + dbgErrorCount +
                      " BankAccount records.", FFSConst.PRINT_DEV);

        return srvcTransBytes;
    }

    /**
     * Error handling for failed build on the TypePayeeInfo field.
     * Extracts the records that are failing the Message Broker
     * build and adds them to the SISGenErrorList. Then pushes
     * the remaining (good) records through Message Broker build
     * and returns the resulting byte[].
     *
     * @param srvcTrans Master TypeSrvcTrans object. This object will not be
     *                  modified.
     * @param builderErrList
     *                  Storage buffer for records that fail the Message Broker
     *                  build process.
     * @return The result of the Message Broker build process on the
     *         remaining, good, records.
     * @exception BPWException
     */
    protected byte[] handlePayeeInfoBuildError(TypeSrvcTrans srvcTrans,
                                             SISGenErrorList builderErrList)
    throws BPWException
    {
        // Lists for separating good from bad.
        ArrayList validPayeeInfo = new ArrayList();
        int dbgErrorCount = 0;

        // Create a dummy object to use for MB Building.
        TypeSrvcTrans dummySrvcTrans = getDummySrvcTrans(srvcTrans);
        dummySrvcTrans.PayeeInfo = new TypePayeeInfo[1];
        dummySrvcTrans.PayeeInfoExists = true;

        // Iterate through each record, weeding out the bad guys.
        for (int idx=0; idx < srvcTrans.PayeeInfo.length; idx++) {
            try {
                dummySrvcTrans.PayeeInfo[0] = srvcTrans.PayeeInfo[idx];
                buildSrvcTrans(dummySrvcTrans);

                // Successful build. Add to our list of valid items.
                validPayeeInfo.add(srvcTrans.PayeeInfo[idx]);
            } catch (BPWException mbe) {
                // This is a bad record. Add to our list of invalid items.
                SISGenError sisGenError = new SISGenError(srvcTrans.PayeeInfo[idx], mbe);
                builderErrList.addPayeeInfoError(sisGenError);

                // Decrement the PayeeInfo count so that the trailer
                // values will be accurate.
                _payeeInfoCount--;

                // Increment out debug error count.
                dbgErrorCount++;
            }
        } // End for-loop

        // Now do a build of only the valid values (if there are any).
        byte[] srvcTransBytes = null;
        if (validPayeeInfo.size() > 0) {
            dummySrvcTrans.PayeeInfo = (TypePayeeInfo[])validPayeeInfo.toArray(
                                                                              new TypePayeeInfo[validPayeeInfo.size()]);
            // Build the good batch of records into a byte array.
            // Don't wrap with a try/catch. If this fails, then there is
            // a bigger error; let it throw all the way out.
            srvcTransBytes = buildSrvcTrans(dummySrvcTrans);
        }

        FFSDebug.log ("CheckFree Adapter: Error processing " + dbgErrorCount +
                      " PayeeInfo records.", FFSConst.PRINT_DEV);

        return srvcTransBytes;
    }

    /**
     * Error handling for failed build on the TypePayeeAcctInfo field.
     * Extracts the records that are failing the Message Broker
     * build and adds them to the SISGenErrorList. Then pushes
     * the remaining (good) records through Message Broker build
     * and returns the resulting byte[].
     *
     * @param srvcTrans Master TypeSrvcTrans object. This object will not be
     *                  modified.
     * @param builderErrList
     *                  Storage buffer for records that fail the Message Broker
     *                  build process.
     * @return The result of the Message Broker build process on the
     *         remaining, good, records.
     * @exception BPWException
     */
    protected byte[] handlePayeeAcctInfoBuildError(TypeSrvcTrans srvcTrans,
                                                 SISGenErrorList builderErrList)
    throws BPWException
    {
        // Lists for separating good from bad.
        ArrayList validPayeeAcctInfo = new ArrayList();
        int dbgErrorCount = 0;

        // Create a dummy object to use for MB Building.
        TypeSrvcTrans dummySrvcTrans = getDummySrvcTrans(srvcTrans);
        dummySrvcTrans.PayeeAcctInfo = new TypePayeeAcctInfo[1];
        dummySrvcTrans.PayeeAcctInfoExists = true;

        // Iterate through each record, weeding out the bad guys.
        for (int idx=0; idx < srvcTrans.PayeeAcctInfo.length; idx++) {
            try {
                dummySrvcTrans.PayeeAcctInfo[0] = srvcTrans.PayeeAcctInfo[idx];
                buildSrvcTrans(dummySrvcTrans);

                // Successful build. Add to our list of valid items.
                validPayeeAcctInfo.add(srvcTrans.PayeeAcctInfo[idx]);
            } catch (BPWException mbe) {
                // This is a bad record. Add to our list of invalid items.
                SISGenError sisGenError = new SISGenError(srvcTrans.PayeeAcctInfo[idx], mbe);
                builderErrList.addPayeeAcctInfoError(sisGenError);

                // Decrement the PayeeAcctInfo count so that the trailer
                // values will be accurate.
                _payeeAcctInfoCount--;

                // Increment out debug error count.
                dbgErrorCount++;
            }
        } // End for-loop

        // Now do a build of only the valid values (if there are any).
        byte[] srvcTransBytes = null;
        if (validPayeeAcctInfo.size() > 0) {
            dummySrvcTrans.PayeeAcctInfo = (TypePayeeAcctInfo[])validPayeeAcctInfo.toArray(
                                                                                          new TypePayeeAcctInfo[validPayeeAcctInfo.size()]);
            // Build the good batch of records into a byte array.
            // Don't wrap with a try/catch. If this fails, then there is
            // a bigger error; let it throw all the way out.
            srvcTransBytes = buildSrvcTrans(dummySrvcTrans);
        }

        FFSDebug.log ("CheckFree Adapter: Error processing " + dbgErrorCount +
                      " PayeeAccount records.", FFSConst.PRINT_DEV);

        return srvcTransBytes;
    }

    /**
     * Error handling for failed build on the TypePmtInfo field.
     * Extracts the records that are failing the Message Broker
     * build and adds them to the SISGenErrorList. Then pushes
     * the remaining (good) records through Message Broker build
     * and returns the resulting byte[].
     *
     * @param srvcTrans Master TypeSrvcTrans object. This object will not be
     *                  modified.
     * @param builderErrList
     *                  Storage buffer for records that fail the Message Broker
     *                  build process.
     * @return The result of the Message Broker build process on the
     *         remaining, good, records.
     * @exception BPWException
     */
    protected byte[] handlePmtInfoBuildError(TypeSrvcTrans srvcTrans,
                                           SISGenErrorList builderErrList)
    throws BPWException
    {
        // Lists for separating good from bad.
        ArrayList validPmtInfo = new ArrayList();
        int dbgErrorCount = 0;

        // Create a dummy object to use for MB Building.
        TypeSrvcTrans dummySrvcTrans = getDummySrvcTrans(srvcTrans);
        dummySrvcTrans.PmtInfo = new TypePmtInfo[1];
        dummySrvcTrans.PmtInfoExists = true;

        // Iterate through each record, weeding out the bad guys.
        for (int idx=0; idx < srvcTrans.PmtInfo.length; idx++) {
            try {
                dummySrvcTrans.PmtInfo[0] = srvcTrans.PmtInfo[idx];
                buildSrvcTrans(dummySrvcTrans);

                // Successful build. Add to our list of valid items.
                validPmtInfo.add(srvcTrans.PmtInfo[idx]);
            } catch (BPWException mbe) {
                // This is a bad record. Add to our list of invalid items.
                TypePmtInfo pmtInfo = srvcTrans.PmtInfo[idx];
                SISGenError sisGenError = new SISGenError(pmtInfo, mbe);
                builderErrList.addPmtInfoError(sisGenError);

                // Decrement the PmtInfo, PmtAmount count so that
                // the trailer values will be accurate.
                _pmtInfoCount--;
                _totalPmtAmount -= pmtInfo.Info.PmtAmt;

                if (pmtInfo.InvInfoExists == true) {
                    // Also decrement the PmtInvoice Count.
                    _pmtInvInfoCount--;
                }

                // Increment out debug error count.
                dbgErrorCount++;
            }
        } // End for-loop

        // Now do a build of only the valid values (if there are any).
        byte[] srvcTransBytes = null;
        if (validPmtInfo.size() > 0) {
            dummySrvcTrans.PmtInfo = (TypePmtInfo[])validPmtInfo.toArray(
                                                                        new TypePmtInfo[validPmtInfo.size()]);
            // Build the good batch of records into a byte array.
            // Don't wrap with a try/catch. If this fails, then there is
            // a bigger error; let it throw all the way out.
            srvcTransBytes = buildSrvcTrans(dummySrvcTrans);
        }

        FFSDebug.log ("CheckFree Adapter: Error processing " + dbgErrorCount +
                      " Payment records.", FFSConst.PRINT_DEV);

        return srvcTransBytes;
    }


    /**
     * Performs specific handling for the various "bad" records
     * found during the CheckFree SIS file generation process.
     *
     * The list of "bad" records includes records that had data
     * mapping errors or Message Broker builder errors.
     *
     * @param sisGenErrList
     *               The buffer containing all of the erroneous records for
     *               the current SIS file generation process.
     * @param dbh    A database connection holder that has the database
     *               connection that we can use for database operations
     *               within the current transaction.
     */
    protected void processBuildErrors(SISGenErrorList sisGenErrList,
                                    FFSConnectionHolder dbh)
    {
        FFSDebug.log("CheckFree Adapter: processBuildErrors begin...",
                     FFSConst.PRINT_DEV);

        // Subscriber (1000) Errors
        if (sisGenErrList.getSubInfoErrorCount() > 0) {
            processSubErrors(sisGenErrList.getSubInfoErrors(), dbh);
        }

        // BankAcctInfo (2010) Errors
        if (sisGenErrList.getBankAcctInfoErrorCount() > 0) {
            processBankAcctErrors(sisGenErrList.getBankAcctInfoErrors(), dbh);
        }

        // PayeeInfo (3000) Errors
        if (sisGenErrList.getPayeeInfoErrorCount() > 0) {
            processPayeeErrors(sisGenErrList.getPayeeInfoErrors(), dbh);
        }

        // PayeeAcctInfo (3010) Errors
        if (sisGenErrList.getPayeeAcctInfoErrorCount() > 0) {
            processPayeeAcctErrors(sisGenErrList.getPayeeAcctInfoErrors(), dbh);
        }

        // PmtInfo (4000/4010) Errors
        if (sisGenErrList.getPmtInfoErrorCount() > 0) {
            processPmtErrors(sisGenErrList.getPmtInfoErrors(), dbh);
        }

        FFSDebug.log("CheckFree Adapter: processBuildErrors end...",
                     FFSConst.PRINT_DEV);
    }


    /**
     * Fail the customers associated with the Subscriber
     * Information records in the provided ArrayList.
     *
     * This method is called during the SIS file generation
     * process. The list includes Subscriber Information records
     * that had data mapping errors or Message Broker builder
     * errors.
     *
     * @param subErrs Array List of TypeSubInfo objects representing the
     *                Subscriber Information records that should be failed.
     * @param dbh     A database connection holder that has the database
     *                connection that we can use for database operations
     *                within the current transaction.
     */
    protected void processSubErrors(ArrayList subErrs,
                                  FFSConnectionHolder dbh)
    {
        FFSDebug.log("CheckFree Adapter: " + subErrs.size() +
                     " SubInfo error(s).", FFSConst.PRINT_DEV);

        SISGenError sisGenErr;
        TypeSubInfo subInfo;
        Throwable subException;
        for (int idx=0; idx < subErrs.size(); idx++) {
            // Retrieve the Subscriber record and associated exception.
            sisGenErr = (SISGenError)subErrs.get(idx);
            subInfo = (TypeSubInfo) sisGenErr.record;
            subException = sisGenErr.error;

            // Set the error code to INVALID DATA
            subInfo.RS_ErrCode = CHECKFREE_ERROR_CODE__INVALID_DATA;
            subInfo.RS_ErrMsg = subException.toString();

            try {
                // Push the failed record to the result processing.
                FFSDebug.log("CheckFree Adapter: Build Error: " +
                             "Subscriber record failed: " +
                             "Subscriber ID=" + subInfo.SubID + ", " +
                             "Error code=" + subInfo.RS_ErrCode + ", " +
                             "Error msg=" + subInfo.RS_ErrMsg,
                             FFSConst.PRINT_ERR);

                processSrvcTransSubInfo(subInfo, dbh);
            } catch (Throwable t) {
                // Plan B. Result processing failed for some reason.
                // Print out the error to the log. Give pertinent information.
                FFSDebug.log(t,
                             "CheckFree Adapter: Unable to handle " +
                             "Subscriber record failure: " +
                             "Subscriber ID=" + subInfo.SubID,
                             FFSConst.PRINT_ERR);
            }
        } // End for-loop
    }

    /**
     * Process Bank Account Information record failures
     * for the records found in the provided ArrayList.
     *
     * This method is called during the SIS file generation
     * process. The list includes Bank Account Information
     * records that had data mapping errors or Message Broker
     * builder errors.
     *
     * @param bankAcctErrs
     *               Array List of TypeBankAcctInfo objects representing the
     *               Bank Account Information records that should be failed.
     * @param dbh    A database connection holder that has the database
     *               connection that we can use for database operations
     *               within the current transaction.
     */
    protected void processBankAcctErrors(ArrayList bankAcctErrs,
                                       FFSConnectionHolder dbh)
    {
        FFSDebug.log("CheckFree Adapter: " + bankAcctErrs.size() +
                     " BankAcctInfo error(s).", FFSConst.PRINT_DEV);

        SISGenError sisGenErr;
        TypeBankAcctInfo bankAcctInfo;
        Throwable bankAcctException;
        for (int idx=0; idx < bankAcctErrs.size(); idx++) {
            // Retrieve the BankAcct record and associated exception.
            sisGenErr = (SISGenError)bankAcctErrs.get(idx);
            bankAcctInfo = (TypeBankAcctInfo) sisGenErr.record;
            bankAcctException = sisGenErr.error;

            // Set the error code to INVALID DATA
            bankAcctInfo.RS_ErrCode = CHECKFREE_ERROR_CODE__INVALID_DATA;
            bankAcctInfo.RS_ErrMsg = bankAcctException.toString();

            try {
                // Push the failed record to the result processing.
                FFSDebug.log("CheckFree Adapter: Build Error: " +
                             "BankAcct record failed: " +
                             "Subscriber ID=" + bankAcctInfo.SubID + ", " +
                             "Error code=" + bankAcctInfo.RS_ErrCode + ", " +
                             "Error msg=" + bankAcctInfo.RS_ErrMsg,
                             FFSConst.PRINT_ERR);

                processSrvcTransBankAcctInfo(bankAcctInfo, dbh);
            } catch (Throwable t) {
                // Plan B. Result processing failed for some reason.
                // Print out the error to the log. Give pertinent information.
                FFSDebug.log(t,
                             "CheckFree Adapter: Unable to handle " +
                             "BankAcct record failure: " +
                             "Subscriber ID=" + bankAcctInfo.SubID,
                             FFSConst.PRINT_ERR);
            }
        } // End for-loop
    }

    /**
     * Fail the Customer Payees associated with the Payee
     * Information records in the provided ArrayList.
     *
     * This method is called during the SIS file generation
     * process. The list includes Payee Information records
     * that had data mapping errors or Message Broker builder
     * errors.
     *
     * @param payeeErrs Array List of TypePayeeInfo objects representing the
     *                  Payee Information records that should be failed.
     * @param dbh       A database connection holder that has the database
     *                  connection that we can use for database operations
     *                  within the current transaction.
     */
    protected void processPayeeErrors(ArrayList payeeErrs,
                                    FFSConnectionHolder dbh)
    {
        FFSDebug.log("CheckFree Adapter: " + payeeErrs.size() +
                     " PayeeInfo error(s).", FFSConst.PRINT_DEV);

        SISGenError sisGenErr;
        TypePayeeInfo payeeInfo;
        Throwable payeeException;
        for (int idx=0; idx < payeeErrs.size(); idx++) {
            // Retrieve the Payee record and associated exception.
            sisGenErr = (SISGenError)payeeErrs.get(idx);
            payeeInfo = (TypePayeeInfo) sisGenErr.record;
            payeeException = sisGenErr.error;

            // Set the error code to INVALID DATA
            payeeInfo.RS_ErrCode = CHECKFREE_ERROR_CODE__INVALID_DATA;
            payeeInfo.RS_ErrMsg = "CheckFree Adapter: Unable to create Payee record.";

            try {
                // Push the failed record to the result processing.
                FFSDebug.log("CheckFree Adapter: Build Error: " +
                             "Payee record failed: " +
                             "Subscriber ID=" + payeeInfo.SubID + ", " +
                             "PayeeList ID=" + payeeInfo.PayeeListID + ", " +
                             "Error code=" + payeeInfo.RS_ErrCode + ", " +
                             "Error msg=" + payeeException.toString(),
                             FFSConst.PRINT_ERR);

                processSrvcTransPayeeInfo(payeeInfo, dbh);
            } catch (Throwable t) {
                // Plan B. Result processing failed for some reason.
                // Print out the error to the log. Give pertinent information.
                FFSDebug.log(t,
                             "CheckFree Adapter: Unable to handle " +
                             "Payee record failure: " +
                             "Subscriber ID=" + payeeInfo.SubID + ", " +
                             "PayeeList ID=" + payeeInfo.PayeeListID,
                             FFSConst.PRINT_ERR);
            }
        } // End for-loop
    }

    /**
     * Fail the Customer Payees associated with the Payee
     * Account Information records in the provided ArrayList.
     *
     * This method is called during the SIS file generation
     * process. The list includes Payee Account Information
     * records that had data mapping errors or Message Broker
     * builder errors.
     *
     * @param payeeAcctErrs
     *               Array List of TypePayeeAcctInfo objects representing
     *               the Payee Account Information records that should be
     *               failed.
     * @param dbh    A database connection holder that has the database
     *               connection that we can use for database operations
     *               within the current transaction.
     */
    protected void processPayeeAcctErrors(ArrayList payeeAcctErrs,
                                        FFSConnectionHolder dbh)
    {
        FFSDebug.log("CheckFree Adapter: " + payeeAcctErrs.size() +
                     " PayeeAcctInfo error(s).", FFSConst.PRINT_DEV);

        SISGenError sisGenErr;
        TypePayeeAcctInfo payeeAcctInfo;
        Throwable payeeAcctException;
        for (int idx=0; idx < payeeAcctErrs.size(); idx++) {
            // Retrieve the PayeeAcct record and associated exception.
            sisGenErr = (SISGenError)payeeAcctErrs.get(idx);
            payeeAcctInfo = (TypePayeeAcctInfo) sisGenErr.record;
            payeeAcctException = sisGenErr.error;

            // Set the error code to INVALID DATA
            payeeAcctInfo.RS_ErrCode = CHECKFREE_ERROR_CODE__INVALID_DATA;
            payeeAcctInfo.RS_ErrMsg = "CheckFree Adapter: Unable to create PayeeAcct record.";
            payeeAcctException.toString();

            try {
                // Push the failed record to the result processing.
                FFSDebug.log("CheckFree Adapter: Build Error: " +
                             "PayeeAcct record failed: " +
                             "Subscriber ID=" + payeeAcctInfo.SubID + ", " +
                             "PayeeList ID=" + payeeAcctInfo.PayeeListID + ", " +
                             "Error code=" + payeeAcctInfo.RS_ErrCode + ", " +
                             "Error msg=" + payeeAcctException.toString(),
                             FFSConst.PRINT_ERR);

                processSrvcTransPayeeAcctInfo(payeeAcctInfo, dbh);
            } catch (Throwable t) {
                // Plan B. Result processing failed for some reason.
                // Print out the error to the log. Give pertinent information.
                FFSDebug.log(t,
                             "CheckFree Adapter: Build Error: Unable to handle " +
                             "PayeeAcct record failure: " +
                             "Subscriber ID=" + payeeAcctInfo.SubID + ", " +
                             "PayeeList ID=" + payeeAcctInfo.PayeeListID,
                             FFSConst.PRINT_ERR);
            }
        } // End for-loop
    }

    /**
     * Fail the Bill Payments associated with the Payment
     * Information records in the provided ArrayList.
     *
     * This method is called during the SIS file generation
     * process. The list includes Payment Information records
     * that had data mapping errors or Message Broker builder
     * errors.
     *
     * @param pmtErrs Array List of TypePmtInfo objects representing the
     *                Payment Information records that should be failed.
     * @param dbh     A database connection holder that has the database
     *                connection that we can use for database operations
     *                within the current transaction.
     */
    protected void processPmtErrors(ArrayList pmtErrs,
                                  FFSConnectionHolder dbh)
    {
        FFSDebug.log("CheckFree Adapter: " + pmtErrs.size() +
                     " PmtInfo error(s).", FFSConst.PRINT_DEV);

        SISGenError sisGenErr;
        TypePmtInfo pmtInfo;
        Throwable pmtException;
        for (int idx=0; idx < pmtErrs.size(); idx++) {
            // Retrieve the Payment record and associated exception.
            sisGenErr = (SISGenError)pmtErrs.get(idx);
            pmtInfo = (TypePmtInfo) sisGenErr.record;
            pmtException = sisGenErr.error;

            // Set the error code and error message.
            pmtInfo.Info.RS_ErrCode = CHECKFREE_ERROR_CODE__INVALID_DATA;
            // The RS_ErrMsg field is used to populate the error message
            // section of the OFX Response message (i.e. It is user visible).
            pmtInfo.Info.RS_ErrMsg = "The payment request contains invalid data.";

            try {
                // Push the failed record to the result processing.
                FFSDebug.log("CheckFree Adapter: Build Error: " +
                             "Payment record failed: " +
                             "CSP Payment ID=" + pmtInfo.Info.CspPmtID + ", " +
                             "Subscriber ID=" + pmtInfo.Info.SubID + ", " +
                             "PayeeList ID=" + pmtInfo.Info.PayeeListID + ", " +
                             "Error code=" + pmtInfo.Info.RS_ErrCode + ", " +
                             "Error msg=" + pmtException.toString(),
                             FFSConst.PRINT_ERR);

                processSrvcTransPmtInfo(pmtInfo, dbh);
            } catch (Throwable t) {
                // Plan B. Result processing failed for some reason.
                // Print out the error to the log. Give pertinent information.
                FFSDebug.log(t,
                             "CheckFree Adapter: Unable to handle " +
                             "Payment record failure: " +
                             "CSP Payment ID=" + pmtInfo.Info.CspPmtID + ", " +
                             "Subscriber ID=" + pmtInfo.Info.SubID + ", " +
                             "PayeeList ID=" + pmtInfo.Info.PayeeListID,
                             FFSConst.PRINT_ERR);
            }
        } // End for-loop
    }
}
