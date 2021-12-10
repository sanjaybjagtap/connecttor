//
// BSLBTransactions.java
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
import com.ffusion.beans.lockbox.LockboxTransaction;
import com.ffusion.beans.lockbox.LockboxTransactions;
import com.ffusion.dataconsolidator.constants.DCConstants;
import com.ffusion.util.MapUtil;
import com.ffusion.util.db.DBUtil;

public class BSLBTransactions {
	

    private static final String SQL_ADD_LBTRANSACTION = "INSERT INTO BS_LBTransactions( AccountID, TransactionIndex, LockboxNumber, DataDate, " +
    							"DataSource, TransID, TransTypeID, Description, Amount, NumRejectedChecks, RejectedAmount, " +
							"ImmedAvailAmount, OneDayAvailAmount, MoreOneDayAvailAm, ValueDateTime, BankRefNum, CustRefNum, " +
							"ExtendABeanXMLID, Extra, BAIFileIdentifier ) VALUES( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ";

    private static final String SQL_GET_LBTRANS1 = "SELECT b.TransactionIndex, b.LockboxNumber, b.DataDate, b.TransID, b.TransTypeID, b.Description, b.Amount, " +
    						"b.NumRejectedChecks, b.RejectedAmount, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBTransactions b WHERE " +
						"a.AccountID=b.AccountID AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate>=? AND b.DataDate<=? ORDER BY b.TransactionIndex";

    private static final String SQL_GET_LBTRANS2 = "SELECT b.TransactionIndex, b.LockboxNumber, b.DataDate, b.TransID, b.TransTypeID, b.Description, b.Amount, " +
    						"b.NumRejectedChecks, b.RejectedAmount, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBTransactions b WHERE " +
						"a.AccountID=b.AccountID AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate>=? ORDER BY b.TransactionIndex";

    private static final String SQL_GET_LBTRANS3 = "SELECT b.TransactionIndex, b.LockboxNumber, b.DataDate, b.TransID, b.TransTypeID, b.Description, b.Amount, " +
    						"b.NumRejectedChecks, b.RejectedAmount, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBTransactions b WHERE " +
						"a.AccountID=b.AccountID AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate<=? ORDER BY b.TransactionIndex";

    private static final String SQL_GET_LBTRANS4 = "SELECT b.TransactionIndex, b.LockboxNumber, b.DataDate, b.TransID, b.TransTypeID, b.Description, b.Amount, " +
    						"b.NumRejectedChecks, b.RejectedAmount, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBTransactions b WHERE " +
						"a.AccountID=b.AccountID AND a.AccountID=? AND a.RoutingNum=? ORDER BY b.TransactionIndex";

    // the following transactions are used to facilitate paging of the transactions
    private static final String SQL_GET_PAGED_LBTRANS1 = 
	"SELECT b.TransactionIndex, b.LockboxNumber, b.DataDate, b.TransID, b.TransTypeID, b.Description, b.Amount, " +
	"b.NumRejectedChecks, b.RejectedAmount, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, " +
	"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBTransactions b WHERE " +
	"a.AccountID=b.AccountID AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate>=? AND b.DataDate<=? AND " +
	"b.TransactionIndex>=? AND b.TransactionIndex<=? ORDER BY b.TransactionIndex";
    
    private static final String SQL_GET_PAGED_LBTRANS2 = 
	"SELECT b.TransactionIndex, b.LockboxNumber, b.DataDate, b.TransID, b.TransTypeID, b.Description, b.Amount, " +
	"b.NumRejectedChecks, b.RejectedAmount, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, " +
	"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBTransactions b WHERE " +
	"a.AccountID=b.AccountID AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate>=? AND b.TransactionIndex>=? " +
	"AND b.TransactionIndex<=? ORDER BY b.TransactionIndex";

