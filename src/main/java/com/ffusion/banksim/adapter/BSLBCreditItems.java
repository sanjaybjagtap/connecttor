//
// BSLBCreditItems.java
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
import com.ffusion.beans.lockbox.LockboxCreditItem;
import com.ffusion.beans.lockbox.LockboxCreditItems;
import com.ffusion.dataconsolidator.constants.DCConstants;
import com.ffusion.util.MapUtil;
import com.ffusion.util.db.DBUtil;


public class BSLBCreditItems {
	

	private static final String SQL_ADD_LBCREDITITEM = 
	"INSERT INTO BS_LBCreditItems( LockboxID, CreditItemIndex, DataDate, DataSource, ItemID, DocumentType, Payor, " +
	"Amount, CheckNumber, CheckDate, CouponAccountNum, CouponAmount1, CouponAmount2, CouponDate1, CouponDate2, " +
	"CouponRefNum, CheckRoutingNum, CheckAccountNum, LockboxWorkType, LockboxBatchNum, LockboxSeqNum, Memo, " +
	"ImmedAvailAmount, OneDayAvailAmount, MoreOneDayAvailAm, ValueDateTime, BankRefNum, CustRefNum, " +
	"ExtendABeanXMLID, Extra, BAIFileIdentifier ) VALUES( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
	"?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? ) ";

    private static final String SQL_GET_LBCREDITITEMS1 = 
	"SELECT c.LockboxNumber, b.CreditItemIndex, b.DataDate, b.ItemID, b.DocumentType, b.Payor, b.Amount, " +
	"b.CheckNumber, b.CheckDate, b.CouponAccountNum, b.CouponAmount1, b.CouponAmount2, b.CouponDate1, b.CouponDate2, " +
	"b.CouponRefNum, b.CheckRoutingNum, b.CheckAccountNum, b.LockboxWorkType, b.LockboxBatchNum, b.LockboxSeqNum, " +
	"b.Memo, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, b.ValueDateTime, b.BankRefNum, " +
	"b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBCreditItems b, BS_Lockbox c WHERE " +
	"a.AccountID=c.AccountID AND b.LockboxID=c.LockboxID AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate>=? " +
	"AND b.DataDate<=? AND c.LockboxNumber = ? ORDER BY b.CreditItemIndex";

    private static final String SQL_GET_LBCREDITITEMS2 = 
	"SELECT c.LockboxNumber, b.CreditItemIndex, b.DataDate, b.ItemID, b.DocumentType, b.Payor, b.Amount, " +
	"b.CheckNumber, b.CheckDate, b.CouponAccountNum, b.CouponAmount1, b.CouponAmount2, b.CouponDate1, b.CouponDate2, " +
	"b.CouponRefNum, b.CheckRoutingNum, b.CheckAccountNum, b.LockboxWorkType, b.LockboxBatchNum, b.LockboxSeqNum, " +
	"b.Memo, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, b.ValueDateTime, b.BankRefNum, " +
	"b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBCreditItems b, BS_Lockbox c WHERE " +
	"a.AccountID=c.AccountID AND b.LockboxID=c.LockboxID AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate>=? " +
	"AND c.LockboxNumber = ? ORDER BY b.CreditItemIndex";

    private static final String SQL_GET_LBCREDITITEMS3 = 
	"SELECT c.LockboxNumber, b.CreditItemIndex, b.DataDate, b.ItemID, b.DocumentType, b.Payor, b.Amount, " +
	"b.CheckNumber, b.CheckDate, b.CouponAccountNum, b.CouponAmount1, b.CouponAmount2, b.CouponDate1, b.CouponDate2, " +
	"b.CouponRefNum, b.CheckRoutingNum, b.CheckAccountNum, b.LockboxWorkType, b.LockboxBatchNum, b.LockboxSeqNum, " +
	"b.Memo, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, b.ValueDateTime, b.BankRefNum, " +
	"b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBCreditItems b, BS_Lockbox c WHERE " +
	"a.AccountID=c.AccountID AND b.LockboxID=c.LockboxID AND a.AccountID=? AND a.RoutingNum=? AND " +
	"b.DataDate<=?  AND c.LockboxNumber = ? ORDER BY b.CreditItemIndex";

    private static final String SQL_GET_LBCREDITITEMS4 = 
	"SELECT c.LockboxNumber, b.CreditItemIndex, b.DataDate, b.ItemID, b.DocumentType, b.Payor, b.Amount, " +
	"b.CheckNumber, b.CheckDate, b.CouponAccountNum, b.CouponAmount1, b.CouponAmount2, b.CouponDate1, b.CouponDate2, " +
	"b.CouponRefNum, b.CheckRoutingNum, b.CheckAccountNum, b.LockboxWorkType, b.LockboxBatchNum, b.LockboxSeqNum, " +
	"b.Memo, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, b.ValueDateTime, b.BankRefNum, " +
	"b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBCreditItems b, BS_Lockbox c WHERE " +
	"a.AccountID=c.AccountID AND b.LockboxID=c.LockboxID AND a.AccountID=? AND a.RoutingNum=? " +
	"AND c.LockboxNumber = ? ORDER BY b.CreditItemIndex";

