// Copyright (c) 2001 Financial Fusion, Inc.  All Rights Reserved.

package com.ffusion.ffs.bpw.fulfill.metavante;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;

import com.ffusion.ffs.bpw.db.CustRoute;
import com.ffusion.ffs.bpw.db.Customer;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.interfaces.CustomerInfo;
import com.ffusion.ffs.bpw.interfaces.CustomerRouteInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.master.CommonProcessor;
import com.ffusion.ffs.bpw.util.BPWUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.db.FFSResultSet;
import com.ffusion.ffs.util.FFSDebug;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

///////////////////////////////////////////////////////////////////////////////
// An instace of this class contains data retrieved from a Metavante Consumer
// Information File. Format of this response file is described in Appendix B1
// of the Metavante Bill Payment Technical Implementation Guide.
///////////////////////////////////////////////////////////////////////////////
public class ConsumerInfoFile {
    // Variables for the values of the fields retrieved from a file record.
    public String _customerID;
    public String _consumerID;
    public String _consumerFirstName;
    public String _consumerMiddleInitial;
    public String _consumerLastName;
    public String _consumerSuffix;
    public String _secondaryConsumerFirstName;
    public String _secondaryConsumerMiddleInitial;
    public String _secondaryConsumerLastName;
    public String _secondaryConsumerSuffix;
    public String _addressLine1;
    public String _addressLine2;
    public String _city;
    public String _state;
    public String _zipCode;
    public String _country;
    public String _consumerStatus;
    public String _primaryCountryCode;
    public String _primaryPhone;
    public String _secondaryCountryCode;
    public String _secondaryPhone;
    public String _secondaryConsumerPrimaryCountryCode;
    public String _secondaryConsumerPrimaryPhone;
    public String _secondaryConsumerSecondaryCountryCode;
    public String _secondaryConsumerSecondaryPhone;
    public String _personalSecurityCode;
    public Float  _transactionDollarLimit;
    public String _sponsorID;
    public String _consumerBillingPlan;
    public String _submitDate;


    // SQL statements constants.
    private static final String SQL_FIND_CUSTOMER_BY_NAME_AND_ADDRESS =
    "SELECT ConsumerStatus, CustomerID FROM BPW_Customer WHERE " +
    "FirstName = ? AND MiddleInitial = ? AND LastName = ? AND " +
    "Suffix = ? AND AddressLine1 = ? AND AddressLine2 = ? AND " +
    "City = ? AND State = ? AND ZipCode = ? AND Country = ?";

    private static final String SQL_FIND_CUSTOMER_BY_NAME =
    "SELECT ConsumerStatus, CustomerID FROM BPW_Customer WHERE " +
    "LastName = ? AND FirstName = ?";

    private static final String SQL_DELETE_XREF =
    "DELETE FROM BPW_ConsumerCrossReference WHERE ConsumerID = ?";

    private static final String SQL_DELETE_PROD_ACCESS =
    "DELETE FROM BPW_CustomerProductAccess WHERE ConsumerID = ?";

    private static final String SQL_DELETE_BANK_INFO =
    "DELETE FROM BPW_CustomerBankInfo WHERE ConsumerID = ?";

    private static final String SQL_ENABLE_CUSTOMER_PAYEES =
    "UPDATE BPW_CustomerPayee SET Status = 'ACTIVE' WHERE CustomerID = ?";

    // Constants for line type indicators.
    private static final char HEADER_LINE_INDICATOR = '5';
    private static final char DETAIL_LINE_INDICATOR = '6';
    private static final char TRAILER_LINE_INDICATOR = '8';

    // Constant for the file type represented by this class.
    private static final String FILE_TYPE = "CONSUMER-AD";

    // Constants for the vaild values of the pre-defined fields
    private static final String[] COUNTRY_VALUES = { "US"};
    private static final String[] CONSUMER_STATUS_VALUES = { 
        "PENDING", "ACTIVE", "BLOCKED", "CLOSED", "CANCELLED"};
    private static final String[] CONSUMER_BILLING_PLAN_VALUES = {"PLAN #1"};

    // An HashMap for caching the contents of the database
    public static HashMap _cache;