    private static final String SQL_GET_PAGED_LBTRANS3 = 
	"SELECT b.TransactionIndex, b.LockboxNumber, b.DataDate, b.TransID, b.TransTypeID, b.Description, b.Amount, " +
	"b.NumRejectedChecks, b.RejectedAmount, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, " +
	"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBTransactions b WHERE " +
	"a.AccountID=b.AccountID AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate<=? AND b.TransactionIndex>=? " +
	"AND b.TransactionIndex<=? ORDER BY b.TransactionIndex";
    
    private static final String SQL_GET_PAGED_LBTRANS4 = 
	"SELECT b.TransactionIndex, b.LockboxNumber, b.DataDate, b.TransID, b.TransTypeID, b.Description, b.Amount, " +
	"b.NumRejectedChecks, b.RejectedAmount, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, " +
	"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBTransactions b WHERE " +
	"a.AccountID=b.AccountID AND a.AccountID=? AND a.RoutingNum=? AND b.TransactionIndex>=? " +
	"AND b.TransactionIndex<=? ORDER BY b.TransactionIndex";
    
    private static final String SQL_GET_LBTRANS_MAX_INDEX_1 = 
	"SELECT min( b.TransactionIndex ), max( b.TransactionIndex ) FROM BS_Account a, BS_LBTransactions b WHERE " +
	"a.AccountID=b.AccountID AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate>=? AND b.DataDate<=?";

    private static final String SQL_GET_LBTRANS_MAX_INDEX_2 = 
	"SELECT min( b.TransactionIndex ), max( b.TransactionIndex ) FROM BS_Account a, BS_LBTransactions b WHERE " +
	"a.AccountID=b.AccountID AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate>=?";

    private static final String SQL_GET_LBTRANS_MAX_INDEX_3 = 
	"SELECT min( b.TransactionIndex ), max( b.TransactionIndex ) FROM BS_Account a, BS_LBTransactions b WHERE " +
	"a.AccountID=b.AccountID AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate<=?";

    private static final String SQL_GET_LBTRANS_MAX_INDEX_4 = 
	"SELECT min( b.TransactionIndex ), max( b.TransactionIndex ) FROM BS_Account a, BS_LBTransactions b WHERE " +
	"a.AccountID=b.AccountID AND a.AccountID=? AND a.RoutingNum=?";


    private static final String SQL_GET_PREVIOUSTRANS = 
	"SELECT b.TransactionIndex, b.LockboxNumber, b.DataDate, b.TransID, b.TransTypeID, b.Description, b.Amount, " +
	"b.NumRejectedChecks, b.RejectedAmount, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, " +
	"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBTransactions b WHERE " +
	"a.AccountID=b.AccountID AND a.AccountID=? AND a.RoutingNum=? AND b.TransactionIndex>=? " +
	"AND b.TransactionIndex<=? ORDER BY b.TransactionIndex ";

    private static final String SQL_GET_RECENTTRANS = 
	"SELECT b.TransactionIndex, b.LockboxNumber, b.DataDate, b.TransID, b.TransTypeID, b.Description, b.Amount, " +
	"b.NumRejectedChecks, b.RejectedAmount, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, " +
	"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBTransactions b WHERE " +
	"a.AccountID=b.AccountID AND a.AccountID=? AND a.RoutingNum=? AND b.TransactionIndex>=? " +
	"AND b.TransactionIndex<=? ORDER BY b.TransactionIndex";

    private static final String SQL_GET_NEXTTRANS = 
	"SELECT b.TransactionIndex, b.LockboxNumber, b.DataDate, b.TransID, b.TransTypeID, b.Description, b.Amount, " +
	"b.NumRejectedChecks, b.RejectedAmount, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, " +
	"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBTransactions b WHERE " +
	"a.AccountID=b.AccountID AND a.AccountID=? AND a.RoutingNum=? AND b.TransactionIndex>=? " +
	"AND b.TransactionIndex<=? ORDER BY b.TransactionIndex ";
    


    // Operations on LockboxAccount Transactions
    