    private static final String SQL_GET_PAGED_LBCREDITITEMS1 = 
	"SELECT c.LockboxNumber, b.CreditItemIndex, b.DataDate, b.ItemID, b.DocumentType, b.Payor, b.Amount, " +
	"b.CheckNumber, b.CheckDate, b.CouponAccountNum, b.CouponAmount1, b.CouponAmount2, b.CouponDate1, b.CouponDate2, " +
	"b.CouponRefNum, b.CheckRoutingNum, b.CheckAccountNum, b.LockboxWorkType, b.LockboxBatchNum, b.LockboxSeqNum, " +
	"b.Memo, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, b.ValueDateTime, b.BankRefNum, " +
	"b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBCreditItems b, BS_Lockbox c WHERE " +
	"a.AccountID=c.AccountID AND b.LockboxID=c.LockboxID AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate>=? " +
	"AND b.DataDate<=? AND c.LockboxNumber = ? AND b.CreditItemIndex>=? AND b.CreditItemIndex<=? ORDER BY b.CreditItemIndex";

    private static final String SQL_GET_PAGED_LBCREDITITEMS2 = 
	"SELECT c.LockboxNumber, b.CreditItemIndex, b.DataDate, b.ItemID, b.DocumentType, b.Payor, b.Amount, " +
	"b.CheckNumber, b.CheckDate, b.CouponAccountNum, b.CouponAmount1, b.CouponAmount2, b.CouponDate1, b.CouponDate2, " +
	"b.CouponRefNum, b.CheckRoutingNum, b.CheckAccountNum, b.LockboxWorkType, b.LockboxBatchNum, b.LockboxSeqNum, " +
	"b.Memo, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, b.ValueDateTime, b.BankRefNum, " +
	"b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBCreditItems b, BS_Lockbox c WHERE " +
	"a.AccountID=c.AccountID AND b.LockboxID=c.LockboxID AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate>=? " +
	"AND c.LockboxNumber = ? AND b.CreditItemIndex>=? AND b.CreditItemIndex<=? ORDER BY b.CreditItemIndex";

    private static final String SQL_GET_PAGED_LBCREDITITEMS3 = 
	"SELECT c.LockboxNumber, b.CreditItemIndex, b.DataDate, b.ItemID, b.DocumentType, b.Payor, b.Amount, " +
	"b.CheckNumber, b.CheckDate, b.CouponAccountNum, b.CouponAmount1, b.CouponAmount2, b.CouponDate1, b.CouponDate2, " +
	"b.CouponRefNum, b.CheckRoutingNum, b.CheckAccountNum, b.LockboxWorkType, b.LockboxBatchNum, b.LockboxSeqNum, " +
	"b.Memo, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, b.ValueDateTime, b.BankRefNum, " +
	"b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBCreditItems b, BS_Lockbox c WHERE " +
	"a.AccountID=c.AccountID AND b.LockboxID=c.LockboxID AND a.AccountID=? AND a.RoutingNum=? AND " +
	"b.DataDate<=?  AND c.LockboxNumber = ? AND b.CreditItemIndex>=? AND b.CreditItemIndex<=? ORDER BY b.CreditItemIndex";

    private static final String SQL_GET_PAGED_LBCREDITITEMS4 = 
	"SELECT c.LockboxNumber, b.CreditItemIndex, b.DataDate, b.ItemID, b.DocumentType, b.Payor, b.Amount, " +
	"b.CheckNumber, b.CheckDate, b.CouponAccountNum, b.CouponAmount1, b.CouponAmount2, b.CouponDate1, b.CouponDate2, " +
	"b.CouponRefNum, b.CheckRoutingNum, b.CheckAccountNum, b.LockboxWorkType, b.LockboxBatchNum, b.LockboxSeqNum, " +
	"b.Memo, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, b.ValueDateTime, b.BankRefNum, " +
	"b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBCreditItems b, BS_Lockbox c WHERE " +
	"a.AccountID=c.AccountID AND b.LockboxID=c.LockboxID AND a.AccountID=? AND a.RoutingNum=? " +
	"AND c.LockboxNumber = ? AND b.CreditItemIndex>=? AND b.CreditItemIndex<=? ORDER BY b.CreditItemIndex";

    private static final String SQL_GET_MAX_INDEX_LBCREDITITEMS1 = 
	"SELECT min( b.CreditItemIndex ),max( b.CreditItemIndex) FROM BS_Account a, BS_LBCreditItems b, BS_Lockbox c " +
	"WHERE a.AccountID=c.AccountID AND b.LockboxID=c.LockboxID AND a.AccountID=? AND a.RoutingNum=? " +
	"AND b.DataDate>=? AND b.DataDate<=? AND c.LockboxNumber = ?";

