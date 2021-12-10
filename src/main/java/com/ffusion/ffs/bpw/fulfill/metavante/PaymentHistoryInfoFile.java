// Copyright (c) 2001 Financial Fusion, Inc.  All Rights Reserved.

package com.ffusion.ffs.bpw.fulfill.metavante;

import java.io.BufferedReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.db.Payee;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.PayeeInfo;
import com.ffusion.ffs.bpw.interfaces.PmtTrnRslt;
import com.ffusion.ffs.bpw.master.BackendProcessor;
import com.ffusion.ffs.bpw.util.BPWUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.db.FFSResultSet;
import com.ffusion.ffs.util.FFSDebug;

///////////////////////////////////////////////////////////////////////////////
// An instace of this class contains data retrieved from a Metavante Payment
// History Information File. Format of this response file is described in
// Appendix B8 of the Metavante Bill Payment Technical Implementation Guide.
///////////////////////////////////////////////////////////////////////////////
public class PaymentHistoryInfoFile
{
    // Variables for the values of the fields retrieved from a file record.
    public String _confirmationNumber;
    public String _paymentStatus;
    public Date   _dateTimeOfStatusChange;
    public String _consumerID;
    public Float  _amount;
    public String _internalPayeeID;
    public Integer _consumerPayeeRefNumber;
    public String _transactionType;
    public String _detailType;
    public String _ACHTraceNumber;

    public static BackendProcessor _backendProcessor;

    // Used for detecting the confirmation number overflow condition
    public static final int  METAVANTE_MAX_CONFID = 99999999;

    // Constants for line type indicators.
    private static final char HEADER_LINE_INDICATOR = '5';
    private static final char DETAIL_LINE_INDICATOR = '6';
    private static final char TRAILER_LINE_INDICATOR = '8';

    // Constant for the file type represented by this class.
    private static final String FILE_TYPE = "HISTORY-AD";

    // Constants for the pre-defined values of the field
    private static final String[] PAYMENT_STATUS_VALUES = { "PENDING", "PROCESSED" , "HOLD", "STOPPED" };
    private static final String[] TRANSACTION_TYPE_VALUES = { "CURRENT", "FUTURE", "RECURRING", "MANUAL", "ADJUSTCR" };
    private static final String[] DETAIL_TYPE_VALUES = { "ORIGINAL", "RESENDCR", "REVERSALCR"};
    
    // Date and time format used for various fields
    private static final SimpleDateFormat _dt = new SimpleDateFormat( "yyyyMMddhh:mm:ss" );
    
    private static final String SQL_UPDATE_PAYMENT =
    	"UPDATE BPW_PmtInstruction SET PayeeID=?, PayeeListID=?, StartDate=?, Status=? WHERE SrvrTID=?";

    private static final String SQL_FIND_CUST_PAYEE =
    	"SELECT PayeeID FROM BPW_CustomerPayee WHERE PayeeListID = ? AND CustomerID = ?";

    ////////////////////////////////////////////////////////////////////////////
    // Populates the static cache with the contents in the database.
    ////////////////////////////////////////////////////////////////////////////
    public static void init() throws Exception {
		if (_backendProcessor == null){
			_backendProcessor = new BackendProcessor();
		}
	}
    

    ///////////////////////////////////////////////////////////////////////
    // Stores information from an array of PaymentHistoryInfoFile objects
    // into the database.
    ///////////////////////////////////////////////////////////////////////
    public static void storeToDB( PaymentHistoryInfoFile[] infoFiles,
    				  FFSConnectionHolder dbh )
				  throws Exception
    {
		if (_backendProcessor == null){
			_backendProcessor = new BackendProcessor();
		}
	for( int i = 0; i < infoFiles.length; i++ ) {
	    infoFiles[i].storeToDB( dbh );
	}
    }






