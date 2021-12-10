//
// DBCustomer.java
//
// Copyright (c) 2002 Financial Fusion, Inc.
// All Rights Reserved
//

package com.ffusion.banksim.db;

import com.ffusion.banksim.interfaces.*;

import com.ffusion.beans.*;
import com.ffusion.beans.user.User;
import com.ffusion.beans.accounts.*;
import com.ffusion.beans.banking.*;

import java.sql.SQLException;
import java.util.*;

public class DBCustomer 
{
    ///////////////////////////////////////////////////////////////////////
    // SQL Statements
    ///////////////////////////////////////////////////////////////////////
    private static final String DOES_CUSTOMER_EXIST =
    	"select CustomerID from BS_Customer where UserID=?";

    private static final String DOES_CUSTOMER_ID_EXIST =
    	"select CustomerID from BS_Customer where CustomerID=?";

    private static final String DOES_USERNAME_EXIST =
    	"select CustomerID, UserID from BS_Customer where UserID=?";

    private static final String INSERT_CUSTOMER =
    	"insert into BS_Customer( CustomerID, UserID, Password, FirstName, MiddleName, " +
	"LastName, Address1, Address2, City, State, PostalCode, Country, DayPhone, " +
	"EveningPhone, EMailAddress ) " +
	    "values( ?,?,?,?,?,?,?,?,?,?,?,?,?,?,? )";

    private static final String UPDATE_CUSTOMER =
        "update BS_Customer set FirstName = ?, MiddleName = ?, " +
        "LastName = ?, Address1 = ?, Address2 = ?, City = ?, State = ?, " +
	"PostalCode = ?, Country = ?, DayPhone = ?, " +
	"EveningPhone = ?, EMailAddress = ? " +
	    "where UserID = ?";
	    
    private static final String UPDATE_PASSWORD =
        "update BS_Customer set Password = ? " +
	    "where UserID = ?";
	    
    private static final String DELETE_CUSTOMER =
    	"Delete from BS_Customer where UserID = ?";

    // Information about the default customer.  If the destination account in a transfer
    // does not exist, it is created as an account of this user.
    public static final String DEFAULT_CUSTOMER = "BANKSIM";
    public static final String DEFAULT_CUSTOMER_USERID = "banksim";
    public static final String DEFAULT_CUSTOMER_PASSWORD = "banksim";
	
    /**
    * doesCustomerExist - check to see if the customer exists
    * @param customerID String object 
    * @param conn DBConnection object that used to connect to the BankSim database
    * @return boolean to identify if the customer exists in the database
    */
    public static final boolean doesCustomerExist( String userName, DBConnection conn )
    {
	DBResultSet rset = null;
	try {
	    rset = conn.prepareQuery( DOES_CUSTOMER_EXIST );
	    Object[] params_DCE = { userName };
	    rset.open( params_DCE );
	    while( rset.getNextRow() ) {
		// we found a row ... so the ID is not going to be unique.
		rset.close();
		return true;
	    }
	    rset.close();
	    return false;
	} catch( Exception e ) {
	    // I don't expect anything truly bad to happen on a simple select...
	    try {
		rset.close();
	    } catch( Exception ex ) {}
	    return false;
	}
    }

    /**
    * addCustomer - add the customer into the database with the provided information
    * @param customer populated User object that contains the customer information
    * @param conn DBConnection object that used to connect to the BankSim database
    * @exception BSException that states the customer is already exist or wraps the SQLException throw by other methods
    */
    public static final void addCustomer( User customer, DBConnection conn ) throws BSException
    {
	boolean isAutoCommit = false;

	try {
	    // Store the current transactional state and make sure we've
	    // turned off autocommit
	    isAutoCommit = conn.isAutoCommit();
	    if( isAutoCommit ) conn.setAutoCommit( false );

	    // Verify that there isn't already an account with the same username in the table.
	    // It is preferable to throw a specific exception now than a general
	    // SQL exception later.
	    if( DBCustomer.doesCustomerExist( customer.getUserName(), conn ) ) {
		throw new BSException( BSException.BSE_USERNAME_EXISTS,
			   MessageText.getMessage( IBSErrConstants.ERR_USERNAME_EXISTS ) );
	    }
	    
	    // Verify that there isn't already an account with the same user name in the table.
	    DBResultSet rset = conn.prepareQuery( DOES_CUSTOMER_ID_EXIST );
	    Object[] params_DUE = { customer.getId() };
	    
	    rset.open( params_DUE );
	    while( rset.getNextRow() ) {
		    // customer id is unique, but user name is not
		    rset.close();
		    throw new BSException( BSException.BSE_CUSTOMER_EXISTS,
			       MessageText.getMessage( IBSErrConstants.ERR_CUSTOMER_EXISTS ) );
	    }
	    rset.close();

	    Object[] parms = {
		customer.getId(),
		customer.getUserName(),
		customer.getPassword(),
		customer.getFirstName(),
		customer.getMiddleName(),
		customer.getLastName(),
		customer.getStreet(),
		customer.getStreet2(),
		customer.getCity(),
		customer.getState(),
		customer.getZipCode(),
		customer.getCountry(),
		customer.getPhone(),
		customer.getPhone2(),
		customer.getEmail(),
	    };

	    conn.executeUpdate( INSERT_CUSTOMER, parms );

	    conn.commit();

	} catch( SQLException sqle ) {
	    // Roll back the transaction then wrap and rethrow sqle
	    try {
		conn.rollback();
	    } catch( SQLException sqle2 ) {
		// Just ignore this one and wrap and rethrow the previous
		// since it's probably related to what happened before
	    }
	    throw new BSException( BSException.BSE_DB_EXCEPTION,
				       DBSqlUtils.getRealSQLException( sqle ) );
	} finally {
	    // Must always turn on autocommit if we turned it off
	    try {
		if( isAutoCommit ) conn.setAutoCommit( true );
	    } catch( SQLException sqle ) {
		throw new BSException( BSException.BSE_DB_EXCEPTION,
			   DBSqlUtils.getRealSQLException( sqle ) );
	    }
	}
    }

