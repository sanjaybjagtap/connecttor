// Copyright (c) 2001 Financial Fusion, Inc.  All Rights Reserved.

package com.ffusion.ffs.bpw.fulfill.orcc;


import java.text.DateFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;

import com.ffusion.ffs.bpw.BPWServer;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.sap.banking.common.interceptors.PerfLoggerUtil;
import com.sap.banking.io.beans.File;


public class ORCCUtil
{
    static int	transIDMultiplier = -1;

    private static final char[] ALPHABET_MAP = {
	'0', '1', '2', '3', '4', '5', '6',
	'7', '8', '9',
	'A', 'B', 'C', 'D', 'E', 'F', 'G',
	'H', 'I', 'J', 'K', 'L', 'M', 'N',
	'O', 'P', 'Q', 'R', 'S', 'G', 'U',
	'V', 'W', 'X', 'Y', 'Z' };


    public static final int 		NUM_ALPHABETS	= ALPHABET_MAP.length;
    public static final int		UNKNOWN_TYPE	= -1;
    public static final int		FI_MLF		= 1;
    public static final int		FI_DPR		= 2;
    public static final int		ORCC_MLF	= 3;
    public static final int		ORCC_ERR	= 4;
    public static final DateFormat 	ORCC_DATE_FORMAT =
    		new SimpleDateFormat( ORCCConstants.STR_ORCC_DATE_FORMAT );
    public static final DateFormat 	OFX_DATE_FORMAT =
    		new SimpleDateFormat( ORCCConstants.STR_OFX_DATE_FORMAT );
    private static final HashMap	_alphabetMap	= new HashMap();


    public static void init() throws Exception
    {
    	String method = "ORCCUtil.init";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
	try{
	    transIDMultiplier = getTransIDMultiplier();
	    initAlphabetMap();
	} catch ( Exception e ) {
	    log( "Failed to initialize ORCC properties: "+e.getLocalizedMessage() );
	    PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
	}
    }

    private static final int getTransIDMultiplier() throws Exception
    {
	return ORCCDBAPI.getTransIDMultiplier();
    }

    static final void incrementTransIDMultiplier() throws Exception
    {
	ORCCDBAPI.incrementTransIDMultiplier();
	++transIDMultiplier;
    }


    public static final int getFileType( String file )
    {
    	String method = "ORCCUtil.getFileType";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
	int type = UNKNOWN_TYPE;
	String fName = file.toUpperCase();

	if( fName.startsWith( ORCCConstants.ORCC_FILE_PREFIX ) ) {
	    try{
		String dayStr = fName.substring( "FXXXX".length(), "FXXXXDD".length() );
    
		int num = Integer.parseInt( dayStr );
		if( num<=0 || num>31 ) {
		    log( "Invalid date sub-string in file name: "+file );
		    return UNKNOWN_TYPE;
		}
	    } catch (Exception e ) {
		log( "Exception occured when processing file "+file );
		PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
		return UNKNOWN_TYPE;
	    }

	    if( fName.endsWith( ORCCConstants.MLF_EXTENSION_NAME ) ) {
		type = ORCC_MLF;
	    } else {
		log( "Unknown extension type for file "+file);
		return UNKNOWN_TYPE;
	    }
	} else if( fName.startsWith( ORCCConstants.FI_FILE_PREFIX ) ) {
	    try{
		String dayStr = fName.substring( "TXXXX".length(), "TXXXXDD".length() );
    
		int num = Integer.parseInt( dayStr );
		if( num<=0 || num>31 ) {
		    log( "Invalid date sub-string in file name: "+file );
		    return UNKNOWN_TYPE;
		}
	    } catch (Exception e ) {
		log( "Exception occured when processing file "+file );
		PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
		return UNKNOWN_TYPE;
	    }

	    if( fName.endsWith( ORCCConstants.MLF_EXTENSION_NAME ) ) {
		type = FI_MLF;
	    } else if( fName.endsWith( ORCCConstants.DPR_EXTENSION_NAME ) ) {
		type = FI_DPR;
	    } else if( fName.endsWith( ORCCConstants.ERR_EXTENSION_NAME ) ) {
		type = ORCC_ERR;
	    } else {
		log( "Unknown extension type for file "+file);
		return UNKNOWN_TYPE;
	    }
	} else {
	    log( "Unrecognized file prefix for file "+file);

	}
	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
	return type;
    }


    public static final int getFileType( File f ) { return getFileType( f.getName() ); }

    //////////////////////////////////////////////////////////////////////////
    // Initialize _alphabetMap, for remote link ID's
    //////////////////////////////////////////////////////////////////////////
    private static void initAlphabetMap()
    {
	for ( int i=0; i<ALPHABET_MAP.length; ++i ) {
	    _alphabetMap.put( new Character( ALPHABET_MAP[i] ),
	    		new Integer( i ) );
	}
    }


    public static char getAlphabetFromInt( int idx ) { return ALPHABET_MAP[idx]; }

    public static int getIntFromAlphabet( char c )
    {
	Integer val = (Integer)_alphabetMap.get( new Character( c ) );
	return ( val==null ) ?-1 :val.intValue();
    }


