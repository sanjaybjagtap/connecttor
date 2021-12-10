// Copyright (c) 2001 Financial Fusion, Inc.  All Rights Reserved.

package com.ffusion.ffs.bpw.fulfill.orcc;


import java.io.BufferedOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Vector;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import com.ffusion.ffs.bpw.db.Customer;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.db.Payee;
import com.ffusion.ffs.bpw.handler.OSGIUtil;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.CustPayeeRslt;
import com.ffusion.ffs.bpw.interfaces.CustomerInfo;
import com.ffusion.ffs.bpw.interfaces.CustomerPayeeInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.FulfillmentInfo;
import com.ffusion.ffs.bpw.interfaces.PayeeInfo;
import com.ffusion.ffs.bpw.interfaces.PayeeRouteInfo;
import com.ffusion.ffs.bpw.interfaces.PmtInfo;
import com.ffusion.ffs.bpw.interfaces.PmtTrnRslt;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.handlers.FulfillmentAPI;
import com.ffusion.ffs.bpw.master.CommonProcessor;
import com.ffusion.ffs.bpw.serviceMsg.BPWMsgBroker;
import com.ffusion.ffs.bpw.util.BPWRegistryUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSRegistry;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.EnumCheckType;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.EnumDebitResultCode;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.EnumDeviceType;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.EnumMerchStatus;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.EnumRecordType;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.EnumTransType;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeDPRRQ;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeDPRRQRecord;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeELFRS;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFBanktRs;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFCustAdd;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFCustChange;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFCustDel;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFCustInfo;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFCustRq;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFCustRqUn;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFCustRs;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFHist2Add;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFHist2Rs;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFHist2RsUn;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFLinkAdd;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFLinkChange;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFLinkDel;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFLinkInfo;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFLinkRq;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFLinkRqUn;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFLinkRs;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFLinkRsUn;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFMailRq;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFMailRs;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFMerchAdd;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFMerchChange;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFMerchDel;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFMerchInfo;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFMerchKeyChange;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFMerchRq;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFMerchRqUn;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFMerchRs;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFMerchRsUn;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFPinqRs;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFRQ;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFRS;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFRecordHeader;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFReissueRs;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeRecordCounter;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeRecordError;
import com.sap.banking.io.beans.File;
import com.sap.banking.io.beans.FileInputStream;
import com.sap.banking.io.beans.FileOutputStream;
import com.sap.banking.io.provider.interfaces.FileHandlerProvider;

///////////////////////////////////////////////////////////////////////////////
// Main class of the ORCC connector.
///////////////////////////////////////////////////////////////////////////////
public class ORCCHandler implements FulfillmentAPI
{
	// Static variables
	private static String 	_cachePath 	= ORCCConstants.DEFAULT_ORCC_CACHE_PATH;
	//private static String 	_importDir 	= ORCCConstants.DEFAULT_IMPORT_DIR;
	private static String 	_exportDir 	= ORCCConstants.DEFAULT_EXPORT_DIR;
	private static BPWMsgBroker _mb 		= null;
	private static String 	_fiID		= null;
	private static String 	_frontID 	= null;

	private static File 	_dprFile 	= null;
	private static File 	_mlfFile 	= null;
	private static FileOutputStream _mlfFileOut = null;
	private static FileOutputStream _dprFileOut = null;
	private static BufferedOutputStream _dprOut = null;
	private static BufferedOutputStream _mlfOut = null;
	private static int 		_dprCount 	= 0;
	private static int 		_mlfCount 	= 0;
	private static TypeMLFRQ 	_mlfRq 		= null;
	private static TypeDPRRQ 	_dprRq 		= null;
	private static Vector 	_errInfo 	= new Vector( 20, 20 );
	private static HashMap	_merchTable	= new HashMap();
	private static HashMap	_linkTable	= new HashMap();
	private static HashMap	_custTable	= new HashMap();
	private static Object	_mutex		= new Object();
	private static boolean	_locked		= false;

	// get routeID for EnforcePayment option
	private static int _routeID = -1;

	// get properConfig for EnforcePayment option
	private static PropertyConfig _propConfig 	= null;


	private static com.ffusion.ffs.bpw.master.BackendProcessor _backendProcessor;
	// Static initializer
	static void init() {
		try{
			_propConfig = (PropertyConfig)FFSRegistry.lookup(BPWResource.PROPERTYCONFIG);

			if( _backendProcessor == null ) {
				_backendProcessor = new com.ffusion.ffs.bpw.master.BackendProcessor();
			}

			getProperties();
			createMBInstance();
			ORCCUtil.init();
		} catch( Exception e ) {
			ORCCUtil.log( e, "ORCC handler failed to initialize!" );
		}
	}

	private FileHandlerProvider fileHandlerProvider;
	
	public void setFileHandlerProvider(FileHandlerProvider fileHandlerProvider) {
		this.fileHandlerProvider = fileHandlerProvider;
		try {
			afterPropertiesSet();
		} catch (Exception e) {
//			e.printStackTrace();
		}
	}
	
	public void afterPropertiesSet() throws Exception {
		// get route ID
		FulfillmentInfo fulfill = BPWRegistryUtil.getFulfillmentInfo( this.getClass() );
		if( fulfill == null ) {
			throw new Exception( "FulfillmentInfo not found for "
					+ this.getClass().getName() );
		}
		ORCCDBAPI.setFulfillment( fulfill );
		_routeID = fulfill.RouteID;

		// Create the cache if it doesn't exist
		File cache = new File( _cachePath );
		cache.setFileHandlerProvider(fileHandlerProvider);
		try{
			if( !cache.exists() ) {
				cache.mkdir();
				ORCCUtil.log( "ORCC cache directory created at "+cache.getAbsolutePath() );
			} else if( !cache.isDirectory() ) {
				ORCCUtil.log( "ORCC cache path "+cache.getAbsolutePath()
				+" is not a directory. " );
			}

			// init ORCCUtil
			ORCCUtil.init();
		} catch ( Exception e ) {
			ORCCUtil.log( e.toString());
		}
	}
	
	public void initialize() throws Exception
	{

	}


	///////////////////////////////////////////////////////////////////////////
	// Reads various settings from the properties file.
	///////////////////////////////////////////////////////////////////////////
	private static void getProperties()
	{
		// Get the import & export directory properties
		_exportDir 	= ORCCUtil.getProperty( DBConsts.ORCC_EXPORT_DIR,
				ORCCConstants.DEFAULT_EXPORT_DIR );
		//_importDir 	= ORCCUtil.getProperty( DBConsts.ORCC_IMPORT_DIR,
		//			ORCCConstants.DEFAULT_IMPORT_DIR );
		_fiID		= ORCCUtil.getProperty( DBConsts.ORCC_FI_ID );

		// Padding _fiID to be length 4
		int len = _fiID.length();
		if( len==1 ) {
			_fiID = "000"+_fiID;
		} else if( len==2 ) {
			_fiID = "00"+_fiID;
		} else if( len==3) {
			_fiID = "0"+_fiID;
		}

		_frontID	= ORCCUtil.getProperty( DBConsts.ORCC_FRONT_ID,
				ORCCConstants.DEFAULT_ORCC_FRONT_ID );
		_cachePath 	= ORCCUtil.getProperty( DBConsts.ORCC_CACHE_PATH,
				ORCCConstants.DEFAULT_ORCC_CACHE_PATH );
	}


	///////////////////////////////////////////////////////////////////////////
	// This method is called to do the prep work for this connector
	///////////////////////////////////////////////////////////////////////////
	public void start() throws Exception
	{
		ORCCUtil.log( "Starting ORCC connector...");
		init();
		ORCCUtil.log( "ORCC connector started");
	}


	///////////////////////////////////////////////////////////////////////////
	// This method is called to do the housekeeping work for this connector
	///////////////////////////////////////////////////////////////////////////
	public void shutdown() throws Exception
	{
		ORCCUtil.log("Shutting down ORCC connector ... ");
		_mb=null;
		ORCCUtil.log("ORCC connector shut down ");
	}

	public void addPayees(PayeeInfo[] payees, FFSConnectionHolder dbh )
			throws Exception
	{
		ORCCUtil.log("addPayees start");
		try {
			addORCCMerchants( payees, dbh );
		} catch (Exception e ) {
			unlock();
			throw e;
		}
		ORCCUtil.log("addPayees end");
	}

	public void modPayees(PayeeInfo[] payees, FFSConnectionHolder dbh )
			throws Exception
	{
	}
	public void deletePayees(PayeeInfo[] payees, FFSConnectionHolder dbh )
			throws Exception
	{
	}
	public void startPayeeBatch( FFSConnectionHolder dbh )
			throws Exception
	{
	}
	public void endPayeeBatch( FFSConnectionHolder dbh )
			throws Exception
	{
	}



	///////////////////////////////////////////////////////////////////////////
	// Set lock so the scheduler and file checker do not run at the same time
	///////////////////////////////////////////////////////////////////////////
	static final void lock() {
		synchronized ( _mutex ) {
			if( _locked ) {
				try{
					_mutex.wait();
				}catch(InterruptedException e ) {
				}
			} else {
				_locked = true;
			}
		}
	}


	///////////////////////////////////////////////////////////////////////////
	// Release lock so the other one can run
	///////////////////////////////////////////////////////////////////////////
	static final void unlock() {
		synchronized ( _mutex ) {
			_locked = false;
			_mutex.notify();
		}
	}

	///////////////////////////////////////////////////////////////////////////
	// This is the first method being called when the schedule is executed
	///////////////////////////////////////////////////////////////////////////
	public void startPmtBatch( FFSConnectionHolder dbh ) throws Exception
	{
		ORCCUtil.log( "ORCC FulfillmentAPI.startPmtBatch() start." );
		lock();
		// Do a check to avoid NullPointerException when MB init fails
		if( _mb==null ) {
			Exception e = new Exception( "Message broker "
					+ "initialization has failed." );
			ORCCUtil.log( e, "Unable to process ORCC requests." );
			throw e;
		}

		try{
			openCache();
		} catch ( Exception e ) {
			ORCCUtil.log( "Failed to create cache for requests to ORCC");
			throw e;
		}
		_mlfCount = 0;
		_dprCount = 0;
		_errInfo.removeAllElements();

		_mlfRq = new TypeMLFRQ();
		_mlfRq.MLFCustRq = new TypeMLFCustRq();
		_mlfRq.MLFCustRq.MLFCustRqUnExists = false;

		_mlfRq.MLFMerchRq = new TypeMLFMerchRq();
		_mlfRq.MLFMerchRq.MLFMerchRqUnExists = false;

		_mlfRq.MLFLinkRq = new TypeMLFLinkRq();
		_mlfRq.MLFLinkRq.MLFLinkRqUnExists = false;

		_mlfRq.MLFMailRq = new TypeMLFMailRq();
		_mlfRq.MLFMailRq.MLFMailRqUnExists = false;

		_dprRq = new TypeDPRRQ();

		ORCCUtil.log( "ORCC FulfillmentAPI.startPmtBatch() done." );
	}


	///////////////////////////////////////////////////////////////////////////
	// This is the last method being called when the schedule is executed
	///////////////////////////////////////////////////////////////////////////
	public void endPmtBatch( FFSConnectionHolder dbh ) throws Exception
	{
		ORCCUtil.log( "ORCC FulfillmentAPI.endPaymentBatch() start." );
		// Do nothing
		unlock();

		// clear the cache
		_custTable.clear();
		_linkTable.clear();
		_merchTable.clear();

		try{
			addCountRecords();
			writeFiles();
			closeCache();
		} catch ( Exception e ) {
			ORCCUtil.log( "Failed to close cache for requests to ORCC");
			throw e;
		}
		ORCCUtil.log( "ORCC FulfillmentAPI.endPaymentBatch() done." );
	}