    /**
    * addDefaultCustomer - add a  default customer into the database 
    * @param conn DBConnection object that used to connect to the BankSim database
    * @exception BSException that wraps the SQLException throw by another method
    */
    public static final void addDefaultCustomer( DBConnection conn ) throws BSException
    {
	// Do nothing if the default customer already exists.
	if( DBCustomer.doesCustomerExist( DBCustomer.DEFAULT_CUSTOMER_USERID, conn ) ) return;

	User customer = new User();

	customer.setId( DBCustomer.DEFAULT_CUSTOMER );
	customer.setUserName( DBCustomer.DEFAULT_CUSTOMER_USERID );
	customer.setPassword( DBCustomer.DEFAULT_CUSTOMER_PASSWORD );
	customer.setFirstName( "Default" );
	customer.setMiddleName( "BankSim" );
	customer.setLastName( "User" );
	customer.setStreet( "" );
	customer.setStreet2( "" );
	customer.setCity( "" );
	customer.setState( "" );
	customer.setZipCode( "" );
	customer.setCountry( "" );
	customer.setPhone( "(123)456-7890" );
	customer.setPhone2( "(123)456-7890" );
	customer.setEmail( "" );

	DBCustomer.addCustomer( customer, conn );
    }

    /**
    * updateCustomer - update the customer in the database with the provided information
    * @param customer populated User object that contains the latest information
    * @param conn DBConnection object that used to connect to the BankSim database
    * @exception BSException that states the customer is already exist or wraps the SQLException throw by other methods
    */
    public static final void updateCustomer( User customer, DBConnection conn ) throws BSException
    {
	boolean isAutoCommit = false;

	try {
	    // Store the current transactional state and make sure we've
	    // turned off autocommit
	    isAutoCommit = conn.isAutoCommit();
	    if( isAutoCommit ) conn.setAutoCommit( false );

	    // Verify that the user name exists in the database.
	    DBResultSet rset = conn.prepareQuery( DOES_USERNAME_EXIST );
	    Object[] params_DUE = { customer.getUserName() };
	    rset.open( params_DUE );
	    if( !rset.getNextRow() ) {
		// customer doesn't exist in the database
		rset.close();
		throw new BSException( BSException.BSE_CUSTOMER_NOT_EXISTS,
			       MessageText.getMessage( IBSErrConstants.ERR_CUSTOMER_NOT_EXISTS ) );
	    }
	    rset.close();

	    Object[] params = {
		customer.getFirstName(),
		customer.getMiddleName(),
		customer.getLastName(),
		customer.getStreet(),
		customer.getStreet2(),
		customer.getCity(),
		customer.getState(),
		customer.getZipCode(),
		customer.getCountry(),
		customer.getPhone(),
		customer.getPhone2(),
		customer.getEmail(),
		customer.getUserName(),
	    };

	    conn.executeUpdate( UPDATE_CUSTOMER, params );

	    conn.commit();

	} catch( SQLException sqle ) {
	    // Roll back the transaction then wrap and rethrow sqle
	    try {
		conn.rollback();
	    } catch( SQLException sqle2 ) {
		// Just ignore this one and wrap and rethrow the previous
		// since it's probably related to what happened before
	    }
	    throw new BSException( BSException.BSE_DB_EXCEPTION,
				       DBSqlUtils.getRealSQLException( sqle ) );
	} finally {
	    // Must always turn on autocommit if we turned it off
	    try {
		if( isAutoCommit ) conn.setAutoCommit( true );
	    } catch( SQLException sqle ) {
		throw new BSException( BSException.BSE_DB_EXCEPTION,
			   DBSqlUtils.getRealSQLException( sqle ) );
	    }
	}
    }

