//
// DBTransfer.java
//
// Copyright (c) 2002 Financial Fusion, Inc.
// All Rights Reserved
//

package com.ffusion.banksim.db;

import com.ffusion.banksim.interfaces.BSDBParams;
import com.ffusion.banksim.interfaces.BSException;
import com.ffusion.beans.Balance;
import com.ffusion.beans.DateTime;
import com.ffusion.beans.banking.TransactionTypes;
import com.ffusion.beans.banking.Transfer;
import com.ffusion.beans.banking.TransferStatus;
import com.ffusion.beans.banking.Transaction;
import com.ffusion.beans.common.Currency;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.util.db.ConnectionDefines;
import com.ffusion.util.db.DBUtil;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Date;

public class DBTransfer {

	private static final String VALIDATE_TO_ACCOUNT= "validateToAccount";
	
	//indicate the size of the memo field in BS_Transactions
	private static final int MEMO_FIELD = 40;

	///////////////////////////////////////////////////////////////////////
	// SQL Statements
	///////////////////////////////////////////////////////////////////////
	private static final String DOES_ACCOUNT_EXIST =
			"select AccountID from BS_Account where AccountID=?";

	//Begin Modify by TCG Team for MS SQL Support
    private static final String ADD_FIRST_TRANSACTION =
    	"insert into BS_Transactions( {|TransactionID,|TransactionID,|TransactionID,|||TransactionID,|TransactionID,|TransactionID,} TransactionDate, " +
	"TransactionTypeID, AccountID, FIID, Amount, DestAccountID, " +
	"DestFIID, CurrencyCode, Memo, ChequeNumber, ReferenceNumber, DataClassification, RunningBalance ) " +
	    "values( {|?,|NEXTVAL FOR BS_TransactionIDSequence,|BS_TransactionIDSequence.NEXTVAL,|||NEXTVAL FOR BS_TransactionIDSequence,|nextval('BS_TransactionIDSequence'),|BS_TransactionIDSequence.NEXTVAL,} " +
	    	    "?,?,?,?,?,?,?,?,?,?," +
	            "{|0|NEXTVAL FOR BS_ReferenceNumberSequence|BS_ReferenceNumberSequence.NEXTVAL|0|0|NEXTVAL FOR BS_ReferenceNumberSequence|nextval('BS_ReferenceNumberSequence')|BS_ReferenceNumberSequence.NEXTVAL},?,? )"; //Modify by TCG for ASE Support

    private static final String ADD_SECOND_TRANSACTION =
    	"insert into BS_Transactions( {|TransactionID,|TransactionID,|TransactionID,|||TransactionID,|TransactionID,|TransactionID,} TransactionDate, " +
	"TransactionTypeID, AccountID, FIID, Amount, DestAccountID, " +
	"DestFIID, CurrencyCode, Memo, ChequeNumber, ReferenceNumber, DataClassification, RunningBalance ) " +
	    "values( {|?,|NEXTVAL FOR BS_TransactionIDSequence,|BS_TransactionIDSequence.NEXTVAL,|||NEXTVAL FOR BS_TransactionIDSequence,|nextval('BS_TransactionIDSequence'),|BS_TransactionIDSequence.NEXTVAL,} " +
	    	    "?,?,?,?,?,?,?,?,?,?," +
	            "{|0|PREVVAL FOR BS_ReferenceNumberSequence|BS_ReferenceNumberSequence.CURRVAL|0|0|PREVVAL FOR BS_ReferenceNumberSequence|nextval('BS_ReferenceNumberSequence')|BS_ReferenceNumberSequence.CURRVAL},?,? )"; //Modify by TCG for ASE Support