	public void startCustomerPayeeBatch( FFSConnectionHolder dbh ) throws Exception
	{
		// Do nothing
		ORCCUtil.log( "ORCC FulfillmentAPI.startCustomerPayeeBatch() done." );
	}

	public void endCustomerPayeeBatch( FFSConnectionHolder dbh ) throws Exception
	{
		ORCCUtil.log( "ORCC FulfillmentAPI.endCustomerPayeeBatch() start." );
		ORCCUtil.log( "ORCC FulfillmentAPI.endCustomerPayeeBatch() done." );
	}

	public void addCustomerPayees( CustomerPayeeInfo[] info,
			PayeeInfo[] payees,
			FFSConnectionHolder dbh )
					throws Exception
	{
		ORCCUtil.log( "addCustomerPayee() start: number of customer-payee links="
				+((info==null) ?0 :info.length) );
		try {
			addCustomerPayeeLinks( info, dbh );
		} catch (Exception e ) {
			unlock();
			throw e;
		}
		ORCCUtil.log( "addCustomerPayee() end" );
	}

	public void modCustomerPayees( CustomerPayeeInfo[] info,
			PayeeInfo[] payees,
			FFSConnectionHolder dbh )
					throws Exception
	{
		ORCCUtil.log( "modCustomerPayee() start: number of customer-payee links="
				+((info==null) ?0 :info.length) );
		try {
			modifyCustomerPayeeLinks( info, dbh );
		} catch (Exception e ) {
			unlock();
			throw e;
		}
		ORCCUtil.log( "modCustomerPayee() end" );
	}

	public void deleteCustomerPayees( CustomerPayeeInfo[] info,
			PayeeInfo[] payees,
			FFSConnectionHolder dbh )
					throws Exception
	{
		ORCCUtil.log( "deleteCustomerPayee() start: number of customer-payee links="
				+((info==null) ?0 :info.length) );
		try{
			deleteCustomerPayeeLinks( info, dbh );
		} catch (Exception e ) {
			unlock();
			throw e;
		}
		ORCCUtil.log( "deleteCustomerPayee() end" );
	}


	public void startCustBatch( FFSConnectionHolder dbh ) throws Exception
	{
	}


	public void endCustBatch( FFSConnectionHolder dbh ) throws Exception
	{
	}


	public int addCustomers( CustomerInfo[] customers, FFSConnectionHolder dbh )
			throws Exception
	{
		ORCCUtil.log( "ORCC FulfillmentAPI.addCustomers() start.", FFSConst.PRINT_DEV );
		int len = customers.length;
		try {
			if( customers==null || customers.length<=0 ) return 0;
			TypeMLFRecordHeader header = getRecordHeader();

			TypeMLFCustRqUn[] custRqUn = null;
			int pos = 0;
			if( _mlfRq.MLFCustRq.MLFCustRqUnExists ) {
				pos = _mlfRq.MLFCustRq.MLFCustRqUn.length;
				custRqUn = new TypeMLFCustRqUn[pos+len];
				for( int i=0; i<pos; ++i ) custRqUn[i] = _mlfRq.MLFCustRq.MLFCustRqUn[i];
			} else {
				custRqUn = new TypeMLFCustRqUn[len];
			}
			_mlfRq.MLFCustRq.MLFCustRqUn = custRqUn;
			_mlfRq.MLFCustRq.MLFCustRqUnExists = custRqUn.length>0;

			TypeMLFCustAdd cust = null;
			TypeMLFCustInfo info = null;
			int i;
			for( i=0; i<len; ++i ) {
				if( customers[i]==null ) continue;
				custRqUn[pos] = new TypeMLFCustRqUn();
				cust = new TypeMLFCustAdd();
				custRqUn[pos].__memberName = "MLFCustAdd";
				custRqUn[pos++].MLFCustAdd = cust;

				cust.MLFRecordHeader = header;
				info = new TypeMLFCustInfo( );
				cust.MLFCustInfo = info;

				// Here, get ORCC customerinfo out from other
				// (non bpw_customer) tables datbase first 
				ORCCDBAPI.getORCCCustomerInfo( customers[i], info, dbh );
				customerInfo2MLFCustomerInfo( customers[i], info );

				// Store into the _custTable
				_custTable.put( customers[i].customerID, info );

				// increment count
				++_mlfCount;
			}
		} catch (Exception e ) {
			unlock();
			throw e;
		}
		ORCCUtil.log( "ORCC FulfillmentAPI.addCustomers() done.", FFSConst.PRINT_DEV );

		return len;
	}


	public int modifyCustomers( CustomerInfo[] customers, FFSConnectionHolder dbh )
			throws Exception
	{
		ORCCUtil.log( "ORCC FulfillmentAPI.modifyCustomers() start.", FFSConst.PRINT_DEV );
		int len = customers.length;
		try{
			if( customers==null || customers.length<=0 ) return 0;
			TypeMLFRecordHeader header = getRecordHeader();

			TypeMLFCustRqUn[] custRqUn = null;
			int pos = 0;
			if( _mlfRq.MLFCustRq.MLFCustRqUnExists ) {
				pos = _mlfRq.MLFCustRq.MLFCustRqUn.length;
				custRqUn = new TypeMLFCustRqUn[pos+len];
				for( int i=0; i<pos; ++i ) custRqUn[i] = _mlfRq.MLFCustRq.MLFCustRqUn[i];
			} else {
				custRqUn = new TypeMLFCustRqUn[len];
			}

			_mlfRq.MLFCustRq.MLFCustRqUn = custRqUn;
			_mlfRq.MLFCustRq.MLFCustRqUnExists = custRqUn.length>0;

			TypeMLFCustChange cust = null;
			TypeMLFCustInfo info = null;
			int i;
			for( i=0; i<len; ++i ) {
				if( customers==null ) continue;
				cust = new TypeMLFCustChange();

				custRqUn[pos] = new TypeMLFCustRqUn();
				custRqUn[pos].__memberName = "MLFCustChange";
				custRqUn[pos++].MLFCustChange = cust;

				cust.MLFRecordHeader = header;
				info = new TypeMLFCustInfo( );
				cust.MLFCustInfo = info;

				// Here, get ORCC customerinfo out from other
				// (non bpw_customer) tables datbase first 
				ORCCDBAPI.getORCCCustomerInfo( customers[i], info, dbh );
				customerInfo2MLFCustomerInfo( customers[i], info );

				// Store into the _custTable
				_custTable.put( customers[i].customerID, info );

				// increment count
				++_mlfCount;
			}
		}catch( Exception e ) {
			unlock();
			throw e;
		}
		ORCCUtil.log( "ORCC FulfillmentAPI.modifyCustomers() done.", FFSConst.PRINT_DEV );

		return len;
	}


	public int deleteCustomers( CustomerInfo[] customers, FFSConnectionHolder dbh )
			throws Exception
	{
		ORCCUtil.log( "ORCC FulfillmentAPI.deleteCustomers() start.", FFSConst.PRINT_DEV );
		int len = customers.length;
		try {
			if( customers==null || customers.length<=0 ) return 0;
			TypeMLFRecordHeader header = getRecordHeader();

			TypeMLFCustRqUn[] custRqUn = null;
			int pos = 0;
			if( _mlfRq.MLFCustRq.MLFCustRqUnExists ) {
				pos = _mlfRq.MLFCustRq.MLFCustRqUn.length;
				custRqUn = new TypeMLFCustRqUn[pos+len];
				for( int i=0; i<pos; ++i ) custRqUn[i] = _mlfRq.MLFCustRq.MLFCustRqUn[i];
			} else {
				custRqUn = new TypeMLFCustRqUn[len];
			}

			_mlfRq.MLFCustRq.MLFCustRqUn = custRqUn;
			_mlfRq.MLFCustRq.MLFCustRqUnExists = custRqUn.length>0;

			TypeMLFCustDel cust = null;
			TypeMLFCustInfo info = null;
			for (int i = 0; i < len; ++i) {
				if (customers == null) {
					continue;
				}
				cust = new TypeMLFCustDel();

				custRqUn[pos] = new TypeMLFCustRqUn();
				custRqUn[pos].__memberName = "MLFCustDel";
				custRqUn[pos++].MLFCustDel = cust;

				cust.MLFRecordHeader = header;
				info = new TypeMLFCustInfo();
				cust.MLFCustInfo = info;

				// Here, initialize the ORCCCustomerFields into "fields"
				// Set deletion date as current date
				String dateStr = ORCCUtil
						.getDateString(ORCCUtil.ORCC_DATE_FORMAT);
				info.DateDeleted = ORCCUtil.getDateValue(dateStr);

				// Here, get ORCC customerinfo out from other
				// (non bpw_customer) tables datbase first
				ORCCDBAPI.getORCCCustomerInfo(customers[i], info, dbh);
				customerInfo2MLFCustomerInfo(customers[i], info);

				// Store into the _custTable
				_custTable.put(customers[i].customerID, info);

				// increment count
				++_mlfCount;
			}
		} catch (Exception e ) {
			unlock();
			throw e;
		}
		ORCCUtil.log( "ORCC FulfillmentAPI.deleteCustomers() done.", FFSConst.PRINT_DEV );

		return len;
	}


	public void addPayments( PmtInfo[] pmtInfo,
			PayeeRouteInfo[] routeInfo,
			FFSConnectionHolder dbh )
					throws Exception
	{
		ORCCUtil.log( "ORCC FulfillmentAPI.addPayments() start.", FFSConst.PRINT_DEV );
		try{
			if( pmtInfo == null || pmtInfo.length<=0 ) return;
			int len = pmtInfo.length;
			TypeDPRRQRecord records[] = new TypeDPRRQRecord[len];
			for( int i=0; i<len; ++i ) {
				records[i] = new TypeDPRRQRecord();
				records[i].FIID = _fiID;
				// Get ORCC specific payment fields from DB, map into
				// " DPRRQRecord"

				// Get ORCC specific link fields from DB and fill into "TypeMLFLinkInfo"

				// Only when customer is not new, we try to get ORCCCustomerID and
				// ORCCAcctID out from ORCCCustomerCrossReference table
				// Only when payee is not new, we try to get ExtdPayeeID and
				// out from bpw payee table
				// Only when custpayee is not new, we try to get remoteLinkID
				// out from bpw_ORCCCrossReference table
				// These three boolean variables are added to avoid access the newly
				// added uncommited records, default old( false );
				boolean newCust = false;
				boolean newPayee = false;
				boolean newCustPayee = false;
				TypeMLFMerchInfo merch = getCachedMerchInfo( pmtInfo[i].PayeeID );
				if( merch != null ) {
					newPayee = true;
					records[i].MerchID = merch.MerchID.trim();
				}

				TypeMLFCustInfo cust = getCachedCustomerInfo( pmtInfo[i].CustomerID );
				String defNameOnAcct = null;	//default name on acct
				if( cust != null ) {
					newCust = true;
					records[i].AcctID = cust.AcctID;
					records[i].CustID = cust.CustID;
					defNameOnAcct = (cust.FullName.length()>30)
							? cust.FullName.substring( 0, 30 )
									: cust.FullName;
				}

				TypeMLFLinkInfo link = getCachedLinkInfo( pmtInfo[i].CustomerID,
						pmtInfo[i].PayeeID );
				if( link != null ) {
					newCustPayee = true;
					records[i].RemoteLinkID = link.RemoteLinkID;
					records[i].NameOnAcct = link.NameOnAcct;
				}

				ORCCDBAPI.getORCCPmtInfo( pmtInfo[i], records[i], newCust,
						newPayee, newCustPayee, dbh );

				// NameOnAcct should not be longer than 30
				if( records[i].NameOnAcct==null ) {
					records[i].NameOnAcct = defNameOnAcct;
				} else if( records[i].NameOnAcct.length()>30 ) {
					records[i].NameOnAcct = records[i].NameOnAcct.substring( 0, 30 );
				}

				//then map the rest fields.
				pmtInfo2DPRRQRecord( pmtInfo[i], records[i] );

				// increment count
				++_dprCount;
			}

			// Check if there are alread DPR records
			if( records.length< _dprCount ) {
				// There are DPR records in the message. append new records into array
				TypeDPRRQRecord[] dprRecords = new TypeDPRRQRecord[_dprCount];
				for( int i=0; i<_dprRq.DPRRQRecord.length; ++i ) {
					dprRecords[i]=_dprRq.DPRRQRecord[i];
				}
				for( int i=_dprRq.DPRRQRecord.length; i<_dprCount; ++i) {
					dprRecords[i] = records[i-_dprRq.DPRRQRecord.length];
				}
				_dprRq.DPRRQRecord = dprRecords;
			} else {
				// There was no dpr records. Simply set the new records to be only records
				_dprRq.DPRRQRecord = records;
			}
			_dprRq.DPRRQRecordExists = _dprCount>0;
		} catch (Exception e ) {
			unlock();
			throw e;
		}
		ORCCUtil.log( "ORCC FulfillmentAPI.addPayments() start.", FFSConst.PRINT_DEV );
	}


