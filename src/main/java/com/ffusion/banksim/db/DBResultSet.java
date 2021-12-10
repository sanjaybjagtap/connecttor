//
// DBResultSet.java
//
// Copyright (c) 2001 Financial Fusion, Inc.
// All Rights Reserved.
//

package com.ffusion.banksim.db;

import com.ffusion.banksim.interfaces.*;
import com.ffusion.util.logging.DebugLog;
import org.slf4j.*;

import java.sql.*;
import java.util.*;

import java.util.logging.*;


/**
 * This class implements a result set.  It requires a connection
 * to the database.
 */
public class DBResultSet
{
	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(DBResultSet.class);

    DBResultSet( PreparedStatement stmt, BSDBParams params )
    {
        _isClosed         = true;
        _rSet             = null;
        _metaData         = null;
	_sqlStatement     = stmt;
	_params           = params;
    }


    protected void finalize() throws SQLException
    {
        if( !_isClosed ) {
            close();
        }
    }


    /**
     * This method closes the result set.
     */
    public final void close() throws SQLException
    {
	try {
	    if( _rSet != null ) {
		_rSet.close();
		_rSet = null;
		_metaData = null;
	    }
	} catch( SQLException sqle ) {
	    if( !_params.isHA() ||
		!( sqle.getSQLState().equals( DBConnection.JC_HA_EXCEPTION_STATE )
		    || sqle.getSQLState().equals( DBConnection.JC_IO_EXCEPTION_STATE ) ) )
	    {
		// We're not in a HA session, or it's not an HA related exception
		throw  sqle;
	    }
	    // Silently ignore HA errors here
	}
	_isClosed = true;
	_rowPos = 0;
	_column = 0;
    }


    /**
     * This method executes a previously prepared statement
     */
    public final void open( Object[] inArgs ) throws SQLException
    {
	if( inArgs != null ) {
	    DebugLog.log(Level.FINE, "com.ffusion.banksim.db.DBResultSet.open()\n" +
			 "inArgs[]: " + Arrays.asList(inArgs).toString() );
	}

	for( int i = 0; ; i++ ) {
	    try {
		if( _rSet != null ) {
		    _rSet.close();
		    _rSet = null;
		    _metaData = null;
		}
		DBSqlUtils.fillParameters( _sqlStatement, inArgs, _params );
		doOpen();
		return;
	    } catch( SQLException sqle ) {
		handleHAException( sqle, false, i );
	    }
	}
    }

    public final void open () throws SQLException { open(null); }


    private void doOpen() throws SQLException
    {
	_rSet = _sqlStatement.executeQuery();
	_metaData = _rSet.getMetaData();
	_nColumns = _metaData.getColumnCount();
	_isClosed = false;
    }

    
    /**
     * This method gets the next row if there is one.
     * @return True if there was another row retrieved.
     * False if there are no more rows to retrieve.
     */
    public final boolean getNextRow() throws SQLException
    {
	for( int i = 0; ; i++ ) {
	    try {
		_column = 0;
	
		if( _rSet != null ) {
		    boolean ret = _rSet.next();
		    _rowPos++;
		    return ret;
		}
		return false;
	    } catch( SQLException sqle ) {
		handleHAException( sqle, false, i );
	    }
	}
    }


    /**
     * This method gets the result set metadata for the 
     * the current result set.  
     */
    public final ResultSetMetaData getResultSetMetaData() { return _metaData; }
    

    /**
     * Use this method to retrieve all the columns for a row in a
     * string array.
     * @return An array of strings, each of which represents the
     * value of a column in a result set.
     */
    public final String[] getColumnsAsArray() throws SQLException
    {
        String columns[] = new String[ _nColumns ];

	for( int j = 0; ; j++ ) {
	    try {
		for( int i = 0; i < _nColumns; i++ ) {
		    columns[i] = getColumnString( i + 1 );
		}
		return columns;
	    } catch( SQLException sqle ) {
		handleHAException( sqle, false, j );
	    }
	}
    }