    /*
    
	 Adds transactions into the repository for a specified lockbox
	 @param lockbox: the LockboxAccount for which we information has been supplied
	 @param trans: a list of LockboxTransaction objects to be added
	 @param dataSource: the source of the data
	 @param connection: Database connection object
    */
    public static void addTransactions( com.ffusion.beans.lockbox.LockboxAccount lockbox,
		 		 	LockboxTransactions transactions,
		 		 	int dataSource,
		 		 	DBConnection connection,
					HashMap extra )
		 		 	throws BSException
    {
	PreparedStatement stmt = null;
	LockboxTransaction trans = null;
	long index = 0;
	try {
	    // get the BAI filename and timestamp
	    String BAIFileIdentifier = null;
	    if( extra != null ) {
	    	BAIFileIdentifier = (String)extra.get( BSConstants.BAI_FILE_IDENTIFIER );
	    }
	    
	    stmt = connection.prepareStatement( connection, SQL_ADD_LBTRANSACTION );
	    String accountID = lockbox.getAccountID();
	    for( int i = 0; i < transactions.size(); i++ ) {
		trans = (LockboxTransaction)transactions.get( i );
	    
		if( trans.getTransactionIndex() != 0 ) {
		    throw new BSException( BSException.BSE_INVALID_TRANSACTION_INDEX,
					   com.ffusion.banksim.db.MessageText.getMessage( com.ffusion.banksim.db.IBSErrConstants.ERR_INVALID_TRANSACTION_INDEX ) );
		} else {
		    index = BSRecordCounter.getNextIndex( connection, BSRecordCounter.TYPE_LOCKBOX, accountID, BSRecordCounter.LOCKBOX_TRANSACTION_INDEX );
		}

		// get the ExtendABean ID
		long extendABeanID = BSExtendABeanXML.addExtendABeanXML( connection, trans.getExtendABeanXML() );

		stmt.setString( 1, accountID );
		stmt.setLong( 2, index );
		stmt.setString( 3, trans.getLockboxNumber() );
		BSUtil.fillTimestampColumn(connection,  stmt,  4,  trans.getProcessingDate() );
		stmt.setInt( 5, dataSource );
		stmt.setInt( 6, trans.getTransactionID() );
		stmt.setInt( 7, trans.getTransactionType() );
		stmt.setString( 8, trans.getDescription() );
		BSUtil.fillCurrencyColumn( stmt, 9, trans.getAmount() );
		stmt.setInt( 10, trans.getNumRejectedChecks() );
		BSUtil.fillCurrencyColumn( stmt, 11, trans.getRejectedAmount() );
		BSUtil.fillCurrencyColumn( stmt, 12, trans.getImmediateFloat() );
		BSUtil.fillCurrencyColumn( stmt, 13, trans.getOneDayFloat() );
		BSUtil.fillCurrencyColumn( stmt, 14, trans.getTwoDayFloat() );
		BSUtil.fillTimestampColumn(connection,  stmt,  15, trans.getValueDateTime() );
		stmt.setString( 16, trans.getBankReferenceNumber() );
		stmt.setString( 17, trans.getCustomerReferenceNumber() );
		stmt.setLong( 18, extendABeanID );
		stmt.setString( 19, null );
		stmt.setString( 20, BAIFileIdentifier );
	
		DBConnection.executeUpdate( stmt, SQL_ADD_LBTRANSACTION );
	    }
	} catch ( Exception e ) {
	    throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if ( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	}

    }

    /**
    	Fills the transactionbean using the information from the resultset
	@param lockbox: account that the transactions are associated with
	@param currentTrans: transaction bean to fill
	@param rs: resultset containing information to fill the transaction bean
    */
    private static void loadTransaction(DBConnection conn, com.ffusion.beans.lockbox.LockboxAccount lockbox,
    					 LockboxTransaction currentTrans,
					 ResultSet rs )
					 throws Exception
    {
	currentTrans.setAccountID( lockbox.getAccountID() );
	currentTrans.setAccountNumber( lockbox.getAccountNumber() );
	currentTrans.setBankID( lockbox.getBankID()  );
	currentTrans.setTransactionIndex( rs.getLong( 1 ) );
	currentTrans.setLockboxNumber( rs.getString(2) );
	currentTrans.setProcessingDate( BSUtil.getTimestampColumn( rs.getTimestamp( 3 ) ) );
	currentTrans.setTransactionID( rs.getInt( 4 ) );
	currentTrans.setTransactionType( rs.getInt( 5 ) );
	currentTrans.setDescription( rs.getString( 6 ) );
	currentTrans.setAmount( BSUtil.getCurrencyColumn( rs.getBigDecimal( 7 ) ) );
	currentTrans.setNumRejectedChecks( rs.getInt( 8 ) );
	currentTrans.setRejectedAmount( BSUtil.getCurrencyColumn( rs.getBigDecimal( 9 ) ) );
	currentTrans.setImmediateFloat( BSUtil.getCurrencyColumn( rs.getBigDecimal( 10 ) ) );
	currentTrans.setOneDayFloat( BSUtil.getCurrencyColumn( rs.getBigDecimal( 11 ) ) );
	currentTrans.setTwoDayFloat( BSUtil.getCurrencyColumn( rs.getBigDecimal( 12 ) ) );
	currentTrans.setValueDateTime( BSUtil.getTimestampColumn( rs.getTimestamp( 13 ) ) );
	currentTrans.setBankReferenceNumber( rs.getString( 14 ) );
	currentTrans.setCustomerReferenceNumber( rs.getString( 15 ) );
	BSExtendABeanXML.fillExtendABean( conn ,currentTrans, rs, 16 );
    }
    
    
    /*
	 Retrieves a list of transactions for a specified LockboxAccount between a start date to an end date
	 @param lockbox: the LockboxAccount for which we want the transactions
	 @param startDate: the start date of transactions to get or null if no start date
	 @param endDate: the end date of transactions to get or null if no end date
	 @return a list of LockboxTransaction beans
    */
    public static LockboxTransactions getTransactions( com.ffusion.beans.lockbox.LockboxAccount lockbox,
											  Calendar startDate,
											  Calendar endDate,
											  DBConnection connection,
											  HashMap extra )
											  throws BSException
    {
	PreparedStatement stmt = null;
	LockboxTransactions trans = null;
	ResultSet rs = null;
	
	try {
	    
	    if( startDate != null ) {
		if( endDate != null ) {
		    stmt = connection.prepareStatement( connection, SQL_GET_LBTRANS1 );
		    stmt.setString(1, lockbox.getAccountID() );
		    stmt.setString(2, lockbox.getRoutingNumber() );
		    BSUtil.fillTimestampColumn(connection,  stmt,  3,  startDate );
		    BSUtil.fillTimestampColumn(connection,  stmt,  4,  endDate );
		    rs = DBConnection.executeQuery( stmt, SQL_GET_LBTRANS1 );
		} else {
		    stmt = connection.prepareStatement( connection, SQL_GET_LBTRANS2 );
		    stmt.setString(1, lockbox.getAccountID() );
		    stmt.setString(2, lockbox.getRoutingNumber() );
		    BSUtil.fillTimestampColumn(connection,  stmt,  3,  startDate );
		    rs = DBConnection.executeQuery( stmt, SQL_GET_LBTRANS2 );
		}
	    } else if( endDate != null ) {
		stmt = connection.prepareStatement( connection, SQL_GET_LBTRANS3 );
		stmt.setString(1, lockbox.getAccountID() );
		stmt.setString(2, lockbox.getRoutingNumber() );
		BSUtil.fillTimestampColumn(connection,  stmt,  3,  endDate );
		rs = DBConnection.executeQuery( stmt, SQL_GET_LBTRANS3 );
	    } else {
		stmt = connection.prepareStatement( connection, SQL_GET_LBTRANS4 );
		stmt.setString(1, lockbox.getAccountID() );
		stmt.setString(2, lockbox.getRoutingNumber() );
		rs = DBConnection.executeQuery( stmt, SQL_GET_LBTRANS4 );
	    }

	    
	    trans = new LockboxTransactions();
	    LockboxTransaction currentTrans = null;
	    while( rs.next() ) {
		currentTrans = trans.create();
		loadTransaction( connection,lockbox, currentTrans, rs );
	    }
		DBUtil.closeResultSet( rs );
	    return trans;

	} catch ( Exception e ) {
	    throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	}
    }
    

    /**
	Retrieves a list of transactions for a specified lockbox between a
	start date to an end date, to a maximum of PAGESIZE of them
	@param lockbox: the lockbox for which we want the transactions
	@param startDate: the start date of transactions to get or null if no start date
	@param endDate: the end date of transactions to get or null if no end date
	@return a list of LockboxTransaction beans
    */
    public static LockboxTransactions getPagedTransactions(
								    com.ffusion.beans.lockbox.LockboxAccount lockbox,
								    Calendar startDate,
								    Calendar endDate,
								    DBConnection connection,
								    HashMap extra )
								    throws BSException
    {
	PreparedStatement stmt = null;
	LockboxTransactions trans = null;
	ResultSet rs = null;
	
	try {
	    int pageSize = MapUtil.getIntValue( extra, BSConstants.PAGE_SIZE, BSConstants.PAGESIZE );
	    long index[] = getMaximumTransactionIndex( connection, lockbox, startDate, endDate );
	    long minIndex = index[0];
	    long maxIndex = index[1];

	    if( startDate != null ) {
		if( endDate != null ) {
		    stmt = connection.prepareStatement( connection, SQL_GET_PAGED_LBTRANS1 );
		    stmt.setString( 1, lockbox.getAccountID() );
		    stmt.setString( 2, lockbox.getRoutingNumber() );
		    BSUtil.fillTimestampColumn(connection,  stmt,  3,  startDate );
		    BSUtil.fillTimestampColumn(connection,  stmt,  4,  endDate );
		    stmt.setLong( 5, minIndex );
		    stmt.setLong( 6, minIndex + pageSize - 1 );
		    rs = DBConnection.executeQuery( stmt, SQL_GET_PAGED_LBTRANS1 );
		} else {
		    stmt = connection.prepareStatement( connection, SQL_GET_PAGED_LBTRANS2 );
		    stmt.setString( 1, lockbox.getAccountID() );
		    stmt.setString( 2, lockbox.getRoutingNumber() );
		    BSUtil.fillTimestampColumn(connection,  stmt,  3,  startDate );
		    stmt.setLong( 4, minIndex );
		    stmt.setLong( 5, minIndex + pageSize - 1 );
		    rs = DBConnection.executeQuery( stmt, SQL_GET_PAGED_LBTRANS2 );
		}
	    } else if( endDate != null ) {
		stmt = connection.prepareStatement( connection, SQL_GET_PAGED_LBTRANS3 );
		stmt.setString( 1, lockbox.getAccountID() );
		stmt.setString( 2, lockbox.getRoutingNumber() );
		BSUtil.fillTimestampColumn(connection,  stmt,  3,  endDate );
		stmt.setLong( 4, minIndex );
		stmt.setLong( 5, minIndex + pageSize - 1 );
		rs = DBConnection.executeQuery( stmt, SQL_GET_PAGED_LBTRANS3 );
	    } else {
		stmt = connection.prepareStatement( connection, SQL_GET_PAGED_LBTRANS4 );
		stmt.setString( 1, lockbox.getAccountID() );
		stmt.setString( 2, lockbox.getRoutingNumber() );
		stmt.setLong( 3, minIndex );
		stmt.setLong( 4, minIndex + pageSize - 1 );
		rs = DBConnection.executeQuery( stmt, SQL_GET_PAGED_LBTRANS4 );
	    }

	    
	    trans = new LockboxTransactions();
	    LockboxTransaction currentTrans = null;

	    while( rs.next() ) {
		currentTrans = trans.create();
		loadTransaction(connection, lockbox, currentTrans, rs );
	    }

	    extra.put( DCConstants.MINIMUM_TRANSACTION_INDEX_FOR_RANGE, new Long( minIndex ) );
	    extra.put( DCConstants.MAXIMUM_TRANSACTION_INDEX_FOR_RANGE, new Long( maxIndex ) );

		DBUtil.closeResultSet( rs );
	    return trans;

	} catch ( Exception e ) {
	    throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	}


    }
    /*
	 Retrieves the most recent transactions for the specified lockbox
	 @param lockbox: the LockboxAccount for which we want the transactions
	 @return a list of LockboxTransaction beans containing the transactions (at most PAGESIZE of them)
    */
    public static LockboxTransactions getRecentTransactions( com.ffusion.beans.lockbox.LockboxAccount lockbox,
    											   DBConnection connection,
											      HashMap extra )
											      throws BSException
    {
	PreparedStatement stmt = null;
	LockboxTransactions trans = new LockboxTransactions( );
	
	try {
	    int pageSize = MapUtil.getIntValue( extra, BSConstants.PAGE_SIZE, BSConstants.PAGESIZE );
	    long index = BSRecordCounter.getIndex( connection, lockbox.getAccountID(), lockbox.getRoutingNumber(),
						   BSRecordCounter.TYPE_LOCKBOX,
						   BSRecordCounter.LOCKBOX_TRANSACTION_INDEX );
	    
	    if ( index != -1 ) {	    
		stmt = connection.prepareStatement( connection, SQL_GET_RECENTTRANS );
		stmt.setString( 1, lockbox.getAccountID() );
		stmt.setString( 2, lockbox.getRoutingNumber() );
		stmt.setLong( 3, index - pageSize + 1 );
		stmt.setLong( 4, index );
		ResultSet rs = DBConnection.executeQuery( stmt, SQL_GET_RECENTTRANS );

		LockboxTransaction currentTrans = null;

		while( rs.next() ) {
		    currentTrans = trans.create();
		    loadTransaction( connection,lockbox, currentTrans, rs );
		}
		DBUtil.closeResultSet( rs );
	    }
	    return trans;

	} catch ( Exception e ) {
	    throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	}
    }
    
    /*
	 Retrieves the next batch of PAGESIZE transactions for the specified lockbox
	 @param lockbox: the LockboxAccount for which we want the transactions
	 @param nextIndex: the next index of information to retrieve
	 @return a list of LockboxTransaction beans containing the transactions (at most PAGE_SIZE of them)
    */
    public static LockboxTransactions getNextTransactions( com.ffusion.beans.lockbox.LockboxAccount lockbox,
											      long nextIndex,
											      DBConnection connection,
											      HashMap extra )
											      throws BSException
    {
	PreparedStatement stmt = null;
	LockboxTransactions trans = null;
	
	try {
	    int pageSize = MapUtil.getIntValue( extra, BSConstants.PAGE_SIZE, BSConstants.PAGESIZE );

	    
	    stmt = connection.prepareStatement( connection, SQL_GET_NEXTTRANS );
	    stmt.setString( 1, lockbox.getAccountID() );
	    stmt.setString( 2, lockbox.getRoutingNumber() );
	    stmt.setLong( 3, nextIndex );
	    stmt.setLong( 4, nextIndex + pageSize - 1);

	    ResultSet rs = DBConnection.executeQuery( stmt, SQL_GET_NEXTTRANS );
	    
	    trans = new LockboxTransactions( );
	    LockboxTransaction currentTrans = null;

	    while( rs.next() ) {
		currentTrans = trans.create();
		loadTransaction( connection,lockbox, currentTrans, rs );
	    }
	    DBUtil.closeResultSet( rs );
	    return trans;

	} catch ( Exception e ) {
	    throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    } 
	}
    }

