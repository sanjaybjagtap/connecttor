//
// BankSim.java
//
// Copyright (c) 2002 Financial Fusion, Inc.
// All Rights Reserved.
//

package com.ffusion.banksim.proxy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.ffusion.banksim.db.DBConnectionDefines;
import com.ffusion.banksim.interfaces.BSDBParams;
import com.ffusion.banksim.interfaces.BSException;
import com.ffusion.banksim.interfaces.BankingBackend;
import com.ffusion.beans.Bank;
import com.ffusion.beans.accounts.Account;
import com.ffusion.beans.accounts.Accounts;
import com.ffusion.beans.banking.Transaction;
import com.ffusion.beans.banking.TransactionTypes;
import com.ffusion.beans.banking.Transactions;
import com.ffusion.beans.banking.Transfer;
import com.ffusion.beans.common.Currency;
import com.ffusion.beans.messages.Message;
import com.ffusion.beans.user.User;
import com.ffusion.beans.util.BeansConverter;
import com.ffusion.ffs.bpw.interfaces.IntraTrnInfo;
import com.ffusion.services.InitFileHandler;
import com.ffusion.services.InitFileHandler.TempXMLHandler;
import com.ffusion.util.beans.PagingContext;
import com.ffusion.util.logging.DebugLog;
import com.sap.banking.commonutilconfig.bo.interfaces.CommonFileConfig;
import com.sap.banking.configuration.services.beans.ConfigurationProperties;

public class BankingBackendImpl implements BankingBackend, DBConnectionDefines
{
	private static final Logger logger = LoggerFactory.getLogger(BankingBackendImpl.class);
	
	private String dbUrl;
	private String dbUser;
	private String dbPassword;
	private String dbDriver;
	private String dbType;
	private String dbMaxConnections;

	private static long updateCount=0;
	private static final String IS_PAYTOCONTACT= "isPayToContact";
	private static final String MEMO= "Memo";
	private static final String VALIDATE_TO_ACCOUNT= "validateToAccount";
		
	public String getDbUrl() {
		return dbUrl;
	}

	public void setDbUrl(String dbUrl) {
		this.dbUrl = dbUrl;
	}

	public String getDbUser() {
		return dbUser;
	}

	public void setDbUser(String dbUser) {
		this.dbUser = dbUser;
	}

	public String getDbPassword() {
		return dbPassword;
	}

	public void setDbPassword(String dbPassword) {
		this.dbPassword = dbPassword;
	}

	public String getDbDriver() {
		return dbDriver;
	}

	public void setDbDriver(String dbDriver) {
		this.dbDriver = dbDriver;
	}

	public String getDbMaxConnections() {
		return dbMaxConnections;
	}

	
	public void setDbMaxConnections(String dbMaxConnections) {
		 this.dbMaxConnections=dbMaxConnections;
	}


	public String getDbType() {
		return dbType;
	}
	
