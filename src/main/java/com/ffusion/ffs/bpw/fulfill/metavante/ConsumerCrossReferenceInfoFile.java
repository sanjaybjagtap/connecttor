// Copyright (c) 2001 Financial Fusion, Inc.  All Rights Reserved.

package com.ffusion.ffs.bpw.fulfill.metavante;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;

import com.ffusion.ffs.bpw.db.ConsumerCrossRef;
import com.ffusion.ffs.bpw.interfaces.ConsumerCrossRefInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSUtil;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

///////////////////////////////////////////////////////////////////////////////
// An instace of this class contains data retrieved from a Metavante Consumer
// Cross Reference Information File. Format of this response file is described
// in Appendix B5 of the Metavante Bill Payment Technical Implementation Guide.
///////////////////////////////////////////////////////////////////////////////
public class ConsumerCrossReferenceInfoFile implements DBConsts {
    // Variables for the values of the fields retrieved from a file record.
    public String _consumerID;
    public String _federalTaxID;
    public String _consumerSSN;
    public String _sponsorID;
    public String _sponsorConsumerID;
    public String _consumerType;
    public String _submitDate;

    // Constants for line type indicators.
    private static final char HEADER_LINE_INDICATOR = '5';
    private static final char DETAIL_LINE_INDICATOR = '6';
    private static final char TRAILER_LINE_INDICATOR = '8';

    // Constant for the file type represented by this class.
    private static final String FILE_TYPE = "CONS-XREF-AD";

    // HashMaps for caching the contents of the database
    public static HashMap _cacheBySponsorConsumerID;
    public static HashMap _cacheByConsumerID;


    ////////////////////////////////////////////////////////////////////////////
    // Populates the static cache with the contents in the database.
    ////////////////////////////////////////////////////////////////////////////
    public static void init() throws Exception
    {
        _cacheBySponsorConsumerID = new HashMap();
        _cacheByConsumerID = new HashMap();
    }