    /**
     * This method returns the particular column as a string.  The columns
     * are 1-based.
     * @param column The column to retrieve.
     * @return The value as a string.
     */
    public final String getColumnString( int column ) throws SQLException
    {
	for( int i = 0; ; i++ ) {
	    try {
		_column = column;
		String str = _rSet.getString( column );
		
		if( !_rSet.wasNull() ) {
		    // trim all CHAR column types as JDBC pads this type with spaces
		    if( _metaData.getColumnType( column ) == Types.CHAR ) {
			str = str.trim();
		    }
		}
		return str;
	    } catch( SQLException sqle ) {
		handleHAException( sqle, false, i );
	    }
	}
    }


    /**
     * This method returns the particular column as a string.  The columns
     * are 1-based.
     *
     * Unlike the above method, this method will not trim whitespace
     * from CHAR column types.  This is important if one wishes to read in
     * a character or string ending in characters less than /u0021
     * (like a space or tab character).
     *
     * @param column The column to retrieve.
     * @return The value as a string.
     */
    public final String getColumnStringNoTrim( int column ) throws SQLException
    {
	for( int i = 0; ; i++ ) {
	    try {
		_column = column;
		String str = _rSet.getString( column );
		
		return str;
	    } catch( SQLException sqle ) {
		handleHAException( sqle, false, i );
	    }
	}
    }

    /**
     * This method returns the particular column as a string.  The columns
     * are 1-based.  The column in question is stored in the DB as a CLOB
     *
     * @param column The column to retrieve.
     * @return The value as a string.
     */
    public final String getColumnClobAsString( int column ) throws SQLException
    {
	if( _params.getConnectionType() != BSDBParams.CONN_ORACLE ) {
	    // Since this isn't an Oracle connection, we
	    // pass this off to getColumnString which works
	    // for everything else.
	    return getColumnString( column );
	}
	
	for( int i = 0; ; i++ ) {
	    try {
		_column = column;
		Clob clob = _rSet.getClob( column );
		if( _rSet.wasNull() ) return null;
		// To do this properly, we would get MAX_INT chars at a time,
		// but really, who gets more than 2^31 - 1 (2GB) characters in a string?
		String str = clob.getSubString( 1, (int) clob.length() );
		
		return str;
	    } catch( SQLException sqle ) {
		handleHAException( sqle, false, i );
	    }
	}
    }


    /**
     * This method returns the particular column as a byte[].  The columns
     * are 1-based.  The column in question is stored in the DB as a BLOB
     *
     * @param column The column to retrieve.
     * @return The value as a string.
     */
    public final byte[] getColumnBlobAsBytes( int column ) throws SQLException
    {
	if( _params.getConnectionType() != BSDBParams.CONN_ORACLE ) {
	    // Since this isn't an Oracle connection, we
	    // pass this off to getColumnBytes which works
	    // for everything else.
	    return getColumnBytes( column );
	}
	
	// Oracle connections require Blob objects
	for( int i = 0; ; i++ ) {
	    try {
		_column = column;
		Blob blob = _rSet.getBlob( column );
		if( _rSet.wasNull() ) return null;
		// To do this properly, we would get MAX_INT chars at a time,
		// but really, are we really going to get more than 2^31 - 1 (2GB)
		// bytes at a time here?
		byte[] bytes = blob.getBytes( 1, (int) blob.length() );
		
		return bytes;
	    } catch( SQLException sqle ) {
		handleHAException( sqle, false, i );
	    }
	}
    }


    /**
     * Use this method to get the specified column as an integer.  The columns
     * are 1-based.
     * @param column The column to retrieve.
     * @return The column value as an integer.
     */
    public final int getColumnInt( int column ) throws SQLException
    {
	for( int i = 0; ; i++ ) {
	    try {
		_column = column;
		return _rSet.getInt( column );
	    } catch( SQLException sqle ) {
		handleHAException( sqle, false, i );
	    }
	}
    }

    /**
     * Use this method to get the specified column as a long.  The columns
     * are 1-based.
     * @param column The column to retrieve.
     * @return The column value as a long.
     */
    public final long getColumnLong( int column ) throws SQLException
    {
	for( int i = 0; ; i++ ) {
	    try {
		_column = column;
		return _rSet.getLong( column );
	    } catch( SQLException sqle ) {
		handleHAException( sqle, false, i );
	    }
	}
    }


    /**
     * Use this method to get the specified column as a boolean.  The columns
     * are 1-based.
     * @param column The column to retrieve.
     * @return The column value as a boolean.
     */
    public final boolean getColumnBool( int column ) throws SQLException
    {
	for( int i = 0; ; i++ ) {
	    try {
		_column = column;
		return _rSet.getBoolean( column );
	    } catch( SQLException sqle ) {
		handleHAException( sqle, false, i );
	    }
	}
    }


