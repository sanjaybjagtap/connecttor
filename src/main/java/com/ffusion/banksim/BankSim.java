//
// BankSim.java
//
// Copyright (c) 2002 Financial Fusion, Inc.
// All Rights Reserved.
//

package com.ffusion.banksim;

import java.io.IOException;
import java.io.InputStream;
import java.text.Format;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;

import javax.swing.Timer;

import org.slf4j.LoggerFactory;

import com.ffusion.banksim.adapter.BSDsbSummary;
import com.ffusion.banksim.adapter.BSDsbTransactions;
import com.ffusion.banksim.adapter.BSLBCreditItems;
import com.ffusion.banksim.adapter.BSLBTransactions;
import com.ffusion.banksim.adapter.BSLockboxSummary;
import com.ffusion.banksim.db.DBAccount;
import com.ffusion.banksim.db.DBClient;
import com.ffusion.banksim.db.DBConnection;
import com.ffusion.banksim.db.DBConnectionPool;
import com.ffusion.banksim.db.DBCustomer;
import com.ffusion.banksim.db.DBFinancialInstitution;
import com.ffusion.banksim.db.DBMail;
import com.ffusion.banksim.db.DBTransaction;
import com.ffusion.banksim.db.DBTransfer;
import com.ffusion.banksim.interfaces.BSConstants;
import com.ffusion.banksim.interfaces.BSDBParams;
import com.ffusion.banksim.interfaces.BSException;
import com.ffusion.beans.Bank;
import com.ffusion.beans.accounts.Account;
import com.ffusion.beans.banking.Transaction;
import com.ffusion.beans.banking.Transactions;
import com.ffusion.beans.banking.Transfer;
import com.ffusion.beans.messages.Message;
import com.ffusion.beans.reporting.ReportCriteria;
import com.ffusion.beans.user.User;
import com.ffusion.reporting.interfaces.IReportResult;
import com.ffusion.util.DateConsts;
import com.ffusion.util.ResourceUtil;
import com.ffusion.util.beans.PagingContext;

public class BankSim
{
	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(BankSim.class);

    private static DBConnectionPool connPool;
    private static boolean initialized = false;
    private static Timer pagedTransactionCleanupTimer;
    private static Properties delayProps;

    /**
     * getBSDBParams - API to easily allow applications to get a BSDBParams object
     * @param user database user name
     * @param password database password
     * @param url jdbc url to database
     * @param dbType BSDBParams database type constant (ie, BSDBParams.CONN_DB2
     * @param useNativeDriver flag to determine if a native driver should be used
     * @return a populated BSDBParams object, suitable for calling BankSim.initialize()
     * @exception BSException if the dbType is unsupported
     */
    public static BSDBParams getBSDBParams( String user,
    				      	    String password,
				            String url,
				            int dbType,
				            boolean useNativeDriver ) throws BSException
    {
	switch( dbType ) {
	    case BSDBParams.CONN_DB2:
	        if( useNativeDriver ) {
	            return BSDBParams.createDB2AppParams( user, password, url );
    		} else {
    		    return BSDBParams.createDB2NetParams( user, password, url );
    		}

        case BSDBParams.CONN_DB2_UN2:
            return BSDBParams.createDB2UN2Params( user, password, url );
        
	    case BSDBParams.CONN_DB2390:
		return BSDBParams.createDB2390Params( user, password, url );

	    case BSDBParams.CONN_ORACLE:
	    	return BSDBParams.createOracleParams( user, password, url, useNativeDriver );

	    case BSDBParams.CONN_ASA:
	    	return BSDBParams.createASAJConnectParams( user, password, url );

	    case BSDBParams.CONN_ASE:
	        return BSDBParams.createASEJConnectParams( user, password, url );
        //Begin Add by TCG Team for MS SQL Support
        case BSDBParams.CONN_MSSQL:
			return BSDBParams.createMSSQLParams(user, password, url);
        case BSDBParams.CONN_POSTGRESQL:
			return BSDBParams.createPostgreSQLParams(user, password, url);
        case BSDBParams.CONN_HANA:
			return BSDBParams.createHanaParams(user, password, url);
	   //End Add

	    default:
		// The exception cannot be logged since we do not know if
		// the BankSim has been initialized
	        throw new BSException( BSException.BSE_DB_UNKNOWN_CONN_TYPE,
	    			   MessageText.getMessage( IBSErrConstants.ERR_UNKNOWN_DB_TYPE ) );
	}
    }

    /**
     * getBSDBParams - API to easily allow applications to get a BSDBParams object
     * @param user database user name
     * @param password database password
     * @param host host machine name
     * @param port host machine port number (in a String object)
     * @param dbName name of the database to connect to
     * @param dbType BSDBParams database type constant (ie, BSDBParams.CONN_DB2
     * @param useNativeDriver flag to determine if a native driver should be used
     * @return a populated BSDBParams object, suitable for calling BankSim.initialize()
     * @exception BSException if the dbType is unsupported
     */
    public static BSDBParams getBSDBParams( String user,
    				      	    String password,
				            String host,
				            String port,
				            String dbName,
				            int dbType,
				            boolean useNativeDriver ) throws BSException
    {
	switch( dbType ) {
	    case BSDBParams.CONN_DB2:
	        if( useNativeDriver ) {
		    return BSDBParams.createDB2AppParams( user, password, host, port, dbName );
		} else {
		    return BSDBParams.createDB2NetParams( user, password, host, port, dbName );
		}

        case BSDBParams.CONN_DB2_UN2:
            return BSDBParams.createDB2UN2Params( user, password, host, port, dbName );
        
	    case BSDBParams.CONN_DB2390:
		return BSDBParams.createDB2390Params( user, password, host, port, dbName );

	    case BSDBParams.CONN_ORACLE:
	    	return BSDBParams.createOracleParams( user, password, host, port, dbName, useNativeDriver );

	    case BSDBParams.CONN_ASA:
	    	return BSDBParams.createASAJConnectParams( user, password, host, port, dbName );

	    case BSDBParams.CONN_ASE:
	        return BSDBParams.createASEJConnectParams( user, password, host, port, dbName );
        //Begin Add by TCG Team for MS SQL Support
        case BSDBParams.CONN_MSSQL:
	        return BSDBParams.createMSSQLParams( user, password, host, port, dbName );
        case BSDBParams.CONN_POSTGRESQL:
	        return BSDBParams.createPostgreSQLParams( user, password, host, port, dbName );
      //End Add
	    default:
		// The exception cannot be logged since we do not know if
		// the BankSim has been initialized
	        throw new BSException( BSException.BSE_DB_UNKNOWN_CONN_TYPE,
	    			   MessageText.getMessage( IBSErrConstants.ERR_UNKNOWN_DB_TYPE ) );
	}
    }

    /**
     * getBSDBParams - API to easily allow applications to get a BSDBParams object
     * @param user database user name
     * @param password database password
     * @param url jdbc url to database
     * @param dbType BSDBParams database type constant (ie, BSDBParams.CONNSTR_DB2
     * @param useNativeDriver flag to determine if a native driver should be used
     * @return a populated BSDBParams object, suitable for calling BankSim.initialize()
     * @exception BSException if the dbType is unsupported
     */
    public static BSDBParams getBSDBParams( String user,
    				      	    String password,
				            String url,
				            String dbType,
				            boolean useNativeDriver ) throws BSException
    {    	
	if( dbType.equals( BSDBParams.CONNSTR_ASA ) ) {
	    return BankSim.getBSDBParams( user, password, url, BSDBParams.CONN_ASA, useNativeDriver );

	} else if( dbType.equals( BSDBParams.CONNSTR_ASE ) ) {
	    return BankSim.getBSDBParams( user, password, url, BSDBParams.CONN_ASE, useNativeDriver );

	} else if( dbType.equals( BSDBParams.CONNSTR_DB2 ) ) {
	    return BankSim.getBSDBParams( user, password, url, BSDBParams.CONN_DB2, useNativeDriver );

    } else if( dbType.equals( BSDBParams.CONNSTR_DB2_UN2 ) ) {
        return BankSim.getBSDBParams( user, password, url, BSDBParams.CONN_DB2_UN2, true );

	} else if( dbType.equals( BSDBParams.CONNSTR_DB2390 ) ) {
	    return BankSim.getBSDBParams( user, password, url, BSDBParams.CONN_DB2390, useNativeDriver );

	} else if( dbType.equals( BSDBParams.CONNSTR_ORACLE ) ) {
	    return BankSim.getBSDBParams( user, password, url, BSDBParams.CONN_ORACLE, useNativeDriver );
	//Begin Add by TCG Team for MS SQL Support
	} else if( dbType.equals( BSDBParams.CONNSTR_POSTGRESQL ) ) {
	    return BankSim.getBSDBParams( user, password, url, BSDBParams.CONN_POSTGRESQL, useNativeDriver );
	//Begin Add by TCG Team for MS SQL Support
	} else if( dbType.equals( BSDBParams.CONNSTR_MSSQL ) ) {
	    return BankSim.getBSDBParams( user, password, url, BSDBParams.CONN_MSSQL, useNativeDriver );
    //End Add
    }  else if( dbType.equals( BSDBParams.CONNSTR_HANA ) ) {
	    return BankSim.getBSDBParams( user, password, url, BSDBParams.CONN_HANA, useNativeDriver ); 
	} else {
	    // The exception cannot be logged since we do not know if
	    // the BankSim has been initialized
	    throw new BSException( BSException.BSE_DB_UNKNOWN_CONN_TYPE,
			       MessageText.getMessage( IBSErrConstants.ERR_UNKNOWN_DB_TYPE ) );
	}
    }