    private static final String SQL_GET_MAX_INDEX_LBCREDITITEMS2 = 
	"SELECT min( b.CreditItemIndex ),max( b.CreditItemIndex) FROM BS_Account a, BS_LBCreditItems b, BS_Lockbox c " +
	"WHERE a.AccountID=c.AccountID AND b.LockboxID=c.LockboxID AND a.AccountID=? AND a.RoutingNum=? " +
	"AND b.DataDate>=? AND c.LockboxNumber = ?";

    private static final String SQL_GET_MAX_INDEX_LBCREDITITEMS3 = 
	"SELECT min( b.CreditItemIndex ),max( b.CreditItemIndex) FROM BS_Account a, BS_LBCreditItems b, BS_Lockbox c " +
	"WHERE a.AccountID=c.AccountID AND b.LockboxID=c.LockboxID AND a.AccountID=? AND a.RoutingNum=? AND " +
	"b.DataDate<=?  AND c.LockboxNumber = ?";

    private static final String SQL_GET_MAX_INDEX_LBCREDITITEMS4 = 
	"SELECT min( b.CreditItemIndex ),max( b.CreditItemIndex) FROM BS_Account a, BS_LBCreditItems b, BS_Lockbox c " +
	"WHERE a.AccountID=c.AccountID AND b.LockboxID=c.LockboxID AND a.AccountID=? AND a.RoutingNum=? " +
	"AND c.LockboxNumber = ?";
    
    
    private static final String SQL_GET_RECENTITEMS = 
	"SELECT c.LockboxNumber, b.CreditItemIndex, b.DataDate, b.ItemID, b.DocumentType, b.Payor, b.Amount, " + 
	"b.CheckNumber, b.CheckDate, b.CouponAccountNum, b.CouponAmount1, b.CouponAmount2, b.CouponDate1, b.CouponDate2, " +
	"b.CouponRefNum, b.CheckRoutingNum, b.CheckAccountNum, b.LockboxWorkType, b.LockboxBatchNum, b.LockboxSeqNum, " +
	"b.Memo, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, b.ValueDateTime, b.BankRefNum, " +
	"b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBCreditItems b, BS_Lockbox c WHERE " +
	"a.AccountID=c.AccountID AND b.LockboxID=c.LockboxID AND a.AccountID=? AND a.RoutingNum=? " +
	"AND c.LockboxNumber = ? AND b.CreditItemIndex>=? AND b.CreditItemIndex<=? ORDER BY b.CreditItemIndex";

    private static final String SQL_GET_PREVIOUSITEMS = 
	"SELECT c.LockboxNumber, b.CreditItemIndex, b.DataDate, b.ItemID, b.DocumentType, b.Payor, b.Amount, " +
	"b.CheckNumber, b.CheckDate, b.CouponAccountNum, b.CouponAmount1, b.CouponAmount2, b.CouponDate1, b.CouponDate2, " +
	"b.CouponRefNum, b.CheckRoutingNum, b.CheckAccountNum, b.LockboxWorkType, b.LockboxBatchNum, b.LockboxSeqNum, " +
	"b.Memo, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, b.ValueDateTime, b.BankRefNum, " +
	"b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBCreditItems b, BS_Lockbox c WHERE " +
	"a.AccountID=c.AccountID AND b.LockboxID=c.LockboxID AND a.AccountID=? AND a.RoutingNum=? AND " +
	"c.LockboxNumber = ? AND b.CreditItemIndex>=? AND b.CreditItemIndex<=? ORDER BY b.CreditItemIndex";

    private static final String SQL_GET_NEXTITEMS = 
	"SELECT c.LockboxNumber, b.CreditItemIndex, b.DataDate, b.ItemID, b.DocumentType, b.Payor, b.Amount, " +
	"b.CheckNumber, b.CheckDate, b.CouponAccountNum, b.CouponAmount1, b.CouponAmount2, b.CouponDate1, b.CouponDate2, " +
	"b.CouponRefNum, b.CheckRoutingNum, b.CheckAccountNum, b.LockboxWorkType, b.LockboxBatchNum, b.LockboxSeqNum, " +
	"b.Memo, b.ImmedAvailAmount, b.OneDayAvailAmount, b.MoreOneDayAvailAm, b.ValueDateTime, b.BankRefNum, " +
	"b.CustRefNum, b.ExtendABeanXMLID FROM BS_Account a, BS_LBCreditItems b, BS_Lockbox c WHERE " +
	"a.AccountID=c.AccountID AND b.LockboxID=c.LockboxID AND a.AccountID=? AND a.RoutingNum=? AND " +
	"c.LockboxNumber = ? AND b.CreditItemIndex>=? AND b.CreditItemIndex<=? ORDER BY b.CreditItemIndex";


    private static final String SQL_ADD_LOCKBOX = "INSERT INTO BS_Lockbox( LockboxID, AccountID, LockboxNumber, ExtendABeanXMLID, Extra) VALUES(?, ?, ?, ?, ?)";

    private static final String SQL_GET_MAX_ID = "SELECT MAX(LockboxID) FROM BS_Lockbox";

