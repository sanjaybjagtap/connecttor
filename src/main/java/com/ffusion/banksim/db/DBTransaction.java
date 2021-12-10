//
// DBTransaction.java
//
// Copyright (c) 2002 Financial Fusion, Inc.
// All Rights Reserved
//

package com.ffusion.banksim.db;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.Format;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Properties;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ffusion.banksim.adapter.BSAdapter;
import com.ffusion.banksim.interfaces.BSConstants;
import com.ffusion.banksim.interfaces.BSException;
import com.ffusion.beans.DateTime;
import com.ffusion.beans.accounts.Account;
import com.ffusion.beans.accounts.GenericBankingRptConsts;
import com.ffusion.beans.banking.Transaction;
import com.ffusion.beans.banking.Transactions;
import com.ffusion.beans.common.Currency;
import com.ffusion.beans.reporting.ReportCriteria;
import com.ffusion.beans.reporting.ReportSortCriteria;
import com.ffusion.beans.reporting.ReportSortCriterion;
import com.ffusion.efs.adapters.profile.util.Profile;
import com.ffusion.util.DateConsts;
import com.ffusion.util.DateParser;
import com.ffusion.util.IntMap;
import com.ffusion.util.MapUtil;
import com.ffusion.util.beans.ExtendABean;
import com.ffusion.util.beans.PagingContext;
import com.ffusion.util.beans.XMLStrings;
import com.ffusion.util.db.ConnectionDefines;
import com.ffusion.util.db.DBUtil;

public class DBTransaction
{
    private static HashMap	_pagedTransactionList = new HashMap();
    private static HashMap	_pagedTransactionLoc = new HashMap();
    private static HashMap	_accountsUsageList = new HashMap();
    private static HashMap      _pagedTransactionCountList = new HashMap();
	
	private static Logger logger = LoggerFactory.getLogger(DBTransaction.class);
	
	private static final String SQL_GET_PAGED_TRANSACTIONS = "SELECT b.TransactionID, b.TransactionTypeID,  " +
	"b.AccountID, b.TransactionDate, b.Amount,b.CurrencyCode, b.Memo, b.ReferenceNumber, b.DataClassification, b.RunningBalance, " + 
	"c.Description, a.RoutingNum " +
	"FROM BS_Transactions b, BS_Account a, BS_TransactionType c " +
	"WHERE a.AccountID = b.AccountID AND c.TransactionTypeID = b.TransactionTypeID ";
	
	/* This new SQL is added to support pagination for Account History and Transaction Search */
	private static final String SQL_GET_PAGED_TRANSACTIONS_NEW = "SELECT b.TransactionID, b.TransactionTypeID,  " +
			"b.AccountID, b.TransactionDate, b.Amount, cast(b.Amount as decimal(31,3)) as DecAmount, b.Memo, b.ReferenceNumber, b.DataClassification, b.RunningBalance, " + 
			"c.Description, a.RoutingNum " +
			"FROM BS_Transactions b, BS_Account a, BS_TransactionType c " +
			"WHERE a.AccountID = b.AccountID AND c.TransactionTypeID = b.TransactionTypeID ";

	/* SQL to allow jump-to-page operation for account history and transaction search */
	private static final String SQL_GET_TRANSACTION_KEYS = "SELECT b.TransactionID " +
			"FROM BS_Transactions b, BS_Account a, BS_TransactionType c " +
			"WHERE a.AccountID = b.AccountID AND c.TransactionTypeID = b.TransactionTypeID ";
	
	
	/* This new SQL is added to support pagination for Account History and Transaction Search */ 
	private static final String SQL_GET_TRANSACTIONS_NUM = "SELECT count(*) FROM BS_Transactions b, BS_Account a, BS_TransactionType c " +
				"WHERE a.AccountID = b.AccountID AND c.TransactionTypeID = b.TransactionTypeID ";
	
	private static final String ADD_TRANSACTION = "INSERT into BS_Transactions( TransactionID, TransactionDate, " +
	"TransactionTypeID, AccountID, FIID, Amount, CurrencyCode, Memo, ChequeNumber, ReferenceNumber, DataClassification, RunningBalance ) " +
	    "values( ?,?,?,?,?,?,?,?,?,?,?,? )";
	
	private static final String AND_TRANSACTION_ID_CLAUSE = " AND b.TransactionID=?";
	
    /**
    * getTransactions - get the transactions under the specified account within a specified time period
    * @param account the account that we will retrieve the list of transactions for.
    * @param startDate a Calendar object that specifies the beginning of the time period
    * @param endDate a Calendar object that specifies the end of the time period
    * @param conn DBConnection object that used to connect to the BankSim database
    * @return Enumeration of Transaction objects
    * @exception BSException that wraps around SQL Exception thrown by other methods
    */
    public static final Enumeration getTransactions( Account account, Calendar startDate, Calendar endDate, DBConnection conn ) throws BSException
    {
		PagingContext context = new PagingContext( startDate, endDate );
		ReportCriteria criteria = new ReportCriteria();
		HashMap newMap = new HashMap();
		context.setMap( newMap );
		context.getMap().put( BSConstants.PAGING_CONTEXT_REPORT_CRITERIA, criteria );
		Format formatter = com.ffusion.util.DateFormatUtil.getFormatter( DateConsts.REPORT_CRITERIA_INTERNAL_FORMAT_DATETIME_STR_WITH_SEC );
		if ( startDate != null ) {
			criteria.getSearchCriteria().setProperty( DateConsts.SEARCH_CRITERIA_START_DATE, formatter.format( startDate.getTime() ) );
		}
		if ( endDate != null ) {
			criteria.getSearchCriteria().setProperty( DateConsts.SEARCH_CRITERIA_END_DATE, formatter.format( endDate.getTime() ) );
		}
		HashMap extra = new HashMap();
		
		return getTransactions( account, context, extra, conn );
	
    }

	   /**
    * getTransactions - gets an <code>Enumeration</code> containing all transactions under the specified account that match the criteria that are passed in the <code>PagingContext</code>.
    * @param account the account that we will retrieve the list of transactions for.
    * @param context the <code>PagingContext</code> that contains the criteria used to retrieve the transactions
    * @param extra a hash map that may contain extra parameters used to retrieve the transactions
    * @param conn a <code>DBConnection</code> object that will be used to connect to the database
    * @return <code>Enumeration</code> of transactions that match the criteria that were passed into the method.
    * @exception BSException that wraps around the SQL Exception thrown by other methods
    */
    public static final Enumeration getTransactions( Account account, PagingContext context, HashMap extra, DBConnection conn ) throws BSException
    {
		Enumeration trans = null;
		// check to see if the account exists in the database
		if( !DBAccount.doesAccountExist( account, conn ) )
		{
			throw new BSException( BSException.BSE_ACCOUNT_NOT_EXISTS,
					MessageText.getMessage( IBSErrConstants.ERR_ACCOUNT_NOT_EXISTS ) );
		}
		
		ReportCriteria criteria = (ReportCriteria) context.getMap().get( BSConstants.PAGING_CONTEXT_REPORT_CRITERIA );
		
		criteria.getSearchCriteria().put( BSConstants.PAGING_CONTEXT_SEARCH_CRITERIA_ACCOUNT, account.getID() );
		criteria.getSearchCriteria().put( BSConstants.PAGING_CONTEXT_SEARCH_CRITERIA_ROUT_NUM, account.getRoutingNum() );
		
					//Get the transactions//
		ResultSet rs = null;
		PreparedStatement stmt = null;	
		try {
			StringBuffer stmtBuffer = new StringBuffer( SQL_GET_PAGED_TRANSACTIONS );
			addSearchCriteria( stmtBuffer, account, context, extra );
			addOrderByClause( stmtBuffer, context, extra );
			stmt = conn.prepareStatement( conn, stmtBuffer.toString() );
			int idx = 1;
			//Now add the dynamically created search and sort criteria
			addSearchValues(stmt, idx, account, context, extra);
			rs = conn.executeQuery( stmt, stmtBuffer.toString() );
			
			Transactions transactions = new Transactions();
			trans = createTransactions( transactions, rs, account );
			trans = filterTransactionsAmount( transactions, trans, account, context, extra );
			
		} catch( BSException bex ) {
			throw bex;
		} catch( Exception ex ) {
			throw new BSException( BSException.BSE_DB_EXCEPTION, ex.toString() );
		} finally {
			criteria.getSearchCriteria().remove( BSConstants.PAGING_CONTEXT_SEARCH_CRITERIA_ACCOUNT );
			criteria.getSearchCriteria().remove( BSConstants.PAGING_CONTEXT_SEARCH_CRITERIA_ROUT_NUM );
		    try {
				rs.close();
				stmt.close();
			} catch( Exception e ) {
			}
		}
		return trans;
    }
	
	/**
	* Creates an Enumeration of transactions given a list of transactions, result set and an account.
	*/
	private static Enumeration createTransactions( Transactions transactions, ResultSet rs, Account account ) throws SQLException {
	    Date date = null;
	    Currency amount = null;
		Transaction transaction = null;
		Vector transVector = new Vector();
	    //transIdx: counter to generate the transactionIdx
	    int transIdx = 0;
		
		while( rs.next() ) {
			transaction = transactions.create();
			transaction.setID( rs.getString( "TransactionID" ) );		//Transaction ID
			transaction.setType( rs.getInt( "TransactionTypeID" ) );		//Transaction Type
			transaction.setMemo( rs.getString( "Memo" ) );	//Transaction Memo
			transaction.setDescription( rs.getString( "Memo" ) );	//Transaction Description is the Memo
	
			//Transaction reference / check number
			transaction.setReferenceNumber( String.valueOf( rs.getInt( "ReferenceNumber" ) ) );
			
			//Transaction date
			date = new Date();
			date.setTime( rs.getLong( "TransactionDate" ) );
			transaction.setDate( new DateTime( date, account.getLocale() ) );
			transaction.setProcessingDate( new DateTime( date, account.getLocale() ) );
			transaction.setValueDate(new DateTime( date, account.getLocale() ) );
			
			//Transaction amount
			String amountString = (String)rs.getString( "Amount" );
			String currencyCode = (String)rs.getString("CurrencyCode");
			amount = null;
			if( amountString != null ) {
			    amount = new Currency( amountString.trim(), currencyCode, null );
			}
			transaction.setAmount( amount );
			
			//set transaction isCredit property
			boolean _isCredit = true;
			if(amount.doubleValue() < 0.0){
				_isCredit = false;
			}
			transaction.setIsCredit(_isCredit);
		
			//Transaction RunningBalance
			BigDecimal runningBal = rs.getBigDecimal( "RunningBalance" );
			amount = null;
			if( runningBal != null ) {
			    amount = new Currency( rs.getBigDecimal( "RunningBalance" ), account.getCurrencyCode(), account.getLocale() );
			}
			transaction.setRunningBalance( amount );
			
			//Transaction Date Classification Value
			transaction.setDataClassification( rs.getString("DataClassification" ) );
		
			transaction.setTransactionIndex( transIdx++ );			
			
			transVector.add( transaction );
	    }
		return transVector.elements();
	}
	
	/**
	* Creates an Enumeration of Paged transactions given a list of transactions, result set and an account.
	*/
	private static Enumeration createTransactionsForPagination( Transactions transactions, ResultSet rs, Account account ) throws SQLException {
	    Date date = null;
	    Currency amount = null;
		Transaction transaction = null;
		Vector transVector = new Vector();
	    //transIdx: counter to generate the transactionIdx
	    int transIdx = 0;
		
		while( rs.next() ) {
			transaction = transactions.create();
			transaction.setID( rs.getString( "TransactionID" ) );		//Transaction ID
			transaction.setType( rs.getInt( "TransactionTypeID" ) );		//Transaction Type
			transaction.setMemo( rs.getString( "Memo" ) );	//Transaction Memo
			transaction.setDescription( rs.getString( "Memo" ) );	//Transaction Description is the Memo
	
			//Transaction reference / check number
			transaction.setReferenceNumber( String.valueOf( rs.getInt( "ReferenceNumber" ) ) );
			
			//Transaction date
			date = new Date();
			date.setTime( rs.getLong( "TransactionDate" ) );
			transaction.setDate( new DateTime( date, account.getLocale() ) );
			transaction.setValueDate( new DateTime( date, account.getLocale() ) );
			transaction.setProcessingDate( new DateTime( date, account.getLocale() ) );
			
			//Transaction amount
			String amountString = (String)rs.getString( "Amount" );
			amount = null;
			if( amountString != null ) {
			    amount = new Currency( amountString.trim(), account.getCurrencyCode(), account.getLocale() );
			}
			transaction.setAmount( amount );
			
			//Transaction decimalAmountString
			String decimalAmountString = "" + rs.getInt( "DecAmount" );
			if( decimalAmountString != null ) {
				transaction.setTrackingID(decimalAmountString);
			}
			
			//set transaction isCredit property
			boolean _isCredit = true;
			if(amount.doubleValue() < 0.0){
				_isCredit = false;
			}
			transaction.setIsCredit(_isCredit);
		
			//Transaction RunningBalance
			BigDecimal runningBal = rs.getBigDecimal( "RunningBalance" );
			amount = null;
			if( runningBal != null ) {
			    amount = new Currency( rs.getBigDecimal( "RunningBalance" ), account.getCurrencyCode(), account.getLocale() );
			}
			transaction.setRunningBalance( amount );
			
			//Transaction Date Classification Value
			transaction.setDataClassification( rs.getString("DataClassification" ) );
		
			//transaction.setTransactionIndex( transIdx++ );			
			transaction.setTransactionIndex( Long.valueOf(rs.getString( "TransactionID")) );//for pagination
			
			transVector.add( transaction );
	    }
		return transVector.elements();
	}
	
