// Copyright (c) 2001 Financial Fusion, Inc.  All Rights Reserved.

package com.ffusion.ffs.bpw.fulfill.metavante;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;

import com.ffusion.ffs.bpw.db.CustomerProductAccess;
import com.ffusion.ffs.bpw.interfaces.CustomerProductAccessInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSUtil;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

///////////////////////////////////////////////////////////////////////////////
// An instace of this class contains data retrieved from a Metavante Consumer
// Product Access Information File. Format of this response file is described
// in Appendix B4 of the Metavante Bill Payment Technical Implementation Guide.
///////////////////////////////////////////////////////////////////////////////
public class ConsumerProductAccessInfoFile implements DBConsts {
    // Variables for the values of the fields retrieved from a file record.
    public String _consumerID;
    public String _productType;
    public String _accessType;
    public String _statusType;
    public String _submitDate;

    // Constants for line type indicators.
    private static final char HEADER_LINE_INDICATOR = '5';
    private static final char DETAIL_LINE_INDICATOR = '6';
    private static final char TRAILER_LINE_INDICATOR = '8';

    // Constant for the file type represented by this class.
    private static final String FILE_TYPE = "CONSPRDACC-AD";


    // Constants for the pre-defined values of the field
    private static final String[] PRODUCT_TYPE_VALUES = { "BILLPAY"};
    private static final String[] ACCESS_TYPE_VALUES = { "PC", "PHONE", "INTERNET"};
    private static final String[] STATUS_TYPE_VALUES = { "ACTIVE", "CLOSED"};

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
    // Look up a ConsumerProductAccessInfoFile from the cache with the given 
    // consumerID
    ////////////////////////////////////////////////////////////////////////////
    //public static ConsumerProductAccessInfoFile lookup( String consumerID )
    public static String lookup( String consumerID, FFSConnectionHolder dbh )
    throws Exception
    {
        String accessType;
        synchronized( _cache ) {
            accessType = (String) _cache.get(consumerID);
        }
        if (accessType == null) {
            accessType = CustomerProductAccess.getAccessTypeByStatus(consumerID, DBConsts.ACTIVE, dbh);
            synchronized( _cache ) {
                _cache.put( consumerID, accessType );
            }
        }
        return accessType;
    }

    public static void deleteFromCache( String consumerID )
    {
        synchronized (_cache) {
            _cache.remove(consumerID);
        }
    }




    ///////////////////////////////////////////////////////////////////////
    // Store the information of the instance to the database with 
    // the given connection.
    ///////////////////////////////////////////////////////////////////////
    private void storeToDB( FFSConnectionHolder dbh ) throws Exception
    {
        CustomerProductAccessInfo custPrdAccInfo = new CustomerProductAccessInfo(
                                                                                _consumerID,
                                                                                _productType,
                                                                                _accessType,
                                                                                _statusType,
                                                                                FFSUtil.getDateString()
                                                                                );
        CustomerProductAccess.update(custPrdAccInfo, dbh );

        // add to static cache
        synchronized( _cache ) {            
            _cache.put( _consumerID, this );
        }
    }

    ///////////////////////////////////////////////////////////////////////
    // Stores the info in the array of ConsumerProductAccessInfoFile
    // objects into the database.
    ///////////////////////////////////////////////////////////////////////
    public static void storeToDB( ConsumerProductAccessInfoFile[] infoFiles,
                                  FFSConnectionHolder dbh )
    throws Exception
    {
        for ( int i = 0; i < infoFiles.length; i++ ) {
            infoFiles[i].storeToDB( dbh );
        }
    }



    ///////////////////////////////////////////////////////////////////////////
    // Default constructor resulting in an empty Consumer Product Access 
    // Information File object.
    ///////////////////////////////////////////////////////////////////////////
    public ConsumerProductAccessInfoFile() {}