	private static final void addORCCMerchants( PayeeInfo[] payees,
			FFSConnectionHolder dbh ) throws Exception
	{
		if( payees==null || payees.length<=0 ) return;

		int len = payees.length;

		TypeMLFMerchAdd merchAdd;
		TypeMLFMerchRqUn[] merchRqUn = null;

		// Expand the MLFRqUn
		int pos = 0;
		if( _mlfRq.MLFMerchRq.MLFMerchRqUnExists ){
			pos = _mlfRq.MLFMerchRq.MLFMerchRqUn.length;
			merchRqUn = new TypeMLFMerchRqUn[pos+len];
			for( int i=0; i<pos; ++i ) merchRqUn[i] = _mlfRq.MLFMerchRq.MLFMerchRqUn[i];
		} else {
			merchRqUn = new TypeMLFMerchRqUn[len];
		}

		TypeMLFRecordHeader header = getRecordHeader();
		TypeMLFMerchInfo info = null;
		ORCCPayeeMaskFields fields = null;
		for( int i=0; i<len; ++i ) {
			merchRqUn[pos] = new TypeMLFMerchRqUn();
			merchAdd = new TypeMLFMerchAdd();
			merchRqUn[pos].MLFMerchAdd = merchAdd;
			merchRqUn[pos++].__memberName = "MLFMerchAdd";

			info = new TypeMLFMerchInfo();
			// Get ORCC specific payee fields from DB and fill into "MLFMerchInfo"
			fields = ORCCDBAPI.getORCCPayeeMaskFields( payees[i], dbh );
			payeeInfo2MLFMerchInfo( payees[i], fields, info );
			info.MerchID = Long.toString( getTempMerchID( Integer.parseInt( payees[i].PayeeID ) ) );
			// update the extdPayeeID of bpw_payee table to new temp merchID
			ORCCDBAPI.updateExtdPayeeIDByPayeeID( info.MerchID, payees[i].PayeeID, dbh );

			merchAdd.MLFRecordHeader = header;
			merchAdd.MLFMerchInfo = info;

			// increment count
			++_mlfCount;

			// Store info into _merchTable by payeeID
			_merchTable.put( payees[i].PayeeID, info );
		}

		_mlfRq.MLFMerchRq.MLFMerchRqUn = merchRqUn;
		_mlfRq.MLFMerchRq.MLFMerchRqUnExists = merchRqUn.length>0;
	}


	private static final void addCustomerPayeeLinks( CustomerPayeeInfo[] info,
			FFSConnectionHolder dbh ) throws Exception
	{
		if( info==null || info.length<=0 ) return;
		int len = info.length;

		TypeMLFLinkRqUn[] linkRqUn = null;
		int pos = 0;
		if( _mlfRq.MLFLinkRq.MLFLinkRqUnExists ) {
			pos = _mlfRq.MLFLinkRq.MLFLinkRqUn.length;
			linkRqUn = new TypeMLFLinkRqUn[pos+len];
			for( int i=0; i<pos; ++i ) linkRqUn[i] = _mlfRq.MLFLinkRq.MLFLinkRqUn[i];
		} else {
			linkRqUn = new TypeMLFLinkRqUn[len];
		}
		_mlfRq.MLFLinkRq.MLFLinkRqUn = linkRqUn;
		_mlfRq.MLFLinkRq.MLFLinkRqUnExists = linkRqUn.length>0;

		TypeMLFLinkAdd linkAdd = null;
		TypeMLFRecordHeader header = getRecordHeader();
		TypeMLFLinkInfo linkInfo;
		for( int i=0; i<len; ++i ) {
			linkAdd = new TypeMLFLinkAdd();
			linkRqUn[pos] = new TypeMLFLinkRqUn();
			linkRqUn[pos].__memberName = "MLFLinkAdd";
			linkRqUn[pos++].MLFLinkAdd = linkAdd;

			linkAdd.MLFRecordHeader = header;
			linkInfo = new TypeMLFLinkInfo();
			linkAdd.MLFLinkInfo = linkInfo;

			// Get ORCC specific link fields from DB and fill into "TypeMLFLinkInfo"

			// Only when customer is not new, we try to get ORCCCustomerID and
			// ORCCAcctID out from ORCCCustomerCrossReference table
			// Only when payee is not new, we try to get ExtdPayeeID and
			// payacct out from bpw payee table
			// These two boolean variables are added to avoid access the newly
			// added uncommited records
			boolean newPayee = false;
			boolean newCust = false;
			TypeMLFMerchInfo merch = getCachedMerchInfo( info[i].PayeeID );
			if( merch != null ) {
				newPayee = true;
				linkInfo.MerchID = merch.MerchID.trim();
				int merchNameLength = merch.MerchName.length();
				linkInfo.LinkNickname = merch.MerchName.substring(0,
						(merchNameLength>12)
						? 12
								: merchNameLength);
			}

			TypeMLFCustInfo cust = getCachedCustomerInfo( info[i].CustomerID );
			if( cust != null ) {
				newCust = true;
				linkInfo.AcctID = cust.AcctID;
				if( info[i].NameOnAcct==null || info[i].NameOnAcct.length()<=0 ) {
					linkInfo.NameOnAcct = (cust.FullName.length()>30)
							? cust.FullName.substring( 0, 30 )
									: cust.FullName;
				} else {
					linkInfo.NameOnAcct = (info[i].NameOnAcct.length()<=30)
							? info[i].NameOnAcct
									: info[i].NameOnAcct.substring(0, 30 );
				}
			}
			ORCCDBAPI.generateORCCCustomerPayeeInfo(
					info[i],
					linkInfo,
					newCust,
					newPayee,
					dbh );
			customerPayeeInfo2MLFLinkInfo( info[i], linkInfo );

			// Store this MLFLinkInfo into _linkTable
			HashMap map = (HashMap)_linkTable.get( info[i].CustomerID );
			if( map==null ) {
				map = new HashMap();
				_linkTable.put( info[i].CustomerID, map );
			}
			map.put( info[i].PayeeID, linkInfo );
			// increment count
			++_mlfCount;
		}
	}


	private static final void modifyCustomerPayeeLinks( CustomerPayeeInfo[] info,
			FFSConnectionHolder dbh ) throws Exception
	{
		if( info==null || info.length<=0 ) return;
		int len = info.length;

		TypeMLFLinkRqUn[] linkRqUn = null;
		int pos = 0;
		if( _mlfRq.MLFLinkRq.MLFLinkRqUnExists ) {
			pos = _mlfRq.MLFLinkRq.MLFLinkRqUn.length;
			linkRqUn = new TypeMLFLinkRqUn[pos+len];
			for( int i=0; i<pos; ++i ) linkRqUn[i] = _mlfRq.MLFLinkRq.MLFLinkRqUn[i];
		} else {
			linkRqUn = new TypeMLFLinkRqUn[len];
		}
		_mlfRq.MLFLinkRq.MLFLinkRqUn = linkRqUn;
		_mlfRq.MLFLinkRq.MLFLinkRqUnExists = linkRqUn.length>0;

		TypeMLFLinkChange linkChange = null;
		TypeMLFRecordHeader header = getRecordHeader();
		TypeMLFLinkInfo linkInfo;
		for( int i=0; i<len; ++i ) {
			linkChange = new TypeMLFLinkChange();

			linkRqUn[pos] = new TypeMLFLinkRqUn();
			linkRqUn[pos].__memberName = "MLFLinkChange";
			linkRqUn[pos++].MLFLinkChange = linkChange;

			linkChange.MLFRecordHeader = header;
			linkInfo = new TypeMLFLinkInfo();
			linkChange.MLFLinkInfo = linkInfo;

			// Get ORCC specific link fields from DB and fill into "TypeMLFLinkInfo"

			// Only when customer is not new, we try to get ORCCCustomerID and
			// ORCCAcctID out from ORCCCustomerCrossReference table
			// Only when payee is not new, we try to get ExtdPayeeID and
			// payacct out from bpw payee table
			// These two boolean variables are added to avoid access the newly
			// added uncommited records
			boolean lookupPayee = true;
			boolean lookupCust = true;
			TypeMLFMerchInfo merch = getCachedMerchInfo( info[i].PayeeID );
			if( merch != null ) {
				lookupPayee = false;
				linkInfo.MerchID = merch.MerchID.trim();
				int merchNameLength = merch.MerchName.length();
				linkInfo.LinkNickname = merch.MerchName.substring(0,
						(merchNameLength>12)
						? 12
								: merchNameLength);
			}
			TypeMLFCustInfo cust = getCachedCustomerInfo( info[i].CustomerID );
			if( cust != null ) {
				lookupCust = false;
				linkInfo.AcctID = cust.AcctID;
				if( info[i].NameOnAcct==null || info[i].NameOnAcct.length()<=0 ) {
					linkInfo.NameOnAcct = ( cust.FullName.length()<=30 )
							? cust.FullName
									: cust.FullName.substring( 0, 30 );
				} else {
					linkInfo.NameOnAcct = (info[i].NameOnAcct.length()<=30)
							? info[i].NameOnAcct
									: info[i].NameOnAcct.substring(0, 30 );
				}
			}
			ORCCDBAPI.getORCCCustomerPayeeInfo( info[i],
					linkInfo,
					lookupCust,
					lookupPayee,
					dbh );
			customerPayeeInfo2MLFLinkInfo( info[i], linkInfo );

			// Store this MLFLinkInfo into _linkTable
			HashMap map = (HashMap)_linkTable.get( info[i].CustomerID );
			if( map==null ) {
				map = new HashMap();
				_linkTable.put( info[i].CustomerID, map );
			}
			map.put( info[i].PayeeID, linkInfo );

			// increment count
			++_mlfCount;
		}
	}