    ///////////////////////////////////////////////////////////////////////
    // Stores information from an instance of PaymentHistoryInfoFile object
    // into the database.
    ///////////////////////////////////////////////////////////////////////
    private void storeToDB( FFSConnectionHolder dbh ) throws Exception
    {
	// Check if we have required field values.
	
	if( _confirmationNumber == null ) {
	    warning( "Mandatory field \"Confirmation Number\" is " +
		     "missing. Record for this payment cannot be processed!" );
	    return;
	}
	
	if( _consumerPayeeRefNumber == null ) {
	    warning( "Mandatory field \"Consumer-Payee Reference Number\" is " +
		     "missing. Record for payment with Confirmation Number: \"" +
		     _confirmationNumber + "\" cannot be processed!" );
	    return;
	}

	if( _internalPayeeID == null ) {
	    warning( "Mandatory field \"Internal Payee ID\" is missing" +
		     "Record for payment with Confirmation Number: \"" +
		     _confirmationNumber + "\" cannot be processed!" );
	    return;
	}
	
	String status = null;
	if( "PROCESSED".equalsIgnoreCase( _paymentStatus ) ) {
	    status = DBConsts.PROCESSEDON;
	} else {
	    status = DBConsts.FAILEDON;
	}

	// Get BPW payee id corresponding to given Metavante payee id.
	PayeeInfo p = Payee.findPayeeByExtendedID( _internalPayeeID, dbh );
	if( p == null || p.PayeeID == null ) {
	    warning( "No BPW_Payee record can be located for ExtdPayeeID \"" +
		     _internalPayeeID + "\". Record for payment with Confirmation Number: \"" +
		     _confirmationNumber + "\" cannot be processed!" );
	    return;
	}

	// Check if there exists a customer-payee relation for this payment.
	ConsumerCrossReferenceInfoFile xref = 
            ConsumerCrossReferenceInfoFile.lookupByConsumerID( _consumerID, dbh );
	if( xref == null || xref._sponsorConsumerID == null ) {
	    warning( "No BPW_ConsumerCrossReference record can be found for Consumer ID \""
		     + _consumerID + "\". Record for payment with Confirmation Number: \"" +
		     _confirmationNumber + "\" cannot be processed!" );
	    return;
	}
	String customerID = xref._sponsorConsumerID;

	Object[] args =
	{
	    _consumerPayeeRefNumber,
	    customerID
	};

	FFSResultSet rset = null;
	try {
	    rset = DBUtil.openResultSet( dbh, SQL_FIND_CUST_PAYEE, args );
	    if( !rset.getNextRow() ) {
		warning( "No BPW_CustomerPayee record can be found for CustomerID: \"" +
			 customerID + "\" and PayeeListID: \"" + _consumerPayeeRefNumber +
			 "\". Record for payment with Confirmation Number: \"" +
			 _confirmationNumber + "\" cannot be processed!" );
		return;
	    }
	} finally {
	    try { if( rset != null ) rset.close(); } catch( Exception e ) {}
	}

	// Format integer holding StartDate value.
	if( _dateTimeOfStatusChange == null ) {
	    warning( "Mandatory fields \"Date/Time of Status Change\" are missing. " +
		     "Record for payment with Confirmation Number: \"" +
		     _confirmationNumber + "\" cannot be processed!" );
	    return;
	}
	Calendar cal = Calendar.getInstance();
	cal.setTime( _dateTimeOfStatusChange );
	int startDate = cal.get( Calendar.YEAR ) * 1000000 + 
		 ( cal.get( Calendar.MONTH ) + 1 ) * 10000 +    // Jan=0+1, Feb=1+1, Mar=2+1, ..
		 cal.get( Calendar.DAY_OF_MONTH ) * 100;

	// get SrvrTID from confirmation number
	String srvrTID;
	try {
            int confID = Integer.parseInt( _confirmationNumber );
            String srvrTidStr = DBUtil.findNextIndexString(dbh, DBConsts.SRVRTID);
            int currentSrvrTid = Integer.parseInt( srvrTidStr );
            int srvrTIDint = DBUtil.computeForSrvrTID( confID, currentSrvrTid, METAVANTE_MAX_CONFID );
            srvrTID = Integer.toString(srvrTIDint);
	} catch( NumberFormatException e ) {
	    warning( "Mandatory field \"Confirmation Number\" is " +
		     "invalid. Record for this payment cannot be processed!" );
	    return;
	}
	
	// Update BPW_PmtInstruction record
	Object[] args2 = {
	    p.PayeeID,
	    _consumerPayeeRefNumber,
	    new Integer( startDate ),
	    status,
	    srvrTID
	};
	DBUtil.executeStatement( dbh, SQL_UPDATE_PAYMENT, args2 );
	String extdPmtInfo = "";
	if (_consumerPayeeRefNumber != null){
		extdPmtInfo = _consumerPayeeRefNumber.toString();
	}
	int pmtStatus = 0;
	if (!status.equalsIgnoreCase(DBConsts.PROCESSEDON)){
		 pmtStatus = 2000;
	}

	PmtTrnRslt pmtRslt = new PmtTrnRslt(customerID,
             srvrTID,  pmtStatus, "Payment processed",extdPmtInfo);

	try{
		_backendProcessor.processOnePmtRslt(pmtRslt, dbh);
	}catch(Exception e){
		e.printStackTrace();
	}

    }