    // property name for the minimum transaction index found within a giving paging range (getPagedTransactions)
    public static final String MINIMUM_TRANSACTION_INDEX_FOR_RANGE = "MINIMUM_TRANSACTION_INDEX_FOR_RANGE";

    // property name for the maximum transaction index found within a giving paging range (getPagedTransactions)
    public static final String MAXIMUM_TRANSACTION_INDEX_FOR_RANGE = "MAXIMUM_TRANSACTION_INDEX_FOR_RANGE";    


    // Operations on LockboxAccount Credit Items
    
    /*
	 Adds credit items into the repository for a specified lockbox
	 @param lockbox: the LockboxAccount for which we information has been supplied
	 @param items: a list of LockboxCreditItem objects to be added
	 @param dataSource: the source of the data
	 @param connection: Database connection object
    */
    public static void addItems( com.ffusion.beans.lockbox.LockboxAccount lockbox,
				  LockboxCreditItems items,
				  int dataSource,
				  DBConnection connection,
				  HashMap extra )
				  throws BSException
   {

	PreparedStatement stmt = null;
	PreparedStatement stmt1 = null;
	PreparedStatement stmt2 = null;
	LockboxCreditItem item = null;
	ResultSet rs = null;
	long index = 0;

	try {
	    // get the BAI filename and timestamp
	    String BAIFileIdentifier = null;
	    if( extra != null ) {
	    	BAIFileIdentifier = (String)extra.get( BSConstants.BAI_FILE_IDENTIFIER );
	    }
	    
	    
	    String accountID = lockbox.getAccountID();
    
	    // get the ExtendABean ID for the lockbox
	    long lockboxXMLID = BSExtendABeanXML.addExtendABeanXML( connection, lockbox.getExtendABeanXML() );


	    stmt = connection.prepareStatement( connection, SQL_ADD_LBCREDITITEM );
	    stmt1 = connection.prepareStatement( connection, SQL_ADD_LOCKBOX );
	    stmt2 = connection.prepareStatement( connection, SQL_GET_MAX_ID );

	    for( int i = 0; i < items.size(); i++ ) {
		item = (LockboxCreditItem)items.get( i );

		// check if lockbox account exists, if not add it in
		int lockboxID = BSAdapter.getLockboxID( connection, accountID, item.getLockboxNumber() );

		if( lockboxID == -1 ) {
		    // get a unique lockboxID
		    rs = DBConnection.executeQuery( stmt2, SQL_GET_MAX_ID );
		    if( rs.next() ){
			lockboxID = rs.getInt( 1 );
		    } else {
			// first row
			lockboxID = 1;
		    }
		    DBUtil.closeResultSet( rs );

		    // add entry to BS_lockbox		    
		    stmt1.setInt( 1, lockboxID );
		    stmt1.setString( 2, accountID );
		    stmt1.setString( 3, item.getLockboxNumber() );
		    stmt1.setLong( 4, lockboxXMLID );
		    stmt1.setString( 5, null );
		    DBConnection.executeUpdate( stmt1, SQL_ADD_LOCKBOX );

		    // also add a new counter
		    BSRecordCounter.addNewCounter( connection, BSRecordCounter.TYPE_LOCKBOX, String.valueOf(lockboxID), BSRecordCounter.LOCKBOX_CREDITITEM_INDEX );
		}

		// get the lockbox credititem index
		if( item.getItemIndex() != 0 ) {
		    throw new BSException( BSException.BSE_INVALID_CREDIT_ITEM_INDEX,
					   com.ffusion.banksim.db.MessageText.getMessage( com.ffusion.banksim.db.IBSErrConstants.ERR_INVALID_CREDIT_ITEM_INDEX ) );
		} else {
		    index = BSRecordCounter.getNextIndex( connection, BSRecordCounter.TYPE_LOCKBOX, String.valueOf(lockboxID), BSRecordCounter.LOCKBOX_CREDITITEM_INDEX );
		}

		// get the ExtendABean ID
		long extendABeanID = BSExtendABeanXML.addExtendABeanXML( connection, item.getExtendABeanXML() );

		stmt.setInt( 1, lockboxID );
		stmt.setLong( 2, index );
		BSUtil.fillTimestampColumn(connection,  stmt,  3, item.getProcessingDate() );
		stmt.setInt( 4, dataSource );
		stmt.setInt( 5, item.getItemID() );
		stmt.setString( 6, item.getDocumentType() );
		stmt.setString( 7, item.getPayer() );
		BSUtil.fillCurrencyColumn( stmt, 8, item.getCheckAmount() );
		stmt.setString( 9, item.getCheckNumber() );
		BSUtil.fillTimestampColumn(connection,  stmt,  10, item.getCheckDate() );
		stmt.setString( 11, item.getCouponAccountNumber() );
		BSUtil.fillCurrencyColumn( stmt,12, item.getCouponAmount1() );
		BSUtil.fillCurrencyColumn( stmt,13, item.getCouponAmount2() );
		BSUtil.fillTimestampColumn(connection,  stmt,  14, item.getCouponDate1() );
		BSUtil.fillTimestampColumn(connection,  stmt,  15, item.getCouponDate2() );
		stmt.setString( 16, item.getCouponReferenceNumber() );
		stmt.setString( 17, item.getCheckRoutingNumber() );
		stmt.setString( 18, item.getCheckAccountNumber() );
		stmt.setString( 19, item.getLockboxWorkType() );
		stmt.setString( 20, item.getLockboxBatchNumber() );
		stmt.setString( 21, item.getLockboxSequenceNumber() );
		stmt.setString( 22, item.getMemo() );
		BSUtil.fillCurrencyColumn( stmt, 23, item.getImmediateFloat() );
		BSUtil.fillCurrencyColumn( stmt, 24, item.getOneDayFloat() );
		BSUtil.fillCurrencyColumn( stmt, 25, item.getTwoDayFloat() );
		BSUtil.fillTimestampColumn(connection,  stmt,  26, item.getValueDateTime() );
		stmt.setString( 27, item.getBankReferenceNumber() );
		stmt.setString( 28, item.getCustomerReferenceNumber() );
		stmt.setLong( 29, extendABeanID );
		stmt.setString( 30, null );
		stmt.setString( 31, BAIFileIdentifier );
	
		DBConnection.executeUpdate( stmt, SQL_ADD_LBCREDITITEM);
	    }
	} catch ( Exception e ) {
	    throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if ( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }

	    if (stmt1 != null ) {
		DBConnection.closeStatement( stmt1 );
	    }

	    if (stmt2 != null ) {
		DBConnection.closeStatement( stmt2 );
	    }
	}
   }