	private static void createTransactionsForPagination2( Transactions transactions, ResultSet rs, Account account ) throws SQLException {
	    Date date = null;
	    Currency amount = null;
		Transaction transaction = null;
	    //transIdx: counter to generate the transactionIdx
	    int transIdx = 0;
		
		while( rs.next() ) {
			transaction = transactions.create();
			transaction.setID( rs.getString( "TransactionID" ) );		//Transaction ID
			transaction.setType( rs.getInt( "TransactionTypeID" ) );		//Transaction Type
			transaction.setMemo( rs.getString( "Memo" ) );	//Transaction Memo
			transaction.setDescription( rs.getString( "Memo" ) );	//Transaction Description is the Memo
	
			//Transaction reference / check number
			transaction.setReferenceNumber( String.valueOf( rs.getInt( "ReferenceNumber" ) ) );
			
			//Transaction date
			date = new Date();
			date.setTime( rs.getLong( "TransactionDate" ) );
			transaction.setDate( new DateTime( date, account.getLocale() ) );
			transaction.setValueDate(new DateTime( date, account.getLocale() ));
			transaction.setProcessingDate( new DateTime( date, account.getLocale() ) );
			
			//Transaction amount
			String amountString = (String)rs.getString( "Amount" );
			amount = null;
			if( amountString != null ) {
			    amount = new Currency( amountString.trim(), account.getCurrencyCode(), account.getLocale() );
			}
			transaction.setAmount( amount );
			
			//Transaction decimalAmountString
			String decimalAmountString = "" + rs.getInt( "DecAmount" );
			if( decimalAmountString != null ) {
				transaction.setTrackingID(decimalAmountString);
			}
			
			//set transaction isCredit property
			boolean _isCredit = true;
			if(amount.doubleValue() < 0.0){
				_isCredit = false;
			}
			transaction.setIsCredit(_isCredit);
		
			//Transaction RunningBalance
			BigDecimal runningBal = rs.getBigDecimal( "RunningBalance" );
			amount = null;
			if( runningBal != null ) {
			    amount = new Currency( rs.getBigDecimal( "RunningBalance" ), account.getCurrencyCode(), account.getLocale() );
			}
			transaction.setRunningBalance( amount );
			
			//Transaction Date Classification Value
			transaction.setDataClassification( rs.getString("DataClassification" ) );
		
			//transaction.setTransactionIndex( transIdx++ );			
			transaction.setTransactionIndex( Long.valueOf(rs.getString( "TransactionID")) );//for pagination
			transaction.setAccount(account);
	    }

	}
	
	/**
	* Performs the filtering of amount values.  Since amounts are stored in the database as strings, it is not possible to search and sort amount values properly.  So we must use this method
	* that will perform the filtering and sorting after retrieving the transactions from the database.
	*/
	private static Enumeration filterTransactionsAmount( Transactions trans, Enumeration enum_v, Account account, PagingContext context, HashMap extra ) {
		if (context == null) {
			return enum_v;
	    }

	    HashMap pcMap = context.getMap();
	    if (pcMap == null) {
			return enum_v;
	    }

	    ReportCriteria rc = (ReportCriteria)pcMap.get(BSConstants.PAGING_CONTEXT_REPORT_CRITERIA);
		if ( rc == null || rc.getSearchCriteria() == null ) {
			return enum_v;
	    }
		ReportSortCriteria sortCriteria = rc.getSortCriteria();
	    Properties searchCriteria = rc.getSearchCriteria();
		
		trans = filterAmountSearch( trans, searchCriteria, account.getLocale(), extra);
		
		filterAmountSort( trans, sortCriteria, extra );
		
		Vector transVector = new Vector();
		for( int i = 0 ; i < trans.size(); i++ ) {
			transVector.add( trans.get(i) );
		}
		return transVector.elements();
		
	}
	
	/**
	* This method will sort the transactions based on the amount sort criteria.
	*/
	private static void filterAmountSort( Transactions trans, ReportSortCriteria sortCriteria, HashMap extra ) {
		String columnName = "";
		StringBuffer sortString = new StringBuffer();
		boolean doSort = false;
		
		//check if we even need to sort by amount, if we don't then return 
		boolean sortAmount = false;
		for( int i=0 ; i < sortCriteria.size(); i++ ) {
			ReportSortCriterion crit = (ReportSortCriterion) sortCriteria.get( i );
	    	columnName = crit.getName();
			if( GenericBankingRptConsts.SORT_CRITERIA_AMOUNT.equals( columnName ) ) {
				sortAmount = true;
			}
		}
		if( !sortAmount ) {
			return;
		}
		
		for( int i = 0; i < sortCriteria.size(); i++ ) {
	    	doSort = true;
			ReportSortCriterion crit = (ReportSortCriterion) sortCriteria.get( i );
	    	columnName = crit.getName();
			boolean asc = crit.getAsc();
			if( i != 0 ) {
				sortString.append(",");
			}
			if( GenericBankingRptConsts.SORT_CRITERIA_AMOUNT.equals( columnName ) ) {
				if( asc ) {
					sortString.append( XMLStrings.AMOUNT );
				}
				else {
					sortString.append( XMLStrings.AMOUNT ).append(",REVERSE");
				}
			}
			else if( GenericBankingRptConsts.SORT_CRITERIA_DATE.equals( columnName ) ) {
				if( asc ) {
					sortString.append( XMLStrings.DATE);
				}
				else {
					sortString.append( XMLStrings.DATE ).append(",REVERSE");
				}
			}
			else if( GenericBankingRptConsts.SORT_CRITERIA_DESCRIPTION.equals( columnName ) ) {
				if( asc ) {
					sortString.append( XMLStrings.DESCRIPTION );
				}
				else {
					sortString.append( XMLStrings.DESCRIPTION ).append(",REVERSE");
				}
			}
			else if( GenericBankingRptConsts.SORT_CRITERIA_TRANS_TYPE.equals( columnName ) ) {
				if( asc ) {
					sortString.append( XMLStrings.TYPE ).append( ExtendABean.STRING );
				}
				else {
					sortString.append( XMLStrings.TYPE ).append( ExtendABean.STRING ).append(",REVERSE");
				}
			}
			else if( GenericBankingRptConsts.SORT_CRITERIA_TRANS_REF_NUM.equals( columnName ) ) {
				if( asc ) {
					sortString.append( XMLStrings.REFERENCENUMBER );
				}
				else {
					sortString.append( XMLStrings.REFERENCENUMBER ).append(",REVERSE");
				}
			}
	    }
		if( doSort ) {
			trans.setSortedBy( sortString.toString() );
		}
		
	}	
    
	/**
	* This method will filter a list of transactions given a set of amount search criteria.
	*/
	private static Transactions filterAmountSearch( Transactions trans, Properties searchCriteria, Locale theLocale, HashMap extra ) {
		String valueMin = searchCriteria.getProperty(GenericBankingRptConsts.SEARCH_CRITERIA_AMOUNT_MIN);
	    String valueMax = searchCriteria.getProperty(GenericBankingRptConsts.SEARCH_CRITERIA_AMOUNT_MAX);
		if( valueMin == null && valueMax == null ) {
			return trans;
		}
		
		String value1 = searchCriteria.getProperty(GenericBankingRptConsts.SEARCH_CRITERIA_AMOUNT_MIN);
	    String value2 = searchCriteria.getProperty(GenericBankingRptConsts.SEARCH_CRITERIA_AMOUNT_MAX);
	    if( value1 != null && value1.length() > 0 ) {
	        if( value2 != null && value2.length() > 0 ) {
	                // insert credit min and credit max
	            Transactions filteredTrans = new Transactions();
	            Currency val1 = new Currency( value1, theLocale );
	            Currency val2 = new Currency( value2, theLocale );
				BigDecimal negVal = val1.getAmountValue().negate();
	            Currency val1Neg = new Currency( negVal, theLocale );
				negVal = val2.getAmountValue().negate();
	            Currency val2Neg = new Currency( negVal, theLocale );
	            for( int i=0 ; i < trans.size(); i++ ) {
	                Transaction nextTrans = (Transaction)trans.get(i);
	                
	                int ret1 = nextTrans.getAmountValue().compareTo(val1);
	                int ret2 = nextTrans.getAmountValue().compareTo(val2);
	                int ret3 = nextTrans.getAmountValue().compareTo(val1Neg);
	                int ret4 = nextTrans.getAmountValue().compareTo(val2Neg);
	                if( ( ret1 >=0 && ret2 <=0 ) || ( ret3 <= 0 && ret4 >= 0 ) ) {
	                    filteredTrans.add( nextTrans );
	                }
	            }
	            return filteredTrans;
	        } else {
					String firstPart = "AMOUNT>="+value1;
	                value1 = '-' + value1;
					String secondPart = "AMOUNT<="+value1;
					trans.setFilter( firstPart + "," + secondPart );
					return trans;
	        }
	    } else if( value2 != null && value2.length() > 0 ) {
				String firstPart = "AMOUNT<="+value2;
				value2 = '-' + value2;
				String secondPart = "AMOUNT>="+value2;
				trans.setFilter( firstPart + "," + secondPart + ",AND" );
				return trans;
	    }
	    
	    return trans;
		
		
	}
	
	/**
	* This method will dynamically append the order by clauses on the sql string buffer based on what sort criterion was passed in the PagingContext.
	*/
    private static void addOrderByClause( StringBuffer sqlStringBuf, PagingContext context, HashMap extra ) {
	
        ReportCriteria criteria = (ReportCriteria) context.getMap().get( BSConstants.PAGING_CONTEXT_REPORT_CRITERIA );
	    ReportSortCriteria sortCriteria = criteria.getSortCriteria();
	   
		boolean orderByAdded = false;
	    boolean sortAscending = true;
	    
	    int sortCriteriaSize = sortCriteria.size();
	    for( int i = 0; i < sortCriteriaSize; ++i ) {
	    	ReportSortCriterion crit = (ReportSortCriterion) sortCriteria.get( i );
	    	String columnName = getColumnName( crit.getName() );
			if( columnName != null ) {
				if( !orderByAdded ) {
					sqlStringBuf.append( " ORDER BY " );
					orderByAdded = true;
				}
				sqlStringBuf.append( columnName );
				sortAscending = crit.getAsc();		
	
				if( sortAscending ) {
					sqlStringBuf.append( " ASC" );
				} else {
					sqlStringBuf.append( " DESC" );	
				}
				
				if( i < (sortCriteriaSize-1) ) {
					sqlStringBuf.append( ", " );
				}
			}
	    }
    }
	
	/**
	* This method does a simple translation of criteria name and it's corresponding column name in the database.
	*/
	private static String getColumnName( String criteriaName ) {

		if( GenericBankingRptConsts.SORT_CRITERIA_DATE.equals( criteriaName ) ) {
			return new String("TransactionDate");
		} else if( GenericBankingRptConsts.SORT_CRITERIA_DESCRIPTION.equals( criteriaName ) ) {
			return new String("Memo");		
		} else if( GenericBankingRptConsts.SORT_CRITERIA_TRANS_TYPE.equals( criteriaName ) ) {
			return new String("Description");
		} else if( GenericBankingRptConsts.SORT_CRITERIA_TRANS_REF_NUM.equals( criteriaName ) ) {
			return new String("ReferenceNumber");		
		} else if( GenericBankingRptConsts.SORT_CRITERIA_RUNNING_BALANCE.equals( criteriaName ) ) {
			return new String("RunningBalance");		
		}else if( GenericBankingRptConsts.SORT_CRITERIA_AMOUNT.equals( criteriaName ) ) {
			return new String("Amount");		
		}
		return null;
	
	}
	
	/**
	* This method does a simple translation of criteria name and it's corresponding column name in the database.
	* This method is used when supporting pagination for Account History
	*/
	private static String getColumnNameForPagination( String criteriaName ) {
		String returnValue = getColumnName(criteriaName);
		if(returnValue!=null){
			if( GenericBankingRptConsts.SORT_CRITERIA_TRANS_TYPE.equals( criteriaName ) &&  "Description".equalsIgnoreCase(returnValue)) {
				returnValue =  new String("c.Description");
			}
			else if( GenericBankingRptConsts.SORT_CRITERIA_AMOUNT.equals( criteriaName ) &&  "Amount".equalsIgnoreCase(returnValue)) {
				return new String("cast(b.Amount as decimal(31,3))");		
			} 
		}
		return returnValue;
	}
	
	/**
	 * This method will add dynamically append the search criteria sql text for the prepared statement.
	 */
	private static void addAccountTransactionsSearchCriteria ( StringBuffer sqlStringBuf, Account account, PagingContext context, HashMap extra ) {
		String value = "";

		if (context == null) {
			return;
		}

		HashMap pcMap = context.getMap();
		if (pcMap == null) {
			return;
		}

		ReportCriteria rc = (ReportCriteria)pcMap.get(BSConstants.PAGING_CONTEXT_REPORT_CRITERIA);
		if ( rc == null || rc.getSearchCriteria() == null ) {
			return;
		}

		Properties searchCriteria = rc.getSearchCriteria();

		if( searchCriteria==null ) {
			return;
		}

		// Account ID
		String accountId = (String)searchCriteria.getProperty(BSConstants.PAGING_CONTEXT_SEARCH_CRITERIA_ACCOUNT);
		if( accountId != null && accountId.length() != 0 ) {
			sqlStringBuf.append(" and b.AccountID = ? " );
		}

		// Start Date
		value = searchCriteria.getProperty( DateConsts.SEARCH_CRITERIA_START_DATE );
		if (value != null && value.length() > 0 ) {
			sqlStringBuf.append(" and b.TransactionDate >= ? " );
		}

		// End Date
		value = searchCriteria.getProperty( DateConsts.SEARCH_CRITERIA_END_DATE );
		if (value != null && value.length() > 0 ) {
			sqlStringBuf.append(" and b.TransactionDate <= ? " );
		}

	}