    ///////////////////////////////////////////////////////////////////////
    // Stores information from an instance of PaymentHistoryInfoFile object
    // into the database.
    ///////////////////////////////////////////////////////////////////////
    public static void importToDB(PmtTrnRslt[] pmtRslt, FFSConnectionHolder dbh) throws Exception
    {

		if (pmtRslt != null){
			int len = pmtRslt.length;
			warning("Total # of pmts to be processed: " + len);
			int i = 0;
			for (i = 0; i < len; i++){
				try{ 
					warning("Processing trans: index: " + i + " customerID: " + pmtRslt [i].customerID
					+ " srvrTid : " +  pmtRslt [i].srvrTid + " status: " +  pmtRslt [i].status); 
					_backendProcessor.processOnePmtRslt(pmtRslt [i], dbh);
				}catch(Exception e){
					if (pmtRslt [i] != null){
						warning("Failed to Process trans: index: " + i + " customerID: " + pmtRslt [i].customerID
					+ " srvrTid : " +  pmtRslt [i].srvrTid + " status: " +  pmtRslt [i].status); 
					} else {
						warning("Failed to Process trans: index: " + i + "pmtRslt [i]: " +  pmtRslt [i]);
					}
					e.printStackTrace();
				}
			
			}
			warning("Total processed  pmts: " + i);
		} else { 
			warning("Invlaid Pmt result array passed: " + pmtRslt);
		}
		warning("Import Data to DB is done");
    }
    ///////////////////////////////////////////////////////////////////////////
    // Default constructor resulting in an empty Payment History Information
    // File object.
    ///////////////////////////////////////////////////////////////////////////
    public PaymentHistoryInfoFile() {}


