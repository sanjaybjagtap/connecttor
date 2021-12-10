//
// BSDsbTransactions.java
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
import com.ffusion.beans.disbursement.DisbursementTransaction;
import com.ffusion.beans.disbursement.DisbursementTransactions;
import com.ffusion.dataconsolidator.constants.DCConstants;
import com.ffusion.util.MapUtil;
import com.ffusion.util.db.DBUtil;

public class BSDsbTransactions {
	

    private static final String SQL_ADD_DSTRANSACTION = "INSERT INTO BS_DsbTransactions( AccountID, TransactionIndex, DataDate, DataSource, TransID, " +
    						"CheckDate, Payee, Amount, CheckNumber, CheckRefNum, Memo, IssuedBy, ApprovedBy, ImmedFundsNeeded, " +
						"OneDayFundsNeeded, TwoDayFundsNeeded, ValueDateTime, BankRefNum, CustRefNum, ExtendABeanXMLID, Extra, Presentment, BAIFileIdentifier ) " +
						"VALUES( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? )";

    private static final String SQL_GET_DSTRANSACTIONS1 = "SELECT b.TransactionIndex, b.DataDate, b.TransID, b.CheckDate, b.Payee, b.Amount, " +
    						"b.CheckNumber, b.CheckRefNum, b.Memo, b.IssuedBy, b.ApprovedBy, b.ImmedFundsNeeded, b.OneDayFundsNeeded, b.TwoDayFundsNeeded, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID, b.Presentment FROM BS_Account a, BS_DsbTransactions b WHERE a.AccountID=b.AccountID " +
						"AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate > ? AND b.DataDate < ? ORDER BY b.TransactionIndex";

    private static final String SQL_GET_DSTRANSACTIONS2 = "SELECT b.TransactionIndex, b.DataDate, b.TransID, b.CheckDate, b.Payee, b.Amount, " +
    						"b.CheckNumber, b.CheckRefNum, b.Memo, b.IssuedBy, b.ApprovedBy, b.ImmedFundsNeeded, b.OneDayFundsNeeded, b.TwoDayFundsNeeded, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID, b.Presentment FROM BS_Account a, BS_DsbTransactions b WHERE a.AccountID=b.AccountID " +
						"AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate > ? ORDER BY b.TransactionIndex";

    private static final String SQL_GET_DSTRANSACTIONS3 = "SELECT b.TransactionIndex, b.DataDate, b.TransID, b.CheckDate, b.Payee, b.Amount, " +
    						"b.CheckNumber, b.CheckRefNum, b.Memo, b.IssuedBy, b.ApprovedBy, b.ImmedFundsNeeded, b.OneDayFundsNeeded, b.TwoDayFundsNeeded, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID, b.Presentment FROM BS_Account a, BS_DsbTransactions b WHERE a.AccountID=b.AccountID " +
						"AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate < ? ORDER BY b.TransactionIndex";

    private static final String SQL_GET_DSTRANSACTIONS4 = "SELECT b.TransactionIndex, b.DataDate, b.TransID, b.CheckDate, b.Payee, b.Amount, " +
    						"b.CheckNumber, b.CheckRefNum, b.Memo, b.IssuedBy, b.ApprovedBy, b.ImmedFundsNeeded, b.OneDayFundsNeeded, b.TwoDayFundsNeeded, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID, b.Presentment FROM BS_Account a, BS_DsbTransactions b WHERE a.AccountID=b.AccountID " +
						"AND a.AccountID=? AND a.RoutingNum=? ORDER BY b.TransactionIndex";

    // the following queries are used in paging (hence the transaction index range specified)
    private static final String SQL_GET_DS_PAGED_TRANSACTIONS1 = "SELECT b.TransactionIndex, b.DataDate, b.TransID, b.CheckDate, b.Payee, b.Amount, " +
    						"b.CheckNumber, b.CheckRefNum, b.Memo, b.IssuedBy, b.ApprovedBy, b.ImmedFundsNeeded, b.OneDayFundsNeeded, b.TwoDayFundsNeeded, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID, b.Presentment FROM BS_Account a, BS_DsbTransactions b WHERE a.AccountID=b.AccountID " +
						"AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate > ? AND b.DataDate < ? AND b.TransactionIndex>=? AND b.TransactionIndex<=? ORDER BY b.TransactionIndex";

    private static final String SQL_GET_DS_PAGED_TRANSACTIONS2 = "SELECT b.TransactionIndex, b.DataDate, b.TransID, b.CheckDate, b.Payee, b.Amount, " +
    						"b.CheckNumber, b.CheckRefNum, b.Memo, b.IssuedBy, b.ApprovedBy, b.ImmedFundsNeeded, b.OneDayFundsNeeded, b.TwoDayFundsNeeded, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID, b.Presentment FROM BS_Account a, BS_DsbTransactions b WHERE a.AccountID=b.AccountID " +
						"AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate > ? AND b.TransactionIndex>=? AND b.TransactionIndex<=? ORDER BY b.TransactionIndex";

    private static final String SQL_GET_DS_PAGED_TRANSACTIONS3 = "SELECT b.TransactionIndex, b.DataDate, b.TransID, b.CheckDate, b.Payee, b.Amount, " +
    						"b.CheckNumber, b.CheckRefNum, b.Memo, b.IssuedBy, b.ApprovedBy, b.ImmedFundsNeeded, b.OneDayFundsNeeded, b.TwoDayFundsNeeded, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID, b.Presentment FROM BS_Account a, BS_DsbTransactions b WHERE a.AccountID=b.AccountID " +
						"AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate < ? AND b.TransactionIndex>=? AND b.TransactionIndex<=? ORDER BY b.TransactionIndex";

