package com.ffusion.banksim.proxy;

import java.security.SecureRandom;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.ffusion.banksim.BankSim;
import com.ffusion.banksim.interfaces.BSException;
import com.ffusion.banksim.interfaces.BankingBackend;
import com.ffusion.banksim.interfaces.RealTimeBankingBackend;
import com.ffusion.beans.Bank;
import com.ffusion.beans.Contact;
import com.ffusion.beans.DateTime;
import com.ffusion.beans.SecureUser;
import com.ffusion.beans.accounts.Account;
import com.ffusion.beans.accounts.AccountHistories;
import com.ffusion.beans.accounts.AccountHistory;
import com.ffusion.beans.accounts.AccountSummaries;
import com.ffusion.beans.accounts.AccountSummary;
import com.ffusion.beans.accounts.AccountTypes;
import com.ffusion.beans.accounts.Accounts;
import com.ffusion.beans.accounts.AssetAcctSummary;
import com.ffusion.beans.accounts.CreditCardAcctSummary;
import com.ffusion.beans.accounts.DepositAcctSummary;
import com.ffusion.beans.accounts.ExtendedAccountSummaries;
import com.ffusion.beans.accounts.ExtendedAccountSummary;
import com.ffusion.beans.accounts.FixedDepositInstrument;
import com.ffusion.beans.accounts.FixedDepositInstruments;
import com.ffusion.beans.accounts.LoanAcctSummary;
import com.ffusion.beans.accounts.SettlementInstructionTypes;
import com.ffusion.beans.banking.Transaction;
import com.ffusion.beans.banking.Transactions;
import com.ffusion.beans.banking.Transfer;
import com.ffusion.beans.common.Currency;
import com.ffusion.beans.messages.Message;
import com.ffusion.beans.user.User;
import com.ffusion.ffs.bpw.interfaces.IntraTrnInfo;
import com.ffusion.util.beans.PagingContext;
import com.sap.banking.product.beans.Product;


public class RealTimeBankingBackendImpl implements RealTimeBankingBackend {

	@Autowired
	@Qualifier(value = "bankingBackendRef")
	private BankingBackend bankingBackend;
	
    public static Map<String, Map<String, AccountSummary>> summaryMap=  new HashMap<String, Map<String, AccountSummary>>();
    
    public static Map<String, Map<String, AccountHistory>> historyMap=  new HashMap<String, Map<String, AccountHistory>>();
    
    public static Map<String, Map<String, FixedDepositInstrument>> fdInstrumentsMap=  new HashMap<String, Map<String, FixedDepositInstrument>>();
    
    public static Map<String, Map<String, Transaction>> fdInstrumentTrxnsMap=  new HashMap<String, Map<String, Transaction>>();
    
    public static Map<String, Map<String, ExtendedAccountSummary>> extendedAccountSummaryMap=  new HashMap<String, Map<String, ExtendedAccountSummary>>();
    
    /** Map ID for real time account summary. */
	public static final String REALTIME_ACCOUNT_SUMMARY = "cache_RealTimeAccountSummary";
	
	/** Map ID for real time account get history. */
	public static final String REALTIME_ACCOUNT_GETHISTORY = "cache_RealTimeAccountGetHistory";
	
	/** Map ID for real time FD Instrument trxns. */
	public static final String REALTIME_FD_INSTRUMENT_TRXNS = "cache_FDInstrumentTransactions";
	
	/** Map ID for real time FD Instruments. */
	public static final String REALTIME_FD_INSTRUMENTS = "cache_FDInstruments";
	
	/** Map ID for real time extended account summary. */
	public static final String REALTIME_EXTENDEDACCOUNT_GETSUMMARY = "cache_RealTimeExtendedAccountGetSummary";
	
	public BankingBackend getBankingBackend() {
		return bankingBackend;
	}

	public void setBankingBackend(BankingBackend bankingBackend) {
		this.bankingBackend = bankingBackend;
	}

	@Override
	public boolean isInitialized() {
		return bankingBackend.isInitialized();
	}

	@Override
	public User signOn(String userID, String password) throws BSException {
		return bankingBackend.signOn(userID, password);
	}

	@Override
	public void setPassword(User customer, String newPassword)
			throws BSException {
		bankingBackend.setPassword(customer, newPassword);	

	}

	@Override
	public void addBank(Bank bank) throws BSException {
		bankingBackend.addBank(bank);
	}

	@Override
	public void addBanks(Bank[] banks) throws BSException {
		bankingBackend.addBanks(banks);	
	}

	@Override
	public Bank getBank(String name) throws BSException {
		return bankingBackend.getBank(name);
	}

	@Override
	public void updateBank(Bank bank) throws BSException {
		 bankingBackend.updateBank(bank);
	}

	@Override
	public void updateBanks(Bank[] bank) throws BSException {
		bankingBackend.updateBanks(bank);
	}

	@Override
	public void deleteBank(Bank bank) throws BSException {
		bankingBackend.deleteBank(bank);
	}

	@Override
	public void addCustomer(User customer) throws BSException {
		bankingBackend.addCustomer(customer);
	}

	@Override
	public void addCustomers(User[] customers) throws BSException {
		bankingBackend.addCustomers(customers);	

	}

	@Override
	public void updateCustomer(User customer) throws BSException {
		bankingBackend.updateCustomer(customer);
	}

	@Override
	public void updateCustomers(User[] customers) throws BSException {
		bankingBackend.updateCustomers(customers);
	}

	@Override
	public void deleteCustomer(User customer) throws BSException {
		bankingBackend.deleteCustomer(customer);
	}

	@Override
	public void addAccount(User customer, Account toAdd) throws BSException {
		bankingBackend.addAccount(customer, toAdd);
	}

	@Override
	public void addAccounts(User customer, Account[] toAdd) throws BSException {
		bankingBackend.addAccounts(customer, toAdd);
	}

	@Override
	public Enumeration getAccounts(User customer) throws BSException {
		return bankingBackend.getAccounts(customer);
	}

