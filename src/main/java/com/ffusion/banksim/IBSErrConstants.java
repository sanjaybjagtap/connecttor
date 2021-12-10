//
// IAEErrConstants.java
//
// Copyright (c) 2001 Financial Fusion, Inc.
// All Rights Reserved.
//

package com.ffusion.banksim;

///////////////////////////////////////////////////////////////////////////////
// Interface with constants identifying error messages in a resource bundle.
///////////////////////////////////////////////////////////////////////////////
public interface IBSErrConstants
{ 
    // Name of the resource bundle.
    static final String RESOURCE_BUNDLE = "com.ffusion.banksim.lang.BSErrStrings";
    
    // Error Messages
    static final String ERR_NOT_INITIALIZED		= "ERR_NOT_INITIALIZED";
    static final String ERR_ALREADY_INITIALIZED		= "ERR_ALREADY_INITIALIZED";
    static final String ERR_UNKNOWN_DB_TYPE		= "ERR_UNKNOWN_DB_TYPE";
}
