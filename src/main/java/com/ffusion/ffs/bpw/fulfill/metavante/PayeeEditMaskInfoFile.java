// Copyright (c) 2001 Financial Fusion, Inc.  All Rights Reserved.

package com.ffusion.ffs.bpw.fulfill.metavante;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.util.FFSDebug;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

///////////////////////////////////////////////////////////////////////////////
// An instace of this class contains data retrieved from a Metavante Payee
// Edit Mask Information File. Format of this response file is described in
// Appendix B7 of the Metavante Bill Payment Technical Implementation Guide.
///////////////////////////////////////////////////////////////////////////////
public class PayeeEditMaskInfoFile
{
    // Variables for the values of the fields retrieved from a file record.
    public String _internalPayeeID;
    public String _obsoleteEditMask;
    public String _validEditMask;

    // Constants for line type indicators.
    private static final char HEADER_LINE_INDICATOR = '5';
    private static final char DETAIL_LINE_INDICATOR = '6';
    private static final char TRAILER_LINE_INDICATOR = '8';

    // Constant for the file type represented by this class.
    private static final String FILE_TYPE = "PAYEE-EDIT-AD";

    // SQL statements constants.
    private static final String SQL_ADD_NEW_PAYEE_EDIT_MASK =
    	"INSERT INTO BPW_PayeeEditMask( PayeeID, ObsoleteEditMask, "
	+ "ValidEditMask ) VALUES(?,?,?)";

    private static final String SQL_GET_ALL_PAYEE_EDIT_MASKS =
    	"SELECT PayeeID, ObsoleteEditMask, ValidEditMask "
        + "FROM BPW_PayeeEditMask";

    private static final String SQL_DELETE_ALL_PAYEE_EDIT_MASKS =
    	"DELETE FROM BPW_PayeeEditMask WHERE PayeeID IN ";

    // at most how many payees to delete at once using above SQL statememt
    private static final int MAX_PAYEE_SET_SIZE = 100;

    ////////////////////////////////////////////////////////////////////////////
    // Performs any required initialization of this class.
    ////////////////////////////////////////////////////////////////////////////
    public static void init() throws Exception {}