	@Override
	public Account getAccount(Account account) throws BSException {
		return bankingBackend.getAccount(account); 
	}

	@Override
	public void updateAccount(Account account) throws BSException {
		 bankingBackend.updateAccount(account);
	}

	@Override
	public void updateAccounts(Account[] accounts) throws BSException {
		bankingBackend.updateAccounts(accounts);
	}

	@Override
	public void deleteAccount(Account account) throws BSException {
		bankingBackend.deleteAccount(account);
	}

	@Override
	public Transfer addTransfer(Transfer transfer, int transType)
			throws BSException {
		return bankingBackend.addTransfer(transfer, transType);
	}

	@Override
	public void addBPWTransfer(String bankId, String acctIdTo,
			String acctTypeTo, String acctIdFrom, String acctTypeFrom,
			String amount, String curDef) throws BSException {
		bankingBackend.addBPWTransfer(bankId, acctIdTo, acctTypeTo, acctIdFrom, acctTypeFrom, amount, curDef);

	}

	@Override
	public void addBPWTransfer(String bankId, String acctIdTo,
			String acctTypeTo, String acctIdFrom, String acctTypeFrom,
			String amount, String curDef, String toAmount,
			String toAmtCurrency, int transType) throws BSException {

		bankingBackend.addBPWTransfer(bankId, acctIdTo, acctTypeTo, acctIdFrom, acctTypeFrom, amount, curDef, toAmount, toAmtCurrency, transType);

	}
	
	@Override
	public void addBPWTransfer(IntraTrnInfo intraTrnInfo, int transType) throws BSException {
		bankingBackend.addBPWTransfer(intraTrnInfo , transType);

	}

	@Override
	public Transfer[] addTransfers(Transfer[] transfers, int[] transType)
			throws BSException {

		return bankingBackend.addTransfers(transfers, transType);
	}

	@Override
	public Enumeration getTransactions(Account account, Calendar startDate,
			Calendar endDate) throws BSException {
		return bankingBackend.getTransactions(account, startDate, endDate);
	}

	@Override
	public void openPagedTransactions(Account account, Calendar startDate,
			Calendar endDate) throws BSException {
		bankingBackend.openPagedTransactions(account, startDate, endDate);
	}

	@Override
	public void openPagedTransactions(Account account, PagingContext context,
			HashMap extra) throws BSException {
		
		bankingBackend.openPagedTransactions(account, context, extra);
	}

	@Override
	public Transactions getPagedTransactions(Account account,
			PagingContext context, HashMap extra) throws BSException {
		return bankingBackend.getPagedTransactions(account, context, extra); 
	}

	@Override
	public Transactions getNextTransactions(Account account,
			PagingContext context, HashMap extra) throws BSException {
		return bankingBackend.getNextTransactions(account, context, extra);
	}

	@Override
	public Transactions getPreviousTransactions(Account account,
			PagingContext context, HashMap extra) throws BSException {

		return bankingBackend.getPreviousTransactions(account, context, extra);
	}

	@Override
	public void closePagedTransactions(Account account) throws BSException {
		bankingBackend.closePagedTransactions(account);
	}

	@Override
	public int getNumberOfTransactions(Account account) throws BSException {
		return bankingBackend.getNumberOfTransactions(account); 
		
	}

	@Override
	public Enumeration getNextPage(Account account, int howMany)
			throws BSException {
	
		return bankingBackend.getNextPage(account, howMany);
	}

	@Override
	public Enumeration getNextPage(Account account, int howMany, int nextIndex)
			throws BSException {
		return bankingBackend.getNextPage(account, howMany, nextIndex);
	}

	@Override
	public Enumeration getPrevPage(Account account, int howMany)
			throws BSException {
		return bankingBackend.getPrevPage(account, howMany);
	}

	@Override
	public Enumeration getPrevPage(Account account, int howMany, int prevIndex)
			throws BSException {
		return bankingBackend.getPrevPage(account, howMany, prevIndex);
	}

	@Override
	public void addMailMessage(User customer, Message message)
			throws BSException {
		bankingBackend.addMailMessage(customer, message);
	}

	@Override
	public Enumeration getMailMessages(User customer) throws BSException {
		return bankingBackend.getMailMessages(customer);
	}

	@Override
	public Transactions getSpecificPage(Account account,
			PagingContext pagingContext, HashMap extra) throws BSException {
		return bankingBackend.getSpecificPage(account, pagingContext, extra);
	}

	@Override
	public Transactions getAccountTransactions(Account account, PagingContext pagingContext, HashMap extra)  
			throws BSException{
		return bankingBackend.getAccountTransactions(account, pagingContext, extra);
	}
	
	@Override
	public Transaction getTransactionById(Account account, String transId,
			HashMap extra) throws BSException {
		return bankingBackend.getTransactionById(account, transId, extra);
	}

	/**************************************************************************/
	
	@Override
	public void changePIN(String currentPin, String newPin) throws BSException {

		// By default. There is no implementation to this method.

	}

