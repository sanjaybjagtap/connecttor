//
// DBSqlUtils.java
//
// Copyright (c) 2001 Financial Fusion, Inc.
// All Rights Reserved.
//

package com.ffusion.banksim.db;

import com.ffusion.banksim.interfaces.*;

import java.sql.*;
import java.util.*;
import java.text.*;
import java.io.*;
import java.math.BigDecimal;

///////////////////////////////////////////////////////////////////////////////
// This class contains various utility metods used in db package. 
///////////////////////////////////////////////////////////////////////////////
public class DBSqlUtils implements DBSQLConstants
{
    ///////////////////////////////////////////////////////////////////////////
    // Checks to see if the given String needs to be streamed using the
    // alternate method for the Oracle drivers.
    ///////////////////////////////////////////////////////////////////////////
    static boolean needsOracleStream( BSDBParams params, String str )
    {
	if( params.getConnectionType() != BSDBParams.CONN_ORACLE ) return false;
	if( str == null || str.length() <= ORACLE_MAX_NON_LOB_LEN ) return false;
	return true;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    // Checks to see if the given byte[] needs to be streamed using the
    // alternate method for the Oracle drivers.
    ///////////////////////////////////////////////////////////////////////////
    static boolean needsOracleStream( BSDBParams params, byte[] bytes )
    {
	if( params.getConnectionType() != BSDBParams.CONN_ORACLE ) return false;
	if( bytes == null || bytes.length <= ORACLE_MAX_NON_LOB_LEN ) return false;
	return true;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    // Checks to see if the String needs special streaming for the thin client
    ///////////////////////////////////////////////////////////////////////////
    static boolean needsOracleThinStream( BSDBParams params, String str )
    {
	if( params.isNativeDriver() ) return false;
	return needsOracleStream( params, str );
    }
    
    ///////////////////////////////////////////////////////////////////////////
    // Checks to see if the String needs special streaming for the thin client
    ///////////////////////////////////////////////////////////////////////////
    static boolean needsOracleThinStream( BSDBParams params, byte[] bytes )
    {
	if( params.isNativeDriver() ) return false;
	return needsOracleStream( params, bytes );
    }
    
    ///////////////////////////////////////////////////////////////////////////
    // Fills in parameters in a PreparedStatement with given values.
    ///////////////////////////////////////////////////////////////////////////  
    static void fillParameters( PreparedStatement stmt,
    				Object[]          inArgs,
    				BSDBParams        params )
	throws SQLException
    {
	// Make sure we call clearParameters(), or DB2 gets unhappy if
	// we change between setString( i, null ) and setXXXX( i, actualValue )
	stmt.clearParameters();
	
        if( inArgs != null ) {
	    // ASA is quite happy with all values as strings, but
	    // ASE needs the right type passed
	    Object obj;
	    for( int i = 0; i < inArgs.length; i++ ) {
		obj = inArgs[i];
		if( obj == null ){
		    stmt.setString( i + 1, null );
		} else if( obj instanceof String ) {
		    String str = (String) obj;
		    if( needsOracleStream( params, str ) ) {
			// The Oracle driver doesn't seem to turn Strings into Clobs for us,
			// rather it sets the type to varchar, which causes problems
			// So here we force a character stream.
			// The magical 4000 is the maximum length of a varchar2 datatype,
			// which is what you get if you use setString.
			stmt.setCharacterStream( i + 1, new StringReader( str ), str.length() );
		    } else if( params.getConnectionType() == BSDBParams.CONN_ORACLE && str.length() == 0 ) {
			// Oracle treats the empty string as a NULL value, so we'll pass
			// in a string with one space instead. It will get trimmed to an
			// empty string in getColumnClobAsString anyways.
			stmt.setString( i + 1, " " );
		    } else {
			stmt.setString( i + 1, str );
		    }
		} else if( obj instanceof Integer ) {
		    stmt.setInt( i + 1, ((Integer)obj).intValue() );
		} else if( obj instanceof Long ) {
		    stmt.setLong( i + 1, ((Long)obj).longValue() );
		} else if( obj instanceof ByteArrayOutputStream ) {
		    stmt.setBytes( i + 1, ((ByteArrayOutputStream)obj).toByteArray() );
		} else if( obj instanceof Float ) {
		    stmt.setFloat( i + 1, ((Float)obj).floatValue() );
                } else if( obj instanceof Double ) {
		    stmt.setDouble( i + 1, ((Double)obj).doubleValue() );
		} else if( obj instanceof BigDecimal ) {
		    stmt.setBigDecimal( i + 1, (BigDecimal)obj );
		} else if( obj instanceof Timestamp) {
		    stmt.setTimestamp( i + 1, (Timestamp)obj );
		} else if( obj instanceof java.sql.Date ) {
		    stmt.setDate( i + 1, (java.sql.Date)obj );
		} else if( obj instanceof Time) {
		    stmt.setTime( i + 1, (Time)obj );
		} else if( obj instanceof byte[] ) {
		    byte[] bytes = (byte[]) obj;
		    if( needsOracleStream( params, bytes ) ) {
			// The Oracle driver pukes on setBytes > 4000.  For stuff beyond
			// that we can use the standard streaming stuff for the oci8 driver.
			stmt.setBinaryStream( i + 1, new ByteArrayInputStream( bytes ), bytes.length );
		    } else {
			stmt.setBytes( i + 1, bytes );
		    }
		} else {
		    throw new SQLException( "Error: unsupported datatype" );
		}
	    }
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Pre-process a SQL statement to remove the { | | | } blocks and
    // replace them with the correct substitution text.
    // The order is:
    // { ASA | ASE | DB2 | Oracle | DB2 OS/390 }
    // If you need a literal | in the replacement text, use !|
    ///////////////////////////////////////////////////////////////////////////
    public static String parseStmt( String original, int connType )
    {
        int     start;
        int     begin;
        int     last;
        int     end;
	int     count;
	int     barIdx;
	String  modified;
	boolean separatorExists;
	StringBuffer tempString = new StringBuffer( original.length() );

	last = 0;
	for(;;) {
	    start = original.indexOf( "{", last );
	    if( start == -1 ) break;
	    end        = original.indexOf( "}", start );
	    tempString = tempString.append( original.substring( last, start) );
	    begin      = start;
	    separatorExists = true;
	    for( count = 1; count < connType; count++ ) {
		begin = original.indexOf( "|", begin + 1 );
	        if( begin == -1 || begin > end ) {
		    separatorExists = false;
		    break;
		}
		if( original.charAt( begin - 1 ) == '!' ) {
		    count--;
		}
	    }
	    // If the proper separator doesn't exist, we go on to the next
	    // replacement string
	    if( !separatorExists ) {
	        last = end + 1;
		continue;
	    }

	    begin++;
	    // Find the end of the replacement string
	    for(;;) {
	        barIdx = original.indexOf( "|", begin );
		if( barIdx == -1 || barIdx > end ) {
		    // No terminating '|' - just copy over everything to the
		    // end '}'
		    tempString = tempString.append( original.substring( begin, end ) );
		    break;
		} else if( original.charAt( barIdx - 1 ) == '!' ) {
		    // Case where we have the escape sequence "!|" for '|'
		    //   -don't copy the '!' escape character over
		    tempString = tempString.append( original.substring( begin, barIdx - 1 ) );
		    tempString = tempString.append( '|' );
		} else {
		    // Terminating '|' exists - copy over everything to the
		    // next '|'
		    tempString = tempString.append( original.substring( begin, barIdx ) );
		    break;
		}
		begin = barIdx + 1;
	    }
	    last = end + 1;
	}

	// If we've done any substitution, create the modified string
	if( last > 0 ) {
            tempString = tempString.append( original.substring( last, original.length() ) );
            modified = tempString.toString();
	} else {
	    modified = original;
	}
        return modified;
    }

    /////////////////////////////////////////////////////////////////////////
    // Change the SQLException passed in to a java.sql.SQLException
    // which we know can be serialized.
    /////////////////////////////////////////////////////////////////////////
    public static final SQLException getRealSQLException( SQLException sqle )
    {
	StringWriter sw = new StringWriter();
	PrintWriter  pw = new PrintWriter( sw );
	sqle.printStackTrace( pw );
	pw.flush();
	pw.close();
	return new SQLException( sw.toString(),
				 sqle.getSQLState(),
				 sqle.getErrorCode() );
    }
}