    //MSSQL support
    private static final String ADD_FIRST_TRANSACTION_MSSQL =
    	"insert into BS_Transactions( TransactionID, TransactionDate, " +
	"TransactionTypeID, AccountID, FIID, Amount, DestAccountID, " +
	"DestFIID, CurrencyCode, Memo, ChequeNumber, ReferenceNumber, DataClassification, RunningBalance ) " +
	    "values( ?,?,?,?,?,?,?,?,?,?,?," +
	            "{|0|NEXTVAL FOR BS_ReferenceNumberSequence|BS_ReferenceNumberSequence.NEXTVAL|0|0|NEXTVAL FOR BS_ReferenceNumberSequence|nextval('BS_ReferenceNumberSequence')},?,? )"; //Modify by TCG for ASE Support

    private static final String ADD_SECOND_TRANSACTION_MSSQL =
    	"insert into BS_Transactions( TransactionID, TransactionDate, " +
	"TransactionTypeID, AccountID, FIID, Amount, DestAccountID, " +
	"DestFIID, CurrencyCode, Memo, ChequeNumber, ReferenceNumber, DataClassification, RunningBalance ) " +
	    "values( ?, ?,?,?,?,?,?,?,?,?,?," +
	            "{|0|PREVVAL FOR BS_ReferenceNumberSequence|BS_ReferenceNumberSequence.CURRVAL|0|0|PREVVAL FOR BS_ReferenceNumberSequence|currval('BS_ReferenceNumberSequence')},?,? )"; //Modify by TCG for ASE Support

    
	private static final String GET_TRANSACTION_ID =
			"select * from BS_Transactions where TransactionDate = ? FETCH FIRST ROW ONLY";

    //Begin Add by TCG Team for MS SQL Support and ASE Support
    //(since here the SQL syntax for MS SQL and ASE are identical, so the MS SQL
    // string is used for ASE)
    private static final String GET_TRANSACTION_ID_MSSQL =
        "select * from BS_Transactions where TransactionDate = ? ";
    //End Add
	private static final String UPDATE_TRANSACTION_NO_SEQUENCE =
			"update BS_Transactions set ReferenceNumber = ? " +
			"where TransactionDate = ?";

	private static final String GET_REFERENCE_NUMBER =
			"{||" +
			"VALUES PREVVAL FOR BS_ReferenceNumberSequence|" +
			"SELECT BS_ReferenceNumberSequence.CURRVAL FROM dual|||VALUES PREVVAL FOR BS_ReferenceNumberSequence|select currval('BS_ReferenceNumberSequence')|SELECT BS_ReferenceNumberSequence.CURRVAL FROM DUMMY}";

	// complete sequence name is BS_TRANSACTIONIDSEQUENCE_SEQ
	// try to keep the name as close as possible to other db platforms
	// sequence name is BS_TransactionIDSequence on DB2 and Oracle
	// all ASE sequence name ends with _SEQ
	private static final String ASE_BS_TRANSACTION_ID_SEQUENCE_PARTIAL_NAME = "BS_TRANSACTIONIDSEQUENCE";
	
	// For MSSQL usage
	private static final String MSSQL_BS_TRANSACTION_ID_SEQUENCE_PARTIAL_NAME = "BS_TRANSACTIONIDSEQUENCE";
	