	@Override
	public FixedDepositInstruments getFixedDepositInstruments(Account account,
			Calendar start, Calendar end, HashMap extra) throws BSException {
		if ( account == null ) {
			throw new BSException( BSException.BSE_ACCOUNT_NOT_EXISTS );
		}

		java.util.Locale locale = account.getLocale();
		DateTime instrumentDate = start != null ? new DateTime( start, locale ) : new DateTime( locale );

		FixedDepositInstruments instruments = new FixedDepositInstruments();
		FixedDepositInstrument  instrument = null;
		String key = account.getID();
		String cacheId = REALTIME_FD_INSTRUMENTS;
		Map<String, FixedDepositInstrument> map = fdInstrumentsMap.get(cacheId);
		if(map != null) {
			instrument = map.get(key);
		}
		if(instrument == null){
			instrument  = instruments.createFixedDepositInstrument();

			instrument.setAccountNumber( account.getNumber() );
			instrument.setAccountID( account.getID() );
			instrument.setBankID( account.getBankID() );
			instrument.setRoutingNumber( account.getRoutingNum() );
			instrument.setInstrumentNumber( "12345" );
			instrument.setInstrumentBankName( "Fusion Bank" );
			instrument.setCurrency( account.getCurrencyCode() );

			Currency principalAmt = new Currency( getNumber("Double", 2), locale );
			instrument.setPrincipalAmount( principalAmt );

			instrument.setInterestRate( 2.03550f );

			Currency accruedInterest = new Currency( getNumber("Double", 2), locale );
			instrument.setAccruedInterest( accruedInterest );

			instrument.setMaturityDate( instrumentDate );

			Currency interestAtMaturity = new Currency( getNumber("Double", 2), locale );
			instrument.setInterestAtMaturity( interestAtMaturity );

			Currency proceedsAtMaturity = new Currency( getNumber("Double", 2), locale );
			instrument.setProceedsAtMaturity( proceedsAtMaturity );

			instrument.setValueDate( instrumentDate );
			instrument.setDaysInTerm( 90 );

			Currency restrictedAmt = new Currency( getNumber("Double", 2), locale );
			instrument.setRestrictedAmount( restrictedAmt );

			instrument.setNumberOfRollovers( 5 );

			Contact stmtMailAddr1 = new Contact();
			stmtMailAddr1.setStreet( "742 Evergreen Terrace" );
			//stmtMailAddr1.setStreet2( "" );
			stmtMailAddr1.setCity( "Springfield" );
			stmtMailAddr1.setState( "MA" );
			stmtMailAddr1.setCountry( "United States of America" );
			stmtMailAddr1.setEmail( "hsimpson@fox.com" );
			stmtMailAddr1.setPhone( "555-1234" );
			//stmtMailAddr1.setPhone2( "" );
			//stmtMailAddr1.setZipCode( "" );
			//stmtMailAddr1.setDataPhone( "" );
			//stmtMailAddr1.setFaxPhone( "" );
			stmtMailAddr1.setPreferredContactMethod( "e-mail" );
			instrument.setStatementMailingAddr1( stmtMailAddr1 );

			Contact stmtMailAddr2 = new Contact();
			stmtMailAddr2.setStreet( "24 Sussex Drive" );
			//stmtMailAddr2.setStreet2( "" );
			stmtMailAddr2.setCity( "Ottawa" );
			stmtMailAddr2.setState( "ON" );
			stmtMailAddr2.setCountry( "Canada" );
			stmtMailAddr2.setEmail( "pm@pm.gc.ca" );
			stmtMailAddr2.setPhone( "(613) 992-4211" );
			//stmtMailAddr2.setPhone2( "" );
			stmtMailAddr2.setZipCode( "K1A 0A2" );
			//stmtMailAddr2.setDataPhone( "" );
			stmtMailAddr2.setFaxPhone( "(613) 941-6900" );
			stmtMailAddr2.setPreferredContactMethod( "phone" );
			instrument.setStatementMailingAddr2( stmtMailAddr2 );

			Contact stmtMailAddr3 = new Contact();
			stmtMailAddr3.setStreet( "Skywalker Ranch" );
			stmtMailAddr3.setStreet2( "5858 Lucas Valley Rd." );
			stmtMailAddr3.setCity( "Nicasio" );
			stmtMailAddr3.setState( "CA" );
			stmtMailAddr3.setCountry( "United States of Ameria" );
			//stmtMailAddr3.setEmail( "" );
			//stmtMailAddr3.setPhone( "" );
			//stmtMailAddr3.setPhone2( "" );
			stmtMailAddr3.setZipCode( getNumber("Integer", 4) );
			//stmtMailAddr3.setDataPhone( "" );
			//stmtMailAddr3.setFaxPhone( "" );
			stmtMailAddr3.setPreferredContactMethod( "none" );
			instrument.setStatementMailingAddr3( stmtMailAddr3 );

			instrument.setSettlementInstructionType( SettlementInstructionTypes.SETTL_TYPE_ROLL_PRINC_AND_INT );
			instrument.setSettlementTargetRoutingNumber( "55555" );
			instrument.setSettlementTargetAccountNumber( "12345" );
			instrument.setDataDate( instrumentDate );

			instrument.setLocale( locale );
			instrument.setDateFormat( account.getDateFormat() );
			Map<String, FixedDepositInstrument> sMap = fdInstrumentsMap.get(cacheId);
			if(sMap == null) {
				sMap = new HashMap<String, FixedDepositInstrument>();
			}
			sMap.put(key, instrument);
			fdInstrumentsMap.put(cacheId, sMap);
		}else {
			instruments.add(instrument);
		}
		return instruments;
	}

	@Override
	public void updateFixedDepositInstrument(FixedDepositInstrument inst,
			HashMap extra) throws BSException {
        if ( inst == null ) {
            throw new BSException( BSException.BSE_FDINSTRUMENT_NOT_EXISTS );
        }

	}

