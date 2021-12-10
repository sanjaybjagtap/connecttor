//
// OracleClassWrapperHack.java
//
// Copyright (c) 2001 Financial Fusion, Inc.
// All Rights Reserved.
//

package com.ffusion.banksim.db;

import com.ffusion.banksim.interfaces.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.sql.*;

import oracle.sql.BLOB;
import oracle.sql.CLOB;


///////////////////////////////////////////////////////////////////////////////
// This class represents a kludge to prevent the oracle.sql.* classes from
// being loaded when the DBConnection object is loaded, even when you aren't
// using the method which uses them.
///////////////////////////////////////////////////////////////////////////////
class OracleClassWrapperHack
{
    ///////////////////////////////////////////////////////////////////////////
    // Streams an Oracle LOB to the DB (used for Oracle thin client).
    // NOTE: for this to work proplerly, you MUST have autocommit turned off,
    //       and the row in question must already be locked (i.e. by first doing
    //       an update with autocommit off) or you must have the for update clause
    //       on the select.
    // Parameters:
    //    conn      - the connection to use
    //    lobSelect - select for the LOB columns
    //    id        - id for the where clause of the lobSelect
    //    parms     - array of String and byte[] for the stream.
    ///////////////////////////////////////////////////////////////////////////
    void executeOracleThinLOBStream( DBConnection conn,
    				     String       lobSelect,
				     int          id,
				     Object[]     parms )
    	throws SQLException, IOException
    {
	// Check to make sure this really is an Oracle Thin client connection
	if( conn.getParams().getConnectionType() != BSDBParams.CONN_ORACLE
	   || conn.getParams().isNativeDriver() ) {
	    throw new SQLException( "Error: can't execute thin LOB stream on non-Oracle or non-thin driver" );
	}
	// First, execute the lobSelect
	Object[] idParm = { new Integer( id ) };
	DBResultSet rset = conn.prepareQuery( lobSelect );
	rset.open( idParm );
	try {
	    if( rset.getNextRow() ) {
		for( int i = 0; i < parms.length; i++ ) {
		    if( parms[i] instanceof String ) {
			// This is a Clob we're dealing with
			// but it's really an oracle.sql.CLOB.
			// We'll get the Writer, and use it to
			// stream the clob data across.
			// Then we flush and close.
			CLOB   clob = (CLOB) rset.getColumnClob( i + 1 );
			Writer wr   = clob.getCharacterOutputStream();
			wr.write( (String) parms[i] );
			wr.flush();
			wr.close();

		    } else if( parms[i] instanceof byte[] ) {
			// This is a Blob we're dealing with
			// but it's really an oracle.sql.BLOB.
			// We'll get the OutputStream, and use it to
			// stream the blob data across.
			// Then we flush and close.
			BLOB         blob = (BLOB) rset.getColumnBlob( i + 1 );
			OutputStream os   = blob.getBinaryOutputStream();
			os.write( (byte[]) parms[i] );
			os.flush();
			os.close();
		    } else {
			// We should only ever be passed Strings or byte[]
			throw new SQLException( "Error: Unsupported Oracle LOB type" );
		    }
		}
	    }
	} finally {
	    rset.close();
	}
    }
}
