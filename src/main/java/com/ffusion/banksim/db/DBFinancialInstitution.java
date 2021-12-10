//
// DBFinancialInstitution.java
//
// Copyright (c) 2002 Financial Fusion, Inc.
// All Rights Reserved
//

package com.ffusion.banksim.db;

import com.ffusion.banksim.interfaces.*;

import com.ffusion.beans.*;

import java.sql.SQLException;

public class DBFinancialInstitution 
{
    ///////////////////////////////////////////////////////////////////////
    // SQL Statements
    ///////////////////////////////////////////////////////////////////////
    private static final String DOES_FI_EXIST =
    	"select FIID from BS_FI where FIID=?";
    
    private static final String DOES_FI_NAME_EXIST =
    	"select Name, FIID from BS_FI where Name=?";
    
    private static final String INSERT_FI =
    	"insert into BS_FI( FIID, Name, Address1, Address2, " +
	"City, State, PostalCode, Country, Phone, EMailAddress, Host, Port ) " +
	    "values( ?,?,?,?,?,?,?,?,?,?,?,? )";

    private static final String GET_FI =
    	"Select FIID, Name, Address1, Address2, City, " +
	"State, PostalCode, Country, Phone, EMailAddress, Host, Port from " +
	"BS_FI where Name = ?";

    private static final String DELETE_FI =
    	"Delete from BS_FI where FIID = ?";

    private static final String DELETE_DEST_FI =
    	"Update BS_Transactions set DestFIID = NULL where DestFIID = ?";

    private static final String DELETE_ACCOUNT =
    	"Delete from BS_Account where FIID = ?";

    private static final String UPDATE_FI =
    	"Update BS_FI set Name = ?, Address1 = ?, Address2 = ?, " +
	"City = ?, State = ?, PostalCode = ?, Country = ?, Phone = ?, " +
	"EMailAddress = ? where FIID = ?";