	@Override
	public Transactions getFDInstrumentTransactions(
			FixedDepositInstrument inst, Calendar start, Calendar end,
			HashMap extra) throws BSException {
		if ( inst == null ) {
			throw new BSException( BSException.BSE_FDINSTRUMENT_NOT_EXISTS );
		}

		java.util.Locale locale = inst.getLocale();
		DateTime transactionDate = start != null ? new DateTime( start, locale ) : new DateTime( locale );

		//Since BankSim does not support Fixed Deposit Transactions operations
		//Return Transactions with meaningful but hard-coded data values
		Transactions transactions = new Transactions();
		Transaction tran1 = null;
		Transaction tran2 = null;
		String key = inst.getAccountID();
		String cacheId = REALTIME_FD_INSTRUMENT_TRXNS;
		Map<String, Transaction> map = fdInstrumentTrxnsMap.get(cacheId);
		if(map != null) {
			tran1 = map.get(key);
		}
		if(tran1 == null){
			tran1 = transactions.create();
			tran1.setID( "12345" );
			tran1.setType( 1 );
			tran1.setCategory( 1 );
			tran1.setDescription( "Rollover Principal" );
			tran1.setDate( transactionDate );
			tran1.setAmount( getNumber("Double", 3) );
			tran1.setFixedDepositRate( 2.22050f );
			tran1.setInstrumentNumber( inst.getInstrumentNumber() );
			tran1.setInstrumentBankName( inst.getInstrumentBankName() );
			Map<String, Transaction> sMap = fdInstrumentTrxnsMap.get(cacheId);
			if(sMap == null) {
				sMap = new HashMap<String, Transaction>();
			}
			sMap.put(key, tran1);
        	fdInstrumentTrxnsMap.put(cacheId, sMap);
		} else {
			transactions.add(tran1);
		}
		if(tran2 == null){
			tran2 = transactions.create();
			tran2.setID( getNumber("Integer", 4) );
			tran2.setType( 1 );
			tran2.setCategory( 1 );
			tran2.setDescription( "Rollover Principal/Interest" );
			tran2.setDate( transactionDate );
			tran2.setAmount( getNumber("Double", 3) );
			tran2.setFixedDepositRate( 3.14159f );
			tran2.setInstrumentNumber( inst.getInstrumentNumber() );
			tran2.setInstrumentBankName( inst.getInstrumentBankName() );
			Map<String, Transaction> sMap = fdInstrumentTrxnsMap.get(cacheId);
			if(sMap == null) {
				sMap = new HashMap<String, Transaction>();
			}
        	sMap.put(key, tran2);
        	fdInstrumentTrxnsMap.put(cacheId, sMap);
		}else {
			transactions.add(tran2);
		}
		
		transactions.setLocale( locale );
		transactions.setDateFormat( inst.getDateFormat() );

		return transactions;
	}

	@Override
	public AccountHistories getHistory(Account account, Calendar start,
			Calendar end, HashMap extra) throws BSException {
		if ( account == null ) {
			throw new BSException( BSException.BSE_ACCOUNT_NOT_EXISTS );
		}

		java.util.Locale locale = account.getLocale();
		DateTime historyDate = start != null ? new DateTime( start, locale ) : new DateTime( locale );

		//Since BankSim does not support Account History operations
		//Return AccountHistories with meaningful but hard-coded data values
		AccountHistories histories = new AccountHistories();
		AccountHistory history = null;
		String key = account.getID();
		String cacheId = REALTIME_ACCOUNT_GETHISTORY;

		Map<String, AccountHistory> map = historyMap.get(cacheId);
		if(map != null) {
			history = map.get(key);
		}

		if(history == null){
			history = histories.create();

			history.setAccountNumber( account.getNumber() );
			history.setAccountID( account.getID() );
			history.setBankID( account.getBankID() );
			history.setRoutingNumber( account.getRoutingNum() );
			history.setHistoryDate( historyDate );

			history.setOpeningLedger( getNumber("Integer", 4) );
			history.setAvgOpeningLedgerMTD( getNumber("Integer", 4) );
			history.setAvgOpeningLedgerYTD( getNumber("Integer", 4) );
			history.setClosingLedger( getNumber("Integer", 4) );
			history.setAvgClosingLedgerMTD( getNumber("Integer", 5) );
			history.setAvgMonth( getNumber("Integer", 4) );
			history.setAggregateBalAdjustment( getNumber("Integer", 3) );
			history.setAvgClosingLedgerYTDPrevMonth( getNumber("Integer", 4) );
			history.setAvgClosingLedgerYTD( getNumber("Integer", 4) );
			history.setCurrentLedger(getNumber("Integer", 4) );
			history.setNetPositionACH( getNumber("Integer", 5) );
			history.setOpenAvailSameDayACHDTC( getNumber("Integer", 3) );
			history.setOpeningAvail( getNumber("Integer", 3) );
			history.setAvgOpenAvailMTD( getNumber("Integer", 4) );
			history.setAvgOpenAvailYTD( getNumber("Integer", 4) );
			history.setAvgAvailPrevMonth( getNumber("Integer", 4) );
			history.setDisbursingOpeningAvailBal( getNumber("Integer", 4) );
			history.setClosingAvail( getNumber("Integer", 3) );
			history.setAvgClosingAvailMTD( getNumber("Integer", 3) );
			history.setAvgClosingAvailPrevMonth( getNumber("Integer", 5) );
			history.setAvgClosingAvailYTDPrevMonth( getNumber("Integer", 5) );
			history.setAvgClosingAvailYTD( getNumber("Integer", 3) );
			history.setLoanBal( getNumber("Integer", 4) );
			history.setTotalInvestmentPosition( getNumber("Integer", 3) );
			history.setCurrentAvailCRSSurpressed( getNumber("Integer", 4) );
			history.setCurrentAvail( getNumber("Integer", 5) );
			history.setAvgCurrentAvailMTD( getNumber("Integer", 4) );
			history.setAvgCurrentAvailYTD( getNumber("Integer", 3) );
			history.setTotalFloat( getNumber("Integer", 4));
			history.setTargetBal( getNumber("Integer", 4) );
			history.setAdjustedBal( getNumber("Integer", 4) );
			history.setAdjustedBalMTD( getNumber("Integer", 4) );
			history.setAdjustedBalYTD( getNumber("Integer", 4) );
			history.setZeroDayFloat( getNumber("Integer", 3) );
			history.setOneDayFloat( getNumber("Integer", 5) );
			history.setFloatAdjusted( getNumber("Integer", 4) );
			history.setTwoOrMoreDayFloat( getNumber("Integer", 3) );
			history.setThreeOrMoreDayFloat( getNumber("Integer", 5) );
			history.setAdjustmentToBal( getNumber("Integer", 3) );
			history.setAvgAdjustmentToBalMTD( getNumber("Integer", 4) );
			history.setAvgAdjustmentToBalYTD( getNumber("Integer", 5) );
			history.setFourDayFloat( getNumber("Integer", 3) );
			history.setFiveDayFloat( getNumber("Integer", 4) );
			history.setSixDayFloat( getNumber("Integer", 4) );
			history.setAvgOneDayFloatMTD( getNumber("Integer", 4) );
			history.setAvgOneDayFloatYTD( getNumber("Integer", 4) );
			history.setAvgTwoDayFloatMTD( getNumber("Integer", 5) );
			history.setAvgTwoDayFloatYTD( getNumber("Integer", 3));
			history.setTransferCalculation(getNumber("Integer", 3) );
			history.setTargetBalDeficiency( getNumber("Integer", 5) );
			history.setTotalFundingRequirement( getNumber("Integer", 4) );

			history.setLocale( account.getLocale() );
			history.setDateFormat( account.getDateFormat() );
			Map<String, AccountHistory> sMap = historyMap.get(cacheId);
			if(sMap == null) {
				sMap = new HashMap<String, AccountHistory>();
			}
			sMap.put(key, history);
			historyMap.put(cacheId, sMap);
		}else {
			histories.add(history);
		}

		return histories;
	}