	private static final void deleteCustomerPayeeLinks( CustomerPayeeInfo[] info,
			FFSConnectionHolder dbh ) throws Exception
	{
		if( info==null || info.length<=0 ) return;
		int len = info.length;

		TypeMLFLinkRqUn[] linkRqUn = null;
		int pos = 0;
		if( _mlfRq.MLFLinkRq.MLFLinkRqUnExists ) {
			pos = _mlfRq.MLFLinkRq.MLFLinkRqUn.length;
			linkRqUn = new TypeMLFLinkRqUn[pos+len];
			for( int i=0; i<pos; ++i ) linkRqUn[i] = _mlfRq.MLFLinkRq.MLFLinkRqUn[i];
		} else {
			linkRqUn = new TypeMLFLinkRqUn[len];
		}
		_mlfRq.MLFLinkRq.MLFLinkRqUn = linkRqUn;
		_mlfRq.MLFLinkRq.MLFLinkRqUnExists = linkRqUn.length>0;

		TypeMLFLinkDel linkDel = null;
		TypeMLFRecordHeader header = getRecordHeader();
		TypeMLFLinkInfo linkInfo;
		for( int i=0; i<len; ++i ) {
			linkDel = new TypeMLFLinkDel();

			linkRqUn[pos] = new TypeMLFLinkRqUn();
			linkRqUn[pos].__memberName = "MLFLinkDel";
			linkRqUn[pos++].MLFLinkDel = linkDel;

			linkDel.MLFRecordHeader = header;
			linkInfo = new TypeMLFLinkInfo();
			linkDel.MLFLinkInfo = linkInfo;


			// Get ORCC specific link fields from DB and fill into "TypeMLFLinkInfo"

			// Only when customer is not new, we try to get ORCCCustomerID and
			// ORCCAcctID out from ORCCCustomerCrossReference table
			// Only when payee is not new, we try to get ExtdPayeeID and
			// payacct out from bpw payee table
			// These two boolean variables are added to avoid access the newly
			// added uncommited records
			boolean lookupPayee = true;
			boolean lookupCust = true;
			TypeMLFMerchInfo merch = getCachedMerchInfo( info[i].PayeeID );
			if( merch != null ) {
				lookupPayee = false;
				linkInfo.MerchID = merch.MerchID.trim();
				int merchNameLength = merch.MerchName.length();
				linkInfo.LinkNickname = merch.MerchName.substring(0,
						(merchNameLength>12)
						? 12
								: merchNameLength);
			}
			TypeMLFCustInfo cust = getCachedCustomerInfo( info[i].CustomerID );
			if( cust != null ) {
				lookupCust = false;
				linkInfo.AcctID = cust.AcctID;
				if( info[i].NameOnAcct==null || info[i].NameOnAcct.length()<=0 ) {
					linkInfo.NameOnAcct = ( cust.FullName.length()<=30 )
							? cust.FullName
									: cust.FullName.substring( 0, 30 );
				} else {
					linkInfo.NameOnAcct = (info[i].NameOnAcct.length()<=30)
							? info[i].NameOnAcct
									: info[i].NameOnAcct.substring(0, 30 );
				}
			}
			ORCCDBAPI.getORCCCustomerPayeeInfo( info[i],
					linkInfo,
					lookupCust,
					lookupPayee,
					dbh );
			customerPayeeInfo2MLFLinkInfo( info[i], linkInfo );
			customerPayeeInfo2MLFLinkInfo( info[i], linkInfo );

			// Store this MLFLinkInfo into _linkTable
			HashMap map = (HashMap)_linkTable.get( info[i].CustomerID );
			if( map==null ) {
				map = new HashMap();
				_linkTable.put( info[i].CustomerID, map );
			}
			map.put( info[i].PayeeID, linkInfo );

			// increment count
			++_mlfCount;
		}
	}


	private static final void createMBInstance() throws Exception
	{
		_mb = (BPWMsgBroker)FFSRegistry.lookup( BPWResource.BPWMSGBROKER);
	}


	///////////////////////////////////////////////////////////////////////////
	// Proccess an MLF file from  ORCC
	///////////////////////////////////////////////////////////////////////////
	public static final boolean processMLFRSFile( File file, FFSConnectionHolder dbh )
			throws Exception
	{
		boolean success = true;
		TypeMLFRS mlfRs = null;

		try{
			mlfRs = parseMLFRSFile( file );
		} catch (Exception e ) {
			return false;
		}

		if( mlfRs!=null ) {
			boolean valid = isValid( mlfRs);
			if( valid ) {
				success = success && processMLFCustRs( mlfRs.MLFCustRs, dbh );
				success = success && processMLFMerchRs( mlfRs.MLFMerchRs, dbh );
				success = success && processMLFLinkRs( mlfRs.MLFLinkRs, dbh );
				success = success && processMLFMailRs( mlfRs.MLFMailRs, dbh );
				success = success && processMLFHist2Rs( mlfRs.MLFHist2Rs, dbh );
				success = success && processMLFBanktRs( mlfRs.MLFBanktRs, dbh );
				success = success && processMLFPinqRs( mlfRs.MLFPinqRs, dbh );
				success = success && processMLFReissueRs( mlfRs.MLFReissueRs, dbh );
			}else{
				String msg= "The MLF response in file "+file.getName()
				+" is inconsistent. Please check the "
				+" FI ID, front ID, and record count."
				+ "MLF failed.";
				ORCCUtil.log( msg );

				throw new Exception ( msg );
			}
		}

		return success;
	}


	///////////////////////////////////////////////////////////////////////////
	// Proccess an ERR file from  ORCC
	///////////////////////////////////////////////////////////////////////////
	public static final boolean processELFRSFile( File file, FFSConnectionHolder dbh )
			throws Exception
	{
		TypeELFRS elfRs = null;

		try{
			elfRs = parseELFRSFile( file );
		} catch (Exception e ) {
			return false;
		}
		if( elfRs!=null ) {
			boolean valid = isValid( elfRs);
			if( valid ) {
				if (! processRecordError( elfRs.RecordError, dbh ) ) return false;
			}else{
				ORCCUtil.log( "The ELF response in file "+file.getName()
				+" is inconsistent. Please check the "
				+" FI ID, front ID, and record count."
				+ "ELF failed.");
				return false;
			}
		}

		return true;
	}


	private static final TypeMLFRS parseMLFRSFile( File file ) throws Exception
	{
		FileInputStream in = null;
		byte[] buff;
		Object message = null;

		try{
			in = new FileInputStream( (File)file );
			buff = new byte[ in.available() ];
			in.read( buff );
			message = _mb.parseMsg(
					new String( buff),
					ORCCConstants.MB_MLF_RS_MESSAGE_NAME,
					ORCCConstants.MB_MESSAGE_SET_NAME );
		} catch ( Exception e ) {
			ORCCUtil.log ( e, "Error when processing MLF file "
					+ file.getName()
					+ " processing canceled." );
			throw e;
		} finally {
			try{
				if( in!=null ) in.close();
				in = null;
			} catch( Exception e ) {
				// Ignore
			}
		}

		return (TypeMLFRS)message;
	}


	private static final TypeELFRS parseELFRSFile( File file ) throws Exception
	{
		FileInputStream in = null;
		byte[] buff;
		Object message = null;

		try{
			in = new FileInputStream( file );
			buff = new byte[ in.available() ];
			in.read( buff );
			message = _mb.parseMsg(
					new String( buff),
					ORCCConstants.MB_ELF_RS_MESSAGE_NAME,
					ORCCConstants.MB_MESSAGE_SET_NAME );
		} catch ( Exception e ) {
			ORCCUtil.log ( e, "Error when processing MLF file "
					+ file.getName()
					+ " processing canceled." );
			throw e;
		} finally {
			try{
				if( in!=null ) in.close();
				in = null;
			} catch( Exception e ) {
				// Ignore
			}
		}

		return (TypeELFRS)message;
	}


	private static boolean processMLFCustRs( TypeMLFCustRs rs, FFSConnectionHolder dbh )
			throws Exception
	{
		return true;
	}


	private static boolean processMLFMerchRs( TypeMLFMerchRs rs,  FFSConnectionHolder dbh)
			throws Exception
	{
		if( !rs.MLFMerchRsUnExists ) return true;

		TypeMLFMerchRsUn merchRsUn[] = rs.MLFMerchRsUn;
		ArrayList addList = new ArrayList( merchRsUn.length );
		ArrayList changeList = new ArrayList( merchRsUn.length );
		ArrayList delList = new ArrayList( merchRsUn.length );
		ArrayList kcList = new ArrayList( merchRsUn.length );

		// Sorting the responses into 4 types
		for( int i=0; i<merchRsUn.length; ++i ) {
			if( merchRsUn[i].__memberName.equals("MLFMerchAdd") ) {
				addList.add( merchRsUn[i].MLFMerchAdd );
			} else if( merchRsUn[i].__memberName.equals( "MLFMerchChange" ) ) {
				changeList.add( merchRsUn[i].MLFMerchChange );
			} else if( merchRsUn[i].__memberName.equals( "MLFMerchDel" ) ) {
				delList.add( merchRsUn[i].MLFMerchDel);
			} else {
				kcList.add( merchRsUn[i].MLFMerchKeyChange );
			}
		}

		// There should be no mesg. in resp. file
		if( !addList.isEmpty() ) {
			int length = addList.size();
			PayeeInfo[] payeeInfo = new PayeeInfo[addList.size()];
			ORCCPayeeMaskFields[] fields = new ORCCPayeeMaskFields[length];
			int i;
			TypeMLFMerchAdd object = null;
			for( i=0; i<length; ++i) {
				object=(TypeMLFMerchAdd)addList.get( i );
				// Verifying consistency
				if( !isFIIDEqual(object.MLFRecordHeader.FIID.trim(), _fiID ) ) {
					ORCCUtil.log( "Inconsistency in FI ID, merchant "
							+ "add record #"
							+Integer.toString(i+1)+" failed" );
					return false;
				}

				// Processing the message
				payeeInfo[i] = new PayeeInfo();
				fields[i] = new ORCCPayeeMaskFields();
				TypeMLFMerchInfo info = object.MLFMerchInfo;
				mlfMerchInfo2PayeeInfo( info, payeeInfo[i], fields[i] );
				// make DB changes based on given PayeeInfo's

				// if not exists in database
				try {
					payeeInfo[i].PayeeID = DBUtil.getNextIndexString(
							DBConsts.PAYEEID );
					fields[i].payeeID = payeeInfo[i].PayeeID;
					ORCCDBAPI.addPayee( payeeInfo[i], fields[i], dbh );
				} catch( Exception e ) {
					ORCCUtil.log( e.toString());
					throw e;
				}
			}
			addList.clear();
		}

		// There should be no suck mesg. in resp. file
		if( !changeList.isEmpty() ) {
			int length = changeList.size();
			TypeMLFMerchChange object = null;
			PayeeInfo[] payeeInfo = new PayeeInfo[length];
			ORCCPayeeMaskFields[] fields = new ORCCPayeeMaskFields[length];
			int i;
			for( i=0; i<length; ++i) {
				object = (TypeMLFMerchChange)changeList.get( i );
				// Verifying consistency
				if( !isFIIDEqual(object.MLFRecordHeader.FIID.trim(), _fiID ) ) {
					ORCCUtil.log( "Inconsistency in FI ID, merchant "
							+ "change record #"
							+Integer.toString(i+1)+" failed" );
					return false;
				}

				// Processing the message
				payeeInfo[i] = new PayeeInfo();
				fields[i] = new ORCCPayeeMaskFields();
				TypeMLFMerchInfo info = object.MLFMerchInfo;
				mlfMerchInfo2PayeeInfo( info, payeeInfo[i], fields[i] );


				// If exists, then update respective record
				try {
					PayeeInfo pe = Payee.findPayeeByExtendedID( info.MerchID.trim(), dbh );
					ORCCDBAPI.updatePayeeORCCFields( payeeInfo[i], dbh );
					fields[i].payeeID = pe.PayeeID;
					ORCCDBAPI.updateORCCPayeeMaskFields( fields[i], dbh );
				} catch( Exception e ) {
					ORCCUtil.log( e.toString());
					throw e;
				}
			}
			// make DB changes based on given PayeeInfo's
			changeList.clear();
		}

		// There should be no suck mesg. in resp. file
		if( !delList.isEmpty() ) {
			int length = delList.size();
			TypeMLFMerchDel object = null;
			PayeeInfo[] payeeInfo = new PayeeInfo[length];
			ORCCPayeeMaskFields[] fields = new ORCCPayeeMaskFields[length];
			int i;
			for( i=0; i<length; ++i) {
				object = (TypeMLFMerchDel)delList.get( i );
				// Verifying consistency
				if( !isFIIDEqual(object.MLFRecordHeader.FIID.trim(), _fiID ) ) {
					ORCCUtil.log( "Inconsistency in FI ID, merchant "
							+ "delete record #"
							+Integer.toString(i+1)+" failed" );
					return false;
				}

				// Processing the message
				payeeInfo[i] = new PayeeInfo();
				fields[i] = new ORCCPayeeMaskFields();
				TypeMLFMerchInfo info = object.MLFMerchInfo;

				// if not exists in database
				try {
					payeeInfo[i] = Payee.findPayeeByExtendedID( info.MerchID.trim(), dbh );
					CommonProcessor.deletePayee( dbh, payeeInfo[i].PayeeID );
				} catch( Exception e ) {
					ORCCUtil.log( e.toString());
					throw e;
				}
			}
			// make DB changes based on given PayeeInfo's
			delList.clear();
		}

		// If there's MerchKeyChange in resp. file, there must be
		// sth. related with new ORCC merchants added. therefore we only add
		// account masks if available
		if( !kcList.isEmpty() ) {
			TypeMLFMerchKeyChange object = null;
			int length = kcList.size();
			for( int i=0; i<length; ++i) {
				object = (TypeMLFMerchKeyChange)kcList.get(i);
				// Verifying consistency
				if( !isFIIDEqual(object.MLFRecordHeader.FIID.trim(), _fiID ) ) {
					ORCCUtil.log( "Inconsistency in FI ID, merchant "
							+ "key change record #"
							+Integer.toString(i+1)+" failed" );
					return false;
				}

				// Field mapping
				// make DB changes based on given PayeeInfo's
				// Keychange here means a brand new ORCC merch has been added.
				// Use the "KeyChange" value in header for old temp MerchID to
				// update extended PayeeID.

				//If no match found, add a new payee
				// to BPW table //???? how to get those non-null fields?

				try {
					ORCCDBAPI.processMLFMerchKeyChange(
							object.MLFRecordHeader.KeyChange,
							object.MLFMerchInfo, dbh );
				} catch( Exception e ) {
					throw e;
				}
			}
			kcList.clear();
		}


		return true;
	}