    /**
    	Fill the bean with the information contained in the resultset
	@param lockbox: Lockbox account that the items are related to
	@param item: Lockbox item that needs to be filled in
	@param rs: ResultSet containing information that will be used to fill lockbox item with
    */
    private static void loadItem(DBConnection conn , com.ffusion.beans.lockbox.LockboxAccount lockbox,
    				  LockboxCreditItem item,
				  ResultSet rs )
				  throws Exception
    {
	item.setAccountID( lockbox.getAccountID() );
	item.setAccountNumber( lockbox.getAccountNumber() );
	item.setBankID( lockbox.getBankID() );
	item.setLockboxNumber( rs.getString( 1 )  );
	item.setItemIndex( rs.getLong( 2 ) );
	item.setProcessingDate( BSUtil.getTimestampColumn( rs.getTimestamp( 3 ) ) );
	item.setItemID( rs.getInt( 4 ) );
	item.setDocumentType( rs.getString( 5 ) );
	item.setPayer( rs.getString( 6 ) );
	item.setCheckAmount( BSUtil.getCurrencyColumn( rs.getBigDecimal( 7 ) ) );
	item.setCheckNumber( rs.getString( 8 ) );
	item.setCheckDate( BSUtil.getTimestampColumn( rs.getTimestamp( 9 ) ) );
	item.setCouponAccountNumber( rs.getString( 10 ) );
	item.setCouponAmount1( BSUtil.getCurrencyColumn( rs.getBigDecimal( 11 ) ) );
	item.setCouponAmount2( BSUtil.getCurrencyColumn( rs.getBigDecimal( 12 ) ) );
	item.setCouponDate1( BSUtil.getTimestampColumn( rs.getTimestamp( 13 ) ) );
	item.setCouponDate2( BSUtil.getTimestampColumn( rs.getTimestamp( 14 ) ) );
	item.setCouponReferenceNumber( rs.getString( 15 ) );
	item.setCheckRoutingNumber( rs.getString( 16 ) );
	item.setCheckAccountNumber( rs.getString( 17 ) );
	item.setLockboxWorkType( rs.getString( 18 ) );
	item.setLockboxBatchNumber( rs.getString( 19 ) );
	item.setLockboxSequenceNumber( rs.getString( 20 ) );
	item.setMemo( rs.getString( 21 ) );
	item.setImmediateFloat( BSUtil.getCurrencyColumn( rs.getBigDecimal( 22 ) ) );
	item.setOneDayFloat( BSUtil.getCurrencyColumn( rs.getBigDecimal( 23 ) ) );
	item.setTwoDayFloat( BSUtil.getCurrencyColumn( rs.getBigDecimal( 24 ) ) );
	item.setValueDateTime( BSUtil.getTimestampColumn( rs.getTimestamp( 25 ) ) );
	item.setBankReferenceNumber( rs.getString( 26 ) );
	item.setCustomerReferenceNumber( rs.getString( 27 ) );
	BSExtendABeanXML.fillExtendABean( conn,item, rs, 28 );
    }
    