	@Override
	public AccountSummaries getSummary(Account account, Calendar start,
			Calendar end, HashMap extra) throws BSException {
		if ( account == null ) {
			throw new BSException( BSException.BSE_ACCOUNT_NOT_EXISTS );
		}

		java.util.Locale locale = account.getLocale();
		DateTime summaryDate = start != null ? new DateTime( start, locale ) : new DateTime( locale );

		//Since BankSim does not support Account History operations
		//Return AccountHistories with meaningful but hard-coded data values
		AccountSummaries summaries = new AccountSummaries();

		int group = account.getAccountGroup();
		int sysType = Account.getAccountSystemTypeFromGroup( group );
		int accountType = account.getTypeValue();

		AccountSummary summary = null;
		String key = account.getID();
		String cacheId = REALTIME_ACCOUNT_SUMMARY;

        Map<String, AccountSummary> map = summaryMap.get(cacheId);
        if(map != null) {
        	summary = map.get(key);
        }
		if(summary == null){

			if ( ( sysType==Account.SYSTEM_TYPE_ASSET ) ) {
				AssetAcctSummary assetSummary = new AssetAcctSummary();

				assetSummary.setBookValue( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
				assetSummary.setMarketValue( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );

				summary = assetSummary;
			} else if ( ( sysType==Account.SYSTEM_TYPE_DEPOSIT ) || ( accountType == AccountTypes.TYPE_MONEY_MARKET )  ) {
				DepositAcctSummary depositSummary = new DepositAcctSummary();

				depositSummary.setTotalCredits( new Currency ( getNumber("Integer", 4), account.getCurrencyCode(), account.getLocale() ));
				depositSummary.setTotalCreditAmtMTD( new Currency (getNumber("Integer", 2) , account.getCurrencyCode(), account.getLocale() ));
				depositSummary.setCreditsNotDetailed( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setDepositsSubjectToFloat( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setTotalAdjCreditsYTD( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setTotalLockboxDeposits( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setTotalDebits( new Currency (getNumber("Integer", 4), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setTotalDebitAmtMTD( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setTodaysTotalDebits( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setTotalDebitsLessWireAndCharge( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setTotalAdjDebitsYTD( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setTotalDebitsExcludeReturns( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setImmedAvailAmt( new Currency (getNumber("Integer", 4), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setOneDayAvailAmt( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setMoreThanOneDayAvailAmt( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setAvailOverdraft( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setRestrictedCash( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setAccruedInterest( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setAccruedDividend( new Currency (getNumber("Integer", 4), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setTotalOverdraftAmt( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setNextOverdraftPmtDate( summaryDate );
				depositSummary.setInterestRate( 1.2004f );
				depositSummary.setOpeningLedger( new Currency (getNumber("Integer", 5), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setClosingLedger( new Currency (getNumber("Integer", 5), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setCurrentAvailBal( new Currency (getNumber("Integer", 5), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setLedgerBal( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setOneDayFloat( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setTwoDayFloat( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setTotalFloat( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setCurrentLedger( new Currency (getNumber("Integer", 5), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setInterestYTD( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
				depositSummary.setPriorYearInterest( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );

				summary = depositSummary;
			} else if ( sysType==Account.SYSTEM_TYPE_LOAN ) {
				LoanAcctSummary loanSummary = new LoanAcctSummary();

				loanSummary.setAvailCredit( new Currency ( getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
				loanSummary.setAmtDue( new Currency ( getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
				loanSummary.setInterestRate( 5.44440f );
				loanSummary.setDueDate( summaryDate );
				loanSummary.setMaturityDate( summaryDate );
				loanSummary.setAccruedInterest( new Currency ( getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
				loanSummary.setOpeningBal( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
				loanSummary.setCollateralDescription( "Beach House" );
				loanSummary.setPrincipalPastDue( new Currency ( getNumber("Integer", 4), account.getCurrencyCode(), account.getLocale() ) );
				loanSummary.setInterestPastDue( new Currency ( getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
				loanSummary.setLateFees( new Currency ( getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
				loanSummary.setNextPrincipalAmt( new Currency ( getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
				loanSummary.setNextInterestAmt( new Currency ( getNumber("Integer", 4), account.getCurrencyCode(), account.getLocale() ) );
				loanSummary.setOpenDate( summaryDate );
				loanSummary.setCurrentBalance( new Currency ( getNumber("Integer", 4), account.getCurrencyCode(), account.getLocale() ) );
				loanSummary.setNextPaymentDate( summaryDate );
				loanSummary.setNextPaymentAmt( new Currency ( getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
				loanSummary.setInterestYTD( new Currency ( getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
				loanSummary.setPriorYearInterest( new Currency ( getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
				loanSummary.setLoanTerm( "1" );
				loanSummary.setTodaysPayoff( new Currency ( getNumber("Integer", 1), account.getCurrencyCode(), account.getLocale() ) );
				loanSummary.setPayoffGoodThru( summaryDate );

				summary = loanSummary;
			} else if ( sysType==Account.SYSTEM_TYPE_CREDITCARD ) {
				CreditCardAcctSummary ccSummary = new CreditCardAcctSummary();

				ccSummary.setAvailCredit( new Currency ( getNumber("Integer", 4), account.getCurrencyCode(), account.getLocale() ) );
				ccSummary.setAmtDue( new Currency ( getNumber("Integer", 1), account.getCurrencyCode(), account.getLocale() ) );
				ccSummary.setInterestRate( 10.766f );
				ccSummary.setDueDate( summaryDate );
				ccSummary.setCardHolderName( "Brad Bowman" );
				ccSummary.setCardExpDate( summaryDate );
				ccSummary.setCreditLimit( new Currency ( getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
				ccSummary.setLastPaymentAmt( new Currency ( getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
				ccSummary.setNextPaymentMinAmt( new Currency ( getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
				ccSummary.setLastPaymentDate( summaryDate );
				ccSummary.setNextPaymentDue( summaryDate );
				ccSummary.setCurrentBalance( new Currency (getNumber("Integer", 5), account.getCurrencyCode(), account.getLocale() ) );
				ccSummary.setLastAdvanceDate( summaryDate );
				ccSummary.setLastAdvanceAmt( new Currency ( getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
				ccSummary.setPayoffAmount( new Currency ( getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );

				summary = ccSummary;
			}


			summary.setAccountNumber( account.getNumber() );
			summary.setAccountID( account.getID() );
			summary.setBankID( account.getBankID() );
			summary.setRoutingNumber( account.getRoutingNum() );

			summary.setSummaryDate( summaryDate );
			summary.setValueDate( summaryDate );

			summary.setLocale( account.getLocale() );
			summary.setDateFormat( account.getDateFormat() );
			Map sMap = summaryMap.get(cacheId);
        	if(sMap == null) {
        		sMap = new HashMap();
        	}
        	sMap.put(key, summary);
        	summaryMap.put(cacheId, sMap);
        	
        	
		}
		summaries.add( summary );

		return summaries;
	}

	@Override
	public ExtendedAccountSummaries getExtendedSummary(Account account,
			Calendar start, Calendar end, HashMap extra) throws BSException {
        if ( account == null ) {
            throw new BSException( BSException.BSE_ACCOUNT_NOT_EXISTS );
        }

        java.util.Locale locale = account.getLocale();
        DateTime summaryDate = start != null ? new DateTime( start, locale ) : new DateTime( locale );

        //Since BankSim does not support Account History operations
        //Return AccountHistories with meaningful but hard-coded data values
        ExtendedAccountSummaries summaries = new ExtendedAccountSummaries();
        ExtendedAccountSummary summary = null;
        String key = account.getID();
        String cacheId = REALTIME_EXTENDEDACCOUNT_GETSUMMARY;
        Map<String, ExtendedAccountSummary> map = extendedAccountSummaryMap.get(cacheId);
        if(map != null) {
        	summary = map.get(key);
        }
        if(summary == null){
        summary = summaries.create();

        summary.setAccountNumber( account.getNumber() );
        summary.setAccountID( account.getID() );
        summary.setBankID( account.getBankID() );
        summary.setRoutingNumber( account.getRoutingNum() );
        summary.setSummaryDate( summaryDate );

        summary.setImmedAvailAmt( new Currency ( getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ));
        summary.setOneDayAvailAmt( new Currency ( getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ));
        summary.setMoreThanOneDayAvailAmt( new Currency ( getNumber("Integer", 4), account.getCurrencyCode(), account.getLocale() ));
        summary.setValueDateTime( summaryDate );
        summary.setAmt( new Currency ( getNumber("Integer", 5), account.getCurrencyCode(), account.getLocale() ));
        summary.setSummaryType( 1 );

        summary.setLocale( account.getLocale() );
        summary.setDateFormat( account.getDateFormat() );
        Map<String, ExtendedAccountSummary> sMap = extendedAccountSummaryMap.get(cacheId);
        if(sMap ==null) {
        	sMap = new HashMap<String, ExtendedAccountSummary>();
        }
    	sMap.put(key, summary);
    	extendedAccountSummaryMap.put(cacheId, sMap);
        }else {
        	summaries.add(summary);
        }
        return summaries;
	}

	private String getNumber(String className, int maxPower) {
		String num = generateRandomNumber(className, 0, maxPower);
		return num;
	}

    private String generateRandomNumber (String className, int minPower, int maxPower)
    {
        String generatedString = "1";
        maxPower = maxPower > 6 ? 6 : maxPower;
        minPower = minPower > 6 ? 6 : minPower;
        if (minPower > maxPower) {
            minPower = 0;
            maxPower = 6;
        }
        minPower = minPower <= 0 ? 0 : (minPower);
        maxPower = maxPower <= 0 ? 0 : (maxPower);
        SecureRandom srandom = new SecureRandom();

        if ("Integer".equals(className)) {
            int leftLimit = 1 * (power(minPower));
            int rightLimit = 1 * (power(maxPower));
            int generatedInteger = leftLimit
                + (int)(srandom.nextFloat() * (rightLimit - leftLimit));
            generatedString = String.valueOf(generatedInteger);
        }
        else if ("Float".equals(className)) {
            float leftLimit = 1F * (power(minPower));
            float rightLimit = 1F * (power(maxPower));
            float generatedFloat = leftLimit
                + srandom.nextFloat() * (rightLimit - leftLimit);
            generatedString = String.valueOf(generatedFloat);
        }
        else if ("Long".equals(className)) {
            long leftLimit = 1L * (power(minPower));
            long rightLimit = 1L * (power(maxPower));
            long generatedLong = leftLimit
                + (long)(srandom.nextDouble() * (rightLimit - leftLimit));
            generatedString = String.valueOf(generatedLong);
        }
        else if ("Double".equals(className)) {
            double leftLimit = 1D * (power(minPower));
            double rightLimit = 1D * (power(maxPower));
            double generatedDouble = leftLimit
                + srandom.nextDouble() * (rightLimit - leftLimit);
            generatedString = String.valueOf(generatedDouble);
        }

        return generatedString;
    }

	private int power(int maxPower) {
		int result = 1;
		for(int i=0;i<maxPower;i++) {
			result = result*10;
		}
		return result;
	}
	
	@Override
	public void addBankAccount(Account account) throws BSException {

		// By default. There is no implementation to this method. This method will be called from  realtime banking service addAccount() method
	}

	@Override
	public void deleteBankAccount(Account account) throws BSException {

		// By default. There is no implementation to this method. This method will be called from  realtime banking service deleteAccount() method
	}

	@Override
	public void updateBankAccount(Account account) throws BSException {
		bankingBackend.updateAccount(account);
	}

	@Override
	public void getBankAccount(Account account) throws BSException {

		// By default. There is no implementation to this method. This method will be called from  realtime banking service getAccount() method
	}

	@Override
	public AccountHistories getHistory(Accounts accounts, Calendar start,
			Calendar end, String batchSize, HashMap extra) throws BSException {
		// this API is not supported in  Real time Banking Service.
		throw new BSException(BSException.BSE_NOT_SUPPORETD,"getHistory API not supported.");
	}

	@Override
	public AccountSummaries getSummary(Accounts accounts, Calendar start,
			Calendar end, String batchSize, HashMap extra) throws BSException {
		AccountSummaries summaries = new AccountSummaries();
		for(int i=0;i<accounts.size();i++){
			Account account = (Account)accounts.get(i);
			AccountSummaries sum = createSummary(account, start, end, extra);
			summaries.addAll(sum);
		}
		return summaries;
	}

	private AccountSummaries createSummary(Account account, Calendar start,
			Calendar end, HashMap extra) throws BSException {
		if ( account == null ) {
            throw new BSException( BSException.BSE_ACCOUNT_NOT_EXISTS );
        }

        java.util.Locale locale = account.getLocale();
        DateTime summaryDate = start != null ? new DateTime( start, locale ) : new DateTime( locale );

        //Since BankSim does not support Account History operations
        //Return AccountHistories with meaningful but hard-coded data values
        AccountSummaries summaries = new AccountSummaries();

        int group = account.getAccountGroup();
        int sysType = Account.getAccountSystemTypeFromGroup( group );
        int accountType = account.getTypeValue();

        AccountSummary summary = null;
        String key = account.getID();
        String cacheId = REALTIME_ACCOUNT_SUMMARY;

        Map<String, AccountSummary> map = summaryMap.get(cacheId);
        if(map != null) {
        	summary = map.get(key);
        }
		
        if(summary == null){
        	if ( ( sysType==Account.SYSTEM_TYPE_ASSET ) ) {
        		AssetAcctSummary assetSummary = new AssetAcctSummary();

        		assetSummary.setBookValue( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		assetSummary.setMarketValue( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );

        		summary = assetSummary;

        	} else if ( ( sysType==Account.SYSTEM_TYPE_DEPOSIT ) || ( accountType == AccountTypes.TYPE_MONEY_MARKET )  ) {
        		DepositAcctSummary depositSummary = new DepositAcctSummary();

        		depositSummary.setTotalCredits( new Currency ( getNumber("Integer", 4), account.getCurrencyCode(), account.getLocale() ));
        		depositSummary.setTotalCreditAmtMTD( new Currency (getNumber("Integer", 2) , account.getCurrencyCode(), account.getLocale() ));
        		depositSummary.setCreditsNotDetailed( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setDepositsSubjectToFloat( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setTotalAdjCreditsYTD( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setTotalLockboxDeposits( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setTotalDebits( new Currency (getNumber("Integer", 4), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setTotalDebitAmtMTD( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setTodaysTotalDebits( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setTotalDebitsLessWireAndCharge( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setTotalAdjDebitsYTD( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setTotalDebitsExcludeReturns( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setImmedAvailAmt( new Currency (getNumber("Integer", 4), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setOneDayAvailAmt( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setMoreThanOneDayAvailAmt( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setAvailOverdraft( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setRestrictedCash( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setAccruedInterest( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setAccruedDividend( new Currency (getNumber("Integer", 4), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setTotalOverdraftAmt( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setNextOverdraftPmtDate( summaryDate );
        		depositSummary.setInterestRate( 1.2004f );
        		depositSummary.setOpeningLedger( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setClosingLedger( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setCurrentAvailBal( new Currency (getNumber("Integer", 4), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setLedgerBal( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setOneDayFloat( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setTwoDayFloat( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setTotalFloat( new Currency (getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setCurrentLedger( new Currency (getNumber("Integer", 5), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setInterestYTD( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
        		depositSummary.setPriorYearInterest( new Currency (getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );

        		summary = depositSummary;
        	} else if ( sysType==Account.SYSTEM_TYPE_LOAN ) {
        		LoanAcctSummary loanSummary = new LoanAcctSummary();

        		loanSummary.setAvailCredit( new Currency ( getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		loanSummary.setAmtDue( new Currency ( getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
        		loanSummary.setInterestRate( 5.44440f );
        		loanSummary.setDueDate( summaryDate );
        		loanSummary.setMaturityDate( summaryDate );
        		loanSummary.setAccruedInterest( new Currency ( getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
        		loanSummary.setOpeningBal( new Currency ( getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
        		loanSummary.setCollateralDescription( "Beach House" );
        		loanSummary.setPrincipalPastDue( new Currency ( getNumber("Integer", 4), account.getCurrencyCode(), account.getLocale() ) );
        		loanSummary.setInterestPastDue( new Currency ( getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
        		loanSummary.setLateFees( new Currency ( getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		loanSummary.setNextPrincipalAmt( new Currency ( getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
        		loanSummary.setNextInterestAmt( new Currency ( getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		loanSummary.setOpenDate( summaryDate );
        		loanSummary.setCurrentBalance( new Currency ( getNumber("Integer", 4), account.getCurrencyCode(), account.getLocale() ) );
        		loanSummary.setNextPaymentDate( summaryDate );
        		loanSummary.setNextPaymentAmt( new Currency ( getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		loanSummary.setInterestYTD( new Currency ( getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
        		loanSummary.setPriorYearInterest( new Currency ( getNumber("Integer", 2), account.getCurrencyCode(), account.getLocale() ) );
        		loanSummary.setLoanTerm( "1" );
        		loanSummary.setTodaysPayoff( new Currency ( getNumber("Integer", 1), account.getCurrencyCode(), account.getLocale() ) );
        		loanSummary.setPayoffGoodThru( summaryDate );

        		summary = loanSummary;
        	} else if ( sysType==Account.SYSTEM_TYPE_CREDITCARD ) {
        		CreditCardAcctSummary ccSummary = new CreditCardAcctSummary();

        		ccSummary.setAvailCredit( new Currency ( getNumber("Integer", 4), account.getCurrencyCode(), account.getLocale() ) );
        		ccSummary.setAmtDue( new Currency ( getNumber("Integer", 1), account.getCurrencyCode(), account.getLocale() ) );
        		ccSummary.setInterestRate( 10.766f );
        		ccSummary.setDueDate( summaryDate );
        		ccSummary.setCardHolderName( "Brad Bowman" );
        		ccSummary.setCardExpDate( summaryDate );
        		ccSummary.setCreditLimit( new Currency ( getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		ccSummary.setLastPaymentAmt( new Currency ( getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		ccSummary.setNextPaymentMinAmt( new Currency ( getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		ccSummary.setLastPaymentDate( summaryDate );
        		ccSummary.setNextPaymentDue( summaryDate );
        		ccSummary.setCurrentBalance( new Currency ( getNumber("Integer", 5), account.getCurrencyCode(), account.getLocale() ) );
        		ccSummary.setLastAdvanceDate( summaryDate );
        		ccSummary.setLastAdvanceAmt( new Currency ( getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );
        		ccSummary.setPayoffAmount( new Currency ( getNumber("Integer", 3), account.getCurrencyCode(), account.getLocale() ) );

        		summary = ccSummary;
        	}


        	summary.setAccountNumber( account.getNumber() );
        	summary.setAccountID( account.getID() );
        	summary.setBankID( account.getBankID() );
        	summary.setRoutingNumber( account.getRoutingNum() );

        	summary.setSummaryDate( summaryDate );
        	summary.setValueDate( summaryDate );

        	summary.setLocale( account.getLocale() );
        	summary.setDateFormat( account.getDateFormat() );
        	Map sMap = summaryMap.get(cacheId);
        	if(sMap == null) {
        		sMap = new HashMap();
        	}
        	sMap.put(key, summary);
        	summaryMap.put(cacheId, sMap);
        }
        summaries.add( summary );

        return summaries;
	}
	
	public String getBankingBackendType() {
		return com.sap.banking.common.constants.BankingConstants.BACKEND_TYPE_NAME_BANKSIM;
	}
	
	public void close()
	{
		BankSim.close();
	}

	@Override
	public User signOn(SecureUser sUser, String userID, String password, Map<String, Object> extraMap)
			throws BSException {
		return signOn(userID, password);
	}

	@Override
	public Enumeration getAccounts(SecureUser sUser, User customer, Map<String, Object> extraMap) throws BSException {
		return getAccounts(customer);
	}

	@Override
	public Transactions getAccountTransactions(SecureUser sUser, Account account, PagingContext pagingContext,
			Map<String, Object> extraMap) throws BSException {
		HashMap<String, Object> extra = checkEmptyExtraMap(extraMap);
		return getAccountTransactions(account, pagingContext, extra);
	}

	@Override
	public AccountSummaries getSummary(SecureUser sUser, Accounts accounts, Calendar start, Calendar end,
			String batchSize, Map<String, Object> extraMap) throws BSException {
		HashMap<String, Object> extra = checkEmptyExtraMap(extraMap);
		return getSummary(accounts, start, end, batchSize, extra);
	}
	
	

	/**
	 * Method to check if passed Map object is null or not. 
	 * If null then this method will return a new instance of HashMap.
	 * If not null, then this method will create instance of HashMap, which contains all entries from the passed map.
	 *
	 * @param extra the extra
	 * @return Hash<String, Object>
	 */
	protected HashMap<String, Object> checkEmptyExtraMap(Map<String, Object> extra) {
		HashMap<String, Object> extraMap = null;
		if (null == extra) {
			extraMap = new HashMap<String, Object>();
		} else {
			if(extra instanceof HashMap){
				extraMap = (HashMap<String, Object>) extra;
			}else{
				extraMap =  new HashMap<String, Object>(extra);		
			}
			
		}
		return extraMap;
	}

	@Override
	public AccountSummaries getSummary(SecureUser sUser, Account account, Calendar start, Calendar end,
			Map<String, Object> extraMap) throws BSException {
		HashMap<String, Object> extra = checkEmptyExtraMap(extraMap);
		return getSummary(account, start, end, extra);
	}

	@Override
	public AccountHistories getHistory(SecureUser sUser, Account account, Calendar start, Calendar end,
			Map<String, Object> extraMap) throws BSException {
		HashMap<String, Object> extra = checkEmptyExtraMap(extraMap);
		return getHistory(account, start, end, extra);
	}

	@Override
	public AccountHistories getHistory(SecureUser sUser, Accounts accounts, Calendar start, Calendar end,
			String batchSize, Map<String, Object> extraMap) throws BSException {
		HashMap<String, Object> extra = checkEmptyExtraMap(extraMap);
		return getHistory(accounts, start, end, batchSize, extra);
	}

	@Override
	public void addLoanContract(SecureUser sUser, Map<String, String> loanContractMap, Map<String, Object> extraMap)
			throws BSException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public List<Product> getCoreBankingProducts(SecureUser sUser, Map<String, Object> extraMap) throws BSException {
		// TODO Auto-generated method stub
		return null;
	}
}
