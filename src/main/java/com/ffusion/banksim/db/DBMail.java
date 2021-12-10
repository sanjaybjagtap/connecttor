//
// DBMail.java
//
// Copyright (c) 2002 Financial Fusion, Inc.
// All Rights Reserved
//

package com.ffusion.banksim.db;

import com.ffusion.banksim.interfaces.BSConstants;
import com.ffusion.banksim.interfaces.BSException;
import com.ffusion.beans.DateTime;
import com.ffusion.beans.accounts.Account;
import com.ffusion.beans.accounts.Accounts;
import com.ffusion.beans.messages.Message;
import com.ffusion.beans.messages.Messages;
import com.ffusion.beans.user.User;

import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Vector;

public class DBMail
{
    ///////////////////////////////////////////////////////////////////////
    // SQL Statements
    ///////////////////////////////////////////////////////////////////////
    private static final String ADD_MAIL =
        //Begin Modify by TCG Team for MS SQL Support
    	"insert into BS_Mail( {||MessageID,|MessageID,|||MessageID,|MessageID,|MessageID,} CustomerID, SentDate, SentFrom, " +
	    "SentTo, Subject, Message, AccountNumber ) " +
	    "values( {||NEXTVAL FOR BS_MessageIDSequence,|BS_MessageIDSequence.NEXTVAL,|||NEXTVAL FOR BS_MessageIDSequence,|nextval('BS_MessageIDSequence'),|BS_MessageIDSequence.NEXTVAL,} " +
	    "?,?,?,?,?,?,?)";
        //End Modify

    private static final String GET_MAIL =
    	"Select MessageID, SentDate, SentFrom, SentTo, Subject, Message, AccountNumber FROM BS_Mail " +
	    "WHERE CustomerID in (Select CustomerID from BS_Customer where UserID = ?)";

    /**
    * addMailMessage - add a mail message to the bank sim
    * @param customer The customer sending the message
    * @param message The message object
    * @param conn a DBConnection object that used to connect to the BankSim database
    * @exception BSException that states the cusrtomer doesn't exist or wraps the SQL Exception thrown by other methods
    */
    public static final void addMailMessage( User 	customer,
    				   	     Message 		message,
					     DBConnection 	conn ) throws BSException
    {
	boolean isAutoCommit = false;

	try {
	    // Store the current transactional state and make sure we've
	    // turned off autocommit
	    isAutoCommit = conn.isAutoCommit();
	    if( isAutoCommit ) conn.setAutoCommit( false );

	    // Verify that the customer exists
	    if( !DBCustomer.doesCustomerExist( customer.getUserName(), conn ) ) {
		throw new BSException( BSException.BSE_CUSTOMER_NOT_EXISTS,
			    MessageText.getMessage( IBSErrConstants.ERR_CUSTOMER_NOT_EXISTS ) );
	    }

	    String accountNum = null;
	    if( message.containsKey( BSConstants.BS_ACCOUNT_NUMBER ) ) {
		accountNum = (String)message.get( BSConstants.BS_ACCOUNT_NUMBER );

		Accounts accounts = new Accounts();
		// Set the account number correctly
		Account account = accounts.create( accountNum, 0 );

		// Verify that there isn't already an account with the same id in the table.
		// It is preferable to throw a specific exception now than a general
		// "Primary Key violation" exception later.
		if( !DBAccount.doesAccountExist( account, conn ) ) {
		    throw new BSException( BSException.BSE_ACCOUNT_NOT_EXISTS,
			       MessageText.getMessage( IBSErrConstants.ERR_ACCOUNT_NOT_EXISTS ) );
		}
	    }

	    Object[] parms = {
		customer.getId(),
		new Long( System.currentTimeMillis() ),
		message.getFrom(),
		message.getTo(),
		message.getSubject(),
		message.getMemo(),
		accountNum,
	    };

	    conn.executeUpdate( ADD_MAIL, parms );

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
    * getMailMessages - Retrieve a user's mail
    * the information contained in the User object
    * note: this method assumes signOn has been called, so there is no need
    * to check if the customer exists
    * @param customer a populated User object
    * @param conn a DBConnection object that used to connect to the BankSim database
    * @return Enumeration of Message objects
    * @exception BSException that wraps around the SQL Exception
    */
    public static final Enumeration getMailMessages( User customer, DBConnection conn ) throws BSException
    {
	Enumeration e = null;
	try {
	    // Verify that the customer exists
	    if( !DBCustomer.doesCustomerExist( customer.getUserName(), conn ) ) {
		throw new BSException( BSException.BSE_CUSTOMER_NOT_EXISTS,
			    MessageText.getMessage( IBSErrConstants.ERR_CUSTOMER_NOT_EXISTS ) );
	    }

	    DBResultSet rset = conn.prepareQuery( GET_MAIL );
	    Object[] params_GAI = { customer.getUserName() };
	    rset.open( params_GAI );
	    // create new vector to temporarily store the messages
	    Vector v = new Vector();
	    // message filtered list
	    Messages messages = new Messages();
	    Message message = null;
	    DateTime date = null;
	    while( rset.getNextRow() ) {
		message = messages.create();
		message.setID( rset.getColumnString( 1 ) );

		date = new DateTime();
		date.setDate( rset.getColumnString( 2 ) );
		message.setDate( date );

		message.setFrom( rset.getColumnString( 3 ) );
		message.setTo( rset.getColumnString( 4 ) );
		message.setSubject( rset.getColumnString( 5 ) );
		message.setMemo( rset.getColumnString( 6 ) );

		// Set the account number in the ExtendABean
		if( rset.getColumnString( 7 ) != null ) {
		    message.put( BSConstants.BS_ACCOUNT_NUMBER, rset.getColumnString( 7 ) );
		}

		v.add( message );
	    }
	    rset.close();
	    e = v.elements();
	} catch( SQLException sqle ) {
	    throw new BSException( BSException.BSE_DB_EXCEPTION,
					DBSqlUtils.getRealSQLException( sqle ) );
	}
	return e;
    }

}
