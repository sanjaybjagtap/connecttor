// Copyright (c) 2001 Financial Fusion, Inc.  All Rights Reserved.

package com.ffusion.ffs.bpw.fulfill.metavante;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;

import com.ffusion.ffs.bpw.BPWServer;
import com.ffusion.ffs.bpw.audit.FMLogAgent;
import com.ffusion.ffs.bpw.db.CustomerBank;
import com.ffusion.ffs.bpw.db.CustomerProductAccess;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.CustomerBankInfo;
import com.ffusion.ffs.bpw.interfaces.CustomerInfo;
import com.ffusion.ffs.bpw.interfaces.CustomerPayeeInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.FulfillmentInfo;
import com.ffusion.ffs.bpw.interfaces.PayeeInfo;
import com.ffusion.ffs.bpw.interfaces.PayeeRouteInfo;
import com.ffusion.ffs.bpw.interfaces.PmtInfo;
import com.ffusion.ffs.bpw.interfaces.PmtTrnRslt;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.handlers.FulfillmentAPI;
import com.ffusion.ffs.bpw.util.BPWRegistryUtil;
import com.ffusion.ffs.bpw.util.BPWUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSProperties;
import com.ffusion.ffs.util.FFSRegistry;
import com.ffusion.ffs.util.FFSUtil;
import com.ffusion.util.beans.filemonitor.FMLogRecord;
import com.sap.banking.io.beans.File;
import com.sap.banking.io.beans.FileInputStream;
import com.sap.banking.io.beans.FileReader;
import com.sap.banking.io.beans.FileWriter;
import com.sap.banking.io.exception.FileNotFoundException;
import com.sap.banking.io.provider.interfaces.FileHandlerProvider;

///////////////////////////////////////////////////////////////////////////////
// Main class of the Metavante connector.
///////////////////////////////////////////////////////////////////////////////
public class MetavanteHandler implements FulfillmentAPI, DBConsts,FFSConst, BPWResource {
    // character used to separate file path components on given OS
    private static final String FILE_SEP = System.getProperty( "file.separator" );

    // Constants for line type indicators
    private static final char HEADER_LINE_INDICATOR = '5';
    private static final char DETAIL_LINE_INDICATOR = '6';
    private static final char TRAILER_LINE_INDICATOR = '8';

    // Date and time format used for various fields
    private static final DateFormat _dt = new SimpleDateFormat( "yyyyMMddhh:mm:ss" );
    // Hardcoded Production Indicator value.
    //  'D' means development phase.
    //  'T' means testing phase.
    //  'P' means production phase.
    private static final char PRODUCTION_INDICATOR = 'D';

    // Payment type constants
    private static final String PT_CURRENT = "CURRENT";
    private static final String PT_FUTURE = "FUTURE";
    private static final String PT_RECURRING = "RECURRING";

    // File type constants
    // files to metavante
    public static String FT_PAYMENTS_IN     = "PAYMENT-IN";
    public static String FT_CONSUMER_IN     = "CONSUMER-IN";
    public static String FT_CONS_PAYEE_IN   = "CONS-PAYEE-IN";
    public static String FT_BANK_IN         = "BANK-IN";
    public static String FT_CONSPRDACC_IN   = "CONS-PRODUCT-IN";
    public static String FT_PAYEE_IN        = "PAYEE-IN";

    // files from metavante
    public static String FT_CONSUMER_AD     = "CONSUMER-OUT";
    public static String FT_CONS_PAYEE_AD   = "CONS-PAYEE-OUT";
    public static String FT_CONS_BANK_AD    = "CONS-BANK-OUT";
    public static String FT_CONSPRDACC_AD   = "CONS-PRODUCT-OUT";
    public static String FT_CONS_XREF_AD    = "CONS-XREF.OUT";
    public static String FT_PAYEE_AD        = "PAYEE-OUT";
    public static String FT_PAYEE_EDIT_AD   = "PAYEE-EDIT-OUT";
    public static String FT_HISTORY_AD      = "HISTORY-OUT";

    // Name of the directory where to put the generated request files.
    private static final String DEFAULT_EXPORT_DIR = "export";
    private static String _exportDir = DEFAULT_EXPORT_DIR;

    // Site ID value.
    private static final String DEFAULT_SITE_ID = "SID";
    private static String _siteID = DEFAULT_SITE_ID;
    private static String _bpwSiteID = _siteID + "01";
    private static String _acSiteID  = _siteID + "AC";
    // Site Name value.
    private static final String DEFAULT_SITE_NAME = "SNAME";
    private static String _siteName = DEFAULT_SITE_NAME;

    private static char _processingMode = PRODUCTION_INDICATOR;

    // Various values we need to remember between method invocations.
    private static File _custFile; // Consumer Information File
    private static File _cbiFile; // Consumer BankInfo File
    private static File _cpaFile; // Consumer Product Access File
    private static File _pmtFile; // Payment Information File
    private static File _cpFile; // Consumer Payee Information File
    private static File _payeeFile; // Payee Information File
    private static BigDecimal _pmtTotal; // total payments value in Payment Information File
    private static long _custRecCount; // number of detail records in Consumer Information File
    private static long _cbiRecCount; // number of detail records in Consumer BankInfo File
    private static long _cpaRecCount; // number of detail records in Consumer Product Access File
    private static long _pmtRecCount; // number of detail records in Payment Information File
    private static long _cpRecCount; // number of detail records in Consumer Payee Information File
    private static long _payeeRecCount; // number of detail records in Payee Information File
    private static int _fileSeqNum; // file sequence number used to number all file types

    // Fulfillment system info
    private static int _routeID = -1;
    private static double _paymentCost = -1;