    /**
     * Use this method to get the specified column as an array of bytes.  
     * The columns are 1-based.
     * @param column The column to retrieve.
     * @return The column value as a byte array.
     */
    public final byte[] getColumnBytes( int column ) throws SQLException
    {
	for( int i = 0; ; i++ ) {
	    try {
		_column = column;
		return _rSet.getBytes( column );
	    } catch( SQLException sqle ) {
		handleHAException( sqle, false, i );
	    }
	}
    }


    /**
     * jConnect (or ODBC or both) have problems converting a bigint to a
     * long -- even though both are 64 bit.  So this routine gets the bigint
     * column and returns it as a long.
     * @param column The column to retrieve.
     * @return The column value as a long.
     */
    public final long getColumnBigIntAsLong( int column ) throws SQLException
    {
	for( int i = 0; ; i++ ) {
	    try {
		_column = column;
		java.math.BigDecimal val = _rSet.getBigDecimal( column, 0 );
		if( _rSet.wasNull() ) {
		    return 0;
		}
		return val.longValue();
	    } catch( SQLException sqle ) {
		handleHAException( sqle, false, i );
	    }
	}
    }


    //////////////////////////////////////////////////////////////////////////
    // Returns the Clob object for this column
    //////////////////////////////////////////////////////////////////////////
    public final Clob getColumnClob( int column ) throws SQLException
    {
	for( int i = 0; ; i++ ) {
	    try {
		_column = column;
		return _rSet.getClob( column );
	    } catch( SQLException sqle ) {
		handleHAException( sqle, false, i );
	    }
	}
    }

    //////////////////////////////////////////////////////////////////////////
    // Returns the Blob object for this column
    //////////////////////////////////////////////////////////////////////////
    public final Blob getColumnBlob( int column ) throws SQLException
    {
	for( int i = 0; ; i++ ) {
	    try {
		_column = column;
		return _rSet.getBlob( column );
	    } catch( SQLException sqle ) {
		handleHAException( sqle, false, i );
	    }
	}
    }

    public final boolean wasNull() throws SQLException
    {
	for( int i = 0; ; i++ ) {
	    try {
		return _rSet.wasNull();
	    } catch( SQLException sqle ) {
		handleHAException( sqle, true, i );
	    }
	}
    }
    
    //
    // Returns the connection type.
    //
    public final BSDBParams getConnectionParams()
    {
	return _params;
    }
    
    // If we've got a HA session going and the exception thrown is
    // the exception we get on failover, then restore the row position
    // in the result set and possibly redo the last column fetch.
    // Otherwise, rethrow the exception.
    private void handleHAException( SQLException sqle, boolean refetchColumn, int retry )
    	throws SQLException
    {
	if( !_params.isHA() ||
	    !( sqle.getSQLState().equals( DBConnection.JC_HA_EXCEPTION_STATE )
		|| sqle.getSQLState().equals( DBConnection.JC_IO_EXCEPTION_STATE ) ) )
	{
	    // We're not in a HA session, or it's not an HA related exception
	    throw sqle;
	}

	if( retry >= DBConnection.HA_MAX_RETRIES ) {
	    // Too many retries
	    throw sqle;
	}
	
	// Save state so that we don't accidentally reset it
	int lastRowPos = _rowPos;
	int lastColumn = _column;
	
	// Reset the result set
	// This assumes that the previously prepared statement will be okay
	// to re-use.
	doOpen();
	
	// Restore the row position
	while( lastRowPos != _rowPos ) {
	    if( !getNextRow() ) {
		// We couldn't get back to the previous state,
		// so rethrow the exception
		throw sqle;
	    }
	}

	// Redo the last column get
	if( refetchColumn && lastColumn > 0 ) {
	    _rSet.getString( lastColumn );
	}
    }

    //
    // Private member variables
    //
    private ResultSet           _rSet;
    private PreparedStatement   _sqlStatement;
    private int                 _nColumns;
    private boolean             _isClosed;
    private ResultSetMetaData   _metaData;
    private int			_rowPos;  // last row processed
    private int			_column;  // last column fetched
    private BSDBParams		_params;
}