    /**
     * getBSDBParams - API to easily allow applications to get a BSDBParams object
     * @param user database user name
     * @param password database password
     * @param host host machine name
     * @param port host machine port number (in a String object)
     * @param dbName name of the database to connect to
     * @param dbType BSDBParams database type constant (ie, BSDBParams.CONNSTR_DB2
     * @param useNativeDriver flag to determine if a native driver should be used
     * @return a populated BSDBParams object, suitable for calling BankSim.initialize()
     * @exception BSException if the dbType is unsupported
     */
    public static BSDBParams getBSDBParams( String user,
    				      	    String password,
				            String host,
				            String port,
				            String dbName,
				            String dbType,
				            boolean useNativeDriver ) throws BSException
    {
	if( dbType.equals( BSDBParams.CONNSTR_ASA ) ) {
	    return BankSim.getBSDBParams( user, password, host, port, dbName, BSDBParams.CONN_ASA, useNativeDriver );

	} else if( dbType.equals( BSDBParams.CONNSTR_ASE ) ) {
	    return BankSim.getBSDBParams( user, password, host, port, dbName, BSDBParams.CONN_ASE, useNativeDriver );

	} else if( dbType.equals( BSDBParams.CONNSTR_DB2 ) ) {
	    return BankSim.getBSDBParams( user, password, host, port, dbName, BSDBParams.CONN_DB2, useNativeDriver );

    } else if( dbType.equals( BSDBParams.CONNSTR_DB2_UN2 ) ) {
        return BankSim.getBSDBParams( user, password, host, port, dbName, BSDBParams.CONN_DB2_UN2, true );

	} else if( dbType.equals( BSDBParams.CONNSTR_DB2390 ) ) {
	    return BankSim.getBSDBParams( user, password, host, port, dbName, BSDBParams.CONN_DB2390, useNativeDriver );

	} else if( dbType.equals( BSDBParams.CONNSTR_ORACLE ) ) {
	    return BankSim.getBSDBParams( user, password, host, port, dbName, BSDBParams.CONN_ORACLE, useNativeDriver );
	//Begin Add by TCG Team for MS SQL Support
	} else if( dbType.equals( BSDBParams.CONNSTR_MSSQL ) ) {
	    return BankSim.getBSDBParams( user, password, host, port, dbName, BSDBParams.CONN_MSSQL, useNativeDriver );
    //End Add
	} else if( dbType.equals( BSDBParams.CONNSTR_POSTGRESQL ) ) {
	    return BankSim.getBSDBParams( user, password, host, port, dbName, BSDBParams.CONN_POSTGRESQL, useNativeDriver );
    //End Add
    } else {
	    // The exception cannot be logged since we do not know if
	    // the BankSim has been initialized
	    throw new BSException( BSException.BSE_DB_UNKNOWN_CONN_TYPE,
			       MessageText.getMessage( IBSErrConstants.ERR_UNKNOWN_DB_TYPE ) );
	}
    }

    /**
    * initialize - Initialize a banksim with DBParameters and connection pool size
    * @param params BSDBParams connection parameters to the banksim db
    * @param poolSize The maximum size of the connection pool
    * @exception BSException that states the banksim is already initialized
    */
    public static void initialize( BSDBParams params, int poolSize ) throws BSException
    {
	BankSim.initialize( params, poolSize, null );
    }

    public static DBConnection getDBConnection() throws Exception
    {
	if( initialized ) {
	    return connPool.getConnection();
	} else { 
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:signOn", exc);
	    throw exc;
	}
    }

    /**
    * initialize - Initialize a banksim with DBParameters and connection pool size
    * and initial properties
    * @param params BSDBParams objectconnection parameters to the banksim db
    * @param poolSize The maximum size of the connection pool
    * @param props initial properties to use.  May be null.
    * @exception BSException that states the banksim is already initialized
    */
    public static synchronized void initialize( BSDBParams params, int poolSize, Properties props ) throws BSException
    {
	if( initialized ) {
		return;
	    /*BSException exc = new BSException( BSException.BSE_ALREADY_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_ALREADY_INITIALIZED ) );

	    com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:initialize", exc);
	    throw exc;*/
	}

	connPool = new DBConnectionPool( params, poolSize );

        DBConnection conn = connPool.getConnection();

	try {
	    // Create the default customer.  If the destination account in a transfer
	    // does not exist, it will be created as belonging to the default custoner.
	    // If the default customer already exists, addDefaultCustomer() does nothing.
	    DBCustomer.addDefaultCustomer( conn );

	} catch( BSException e ) {
	    BSException exc = new BSException( e.getErrorCode(), e );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:initialize", exc);
	    throw exc;
	} finally {
	    connPool.releaseConnection( conn );
	}

	// Create a timer to close paged transactions that have been unused for a certain
	// period of time.  The timer will fire every 15 minutes.
	pagedTransactionCleanupTimer = new Timer( 15 * 60 * 1000, new PagedTransactionCleanup() );
	pagedTransactionCleanupTimer.start();

	// Read in the delay settings from the properties file.
	if( props == null ) {
	    delayProps = new Properties();
	} else {
	    delayProps = new Properties( props );
	}
	try {
	    // If the file doesn't exist, we don't want to do anything (yet)
	    // Use no delay
		InputStream is = null;
		is = ResourceUtil.getResourceAsStream(new BankSim(), BSConstants.BANKSIM_PROPERTIES );
	    if( is != null ) {
		delayProps.load( is );
	    }
	} catch( IOException io ) {
	    // TODO: Determine correct behaviour
	}

	initialized = true;
    }

    /**
    * isInitialized - Check to see if the banksim is initialized or not
    */
    public static boolean isInitialized()
    {
	return initialized;
    }

    private static void doDelay( String delayConst, long startTime )
    {
	String generalDelay = delayProps.getProperty( BSConstants.MIN_TIME_GENERAL );
	if( generalDelay == null ) {
	    generalDelay = "0";
	}

	String delay = delayProps.getProperty( delayConst, generalDelay );

	try {
	    long minDelay = Long.parseLong( delay );
	    long delayRemaining = minDelay - ( System.currentTimeMillis() - startTime );

	    if( delayRemaining > 0 ) {
		Thread.sleep( delayRemaining );
	    }
	} catch( InterruptedException ex ) {
	    // I don't think there's anything we can do other
	    // than return prematurely.
	}
    }

    /**
    * signOn - Try to signon users with the specified userID and password
    * @param userID A String
    * @param password A String
    * @return User object that contains the user's information
    * @exception BSException that states the banksim is not initialized or signon failed
    */
    public static User signOn( String userID, String password ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:signOn", exc);
	    throw exc;
	}
	User client = null;
	DBConnection conn = connPool.getConnection();
	try {
	    client = DBClient.signOn( userID, password, conn );
	} catch( BSException bse ) {
	    BSException exc = new BSException( bse.getErrorCode(), bse );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:signOn", exc);
	    throw exc;
	} finally {
	    connPool.releaseConnection( conn );
	}

