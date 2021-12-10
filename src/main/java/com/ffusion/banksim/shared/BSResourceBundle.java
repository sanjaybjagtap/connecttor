//
// BSResourceBundle.java
//
// Copyright (c) 2001 Financial Fusion, Inc.
// All Rights Reserved.
//

package com.ffusion.banksim.shared;

import java.text.MessageFormat;
import java.util.ResourceBundle;

///////////////////////////////////////////////////////////////////////////////
// Utility class for extracting messages from a resource bundle.
///////////////////////////////////////////////////////////////////////////////
public final class BSResourceBundle
{
    public BSResourceBundle( String bundleName )
    {
	_bundleName = bundleName; // don't load the resource bundle yet
    }

    ///////////////////////////////////////////////////////////////////////////
    // Retrieves a message with no arguments and identified by given key.
    ///////////////////////////////////////////////////////////////////////////
    public final String getMessage( String key )
    {
	if( _msgs == null ) {
	    // load the resource bundle only when needed because it's expensive
	    _msgs = ResourceBundle.getBundle( _bundleName );
	}
	return _msgs.getString( key );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Retrieves a message with given arguments and identified by given key.
    ///////////////////////////////////////////////////////////////////////////
    public final String getMessage( String key, Object[] args )
    {
	return MessageFormat.format( getMessage( key ), args );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Retrieves a message with one argument and identified by given key.
    ///////////////////////////////////////////////////////////////////////////
    public final String getMessage( String key, Object arg )
    {
	return getMessage( key, new Object[]{ arg } );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Retrieves a message with two arguments and identified by given key.
    ///////////////////////////////////////////////////////////////////////////
    public final String getMessage( String key, Object arg1, Object arg2 )
    {
	return getMessage( key, new Object[]{ arg1, arg2 } );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Retrieves a message with three arguments and identified by given key.
    ///////////////////////////////////////////////////////////////////////////
    public final String getMessage( String key, Object arg1, Object arg2,
    				    Object arg3 )
    {
	return getMessage( key, new Object[]{ arg1, arg2, arg3 } );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Retrieves a message with four arguments and identified by given key.
    ///////////////////////////////////////////////////////////////////////////
    public final String getMessage( String key, Object arg1, Object arg2,
    				    Object arg3, Object arg4 )
    {
	return getMessage( key, new Object[]{ arg1, arg2, arg3, arg4 } );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Retrieves a message with five arguments and identified by given key.
    ///////////////////////////////////////////////////////////////////////////
    public final String getMessage( String key, Object arg1, Object arg2,
    				    Object arg3, Object arg4, Object arg5 )
    {
	return getMessage( key, new Object[]{ arg1, arg2, arg3, arg4, arg5 } );
    }

    private ResourceBundle _msgs = null; // points to a resource bundle with messages
    private String         _bundleName;
}