	private static boolean processMLFLinkRs( TypeMLFLinkRs rs, FFSConnectionHolder  dbh )
			throws Exception
	{
		if( !rs.MLFLinkRsUnExists ) return true;

		TypeMLFLinkRsUn[] linkRsUn = rs.MLFLinkRsUn;
		ArrayList addList = new ArrayList( linkRsUn.length);
		ArrayList changeList = new ArrayList( linkRsUn.length);
		ArrayList delList = new ArrayList( linkRsUn.length);
		ArrayList kcList = new ArrayList( linkRsUn.length);
		CustPayeeRslt temp;

		for( int i=0; i<linkRsUn.length; ++i ) {
			if( linkRsUn[i].__memberName.equals( "MLFLinkAdd" ) ) {
				addList.add( linkRsUn[i].MLFLinkAdd );
			} else if( linkRsUn[i].__memberName.equals( "MLFLinkChange" ) ) {
				changeList.add( linkRsUn[i].MLFLinkChange );
			} else if( linkRsUn[i].__memberName.equals( "MLFLinkDel" ) ) {
				delList.add( linkRsUn[i].MLFLinkDel );
			} else {
				kcList.add( linkRsUn[i].MLFLinkKeyChange );
			}
		}
		/*
	// There should be no LinkAdd in resp file
	if( rs.MLFLinkAddExists ) {
	    TypeMLFLinkAdd[] objects = rs.MLFLinkAdd;
	    int i;
	    currList = new ArrayList( objects.length );
	    lists[0] = currList;

	    for( i=0; i<objects.length; ++i) {
		// Verifying consistency
		if( !isFIIDEqual(object.MLFRecordHeader.FIID.trim(), _fiID ) ) {
		    ORCCUtil.log( "Inconsistency in FI ID, link "
		    	+ "add record #"
			+Integer.toString(i+1)+" failed" );
		    return false;
		}

		// Creating new CustPayeeRslt
		temp = new CustPayeeRslt();
		ORCCLinkFields fields = new ORCCLinkFields();
		temp.action = "ADD";
		temp.status = DBConsts.STATUS_OK;
		mlfLinkInfo2CustPayeeRslt( objects[i].MLFLinkInfo, temp );
		try{
		    temp.payeeListID = Integer.parseInt( objects[i].MLFLinkInfo.ORCCLinkID );
		} catch (NumberFormatException e ) {
		    ORCCUtil.log( "Failed to parse ORCC LinkID: "+e.toString() );
		    temp.payeeListID = -1;
		}
		currList.add( temp );
	    }
	} else {
	    lists[0] = new ArrayList(0);
	}
		 */

		// If there's LinkChange in response file, it means we need to
		// update MerchID
		if( !changeList.isEmpty() ) {
			TypeMLFLinkChange object = null;
			int i;
			int length = changeList.size();
			for( i=0; i<length; ++i) {
				object = (TypeMLFLinkChange)changeList.get( i );
				// Verifying consistency
				if( !isFIIDEqual(object.MLFRecordHeader.FIID.trim(), _fiID ) ) {
					ORCCUtil.log( "Inconsistency in FI ID, link "
							+ "change record #"
							+  (i+1) + " failed" );
					return false;
				}

				// Creating new CustPayeeRslt
				temp = new CustPayeeRslt();
				temp.action = DBConsts.MOD;
				temp.status = DBConsts.STATUS_OK;

				try {			
					CustomerPayeeInfo cp = ORCCDBAPI.getCustomerPayeeInfoByRemoteLinkID(
							object.MLFLinkInfo.RemoteLinkID, dbh );
					if( cp==null ) {
						String err = "Cannot find BPW_CustomerPayee record by ORCC remote link ID="
								+object.MLFLinkInfo.RemoteLinkID
								+". Record ignored.";
						ORCCUtil.log( err );
					} else {
						temp.payeeListID = cp.PayeeListID;
						temp.payeeID = cp.PayeeID;
						temp.customerID = cp.CustomerID;
						ORCCDBAPI.processOneCustPayeeRslt(temp, dbh);
						ORCCDBAPI.processMLFLinkChange( object.MLFLinkInfo, dbh );
					}
				} catch( Exception e ) {
					ORCCUtil.log( e.toString() );
					throw e;
				}
			}
			changeList.clear();
		}

		/*
	// There should be no LinkDel in resp file
	if( rs.MLFLinkDelExists ) {
	    TypeMLFLinkDel[] objects = rs.MLFLinkDel;
	    int i;
	    for( i=0; i<objects.length; ++i) {
		// Verifying consistency
		if( !isFIIDEqual(object.MLFRecordHeader.FIID.trim(), _fiID ) ) {
		    ORCCUtil.log( "Inconsistency in FI ID, link "
		    	+ "delete record #"
			+Integer.toString(i+1)+" failed" );
		    return false;
		}

		// Creating new CustPayeeRslt
		temp = new CustPayeeRslt();
		temp.action = "DEL";
		temp.status = DBConsts.STATUS_OK;
		ORCCLinkFields fields = new ORCCLinkFields();
		mlfLinkInfo2CustPayeeRslt( objects[i].MLFLinkInfo, temp );
		try{
		    temp.payeeListID = Integer.parseInt( objects[i].MLFLinkInfo.ORCCLinkID );
		} catch (NumberFormatException e ) {
		    ORCCUtil.log( "Failed to parse ORCC LinkID: "+e.toString() );
		    temp.payeeListID = -1;
		}
		currList.add( temp );
	    }
	}

	// There should be no LinkKeyChange in resp. file
	if( rs.MLFLinkKeyChangeExists ) {
	    TypeMLFLinkKeyChange[] objects = rs.MLFLinkKeyChange;
	    int i;

	    for( i=0; i<objects.length; ++i) {
		// Verifying consistency
		if( !isFIIDEqual(object.MLFRecordHeader.FIID.trim(), _fiID ) ) {
		    ORCCUtil.log( "Inconsistency in FI ID, link "
		    	+ "key change record #"
			+Integer.toString(i+1)+" failed" );
		    return false;
		}

		//Ignore key change
	    }
	}

	int count = lists[0].size()+lists[1].size()+lists[2].size();
	CustPayeeRslt[] rslt = new CustPayeeRslt[count];
	int idx=0;
	for( int i=0; i<lists.length; ++i ) {
	    for( int j=0; j<lists[i].size(); ++j ) {
		rslt[idx++] = (CustPayeeRslt)lists[i].get(j);
	    }
	}

	try{
	    _ofxServices.processCustPayeeRslt( rslt );
	} catch ( Exception e ) {
	    ORCCUtil.log( e.toString() );
	    throw e;
	}
		 */
		return true;
	}


	private static boolean processMLFMailRs( TypeMLFMailRs rs, FFSConnectionHolder dbh )
			throws Exception
	{
		// No processing. not supported right now.
		return true;
	}


	private static boolean processMLFHist2Rs( TypeMLFHist2Rs rs, FFSConnectionHolder dbh )
			throws Exception
	{
		if( !rs.MLFHist2RsUnExists )  return true;

		TypeMLFHist2RsUn[] hist2RsUn = rs.MLFHist2RsUn;
		TypeMLFHist2Add object = null;
		for( int i=0; i<hist2RsUn.length; ++i) {
			object = (TypeMLFHist2Add)hist2RsUn[i].MLFHist2Add;
			// Verifying consistency
			if( !isFIIDEqual(object.MLFRecordHeaderX.FIID.trim(), _fiID ) ) {
				ORCCUtil.log( "Inconsistency in FI ID, Hist2 "
						+ "add record #"
						+Integer.toString(i+1)+" failed" );
				return false;
			}

			String srvrTID = findSrvrTIDByTransID(
					dbh,
					Integer.parseInt(object.MLFHist2Info.TransID) );
			PmtTrnRslt pmtRslt = new PmtTrnRslt();
			pmtRslt.srvrTid = srvrTID;
			if(object.MLFHist2Info.ChkType.value() == EnumCheckType._NIL ) {
				pmtRslt.status = DBConsts.STATUS_GENERAL_ERROR;
				pmtRslt.message = "General error from BPW.";
			} else {
				pmtRslt.status = DBConsts.STATUS_OK;
				pmtRslt.message = "Payment Processed";
			}

			try {
				// Update payment status by srvrTID=transactionID. Status=pmtInfo.Status
				//ORCCDBAPI.updatePmtStatus( srvrTID,  status, originatedDate, dbh );

				// fill out pmtRslt fields with the data of pmtInstruction table
				ORCCDBAPI.getPmtTrnRslt( pmtRslt, dbh );
				_backendProcessor.processOnePmtRslt(pmtRslt, dbh);
			} catch( Exception e ) {
				ORCCUtil.log( e.toString() );
				throw e;
			}
		}

		return true;
	}


	private static boolean processMLFBanktRs( TypeMLFBanktRs rs, FFSConnectionHolder dbh )
			throws Exception
	{
		// No processing
		return true;
	}


	private static boolean processMLFPinqRs( TypeMLFPinqRs rs, FFSConnectionHolder dbh )
			throws Exception
	{
		// No processing
		return true;
	}


	private static boolean processMLFReissueRs( TypeMLFReissueRs rs,  FFSConnectionHolder dbh )
			throws Exception
	{
		// No processing
		return true;
	}