    ///////////////////////////////////////////////////////////////////////
    // Store the information of the instance to the database with 
    // the given connection.
    ///////////////////////////////////////////////////////////////////////
    private void storeToDB( FFSConnectionHolder dbh ) throws Exception
    {
        try {
            _customerID = getCustomerID( _consumerID, dbh );
            Object[] consIDArg = { _customerID};
            if (Customer.isExists(_customerID, dbh)) {
                // Given customer already exists so we need to update an
                // existing DB row.

                String currentStatus = DBConsts.ACTIVE;
                CustomerRouteInfo cr = CustRoute.getCustomerRoute( _customerID, 
                                                 MetavanteHandler.getRouteID(), dbh );
                if (cr != null) {
                    currentStatus = cr.Status;
                }

                Object[] custIDArg = { _customerID};

                if ( "ACTIVE".equalsIgnoreCase( currentStatus ) ) {
                    // The record is currently active so check if this is
                    // a deactivation request.
                    if ( "BLOCKED".equalsIgnoreCase( _consumerStatus ) ||
                         "CLOSED".equalsIgnoreCase( _consumerStatus ) ||
                         "CANCELLED".equalsIgnoreCase( _consumerStatus ) ) {
                        // Delete payment schedules associated with this customer
                        CommonProcessor.deleteCustomer( dbh, _customerID, 0 );
                        // Delete no longer needed cache.
                        try {
                            if (_consumerID != null && _consumerID.length() != 0) {
                                ConsumerCrossReferenceInfoFile.deleteFromConsumerIDCache(_consumerID);
                            }
                            if (_customerID != null && _customerID.length() != 0) {
                                ConsumerCrossReferenceInfoFile.deleteFromSponsorConsumerCache(_customerID);
                            }
                            if (_consumerID != null && _consumerID.length() != 0) {
                                ConsumerProductAccessInfoFile.deleteFromCache(_consumerID);
                            }
                            if (_consumerID != null && _consumerID.length() != 0) {
                                ConsumerBankInfoFile.deleteFromCache(_consumerID, dbh);
                            }
                        }
                        catch (Exception e) {
                            warning("Failed to clear cache from consumer: " + _consumerID + 
                                    "\nError: " + FFSDebug.stackTrace(e));
                        }
                        // Delete no longer needed records.
                        DBUtil.executeStatement( dbh, SQL_DELETE_XREF, consIDArg );
                        DBUtil.executeStatement( dbh, SQL_DELETE_PROD_ACCESS, consIDArg );
                        DBUtil.executeStatement( dbh, SQL_DELETE_BANK_INFO, consIDArg );
                    }
                }
                else {
                    // The record is not currently active so check if this is
                    // an activation request.
                    if ( "ACTIVE".equalsIgnoreCase( _consumerStatus ) ) {
                        // Enable customer payees for given consumer.
                        DBUtil.executeStatement( dbh, SQL_ENABLE_CUSTOMER_PAYEES, custIDArg );
                    }
                }
                updateCustomer( dbh );
            }
            else if ( !activateCustomerByNameAndAddress( dbh ) ) {
                // New customer so we insert a new DB row.
                addCustomer( dbh );
            }
        }
        finally {
        }

        // Adding record to static cache
        synchronized (_cache) {            
            _cache.put( _customerID, this );
        } 
    }