    private static final String SQL_GET_DS_PAGED_TRANSACTIONS4 = "SELECT b.TransactionIndex, b.DataDate, b.TransID, b.CheckDate, b.Payee, b.Amount, " +
    						"b.CheckNumber, b.CheckRefNum, b.Memo, b.IssuedBy, b.ApprovedBy, b.ImmedFundsNeeded, b.OneDayFundsNeeded, b.TwoDayFundsNeeded, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID, b.Presentment FROM BS_Account a, BS_DsbTransactions b WHERE a.AccountID=b.AccountID " +
						"AND a.AccountID=? AND a.RoutingNum=? AND b.TransactionIndex>=? AND b.TransactionIndex<=? ORDER BY b.TransactionIndex";


    private static final String SQL_GET_DSTRANSACTIONS_MAX_INDEX_1 = "SELECT min(b.TransactionIndex), max(b.TransactionIndex) FROM BS_Account a, " +
	"BS_DsbTransactions b WHERE a.AccountID=b.AccountID AND a.AccountID=? " +
	"AND a.RoutingNum=? AND b.DataDate > ? AND b.DataDate < ?";

    private static final String SQL_GET_DSTRANSACTIONS_MAX_INDEX_2 = "SELECT min(b.TransactionIndex), max(b.TransactionIndex) FROM BS_Account a, " +
	"BS_DsbTransactions b WHERE a.AccountID=b.AccountID AND a.AccountID=? " +
	"AND a.RoutingNum=? AND b.DataDate > ?";

    private static final String SQL_GET_DSTRANSACTIONS_MAX_INDEX_3 = "SELECT min(b.TransactionIndex), max(b.TransactionIndex) FROM BS_Account a, " +
	"BS_DsbTransactions b WHERE a.AccountID=b.AccountID AND a.AccountID=? " +
	"AND a.RoutingNum=? AND b.DataDate < ?";

    private static final String SQL_GET_DSTRANSACTIONS_MAX_INDEX_4 = "SELECT min(b.TransactionIndex), max(b.TransactionIndex) FROM BS_Account a, " +
	"BS_DsbTransactions b WHERE a.AccountID=b.AccountID AND a.AccountID=? AND a.RoutingNum=?";

    private static final String SQL_GET_DS_PRESENTMENT_TRANSACTIONS_MAX_INDEX_1 = 
	"SELECT min(b.TransactionIndex), max(b.TransactionIndex) FROM BS_Account a, " +
	"BS_DsbTransactions b WHERE a.AccountID=b.AccountID AND a.AccountID=? " +
	"AND a.RoutingNum =? AND b.Presentment = ? AND b.DataDate > ? AND b.DataDate < ?";

    private static final String SQL_GET_DS_PRESENTMENT_TRANSACTIONS_MAX_INDEX_2 = 
	"SELECT min(b.TransactionIndex), max(b.TransactionIndex) FROM BS_Account a, " +
	"BS_DsbTransactions b WHERE a.AccountID=b.AccountID AND a.AccountID=? " +
	"AND a.RoutingNum =? AND b.Presentment = ? AND b.DataDate > ?";

    private static final String SQL_GET_DS_PRESENTMENT_TRANSACTIONS_MAX_INDEX_3 = 
	"SELECT min(b.TransactionIndex), max(b.TransactionIndex) FROM BS_Account a, " +
	"BS_DsbTransactions b WHERE a.AccountID=b.AccountID AND a.AccountID=? " +
	"AND a.RoutingNum =? AND b.Presentment = ? AND b.DataDate < ?";

    private static final String SQL_GET_DS_PRESENTMENT_TRANSACTIONS_MAX_INDEX_4 = 
	"SELECT min(b.TransactionIndex), max(b.TransactionIndex) FROM BS_Account a, " +
	"BS_DsbTransactions b WHERE a.AccountID=b.AccountID AND a.AccountID=? " +
	"AND a.RoutingNum =? AND b.Presentment = ? ";


    private static final String SQL_GET_RECENTTRANSACTIONS = "SELECT b.TransactionIndex, b.DataDate, b.TransID, b.CheckDate, b.Payee, b.Amount, " +
    						"b.CheckNumber, b.CheckRefNum, b.Memo, b.IssuedBy, b.ApprovedBy, b.ImmedFundsNeeded, b.OneDayFundsNeeded, b.TwoDayFundsNeeded, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID, b.Presentment FROM BS_Account a, BS_DsbTransactions b WHERE a.AccountID=b.AccountID " +
						"AND a.AccountID=? AND a.RoutingNum =? AND b.TransactionIndex>=? AND b.TransactionIndex<=? ORDER BY b.TransactionIndex";

    private static final String SQL_GET_NEXTTRANSACTIONS = "SELECT b.TransactionIndex, b.DataDate, b.TransID, b.CheckDate, b.Payee, b.Amount, " +
    						"b.CheckNumber, b.CheckRefNum, b.Memo, b.IssuedBy, b.ApprovedBy, b.ImmedFundsNeeded, b.OneDayFundsNeeded, b.TwoDayFundsNeeded, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID, b.Presentment FROM BS_Account a, BS_DsbTransactions b WHERE a.AccountID=b.AccountID " +
						"AND a.AccountID=? AND a.RoutingNum=? AND b.TransactionIndex>=? AND b.TransactionIndex<=? ORDER BY b.TransactionIndex";

    private static final String SQL_GET_PREVIOUSTRANSACTIONS = "SELECT b.TransactionIndex, b.DataDate, b.TransID, b.CheckDate, b.Payee, b.Amount, " +
    						"b.CheckNumber, b.CheckRefNum, b.Memo, b.IssuedBy, b.ApprovedBy, b.ImmedFundsNeeded, b.OneDayFundsNeeded, b.TwoDayFundsNeeded, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID, b.Presentment FROM BS_Account a, BS_DsbTransactions b WHERE a.AccountID=b.AccountID " +
						"AND a.AccountID=? AND a.RoutingNum=? AND b.TransactionIndex>=? AND b.TransactionIndex<=? ORDER BY b.TransactionIndex";