	private static boolean processRecordError( TypeRecordError err, FFSConnectionHolder dbh )
			throws Exception
	{
		// to be implemented
		String fileName = err.FileName.toUpperCase();

		if( fileName.endsWith( ORCCConstants.DPR_EXTENSION_NAME ) ) {
			// Payment error
			return !processPmtError( err, dbh );
		}

		// If it reaches here, an MLF entry has failed
		try {

			switch( err.RecDest.value() ) {
			case EnumRecordType._Cust :
				// if could not find a customer by this id, report error.
				CustomerInfo info = Customer.getCustomerByID( err.RecKey.trim(), dbh );
				if( info == null ) {
					ORCCUtil.log( "Error reponse: Could not find this customer in Database " );
					// check status, make sure it is either inprocess or canc_inprocess
					// if in_procee  make failedon
				} else if ( info.status.equals( DBConsts.INPROCESS )  ) {
					if( _propConfig.EnforcePayment ) {
						Customer.updateCustomerStatusWithRouteID( err.RecKey,
								_routeID,
								DBConsts.FAILEDON,
								dbh );
					}
					// if canc_procee do nothing
				} else if ( info.status.equals( DBConsts.CANC_INPROCESS ) ) {

					// if none of above , report error.
				} else {
					ORCCUtil.log( "Error reponse: The status of this customer is wrong " );
				}
				break;
			case EnumRecordType._Merch:
				// if could not find a Payee by this id, report error.
				String merchID = err.RecKey.trim();
				PayeeInfo pinfo = Payee.findPayeeByExtendedID( merchID, dbh );
				if( pinfo == null ) {
					ORCCUtil.log( "Error reponse: Could not find this Payee in Database " );
				}
				ORCCDBAPI.updatePayeeStatusByExtdPayeeID( merchID, DBConsts.FAILEDON, dbh );
				break;
			case EnumRecordType._Link:
				// first 4: FIID, following 6: accountID as in Customer records.
				// rest of the field: remote link ID
				String remoteLinkID = err.RecKey.substring( 4+6 ).trim();
				CustomerPayeeInfo cpinfo = ORCCDBAPI.getCustomerPayeeInfoByRemoteLinkID( remoteLinkID, dbh);
				if( cpinfo == null ) {
					ORCCUtil.log( "Error reponse: Could not find this CustomerPayeeInfo in Database " );
				}
				ORCCDBAPI.updateCustomerPayeeStatus( cpinfo.CustomerID,
						cpinfo.PayeeListID,
						DBConsts.FAILEDON, dbh );
				break;
			case EnumRecordType._Mail:
				// Not supported, ignore
				break;
			default:
				// Invalid message type.
			}
		} catch( Exception e ) {
			ORCCUtil.log( e.toString());
			throw e;
		}

		// Process based on the type of transaction failed

		return true;
	}


	private static boolean processRecordError( TypeRecordError[] errs, FFSConnectionHolder dbh )
			throws Exception
	{
		for( int i=0; i<errs.length; ++i) {
			processRecordError( errs[i], dbh );
		}

		return true;
	}


	private static boolean processPmtError( TypeRecordError err, FFSConnectionHolder dbh )
			throws Exception
	{

		PmtTrnRslt pmtRslt = new PmtTrnRslt();
		String srvrTID = Integer.toString( Integer.parseInt(err.RecKey.trim()) - 9999999);
		pmtRslt.srvrTid = srvrTID;
		pmtRslt.status =  DBConsts.STATUS_GENERAL_ERROR;
		pmtRslt.message = "Payment Failed";
		try {
			// Update payment status by srvrTID=transactionID. Status=pmtInfo.Status
			//PmtInfo info = PmtInstruction.getPmtInfo( srvrTID, dbh );
			//PmtInstruction.updateStatus( dbh, DBConsts.FAILEDON, info.ExtdPmtInfo );

			// fill out pmtRslt fields with the data of pmtInstruction table
			ORCCDBAPI.getPmtTrnRslt( pmtRslt, dbh );
			_backendProcessor.processOnePmtRslt(pmtRslt, dbh);
		} catch( Exception e ) {
			ORCCUtil.log( e.toString());
			throw e;
		}

		return true;
	}


	///////////////////////////////////////////////////////////////////////////
	// Verify that an ORCC issued MLF response is correctly formed
	///////////////////////////////////////////////////////////////////////////
	private static boolean isValid( TypeMLFRS mlfRs ) throws Exception
	{
		TypeRecordCounter counter = mlfRs.RecordCounter;
		TypeMLFRecordHeader header = counter.MLFRecordHeader;
		String fiID = header.FIID.trim();
		int count=-1;
		if( header.KeyChange.length()>0 ) {
			String countStr = header.KeyChange;
			try{
				count=Integer.parseInt( countStr );
			} catch( Exception e ) {
				ORCCUtil.log( "Failed to parse record count to integer.");
				throw e;
			}
		} else {
			count=0;
		}

		// Verify FI ID and Front ID
		if( count<0 || !isFIIDEqual(fiID, _fiID) ) {
			throw new Exception( "FIID inconsistent");
		}

		// Verify the record count
		int numRecords=0;
		if( mlfRs.MLFCustRs.MLFCustRsUnExists ) {
			numRecords+=mlfRs.MLFCustRs.MLFCustRsUn.length;
		}

		if( mlfRs.MLFMerchRs.MLFMerchRsUnExists ) {
			numRecords+=mlfRs.MLFMerchRs.MLFMerchRsUn.length;
		}

		if( mlfRs.MLFLinkRs.MLFLinkRsUnExists ) {
			numRecords+=mlfRs.MLFLinkRs.MLFLinkRsUn.length;
		}

		if( mlfRs.MLFMailRs.MLFMailRsUnExists ) {
			numRecords+=mlfRs.MLFMailRs.MLFMailRsUn.length;
		}

		if( mlfRs.MLFHist2Rs.MLFHist2RsUnExists ) {
			numRecords+=mlfRs.MLFHist2Rs.MLFHist2RsUn.length;
		}

		if( mlfRs.MLFBanktRs.MLFBanktRsUnExists ) {
			numRecords+=mlfRs.MLFBanktRs.MLFBanktRsUn.length;
		}

		if( mlfRs.MLFPinqRs.MLFPinqRsUnExists ) {
			numRecords+=mlfRs.MLFPinqRs.MLFPinqRsUn.length;
		}

		if( mlfRs.MLFReissueRs.MLFReissueRsUnExists ) {
			numRecords+=mlfRs.MLFReissueRs.MLFReissueRsUn.length;
		}

		if( count!=numRecords) {
			throw new Exception( "Record count inconsistent" );
		}

		return true;
	}


	///////////////////////////////////////////////////////////////////////////
	// Verify that an ELF is correctly formed
	///////////////////////////////////////////////////////////////////////////
	private static boolean isValid( TypeELFRS elfRs ) throws Exception
	{
		TypeRecordCounter counter = elfRs.RecordCounter;
		TypeMLFRecordHeader header = counter.MLFRecordHeader;
		String fiID = header.FIID.trim();
		int count=-1;
		if( header.KeyChange.length()>0 ) {
			String countStr = header.KeyChange;
			try{
				count=Integer.parseInt( countStr );
			} catch( Exception e ) {
				ORCCUtil.log( "Failed to parse record count to integer.");
				throw e;
			}
		} else {
			count=0;
		}

		// Verify FI ID and Front ID
		if( count<0 || !isFIIDEqual(fiID, _fiID) ) {
			throw new Exception( "FIID inconsistent");
		}

		// Verify the record count
		int numRecords=0;
		if( elfRs.RecordErrorExists ) {
			numRecords += elfRs.RecordError.length;
		}

		if( count!=numRecords) {
			throw new Exception( "Record count inconsistent" );
		}

		return true;
	}

	private static void openCache() throws Exception
	{
		BufferedOutputStream mlfOut = null;
		FileOutputStream mlfFileOut = null;
		BufferedOutputStream dprOut = null;
		FileOutputStream dprFileOut = null;
		try {
			FileHandlerProvider fileHandlerProvider = (FileHandlerProvider)OSGIUtil.getBean(FileHandlerProvider.class);
			String fileNameBase = getFileNameBase();
			String dprName = fileNameBase+ ORCCConstants.DPR_EXTENSION_NAME;
			String mlfName = fileNameBase+ ORCCConstants.MLF_EXTENSION_NAME;
			File mlfFile = new File( _cachePath+ ORCCConstants.FILE_SEP+mlfName );
			mlfFile.setFileHandlerProvider(fileHandlerProvider);
			File dprFile = new File( _cachePath+ ORCCConstants.FILE_SEP+dprName );
			dprFile.setFileHandlerProvider(fileHandlerProvider);
			if( mlfFile.exists() ) mlfFile.delete();
			if( dprFile.exists() ) dprFile.delete();
	
			mlfFile.createNewFile();
			dprFile.createNewFile();
			mlfFileOut = new FileOutputStream( mlfFile );
			mlfOut = new BufferedOutputStream(
					mlfFileOut );
			dprFileOut = new FileOutputStream( dprFile ) ;
			dprOut = new BufferedOutputStream ( dprFileOut );
			_mlfFile = mlfFile;
			_mlfFileOut = mlfFileOut;
			_mlfOut = mlfOut;
			_dprFile = dprFile;
			_dprFileOut = dprFileOut;
			_dprOut = dprOut;
		} catch(Exception e) {
			throw e;
		}
	}

	private static void closeCache() throws Exception
	{
		File mlfFile = _mlfFile;
		File dprFile = _dprFile;
		BufferedOutputStream mlfOut = _mlfOut;
		BufferedOutputStream dprOut = _dprOut;

		File mlfDestFile = new File( _exportDir+ORCCConstants.FILE_SEP+mlfFile.getName() );
		mlfDestFile.setFileHandlerProvider(mlfFile.getFileHandlerProvider());
		File dprDestFile = new File( _exportDir+ORCCConstants.FILE_SEP+dprFile.getName() );
		dprDestFile.setFileHandlerProvider(dprFile.getFileHandlerProvider());
		try{
			mlfOut.close();
			dprOut.close();

			mlfFile.renameTo( mlfDestFile );
			dprFile.renameTo( dprDestFile );
		} catch( Exception e ) {
			throw e;
		} finally {
			if(null != _mlfOut) {
				try {
					_mlfOut.close();
				} catch(Exception e) {
					// ignore
				}
			}
			if(null != _mlfFileOut) {
				try {
					_mlfFileOut.close();
				} catch(Exception e) {
					// ignore
				}
			}
			if(null != _dprOut) {
				try {
					_dprOut.close();
				} catch(Exception e) {
					// ignore
				}
			}
			if(null != _dprFileOut) {
				try {
					_dprFileOut.close();
				} catch(Exception e) {
					// ignore
				}
			}
			_mlfFile = null;
			_dprFile = null;
		}
	}



