//
// MessageText.java
//
// Copyright (c) 2001 Financial Fusion, Inc.
// All Rights Reserved.
//

package com.ffusion.banksim.db;

import com.ffusion.banksim.shared.*;

import java.util.Properties;
 
public class MessageText implements IBSErrConstants
{
    ///////////////////////////////////////////////////////////////////////////
    // Following methods are dealing with database support's resource bundle.
    ///////////////////////////////////////////////////////////////////////////
    public static String getMessage( String key )
    {
	return _msgs.getMessage( key );
    }

    public static String getMessage( String key, Throwable e )
    {
	return _msgs.getMessage( key, e );
    }

    public static String getMessage( String key, String arg )
    {
	return _msgs.getMessage( key, arg );
    }

    public static String getMessage( String key, String arg, Throwable e )
    {
	return _msgs.getMessage( key, arg, e );
    }
    
    public static String getMessage( String key, String arg1, String arg2 )
    {
	return _msgs.getMessage( key, arg1, arg2 );
    }

    public static String getMessage( String key, String arg1, String arg2, Throwable e )
    {
	return _msgs.getMessage( key, arg1, arg2, e );
    }

    public static BSResourceBundle getMessages()
    {
        return _msgs;
    }

    // DB Support gets all its message strings from here.
    private static BSResourceBundle _msgs = new BSResourceBundle( RESOURCE_BUNDLE );
}
