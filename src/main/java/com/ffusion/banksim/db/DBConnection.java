//
// DBConnection.java
//
// Copyright (c) 2001 Financial Fusion, Inc.
// All Rights Reserved.
//

package com.ffusion.banksim.db;

import com.ffusion.banksim.interfaces.BSDBParams;
import com.ffusion.banksim.interfaces.BSException;
import com.ffusion.util.logging.DebugLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Context;
import java.io.IOException;
import java.sql.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Level;


///////////////////////////////////////////////////////////////////////////////
// This class represents a generic connection to an ASA, ASE, Oracle or
// DB2 server.
///////////////////////////////////////////////////////////////////////////////
public class DBConnection implements IBSErrConstants
{
	private static final Logger logger = LoggerFactory.getLogger(DBConnection.class);

    // JDBC driver used by this connection.
    public static final String JCONNECT4_DRIVER    = "com.sybase.jdbc.SybDriver";
    public static final String JCONNECT5_DRIVER    = "com.sybase.jdbc2.jdbc.SybDriver";
    //Added by TCG to support JConnect 6
    public static final String JCONNECT6_DRIVER    = "com.sybase.jdbc3.jdbc.SybDriver";
    
    public static final String JCONNECT7_DRIVER    = "com.sybase.jdbc4.jdbc.SybDriver";
    //end of Addition
    public static final String JDBC_ORACLE_DRIVER  = "oracle.jdbc.driver.OracleDriver";
    public static final String JDBC_DB2_APP_DRIVER = "COM.ibm.db2.jdbc.app.DB2Driver";
    public static final String JDBC_DB2_NET_DRIVER = "COM.ibm.db2.jdbc.net.DB2Driver";
    public static final String JDBC_DB2_UN2_DRIVER = "com.ibm.db2.jcc.DB2Driver";
    public static final String JDBC_DB2390_DRIVER  = "COM.ibm.db2.jdbc.app.DB2Driver";
    //Begin Add by TCG Team for MS SQL Suppport
    //public static final String JDBC_MSSQL_DRIVER  = "com.jnetdirect.jsql.JSQLDriver";
    public static final String JDBC_MSSQL_DRIVER   = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    public static final String JDBC_POSTGRESQL_DRIVER  = "org.postgresql.Driver";
    public static final String JDBC_HANA_DRIVER   = "com.sap.db.jdbc.Driver";
    //End Add
    //
    // ASA & ASE max LOB size for retrieval constant
    //
    // This is the maximum amount of data which can be retreived
    // from a single column for a single row.
    // Set to 1GB to be consistant with the max DB2 lob length.
    //
    static final int JC_MAX_LOB_LEN = 1024 * 1024 * 1024;

    // 	
    // High-availability ASE session constants
    //
    // jConnect's I/O Exception SQLState (used for HA)
    // The IO exception is for earlier versions of  jC 4.2 and 5.2 which
    // didn't have their own exceptions.  The HA state is for versions
    // after EBF SWR 9648.
    static final String JC_IO_EXCEPTION_STATE = "JZ006";
    static final String JC_HA_EXCEPTION_STATE = "JZ0F2";
    
    // Number of times to retry a HA connection before giving up
    static final int HA_MAX_RETRIES = 3;
    
    // Cache of PreparedStatements
    private static class StatementsCache
    {
	// The limit on how many PreparedStatements to cache.
	private final static int MAX_CACHE_SIZE = 50;
	
	// Size of the hash table. Should be greater
	// than MAX_CACHE_SIZE / 0.75  
	private final static int HASH_TABLE_SIZE = 89;

	// Map of query Strings to PreparedStatements
	private final HashMap _map = new HashMap( HASH_TABLE_SIZE );

	// Queue of query strings corresponding to the most recently
	// added PreparedStatements.
	private final String[] _keys = new String[MAX_CACHE_SIZE];
	private int _head = 0;

