//
// DBAccount.java
//
// Copyright (c) 2002 Financial Fusion, Inc.
// All Rights Reserved
//

package com.ffusion.banksim.db;

import com.ffusion.banksim.interfaces.BSException;
import com.ffusion.beans.Balance;
import com.ffusion.beans.Bank;
import com.ffusion.beans.DateTime;
import com.ffusion.beans.accounts.Account;
import com.ffusion.beans.accounts.AccountFilters;
import com.ffusion.beans.accounts.AccountTypes;
import com.ffusion.beans.accounts.AccountGroups;
import com.ffusion.beans.accounts.Accounts;
import com.ffusion.beans.common.Currency;
import com.ffusion.beans.user.User;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Vector;

public class DBAccount {
	///////////////////////////////////////////////////////////////////////
	// SQL Statements `
	///////////////////////////////////////////////////////////////////////
	private static final String DOES_ACCOUNT_EXIST =
			"select AccountID from BS_Account where AccountNumber=?";

	private static final String DOES_ACCOUNT_NUMBER_EXIST =
			"select AccountNumber from BS_Account where AccountNumber=?";

	private static final String GET_BALANCE =
			"select Balance from BS_Account where AccountNumber=?";

	private static final String DOES_FI_EXIST =
			"select FIID from BS_FI where FIID=?";

	private static final String INSERT_ACCOUNT =
			"insert into BS_Account( AccountID, AccountName, AccountTypeID, CurrencyCode, " +
			"FIID, AccountNumber, Balance, Status, Type, LastUpdated, Description, " +
			"PrimaryAccount, CoreAccount, PersonalAccount, PositivePay, RoutingNum, BICNum , "+
			"CountryCode, InternalAccountID ) " +	// added for core banking system
			"values( ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

	private static final String INSERT_CUSTOMER_TO_ACCOUNT =
			"insert into BS_CustomerAccount( CustomerID, AccountID ) " +
			"values( ?,? )";

	private static final String GET_ACCOUNTS_INFO =
			"Select BS_Account.AccountID, BS_Account.AccountName, BS_Account.AccountTypeID, " +
			"BS_Account.CurrencyCode, BS_Account.FIID, BS_Account.AccountNumber, BS_Account.Balance, " +
			"BS_Account.Status, BS_Account.Type, BS_Account.LastUpdated, BS_Account.Description, " +
			"BS_Account.PrimaryAccount, BS_Account.CoreAccount, BS_Account.PersonalAccount, " +
			"BS_Account.PositivePay, BS_Account.RoutingNum, BS_Account.BICNum, BS_FI.Name, " +
			"BS_Account.StmtFlag, " + //added for Online Statement
			"BS_Account.CountryCode, BS_Account.InternalAccountID " + // added for core banking system
			"FROM BS_Account, BS_FI where BS_FI.FIID = BS_Account.FIID AND " +
			"AccountID in " +
			"(Select AccountID from BS_CustomerAccount where CustomerID in " +
			"(Select CustomerID from BS_Customer where UserID = ? and Password = ?))";

	private static final String GET_ACCOUNT_INFO =
			"Select BS_Account.AccountName, BS_Account.AccountTypeID,  " +
			"BS_Account.CurrencyCode, BS_Account.FIID, BS_Account.AccountID, " +
			"BS_Account.Balance, BS_Account.Status, BS_Account.Type, " +
			"BS_Account.LastUpdated, BS_Account.Description, BS_Account.PrimaryAccount, " +
			"BS_Account.CoreAccount, BS_Account.PersonalAccount, BS_Account.PositivePay, " +
			"BS_Account.RoutingNum, BS_Account.BICNum, BS_FI.Name, " +
            "BS_Account.StmtFlag, " + //added for Online Statement
            "BS_Account.CountryCode, BS_Account.InternalAccountID " + // added for core banking system
            "from BS_Account, BS_FI where BS_FI.FIID = BS_Account.FIID " +
			"and AccountNumber = ? ";

	private static final String UPDATE_BALANCE =
			"Update BS_Account set LastUpdated = ?, Balance = ? where AccountID = ?";