	/**
	* This method will add dynamically append the search criteria sql text for the prepared statement.
	*/
	private static void addSearchCriteria ( StringBuffer sqlStringBuf, Account account, PagingContext context, HashMap extra ) {
		String value = "";
		
		if (context == null) {
			return;
	    }

	    HashMap pcMap = context.getMap();
	    if (pcMap == null) {
		return;
	    }

	    ReportCriteria rc = (ReportCriteria)pcMap.get(BSConstants.PAGING_CONTEXT_REPORT_CRITERIA);
			if ( rc == null || rc.getSearchCriteria() == null ) {
			return;
	    }
		
		Properties searchCriteria = rc.getSearchCriteria();
		
		if( searchCriteria==null ) {
			return;
		}
		
	    // Account ID
		String accountId = (String)searchCriteria.getProperty(BSConstants.PAGING_CONTEXT_SEARCH_CRITERIA_ACCOUNT);
		String routingNum = (String)searchCriteria.getProperty(BSConstants.PAGING_CONTEXT_SEARCH_CRITERIA_ROUT_NUM);
		if( accountId != null && accountId.length() != 0 ) {
			sqlStringBuf.append(" and b.AccountID = ? " );
		}
		if( routingNum != null && routingNum.length() != 0 ) {
			sqlStringBuf.append(" and a.RoutingNum = ? " );
		}


	    // Start Date
	    value = searchCriteria.getProperty( DateConsts.SEARCH_CRITERIA_START_DATE );
	    if (value != null && value.length() > 0 ) {
		sqlStringBuf.append(" and b.TransactionDate >= ? " );
	    }

	    // End Date
	    value = searchCriteria.getProperty( DateConsts.SEARCH_CRITERIA_END_DATE );
	    if (value != null && value.length() > 0 ) {
		sqlStringBuf.append(" and b.TransactionDate <= ? " );
	    }
	
	    // Transaction reference number (start)
	    value = searchCriteria.getProperty(GenericBankingRptConsts.SEARCH_CRITERIA_TRANS_REF_MIN);
	    if (value != null && value.length() > 0 ) {
	    sqlStringBuf.append(" and b.ReferenceNumber >=?");
	    }

	    // Transaction reference number (end)
	    value = searchCriteria.getProperty(GenericBankingRptConsts.SEARCH_CRITERIA_TRANS_REF_MAX);
	    if (value != null && value.length() > 0 ) {
	    sqlStringBuf.append(" and b.ReferenceNumber <=?");
	    }
	    
		
	    // Transaction type
	    value = searchCriteria.getProperty(GenericBankingRptConsts.SEARCH_CRITERIA_TRANS_TYPE);
	    if (value != null && value.length() > 0 && value.indexOf(GenericBankingRptConsts.SEARCH_CRITERIA_VALUE_ALL_TRANS_TYPE) == -1) {
		sqlStringBuf.append(" and b.TransactionTypeID in (  ");
		sqlStringBuf.append( value );
		sqlStringBuf.append( ")" );
	    }
	    
		//Description
		value = searchCriteria.getProperty( GenericBankingRptConsts.SEARCH_CRITERIA_DESCRIPTION );
		if( value != null && value.length() > 0 ) {
			sqlStringBuf.append(" and b.Memo LIKE '%"+ DBUtil.escapeSQLStringLiteral( value ) +"%'" );
		}

	}
	
	
	/**
	* This method will add dynamically append the search criteria sql text for the prepared statement.
	*/
	private static void addAmountSearchCriteria ( StringBuffer sqlStringBuf, Account account, PagingContext context, HashMap extra ) {
		String value = "";
		
		if (context == null) {
			return;
	    }

	    HashMap pcMap = context.getMap();
	    if (pcMap == null) {
		return;
	    }

	    ReportCriteria rc = (ReportCriteria)pcMap.get(BSConstants.PAGING_CONTEXT_REPORT_CRITERIA);
			if ( rc == null || rc.getSearchCriteria() == null ) {
			return;
	    }
		
		Properties searchCriteria = rc.getSearchCriteria();
		
		if( searchCriteria==null ) {
			return;
		}
		
		
		 // Amount
	    String valueMin = searchCriteria.getProperty(GenericBankingRptConsts.SEARCH_CRITERIA_AMOUNT_MIN);
	    String valueMax = searchCriteria.getProperty(GenericBankingRptConsts.SEARCH_CRITERIA_AMOUNT_MAX);
	    String valueExact = searchCriteria.getProperty(GenericBankingRptConsts.SEARCH_CRITERIA_AMOUNT);
	    if( valueMin != null && valueMin.length() > 0 ) {
	        if( valueMax != null && valueMax.length() > 0 ) {
	                // ( Amount>=CreditMin and Amount<=CreditMax ) or ( Amount>=DebitMin and Amount<=DebitMax )
	                sqlStringBuf.append(" and ( ( cast(b.Amount as decimal(31,3))>=? and cast(b.Amount as decimal(31,3))<=? ) or ( cast(b.Amount as decimal(31,3))>=? and cast(b.Amount as decimal(31,3))<=? ) )");
	        } else {
	                // ( Amount>=CreditMin or Amount<=DebitMax )
	                sqlStringBuf.append(" and ( cast(b.Amount as decimal(31,3))>=? or cast(b.Amount as decimal(31,3))<=? )");
	        }
	    } else if( valueMax != null && valueMax.length() > 0 ) {
                // ( Amount<=CreditMax and Amount>=DebitMin )
                sqlStringBuf.append(" and ( cast(b.Amount as decimal(31,3))<=? and cast(b.Amount as decimal(31,3))>=? )");
	    } else if(valueExact != null && valueExact.length() > 0){
	    	  sqlStringBuf.append(" and ( cast(b.Amount as decimal(31,3))=? or cast(b.Amount as decimal(31,3))=?)");
	    }
	}
	
	/**
	 * This method will actually add the parameter values for each of the parameters in the prepared statement.
	 */
	private static int addAccountTransactionsSearchValues( PreparedStatement stmt, int idx, Account account, PagingContext context, HashMap extra) throws Exception {

		String value = "";

		if (context == null) {
			return idx;
		}

		HashMap pcMap = context.getMap();
		if (pcMap == null) {
			return idx;
		}

		ReportCriteria rc = (ReportCriteria)pcMap.get(BSConstants.PAGING_CONTEXT_REPORT_CRITERIA);
		if ( rc == null || rc.getSearchCriteria() == null ) {
			return idx;
		}

		Properties searchCriteria = rc.getSearchCriteria();

		if( searchCriteria == null ) {
			return idx;
		}

		// Account ID
		String accountId = (String)searchCriteria.getProperty(BSConstants.PAGING_CONTEXT_SEARCH_CRITERIA_ACCOUNT);
		if( accountId != null && accountId.length() != 0 ) {
			stmt.setString(idx++, accountId );
		}

		// Start Date
		value = searchCriteria.getProperty( DateConsts.SEARCH_CRITERIA_START_DATE );
		if (value != null && value.length() > 0) {
			//DateTime startDate = new DateTime( value, account.getLocale()  );
			Date startDate = DateParser.parse(value, DateConsts.REPORT_CRITERIA_INTERNAL_FORMAT_DATETIME_STR_WITH_SEC);
			long start = startDate.getTime();
			stmt.setLong( idx++, start );
		}

		// End Date
		value = searchCriteria.getProperty( DateConsts.SEARCH_CRITERIA_END_DATE );
		if (value != null && value.length() > 0) {
			Date endDate = DateParser.parse(value, DateConsts.REPORT_CRITERIA_INTERNAL_FORMAT_DATETIME_STR_WITH_SEC);
			long end = endDate.getTime();
			stmt.setLong( idx++, end );
		}
		return idx;
	}
	
	/**
	* This method will actually add the parameter values for each of the parameters in the prepared statement.
	*/
	private static int addSearchValues( PreparedStatement stmt, int idx, Account account, PagingContext context, HashMap extra) throws Exception {
		
	    String value = "";
		
		if (context == null) {
			return idx;
	    }

	    HashMap pcMap = context.getMap();
	    if (pcMap == null) {
			return idx;
	    }

	    ReportCriteria rc = (ReportCriteria)pcMap.get(BSConstants.PAGING_CONTEXT_REPORT_CRITERIA);
	    if ( rc == null || rc.getSearchCriteria() == null ) {
			return idx;
	    }

	    Properties searchCriteria = rc.getSearchCriteria();
		
		if( searchCriteria == null ) {
			return idx;
	    }
		
		// Account ID
		String accountId = (String)searchCriteria.getProperty(BSConstants.PAGING_CONTEXT_SEARCH_CRITERIA_ACCOUNT);
		String routingNum = (String)searchCriteria.getProperty(BSConstants.PAGING_CONTEXT_SEARCH_CRITERIA_ROUT_NUM);
		if( accountId != null && accountId.length() != 0 ) {
			stmt.setString(idx++, accountId );
		}
		if( routingNum != null && routingNum.length() != 0 ) {
			stmt.setString(idx++, routingNum );
		}
		
	    // Start Date
	    value = searchCriteria.getProperty( DateConsts.SEARCH_CRITERIA_START_DATE );
	    if (value != null && value.length() > 0) {
			//DateTime startDate = new DateTime( value, account.getLocale()  );
			Date startDate = DateParser.parse(value, DateConsts.REPORT_CRITERIA_INTERNAL_FORMAT_DATETIME_STR_WITH_SEC);
			long start = startDate.getTime();
			stmt.setLong( idx++, start );
		}

	    // End Date
	    value = searchCriteria.getProperty( DateConsts.SEARCH_CRITERIA_END_DATE );
	    if (value != null && value.length() > 0) {
			Date endDate = DateParser.parse(value, DateConsts.REPORT_CRITERIA_INTERNAL_FORMAT_DATETIME_STR_WITH_SEC);
			long end = endDate.getTime();
			stmt.setLong( idx++, end );
	    }

	    // Transaction Reference Number Min
	    value = searchCriteria.getProperty(GenericBankingRptConsts.SEARCH_CRITERIA_TRANS_REF_MIN);
	    try {
			if (value != null && value.trim().length() > 0) {
				int refNum = Integer.parseInt( value );
				stmt.setInt( idx++, refNum );
			}
		} catch( Exception ex ) {
			throw new BSException( BSException.BSE_INVALID_REFERENCE_NUM );
		}
	    
	    // Transaction Reference Number Max
	    value = searchCriteria.getProperty(GenericBankingRptConsts.SEARCH_CRITERIA_TRANS_REF_MAX);
	    try {
			if (value != null && value.trim().length() > 0) {
				int refNum = Integer.parseInt( value );
				stmt.setInt( idx++, refNum );
			}
		} catch( Exception ex ) {
			throw new BSException( BSException.BSE_INVALID_REFERENCE_NUM );
		}
		
	    return idx;
	}
	
