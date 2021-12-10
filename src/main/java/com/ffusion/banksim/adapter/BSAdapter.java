//
// BSAdapter.java
//
// Copyright (c) 2002 Financial Fusion, Inc.
// All Rights Reserved.
//
package com.ffusion.banksim.adapter;

import java.sql.*;
import java.io.*;
import java.util.*;

import com.ffusion.beans.accounts.*;
import com.ffusion.beans.banking.Transaction;
import com.ffusion.beans.common.Currency;
import com.ffusion.beans.lockbox.*;
import com.ffusion.beans.disbursement.*;
import com.ffusion.beans.reporting.*;


import com.ffusion.banksim.db.*;
import com.ffusion.beans.DateTime;
import com.ffusion.util.db.DBUtil;
import com.ffusion.util.logging.DebugLog;
import java.util.logging.Level;
import com.ffusion.banksim.interfaces.*;
import com.ffusion.banksim.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BSAdapter {
	private static final Logger logger = LoggerFactory.getLogger(BSAdapter.class);

    private static String poolName = null; // name of DBConnection pool

    public static String CONNECTIONTYPE = null; // type of connection

    public static BSDBParams dbProps = null;



    // SQL Queries
    private static final String SQL_GET_ACCOUNTID = "SELECT AccountID FROM BS_Account WHERE AccountNumber=? AND RoutingNum=?";
    
    private static final String SQL_GET_LOCKBOXID = "SELECT LockboxID FROM BS_Lockbox WHERE AccountID=? AND LockboxNumber=?";


    // Data source enumerations
    public static final int LIVE_DATA = 1;
    public static final int BAI_DATA = 2;
    
    /**
	In the BS_AccountID table, the combination of Account Number and BankID must be unique
    	Returns the BSAccountID given a accountNumber and bankID if the account exists, -1 otherwise
    */
    public static String getAccountID( DBConnection connection, String accountNumber, String routingNum ) throws Exception
    {
	PreparedStatement stmt1 = null;
	try {
	    // get the AccountID
	    stmt1 = connection.prepareStatement( connection, SQL_GET_ACCOUNTID );
	    stmt1.setString( 1, accountNumber );
	    stmt1.setString( 2, routingNum );
	    ResultSet rs1 = DBConnection.executeQuery( stmt1, SQL_GET_ACCOUNTID );
	    String accountID = "-1";
	    
	    if( rs1.next() ) {
		accountID = rs1.getString( BSConstants.ACCOUNTID );
	    }
	    DBUtil.closeResultSet( rs1 );
	    return accountID;

	} catch ( Exception e ) {
	    throw new BSException( BSException.BSE_DB_EXCEPTION, "Error occurred while trying to get AccountID." );
	} finally {
	    if( stmt1 != null ) {
		DBConnection.closeStatement( stmt1 );
	    }
	}
    }

    /**
    	returns the BSLockboxID if the lockbox Account exists, -1 otherwise
    */
    public static int getLockboxID( DBConnection connection, String accountID, String lockboxNumber ) throws Exception
    {
	PreparedStatement stmt1 = null;
	try {
	    // get the LockboxID
	    stmt1 = connection.prepareStatement( connection, SQL_GET_LOCKBOXID );
	    stmt1.setString( 1, accountID );
	    stmt1.setString( 2, lockboxNumber );
	    ResultSet rs1 = DBConnection.executeQuery( stmt1, SQL_GET_LOCKBOXID );
	    int BSLockboxID = -1;
	    
	    if( rs1.next() ) {
		BSLockboxID = rs1.getInt( BSConstants.LOCKBOXID );
	    }
	    DBUtil.closeResultSet( rs1 );
	    return BSLockboxID;

	} catch ( Exception e ) {
	    throw new BSException(BSException.BSE_DB_EXCEPTION,  "Error occurred while trying to get LockboxID." );
	} finally {
	    if( stmt1 != null ) {
		DBConnection.closeStatement( stmt1 );
	    }
	}
    }
    
	/**
	* NOTE: THIS method is for TESTING purposes only.  This is by no means production-level, and will not correctly insert exactly how it should normally be done.
	* Adds a transactions to the banksim database given an account, transaction and possibly other extraneous objects passed in the extra hash map.
	* @param account the from-account of the transaction you wish to add
	* @param transaction the transaction you wish to add
	* @param extra the hash map contain any other objects that may be required to properly add the transaction.
	*/
    public static void addTransaction( Account account, Transaction transaction, HashMap extra ) throws Exception {
        DBTransaction.addTransaction( account, transaction, extra );
    }

}