	private static final String DELETE_ACCOUNT =
			"Delete from BS_Account where AccountID = ?";

	private static final String DELETE_DEST_ACCOUNT =
			"Update BS_Transactions set DestAccountID = NULL where DestAccountID = ?";

	private static final String UPDATE_ACCOUNT =
			"Update BS_Account set AccountNumber = ?, AccountName = ?, AccountTypeID = ?, " +
			"CurrencyCode = ?, Status = ?, LastUpdated = ?, Balance = ?, Description = ? " +
			"PrimaryAccount = ?, CoreAccount = ?, PersonalAccount = ?, PositivePay = ? " +
			"RoutingNum = ?, BICNum = ? " +
			"where AccountID = ?";

    //Key name for Statement Flag variable related to Online Statement
    public static final String stmtFlagKey = "STMT_FLAG";

	/**
	 * doesAccountExist - check to see if the account exists
	 * @param account the account to check
	 * @param conn a DBConnection object that used to connect to the BankSim database
	 * @return boolean indicate if the account exists
	 */
	static final boolean doesAccountExist(Account account, DBConnection conn) {
		DBResultSet rset = null;

		try {
			rset = conn.prepareQuery(DOES_ACCOUNT_EXIST);
			Object[] params_DAE = {account.getNumber()};
			rset.open(params_DAE);
			while (rset.getNextRow()) {
				rset.close();
				return true;
			}
			rset.close();
			return false;
		} catch (Exception e) {
			// I don't expect anything truly bad to happen on a simple select...
			try {
				rset.close();
			} catch (Exception ex) {
			}
			return false;
		}
	}

	/**
	 * updateBalance - update the balance of the account in the database
	 * @param account The account whose balance is being updated
	 * @param newBalance a BigDecimal containing the new balance
	 * @param conn a DBConnection object that used to connect to the BankSim database
	 * @exception SQLException
	 */
	public static final void updateBalance(Account account,
										   BigDecimal newBalance,
										   DBConnection conn) throws SQLException {
		// Store the current transactional state and make sure we've
		// turned off autocommit
		boolean isAutoCommit = conn.isAutoCommit();
		if (isAutoCommit) conn.setAutoCommit(false);

		Object[] parms = {
			new Long(System.currentTimeMillis()),
			newBalance.toString(),
			account.getID(),
		};

		conn.executeUpdate(UPDATE_BALANCE, parms);
	}

	/**
	 * getCurrentBalance - get the balance of the account from the database
	 * @param account The account whose balance is to be retrieved
	 * @param conn a DBConnection object that used to connect to the BankSim database
	 * @return BigDecimal - the balance of the account
	 */
	public static final BigDecimal getCurrentBalance(Account account, DBConnection conn) {
		DBResultSet rset = null;

		try {
			rset = conn.prepareQuery(GET_BALANCE);
			Object[] params = {account.getNumber()};
			rset.open(params);
			rset.getNextRow();

			BigDecimal balance = new BigDecimal(rset.getColumnString(1));

			rset.close();

			return balance;
		} catch (Exception e) {
			// I don't expect anything truly bad to happen on a simple select...
			try {
				rset.close();
			} catch (Exception ex) {
			}
			return new BigDecimal("0");
		}
	}