	/**
	* This method will actually add the parameter values for each of the parameters in the prepared statement.
	*/
	private static int addAmountSearchValues( PreparedStatement stmt, int idx, Account account, PagingContext context, HashMap extra) throws Exception {
		
	    String value = "";
		
		if (context == null) {
			return idx;
	    }

	    HashMap pcMap = context.getMap();
	    if (pcMap == null) {
			return idx;
	    }

	    ReportCriteria rc = (ReportCriteria)pcMap.get(BSConstants.PAGING_CONTEXT_REPORT_CRITERIA);
	    if ( rc == null || rc.getSearchCriteria() == null ) {
			return idx;
	    }

	    Properties searchCriteria = rc.getSearchCriteria();
		
		if( searchCriteria == null ) {
			return idx;
	    }
	    
	    // Amount
	    String value1 = searchCriteria.getProperty(GenericBankingRptConsts.SEARCH_CRITERIA_AMOUNT_MIN);
	    String value2 = searchCriteria.getProperty(GenericBankingRptConsts.SEARCH_CRITERIA_AMOUNT_MAX);
	    String value3 = searchCriteria.getProperty(GenericBankingRptConsts.SEARCH_CRITERIA_AMOUNT);
	    if( value1 != null && value1.length() > 0 ) {
	        if( value2 != null && value2.length() > 0 ) {
	            try {
	                Currency amountMin = new Currency(new BigDecimal(value1), null);
	                Currency amountMax = new Currency(new BigDecimal(value2), null);
	                // insert credit min and credit max
	                fillCurrencyColumn(stmt, idx++, amountMin);
	                fillCurrencyColumn(stmt, idx++, amountMax);
	                // negative value of credit min is debit max
	                // negative value of credit max is debit min
	                value1 = '-' + value1;
	                value2 = '-' + value2;
	                amountMin = new Currency(new BigDecimal(value2), null);
	                amountMax = new Currency(new BigDecimal(value1), null);
	                // insert debit min and debit max
	                fillCurrencyColumn(stmt, idx++, amountMin);
	                fillCurrencyColumn(stmt, idx++, amountMax);
	            } catch (Exception ex) {}
	        } else {
	            try {
	                Currency amount = new Currency(new BigDecimal(value1), null);
	                // insert credit min
	                fillCurrencyColumn(stmt, idx++, amount);
	                // negative value of credit min is debit max
	                value1 = '-' + value1;
	                amount = new Currency(new BigDecimal(value1), null);
	                // insert debit max
	                fillCurrencyColumn(stmt, idx++, amount);
	            } catch (Exception ex) {}
	        }
	    } else if( value2 != null && value2.length() > 0 ) {
	        try {
	            Currency amount = new Currency(new BigDecimal(value2), null);
                // insert credit max
                fillCurrencyColumn(stmt, idx++, amount);
                // negative value of credit max is debit min
                value2 = '-' + value2;
                amount = new Currency(new BigDecimal(value2), null);
                // insert debit min
                fillCurrencyColumn(stmt, idx++, amount);
            } catch (Exception ex) {}
	    } else if(value3 != null && value3.length() > 0){
    		try {
    			Currency amountExact = new Currency(new BigDecimal(value3), null);
    			fillCurrencyColumn(stmt, idx++, amountExact);
    			value3 = '-' + value3;
    			amountExact = new Currency(new BigDecimal(value3), null);
    			fillCurrencyColumn(stmt, idx++, amountExact);
    		} catch (Exception ex) {}
    	}
		
	    return idx;
	}
	
	

	
	/**
	* Retrieves the transactions for the specified account and make it available for paging.  
	* The supplied paging context contains the report critera ( to be used as search criteria ). Only
	* transactions that match all specified criteria will be retrieved.
	* @param account a populated Account object that contains the account information
	* @param context the paging context that contains the report criteria that will be used to retrieve a
	* obtain specific transactions.
	* @param extra other criteria that may be used to retrieve specific transactions.
	*/
	public static final void openPagedTransactions( Account account, PagingContext context, HashMap extra, DBConnection conn ) throws BSException {
		Enumeration enum_v = DBTransaction.getTransactions( account, context, extra, conn );
		
		ArrayList transactions = new ArrayList();
		int numberOfElements = 0;
		while( enum_v.hasMoreElements() ) {
			transactions.add( enum_v.nextElement() );
			numberOfElements++;
		}
		
		// Store the transactions ArrayList and a reference to the current location in that
		// ArrayList.  HashMaps aren't synchronized, so we will synch on the list to ensure
		// that there's no concurrent access.
		synchronized( _pagedTransactionList ) {
			_pagedTransactionList.put( account, transactions );
			_pagedTransactionLoc.put( account, new Integer( numberOfElements ) );
			_accountsUsageList.put( account, Boolean.TRUE );
			_pagedTransactionCountList.put( account, new Integer( numberOfElements ) );
		}
		
		
	}
	
	
	/**
	* Retrieves the first page of transactions for the specified account.  
	* The supplied paging context contains the report critera ( to be used as search criteria ). Only
	* transactions that match all specified criteria will be retrieved.
	* @param account a populated Account object that contains the account information
	* @param context the paging context that contains the report criteria that will be used to retrieve a
	* obtain specific transactions.
	* @param extra other criteria that may be used to retrieve specific transactions.
	* @param conn the DBConnection object
	* @throws BSException
	*/
	public static Transactions getPagedTransactions(Account account,PagingContext context, HashMap extra, DBConnection conn) throws BSException {
		ResultSet rs = null;

		try {
			Enumeration trans = null;
		    Transactions transactions = new Transactions();

		  //Find out Upper Boundary & Lower Boundary of transactions fulfilling search criteria, these values will be used by query for pagination.
		    addUpperBoundaryValues( conn, account, context, extra );
		    addLowerBoundaryValues( conn, account, context, extra );

		    // Check to see if we have a boundary.  If not, it is because the boundary query
		    // returned no rows. (and thus, there are no transactions to return.
		    if( !context.getMap().containsKey( PagingContext.UPPER_BOUND +
					    PagingContext.CURSORID ) ) {
		    	int pageSize = MapUtil.getIntValue( extra, BSConstants.PAGE_SIZE, 10 );
		    	context.getMap().put(PagingContext.TOTAL_TRANS, "0");
			    context.getMap().put(PagingContext.PAGE_SIZE, String.valueOf(pageSize));
			return transactions;
		    }

		    // For getNextTransactions and getPreviousTransactions, information is stored
		    // about each page so that each page can be redisplaed easier.  Since we have
		    // begun a new paged transaction request, this information must be removed.
		    context.getMap().remove( BSConstants.PAGING_CONTEXT_CURRENT_PAGE );
		    context.getMap().remove( BSConstants.PAGING_CONTEXT_PAGE_SETTINGS );

		    int currentPage = 1;
		    context.getMap().put( BSConstants.PAGING_CONTEXT_CURRENT_PAGE, new Integer( currentPage ) );

		    // The page settings used to create this page are stored so that getPreviousTransactions()
		    // can refer to them.
		    storePageSettings( context.getMap() );

		    // Now retrieve the data for the page.
		    rs = getTransactionsFromDB( account, context, true, extra, conn );
		    
			trans = createTransactionsForPagination( transactions, rs, account );

		    updatePagingContextBasedOnRetrievedTransactions( context, transactions );

		    return transactions;
		} catch ( BSException e ) {
		    throw e;
		} catch ( Exception ex ) {
		    throw new BSException( BSException.BSE_DB_EXCEPTION, ex.toString() );
		} finally {
		    DBUtil.closeResultSet( rs );
		}
	}
	
	/**
	* Retrieves the next page of transactions for the specified account.  
	* The supplied paging context contains the report critera ( to be used as search criteria ). Only
	* transactions that match all specified criteria will be retrieved.
	* @param account a populated Account object that contains the account information
	* @param context the paging context that contains the report criteria that will be used to retrieve a
	* obtain specific transactions.
	* @param extra other criteria that may be used to retrieve specific transactions.
	* @param conn the DBConnection object
	* @throws BSException
	*/
	public static Transactions getNextTransactions(Account account,	PagingContext context, HashMap extra, DBConnection conn) throws BSException {
			ResultSet rs = null;
	
			try {
				Enumeration trans = null;
				HashMap map = context.getMap();
				Transactions transactions = new Transactions();

			    // Determine which page we are on, and store the values used
			    // to generate the data for this page.
			    int currentPage = MapUtil.getIntValue( map, BSConstants.PAGING_CONTEXT_CURRENT_PAGE, -1 );

			    // If the current page has not been set (because the version of
			    // getPagedTransactions that doesn't take a paging context was called)
			    // we do not want to store the current page information. (for reverse
			    // compatability reasons)
			    if( currentPage != -1 ) {
				currentPage++;
				map.put( BSConstants.PAGING_CONTEXT_CURRENT_PAGE, new Integer( currentPage ) );
		    
				// The page settings used to create this page are stored so that getPreviousTransactions()
				// can refer to them.
				storePageSettings( map );
			    }
			    
	
			    // Now retrieve the data for the page.
			    rs = getTransactionsFromDB( account, context, true, extra, conn );
			    
				trans = createTransactionsForPagination( transactions, rs, account );
	
			    updatePagingContextBasedOnRetrievedTransactions( context, transactions );
			    
			    return transactions;
			} catch ( BSException e ) {
			    throw e;
			} catch ( Exception ex ) {
			    throw new BSException( BSException.BSE_DB_EXCEPTION, ex.toString() );
			} finally {
			    DBUtil.closeResultSet( rs );
			}
	}
	
	/**
	* Retrieves the previous page of transactions for the specified account.  
	* The supplied paging context contains the report critera ( to be used as search criteria ). Only
	* transactions that match all specified criteria will be retrieved.
	* @param account a populated Account object that contains the account information
	* @param context the paging context that contains the report criteria that will be used to retrieve a
	* obtain specific transactions.
	* @param extra other criteria that may be used to retrieve specific transactions.
	* @param conn the DBConnection object
	* @throws BSException
	*/
	public static Transactions getPreviousTransactions(Account account,	PagingContext context, HashMap extra, DBConnection conn) throws BSException {
			ResultSet rs = null;
	
			try {
				Enumeration trans = null;
				HashMap map = context.getMap();
			    Transactions transactions = new Transactions();
			    ReportCriteria criteria = (ReportCriteria)map.get( BSConstants.PAGING_CONTEXT_REPORT_CRITERIA );
	
			    // Determine which page we are on, and retrieve the values used
			    // to generate the data for this page from when this page was last displayed.
			    int currentPage = MapUtil.getIntValue( map, BSConstants.PAGING_CONTEXT_CURRENT_PAGE, -1 );

			    // If there is no current page information, then there is no information in
			    // the paging context to retrieve
			    if( currentPage != -1 ) {
				currentPage--;
				map.put( BSConstants.PAGING_CONTEXT_CURRENT_PAGE, new Integer( currentPage ) );
		    
				// Retrieve the list of settings for this page
				IntMap pageSettings = (IntMap)map.get( BSConstants.PAGING_CONTEXT_PAGE_SETTINGS );

				HashMap curPageSettings = (HashMap)pageSettings.get( currentPage );

				if ( criteria != null ) {
				    for( int i = 0; i < criteria.getSortCriteria().size(); ++i ) {
					ReportSortCriterion c = (ReportSortCriterion) criteria.getSortCriteria().get( i );

					map.put( PagingContext.SORT_VALUE_MIN + c.getName(),
						curPageSettings.get( PagingContext.SORT_VALUE_MIN + c.getName() ) );
					map.put( PagingContext.SORT_VALUE_MAX + c.getName(),
						curPageSettings.get( PagingContext.SORT_VALUE_MAX + c.getName() ) );
				    }
				}
				map.put( PagingContext.SORT_VALUE_MIN + GenericBankingRptConsts.SORT_CRITERIA_TRANS_INDEX,
					  curPageSettings.get( PagingContext.SORT_VALUE_MIN + PagingContext.CURSORID ) );
				map.put( PagingContext.SORT_VALUE_MAX + GenericBankingRptConsts.SORT_CRITERIA_TRANS_INDEX,
					  curPageSettings.get( PagingContext.SORT_VALUE_MAX + PagingContext.CURSORID ) );
			    }
	
			    // Now retrieve the data for the page.
			    if( currentPage == -1 ) {
				// To maintain reverse compatability, if we called in through the older
				// getPagedTransactions (which doesn't set CURRENT_PAGE) then
				// we want to move backwards through the data...
				rs = getTransactionsFromDB( account, context, false, extra, conn );
			    } else {
				// However, the multi-query API always moves forward through the data.
				rs = getTransactionsFromDB( account, context, true, extra, conn );
			    }
			    
				trans = createTransactionsForPagination( transactions, rs, account );
	
			    updatePagingContextBasedOnRetrievedTransactions( context, transactions );
	
			    return transactions;
			} catch ( BSException e ) {
			    throw e;
			} catch ( Exception ex ) {
			    throw new BSException( BSException.BSE_DB_EXCEPTION, ex.toString() );
			} finally {
			    DBUtil.closeResultSet( rs );
			}
	}
	
	/**
	* Retrieves the specified page of transactions for the specified account.  
	* The supplied paging context contains the report critera ( to be used as search criteria ). Only
	* transactions that match all specified criteria will be retrieved.
	* @param account a populated Account object that contains the account information
	* @param context the paging context that contains the report criteria that will be used to retrieve a
	* obtain specific transactions.
    * @param moveForward direction to page
	* @param extra other criteria that may be used to retrieve specific transactions.
	* @param conn the DBConnection object
	* @throws BSException
	*/
	 private static ResultSet getTransactionsFromDB( Account account,PagingContext context,boolean moveForward,HashMap extra,DBConnection conn ) throws Exception
		{
				ReportCriteria criteria = (ReportCriteria) context.getMap().get( BSConstants.PAGING_CONTEXT_REPORT_CRITERIA );
					
				criteria.getSearchCriteria().put( BSConstants.PAGING_CONTEXT_SEARCH_CRITERIA_ACCOUNT, account.getID() );
				 
				int pageSize = MapUtil.getIntValue( extra, BSConstants.PAGE_SIZE, 10 );
				
				ResultSet rs = null;
				ResultSet rs2 = null;
				PreparedStatement stmt = null;
				PreparedStatement stmt2 = null;
				int num = 0;
				try {
				
					StringBuffer stmtBuffer = new StringBuffer( SQL_GET_PAGED_TRANSACTIONS_NEW );
					addSearchCriteria( stmtBuffer, account, context, extra );
					addAmountSearchCriteria( stmtBuffer, account, context, extra );
					ArrayList values = addSortCriteria ( stmtBuffer, context, moveForward, extra );
					addOrderByClause( stmtBuffer, context, moveForward, null, extra );
					stmt = conn.prepareStatement( conn, stmtBuffer.toString() );
					int idx = 1;
					idx = addSearchValues(stmt, idx, account,context, extra);
					idx = addAmountSearchValues(stmt, idx, account,context, extra);
					addSearchValuesForSort( stmt, idx, values, extra );
					if ( pageSize != -1 ) {
						stmt.setMaxRows( pageSize );
					}
					rs = DBUtil.executeQuery( stmt, stmtBuffer.toString() );
					
					int currentPage = (Integer)context.getMap().get( BSConstants.PAGING_CONTEXT_CURRENT_PAGE);//Fetch number of total records only at once
					if(currentPage==1){
						StringBuffer stmtBuffer2 = new StringBuffer( SQL_GET_TRANSACTIONS_NUM );
						addSearchCriteria( stmtBuffer2, account, context, extra );
						addAmountSearchCriteria( stmtBuffer2, account, context, extra );
						ArrayList values2 = addSortCriteria ( stmtBuffer2, context, moveForward, extra );
						stmt2 = conn.prepareStatement( conn, stmtBuffer2.toString() );
						int idx2 = 1;
						idx2 = addSearchValues(stmt2, idx2,account, context, extra);
						idx2 = addAmountSearchValues(stmt2, idx2,account, context, extra);
						addSearchValuesForSort( stmt2, idx2, values2, extra );
						rs2 = DBUtil.executeQuery( stmt2, stmtBuffer2.toString() );
						
						if(rs2.next())
						num = rs2.getInt(1);
					
						context.getMap().put(PagingContext.TOTAL_TRANS, String.valueOf(num));
						context.getMap().put(PagingContext.PAGE_SIZE, String.valueOf(pageSize));	
					}
				} finally {
					stmt = null;
					stmt2 = null;
				}
				
				return rs;
		}
	
	 
	 