    ///////////////////////////////////////////////////////////////////////////
    // Static initializer.
    ///////////////////////////////////////////////////////////////////////////
    public static void init() {
        try {
            readProperties();
            initFileNames();
            ConsumerBankInfoFile.init();
            ConsumerCrossReferenceInfoFile.init();
            ConsumerInfoFile.init();
            ConsumerPayeeInfoFile.init();
            ConsumerProductAccessInfoFile.init();
            PayeeEditMaskInfoFile.init();
            PayeeInfoFile.init();
            PaymentHistoryInfoFile.init();

            // make sure export directory name ends with a file path separator
            if ( !_exportDir.endsWith( FILE_SEP ) ) {
                _exportDir = _exportDir + FILE_SEP;
            }
        }
        catch ( Exception e ) {
            log( e, "Failed to initialize!" );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Reads various settings from the properties file.
    ///////////////////////////////////////////////////////////////////////////
    private static void readProperties()
    {
        try {
            FFSProperties props = BPWServer.getProperties();

            // Get the export directory property.
            _exportDir = props.getProperty( DBConsts.METAVANTE_EXPORT_DIR,
                                            DEFAULT_EXPORT_DIR );
            // Get the Site ID property.
            _siteID = props.getProperty( DBConsts.METAVANTE_SITE_ID,
                                         DEFAULT_SITE_ID );
            // Get the Site Name property.
            _siteName = props.getProperty( DBConsts.METAVANTE_SITE_NAME,
                                           DEFAULT_SITE_NAME  );
            String processingMode = props.getProperty(DBConsts.METAVANTE_PROCESSING_MODE, "D");
            if (processingMode.equalsIgnoreCase("DEVELOPMENT")) {
                _processingMode = 'D';
            }
            else if (processingMode.equalsIgnoreCase("PRODUCTION")) {
                _processingMode = 'P';
            }
            else if (processingMode.equalsIgnoreCase("QUALITY")) {
                _processingMode = 'T';
            }
        }
        catch ( Exception e ) {
            log( e, "Failed reading the property file!" );
        }
    }

    @Autowired
    private FileHandlerProvider fileHandlerProvider;
    
    ///////////////////////////////////////////////////////////////////////////
    // Default constructor.
    ///////////////////////////////////////////////////////////////////////////
    public MetavanteHandler() throws Exception
    {
    }

    public void addPayees(PayeeInfo[] payees, FFSConnectionHolder dbh )throws Exception{}
    public void modPayees(PayeeInfo[] payees, FFSConnectionHolder dbh )throws Exception{}
    public void deletePayees(PayeeInfo[] payees, FFSConnectionHolder dbh )throws Exception{}
    public void startPayeeBatch( FFSConnectionHolder dbh ) throws Exception{}
    public void endPayeeBatch( FFSConnectionHolder dbh ) throws Exception{}

    ///////////////////////////////////////////////////////////////////////////
    // com.ffusion.ffs.bpw.interfaces.start method implementation.
    ///////////////////////////////////////////////////////////////////////////
    public void start() throws Exception {}


    ///////////////////////////////////////////////////////////////////////////
    // com.ffusion.ffs.bpw.interfaces.shutdown method implementation.
    ///////////////////////////////////////////////////////////////////////////
    public void shutdown() throws Exception {}


    ///////////////////////////////////////////////////////////////////////////
    // com.ffusion.ffs.bpw.interfaces.FulfillmentAPI method implementation.
    ///////////////////////////////////////////////////////////////////////////
    public void startPmtBatch( FFSConnectionHolder dbh ) throws Exception
    {
        log( "FulfillmentAPI.startPmtBatch() starting..." );

        FFSDebug.log("MetavanteHandler: looding information to the cache. Please wait...." );
        init();                   
        FFSDebug.log("MetavanteHandler: cache loaded successfully ....." );

        if ( _fileSeqNum == 0 ) {
            _fileSeqNum = DBUtil.getIndex( dbh, DBConsts.METAVANTE_RQ_FILE_SEQ );
        }

        String fileSeqNum = formatNumber( String.valueOf( _fileSeqNum ), 10 );
        String siteID = _siteID;
        //omer
        String fileType = FT_PAYMENTS_IN;

        String pmtFileName = getRqFileName(fileType);//omer  + '.' + siteID + '.' + fileSeqNum + ".PAY";
        _pmtFile = new File( pmtFileName );
        _pmtFile.setFileHandlerProvider(fileHandlerProvider);
        _pmtTotal = BPWUtil.getBigDecimal("0.0");
        _pmtRecCount = 0;

        // Need to pad Site ID and File Type
        siteID = formatString( siteID, 5 );

        buildHeader( _pmtFile, formatString( fileType, 15 ), siteID, fileSeqNum );

        log( "FulfillmentAPI.startPmtBatch() done." );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Outputs a message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    private static void log( String str )
    {
        FFSDebug.log( "Metavante Adapter: " + str );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Outputs an exception and a message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    private static void log( Throwable t, String str )
    {
        FFSDebug.log( t, "Metavante Adapter: " + str );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Outputs a warning message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    private static void warning( String msg )
    {
        FFSDebug.log( "*** WARNING! Metavante Adapter: " + msg );
    }

    /**
     * Creates a right justified, zero filled amount string of given length.
     * Metavante amount values has no decimal point character and "12345" is
     * treated as $123.45.
     * @param amt amount to format
     * @param len length of formatted string
     * @return formatted string
     */
    private static String formatAmount(String amt, int len) {
    	BigDecimal bd = BPWUtil.getBigDecimal(amt, 2) .movePointRight(2);
    	return formatNumber(bd.toString(), len);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Creates a right justified, zero filled number string of given length.
    ///////////////////////////////////////////////////////////////////////////
    private static String formatNumber( String num, int len )
    {
        if ( num == null ) {
            num = "";
        }
        int l = num.length();
        if ( l >= len ) {
            return num.substring( 0, len );
        }
        else {
            StringBuffer sb = new StringBuffer( len );
            int padSize = len - l;
            for ( int i = 0; i < padSize; i++ ) {
                sb.append( '0' );
            }
            sb.append( num );
            return sb.toString();
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Creates a left justified, space filled string of given length.
    ///////////////////////////////////////////////////////////////////////////
    private static String formatString( String str, int len )
    {
        if ( str == null ) {
            str = "";
        }
        int l = str.length();
        if ( l >= len ) {
            return str.substring( 0, len );
        }
        else {
            StringBuffer sb = new StringBuffer( len );
            sb.append( str );
            int padSize = len - l;
            for ( int i = 0; i < padSize; i++ ) {
                sb.append( ' ' );
            }
            return sb.toString();
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Fromats a phone number String by removing any non-digit characters.
    ///////////////////////////////////////////////////////////////////////////
    private static String formatPhoneNumber( String num )
    {
        if ( num == null ) {
            return "";
        }
        int l = num.length();
        StringBuffer sb = new StringBuffer( 10 );
        for ( int i = 0; i < l; i++ ) {
            char c = num.charAt( i );
            if ( Character.isDigit( c ) ) {
                sb.append( c ); // only append digits and skip everything else
            }
        }
        return sb.toString();
    }


    ///////////////////////////////////////////////////////////////////////////
    // Fromats a Zip Code String by removing any non-digits and non-letters.
    ///////////////////////////////////////////////////////////////////////////
    private static String formatZipCode( String str )
    {
        if ( str == null ) {
            return "";
        }
        int l = str.length();
        StringBuffer sb = new StringBuffer( 9 );
        for ( int i = 0; i < l; i++ ) {
            char c = str.charAt( i );
            if ( Character.isLetterOrDigit( c ) ) {
                sb.append( c ); // only append digits and letters
            }
        }
        return sb.toString();
    }


    ///////////////////////////////////////////////////////////////////////////
    // Removes padding from a right justified, zero filled number string.
    ///////////////////////////////////////////////////////////////////////////
    public static String removeNumberPadding( String num )
    {
        num = num.trim();
        int idx;
        int len = num.length();
        for ( idx = 0; idx < len; idx++ ) {
            if ( num.charAt( idx ) != '0' ) break;
        }
        return num.substring( idx );
    }


    ///////////////////////////////////////////////////////////////////////////
    // com.ffusion.ffs.bpw.interfaces.FulfillmentAPI method implementation.
    // Note: this method is guaranteed to be called as the last FulfillmentAPI
    // call of the scheduler cycle. That is why we move all request files
    // to export directory and increment the file sequence number here.
    ///////////////////////////////////////////////////////////////////////////
    public void endPmtBatch( FFSConnectionHolder dbh ) throws Exception
    {   
        log( "FulfillmentAPI.endPmtBatch() starting..." );

        buildTrailer( _pmtFile.getName(), _pmtRecCount, _pmtTotal.toString() );

        _pmtTotal = BPWUtil.getBigDecimal("0.0");
        _pmtRecCount = 0;
        _cpRecCount = 0;
        _payeeRecCount = 0;

        // The files are done so move all of them to the export directory. 
        moveFileToExportDir( _pmtFile );

        // Log to File Monitor Log
        FMLogAgent.writeToFMLog(dbh,
                                DBConsts.BPW_METAVANTE_FILETYPE_PMTINFO,
                                _exportDir + _pmtFile.getName(),
                                DBConsts.BPTW,
                                DBConsts.METAVANTE,
                                FMLogRecord.STATUS_COMPLETE);

        moveFileToExportDir( _cpFile );

        // Log to File Monitor Log
        FMLogAgent.writeToFMLog(dbh,
                                DBConsts.BPW_METAVANTE_FILETYPE_CONSPAYEEINFO,
                                _exportDir + _cpFile.getName(),
                                DBConsts.BPTW,
                                DBConsts.METAVANTE,
                                FMLogRecord.STATUS_COMPLETE);

        moveFileToExportDir( _payeeFile );

        // Log to File Monitor Log
        FMLogAgent.writeToFMLog(dbh,
                                DBConsts.BPW_METAVANTE_FILETYPE_PAYEEINFO,
                                _exportDir + _payeeFile.getName(),
                                DBConsts.BPTW,
                                DBConsts.METAVANTE,
                                FMLogRecord.STATUS_COMPLETE);

        _pmtFile = null;
        _cpFile = null;
        _payeeFile = null;

        // Everything succeeded so increment the _fileSeqNum
        _fileSeqNum = DBUtil.getNextIndex( DBConsts.METAVANTE_RQ_FILE_SEQ );

        log( "FulfillmentAPI.endPmtBatch() done." );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Moves given file to export directory.
    ///////////////////////////////////////////////////////////////////////////
    private void moveFileToExportDir( File file )
    {
        String src = file.getName();
        String dst = _exportDir + src;
        log( "Moving \"" + src + "\" to \"" + dst + "\"." );
        File dstFile = new File( dst );
        dstFile.setFileHandlerProvider(fileHandlerProvider);
        file.renameTo( new File( dst ) );
    }


    ///////////////////////////////////////////////////////////////////////////
    // com.ffusion.ffs.bpw.interfaces.FulfillmentAPI method implementation.
    ///////////////////////////////////////////////////////////////////////////
    public void startCustomerPayeeBatch( FFSConnectionHolder dbh )
    throws Exception
    {
        log( "FulfillmentAPI.startCustomerPayeeBatch() starting..." );

        if ( _fileSeqNum == 0 ) {
            _fileSeqNum = DBUtil.getIndex( dbh, DBConsts.METAVANTE_RQ_FILE_SEQ );
        }

        startCustomerPayeeFile();
        startPayeeFile();

        log( "FulfillmentAPI.startCustomerPayeeBatch() done." );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Creates Metavante Cons-Payee request file.
    ///////////////////////////////////////////////////////////////////////////
    private void startCustomerPayeeFile() throws Exception
    {           
        String fileSeqNum = formatNumber( String.valueOf( _fileSeqNum ), 10 );
        String siteID = _siteID;
        String fileType = FT_CONS_PAYEE_IN;

        String cpFileName = getRqFileName(fileType);//omer + '.' + siteID + '.' + fileSeqNum + ".SVF";
        _cpFile = new File( cpFileName );
        _cpFile.setFileHandlerProvider(fileHandlerProvider);
        _cpRecCount = 0;

        // Need to pad Site ID and File Type
        siteID = formatString( siteID, 5 );

        buildHeader( _cpFile, formatString( fileType, 15 ), siteID, fileSeqNum );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Creates Metavante Payee request file.
    ///////////////////////////////////////////////////////////////////////////
    private void startPayeeFile() throws Exception
    {   
        String fileSeqNum = formatNumber( String.valueOf( _fileSeqNum ), 10 );
        String siteID = _siteID;
        String fileType = FT_PAYEE_IN;

        _payeeFile = new File( getRqFileName(FT_PAYEE_IN) );
        _payeeFile.setFileHandlerProvider(fileHandlerProvider);
        _payeeRecCount = 0;

        // Need to pad Site ID and File Type
        siteID = formatString( siteID, 5 );

        buildHeader( _payeeFile, formatString( fileType, 15 ), siteID, fileSeqNum );
    }


    ///////////////////////////////////////////////////////////////////////////
    // com.ffusion.ffs.bpw.interfaces.FulfillmentAPI method implementation.
    ///////////////////////////////////////////////////////////////////////////
    public void endCustomerPayeeBatch( FFSConnectionHolder dbh )
    throws Exception
    {
        log( "FulfillmentAPI.endCustomerPayeeBatch() starting..." );

        // Finish creating payee and customer payee files.
        buildTrailer( _cpFile.getName(), _cpRecCount, "0.0" );
        buildTrailer( _payeeFile.getName(), _payeeRecCount, "0.0" );

        log( "FulfillmentAPI.endCustomerPayeeBatch() done." );
    }


    ///////////////////////////////////////////////////////////////////////////
    // com.ffusion.ffs.bpw.interfaces.FulfillmentAPI method implementation.
    ///////////////////////////////////////////////////////////////////////////
    public void startCustBatch( FFSConnectionHolder dbh ) throws Exception
    {
        log( "MetavanteHandler.startCustBatch() starting..." );

        String fileSeqNum = formatNumber( String.valueOf( _fileSeqNum ), 10 );
        String siteID = _siteID;

        _custFile = new File( getRqFileName(FT_CONSUMER_IN) );
        _custFile.setFileHandlerProvider(fileHandlerProvider);
        _cbiFile = new File( getRqFileName(FT_BANK_IN) );
        _cbiFile.setFileHandlerProvider(fileHandlerProvider);
        _cpaFile = new File( getRqFileName(FT_CONSPRDACC_IN) );
        _cpaFile.setFileHandlerProvider(fileHandlerProvider);
        
        _custRecCount = 0;
        _cbiRecCount = 0;
        _cpaRecCount = 0;

        // Need to pad Site ID and File Type
        siteID = formatString( siteID, 5 );

        buildHeader( _custFile, formatString( FT_CONSUMER_IN, 15 ), siteID, fileSeqNum );
        buildHeader( _cbiFile, formatString( FT_BANK_IN, 15 ), siteID, fileSeqNum );
        buildHeader( _cpaFile, formatString( FT_CONSPRDACC_IN, 15 ), siteID, fileSeqNum );

        log( "MetavanteHandler.startCustBatch() done." );
    }


    ///////////////////////////////////////////////////////////////////////////
    // com.ffusion.ffs.bpw.interfaces.FulfillmentAPI method implementation.
    ///////////////////////////////////////////////////////////////////////////
    public void endCustBatch( FFSConnectionHolder dbh ) throws Exception
    {
        log( "MetavanteHandler.endCustBatch() starting..." );

        buildTrailer( _custFile.getName(), _custRecCount, "0.0" );
        buildTrailer( _cbiFile.getName(), _cbiRecCount, "0.0" );
        buildTrailer( _cpaFile.getName(), _cpaRecCount, "0.0" );

        _custRecCount = 0;
        _cbiRecCount = 0;
        _cpaRecCount = 0;

        // The files are done so move all of them to the export directory. 
        moveFileToExportDir( _custFile );

        // Log to File Monitor Log
        FMLogAgent.writeToFMLog(dbh,
                                DBConsts.BPW_METAVANTE_FILETYPE_CONSINFO,
                                _exportDir + _custFile.getName(),
                                DBConsts.BPTW,
                                DBConsts.METAVANTE,
                                FMLogRecord.STATUS_COMPLETE);

        moveFileToExportDir( _cbiFile );

        // Log to File Monitor Log
        FMLogAgent.writeToFMLog(dbh,
                                DBConsts.BPW_METAVANTE_FILETYPE_CONSBANKINFO,
                                _exportDir + _cbiFile.getName(),
                                DBConsts.BPTW,
                                DBConsts.METAVANTE,
                                FMLogRecord.STATUS_COMPLETE);

        moveFileToExportDir( _cpaFile );

        // Log to File Monitor Log
        FMLogAgent.writeToFMLog(dbh,
                                DBConsts.BPW_METAVANTE_FILETYPE_CONSPRODACCESSINFO,
                                _exportDir + _cpaFile.getName(),
                                DBConsts.BPTW,
                                DBConsts.METAVANTE,
                                FMLogRecord.STATUS_COMPLETE);

        _custFile = null;
        _cbiFile = null;
        _cpaFile = null;

        log( "MetavanteHandler.endCustBatch() done." );
    }

    ///////////////////////////////////////////////////////////////////////////
    // com.ffusion.ffs.bpw.interfaces.FulfillmentAPI method implementation.
    ///////////////////////////////////////////////////////////////////////////
    public int addCustomers( CustomerInfo[] customers, FFSConnectionHolder dbh )
    throws Exception
    {
        FFSDebug.log( "MetavanteHandler.addCustomers: start", PRINT_DEV );
        processCustomers( customers, 'A', dbh );
        processConsumerBankInfo( customers, 'A', dbh );
        processConsumerProductAccess( customers, 'A', dbh );
        FFSDebug.log( "MetavanteHandler.addCustomers: done.", PRINT_DEV );
        return 0;
    }


    ///////////////////////////////////////////////////////////////////////////
    // com.ffusion.ffs.bpw.interfaces.FulfillmentAPI method implementation.
    ///////////////////////////////////////////////////////////////////////////
    public int modifyCustomers( CustomerInfo[] customers, FFSConnectionHolder dbh )
    throws Exception
    {
        FFSDebug.log( "MetavanteHandler.modifyCustomers: start", PRINT_DEV );
        processCustomers( customers, 'M', dbh );
        processConsumerBankInfo( customers, 'M', dbh );
        processConsumerProductAccess( customers, 'M', dbh );
        FFSDebug.log( "MetavanteHandler.modifyCustomers: done.", PRINT_DEV );
        return 0;
    }


    ///////////////////////////////////////////////////////////////////////////
    // com.ffusion.ffs.bpw.interfaces.FulfillmentAPI method implementation.
    ///////////////////////////////////////////////////////////////////////////
    public int deleteCustomers( CustomerInfo[] customers, FFSConnectionHolder dbh )
    throws Exception
    {
        FFSDebug.log( "MetavanteHandler.deleteCustomers: start", PRINT_DEV );
        processCustomers( customers, 'D', dbh );
        processConsumerBankInfo( customers, 'D', dbh );
        processConsumerProductAccess( customers, 'D', dbh );
        FFSDebug.log( "MetavanteHandler.deleteCustomers: done.", PRINT_DEV );
        return 0;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Outputs detail lines to a Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void processCustomers( CustomerInfo[] info,
                                   char action,
                                   FFSConnectionHolder dbh )
    throws Exception
    {
        FFSDebug.log( "MetavanteHandler.processCustomers: start", PRINT_DEV );
        FileWriter fw = null;
        PrintWriter pw = null;
        try {
	        int length = info.length;
	        if ( length == 0 ) return;
	        fw = new FileWriter( _custFile.getName(), true );
	        fw.setFileHandlerProvider(fileHandlerProvider);
	
	        pw = new PrintWriter( new BufferedWriter(fw) );
	
	        // Output a line for each customer record
	        for ( int i = 0; i < length; i++ ) {
	            if ( info[i] == null ) continue;
	            if ( info[i].status.equals(DBConsts.MODACCT) ) action = 'N';
	
	            StringBuffer sb = new StringBuffer( 400 );
	
	            ConsumerCrossReferenceInfoFile xref = 
	            ConsumerCrossReferenceInfoFile.lookupBySponsorConsumerID( info[i].customerID, dbh );
	            if (xref == null) {
	                warning( "No BPW_ConsumerCrossReference record can be found for CustomerID \""
	                         + info[i].customerID  );
	                continue;
	            }
	            String consumerID = xref._consumerID;               // Consumer ID
	            if ( consumerID == null ) {
	                warning( "No BPW_ConsumerCrossReference record can be found for CustomerID \""
	                         + info[i].customerID  );
	                continue;
	            }
	
	            sb.append( DETAIL_LINE_INDICATOR );                 // detail line indicator
	            sb.append( formatString( xref._consumerType, 10 ) );
	            sb.append( formatString( xref._consumerSSN, 9 ) );
	            sb.append( formatString( xref._federalTaxID, 9 ) );
	            sb.append( formatNumber( String.valueOf( xref._sponsorID ), 10 ) );    // Sponsor ID
	            sb.append( formatString( xref._sponsorConsumerID, 20 ) );
	            sb.append( formatString( info[i].billingPlan, 10 ) );
	            sb.append( formatString( info[i].firstName, 25 ) );
	            sb.append( formatString( info[i].initial, 1 ) );
	            sb.append( formatString( info[i].lastName, 25 ) );
	            sb.append( formatString( info[i].suffix, 5 ) );
	            sb.append( formatString( info[i].addressLine1, 35 ) );
	            sb.append( formatString( info[i].addressLine2, 35 ) );
	            sb.append( formatString( info[i].city, 28 ) );
	            sb.append( formatString( info[i].state, 2 ) );
	            sb.append( formatString( info[i].zipcode, 9 ) );
	            sb.append( formatString( info[i].country, 10 ) );
	            sb.append( formatString( info[i].phone1, 10 ) );
	            sb.append( formatString( info[i].countryCode1, 3 ) );
	            sb.append( formatString( info[i].phone2, 10 ) );
	            sb.append( formatString( info[i].countryCode2, 3 ) );
	            sb.append( formatString( info[i].acctVerification, 25 ) );
	            sb.append( formatString( info[i].securityCode, 10 ) );
	            sb.append( formatString( "" /*info[i].jointSSN*/, 9 ) );
	            sb.append( formatString( info[i].jointFirstName, 25 ) );
	            sb.append( formatString( info[i].jointInitial, 1 ) );
	            sb.append( formatString( info[i].jointLastName, 25 ) );
	            sb.append( formatString( info[i].jointSuffix, 5 ) );
	            sb.append( formatString( info[i].jointPhone1, 10 ) );
	            sb.append( formatString( info[i].jointCountryCode1, 3 ) );
	            sb.append( formatString( info[i].jointPhone2, 10 ) );
	            sb.append( formatString( info[i].jointCountryCode2, 3 ) );
	
	            sb.append( action );                       // Action to be Performed
	
	            pw.println( sb.toString() );
	            _custRecCount++;
	
	            /* the following status update is moved to ConsumerInfoFile.java
	            if ( action == 'A' ) {
	                CustRoute.updateCustRouteStatus( info[i].customerID, 
	                          _routeID, DBConsts.ACTIVE, dbh);
	            }
	            else if ( action == 'D' ) {
	                CustRoute.updateCustRouteStatus( info[i].customerID, 
	                          _routeID, DBConsts.CLOSED, dbh);
	            }
	            else if ( action == 'M' ) {
	                CustRoute.updateCustRouteStatus( info[i].customerID, 
	                          _routeID, DBConsts.ACTIVE, dbh);
	            }
	            else if ( action == 'N' ) {
	                CustRoute.updateCustRouteStatus( info[i].customerID, 
	                          _routeID, DBConsts.ACTIVE, dbh);
	            }
	            */
	        }
	
	        pw.flush();
	        pw.close();
        } catch(Exception e) {
        	throw e;
        } finally {
        	if(null != fw) {
        		try {
        			fw.close();
        		} catch(Exception e) {
        			// ignore
        		}
        	}
        	if(null != pw) {
        		try {
        			pw.close();
        		} catch(Exception e) {
        			// ignore
        		}
        	}
        }
        FFSDebug.log( "MetavanteHandler.processCustomers: done", PRINT_DEV );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Outputs detail lines to a Consumer BankInfo File.
    ///////////////////////////////////////////////////////////////////////////
    private void processConsumerBankInfo( CustomerInfo[] info,
                                          char action,
                                          FFSConnectionHolder dbh )
    throws Exception
    {
        FFSDebug.log( "MetavanteHandler.processConsumerBankInfo: start", PRINT_DEV );
        FileWriter fw = null;
        BufferedWriter bw = null;
        PrintWriter pw = null;
        try {
	        int length = info.length;
	        if ( length == 0 ) return;
	
	        fw = new FileWriter( _cbiFile.getName(), true );
	        fw.setFileHandlerProvider(fileHandlerProvider);
	        bw = new BufferedWriter(fw);
	        pw = new PrintWriter( bw );
	
	        // Output a line for customer record
	        for ( int i = 0; i < length; i++ ) {
	            if ( info[i] == null ) continue;
	
	            ConsumerCrossReferenceInfoFile xref = 
	            ConsumerCrossReferenceInfoFile.lookupBySponsorConsumerID( info[i].customerID, dbh );
	            if (xref == null) {
	                warning( "No BPW_ConsumerCrossReference record can be found for CustomerID \""
	                         + info[i].customerID  );
	                continue;
	            }
	            String consumerID = xref._consumerID;               // Consumer ID
	            if ( consumerID == null ) {
	                warning( "No BPW_ConsumerCrossReference record can be found for CustomerID \""
	                         + info[i].customerID  );
	                continue;
	            }
	            //ConsumerBankInfoFile cbinfo = ConsumerBankInfoFile.lookup( consumerID );
	            if (action == 'A') {
	                writeConsumerBankInfo('A', consumerID, pw, xref, dbh);
	            }
	            if (action == 'D') {
	                writeConsumerBankInfo('N', consumerID, pw, xref, dbh);
	                writeConsumerBankInfo('D', consumerID, pw, xref, dbh);
	            }
	            if (action == 'M') {
	                writeConsumerBankInfo('d', consumerID, pw, xref, dbh);
	                writeConsumerBankInfo('a', consumerID, pw, xref, dbh);
	                writeConsumerBankInfo('N', consumerID, pw, xref, dbh);
	            }
	            _cbiRecCount++;
	        }
	
	        pw.flush();
	        pw.close();
        } catch(Exception e) {
        	throw e;
        } finally {
        	if(null != pw) {
        		try {
        			pw.close();
        		} catch(Exception e) {
        			// ignore
        		}
        	}
        	if(null != bw) {
        		try {
        			bw.close();
        		} catch(Exception e) {
        			// ignore
        		}
        	}
        	if(null != fw) {
        		try {
        			fw.close();
        		} catch(Exception e) {
        			// ignore
        		}
        	}
        }
        FFSDebug.log( "MetavanteHandler.processConsumerBankInfo: done", PRINT_DEV );
    }

    private void writeConsumerBankInfo( char action, String consumerID, PrintWriter pw, 
                                        ConsumerCrossReferenceInfoFile xref,
                                        FFSConnectionHolder dbh )
    throws Exception
    {
        CustomerBankInfo[] cbinfos;
        if (action == 'A') {
            cbinfos = CustomerBank.getByConsumerIDAndStatus(consumerID, DBConsts.NEW, dbh);
        }
        else if (action == 'D') {
            cbinfos = CustomerBank.getByConsumerIDAndStatus(consumerID, DBConsts.CANC, dbh);
        }
        else if (action == 'a') {
            cbinfos = CustomerBank.getByConsumerIDAndStatus(consumerID, DBConsts.NEW, dbh);
            action = 'A';
        }
        else if (action == 'd') {
            cbinfos = CustomerBank.getByConsumerIDAndStatus(consumerID, DBConsts.CANC, dbh);
            action = 'D';
        }
        else { // 'N'
            cbinfos = CustomerBank.getByConsumerIDAndStatus(consumerID, DBConsts.ACTIVE, dbh);
        }


        for (int j = 0; j < cbinfos.length; j++) {
            StringBuffer sb = new StringBuffer( 400 );
            sb.append( DETAIL_LINE_INDICATOR );                 // detail line indicator
            sb.append( formatString( xref._consumerType, 10 ) );
            sb.append( formatNumber( String.valueOf( xref._sponsorID ), 10 ) );    // Sponsor ID
            sb.append( formatString( xref._sponsorConsumerID, 20 ) );
            sb.append( formatString( cbinfos[j].routingAndTransitNumber, 9 ) );
            sb.append( formatString( cbinfos[j].acctNumber, 20 ) );
            sb.append( formatString( cbinfos[j].acctType, 10 ) );   // "DDA", SV"
            //if (cbinfo[i].status)
            sb.append( action );                       // Action to be Performed
            sb.append( formatString( cbinfos[j].primaryAcctFlag, 1 ) );  /***/
            pw.println( sb.toString() );
            if (action == 'A') {
                CustomerBank.updateStatus( cbinfos[j], DBConsts.NEW, DBConsts.ACTIVE, dbh);
            }
            else if (action == 'D') {
                if (cbinfos[j].status.equals(DBConsts.CANC))
                    CustomerBank.deleteByStatus(cbinfos[j], DBConsts.CANC, dbh);
            }
            else if (action == 'a') {
                CustomerBank.updateStatus( cbinfos[j], DBConsts.NEW, DBConsts.ACTIVE, dbh);
            }
            else if (action == 'd') {
                CustomerBank.deleteByStatus(cbinfos[j], DBConsts.CANC, dbh);
            }
        }                                         
    }
    ///////////////////////////////////////////////////////////////////////////
    // Outputs detail lines to a Consumer BankInfo File.
    ///////////////////////////////////////////////////////////////////////////
    private void processConsumerProductAccess( CustomerInfo[] info,
                                               char action,
                                               FFSConnectionHolder dbh )
    throws Exception
    {
        FFSDebug.log( "MetavanteHandler.processConsumerProductAccess: start", PRINT_DEV );
        FileWriter fw = null;
        BufferedWriter bw = null;
        PrintWriter pw = null;
        try {
	        int length = info.length;
	        if ( length == 0 ) return;
	
	        fw = new FileWriter( _cpaFile.getName(), true ) ;
	        fw.setFileHandlerProvider(fileHandlerProvider);
	        bw = new BufferedWriter(fw);
	        pw = new PrintWriter( bw );
	
	        // Output a line for customer record
	        for ( int i = 0; i < length; i++ ) {
	            if ( info[i] == null ) continue;
	
	            ConsumerCrossReferenceInfoFile xref = 
	            ConsumerCrossReferenceInfoFile.lookupBySponsorConsumerID( info[i].customerID, dbh );
	            if (xref == null) {
	                warning( "No BPW_ConsumerCrossReference record can be found for CustomerID \""
	                         + info[i].customerID  );
	                continue;
	            }
	            String consumerID = xref._consumerID;               // Consumer ID
	            if ( consumerID == null ) {
	                warning( "No BPW_ConsumerCrossReference record can be found for CustomerID \""
	                         + info[i].customerID  );
	                continue;
	            }
	            //ConsumerProductAccessInfoFile cpainfo = ConsumerProductAccessInfoFile.lookup( consumerID );
	
	            if (action == 'A') {
	                writeConsumerProductAccess('A', consumerID, pw, xref, dbh);
	            }
	            if (action == 'D') {
	                writeConsumerProductAccess('N', consumerID, pw, xref, dbh);
	                writeConsumerProductAccess('D', consumerID, pw, xref, dbh);
	            }
	            if (action == 'M') {
	                writeConsumerProductAccess('d', consumerID, pw, xref, dbh);
	                writeConsumerProductAccess('a', consumerID, pw, xref, dbh);
	                writeConsumerProductAccess('N', consumerID, pw, xref, dbh);
	            }
	            _cpaRecCount++;
	        }
	
	        pw.flush();
	        pw.close();
        } catch(Exception e) {
        	throw e;
        } finally {
        	if(null != pw) {
        		try {
        			pw.close();
        		} catch(Exception e) {
        			// ignore
        		}
        	}
        	if(null != bw) {
        		try {
        			bw.close();
        		} catch(Exception e) {
        			// ignore
        		}
        	}
        	if(null != fw) {
        		try {
        			fw.close();
        		} catch(Exception e) {
        			// ignore
        		}
        	}
        }
        FFSDebug.log( "MetavanteHandler.processConsumerProductAccess: done", PRINT_DEV );
    }

    private void writeConsumerProductAccess( char action, String consumerID, PrintWriter pw, 
                                             ConsumerCrossReferenceInfoFile xref,
                                             FFSConnectionHolder dbh )
    throws Exception
    {
        String accessType;
        if (action == 'A') {
            accessType = CustomerProductAccess.getAccessTypeByStatus(consumerID, DBConsts.NEW, dbh);
            CustomerProductAccess.updateStatus( consumerID, DBConsts.NEW, DBConsts.ACTIVE, dbh);
        }
        else if (action == 'D') {
            accessType = CustomerProductAccess.getAccessTypeByStatus(consumerID, DBConsts.CANC, dbh);
            CustomerProductAccess.deleteByStatus(consumerID, DBConsts.CANC, dbh);
        }
        else if (action == 'a') {
            accessType = CustomerProductAccess.getAccessTypeByStatus(consumerID, DBConsts.NEW, dbh);
            CustomerProductAccess.updateStatus( consumerID, DBConsts.NEW, DBConsts.ACTIVE, dbh);
            action = 'A';
        }
        else if (action == 'd') {
            accessType = CustomerProductAccess.getAccessTypeByStatus(consumerID, DBConsts.CANC, dbh);
            CustomerProductAccess.deleteByStatus(consumerID, DBConsts.CANC, dbh);
            action = 'D';
        }
        else {
            accessType = CustomerProductAccess.getAccessTypeByStatus(consumerID, DBConsts.ACTIVE, dbh);
        }
        if (accessType == null) {
            return;
        }
        StringBuffer sb = new StringBuffer( 400 );
        sb.append( DETAIL_LINE_INDICATOR );                 // detail line indicator
        sb.append( formatString( xref._consumerType, 10 ) );
        sb.append( formatNumber( String.valueOf( xref._sponsorID ), 10 ) );    // Sponsor ID
        sb.append( formatString( xref._sponsorConsumerID, 20 ) );
        sb.append( formatString( accessType, 10 ) );   // "PC", "PHONE", "INTERNET"
        //sb.append( formatString( cpainfo._productType, 10 ) );
        sb.append( formatString( "BILLPAY", 10 ) );   // Product Type="BILLPAY"
        sb.append( formatString( _siteID, 5 ) );      // Device ID/Site Name
        sb.append( action );                       // Action to be Performed
        pw.println( sb.toString() );
    }

    ///////////////////////////////////////////////////////////////////////////
    // com.ffusion.ffs.bpw.interfaces.FulfillmentAPI method implementation.
    ///////////////////////////////////////////////////////////////////////////
    public void addCustomerPayees( CustomerPayeeInfo[] info,
                                   PayeeInfo[] payees,
                                   FFSConnectionHolder dbh )
    throws Exception
    {
        log( "FulfillmentAPI.addCustomerPayees() starting..." );
        processPayees( info, payees, dbh );
        processCustomerPayees( info, payees, 'A', dbh );
        log( "FulfillmentAPI.addCustomerPayees() done." );
    }

    ///////////////////////////////////////////////////////////////////////////
    // com.ffusion.ffs.bpw.interfaces.FulfillmentAPI method implementation.
    ///////////////////////////////////////////////////////////////////////////
    public void modCustomerPayees( CustomerPayeeInfo[] info,
                                   PayeeInfo[] payees,
                                   FFSConnectionHolder dbh )
    throws Exception
    {
        log( "FulfillmentAPI.modCustomerPayees() starting..." );
        processPayees( info, payees, dbh );
        processCustomerPayees( info, payees, 'M', dbh );
        log( "FulfillmentAPI.modCustomerPayees() done." );
    }

    ///////////////////////////////////////////////////////////////////////////
    // com.ffusion.ffs.bpw.interfaces.FulfillmentAPI method implementation.
    ///////////////////////////////////////////////////////////////////////////
    public void deleteCustomerPayees( CustomerPayeeInfo[] info,
                                      PayeeInfo[] payees,
                                      FFSConnectionHolder dbh )
    throws Exception
    {
        log( "FulfillmentAPI.deleteCustomerPayees() starting..." );
        processCustomerPayees( info, payees, 'D', dbh );
        log( "FulfillmentAPI.deleteCustomerPayees() done." );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Outputs detail lines to a Consumer Payee Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void processCustomerPayees( CustomerPayeeInfo[] info,
                                        PayeeInfo[] payees,
                                        char action,
                                        FFSConnectionHolder dbh )
    throws Exception
    {
        log( "processCustomerPayees() starting..." );
        FileWriter fw = null;
        BufferedWriter bw = null;
        PrintWriter pw = null;
        try {
	        int length = info.length;
	        if ( length == 0 ) return;
	
	        fw = new FileWriter( _cpFile.getName(), true );
	        fw.setFileHandlerProvider(fileHandlerProvider);
	        bw = new BufferedWriter(fw);
	        pw = new PrintWriter( bw );
	
	        // Output a line for each customer payee record
	        for ( int i = 0; i < length; i++ ) {
	            if ( info[i] == null ) continue;
	
	            StringBuffer sb = new StringBuffer( 70 );
	
	            /* the following status update is moved to ConsumerPayeeInfoFile.java
	            if ( action == 'A' || action == 'M' ) {
	                //CustPayee.updateStatus( info[i].CustomerID, info[i].PayeeListID,
	                //                        DBConsts.ACTIVE, dbh );
	                CustPayeeRoute.updateCustPayeeRouteStatus(info[i].CustomerID, info[i].PayeeListID,
	                                                          _routeID, DBConsts.ACTIVE, dbh);
	            }
	            else if ( action == 'D' ) {
	                CustPayee.updateStatus( info[i].CustomerID, info[i].PayeeListID,
	                                        DBConsts.CLOSED, dbh );
	                CustPayeeRoute.updateCustPayeeRouteStatus(info[i].CustomerID, info[i].PayeeListID,
	                                                          _routeID, DBConsts.CLOSED, dbh);
	            }
	            */
	
	            sb.append( DETAIL_LINE_INDICATOR );                                    // detail line indicator
	
	            String consumerID = getConsumerID( info[i].CustomerID, dbh );          // Consumer ID
	            if ( consumerID != null ) {
	                sb.append( formatString( consumerID, 22 ) );
	            }
	            else {
	                warning( "No BPW_ConsumerCrossReference record can be found for CustomerID \""
	                         + info[i].CustomerID + "\". Record for customer-payee with PayeeID: \"" +
	                         info[i].PayeeID + "\" and PayeeListID: \"" + info[i].PayeeListID +
	                         "\" cannot be generated!"  );
	                continue;
	            }
	
	            sb.append( formatNumber( String.valueOf( info[i].PayeeListID ), 4 ) ); // Consumer-Payee Reference Number
	
	            if ( payees[i] == null || payees[i].ExtdPayeeID == null ||              // Internal Payee ID
	                 payees[i].ExtdPayeeID.equals( PayeeInfo.DEFAULT_EXTD_PAYEE_ID ) ) {
	                sb.append( formatString( payeeIdToInternalPayeeId( info[i].PayeeID ), 15 ) );
	            }
	            else {
	                sb.append( formatString( payees[i].ExtdPayeeID, 15 ) );
	            }
	
	            sb.append( formatString( info[i].PayAcct, 25 ) );                      // Consumer-Payee Account Number
	            sb.append( action );                                                   // Action to be Performed
	
	            pw.println( sb.toString() );
	            _cpRecCount++;
	        }
	
	        pw.flush();
	        pw.close();
        } catch(Exception e) {
        	throw e;
        } finally {
        	if(null != pw) {
        		try {
        			pw.close();
        		} catch(Exception e) {
        			// ignore
        		}
        	}
        	if(null != bw) {
        		try {
        			bw.close();
        		} catch(Exception e) {
        			// ignore
        		}
        	}
        	if(null != fw) {
        		try {
        			fw.close();
        		} catch(Exception e) {
        			// ignore
        		}
        	}
        }
        log( "processCustomerPayees() done." );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Looks up Consumer ID in the Consumer Cross Reference Information File.
    ///////////////////////////////////////////////////////////////////////////
    private static String getConsumerID( String customerID, FFSConnectionHolder dbh ) 
    throws Exception
    {
        ConsumerCrossReferenceInfoFile xref = 
            ConsumerCrossReferenceInfoFile.lookupBySponsorConsumerID( customerID, dbh );
        return xref == null ? null : xref._consumerID;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Outputs detail lines to a Payee Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void processPayees( CustomerPayeeInfo[] info,
                                PayeeInfo[] payees,
                                FFSConnectionHolder dbh )
    throws Exception
    {
        log( "processPayees() starting..." );
        FileWriter fw = null;
        BufferedWriter bw = null;
        PrintWriter pw = null;
        try {
	        int length = payees.length;
	        if ( length == 0 ) return;
	
	        fw = new FileWriter( _payeeFile.getName(), true );
	        fw.setFileHandlerProvider(fileHandlerProvider);
	        bw = new BufferedWriter(fw);
	        pw = new PrintWriter( bw );
	
	        // Output a line for each customer payee record
	        StringBuffer notToAdd = new StringBuffer();
	        notToAdd.append("This list of payees already submitted to Metavate before. They will be ignored: ");
	        boolean hasNotToAdd = false;
	        for ( int i = 0; i < length; i++ ) {
	            if ( info[i] == null || payees[i] == null ||
	                payees[i].PayeeType != DBConsts.PERSONAL ||  payees[i].Status.compareTo(DBConsts.NEW)!=0 ) {
	                notToAdd.append(payees[i].PayeeID);
	                notToAdd.append(", ");
	                hasNotToAdd = true;
	                continue;
	            }
	            StringBuffer sb = new StringBuffer( 256 );
	
	            payees[i].ExtdPayeeID = payeeIdToInternalPayeeId( payees[i].PayeeID );
	            //payees[i].Status = DBConsts.INPROCESS;
	            PayeeRouteInfo pri = PayeeInfoFile.createPayeeRouteInfo( payees[i] );
	            PayeeInfoFile.updatePayee( payees[i], payees[i], pri, dbh );
	
	            sb.append( DETAIL_LINE_INDICATOR );                                     // detail line indicator
	            sb.append( formatString( payees[i].ExtdPayeeID, 15 ) );                 // Internal Payee ID
	            sb.append( formatString( payees[i].PayeeName, 50 ) );                   // Payee Name
	            if ( payees[i].Addr3 != null ) {
	                sb.append( formatString( payees[i].Addr1, 35 ) );                   // Attention Line
	                sb.append( formatString( payees[i].Addr2, 35 ) );                   // Address Line 1
	                sb.append( formatString( payees[i].Addr3, 35 ) );                   // Address Line 2
	            }
	            else {
	                sb.append( formatString( "", 35 ) );                                // Attention Line
	                sb.append( formatString( payees[i].Addr1, 35 ) );                   // Address Line 1
	                sb.append( formatString( payees[i].Addr2, 35 ) );                   // Address Line 2
	            }
	            sb.append( formatString( payees[i].City, 28 ) );                        // City
	            sb.append( formatString( payees[i].State, 2 ) );                        // State
	            sb.append( formatString( formatZipCode( payees[i].Zipcode ), 9 ) );     // Zip Code
	            sb.append( formatNumber( formatPhoneNumber( payees[i].Phone ), 10 ) );  // Phone Number
	
	            String consumerID = getConsumerID( info[i].CustomerID, dbh );           // Consumer ID
	            if ( consumerID != null ) {
	                sb.append( formatString( consumerID, 22 ) );
	            }
	            else {
	                warning( "No BPW_ConsumerCrossReference record can be found for CustomerID \""
	                         + info[i].CustomerID + "\". Record for payee with ExtdPayeeID: \"" +
	                         payees[i].ExtdPayeeID + "\" cannot be generated!"  );
	                continue;
	            }
	
	            pw.println( sb.toString() );
	            _payeeRecCount++;
	        }
	
	        pw.flush();
	        pw.close();
	        if (hasNotToAdd) 
	            warning(notToAdd.toString());
        } catch(Exception e) {
        	throw e;
        } finally {
        	if(null != pw) {
        		try {
        			pw.close();
        		} catch(Exception e) {
        			// ignore
        		}
        	}
        	if(null != bw) {
        		try {
        			bw.close();
        		} catch(Exception e) {
        			// ignore
        		}
        	}
        	if(null != fw) {
        		try {
        			fw.close();
        		} catch(Exception e) {
        			// ignore
        		}
        	}
        }
        log( "processPayees() done." );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Converts BPW PayeeID to Internal Payee ID by prepending to it the
    // SiteID.
    ///////////////////////////////////////////////////////////////////////////
    private static String payeeIdToInternalPayeeId( String payeeID )
    {
        if ( !payeeID.startsWith( _siteID ) ) {
            payeeID = _siteID + payeeID;
        }
        return payeeID;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Converts Internal Payee ID to BPW PayeeID by removing the SiteID.
    ///////////////////////////////////////////////////////////////////////////
    public static String internalPayeeIdToBPWPayeeId( String internalPayeeId )
    {
        if ( internalPayeeId == null ) return null;

        int len = _siteID.length();
        if ( internalPayeeId.length() <= len ||
             !internalPayeeId.startsWith( _siteID ) ) {
            return internalPayeeId; // cannot remove SiteID
        }
        return internalPayeeId.substring( len );
    }


    ///////////////////////////////////////////////////////////////////////////
    // com.ffusion.ffs.bpw.interfaces.FulfillmentAPI method implementation.
    ///////////////////////////////////////////////////////////////////////////
    public void addPayments( PmtInfo[] pmts, PayeeRouteInfo[] routeinfo,
                             FFSConnectionHolder dbh ) throws Exception
    {
        log( "FulfillmentAPI.addPayments() starting..." );
        FileWriter fw = null;
        BufferedWriter bw = null;
        PrintWriter pw = null;
        try {
	        int length = pmts.length;
	        if ( length == 0 ) return;
	
	        fw = new FileWriter( _pmtFile.getName(), true );
	        fw.setFileHandlerProvider(fileHandlerProvider);
	        bw = new BufferedWriter(fw);
	        pw = new PrintWriter( bw );
	
	        // Output a line for each payment record
	        for ( int i = 0; i < length; i++ ) {
	            if ( pmts[i] == null ) continue;
	
	            StringBuffer sb = new StringBuffer( 256 );
	
	            sb.append( DETAIL_LINE_INDICATOR );                                     // detail line indicator
	            sb.append( formatString( _siteName, 5 ) );                              // Site Name
	            pmts[i].ConfirmationNumber = Integer.toString(Integer.parseInt(pmts[i].SrvrTID) 
	                                                  % PaymentHistoryInfoFile.METAVANTE_MAX_CONFID);
	            sb.append( formatNumber( pmts[ i ].ConfirmationNumber, 8 ) );           // Confirmation Number
	
	            String consumerID = getConsumerID( pmts[i].CustomerID, dbh );           // Consumer ID
	            if ( consumerID != null ) {
	                sb.append( formatString( consumerID, 22 ) );
	            }
	            else {
	                warning( "No BPW_ConsumerCrossReference record can be found for CustomerID \""
	                         + pmts[i].CustomerID + "\". Record for payment with Confirmation Number: \"" +
	                         pmts[i].ConfirmationNumber + "\" cannot be generated!"  );
	                continue;
	            }
	
	            if ( routeinfo[i] == null || routeinfo[i].ExtdPayeeID == null ) {        // Internal Payee ID
	                sb.append( formatString( payeeIdToInternalPayeeId( pmts[i].PayeeID ), 15 ) );
	            }
	            else {
	                sb.append( formatString( routeinfo[i].ExtdPayeeID, 15 ) );
	            }
	            sb.append( formatString( pmts[i].PayAcct, 25 ) );                       // Consumer-Payee Account Number
	            sb.append( formatNumber( String.valueOf( pmts[i].PayeeListID ), 4 ) );  // Consumer-Payee Reference Number
	            sb.append( formatAmount( pmts[i].getAmt(), 10 ) );                        // Amount
	            _pmtTotal = _pmtTotal.add(BPWUtil.getBigDecimal(pmts[i].getAmt()));
	            
	            String todayDate = _dt.format(new Date());
	            sb.append( todayDate );                                                 // Date and Time Initiated
	            sb.append( todayDate.substring( 0, 8 ) );                               // Effective Date
	
	            sb.append( formatString( getPaymentType( pmts[i].PaymentType ), 10 ) ); // Payment Type
	
	            String consSettlementRefNum = 
	                getConsSettlementRefNum( consumerID, pmts[i].AcctDebitID, dbh );    // Consumer-Settlement Reference Number
	            if ( consSettlementRefNum != null ) {
	                sb.append( formatNumber( consSettlementRefNum, 10 ) );
	            }
	            else {
	                warning( "No BPW_CustomerBankInfo record can be found for ConsumerID \""
	                         + consumerID + "\" and AcctNumber \"" +  pmts[i].AcctDebitID +
	                         "\". Record for payment with Confirmation Number: \"" +
	                         pmts[i].ConfirmationNumber + "\" cannot be generated!"  );
	                continue;
	            }
	
	            sb.append( formatString( pmts[i].Memo, 50 ) );                          // Memo Line Information
	            sb.append( formatString( getAccessType( consumerID, dbh ), 10 ) );      // Access Device Type (not required)
	            sb.append( formatString( getConsFirstName( pmts[i].CustomerID, dbh ), 25 ) );// Consumer First Name (not reqired)
	
	            String consLastName = getConsLastName( pmts[i].CustomerID, dbh );       // Consumer Last Name
	            if ( consLastName != null ) {
	                sb.append( formatString( consLastName, 25 ) );
	            }
	            else {
	                warning( "No BPW_Customer record can be found for ConsumerID \""
	                         + consumerID + "\". Record for payment with Confirmation Number: \"" +
	                         pmts[i].ConfirmationNumber + "\" cannot be generated!"  );
	                continue;
	            }
	
	            pw.println( sb.toString() );         
	            _pmtRecCount++;
	        }
	
	        pw.flush();
	        pw.close();
        } catch(Exception e) {
        	throw e;
        } finally {
        	if(null != pw) {
        		try {
        			pw.close();
        		} catch(Exception e) {
        			// ignore
        		}
        	}
        	if(null != bw) {
        		try {
        			bw.close();
        		} catch(Exception e) {
        			// ignore
        		}
        	}
        	if(null != fw) {
        		try {
        			fw.close();
        		} catch(Exception e) {
        			// ignore
        		}
        	}
        }
        log( "FulfillmentAPI.addPayments() done." );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Converts BPW payment type constant to a Metavante one.
    ///////////////////////////////////////////////////////////////////////////
    private String getPaymentType( String pmtType )
    {
        if ( DBConsts.PMTTYPE_CURRENT.equalsIgnoreCase( pmtType ) ) {
            return PT_CURRENT;
        }
        else if ( DBConsts.PMTTYPE_RECURRING.equalsIgnoreCase( pmtType ) ) {
            return PT_RECURRING;
        }
        else {
            log( "Unknown payment type \"" + pmtType + "\" detected while " +
                 "generating Payment Information request file. Defaulting " +
                 "payment type to \"" + PT_FUTURE + "\"" );
            return PT_FUTURE;
        }

    }


    ///////////////////////////////////////////////////////////////////////////
    // Looks up the Consumer Settlement Reference Number in Consumer Bank
    // Information File.
    ///////////////////////////////////////////////////////////////////////////
    private String getConsSettlementRefNum( String consumerID, String acctNum, FFSConnectionHolder dbh ) 
    throws Exception
    {
        ConsumerBankInfoFile bi = ConsumerBankInfoFile.lookup( consumerID, acctNum, dbh );
        return bi == null ? null : bi._consumerSettlementReferenceNumber;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Looks up the Consumer's Last Name in Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private String getConsLastName( String customerID, FFSConnectionHolder dbh ) 
    throws Exception
    {
        ConsumerInfoFile ci = ConsumerInfoFile.lookup( customerID, dbh );
        return ci == null ? null : ci._consumerLastName;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Looks up the Consumer's First Name in Consumer Information File.
    // This field is optional.
    ///////////////////////////////////////////////////////////////////////////
    private String getConsFirstName( String customerID, FFSConnectionHolder dbh ) 
    throws Exception
    {
        ConsumerInfoFile ci = ConsumerInfoFile.lookup( customerID, dbh );
        return ci == null || ci._consumerFirstName == null ? "" : ci._consumerFirstName;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Looks up the Consumer's First Name in Consumer Information File.
    // This field is optional.
    ///////////////////////////////////////////////////////////////////////////
    private String getAccessType( String consumerID, FFSConnectionHolder dbh ) 
    throws Exception
    {
        //ConsumerProductAccessInfoFile ai = ConsumerProductAccessInfoFile.lookup( consumerID );
        //return ai == null || ai._accessType == null ? "" : ai._accessType;
        String accessType = ConsumerProductAccessInfoFile.lookup( consumerID, dbh );
        return accessType == null ? "" : accessType;
    }


    ////////////////////////////////////////////////////////////////////////////
    // Outputs header line to given file.
    ////////////////////////////////////////////////////////////////////////////
    private void buildHeader( File file,
                              String fileType,
                              String siteID,
                              String fileSeqNum )
    throws Exception
    {
    	FileWriter fw = new FileWriter( file );
        PrintWriter pw = new PrintWriter( new BufferedWriter(fw) );

        pw.print( HEADER_LINE_INDICATOR );    // output header line indicator
        pw.print( fileType );                 // output File Type field
        pw.print( fileSeqNum );               // output File Sequence Number field
        pw.print( _dt.format( new Date() ) ); // output date and time fields
        pw.print( siteID );                   // output Site ID field
        pw.println( _processingMode );   // output Production Indicator field

        pw.flush();
        pw.close();
    }


    ////////////////////////////////////////////////////////////////////////////
    // Outputs trailer line to given file.
    ////////////////////////////////////////////////////////////////////////////
    private void buildTrailer( String fileName,
                               long recordCount,
                               String totalAmount )
    throws Exception
    {
    	FileWriter fw = new FileWriter( fileName, true );
    	fw.setFileHandlerProvider(fileHandlerProvider);
        PrintWriter pw = new PrintWriter( new BufferedWriter(fw) );

        pw.print( TRAILER_LINE_INDICATOR );                            // trailer line indicator
        pw.print( formatNumber( String.valueOf( recordCount ), 10 ) ); // Record Count
        pw.print( formatAmount( totalAmount, 10 ) );                   // Total Amount

        pw.flush();
        pw.close();
    }

    ////////////////////////////////////////////////////////////////////////////
    // Processes a Metavante response file.
    ////////////////////////////////////////////////////////////////////////////
    public static File processResponseFile(File file, FFSConnectionHolder dbh){
        String fileName = file.getName();
        String fileType = null;
        BufferedReader in = null;
        try {
            in = new BufferedReader( new FileReader( file ) );
            if ( fileName.startsWith( FT_CONSUMER_AD ) ) {
                // Log to File Monitor Log
                // We pass in null value for db connection,
                // then a new db connection will be used
                // for this log and be committed right away
                fileType = DBConsts.BPW_METAVANTE_FILETYPE_CONSINFO;
                FMLogAgent.writeToFMLog(null,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_IN_PROCESS);

                ConsumerInfoFile[] info = ConsumerInfoFile.parseFile( in );
                ConsumerInfoFile.storeToDB( info, dbh );

                // Log to File Monitor Log
                FMLogAgent.writeToFMLog(dbh,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_COMPLETE);

                log( "Successfully processed Consumer Information response file \"" + fileName + "\"" );
            }
            else if ( fileName.startsWith( FT_CONS_PAYEE_AD ) ) {
                // Log to File Monitor Log
                // We pass in null value for db connection,
                // then a new db connection will be used
                // for this log and be committed right away
                fileType = DBConsts.BPW_METAVANTE_FILETYPE_CONSPAYEEINFO;
                FMLogAgent.writeToFMLog(null,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_IN_PROCESS);

                ConsumerPayeeInfoFile[] info = ConsumerPayeeInfoFile.parseFile( in );
                ConsumerPayeeInfoFile.storeToDB( info, dbh );

                // Log to File Monitor Log
                FMLogAgent.writeToFMLog(dbh,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_COMPLETE);

                log( "Successfully processed Consumer Payee Information response file \"" + fileName + "\"" );
            }
            else if ( fileName.startsWith( FT_CONS_BANK_AD ) ) {
                // Log to File Monitor Log
                // We pass in null value for db connection,
                // then a new db connection will be used
                // for this log and be committed right away
                fileType = DBConsts.BPW_METAVANTE_FILETYPE_CONSBANKINFO;
                FMLogAgent.writeToFMLog(null,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_IN_PROCESS);

                ConsumerBankInfoFile[] info = ConsumerBankInfoFile.parseFile( in );
                ConsumerBankInfoFile.storeToDB( info, dbh );

                // Log to File Monitor Log
                FMLogAgent.writeToFMLog(dbh,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_COMPLETE);

                log( "Successfully processed Consumer Bank Information response file \"" + fileName + "\"" );
            }
            else if ( fileName.startsWith( FT_CONSPRDACC_AD ) ) {
                // Log to File Monitor Log
                // We pass in null value for db connection,
                // then a new db connection will be used
                // for this log and be committed right away
                fileType = DBConsts.BPW_METAVANTE_FILETYPE_CONSPRODACCESSINFO;
                FMLogAgent.writeToFMLog(null,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_IN_PROCESS);

                ConsumerProductAccessInfoFile[] info = ConsumerProductAccessInfoFile.parseFile( in );
                ConsumerProductAccessInfoFile.storeToDB( info, dbh );

                // Log to File Monitor Log
                FMLogAgent.writeToFMLog(dbh,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_COMPLETE);

                log( "Successfully processed Consumer Product Access Information response file \"" + fileName + "\"" );
            }
            else if ( fileName.startsWith( FT_CONS_XREF_AD ) ) {
                // Log to File Monitor Log
                // We pass in null value for db connection,
                // then a new db connection will be used
                // for this log and be committed right away
                fileType = DBConsts.BPW_METAVANTE_FILETYPE_CONSCROSSREFINFO;
                FMLogAgent.writeToFMLog(null,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_IN_PROCESS);

                ConsumerCrossReferenceInfoFile[] info = ConsumerCrossReferenceInfoFile.parseFile( in );
                ConsumerCrossReferenceInfoFile.storeToDB( info, dbh );

                // Log to File Monitor Log
                FMLogAgent.writeToFMLog(dbh,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_COMPLETE);

                log( "Successfully processed Consumer Cross Reference Information response file \"" + fileName + "\"" );
            }
            else if ( fileName.startsWith( FT_PAYEE_AD ) ) {
                // Log to File Monitor Log
                // We pass in null value for db connection,
                // then a new db connection will be used
                // for this log and be committed right away
                fileType = DBConsts.BPW_METAVANTE_FILETYPE_PAYEEINFO;
                FMLogAgent.writeToFMLog(null,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_IN_PROCESS);

                PayeeInfoFile[] info = PayeeInfoFile.parseFile( in );
                PayeeInfoFile.storeToDB( info, dbh );

                // Log to File Monitor Log
                FMLogAgent.writeToFMLog(dbh,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_COMPLETE);

                log( "Successfully processed Payee Information response file \"" + fileName + "\"" );
            }
            else if ( fileName.startsWith( FT_PAYEE_EDIT_AD ) ) {
                // Log to File Monitor Log
                // We pass in null value for db connection,
                // then a new db connection will be used
                // for this log and be committed right away
                fileType = DBConsts.BPW_METAVANTE_FILETYPE_PAYEEEDITMASKINFO;
                FMLogAgent.writeToFMLog(null,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_IN_PROCESS);

                PayeeEditMaskInfoFile[] info = PayeeEditMaskInfoFile.parseFile( in );
                PayeeEditMaskInfoFile.storeToDB( info, dbh );

                // Log to File Monitor Log
                FMLogAgent.writeToFMLog(dbh,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_COMPLETE);

                log( "Successfully processed Payee Edit Mask Information response file \"" + fileName + "\"" );
            }
            else if ( fileName.startsWith( FT_HISTORY_AD ) ) {
                // Log to File Monitor Log
                // We pass in null value for db connection,
                // then a new db connection will be used
                // for this log and be committed right away
                fileType = DBConsts.BPW_METAVANTE_FILETYPE_PMTHISTINFO;
                FMLogAgent.writeToFMLog(null,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_IN_PROCESS);

                PaymentHistoryInfoFile[] info = PaymentHistoryInfoFile.parseFile( in );
                PaymentHistoryInfoFile.storeToDB( info, dbh );

                // Log to File Monitor Log
                FMLogAgent.writeToFMLog(dbh,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_COMPLETE);

                log( "Successfully processed Payment History Information response file \"" + fileName + "\"" );
            }
            else if ( fileName.startsWith( "IMPORTDATA") ) {
                // Log to File Monitor Log
                // We pass in null value for db connection,
                // then a new db connection will be used
                // for this log and be committed right away
                fileType = DBConsts.BPW_METAVANTE_FILETYPE_PMTHISTINFO;
                FMLogAgent.writeToFMLog(null,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_IN_PROCESS);

                try {
                    log( "Updating trans data using migrated data" );
                    Class  cls          = Class.forName("com.ffusion.ffs.bpw.interfaces.ImportTransData");
                    Method method       = cls.getMethod("getPaymentHistoryInfo", (Class[])null);
                    PmtTrnRslt[] info   = (PmtTrnRslt[])method.invoke(null, new Object[]{});
                    //PmtTrnRslt[] info = ImportTransData.getPaymentHistoryInfo();
                    PaymentHistoryInfoFile.importToDB( info, dbh );

                    // Log to File Monitor Log
                    FMLogAgent.writeToFMLog(dbh,
                                            fileType,
                                            file.getPath(),
                                            DBConsts.METAVANTE,
                                            DBConsts.BPTW,
                                            FMLogRecord.STATUS_COMPLETE);

                    log( "Successfully processed Payment History using migrated data" );
                }
                catch (Exception exp) {
                    exp.printStackTrace();

                    // Log to File Monitor Log
                    // We pass in null value for db connection,
                    // then a new db connection will be used
                    // for this log and be committed right away
                    FMLogAgent.writeToFMLog(null,
                                            fileType,
                                            file.getPath(),
                                            DBConsts.METAVANTE,
                                            DBConsts.BPTW,
                                            FMLogRecord.STATUS_FAILED);
                }
            }
            else {
                log( "Ignoring response file \"" + fileName + "\" because it has an unknown file type." );
            }

            // Everything succeeded so we can commit any DB updates.
            dbh.conn.commit();
        }
        catch ( Exception e ) {
            try {
                dbh.conn.rollback();
            }
            catch ( Exception e2 ) {
            }
            log( e, "Error occurred while processing the response file \"" + fileName +
                 "\" All DB updates have been rolled back." );

            // Log to File Monitor Log
            // We pass in null value for db connection,
            // then a new db connection will be used
            // for this log and be committed right away
            FMLogAgent.writeToFMLog(null,
                                    fileType,
                                    file.getPath(),
                                    DBConsts.METAVANTE,
                                    DBConsts.BPTW,
                                    FMLogRecord.STATUS_FAILED);

            return null; // this will prevent the file from being deleted
        }
        finally {
            try {
                if ( in != null ) in.close();
            }
            catch ( Exception e2 ) {
            }
        }
        return file;
    }    


    ////////////////////////////////////////////////////////////////////////////
    // Processes a Metavante response file.
    ////////////////////////////////////////////////////////////////////////////
    public static File processResponseFile( File file )
    {
        String fileName = file.getName();
        String fileType = null;
        BufferedReader in = null;
        FFSConnectionHolder dbh = new FFSConnectionHolder();
        try {
            dbh.conn = DBUtil.getValidConnection();
            in = new BufferedReader( new FileReader( file ) );
            if ( fileName.startsWith( FT_CONSUMER_AD ) ) {
                // Log to File Monitor Log
                // We pass in null value for db connection,
                // then a new db connection will be used
                // for this log and be committed right away
                fileType = DBConsts.BPW_METAVANTE_FILETYPE_CONSINFO;
                FMLogAgent.writeToFMLog(null,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_IN_PROCESS);

                ConsumerInfoFile[] info = ConsumerInfoFile.parseFile( in );
                ConsumerInfoFile.storeToDB( info, dbh );

                // Log to File Monitor Log
                FMLogAgent.writeToFMLog(dbh,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_COMPLETE);

                log( "Successfully processed Consumer Information response file \"" + fileName + "\"" );
            }
            else if ( fileName.startsWith( FT_CONS_PAYEE_AD ) ) {
                // Log to File Monitor Log
                // We pass in null value for db connection,
                // then a new db connection will be used
                // for this log and be committed right away
                fileType = DBConsts.BPW_METAVANTE_FILETYPE_CONSPAYEEINFO;
                FMLogAgent.writeToFMLog(null,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_IN_PROCESS);

                ConsumerPayeeInfoFile[] info = ConsumerPayeeInfoFile.parseFile( in );
                ConsumerPayeeInfoFile.storeToDB( info, dbh );

                // Log to File Monitor Log
                FMLogAgent.writeToFMLog(dbh,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_COMPLETE);

                log( "Successfully processed Consumer Payee Information response file \"" + fileName + "\"" );
            }
            else if ( fileName.startsWith( FT_CONS_BANK_AD ) ) {
                // Log to File Monitor Log
                // We pass in null value for db connection,
                // then a new db connection will be used
                // for this log and be committed right away
                fileType = DBConsts.BPW_METAVANTE_FILETYPE_CONSBANKINFO;
                FMLogAgent.writeToFMLog(null,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_IN_PROCESS);

                ConsumerBankInfoFile[] info = ConsumerBankInfoFile.parseFile( in );
                ConsumerBankInfoFile.storeToDB( info, dbh );

                // Log to File Monitor Log
                FMLogAgent.writeToFMLog(dbh,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_COMPLETE);

                log( "Successfully processed Consumer Bank Information response file \"" + fileName + "\"" );
            }
            else if ( fileName.startsWith( FT_CONSPRDACC_AD ) ) {
                // Log to File Monitor Log
                // We pass in null value for db connection,
                // then a new db connection will be used
                // for this log and be committed right away
                fileType = DBConsts.BPW_METAVANTE_FILETYPE_CONSPRODACCESSINFO;
                FMLogAgent.writeToFMLog(null,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_IN_PROCESS);

                ConsumerProductAccessInfoFile[] info = ConsumerProductAccessInfoFile.parseFile( in );
                ConsumerProductAccessInfoFile.storeToDB( info, dbh );

                // Log to File Monitor Log
                FMLogAgent.writeToFMLog(dbh,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_COMPLETE);

                log( "Successfully processed Consumer Product Access Information response file \"" + fileName + "\"" );
            }
            else if ( fileName.startsWith( FT_CONS_XREF_AD ) ) {
                // Log to File Monitor Log
                // We pass in null value for db connection,
                // then a new db connection will be used
                // for this log and be committed right away
                fileType = DBConsts.BPW_METAVANTE_FILETYPE_CONSPRODACCESSINFO;
                FMLogAgent.writeToFMLog(null,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_IN_PROCESS);

                ConsumerCrossReferenceInfoFile[] info = ConsumerCrossReferenceInfoFile.parseFile( in );
                ConsumerCrossReferenceInfoFile.storeToDB( info, dbh );

                // Log to File Monitor Log
                FMLogAgent.writeToFMLog(dbh,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_COMPLETE);

                log( "Successfully processed Consumer Cross Reference Information response file \"" + fileName + "\"" );
            }
            else if ( fileName.startsWith( FT_PAYEE_AD ) ) {
                // Log to File Monitor Log
                // We pass in null value for db connection,
                // then a new db connection will be used
                // for this log and be committed right away
                fileType = DBConsts.BPW_METAVANTE_FILETYPE_PAYEEINFO;
                FMLogAgent.writeToFMLog(null,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_IN_PROCESS);

                PayeeInfoFile[] info = PayeeInfoFile.parseFile( in );
                PayeeInfoFile.storeToDB( info, dbh );

                // Log to File Monitor Log
                FMLogAgent.writeToFMLog(dbh,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_COMPLETE);

                log( "Successfully processed Payee Information response file \"" + fileName + "\"" );
            }
            else if ( fileName.startsWith( FT_PAYEE_EDIT_AD ) ) {
                // Log to File Monitor Log
                // We pass in null value for db connection,
                // then a new db connection will be used
                // for this log and be committed right away
                fileType = DBConsts.BPW_METAVANTE_FILETYPE_PAYEEEDITMASKINFO;
                FMLogAgent.writeToFMLog(null,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_IN_PROCESS);

                PayeeEditMaskInfoFile[] info = PayeeEditMaskInfoFile.parseFile( in );
                PayeeEditMaskInfoFile.storeToDB( info, dbh );

                // Log to File Monitor Log
                FMLogAgent.writeToFMLog(dbh,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_COMPLETE);

                log( "Successfully processed Payee Edit Mask Information response file \"" + fileName + "\"" );
            }
            else if ( fileName.startsWith( FT_HISTORY_AD ) ) {
                // Log to File Monitor Log
                // We pass in null value for db connection,
                // then a new db connection will be used
                // for this log and be committed right away
                fileType = DBConsts.BPW_METAVANTE_FILETYPE_PMTHISTINFO;
                FMLogAgent.writeToFMLog(null,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_IN_PROCESS);

                PaymentHistoryInfoFile[] info = PaymentHistoryInfoFile.parseFile( in );
                PaymentHistoryInfoFile.storeToDB( info, dbh );

                // Log to File Monitor Log
                FMLogAgent.writeToFMLog(dbh,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_COMPLETE);

                log( "Successfully processed Payment History Information response file \"" + fileName + "\"" );
            }
            else if ( fileName.startsWith( "IMPORTDATA") ) {
                fileType = DBConsts.BPW_METAVANTE_FILETYPE_PMTHISTINFO;
                // Log to File Monitor Log
                // We pass in null value for db connection,
                // then a new db connection will be used
                // for this log and be committed right away
                FMLogAgent.writeToFMLog(null,
                                        fileType,
                                        file.getPath(),
                                        DBConsts.METAVANTE,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_IN_PROCESS);

                try {
                    log( "Updating trans data using migrated data" );
                    Class  cls          = Class.forName("com.ffusion.ffs.bpw.interfaces.ImportTransData");
                    Method method       = cls.getMethod("getPaymentHistoryInfo", (Class[])null);
                    PmtTrnRslt[] info   = (PmtTrnRslt[])method.invoke(null, new Object[]{});
                    //PmtTrnRslt[] info = ImportTransData.getPaymentHistoryInfo();
                    PaymentHistoryInfoFile.importToDB( info, dbh );

                    // Log to File Monitor Log
                    FMLogAgent.writeToFMLog(dbh,
                                            fileType,
                                            file.getPath(),
                                            DBConsts.METAVANTE,
                                            DBConsts.BPTW,
                                            FMLogRecord.STATUS_COMPLETE);

                    log( "Successfully processed Payment History using migrated data" );
                }
                catch (Exception exp) {
                    exp.printStackTrace();

                    // Log to File Monitor Log
                    // We pass in null value for db connection,
                    // then a new db connection will be used
                    // for this log and be committed right away
                    FMLogAgent.writeToFMLog(null,
                                            fileType,
                                            file.getPath(),
                                            DBConsts.METAVANTE,
                                            DBConsts.BPTW,
                                            FMLogRecord.STATUS_FAILED);
                }
            }
            else {
                log( "Ignoring response file \"" + fileName + "\" because it has an unknown file type." );
            }

            // Everything succeeded so we can commit any DB updates.
            dbh.conn.commit();
        }
        catch ( Exception e ) {
            try {
                dbh.conn.rollback();
            }
            catch ( Exception e2 ) {
            }
            log( e, "Error occurred while processing the response file \"" + fileName +
                 "\" All DB updates have been rolled back." );

            // Log to File Monitor Log
            // We pass in null value for db connection,
            // then a new db connection will be used
            // for this log and be committed right away
            FMLogAgent.writeToFMLog(null,
                                    fileType,
                                    file.getPath(),
                                    DBConsts.METAVANTE,
                                    DBConsts.BPTW,
                                    FMLogRecord.STATUS_FAILED);

            return null; // this will prevent the file from being deleted
        }
        finally {
            try {
                if ( in != null ) in.close();
            }
            catch ( Exception e2 ) {
            }
            DBUtil.freeConnection( dbh.conn );
        }
        return file;
    }    

    /**
      *
      */
    public Properties getProperties( String fileName )  throws FFSException
    {
        Properties prop = new Properties();
        FileInputStream in = null;
        try {
            in = new FileInputStream(fileName);
            in.setFileHandlerProvider(fileHandlerProvider);
            prop.load(in);
            in.close();
        }
        catch (FileNotFoundException fe) {
            System.out.println(fe.getMessage());
            return prop;
        }
        catch (IOException ioe) {
            System.out.println(ioe.getMessage());
            throw new FFSException( ioe.getMessage());
        } finally {
        	if(null != in) {
        		try {
        			in.close();
        		} catch(Exception e) {
        			//ignore
        		}
        	}
        }
        return prop; 
    }

    protected static int getRouteID() throws Exception
    {
    	if(_routeID == -1){
    	     // get fulfillment system info
            FulfillmentInfo fulfill = BPWRegistryUtil.getFulfillmentInfo( MetavanteHandler.class );
            if ( fulfill == null ) {
                throw new Exception( "FulfillmentInfo not found for "
                                     + MetavanteHandler.class.getName() );
            }
            _routeID = fulfill.RouteID;
    	}
        return _routeID;
    }

    protected static double getPaymentCost() throws Exception
    {
    	if(_paymentCost == -1){
   	     // get fulfillment system info
           FulfillmentInfo fulfill = BPWRegistryUtil.getFulfillmentInfo( MetavanteHandler.class );
           if ( fulfill == null ) {
               throw new Exception( "FulfillmentInfo not found for "
                                    + MetavanteHandler.class.getName() );
           }
           _paymentCost = fulfill.PaymentCost;
    	} 
        return _paymentCost;
    }

    // omer
    private static String getRqFileName(String fileType) throws Exception{

        if (fileType == null || fileType.length() == 0) {
            return null;
        }

        String now  = FFSUtil.getNow("yyyyMMddHHmmss");
        String date = now.substring(0,8);
        String time = now.substring(8);
        //omer
        if (fileType.equals(FT_PAYMENTS_IN)) {
            return(_siteID + '-' + fileType + '.' + date + '.' + time + '.' + "PAY");
        }
        else if (fileType.equals(FT_CONSUMER_IN)) {
            return(_acSiteID + '-' + fileType + '.' + date + '.' + time + '.' + "ACN");
        }
        else if (fileType.equals(FT_CONS_PAYEE_IN)) {
            return(_siteID + '-' + fileType + '.' + date + '.' + time + '.' + "SVF");
        }
        else if (fileType.equals(FT_BANK_IN)) {
            return(_acSiteID + '-' + fileType + '.' + date + '.' + time + '.' + "ACB");
        }
        else if (fileType.equals(FT_CONSPRDACC_IN)) {
            return(_acSiteID + '-' + fileType + '.' + date + '.' + time + '.' + "ACP");
        }
        else if (fileType.equals(FT_PAYEE_IN)) {
            return(_siteID + '-' + fileType + '.' + date + '.' + time + '.' + "VEN");
        }
        else {
            throw new Exception("Invalid file type: " + fileType);
        }
    }

    private static void initFileNames(){

        if (_siteID != null && (_siteID.indexOf("01") != -1 || _siteID.indexOf("AC") != -1)) {
            _bpwSiteID = _siteID.substring(0,(_siteID.length()-2)) + "01";
            _acSiteID  = _siteID.substring(0,(_siteID.length()-2)) + "AC";
        }

        PropertyConfig propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);
        Hashtable  otherProperties = propertyConfig .otherProperties ;

        FT_PAYMENTS_IN   = (String)otherProperties.get(METAVANTE_PAYMENT_IN);
        FT_CONSUMER_IN   = (String)otherProperties.get(METAVANTE_CONSUMER_IN);
        FT_CONS_PAYEE_IN = (String)otherProperties.get(METAVANTE_CONS_PAYEE_IN);
        FT_BANK_IN       = (String)otherProperties.get(METAVANTE_BANK_IN);
        FT_CONSPRDACC_IN = (String)otherProperties.get(METAVANTE_CONS_PRODUCT_IN);
        FT_PAYEE_IN      = (String)otherProperties.get(METAVANTE_PAYEE_IN);

        // files from metavante
        FT_CONSUMER_AD   = _bpwSiteID + '-' + (String)otherProperties.get(METAVANTE_CONSUMER_OUT);
        FT_CONS_PAYEE_AD = _bpwSiteID + '-' + (String)otherProperties.get(METAVANTE_CONS_PAYEE_OUT);
        FT_CONS_BANK_AD  = _bpwSiteID + '-' + (String)otherProperties.get(METAVANTE_CONS_BANK_OUT);
        FT_CONSPRDACC_AD = _bpwSiteID + '-' + (String)otherProperties.get(METAVANTE_CONS_PRODUCT_OUT);
        FT_CONS_XREF_AD  = _bpwSiteID + '-' + (String)otherProperties.get(METAVANTE_CONS_XREF_OUT);
        FT_PAYEE_AD      = _bpwSiteID + '-' + (String)otherProperties.get(METAVANTE_PAYEE_OUT);
        FT_PAYEE_EDIT_AD = _bpwSiteID + '-' + (String)otherProperties.get(METAVANTE_PAYEE_EDIT_OUT);
        FT_HISTORY_AD    = _bpwSiteID + '-' + (String)otherProperties.get(METAVANTE_HISTORY_OUT);
    }

}
