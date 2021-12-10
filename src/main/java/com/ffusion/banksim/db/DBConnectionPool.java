//
// DBConnectionPool.java
//
// Copyright (c) 2001 Financial Fusion, Inc.
// All Rights Reserved.
//

package com.ffusion.banksim.db;

import com.ffusion.banksim.interfaces.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashSet;

public class DBConnectionPool
{
    public DBConnectionPool( BSDBParams params, int maxConnections )
    {
	_maxConnections  = maxConnections < 1 ? 1 : maxConnections;
	_params          = params;
	_freeConnections = new ArrayList( maxConnections );

	int setSize = _maxConnections < 5 ? 11 : 2 * _maxConnections + 1;
	_connections     = new HashSet( setSize );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Take a connection from the pool
    ///////////////////////////////////////////////////////////////////////////
    public synchronized DBConnection getConnection() throws BSException
    {
	return doGetConnection( true );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Take a connection from the pool, with the option of not respecting
    // the maximum connection pool size.
    ///////////////////////////////////////////////////////////////////////////
    private DBConnection doGetConnection( boolean respectMaxConn )
	throws BSException
    {
	DBConnection conn = null;
	
	while( conn == null ) {
	    int freeConnSize = _freeConnections.size();
	    if( freeConnSize == 0 ) {
		// If there are no available connections, create one
		// if we haven't already reached the maximum
		if( respectMaxConn && _connections.size() == _maxConnections ) {
		    throw new BSException( BSException.BSE_DB_MAX_CONN_POOL_SIZE );
		}
		conn = DBConnection.create( _params );
		conn.open();
		_connections.add( conn );
	    } else {
		// We have a free connection, so use it
		conn = (DBConnection) _freeConnections.remove( freeConnSize - 1 );
		if( !conn.isAlive() ) {
		    // This connection is dead so lets get rid of it.
		    _connections.remove( conn );
		    conn.close();
		    conn = null; // we still don't have a connection

		}
	    }
	}
	return conn;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Put the connection back in the pool, committing any transactions first
    ///////////////////////////////////////////////////////////////////////////
    public synchronized void releaseConnection( DBConnection conn )
        throws BSException
    {
	if( !_connections.contains( conn ) ) {
	    // You can't release a connection to this pool unless
	    // it came from here
	    throw new BSException( BSException.BSE_DB_CONN_NOT_IN_POOL );
	}

	// If somebody returned the connection with an active transaction,
	// throw an exception because this could be a problem later
	if( !conn.isAutoCommit() ) {
	    throw new BSException( BSException.BSE_DB_UNCOMMITTED_TRANS );
	}
	
	// If we have space in the pool i.e. if the maximum pool size was not
	// decreased then add this connection to the free list, else close it
	// and remove any references to it.
	if( _connections.size() <= _maxConnections ) {
	    _freeConnections.add( conn );
	} else {
	    _connections.remove( conn );
	    conn.close();
	}
    }

    ///////////////////////////////////////////////////////////////////////////
    // Replace a dead connection with a new one from the pool.  If you pass
    // in a connection from this pool, you are guaranteed that you will get
    // a connection out, unless something horrible happens in the DB (ie. it
    // doesn't matter if the number of connections is greater than the max,
    // because since you had one before, you'll still have one after).
    ///////////////////////////////////////////////////////////////////////////
    public synchronized DBConnection renewConnection( DBConnection conn )
    	throws BSException
    {
	// Try to reuse the existing connection if we can
	DBConnection newConn = conn;
	
	if( !_connections.contains( conn ) ) {
	    // You can't renew a connection unless
	    // it came from this pool
	    throw new BSException( BSException.BSE_DB_CONN_NOT_IN_POOL );
	}

	if( conn.isAlive() ) {
	    // There's nothing wrong with the connection,
	    // just make sure autoCommit is on.
	    if( !conn.isAutoCommit() ) {
		try {
		    conn.setAutoCommit( true );
		} catch( SQLException sqle ) {
		    // Need a new connection
		    newConn = null;
		}
	    }
	} else {
	    // Need a new connection
	    newConn = null;
	}

	if( newConn == null ) {
	    // Allocate a new connection before we
	    // remove the current one from the list.
	    newConn = doGetConnection( false );
	    _connections.remove( conn );
	    conn.close();
	    conn = null;
	}
	return newConn;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    // Close ALL connections, even the ones in use elsewhere
    ///////////////////////////////////////////////////////////////////////////
    public synchronized void clear()
    {
	Iterator i = _connections.iterator();
	while( i.hasNext() ) {
	    ((DBConnection) i.next()).close();
	}
	_connections.clear();
	_freeConnections.clear();
    }
    
    ///////////////////////////////////////////////////////////////////////////
    // Get the BSDBParams used to create connections for this pool.
    ///////////////////////////////////////////////////////////////////////////
    public BSDBParams getParams()
    {
	return _params;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    // Get the maximum number of connections this pool can hold.
    ///////////////////////////////////////////////////////////////////////////
    public final int getMaxConnections()
    {
	return _maxConnections;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Set the maximum number of connections to hold in the pool.
    ///////////////////////////////////////////////////////////////////////////
    public synchronized void setMaxConnections( int size )
    {
	int newSize = size < 1 ? 1 : size;
	if( _maxConnections > newSize ) {
	    removeFreeConnections( _maxConnections - newSize );
	}
	_maxConnections = newSize;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Closes given number of free connections and removes references to them
    // from the pool.
    ///////////////////////////////////////////////////////////////////////////
    private void removeFreeConnections( int num )
    {
	int freeCount = _freeConnections.size();
	int removeCount = num > freeCount ? freeCount : num;
	DBConnection conn;
	for( int i = 1; i <= removeCount; i++ ) {
	    conn = (DBConnection) _freeConnections.remove( freeCount - i );
	    _connections.remove( conn );
	    conn.close();
	}
    }

    protected void finalize()
    {
	clear();
    }
    
    private int        _maxConnections;
    private BSDBParams _params;
    private HashSet    _connections;
    private ArrayList  _freeConnections;
}