    /*
	 Retrieves a list of credit items for a specified LockboxAccount between a
	 start date to an end date
	 @param lockbox: the LockboxAccount for which we want to retrieve credit items
	 @param lockboxNumber: the lockbox number for which we want to retrieve credit items	 
	 @param startDate: the start date of items to get or null for no start date
	 @param endDate: the end date of items to get or null for no end date
	 @return a list of LockboxCreditItem beans
    */
    public static LockboxCreditItems getItems( com.ffusion.beans.lockbox.LockboxAccount lockbox,
									 String lockboxNumber,
									 Calendar startDate,
									 Calendar endDate,
									 DBConnection connection,
									 HashMap extra )
	throws BSException
    {
	PreparedStatement stmt = null;
	LockboxCreditItems items = null;
	ResultSet rs = null;
	
	try {
	   
	    if( startDate != null ) {
		if( endDate != null ) {
		    stmt = connection.prepareStatement( connection, SQL_GET_LBCREDITITEMS1 );
		    stmt.setString( 1, lockbox.getAccountID() );
		    stmt.setString( 2, lockbox.getRoutingNumber() );
		    BSUtil.fillTimestampColumn(connection,  stmt,  3,  startDate );
		    BSUtil.fillTimestampColumn(connection,  stmt,  4,  endDate );
		    stmt.setString( 5, lockboxNumber );
		    rs = DBConnection.executeQuery( stmt, SQL_GET_LBCREDITITEMS1 );
		} else {
		    stmt = connection.prepareStatement( connection, SQL_GET_LBCREDITITEMS2 );
		    stmt.setString( 1, lockbox.getAccountID() );
		    stmt.setString( 2, lockbox.getRoutingNumber() );
		    BSUtil.fillTimestampColumn(connection,  stmt,  3,  startDate );
		    stmt.setString( 4, lockboxNumber );	    
		    rs = DBConnection.executeQuery( stmt, SQL_GET_LBCREDITITEMS2 );
		}
	    } else if( endDate != null ) {
		stmt = connection.prepareStatement( connection, SQL_GET_LBCREDITITEMS3 );
		stmt.setString(1, lockbox.getAccountID() );
		stmt.setString(2, lockbox.getRoutingNumber() );
		BSUtil.fillTimestampColumn(connection,  stmt,  3,  endDate );
		stmt.setString( 4, lockboxNumber );
		rs = DBConnection.executeQuery( stmt, SQL_GET_LBCREDITITEMS3 );
	    } else {
		stmt = connection.prepareStatement( connection, SQL_GET_LBCREDITITEMS4 );
		stmt.setString( 1, lockbox.getAccountID() );
		stmt.setString( 2, lockbox.getRoutingNumber() );
		stmt.setString( 3, lockboxNumber );
		rs = DBConnection.executeQuery( stmt, SQL_GET_LBCREDITITEMS4 );
	    }
	    
	    items = new LockboxCreditItems( );
	    LockboxCreditItem item = null;
	    while( rs.next() ) {
		item = items.create();
		loadItem( connection,lockbox, item, rs );
	    }
		DBUtil.closeResultSet( rs );
	    return items;

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
   	Retrieves a list of credit items for a specified lockbox between a
	start date to an end date, to a maximum of PAGESIZE of them
	@param lockbox: the lockbox for which we want to retrieve credit items
	@param lockboxNumber: the lockbox number for which we want to retrieve credit items
	@param startDate: the start date of items to get or null for no start date
	@param endDate: the end date of items to get or null for no end date
	@return a list of LockboxCreditItem beans
    */
	public static LockboxCreditItems getPagedLockboxCreditItems(
									    com.ffusion.beans.lockbox.LockboxAccount lockbox,
									    String lockboxNumber,
									    Calendar startDate,
									    Calendar endDate,
									    DBConnection connection,
									    HashMap extra )
									    throws BSException
    {
	PreparedStatement stmt = null;
	LockboxCreditItems items = null;
	ResultSet rs = null;
	
	try {
	    int pageSize = MapUtil.getIntValue( extra, BSConstants.PAGE_SIZE, BSConstants.PAGESIZE );
	    long index[] = getMaximumTransactionIndex( connection, lockbox, lockboxNumber, startDate, endDate );
	    long minIndex = index[ 0 ];
	    long maxIndex = index[ 1 ];	    

	    if( startDate != null ) {
		if( endDate != null ) {
		    stmt = connection.prepareStatement( connection, SQL_GET_PAGED_LBCREDITITEMS1 );
		    stmt.setString( 1, lockbox.getAccountID() );
		    stmt.setString( 2, lockbox.getRoutingNumber() );
		    BSUtil.fillTimestampColumn(connection,  stmt,  3,  startDate );
		    BSUtil.fillTimestampColumn(connection,  stmt,  4,  endDate );
		    stmt.setString( 5, lockboxNumber );
		    stmt.setLong( 6, minIndex );
		    stmt.setLong( 7, minIndex + (pageSize - 1) );
		    rs = DBConnection.executeQuery( stmt, SQL_GET_PAGED_LBCREDITITEMS1 );
		} else {
		    stmt = connection.prepareStatement( connection, SQL_GET_PAGED_LBCREDITITEMS2 );
		    stmt.setString( 1, lockbox.getAccountID() );
		    stmt.setString( 2, lockbox.getRoutingNumber() );
		    BSUtil.fillTimestampColumn(connection,  stmt,  3,  startDate );
		    stmt.setString( 4, lockboxNumber );
		    stmt.setLong( 5, minIndex );
		    stmt.setLong( 6, minIndex + (pageSize - 1) );
		    rs = DBConnection.executeQuery( stmt, SQL_GET_PAGED_LBCREDITITEMS2 );
		}
	    } else if( endDate != null ) {
		stmt = connection.prepareStatement( connection, SQL_GET_PAGED_LBCREDITITEMS3 );
		stmt.setString( 1, lockbox.getAccountID() );
		stmt.setString( 2, lockbox.getRoutingNumber() );
		BSUtil.fillTimestampColumn(connection,  stmt,  3,  endDate );
		stmt.setString( 4, lockboxNumber );
		stmt.setLong( 5, minIndex );
		stmt.setLong( 6, minIndex + (pageSize - 1) );
		rs = DBConnection.executeQuery( stmt, SQL_GET_PAGED_LBCREDITITEMS3 );
	    } else {
		stmt = connection.prepareStatement( connection, SQL_GET_PAGED_LBCREDITITEMS4 );
		stmt.setString( 1, lockbox.getAccountID() );
		stmt.setString( 2, lockbox.getRoutingNumber() );
		stmt.setString( 3, lockboxNumber );
		stmt.setLong( 4, minIndex );
		stmt.setLong( 5, minIndex + (pageSize - 1) );
		rs = DBConnection.executeQuery( stmt, SQL_GET_PAGED_LBCREDITITEMS4 );
	    }
	    
	    items = new LockboxCreditItems( );
	    LockboxCreditItem item = null;
	    while( rs.next() ) {
		item = items.create();
		loadItem( connection, lockbox, item, rs );
	    }
		DBUtil.closeResultSet( rs );

	    extra.put( DCConstants.MINIMUM_TRANSACTION_INDEX_FOR_RANGE, new Long( minIndex ) );
	    extra.put( DCConstants.MAXIMUM_TRANSACTION_INDEX_FOR_RANGE, new Long( maxIndex ) );

	    return items;

	} catch ( Exception e ) {
	    throw new BSException(BSException.BSE_DB_EXCEPTION,  e.getMessage() );
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
	 Retrieves the most recent credit items for the specified lockbox
	 @param lockbox: the LockboxAccount for which we want to retrieve credit items
	 @param lockboxNumber: the lockbox number for which we want to retrieve credit items
	 @return a list of LockboxCreditItem beans containing the items (at most PAGESIZE of them)
    */
    public static LockboxCreditItems getRecentItems( com.ffusion.beans.lockbox.LockboxAccount lockbox,
									       String lockboxNumber,
									       DBConnection connection,
									       HashMap extra )
	throws BSException
    {
	PreparedStatement stmt = null;
	LockboxCreditItems items = new LockboxCreditItems( );
	
	try {
	    int pageSize = MapUtil.getIntValue( extra, BSConstants.PAGE_SIZE, BSConstants.PAGESIZE );
	    long index = BSRecordCounter.getIndex( connection, lockbox.getAccountID(),
						   lockbox.getRoutingNumber(), lockboxNumber, 
						   BSRecordCounter.TYPE_LOCKBOX,
						   BSRecordCounter.LOCKBOX_TRANSACTION_INDEX );

	    if ( index != -1 ) {
		stmt = connection.prepareStatement( connection, SQL_GET_RECENTITEMS );
		stmt.setString( 1, lockbox.getAccountID() );
		stmt.setString( 2, lockbox.getRoutingNumber() );
		stmt.setString( 3, lockboxNumber );
		stmt.setLong( 4, index - pageSize + 1 );
		stmt.setLong( 5, index );
      		ResultSet rs = DBConnection.executeQuery( stmt, SQL_GET_RECENTITEMS );

		LockboxCreditItem item = null;
		while( rs.next() ) {
		    item = items.create();
		    loadItem(connection, lockbox, item, rs );
		}
	    DBUtil.closeResultSet( rs );
	    }
	    return items;

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
	 Retrieves the next batch of PAGESIZE credit items for the specified lockbox
	 @param lockbox: the LockboxAccount for which we want the credit items
	 @param lockboxNumber: the lockbox number for which we want to retrieve credit items
	 @param nextIndex: the next index of information to retrieve
	 @return a list of LockboxCreditItem beans containing the items (at most PAGE_SIZE of them)
    */
    public static LockboxCreditItems getNextItems( com.ffusion.beans.lockbox.LockboxAccount lockbox,
									     String lockboxNumber,
									     long nextIndex,
									     DBConnection connection,
									     HashMap extra )
	throws BSException
    {
	PreparedStatement stmt = null;
	LockboxCreditItems items = null;
	
	try {
	    int pageSize = MapUtil.getIntValue( extra, BSConstants.PAGE_SIZE, BSConstants.PAGESIZE );

	    stmt = connection.prepareStatement( connection, SQL_GET_NEXTITEMS );
	    stmt.setString( 1, lockbox.getAccountID() );
	    stmt.setString( 2, lockbox.getRoutingNumber() );
	    stmt.setString( 3, lockboxNumber );
	    stmt.setLong( 4, nextIndex);
	    stmt.setLong( 5, nextIndex + pageSize - 1);
	    ResultSet rs = DBConnection.executeQuery( stmt, SQL_GET_NEXTITEMS );
	    
	    items = new LockboxCreditItems( );
	    LockboxCreditItem item = null;
	    while( rs.next() ) {
		item = items.create();
		loadItem( connection,lockbox, item, rs );
	    }
	    DBUtil.closeResultSet( rs );
	    return items;

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
	 Retrieves the previous batch of PAGESIZE credit items for the specified lockbox
	 @param lockbox: the LockboxAccount for which we want to retrieve credit items
	 @param lockboxNumber: the lockbox number for which we want to retrieve credit items
	 @param lastIndex: the last index of information to retrieve
	 @return a list of LockboxCreditItem beans containing the items (at most PAGE_SIZE of them)
    */
    public static LockboxCreditItems getPreviousItems( com.ffusion.beans.lockbox.LockboxAccount lockbox,
										 String lockboxNumber,
										 long lastIndex,
										 DBConnection connection,
										 HashMap extra )
	throws BSException
    {
	PreparedStatement stmt = null;
	LockboxCreditItems items = null;
	
	try {
	    int pageSize = MapUtil.getIntValue( extra, BSConstants.PAGE_SIZE, BSConstants.PAGESIZE );

	    
	    stmt = connection.prepareStatement( connection, SQL_GET_PREVIOUSITEMS );
	    stmt.setString( 1, lockbox.getAccountID() );
	    stmt.setString( 2, lockbox.getRoutingNumber() );
	    stmt.setString( 3, lockboxNumber );
	    stmt.setLong( 4, lastIndex - pageSize + 1 );
	    stmt.setLong( 5, lastIndex );
	    ResultSet rs = DBConnection.executeQuery( stmt, SQL_GET_PREVIOUSITEMS );
	    
	    items = new LockboxCreditItems( );
	    LockboxCreditItem item = null;
	    while( rs.next() ) {
		item = items.create();
		loadItem(connection, lockbox, item, rs );
	    }
	    DBUtil.closeResultSet( rs );
	    return items;

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

    private static long[] getMaximumTransactionIndex( DBConnection connection, 
						    com.ffusion.beans.lockbox.LockboxAccount lockbox,
						    String lockboxNumber, Calendar startDate, Calendar endDate ) 
	throws Exception {
	ResultSet rs = null;
	PreparedStatement stmt = null;
	
	if( startDate != null ) {
	    if( endDate != null ) {
		stmt = connection.prepareStatement( connection, SQL_GET_MAX_INDEX_LBCREDITITEMS1 );
		stmt.setString( 1, lockbox.getAccountID() );
		stmt.setString( 2, lockbox.getRoutingNumber() );
		BSUtil.fillTimestampColumn(connection,  stmt,  3,  startDate );
		BSUtil.fillTimestampColumn(connection,  stmt,  4,  endDate );
		stmt.setString( 5, lockboxNumber );
		rs = DBConnection.executeQuery( stmt, SQL_GET_MAX_INDEX_LBCREDITITEMS1 );
	    } else {
		stmt = connection.prepareStatement( connection, SQL_GET_MAX_INDEX_LBCREDITITEMS2 );
		stmt.setString( 1, lockbox.getAccountID() );
		stmt.setString( 2, lockbox.getRoutingNumber() );
		BSUtil.fillTimestampColumn(connection,  stmt,  3,  startDate );
		stmt.setString( 4, lockboxNumber );
		rs = DBConnection.executeQuery( stmt, SQL_GET_MAX_INDEX_LBCREDITITEMS2 );
	    }
	} else if( endDate != null ) {
	    stmt = connection.prepareStatement( connection, SQL_GET_MAX_INDEX_LBCREDITITEMS3 );
	    stmt.setString( 1, lockbox.getAccountID() );
	    stmt.setString( 2, lockbox.getRoutingNumber() );
	    BSUtil.fillTimestampColumn(connection,  stmt,  3,  endDate );
	    stmt.setString( 4, lockboxNumber );
	    rs = DBConnection.executeQuery( stmt, SQL_GET_MAX_INDEX_LBCREDITITEMS3 );
	} else {
	    stmt = connection.prepareStatement( connection, SQL_GET_MAX_INDEX_LBCREDITITEMS4 );
	    stmt.setString( 1, lockbox.getAccountID() );
	    stmt.setString( 2, lockbox.getRoutingNumber() );
	    stmt.setString( 3, lockboxNumber );
	    rs = DBConnection.executeQuery( stmt, SQL_GET_MAX_INDEX_LBCREDITITEMS4 );
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