    /**
	 Retrieves the previous batch of PAGESIZE transactions for the specified lockbox
	 @param lockbox: the LockboxAccount for which we want to retrieve transactions
	 @param lastIndex: the last index of information to retrieve
	 @return a list of LockboxTransaction beans containing the transactions (at most PAGE_SIZE of them)
    */
    public static LockboxTransactions getPreviousTransactions( com.ffusion.beans.lockbox.LockboxAccount lockbox,
												  long lastIndex,
												  DBConnection connection,
												  HashMap extra )
												  throws BSException
    {
	PreparedStatement stmt = null;
	LockboxTransactions trans = null;
	
	try {
	    int pageSize = MapUtil.getIntValue( extra, BSConstants.PAGE_SIZE, BSConstants.PAGESIZE );

	    
	    stmt = connection.prepareStatement( connection, SQL_GET_PREVIOUSTRANS );
	    stmt.setString( 1, lockbox.getAccountID() );
	    stmt.setString( 2, lockbox.getRoutingNumber() );
	    stmt.setLong( 3, lastIndex - pageSize + 1 );
	    stmt.setLong( 4, lastIndex );
	    ResultSet rs = DBConnection.executeQuery( stmt, SQL_GET_PREVIOUSTRANS );
	    
	    trans = new LockboxTransactions( );
	    LockboxTransaction currentTrans = null;

	    while( rs.next() ) {
		currentTrans = trans.create();
		loadTransaction(connection, lockbox, currentTrans, rs );
	    }
		DBUtil.closeResultSet( rs );
	    return trans;

	} catch ( Exception e ) {
	    throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	}
    }

