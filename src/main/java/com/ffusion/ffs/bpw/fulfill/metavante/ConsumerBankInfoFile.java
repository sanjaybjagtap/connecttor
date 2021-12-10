// Copyright (c) 2001 Financial Fusion, Inc.  All Rights Reserved.

package com.ffusion.ffs.bpw.fulfill.metavante;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;

import com.ffusion.ffs.bpw.db.CustomerBank;
import com.ffusion.ffs.bpw.interfaces.CustomerBankInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSUtil;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

///////////////////////////////////////////////////////////////////////////////
// An instace of this class contains data retrieved from a Metavante Consumer
// Bank Information File. Format of this response file is described in
// Appendix B3 of the Metavante Bill Payment Technical Implementation Guide.
///////////////////////////////////////////////////////////////////////////////
public class ConsumerBankInfoFile implements DBConsts {
    // Variables for the values of the fields retrieved from a file record.
    public String _consumerID;
    public String _routingAndTransitNumber;
    public String _consumerAccountNumber;
    public String _accountType;
    public String _consumerSettlementReferenceNumber;
    public String _primarySettlementAccountFlag;
    public String _submitDate;

    // Valid values for the alphanumeric pre-defined fields.
    private static final String ACCOUNT_TYPE[] = {"DDA", "SV"};

    // Constants for line type indicators.
    private static final char HEADER_LINE_INDICATOR = '5';
    private static final char DETAIL_LINE_INDICATOR = '6';
    private static final char TRAILER_LINE_INDICATOR = '8';

    // Constant for the file type represented by this class.
    private static final String FILE_TYPE = "CONS-BANK-AD";

    // An HashMap for caching the contents of the database
    public static HashMap _cache;


    ////////////////////////////////////////////////////////////////////////////
    // Populates the static cache with the contents in the database.
    ////////////////////////////////////////////////////////////////////////////
    public static void init() throws Exception
    {
        _cache = new HashMap();
    }


    ////////////////////////////////////////////////////////////////////////////
    // Look up a ConsumerBankInfoFile from the cache with the given 
    // consumerID
    ////////////////////////////////////////////////////////////////////////////
    public static ConsumerBankInfoFile lookup( String consumerID, String acctNum, FFSConnectionHolder dbh )
    throws Exception
    {
        ConsumerBankInfoFile thisBankInfo;
        synchronized( _cache ) {
            thisBankInfo = (ConsumerBankInfoFile)_cache.get( consumerID+"+"+acctNum );
        }
        if (thisBankInfo == null) {
            CustomerBankInfo[] cbinfos = CustomerBank.getByConsumerID(consumerID, dbh);
            for (int i=0; i<cbinfos.length; i++) {
                thisBankInfo = new ConsumerBankInfoFile();
                thisBankInfo._consumerID = cbinfos[i].consumerID;
                thisBankInfo._routingAndTransitNumber = cbinfos[i].routingAndTransitNumber;
                thisBankInfo._consumerAccountNumber = cbinfos[i].acctNumber;
                thisBankInfo._accountType = cbinfos[i].acctType;
                thisBankInfo._consumerSettlementReferenceNumber = cbinfos[i].settlementRefNumber;
                thisBankInfo._primarySettlementAccountFlag = cbinfos[i].primaryAcctFlag;
                //thisBankInfo.status = cbinfos[0].status;
                thisBankInfo._submitDate = cbinfos[i].submitDate;
                synchronized( _cache ) {
                    _cache.put( consumerID+"+"+cbinfos[i].acctNumber, thisBankInfo );
                }
                if (acctNum.equals(cbinfos[i].acctNumber)) {
                    return thisBankInfo;
                }
            }
            return null;
        }
        else
            return thisBankInfo;
    }