	///////////////////////////////////////////////////////////////////////////
	// Get the name for the new MLF and DPR files
	///////////////////////////////////////////////////////////////////////////
	private static final String getFileNameBase() throws Exception
	{
		FileHandlerProvider fileHandlerProvider = (FileHandlerProvider)OSGIUtil.getBean(FileHandlerProvider.class);
		File exportDir = new File( _exportDir );
		exportDir.setFileHandlerProvider(fileHandlerProvider);
		GregorianCalendar cal = new GregorianCalendar();
		int day = cal.get( Calendar.DAY_OF_MONTH );
		String dayStr = (day<10)
				?( "0"+Integer.toString( day ) )
						:Integer.toString(day);

				String nameBase = ORCCConstants.FI_FILE_PREFIX+_fiID+dayStr;

				int idx = ORCCUtil.getIntFromAlphabet( 'A' );
				if( !exportDir.exists() ) {
					exportDir.mkdir();
					return (nameBase+ ORCCUtil.getAlphabetFromInt( idx ) );
				} else if( !exportDir.isDirectory() ) {
					Exception e = new Exception( "Error: Export directory "
							+exportDir.getAbsolutePath()
							+ " is not a directory.");
					ORCCUtil.log( e.getLocalizedMessage() );
					throw e;
				}

				String list[] = exportDir.list();
				if( list.length>0 ) {
					for( int i=0; i< list.length; ++i ) {
						if( list[i].startsWith( nameBase )
								&& ( list[i].endsWith( ORCCConstants.DPR_EXTENSION_NAME )
										|| list[i].endsWith( ORCCConstants.MLF_EXTENSION_NAME ) ) ) {
							char c = list[i].charAt( "TXXXXDD".length() );
							int currIdx = ORCCUtil.getIntFromAlphabet( c );
							if( currIdx>=idx ) idx = currIdx+1;
						}
					}
				}

				nameBase = nameBase+ORCCUtil.getAlphabetFromInt( idx );

				return nameBase;
	}


	///////////////////////////////////////////////////////////////////////////
	// Writing record counts to DPR and MLF
	///////////////////////////////////////////////////////////////////////////
	public static final void addCountRecords()
	{
		TypeMLFRecordHeader mlfHeader = getRecordHeader();
		TypeMLFRecordHeader dprHeader = getRecordHeader();

		StringBuffer sb = new StringBuffer(5);
		if( _mlfCount<10 ) {
			sb.append( "   " );
		} else if( _mlfCount<100 ) {
			sb.append( "  " );
		} else if( _mlfCount<1000 ) {
			sb.append( " " );
		}
		sb.append(_mlfCount );
		mlfHeader.KeyChange = sb.toString();

		sb = new StringBuffer(5);
		if( _dprCount<10 ) {
			sb.append( "   " );
		} else if( _dprCount<100 ) {
			sb.append( "  " );
		} else if( _dprCount<1000 ) {
			sb.append( " " );
		}
		sb.append(_dprCount );
		dprHeader.KeyChange = sb.toString();

		// Attaching to corresponding message objects
		TypeRecordCounter mlfCounter = new TypeRecordCounter();
		TypeRecordCounter dprCounter = new TypeRecordCounter();
		mlfCounter.MLFRecordHeader = mlfHeader;
		dprCounter.MLFRecordHeader = dprHeader;
		_mlfRq.RecordCounter = mlfCounter;
		_dprRq.RecordCounter = dprCounter;
	}


	private static final void writeFiles() throws Exception
	{
		byte[] mlfBytes = _mb.buildMsg(
				_mlfRq,
				ORCCConstants.MB_MLF_RQ_MESSAGE_NAME,
				ORCCConstants.MB_MESSAGE_SET_NAME ).getBytes();
		byte[] dprBytes = _mb.buildMsg(
				_dprRq,
				ORCCConstants.MB_DPR_MESSAGE_NAME,
				ORCCConstants.MB_MESSAGE_SET_NAME ).getBytes();

		_mlfOut.write( mlfBytes );
		_mlfOut.flush();
		_dprOut.write( dprBytes );
		_dprOut.flush();

		_mlfRq = null;
		_dprRq = null;
	}


	///////////////////////////////////////////////////////////////////////////
	// Creating messages for the builder to parse back into byte[]
	///////////////////////////////////////////////////////////////////////////
	/*
    private static final IMBMessage createDPRRQIMBMessage( TypeDPRRQ rq )
    {
	return createIMBMessage( ORCCConstants.MB_DPR_MESSAGE_NAME, rq );
    }


    private static final IMBMessage createMLFRQIMBMessage( TypeMLFRQ rq )
    {
	return createIMBMessage( ORCCConstants.MB_MLF_RQ_MESSAGE_NAME, rq );
    }


    private static final IMBMessage createMLFRSIMBMessage( TypeMLFRS rs )
    {
	return createIMBMessage( ORCCConstants.MB_MLF_RS_MESSAGE_NAME, rs );
    }

    private static final IMBMessage createELFRSIMBMessage( TypeELFRS rs )
    {
	return createIMBMessage( ORCCConstants.MB_ELF_RS_MESSAGE_NAME, rs );
    }


    private static final IMBMessage createIMBMessage( String msgName, Object idlInstance )
    {
	return MBMessageFactory.createIDLMessage(
			ORCCConstants.MB_MESSAGE_SET_NAME,
			msgName,
			idlInstance );
    }

	 */
	///////////////////////////////////////////////////////////////////////////
	// Field mapping methods
	///////////////////////////////////////////////////////////////////////////
	private static final void mlfMerchInfo2PayeeInfo(
			TypeMLFMerchInfo info,
			PayeeInfo payeeInfo,
			ORCCPayeeMaskFields fields )
	{
		payeeInfo.PayeeType = DBConsts.GLOBAL;
		payeeInfo.ExtdPayeeID = info.MerchID.trim();
		switch( info.MerchStatus.value() ) {
		case EnumMerchStatus._Active:
			payeeInfo.Status = DBConsts.ACTIVE;
			break;
		case EnumMerchStatus._Inactive:
		case EnumMerchStatus._NIL:
		case EnumMerchStatus._Remote:
		case EnumMerchStatus._Solicitation:
		default:
			payeeInfo.Status = DBConsts.PENDING;
		}

		// If merch status is 'A' set active, otherwise set pending
		payeeInfo.Status = ( info.MerchStatus.value()==EnumMerchStatus._Active )
				? DBConsts.ACTIVE
						: DBConsts.PENDING;
		payeeInfo.PayeeName = info.MerchName;
		payeeInfo.Phone = info.PhoneNum;
		payeeInfo.Extension = (info.PhoneExt.length()>0) ?info.PhoneExt :null;
		payeeInfo.Addr1 = info.Address;
		payeeInfo.City = info.City;
		payeeInfo.State = info.State;
		payeeInfo.Zipcode = info.ZipCode;
		payeeInfo.ContactName = info.ContactName;
		payeeInfo.PayeeLevelType = "GLOBAL";

		payeeInfo.ContactName = (info.ContactName.length()>0)
				? info.ContactName
						: null;
		payeeInfo.NickName = (info.DefaultNickName.length()>0)
				? info.DefaultNickName
						: null;
		payeeInfo.DaysToPay = Integer.parseInt( info.DaysToPay );
		fields.acctMinLength = (info.AcctLengthMin.length()>0)
				? Integer.parseInt( info.AcctLengthMin )
						: -1;
				fields.acctMaxLength = (info.AcctLengthMax.length()>0)
						? Integer.parseInt( info.AcctLengthMax )
								: -1;
						fields.acctMask1 = (info.AcctMask1.length()>0) ?info.AcctMask1 :null;
						fields.acctMask2 = (info.AcctMask2.length()>0) ?info.AcctMask2 :null;
						fields.acctMask3 = (info.AcctMask3.length()>0) ?info.AcctMask3 :null;
						fields.acctMask4 = (info.AcctMask4.length()>0) ?info.AcctMask4 :null;
						fields.acctMask5 = (info.AcctMask5.length()>0) ?info.AcctMask5 :null;
	}

	/*
    private static final void mlfLinkInfo2CustPayeeRslt(
    			TypeMLFLinkInfo linkInfo,
			CustPayeeRslt rslt )
    {
	rslt.customerID = null;
	rslt.payeeListID = Integer.parseInt( linkInfo.AcctID );

	rslt.payeeID = linkInfo.MerchID;

       rslt.orccLinkID = linkInfo.ORCCLinkID;
	fields.PayAcct = linkInfo.AcctAtMerch;
	fields.nameOnAcct = linkInfo.NameOnAcct;
	fields.LinkNickname = linkInfo.LinkNickname;
	fields.linkDate = linkInfo.LinkDate;
	fields.reserved1 = (linkInfo.ReservedField1Exists)
			?linkInfo.ReservedField1
			:null;
	fields.reserved2 = (linkInfo.ReservedField2Exists)
			?linkInfo.ReservedField2
			:null;
	fields.reserved3 = (linkInfo.ReservedField3Exists)
			?linkInfo.ReservedField3
			:null;
	fields.remoteLinkID = linkInfo.RemoteLinkID;

    }

    private static final void mlfCustInfo2CustomerInfo(TypeMLFCustInfo info,
			CustomerInfo custInfo) {
		custInfo.lastName = info.LastName;
		custInfo.firstName = info.FullName;
		custInfo.addressLine1 = info.Address;
		custInfo.addressLine2 = null;
		custInfo.city = info.City;
		custInfo.state = info.State;
		custInfo.zipcode = info.ZipCode;
		custInfo.phone1 = info.PrimaryPhone;
		custInfo.phone2 = (info.SecondaryPhone.length() > 0) ? info.SecondaryPhone
				: null;
		custInfo.securityCode = (info.CustAuthentication.length() > 0) ? info.CustAuthentication
				: null;
		custInfo.ssn = (info.SSN.length() > 0) ? info.SSN : null;
		custInfo.email = (info.InternetAddress.length() > 0) ? info.InternetAddress
				: null;

		custInfo.remoteUserKey = info.RemoteUserKey;
	}
	 */

