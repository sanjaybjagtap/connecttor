//
// IAEErrConstants.java
//
// Copyright (c) 2001 Financial Fusion, Inc.
// All Rights Reserved.
//

package com.ffusion.banksim.db;

///////////////////////////////////////////////////////////////////////////////
// Interface with constants identifying error messages in a resource bundle.
///////////////////////////////////////////////////////////////////////////////
public interface IBSErrConstants
{ 
    // Name of the resource bundle.
    static final String RESOURCE_BUNDLE = "com.ffusion.banksim.db.lang.BSErrStrings";
    
    // Error Messages
    static final String ERR_JDBC_DRIVER_NOT_FOUND	= "ERR_JDBC_DRIVER_NOT_FOUND";
    static final String ERR_COULD_NOT_CONNECT_TO_DB	= "ERR_COULD_NOT_CONNECT_TO_DB";
    static final String ERR_UNKNOWN_CONNECTION_TYPE	= "ERR_UNKNOWN_CONNECTION_TYPE";
    static final String ERR_NO_REPOSITORY		= "ERR_NO_REPOSITORY";
    static final String ERR_REPOSITORY_TOO_OLD		= "ERR_REPOSITORY_TOO_OLD";
    static final String ERR_REPOSITORY_TOO_NEW		= "ERR_REPOSITORY_TOO_NEW";
    static final String ERR_CREATING_REPOSITORY		= "ERR_CREATING_REPOSITORY";
    static final String ERR_DESTROYING_REPOSITORY	= "ERR_DESTROYING_REPOSITORY";
    static final String ERR_INVALID_NAME_OR_PASS        = "ERR_INVALID_NAME_OR_PASS";
    static final String ERR_NAME_EXISTS                 = "ERR_NAME_EXISTS";
    static final String ERR_BAD_ENCODING                = "ERR_BAD_ENCODING";
    
    // Customers
    static final String ERR_CUSTOMER_EXISTS		= "ERR_CUSTOMER_EXISTS";
    static final String ERR_USERNAME_EXISTS		= "ERR_USERNAME_EXISTS";
    static final String ERR_CUSTOMER_NOT_EXISTS		= "ERR_CUSTOMER_NOT_EXISTS";
    
    // Accounts
    static final String ERR_ACCOUNT_EXISTS		= "ERR_ACCOUNT_EXISTS";
    static final String ERR_ACC_FI_NOT_EXIST		= "ERR_ACC_FI_NOT_EXIST";
    static final String ERR_ACCOUNT_NOT_EXISTS		= "ERR_ACCOUNT_NOT_EXISTS";

    // Financial Institutions
    static final String ERR_FI_EXISTS			= "ERR_FI_EXISTS";
    static final String ERR_FI_NAME_EXISTS		= "ERR_FI_NAME_EXISTS";
    static final String ERR_FI_NOT_EXISTS		= "ERR_FI_NOT_EXISTS";
    
    // Client
    static final String ERR_USERID_NOT_EXISTS		= "ERR_USERID_NOT_EXISTS";
    static final String ERR_INCORRECT_PASSWORD		= "ERR_INCORRECT_PASSWORD";

    // Transfers
    static final String ERR_FROM_ACC_NOT_EXIST		= "ERR_FROM_ACC_NOT_EXIST";
    static final String ERR_FROM_ACC_NSF		= "ERR_FROM_ACC_NSF";
    static final String ERR_AMOUNT_NOT_POSITIVE		= "ERR_AMOUNT_NOT_POSITIVE";
    static final String ERR_ACCOUNTS_SAME		= "ERR_ACCOUNTS_SAME";

    // Paged Transactions
    static final String ERR_ACCOUNT_NOT_PAGED		= "ERR_ACCOUNT_NOT_PAGED";

    // Lockbox
    static final String ERR_INVALID_CREDIT_ITEM_INDEX   = "ERR_INVALID_CREDIT_ITEM_INDEX";
    static final String ERR_INVALID_TRANSACTION_INDEX   = "ERR_INVALID_TRANSACTION_INDEX";
}
