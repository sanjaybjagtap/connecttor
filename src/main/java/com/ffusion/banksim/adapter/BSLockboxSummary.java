//
// BSLockboxSummary.java
//
// Copyright (c) 2002 Financial Fusion, Inc.
// All Rights Reserved.
//

package com.ffusion.banksim.adapter;


import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Calendar;
import java.util.HashMap;

import com.ffusion.banksim.db.DBConnection;
import com.ffusion.banksim.db.util.BSUtil;
import com.ffusion.banksim.interfaces.BSConstants;
import com.ffusion.banksim.interfaces.BSException;
import com.ffusion.beans.common.Currency;
import com.ffusion.beans.lockbox.LockboxAccount;
import com.ffusion.beans.lockbox.LockboxSummaries;
import com.ffusion.beans.lockbox.LockboxSummary;
import com.ffusion.util.db.DBUtil;

public class BSLockboxSummary {
	
    private static final String SQL_ADD_LOCKBOXSUMMARY = "INSERT INTO BS_LockboxSummary( AccountID, DataDate, DataSource, TotalCredits, TotalDebits, TotalNumCredits, TotalNumDebits, " +
    					"ImmediateFloat, OneDayFloat, TwoDayFloat, ExtendABeanXMLID, Extra, BAIFileIdentifier ) VALUES( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ";

    private static final String SQL_UPD_LOCKBOXSUMMARY = "UPDATE BS_LockboxSummary SET AccountID=?, DataDate=?, DataSource=?, TotalCredits=?, TotalDebits=?, TotalNumCredits=?, TotalNumDebits=?, " +
    					"ImmediateFloat=?, OneDayFloat=?, TwoDayFloat=?, BAIFileIdentifier=? " +
					"WHERE AccountID=? AND DataDate=? ";

    private static String SQL_GET_LBSUMMARIES1 = "SELECT b.DataDate, b.TotalCredits, b.TotalDebits, b.TotalNumCredits, b.TotalNumDebits, " +
    					"b.ImmediateFloat, b.OneDayFloat, b.TwoDayFloat, b.ExtendABeanXMLID FROM BS_Account a, BS_LockboxSummary b WHERE b.AccountID= a.AccountID " +
					"AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate>=? AND b.DataDate<=? ORDER BY b.DataDate";

    private static String SQL_GET_LBSUMMARIES2 = "SELECT b.DataDate, b.TotalCredits, b.TotalDebits, b.TotalNumCredits, b.TotalNumDebits, " +
    					"b.ImmediateFloat, b.OneDayFloat, b.TwoDayFloat, b.ExtendABeanXMLID FROM BS_Account a, BS_LockboxSummary b WHERE b.AccountID= a.AccountID " +
					"AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate>=? ORDER BY b.DataDate";

    private static String SQL_GET_LBSUMMARIES3 = "SELECT b.DataDate, b.TotalCredits, b.TotalDebits, b.TotalNumCredits, b.TotalNumDebits, " +
    					"b.ImmediateFloat, b.OneDayFloat, b.TwoDayFloat, b.ExtendABeanXMLID FROM BS_Account a, BS_LockboxSummary b WHERE b.AccountID= a.AccountID " +
					"AND a.AccountID=? AND a.RoutingNum=? AND  b.DataDate<=? ORDER BY b.DataDate";

    private static String SQL_GET_LBSUMMARIES4 = "SELECT b.DataDate, b.TotalCredits, b.TotalDebits, b.TotalNumCredits, b.TotalNumDebits, " +
    					"b.ImmediateFloat, b.OneDayFloat, b.TwoDayFloat, b.ExtendABeanXMLID FROM BS_Account a, BS_LockboxSummary b WHERE b.AccountID= a.AccountID " +
					"AND a.AccountID=? AND a.RoutingNum=? ORDER BY b.DataDate";

    private static String SQL_GET_LBSUMMARIES5 = "SELECT b.DataDate, b.TotalCredits, b.TotalDebits, b.TotalNumCredits, b.TotalNumDebits, " +
    					"b.ImmediateFloat, b.OneDayFloat, b.TwoDayFloat, b.ExtendABeanXMLID FROM BS_Account a, BS_LockboxSummary b WHERE b.AccountID= a.AccountID " +
					"AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate=?";