    public static void deleteFromCache( String consumerID, FFSConnectionHolder dbh )
    throws Exception
    {
        CustomerBankInfo[] cbinfos = CustomerBank.getByConsumerID(consumerID, dbh);
        synchronized( _cache ) {
            for (int i=0; i<cbinfos.length; i++) {
                _cache.remove( consumerID+"+"+cbinfos[i].acctNumber );
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses given Consumer Bank Information File and returns an array of
    // ConsumerBankInfoFile objects holding its contents.
    ///////////////////////////////////////////////////////////////////////////
    public static ConsumerBankInfoFile[] parseFile( BufferedReader in )
    throws Exception
    {
    	String method = "ClassName.parseFile";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        parseHeader( in.readLine() ); // first line should be the header

        ArrayList l = new ArrayList();
        String line = null;
        while ( (line = in.readLine() ) != null ) {
            if ( line.charAt( 0 ) == DETAIL_LINE_INDICATOR ) {
                l.add( new ConsumerBankInfoFile( line ) ); // another detail line
            }
            else {
                parseTrailer( line, l.size() ); // this can only be the trailer line
                if ( in.readLine() != null ) {
                    warning( "Extra data is available following the trailer line!" );
                }
                break; // trailer reached, no more detail lines
            }
        }

        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return(ConsumerBankInfoFile[])l.toArray( new ConsumerBankInfoFile[l.size()] );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses header line of a Consumer Bank Information File.
    ///////////////////////////////////////////////////////////////////////////
    private static void parseHeader( String line ) throws Exception
    {
        // verify that header line has expected length
        if ( line.length() != 48 ) {
            error( "Header line is not 48 characters long!" );
        }

        // verify that header line starts with expected character
        char c = line.charAt( 0 );
        if ( c != HEADER_LINE_INDICATOR ) {
            error( "Unexpected value of the \"Record Type\" header field: '" +
                   c + "', expected: '" + HEADER_LINE_INDICATOR + "'" );
        }

        // verify that File Type field has expected value
        String s = line.substring( 1, 16 );
        if ( !s.startsWith( FILE_TYPE ) ) {
            error( "Unexpected value of the \"File Type\" header field: \"" +
                   s + "\", expected: \"" + FILE_TYPE + "\"" );
        }

        // TODO: it might be wise to check other header fields when we know
        // what values to expect.
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses trailer line of a Consumer Bank Information File.
    ///////////////////////////////////////////////////////////////////////////
    private static void parseTrailer( String line, int expectedRecCount )
    throws Exception
    {
        // verify that trailer line has expected length
        if ( line.length() != 21 ) {
            error( "Trailer line is not 21 characters long!" );
        }

        // verify that trailer line starts with expected character
        char c = line.charAt( 0 );
        if ( c != TRAILER_LINE_INDICATOR ) {
            error( "Unexpected value of the \"Record Type\" trailer field: '" +
                   c + "', expected: '" + TRAILER_LINE_INDICATOR + "'" );
        }

        // verify that Record Count field has expected value
        String s = line.substring( 1, 11 );
        try {
            int i = Integer.parseInt( s );
            if ( i != expectedRecCount ) {
                warning( "Unexpected value of the \"Record Count\" trailer field: " +
                         i + ", expecting: " + expectedRecCount );
            }
        }
        catch ( Exception e ) {
            warning( "Invalid value of the \"Record Count\" trailer field: \"" +
                     s + "\", expecting: " + expectedRecCount );
        }

        // TODO: it might be wise to check "Total Amount" field value when we
        // know what value to expect.
    }


    ///////////////////////////////////////////////////////////////////////////
    // Throws an Exception with an error message.
    ///////////////////////////////////////////////////////////////////////////
    private static void error( String msg ) throws Exception
    {
        throw new Exception( "ERROR! Consumer Bank Information File parser: " + msg );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Outputs a warning message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    private static void warning( String msg )
    {
        FFSDebug.log( "WARNING! Consumer Bank Information File parser: " + msg );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Outputs an exception and a warning message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    private static void warning( Throwable t, String msg )
    {
        FFSDebug.log( t, "WARNING! Consumer Bank Information File parser: " + msg );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Default constructor resulting in an empty ConsumerBankInfoFile object.
    ///////////////////////////////////////////////////////////////////////////
    public ConsumerBankInfoFile() {}


    ///////////////////////////////////////////////////////////////////////////
    // Constructor taking a Detail Line from a Consumer Bank Information File
    // and using it to populate its instance data members.
    ///////////////////////////////////////////////////////////////////////////
    public ConsumerBankInfoFile( String line ) throws Exception
    {
    	String method = "ConsumerBankInfoFile.ConsumerBankInfoFile";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        if ( line.length() != 73 ) {
            error( "Detail line is not 73 characters long!" );
        }

        // The first character of the line is supposed to be Record Type.
        // We skip it here and start parsing from the second character.
        parseConsumerID( line.substring( 1, 23 ) );
        parseRoutingAndTransitNumber( line.substring( 23, 32 ) );
        parseConsumerAccountNumber( line.substring( 32, 52 ) );
        parseAccountType( line.substring( 52, 62 ) );
        parseConsumerSettlementReferenceNumber( line.substring( 62, 72 ) );
        parsePrimarySettlementAccountFlag( line.substring( 72, 73 ) ); 
        
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Consumer ID field of the Consumer Bank Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseConsumerID( String data ) throws Exception
    {
        data = data.trim();
        if ( data.length() != 0 ) {
            _consumerID = data;
        }
        else {
            warning( "Value for the mandatory \"Consumer ID\" field is missing." );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Routing and Transit Number field of the Consumer Bank 
    // Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseRoutingAndTransitNumber( String data ) throws Exception
    {
        data = MetavanteHandler.removeNumberPadding( data );
        if ( data.length() > 0 ) {
            _routingAndTransitNumber = data;
        }
        else {
            warning( "Value for the mandatory \"Routing and Transit Number\" field is missing." );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Consumer Account Number field of the Consumer Bank Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseConsumerAccountNumber( String data ) throws Exception
    {
        data = data.trim();
        if ( data.length() != 0 ) {
            _consumerAccountNumber = data;
        }
        else {
            warning( "Value for the mandatory \"Consumer Account Number\" field is missing." );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Account Type field of the Consumer Bank Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseAccountType( String data ) throws Exception
    {
        boolean validValue = false;
        data = data.trim();
        for (int i = 0; i < ACCOUNT_TYPE.length; ++i) {
            if ( data.equalsIgnoreCase(ACCOUNT_TYPE[i]) ) {
                validValue = true;
                break;
            }
        }

        if ( !validValue ) {
            warning( "Value for the mandatory \"Account Type\" field " +
                     "doesn't match any of the pre-defined values." );
        }
        _accountType = data; 
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Consumer Settlement Reference Number field of the Consumer Bank 
    // Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseConsumerSettlementReferenceNumber( String data ) throws Exception
    {
        data = MetavanteHandler.removeNumberPadding( data );
        if ( data.length() > 0 ) {
            _consumerSettlementReferenceNumber = data;
        }
        else {
            warning( "Value for the mandatory \"Consumer Settlement Reference Number\" field is missing." );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Primary Settlement Account Flag field of the Consumer Bank 
    // Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parsePrimarySettlementAccountFlag( String data ) throws Exception
    {
        data = data.trim();
        if ( data.length() != 0 ) {
            _primarySettlementAccountFlag = data;
        }
        else {
            warning( "Value for the mandatory \"Primary Settlement Account Flag\" field is missing." );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Overrides java.lang.Object.toString(). 
    ///////////////////////////////////////////////////////////////////////////
    public String toString()
    {
        String LINE_SEP = System.getProperty( "line.separator" );
        return "_consumerID = " + (_consumerID == null ? "null" : "\"" + _consumerID + "\"" ) + LINE_SEP +
        "_routingAndTransitNumber = " + (_routingAndTransitNumber == null ? "null" : "\"" + _routingAndTransitNumber + "\"" ) + LINE_SEP +
        "_consumerAccountNumber = " + (_consumerAccountNumber == null ? "null" : "\"" + _consumerAccountNumber + "\"" ) + LINE_SEP +
        "_accountType = " + (_accountType == null ? "null" : "\"" + _accountType + "\"" ) + LINE_SEP +
        "_consumerSettlementReferenceNumber = " + (_consumerSettlementReferenceNumber == null ? "null" : "\"" + _consumerSettlementReferenceNumber + "\"" ) + LINE_SEP +
        "_primarySettlementAccountFlag = " + (_primarySettlementAccountFlag == null ? "null" : "\"" + _primarySettlementAccountFlag + "\"" ) + LINE_SEP;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Stores the fields, of a ConsumerBankInfoFile instance, into the 
    // BPW_CustomerBankInfo table.
    ///////////////////////////////////////////////////////////////////////////
    private void storeToDB( FFSConnectionHolder dbh ) throws Exception
    {
        CustomerBankInfo custBankInfo = new CustomerBankInfo(
                                                            _consumerID,
                                                            _routingAndTransitNumber,
                                                            _consumerAccountNumber,
                                                            _accountType,
                                                            _consumerSettlementReferenceNumber,
                                                            _primarySettlementAccountFlag,
                                                            DBConsts.ACTIVE,
                                                            FFSUtil.getDateString()
                                                            );
        CustomerBank.update(custBankInfo, dbh);

        // add to static cache
        synchronized (_cache) {            
            _cache.put( _consumerID+"+"+_consumerAccountNumber, this );
        }
    }


    ///////////////////////////////////////////////////////////////////////
    // Stores the info in the array of ConsumerBankInfoFile objects into
    // the database.
    ///////////////////////////////////////////////////////////////////////
    public static void storeToDB( ConsumerBankInfoFile[] infoFiles,
                                  FFSConnectionHolder dbh )
    throws Exception
    {
        for ( int i = 0; i < infoFiles.length; i++ ) {
            infoFiles[i].storeToDB( dbh );
        }
    }
}