	    /**
		Determine the minimum and maximum bounds for the sort criteria from the transaction collection and set these values into the paging context.
		@param context the paging context to be updated with the new information from the transactions retrieved
		@param trans the transactions retrieved from the paging call
	    */
	    private static void updatePagingContextBasedOnRetrievedTransactions( PagingContext context, Transactions trans ) {
		if( trans == null || trans.size() == 0 ) {
		    return;
		}

		HashMap map = context.getMap();
		if( map == null ) {
		    map = new HashMap();
		    context.setMap( map );
		}

		ReportCriteria criteria = (ReportCriteria)map.get( BSConstants.PAGING_CONTEXT_REPORT_CRITERIA );
		if ( criteria == null ) return;
		
		boolean bAsc = false;
		for( int i = 0; i < criteria.getSortCriteria().size(); ++i ) {
		    ReportSortCriterion c = (ReportSortCriterion) criteria.getSortCriteria().get( i );
		    Object value1 = getValueForSortCriterion( c, trans.get( 0 ) );
		    Object value2 = getValueForSortCriterion( c, trans.get( trans.size() - 1 ) );

		    if( c.getAsc() ) {
			map.put( PagingContext.SORT_VALUE_MIN + c.getName(), value1 );
			map.put( PagingContext.SORT_VALUE_MAX + c.getName(), value2 );
		    } else {
			map.put( PagingContext.SORT_VALUE_MIN + c.getName(), value2 );
			map.put( PagingContext.SORT_VALUE_MAX + c.getName(), value1 );
		    }

		    bAsc = c.getAsc();
		}


		Object value1 = getValueForSortCriterion( new ReportSortCriterion( 1, GenericBankingRptConsts.SORT_CRITERIA_TRANS_INDEX, true ), trans.get( 0 ) );
		Object value2 = getValueForSortCriterion( new ReportSortCriterion( 1, GenericBankingRptConsts.SORT_CRITERIA_TRANS_INDEX, true ), trans.get( trans.size() - 1 ) );

		if( bAsc ) {
		    map.put( PagingContext.SORT_VALUE_MIN + GenericBankingRptConsts.SORT_CRITERIA_TRANS_INDEX, value1 );
		    map.put( PagingContext.SORT_VALUE_MAX + GenericBankingRptConsts.SORT_CRITERIA_TRANS_INDEX, value2 );
		} else {
		    map.put( PagingContext.SORT_VALUE_MIN + GenericBankingRptConsts.SORT_CRITERIA_TRANS_INDEX, value2 );
		    map.put( PagingContext.SORT_VALUE_MAX + GenericBankingRptConsts.SORT_CRITERIA_TRANS_INDEX, value1 );
		}
	    }
	 
	    
	    
	    /**
	       Retrieve the information from a transaction object corresponding to the specified sort criterion 
	       @param crit the sort criteria for which data should be retrieved
	       @param data the transaction object from which the data should be retrieved
	     */
	    private static Object getValueForSortCriterion( ReportSortCriterion crit, Object data ) {
		Transaction tran = (Transaction) data;
		if( crit.getName().equals( GenericBankingRptConsts.SORT_CRITERIA_PROCESS_DATE ) || 
	                crit.getName().equals(GenericBankingRptConsts.SORT_CRITERIA_DATE ) ) {
		    return tran.getRODateValue();
		} else if( crit.getName().equals( GenericBankingRptConsts.SORT_CRITERIA_AMOUNT ) ) {
		    return tran.getAmountValue();
		} else if( crit.getName().equals( GenericBankingRptConsts.SORT_CRITERIA_TRANS_INDEX ) ) {
		    return new Long( tran.getTransactionIndex() );
		} else if( crit.getName().equals( GenericBankingRptConsts.SORT_CRITERIA_TRANS_TYPE ) ) {
		    return tran.getType();
		} else if( crit.getName().equals( GenericBankingRptConsts.SORT_CRITERIA_TRANS_REF_NUM ) ) {
		    return tran.getReferenceNumber();
		} else if( crit.getName().equals( GenericBankingRptConsts.SORT_CRITERIA_BANK_REF_NUM ) ) {
		    return tran.getBankReferenceNumber();
		} else if( crit.getName().equals( GenericBankingRptConsts.SORT_CRITERIA_CUSTOMER_REF_NUM ) ) {
		    return tran.getCustomerReferenceNumber();
		} else if( crit.getName().equals( GenericBankingRptConsts.SORT_CRITERIA_RUNNING_BALANCE ) ) {
		    return tran.getRunningBalanceValue();
		} else if( crit.getName().equals( GenericBankingRptConsts.SORT_CRITERIA_DESCRIPTION ) ) {
		    return tran.getDescription();
		} else if( crit.getName().equals( GenericBankingRptConsts.SORT_CRITERIA_MEMO ) ) {
		    return tran.getMemo();
		} else if( crit.getName().equals( GenericBankingRptConsts.SORT_CRITERIA_DUE_DATE ) ) {
		    return tran.getRODueDate();
		}
		return null;
	    }
	    
	 
	    /**  Fill the parametrized statement with the appropriate values based on the information retrieved from the paging context
		 @param stmt the parametrized statement to be filled with the appropriate values
		 @param idx the current index to start filling information into
		 @param values a search values to be added to the prepared statement
		 @param extra used for future expansion of the API
		 @return the next index that is available (if it exists) to place information into the prepared statement
	    */
		private static int addSearchValuesForSort( PreparedStatement stmt, int idx, 
							   ArrayList values, HashMap extra ) throws Exception
		{
		    for( int i = 0; i < values.size(); ++i ) {
			fillValue( stmt, idx++, values.get( i ) );
		    }

		    return idx;
		}
	 
	 
		
	 
	 private static void addOrderByClause( StringBuffer sql, PagingContext context, boolean bForward, Boolean asc, HashMap extra ) throws Exception
		{
		    ReportCriteria criteria = (ReportCriteria) context.getMap().get( BSConstants.PAGING_CONTEXT_REPORT_CRITERIA );
		    ReportSortCriteria sortCriteria = criteria.getSortCriteria();

		    sql.append( " ORDER BY " );
		    boolean bAsc = ( asc != null ) ? asc.booleanValue() : ( bForward ? true : false );
		    for( int i = 0; i < sortCriteria.size(); ++i ) {
				if( i != 0 ) {
					sql.append( ", " );
				}
			    ReportSortCriterion crit = (ReportSortCriterion) sortCriteria.get( i );
			    sql.append( getColumnNameForPagination( crit.getName() ) );
				bAsc = ( asc == null ) ?
					( bForward ? crit.getAsc() : !crit.getAsc() ) :
					asc.booleanValue();
			    sql.append( bAsc ? " ASC" : " DESC" );
		    }
		    if( sortCriteria.size() != 0 ) {
			sql.append( "," );
		    }
		    sql.append( " TransactionID " ).append( (bAsc) ? "ASC" : "DESC" );
		}
	 
	 
		/**
		 * Add sort criteria to the order by clause.
		 * @param sql sql string
		 * @param context PagingContext containing sort criteria
		 * @param extra HashMap
		 * @return an ArrayList containing the values to populate this portion of the query with.
		 */
		private static ArrayList addSortCriteria ( StringBuffer sql, PagingContext context, boolean bForward, HashMap extra ) throws Exception
		{
			ReportCriteria criteria = (ReportCriteria) context.getMap().get( BSConstants.PAGING_CONTEXT_REPORT_CRITERIA );
			ReportSortCriteria sortCriteria = criteria.getSortCriteria();

			ArrayList values = new ArrayList();

			// The first page is a special case -- so we won't have any transaction index
			// information for it.
			int currentPage = MapUtil.getIntValue( context.getMap(), BSConstants.PAGING_CONTEXT_CURRENT_PAGE, -1 );
			if( currentPage == 1 ) return values;

			boolean bAsc = true;

			// the sort criteria should be formatted in the following way: if there are three sort criteria A, B, and C:
			// A > ? OR ( A = ? AND B > ? ) OR ( A = ? AND B = ? AND C > ? ) 
			// OR ( A = ? AND B =? AND C = ? AND TransactionIndex > ? )

			// NOTE:  Any modification to the way this sort query is built up must also be reflected in the method
			// above which fills in the parametrized values from this statement.

			ArrayList orClauses = new ArrayList();

			StringBuffer orClause = null;

			// Store the values that need to be replaced when the or clause
			// is populated.
			ArrayList orClauseValues = new ArrayList();
			
			String transIndexKeyName = null;
			
			// Determine whether the transaction index should be sorted ascending or descending.  The
			// transaction index will be sorted in the same way as the last sort criteria or ascending
			// if there is no sort criteria
			if ( !sortCriteria.isEmpty() ) {
				bAsc = ((ReportSortCriterion) sortCriteria.get ( sortCriteria.size() - 1 )).getAsc();
			} else {
				bAsc = true;
			}
			
			if( bForward ) {
				if( bAsc ) {
					transIndexKeyName = PagingContext.SORT_VALUE_MAX + 
							PagingContext.CURSORID;
				} else {
					transIndexKeyName = PagingContext.SORT_VALUE_MIN + 
							PagingContext.CURSORID;
				}
			} else {
				if( bAsc ) {
					transIndexKeyName = PagingContext.SORT_VALUE_MIN + 
							PagingContext.CURSORID;
				} else {
					transIndexKeyName = PagingContext.SORT_VALUE_MAX + 
							PagingContext.CURSORID;
				}
			}
			
			// Add the transaction index into the sort criteria.
			if ( context.getMap().containsKey( transIndexKeyName ) ) {
				sortCriteria.create( sortCriteria.size() + 1, PagingContext.CURSORID, bAsc );
			}
			
			for( int i = 0; i < sortCriteria.size(); ++i ) {
				ReportSortCriterion crit = (ReportSortCriterion) sortCriteria.get( i );
				boolean isAseMssql = ( BSAdapter.CONNECTIONTYPE != null ) && 
				                (( BSAdapter.CONNECTIONTYPE.indexOf( ConnectionDefines.DB_SYBASE_ASE ) != -1 )
				                   || BSAdapter.CONNECTIONTYPE.equalsIgnoreCase( ConnectionDefines.DB_MS_SQLSERVER ));
				
				boolean nullsComeFirst = ( isAseMssql  && crit.getAsc() ) || ( !isAseMssql  && !crit.getAsc() );
				String key = null;
				String sign = null;

				bAsc = crit.getAsc();
				
				// Determine the sign to be used when adding the criterion to the query.
				if( bForward ) {
					if( bAsc ) {
						key = PagingContext.SORT_VALUE_MAX + crit.getName();
						sign = " > ";
					} else {
						key = PagingContext.SORT_VALUE_MIN + crit.getName();
						sign = " < ";
					}
				} else {
					if( bAsc ) {
						key = PagingContext.SORT_VALUE_MIN + crit.getName();
						sign = " < ";
					} else {
						key = PagingContext.SORT_VALUE_MAX + crit.getName();
						sign = " > ";
					}
				}

				if( context.getMap().containsKey( key ) ) {
					String colName;
					
					if ( PagingContext.CURSORID.equals( crit.getName() ) ) {
						colName = "TransactionID";
					} else {
						colName = getColumnNameForPagination( crit.getName() );
					}
					
					Object critValue = context.getMap().get( key );
					if( critValue != null ) {

						if ( orClause == null ) {
							orClause = new StringBuffer();
						}
						
						// This is going to add the ( A = ? AND B > ? ) section, and the ( A = ? AND B = ? AND C > ? )
						// sections from our example.
						StringBuffer newClause = new StringBuffer( orClause.toString() );

						if( newClause.length() != 0 ) {
							newClause.append( " AND " );
						}

						if( !nullsComeFirst ) { 
							// Null values come last so if the last transaction read in for this 
							// criterion is non-null, then we have not read in the null values yet.
							newClause.append( "( " ).append( colName ).append( sign ).append( "? " );
							newClause.append( "OR " ).append( colName ).append( " is NULL ) " );
						} else {
							// Null values come first so if the last transaction read in for this 
							// criterion is non-null, then we have already read in the null values.
							newClause.append( colName ).append( sign ).append( "? " );
						}

						values.addAll( orClauseValues );
						values.add( critValue );

						// Add the criteria to the orClause
						if( orClause.length() != 0 ) {
							orClause.append( " AND " );
						}

						if( !nullsComeFirst ) { 
							orClause.append( "( " ).append( colName ).append( " = ? " );
							orClause.append( "OR " ).append( colName ).append( " is NULL ) " );
						} else {
							orClause.append( colName ).append( " = ? " );
						}

						orClauseValues.add( critValue );
						orClauses.add( newClause );

					} else { // if the last value read in for this criterion was null
						if( orClause == null ) {
							orClause = new StringBuffer();
						} else if ( orClause.length() != 0 ) {
							orClause.append( " AND " );
						}
						
						if( !nullsComeFirst ) {
							// Null values come last so if the last transaction read in for this 
							// criterion is null, then we have read in some (or all) of the null values.
							orClause.append( colName ).append( " is NULL " );
						} else {
							// Null values come first so if the last transaction read in for this 
							// criterion is null, then we have already read in some (or all) the null
							// however, there still may be non-null values so we have to add
							// colName is NOT NULL to the query.
							StringBuffer newClause = new StringBuffer ( orClause.toString() );
							orClause.append( colName ).append ( " is NULL " );
							
							newClause.append( colName ).append ( " is NOT NULL" );
							orClauses.add( newClause );
							
							values.addAll( orClauseValues );
						}
					}
				}
			}
			
			if ( context.getMap().containsKey( transIndexKeyName ) ) {
				sql.append( " AND (" );
				sql.append( (StringBuffer) orClauses.get ( 0 ) );

				// Add each of the or clauses to the statement.
				for ( int orCount = 1; orCount < orClauses.size(); orCount++ ) {
					sql.append( " OR (" ).append( (StringBuffer)orClauses.get( orCount ) ).append( " ) " );
				}

				sql.append( " ) " );
				
				// Remove the transaction index from the sort criteria.
				sortCriteria.remove ( sortCriteria.size() - 1 );
			}

			return values;
		}	 
	 
	
    private static void storePageSettings( HashMap map )
    {
		// Retrieve the list of settings for each page
		IntMap pageSettings = (IntMap)map.get( BSConstants.PAGING_CONTEXT_PAGE_SETTINGS );
		if( pageSettings == null ) {
		    pageSettings = new IntMap();
		    map.put( BSConstants.PAGING_CONTEXT_PAGE_SETTINGS, pageSettings );
			}
		
			int currentPage = MapUtil.getIntValue( map, BSConstants.PAGING_CONTEXT_CURRENT_PAGE, 1 );
		
			// Determine if we have already saved settings for this page.
			// If not, then add them.
			if( pageSettings.get( currentPage ) == null ) {
			    // Store the information used to create this page.
			    HashMap curPageSettings = new HashMap();
		
			    curPageSettings.putAll( map );
		
			    pageSettings.put( currentPage, curPageSettings );
			}
    }
	
  
    // Calculate the Upper boundary for Paged Transactions fulfilling search criteria
    private static void addUpperBoundaryValues( DBConnection connection, Account account, PagingContext context, HashMap extra ) throws Exception
    {
			ReportCriteria criteria = (ReportCriteria) context.getMap().get( BSConstants.PAGING_CONTEXT_REPORT_CRITERIA );
			ReportSortCriteria sortCriteria = criteria.getSortCriteria();
		
			criteria.getSearchCriteria().put( BSConstants.PAGING_CONTEXT_SEARCH_CRITERIA_ACCOUNT, account.getID() );
		
			if ( account.getRoutingNum() != null ) {
			    criteria.getSearchCriteria().put( BSConstants.PAGING_CONTEXT_SEARCH_CRITERIA_ROUT_NUM, account.getRoutingNum() );
			}
		
			if( !criteria.getSearchCriteria().containsKey( BSConstants.PAGING_CONTEXT_SEARCH_CRITERIA_DATA_CLASSIFICATION ) ) {
			    String dataClassification = MapUtil.getStringValue( extra, BSConstants.DATA_CLASSIFICATION, BSConstants.DATA_CLASSIFICATION_PREVIOUSDAY );
			    criteria.getSearchCriteria().put( BSConstants.PAGING_CONTEXT_SEARCH_CRITERIA_DATA_CLASSIFICATION, dataClassification );
			}
			
			ResultSet rs = null;
			PreparedStatement stmt = null;	
		
			try {
			    StringBuffer sql = new StringBuffer( "SELECT " );
			    addSortColumns( sql, context, extra );
			    sql.append( " FROM BS_Transactions b, BS_Account a, BS_TransactionType c  " );
			    sql.append( " WHERE a.AccountID = b.AccountID AND c.TransactionTypeID = b.TransactionTypeID " );
			    
			    addSearchCriteria( sql, account, context, extra );
			    addAmountSearchCriteria( sql, account, context, extra );
			    addOrderByClause( sql, context, true, new Boolean(false), extra );
			    
			    stmt = connection.prepareStatement( connection, sql.toString() );
			    
	            int idx = 1;
			    idx = addSearchValues(stmt, idx, account,context, extra);
			    idx = addAmountSearchValues(stmt, idx, account,context, extra);
			    stmt.setMaxRows( 1 );
			    rs = DBUtil.executeQuery( stmt, sql.toString() );
			    if( rs.next() ) {
				for( int i = 0; i < sortCriteria.size(); ++i ) {
				    ReportSortCriterion crit = (ReportSortCriterion) sortCriteria.get( i );
				    context.getMap().put( PagingContext.UPPER_BOUND + crit.getName(), getValue( rs, i + 1, account.getCurrencyCode(), criteria.getLocale() ) );
				}
				context.getMap().put( PagingContext.UPPER_BOUND + PagingContext.CURSORID, getValue( rs,  sortCriteria.size() + 1, account.getCurrencyCode(), criteria.getLocale() ) );
			    }
			} catch ( SQLException e ) {
				throw new BSException( BSException.BSE_DB_EXCEPTION, "Failed to add sort-by columns for transaction retrieval.", e );
			} catch( Exception ex ) {
				throw new BSException( BSException.BSE_DB_EXCEPTION, ex.toString() );
			} finally {
			    DBUtil.closeResultSet( rs );
			    stmt = null;
			}
	
    }
    