	// Finds PreparedStatement object corresponding to given query String.
	// This method will be called thousands of times, so it must be
	// efficient. For example do not create any objects here!
	final PreparedStatement get( String query )
	{
	    return (PreparedStatement)_map.get( query );
	}

	// Cache given PreparedStatement. This method assumes that stmt does
	// not already exist in the cache!
	final void put( String query, PreparedStatement stmt )
	{
	    if( _map.size() == MAX_CACHE_SIZE ) {
		// we reached the limit so lets remove the least recently
		// added PreparedStatement
		try {
		    PreparedStatement oldStmt = (PreparedStatement) _map.remove( _keys[_head] );
		    oldStmt.close();
		} catch( SQLException sqle ) {
		    // Ignore, since there's not much we can do about it
		}
	    }
	    _map.put( query, stmt );
	    _keys[_head] = query;
	    _head = (_head + 1) % MAX_CACHE_SIZE;
	}

	// Remove all statements from the map
	final void clear()
	{
	    _head = 0;
	    for( int i = 0; i < MAX_CACHE_SIZE; i++ ) {
		if( _keys[i] != null ) {
		    try {
			PreparedStatement stmt = (PreparedStatement) _map.remove( _keys[i] );
			if( stmt != null ) {
			    stmt.close();
			}
		    } catch( SQLException sqle ) {
			// Ignore, since there's not much we can do about it
		    }
		    _keys[i] = null;
		}
	    }
	}
    }