    ///////////////////////////////////////////////////////////////////////////
    // Looks up Customer ID in the Consumer Cross Reference Information File.
    ///////////////////////////////////////////////////////////////////////////
    private static String getCustomerID( String consumerID, FFSConnectionHolder dbh )
    throws Exception
    {
        ConsumerCrossReferenceInfoFile xref = 
            ConsumerCrossReferenceInfoFile.lookupByConsumerID( consumerID, dbh );
        //if ( xref != null ||  xref._sponsorConsumerID != null ) {
        if ( xref != null &&  xref._sponsorConsumerID != null ) {
            return xref._sponsorConsumerID;
        }
        else {
            warning( "No Cross Reference record can be found for ConsumerID \"" +
                     consumerID + "\". Given Consumer Information record cannot be processed." );
            return null;
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Looks up Customer record using name and address, and activates it.
    // Returns false if either given customer record does not exist or if
    // duplicate ones exist or if the record was already activated.
    ///////////////////////////////////////////////////////////////////////////
    private boolean activateCustomerByNameAndAddress( FFSConnectionHolder dbh )
    throws Exception
    {
        if ( !"ACTIVE".equalsIgnoreCase( _consumerStatus ) ) {
            return false; // not an activation request
        }

        FFSResultSet rset = null;
        try {
            // first try to find a customer with given last and first name
            Object[] args = {_consumerLastName, _consumerFirstName};
            rset = DBUtil.openResultSet( dbh, SQL_FIND_CUSTOMER_BY_NAME, args);
            if (!rset.getNextRow()) {
                // no customer has given name
                return false;
            }

            String status = rset.getColumnString( 1 );
            String customerID = rset.getColumnString( 2 );

            if ( rset.getNextRow() ) {
                // multiple customers have given name so we must pick one by
                // matching the customer's address
                Object[] args2 = {
                    _consumerFirstName,
                    _consumerMiddleInitial,
                    _consumerLastName,
                    _consumerSuffix,
                    _addressLine1,
                    _addressLine2,
                    _city,
                    _state,
                    _zipCode,
                    _country
                };
                rset.close(); // don't need the old result set anymore
                rset = DBUtil.openResultSet(dbh, SQL_FIND_CUSTOMER_BY_NAME_AND_ADDRESS,args2);
                if (!rset.getNextRow()) {
                    // None of the multiple customers with given name has a matching
                    // address so we just assume this is not a reactivation request
                    return false;
                }
                status = rset.getColumnString( 1 );
                customerID = rset.getColumnString( 2 );
                if ( rset.getNextRow() ) {
                    // Multiple customers with given name have a matching address
                    // so we just assume this is not a reactivation request.
                    return false;
                }
            }

            // Activate the record if not already active.
            if ( "ACTIVE".equalsIgnoreCase( status ) ) {
                return false; // record already active 
            }
            //String customerID = getCustomerID( consumerID );
            if ( customerID == null ) {
                return false; // missing xref record
            }
            DBUtil.executeStatement( dbh, SQL_ENABLE_CUSTOMER_PAYEES, new Object[]{ customerID});
            updateCustomer(customerID, dbh);
            return true;
        }
        finally {
            try {
                if ( rset != null ) rset.close();
            }
            catch ( Exception e ) {
            }
        }
    }



    ///////////////////////////////////////////////////////////////////////
    // Updates an existing BPW_Customer DB record.
    ///////////////////////////////////////////////////////////////////////
    private void updateCustomer( FFSConnectionHolder dbh ) throws Exception
    {
        CustomerInfo custinfo = new CustomerInfo();
        custinfo.customerID = _customerID;
        custinfo.firstName = _consumerFirstName;
        custinfo.initial = _consumerMiddleInitial;
        custinfo.lastName = _consumerLastName;
        custinfo.suffix = _consumerSuffix;
        custinfo.jointFirstName = _secondaryConsumerFirstName;
        custinfo.jointInitial = _secondaryConsumerMiddleInitial;
        custinfo.jointLastName = _secondaryConsumerLastName;
        custinfo.jointSuffix = _secondaryConsumerSuffix;
        custinfo.addressLine1 = _addressLine1;
        custinfo.addressLine2 = _addressLine2;
        custinfo.city = _city;
        custinfo.state = _state;
        custinfo.zipcode = _zipCode;
        custinfo.country = _country;
        custinfo.status = _consumerStatus;
        if (_consumerStatus.equals("ACTIVE")) 
            custinfo.status = DBConsts.ACTIVE;
        else if (_consumerStatus.equals("CLOSED")) 
            custinfo.status = DBConsts.CLOSED;
        else if (_consumerStatus.equals("CANCELLED")) 
            custinfo.status = DBConsts.CLOSED;
        else if (_consumerStatus.equals("BLOCKED")) 
            custinfo.status = DBConsts.CLOSED;
        else if (_consumerStatus.equals("PENDING")) 
            return;  // do nothing if status is PENDING
        custinfo.countryCode1 = _primaryCountryCode;
        custinfo.phone1 = _primaryPhone;
        custinfo.countryCode2 = _secondaryCountryCode;
        custinfo.phone2 = _secondaryPhone;
        custinfo.jointCountryCode1 = _secondaryConsumerPrimaryCountryCode;
        custinfo.jointPhone1 = _secondaryConsumerPrimaryPhone;
        custinfo.jointCountryCode2 = _secondaryConsumerSecondaryCountryCode;
        custinfo.jointPhone2 = _secondaryConsumerSecondaryPhone;
        custinfo.securityCode = _personalSecurityCode;
        float dlimit = _transactionDollarLimit.floatValue();
        custinfo.limit = Float.toString( dlimit );
        custinfo.sponsorID = _sponsorID;
        custinfo.billingPlan = _consumerBillingPlan;
        Customer.updateCustomer( custinfo, dbh );
        CustRoute.updateCustRouteStatus( custinfo.customerID, MetavanteHandler.getRouteID(), custinfo.status, dbh);
    }


    ///////////////////////////////////////////////////////////////////////
    // Updates an existing BPW_Customer DB record.
    ///////////////////////////////////////////////////////////////////////
    private void updateCustomer( String oldCustomerID,
                                 FFSConnectionHolder dbh )
    throws Exception
    {
        CustomerInfo custinfo = new CustomerInfo();
        custinfo.customerID = _customerID;
        custinfo.firstName = _consumerFirstName;
        custinfo.initial = _consumerMiddleInitial;
        custinfo.lastName = _consumerLastName;
        custinfo.suffix = _consumerSuffix;
        custinfo.jointFirstName = _secondaryConsumerFirstName;
        custinfo.jointInitial = _secondaryConsumerMiddleInitial;
        custinfo.jointLastName = _secondaryConsumerLastName;
        custinfo.jointSuffix = _secondaryConsumerSuffix;
        custinfo.addressLine1 = _addressLine1;
        custinfo.addressLine2 = _addressLine2;
        custinfo.city = _city;
        custinfo.state = _state;
        custinfo.zipcode = _zipCode;
        custinfo.country = _country;
        custinfo.status = _consumerStatus;
        if (_consumerStatus.equals("ACTIVE")) 
            custinfo.status = DBConsts.ACTIVE;
        else if (_consumerStatus.equals("CLOSED")) 
            custinfo.status = DBConsts.CLOSED;
        else if (_consumerStatus.equals("CANCELLED")) 
            custinfo.status = DBConsts.CLOSED;
        else if (_consumerStatus.equals("BLOCKED")) 
            custinfo.status = DBConsts.CLOSED;
        else if (_consumerStatus.equals("PENDING")) 
            return;  // do nothing if status is PENDING
        custinfo.countryCode1 = _primaryCountryCode;
        custinfo.phone1 = _primaryPhone;
        custinfo.countryCode2 = _secondaryCountryCode;
        custinfo.phone2 = _secondaryPhone;
        custinfo.jointCountryCode1 = _secondaryConsumerPrimaryCountryCode;
        custinfo.jointPhone1 = _secondaryConsumerPrimaryPhone;
        custinfo.jointCountryCode2 = _secondaryConsumerSecondaryCountryCode;
        custinfo.jointPhone2 = _secondaryConsumerSecondaryPhone;
        custinfo.securityCode = _personalSecurityCode;
        float dlimit = _transactionDollarLimit.floatValue();
        custinfo.limit = Float.toString( dlimit );
        custinfo.sponsorID = _sponsorID;
        custinfo.billingPlan = _consumerBillingPlan;
        Customer.updateCustomer( oldCustomerID, custinfo, dbh );
        CustRoute.updateCustRouteStatus( custinfo.customerID, MetavanteHandler.getRouteID(), custinfo.status, dbh);
    }


    ///////////////////////////////////////////////////////////////////////
    // Creates a new BPW_Customer DB record.
    ///////////////////////////////////////////////////////////////////////
    private void addCustomer( FFSConnectionHolder dbh ) throws Exception
    {
        CustomerInfo custinfo = new CustomerInfo();
        custinfo.customerID = _customerID;
        custinfo.firstName = _consumerFirstName;
        custinfo.initial = _consumerMiddleInitial;
        custinfo.lastName = _consumerLastName;
        custinfo.suffix = _consumerSuffix;
        custinfo.jointFirstName = _secondaryConsumerFirstName;
        custinfo.jointInitial = _secondaryConsumerMiddleInitial;
        custinfo.jointLastName = _secondaryConsumerLastName;
        custinfo.jointSuffix = _secondaryConsumerSuffix;
        custinfo.addressLine1 = _addressLine1;
        custinfo.addressLine2 = _addressLine2;
        custinfo.city = _city;
        custinfo.state = _state;
        custinfo.zipcode = _zipCode;
        custinfo.country = _country;
        custinfo.status = _consumerStatus;
        custinfo.countryCode1 = _primaryCountryCode;
        custinfo.phone1 = _primaryPhone;
        custinfo.countryCode2 = _secondaryCountryCode;
        custinfo.phone2 = _secondaryPhone;
        custinfo.jointCountryCode1 = _secondaryConsumerPrimaryCountryCode;
        custinfo.jointPhone1 = _secondaryConsumerPrimaryPhone;
        custinfo.jointCountryCode2 = _secondaryConsumerSecondaryCountryCode;
        custinfo.jointPhone2 = _secondaryConsumerSecondaryPhone;
        custinfo.securityCode = _personalSecurityCode;
        float dlimit = _transactionDollarLimit.floatValue();
        custinfo.limit = Float.toString( dlimit );
        custinfo.sponsorID = _sponsorID;
        custinfo.billingPlan = _consumerBillingPlan;
        Customer.addCustomer( custinfo, dbh );
        CustRoute.insert( dbh, custinfo.customerID, MetavanteHandler.getRouteID() );
    }


    ///////////////////////////////////////////////////////////////////////
    // Creates a connection to the database and stores the info in the 
    // array of Consumer Information Files into the database
    ///////////////////////////////////////////////////////////////////////
    public static void storeToDB( ConsumerInfoFile[] infoFiles,
                                  FFSConnectionHolder dbh )
    throws Exception
    {       
        for ( int i = 0; i < infoFiles.length; i++ ) {
            infoFiles[i].storeToDB( dbh );
        }
    }


    ///////////////////////////////////////////////////////////////////////
    // Parses given Consumer Information File and return an array of
    // ConsumerInfo objects holding its contents.
    ///////////////////////////////////////////////////////////////////////
    public static ConsumerInfoFile[] parseFile( BufferedReader in )
    throws Exception
    {
    	 String method = "ConsumerInfoFile.parseFile";
         long start = System.currentTimeMillis();
         int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        parseHeader( in.readLine() ); // first line should be the header

        ArrayList l = new ArrayList();
        String line = null;
        while ( (line = in.readLine() ) != null ) {
            if ( line.charAt( 0 ) == DETAIL_LINE_INDICATOR ) {
                l.add( new ConsumerInfoFile( line ) ); // another detail line
            }
            else {
                parseTrailer( line, l.size() ); // this can only be the trailer line
                if ( in.readLine() != null ) {
                    warning( "Extra data is available following the trailer line." );
                }
                break; // trailer reached, no more detail lines
            }
        }
        
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return(ConsumerInfoFile[])l.toArray( new ConsumerInfoFile[l.size()] );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses header line of a Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private static void parseHeader( String line ) throws Exception
    {
        // verify that header line has expected length
        if ( line.length() != 48 ) {
            error( "Header line is not 48 characters long!" );
        }

        // verify that header line starts with expected character
        char c = line.charAt( 0 );
        if ( c != HEADER_LINE_INDICATOR ) {
            error( "Unexpected value of the \"Record Type\" header field: '" +
                   c + "', expected: '" + HEADER_LINE_INDICATOR + "'" );
        }

        // verify that File Type field has expected value
        String s = line.substring( 1, 16 );
        if ( !s.startsWith( FILE_TYPE ) ) {
            error( "Unexpected value of the \"File Type\" header field: \"" +
                   s + "\", expected: \"" + FILE_TYPE + "\"" );
        }

        // TODO: it might be wise to check other header fields when we know
        // what values to expect.
    }


    ///////////////////////////////////////////////////////////////////////////
    //Parses trailer line of a Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private static void parseTrailer( String line, int expectedRecCount )
    throws Exception
    {
        // verify that trailer line has expected length
        if ( line.length() != 21 ) {
            error( "Trailer line is not 21 characters long!" );
        }

        // verify that trailer line starts with expected character
        char c = line.charAt( 0 );
        if ( c != TRAILER_LINE_INDICATOR ) {
            error( "Unexpected value of the \"Record Type\" trailer field: '" +
                   c + "', expected: '" + TRAILER_LINE_INDICATOR + "'" );
        }

        // verify that Record Count field has expected value
        String s = line.substring( 1, 11 );
        try {
            int i = Integer.parseInt( s );
            if ( i != expectedRecCount ) {
                warning( "Unexpected value of the \"Record Count\" trailer field: " +
                         i + ", expecting: " + expectedRecCount );
            }
        }
        catch ( Exception e ) {
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
        throw new Exception( "ERROR! Consumer Information File parser: " + msg );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Outputs a warning message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    private static void warning( String msg )
    {
        FFSDebug.log( "WARNING! Consumer Information File parser: " + msg );
    }



    ///////////////////////////////////////////////////////////////////////////
    // Default constructor resulting in an empty ConsumerInfoFile object.
    ///////////////////////////////////////////////////////////////////////////
    public ConsumerInfoFile() {}


    ///////////////////////////////////////////////////////////////////////////
    // Constructor taking a Detail Line from a Consumer Information File
    // and using it to populate its instance data members.
    ///////////////////////////////////////////////////////////////////////////
    public ConsumerInfoFile( String line ) throws Exception
    {
        if ( line.length() != 356 ) {
            error( "Detail line is not 356 characters long!" );
        }

        // The first character of the line is supposed to be Record Type.
        // We skip it here and start parsing from the second character.
        parseConsumerID( line.substring( 1, 23 ) );
        //_customerID = getCustomerID( _consumerID, dbh );
        parseConsumerFirstName( line.substring( 23, 48 ) );
        parseConsumerMiddleInitial( line.substring( 48, 49 ) );
        parseConsumerLastName( line.substring( 49, 74 ) );
        parseConsumerSuffix( line.substring( 74, 79 ) );
        parseSecondaryConsumerFirstName( line.substring( 79, 104 ) );
        parseSecondaryConsumerMiddleInitial( line.substring( 104, 105 ) );
        parseSecondaryConsumerLastName( line.substring( 105, 130 ) );
        parseSecondaryConsumerSuffix( line.substring( 130, 135 ) );
        parseAddressLine1( line.substring( 135, 170 ) );
        parseAddressLine2( line.substring( 170, 205 ) );
        parseCity( line.substring( 205, 233 ) );
        parseState( line.substring( 233, 235 ) );
        parseZipCode( line.substring( 235, 244 ) );
        parseCountry( line.substring( 244, 254 ) );
        parseConsumerStatus( line.substring( 254, 264 ) );
        parsePrimaryCountryCode( line.substring( 264, 267 ) );
        parsePrimaryPhone( line.substring( 267, 277 ) );
        parseSecondaryCountryCode( line.substring( 277, 280 ) );
        parseSecondaryPhone( line.substring( 280, 290 ) );
        parseSecondaryConsumerPrimaryCountryCode( line.substring( 290, 293 ) );
        parseSecondaryConsumerPrimaryPhone( line.substring( 293, 303 ) );
        parseSecondaryConsumerSecondaryCountryCode( line.substring( 303, 306 ) );
        parseSecondaryConsumerSecondaryPhone( line.substring( 306, 316 ) );
        parsePersonalSecurityCode( line.substring( 316, 326 ) );
        parseTransactionDollarLimit( line.substring( 326, 336 ) );
        parsesponsorID( line.substring( 336, 346 ) );
        parseConsumerBillingPlan( line.substring( 346, 356 ) );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Consumer ID field of the Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseConsumerID( String data ) throws Exception
    {
        data = data.trim();
        if ( data.length() != 0 ) {
            _consumerID = data;
        }
        else {
            warning( "Value for the mandatory \"Consumer ID\" field is missing." );
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Consumer First Name field of the Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseConsumerFirstName( String data )
    {
        data = data.trim();
        if ( data.length() != 0 ) {
            _consumerFirstName = data;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Consumer Middle Initial field of the Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseConsumerMiddleInitial( String data )
    {
        data = data.trim();
        if ( data.length() != 0 ) {
            _consumerMiddleInitial = data;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Consumer Last Name field of the Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseConsumerLastName( String data )
    throws Exception
    {
        data = data.trim();
        if ( data.length() != 0 ) {
            _consumerLastName = data;
        }
        else {
            warning( "Value for the mandatory \"Consumer Last Name\" field is missing." );
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Consumer Suffix field of the Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseConsumerSuffix( String data )
    {
        data = data.trim();
        if ( data.length() != 0 ) {
            _consumerSuffix = data;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Secondary Consumer First Name field of the Consumer Information
    // File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseSecondaryConsumerFirstName( String data )
    {
        data = data.trim();
        if ( data.length() != 0 ) {
            _secondaryConsumerFirstName = data;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Secondary Consumer Middle Initial field of the Consumer
    // Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseSecondaryConsumerMiddleInitial( String data )
    {
        data = data.trim();
        if ( data.length() != 0 ) {
            _secondaryConsumerMiddleInitial = data;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Secondary Consumer Last Name field of the Consumer
    // Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseSecondaryConsumerLastName( String data )
    {
        data = data.trim();
        if ( data.length() != 0 ) {
            _secondaryConsumerLastName = data;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Secondary Consumer Suffix field of the Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseSecondaryConsumerSuffix( String data )
    {
        data = data.trim();
        if ( data.length() != 0 ) {
            _secondaryConsumerSuffix = data;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Address Line 1 field of the Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseAddressLine1( String data )
    throws Exception
    {
        data = data.trim();
        if ( data.length() != 0 ) {
            _addressLine1 = data;
        }
        else {
            warning( "Value for the mandatory \"Address Line 1\" field is missing." );
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Address Line 2 field of the Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseAddressLine2( String data )
    {
        data = data.trim();
        if ( data.length() != 0 ) {
            _addressLine2 = data;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses City field of the Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseCity( String data )
    throws Exception
    {
        data = data.trim();
        if ( data.length() != 0 ) {
            _city = data;
        }
        else {
            warning( "Value for the mandatory \"City\" field is missing." );
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses State field of the Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseState( String data )
    throws Exception
    {
        data = data.trim();
        if ( data.length() != 0 ) {
            _state = data;
        }
        else {
            warning( "Value for the mandatory \"State\" field is missing." );
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Zip Code field of the Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseZipCode( String data )
    throws Exception
    {
        data = data.trim();
        if ( data.length() != 0 ) {
            _zipCode = data;
        }
        else {
            warning( "Value for the mandatory \"Zip Code\" field is missing." );
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Country field of the Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseCountry( String data )
    throws Exception
    {
        boolean validValue = false;
        data = data.trim();
        for ( int i=0; i < COUNTRY_VALUES.length ; i++ ) {
            if (data.equalsIgnoreCase(COUNTRY_VALUES[i])) {
                validValue = true;
                break;
            }
        }
        if ( !validValue ) {
            warning( "Value for the mandatory \"Country\" field " +
                     "doesn't match any of the pre-defined values." );
        }
        _country = data;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Consumer Status field of the Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseConsumerStatus( String data )
    throws Exception
    {
        boolean validValue = false;
        data = data.trim();
        for (int i=0; i < CONSUMER_STATUS_VALUES.length ; i++) {
            if (data.equalsIgnoreCase(CONSUMER_STATUS_VALUES[i])) {
                validValue = true;
                break;
            }
        }
        if ( !validValue ) {
            warning( "Value for the mandatory \"Consumer Status\" field " +
                     "doesn't match any of the pre-defined values." );
        }
        _consumerStatus = data;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Primary Country Code field of the Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parsePrimaryCountryCode( String data )
    {
        data = MetavanteHandler.removeNumberPadding( data );
        if ( data.length() > 0 ) {
            _primaryCountryCode = data;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Primary Phone field of the Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parsePrimaryPhone( String data )
    {
        data = MetavanteHandler.removeNumberPadding( data );
        if ( data.length() > 0 ) {
            _primaryPhone = data;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Secondary Country Code field of the Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseSecondaryCountryCode( String data )
    {
        data = MetavanteHandler.removeNumberPadding( data );
        if ( data.length() > 0 ) {
            _secondaryCountryCode = data;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Secondary Phone field of the Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseSecondaryPhone( String data )
    {
        data = MetavanteHandler.removeNumberPadding( data );
        if ( data.length() > 0 ) {
            _secondaryPhone = data;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Secondary Consumer Primary Country Code field of the Consumer
    // Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseSecondaryConsumerPrimaryCountryCode( String data )
    {
        data = MetavanteHandler.removeNumberPadding( data );
        if ( data.length() > 0 ) {
            _secondaryConsumerPrimaryCountryCode = data;
        }
    }

    //////////////////////////////////////////////////////////////////////////
    // Parses Secondary Consumer Primary Phone field of the Consumer
    // Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseSecondaryConsumerPrimaryPhone( String data )
    {
        data = MetavanteHandler.removeNumberPadding( data );
        if ( data.length() > 0 ) {
            _secondaryConsumerPrimaryPhone = data;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Secondary Consumer Secondar yCountry Code field of the Consumer
    // Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseSecondaryConsumerSecondaryCountryCode( String data )
    {
        data = MetavanteHandler.removeNumberPadding( data );
        if ( data.length() > 0 ) {
            _secondaryConsumerSecondaryCountryCode = data;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Secondary Consumer Secondary Phone field of the Consumer
    // Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseSecondaryConsumerSecondaryPhone( String data )
    {
        data = MetavanteHandler.removeNumberPadding( data );
        if ( data.length() > 0 ) {
            _secondaryConsumerSecondaryPhone = data;
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // Parses Personal Security Code field of the Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parsePersonalSecurityCode( String data ) throws Exception
    {
        data = data.trim();
        if ( data.length() != 0 ) {
            _personalSecurityCode = data;
        }
        else {
            warning( "Value for the mandatory \"Personal Security Code\" field is missing." );
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Transaction Dollar Limit field of the Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseTransactionDollarLimit( String data ) throws Exception
    {
        data = MetavanteHandler.removeNumberPadding( data );
        if ( data.length() > 0 ) {
            // Metavante amount fields have no decimal point 
        	_transactionDollarLimit = new Float(BPWUtil.getBigDecimal(data).
        		movePointLeft(2).floatValue());
        }
        else {
            warning( "Value for the mandatory \"Transaction Dollar Limit\" field is missing." );
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Sponsor ID field of the Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parsesponsorID( String data ) throws Exception
    {
        data = MetavanteHandler.removeNumberPadding( data );
        if ( data.length() > 0 ) {
            _sponsorID = data;
        }
        else {
            warning( "Value for the mandatory \"Sponsor ID\" field is missing." );
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Parses Consumer Billing Plan field of the Consumer Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseConsumerBillingPlan( String data ) throws Exception
    {
        boolean validValue = false;
        data = data.trim();
        for (int i=0; i < CONSUMER_BILLING_PLAN_VALUES.length ; i++) {
            if ( data.equalsIgnoreCase(CONSUMER_BILLING_PLAN_VALUES[i]) ) {
                validValue = true;
                break;
            }
        }
        if ( !validValue ) {
            warning( "Value for the mandatory \"Consumer Billing Plan\" field " +
                     "doesn't match any of the pre-defined values. data passed: [" + data + "]");
        }
        _consumerBillingPlan = data;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Overrides java.lang.Object.toString(). 
    ///////////////////////////////////////////////////////////////////////////
    public String toString()
    {
        String LINE_SEP = System.getProperty( "line.separator" );
        return "_consumerID = " + (_consumerID == null ? "null" : "\"" + _consumerID + "\"" ) + LINE_SEP +
        "_consumerFirstName = " + (_consumerFirstName == null ? "null" : "\"" + _consumerFirstName + "\"" ) + LINE_SEP +
        "_consumerMiddleInitial = " + (_consumerMiddleInitial == null ? "null" : "\"" + _consumerMiddleInitial + "\"" ) + LINE_SEP +
        "_consumerLastName = " + (_consumerLastName == null ? "null" : "\"" + _consumerLastName + "\"" ) + LINE_SEP +
        "_consumerSuffix = " + (_consumerSuffix == null ? "null" : "\"" + _consumerSuffix + "\"" ) + LINE_SEP +
        "_secondaryConsumerFirstName = " + (_secondaryConsumerFirstName == null ? "null" : "\"" + _secondaryConsumerFirstName + "\"" ) + LINE_SEP +
        "_secondaryConsumerMiddleInitial = " + (_secondaryConsumerMiddleInitial == null ? "null" : "\"" + _secondaryConsumerMiddleInitial + "\"" ) + LINE_SEP +
        "_secondaryConsumerLastName = " + (_secondaryConsumerLastName == null ? "null" : "\"" + _secondaryConsumerLastName + "\"" ) + LINE_SEP +
        "_secondaryConsumerSuffix = " + (_secondaryConsumerSuffix == null ? "null" : "\"" + _secondaryConsumerSuffix + "\"" ) + LINE_SEP +
        "_addressLine1 = " + (_addressLine1 == null ? "null" : "\"" + _addressLine1 + "\"" ) + LINE_SEP +
        "_addressLine2 = " + (_addressLine2 == null ? "null" : "\"" + _addressLine2 + "\"" ) + LINE_SEP +
        "_city = " + (_city == null ? "null" : "\"" + _city + "\"" ) + LINE_SEP +
        "_state = " + (_state == null ? "null" : "\"" + _state + "\"" ) + LINE_SEP +
        "_zipCode = " + (_zipCode == null ? "null" : "\"" + _zipCode + "\"" ) + LINE_SEP +
        "_country = " + (_country == null ? "null" : "\"" + _country + "\"" ) + LINE_SEP +
        "_consumerStatus = " + (_consumerStatus == null ? "null" : "\"" + _consumerStatus + "\"" ) + LINE_SEP +
        "_primaryCountryCode = " + (_primaryCountryCode == null ? "null" : "\"" + _primaryCountryCode + "\"" ) + LINE_SEP +
        "_primaryPhone = " + (_primaryPhone == null ? "null" : "\"" + _primaryPhone + "\"" ) + LINE_SEP +
        "_secondaryCountryCode = " + (_secondaryCountryCode == null ? "null" : "\"" + _secondaryCountryCode + "\"" ) + LINE_SEP +
        "_secondaryPhone = " + (_secondaryPhone == null ? "null" : "\"" + _secondaryPhone + "\"" ) + LINE_SEP +
        "_secondaryConsumerPrimaryCountryCode = " + (_secondaryConsumerPrimaryCountryCode == null ? "null" : "\"" + _secondaryConsumerPrimaryCountryCode + "\"" ) + LINE_SEP +
        "_secondaryConsumerPrimaryPhone = " + (_secondaryConsumerPrimaryPhone == null ? "null" : "\"" + _secondaryConsumerPrimaryPhone + "\"" ) + LINE_SEP +
        "_secondaryConsumerSecondaryCountryCode = " + (_secondaryConsumerSecondaryCountryCode == null ? "null" : "\"" + _secondaryConsumerSecondaryCountryCode + "\"" ) + LINE_SEP +
        "_secondaryConsumerSecondaryPhone = " + (_secondaryConsumerSecondaryPhone == null ? "null" : "\"" + _secondaryConsumerSecondaryPhone + "\"" ) + LINE_SEP +
        "_personalSecurityCode = " + (_personalSecurityCode == null ? "null" : "\"" + _personalSecurityCode + "\"" ) + LINE_SEP +
        "_transactionDollarLimit = " + _transactionDollarLimit + LINE_SEP +
        "_sponsorID = " + (_sponsorID == null ? "null" : "\"" + _sponsorID + "\"" ) + LINE_SEP +
        "_consumerBillingPlan = " + (_consumerBillingPlan == null ? "null" : "\"" + _consumerBillingPlan + "\"" ) + LINE_SEP;
    }


    ////////////////////////////////////////////////////////////////////////////
    // Populates the static cache with the contents in the database.
    ////////////////////////////////////////////////////////////////////////////
    public static void init() throws Exception
    {
        _cache = new HashMap();
    }


    ///////////////////////////////////////////////////////////////////////////
    // Look up a ConsumerInfoFile from the cache with the given ConsumerID
    ///////////////////////////////////////////////////////////////////////////
    public static ConsumerInfoFile lookup( String customerID, FFSConnectionHolder dbh )
    throws Exception
    {
        ConsumerInfoFile thisConsumerInfo;
        synchronized( _cache ) {
            thisConsumerInfo = (ConsumerInfoFile)_cache.get( customerID );
        }
        if (thisConsumerInfo == null) {
            CustomerInfo custinfo = Customer.getCustomerByID(customerID, dbh);
            thisConsumerInfo = new ConsumerInfoFile();
            thisConsumerInfo._customerID = custinfo.customerID;
            thisConsumerInfo._consumerFirstName = custinfo.firstName;
            thisConsumerInfo._consumerMiddleInitial = custinfo.initial;
            thisConsumerInfo._consumerLastName = custinfo.lastName;
            thisConsumerInfo._consumerSuffix = custinfo.suffix;
            thisConsumerInfo._secondaryConsumerFirstName = custinfo.jointFirstName;
            thisConsumerInfo._secondaryConsumerMiddleInitial = custinfo.jointInitial;
            thisConsumerInfo._secondaryConsumerLastName = custinfo.jointLastName;
            thisConsumerInfo._secondaryConsumerSuffix = custinfo.jointSuffix;
            thisConsumerInfo._addressLine1 = custinfo.addressLine1;
            thisConsumerInfo._addressLine2 = custinfo.addressLine2;
            thisConsumerInfo._city = custinfo.city;
            thisConsumerInfo._state = custinfo.state;
            thisConsumerInfo._zipCode = custinfo.zipcode;
            thisConsumerInfo._country = custinfo.country;
            thisConsumerInfo._consumerStatus = custinfo.status;
            thisConsumerInfo._primaryCountryCode = custinfo.countryCode1;
            thisConsumerInfo._primaryPhone = custinfo.phone1;
            thisConsumerInfo._secondaryCountryCode = custinfo.countryCode2;
            thisConsumerInfo._secondaryPhone = custinfo.phone2;
            thisConsumerInfo._secondaryConsumerPrimaryCountryCode = custinfo.jointCountryCode1;
            thisConsumerInfo._secondaryConsumerPrimaryPhone = custinfo.jointPhone1;
            thisConsumerInfo._secondaryConsumerSecondaryCountryCode = custinfo.jointCountryCode2;
            thisConsumerInfo._secondaryConsumerSecondaryPhone = custinfo.jointPhone2;
            thisConsumerInfo._personalSecurityCode = custinfo.securityCode;
            thisConsumerInfo._transactionDollarLimit = new Float(Float.parseFloat(custinfo.limit));
            thisConsumerInfo._sponsorID = custinfo.sponsorID;
            thisConsumerInfo._consumerBillingPlan = custinfo.billingPlan;
            synchronized( _cache ) {
                _cache.put( customerID, thisConsumerInfo );
            }
        }
        return thisConsumerInfo;
    }
}