    /**
    * updatePassword - change only the customer's password
    * @param customer User object that contains at least the userID and new password
    * @param conn DBConnection object that used to connect to the BankSim database
    * @exception BSException that states the customer is already exist or wraps the SQLException throw by other methods
    */
    public static final void updatePassword( User customer, DBConnection conn ) throws BSException
    {
	boolean isAutoCommit = false;

	try {
	    // Store the current transactional state and make sure we've
	    // turned off autocommit
	    isAutoCommit = conn.isAutoCommit();
	    if( isAutoCommit ) conn.setAutoCommit( false );

	    // Verify that the user name exists in the database.
	    DBResultSet rset = conn.prepareQuery( DOES_USERNAME_EXIST );
	    Object[] params_DUE = { customer.getUserName() };
	    rset.open( params_DUE );
	    if( !rset.getNextRow() ) {
		// customer doesn't exist in the database
		rset.close();
		throw new BSException( BSException.BSE_CUSTOMER_NOT_EXISTS,
			       MessageText.getMessage( IBSErrConstants.ERR_CUSTOMER_NOT_EXISTS ) );
	    }
	    rset.close();

	    Object[] params = {
		customer.getPassword(),
		customer.getUserName(),
	    };

	    conn.executeUpdate( UPDATE_PASSWORD, params );

	    conn.commit();

	} catch( SQLException sqle ) {
	    // Roll back the transaction then wrap and rethrow sqle
	    try {
		conn.rollback();
	    } catch( SQLException sqle2 ) {
		// Just ignore this one and wrap and rethrow the previous
		// since it's probably related to what happened before
	    }
	    throw new BSException( BSException.BSE_DB_EXCEPTION,
				       DBSqlUtils.getRealSQLException( sqle ) );
	} finally {
	    // Must always turn on autocommit if we turned it off
	    try {
		if( isAutoCommit ) conn.setAutoCommit( true );
	    } catch( SQLException sqle ) {
		throw new BSException( BSException.BSE_DB_EXCEPTION,
			   DBSqlUtils.getRealSQLException( sqle ) );
	    }
	}
    }

    /**
    * deleteCustomer - delete the customer information from the database
    * @param customer a populated Customer object that contains the customer information
    * @param conn DBConnection object that used to connect to the BankSim database
    * @exception BSException that states the customer does not exist or wraps around the SQL Exception thrown by other methods
    */
    public final static void deleteCustomer( User customer, DBConnection conn ) throws BSException
    {
	// check to see if the Customer exists in the database
	if( !doesCustomerExist( customer.getUserName(), conn ) )
	{
	    throw new BSException( BSException.BSE_CUSTOMER_NOT_EXISTS,
	    		MessageText.getMessage( IBSErrConstants.ERR_CUSTOMER_NOT_EXISTS ) );
	}
	boolean isAutoCommit = false;
	try {

	    // Store the current transactional state and make sure we've
	    // turned off autocommit
	    isAutoCommit = conn.isAutoCommit();
	    if( isAutoCommit ) conn.setAutoCommit( false );

	    // Retrieve the accounts under this customer
	    Enumeration e = DBAccount.getAccounts( customer, conn );

	    // Remove the accounts
	    while( e.hasMoreElements() )
	    {
		DBAccount.deleteAccount( ( Account ) e.nextElement(), conn );
	    }

	    // Remove the customer
	    Object[] params_DC = { customer.getUserName() };
	    conn.executeUpdate( DELETE_CUSTOMER, params_DC );

	    conn.commit();

	} catch( SQLException sqle ) {
	    // Roll back the transaction then wrap and rethrow sqle
	    try {
		conn.rollback();
	    } catch( SQLException sqle2 ) {
		// Just ignore this one and wrap and rethrow the previous
		// since it's probably related to what happened before
	    }
	    throw new BSException( BSException.BSE_DB_EXCEPTION,
				       DBSqlUtils.getRealSQLException( sqle ) );
	// catch the BSException thrown by DBAccount.getAccounts and DBAccount.deleteAccount
	} catch( BSException bse ) {
	    throw new BSException( bse.getErrorCode(), bse );
	} finally {
	    // Must always turn on autocommit if we turned it off
	    try {
		if( isAutoCommit ) conn.setAutoCommit( true );
	    } catch( SQLException sqle ) {
		throw new BSException( BSException.BSE_DB_EXCEPTION,
			   DBSqlUtils.getRealSQLException( sqle ) );
	    }
	}
    }
}