	BankSim.doDelay( BSConstants.MIN_TIME_SIGNON, methodStart );
	return client;
    }

    /**
    * setPassword - Set the password for the specified users assuming the users has signon properly
    * @param customer A String
    * @param newPassword A String
    * @exception BSException that states the banksim is not initialized or exception from updateCustomer()
    */
    public static void setPassword( User customer, String newPassword ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:setPassword", exc);
	    throw exc;
	}
	customer.setPassword( newPassword );
	DBConnection conn = connPool.getConnection();
	try {
	    DBCustomer.updatePassword( customer, conn );
	} catch( BSException bse ) {
	    BSException exc = new BSException( bse.getErrorCode(), bse );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:setPassword", exc);
	    throw exc;
	}

	BankSim.doDelay( BSConstants.MIN_TIME_SET_PASSWORD, methodStart );
    }

    /**
     * addBank - Adds a bank to the Bank Simulator
     * @param bank a populated Bank object whose Bank is being added.
     */
    private static void addBank( Bank bank, boolean delay ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:addBank", exc);
	    throw exc;
	}

        DBConnection conn = connPool.getConnection();
	try {
	    DBFinancialInstitution.addBank( bank, conn );
	} catch( BSException e ) {
	    BSException exc = new BSException( e.getErrorCode(), e );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:addBank", exc);
	    throw exc;
	} finally {
	    connPool.releaseConnection( conn );
	}

	if( delay ) {
	    BankSim.doDelay( BSConstants.MIN_TIME_ADD_BANK, methodStart );
	}
    }

    /**
     * addBank - Adds a bank to the Bank Simulator
     * @param bank a populated Bank object whose Bank is being added.
     */
    public static void addBank( Bank bank ) throws BSException
    {
	BankSim.addBank( bank, true );
    }

    /**
     * addBanks - Adds a bank to the Bank Simulator
     * @param banks a populated Bank array containing Bank objects to add.
     */
    public static void addBanks( Bank[] banks ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	for( int i = 0; i < banks.length; i++ ) {
	    BankSim.addBank( banks[i], false );
	}

	BankSim.doDelay( BSConstants.MIN_TIME_ADD_BANKS, methodStart );
    }

    /**
    * getBank - Retrieves bank information from the database
    * @param name String object that specifies the name of the bank
    * @return Bank object that contains the bank information
    * @exception BSException that states the banksim is not initialized or getBank failed
    */
    public static Bank getBank( String name ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getBank", exc);
	    throw exc;
	}

	Bank bank = null;
        DBConnection conn = connPool.getConnection();
	try {
	    bank = DBFinancialInstitution.getBank( name, conn );
	} catch( BSException bse ) {
	    BSException exc = new BSException( bse.getErrorCode(), bse );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getBank", exc);
	    throw exc;
	} finally {
	    connPool.releaseConnection( conn );
	}

	BankSim.doDelay( BSConstants.MIN_TIME_GET_BANK, methodStart );
	return bank;
    }

    private static void updateBank( Bank bank, boolean delay ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:updateBank", exc);
	    throw exc;
	}
        DBConnection conn = connPool.getConnection();
	try {
	    DBFinancialInstitution.updateBank( bank, conn );
	} catch( BSException bse ) {
	    BSException exc = new BSException( bse.getErrorCode(), bse );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:updateBank", exc);
	    throw exc;
	} finally {
	    connPool.releaseConnection( conn );
	}

	if( delay ) {
	    BankSim.doDelay( BSConstants.MIN_TIME_UPDATE_BANK, methodStart );
	}
    }

    /**
    * updateBank - store the lastest Bank information into the database
    * @param bank a populated Bank object that contains the lastest bank information
    * @exception BSException that states the banksim is not initialized or updateBank failed
    */
    public static void updateBank( Bank bank ) throws BSException
    {
	BankSim.updateBank( bank, true );
    }

    public static void updateBanks( Bank[] banks ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	for( int i = 0; i < banks.length; i++ ) {
	    BankSim.updateBank( banks[i], false );
	}

	BankSim.doDelay( BSConstants.MIN_TIME_UPDATE_BANKS, methodStart );
    }

    /**
    * deleteBank - remove the specified bank info from the database
    * @param bank a populated Bank object that contains the bank information
    * @exception BSException that states the banksim is not initialized or deleteBank fails
    */
    public static void deleteBank( Bank bank ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:deleteBank", exc);
	    throw exc;
	}

        DBConnection conn = connPool.getConnection();
	try {
	    DBFinancialInstitution.deleteBank( bank, conn );
	} catch( BSException bse ) {
	    BSException exc = new BSException( bse.getErrorCode(), bse );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:deleteBank", exc);
	    throw exc;
	} finally {
	    connPool.releaseConnection( conn );
	}

	BankSim.doDelay( BSConstants.MIN_TIME_DELETE_BANK, methodStart );
    }

    private static void addCustomer( User customer, boolean delay ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:addCustomer", exc);
	    throw exc;
	}
	DBConnection conn = connPool.getConnection();
	try {
	    DBCustomer.addCustomer( customer, conn );
	} catch( BSException bse ) {
	    BSException exc = new BSException( bse.getErrorCode(), bse );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:addCustomer", exc);
	    throw exc;
	} finally {
	    connPool.releaseConnection( conn );
	}

	if( delay ) {
	    BankSim.doDelay( BSConstants.MIN_TIME_ADD_CUSTOMER, methodStart );
	}
    }

    /**
     * addCustomer - Adds a customer to the Bank Simulator
     * @param customer a populated User object containing the customer info
     */
    public static void addCustomer( User customer ) throws BSException
    {
	BankSim.addCustomer( customer, true );
    }

    public static void addCustomers( User[] customers ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	for( int i = 0; i < customers.length; i++ ) {
	    BankSim.addCustomer( customers[i], false );
	}

	BankSim.doDelay( BSConstants.MIN_TIME_ADD_CUSTOMERS, methodStart );
    }

    private static void updateCustomer( User customer, boolean delay ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:updateCustomer", exc);
	    throw exc;
	}
        DBConnection conn = connPool.getConnection();
	try {
	    DBCustomer.updateCustomer( customer, conn );
	} catch( BSException bse ) {
	    BSException exc = new BSException( bse.getErrorCode(), bse );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:updateCustomer", exc);
	    throw exc;
	} finally {
	    connPool.releaseConnection( conn );
	}

	if( delay ) {
	    BankSim.doDelay( BSConstants.MIN_TIME_UPDATE_CUSTOMER, methodStart );
	}
    }

    /**
    * updateCustomer - store the latest customer information into the database
    * @param customer a populated User object that has the latest customer information
    * @exception BSException that states the banksim is not initialized or update failed
    */
    public static void updateCustomer( User customer ) throws BSException
    {
	BankSim.updateCustomer( customer, true );
    }

    public static void updateCustomers( User[] customers ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	for( int i = 0; i < customers.length; i++ ) {
	    BankSim.updateCustomer( customers[i], false );
	}

	BankSim.doDelay( BSConstants.MIN_TIME_UPDATE_CUSTOMERS, methodStart );
    }

    /**
    * deleteCustomer - remove the specified customer info from the database
    * @param customer a populated User object that contains the customer information
    * @exception BSException that states the banksim is not initialized or deleteCustomer fails
    */
    public static void deleteCustomer( User customer ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:deleteCustomer", exc);
	    throw exc;
	}

        DBConnection conn = connPool.getConnection();
	try {
	    DBCustomer.deleteCustomer( customer, conn );
	} catch( BSException bse ) {
	    BSException exc = new BSException( bse.getErrorCode(), bse );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:deleteCustomer", exc);
	    throw exc;
	} finally {
	    connPool.releaseConnection( conn );
	}

	BankSim.doDelay( BSConstants.MIN_TIME_DELETE_CUSTOMER, methodStart );
    }

    private static void addAccount( User customer,
    				    Account toAdd,
				    boolean delay ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:addAccount", exc);
	    throw exc;
	}

        DBConnection conn = connPool.getConnection();
	try {
	    DBAccount.addAccount( customer, toAdd, conn, false );
	} catch( BSException e ) {
	    BSException exc = new BSException( e.getErrorCode(), e );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:addAccount", exc);
	    throw exc;
	} finally {
	    connPool.releaseConnection( conn );
	}

	if( delay ) {
	    BankSim.doDelay( BSConstants.MIN_TIME_ADD_ACCOUNT, methodStart );
	}
    }

    /**
     * addAccount - Adds an account to the Bank Simulator
     * @param customer a populated User object whose Account is being added.
     * @param toAdd the Account object to add to the Bank Simulator
     */
    public static void addAccount( User customer, Account toAdd ) throws BSException
    {
	BankSim.addAccount( customer, toAdd, true );
    }

    public static void addAccounts( User customer, Account[] toAdd ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	for( int i = 0; i < toAdd.length; i++ ) {
	    BankSim.addAccount( customer, toAdd[i], false );
	}

	BankSim.doDelay( BSConstants.MIN_TIME_ADD_ACCOUNTS, methodStart );
    }

    /**
    * getAccounts - retrieves the account information from the datbase
    * by using the information contained in the User object
    * @param customer a populated User object that contains customer information
    * @exception BSException that states the banksim is not initialized or getAccounts failed
    */
    public static Enumeration getAccounts( User customer ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getAccounts", exc);
	    throw exc;
	}

	Enumeration e = null;
        DBConnection conn = connPool.getConnection();
	try {
	    e = DBAccount.getAccounts( customer, conn );
	} catch( BSException bse ) {
	    BSException exc = new BSException( bse.getErrorCode(), bse );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getAccounts", exc);
	    throw exc;
	} finally {
	    connPool.releaseConnection( conn );
	}

	BankSim.doDelay( BSConstants.MIN_TIME_GET_ACCOUNTS, methodStart );
	return e;
    }

    /**
    * getAccount - populates an arbitrary account object
    * @param account an Account object containing the ID of the account to get
    * @return the fully-populated Account object corresponding to the input parameter
    * @exception BSException that states the banksim is not initialized or getAccount failed
    */
    public static Account getAccount( Account account ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getAccount", exc);
	    throw exc;
	}

        DBConnection conn = connPool.getConnection();
	try {
	    account = DBAccount.getAccount( account, conn );
	} catch( BSException bse ) {
	    BSException exc = new BSException( bse.getErrorCode(), bse );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getAccount", exc);
	    throw exc;
	} finally {
	    connPool.releaseConnection( conn );
	}

	BankSim.doDelay( BSConstants.MIN_TIME_GET_ACCOUNT, methodStart );
	return account;
    }

    private static void updateAccount( Account account, boolean delay ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:updateAccount", exc);
	    throw exc;
	}
        DBConnection conn = connPool.getConnection();
	try {
	    DBAccount.updateAccount( account, conn );
	} catch( BSException bse ) {
	    BSException exc = new BSException( bse.getErrorCode(), bse );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:updateAccount", exc);
	    throw exc;
	} finally {
	    connPool.releaseConnection( conn );
	}

	if( delay ) {
	    BankSim.doDelay( BSConstants.MIN_TIME_UPDATE_ACCOUNT, methodStart );
	}
    }

    /**
    * updateAccount - store the lastest Account information into the database
    * @param account a populated Account object that contains the lastest account information
    * @exception BSException that states the banksim is not initialized or updateAccount failed
    */
    public static void updateAccount( Account account ) throws BSException
    {
	BankSim.updateAccount( account, true );
    }

    public static void updateAccounts( Account[] accounts ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	for( int i = 0; i < accounts.length; i++ ) {
	    BankSim.updateAccount( accounts[i], false );
	}

	BankSim.doDelay( BSConstants.MIN_TIME_UPDATE_ACCOUNTS, methodStart );
    }

    /**
    * deleteAccount - remove the specified account info from the database
    * @param account a populated Account object that contains the account information
    * @exception BSException that states the banksim is not initialized or deleteAccount fails
    */
    public static void deleteAccount( Account account ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:deleteAccount", exc);
	    throw exc;
	}

        DBConnection conn = connPool.getConnection();
	try {
	    DBAccount.deleteAccount( account, conn );
	} catch( BSException bse ) {
	    BSException exc = new BSException( bse.getErrorCode(), bse );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:deleteAccount", exc);
	    throw exc;
	} finally {
	    connPool.releaseConnection( conn );
	}

	BankSim.doDelay( BSConstants.MIN_TIME_DELETE_ACCOUNT, methodStart );
    }

    private static Transfer addTransfer( Transfer transfer,
    					 int transType,
					 boolean delay ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:addTransfer", exc);
	    throw exc;
	}

        DBConnection conn = connPool.getConnection();
	Transfer toReturn = null;
	try {
	    toReturn = DBTransfer.addTransfer( transfer, transType, conn );
	} catch( BSException e ) {
	    BSException exc = new BSException( e.getErrorCode(), e );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:addTransfer", exc);
	    throw exc;
	} finally {
	    connPool.releaseConnection( conn );
	}

	if( delay ) {
	    BankSim.doDelay( BSConstants.MIN_TIME_ADD_TRANSFER, methodStart );
	}
	return toReturn;
    }

    /**
    * addTransfer - add the transactions information into the datbase
    * by using the information contained in the Transfer object
    * @param transfer a populated Transfer object that contains the transfer information
    * @param transType a transaction type constant (from com.ffusion.beans.banking.TransactionTypes) used for the transaction that is created when the transfer occurs
    * @return a populated Transfer object with the reference number filled in
    * @exception BSException that states the banksim is not initialized or addTransfer failed
    */
    public static Transfer addTransfer( Transfer transfer, int transType ) throws BSException
    {
	return BankSim.addTransfer( transfer, transType, true );
    }

    public static Transfer[] addTransfers( Transfer[] transfers, int transType[] ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();
	Transfer retVal[] = new Transfer[transfers.length];

	for( int i = 0; i < transfers.length; i++ ) {
	    retVal[i] = BankSim.addTransfer( transfers[i], transType[i], false );
	}

	BankSim.doDelay( BSConstants.MIN_TIME_ADD_TRANSFERS, methodStart );

	return retVal;
    }

    /**
    * getTransactions - retrieves the transactions information from the datbase
    * by using the information contained in the Account object, startDate and endDate
    * @param account a populated Account object that contains account information
    * @param startDate a Calendar object to specify the startDate
    * @param endDate a Calendar object to specify the endDate
    * @return Enumeration of Transaction objects
    * @exception BSException that states the banksim is not initialized or getTransactions failed
    */
    public static Enumeration getTransactions( Account account,
    					       Calendar startDate,
					       Calendar endDate ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getTransactions", exc);
	    throw exc;
	}

	Enumeration e = null;
        DBConnection conn = connPool.getConnection();
	try {
	    e = DBTransaction.getTransactions( account, startDate, endDate, conn );
	} catch( BSException bse ) {
	    BSException exc = new BSException( bse.getErrorCode(), bse );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getTransactions", exc);
	    throw exc;
	} finally {
	    connPool.releaseConnection( conn );
	}

	BankSim.doDelay( BSConstants.MIN_TIME_GET_TRANSACTIONS, methodStart );
	return e;
    }
	
    /**
    * openPagedTransactions - informs BankSim to collect and cache a transaction list
    * for later "paging through" by other methods by using the information contained
    * in the Account object, startDate and endDate
    * @param account the account that we will retrieve the list of transactions for.
    * @param startDate a Calendar object to specify the startDate
    * @param endDate a Calendar object to specify the endDate
    * @exception BSException that states the banksim is not initialized or getTransactions failed
    */
    public static void openPagedTransactions( Account account,
					      Calendar startDate,
					      Calendar endDate ) throws BSException
    {
		PagingContext context = new PagingContext( startDate, endDate );
		ReportCriteria criteria = new ReportCriteria();
		HashMap newMap = new HashMap();
		context.setMap( newMap );
		context.getMap().put( BSConstants.PAGING_CONTEXT_REPORT_CRITERIA, criteria );
		Format formatter = com.ffusion.util.DateFormatUtil.getFormatter( DateConsts.REPORT_CRITERIA_INTERNAL_FORMAT_DATETIME_STR_WITH_SEC );
		if ( startDate != null ) {
			criteria.getSearchCriteria().setProperty( DateConsts.SEARCH_CRITERIA_START_DATE, formatter.format( startDate.getTime() ) );
		}
		if ( endDate != null ) {
			criteria.getSearchCriteria().setProperty( DateConsts.SEARCH_CRITERIA_END_DATE, formatter.format( endDate.getTime() ) );
		}
		HashMap extra = new HashMap();
	
		openPagedTransactions( account, context, extra );
    }

	/**
	* openPagedTransactions - informs BankSim to collect and cache a transaction list for 
	* later "paging through" by other methods by using the information contained in the 
	* <code>Account</code> object, <code>PagingContext</code> and extra hashmap.
	* @param account the account that we will retrieve the list of transactions for.
	* @param context the object that holds the criteria that are used to get the list of transactions
	* @param extra a hash map containing any other extraneous objects that the method may use to perform properly
	* @exception BSException that states the banksim is not initialized or getTransactions failed
	*/
	public static void openPagedTransactions( Account account, PagingContext context, HashMap extra ) throws BSException {
		
		// Remember the time now -- we'll need it later to make sure that
		// we delay the correct amount.
		long methodStart = System.currentTimeMillis();
	    
	    if( !initialized ) {
			BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
						   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
			com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:openPagedTransactions", exc);
			throw exc;
		}
	
			DBConnection conn = connPool.getConnection();
		try {
			DBTransaction.openPagedTransactions( account, context, extra, conn );
		} catch( BSException bse ) {
			BSException exc = new BSException( bse.getErrorCode(), bse );
			com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:openPagedTransactions", exc);
			throw exc;
		} finally {
			connPool.releaseConnection( conn );
		}
	
		BankSim.doDelay( BSConstants.MIN_TIME_OPEN_PAGED_TRANSACTIONS, methodStart );
	}
	
	/**
	 * This API handles the Account History Paging, fetches the first page for specified account with the help
	 * of DBTransaction class using <code>Account</code> object, <code>PagingContext</code> and extra hashmap.
	 * @param account
	 * @param context
	 * @param extra
	 * @return
	 * @throws BSException
	 */
	public static Transactions getPagedTransactions(Account account,PagingContext context, HashMap extra) throws BSException {
		// Remember the time now -- we'll need it later to make sure that
				// we delay the correct amount.
				long methodStart = System.currentTimeMillis();
				Transactions transactions = null;
			    if( !initialized ) {
					BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
								   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
					com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getPagedTransactions", exc);
					throw exc;
				}
			
					DBConnection conn = connPool.getConnection();
				try {
					transactions = DBTransaction.getPagedTransactions( account, context, extra, conn );
				} catch( BSException bse ) {
					BSException exc = new BSException( bse.getErrorCode(), bse );
					com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getPagedTransactions", exc);
					throw exc;
				} finally {
					connPool.releaseConnection( conn );
				}
			
				BankSim.doDelay( BSConstants.MIN_TIME_OPEN_PAGED_TRANSACTIONS, methodStart );
				return transactions;
	}
	
	/**
	 * This API handles the Account History Paging, fetches the next page for specified account with the help
	 * of DBTransaction class using <code>Account</code> object, <code>PagingContext</code> and extra hashmap.
	 * @param account
	 * @param context
	 * @param extra
	 * @return
	 * @throws BSException
	 */
	public static Transactions getNextTransactions(Account account,	PagingContext context, HashMap extra) throws BSException {
				// Remember the time now -- we'll need it later to make sure that
				// we delay the correct amount.
				long methodStart = System.currentTimeMillis();
				Transactions transactions = null;
			    if( !initialized ) {
					BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
								   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
					com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getNextTransactions", exc);
					throw exc;
				}
			
					DBConnection conn = connPool.getConnection();
				try {
					transactions = DBTransaction.getNextTransactions( account, context, extra, conn );
				} catch( BSException bse ) {
					BSException exc = new BSException( bse.getErrorCode(), bse );
					com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getNextTransactions", exc);
					throw exc;
				} finally {
					connPool.releaseConnection( conn );
				}
			
				BankSim.doDelay( BSConstants.MIN_TIME_OPEN_PAGED_TRANSACTIONS, methodStart );
				return transactions;
	}
	
	/**
	 * This API handles the Account History Paging, fetches the previous page for specified account with the help
	 * of DBTransaction class using <code>Account</code> object, <code>PagingContext</code> and extra hashmap.
	 * @param account
	 * @param context
	 * @param extra
	 * @return
	 * @throws BSException
	 */
	public static Transactions getPreviousTransactions(Account account,	PagingContext context, HashMap extra) throws BSException {
				// Remember the time now -- we'll need it later to make sure that
				// we delay the correct amount.
				long methodStart = System.currentTimeMillis();
				Transactions transactions = null;
			    if( !initialized ) {
					BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
								   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
					com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getPreviousTransactions", exc);
					throw exc;
				}
			
					DBConnection conn = connPool.getConnection();
				try {
					transactions = DBTransaction.getPreviousTransactions( account, context, extra, conn );
				} catch( BSException bse ) {
					BSException exc = new BSException( bse.getErrorCode(), bse );
					com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getPreviousTransactions", exc);
					throw exc;
				} finally {
					connPool.releaseConnection( conn );
				}
			
				BankSim.doDelay( BSConstants.MIN_TIME_OPEN_PAGED_TRANSACTIONS, methodStart );
				return transactions;
	}
	
	
    /**
    * closePagedTransactions - informs BankSim to no longer hold onto the paged info
    * for the specified account
    * @param account a populated Account object that's already been passed to openPagedTransactions
    * @exception BSException that states the banksim is not initialized or that the account does not have any paged transactions
    */
    public static void closePagedTransactions( Account account ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:closePagedTransactions", exc);
	    throw exc;
	}

	try {
	    DBTransaction.closePagedTransactions( account );
	} catch( BSException bse ) {
	    BSException exc = new BSException( bse.getErrorCode(), bse );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:closePagedTransactions", exc);
	    throw exc;
	}

	BankSim.doDelay( BSConstants.MIN_TIME_CLOSE_PAGED_TRANSACTIONS, methodStart );
    }

    /**
     * getNumberOfTransactions - retrieve the number of transactions for a given account
     *
     * @param account a populated Account object that's already been passed to openPagedTransactions
     */

    public static int getNumberOfTransactions( Account account ) throws BSException{
	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getNextPage", exc);
	    throw exc;
	}

	try {	
	    return DBTransaction.getNumberOfTransactions( account ); 
	} catch( BSException bse ) {
	    BSException exc = new BSException( bse.getErrorCode(), bse );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getNextPage", exc);
	    throw exc;
	}
    }

    /**
    * getNextPage - retrieve the next page of transaction information for an account
    * @param account a populated Account object that's already been passed to openPagedTransactions
    * @param howMany the number of transaction elements to return
    * @return Enumeration of Transaction objects
    * @exception BSException that states the banksim is not initialized or that the account does not have any paged transactions
    */
    public static Enumeration getNextPage( Account account, int howMany ) throws BSException
    {
	return getNextPage( account, howMany, -1 );
    }

    /**
    * getNextPage - retrieve the next page of transaction information for an account
    * @param account a populated Account object that's already been passed to openPagedTransactions
    * @param howMany the number of transaction elements to return
    * @param nextIndex the index of the next transaction to retrieve
                       if the value is -1, retrieves a page determined from previous page requests
    * @return Enumeration of Transaction objects
    * @exception BSException that states the banksim is not initialized or that the account does not have any paged transactions
    */
    public static Enumeration getNextPage( Account account, int howMany, int nextIndex ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getNextPage", exc);
	    throw exc;
	}

	Enumeration e = null;
	try {
	    e = DBTransaction.getNextPage( account, howMany, nextIndex );
	} catch( BSException bse ) {
	    BSException exc = new BSException( bse.getErrorCode(), bse );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getNextPage", exc);
	    throw exc;
	}

	BankSim.doDelay( BSConstants.MIN_TIME_GET_NEXT_PAGE, methodStart );
	return e;
    }

    /**
    * getPrevPage - retrieve the previous page of transaction information for an account
    * @param account a populated Account object that's already been passed to openPagedTransactions
    * @param howMany the number of transaction elements to return
    * @return Enumeration of Transaction objects
    * @exception BSException that states the banksim is not initialized or that the account does not have any paged transactions
    */
    public static Enumeration getPrevPage( Account account, int howMany ) throws BSException
    {
	return getPrevPage( account, howMany, -1 );
    }

    /**
    * getPrevPage - retrieve the previous page of transaction information for an account
    * @param account a populated Account object that's already been passed to openPagedTransactions
    * @param howMany the number of transaction elements to return
    * @param prevIndex the transaction index of the last transaction to retrieve
                       if the value is -1, retrieves a page determined from previous page requests
    * @return Enumeration of Transaction objects
    * @exception BSException that states the banksim is not initialized or that the account does not have any paged transactions
    */
    public static Enumeration getPrevPage( Account account, int howMany, int prevIndex ) throws BSException
    {
	// Remember the time now -- we'll need it later to make sure that
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getPrevPage", exc);
	    throw exc;
	}

	Enumeration e = null;
	try {
	    e = DBTransaction.getPrevPage( account, howMany, prevIndex );
	} catch( BSException bse ) {
	    BSException exc = new BSException( bse.getErrorCode(), bse );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getPrevPage", exc);
	    throw exc;
	}

	BankSim.doDelay( BSConstants.MIN_TIME_GET_PREV_PAGE, methodStart );
	return e;
    }

    /**
    * addMailMessage - add a mail message to the bank sim
    * @param customer The customer sending the message
    * @param message The message object
    * @exception BSException that states the cusrtomer doesn't exist or wraps the SQL Exception thrown by other methods
    */
    public static final void addMailMessage( User 	customer,
    				   	     Message 		message ) throws BSException
    {
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:addMailMessage", exc);
	    throw exc;
	}

        DBConnection conn = connPool.getConnection();
	try {
	    DBMail.addMailMessage( customer, message, conn );
	} catch( BSException bse ) {
	    BSException exc = new BSException( bse.getErrorCode(), bse );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:addMailMessage", exc);
	    throw exc;
	}

	BankSim.doDelay( BSConstants.MIN_TIME_ADD_MAIL_MESSAGE, methodStart );
    }

    /**
    * getMailMessages - Retrieve a user's mail
    * the information contained in the User object
    * note: this method assumes signOn has been called, so there is no need
    * to check if the customer exists
    * @param customer a populated User object
    * @return Enumeration of Message objects
    * @exception BSException that wraps around the SQL Exception
    */
    public static final Enumeration getMailMessages( User customer ) throws BSException
    {
	// we delay the correct amount.
	long methodStart = System.currentTimeMillis();

	if( !initialized ) {
	    BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
	    			   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getMailMessages", exc);
	    throw exc;
	}

        DBConnection conn = connPool.getConnection();
	Enumeration e = null;
	try {
	    e = DBMail.getMailMessages( customer, conn );
	} catch( BSException bse ) {
	    BSException exc = new BSException( bse.getErrorCode(), bse );
		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getMailMessages", exc);
	    throw exc;
	}

	BankSim.doDelay( BSConstants.MIN_TIME_GET_MAIL_MESSAGES, methodStart );

	return e;
    }

    
    /**
	Adds summary information into the repository for a specified lockbox
	@param lockbox: the lockbox for which summary information has been supplied
	@param summary: the summary information to be added
	@param dataSource: the source of the data
	@param connection: database DBConnection object
    */
    public static void addLockboxSummary( com.ffusion.beans.lockbox.LockboxSummary summary,
					    int dataSource,
					    DBConnection connection,
					    HashMap extra )
					    throws BSException
    {
	BSLockboxSummary.add( summary, dataSource, connection, extra );
    }
    
    /**
	Retrieve the summary information for a specified lockbox for a date range
	@param lockbox: the lockbox for which we want the summaries
	@param startDate: the start date of summaries to get or null if no end date
	@param endDate: the end date of summaries to get or null if no end date
    */
    public static com.ffusion.beans.lockbox.LockboxSummaries getLockboxSummaries( com.ffusion.beans.lockbox.LockboxAccount lockbox,
							Calendar startDate,
							Calendar endDate,
							HashMap extra )
							throws BSException
    {
	DBConnection conn = connPool.getConnection();
	try {
	    return BSLockboxSummary.getLockboxSummaries(  lockbox, startDate, endDate, conn, extra );
	} finally {
	    connPool.releaseConnection( conn );
	}

    }

    
    // Operations on Disbursement Summaries
    
    /*
	 Adds disbursement summary information into the repository for a specified account
	 @param account: the account for which summary information has been supplied
	 @param summary: the summary information to be added
	 @param dataSource: the source of the data
	 @param connection: database DBConnection object
    */
    public static void addDisbursementSummary( com.ffusion.beans.disbursement.DisbursementSummary summary, int dataSource, DBConnection connection, HashMap extra ) throws BSException
    {
	BSDsbSummary.addDisbursementSummary( summary, dataSource, connection, extra );
    }


    /*
	 Retrieve the summary information for a specified disbursement account for a date range
	 @param account: the disbursement account for which we want the summaries
	 @param startDate: the start date of summaries to get or null if no start date
	 @param endDate: the end date of summaries to get or null if no start date
    */
    public static com.ffusion.beans.disbursement.DisbursementSummaries getDisbursementSummaries( com.ffusion.beans.disbursement.DisbursementAccount account,
								  Calendar startDate,
								  Calendar endDate,
								  HashMap extra )
								  throws BSException
     {
	DBConnection conn = connPool.getConnection();
	try {
	    return BSDsbSummary.getDisbursementSummaries( account, startDate, endDate, conn, extra );
	} finally {
	    connPool.releaseConnection( conn );
	}
     }

    /*
	 Retrieve the summary information for a specified presentment
	 @param presentment: the presentment for which we want to retrieve summaries
    */
    public static com.ffusion.beans.disbursement.DisbursementSummaries getDisbursementSummariesForPresentment (
						     com.ffusion.beans.disbursement.DisbursementAccount account,
						     String presentment,
						     Calendar startDate,
						     Calendar endDate,
						     HashMap extra )
	throws BSException
     {
	DBConnection conn = connPool.getConnection();
	try {
	    return BSDsbSummary.getDisbursementSummariesForPresentment( account, presentment, 
									startDate, endDate, conn, extra );
	} finally {
	    connPool.releaseConnection( conn );
	}
     }

    /*
	 Retrieve the presentment summary information for a date range
	 @param startDate: the start date of summaries to get or null if no start date
	 @param endDate: the end date of summaries to get or null if no start date
    */

    public static com.ffusion.beans.disbursement.DisbursementPresentmentSummaries getDisbursementPresentmentSummaries( 
								  com.ffusion.beans.disbursement.DisbursementAccounts accounts,     
								  Calendar startDate,
								  Calendar endDate,
								  HashMap extra )
								  throws BSException
     {
	DBConnection conn = connPool.getConnection();
	try {
	    return BSDsbSummary.getDisbursementPresentmentSummaries( accounts, startDate, endDate, conn, extra );
	} finally {
	    connPool.releaseConnection( conn );
	}
     }

    // Operations on Disbursement Transactions
    
    /*
	 Adds transactions into the repository for a specified disbursement account
	 @param account: the disbursement account for which we want to add the transactions
	 @param transactions: a list of DisbursementTransaction objects to be added
	 @param dataSource: the source of the data
	 @param connection: Database DBConnection object
    */
    public static void addDisbursementTransactions(  com.ffusion.beans.disbursement.DisbursementAccount account,
						      com.ffusion.beans.disbursement.DisbursementTransactions transactions,
						      int dataSource,
						      DBConnection connection, HashMap extra )
						      throws BSException
    {
	BSDsbTransactions.addTransactions( account, transactions, dataSource, connection, extra );
    }


    /*
	 Retrieves a list of transactions for a specified disbursement account between a start date to an end date
	 @param account: the disbursement account for which we want to retrieve transactions
	 @param startDate: the start date of transactions to get or null if no start date
	 @param endDate: the end date of transactions to get or null if no end date
	 @return a list of DisbursementTransaction beans
    */
    public static com.ffusion.beans.disbursement.DisbursementTransactions getDisbursementTransactions(
							  com.ffusion.beans.disbursement.DisbursementAccount account,
							  Calendar startDate,
							  Calendar endDate,
							  HashMap extra )
							  throws BSException
    {
	return getDisbursementTransactions( account, startDate, endDate, null, extra );
    }

    /*
	 Retrieves a list of transactions for a specified disbursement account and presentment between a start date to an end date
	 @param account: the disbursement account for which we want to retrieve transactions
	 @param startDate: the start date of transactions to get or null if no start date
	 @param endDate: the end date of transactions to get or null if no end date
	 @param presentment: the presentment for which we want to retrieve transactions
	 @return a list of DisbursementTransaction beans
    */
    public static com.ffusion.beans.disbursement.DisbursementTransactions getDisbursementTransactions(
							  com.ffusion.beans.disbursement.DisbursementAccount account,
							  Calendar startDate,
							  Calendar endDate,
							  String presentment,
							  HashMap extra )
							  throws BSException
    {
	DBConnection conn = connPool.getConnection();
	try {
	    return BSDsbTransactions.getTransactions( account, startDate, endDate, presentment, conn, extra );
	} finally {
	    connPool.releaseConnection( conn );
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
    public static com.ffusion.beans.disbursement.DisbursementTransactions getPagedDisbursementTransactions(
								com.ffusion.beans.disbursement.DisbursementAccount account,
								Calendar startDate,
								Calendar endDate,
								HashMap extra )
								throws BSException
    {
	return getPagedDisbursementTransactions( account, startDate, endDate, null, extra );
    }

    /**
	Retrieves a list of transactions for a specified disbursement account and presentment between a
	start date to an end date, to a maximum of PAGESIZE of them
	@param account: the disbursement account for which we want to retrieve transactions
	@param startDate: the start date of transactions to get or null if no start date
	@param endDate: the end date of transactions to get or null if no end date
	@param presentment: the presentment for which we want to retrieve transactions
	@return a list of DisbursementTransaction beans
    */
    public static com.ffusion.beans.disbursement.DisbursementTransactions getPagedDisbursementTransactions(
								com.ffusion.beans.disbursement.DisbursementAccount account,
								Calendar startDate,
								Calendar endDate,
								String presentment,
								HashMap extra )
								throws BSException
    {
	DBConnection conn = connPool.getConnection();
	try {
	    return BSDsbTransactions.getPagedTransactions( account, startDate, endDate, presentment, conn, extra );
	} finally {
	    connPool.releaseConnection( conn );
	}
    }

    /*
	 Retrieves the most recent transactions for the specified disbursement account
	 @param account: the disbursement account for which we want to retrieve transactions
	 @return a list of DisbursementTransaction beans containing the transactions (at
		 		  most PAGESIZE of them)
    */
    public static com.ffusion.beans.disbursement.DisbursementTransactions getRecentDisbursementTransactions(
		 		 		  com.ffusion.beans.disbursement.DisbursementAccount account,
		 		 		  HashMap extra )
						  throws BSException
    {
	DBConnection conn = connPool.getConnection();
	try {
		return BSDsbTransactions.getRecentTransactions( account, conn, extra );
	} finally {
	    	connPool.releaseConnection( conn );
	}
    }

    /*
	 Retrieves the next batch of PAGESIZE transactions for the specified disbursement account
	 @param account: the disbursement account for which we want to retrieve transactions
	 @param nextIndex: the next index of information to retrieve
	 @return a list of DisbursementTransaction beans containing the transactions (at
	 most PAGE_SIZE of them)
    */
    public static com.ffusion.beans.disbursement.DisbursementTransactions getNextDisbursementTransactions(
		 		 		 		 		 		  com.ffusion.beans.disbursement.DisbursementAccount account,
		 		 		 		 		 		  long nextIndex,
		 		 		 		 		 		  HashMap extra )
		 		 		 		 		 		  throws BSException
    {
	DBConnection conn = connPool.getConnection();
	try {
		return BSDsbTransactions.getNextTransactions( account, nextIndex, conn, extra );
	} finally {
	    	connPool.releaseConnection( conn );
	}
    }   
    
    /*
	 Retrieves the previous batch of PAGESIZE transactions for the specified disbursement account
	 @param account: the disbursement account for which we want to retrieve transactions
	 @param lastIndex: the last index of information to retrieve
	 @return a list of DisbursementTransaction beans containing the transactions (at
	 most PAGE_SIZE of them)
    */
    public static com.ffusion.beans.disbursement.DisbursementTransactions getPreviousDisbursementTransactions(
		 		 		 		 		 		      com.ffusion.beans.disbursement.DisbursementAccount account,
		 		 		 		 		 		      long lastIndex,
		 		 		 		 		 		      HashMap extra )
		 		 		 		 		 		      throws BSException
    {
	DBConnection conn = connPool.getConnection();
	try {
		return BSDsbTransactions.getPreviousTransactions( account, lastIndex, conn, extra );
	} finally {
	    	connPool.releaseConnection( conn );
	}
    }


    // Operations on LockboxAccount Transactions
    
    /*
    
	 Adds transactions into the repository for a specified lockbox
	 @param lockbox: the LockboxAccount for which we information has been supplied
	 @param trans: a list of LockboxTransaction objects to be added
	 @param dataSource: the source of the data
	 @param connection: Database DBConnection object
    */
    public static void addLockboxTransactions( com.ffusion.beans.lockbox.LockboxAccount lockbox,
		 		 		  com.ffusion.beans.lockbox.LockboxTransactions transactions,
		 		 		  int dataSource,
		 		 		  DBConnection connection, HashMap extra )
		 		 		 throws BSException
    {
	BSLBTransactions.addTransactions( lockbox, transactions, dataSource, connection, extra );
    }


    /*
	 Retrieves a list of transactions for a specified LockboxAccount between a start date to an end date
	 @param lockbox: the LockboxAccount for which we want the transactions
	 @param startDate: the start date of transactions to get or null if no start date
	 @param endDate: the end date of transactions to get or null if no end date
	 @return a list of LockboxTransaction beans
    */
    public static com.ffusion.beans.lockbox.LockboxTransactions getLockboxTransactions( com.ffusion.beans.lockbox.LockboxAccount lockbox,
											  Calendar startDate,
											  Calendar endDate,
											  HashMap extra )
											  throws BSException
    {
	DBConnection conn = connPool.getConnection();
	try {
	    return BSLBTransactions.getTransactions( lockbox, startDate, endDate, conn, extra );
	} finally {
	    connPool.releaseConnection( conn );
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
    public static com.ffusion.beans.lockbox.LockboxTransactions getPagedLockboxTransactions(
								    com.ffusion.beans.lockbox.LockboxAccount lockbox,
								    Calendar startDate,
								    Calendar endDate,
								    HashMap extra )
								    throws BSException
    {
	DBConnection conn = connPool.getConnection();
	try {
	    return BSLBTransactions.getPagedTransactions( lockbox, startDate, endDate, conn, extra );
	} finally {
	    connPool.releaseConnection( conn );
	}
    }

    /*
	 Retrieves the most recent transactions for the specified lockbox
	 @param lockbox: the LockboxAccount for which we want the transactions
	 @return a list of LockboxTransaction beans containing the transactions (at most PAGESIZE of them)
    */
    public static com.ffusion.beans.lockbox.LockboxTransactions getRecentLockboxTransactions( com.ffusion.beans.lockbox.LockboxAccount lockbox,
											      HashMap extra )
											      throws BSException
    {
	DBConnection conn = connPool.getConnection();
	try {
	    return BSLBTransactions.getRecentTransactions( lockbox, conn, extra );
	} finally {
	    connPool.releaseConnection( conn );
	}
    }


    /*
	 Retrieves the next batch of PAGESIZE transactions for the specified lockbox
	 @param lockbox: the LockboxAccount for which we want the transactions
	 @param nextIndex: the next index of information to retrieve
	 @return a list of LockboxTransaction beans containing the transactions (at most PAGE_SIZE of them)
    */
    public static com.ffusion.beans.lockbox.LockboxTransactions getNextLockboxTransactions( com.ffusion.beans.lockbox.LockboxAccount lockbox,
											      long nextIndex,
											      HashMap extra )
											      throws BSException
    {
	DBConnection conn = connPool.getConnection();
	try {
	    return BSLBTransactions.getNextTransactions( lockbox, nextIndex, conn, extra );
	} finally {
	    connPool.releaseConnection( conn );
	}
    }


    /**
	 Retrieves the previous batch of PAGESIZE transactions for the specified lockbox
	 @param lockbox: the LockboxAccount for which we want to retrieve transactions
	 @param lastIndex: the last index of information to retrieve
	 @return a list of LockboxTransaction beans containing the transactions (at most PAGE_SIZE of them)
    */
    public static com.ffusion.beans.lockbox.LockboxTransactions getPreviousLockboxTransactions( com.ffusion.beans.lockbox.LockboxAccount lockbox,
												  long lastIndex,
												  HashMap extra )
												  throws BSException
    {
	DBConnection conn = connPool.getConnection();
	try {
	    return BSLBTransactions.getPreviousTransactions( lockbox, lastIndex, conn, extra );
	} finally {
	    connPool.releaseConnection( conn );
	}
    }

    // Operations on LockboxAccount Credit Items
    
    /*
	 Adds credit items into the repository for a specified lockbox
	 @param lockbox: the LockboxAccount for which we information has been supplied
	 @param items: a list of LockboxCreditItem objects to be added
	 @param dataSource: the source of the data
	 @param connection: Database DBConnection object
    */
    public static void addLockboxCreditItems( com.ffusion.beans.lockbox.LockboxAccount lockbox,
					      com.ffusion.beans.lockbox.LockboxCreditItems items,
					      int dataSource,
					      DBConnection connection,
					      HashMap extra )
					      throws BSException
   {
       BSLBCreditItems.addItems( lockbox, items, dataSource, connection, extra );
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
    public static com.ffusion.beans.lockbox.LockboxCreditItems getLockboxCreditItems( com.ffusion.beans.lockbox.LockboxAccount lockbox,
										      String lockboxNumber,
										      Calendar startDate,
										      Calendar endDate,
										      HashMap extra )
										      throws BSException
    {
	DBConnection conn = connPool.getConnection();
	try {
	    return BSLBCreditItems.getItems( lockbox, lockboxNumber, startDate, endDate, conn, extra );
	} finally {
	    connPool.releaseConnection( conn );
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
    public static com.ffusion.beans.lockbox.LockboxCreditItems getPagedLockboxCreditItems(
									    com.ffusion.beans.lockbox.LockboxAccount lockbox,
									    String lockboxNumber,
									    Calendar startDate,
									    Calendar endDate,
									    HashMap extra )
									    throws BSException
    {
	DBConnection conn = connPool.getConnection();
	try {
	    return BSLBCreditItems.getPagedLockboxCreditItems( lockbox, lockboxNumber, startDate, endDate, conn, extra );
	} finally {
	    connPool.releaseConnection( conn );
	}
    }
	
    /*
	 Retrieves the most recent credit items for the specified lockbox
	 @param lockbox: the LockboxAccount for which we want to retrieve credit items
	 @param lockboxNumber: the lockbox number for which we want to retrieve credit items
	 @return a list of LockboxCreditItem beans containing the items (at most PAGESIZE of them)
    */
    public static com.ffusion.beans.lockbox.LockboxCreditItems getRecentLockboxCreditItems( com.ffusion.beans.lockbox.LockboxAccount lockbox,
											    String lockboxNumber,
											    HashMap extra )
	throws BSException
    {
	DBConnection conn = connPool.getConnection();
	try {
	    return BSLBCreditItems.getRecentItems( lockbox, lockboxNumber, conn, extra );
	} finally {
	    connPool.releaseConnection( conn );
	}
    }
    
    /*
	 Retrieves the next batch of PAGESIZE credit items for the specified lockbox
	 @param lockbox: the LockboxAccount for which we want the credit items
	 @param lockboxNumber: the lockbox number for which we want to retrieve credit items
	 @param nextIndex: the next index of information to retrieve
	 @return a list of LockboxCreditItem beans containing the items (at most PAGE_SIZE of them)
    */
    public static com.ffusion.beans.lockbox.LockboxCreditItems getNextLockboxCreditItems( com.ffusion.beans.lockbox.LockboxAccount lockbox,
											  String lockboxNumber,
											  long nextIndex,
											  HashMap extra )
											  throws BSException
    {
	DBConnection conn = connPool.getConnection();
	try {
	    return BSLBCreditItems.getNextItems( lockbox, lockboxNumber, nextIndex, conn, extra );
	} finally {
	    connPool.releaseConnection( conn );
	}
    }
    
    /*
	 Retrieves the previous batch of PAGESIZE credit items for the specified lockbox
	 @param lockbox: the LockboxAccount for which we want to retrieve credit items
	 @param lockboxNumber: the lockbox number for which we want to retrieve credit items
	 @param lastIndex: the last index of information to retrieve
	 @return a list of LockboxCreditItem beans containing the items (at most PAGE_SIZE of them)
    */
    public static com.ffusion.beans.lockbox.LockboxCreditItems getPreviousLockboxCreditItems( com.ffusion.beans.lockbox.LockboxAccount lockbox,
											      String lockboxNumber,
											      long lastIndex,
											      HashMap extra )
											      throws BSException
    {
	DBConnection conn = connPool.getConnection();
	try {
	    return BSLBCreditItems.getPreviousItems( lockbox, lockboxNumber, lastIndex, conn, extra );
	} finally {
	    connPool.releaseConnection( conn );
	}
    }


    // Operations regarding Reports
    /*
	 Get the data for a specified disbursement report.
	 @param user: the SecureUser requesting the information
	 @param criteria: the criteria of the report
    */
    public static IReportResult getDisbursementReportData(  ReportCriteria criteria, 
							    HashMap extra ) throws BSException
    {
	 return null;
    }
    

    /*
	 Get the data for a specified lockbox report.
	 @param user: the SecureUser requesting the information
	 @param criteria: the criteria of the report
    */
    public static IReportResult getLockboxReportData(	ReportCriteria criteria, 
							HashMap extra ) throws BSException
    {
	//return DCLockboxReport.getReportData( criteria, extra );
	return null;
    }
    
	/**
	 * Get a specific page of account history or transaction search records.<br>
	 * This API allows jump-to-page operation.
	 * 
	 * @param account
	 *            The account to fetch transactions for.
	 * @param pagingContext
	 *            The paging context
	 * @param extra
	 *            Extra parameter map.
	 * @return Requested page of transactions.
	 * @throws BSException
	 */
    public static Transactions getSpecificPage(Account account, PagingContext pagingContext,
			HashMap extra) throws BSException {
    	
    	long methodStart = System.currentTimeMillis();
		Transactions transactions = null;
	    if( !initialized ) {
			BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
						   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
			com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getSpecificPageOfConsumerTrans", exc);
			throw exc;
		}
	
			DBConnection conn = connPool.getConnection();
		try {
			transactions = DBTransaction.getSpecificPageOfTransactions( account, pagingContext, extra, conn );
		} catch( BSException bse ) {
			BSException exc = new BSException( bse.getErrorCode(), bse );
			com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getSpecificPageOfConsumerTrans", exc);
			throw exc;
		} finally {
			connPool.releaseConnection( conn );
		}
	
		BankSim.doDelay( BSConstants.MIN_TIME_OPEN_PAGED_TRANSACTIONS, methodStart );
		return transactions;
    }
    
    /**
     * Get account transactions.<br>
     * 
     * @param account
     *            The account to fetch transactions for.
     * @param pagingContext
     *            The paging context
     * @param extra
     *            Extra parameter map.
     * @return Requested page of transactions.
     * @throws BSException
     */
    public static Transactions getAccountTransactions(Account account, PagingContext pagingContext,
    		HashMap extra)throws BSException {
    	long methodStart = System.currentTimeMillis();
    	Transactions transactions = null;
    	if( !initialized ) {
    		BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
    				MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
    		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getAccountTransactions", exc);
    		throw exc;
    	}

    	DBConnection conn = connPool.getConnection();
    	try {
    		transactions = DBTransaction.getAccountTransactions( account, pagingContext, extra, conn );
    	} catch( BSException bse ) {
    		BSException exc = new BSException( bse.getErrorCode(), bse );
    		com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getAccountTransactions", exc);
    		throw exc;
    	} finally {
    		connPool.releaseConnection( conn );
    	}

    	BankSim.doDelay( BSConstants.MIN_TIME_OPEN_PAGED_TRANSACTIONS, methodStart );
    	return transactions;
    }
    
    /**
     * Get Transaction Details By Transaction Id.
     * @param secureUser
     * @param account
     * @param transId
     * @param extra
     * @return Transaction
     * @throws BSException
     */
    public static Transaction getTransactionById(Account account, String transId, HashMap extra)  throws BSException { 
    	long methodStart = System.currentTimeMillis();
		Transaction transaction = null;
	    if( !initialized ) {
			BSException exc = new BSException( BSException.BSE_NOT_INITIALIZED,
						   MessageText.getMessage( IBSErrConstants.ERR_NOT_INITIALIZED ) );
			com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getTransactionById", exc);
			throw exc;
		}
		DBConnection conn = connPool.getConnection();
		try {
			transaction = DBTransaction.getTransactionById(account, transId, extra, conn); 
			
		} catch( BSException bse ) {
			BSException exc = new BSException( bse.getErrorCode(), bse );
			com.ffusion.util.logging.DebugLog.throwing("com.ffusion.banksim.Banksim:getTransactionById", exc);
			throw exc;
		} finally {
			connPool.releaseConnection( conn );
		}
		// BankSim.doDelay( BSConstants.MIN_TIME_OPEN_PAGED_TRANSACTIONS, methodStart );
		return transaction;
    }
    
    public static void close()
    {
    	DBTransaction.closeUnusedPagedTransactions();
    	pagedTransactionCleanupTimer.stop();
    }
    
}