    /**
    * doesBankExist - check to see if the specified bank exists in the database
    * @param bankID String object that specified the bank id
    * @param conn a DBConnection object that used to connect to BankSim database
    * @return boolean - indicate if the specified bank exists in the database
    */
    public static final boolean doesBankExist( String bankID, DBConnection conn )
    {
	DBResultSet rset = null;
	try {
	    rset = conn.prepareQuery( DOES_FI_EXIST );
	    Object[] params = { bankID };
	    rset.open( params );
	    while( rset.getNextRow() ) {
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
    * doesBankNameExist - check to see if the specified bank name exists in the database
    * @param name String object that specified the bank name
    * @param conn a DBConnection object that used to connect to BankSim database
    * @return boolean - indicate if the specified bank name exists in the database
    */
    public static final boolean doesBankNameExist( String name, DBConnection conn )
    {
	DBResultSet rset = null;
	try {
	    rset = conn.prepareQuery( DOES_FI_NAME_EXIST );
	    Object[] params = { name };
	    rset.open( params );
	    while( rset.getNextRow() ) {
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
    * addBank - add the specified bank into the database
    * @param bank a populated Bank object that contains the bank information
    * @param conn a DBConnection object that used to connect to BankSim database
    * @exception BSException that states the specified bank already exists in the database or wraps the SQL Exceptions thrown by other methods
    */
    public static final void addBank( Bank bank, DBConnection conn ) throws BSException
    {
	boolean isAutoCommit = false;

	try {
	    // Store the current transactional state and make sure we've
	    // turned off autocommit
	    isAutoCommit = conn.isAutoCommit();
	    if( isAutoCommit ) conn.setAutoCommit( false );

	    // Verify that there isn't already a bank with the same id in the table.
	    // It is preferable to throw a specific exception now than a general
	    // "Primary Key violation" exception later.
	    if( DBFinancialInstitution.doesBankExist( bank.getID(), conn ) ) {
		throw new BSException( BSException.BSE_FI_EXISTS,
			   MessageText.getMessage( IBSErrConstants.ERR_FI_EXISTS ) );

	    }

	    // Verify that there isn't already a bank with the same name in the table.
	    // It is preferable to throw a specific exception now than a general
	    // "Primary Key violation" exception later.
	    if( DBFinancialInstitution.doesBankNameExist( bank.getName(), conn ) ) {
		throw new BSException( BSException.BSE_FI_EXISTS,
			   MessageText.getMessage( IBSErrConstants.ERR_FI_NAME_EXISTS ) );

	    }

	    Object[] parms = {
		bank.getID(),
		bank.getName(),
		bank.getStreet(),
		bank.getStreet2(),
		bank.getCity(),
		bank.getState(),
		bank.getZipCode(),
		bank.getCountry(),
		bank.getPhone(),
		bank.getEmail(),
		"localhost", 		// Inter-BankSim communication not implemented.
		new Integer( 0 ), 	// Inter-BankSim communication not implemented.
	    };

	    conn.executeUpdate( INSERT_FI, parms );

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
    * getBank - gets the info for the specified bank
    * @param name String object that specifies the name of the bank
    * @param conn DBConnection object that used to connect to the BankSim datbase
    * @return Bank object that contains the bank info
    * @exception BSException that states the bank does not exist or wraps around SQL Exception thrown by other methods
    */
    public static final Bank getBank( String name, DBConnection conn ) throws BSException
    {
	Bank bank = null;
	try {
	    DBResultSet rset = conn.prepareQuery( GET_FI );
	    Object[] params_GB = { name };
	    rset.open( params_GB );
	    bank = new Bank();
	    if( rset.getNextRow() ) {
		// only id and name fields is from bank.java
		bank.setID( rset.getColumnString( 1 ) );
		bank.setName( rset.getColumnString( 2 ) );
		// the following fields are from contact.java
		bank.setStreet( rset.getColumnString( 3 ) );
		bank.setStreet2( rset.getColumnString( 4 ) );
		bank.setCity( rset.getColumnString( 5 ) );
		bank.setState( rset.getColumnString( 6 ) );
		bank.setZipCode( rset.getColumnString( 7 ) );
		bank.setCountry( rset.getColumnString( 8 ) );
		bank.setPhone( rset.getColumnString( 9 ) );
		bank.setEmail( rset.getColumnString( 10 ) );
		//TODO: setPort
		//TODO: setHost
	    }
	    rset.close();
	} catch( SQLException sqle ) {
	    throw new BSException( BSException.BSE_DB_EXCEPTION,
	    				DBSqlUtils.getRealSQLException( sqle ) );
	}
	return bank;
    }

    /**
    * deleteBank - delete the bank information from the database
    * @param bank a populated Bank object that contains the bank information
    * @param conn DBConnection object that used to connect to the BankSim database
    * @exception BSException that states the bank does not exist or wraps around the SQL Exception thrown by other methods
    */
    public final static void deleteBank( Bank bank, DBConnection conn ) throws BSException
    {
	// check to see if the bank exists in the database
	if( !doesBankExist( bank.getID(), conn ) )
	{
	    throw new BSException( BSException.BSE_FI_NOT_EXISTS,
	    		MessageText.getMessage( IBSErrConstants.ERR_FI_NOT_EXISTS ) );
	}
	boolean isAutoCommit = false;
	try {

	    // Store the current transactional state and make sure we've
	    // turned off autocommit
	    isAutoCommit = conn.isAutoCommit();
	    if( isAutoCommit ) conn.setAutoCommit( false );

	    Object[] params_DB = { bank.getID() };

	    // Delete the accounts in the specified bank
	    conn.executeUpdate( DELETE_ACCOUNT, params_DB );

	    // Set the destination financialinstitution id in BS_Transactions to null
	    conn.executeUpdate( DELETE_DEST_FI, params_DB );

	    // Delete the bank
	    conn.executeUpdate( DELETE_FI, params_DB );

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
    * updateBank - update the bank in the database with the provided information
    * @param bank populated Bank object that contains the latest information
    * @param conn DBConnection object that used to connect to the BankSim database
    * @exception BSException that states the bank does not exist in the database or wraps the SQLException throw by other methods
    */
    public static final void updateBank( Bank bank, DBConnection conn ) throws BSException
    {
	boolean isAutoCommit = false;

	try {
	    // Store the current transactional state and make sure we've
	    // turned off autocommit
	    isAutoCommit = conn.isAutoCommit();
	    if( isAutoCommit ) conn.setAutoCommit( false );

 	    // Verify that the bank id exists in the database
	    DBResultSet rset = conn.prepareQuery( DOES_FI_EXIST );
	    Object[] params_DFE = { bank.getID() };
	    rset.open( params_DFE );
	    if( !rset.getNextRow() )
	    {
		// bank doesn't exist in the database
		rset.close();
		throw new BSException( BSException.BSE_FI_NOT_EXISTS,
			   MessageText.getMessage( IBSErrConstants.ERR_FI_NOT_EXISTS ) );
	    }
	    rset.close();
	    // Verify that there isn't already an bank with the same bank name in the table.
	    rset = conn.prepareQuery( DOES_FI_NAME_EXIST );
	    Object[] params_DFNE = { bank.getName() };
	    rset.open( params_DFNE );
	    while( rset.getNextRow() ) {
		// we found a row 
		// check to see if it's the same bank
		if( rset.getColumnString( 2 ).equals( bank.getID() ) )
		{
		    break;
		}
		else
		// the new bank name is already exist in the database
		{
		    rset.close();
		    throw new BSException( BSException.BSE_FI_EXISTS,
			       MessageText.getMessage( IBSErrConstants.ERR_FI_NAME_EXISTS ) );
		}
	    }
	    rset.close();

	    Object[] params = {
		bank.getName(),
		bank.getStreet(),
		bank.getStreet2(),
		bank.getCity(),
		bank.getState(),
		bank.getZipCode(),
		bank.getCountry(),
		bank.getPhone(),
		bank.getEmail(),
		//TODO: setPort
		//TODO: setHost
		bank.getID(),
	    };

	    conn.executeUpdate( UPDATE_FI, params );

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
}