    /**
	Adds summary information into the repository for a specified lockbox
	@param lockbox: the lockbox for which summary information has been supplied
	@param summary: the summary information to be added
	@param dataSource: the source of the data
	@param connection: database connection object
    */
    public static void add(  LockboxSummary incomingSummary, int dataSource, DBConnection connection, HashMap extra ) throws BSException
    {
	PreparedStatement stmt = null;
	LockboxAccount lockbox = incomingSummary.getLockboxAccount();

	try {
	    // get the BAI filename and timestamp
	    String BAIFileIdentifier = null;
	    if( extra != null ) {
	    	BAIFileIdentifier = (String)extra.get( BSConstants.BAI_FILE_IDENTIFIER );
	    }
	    
	    String accountID = lockbox.getAccountID();

	    // if no summary exists for this date and account, do a straightforward add; otherwise,
	    // update the existing summary in the database
	    LockboxSummary existingSummary = getLockboxSummary( lockbox, incomingSummary.getSummaryDate(), connection );
	    LockboxSummary summary = null;

	    if( existingSummary == null ) {
		stmt = connection.prepareStatement( connection, SQL_ADD_LOCKBOXSUMMARY );
		summary = incomingSummary;
	    } else {
		stmt = connection.prepareStatement( connection, SQL_UPD_LOCKBOXSUMMARY );
		summary = existingSummary;

		Currency curr = null;
		int iVal = -1;

		curr = incomingSummary.getTotalLockboxCredits();
		if( curr != null ) {
		    summary.setTotalLockboxCredits( curr );
		}

		curr = incomingSummary.getTotalLockboxDebits();
		if( curr != null ) {
		    summary.setTotalLockboxDebits( curr );
		}

		iVal = incomingSummary.getTotalNumLockboxCredits();
		if( iVal != -1 ) {
		    summary.setTotalNumLockboxCredits( iVal );
		}

		iVal = incomingSummary.getTotalNumLockboxDebits();
		if( iVal != -1 ) {
		    summary.setTotalNumLockboxDebits( iVal );
		}

		curr = incomingSummary.getImmediateFloat();
		if( curr != null ) {
		    summary.setImmediateFloat( curr );
		}

		curr = incomingSummary.getOneDayFloat();
		if( curr != null ) {
		    summary.setOneDayFloat( curr );
		}

		curr = incomingSummary.getTwoDayFloat();
		if( curr != null ) {
		    summary.setTwoDayFloat( curr );
		}
	    }
	    
	    stmt.setString(  1, accountID );
	    BSUtil.fillTimestampColumn(connection,  stmt,  2,  summary.getSummaryDate() );
	    stmt.setInt( 3, dataSource );
	    BSUtil.fillCurrencyColumn( stmt, 4, summary.getTotalLockboxCredits() );
	    BSUtil.fillCurrencyColumn( stmt, 5, summary.getTotalLockboxDebits() );
	    stmt.setInt( 6, summary.getTotalNumLockboxCredits() );
	    stmt.setInt( 7, summary.getTotalNumLockboxDebits() );
	    BSUtil.fillCurrencyColumn( stmt, 8, summary.getImmediateFloat() );
	    BSUtil.fillCurrencyColumn( stmt, 9, summary.getOneDayFloat() );
	    BSUtil.fillCurrencyColumn( stmt, 10, summary.getTwoDayFloat() );

	    if( existingSummary == null ) {
		stmt.setLong( 11, BSExtendABeanXML.addExtendABeanXML( connection, summary.getExtendABeanXML() ) );
		stmt.setString( 12, null );
		stmt.setString( 13, BAIFileIdentifier );
		DBConnection.executeUpdate( stmt, SQL_ADD_LOCKBOXSUMMARY );
	    } else {
		stmt.setString( 11, BAIFileIdentifier );
		stmt.setString( 12, accountID );
		BSUtil.fillTimestampColumn(connection,  stmt, 13, summary.getSummaryDate() );
		DBConnection.executeUpdate( stmt, SQL_UPD_LOCKBOXSUMMARY );
	    }

	} catch ( Exception e ) {
	    throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if ( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	}
	
    }
    
    /*
	 Retrieve the summary information for a specified LockboxAccount for a date range
	 @param lockbox: the LockboxAccount for which we want the summaries
	 @param startDate: the start date of summaries to get or null if no end date
	 @param endDate: the end date of summaries to get or null if no end date
    */
    public static LockboxSummaries getLockboxSummaries( LockboxAccount lockbox,
										 Calendar startDate,
										 Calendar endDate,
										 DBConnection connection,
										 HashMap extra )
										 throws BSException
    {
	PreparedStatement stmt = null;
	LockboxSummaries sums = null;
	
	ResultSet rs = null;
	try {
	    
	    if( startDate != null ) {
		if( endDate != null ) {
		    stmt = connection.prepareStatement( connection, SQL_GET_LBSUMMARIES1 );
		    stmt.setString(1, lockbox.getAccountID() );
		    stmt.setString(2, lockbox.getRoutingNumber() );
		    BSUtil.fillTimestampColumn(connection,  stmt,  3,  startDate );
		    BSUtil.fillTimestampColumn(connection,  stmt,  4,  endDate );
		    rs = DBConnection.executeQuery( stmt, SQL_GET_LBSUMMARIES1 );
		} else {
		    stmt = connection.prepareStatement( connection, SQL_GET_LBSUMMARIES2 );
		    stmt.setString(1, lockbox.getAccountID() );
		    stmt.setString(2, lockbox.getRoutingNumber() );
		    BSUtil.fillTimestampColumn(connection,  stmt,  3,  startDate );
		    rs = DBConnection.executeQuery( stmt, SQL_GET_LBSUMMARIES2 );
		}
	    } else if( endDate != null ) {
		stmt = connection.prepareStatement( connection, SQL_GET_LBSUMMARIES3 );
		stmt.setString(1, lockbox.getAccountID() );
		stmt.setString(2, lockbox.getRoutingNumber() );
		BSUtil.fillTimestampColumn(connection,  stmt,  3,  endDate );
		rs = DBConnection.executeQuery( stmt, SQL_GET_LBSUMMARIES3 );
	    } else {
		stmt = connection.prepareStatement( connection, SQL_GET_LBSUMMARIES4 );
		stmt.setString(1, lockbox.getAccountID() );
		stmt.setString(2, lockbox.getRoutingNumber() );
		rs = DBConnection.executeQuery( stmt, SQL_GET_LBSUMMARIES4 );
	    }
	    
	    sums = new LockboxSummaries( );
	    LockboxSummary currentSummary = null;
	    while( rs.next() ) {
		currentSummary = createSummary(connection, lockbox, rs );
		sums.add( currentSummary );
	    }
	    DBUtil.closeResultSet( rs );
	    return sums;

	} catch ( Exception e ) {
	    throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	    
	}
    }

    /*
	 Retrieve the summary information for a specified LockboxAccount for a date
	 @param lockbox: the LockboxAccount for which we want the summaries
	 @param date: the date of summary to get
	 @param connection: database connection to use
    */
    private static LockboxSummary getLockboxSummary( LockboxAccount lockbox,
									       Calendar date,
									       DBConnection connection )
	throws BSException
    {
	PreparedStatement stmt = null;
	ResultSet rs = null;
	try {
	    stmt = connection.prepareStatement( connection, SQL_GET_LBSUMMARIES5 );
	    stmt.setString( 1, lockbox.getAccountID() );
	    stmt.setString( 2, lockbox.getRoutingNumber() );
	    BSUtil.fillTimestampColumn(connection,  stmt,  3,  date );
	    rs = DBConnection.executeQuery( stmt, SQL_GET_LBSUMMARIES5 );
	    
	    // only one record per account per day
	    LockboxSummary currentSummary = null;
	    if( rs.next() ) {
		currentSummary = createSummary(connection, lockbox, rs );
	    }
	    DBUtil.closeResultSet( rs );
	    return currentSummary;

	} catch ( Exception e ) {
	    throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	}
    }

    private static LockboxSummary createSummary(DBConnection conn, LockboxAccount lockbox, ResultSet rs )
    	throws Exception
    {
	LockboxSummary currentSummary = new LockboxSummary();

	currentSummary.setLockboxAccount( lockbox );
	currentSummary.setSummaryDate( BSUtil.getTimestampColumn( rs.getTimestamp( 1 ) ) );
	currentSummary.setTotalLockboxCredits( BSUtil.getCurrencyColumn( rs.getBigDecimal( 2 ) ) );
	currentSummary.setTotalLockboxDebits( BSUtil.getCurrencyColumn( rs.getBigDecimal( 3 ) ) );
	currentSummary.setTotalNumLockboxCredits( rs.getInt( 4 ) );
	currentSummary.setTotalNumLockboxDebits( rs.getInt( 5 ) );
	currentSummary.setImmediateFloat( BSUtil.getCurrencyColumn( rs.getBigDecimal( 6 ) ) );
	currentSummary.setOneDayFloat( BSUtil.getCurrencyColumn( rs.getBigDecimal( 7 ) ) );
	currentSummary.setTwoDayFloat( BSUtil.getCurrencyColumn( rs.getBigDecimal( 8 ) ) );
	BSExtendABeanXML.fillExtendABean( conn,currentSummary, rs, 9 );

	return currentSummary;
    }
}