	public void setDbType(String dbType ) {
		 this.dbType=dbType;
	}
	


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
	public BSDBParams getBSDBParams( String user,
    				      	    String password,
				            String url,
				            int dbType,
				            boolean useNativeDriver ) throws BSException
    {
	return com.ffusion.banksim.BankSim.getBSDBParams( user, password, url, dbType, useNativeDriver );
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
	public BSDBParams getBSDBParams( String user,
    				      	    String password,
				            String host,
				            String port,
				            String dbName,
				            int dbType,
				            boolean useNativeDriver ) throws BSException
    {
	return com.ffusion.banksim.BankSim.getBSDBParams( user,
							  password,
							  host,
							  port,
							  dbName,
							  dbType,
							  useNativeDriver );
    }
    
    public void init() {
    	
    	BSDBParams params = null;
		int poolSize;
			
		//initialize(url, new BanksimXMLHandler());
    	
		try {
			params = getBSDBParams(dbUser, dbPassword, dbUrl, dbType,false);
			params.setDBDriver(dbDriver);
			poolSize = Integer.valueOf(getDbMaxConnections()).intValue();  
			initialize(params, poolSize);
		} catch (BSException bse) {
			bse.printStackTrace();
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
	public BSDBParams getBSDBParams( String user,
    				      	    String password,
				            String url,
				            String dbType,
				            boolean useNativeDriver ) throws BSException
    {
	return com.ffusion.banksim.BankSim.getBSDBParams( user, password, url, dbType, useNativeDriver );
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
	public BSDBParams getBSDBParams( String user,
    				      	    String password,
				            String host,
				            String port,
				            String dbName,
				            String dbType,
				            boolean useNativeDriver ) throws BSException
    {
	return com.ffusion.banksim.BankSim.getBSDBParams( user,
							  password,
							  host,
							  port,
							  dbName,
							  dbType,
							  useNativeDriver );
    }

	/**
	 * initialize - Initialize a BankSim with DBParameters and connection pool size
	 * @param params BSDBParams object
	 * @param poolSize an int
	 * @exception BSException that states the BankSim is already initialized 
	 */
	public void initialize( BSDBParams params, int poolSize ) throws BSException
    {
	com.ffusion.banksim.BankSim.initialize( params, poolSize );
    }

	/**
	 * initialize - Initialize a banksim with DBParameters and connection pool size
	 * and initial properties
	 * @param params BSDBParams objectconnection parameters to the banksim db
	 * @param poolSize The maximum size of the connection pool
	 * @param props initial properties to use.  May be null.
	 * @exception BSException that states the banksim is already initialized 
	 */
	public void initialize( BSDBParams params, int poolSize, Properties props ) throws BSException
    {
	com.ffusion.banksim.BankSim.initialize( params, poolSize, props );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#isInitialized()
	 */
    @Override
	public boolean isInitialized()
    {
	return com.ffusion.banksim.BankSim.isInitialized();
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#signOn(java.lang.String, java.lang.String)
	 */
    @Override
	public User signOn( String userID, String password ) throws BSException
    {
	return com.ffusion.banksim.BankSim.signOn( userID, password );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#setPassword(com.ffusion.beans.user.User, java.lang.String)
	 */
    @Override
	public void setPassword( User customer, String newPassword ) throws BSException
    {
	com.ffusion.banksim.BankSim.setPassword( customer, newPassword );
    }

    /**
	 * addBank - Adds a bank to the Bank Simulator
	 * @param bank a populated Bank object whose Bank is being added.
	 */
	public void addBank( Bank bank ) throws BSException
    {
	com.ffusion.banksim.BankSim.addBank( bank );
    }

	/**
	 * addBanks - Adds a bank to the Bank Simulator
	 * @param banks an array of populated Bank objects to add.
	 */
	public void addBanks( Bank[] banks ) throws BSException
    {
	com.ffusion.banksim.BankSim.addBanks( banks );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#getBank(java.lang.String)
	 */
    @Override
	public Bank getBank( String name ) throws BSException
    {
	return com.ffusion.banksim.BankSim.getBank( name );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#updateBank(com.ffusion.beans.Bank)
	 */
    @Override
	public void updateBank( Bank bank ) throws BSException
    {
	com.ffusion.banksim.BankSim.updateBank( bank );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#updateBanks(com.ffusion.beans.Bank[])
	 */
    @Override
	public void updateBanks( Bank[] bank ) throws BSException
    {
	com.ffusion.banksim.BankSim.updateBanks( bank );
    }
    
    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#deleteBank(com.ffusion.beans.Bank)
	 */
    @Override
	public void deleteBank( Bank bank ) throws BSException
    {
	com.ffusion.banksim.BankSim.deleteBank( bank );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#addCustomer(com.ffusion.beans.user.User)
	 */
    @Override
	public void addCustomer( User customer ) throws BSException
    {
	com.ffusion.banksim.BankSim.addCustomer( customer );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#addCustomers(com.ffusion.beans.user.User[])
	 */
    @Override
	public void addCustomers( User[] customers ) throws BSException
    {
	com.ffusion.banksim.BankSim.addCustomers( customers );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#updateCustomer(com.ffusion.beans.user.User)
	 */
    @Override
	public void updateCustomer( User customer ) throws BSException
    {
	com.ffusion.banksim.BankSim.updateCustomer( customer );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#updateCustomers(com.ffusion.beans.user.User[])
	 */
    @Override
	public void updateCustomers( User[] customers ) throws BSException
    {
	com.ffusion.banksim.BankSim.updateCustomers( customers );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#deleteCustomer(com.ffusion.beans.user.User)
	 */
    @Override
	public void deleteCustomer( User customer ) throws BSException
    {
	com.ffusion.banksim.BankSim.deleteCustomer( customer );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#addAccount(com.ffusion.beans.user.User, com.ffusion.beans.accounts.Account)
	 */
    @Override
	public void addAccount( User customer, Account toAdd ) throws BSException
    {
	com.ffusion.banksim.BankSim.addAccount( customer, toAdd );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#addAccounts(com.ffusion.beans.user.User, com.ffusion.beans.accounts.Account[])
	 */
    @Override
	public void addAccounts( User customer, Account[] toAdd ) throws BSException
    {
	com.ffusion.banksim.BankSim.addAccounts( customer, toAdd );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#getAccounts(com.ffusion.beans.user.User)
	 */
    @Override
	public Enumeration getAccounts( User customer ) throws BSException
    {
	return com.ffusion.banksim.BankSim.getAccounts( customer );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#getAccount(com.ffusion.beans.accounts.Account)
	 */
    @Override
	public Account getAccount( Account account ) throws BSException
    {
	return com.ffusion.banksim.BankSim.getAccount( account );
    }
    
    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#updateAccount(com.ffusion.beans.accounts.Account)
	 */
    @Override
	public void updateAccount( Account account ) throws BSException
    {
	com.ffusion.banksim.BankSim.updateAccount( account );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#updateAccounts(com.ffusion.beans.accounts.Account[])
	 */
    @Override
	public void updateAccounts( Account[] accounts ) throws BSException
    {
	com.ffusion.banksim.BankSim.updateAccounts( accounts );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#deleteAccount(com.ffusion.beans.accounts.Account)
	 */
    @Override
	public void deleteAccount( Account account ) throws BSException
    {
	com.ffusion.banksim.BankSim.deleteAccount( account );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#addTransfer(com.ffusion.beans.banking.Transfer, int)
	 */
    @Override
	public Transfer addTransfer( Transfer transfer, int transType ) throws BSException
    {
	return com.ffusion.banksim.BankSim.addTransfer( transfer, transType );
    }

	/* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#addBPWTransfer(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void addBPWTransfer(String bankId, String acctIdTo, String acctTypeTo, String acctIdFrom, String acctTypeFrom, String amount, String curDef) throws BSException
	{
		addBPWTransfer(bankId, acctIdTo, acctTypeTo, acctIdFrom, acctTypeFrom, amount, curDef, null, null, TransactionTypes.TYPE_TRANSFER);
	}
	
	
	@Override
	public void addBPWTransfer(IntraTrnInfo info, int transType) throws BSException
	{
		String bankId = info.bankId;
		String acctIdTo =  info.acctIdTo;
		String acctTypeTo = info.acctTypeTo;
		String acctIdFrom = info.acctIdFrom;
		String acctTypeFrom = info.acctTypeFrom;
		String amount = info.amount;
		String curDef = info.curDef;
		String toAmount = info.toAmount;
		String toAmtCurrency = info.toAmtCurrency;
		
		Transfer transfer = new Transfer();
		if(info.extraFields != null && ((HashMap)info.extraFields).get(IS_PAYTOCONTACT) != null){
			if(((HashMap)info.extraFields).get(IS_PAYTOCONTACT).equals("true")){
				transfer.put(VALIDATE_TO_ACCOUNT, "false");
			}
		}
		
		if(info.extraFields != null && ((HashMap)info.extraFields).get(MEMO) != null){
			// set memo
	        transfer.setMemo((String)((HashMap)info.extraFields).get(MEMO));
		} else {
			// set memo
	        transfer.setMemo("From the BPW Intra-Bank Transaction Handler");
		}
        // set amount
        transfer.setAmount(new Currency(amount, curDef, transfer.getLocale()));

        // set to amount for multi-currency transfers
        if (validNonZeroAmount(toAmount))
            transfer.setToAmount(new Currency(toAmount, toAmtCurrency, transfer.getLocale()));


        // Create the account objects for the backend
        Accounts accounts = new Accounts();

        Account from = accounts.create(bankId, acctIdFrom, BeansConverter.getBPWAccountType(acctTypeFrom));
        from.setCurrencyCode(curDef);
        acctIdTo = acctIdTo == null ? "" : acctIdTo;
        Account to = accounts.create(bankId, acctIdTo, BeansConverter.getBPWAccountType(acctTypeTo));
        if (toAmtCurrency == null || toAmtCurrency.length() == 0)
        	to.setCurrencyCode(curDef);  // single-currency transfer
        else
        	to.setCurrencyCode(toAmtCurrency);  // multi-currency transfer

        // Complete the transfer object by giving it the accounts to use
        transfer.setFromAccount(from);
        transfer.setToAccount(to);


        addTransfer(transfer, transType);
        if(info.extraFields != null && ((HashMap)info.extraFields).get(IS_PAYTOCONTACT) != null){
			if(((HashMap)info.extraFields).get(IS_PAYTOCONTACT).equals("true")){
				info.setConfirmNum(transfer.getReferenceNumber());
			}
		}
        
	}
	/* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#addBPWTransfer(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, int)
	 */
	@Override
	public void addBPWTransfer(String bankId, String acctIdTo, String acctTypeTo, String acctIdFrom, String acctTypeFrom, String amount, String curDef, String toAmount, String toAmtCurrency, int transType) throws BSException
	{
		Transfer transfer = new Transfer();

        // set amount
        transfer.setAmount(new Currency(amount, curDef, transfer.getLocale()));

        // set to amount for multi-currency transfers
        if (validNonZeroAmount(toAmount))
            transfer.setToAmount(new Currency(toAmount, toAmtCurrency, transfer.getLocale()));

        // set memo
        transfer.setMemo("From the BPW Intra-Bank Transaction Handler");

        // Create the account objects for the backend
        Accounts accounts = new Accounts();

        Account from = accounts.create(bankId, acctIdFrom, BeansConverter.getBPWAccountType(acctTypeFrom));
        from.setCurrencyCode(curDef);
        acctIdTo = acctIdTo == null ? "" : acctIdTo;
        Account to = accounts.create(bankId, acctIdTo, BeansConverter.getBPWAccountType(acctTypeTo));
        if (toAmtCurrency == null || toAmtCurrency.length() == 0)
        	to.setCurrencyCode(curDef);  // single-currency transfer
        else
        	to.setCurrencyCode(toAmtCurrency);  // multi-currency transfer

        // Complete the transfer object by giving it the accounts to use
        transfer.setFromAccount(from);
        transfer.setToAccount(to);


        addTransfer(transfer, transType);
	}

    private boolean validNonZeroAmount(String amount)
    {
        if (amount == null || amount.length() == 0)
            return false;

        try
        {
            BigDecimal amt = new BigDecimal(amount);
            return amt.compareTo(new BigDecimal(0)) > 0;
        }
        catch (NumberFormatException ignored)
        {
            return false;
        }
    }


    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#addTransfers(com.ffusion.beans.banking.Transfer[], int[])
	 */
    @Override
	public Transfer[] addTransfers( Transfer[] transfers, int transType[] ) throws BSException
    {
	return com.ffusion.banksim.BankSim.addTransfers( transfers, transType );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#getTransactions(com.ffusion.beans.accounts.Account, java.util.Calendar, java.util.Calendar)
	 */
    @Override
	public Enumeration getTransactions( Account account,
    					       Calendar startDate,
					       Calendar endDate ) throws BSException
    {
	return com.ffusion.banksim.BankSim.getTransactions( account, startDate, endDate );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#openPagedTransactions(com.ffusion.beans.accounts.Account, java.util.Calendar, java.util.Calendar)
	 */
    @Override
	public void openPagedTransactions( Account account,
					      Calendar startDate,
					      Calendar endDate ) throws BSException
    {
	com.ffusion.banksim.BankSim.openPagedTransactions( account, startDate, endDate );
    }
    
    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#openPagedTransactions(com.ffusion.beans.accounts.Account, com.ffusion.util.beans.PagingContext, java.util.HashMap)
	 */
     @Override
	public void openPagedTransactions( Account account,
 					      PagingContext context,
 					      HashMap extra ) throws BSException {
         com.ffusion.banksim.BankSim.openPagedTransactions( account, context, extra );
     }
     
     /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#getPagedTransactions(com.ffusion.beans.accounts.Account, com.ffusion.util.beans.PagingContext, java.util.HashMap)
	 */
     @Override
	public Transactions getPagedTransactions(Account account,PagingContext context, HashMap extra) throws BSException {
    	 return com.ffusion.banksim.BankSim.getPagedTransactions( account, context, extra );
 	}
     
     /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#getNextTransactions(com.ffusion.beans.accounts.Account, com.ffusion.util.beans.PagingContext, java.util.HashMap)
	 */
     @Override
	public Transactions getNextTransactions(Account account,PagingContext context, HashMap extra) throws BSException {
    	 return com.ffusion.banksim.BankSim.getNextTransactions( account, context, extra );
 	}
     
     /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#getPreviousTransactions(com.ffusion.beans.accounts.Account, com.ffusion.util.beans.PagingContext, java.util.HashMap)
	 */
     @Override
	public Transactions getPreviousTransactions(Account account,PagingContext context, HashMap extra) throws BSException {
    	 return com.ffusion.banksim.BankSim.getPreviousTransactions( account, context, extra );
 	} 
     
     

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#closePagedTransactions(com.ffusion.beans.accounts.Account)
	 */
    @Override
	public void closePagedTransactions( Account account ) throws BSException
    {
	com.ffusion.banksim.BankSim.closePagedTransactions( account );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#getNumberOfTransactions(com.ffusion.beans.accounts.Account)
	 */
    @Override
	public int getNumberOfTransactions( Account account ) throws BSException
    {
	return com.ffusion.banksim.BankSim.getNumberOfTransactions( account );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#getNextPage(com.ffusion.beans.accounts.Account, int)
	 */
    @Override
	public Enumeration getNextPage( Account account, int howMany ) throws BSException
    {
	return com.ffusion.banksim.BankSim.getNextPage( account, howMany );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#getNextPage(com.ffusion.beans.accounts.Account, int, int)
	 */
    @Override
	public Enumeration getNextPage( Account account, int howMany, int nextIndex ) throws BSException
    {
	return com.ffusion.banksim.BankSim.getNextPage( account, howMany, nextIndex );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#getPrevPage(com.ffusion.beans.accounts.Account, int)
	 */
    @Override
	public Enumeration getPrevPage( Account account, int howMany ) throws BSException
    {
	return com.ffusion.banksim.BankSim.getPrevPage( account, howMany );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#getPrevPage(com.ffusion.beans.accounts.Account, int, int)
	 */
    @Override
	public Enumeration getPrevPage( Account account, int howMany, int prevIndex ) throws BSException
    {
	return com.ffusion.banksim.BankSim.getPrevPage( account, howMany, prevIndex );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#addMailMessage(com.ffusion.beans.user.User, com.ffusion.beans.messages.Message)
	 */
    @Override
	public final void addMailMessage( User 	customer,
    				   	     Message 		message ) throws BSException
    {
	com.ffusion.banksim.BankSim.addMailMessage( customer, message );
    }

    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#getMailMessages(com.ffusion.beans.user.User)
	 */
    @Override
	public final Enumeration getMailMessages( User customer ) throws BSException
    {
	return com.ffusion.banksim.BankSim.getMailMessages( customer );
    }
    
    /* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#getSpecificPage(com.ffusion.beans.accounts.Account, com.ffusion.util.beans.PagingContext, java.util.HashMap)
	 */
	@Override
	public final Transactions getSpecificPage(Account account, PagingContext pagingContext,
			HashMap extra) throws BSException {
		
		return com.ffusion.banksim.BankSim.getSpecificPage(account, pagingContext, extra);
	}
	
	/* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#getAccountTransactions(com.ffusion.beans.accounts.Account, com.ffusion.util.beans.PagingContext, java.util.HashMap)
	 */
	@Override
	public Transactions getAccountTransactions(Account account, PagingContext pagingContext, HashMap extra)
			throws BSException {
		return com.ffusion.banksim.BankSim.getAccountTransactions(account, pagingContext, extra);
	}
	
	/* (non-Javadoc)
	 * @see com.ffusion.banksim.proxy.BankSimAPI#getTransactionById(com.ffusion.beans.accounts.Account, java.lang.String, java.util.HashMap)
	 */
	@Override
	public final Transaction getTransactionById(Account account, String transId, HashMap extra)  throws BSException {
		return com.ffusion.banksim.BankSim.getTransactionById(account, transId, extra);
	}
	
	/**
	 * Update configuration if ConfigAdmin service sends update
	 * 
	 * @param properties
	 */
	public void updateConfiguration(Map<String,?> properties){
		
		if(properties == null || properties.isEmpty())
		{
		  logger.warn("##OCB_CONFIG## --- EMPTY ConfigurationUpdate Received for Banksim --- ##OCB_CONFIG##");
		}
		else
		{
			try { updateCount++;
			logger.warn("CONFIGURATION UPDATE#"+updateCount+" :Banksim");
			}catch(Exception e)
			{
				updateCount=0;
			}
			
		   logger.info("ConfigurationUpdate Received: {}" , properties);
		}
		
		if((String) properties.get("banksim.db.type") != null){
			this.setDbType((String) properties.get("banksim.db.type"));
		}
		
		if((String) properties.get("banksim.db.maxconnections")!= null){
			this.setDbMaxConnections((String) properties.get("banksim.db.maxconnections"));
		}
		
		logger.warn("ClusterServiceImpl Configuration updated successfully - {}", toString());
	}


}