    /**
        if the object is null, then set the indicated column to null, otherwise set the column
	to the value of the object. The type of the data set in the statement depends on the type of
	of the object.
	@param rs result set
	@param column column in statement to fill
	@param currencyCode ISO currency code
    */
    public static Object getValue( ResultSet rs, int column, String currencyCode, Locale locale ) throws Exception
    {
	ResultSetMetaData rsmd = rs.getMetaData();
	switch( rsmd.getColumnType( column ) ) {
	    case java.sql.Types.BIGINT:
	    case java.sql.Types.TINYINT:
	    case java.sql.Types.SMALLINT:
	    case java.sql.Types.NUMERIC:
	    return new Long( rs.getLong( column ) );
	    case java.sql.Types.INTEGER:
	    return new Integer( rs.getInt( column ) );
	    case java.sql.Types.TIMESTAMP:
	    return getTimestampColumn( rs.getTimestamp( column ), locale );
	    case java.sql.Types.DECIMAL:
	    return getCurrencyColumn( rs.getBigDecimal( column ), currencyCode, locale );
	    case java.sql.Types.VARCHAR:
	    return rs.getString( column );
	}
	return null;
    }
    
    
	    /**
	    create a Currency object with the BigDecimal amount if it is not null, otherwise return null
	@param amount amount to create a Currency object with
	@param currency the currency code to use
	@param locale Locale
	@return Currency object using amount and locale value
	*/
	public static Currency getCurrencyColumn( BigDecimal amount, String currency, Locale locale  )
	{
		if( amount == null ) {
		    return null;
		} else {
		    return ( new Currency( amount, currency, locale ) );
		}
	}
    
	    /**
		create a DateTime object with the Timestamp date if it is not null, otherwise return null
	@param date date to create a DateTime object with
	@param locale Locale
	@return DateTime object using date value
	*/
	public static DateTime getTimestampColumn( Timestamp date, Locale locale )
	{
		if( date == null ) {
			return null;
		} else {
			return ( new DateTime( date, locale ) );
		}
	}
    
    
    private static void addSortColumns( StringBuffer sql, PagingContext context, HashMap extra ) throws Exception
    {
		ReportCriteria criteria = (ReportCriteria) context.getMap().get( BSConstants.PAGING_CONTEXT_REPORT_CRITERIA );
		ReportSortCriteria sortCriteria = criteria.getSortCriteria();
	
		for( int i = 0; i < sortCriteria.size(); ++i ) {
		    ReportSortCriterion crit = (ReportSortCriterion) sortCriteria.get( i );
		    sql.append( getColumnNameForPagination( crit.getName() ) ).append( ", " );
		}
		sql.append( " TransactionID " );
    }
    
    

 // Calculate the Lower boundary for Paged Transactions fulfilling search criteria
   private static void addLowerBoundaryValues( DBConnection connection, Account account, PagingContext context, HashMap extra ) throws Exception
    {
			ReportCriteria criteria = (ReportCriteria) context.getMap().get( BSConstants.PAGING_CONTEXT_REPORT_CRITERIA );
			ReportSortCriteria sortCriteria = criteria.getSortCriteria();
		
			criteria.getSearchCriteria().put( BSConstants.PAGING_CONTEXT_SEARCH_CRITERIA_ACCOUNT, account.getID() );
			if ( account.getRoutingNum() != null ) {
			    criteria.getSearchCriteria().put( BSConstants.PAGING_CONTEXT_SEARCH_CRITERIA_ROUT_NUM, account.getRoutingNum() );
			}
			if( !criteria.getSearchCriteria().containsKey( BSConstants.PAGING_CONTEXT_SEARCH_CRITERIA_DATA_CLASSIFICATION ) ) {
			    String dataClassification = MapUtil.getStringValue( extra, BSConstants.DATA_CLASSIFICATION, BSConstants.DATA_CLASSIFICATION_PREVIOUSDAY );
			    criteria.getSearchCriteria().put( BSConstants.PAGING_CONTEXT_SEARCH_CRITERIA_DATA_CLASSIFICATION, dataClassification );
			}
		
			ResultSet rs = null;
			PreparedStatement stmt = null;	
		
			try {
			    StringBuffer sql = new StringBuffer( "SELECT " );
			    addSortColumns( sql, context, extra );
			    sql.append( " FROM BS_Transactions b, BS_Account a, BS_TransactionType c  " );
			    sql.append( " WHERE a.AccountID = b.AccountID AND c.TransactionTypeID = b.TransactionTypeID " );
			    addSearchCriteria( sql, account, context, extra );
			    addAmountSearchCriteria( sql, account, context, extra );
			    addOrderByClause( sql, context, true, new Boolean(true), extra );
			    
			    stmt = connection.prepareStatement( connection, sql.toString() );
			    
		        int idx = 1;
			    idx = addSearchValues(stmt, idx,account, context, extra);
			    idx = addAmountSearchValues(stmt, idx,account, context, extra);
			    stmt.setMaxRows( 1 );
			    rs = DBUtil.executeQuery( stmt, sql.toString() );
			    if( rs.next() ) {
				for( int i = 0; i < sortCriteria.size(); ++i ) {
				    ReportSortCriterion crit = (ReportSortCriterion) sortCriteria.get( i );
				    context.getMap().put( PagingContext.LOWER_BOUND + crit.getName(), getValue( rs, i + 1, account.getCurrencyCode(), criteria.getLocale() ) );
				}
				context.getMap().put( PagingContext.LOWER_BOUND + PagingContext.CURSORID, getValue( rs,  sortCriteria.size() + 1, account.getCurrencyCode(), criteria.getLocale() ) );
			    }
			} catch( Exception ex ) {
				throw new BSException( BSException.BSE_DB_EXCEPTION, ex.toString() );
			} finally {
			    DBUtil.closeResultSet( rs );
			    stmt = null;
			}
	
    }
	
    /**
    * openPagedTransactions - get the transactions under the specified account within a
    * specified time period and
    * @param account a populated Account object that contains the account information
    * @param startDate a Calendar object that specifies the beginning of the time period
    * @param endDate a Calendar object that specifies the end of the time period
    * @param conn DBConnection object that used to connect to the BankSim database
    * @exception BSException that wraps around SQL Exception thrown by other methods
    */
    public static final void openPagedTransactions( Account account, Calendar startDate, Calendar endDate, DBConnection conn ) throws BSException
    {
		PagingContext context = new PagingContext( startDate, endDate );
		ReportCriteria criteria = new ReportCriteria();
		HashMap newMap = new HashMap();
		context.setMap( newMap );
		context.getMap().put( BSConstants.PAGING_CONTEXT_REPORT_CRITERIA, criteria );
		Format formatter = com.ffusion.util.DateFormatUtil.getFormatter( DateConsts.REPORT_CRITERIA_INTERNAL_FORMAT_DATETIME_STR_WITH_SEC );
		if ( startDate != null ) {
			criteria.getSearchCriteria().setProperty( DateConsts.SEARCH_CRITERIA_START_DATE, formatter.format( startDate.getTime() ) );
		}
		if ( endDate != null ) {
			criteria.getSearchCriteria().setProperty( DateConsts.SEARCH_CRITERIA_END_DATE, formatter.format( endDate.getTime() ) );
		}
		HashMap extra = new HashMap();
		
		openPagedTransactions( account, context, extra, conn );
    } 

    /**
    * closePagedTransactions - informs BankSim to no longer hold onto the paged info
    * for the specified account
    * @param account a populated Account object that contains account information
    * @exception BSException that states the banksim is not initialized or that the account does not have any paged transactions
    */
    public static void closePagedTransactions( Account account ) throws BSException
    {
	synchronized( _pagedTransactionList ) {
	    // Assume that if the account is on one HashMap it's in the other.
	    if( !_pagedTransactionList.containsKey( account ) ) {
		throw new BSException( BSException.BSE_ACCOUNT_NOT_PAGED,
			   MessageText.getMessage( IBSErrConstants.ERR_ACCOUNT_NOT_PAGED ) );
	    }

	    _pagedTransactionList.remove( account );
	    _pagedTransactionLoc.remove( account );
	    _pagedTransactionCountList.remove( account );
	    _accountsUsageList.remove( account );
	}
    }

    /**
     * getNumberOfTransactions - retrieve the number of transactions for a given account
     *
     * @param account a populated Account object that's already been passed to openPagedTransactions
     * @return the number of transactions
     */