    private static final String SQL_GET_DS_PRESENTMENT_TRANSACTIONS1 = "SELECT b.TransactionIndex, b.DataDate, b.TransID, b.CheckDate, b.Payee, b.Amount, " +
    						"b.CheckNumber, b.CheckRefNum, b.Memo, b.IssuedBy, b.ApprovedBy, b.ImmedFundsNeeded, b.OneDayFundsNeeded, b.TwoDayFundsNeeded, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID, b.Presentment FROM BS_Account a, BS_DsbTransactions b WHERE a.AccountID=b.AccountID " +
						"AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate > ? AND b.DataDate < ? AND b.PRESENTMENT = ? ORDER BY b.TransactionIndex";

    private static final String SQL_GET_DS_PRESENTMENT_TRANSACTIONS2 = "SELECT b.TransactionIndex, b.DataDate, b.TransID, b.CheckDate, b.Payee, b.Amount, " +
    						"b.CheckNumber, b.CheckRefNum, b.Memo, b.IssuedBy, b.ApprovedBy, b.ImmedFundsNeeded, b.OneDayFundsNeeded, b.TwoDayFundsNeeded, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID, b.Presentment FROM BS_Account a, BS_DsbTransactions b WHERE a.AccountID=b.AccountID " +
						"AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate > ? AND b.PRESENTMENT = ? ORDER BY b.TransactionIndex";

    private static final String SQL_GET_DS_PRESENTMENT_TRANSACTIONS3 = "SELECT b.TransactionIndex, b.DataDate, b.TransID, b.CheckDate, b.Payee, b.Amount, " +
    						"b.CheckNumber, b.CheckRefNum, b.Memo, b.IssuedBy, b.ApprovedBy, b.ImmedFundsNeeded, b.OneDayFundsNeeded, b.TwoDayFundsNeeded, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID, b.Presentment FROM BS_Account a, BS_DsbTransactions b WHERE a.AccountID=b.AccountID " +
						"AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate < ? AND b.PRESENTMENT = ? ORDER BY b.TransactionIndex";

    private static final String SQL_GET_DS_PRESENTMENT_TRANSACTIONS4 = "SELECT b.TransactionIndex, b.DataDate, b.TransID, b.CheckDate, b.Payee, b.Amount, " +
    						"b.CheckNumber, b.CheckRefNum, b.Memo, b.IssuedBy, b.ApprovedBy, b.ImmedFundsNeeded, b.OneDayFundsNeeded, b.TwoDayFundsNeeded, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID, b.Presentment FROM BS_Account a, BS_DsbTransactions b WHERE a.AccountID=b.AccountID " +
						"AND a.AccountID=? AND a.RoutingNum=? AND b.PRESENTMENT = ? ORDER BY b.TransactionIndex";

    // the queries to support viewing transactions for a presentment in a paged format
    private static final String SQL_GET_DS_PRESENTMENT_PAGED_TRANSACTIONS1 = "SELECT b.TransactionIndex, b.DataDate, b.TransID, b.CheckDate, b.Payee, b.Amount, " +
    						"b.CheckNumber, b.CheckRefNum, b.Memo, b.IssuedBy, b.ApprovedBy, b.ImmedFundsNeeded, b.OneDayFundsNeeded, b.TwoDayFundsNeeded, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID, b.Presentment FROM BS_Account a, BS_DsbTransactions b WHERE a.AccountID=b.AccountID " +
						"AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate > ? AND b.DataDate < ? AND b.Presentment = ? AND b.TransactionIndex>=? AND b.TransactionIndex<=? ORDER BY b.TransactionIndex";

    private static final String SQL_GET_DS_PRESENTMENT_PAGED_TRANSACTIONS2 = "SELECT b.TransactionIndex, b.DataDate, b.TransID, b.CheckDate, b.Payee, b.Amount, " +
    						"b.CheckNumber, b.CheckRefNum, b.Memo, b.IssuedBy, b.ApprovedBy, b.ImmedFundsNeeded, b.OneDayFundsNeeded, b.TwoDayFundsNeeded, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID, b.Presentment FROM BS_Account a, BS_DsbTransactions b WHERE a.AccountID=b.AccountID " +
						"AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate > ? AND b.Presentment = ? AND b.TransactionIndex>=? AND b.TransactionIndex<=? ORDER BY b.TransactionIndex";

    private static final String SQL_GET_DS_PRESENTMENT_PAGED_TRANSACTIONS3 = "SELECT b.TransactionIndex, b.DataDate, b.TransID, b.CheckDate, b.Payee, b.Amount, " +
    						"b.CheckNumber, b.CheckRefNum, b.Memo, b.IssuedBy, b.ApprovedBy, b.ImmedFundsNeeded, b.OneDayFundsNeeded, b.TwoDayFundsNeeded, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID, b.Presentment FROM BS_Account a, BS_DsbTransactions b WHERE a.AccountID=b.AccountID " +
						"AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate < ? AND b.Presentment = ? AND b.TransactionIndex>=? AND b.TransactionIndex<=? ORDER BY b.TransactionIndex";

    private static final String SQL_GET_DS_PRESENTMENT_PAGED_TRANSACTIONS4 = "SELECT b.TransactionIndex, b.DataDate, b.TransID, b.CheckDate, b.Payee, b.Amount, " +
    						"b.CheckNumber, b.CheckRefNum, b.Memo, b.IssuedBy, b.ApprovedBy, b.ImmedFundsNeeded, b.OneDayFundsNeeded, b.TwoDayFundsNeeded, " +
						"b.ValueDateTime, b.BankRefNum, b.CustRefNum, b.ExtendABeanXMLID, b.Presentment FROM BS_Account a, BS_DsbTransactions b WHERE a.AccountID=b.AccountID " +
						"AND a.AccountID=? AND a.RoutingNum=? AND b.Presentment = ? AND b.TransactionIndex>=? AND b.TransactionIndex<=? ORDER BY b.TransactionIndex";


