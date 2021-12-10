//
// BSErrStrings.java
//
// Copyright (c) 2001 Financial Fusion, Inc.
// All Rights Reserved.
//

package com.ffusion.banksim.lang;

import java.util.ListResourceBundle;

import com.ffusion.banksim.IBSErrConstants;

public class BSErrStrings extends ListResourceBundle implements IBSErrConstants
{
    public Object[][] getContents() { return contents; }

    // Use this as a new line separator instead of "\r\n" or "\n".
    private static final String LINE_SEP = System.getProperty( "line.separator" );
    
    static final Object[][] contents =
    {
	// Error Messages
	{ERR_NOT_INITIALIZED,		"The Bank Simulator was not initialized properly." + LINE_SEP + "Use the BankSim.initialize() to initialize the Bank Simulator." },
	{ERR_ALREADY_INITIALIZED,	"The Bank Simulator has already been initialized" },
	{ERR_UNKNOWN_DB_TYPE,		"The database type value passed in is unknown" },
    };
}