    private static long[] getMaximumTransactionIndex( DBConnection connection, 
						   com.ffusion.beans.lockbox.LockboxAccount lockbox,
						   Calendar startDate, Calendar endDate ) throws Exception {
	ResultSet rs = null;
	PreparedStatement stmt = null;
	
	if( startDate != null ) {
	    if( endDate != null ) {
		stmt = connection.prepareStatement( connection, SQL_GET_LBTRANS_MAX_INDEX_1 );
		stmt.setString(1, lockbox.getAccountID() );
		stmt.setString(2, lockbox.getRoutingNumber() );
		BSUtil.fillTimestampColumn(connection,  stmt,  3,  startDate );
		BSUtil.fillTimestampColumn(connection,  stmt,  4,  endDate );
		rs = DBConnection.executeQuery( stmt, SQL_GET_LBTRANS_MAX_INDEX_1 );
	    } else {
		stmt = connection.prepareStatement( connection, SQL_GET_LBTRANS_MAX_INDEX_2 );
		stmt.setString(1, lockbox.getAccountID() );
		stmt.setString(2, lockbox.getRoutingNumber() );
		BSUtil.fillTimestampColumn(connection,  stmt,  3,  startDate );
		rs = DBConnection.executeQuery( stmt, SQL_GET_LBTRANS_MAX_INDEX_2 );
	    }
	} else if( endDate != null ) {
	    stmt = connection.prepareStatement( connection, SQL_GET_LBTRANS_MAX_INDEX_3 );
	    stmt.setString(1, lockbox.getAccountID() );
	    stmt.setString(2, lockbox.getRoutingNumber() );
	    BSUtil.fillTimestampColumn(connection,  stmt,  3,  endDate );
	    rs = DBConnection.executeQuery( stmt, SQL_GET_LBTRANS_MAX_INDEX_3 );
	} else {
	    stmt = connection.prepareStatement( connection, SQL_GET_LBTRANS_MAX_INDEX_4 );
	    stmt.setString(1, lockbox.getAccountID() );
	    stmt.setString(2, lockbox.getRoutingNumber() );
	    rs = DBConnection.executeQuery( stmt, SQL_GET_LBTRANS_MAX_INDEX_4 );
	}

	long index[] = new long[2];
	if ( rs.next() == false ) {
	    throw new BSException( BSException.BSE_INVALID_TRANSACTION_INDEX, 
				   "Unable to retrieve the maximum transaction index for the given criteria" );
	} else {
	    index[0] = rs.getLong( 1 );
	    index[1] = rs.getLong( 2 );
	}
	return index;
    }

}