	/**
	 * addAccount - add the accounts information into the database by using
	 * the information contained in the User object and Account object
	 * @param customer a populated User object
	 * @param account a populated Account object
	 * @param conn a DBConnection object that used to connect to the BankSim database
	 * @param autoCreated is this account being auto-created as the destination account in a transfer?
	 * @exception BSException that states there is an account with the same id in the database or wraps around the SQL Exception
	 */
	public static final void addAccount(User customer,
										Account account,
										DBConnection conn,
										boolean autoCreated) throws BSException {
		boolean isAutoCommit = false;

		try {
			// Store the current transactional state and make sure we've
			// turned off autocommit
			isAutoCommit = conn.isAutoCommit();
			if (isAutoCommit) conn.setAutoCommit(false);

			// Verify that there isn't already an account with the same id in the table.
			// It is preferable to throw a specific exception now than a general
			// "Primary Key violation" exception later.
			if (DBAccount.doesAccountExist(account, conn)) {
				throw new BSException(BSException.BSE_ACCOUNT_EXISTS,
						MessageText.getMessage(IBSErrConstants.ERR_ACCOUNT_EXISTS));
			}

			// Oracle does not store null or blank string in table. Assign default value when bank id and name is blank.
			if(account.getBankID() == null || account.getBankID().length() == 0) {
				account.setBankID("660110110");
			}
			
			// Check that the Bank exists in the database
			DBResultSet rset = conn.prepareQuery(DOES_FI_EXIST);
			Object[] params_DFIE = {account.getBankID()};
			rset.open(params_DFIE);
			boolean foundFI = false;
			while (rset.getNextRow()) {
				foundFI = true;

			}
			rset.close();

			if (!foundFI && !autoCreated) {
				throw new BSException(BSException.BSE_FI_NOT_EXISTS,
						MessageText.getMessage(IBSErrConstants.ERR_ACC_FI_NOT_EXIST));
			} else if (!foundFI) {
				// This account is being created as the destination account in a transfer
				// The bank mention does not exist.  Try and create it.
				Bank destBank = new Bank();
				destBank.setID(account.getBankID());
				destBank.setName(account.getBankID());

				DBFinancialInstitution.addBank(destBank, conn);
			}

			// Build a float object for the account balance.
			BigDecimal amount;
			if (account.getCurrentBalance() == null
					|| account.getCurrentBalance().getAmount() == null) {
				amount = new BigDecimal("0.00");
			} else {
				// TODO: Find a currency-friendly way to set the scale
				amount = account.getCurrentBalance().getAmountValue().getAmountValue();
				amount = amount.setScale(2, BigDecimal.ROUND_HALF_EVEN);
			}

			String number;
/*			if (account.getNumber() != null) {
				number = account.getNumber();
			} else {
				Number = account.getID().substring(0, account.getID().lastIndexOf("-"));
			}*/
			if (account.getNumber() != null) {
				number = account.getNumber();
			} else {
				int index = account.getID().lastIndexOf("-");
				if( index != -1 ){
					number = account.getID().substring( 0, index );
				} else {
					number = account.getID();
				}
			}
            if (account.getBicAccount() == null)        // not allowed to be null
                account.setBicAccount("1234");
            if (account.getRoutingNum() == null)        // not allowed to be null
                account.setRoutingNum(account.getBankID());

			Object[] parms = {
				account.getID(),
				account.getNickName(), // account name
				new Integer(account.getTypeValue()),
				account.getCurrencyCode(),
				account.getBankID(),
				number,
				amount.toString(),
				new Integer(account.getStatus()),
				// parse the account id and get the account type
				//account.getID().substring(account.getID().lastIndexOf("-") + 1),
				// get the account type
				account.getType(),
				new Long(System.currentTimeMillis()),
				"", // TODO: implement Description
				account.getPrimaryAccount(),
				account.getCoreAccount(),
				account.getPersonalAccount(),
				account.getPositivePay(),
				account.getRoutingNum(),
				account.getBicAccount(),
				account.getCountryCode(),
				account.getInternalAccountId()
			};

			conn.executeUpdate(INSERT_ACCOUNT, parms);

			Object[] parms2 = {
				customer.getId(),
				account.getID(),
			};

			conn.executeUpdate(INSERT_CUSTOMER_TO_ACCOUNT, parms2);

			conn.commit();

		} catch (SQLException sqle) {
			// Roll back the transaction then wrap and rethrow sqle
			try {
				conn.rollback();
			} catch (SQLException sqle2) {
				// Just ignore this one and wrap and rethrow the previous
				// since it's probably related to what happened before
			}
			throw new BSException(BSException.BSE_DB_EXCEPTION,
					DBSqlUtils.getRealSQLException(sqle));
		} finally {
			// Must always turn on autocommit if we turned it off
			try {
				if (isAutoCommit) conn.setAutoCommit(true);
			} catch (SQLException sqle) {
				throw new BSException(BSException.BSE_DB_EXCEPTION,
						DBSqlUtils.getRealSQLException(sqle));
			}
		}
	}