    ///////////////////////////////////////////////////////////////////////////
    // Store the information of the instance to the database. 
    ///////////////////////////////////////////////////////////////////////////
    private void storeToDB( FFSConnectionHolder dbh, Map cache )
    throws Exception
    {
	if( _internalPayeeID == null || _internalPayeeID.length() == 0 ) {
	    return; // skip invalid _internalPayeeID
	}
	
	if( _validEditMask == null || _validEditMask.length() == 0 ) {
	    return; // skip invalid _validEditMask :)
	}

	if( _obsoleteEditMask != null && _obsoleteEditMask.length() == 0 ) {
	    _obsoleteEditMask = null;
	}

	// Check if we already have given valid Edit Mask for this payee ID.
	Set masks = (Set)cache.get( _internalPayeeID );
	if( masks == null ) {
	    masks = new HashSet( 11 );
	    cache.put( _internalPayeeID, masks );
	}
	if( masks.contains( _validEditMask ) ) {
	    return; // skip duplicate _validEditMask
	}

	// Insert new row to the BPW DB.
        Object[] args =	{
            _internalPayeeID,
            _obsoleteEditMask,
            _validEditMask
        };
        DBUtil.executeStatement( dbh, SQL_ADD_NEW_PAYEE_EDIT_MASK, args );

	// If DB update succeeded then also update the cache.
	masks.add( _validEditMask );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Creates a connection to the database and stores the info in the 
    // array of Payee Edit Mask Information Files into the database
    ///////////////////////////////////////////////////////////////////////////
    public static void storeToDB( PayeeEditMaskInfoFile[] infoFiles,
    				  FFSConnectionHolder dbh )
				  throws Exception
    {
    	String method = "PayeeEditMaskInfoFile.storeToDB";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
	if( infoFiles == null || infoFiles.length < 1 ) return;
	
	// Map of _internalPayeeID --> set of _validEditMask
	HashMap cache = new HashMap();
	    
	FFSDebug.log( "Payee Edit Mask Information File parser: deleting all " +
		      "old PayeeEditMask records." );

	// Collect payee IDs for all added masks
	Set payees = new HashSet();
	for( int i = 0; i < infoFiles.length; i++ ) {
	    payees.add( infoFiles[i]._internalPayeeID );
	}
	
	// Delete all payee edit records with given payee IDs.
	Iterator it = payees.iterator();
	while( it.hasNext() ) {
	    StringBuffer sb = new StringBuffer();
	    sb.append( "('" );
	    sb.append( (String)it.next() );
	    sb.append( '\'' );
	    for( int j = 0; j < MAX_PAYEE_SET_SIZE - 1; j++ ) {
		if( !it.hasNext() ) break;
		sb.append( ",'" );
		sb.append( (String)it.next() );
		sb.append( '\'' );
	    }
	    sb.append( ')' );
	    DBUtil.executeStatement( dbh, SQL_DELETE_ALL_PAYEE_EDIT_MASKS +
				     sb.toString(), null );
	}
	
	FFSDebug.log( "Payee Edit Mask Information File parser: adding " +
		      infoFiles.length + " new PayeeEditMask records." );
	for( int i = 0; i < infoFiles.length; i++ ) {
	    infoFiles[i].storeToDB( dbh, cache );	
	}
	FFSDebug.log( "Payee Edit Mask Information File parser: successfully " +
		      "added new PayeeEditMask records." );
	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }


    ///////////////////////////////////////////////////////////////////////////
    // Default constructor resulting in an empty Payee Edit Mask Information 
    // File object.
    ///////////////////////////////////////////////////////////////////////////
    public PayeeEditMaskInfoFile() {}


    ///////////////////////////////////////////////////////////////////////////
    // Constructor taking a Detail Line from a Payee Edit Mask Information File
    // and using it to populate its instance data members.
    ///////////////////////////////////////////////////////////////////////////
    public PayeeEditMaskInfoFile( String line ) throws Exception
    {
        if( line.length() != 76 ) {
            error( "Detail line is not 76 characters long!" );
        }
    
        // The first character of the line is supposed to be Record Type.
        // We skip it here and start parsing from the second character.
        parseInternalPayeeID( line.substring( 1, 16 ) );
        parseObsoleteEditMask( line.substring( 16, 46 ) );
        parseValidEditMask( line.substring( 46, 76 ) );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Internal Payee ID field of the Payee Edit Mask Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseInternalPayeeID( String data )
    throws Exception
    {	
        data = data.trim();
        if( data.length() != 0 ) {
            _internalPayeeID = data;
        } else {
	    warning( "Value for the mandatory \"Internal Payee ID\" field is missing." );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Obsolete Edit Mask field of the Payee Edit Mask Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseObsoleteEditMask( String data )
    throws Exception
    {	
        data = data.trim();
        if( data.length() != 0 ) {
            _obsoleteEditMask = data;
        } else {
	    warning( "Value for the mandatory \"Obsolete Edit Mask\" field is missing." );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Valid Edit Mask field of the Payee Edit Mask Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseValidEditMask( String data )
    throws Exception
    {	
        data = data.trim();
        if( data.length() != 0 ) {
            _validEditMask = data;
        } else {
	    warning( "Value for the mandatory \"Valid Edit Mask\" field is missing." );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses given Payee Edit Mask Information File and returns an array of
    // PayeeEditMaskInfoFile objects holding its contents.
    ///////////////////////////////////////////////////////////////////////////
    public static PayeeEditMaskInfoFile[] parseFile( BufferedReader in )
    throws Exception
    {
    	String method = "PayeeEditMaskInfoFile.parseFile";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        parseHeader( in.readLine() ); // first line should be the header
        
        ArrayList l = new ArrayList();
        String line = null;
        while( (line = in.readLine() ) != null ) {
            if( line.charAt( 0 ) == DETAIL_LINE_INDICATOR ) {
                l.add( new PayeeEditMaskInfoFile( line ) ); // another detail line
            } else {
                parseTrailer( line, l.size() ); // this can only be the trailer line
                if( in.readLine() != null ) {
                    warning( "Extra data is available following the trailer line!" );
                }
                break; // trailer reached, no more detail lines
            }
        }
        
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return (PayeeEditMaskInfoFile[])l.toArray( new PayeeEditMaskInfoFile[l.size()] );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses header line of a Payment History Information File.
    ///////////////////////////////////////////////////////////////////////////
    private static void parseHeader( String line ) throws Exception
    {
        // verify that header line has expected length
        if( line.length() != 48 ) {
	    error( "Header line is not 48 characters long!" );
	}

	// verify that header line starts with expected character
	char c = line.charAt( 0 );
	if( c != HEADER_LINE_INDICATOR ) {
	    error( "Unexpected value of the \"Record Type\" header field: '" +
	    	   c + "', expected: '" + HEADER_LINE_INDICATOR + "'" );
	}

	// verify that File Type field has expected value
	String s = line.substring( 1, 16 );
	if( !s.startsWith( FILE_TYPE ) ) {
	    error( "Unexpected value of the \"File Type\" header field: \"" +
	    	   s + "\", expected: \"" + FILE_TYPE + "\"" );
	}

	// TODO: it might be wise to check other header fields when we know
	// what values to expect.
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses trailer line of a Payment History Information File.
    ///////////////////////////////////////////////////////////////////////////
    private static void parseTrailer( String line, int expectedRecCount )
    throws Exception
    {
        // verify that trailer line has expected length
        if( line.length() != 21 ) {
	    error( "Trailer line is not 21 characters long!" );
	}

	// verify that trailer line starts with expected character
	char c = line.charAt( 0 );
	if( c != TRAILER_LINE_INDICATOR ) {
	    error( "Unexpected value of the \"Record Type\" trailer field: '" +
	    	   c + "', expected: '" + TRAILER_LINE_INDICATOR + "'" );
	}

	// verify that Record Count field has expected value
	String s = line.substring( 1, 11 );
	try {
	    int i = Integer.parseInt( s );
	    if( i != expectedRecCount ) {
		warning( "Unexpected value of the \"Record Count\" trailer field: " +
			 i + ", expecting: " + expectedRecCount );
	    }
	} catch( Exception e ) {
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
        throw new Exception( "ERROR! Payee Edit Mask Information File parser: " + msg );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Outputs a warning message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    private static void warning( String msg )
    {
        FFSDebug.log( "WARNING! Payee Edit Mask Information File parser: " + msg );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Outputs an exception and a warning message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    private static void warning( Throwable t, String msg )
    {
        FFSDebug.log( t, "WARNING! Payee Edit Mask Information File parser: " + msg );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Overrides java.lang.Object.toString(). 
    ///////////////////////////////////////////////////////////////////////////
    public String toString()
    {
        String LINE_SEP = System.getProperty( "line.separator" );
        return  "_internalPayeeID = " + (_internalPayeeID == null ? "null" : "\"" + _internalPayeeID + "\"" ) + LINE_SEP +
                "_obsoleteEditMask = " + (_obsoleteEditMask == null ? "null" : "\"" + _obsoleteEditMask + "\"" ) + LINE_SEP +
                "_validEditMask = " + (_validEditMask == null ? "null" : "\"" + _validEditMask + "\"" ) + LINE_SEP;           
    }
}