    ///////////////////////////////////////////////////////////////////////////
    // Constructor taking a Detail Line from a Payment History Information File
    // and using it to populate its instance data members.
    ///////////////////////////////////////////////////////////////////////////
    public PaymentHistoryInfoFile( String line ) throws Exception
    {
		if (_backendProcessor == null){
			_backendProcessor = new BackendProcessor();
		}

        if( line.length() != 106 && line.length() != 121 ) {
            error( "Detail line is not 106 or 121 characters long!" );
        }
    
        // The first character of the line is supposed to be Record Type.
        // We skip it here and start parsing from the second character.
        parseConfirmationNumber( line.substring( 1, 9 ) );
        parsePaymentStatus( line.substring( 9, 19 ) );
        parseDateTimeOfStatusChange( line.substring( 19, 35 ) );
        parseConsumerID( line.substring( 35, 57 ) );
        parseAmount( line.substring( 57, 67 ) );
        parseInternalPayeeID( line.substring( 67, 82 ) );
        parseConsumerPayeeRefNumber( line.substring( 82, 86 ) );
        parseTransactionType( line.substring( 86, 96 ) );
        parseDetailType( line.substring( 96, 106 ) );
        if( line.length() == 121 ) {
            parseACHTraceNumber( line.substring( 106, 121 ) );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Confirmation Number field of the Payment History Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseConfirmationNumber( String data ) throws Exception
    {
        data = MetavanteHandler.removeNumberPadding( data );
        if( data.length() > 0 ) {
            _confirmationNumber = data;
        } else {
            warning( "Value for the mandatory \"Confirmation Number\" field is missing." );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Payment Status field of the Payment History Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parsePaymentStatus( String data ) throws Exception
    {
	boolean validValue = false;
        data = data.trim();
        for (int i=0; i < PAYMENT_STATUS_VALUES.length; i++){
	    if (data.equalsIgnoreCase(PAYMENT_STATUS_VALUES[i])){
	        validValue = true;
		break;
	    }
	}
	if( !validValue ) {
	    warning( "Value for the mandatory \"Payment Status\" field " +
	    	     "doesn't match any of the pre-defined values." );
        }
	_paymentStatus = data;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Date and Time of Status Change field of the Payment History Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseDateTimeOfStatusChange( String data )
    throws Exception
    {
        try {
	    _dateTimeOfStatusChange = _dt.parse(data);			
	} catch (java.text.ParseException e) {
	    warning( "Incorrect format of \"Date/Time of Status Change\" fields." );
	}
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Consumer ID field of the Payment History Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseConsumerID( String data )
    throws Exception
    {
        data = data.trim();
        if( data.length() != 0 ) {
	    _consumerID = data;
	} else {
	    warning( "Value for the mandatory \"Consumer ID\" field is missing." );
	}
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Amount field of the Payment History Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseAmount( String data ) throws Exception
    {
        data = MetavanteHandler.removeNumberPadding( data );
	if( data.length() > 0 ) {
	    // Metavante amount fields have no decimal point 
	    _amount = new Float(BPWUtil.getBigDecimal(data).movePointLeft(2).floatValue());
	} else {
	    warning( "Value for the mandatory \"Amount\" field is missing." );
	}
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Internal Payee ID field of the Payment History Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseInternalPayeeID( String data )
    throws Exception
    {
	data = data.trim();
	if( data.length() != 0 ) {
	    _internalPayeeID = data;
	} else {
	    warning( "Value for the mandatory \"Payee ID\" field is missing." );
	}
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Consumer-Payee Reference Number field of the Payment History 
    // Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseConsumerPayeeRefNumber( String data )
    throws Exception
    {
        data = MetavanteHandler.removeNumberPadding( data );
	if( data.length() > 0 ) {
	    _consumerPayeeRefNumber = new Integer( data );
	} else {
	    warning( "Value for the mandatory \"Consumer-Payee Reference Number\" field is missing." );
	}
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Transaction Type field of the Payment History Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseTransactionType( String data ) throws Exception
    {
	boolean validValue = false;
        data = data.trim();
	for (int i=0; i< TRANSACTION_TYPE_VALUES.length; i++) {
	    if( data.equalsIgnoreCase(TRANSACTION_TYPE_VALUES[i])) {
	        validValue = true;
		break;
	    }
    	}
	if( !validValue ) {
	    warning( "Value for the mandatory \"Transaction Type\" field " +
	    	     "doesn't match any of the pre-defined values." );
        }
	_transactionType = data;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Detail Type field of the Payment History Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseDetailType( String data ) throws Exception
    {
	boolean validValue = false;
        data = data.trim();
        for (int i=0; i< DETAIL_TYPE_VALUES.length; i++) {
	    if( data.equalsIgnoreCase(DETAIL_TYPE_VALUES[i])) {
	        validValue = true;
		break;
	    }
	}
	if( !validValue ) {
	    warning( "Value for the mandatory \"Detail Type\" field " +
	    	     "doesn't match any of the pre-defined values." );
        }
	_detailType = data;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses ACH Trace Number field of the Payment History Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseACHTraceNumber( String data )
    {
        data = data.trim();
	if( data.length() != 0 ) {
	    _ACHTraceNumber = data;
	}
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses given Payment History Information File and returns an array of
    // PaymentHistoryInfoFile objects holding its contents.
    ///////////////////////////////////////////////////////////////////////////
    public static PaymentHistoryInfoFile[] parseFile( BufferedReader in )
    throws Exception
    {
        parseHeader( in.readLine() ); // first line should be the header
    
        ArrayList l = new ArrayList();
        String line = null;
        while( (line = in.readLine() ) != null ) {
	    if( line.charAt( 0 ) == DETAIL_LINE_INDICATOR ) {
	        l.add( new PaymentHistoryInfoFile( line ) ); // another detail line
	    } else {
	        parseTrailer( line, l.size() ); // this can only be the trailer line
	        if( in.readLine() != null ) {
	            warning( "Extra data is available following the trailer line." );
	        }
	        break; // trailer reached, no more detail lines
	    }
        }
	return (PaymentHistoryInfoFile[])l.toArray( new PaymentHistoryInfoFile[l.size()] );
    }
        

    ///////////////////////////////////////////////////////////////////////////
    // Parses header line of a Payment History Information File.
    ///////////////////////////////////////////////////////////////////////////
    private static void parseHeader( String line ) throws Exception
    {
        // verify that header line has expected length
        if( line.length() != 48 ) {
	    error( "Header line is not 48 characters long!" );
	}

	// verify that header line starts with expected character
	char c = line.charAt( 0 );
	if( c != HEADER_LINE_INDICATOR ) {
	    error( "Unexpected value of the \"Record Type\" header field: '" +
	    	   c + "', expected: '" + HEADER_LINE_INDICATOR + "'" );
	}

	// verify that File Type field has expected value
	String s = line.substring( 1, 16 );
	if( !s.startsWith( FILE_TYPE ) ) {
	    error( "Unexpected value of the \"File Type\" header field: \"" +
	    	   s + "\", expected: \"" + FILE_TYPE + "\"" );
	}

	// TODO: it might be wise to check other header fields when we know
	// what values to expect.
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses trailer line of a Payment History Information File.
    ///////////////////////////////////////////////////////////////////////////
    private static void parseTrailer( String line, int expectedRecCount )
    throws Exception
    {
        // verify that trailer line has expected length
        if( line.length() != 21 ) {
	    error( "Trailer line is not 21 characters long!" );
	}

	// verify that trailer line starts with expected character
	char c = line.charAt( 0 );
	if( c != TRAILER_LINE_INDICATOR ) {
	    error( "Unexpected value of the \"Record Type\" trailer field: '" +
	    	   c + "', expected: '" + TRAILER_LINE_INDICATOR + "'" );
	}

	// verify that Record Count field has expected value
	String s = line.substring( 1, 11 );
	try {
	    int i = Integer.parseInt( s );
	    if( i != expectedRecCount ) {
		warning( "Unexpected value of the \"Record Count\" trailer field: " +
			 i + ", expecting: " + expectedRecCount );
	    }
	} catch( Exception e ) {
	    warning( "Invalid value of the \"Record Count\" trailer field: \"" +
	    	     s + "\", expecting: " + expectedRecCount );
	}

	// TODO: it might be wise to check "Total Amount" field value when we
	// know what value to expect.
    }

	
    ///////////////////////////////////////////////////////////////////////////
    // Throws an Exception with an error message.
    ///////////////////////////////////////////////////////////////////////////
    private static void error( String msg ) throws Exception
    {
        throw new Exception( "ERROR! Payment History Information File parser: " + msg );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Outputs a warning message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    private static void warning( String msg )
    {
        FFSDebug.log( "WARNING! Payment History Information File parser: " + msg );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Overrides java.lang.Object.toString(). 
    ///////////////////////////////////////////////////////////////////////////
    public String toString()
    {
        String LINE_SEP = System.getProperty( "line.separator" );
        return "_confirmationNumber = " + (_confirmationNumber == null ? "null" : "\"" + _confirmationNumber + "\"" ) + LINE_SEP +
                "_paymentStatus = " + (_paymentStatus == null ? "null" : "\"" + _paymentStatus + "\"" ) + LINE_SEP +
                "_dateTimeOfStatusChange = " + _dateTimeOfStatusChange + LINE_SEP +
                "_consumerID = " + (_consumerID == null ? "null" : "\"" + _consumerID + "\"" ) + LINE_SEP +
                "_amount = " + _amount + LINE_SEP +
                "_internalPayeeID = " + (_internalPayeeID == null ? "null" : "\"" + _internalPayeeID + "\"" ) + LINE_SEP +
                "_consumerPayeeRefNumber = " + _consumerPayeeRefNumber + LINE_SEP +
                "_transactionType = " + (_transactionType == null ? "null" : "\"" + _transactionType + "\"" ) + LINE_SEP +
                "_detailType = " + (_detailType == null ? "null" : "\"" + _detailType + "\"" ) + LINE_SEP +
                "_ACHTraceNumber = " + (_ACHTraceNumber == null ? "null" : "\"" + _ACHTraceNumber + "\"" ) + LINE_SEP;
    }
}
