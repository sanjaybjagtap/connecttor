//
// BSErrStrings.java
//
// Copyright (c) 2001 Financial Fusion, Inc.
// All Rights Reserved.
//

package com.ffusion.banksim.db.lang;

import java.util.ListResourceBundle;

import com.ffusion.banksim.db.IBSErrConstants;

public class BSErrStrings extends ListResourceBundle implements IBSErrConstants
{
    public Object[][] getContents() { return contents; }

    // Use this as a new line separator instead of "\r\n" or "\n".
    private static final String LINE_SEP = System.getProperty( "line.separator" );
    
    static final Object[][] contents =
    {
	// Error Messages
	{ERR_JDBC_DRIVER_NOT_FOUND,	"Could not connect to database because JDBC driver \"{0}\" was not found."},
	{ERR_COULD_NOT_CONNECT_TO_DB,	"Could not connect to database using driver \"{0}\" and url \"{1}\""},
	{ERR_UNKNOWN_CONNECTION_TYPE,	"Unknown database connection type."},
	{ERR_NO_REPOSITORY,		"A repository does not exist in this database."},
	{ERR_REPOSITORY_TOO_OLD,	"This repository requires an older version of the Universal Alerts Engine"},
	{ERR_REPOSITORY_TOO_NEW,	"This repository requires a newer version of the Universal Alerts Engine"},
	{ERR_CREATING_REPOSITORY,	"A database error occured while creating the repository."},
	{ERR_DESTROYING_REPOSITORY,	"A database error occured while destroying the repository."},
	{ERR_INVALID_NAME_OR_PASS,      "The name or password specified is invalid."},
	{ERR_NAME_EXISTS,               "The specified name already exists."},
	{ERR_BAD_ENCODING,              "Encoding \"{0}\" is not supported. Using platform default encoding instead."},
	
	// Customers
	{ERR_CUSTOMER_EXISTS,           "A customer with the same ID as the customer being added already exists."},
	{ERR_USERNAME_EXISTS,           "A customer with the same user name as the customer being updated already exists."},
	{ERR_CUSTOMER_NOT_EXISTS,       "The specified customer does not exist in the database"},
	
	// Accounts
	{ERR_ACCOUNT_EXISTS,            "An account with the same ID as the account being added already exists."},
	{ERR_ACC_FI_NOT_EXIST,		"The bank referenced in the account being added does not exist in the Bank Simulator."},
	{ERR_ACCOUNT_NOT_EXISTS,        "The specified account does not exist in the database"},

	// Financial Institutions
	{ERR_FI_EXISTS,            	"A bank with the same ID as the bank being added already exists."},
	{ERR_FI_NAME_EXISTS,           	"A bank with the same name as the bank being added already exists."},
	{ERR_FI_NOT_EXISTS,             "The specified bank does not exist in the database"},
	
	// Client
	{ERR_USERID_NOT_EXISTS,		"The userID does not exist in the database"},
	{ERR_INCORRECT_PASSWORD,	"The password is incorrect"},

	// Transfers
	{ERR_FROM_ACC_NOT_EXIST,       	"The account to transfer funds from does not exist."},
	{ERR_FROM_ACC_NSF,       	"There are insufficient funds to complete the transfer."},
	{ERR_AMOUNT_NOT_POSITIVE,      	"There amount being transferred cannot be negative or zero."},
	{ERR_ACCOUNTS_SAME,      	"The account to transfer funds from and the account to transfer those funds to are the same account."},

	// Paged Transactions
	{ERR_ACCOUNT_NOT_PAGED,      	"The paged transaction operation failed since the specified account does not have a set of transactions opened for paged access.  Use BankSim.openPagedTransactions() to open a set of transactions for paged access."},

	// Lockox
	{ERR_INVALID_CREDIT_ITEM_INDEX, "The Credit Item contains an invalid item index."},
	{ERR_INVALID_TRANSACTION_INDEX, "The Lockbox Transaction contains an invalid index."},
    };
}