	private static final void payeeInfo2MLFMerchInfo(
			PayeeInfo payee,
			ORCCPayeeMaskFields acctMasks,
			TypeMLFMerchInfo info )
	{
		info.MerchID = payee.ExtdPayeeID;
		// Always use status "Remote" in add merchant requests
		info.MerchStatus = EnumMerchStatus.Remote;
		info.MerchName = payee.PayeeName;
		info.DefaultNickName = (payee.NickName==null)
				?ORCCConstants.MB_NULL_FIELD_VALUE
						:payee.NickName;
		info.ContactName = ( payee.ContactName == null ) ?"" :payee.ContactName;
		info.PhoneNum = ORCCUtil.getNumericString( payee.Phone );
		info.PhoneExt = ORCCUtil.getNumericString( payee.Extension );
		if( info.PhoneExt==null ) info.PhoneExt = "";
		info.Address = payee.Addr1
				+ ( (payee.Addr2==null) ?"" :payee.Addr2 )
				+ ( (payee.Addr3==null) ?"" :payee.Addr3 );
		info.City = payee.City;
		info.State = payee.State;
		info.ZipCode = payee.Zipcode;
		if( payee.DaysToPay >= 0 ) {
			info.DaysToPay = (payee.DaysToPay<10)
					?"0" +payee.DaysToPay
							: Integer.toString( payee.DaysToPay );
		} else {
			info.DaysToPay = "00";		// Default days to pay=0
		}

		if( acctMasks == null ) {
			info.AcctLengthMin	= ORCCConstants.MB_NULL_FIELD_VALUE;
			info.AcctLengthMax	= ORCCConstants.MB_NULL_FIELD_VALUE;
			info.AcctMask1	= ORCCConstants.MB_NULL_FIELD_VALUE;
			info.AcctMask2	= ORCCConstants.MB_NULL_FIELD_VALUE;
			info.AcctMask3	= ORCCConstants.MB_NULL_FIELD_VALUE;
			info.AcctMask4	= ORCCConstants.MB_NULL_FIELD_VALUE;
			info.AcctMask5	= ORCCConstants.MB_NULL_FIELD_VALUE;
		} else {
			info.AcctLengthMin = (acctMasks.acctMinLength>0)
					?Integer.toString( acctMasks.acctMinLength )
							:"";
					info.AcctLengthMax = (acctMasks.acctMaxLength>0)
							?Integer.toString( acctMasks.acctMaxLength )
									:"";
							info.AcctMask1 = (acctMasks.acctMask1==null)
									?ORCCConstants.MB_NULL_FIELD_VALUE
											:acctMasks.acctMask1;
							info.AcctMask2 = (acctMasks.acctMask2==null)
									?ORCCConstants.MB_NULL_FIELD_VALUE
											:acctMasks.acctMask2;
							info.AcctMask3 = (acctMasks.acctMask3==null)
									?ORCCConstants.MB_NULL_FIELD_VALUE
											:acctMasks.acctMask3;
							info.AcctMask4 = (acctMasks.acctMask4==null)
									?ORCCConstants.MB_NULL_FIELD_VALUE
											:acctMasks.acctMask4;
							info.AcctMask5 = (acctMasks.acctMask5==null)
									?ORCCConstants.MB_NULL_FIELD_VALUE
											:acctMasks.acctMask5;
		}

		// Following fields are set blank
		info.LongHelp1		= ORCCConstants.MB_NULL_FIELD_VALUE;
		info.LongHelp2		= ORCCConstants.MB_NULL_FIELD_VALUE;
		info.LongHelp3		= ORCCConstants.MB_NULL_FIELD_VALUE;
		info.LongHelp4		= ORCCConstants.MB_NULL_FIELD_VALUE;
		info.ShortHelp1		= ORCCConstants.MB_NULL_FIELD_VALUE;
		info.ShortHelp2		= ORCCConstants.MB_NULL_FIELD_VALUE;
		info.ShortHelp3		= ORCCConstants.MB_NULL_FIELD_VALUE;
		info.ShortHelp4		= ORCCConstants.MB_NULL_FIELD_VALUE;
		info.ShortHelp5		= ORCCConstants.MB_NULL_FIELD_VALUE;
		info.ShortHelp6		= ORCCConstants.MB_NULL_FIELD_VALUE;
		info.ShortHelp7		= ORCCConstants.MB_NULL_FIELD_VALUE;
		info.ShortHelp8		= ORCCConstants.MB_NULL_FIELD_VALUE;
		info.Multisite		= ORCCConstants.MB_NULL_FIELD_VALUE;
		info.Reserved		= ORCCConstants.MB_NULL_FIELD_VALUE;
		info.DefaultIVRNickname	= ORCCConstants.MB_NULL_FIELD_VALUE;
		info.Private		= ORCCConstants.MB_NULL_FIELD_VALUE;
		info.PrenotePeriod	= ORCCConstants.MB_NULL_FIELD_VALUE;
		info.WhiteSpaceExists	= false;
	}


	public static final void customerPayeeInfo2MLFLinkInfo(
			CustomerPayeeInfo info,
			TypeMLFLinkInfo linkInfo )
	{
		linkInfo.ORCCLinkID = ORCCConstants.MB_NULL_FIELD_VALUE;
		linkInfo.AcctAtMerch = ORCCUtil.getAlphaNumericString(info.PayAcct);
		if( linkInfo.AcctAtMerch == null ) linkInfo.AcctAtMerch = ORCCConstants.MB_NULL_FIELD_VALUE;

		int len = "MM/DD/YYYY".length();
		if( info.LinkGoDate>0 ) {
			StringBuffer sb = new StringBuffer( len );
			int m = info.LinkGoDate / 1000000;
			int d = (m % 1000000 ) /10000 ;
			int y = info.LinkGoDate % 10000;
			if( m<10 ) sb.append(0);
			sb.append( m );
			sb.append( "/" );
			if( d<0 ) sb.append( 0 );
			sb.append( d );
			sb.append( "/" );
			sb.append( y );
			linkInfo.LinkDate =  sb.toString();
		} else {
			linkInfo.LinkDate = ORCCUtil.getDateString(
					ORCCUtil.ORCC_DATE_FORMAT ).substring( 0, len );
		}

		// Following fields are left empty
		linkInfo.ReservedField1		= ORCCConstants.MB_NULL_FIELD_VALUE;
		linkInfo.ReservedField2		= ORCCConstants.MB_NULL_FIELD_VALUE;
		linkInfo.ReservedField3		= ORCCConstants.MB_NULL_FIELD_VALUE;
		linkInfo.WhiteSpaceExists = false;
	}


	private static final void pmtInfo2DPRRQRecord(
			PmtInfo pmtInfo,
			TypeDPRRQRecord record ) throws Exception
	{
		record.TransID = Integer.toString(
				getTransIDFromSrvrTID(Integer.parseInt(pmtInfo.SrvrTID)) );
		record.TransType = EnumTransType.BillPayment;
		record.AcctAtMerch = pmtInfo.PayAcct;
		record.Amount = Double.parseDouble(pmtInfo.getAmt());
		record.FromAcctNum = pmtInfo.AcctDebitID;  //should we use PayAcct ?????
		String dtStr       = ORCCUtil.getDateString(ORCCUtil.ORCC_DATE_FORMAT);

		record.DebitDate = ORCCUtil.getDateValue(dtStr);
		record.DebitTime = ORCCUtil.getTimeValue(dtStr);
		if( record.DebitTime.length()<=4 ) record.DebitTime = "0"+record.DebitTime;
		record.DebitResultCode = EnumDebitResultCode.Successful;

		// Following fields are left empty
		record.ReservedField1 = ORCCConstants.MB_NULL_FIELD_VALUE;
		record.ReservedField2 = ORCCConstants.MB_NULL_FIELD_VALUE;
		record.ReservedField3 = ORCCConstants.MB_NULL_FIELD_VALUE;
		record.ReservedField4 = ORCCConstants.MB_NULL_FIELD_VALUE;
		record.ReservedField5 = ORCCConstants.MB_NULL_FIELD_VALUE;
		// No support to device type
		record.DeviceType = EnumDeviceType.NIL;
		record.WhiteSpaceExists = false;
	}


	private static final void customerInfo2MLFCustomerInfo(
			CustomerInfo info,
			TypeMLFCustInfo custInfo )
	{
		custInfo.LastName = info.lastName;
		custInfo.FullName = info.lastName +", "+info.firstName;
		custInfo.Address = info.addressLine1;
		if( info.addressLine2!=null ) {
			custInfo.Address += ", "+info.addressLine2;
		}
		custInfo.City = info.city;
		custInfo.State = info.state;
		custInfo.ZipCode = info.zipcode;
		custInfo.PrimaryPhone = (info.countryCode1==null || info.countryCode1.length()<=0 )
				? ORCCUtil.getNumericString( info.phone1)
						: ORCCUtil.getNumericString( info.countryCode1+info.phone1 );
				if( info.phone2==null || info.phone2.length()<=0 ) {
					custInfo.SecondaryPhone = ORCCConstants.MB_NULL_FIELD_VALUE;
				} else {
					custInfo.SecondaryPhone = (info.countryCode2==null || info.countryCode2.length()<=0 )
							? ORCCUtil.getNumericString( info.phone2)
									: ORCCUtil.getNumericString( info.countryCode2+info.phone2 );
				}
				custInfo.CustAuthentication = (info.securityCode==null)
						? ORCCConstants.MB_NULL_FIELD_VALUE
								: info.securityCode;
				custInfo.SSN = (info.ssn==null) ?ORCCConstants.MB_NULL_FIELD_VALUE :info.ssn;
				custInfo.CustType = ORCCConstants.MB_NULL_FIELD_VALUE;
				custInfo.DateDeleted = ORCCConstants.MB_NULL_FIELD_VALUE;
				custInfo.InternetAddress = (info.email==null)
						? ORCCConstants.MB_NULL_FIELD_VALUE
								: info.email;
				custInfo.RemoteUserKey = info.remoteUserKey;

				// Following fields are left empty
				custInfo.WhiteSpaceExists = false;
	}


	/*
    // /////////////////////////////////////////////////////////////////////////
	// Mapping MLFCustInfo back to CustomerInfo and ORCCCustomerFields
	// Only partial mapping provided.
	// /////////////////////////////////////////////////////////////////////////
	private static final void mlfCustomerInfo2CustomerInfo(
			TypeMLFCustInfo custInfo, CustomerInfo info) {
		info.customerID = custInfo.CustID;
		info.lastName = custInfo.LastName;
		info.ssn = custInfo.SSN;
		info.email = custInfo.InternetAddress;
		info.remoteUserKey = custInfo.RemoteUserKey;
	}
	 */

	private static final TypeMLFRecordHeader getRecordHeader(){
		String dtStr = ORCCUtil.getDateString( ORCCUtil.ORCC_DATE_FORMAT );
		TypeMLFRecordHeader header = new TypeMLFRecordHeader();
		header.FrontID = _frontID;
		header.FIID = _fiID;
		header.RecDate = ORCCUtil.getDateValue( dtStr );
		header.RecTime = ORCCUtil.getTimeValue( dtStr );
		if( header.RecTime.length()<=4 ) header.RecTime = "0"+header.RecTime;
		header.KeyChange = ORCCConstants.MB_NULL_FIELD_VALUE;

		return header;
	}


	static final long getTempMerchID ( int payeeID )
	{
		long fiID = Long.parseLong( _fiID );
		payeeID %= 100000;

		return 9000000000L + fiID*100000L + (long)payeeID;
	}


	static final int parseTempMerchID ( String keyChange )
	{
		long val = Long.parseLong( keyChange );
		long fiID = Long.parseLong( _fiID );

		return (int)(val-9000000000L - fiID*100000L);
	}

	static final boolean isTempMerchID ( String s )
	{
		long id = 0;
		long fiID = Long.parseLong( _fiID );
		try{
			id = Long.parseLong( s );
		} catch( Exception e ) {
			return false;
		}
		id -= fiID*100000L + 9000000000L;

		return (id>=0 && id<100000);
	}

	private static final boolean isFIIDEqual( String id1, String id2 )
	{
		int fiid1 = Integer.parseInt( id1 );
		int fiid2 = Integer.parseInt( id2 );

		return fiid1==fiid2;
	}


	static final TypeMLFCustInfo getCachedCustomerInfo ( String custID )
	{
		return (TypeMLFCustInfo)_custTable.get( custID );
	}

	static final TypeMLFMerchInfo getCachedMerchInfo( String payeeID )
	{
		return (TypeMLFMerchInfo)_merchTable.get( payeeID );
	}

	static final TypeMLFLinkInfo getCachedLinkInfo( String custID, String payeeID )
	{
		HashMap map = (HashMap)_linkTable.get( custID );

		return (map==null) ?null :(TypeMLFLinkInfo)map.get( payeeID );
	}


	private static String findSrvrTIDByTransID( FFSConnectionHolder dbh,
			int transID ) throws Exception
	{
		int hi = ORCCUtil.transIDMultiplier*ORCCConstants.ORCC_TRANSID_LIMIT
				+transID - ORCCConstants.ORCC_TRANSID_BASE;
		int lo = (ORCCUtil.transIDMultiplier-1)*ORCCConstants.ORCC_TRANSID_LIMIT
				+transID - ORCCConstants.ORCC_TRANSID_BASE;

		return ORCCDBAPI.findSrvrTID( dbh, hi, lo );
	}

	private static int getTransIDFromSrvrTID( int srvrTID ) throws Exception
	{
		int n = srvrTID/ORCCConstants.ORCC_TRANSID_LIMIT;
		while( n>ORCCUtil.transIDMultiplier )ORCCUtil.incrementTransIDMultiplier();

		return srvrTID%ORCCConstants.ORCC_TRANSID_LIMIT+ORCCConstants.ORCC_TRANSID_BASE;
	}
	// get routeID for EnforcePayment option
	public static int getRouteID()
	{
		return _routeID;
	}

	// get properConfig for EnforcePayment option
	public static PropertyConfig getPropConfig()
	{
		return _propConfig;
	}

}