	/**
	 * getAccounts - get the accounts information from the database by using
	 * the information contained in the User object
	 * note: this method assumes signOn has been called, so there is no need
	 * to check if the customer exists
	 * @param customer a populated User object
	 * @param conn a DBConnection object that used to connect to the BankSim database
	 * @return Enumeration of Account objects
	 * @exception BSException that wraps around the SQL Exception
	 */
	public static final Enumeration getAccounts(User customer, DBConnection conn) throws BSException {
		Enumeration e = null;
		try {
			DBResultSet rset = conn.prepareQuery(GET_ACCOUNTS_INFO);
			Object[] params_GAI = {customer.getUserName(), customer.getPassword()};
			rset.open(params_GAI);
			// create new vector to temporarily store the accounts
			Vector v = new Vector();
			// account filtered list
			Accounts accounts = new Accounts();
			Account account = null;
			Balance balance = null;
			Currency currency = null;
			DateTime date = null;
			while (rset.getNextRow()) {
				account = accounts.create(rset.getColumnString(5), rset.getColumnString(1), rset.getColumnString(6),rset.getColumnInt(3));
				account.setAccountGroup(mapAccountType(rset.getColumnInt(3)));
				account.setCurrencyCode(rset.getColumnString(4));
				account.setNickName(rset.getColumnString(2));
				account.setStatus(rset.getColumnInt(8));
				// build the balance object and currency object
				balance = new Balance();
				currency = new Currency();
				currency.setAmount(new BigDecimal(rset.getColumnString(7)));
				currency.setCurrencyCode( account.getCurrencyCode() );
				balance.setAmount(currency);
				// build the datetime object
				date = new DateTime();
				date.setDate(rset.getColumnString(10));
				balance.setDate(date);
				account.setCurrentBalance(balance);
				account.setAvailableBalance(balance);

				// TODO: read in description (col 11 )
				account.setPrimaryAccount( rset.getColumnString( 12 ) );
				account.setCoreAccount( rset.getColumnString( 13 ) );
				account.setPersonalAccount( rset.getColumnString( 14 ) );
				account.setPositivePay( rset.getColumnString( 15 ) );
				account.setRoutingNum( rset.getColumnString( 16 ) );
				account.setBicAccount( rset.getColumnString( 17 ) );

				account.setBankName( rset.getColumnString( 18 ) );

                //Added Statement Flag setting for Online Statement
                account.set(stmtFlagKey, rset.getColumnString( 19 ));
                
                //Set Account Country code for Core Banking system
                account.setCountryCode(rset.getColumnString( 20 ) );
                
                // Set the Internal account ID required by core banking system for account history
                account.setInternalAccountId(rset.getColumnString( 21 ));

				// Set filters for this account.
				DBAccount.setFilters(account);

				v.add(account);
			}
			rset.close();
			e = v.elements();
		} catch (SQLException sqle) {
			throw new BSException(BSException.BSE_DB_EXCEPTION,
					DBSqlUtils.getRealSQLException(sqle));
		}
		return e;
	}