    /*
	 Adds transactions into the repository for a specified disbursement account
	 @param account: the disbursement account for which we want to add the transactions
	 @param transactions: a list of DisbursementTransaction objects to be added
	 @param dataSource: the source of the data
	 @param connection: Database connection object
    */
    public static void addTransactions(  com.ffusion.beans.disbursement.DisbursementAccount account,
					  DisbursementTransactions transactions,
					  int dataSource,
					  DBConnection connection,
					  HashMap extra )
					  throws BSException
    {

	PreparedStatement stmt = null;
	DisbursementTransaction trans = null;
	long index = 0;
	try {
	    // get the BAI filename and timestamp
	    String BAIFileIdentifier = null;
	    if( extra != null ) {
	    	BAIFileIdentifier = (String)extra.get( BSConstants.BAI_FILE_IDENTIFIER );
	    }
	    

	    stmt = connection.prepareStatement( connection, SQL_ADD_DSTRANSACTION );
	    String accountID = account.getAccountID();
	    for( int i = 0; i < transactions.size(); i++ ) {
		trans = (DisbursementTransaction)transactions.get( i );
		
		if( trans.getTransactionIndex() != 0 ) {
		    throw new BSException( BSException.BSE_INVALID_TRANSACTION_INDEX,
					   com.ffusion.banksim.db.MessageText.getMessage( com.ffusion.banksim.db.IBSErrConstants.ERR_INVALID_TRANSACTION_INDEX ) );
		} else {
		    index = BSRecordCounter.getNextIndex( connection, BSRecordCounter.TYPE_ACCOUNT, accountID, BSRecordCounter.DISBURSEMENT_TRANSACTION_INDEX );
		}

		// get the ExtendABean ID
		long extendABeanID = BSExtendABeanXML.addExtendABeanXML( connection, trans.getExtendABeanXML() );

		stmt.setString( 1, accountID );
		stmt.setLong( 2, index );
		BSUtil.fillTimestampColumn(connection,  stmt, 3, trans.getProcessingDate() );
		stmt.setInt( 4, dataSource );
		stmt.setInt( 5, trans.getTransID() );
		BSUtil.fillTimestampColumn(connection,  stmt, 6, trans.getCheckDate() );
		stmt.setString( 7, trans.getPayee() );
		BSUtil.fillCurrencyColumn( stmt, 8, trans.getCheckAmount() );
		stmt.setString( 9, trans.getCheckNumber() );
		stmt.setString( 10, trans.getCheckReferenceNumber() );
		stmt.setString( 11, trans.getMemo() );
		stmt.setString( 12, trans.getIssuedBy() );
		stmt.setString( 13, trans.getApprovedBy() );
		BSUtil.fillCurrencyColumn( stmt, 14, trans.getImmediateFundsNeeded() );
		BSUtil.fillCurrencyColumn( stmt, 15, trans.getOneDayFundsNeeded() );
		BSUtil.fillCurrencyColumn( stmt, 16, trans.getTwoDayFundsNeeded() );
		BSUtil.fillTimestampColumn(connection,  stmt, 17, trans.getValueDateTime() );
		stmt.setString( 18, trans.getBankReferenceNumber() );
		stmt.setString( 19, trans.getCustomerReferenceNumber() );
		stmt.setLong( 20, extendABeanID );
		stmt.setString( 21, null );
		stmt.setString( 22, trans.getPresentment() );
		stmt.setString( 23, BAIFileIdentifier );
	
		DBConnection.executeUpdate( stmt, SQL_ADD_DSTRANSACTION );
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
    	load the results from the resultset into the transaction bean
	@param trans: DisbursementTransaction bean to be filled in
	@param rs: ResultSet contains information to fill trans with
    */
    private static void loadTransaction( DBConnection conn, com.ffusion.beans.disbursement.DisbursementAccount account,
    					DisbursementTransaction trans,
					ResultSet rs )
					throws Exception
    {
	trans.setAccountID( account.getAccountID() );
	trans.setAccountNumber( account.getAccountNumber() );
	trans.setBankID( account.getBankID() );
	trans.setTransactionIndex( rs.getLong( 1 ) );
	trans.setProcessingDate( BSUtil.getTimestampColumn(  rs.getTimestamp( 2 ) ) );
	trans.setTransID( rs.getInt( 3 ) );
	trans.setCheckDate( BSUtil.getTimestampColumn( rs.getTimestamp( 4 ) ) );
	trans.setPayee( rs.getString( 5 ) );
	trans.setCheckAmount( BSUtil.getCurrencyColumn(  rs.getBigDecimal( 6 ), account.getCurrencyType() ) );
	trans.setCheckNumber( rs.getString( 7 ) );
	trans.setCheckReferenceNumber( rs.getString( 8 ) );
	trans.setMemo( rs.getString( 9 ) );
	trans.setIssuedBy( rs.getString( 10 ) );
	trans.setApprovedBy( rs.getString( 11 ) );
	trans.setImmediateFundsNeeded( BSUtil.getCurrencyColumn(  rs.getBigDecimal( 12 ), account.getCurrencyType() ) );
	trans.setOneDayFundsNeeded( BSUtil.getCurrencyColumn(  rs.getBigDecimal( 13 ), account.getCurrencyType() ) );
	trans.setTwoDayFundsNeeded( BSUtil.getCurrencyColumn(  rs.getBigDecimal( 14 ), account.getCurrencyType() ) );
	trans.setValueDateTime( BSUtil.getTimestampColumn(  rs.getTimestamp( 15 ) ) );
	trans.setBankReferenceNumber( rs.getString( 16 ) );
	trans.setCustomerReferenceNumber( rs.getString( 17 ) );
	BSExtendABeanXML.fillExtendABean(conn, trans, rs, 18 );
	trans.setPresentment( rs.getString( 19 ) );
    }

    /*
	 Retrieves a list of transactions for a specified disbursement account between a start date to an end date
	 @param account: the disbursement account for which we want to retrieve transactions
	 @param startDate: the start date of transactions to get or null if no start date
	 @param endDate: the end date of transactions to get or null if no end date
	 @return a list of DisbursementTransaction beans
    */
    public static DisbursementTransactions getTransactions(
							  com.ffusion.beans.disbursement.DisbursementAccount account,
							  Calendar startDate,
							  Calendar endDate,
							  DBConnection connection,
							  HashMap extra )
							  throws BSException
    {
	return getTransactions( account, startDate, endDate, null, connection, extra );
    }
    
    /*
	 Retrieves a list of transactions for a specified disbursement account and presentment between a start date 
	 to an end date
	 @param account: the disbursement account for which we want to retrieve transactions
	 @param startDate: the start date of transactions to get or null if no start date
	 @param endDate: the end date of transactions to get or null if no end date
	 @param presentment: the presentment for which we want to retrieve transactions (null considers all presentments)
	 @return a list of DisbursementTransaction beans
    */
    public static DisbursementTransactions getTransactions(
							  com.ffusion.beans.disbursement.DisbursementAccount account,
							  Calendar startDate,
							  Calendar endDate,
							  String presentment,
							  DBConnection connection,
							  HashMap extra )
							  throws BSException
    {
	PreparedStatement stmt = null;
	DisbursementTransactions transactions = null;
	ResultSet rs  = null;
	try {
	    if ( presentment == null ) {
		if( startDate != null ) {
		    if( endDate != null ) {
			stmt = connection.prepareStatement( connection, SQL_GET_DSTRANSACTIONS1 );
			stmt.setString(1, account.getAccountID() );
			stmt.setString(2, account.getRoutingNumber() );
			BSUtil.fillTimestampColumn(connection,  stmt, 3,  startDate );
			BSUtil.fillTimestampColumn(connection,  stmt, 4,  endDate );
			rs = DBConnection.executeQuery( stmt, SQL_GET_DSTRANSACTIONS1 );
		    } else {
			stmt = connection.prepareStatement( connection, SQL_GET_DSTRANSACTIONS2 );
			stmt.setString(1, account.getAccountID() );
			stmt.setString(2, account.getRoutingNumber() );
			BSUtil.fillTimestampColumn(connection,  stmt, 3,  startDate );
			rs = DBConnection.executeQuery( stmt, SQL_GET_DSTRANSACTIONS2 );
		    }
		} else if( endDate != null ) {
		    stmt = connection.prepareStatement( connection, SQL_GET_DSTRANSACTIONS3 );
		    stmt.setString(1, account.getAccountID() );
		    stmt.setString(2, account.getRoutingNumber() );
		    BSUtil.fillTimestampColumn(connection,  stmt, 3,  endDate );
		    rs = DBConnection.executeQuery( stmt, SQL_GET_DSTRANSACTIONS3 );
		} else {
		    stmt = connection.prepareStatement( connection, SQL_GET_DSTRANSACTIONS4 );
		    stmt.setString(1, account.getAccountID() );
		    stmt.setString(2, account.getRoutingNumber() );
		    rs = DBConnection.executeQuery( stmt, SQL_GET_DSTRANSACTIONS4 );
		}
	    } else {
		// since a presentment name was provided we must use a query which searches by 
		// account and presentment
		if( startDate != null ) {
		    if( endDate != null ) {
			stmt = connection.prepareStatement( connection, SQL_GET_DS_PRESENTMENT_TRANSACTIONS1 );
			stmt.setString( 1, account.getAccountID() );
			stmt.setString( 2, account.getRoutingNumber() );
			BSUtil.fillTimestampColumn(connection,  stmt, 3,  startDate );
			BSUtil.fillTimestampColumn(connection,  stmt, 4,  endDate );
			stmt.setString( 5, presentment );
			rs = DBConnection.executeQuery( stmt, SQL_GET_DS_PRESENTMENT_TRANSACTIONS1 );
		    } else {
			stmt = connection.prepareStatement( connection, SQL_GET_DS_PRESENTMENT_TRANSACTIONS2 );
			stmt.setString( 1, account.getAccountID() );
			stmt.setString( 2, account.getRoutingNumber() );
			BSUtil.fillTimestampColumn(connection,  stmt, 3,  startDate );
			stmt.setString( 4, presentment );
			rs = DBConnection.executeQuery( stmt, SQL_GET_DS_PRESENTMENT_TRANSACTIONS2 );
		    }
		} else if( endDate != null ) {
		    stmt = connection.prepareStatement( connection, SQL_GET_DS_PRESENTMENT_TRANSACTIONS3 );
		    stmt.setString( 1, account.getAccountID() );
		    stmt.setString( 2, account.getRoutingNumber() );
		    BSUtil.fillTimestampColumn(connection,  stmt, 3,  endDate );
		    stmt.setString( 4, presentment );
		    rs = DBConnection.executeQuery( stmt, SQL_GET_DS_PRESENTMENT_TRANSACTIONS3 );
		} else {
		    stmt = connection.prepareStatement( connection, SQL_GET_DS_PRESENTMENT_TRANSACTIONS4 );
		    stmt.setString( 1, account.getAccountID() );
		    stmt.setString( 2, account.getRoutingNumber() );
		    stmt.setString( 3, presentment );
		    rs = DBConnection.executeQuery( stmt, SQL_GET_DS_PRESENTMENT_TRANSACTIONS4 );
		}
	    }

	    transactions = new DisbursementTransactions( );
	    DisbursementTransaction trans = null;
	    while( rs.next() ) {
		trans = transactions.create();
		loadTransaction(connection, account, trans, rs );
	    }
	    DBUtil.closeResultSet( rs );
	    return transactions;

	} catch ( Exception e ) {
	    throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	    
	    if( connection != null ) {
		connection.close();
	    }
	}
    }
  
    /**
	Retrieves a list of transactions for a specified disbursement account between a
	start date to an end date, to a maximum of PAGESIZE of them
	@param account: the disbursement account for which we want to retrieve transactions
	@param startDate: the start date of transactions to get or null if no start date
	@param endDate: the end date of transactions to get or null if no end date
	@return a list of DisbursementTransaction beans
    */
    public static DisbursementTransactions getPagedTransactions(
								com.ffusion.beans.disbursement.DisbursementAccount account,
								Calendar startDate,
								Calendar endDate,
								DBConnection connection,
								HashMap extra )
								throws BSException
    {
	return getPagedTransactions( account, startDate, endDate, null, connection, extra );
    }  
    
    /**
	Retrieves a list of transactions for a specified disbursement account and presentment between a
	start date to an end date, to a maximum of PAGESIZE of them
	@param account: the disbursement account for which we want to retrieve transactions
	@param startDate: the start date of transactions to get or null if no start date
	@param endDate: the end date of transactions to get or null if no end date
	@param presentment: the presentment for which we want to retrieve transactions (null considers all transactions)
	@return a list of DisbursementTransaction beans
    */
    public static DisbursementTransactions getPagedTransactions(
								com.ffusion.beans.disbursement.DisbursementAccount account,
								Calendar startDate,
								Calendar endDate,
								String presentment,
								DBConnection connection,
								HashMap extra )
								throws BSException
    {
	PreparedStatement stmt = null;
	DisbursementTransactions transactions = null;
	
	ResultSet rs  = null;
	try {
	    int pageSize = MapUtil.getIntValue( extra, BSConstants.PAGE_SIZE, BSConstants.PAGESIZE );
	    long index[] = getMaximumTransactionIndex( connection, account, presentment, startDate, endDate );
	    long minIndex = index[0];
	    long maxIndex = index[1];

	    if ( presentment == null ) {
		if( startDate != null ) {
		    if( endDate != null ) {
			stmt = connection.prepareStatement( connection, SQL_GET_DS_PAGED_TRANSACTIONS1);
			stmt.setString(1, account.getAccountID() );
			stmt.setString(2, account.getRoutingNumber() );
			BSUtil.fillTimestampColumn(connection,  stmt, 3,  startDate );
			BSUtil.fillTimestampColumn(connection,  stmt, 4,  endDate );
			stmt.setLong( 5, minIndex );
			stmt.setLong( 6, minIndex + pageSize - 1 );
			rs = DBConnection.executeQuery( stmt, SQL_GET_DS_PAGED_TRANSACTIONS1 );
		    } else {
			stmt = connection.prepareStatement( connection, SQL_GET_DS_PAGED_TRANSACTIONS2 );
			stmt.setString(1, account.getAccountID() );
			stmt.setString(2, account.getRoutingNumber() );
			BSUtil.fillTimestampColumn(connection,  stmt, 3,  startDate );
			stmt.setLong( 4, minIndex );
			stmt.setLong( 5, minIndex + pageSize - 1 );
			rs = DBConnection.executeQuery( stmt, SQL_GET_DS_PAGED_TRANSACTIONS2 );
		    }
		} else if( endDate != null ) {
			stmt = connection.prepareStatement( connection, SQL_GET_DS_PAGED_TRANSACTIONS3 );
			stmt.setString(1, account.getAccountID() );
			stmt.setString(2, account.getRoutingNumber() );
			BSUtil.fillTimestampColumn(connection,  stmt, 3,  endDate );
			stmt.setLong( 4, minIndex );
			stmt.setLong( 5, minIndex + pageSize - 1 );
			rs = DBConnection.executeQuery( stmt, SQL_GET_DS_PAGED_TRANSACTIONS3 );
		} else {
			stmt = connection.prepareStatement( connection, SQL_GET_DS_PAGED_TRANSACTIONS4 );
			stmt.setString(1, account.getAccountID() );
			stmt.setString(2, account.getRoutingNumber() );
			stmt.setLong( 3, minIndex );
			stmt.setLong( 4, minIndex + pageSize - 1 );
			rs = DBConnection.executeQuery( stmt, SQL_GET_DS_PAGED_TRANSACTIONS4 );
		}
	    } else {
		// since a presentment name was provided we must use a query which searches by 
		// account and presentment
		if( startDate != null ) {
		    if( endDate != null ) {
			stmt = connection.prepareStatement( connection, SQL_GET_DS_PRESENTMENT_PAGED_TRANSACTIONS1 );
			stmt.setString( 1, account.getAccountID() );
			stmt.setString( 2, account.getRoutingNumber() );
			BSUtil.fillTimestampColumn(connection,  stmt, 3,  startDate );
			BSUtil.fillTimestampColumn(connection,  stmt, 4,  endDate );
			stmt.setString( 5, presentment );
			stmt.setLong( 6, minIndex );
			stmt.setLong( 7, minIndex + pageSize - 1 );
			rs = DBConnection.executeQuery( stmt, SQL_GET_DS_PRESENTMENT_PAGED_TRANSACTIONS1 );
		    } else {
			stmt = connection.prepareStatement( connection, SQL_GET_DS_PRESENTMENT_PAGED_TRANSACTIONS2 );
			stmt.setString( 1, account.getAccountID() );
			stmt.setString( 2, account.getRoutingNumber() );
			BSUtil.fillTimestampColumn(connection,  stmt, 3,  startDate );
			stmt.setString( 4, presentment );
			stmt.setLong( 5, minIndex );
			stmt.setLong( 6, minIndex + pageSize - 1 );
			rs = DBConnection.executeQuery( stmt, SQL_GET_DS_PRESENTMENT_PAGED_TRANSACTIONS2 );
		    }
		} else if( endDate != null ) {
			stmt = connection.prepareStatement( connection, SQL_GET_DS_PRESENTMENT_PAGED_TRANSACTIONS3 );
			stmt.setString( 1, account.getAccountID() );
			stmt.setString( 2, account.getRoutingNumber() );
			BSUtil.fillTimestampColumn(connection,  stmt, 3,  endDate );
			stmt.setString( 4, presentment );
			stmt.setLong( 5, minIndex );
			stmt.setLong( 6, minIndex + pageSize - 1 );
			rs = DBConnection.executeQuery( stmt, SQL_GET_DS_PRESENTMENT_PAGED_TRANSACTIONS3 );
		} else {
			stmt = connection.prepareStatement( connection, SQL_GET_DS_PRESENTMENT_PAGED_TRANSACTIONS4 );
			stmt.setString( 1, account.getAccountID() );
			stmt.setString( 2, account.getRoutingNumber() );
			stmt.setString( 3, presentment );
			stmt.setLong( 4, minIndex );
			stmt.setLong( 5, minIndex + pageSize - 1 );
			rs = DBConnection.executeQuery( stmt, SQL_GET_DS_PRESENTMENT_PAGED_TRANSACTIONS4 );
		}
	}

	transactions = new DisbursementTransactions( );
	DisbursementTransaction trans = null;
	while( rs.next() ) {
		trans = transactions.create();
		loadTransaction(connection , account, trans, rs );
	}
	DBUtil.closeResultSet( rs );

	extra.put( DCConstants.MINIMUM_TRANSACTION_INDEX_FOR_RANGE, new Long( minIndex ) );
	extra.put( DCConstants.MAXIMUM_TRANSACTION_INDEX_FOR_RANGE, new Long( maxIndex ) );

	return transactions;

	} catch ( Exception e ) {
		throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
		if( stmt != null ) {
			DBConnection.closeStatement( stmt );
		}
		if( connection != null ) {
			connection.close();
		}
	}
    }

    /*
	 Retrieves the most recent transactions for the specified disbursement account
	 @param account: the disbursement account for which we want to retrieve transactions
	 @return a list of DisbursementTransaction beans containing the transactions (at
		 		  most PAGESIZE of them)
    */
    public static DisbursementTransactions getRecentTransactions(
				  com.ffusion.beans.disbursement.DisbursementAccount account,
				  DBConnection connection,
				  HashMap extra )
				  throws BSException
    {
	PreparedStatement stmt = null;
	DisbursementTransactions transactions = new DisbursementTransactions( );
	
	try {
	    int pageSize = MapUtil.getIntValue( extra, BSConstants.PAGE_SIZE, BSConstants.PAGESIZE );

	    long index = BSRecordCounter.getIndex( connection, account.getAccountID(), account.getRoutingNumber(),
						   BSRecordCounter.TYPE_ACCOUNT,
						   BSRecordCounter.DISBURSEMENT_TRANSACTION_INDEX );

	    if ( index != -1 ) {
		stmt = connection.prepareStatement( connection, SQL_GET_RECENTTRANSACTIONS );
		stmt.setString(1, account.getAccountID() );
		stmt.setString(2, account.getRoutingNumber() );
		stmt.setLong( 3, index - pageSize + 1 );
		stmt.setLong( 4, index );	
		ResultSet rs = DBConnection.executeQuery( stmt, SQL_GET_RECENTTRANSACTIONS );
		
		DisbursementTransaction trans = null;
		while( rs.next() ) {
		    trans = transactions.create();
		    loadTransaction(connection, account, trans, rs );
		}
		DBUtil.closeResultSet( rs );
	    }
	    return transactions;

	} catch ( Exception e ) {
	    throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if ( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	    
	    if( connection != null ) {
		connection.close();
	    }
	}
    }

    /*
	 Retrieves the next batch of PAGESIZE transactions for the specified disbursement account
	 @param account: the disbursement account for which we want to retrieve transactions
	 @param nextIndex: the next index of information to retrieve
	 @return a list of DisbursementTransaction beans containing the transactions (at
	 most PAGE_SIZE of them)
    */
    public static DisbursementTransactions getNextTransactions(
						      com.ffusion.beans.disbursement.DisbursementAccount account,
						      long nextIndex,
						      DBConnection connection,
						      HashMap extra )
						      throws BSException
    {
	PreparedStatement stmt = null;
	DisbursementTransactions transactions = null;
	
	try {
	    int pageSize = MapUtil.getIntValue( extra, BSConstants.PAGE_SIZE, BSConstants.PAGESIZE );
	    stmt = connection.prepareStatement( connection, SQL_GET_NEXTTRANSACTIONS );
	    stmt.setString(1, account.getAccountID() );
	    stmt.setString(2, account.getRoutingNumber() );
	    stmt.setLong(3, nextIndex);
	    stmt.setLong(4, nextIndex + pageSize - 1);
	    ResultSet rs = DBConnection.executeQuery( stmt, SQL_GET_NEXTTRANSACTIONS );
	       
	    transactions = new DisbursementTransactions( );
	    DisbursementTransaction trans = null;
	    while( rs.next() ) {
		trans = transactions.create();
		loadTransaction( connection, account, trans, rs );
	    }
		DBUtil.closeResultSet( rs );
	    return transactions;

	} catch ( Exception e ) {
	    throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if ( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	    
	    if( connection != null ) {
		connection.close();
	    }
	}
    }
    
    /*
	 Retrieves the previous batch of PAGESIZE transactions for the specified disbursement account
	 @param account: the disbursement account for which we want to retrieve transactions
	 @param lastIndex: the last index of information to retrieve
	 @return a list of DisbursementTransaction beans containing the transactions (at
	 most PAGE_SIZE of them)
    */
    public static DisbursementTransactions getPreviousTransactions(
						      com.ffusion.beans.disbursement.DisbursementAccount account,
						      long lastIndex,
						      DBConnection connection,
						      HashMap extra )
						      throws BSException
    {
	PreparedStatement stmt = null;
	DisbursementTransactions transactions = null;
	
	try {
	    int pageSize = MapUtil.getIntValue( extra, BSConstants.PAGE_SIZE, BSConstants.PAGESIZE );
	    stmt = connection.prepareStatement( connection, SQL_GET_PREVIOUSTRANSACTIONS );
	    stmt.setString(1, account.getAccountID() );
	    stmt.setString(2, account.getRoutingNumber() );
	    stmt.setLong( 3, lastIndex - pageSize + 1 );
	    stmt.setLong( 4, lastIndex );
	    ResultSet rs = DBConnection.executeQuery( stmt, SQL_GET_PREVIOUSTRANSACTIONS );
	       
	    transactions = new DisbursementTransactions( );
	    DisbursementTransaction trans = null;
	    while( rs.next() ) {
		trans = transactions.create();
		loadTransaction(connection, account, trans, rs );
	    }
		DBUtil.closeResultSet( rs );
	    return transactions;

	} catch ( Exception e ) {
	    throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if ( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	    
	    if( connection != null ) {
		connection.close();
	    }
	}
    }
    private static long[] getMaximumTransactionIndex( DBConnection connection, 
						    com.ffusion.beans.disbursement.DisbursementAccount account, 
						    String presentment, Calendar startDate, Calendar endDate ) 
	throws Exception {

	ResultSet rs  = null;
	PreparedStatement stmt = null;

	if ( presentment == null ) {
	    if( startDate != null ) {
		if( endDate != null ) {
		    stmt = connection.prepareStatement( connection, SQL_GET_DSTRANSACTIONS_MAX_INDEX_1 );
		    stmt.setString( 1, account.getAccountID() );
		    stmt.setString( 2, account.getRoutingNumber() );
		    BSUtil.fillTimestampColumn(connection,  stmt, 3,  startDate );
		    BSUtil.fillTimestampColumn(connection,  stmt, 4,  endDate );
		    rs = DBConnection.executeQuery( stmt, SQL_GET_DSTRANSACTIONS_MAX_INDEX_1 );
		} else {
		    stmt = connection.prepareStatement( connection, SQL_GET_DSTRANSACTIONS_MAX_INDEX_2 );
		    stmt.setString( 1, account.getAccountID() );
		    stmt.setString( 2, account.getRoutingNumber() );
		    BSUtil.fillTimestampColumn(connection,  stmt, 3,  startDate );
		    rs = DBConnection.executeQuery( stmt, SQL_GET_DSTRANSACTIONS_MAX_INDEX_2 );
		}
	    } else if( endDate != null ) {
		stmt = connection.prepareStatement( connection, SQL_GET_DSTRANSACTIONS_MAX_INDEX_3 );
		stmt.setString( 1, account.getAccountID() );
		stmt.setString( 2, account.getRoutingNumber() );
		BSUtil.fillTimestampColumn(connection,  stmt, 3,  endDate );
		rs = DBConnection.executeQuery( stmt, SQL_GET_DSTRANSACTIONS_MAX_INDEX_3 );
	    } else {
		stmt = connection.prepareStatement( connection, SQL_GET_DSTRANSACTIONS_MAX_INDEX_4 );
		stmt.setString( 1, account.getAccountID() );
		stmt.setString( 2, account.getRoutingNumber() );
		rs = DBConnection.executeQuery( stmt, SQL_GET_DSTRANSACTIONS_MAX_INDEX_4 );
	    }
	} else {
	    if( startDate != null ) {
		if( endDate != null ) {
		    stmt = connection.prepareStatement( connection, SQL_GET_DS_PRESENTMENT_TRANSACTIONS_MAX_INDEX_1 );
		    stmt.setString( 1, account.getAccountID() );
		    stmt.setString( 2, account.getRoutingNumber() );
		    stmt.setString( 3, presentment );
		    BSUtil.fillTimestampColumn(connection,  stmt, 4,  startDate );
		    BSUtil.fillTimestampColumn(connection,  stmt, 5,  endDate );
		    rs = DBConnection.executeQuery( stmt, SQL_GET_DS_PRESENTMENT_TRANSACTIONS_MAX_INDEX_1 );
		} else {
		    stmt = connection.prepareStatement( connection, SQL_GET_DS_PRESENTMENT_TRANSACTIONS_MAX_INDEX_2 );
		    stmt.setString( 1, account.getAccountID() );
		    stmt.setString( 2, account.getRoutingNumber() );
		    stmt.setString( 3, presentment );
		    BSUtil.fillTimestampColumn(connection,  stmt, 4,  startDate );
		    rs = DBConnection.executeQuery( stmt, SQL_GET_DS_PRESENTMENT_TRANSACTIONS_MAX_INDEX_2 );
		}
	    } else if( endDate != null ) {
		stmt = connection.prepareStatement( connection, SQL_GET_DS_PRESENTMENT_TRANSACTIONS_MAX_INDEX_3 );
		stmt.setString( 1, account.getAccountID() );
		stmt.setString( 2, account.getRoutingNumber() );
		stmt.setString( 3, presentment );
		BSUtil.fillTimestampColumn(connection,  stmt, 4,  endDate );
		rs = DBConnection.executeQuery( stmt, SQL_GET_DS_PRESENTMENT_TRANSACTIONS_MAX_INDEX_3 );
	    } else {
		stmt = connection.prepareStatement( connection, SQL_GET_DS_PRESENTMENT_TRANSACTIONS_MAX_INDEX_4 );
		stmt.setString( 1, account.getAccountID() );
		stmt.setString( 2, account.getRoutingNumber() );
		stmt.setString( 3, presentment );
		rs = DBConnection.executeQuery( stmt, SQL_GET_DS_PRESENTMENT_TRANSACTIONS_MAX_INDEX_4 );
	    }
	}
	
	long index[] = new long[2];
	if ( rs.next() == false ) {
	    throw new BSException( BSException.BSE_INVALID_TRANSACTION_INDEX, "Unable to retrieve the maximum transaction index for the given criteria" );
	} else {
	    index[0] = rs.getLong( 1 );
	    index[1] = rs.getLong( 2 );
	}
	return index;
    } 

}