    ///////////////////////////////////////////////////////////////////////////
    // Constructor taking a Detail Line from a Consumer Product Access 
    // Information File and using it to populate its instance data members.
    ///////////////////////////////////////////////////////////////////////////
    public ConsumerProductAccessInfoFile( String line ) throws Exception
    {
        if ( line.length() != 53 ) {
            error( "Detail line is not 53 characters long!" );
        }

        // The first character of the line is supposed to be Record Type.
        // We skip it here and start parsing from the second character.
        parseConsumerID( line.substring( 1, 23 ) );
        parseProductType( line.substring( 23, 33 ) );
        parseAccessType( line.substring( 33, 43 ) );
        parseStatusType( line.substring( 43, 53 ) );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Consumer ID field of the Consumer Product Access Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseConsumerID( String data )
    throws Exception
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
    // Parses Product Type field of the Consumer Product Access Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseProductType( String data ) throws Exception
    {
        boolean validValue = false;
        data = data.trim();
        for ( int i=0; i< PRODUCT_TYPE_VALUES.length; i++ ) {
            if ( data.equalsIgnoreCase(PRODUCT_TYPE_VALUES[i])) {
                validValue = true;
                break;
            }
        }

        if ( !validValue ) {
            warning( "Value for the mandatory \"Product Type\" field " +
                     "doesn't match any of the pre-defined values." );
        }
        _productType = data;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Access Type field of the Consumer Product Access Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseAccessType( String data ) throws Exception
    {
        boolean validValue = false;
        data = data.trim();
        for (int i=0; i< ACCESS_TYPE_VALUES.length; i++) {
            if ( data.equalsIgnoreCase(ACCESS_TYPE_VALUES[i])) {
                validValue = true;
                break;
            }
        }
        if ( !validValue ) {
            warning( "Value for the mandatory \"Access Type\" field " +
                     "doesn't match any of the pre-defined values." );
        }
        _accessType = data;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Status Type field of the Consumer Product Access Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseStatusType( String data ) throws Exception
    {
        boolean validValue = false;
        data = data.trim();
        for (int i=0; i< STATUS_TYPE_VALUES.length; i++) {
            if ( data.equalsIgnoreCase(STATUS_TYPE_VALUES[i])) {
                validValue = true;
                break;
            }
        }
        if ( !validValue ) {
            warning( "Value for the mandatory \"Status Type\" field " +
                     "doesn't match any of the pre-defined values." );
        }
        _statusType = data;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses given Consumer Product Access Information File and returns an
    // array of ConsumerProductAccessInfoFile objects holding its contents.
    ///////////////////////////////////////////////////////////////////////////
    public static ConsumerProductAccessInfoFile[] parseFile( BufferedReader in )
    throws Exception
    {
    	String method = "ConsumerProductAccessInfoFile.parseFile";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        parseHeader( in.readLine() ); // first line should be the header

        ArrayList l = new ArrayList();
        String line = null;
        while ( (line = in.readLine() ) != null ) {
            if ( line.charAt( 0 ) == DETAIL_LINE_INDICATOR ) {
                l.add( new ConsumerProductAccessInfoFile( line ) ); // another detail line
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
        return(ConsumerProductAccessInfoFile[])l.toArray( new ConsumerProductAccessInfoFile[l.size()] );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses header line of a Consumer Product Access Information File.
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
    // Parses trailer line of a Consumer Product Access Information File.
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
        throw new Exception( "ERROR! Consumer Product Access Information File parser: " + msg );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Outputs a warning message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    private static void warning( String msg )
    {
        FFSDebug.log( "WARNING! Consumer Product Access Information File parser: " + msg );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Outputs an exception and a warning message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    private static void warning( Throwable t, String msg )
    {
        FFSDebug.log( t, "WARNING! Consumer Product Access Information File parser: " + msg );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Overrides java.lang.Object.toString(). 
    ///////////////////////////////////////////////////////////////////////////
    public String toString()
    {
        String LINE_SEP = System.getProperty( "line.separator" );
        return	"_consumerID = " + (_consumerID == null ? "null" : "\"" + _consumerID + "\"" ) + LINE_SEP +
        "_productType = " + (_productType == null ? "null" : "\"" + _productType + "\"" ) + LINE_SEP +
        "_accessType = " + (_accessType == null ? "null" : "\"" + _accessType + "\"" ) + LINE_SEP +
        "_statusType = " + (_statusType == null ? "null" : "\"" + _statusType + "\"" ) + LINE_SEP ;
    }
}