    public static int getNumberOfTransactions( Account account ) throws BSException {
	if( !_pagedTransactionCountList.containsKey( account ) ) {
	    throw new BSException( BSException.BSE_ACCOUNT_NOT_PAGED,
				   MessageText.getMessage( IBSErrConstants.ERR_ACCOUNT_NOT_PAGED ) );
	}

	Integer count = (Integer)_pagedTransactionCountList.get( account );
	return count.intValue();
    }

    /**
    * getNextPage - retrieve the next page of transaction information for an account
    * @param account a populated Account object that's already been passed to openPagedTransactions
    * @param howMany the number of transaction elements to return
    * @return Enumeration of Transaction objects
    * @exception BSException that states the banksim is not initialized or that the account does not have any paged transactions
    */
    public static Enumeration getNextPage( Account account, int howMany ) throws BSException
    {
	return getNextPage( account, howMany, -1 );
    }

    /**
    * getNextPage - retrieve the next page of transaction information for an account
    * @param account a populated Account object that's already been passed to openPagedTransactions
    * @param howMany the number of transaction elements to return
    * @param nextIndex the index of the next transaction to retrieve
                       if the value is -1, retrieves a page determined from previous page requests
    * @return Enumeration of Transaction objects
    * @exception BSException that states the banksim is not initialized or that the account does not have any paged transactions
    */
    public static Enumeration getNextPage( Account account, int howMany, int nextIndex ) throws BSException
    {
	// Vector containing transactions to return
        Vector toReturn = new Vector();

	synchronized( _pagedTransactionList ) {
	    // Assume that if the account is on one HashMap it's in the other.
	    if( !_pagedTransactionList.containsKey( account ) ) {
		throw new BSException( BSException.BSE_ACCOUNT_NOT_PAGED,
			   MessageText.getMessage( IBSErrConstants.ERR_ACCOUNT_NOT_PAGED ) );
	    }

	    int startPos = nextIndex == -1 ? ((Integer)_pagedTransactionLoc.get( account )).intValue()
	                                   : nextIndex;
	    int curPos = startPos;
	    ArrayList list = (ArrayList)_pagedTransactionList.get( account );

	    // Add elements to the return Vector.  Make sure we don't return more than
	    // howMany and make sure we don't overrun the transaction list.
	    for( ; curPos < startPos + howMany && curPos >= 0 && curPos < list.size(); curPos++ ) {
		toReturn.add( list.get( curPos ) );
	    }

	    // Update the position
	    _pagedTransactionLoc.put( account, new Integer( curPos ) );

	    // set that this account was used
	    _accountsUsageList.put( account, Boolean.TRUE );
	}

	return toReturn.elements();
    }

    /**
    * getPrevPage - retrieve the previous page of transaction information for an account
    * @param account a populated Account object that's already been passed to openPagedTransactions
    * @param howMany the number of transaction elements to return
    * @return Enumeration of Transaction objects
    * @exception BSException that states the banksim is not initialized or that the account does not have any paged transactions
    */
    public static Enumeration getPrevPage( Account account, int howMany ) throws BSException
    {
	return getPrevPage( account, howMany, -1 );
    }

    /**
    * getPrevPage - retrieve the previous page of transaction information for an account
    * @param account a populated Account object that's already been passed to openPagedTransactions
    * @param howMany the number of transaction elements to return
    * @param prevIndex the transaction index of the last transaction to retrieve
                       if the value is -1, retrieves a page determined from previous page requests
    * @return Enumeration of Transaction objects
    * @exception BSException that states the banksim is not initialized or that the account does not have any paged transactions
    */
    public static Enumeration getPrevPage( Account account, int howMany, int prevIndex ) throws BSException
    {
	// Vector containing transactions to return
        Vector toReturn = new Vector();

	synchronized( _pagedTransactionList ) {
	    // Assume that if the account is on one HashMap it's in the other.
	    if( !_pagedTransactionList.containsKey( account ) ) {
		throw new BSException( BSException.BSE_ACCOUNT_NOT_PAGED,
			   MessageText.getMessage( IBSErrConstants.ERR_ACCOUNT_NOT_PAGED ) );
	    }

	    int startPos = prevIndex == -1 ? ((Integer)_pagedTransactionLoc.get( account )).intValue()
	                                   : prevIndex;
	    int curPos = startPos - 1;
	    ArrayList list = (ArrayList)_pagedTransactionList.get( account );

	    // Add elements to the return Vector.  Make sure we don't return more than
	    // howMany and make sure we don't overrun the transaction list.
	    for( ; curPos > startPos - howMany - 1 && curPos >= 0 && curPos < list.size(); curPos-- ) {
		toReturn.add( list.get( curPos ) );
	    }

	    // Update the position
	    _pagedTransactionLoc.put( account, new Integer( curPos + 1 ) );

	    // set that this account was used
	    _accountsUsageList.put( account, Boolean.TRUE );
	}

	return toReturn.elements();
    }

    /**
    * closeUnusedPagedTransactions - close all accounts that have not been accessed
    * 				     since the last call to closeUnusedPagedTransactions
    */
    public static void closeUnusedPagedTransactions()
    {
	synchronized( _pagedTransactionList ) {
	    Iterator pagedAccounts = _accountsUsageList.keySet().iterator();

	    while( pagedAccounts.hasNext() ) {
		Account account = (Account)pagedAccounts.next();
		Boolean wasUsedRecently = (Boolean)_accountsUsageList.get( account );

		if( !wasUsedRecently.booleanValue() ) {
		    // Since this account hasn't been used, close it
		    try {
			DBTransaction.closePagedTransactions( account );
		    } catch( Exception e ) {
			// We know the account is opened for paged access
			// the exception will not be thrown
		    }
		}

		// If it was in use, change it's flag back to false.  That way, if
		// no other method sets it to true by the time this method gets called again,
		// it'll be cleaned up
		_accountsUsageList.put( account, Boolean.FALSE );
	    }
	}
    }
	
	/**
	* Adds a transaction to the database - BS_Transactions table. - THIS METHOD IS FOR TESTING PURPOSES ONLY.
	* @param account the from-account of the transaction to you wish to add to bank sim
	* @param transaction the new transaction that you wish to add to bank sim 
	* @param extra a hash map containing any other necessary objects
	*/
	public static void addTransaction( Account account, Transaction transaction, HashMap extra ) throws Exception {
		if( account == null || transaction == null ) {
    	    // Nothing to add
    	    return;
    	}
		Profile.isInitialized();
        Connection con = null;
		PreparedStatement stmt = null;
	
        try {
            con = DBUtil.getConnection( Profile.getPoolName(), false, Connection.TRANSACTION_READ_COMMITTED);
            
			BigDecimal amountToXfer = null;
			String amountToXferString = null;
			if( transaction.getAmountValue() != null ) {
				amountToXfer = transaction.getAmountValue().getAmountValue();
				amountToXfer = amountToXfer.setScale(2, BigDecimal.ROUND_HALF_EVEN);
				amountToXferString = amountToXfer.toString();
			}

			BigDecimal currentRunningBalance = null;
			String currentRunningBalanceString = null;
			if( transaction.getRunningBalanceValue() != null ) {
				currentRunningBalance = transaction.getRunningBalanceValue().getAmountValue();
				currentRunningBalance = currentRunningBalance.setScale(2, BigDecimal.ROUND_HALF_EVEN);
			}
			
			int transType = transaction.getTypeValue();
			long transDateLong = transaction.getRODateValue().getTimeInMillis();
			
			stmt = con.prepareStatement(ADD_TRANSACTION);
			int idx = 1;
			stmt.setInt( idx++, Integer.parseInt( transaction.getID() ) );
			stmt.setLong( idx++, transDateLong );
			stmt.setInt( idx++, transType );
			stmt.setString( idx++, account.getID() );
			stmt.setString( idx++, account.getBankID() );
			stmt.setString( idx++, amountToXferString );
			stmt.setString( idx++, account.getCurrencyCode() );
			stmt.setString( idx++, transaction.getMemo() );
			stmt.setString( idx++, transaction.getReferenceNumber() );
			stmt.setInt( idx++, Integer.parseInt( transaction.getReferenceNumber() ) );
			stmt.setString( idx++, transaction.getDataClassification() );
			stmt.setBigDecimal( idx++, currentRunningBalance );

			stmt.executeUpdate();
            
            DBUtil.commit(con);
        } catch (Exception ex) {
            DBUtil.rollback(con);
        } finally {
            DBUtil.returnConnection(Profile.getPoolName(), con);
			try {
				stmt.close();
			}
			catch( Exception e ) {
			}
        }
			
	}

	
    /**
     * Add the white-space padding from the incoming string if required.
     * Data Consolidator allows the bank to specify the data type for the bank and customer reference number.
     * If the data type is set to Integer, the reference number is left padded with white space before storing it in the database.
     * When doing search, the incoming search criteria value is also left-padded with white space before placing them in the query.
     * This allows the reference number be searched as an Integer.
     * This function is a helper function that adds the padding if the data type of the field is Integer.
     *
     * @param value Search value
     * @param type DC type - either String or Integer
     * @return value to be used in DB query - may be space padded
     */
    private static String padRefNum(String value, String type)
    {
    	
    	 String ENCODING = "UTF-8";
		if( value == null ) {
		    return null;
		}
		if (type.equals("Integer")) {
		    StringBuffer temp = new StringBuffer();
	
		    int length = value.length();
		    try {
			// Determine the amount of bytes the string will take in the database,
			byte[] valueBytes = value.getBytes( ENCODING );
	
			length = valueBytes.length;
		    } catch( java.io.UnsupportedEncodingException uee ) {
			// Intentionally eaten
		    }
	
		    for (int i=40 - length; i > 0; i--) {
			temp.append(" ");
		    }
		    value = temp.toString() + value;
		}
	return value;
    }	
	

    /**
	    if the object is null, then set the indicated column to null, otherwise set the column
	to the value of the object. The type of the data set in the statement depends on the type of
	of the object.
	@param stmt statement to fill
	@param column column in statement to fill
	@param obj object to fill statement column with
	*/
	public static void fillValue( PreparedStatement stmt, int column, Object obj ) throws Exception
	{
			if( obj instanceof Integer ) {
			    stmt.setInt( column, ((Integer) obj).intValue() );
			} else if( obj instanceof Long ) {
			    stmt.setLong( column, ((Long) obj).longValue() );
			} else if( obj instanceof String ) {
			    stmt.setString( column, (String) obj );
			} else if( obj instanceof DateTime ) {
				DateTime sortDate = (DateTime) obj ;
				long sortTime = sortDate.getTimeInMillis();
				stmt.setLong( column, sortTime );
			} else if( obj instanceof Calendar ) {
			    fillTimestampColumn( stmt, column, (Calendar) obj );
			} else if( obj instanceof Currency ) {
			    fillCurrencyColumn( stmt, column, (Currency) obj );
			}
	}

	
	/**
	 if the object is null, then set the indicated column to null, otherwise set the column
	 to the value of the datetime. The datetime is formatted based on the format specified
	 @param stmt statement to fill
	 @param column column in statement to fill
	 @param value The value to fill
	 @param dateFormat The date format
	 */
	public static void fillTimestampColumn(PreparedStatement stmt, int column, String value, String dateFormat) throws Exception
	{
	    if( value != null && value.length() != 0 ) {
		Date parsedDate = DateParser.parse( value, dateFormat );
		fillTimestampColumn(stmt, column, new DateTime( parsedDate , Locale.getDefault(), dateFormat ) );
	    }
	}
	
	
	/**
	if the DateTime object is null, then set the indicated column to null, otherwise set the column
	to the value of the object
	@param stmt statement to fill
	@param column column in statement to fill
	@param obj Calendar object to fill statement column with
	*/
	public static void fillTimestampColumn(PreparedStatement stmt, int column, Calendar obj) throws Exception
	{
		if (obj != null) {
	               //SQL SERVER datetime precision is 0.03Second
	               if (BSAdapter.CONNECTIONTYPE != null && BSAdapter.CONNECTIONTYPE.equalsIgnoreCase( ConnectionDefines.DB_MS_SQLSERVER )){
	                   obj.set(Calendar.MILLISECOND, 0);                        
	               }
	               stmt.setTimestamp(column, new Timestamp(obj.getTime().getTime()));
		} else {
			stmt.setTimestamp(column, null);
		}
	}
	
	
	/**
	if the Currency object is null, then set the indicated column to null, otherwise set the column
	to the BigDecimal value of the Currency
	@param stmt statement to fill
	@param column column in statement to fill
	@param obj Currency object to fill statement column with
	*/
	public static void fillCurrencyColumn( PreparedStatement stmt, int column, Currency obj ) throws Exception
	{
	if( obj != null ) {
	    BigDecimal bigD = obj.getAmountValue();
	    try {
		bigD = bigD.setScale( 3, BigDecimal.ROUND_UNNECESSARY );
	    } catch ( ArithmeticException e ) {
		throw new BSException( BSException.BSE_DB_EXCEPTION, "Invalid currency amount. Scale value must contain no more than three digits.",e );
	    }
	    stmt.setBigDecimal( column, bigD );
	} else {
	    //	    stmt.setBigDecimal( column, null );
	    stmt.setNull( column, java.sql.Types.NUMERIC );
	}
	}
	/**
	 * This API allows jumping to any page within a page range. It works in two
	 * phases. When first page is requested, it resets the paging state and
	 * populates a list of primary keys for subsequent paging. This list is
	 * stored in the paging context bean under the key PagingContext.KEY_LIST.
	 * <br><br>
	 * The first phase of setting up the list is executed by getTransactionKeys
	 * when goToPage field in paging context is set to 1. Thereafter, getPage
	 * API will use the key list to fetch the actual records. The paging context
	 * must be
     * 
	 * @param account The accounts to fetch transactions for.
	 * @param context The paging context object. Paging context should must contain 
	 * @param extra Extra parameter map.
	 * @param connection Database connection.
	 * @return Requested page of transactions.
	 * @throws BSException
	 */
	public static Transactions getSpecificPageOfTransactions(Account account, PagingContext context, HashMap extra,
			DBConnection connection) throws BSException {
		
		try {
			int goToPage = context.getGotoPage();
			if(context.resetPagingState()) {	
    			context.setGotoPage(1);
    			context.getMap().remove(PagingContext.KEY_LIST);	//	Remove previous list.
    			context.getMap().remove(PagingContext.ORDER_BY);
    			context = getTransactionKeys(account, context, connection, true, extra);
    		}
    		return getPage(account, context, connection, extra);
			
		} catch ( BSException e ) {
		    throw e;
		} catch ( Exception ex ) {
		    throw new BSException( BSException.BSE_DB_EXCEPTION, ex.toString() );
		} 
		
	}
	