    ////////////////////////////////////////////////////////////////////////////
    // Look up a ConsumerCrossReferenceInfoFile from the cache with the given 
    // Sponsor Consumer ID.
    ////////////////////////////////////////////////////////////////////////////
    public static ConsumerCrossReferenceInfoFile lookupBySponsorConsumerID(String sponsorConsumerID, FFSConnectionHolder dbh )
    throws Exception
    {
        ConsumerCrossReferenceInfoFile crossRef; 
        synchronized( _cacheBySponsorConsumerID ) {
            crossRef = (ConsumerCrossReferenceInfoFile) _cacheBySponsorConsumerID.get( sponsorConsumerID );
        }
        if (crossRef == null) {
            ConsumerCrossRefInfo conXRef = ConsumerCrossRef.getByCustomerID(sponsorConsumerID,dbh);
            if (conXRef != null) {
                crossRef = new ConsumerCrossReferenceInfoFile();
                crossRef._consumerID = conXRef.consumerID;
                crossRef._federalTaxID = conXRef.federalTaxID;
                crossRef._consumerSSN = conXRef.consumerSSN;
                crossRef._sponsorID = conXRef.sponsorID;
                crossRef._sponsorConsumerID = conXRef.customerID;
                crossRef._consumerType = conXRef.consumerType;
                synchronized( _cacheBySponsorConsumerID ) {
                    _cacheBySponsorConsumerID.put( sponsorConsumerID, crossRef );
                }
            }
        }
        return crossRef;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Look up a ConsumerCrossReferenceInfoFile from the cache with the given 
    // Consumer ID.
    ////////////////////////////////////////////////////////////////////////////
    public static ConsumerCrossReferenceInfoFile lookupByConsumerID( String consumerID, 
                                                                     FFSConnectionHolder dbh )
    throws Exception
    {
        ConsumerCrossReferenceInfoFile crossRef; 
        synchronized( _cacheByConsumerID ) {
            crossRef = (ConsumerCrossReferenceInfoFile) _cacheByConsumerID.get( consumerID );
        }
        if (crossRef == null) {
            ConsumerCrossRefInfo conXRef = ConsumerCrossRef.getByConsumerID(consumerID,dbh);
            if (conXRef != null) {
                crossRef = new ConsumerCrossReferenceInfoFile();
                crossRef._consumerID = conXRef.consumerID;
                crossRef._federalTaxID = conXRef.federalTaxID;
                crossRef._consumerSSN = conXRef.consumerSSN;
                crossRef._sponsorID = conXRef.sponsorID;
                crossRef._sponsorConsumerID = conXRef.customerID;
                crossRef._consumerType = conXRef.consumerType;
                synchronized( _cacheByConsumerID ) {
                    _cacheByConsumerID.put( consumerID, crossRef );
                }
            }
        }
        return crossRef;
    }

    public static void deleteFromSponsorConsumerCache( String sponsorConsumerID )
    {
        synchronized( _cacheBySponsorConsumerID ) {
            _cacheBySponsorConsumerID.remove( sponsorConsumerID );
        }
    }
    public static void deleteFromConsumerIDCache( String consumerID )
    {
        synchronized( _cacheByConsumerID ) {
            _cacheByConsumerID.remove( consumerID );
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    // Store the information of the instance to the static cache and the
    // database with the given connection.
    ////////////////////////////////////////////////////////////////////////////
    private void storeToDB( FFSConnectionHolder dbh ) throws Exception
    {
        ConsumerCrossRefInfo cxref = new ConsumerCrossRefInfo(
                                                             _consumerID,
                                                             _federalTaxID,
                                                             _consumerSSN,
                                                             _sponsorID,
                                                             _sponsorConsumerID,
                                                             _consumerType,
                                                             FFSUtil.getDateString()
                                                             );
        ConsumerCrossRef.add(cxref, dbh);

        // add to static cache
        synchronized (_cacheBySponsorConsumerID) {            
            _cacheBySponsorConsumerID.put( _sponsorConsumerID, this );
        }
        synchronized (_cacheByConsumerID) {            
            _cacheByConsumerID.put( _consumerID, this );
        }

    }

    ////////////////////////////////////////////////////////////////////////////
    // Stores the info in the array of ConsumerCrossReferenceInfoFile objcets
    // into the database.
    ////////////////////////////////////////////////////////////////////////////
    public static void storeToDB( ConsumerCrossReferenceInfoFile[] infoFiles,
                                  FFSConnectionHolder dbh )
    throws Exception
    {
        for ( int i = 0; i < infoFiles.length; i++ ) {
            infoFiles[i].storeToDB( dbh );
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    // Default constructor resulting in an empty File object.
    ////////////////////////////////////////////////////////////////////////////
    public ConsumerCrossReferenceInfoFile() {}

    ////////////////////////////////////////////////////////////////////////////
    // Constructor taking a Detail Line from a Consumer Cross Reference 
    // Information File and using it to populate its instance data members.
    ////////////////////////////////////////////////////////////////////////////
    public ConsumerCrossReferenceInfoFile( String line ) throws Exception
    {
    	  	if ( line.length() != 71 ) {
            error( "Detail line is not 71 characters long!" );
        }

        // The first character of the line is supposed to be Record Type.
        // We skip it here and start parsing from the second character.
        // Note: Metavante specs Appendix B5 have the order of Federal Tax ID
        // and Consumer SNN incorrectly switched.
        parseConsumerID( line.substring( 1, 23 ) );
        parseConsumerSSN( line.substring( 23, 32 ) );
        parseFederalTaxID( line.substring( 32, 41 ) );
        parseSponsorID( line.substring( 41, 51 ) );
        parseSponsorConsumerID( line.substring( 51, 71 ) );
    }


    ////////////////////////////////////////////////////////////////////////////
    // Parses Consumer ID field of the Consumer Cross Reference Information 
    // File.
    ////////////////////////////////////////////////////////////////////////////
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

    ////////////////////////////////////////////////////////////////////////////
    // Parses Federal Tax ID field of the Consumer Cross Reference Information 
    // File.
    ////////////////////////////////////////////////////////////////////////////
    private void parseFederalTaxID( String data )
    {
        data = data.trim();
        if ( data.length() != 0 ) {
            _federalTaxID = data;
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // Parses Consumer SSN field of the Consumer Cross Reference Information 
    // File.
    ////////////////////////////////////////////////////////////////////////////
    private void parseConsumerSSN( String data )
    {
        data = data.trim();
        if ( data.length() != 0 ) {
            _consumerSSN = data;
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // Parses Sponsor ID field of the Consumer Cross Reference Information File.
    ////////////////////////////////////////////////////////////////////////////
    private void parseSponsorID( String data )
    {
        data = MetavanteHandler.removeNumberPadding( data );
        if ( data.length() > 0 ) {
            _sponsorID = data;
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // Parses Sponsor Consumer ID field of the Consumer Cross Reference 
    // Information File.
    ////////////////////////////////////////////////////////////////////////////
    private void parseSponsorConsumerID( String data ) throws Exception
    {
        data = data.trim();
        if ( data.length() != 0 ) {
            _sponsorConsumerID = data;
        }
        else {
            warning( "Value for the mandatory \"Consumer ID\" field is missing." );
        }   
    }


    ////////////////////////////////////////////////////////////////////////////
    // Parses given Consumer Cross Reference Information File and returns an
    // array of ConsumerCrossReferenceInfoFile objects holding its contents.
    ////////////////////////////////////////////////////////////////////////////
    public static ConsumerCrossReferenceInfoFile[] parseFile( BufferedReader in )
    throws Exception
    {
    	String method = "ConsumerCrossReferenceInfoFile.parseFile";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        parseHeader( in.readLine() ); // first line should be the header

        ArrayList l = new ArrayList();
        String line = null;
        while ( (line = in.readLine() ) != null ) {
            if ( line.charAt( 0 ) == DETAIL_LINE_INDICATOR ) {
                l.add( new ConsumerCrossReferenceInfoFile( line ) ); // another detail line
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
        return(ConsumerCrossReferenceInfoFile[])l.toArray( new ConsumerCrossReferenceInfoFile[l.size()] );
    }


    ////////////////////////////////////////////////////////////////////////////
    // Parses header line of a Consumer Cross Reference Information File.
    ////////////////////////////////////////////////////////////////////////////
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


    ////////////////////////////////////////////////////////////////////////////
    // Parses trailer line of a Consumer Cross Reference Information File.
    ////////////////////////////////////////////////////////////////////////////
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
        throw new Exception( "ERROR! Consumer Cross Reference Information File parser: " + msg );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Outputs a warning message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    private static void warning( String msg )
    {
        FFSDebug.log( "WARNING! Consumer Cross Reference Information File parser: " + msg );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Outputs an exception and a warning message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    private static void warning( Throwable t, String msg )
    {
        FFSDebug.log( t, "WARNING! Consumer Cross Reference Information File parser: " + msg );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Overrides java.lang.Object.toString(). 
    ///////////////////////////////////////////////////////////////////////////
    public String toString()
    {
        String LINE_SEP = System.getProperty( "line.separator" );
        return  "_consumerID = " + (_consumerID == null ? "null" : "\"" + _consumerID + "\"" ) + LINE_SEP +
        "_federalTaxID = " + (_federalTaxID == null ? "null" : "\"" + _federalTaxID + "\"" ) + LINE_SEP +
        "_consumerSSN = " + (_consumerSSN == null ? "null" : "\"" + _consumerSSN + "\"" ) + LINE_SEP +
        "_sponsorID = " + (_sponsorID == null ? "null" : "\"" + _sponsorID + "\"" ) + LINE_SEP +
        "_sponsorConsumerID = " + (_sponsorConsumerID == null ? "null" : "\"" + _sponsorConsumerID + "\"" ) + LINE_SEP;        
    }

    public void addCache( ConsumerCrossRefInfo cxref ) throws Exception
    {
        ConsumerCrossReferenceInfoFile info = new ConsumerCrossReferenceInfoFile();
        info._consumerID = cxref.consumerID;
        info._federalTaxID = cxref.federalTaxID;
        info._consumerSSN = cxref.consumerSSN;
        info._sponsorID = cxref.sponsorID;
        info._sponsorConsumerID = cxref.customerID;
        info._consumerType = cxref.consumerType;
        info._submitDate = cxref.submitDate;

        // add to static cache
        synchronized (_cacheBySponsorConsumerID) {            
            _cacheBySponsorConsumerID.put( info._sponsorConsumerID, info );
        }
        synchronized (_cacheByConsumerID) {            
            _cacheByConsumerID.put( info._consumerID, info );
        }
    }

    public void deleteCache( ConsumerCrossRefInfo cxref ) throws Exception
    {
        // delete from static cache
        synchronized (_cacheBySponsorConsumerID) {            
            _cacheBySponsorConsumerID.remove( cxref.customerID );
        }
        synchronized( _cacheByConsumerID ) {
            _cacheByConsumerID.remove( cxref.consumerID );
        }

    }
}