    ///////////////////////////////////////////////////////////////////////////
    // Private constructor because DBConections can only be created using the
    // static create() method.
    ///////////////////////////////////////////////////////////////////////////
    private DBConnection( BSDBParams params )
    {
	_isAutoCommit = true;  // AutoCommit is on by default in JDBC
	_params       = params;
	_props        = new Properties();
	_props.put( "user", _params.getUser() );
	_props.put( "password", _params.getPassword() );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Finalizer. Closes the connection if still open.
    ///////////////////////////////////////////////////////////////////////////
    protected void finalize() {
	close();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Creates a DBConnection using given connection parameters.
    ///////////////////////////////////////////////////////////////////////////
    public static DBConnection create( BSDBParams params ) throws BSException
    {
	DBConnection conn = null;
	switch( params.getConnectionType() ) {
	    case BSDBParams.CONN_ASA:
	    	conn = createJConnectConnection( params );
		break;
	    case BSDBParams.CONN_ASE:
	    	if( params.isHA() ) {
		    conn = createHAConnection( params );
		} else {
		    conn = createJConnectConnection( params );
		}
		break;
	    case BSDBParams.CONN_DB2:
	    	conn = createDB2Connection( params );
		break;
        case BSDBParams.CONN_DB2_UN2:
            conn = createDB2UN2Connection( params );
        break;
	    case BSDBParams.CONN_ORACLE:
	    	conn = createOracleConnection( params );
		break;
	    case BSDBParams.CONN_DB2390:
	    	conn = createDB2390Connection( params );
		break;
        //Begin Add by TCG Team for MS SQL Support
	    case BSDBParams.CONN_MSSQL:
	    	conn = createMSSQLConnection( params );
	    case BSDBParams.CONN_POSTGRESQL:
	    	conn = createPostgreSQLConnection( params );
		break;
	    case BSDBParams.CONN_HANA:
	    	conn = createHANAConnection( params );
		break;
      //End Add
	    default:
	    	error( BSException.BSE_DB_UNKNOWN_CONN_TYPE,
		       ERR_UNKNOWN_CONNECTION_TYPE );
	        break;
	}
	
	return conn;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Creates a high-availability session connection. Right now this only
    // works with ASE 12.0 and JConnect 4.2+ or 5.2+.
    ///////////////////////////////////////////////////////////////////////////
    private static DBConnection createHAConnection( BSDBParams params )
	throws BSException
    {
	DBConnection conn = new DBConnection( params );
	conn._url = "jdbc:sybase:jndi:" + params.getJNDIURL() +
		    params.getJNDICtx();
	
	conn._props.put( "REQUEST_HA_SESSION", "true" );
	conn._props.put( Context.INITIAL_CONTEXT_FACTORY, params.getCtxFactory() );
	conn._props.put( Context.PROVIDER_URL, params.getJNDIURL() );
	
	if( params.getDBDriver() == null ) {
	    conn._driver = JCONNECT5_DRIVER;
	} else {
	    conn._driver = params.getDBDriver();
	}
	
	return conn;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Creates a connection using a JConnect driver.
    ///////////////////////////////////////////////////////////////////////////
    private static DBConnection createJConnectConnection( BSDBParams params )
	throws BSException
    {
	DBConnection conn = new DBConnection( params );
	conn._url = params.getDBUrl();

	if( params.getDBDriver() == null ) {
	//Modified By TCG to support JConnect 6 driver
		    conn._driver = JCONNECT7_DRIVER;
	//end of Modification
	} else {
	    conn._driver = params.getDBDriver();
	}
	
	return conn;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Creates a connection to DB2 database.
    ///////////////////////////////////////////////////////////////////////////
    private static DBConnection createDB2Connection( BSDBParams params )
	throws BSException
    {
	DBConnection conn = new DBConnection( params );
	if( params.isNativeDriver() ) {
	    conn._url = params.getDBUrl();
	    if( params.getDBDriver() == null ) {
	        conn._driver = JDBC_DB2_APP_DRIVER;
	    } else {
		conn._driver = params.getDBDriver();
	    }
	} else {
	    conn._url = params.getDBUrl();
	    if( params.getDBDriver() == null ) {
	        conn._driver = JDBC_DB2_NET_DRIVER;
	    } else {
		conn._driver = params.getDBDriver();
	    }
	}
	
	return conn;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Creates a connection to DB2 database.
    ///////////////////////////////////////////////////////////////////////////
    private static DBConnection createDB2UN2Connection( BSDBParams params )
    throws BSException
    {
        DBConnection conn = new DBConnection( params );
        conn._url = params.getDBUrl();
        if( params.getDBDriver() == null ) {
            conn._driver = JDBC_DB2_UN2_DRIVER;
        } else {
            conn._driver = params.getDBDriver();
        }
    
        return conn;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Creates a connection to DB2 database running on an OS390 box.
    ///////////////////////////////////////////////////////////////////////////
    private static DBConnection createDB2390Connection( BSDBParams params )
	throws BSException
    {
	DBConnection conn = new DBConnection( params );
	conn._url = params.getDBUrl();
	if( params.getDBDriver() == null ) {
	    conn._driver = JDBC_DB2390_DRIVER;
	} else {
	    conn._driver = params.getDBDriver();
	}

	return conn;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Creates a connection to an Oracle database.
    ///////////////////////////////////////////////////////////////////////////
    private static DBConnection createOracleConnection( BSDBParams params )
	throws BSException
    {
	DBConnection conn = new DBConnection( params );

	conn._url = params.getDBUrl();
	if( params.getDBDriver() == null ) {
	    conn._driver = JDBC_ORACLE_DRIVER;
	} else {
	    conn._driver = params.getDBDriver();
	}
	
	return conn;
    }

    private static DBConnection createPostgreSQLConnection( BSDBParams params )
	throws BSException
    {
	DBConnection conn = new DBConnection( params );

	conn._url = params.getDBUrl();
	if( params.getDBDriver() == null ) {
	    conn._driver = JDBC_POSTGRESQL_DRIVER;
	} else {
	    conn._driver = params.getDBDriver();
	}
	
	return conn;
    }
    
    //Begin Add by TCG Team for MS SQL Support
   ///////////////////////////////////////////////////////////////////////////
    // Creates a connection to a MS SQL database.
    ///////////////////////////////////////////////////////////////////////////
    private static DBConnection createMSSQLConnection( BSDBParams params )
	throws BSException
    {
    	DBConnection conn = new DBConnection( params );

    	conn._url = params.getDBUrl();
    	if( params.getDBDriver() == null ) {
	      conn._driver = JDBC_MSSQL_DRIVER;
      } else {
  	    conn._driver = params.getDBDriver();
  	  }
    	return conn;
    }
    
    //Begin Add by TCG Team for HANA Support
    ///////////////////////////////////////////////////////////////////////////
	// Creates a connection to a HANA.
	///////////////////////////////////////////////////////////////////////////
    private static DBConnection createHANAConnection( BSDBParams params )
    		throws BSException
    {
    	DBConnection conn = new DBConnection( params );

    	conn._url = params.getDBUrl();
    	if( params.getDBDriver() == null ) {
    		conn._driver = JDBC_HANA_DRIVER;
    	} else {
    		conn._driver = params.getDBDriver();
    	}
    	return conn;
    }
    //End Add

    ///////////////////////////////////////////////////////////////////////////
    // Open the connection.
    ///////////////////////////////////////////////////////////////////////////
    public synchronized void open() throws BSException
    {
	close(); // first close the connection if it was already open
	//Modified By TCG to Load JConnect 6 driver
	//Load the JDBC driver.
	try {
	    try {
			Class.forName( _driver ); 
	    } 
	    catch( Exception ex) {
	    	throw ex;
		// If this was the JCONNECT6_DRIVER, then
		// we should try again with JCONNECT5_DRIVER
			/*if( _driver == JCONNECT6_DRIVER ) {
		    	_driver = JCONNECT5_DRIVER;//try jconnect 5 driver
		    	try	{
		    		Class.forName( _driver );
		  		}
		  		catch(Exception ex2) {
		  			// If this was the JCONNECT5_DRIVER, then
					// we should try again with JCONNECT4_DRIVER
		
					if( _driver == JCONNECT5_DRIVER ) {
			    		_driver = JCONNECT4_DRIVER;//try jconnect 4 driver
			    		Class.forName( _driver );
					}			   
					else {
						throw ex2;
					}
		  		}
		  }
		  else {
			// Just rethrow it and let the error handling below
		   	// worry about it.
		   		throw ex;
		  }*/
	  	}
	}
	//end of Modification
	catch( Exception ex )  {
	    error( BSException.BSE_DB_DRIVER_NOT_FOUND,
	           ERR_JDBC_DRIVER_NOT_FOUND,
		   _driver,
		   ex );
	}

	try {
	    _conn = DriverManager.getConnection( _url, _props );

	    // If we got to this point it means connection was opened,
	    // otherwise an exception would have been thrown.
	    _isOpened = true;

	    // Get DB's metadata and set the desired transaction isolation
	    // level if it is supported.
	    _metaData = _conn.getMetaData();
	    if( _metaData.supportsTransactionIsolationLevel( Connection.TRANSACTION_READ_COMMITTED ) ) {
		_conn.setTransactionIsolation( Connection.TRANSACTION_READ_COMMITTED );
	    }

	    // Allow jConnect connections to retreive large data
	    //Begin Modify by TCG Team for MS SQL Support
        if( _params.getConnectionType() == BSDBParams.CONN_ASA ||
	    	_params.getConnectionType() == BSDBParams.CONN_ASE||
            _params.getConnectionType() == BSDBParams.CONN_MSSQL )
        //End Modify
	    {
		Statement stmt = _conn.createStatement();
		stmt.executeUpdate( "set textsize " + JC_MAX_LOB_LEN );
		stmt.close();
	    }
	} catch( SQLException ex ) {
	    close();
	    error( BSException.BSE_DB_COULD_NOT_CONNECT,
	    	   ERR_COULD_NOT_CONNECT_TO_DB,
		   _driver,
		   _url,
		   ex );
	}
    }


    ///////////////////////////////////////////////////////////////////////////
    // Close the connection.
    ///////////////////////////////////////////////////////////////////////////
    public synchronized void close()
    {
        if( _isOpened ) {
            try {
		// If we get an exception, it's likely that the connection was
		// broken but is complaining about another problem. Therefore,
		// we proceed with the disconnect even when an exception occurs.
		_isOpened = false;

		// We reset the autoCommit variable here because new connections
		// have autoCommit set to true in JDBC, and DBConnectionPool
		// doesn't like DBConnections with autoCommit false.
		_isAutoCommit = true;
		
		// Flush the statement cache before closing the connection
		// (cannot call close on stmts after the connection is closed)
		_statements.clear();
		// Actually close the connection to the database.
                _conn.close();
            } catch( SQLException ex ) {
		// Ignore, since there's not much we can do about it
            }
        }
    }

    private static final String ASESQL_SET_CHAINED_OFF = "set chained off";
    private static final String ASESQL_SET_CHAINED_ON  = "set chained on";

    ///////////////////////////////////////////////////////////////////////////
    // Enables/disables autocommit.
    ///////////////////////////////////////////////////////////////////////////
    public synchronized boolean setAutoCommit( boolean autoCommit )
	throws SQLException
    {
        if( _isOpened ) {
        /*
        //Commented by TCG to make it consistent with other database
	    if( _params.getConnectionType() == BSDBParams.CONN_ASE ) {
	        // ASE: autoCommit on means chained mode off
	        executeUpdate( autoCommit ? ASESQL_SET_CHAINED_OFF :
					    ASESQL_SET_CHAINED_ON );	
	    } else {
		_conn.setAutoCommit( autoCommit );
	    } */
	    _conn.setAutoCommit( autoCommit );
	    _isAutoCommit = autoCommit;
	    return true;
	}
	return false;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Checks if this connection is still alive. Currently we do it by
    // toggling AutoCommit. Note that closed connection is considered dead.
    // WARNING: turning AutoCommit on forces a commit, so don't check liveness
    // in the middle of a transaction!!
    ///////////////////////////////////////////////////////////////////////////
    public synchronized boolean isAlive()
    {
        try {
            if( _params.getConnectionType() == BSDBParams.CONN_ORACLE ) {
                DBResultSet  rset;
                rset = prepareQuery( "select 1 from dual" );
                rset.open();
                rset.close();
                return true;
            } else if ( (_params.getConnectionType() == BSDBParams.CONN_DB2) ||
                        (_params.getConnectionType() == BSDBParams.CONN_DB2_UN2) ) {
                DBResultSet rset;
                rset = prepareQuery( "select grantee from sysibm.sysdbauth" );
                rset.open();
                rset.close();
                return true;
            } else {
                boolean flag = _isAutoCommit;
                return setAutoCommit( !flag ) && setAutoCommit( flag );
            }
        } catch( Exception e ) {
            return false; // if something bad happens assume connection is dead
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Returns the current autoCommit state
    ///////////////////////////////////////////////////////////////////////////
    public synchronized boolean isAutoCommit()
    {
	return _isAutoCommit;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    // Commits the current transaction
    ///////////////////////////////////////////////////////////////////////////
    public synchronized void commit() throws SQLException
    {
	if( _isOpened ) {
		DebugLog.log(Level.FINE,"com.ffusion.banksim.db.DBConnection.commit()");
	    _conn.commit();
	}
    }

    ///////////////////////////////////////////////////////////////////////////
    // Rolls back the current transaction
    ///////////////////////////////////////////////////////////////////////////
    public synchronized void rollback() throws SQLException
    {
	if( _isOpened ) {
		DebugLog.log(Level.FINE,"com.ffusion.banksim.db.DBConnection.rollback()");
	    _conn.rollback();
	}
    }
    
    ///////////////////////////////////////////////////////////////////////////
    // Executes given SQL update command (i.e. not a SELECT statement).
    // Returns the number of columns affected.
    ///////////////////////////////////////////////////////////////////////////
    public synchronized int executeUpdate( String sql ) throws SQLException
    {
        return executeUpdate( sql, null );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Executes given SQL update command (i.e. not a SELECT statement) using
    // specified arguments. Returns the number of columns affected.
    ///////////////////////////////////////////////////////////////////////////
    public synchronized int executeUpdate( String sql, Object [] inArgs )
	throws SQLException
    {
    //Modify by Debasis to remove NullPointerException error
    if ( inArgs != null ) {
		DebugLog.log(Level.FINE,"com.ffusion.banksim.db.DBConnection.executeUpdate()\n" +
			"sql: " + sql + "\n" +
			"inArgs[]: " + Arrays.asList(inArgs).toString());
		}
	else {
		DebugLog.log(Level.FINE,"com.ffusion.banksim.db.DBConnection.executeUpdate()\n" +
			"sql: " + sql);
		}
	//End of Modify by Debasis

	// first lets see if we have given statement in the cache
	PreparedStatement stmt = _statements.get( sql );
	for( int retry = 0; ; retry++ ) {
	    try {
		// stmt is null because it wasn't in the cache
		if( stmt == null ) {
		    String modified = DBSqlUtils.parseStmt( sql,
		    	_params.getConnectionType() );

            if (!modified.equals(sql))
            {
                if ( inArgs != null ) {
                    DebugLog.log(Level.FINE,"com.ffusion.banksim.db.DBConnection.executeUpdate()\n" +
                        "MODIFIED sql: " + modified + "\n" +
                        "inArgs[]: " + Arrays.asList(inArgs).toString());
                    }
                else {
                    DebugLog.log(Level.FINE,"com.ffusion.banksim.db.DBConnection.executeUpdate()\n" +
                        "MODIFIED sql: " + modified);
                    }
            }

		    if( modified.trim().length() > 0 ) {
				stmt = _conn.prepareStatement( modified );
				_statements.put( sql, stmt );
		    }
		}
		// stmt is still null because there's nothing to execute
		if( stmt == null ) {
		    return 0;
		 } else {
		    DBSqlUtils.fillParameters( stmt, inArgs, _params );
		    return stmt.executeUpdate();
		}
	    } catch( SQLException sqle ) {
		// If this isn't a HA session or it's not a socket reset message,
		// then rethrow the exception
		if( !_params.isHA() ||
		    !( sqle.getSQLState().equals( JC_HA_EXCEPTION_STATE )
		    	|| sqle.getSQLState().equals( JC_IO_EXCEPTION_STATE ) ) )
		{
		    throw sqle;
		}
		if( retry >= HA_MAX_RETRIES ) {
		    // Too many retries
		    throw sqle;
		}
	    }
	}
    }

    ///////////////////////////////////////////////////////////////////////////
    // Prepare for execution given SQL query. The query is not executed
    // until open() is called on the returned result set.
    ///////////////////////////////////////////////////////////////////////////
    public synchronized DBResultSet prepareQuery( String query )
	throws SQLException
    {
	DebugLog.log(Level.FINE,"com.ffusion.banksim.db.DBConnection.prepareQuery()\nquery: " + query );

	PreparedStatement stmt = _statements.get( query );
	for( int retry = 0; ; retry++ ) {
	    try {
		if( stmt == null ) {
		    String modified = DBSqlUtils.parseStmt( query,
		    	_params.getConnectionType() );
		    stmt = _conn.prepareStatement( modified );
		    _statements.put( query, stmt );
		}
		return new DBResultSet( stmt, _params );
	    } catch( SQLException sqle ) {
		// If this isn't a HA session or it's not a socket reset message,
		// then rethrow the exception
		if( !_params.isHA() ||
		    !( sqle.getSQLState().equals( JC_HA_EXCEPTION_STATE )
		    	|| sqle.getSQLState().equals( JC_IO_EXCEPTION_STATE ) ) )
		{
		    throw sqle;
		}
		if( retry >= HA_MAX_RETRIES ) {
		    throw sqle; // Too many retries
		}
	    }
	}
    }

    ///////////////////////////////////////////////////////////////////////////
    // Executes a stored procedure using specified SQL command with given
    // parameters. Returns the integer value returned by the stored procedure.
    ///////////////////////////////////////////////////////////////////////////
    public synchronized int executeStoredProcedure( String sql,
    						    Object [] inArgs )
						    throws SQLException
    {
	DebugLog.log(Level.FINE,"com.ffusion.banksim.db.DBConnection.executeStoredProcedure()\n" +
	  	     "sql: " + sql + "\n" +
		     "inArgs[]: " + Arrays.asList(inArgs).toString() );

	CallableStatement stmt = (CallableStatement) _statements.get( sql );
	for( int retry = 0; ; retry++ ) {
	    try {
		if( stmt == null ) {
		    String modified = DBSqlUtils.parseStmt( sql,
		    	_params.getConnectionType() );
		    stmt = _conn.prepareCall( modified );
		    _statements.put( sql, stmt );
		}

		DBSqlUtils.fillParameters( stmt, inArgs, _params );
		stmt.registerOutParameter( inArgs.length + 1, Types.INTEGER );
	        stmt.execute();
		return stmt.getInt( inArgs.length + 1 );
	    } catch( SQLException sqle ) {
		// If this isn't a HA session or it's not a socket reset message,
		// then rethrow the exception
		if( !_params.isHA() ||
		    !( sqle.getSQLState().equals( JC_HA_EXCEPTION_STATE )
		    	|| sqle.getSQLState().equals( JC_IO_EXCEPTION_STATE ) ) )
		{
		    throw sqle;
		}
		if( retry >= HA_MAX_RETRIES ) {
		    // Too many retries
		    throw sqle;
		}
	    }
	}
    }

	/**
	 * Returns a prepared statement object created from the specified Connection.
	 * @param con Connection object from which to create a PreparedStatement
	 * @param sql SQL string with which to create a PreparedStatement
	 * @return PreparedStatement object
	 */
	public synchronized PreparedStatement prepareStatement( DBConnection con, String sql) throws Exception
	{
		PreparedStatement pstmt = _conn.prepareStatement(sql);
		if (pstmt == null)
			throw new Exception("Couldn't prepare statement");
		return pstmt;
	}


	/**
	 * Close the specified Statement.
	 * @param stmt Statement object to be closed
	 */
	public static void closeStatement(Statement stmt)
	{
		try {
			if (stmt != null)
				stmt.close();
		} catch (Exception ignored) {
		}
	}

	/**
	 * execute a prepared statement update
	 * @param pStmt prepared statement to execute
	 * @return return from execute
	 * @exception Exception couldn't create the statement
	 */
	public static int executeUpdate(PreparedStatement pStmt, String sql) throws Exception
	{
		long time = System.currentTimeMillis();
		int ret = 0;
		try {
			ret = pStmt.executeUpdate();
		} finally {
// log here			DirectoryServlet.logSql(sql,time);
		}
		return ret;
	}

	/**
	 * execute a prepared statement query
	 * @param pStmt prepared statement to execute
	 * @return result set
	 * @exception Exception couldn't create the statement
	 */
	public static ResultSet executeQuery(PreparedStatement pStmt, String sql) throws Exception
	{
		long time = System.currentTimeMillis();
		ResultSet rs = null;
		try {
			rs = pStmt.executeQuery();
		} finally {
// log here			DirectoryServlet.logSql(sql,time);
		}
		return rs;
	}



    ///////////////////////////////////////////////////////////////////////////
    // Streams an Oracle LOB to the DB (used for Oracle thin client).
    // NOTE: for this to work proplerly, you MUST have autocommit turned off,
    //       and the row in question must already be locked (i.e. by first doing
    //       an update with autocommit off) or you must have the for update clause
    //       on the select.
    // Parameters:
    //    lobSelect - select for the LOB columns
    //    id        - id for the where clause of the lobSelect
    //    parms     - array of String and byte[] for the stream.
    ///////////////////////////////////////////////////////////////////////////
    public synchronized void executeOracleThinLOBStream( String lobSelect, int id, Object[] parms )
    	throws SQLException, IOException
    {
	// We delegate this to a static method in another class because the class loader
	// was forcing the load of the oracle.sql.* classes, even if you never used
	// this method.  Which is a bad thing if you don't happen to have the oracle
	// stuff in your classpath.
	if( _ocwh == null ) _ocwh = new OracleClassWrapperHack();
	_ocwh.executeOracleThinLOBStream( this, lobSelect, id, parms );
    }
    
    // Returns the driver class for this connection
    public final String getDriverType() {
	return _driver;
    }

    // Returns true if the connection is open
    public final boolean isOpened() {
	return _isOpened;
    }

    // Returns the connection params
    public final BSDBParams getParams() {
	return _params;
    }
    
    // Returns the underlying connection object
    // This is set to package access as this is purely for
    // DBTransfer.addTransfer() against ASE
    final Connection getConnection()
    {
    	return _conn;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    // Throws an exception containing an error message with no arguments and
    // a given resource bundle key.
    ///////////////////////////////////////////////////////////////////////////
    private static void error( int errCode, String key ) throws BSException
    {
	throw new BSException( errCode, MessageText.getMessage( key ) );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Throws an exception containing an error message with no arguments and
    // a given resource bundle key.
    ///////////////////////////////////////////////////////////////////////////
    private static void error( int errCode, String key, Throwable e ) throws BSException
    {
	throw new BSException( errCode, MessageText.getMessage( key ), e );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Throws an exception containing an error message with one argument and a
    // given resource bundle key.
    ///////////////////////////////////////////////////////////////////////////
    private static void error( int errCode, String key, String arg ) throws BSException
    {
	throw new BSException( errCode, MessageText.getMessage( key, arg ) );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Throws an exception containing an error message with one argument and a
    // given resource bundle key.
    ///////////////////////////////////////////////////////////////////////////
    private static void error( int errCode, String key, String arg, Throwable e )
    	throws BSException
    {
	throw new BSException( errCode, MessageText.getMessage( key, arg ), e );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Throws an exception containing an error message with two arguments and a
    // given resource bundle key.
    ///////////////////////////////////////////////////////////////////////////
    private static void error( int    errCode,
    			       String key,
			       String arg1,
    			       String arg2 ) throws BSException
    {
	throw new BSException( errCode, MessageText.getMessage( key, arg1, arg2 ) );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Throws an exception containing an error message with two arguments and a
    // given resource bundle key.
    ///////////////////////////////////////////////////////////////////////////
    private static void error( int       errCode,
    			       String    key,
			       String    arg1,
    			       String    arg2,
			       Throwable e ) throws BSException
    {
	throw new BSException( errCode, MessageText.getMessage( key, arg1, arg2 ), e );
    }
    
    // This is here to prevent class loader issues when not using oracle.
    private static OracleClassWrapperHack _ocwh = null;
    
    private Connection       _conn;         // The actual connection to the DB.
    private DatabaseMetaData _metaData;     // Metadata for the connected DB.
    private String           _url;          // URL of the connected DB.
    private String           _driver;       // Driver class used by this connection
    private boolean          _isOpened;     // True if the connection is open
    private boolean          _isAutoCommit; // True if the connection is set to autocommit
    private BSDBParams       _params;       // Connection parameters.
    private Properties       _props;        // Connection properties.
    private StatementsCache  _statements = new StatementsCache();
}
