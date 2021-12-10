//
// BSRecordCounter.java
//
// Copyright (c) 2002 Financial Fusion, Inc.
// All Rights Reserved.
//

package com.ffusion.banksim.adapter;


import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.ffusion.banksim.db.DBConnection;
import com.ffusion.banksim.interfaces.BSException;
import com.ffusion.util.db.DBUtil;

class BSRecordCounter {

    // Object Types
    protected static final int TYPE_ACCOUNT = 1;
    protected static final int TYPE_LOCKBOX = 2;
    protected static final int TYPE_EXTENDABEAN = 3;

    // Object ID
    protected static final int EXTENDABEAN_ID = 0;

    // Counter name constants
    protected static final String LOCKBOX_CREDITITEM_INDEX = "LOCKBOX_CREDITITEM_INDEX";
    protected static final String LOCKBOX_TRANSACTION_INDEX = "LOCKBOX_TRANSACTION_INDEX";
    protected static final String DISBURSEMENT_TRANSACTION_INDEX = "DISBURSEMENT_TRANSACTION_INDEX";
    protected static final String EXTENDABEAN_INDEX = "ExtendABeanIndex";


    // SQL queries
    private static final String SQL_ADD_COUNTER = "INSERT INTO BS_RecordCounter( ObjectType, ObjectID, CounterName, NextIndex ) VALUES( ?, ?, ?, ? )";

    private static final String SQL_INCREMENT_INDEX = "UPDATE BS_RecordCounter SET NextIndex=NextIndex+1 WHERE ObjectType=? AND ObjectID=? AND CounterName=?";

    private static final String SQL_DELETE_COUNTER = "DELETE FROM BS_RecordCounter WHERE ObjectType=? AND ObjectID=? AND CounterName=?";

    private static final String SQL_GET_INDEX = "SELECT NextIndex FROM BS_RecordCounter WHERE ObjectType=? AND ObjectID=? AND CounterName=?";

    private static final String SQL_COUNTER_EXISTS = "SELECT * FROM BS_RecordCounter WHERE ObjectType=? AND ObjectID=? AND CounterName=?";

    private static final String SQL_GET_INDEX_FOR_ACCOUNT_INFO = "SELECT NextIndex FROM BS_RecordCounter counter, BS_Account acc WHERE acc.AccountNumber=? AND acc.RoutingNum=? AND acc.AccountID = counter.ObjectID AND ObjectType=? AND CounterName=?";

    private static final String SQL_GET_LOCKBOX_ID = "SELECT lockbox.LockboxID FROM BS_Lockbox lockbox, BS_Account acc " +
	"WHERE acc.AccountID = ? AND acc.RoutingNum = ? AND lockbox.LockboxNumber = ? AND acc.AccountID = lockbox.AccountID";