    public static String getProperty( String key )
    {
	String val = null;
	try{
	    val= BPWServer.getPropertyValue( key );
	} catch (FFSException e) {
	    return null;
	}

	return val;
    }

    public static String getProperty( String key, String defVal )
    {
	String val = null;

	try{
	    val = (String)BPWServer.getPropertyValue( key, defVal );
	} catch (FFSException e ) {
	    return null;
	}

	return val;
    }

    public static Properties getProperties() { return BPWServer.getProperties(); }


    ///////////////////////////////////////////////////////////////////////////
    // Get time string in given format
    ///////////////////////////////////////////////////////////////////////////
    public static final String getDateString( DateFormat dateFmt )
    {
	return dateFmt.format( new Date() );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Get time string in given format
    ///////////////////////////////////////////////////////////////////////////
    public static final String getDateString( DateFormat dateFmt, Date dt )
    {
	return dateFmt.format( dt );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Get date part of the date string
    ///////////////////////////////////////////////////////////////////////////
    public static final String getDateValue( String dateStr )
    {
	return dateStr.substring( 0, "MM/DD/YYYY".length());
    }


    ///////////////////////////////////////////////////////////////////////////
    // Get time part of the date string
    ///////////////////////////////////////////////////////////////////////////
    public static final String getTimeValue( String dateStr )
    {
	return dateStr.substring( "MM/DD/YYYY".length() );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parse the given date and time string to date+time format
    ///////////////////////////////////////////////////////////////////////////
    public static final Date parseDateTime( DateFormat fmt, String str )
    {
	return fmt.parse( str, new ParsePosition(0) );
    }


    ///////////////////////////////////////////////////////////////////////////
    // check account number format vs. account mask
    ///////////////////////////////////////////////////////////////////////////
    public static final boolean checkVSAcctMask ( String acctNum, String mask )
    {
    	String method = "ORCCUtil.checkVSAcctMask";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
	if( mask==null || mask.length()<=0 ) return true;
	int len = mask.length();
	if( acctNum == null || acctNum.length()!=len ) return false;

	// case insensitive
	acctNum = acctNum.toUpperCase();
	mask = mask.toUpperCase();
	char temp;
	for( int i=0; i<len; ++i ) {
	    temp = acctNum.charAt( i );
	    switch( mask.charAt(i) ) {
		case 'N':
		    // Numeric, must be 0-9
		    if( temp>'9'||temp<'0' ) return false;
		    break;
		case 'A':
		    // Alphabetic, must be A-Z
		    if( (temp<'A' || temp> 'Z' ) ) return false;
		    break;
		case '-':
		    if( temp!='-' ) return false;
		    break;
		case ' ':
		    // Space.
		    if( temp!=' ' ) return false;
		    break;
		case '*':
		    // alpha or numeric: 0-9, A-Z
		    if( !( (temp>='0' && temp<='9')
		    	|| (temp>='A' && temp<='Z')
			|| temp==' ' ) ) {
			return false;
		    }
		    break;
		default:
		    // Default: constant, must be equal.
		    if( temp != mask.charAt(i) ) return false;
	    }
	}
	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
	return true;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Filter out the non-digital charactors of a string. Used for SSN and Tel.
    ///////////////////////////////////////////////////////////////////////////
    public static final String getAlphaNumericString( String s )
    {
    	String method = "ORCCUtil.getAlphaNumericString";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
	if( s==null || s.length()<=0 ) return "";
	int len = s.length();
	StringBuffer sb = new StringBuffer( len );
	char c;
	for( int i=0; i<len; ++i ) {
	    c = s.charAt( i );
	    if( Character.isLetterOrDigit( c ) ) sb.append( c );
	}
	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
	return sb.toString();
    }


    ///////////////////////////////////////////////////////////////////////////
    // Filter out the non-digital charactors of a string. Used for SSN and Tel.
    ///////////////////////////////////////////////////////////////////////////
    public static final String getNumericString( String s )
    {
    	String method = "ORCCUtil.getNumericString";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
	if( s==null || s.length()<=0 ) return "";
	int len = s.length();
	StringBuffer sb = new StringBuffer( len );
	char c;
	for( int i=0; i<len; ++i ) {
	    c = s.charAt( i );
	    if( Character.isDigit( c ) ) sb.append( c );
	}
	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
	return sb.toString();
    }


    ///////////////////////////////////////////////////////////////////////////
    // Outputs a message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    public static void log( String str )
    {
        FFSDebug.log( "ORCC Adapter: " + str, FFSConst.PRINT_DEV );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Outputs an exception and a message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    public static void log( Throwable t, String str )
    {
        FFSDebug.log( t, "ORCC Adapter: " + str, FFSConst.PRINT_DEV );
    }


    // if the error level is higher or equal to logLevel,
    // concatenate and print multiple strings to log file
    public static void log(String str, int debugLevel) {
        FFSDebug.log( "ORCC Adapter: " + str, debugLevel );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Outputs a warning message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    public static void warn( String msg )
    {
        FFSDebug.log( "WARNING! ORCC Adapter: " + msg, FFSConst.PRINT_ERR );
    }
}