	/**
	 * addTransfer - add the transfer into the database by using the information
	 * contained in the Transfer object
	 * @param transfer a populated Transfer object that contains the transfer information
	 * @param transType a transaction type constant (from com.ffusion.beans.banking.TransactionTypes) used for the transaction that is created when the transfer occurs
	 * @param conn a DBConnection object that used to connect to the BankSim database
	 * @return a populated Transfer object with the transaction reference number filled in.
	 * @exception BSException that states the account doesn't exist or wraps the SQL Exception thrown by other methods
	 */
	public static final Transfer addTransfer(Transfer transfer,
											 int transType,
											 DBConnection conn) throws BSException {
		boolean isAutoCommit = false;

		try {
			boolean allowToAccountValidations = transfer.get(VALIDATE_TO_ACCOUNT)== null || (transfer.get(VALIDATE_TO_ACCOUNT)!= null && !transfer.get(VALIDATE_TO_ACCOUNT).equals("false"));
			// Store the current transactional state and make sure we've
			// turned off autocommit
			isAutoCommit = conn.isAutoCommit();
			if (isAutoCommit) conn.setAutoCommit(false);
			
			simulateError(transfer);

			// Verify that the source account exists.  If it doesn't, then how can we transfer
			// money out of it?
			if(transfer.getPaymentSubType() != null && DBConsts.PMT_SUB_TYPE_WALLET_REDEEM_PAYMENT.equalsIgnoreCase(transfer.getPaymentSubType())) {
				
				// Source Account for the Redeem would be the Bank Account
				
				//TODO: To be Provided by Custom Implementation
				
			} else if (!DBAccount.doesAccountExist(transfer.getFromAccount(), conn)) {
				throw new BSException(BSException.BSE_ACCOUNT_NOT_EXISTS,
						MessageText.getMessage(IBSErrConstants.ERR_FROM_ACC_NOT_EXIST));
			}

			// Determine if the destination account exists.  Create it if it does not
			// exist.
			if (allowToAccountValidations && !DBAccount.doesAccountExist(transfer.getToAccount(), conn)) {
				DBAccount.addAccount(DBClient.signOn(DBCustomer.DEFAULT_CUSTOMER_USERID,
						DBCustomer.DEFAULT_CUSTOMER_PASSWORD,
						conn),
						transfer.getToAccount(),
						conn,
						true);
			} else {
				if (transfer.getFromAccount().getID().equals(transfer.getToAccount().getID())) {
					// The source and destination accounts are the same!
					throw new BSException(BSException.BSE_ACCOUNTS_SAME,
							MessageText.getMessage(IBSErrConstants.ERR_ACCOUNTS_SAME));
				}
			}

            // set amount to subtract from account
            BigDecimal fromAmount = transfer.getAmountValue().getAmountValue();
            fromAmount = fromAmount.setScale(2, BigDecimal.ROUND_HALF_EVEN);

            // set amount to add to account
            BigDecimal toAmount = transfer.getAmountValue().getAmountValue();
            toAmount = toAmount.setScale(2, BigDecimal.ROUND_HALF_EVEN);

            // to amount has different value if multi-currency transfer
            if (validNonZeroAmount(transfer.getToAmountValue()))
            {
                toAmount = transfer.getToAmountValue().getAmountValue();
                toAmount = toAmount.setScale(2, BigDecimal.ROUND_HALF_EVEN);
            }

            // get previous balances
            BigDecimal fromOldBalance = DBAccount.getCurrentBalance(transfer.getFromAccount(), conn);
			BigDecimal toOldBalance = null;
			if(allowToAccountValidations){
			toOldBalance = DBAccount.getCurrentBalance(transfer.getToAccount(), conn);
			}
            // Set the scale to 2. This is done so the string representation of the BigDecimal doesn't go too long.
			// TODO: Determine a way to set the scale correctly for any currency.
            fromOldBalance = fromOldBalance.setScale(2, BigDecimal.ROUND_HALF_EVEN);
            if(allowToAccountValidations){
			toOldBalance = toOldBalance.setScale(2, BigDecimal.ROUND_HALF_EVEN);
            }

			// Check that the source account has sufficient funds.
			if (fromOldBalance.compareTo(fromAmount) < 0) {
				throw new BSException(BSException.BSE_NSF,
						MessageText.getMessage(IBSErrConstants.ERR_FROM_ACC_NSF));
			}

            // Check that the amount being transferred is positive
			if (toAmount.compareTo(new BigDecimal("0")) < 0) {
				throw new BSException(BSException.BSE_AMOUNT_NOT_POSITIVE,
						MessageText.getMessage(IBSErrConstants.ERR_AMOUNT_NOT_POSITIVE));
			}

			Long transactionTime = new Long(System.currentTimeMillis());
			String chequeNum;
			if (transType == TransactionTypes.TYPE_CHECK) {
				// TODO: Will the cheque number be passed in in the reference number field?
				chequeNum = transfer.getReferenceNumber();
			} else {
				chequeNum = null;
			}

			//The memo field in BS_Transactions is the same as size of MEMO_FIELD
			//check to see if the size of the memo is larger than MEMO_FIELD
			//truncate it if it is larger than MEMO_FIELD
			if (transfer.getMemo() != null
					&& transfer.getMemo().length() > MEMO_FIELD) {
				transfer.setMemo(transfer.getMemo().substring(0, MEMO_FIELD));
			}
				
            // for ASE, we get the transaction ID from the sequence table
			// SQL insert statement has placeholder for TransactionID
			if(conn.getParams().getConnectionType() == BSDBParams.CONN_ASE)
			{
				Object[] parms = {
					new Integer(DBUtil.getNextId(	conn.getConnection(),
													ConnectionDefines.DB_SYBASE_ASE,
													ASE_BS_TRANSACTION_ID_SEQUENCE_PARTIAL_NAME	)),
					transactionTime,
					new Integer(transType),
					transfer.getFromAccount().getID(),
					transfer.getFromAccount().getBankID(),
					fromAmount.negate().toString(),
					transfer.getToAccount().getID(),
					transfer.getToAccount().getBankID(),
					transfer.getFromAccount().getCurrencyCode(),
					transfer.getMemo(),
					chequeNum,
					Transaction.PREVIOUS_DAY_TRANSACTION,
					fromOldBalance.subtract(fromAmount)
				};
	
				conn.executeUpdate(ADD_FIRST_TRANSACTION, parms);
			}
			else if(conn.getParams().getConnectionType() == BSDBParams.CONN_MSSQL) //Add SQL Server support
			{
				Object[] parms = {
						new Integer(DBUtil.getNextId(	conn.getConnection(),
														ConnectionDefines.DB_MS_SQLSERVER,
														MSSQL_BS_TRANSACTION_ID_SEQUENCE_PARTIAL_NAME	)),
						transactionTime,
						new Integer(transType),
						transfer.getFromAccount().getID(),
						transfer.getFromAccount().getBankID(),
						fromAmount.negate().toString(),
						transfer.getToAccount().getID(),
						transfer.getToAccount().getBankID(),
						transfer.getFromAccount().getCurrencyCode(),
						transfer.getMemo(),
						chequeNum,
						Transaction.PREVIOUS_DAY_TRANSACTION,
						fromOldBalance.subtract(fromAmount)
					};
		
					conn.executeUpdate(ADD_FIRST_TRANSACTION_MSSQL, parms);
				
			}else {
				// set blank string if bank id is null for the oracle database
				if(transfer.getFromAccount().getBankID() == null) {
					transfer.getFromAccount().setBankID("");
				}
				
				if(transfer.getToAccount().getBankID() == null) {
					transfer.getToAccount().setBankID("");
				}
				Object[] parms = {
						transactionTime,
						new Integer(transType),
						transfer.getFromAccount().getID(),
						transfer.getFromAccount().getBankID(),
						fromAmount.negate().toString(),
						transfer.getToAccount().getID(),
						transfer.getToAccount().getBankID(),
						transfer.getFromAccount().getCurrencyCode(),
						transfer.getMemo(),
						chequeNum,
						Transaction.PREVIOUS_DAY_TRANSACTION,
						fromOldBalance.subtract(fromAmount)	
				};
	
				conn.executeUpdate(ADD_FIRST_TRANSACTION, parms);
			}
			
			// update balances
            DBAccount.updateBalance(transfer.getFromAccount(), fromOldBalance.subtract(fromAmount), conn);
			
            if(allowToAccountValidations){	
            // *** Now add the opposite transfer ***

            // Check for the transaction type
			if (transType == TransactionTypes.TYPE_ATM_CREDIT) {
				transType = TransactionTypes.TYPE_ATM_DEBIT;
			}
			if (transType == TransactionTypes.TYPE_POS_CREDIT) {
				transType = TransactionTypes.TYPE_POSDEBIT;
			}

			// for ASE, we get the transaction ID from the sequence table
			// SQL insert statement has placeholder for TransactionID
			if(conn.getParams().getConnectionType() == BSDBParams.CONN_ASE)
			{
				Object[] parms2 = {
					new Integer(DBUtil.getNextId(	conn.getConnection(),
													ConnectionDefines.DB_SYBASE_ASE,
													ASE_BS_TRANSACTION_ID_SEQUENCE_PARTIAL_NAME	)),
					transactionTime,
					new Integer(transType),
					transfer.getToAccount().getID(),
					transfer.getToAccount().getBankID(),
					toAmount.toString(),
					transfer.getFromAccount().getID(),
					transfer.getFromAccount().getBankID(),
					transfer.getToAccount().getCurrencyCode(),
					transfer.getMemo(),
					chequeNum,
					Transaction.PREVIOUS_DAY_TRANSACTION,
					toOldBalance.add(toAmount)
				};

				conn.executeUpdate(ADD_SECOND_TRANSACTION, parms2);
			}
			else if(conn.getParams().getConnectionType() == BSDBParams.CONN_MSSQL){
				Object[] parms2 = {
						new Integer(DBUtil.getNextId(	conn.getConnection(),
														ConnectionDefines.DB_MS_SQLSERVER,
														MSSQL_BS_TRANSACTION_ID_SEQUENCE_PARTIAL_NAME	)),
						transactionTime,
						new Integer(transType),
						transfer.getToAccount().getID(),
						transfer.getToAccount().getBankID(),
						toAmount.toString(),
						transfer.getFromAccount().getID(),
						transfer.getFromAccount().getBankID(),
						transfer.getToAccount().getCurrencyCode(),
						transfer.getMemo(),
						chequeNum,
						Transaction.PREVIOUS_DAY_TRANSACTION,
						toOldBalance.add(toAmount)
					};

					conn.executeUpdate(ADD_SECOND_TRANSACTION_MSSQL, parms2);
			} else {
				// set blank string if bank id is null for the oracle database
				if(transfer.getFromAccount().getBankID() == null) {
					transfer.getFromAccount().setBankID("");
				}
				
				if(transfer.getToAccount().getBankID() == null) {
					transfer.getToAccount().setBankID("");
				}
				Object[] parms2 = {
						transactionTime,
						new Integer(transType),
						transfer.getToAccount().getID(),
						transfer.getToAccount().getBankID(),
						toAmount.toString(),
						transfer.getFromAccount().getID(),
						transfer.getFromAccount().getBankID(),
						transfer.getToAccount().getCurrencyCode(),
						transfer.getMemo(),
						chequeNum,
						Transaction.PREVIOUS_DAY_TRANSACTION,
						toOldBalance.add(toAmount)

				};

				conn.executeUpdate(ADD_SECOND_TRANSACTION, parms2);
			}
			//update balances
			DBAccount.updateBalance(transfer.getToAccount(), toOldBalance.add(toAmount), conn); 
		}			
		
			
			

			// Get the reference number for this transaction from the database.
			//Begin Modify by TCG Team for MS SQL and ASE Support
	    	if( conn.getParams().getConnectionType() == BSDBParams.CONN_DB2390 ||
          	conn.getParams().getConnectionType() == BSDBParams.CONN_MSSQL ||
          	conn.getParams().getConnectionType() == BSDBParams.CONN_ASE ) {
				// Since DB@ on the 390 does not support sequences, the reference
				// number isn't even set yet.  So we set the reference number to
				// be one of the automatically generated transaction IDS.
				Object[] getIDParms = {transactionTime};

				DBResultSet rset = null;

				if (conn.getParams().getConnectionType() == BSDBParams.CONN_MSSQL ||
				conn.getParams().getConnectionType() == BSDBParams.CONN_ASE )
				{
				  rset = conn.prepareQuery( GET_TRANSACTION_ID_MSSQL );
				}
				else
				{
					rset = conn.prepareQuery( GET_TRANSACTION_ID );
				}

				//End Modify
				rset.open(getIDParms);
				rset.getNextRow();
				int refNum = rset.getColumnInt(1);
				rset.close();
				transfer.setID(Integer.toString(refNum));

				Object[] setRefIDParms = {new Integer(refNum), transactionTime};
				conn.executeUpdate(UPDATE_TRANSACTION_NO_SEQUENCE, setRefIDParms);

				if (transType != TransactionTypes.TYPE_CHECK) {
					transfer.setReferenceNumber(Integer.toString(refNum));
				}

			} else {
				if (transType != TransactionTypes.TYPE_CHECK) {
					DBResultSet rset = null;

					rset = conn.prepareQuery(GET_REFERENCE_NUMBER);
					rset.open();
					rset.getNextRow();
					int refNum = rset.getColumnInt(1);
					transfer.setReferenceNumber(Integer.toString(refNum));
					transfer.setID(Integer.toString(refNum));
					rset.close();
				}
			}

			// Set the transfer status
			transfer.setStatus(TransferStatus.TRS_TRANSFERED);

			// Set the new balance for the two accounts in the Account
			// object
			DateTime date = new DateTime();
			Date transTime = new Date(transactionTime.longValue());
			date.setTime(transTime);

			// Set the transfer status
			transfer.setStatus(TransferStatus.TRS_TRANSFERED);
			transfer.setDate(date);

			Balance fromBalance = new Balance();
			Currency fromCurrency = new Currency();
			fromCurrency.setAmount(fromOldBalance.subtract(fromAmount));
			fromBalance.setAmount(fromCurrency);
			fromBalance.setDate(date);
			transfer.getFromAccount().setCurrentBalance(fromBalance);

			if(allowToAccountValidations){
			Balance toBalance = new Balance();
			Currency toCurrency = new Currency();
			toCurrency.setAmount(toOldBalance.add(toAmount));
			toBalance.setAmount(toCurrency);
			toBalance.setDate(date);
			transfer.getToAccount().setCurrentBalance(toBalance);
			}
			conn.commit();

			return transfer;

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
					
			//Start	added by TCG for ASE-EAServer Porting
			// To avoid SET CHAINED error in ASE 		
		} catch (BSException bs) {
			// Roll back the transaction then wrap and rethrow sqle
			try {
				conn.rollback();
			} catch (SQLException sqle) {
				// Just ignore this one and rethrow the previous
				// since it's probably related to what happened before
			}
			throw bs;
			
		} catch (Throwable thr) {
			// Roll back the transaction then wrap and rethrow sqle
			try {
				conn.rollback();
			} catch (SQLException sqle) {
				// Just ignore this one and wrap and rethrow the previous
				// since it's probably related to what happened before
			}
			throw new BSException(BSException.BSE_DB_EXCEPTION,thr.toString());
			//End added by TCG for ASE-EAServer Porting
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
	
	private static void simulateError(Transfer transfer) throws BSException {
		
		String OCB_ERR_ACC_NOT_EXIT = "OCB NO ACCOUNT";
		String OCB_ERR_ACC_NO_FUNDS = "OCB NO FUNDS";
		String OCB_ERR = "OCB ERROR";
		if (transfer.getMemo() != null ) {
			
			String memo = transfer.getMemo();
			
			if(OCB_ERR_ACC_NOT_EXIT.equalsIgnoreCase(memo)) {
				throw new BSException(BSException.BSE_ACCOUNT_NOT_EXISTS,
						MessageText.getMessage(IBSErrConstants.ERR_FROM_ACC_NOT_EXIST));
			} else if(OCB_ERR_ACC_NO_FUNDS.equalsIgnoreCase(memo)) {
				throw new BSException(BSException.BSE_NSF,
						MessageText.getMessage(IBSErrConstants.ERR_ACCOUNTS_SAME));
			} else if(OCB_ERR.equalsIgnoreCase(memo)) {
				throw new BSException(BSException.BSE_DB_EXCEPTION,
						MessageText.getMessage(IBSErrConstants.ERR_COULD_NOT_CONNECT_TO_DB));
			}
			
		}
	}


    private static boolean validNonZeroAmount(Currency amount)
    {
        if (amount == null)
            return false;

        return amount.getAmountValue().compareTo(new BigDecimal(0)) > 0;
    }

}