    // Get the index for the given counter
    protected static long getIndex( DBConnection connection, int type, int objectID, String counterName ) throws BSException
    {
	PreparedStatement stmt = null;
	ResultSet rs = null;
        try {
	    stmt = connection.prepareStatement( connection, SQL_GET_INDEX );
	    stmt.setInt( 1, type );
	    stmt.setString( 2, Integer.toString( objectID ) );
	    stmt.setString( 3, counterName );
	    rs = DBConnection.executeQuery( stmt, SQL_GET_INDEX );
	     
	    if( rs.next() ) {
		long index = rs.getLong( 1 );
		DBUtil.closeResultSet( rs );
	        return index;
     	    } else {
	       throw new BSException(BSException.BSE_DB_EXCEPTION);
	    }
	} catch ( Exception e ) {
	    throw new BSException ( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {

	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	}
    }

    // Get the index for the given account (identified by number, bank id and routing number)
    protected static long getIndex( DBConnection connection, String accountNumber, String routingNumber, int objectType,
				    String counterName ) throws BSException
    {
	PreparedStatement stmt = null;
	ResultSet rs = null;
        try {
	    stmt = connection.prepareStatement( connection, SQL_GET_INDEX_FOR_ACCOUNT_INFO );
	    stmt.setString( 1, accountNumber );
	    stmt.setString( 2, routingNumber );
	    stmt.setInt( 3, objectType );
	    stmt.setString( 4, counterName );
	    rs = DBConnection.executeQuery( stmt, SQL_GET_INDEX_FOR_ACCOUNT_INFO );
	     
	    if( rs.next() ) {
		long index = rs.getLong( 1 );
	    DBUtil.closeResultSet( rs );
	        return index;
     	    } else {
	       return -1;
	    }
	} catch ( Exception e ) {
	    return -1;
	} finally {

	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	}
    }

    // Get the index for the given account (identified by number, bank id and routing number) and
    // lockbox number
    protected static long getIndex( DBConnection connection, String accountNumber, 
				    String routingNumber, String lockboxNumber, int objectType, 
				    String counterName ) throws BSException
    {
	PreparedStatement stmt = null;
	ResultSet rs = null;
        try {
	    stmt = connection.prepareStatement( connection, SQL_GET_LOCKBOX_ID );
	    stmt.setString( 1, accountNumber );
	    stmt.setString( 2, routingNumber );
	    stmt.setString( 3, lockboxNumber );
	    rs = DBConnection.executeQuery( stmt, SQL_GET_LOCKBOX_ID );
	    
	    if( rs.next() ) {
		int id = rs.getInt( 1 );
		DBUtil.closeResultSet( rs );
		long index = getIndex( connection, objectType, id, counterName );
	        return index;
     	    } else {
		return -1;
	    }
	} catch ( Exception e ) {
	    throw new BSException ( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	}
    }


    // Add a new counter
    protected static void addNewCounter( DBConnection connection, int type, String objectID, String counterName ) throws BSException
    {

	PreparedStatement stmt = null;
        try {
	    stmt = connection.prepareStatement( connection, SQL_ADD_COUNTER );
	    stmt.setInt( 1, type );
	    stmt.setString( 2, objectID );
	    stmt.setString( 3, counterName );
	    stmt.setLong( 4, 1 );
	    DBConnection.executeUpdate( stmt, SQL_ADD_COUNTER );
	} catch ( Exception e ) {
	    throw new BSException ( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	}
    }


    // Delete the specified counter
    protected static void deleteCounter( DBConnection connection,  int type, int objectID, String counterName ) throws BSException
    {
	PreparedStatement stmt = null;
        try {
	    stmt = connection.prepareStatement( connection, SQL_DELETE_COUNTER );
	    stmt.setInt( 1, type );
	    stmt.setInt( 2, objectID );
	    stmt.setString( 3, counterName );
	    DBConnection.executeUpdate( stmt, SQL_DELETE_COUNTER );
	} catch ( Exception e ) {
	    throw new BSException ( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	}
    }


    // increment the specified index by one
    protected static void incrementIndex( DBConnection connection, int type, int objectID, String counterName ) throws BSException
    {
	PreparedStatement stmt = null;
        try {
	    stmt = connection.prepareStatement( connection, SQL_INCREMENT_INDEX );
	    stmt.setInt( 1, type );
	    stmt.setInt( 2, objectID );
	    stmt.setString( 3, counterName );
	    DBConnection.executeUpdate( stmt, SQL_INCREMENT_INDEX );
	} catch ( Exception e ) {
	    throw new BSException ( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	}
    }

    // the current index in the table refers to the last index
    // so increment the current index, and return the new value
    /*protected static long getNextIndex( DBConnection connection, int type, int objectID, String counterName ) throws BSException
    {
	PreparedStatement stmt = null;
	long index = 0;
	ResultSet rs = null;
        try {
	    stmt = connection.prepareStatement( connection, SQL_INCREMENT_INDEX );
	    stmt.setInt( 1, type );
	    stmt.setInt( 2, objectID );
	    stmt.setString( 3, counterName );
	    DBConnection.executeUpdate( stmt, SQL_INCREMENT_INDEX );

	    stmt = connection.prepareStatement( connection, SQL_GET_INDEX );
	    stmt.setInt( 1, type );
	    stmt.setInt( 2, objectID );
	    stmt.setString( 3, counterName );
	    rs = DBConnection.executeQuery( stmt, SQL_GET_INDEX );
	     
	    if( rs.next() ) {
	        index = rs.getLong( 1 );
		rs.close();
     	    } else {
	       throw new BSException(BSException.BSE_DB_EXCEPTION);
	    }

	    return index;
	} catch ( Exception e ) {
	    throw new BSException ( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	}
    }
*/
    // the current index in the table refers to the last index
    // so increment the current index, and return the new value
    // objectID will be a String if accountID is passed in
    protected static long getNextIndex( DBConnection connection, int type, String objectID, String counterName ) throws BSException
    {
	PreparedStatement stmt = null;
	long index = 0;
	ResultSet rs = null;
        try {
	    stmt = connection.prepareStatement( connection, SQL_INCREMENT_INDEX );
	    stmt.setInt( 1, type );
	    stmt.setString( 2, objectID );
	    stmt.setString( 3, counterName );
	    DBConnection.executeUpdate( stmt, SQL_INCREMENT_INDEX );


	    stmt = connection.prepareStatement( connection, SQL_GET_INDEX );
	    stmt.setInt( 1, type );
	    stmt.setString( 2, objectID );
	    stmt.setString( 3, counterName );
	    rs = DBConnection.executeQuery( stmt, SQL_GET_INDEX );
	     
	    if( rs.next() ) {
	        index = rs.getLong( 1 );
			DBUtil.closeResultSet( rs );
     	    } else {
		addNewCounter( connection, type, objectID, counterName );
		return 1;
	       //throw new BSException(BSException.BSE_DB_EXCEPTION);
	    }

	    return index;
	} catch ( Exception e ) {
	    throw new BSException ( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	}
    }


    // returns true if counter exists, false otherwise
    protected static boolean counterExists( DBConnection connection, int type, int objectID, String counterName ) throws BSException
    {
	PreparedStatement stmt = null;
	ResultSet rs = null;
        try {
	    stmt = connection.prepareStatement( connection, SQL_COUNTER_EXISTS );
	    stmt.setInt( 1, type );
	    stmt.setInt( 2, objectID );
	    stmt.setString( 3, counterName );
	    rs = DBConnection.executeQuery( stmt, SQL_COUNTER_EXISTS );
	     
	    if( rs.next() ) {
		DBUtil.closeResultSet( rs );
	        return true;
     	    } else {
     	DBUtil.closeResultSet( rs );
		return false;
	    }

	} catch ( Exception e ) {
	    throw new BSException ( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {

	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }

	}
    }

}