	/**
	 * getAccount - populates an arbitrary account object
	 * @param account an Account object containing the number of the account to get
	 * @param conn a DBConnection object that used to connect to the BankSim database
	 * @return the fully-populated Account object corresponding to the input parameter
	 * @exception BSException that states the banksim is not initialized or getAccount failed
	 */
	public static final Account getAccount(Account account, DBConnection conn) throws BSException {
		Enumeration e = null;
		try {
			// Verify that the account exists.
			if (!DBAccount.doesAccountExist(account, conn)) {
				throw new BSException(BSException.BSE_ACCOUNT_NOT_EXISTS,
						MessageText.getMessage(IBSErrConstants.ERR_ACCOUNT_NOT_EXISTS));
			}

			DBResultSet rset = conn.prepareQuery(GET_ACCOUNT_INFO);
			Object[] params_GAI = {account.getNumber()};
			rset.open(params_GAI);

			Balance balance = null;
			Currency currency = null;
			DateTime date = null;

			// There's only going to be one row.
			rset.getNextRow();
			account.setID(rset.getColumnString(5));
			account.setType(rset.getColumnInt(2));
			account.setCurrencyCode(rset.getColumnString(3));
			account.setBankID(rset.getColumnString(4));
			account.setNickName(rset.getColumnString(1));
			account.setStatus(rset.getColumnInt(7));
			// build the balance object and currency object
			balance = new Balance();
			currency = new Currency();
			currency.setAmount(new BigDecimal(rset.getColumnString(6)));
			currency.setCurrencyCode( account.getCurrencyCode() );
			balance.setAmount(currency);
			// build the datetime object
			date = new DateTime();
			date.setDate(rset.getColumnString(9));
			balance.setDate(date);
			account.setCurrentBalance(balance);
			account.setAvailableBalance(balance);

			// TODO: implement Description (col 10)
			account.setPrimaryAccount( rset.getColumnString( 11 ) );
			account.setCoreAccount( rset.getColumnString( 12 ) );
			account.setPersonalAccount( rset.getColumnString( 13 ) );
			account.setPositivePay( rset.getColumnString( 14 ) );
			account.setRoutingNum( rset.getColumnString( 15 ) );
			account.setBicAccount( rset.getColumnString( 16 ) );

			account.setBankName( rset.getColumnString( 17 ) );

			//Added Statement Flag setting for Online Statement
			account.set(stmtFlagKey, rset.getColumnString( 18 ));
			
			//Set Account Country code for Core Banking system
			account.setCountryCode(rset.getColumnString( 19 ) );
			
			// Set the Internal account ID required by core banking system for account history
			account.setInternalAccountId(rset.getColumnString( 20 ));

			// Set filters for this account.
			DBAccount.setFilters(account);

			rset.close();
		} catch (SQLException sqle) {
			throw new BSException(BSException.BSE_DB_EXCEPTION,
					DBSqlUtils.getRealSQLException(sqle));
		}
		return account;
	}

	private final static void setFilters(Account account) {
		int accountType = account.getTypeValue();

		// TODO: Determine correct behaviour.  AFAIK, all accounts can support transactions
		// (otherwise -- how does one get money out of that account?) and most should
		// support transfer from and to.  We may want to limit transfer from at some later
		// point to enhance the modelling of the Banksim. (ie, can't transfer from a
		// credit card account.)

		account.setFilterable(AccountFilters.TRANSACTIONS_FILTER);

		if (accountType == AccountTypes.TYPE_CHECKING)
		{
			account.setFilterable(AccountFilters.TRANSFER_TO_FILTER);
			account.setFilterable(AccountFilters.TRANSFER_FROM_FILTER);
			account.setFilterable(AccountFilters.BILL_PAY_FILTER);
		} else
		if (accountType == AccountTypes.TYPE_SAVINGS)
		{
			account.setFilterable(AccountFilters.TRANSFER_TO_FILTER);
			account.setFilterable(AccountFilters.TRANSFER_FROM_FILTER);
		} else
		if (accountType == AccountTypes.TYPE_CREDIT)
		{
			//Modified by TCG for EAServer-ASE porting
			//Uncommented by TCG for EAServer-ASE porting
			account.setFilterable(AccountFilters.TRANSFER_TO_FILTER);
			account.setFilterable(AccountFilters.TRANSFER_FROM_FILTER);
			//End of Modification TCG for EAServer-ASE porting
		} else
		{
			account.setFilterable(AccountFilters.TRANSFER_TO_FILTER);
			account.setFilterable(AccountFilters.TRANSFER_FROM_FILTER);
			account.setFilterable(AccountFilters.BILL_PAY_FILTER);
		}
	}