	/**
	 * This API is used to get Account Transactions
	 * <br><br>
     * 
	 * @param account The accounts to fetch transactions for.
	 * @param context The paging context object. Paging context should must contain 
	 * @param extra Extra parameter map.
	 * @param connection Database connection.
	 * @return Requested page of transactions.
	 * @throws BSException
	 */
	public static Transactions getAccountTransactions(Account account, PagingContext context, HashMap extra,
			DBConnection connection) throws BSException {
		
		final String thisMethod = "DBTransaction.getAccountTransactions";

		ResultSet rs = null;
		PreparedStatement stmt = null;
		StringBuffer sql = null;
		Transactions trans = new Transactions();
		try {
			sql = new StringBuffer(SQL_GET_PAGED_TRANSACTIONS_NEW);
			addAccountTransactionsSearchCriteria( sql, account, context, extra );
			stmt = connection.prepareStatement( connection, sql.toString() );
			int idx = 1;
			idx = addAccountTransactionsSearchValues(stmt, idx, account,context, extra);
			rs = DBUtil.executeQuery( stmt, sql.toString() );
			createTransactionsForPagination2( trans, rs, account );
			debug(thisMethod, "Transactions read = " + trans.size());
			return trans;
		} catch(Exception e) {
			  throw new BSException( BSException.BSE_DB_EXCEPTION, thisMethod + ": Unable to retrieve transactions. "+e.toString() );
		} finally {
			DBUtil.closeAll(stmt, rs);
		}
	}

	/**
	 * Get Transaction Details By Id.
	 * @param account
	 * @param transId
	 * @param extra
	 * @param conn
	 * @return Transaction
	 * @throws BSException
	 */
	public static Transaction getTransactionById(Account account, String transId, HashMap extra, DBConnection conn ) throws BSException {
		final String thisMethod = "DBTransaction.getTransactionById";
		ResultSet rs = null;
		PreparedStatement stmt = null;
		StringBuffer sql = null;
		Transactions trans = new Transactions();
		Transaction transaction = null;
		try {
			StringBuffer stmtBuffer = new StringBuffer( SQL_GET_PAGED_TRANSACTIONS_NEW); 
			if (transId != null && !transId.isEmpty()) {
				stmtBuffer.append( AND_TRANSACTION_ID_CLAUSE );
			} else {
				throw new BSException( BSException.BSE_DB_EXCEPTION, "The Trans ID  value provided ( " + transId + " ) was " +
					       " not valid. " );
			}
			debug(thisMethod, "SQL -", stmtBuffer);
			stmt = conn.prepareStatement( conn, stmtBuffer.toString() );
			int idx = 1;
			stmt.setInt( idx++, Integer.parseInt(transId));
			stmt.setMaxRows(1);
			rs = DBUtil.executeQuery( stmt, stmtBuffer.toString() );
			createTransactionsForPagination2( trans, rs, account );
			for (Object object : trans) {
				transaction = (Transaction) object;	
			}
			// debug(thisMethod, "Transactions read = " + trans.size());
			return transaction;
		} catch ( BSException e ) {
		    throw e;
		} catch ( Exception ex ) {
		    throw new BSException( BSException.BSE_DB_EXCEPTION, ex.toString() );
		} 
	}
	
	
	/**
	 * Phase 1 of paging. In this phase the search and sort criteria are used to read primary keys into memory. These
	 * keys are stored in paging context under PagingContext.KEY_LIST. These keys are then used by getPage function to
	 * read the actual data page.
	 * 
	 * @param account
	 *            The account to fetch transactions for.
	 * @param context
	 *            The paging context object.
	 * @param connection
	 *            Database connection.
	 * @param moveForward
	 *            Boolean flag that is required by old code used in this function.
	 * @param extra
	 *            Extra parameter map.
	 * @return Paging context populated with primary keys of records. Keys are stored under PagingContext.KEY_LIST.
	 * @throws Exception
	 */
	private static PagingContext getTransactionKeys(Account account, PagingContext context, DBConnection connection,
			boolean moveForward, HashMap extra) throws Exception {

		String thisMethod = "DBTransaction.getTransactionKeys";
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			ReportCriteria criteria = (ReportCriteria) context.getMap().get( BSConstants.PAGING_CONTEXT_REPORT_CRITERIA );
			criteria.getSearchCriteria().put( BSConstants.PAGING_CONTEXT_SEARCH_CRITERIA_ACCOUNT, account.getID() );
			StringBuffer stmtBuffer = new StringBuffer( SQL_GET_TRANSACTION_KEYS );
			addSearchCriteria( stmtBuffer, account, context, extra );
			addAmountSearchCriteria( stmtBuffer, account, context, extra );
			StringBuffer orderBybuff = new StringBuffer();
			addOrderByClause( orderBybuff, context, moveForward, null, extra );
			stmtBuffer.append(orderBybuff);
			//	Save this for later use.
			context.getMap().put(PagingContext.ORDER_BY, orderBybuff.toString());
			orderBybuff = null;
			debug(thisMethod, "SQL -", stmtBuffer);
			stmt = connection.prepareStatement( connection, stmtBuffer.toString() );
			int idx = 1;
			idx = addSearchValues(stmt, idx, account,context, extra);
			idx = addAmountSearchValues(stmt, idx, account,context, extra);
			rs = DBUtil.executeQuery( stmt, stmtBuffer.toString() );
			stmtBuffer = null;
			ArrayList<Integer> keys = new ArrayList<Integer>(64);
			int totalRecords = 0;
			while(rs.next()) {
				keys.add(rs.getInt(1));
				totalRecords++;
			}
			int pageSize = MapUtil.getIntValue( extra, BSConstants.PAGE_SIZE, 10 );
			context.getMap().put(PagingContext.KEY_LIST, keys);
			context.getMap().put(PagingContext.TOTAL_TRANS, String.valueOf(totalRecords));
			context.getMap().put(PagingContext.PAGE_SIZE, String.valueOf(pageSize));	
		} catch(Exception e) {
			throw new Exception(thisMethod + ": Unable to retrieve transaction keys. ", e);
		} finally {
		    DBUtil.closeResultSet( rs );
		    DBUtil.closeStatement( stmt );
		}
		return context;
		 
	}

	/**
	 * Phase 2 of paging. In this phase the key list from paging context is used to read entire rows that fall in the
	 * requested page. This key list is stored in paging context under PagingContext.KEY_LIST.<br>
	 * 
	 * @param account
	 *            The accounts to fetch records for.
	 * @param context
	 *            Paging context object. It should contain PagingContext.KEY_LIST populated
	 *            by getTransactionKeys and PagingContext.ORDER_BY.
	 * @param connection
	 *            Database connection.
	 * @param extra
	 *            Extra parameter map.
	 * @return Requested page of transactions.
	 * @throws Exception
	 */
	private static Transactions getPage(Account account, PagingContext context, DBConnection connection, HashMap extra)
			throws Exception {

		final String thisMethod = "DBTransaction.getPage";
		
		ResultSet rs = null;
		PreparedStatement stmt = null;
		StringBuffer sql = null;
		Transactions trans = new Transactions();
		try {
			
			ArrayList<Integer> keys = (ArrayList<Integer>) context.getMap().get(PagingContext.KEY_LIST); 
			if(keys == null) {
				throw new NullPointerException("No keys found to fetch transactions against.");
			}
			int pageNumber = -1;
			int pageSize = 10;
			try {
				pageSize = Integer.parseInt((String)context.getMap().get(PagingContext.PAGE_SIZE));
			} catch(Exception igonre) {
				
			}
			if(pageSize <= 0) {
				//	Return everything.
				pageSize = keys.size();
			}
			int	totalPages = keys.size() / pageSize;
			if(keys.size() % pageSize > 0)
				totalPages++;
			if(totalPages == 0) {
				context.setPageNumber(1);
				context.setFirstPage(true);
				context.setLastPage(true);
				return trans;
			}
			
			if(context.getDirection().equals(PagingContext.DIRECTION_FIRST)) {
				pageNumber = 1;
			} else if(context.getDirection().equals(PagingContext.DIRECTION_NEXT)) {
				pageNumber = context.getPageNumber() + 1;
			} else if(context.getDirection().equals(PagingContext.DIRECTION_PREVIOUS)) {
				pageNumber = context.getPageNumber() - 1;
			} else if(context.getDirection().equals(PagingContext.DIRECTION_LAST)) {
				pageNumber = totalPages;
			}  else {
				pageNumber = context.getGotoPage();
			} 
			
			if(pageNumber <= 0) {
				pageNumber = 1;
				context.setFirstPage(true);
			}

			if(pageNumber > totalPages) {
				pageNumber = totalPages;
				context.setLastPage(true);
			}
			
			int start = (pageNumber - 1) * pageSize;
			int end = start + pageSize - 1;
			if(end < 0)
				end = 0;
			
			if(end >= keys.size()) {
				//	Go to last page.
				end = keys.size() - 1;
				if(end < 0)
					end = 0;
				start = (keys.size() / pageSize) * pageSize;
				if(start < 0)
					start = 0;
			}
			debug(thisMethod, "startIndex = " + start + ", endIndex = " + end);
			sql = new StringBuffer(SQL_GET_PAGED_TRANSACTIONS_NEW);
			if(!addWhereClauseOnPage(sql, start, end)) {
				//	No keys to fetch records for.
				return trans;
			}
			addOrderByClauseOnPage(sql, context);
			debug(thisMethod, "SQL - ", sql);
			stmt = connection.prepareStatement( connection, sql.toString() );
			setStatementKeys(stmt, keys, start, end, 1);
			rs = DBUtil.executeQuery(stmt, sql.toString());
			createTransactionsForPagination2( trans, rs, account );
			debug(thisMethod, "Transactions read = " + trans.size());
			return trans;
		} catch(Exception e) {
			throw new Exception(thisMethod + ": Unable to retrieve transaction page. ", e);
		} finally {
			DBUtil.closeAll(stmt, rs);
		}
	}
	/**
	 * Set primary keys into statement.
	 * @param stmt Statement to add keys to.
	 * @param keys The keys.
	 * @param start Start index in keys collection to fetch keys.
	 * @param end End index in keys collection to stop fetching keys at (inclusive).
	 * @param index Index in the statement to add keys.
	 * @return Index in statement at which the next value should go in.
	 * @throws Exception
	 */
	private static int setStatementKeys(PreparedStatement stmt, 
								 ArrayList<Integer> keys,
								 int start,
								 int end,
								 int index) throws Exception {

		for(int i = start; i <= end; i++) {
			stmt.setInt(index, keys.get(i).intValue());
			index++;
		}

		return index;
	}
	/**
	 * Adds order by clause for a page if the clause is present in paging context
	 * under key PagingContext.ORDER_BY.
	 * 
	 * @param sql The string buffer to add clause to.
	 * @param context Paging context object.
	 * @return <code>True</code> if clause is added, <code>false</code> otherwise.
	 */
	private static boolean addOrderByClauseOnPage(StringBuffer sql, PagingContext context) {
		
		String orderBy = (String) context.getMap().get(PagingContext.ORDER_BY); 
		if(orderBy != null && orderBy.length() > 0) {
			sql.append(orderBy);
			return true;
		}
		return false;
	}
	
	/**
	 * Adds the IN clause using the keys collection. This method iterates the keys list from start to end both inclusive,
	 * and adds an in clause placeholder to the passed in sql.<br> 
	 * 
	 * @param sql
	 *            The string buffer to add clause to.
	 * @param start
	 *            The start index.
	 * @param end
	 *            The end index.
	 * @return <code>true</code> if where clause is added, <code>false</code> if
	 *         not.
	 * @throws Exception
	 */
	private static boolean addWhereClauseOnPage(StringBuffer sql, 
										 int start,
										 int end) throws Exception {
	
		if((end - start) >= 0) {
			sql.append(" AND b.TransactionID IN (");
			for(int i = start; i <= end; i++) {
				sql.append('?');
				if(i != end)
					sql.append(',');
			}
			sql.append(')');
			return true;
		}
		return false;
		
	}
	
	
	/**
     * Debug at TRACE level.
     * 
     * @param method Caller.
     * @param msg Message.
     * @param buffer Message buffer.
     */
    private static void debug(String method, String msg, final StringBuffer buffer) {
    	
    	if(buffer != null && logger.isTraceEnabled())
    		logger.trace(method + " : " + msg + buffer.toString());
    }
    /**
     * Debug at TRACE level.
     * 
     * @param method Caller.
     * @param msg Message.
     */
    private static void debug(String method, String msg) {
    	
    	if(msg != null)
    		logger.trace(method + " : " + msg);
    }

}
