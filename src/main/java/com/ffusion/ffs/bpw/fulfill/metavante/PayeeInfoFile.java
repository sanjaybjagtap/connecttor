// Copyright (c) 2001 Financial Fusion, Inc.  All Rights Reserved.

package com.ffusion.ffs.bpw.fulfill.metavante;

import java.io.BufferedReader;
import java.util.ArrayList;

import com.ffusion.ffs.bpw.db.Payee;
import com.ffusion.ffs.bpw.db.PayeeToRoute;
import com.ffusion.ffs.bpw.db.PmtInstruction;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.PayeeInfo;
import com.ffusion.ffs.bpw.interfaces.PayeeRouteInfo;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.util.FFSDebug;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

///////////////////////////////////////////////////////////////////////////////
// An instace of this class contains data retrieved from a Metavante Payee
// Information File. Format of this response file is described in Appendix B6
// of the Metavante Bill Payment Technical Implementation Guide.
///////////////////////////////////////////////////////////////////////////////
public class PayeeInfoFile
{
    // Variables for the values of the fields retrieved from a file record.
    public String _internalPayeeID;
    public String _payeeName;
    public String _attentionLine;
    public String _addressLine1;
    public String _addressLine2;
    public String _city;
    public String _state;
    public String _zipCode;
    public String _countryCode;
    public String _phoneNumber;
    public String _payeeStatus;
    public String _disbursementType;
    public String _payeeLevelType;

    // Constants for line type indicators.
    private static final char HEADER_LINE_INDICATOR = '5';
    private static final char DETAIL_LINE_INDICATOR = '6';
    private static final char TRAILER_LINE_INDICATOR = '8';
    // Constant for the file type represented by this class.
    private static final String FILE_TYPE = "PAYEE-AD";

    // Constants for the pre-defined values of the field
    private static final String[] COUNTRY_VALUES = { "US" };
    private static final String[] DISBURSEMENT_TYPE_VALUES = { "ELECTRONIC", "CHECK" };
    private static final String[] PAYEE_LEVEL_TYPE_VALUES = { "PERSONAL", "REGIONAL", "NATIONAL", "CUSTOM" };
    private static final String[] PAYEE_STATUS_VALUES = { "PENDING", "ACTIVE", "CLOSED" };

    // BPW payee status constants corresponding to above Metavante constants
    private static final String[] BPW_PAYEE_STATUS_VALUES = { DBConsts.PENDING, DBConsts.ACTIVE, DBConsts.CLOSED };


    ////////////////////////////////////////////////////////////////////////////
    // Returns BPW constant corresponding to _payeeStatus value.
    ////////////////////////////////////////////////////////////////////////////
    private String getPayeeStatus() throws Exception
    {
	if( _payeeStatus == null || _payeeStatus.length() == 0 ) {
	    warning( "Missing Payee Status value. Defaulting to \"" +
	    	     DBConsts.ACTIVE + "\"." );
	    return DBConsts.ACTIVE;
	}
	
	for( int i = 0; i < PAYEE_STATUS_VALUES.length; i++ ) {
            if( _payeeStatus.equalsIgnoreCase( PAYEE_STATUS_VALUES[i] ) ) {
                return BPW_PAYEE_STATUS_VALUES[i];
            }
        }

	warning( "Unknown Payee Status value. Defaulting to \"" +
	    	 DBConsts.ACTIVE + "\"." );
        return DBConsts.ACTIVE;

    }


    ////////////////////////////////////////////////////////////////////////////
    // Populates the static cache with the contents in the database.
    ////////////////////////////////////////////////////////////////////////////
    public static void init() throws Exception
    {
	}

   
    ///////////////////////////////////////////////////////////////////////
    // Update corresponding row in the database.
    ///////////////////////////////////////////////////////////////////////
    private void storeToDB( FFSConnectionHolder dbh ) throws Exception
    {
	boolean personal = "PERSONAL".equalsIgnoreCase( _payeeLevelType );	      
	PayeeInfo pi = new PayeeInfo();
	pi.ExtdPayeeID = _internalPayeeID;
	pi.PayeeType = personal ? DBConsts.PERSONAL : DBConsts.GLOBAL;
	pi.PayeeName = _payeeName;
	if( _attentionLine == null ) {
	    pi.Addr1 = _addressLine1;
	    pi.Addr2 = _addressLine2;
	} else {
	    pi.Addr1 = _attentionLine;
	    pi.Addr2 = _addressLine1;
	    pi.Addr3 = _addressLine2;
	}
	pi.City = _city;
	pi.State = _state;
	pi.Zipcode = _zipCode;
	pi.Country = _countryCode;
	pi.Phone = _phoneNumber;
	pi.RouteID =  MetavanteHandler.getRouteID();
	pi.LinkPayeeID = null;
	pi.Status = getPayeeStatus();
        if (!pi.Status.equals(DBConsts.ACTIVE) && !pi.Status.equals(DBConsts.CLOSED)) {
            return;  // do nothing if status is PENDING
        }
	pi.DisbursementType = _disbursementType;
	pi.PayeeLevelType = _payeeLevelType;

	PayeeRouteInfo pri = createPayeeRouteInfo( pi );

	addPayee( pi, pri, dbh );
    }