	/**
	 * deleteAccount - delete the account information from the database
	 * @param account a populated Account object that contains the account information
	 * @param conn DBConnection object that used to connect to the BankSim database
	 * @exception BSException that states the account does not exist or wraps around the SQL Exception thrown by other methods
	 */
	public final static void deleteAccount(Account account, DBConnection conn) throws BSException {
		// check to see if the Account exists in the database
		if (!doesAccountExist(account, conn)) {
			throw new BSException(BSException.BSE_ACCOUNT_NOT_EXISTS,
					MessageText.getMessage(IBSErrConstants.ERR_ACCOUNT_NOT_EXISTS));
		}
		boolean isAutoCommit = false;
		try {

			// Store the current transactional state and make sure we've
			// turned off autocommit
			isAutoCommit = conn.isAutoCommit();
			if (isAutoCommit) conn.setAutoCommit(false);

			Object[] params_DA = {account.getID()};

			// Set the destination account id in BS_Transactions to null
			conn.executeUpdate(DELETE_DEST_ACCOUNT, params_DA);

			// Remove the account in BS_Account
			conn.executeUpdate(DELETE_ACCOUNT, params_DA);

			conn.commit();

		} catch (SQLException sqle) {
			// Roll back the transaction then wrap and rethrow sqle
			try {
				conn.rollback();
			} catch (SQLException sqle2) {
				// Just ignore this one and wrap and rethrow the previous
				// since it's probably related to what happened before
			}
			throw new BSException(BSException.BSE_DB_EXCEPTION,
					DBSqlUtils.getRealSQLException(sqle));
		} finally {
			// Must always turn on autocommit if we turned it off
			try {
				if (isAutoCommit) conn.setAutoCommit(true);
			} catch (SQLException sqle) {
				throw new BSException(BSException.BSE_DB_EXCEPTION,
						DBSqlUtils.getRealSQLException(sqle));
			}
		}
	}

	/**
	 * updateAccount - update the account in the database with the provided information
	 * @param account populated Account object that contains the latest information
	 * @param conn DBConnection object that used to connect to the BankSim database
	 * @exception BSException that states the account does not exist in the database or wraps the SQLException throw by other methods
	 */
	public static final void updateAccount(Account account, DBConnection conn) throws BSException {
		boolean isAutoCommit = false;

		try {
			// Store the current transactional state and make sure we've
			// turned off autocommit
			isAutoCommit = conn.isAutoCommit();
			if (isAutoCommit) conn.setAutoCommit(false);

			// Verify that the account id exists in the database
			if (!DBAccount.doesAccountExist(account, conn)) {
				throw new BSException(BSException.BSE_ACCOUNT_NOT_EXISTS,
						MessageText.getMessage(IBSErrConstants.ERR_ACCOUNT_NOT_EXISTS));
			}

			// TODO: Find a better way of determining the correct scale. (one that is
			// currency sensitive)
			BigDecimal amount = account.getCurrentBalance().getAmountValue().getAmountValue();
			amount = amount.setScale(2, BigDecimal.ROUND_HALF_EVEN);

			Object[] params = {
				account.getNumber(),
				account.getNickName(), // account name
				new Integer(account.getTypeValue()),
				account.getCurrencyCode(),
				new Integer(account.getStatus()),
				// Put the current date into the last updated field
				new Long(System.currentTimeMillis()),
				amount.toString(),
				"", // TODO: implement Description
				account.getPrimaryAccount(),
				account.getCoreAccount(),
				account.getPersonalAccount(),
				account.getPositivePay(),
				account.getRoutingNum(),
				account.getBicAccount(),
				account.getID(),
			};

			conn.executeUpdate(UPDATE_ACCOUNT, params);

			conn.commit();

		} catch (SQLException sqle) {
			// Roll back the transaction then wrap and rethrow sqle
			try {
				conn.rollback();
			} catch (SQLException sqle2) {
				// Just ignore this one and wrap and rethrow the previous
				// since it's probably related to what happened before
			}
			throw new BSException(BSException.BSE_DB_EXCEPTION,
					DBSqlUtils.getRealSQLException(sqle));
		} finally {
			// Must always turn on autocommit if we turned it off
			try {
				if (isAutoCommit) conn.setAutoCommit(true);
			} catch (SQLException sqle) {
				throw new BSException(BSException.BSE_DB_EXCEPTION,
						DBSqlUtils.getRealSQLException(sqle));
			}
		}
	}

    /**
     * Map the account type to an account group type.
     * WARNING: THIS IS AN EXACT COPY OF FUNCTION FROM AccountService.java
     * Any modification must be reflected in both places.
     * @param accType type of account
     */
    private static int mapAccountType( int accType )
    {
        // default
        return Account.getAccountGroupFromType( accType );
    }

}