    ///////////////////////////////////////////////////////////////////////
    // Creates a Metavante PayeeRouteInfo object based on data from a
    // PayeeInfo object.
    ///////////////////////////////////////////////////////////////////////
    public static PayeeRouteInfo createPayeeRouteInfo( PayeeInfo pi ) throws Exception
    {
	PayeeRouteInfo pri = new PayeeRouteInfo();
	pri.PayeeID = pi.PayeeID;
	pri.PayeeType = pi.PayeeType;
	pri.PaymentCost = MetavanteHandler.getPaymentCost();
	pri.ExtdPayeeID = pi.ExtdPayeeID;
	pri.RouteID =  MetavanteHandler.getRouteID();
	pri.BankID = null;
	pri.AcctID = null;
	pri.AcctType = null;
	pri.ExtdInfo = null;
	return pri;
    }


    ///////////////////////////////////////////////////////////////////////
    // Stores the info in the array of PayeeInfoFile objects into the DB.
    ///////////////////////////////////////////////////////////////////////
    public static void storeToDB( PayeeInfoFile[] infoFiles,
    				  FFSConnectionHolder dbh )
				  throws Exception
    {
    	 String method = "PayeeInfoFile.storeToDB";
         long start = System.currentTimeMillis();
         int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
	int l = infoFiles.length;
	FFSDebug.log( "Payee Information File parser: adding " + l +
		      " new payees. This may take a while..." );
	for( int i = 0; i < infoFiles.length; i++ ) {
	    infoFiles[i].storeToDB( dbh );
	    FFSDebug.log( "Payee Information File parser: successfully " +
			  "added payee with Internal Payee ID: \"" +
			  infoFiles[i]._internalPayeeID + "\"." );
	}
	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }


    ///////////////////////////////////////////////////////////////////////////
    // Default constructor resulting in an empty Payee Information File object.
    ///////////////////////////////////////////////////////////////////////////
    public PayeeInfoFile() {}


    ///////////////////////////////////////////////////////////////////////////
    // Constructor taking a Detail Line from a Payee Information File
    // and using it to populate its instance data members.
    ///////////////////////////////////////////////////////////////////////////
    public PayeeInfoFile( String line ) throws Exception
    {
        if( line.length() != 260 ) {
            error( "Detail line is not 260 characters long!"); 	
        }
    
        // The first character of the line is supposed to be Record Type.
        // We skip it here and start parsing from the second character.
        parseInternalPayeeID( line.substring( 1, 16 ) );
        parsePayeeName( line.substring( 16, 66 ) );
        parseAttentionLine( line.substring( 66, 101 ) );
        parseAddressLine1( line.substring( 101, 136 ) );
        parseAddressLine2( line.substring( 136, 171 ) );
        parseCity( line.substring( 171, 199 ) );
        parseState( line.substring( 199, 201 ) );
        parseZipCode( line.substring( 201, 210 ) );
        parseCountryCode( line.substring( 210, 220 ) );
        parsePhoneNumber( line.substring( 220, 230 ) );
        parsePayeeStatus( line.substring( 230, 240 ) );
        parseDisbursementType( line.substring( 240, 250 ) );
        parsePayeeLevelType( line.substring( 250, 260 ) );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Internal Payee ID field of the Payee Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseInternalPayeeID( String data ) throws Exception
    {
        data = data.trim();
        if( data.length() != 0 ) {
            _internalPayeeID = data;
        } else {
	    warning( "Value for the mandatory \"Internal Payee ID\" field is missing." );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Payee Name field of the Payee Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parsePayeeName( String data ) throws Exception
    {
        data = data.trim();
        if( data.length() != 0 ) {
            _payeeName = data;
        } else {
	    warning( "Value for the mandatory \"IPayee Name\" field is missing." );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Attention Line field of the Payee Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseAttentionLine( String data ) throws Exception
    {
        data = data.trim();
        if( data.length() != 0 ) {
            _attentionLine = data;
        }        
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Address Line 1 field of the Payee Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseAddressLine1( String data ) throws Exception
    {
        data = data.trim();
        if( data.length() != 0 ) {
            _addressLine1 = data;
        } else {
	    warning( "Value for the mandatory \"Address Line 1\" field is missing." );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Address Line 2 field of the Payee Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseAddressLine2( String data )
    {
        data = data.trim();
        if( data.length() != 0 ) {
            _addressLine2 = data;
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses City field of the Payee Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseCity( String data ) throws Exception
    {
        data = data.trim();
        if( data.length() != 0 ) {
            _city = data;
        } else {
	    warning( "Value for the mandatory \"City\" field is missing." );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses State field of the Payee Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseState( String data ) throws Exception
    {
        data = data.trim();
        if( data.length() != 0 ) {
            _state = data;
        } else {
	    warning( "Value for the mandatory \"State\" field is missing." );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Zip Code field of the Payee Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseZipCode( String data ) throws Exception
    {
        data = data.trim();
        if( data.length() != 0 ) {
            _zipCode = data;
        } else {
	    warning( "Value for the mandatory \"Zip Code\" field is missing." );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Country Code field of the Payee Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseCountryCode( String data ) throws Exception
    {
	boolean validValue = false;
        data = data.trim();
        for (int i = 0; i < COUNTRY_VALUES.length; i++ ) {
            if( data.equals( COUNTRY_VALUES[i] ) ) {
                validValue = true;
		break;
            }
        }
	if( !validValue ) {
	    warning( "Value for the mandatory \"Country Code\" field " +
	    	     "doesn't match any of the pre-defined values." );
        }
	_countryCode = data;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Phone Number field of the Payee Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parsePhoneNumber( String data ) throws Exception
    {
        data = MetavanteHandler.removeNumberPadding( data );
        if( data.length() > 0 ) {
            _phoneNumber = data;
        } else {
	    warning( "Value for the mandatory \"Phone Number\" field is missing." );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Payee Status field of the Payee Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parsePayeeStatus( String data ) throws Exception
    {
	boolean validValue = false;
        data = data.trim();
        for( int i = 0; i < PAYEE_STATUS_VALUES.length; i++ ) {
            if( data.equalsIgnoreCase( PAYEE_STATUS_VALUES[i] ) ) {
                validValue = true;
		break;
            }
        }
	if( !validValue ) {
	    warning( "Value for the mandatory \"Payee Status\" field " +
	    	     "doesn't match any of the pre-defined values." );
        }
	_payeeStatus = data;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Disbursement Type field of the Payee Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseDisbursementType( String data ) throws Exception
    {
	boolean validValue = false;
        data = data.trim();
        for (int i=0; i<DISBURSEMENT_TYPE_VALUES.length ; i++) {
            if( data.equalsIgnoreCase(DISBURSEMENT_TYPE_VALUES[i])) {
                validValue = true;
		break;
            }
        }
	if( !validValue ) {
	    warning( "Value for the mandatory \"Disbursement Type\" field " +
	    	     "doesn't match any of the pre-defined values." );
        }
	_disbursementType = data;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Payee Level Type field of the Payee Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parsePayeeLevelType( String data ) throws Exception
    {
	boolean validValue = false;
        data = data.trim();
        for (int i=0; i<PAYEE_LEVEL_TYPE_VALUES.length ; i++) {
            if( data.equalsIgnoreCase(PAYEE_LEVEL_TYPE_VALUES[i])) {
                validValue = true;
		break;
            }
        }
	if( !validValue ) {
	    warning( "Value for the mandatory \"Payee Level Type\" field " +
	    	     "doesn't match any of the pre-defined values." );
        }
	_payeeLevelType = data;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses given Payee Information File and returns an array of
    // PayeeInfoFile objects holding its contents.
    ///////////////////////////////////////////////////////////////////////////
    public static PayeeInfoFile[] parseFile( BufferedReader in )
    throws Exception
    {
    	String method = "PayeeInfoFile.parseFile";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        parseHeader( in.readLine() ); // first line should be the header
        
        ArrayList l = new ArrayList();
        String line = null;
        while( (line = in.readLine() ) != null ) {
            if( line.charAt( 0 ) == DETAIL_LINE_INDICATOR ) {
                l.add( new PayeeInfoFile( line ) ); // another detail line
            } else {
                parseTrailer( line, l.size() ); // this can only be the trailer line
                if( in.readLine() != null ) {
                    warning( "Extra data is available following the trailer line!" );
                }
                break; // trailer reached, no more detail lines
            }
        }        
        
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return (PayeeInfoFile[])l.toArray( new PayeeInfoFile[l.size()] );
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
        throw new Exception( "ERROR! Payee Information File parser: " + msg );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Outputs a warning message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    private static void warning( String msg )
    {
        FFSDebug.log( "WARNING! Payee Information File parser: " + msg );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Outputs an exception and a warning message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    private static void warning( Throwable t, String msg )
    {
        FFSDebug.log( t, "WARNING! Payee Information File parser: " + msg );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Overrides java.lang.Object.toString(). 
    ///////////////////////////////////////////////////////////////////////////
    public String toString()
    {
        String LINE_SEP = System.getProperty( "line.separator" );
        return	"_internalPayeeID = " + (_internalPayeeID == null ? "null" : "\"" + _internalPayeeID + "\"" ) + LINE_SEP +
                "_payeeName = " + (_payeeName == null ? "null" : "\"" + _payeeName + "\"" ) + LINE_SEP +
                "_attentionLine = " + (_attentionLine == null ? "null" : "\"" + _attentionLine + "\"" ) + LINE_SEP +
                "_addressLine1 = " + (_addressLine1 == null ? "null" : "\"" + _addressLine1 + "\"" ) + LINE_SEP +
                "_addressLine2 = " + (_addressLine2 == null ? "null" : "\"" + _addressLine2 + "\"" ) + LINE_SEP +
                "_city = " + (_city == null ? "null" : "\"" + _city + "\"" ) + LINE_SEP +
                "_state = " + (_state == null ? "null" : "\"" + _state + "\"" ) + LINE_SEP +
                "_zipCode = " + (_zipCode == null ? "null" : "\"" + _zipCode + "\"" ) + LINE_SEP +
                "_countryCode = " + (_countryCode == null ? "null" : "\"" + _countryCode + "\"" ) + LINE_SEP +
                "_phoneNumber = " + (_phoneNumber == null ? "null" : "\"" + _phoneNumber + "\"" ) + LINE_SEP +
                "_payeeStatus = " + (_payeeStatus == null ? "null" : "\"" + _payeeStatus + "\"" ) + LINE_SEP +
                "_disbursementType = " + (_disbursementType == null ? "null" : "\"" + _disbursementType + "\"" ) + LINE_SEP +
                "_payeeLevelType = " + (_payeeLevelType == null ? "null" : "\"" + _payeeLevelType + "\"" ) + LINE_SEP;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Adds a new payee to the DB. AutoCommit should be set to false
    // before calling this method.
    ///////////////////////////////////////////////////////////////////////////
    private static void addPayee( PayeeInfo payeeinfo,
    				  PayeeRouteInfo routeinfo,
				  FFSConnectionHolder dbh )
				  throws Exception
    {
	PayeeInfo oldPayeeInfo =
		Payee.findPayeeByExtendedID( payeeinfo.ExtdPayeeID, dbh );

	Payee payee = new Payee( payeeinfo );
	
	if( oldPayeeInfo == null ) {
	    payee.setPayeeID();
	    payee.storeToDB( dbh );
	    routeinfo.PayeeID = payee.getPayeeID();
	    PayeeToRoute pr = new PayeeToRoute( routeinfo );
	    pr.storeToDB( dbh );
	} else {
	    payeeinfo.PayeeID = oldPayeeInfo.PayeeID;
	    routeinfo.PayeeID = oldPayeeInfo.PayeeID;
	    updatePayee( payeeinfo, oldPayeeInfo, routeinfo, dbh );
	}
    }


    ///////////////////////////////////////////////////////////////////////////
    // Updates an existing payee in the DB. AutoCommit should be set to false
    // before calling this method.
    ///////////////////////////////////////////////////////////////////////////
    public static void updatePayee( PayeeInfo newPayeeInfo,
				    PayeeInfo oldPayeeInfo,
				    PayeeRouteInfo routeinfo,
				    FFSConnectionHolder dbh )
				    throws Exception
    {
    	String method = "PayeeInfoFile.updatePayee";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
	Payee payee = new Payee( newPayeeInfo );
	boolean isRefered = PmtInstruction.hasPendingPmt( newPayeeInfo.PayeeID, dbh ) ||
  			    Payee.hasPendingLink( newPayeeInfo.PayeeID, dbh );
	if( isRefered ) {
	    PayeeInfo[] pi = new PayeeInfo[]{ oldPayeeInfo };
	    if( payee.matchPayee( pi ) == null ) {
		warning( "Cannot modify payee information because this payee is being refered." );
		if( oldPayeeInfo.Status.equals( DBConsts.INPROCESS ) && //they are the same object?
		    newPayeeInfo.Status.equals( DBConsts.ACTIVE ) ) {//////////////////////////////
		    // update only the status from INPROCESS to ACTIVE
		    payee = new Payee( oldPayeeInfo );
		    payee.setStatus( DBConsts.ACTIVE );
		} else {
			PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
		    return;
		}
	    }
	}
	payee.update( dbh );
	PayeeToRoute pr = new PayeeToRoute( routeinfo );
	pr.updateOrInsert( dbh );
	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }
    
}
