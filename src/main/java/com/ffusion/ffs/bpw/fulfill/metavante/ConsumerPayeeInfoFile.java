// Copyright (c) 2001 Financial Fusion, Inc.  All Rights Reserved.

package com.ffusion.ffs.bpw.fulfill.metavante;

import java.io.BufferedReader;
import java.util.ArrayList;

import com.ffusion.ffs.bpw.db.CustPayee;
import com.ffusion.ffs.bpw.db.CustPayeeRoute;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.db.Payee;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.PayeeInfo;
import com.ffusion.ffs.bpw.master.CommonProcessor;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.db.FFSResultSet;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSUtil;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

///////////////////////////////////////////////////////////////////////////////
// An instace of this class contains data retrieved from a Metavante Consumer
// Payee Information File. Format of this response file is described in
// Appendix B2 of the Metavante Bill Payment Technical Implementation Guide.
///////////////////////////////////////////////////////////////////////////////
public class ConsumerPayeeInfoFile implements DBConsts
{
    // Variables for the values of the fields retrieved from a file record.
    public String _consumerID;
    public Integer _consumerPayeeReferenceNumber;
    public String _internalPayeeID;
    public String _consumerPayeeAccountNumber;
    public String _lastPaidDate;
    public String _consumerPayeeStatus;
    public String _submitDate;

    // Valid values for the alphanumeric pre-defined fields.
    private static final String CONSUMER_PAYEE_STATUS[] = {"ACTIVE", "PENDING", "CLOSED"};

    // Constants for line type indicators.
    private static final char HEADER_LINE_INDICATOR = '5';
    private static final char DETAIL_LINE_INDICATOR = '6';
    private static final char TRAILER_LINE_INDICATOR = '8';

    // Constant for the file type represented by this class.
    private static final String FILE_TYPE = "CONS-PAYEE-AD";

    // SQL statements constants.

    private static final String SQL_FIND_CUSTOMER_PAYEE =
        "SELECT PayeeID FROM BPW_CustomerPayee WHERE CustomerID=? AND PayeeListID=?";

    private static final String SQL_FIND_CUSTOMER_PAYEE_2 =
        "SELECT PayeeListID FROM BPW_CustomerPayee WHERE CustomerID=? AND PayeeID=?";


    private static final String SQL_UPDATE_CUSTOMER_PAYEE =
        "UPDATE BPW_CustomerPayee SET PayeeID=?, PayAcct=?, Status=? "
    + "WHERE CustomerID=? AND PayeeListID=?";

    private static final String SQL_UPDATE_REC_PAYMENT =
        "UPDATE BPW_RecPmtInstruction SET PayeeID=?, PayAcct=? "
    + "WHERE CustomerID=? AND PayeeListID=?";

    private static final String SQL_UPDATE_PAYMENT =
        "UPDATE BPW_PmtInstruction SET PayeeID=?, PayAcct=? "
    + "WHERE CustomerID=? AND PayeeListID=?";

    private static final String SQL_UPDATE_CUSTOMER_PAYEE_2 =
        "UPDATE BPW_CustomerPayee SET PayeeListID=?, PayAcct=?, Status=? "
    + "WHERE CustomerID=? AND PayeeListID=?";

    private static final String SQL_UPDATE_REC_PAYMENT_2 =
        "UPDATE BPW_RecPmtInstruction SET PayeeListID=?, PayAcct=? "
    + "WHERE CustomerID=? AND PayeeListID=?";

    private static final String SQL_UPDATE_PAYMENT_2 =
        "UPDATE BPW_PmtInstruction SET PayeeListID=?, PayAcct=? "
    + "WHERE CustomerID=? AND PayeeListID=?";


    private static final String SQL_ADD_CUSTOMER_PAYEE =
        "INSERT INTO BPW_CustomerPayee( PayeeID, PayeeListID, CustomerID, "
      + "PayAcct, Status, SubmitDate) VALUES(?,?,?,?,?,?)";


    ///////////////////////////////////////////////////////////////////////////
    // Populates the static cache with the contents in the database.
    ///////////////////////////////////////////////////////////////////////////
    public static void init() throws Exception {}


    ///////////////////////////////////////////////////////////////////////////
    // Store the information of the instance to the static cache and the
    // database with the given connection.
    ///////////////////////////////////////////////////////////////////////////
    private void storeToDB( FFSConnectionHolder dbh ) throws Exception
    {
    String customerID = getCustomerID(dbh);
    if( customerID == null ) return; // CustomerID is mandatory

    String payeeID = getPayee( dbh );
    if( payeeID == null ) return; // PayeeID is mandatory

    String status = getStatus();
    if( status == null ) return; // PayeeListID is mandatory

    Integer payeeListID = getPayeeListID();
    if( payeeListID == null ) return; // PayeeListID is mandatory

    String payAcct = _consumerPayeeAccountNumber; // PayAcct is optional

    if( customerPayeeExists( dbh, customerID, payeeListID ) ) {
        // A record for given customer-payee already exists in the DB.
        // We will update this record.
        updateExistingCustomerPayee( dbh, customerID, payeeListID, payeeID,
                     payAcct, status );
    } else {
        Integer oldPayeeListID = findCustomerPayee( dbh, customerID,
                                payeeID );
        if( oldPayeeListID != null ) {
        // Replace payeeListID in an existing record.
        replacePayeeListID( dbh, customerID, payeeListID,
                    oldPayeeListID, payAcct, status );
        } else {
        // Create new consumer-payee record.
        createNewCustomerPayee( dbh, payeeID, payeeListID, customerID,
                    payAcct, status );
        }
    }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Lookup BPW Customer ID corresponding to given Metavante Consumer ID.
    ///////////////////////////////////////////////////////////////////////////
    private String getCustomerID(FFSConnectionHolder dbh)
    throws Exception
    {
    if( _consumerID == null ) {
        warning( "Value for the \"Consumer ID\" field is missing. Aborting " +
                 "processing of the response Consumer Payee record." );
            return null;
    }
    ConsumerCrossReferenceInfoFile ci =
            ConsumerCrossReferenceInfoFile.lookupByConsumerID( _consumerID, dbh );
    if( ci == null ) {
        warning( "No Cross Reference record can be found for ConsumerID \"" +
                 _consumerID + "\". Aborting processing of the response " +
             "Consumer Payee record." );
            return null;
    }
    if( ci._sponsorConsumerID == null ) {
        warning( "Cross Reference record for ConsumerID \"" + _consumerID +
                 "\" is missing a value for \"Sponsor Consumer ID\" field. " +
             "Aborting processing of the response Consumer Payee record." );
            return null;
    }
    return ci._sponsorConsumerID;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Lookup BPW Payee ID corresponding to given Metavante Internal Paye ID.
    ///////////////////////////////////////////////////////////////////////////
    private String getPayee( FFSConnectionHolder dbh ) throws Exception
    {
    if( _internalPayeeID == null ) {
        warning( "Value for the \"Internal Payee Id\" field is missing. " +
                  "Aborting processing of the response Consumer Payee record." );
            return null;
    }
    PayeeInfo p = Payee.findPayeeByExtendedID( _internalPayeeID, dbh );
    if( p == null || p.PayeeID == null ) {
        warning( "No BPW payee can be located with given Metavante PayeeID \"" +
             _internalPayeeID + "\". Ignoring the record." );
        return null;
    }
    return p.PayeeID;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Lookup BPW status constant corresponding to given Metavante Consumer
    // Payee Status.
    ///////////////////////////////////////////////////////////////////////////
    private String getStatus()
    {
    if( _consumerPayeeStatus == null ) {
        warning( "Value for the \"Consumer Payee Status\" field is missing. " +
                 "Aborting processing of the response Consumer Payee record." );
            return null;
    }
    if( _consumerPayeeStatus.equalsIgnoreCase( "ACTIVE" ) ) {
        return DBConsts.ACTIVE;
    } else if( _consumerPayeeStatus.equalsIgnoreCase( "PENDING" ) ) {
        return DBConsts.PENDING;
    } else if( _consumerPayeeStatus.equalsIgnoreCase( "CLOSED" ) ) {
        return DBConsts.CLOSED;
    } else {
        warning( "Value \"" + _consumerPayeeStatus + "\" for the " +
                 "\"Consumer Payee Status\" field is invalid. Aborting " +
                  "processing of the response Consumer Payee record." );
            return null;
    }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Returns BPW PayeeListID for this consumer payee record.
    ///////////////////////////////////////////////////////////////////////////
    private Integer getPayeeListID()
    {
    if( _consumerPayeeReferenceNumber == null ) {
        warning( "Value for the \"Consumer Payee Reference Number\" field is missing. " +
                 "Aborting processing of the response Consumer Payee record." );
    }
    return _consumerPayeeReferenceNumber;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Return true if there exists a BPW_CustomerPayee record with given
    // CustomerID and PayeeListID.
    ///////////////////////////////////////////////////////////////////////////
    private static boolean customerPayeeExists( FFSConnectionHolder dbh,
                            String customerID,
                        Integer payeeListID )
                        throws Exception
    {
    FFSResultSet rset = null;
    try {
        rset = DBUtil.openResultSet( dbh, SQL_FIND_CUSTOMER_PAYEE,
                         new Object[]{ customerID, payeeListID } );
        return rset.getNextRow();
    } finally {
        try { if( rset != null ) rset.close(); } catch( Exception e ) {}
    }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Return PayeeListID of a an existing BPW_CustomerPayee record with given
    // CustomerID and PayeeID. Returns null if none or if more then one such
    // records exist.
    ///////////////////////////////////////////////////////////////////////////
    private static Integer findCustomerPayee( FFSConnectionHolder dbh,
                              String customerID,
                          String payeeID )
                          throws Exception
    {
    FFSResultSet rset = null;
    try {
        rset = DBUtil.openResultSet( dbh, SQL_FIND_CUSTOMER_PAYEE_2,
                         new Object[]{ customerID, payeeID } );
        if( rset.getNextRow() ) {
        Integer payeeListID = new Integer( rset.getColumnInt( 1 ) );
        if( !rset.getNextRow() ) {
            return payeeListID;
        }
        }
        return null;
    } finally {
        try { if( rset != null ) rset.close(); } catch( Exception e ) {}
    }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Updates an existing customer-payee record and related payment and rec
    // payment records.
    ///////////////////////////////////////////////////////////////////////////
    private void updateExistingCustomerPayee( FFSConnectionHolder dbh,
                              String customerID,
                          Integer payeeListID,
                              String payeeID,
                          String payAcct,
                          String status ) throws Exception
    {
    /*
    warning( "Updating an existing payee with customerID = \"" + customerID +
          "\", payeeListID = \"" + payeeListID + "\", payeeID = \"" +
          payeeID + "\", payAcct = \"" + payAcct + "\"." );
    */

    if( DBConsts.CLOSED.equalsIgnoreCase( status ) ) {
        CommonProcessor.deletePaymentSchedulesByPayeeListID( dbh, customerID, payeeListID );
    }

    // update BPW_CustomerPayee table
    DBUtil.executeStatement( dbh, SQL_UPDATE_CUSTOMER_PAYEE,
                 new Object[]{ payeeID,
                           payAcct,
                           status,
                           customerID,
                           payeeListID } );
        // update status, do nothing on PENDING
        if (status.equals(DBConsts.ACTIVE)) {
            CustPayeeRoute.updateCustPayeeRouteStatus(customerID, payeeListID.intValue(),
                                                      MetavanteHandler.getRouteID(), DBConsts.ACTIVE, dbh);
            CustPayee.updateStatus( customerID, payeeListID.intValue(), DBConsts.ACTIVE, dbh );
        }
        else if (status.equals(DBConsts.CLOSED)) {
            CustPayeeRoute.updateCustPayeeRouteStatus(customerID, payeeListID.intValue(),
                                                      MetavanteHandler.getRouteID(), DBConsts.CLOSED, dbh);
            CustPayee.updateStatus( customerID, payeeListID.intValue(), DBConsts.CLOSED, dbh );
        }

        //  Do not change status to ACTIVE/PENDING for PmtInstruction and RecPmtInstruction
    // update BPW_RecPmtInstruction table
    DBUtil.executeStatement( dbh, SQL_UPDATE_REC_PAYMENT,
                 new Object[]{ payeeID,
                           payAcct,
                           //status,
                           customerID,
                           payeeListID } );
    // update BPW_PmtInstruction table
    DBUtil.executeStatement( dbh, SQL_UPDATE_PAYMENT,
                 new Object[]{ payeeID,
                           payAcct,
                           //status,
                           customerID,
                           payeeListID } );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Replaces PayeeList in an existing customer-payee record and related
    // payment and rec payment records.
    ///////////////////////////////////////////////////////////////////////////
    private void replacePayeeListID( FFSConnectionHolder dbh,
                              String customerID,
                          Integer newPayeeListID,
                              Integer oldPayeeListID,
                          String payAcct,
                          String status ) throws Exception
    {
    /*
    warning( "Replacing an existing payee with customerID = \"" + customerID +
          "\", newPayeeListID = \"" + newPayeeListID + "\", oldPayeeListID = \"" +
          oldPayeeListID + "\", payAcct = \"" + payAcct + "\", status = \"" +
          status + "\"." );
       */

    if( DBConsts.CLOSED.equalsIgnoreCase( status ) ) {
        CommonProcessor.deletePaymentSchedulesByPayeeListID( dbh, customerID, oldPayeeListID );
    }

    // update BPW_CustomerPayee table
    DBUtil.executeStatement( dbh, SQL_UPDATE_CUSTOMER_PAYEE_2,
                 new Object[]{ newPayeeListID,
                           payAcct,
                           status,
                           customerID,
                           oldPayeeListID } );
        // update status, do nothing on PENDING
        if (status.equals(DBConsts.ACTIVE)) {
            CustPayeeRoute.updateCustPayeeRouteStatus(customerID, newPayeeListID.intValue(),
                                                      MetavanteHandler.getRouteID(), DBConsts.ACTIVE, dbh);
            CustPayee.updateStatus( customerID, newPayeeListID.intValue(), DBConsts.ACTIVE, dbh );
        }
        else if (status.equals(DBConsts.CLOSED)) {
            CustPayeeRoute.updateCustPayeeRouteStatus(customerID, newPayeeListID.intValue(),
                                                      MetavanteHandler.getRouteID(), DBConsts.CLOSED, dbh);
            CustPayee.updateStatus( customerID, newPayeeListID.intValue(), DBConsts.CLOSED, dbh );
        }

    // update BPW_RecPmtInstruction table
    DBUtil.executeStatement( dbh, SQL_UPDATE_REC_PAYMENT_2,
                 new Object[]{ newPayeeListID,
                           payAcct,
                           //status,
                           customerID,
                           oldPayeeListID } );
    // update BPW_PmtInstruction table
    DBUtil.executeStatement( dbh, SQL_UPDATE_PAYMENT_2,
                 new Object[]{ newPayeeListID,
                           payAcct,
                           //status,
                           customerID,
                           oldPayeeListID } );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Creates new customer-payee record in BPW_CustomerPayee table.
    ///////////////////////////////////////////////////////////////////////////
    private void createNewCustomerPayee( FFSConnectionHolder dbh,
                     String payeeID,
                     Integer payeeListID,
                     String customerID,
                     String payAcct,
                     String status ) throws Exception
    {
    /*
    warning( "Creating new payee with payeeID = \"" + payeeID +
          "\", payeeListID = \"" + payeeListID + "\", customerID = \"" +
          customerID + "\", payAcct = \"" + payAcct + "\", status = \"" +
          status + "\"." );
        */

    DBUtil.executeStatement( dbh, SQL_ADD_CUSTOMER_PAYEE,
                 new Object[]{ payeeID,
                           payeeListID,
                           customerID,
                           payAcct,
                           status,
                           FFSUtil.getDateString()} );
        if (status.equals(ACTIVE)) {
            CustPayeeRoute.insertCustPayeeRoute( customerID, payeeListID.intValue(),
                                              MetavanteHandler.getRouteID(), dbh);
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Stores the info in the array of ConsumerPayeeInfoFile objects into the
    // database.
    ///////////////////////////////////////////////////////////////////////////
    public static void storeToDB( ConsumerPayeeInfoFile[] infoFiles,
                      FFSConnectionHolder dbh )
                  throws Exception
    {
    for( int i = 0; i < infoFiles.length; i++ ) {
        infoFiles[i].storeToDB( dbh );
    }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses given Consumer Payee Information File and returns an array of
    // ConsumerPayeeInfoFile objects holding its contents.
    ///////////////////////////////////////////////////////////////////////////
    public static ConsumerPayeeInfoFile[] parseFile( BufferedReader in )
    throws Exception
    
    {
    	String method = "ConsumerPayeeInfoFile.parseFile";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
    parseHeader( in.readLine() ); // first line should be the header

    ArrayList l = new ArrayList();
    String line = null;
    while( (line = in.readLine() ) != null ) {
        if( line.charAt( 0 ) == DETAIL_LINE_INDICATOR ) {
        l.add( new ConsumerPayeeInfoFile( line ) ); // another detail line
        } else {
        parseTrailer( line, l.size() ); // this can only be the trailer line
        if( in.readLine() != null ) {
            warning( "Extra data is available following the trailer line!" );
        }
        break; // trailer reached, no more detail lines
        }
    }

    PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    return (ConsumerPayeeInfoFile[])l.toArray( new ConsumerPayeeInfoFile[l.size()] );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses header line of a Consumer Payee Information File.
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
    // Parses trailer line of a Consumer Payee Information File.
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
        throw new Exception( "ERROR! Consumer Payee Information File parser: " + msg );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Outputs a warning message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    private static void warning( String msg )
    {
        FFSDebug.log( "WARNING! Consumer Payee Information File parser: " + msg );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Outputs an exception and a warning message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    private static void warning( Throwable t, String msg )
    {
        FFSDebug.log( t, "WARNING! Consumer Payee Information File parser: " + msg );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Default constructor resulting in an empty ConsumerPayeeInfoFile object.
    ///////////////////////////////////////////////////////////////////////////
    public ConsumerPayeeInfoFile() {}


    ///////////////////////////////////////////////////////////////////////////
    // Constructor taking a Detail Line from a Consumer Payee Information File
    // and using it to populate its instance data members.
    ///////////////////////////////////////////////////////////////////////////
    public ConsumerPayeeInfoFile( String line ) throws Exception
    {
    	
    if( line.length() != 85 ) {
        error( "Detail line is not 85 characters long!" );
    }

    // The first character of the line is supposed to be Record Type.
    // We skip it here and start parsing from the second character.
    parseConsumerID( line.substring( 1, 23 ) );
        parseConsumerPayeeReferenceNumber( line.substring( 23, 27 ) );
        parseInternalPayeeID( line.substring( 27, 42 ) );
        parseConsumerPayeeAccountNumber( line.substring( 42, 67 ) );
        parseLastPaidDate( line.substring( 67, 75 ) );
        parseConsumerPayeeStatus( line.substring( 75, 85 ) );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Consumer ID field of the Consumer Payee Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseConsumerID( String data ) throws Exception
    {
    data = data.trim();
    if( data.length() != 0 ) {
        _consumerID = data;
    } else {
        warning( "Value for the mandatory \"Consumer ID\" field is missing." );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Consumer-Payee Reference Number field of the Consumer Payee
    // Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseConsumerPayeeReferenceNumber( String data ) throws Exception
    {
    data = MetavanteHandler.removeNumberPadding( data );
    if( data.length() > 0 ) {
        _consumerPayeeReferenceNumber = new Integer( data );
    } else {
        warning( "Value for the mandatory \"Consumer-Payee Reference Number\" field is missing." );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Internal Payee ID field of the Consumer Payee Information File.
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
    // Parses Consumer Payee Account Number field of the Consumer Payee
    // Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseConsumerPayeeAccountNumber( String data ) throws Exception
    {
    data = data.trim();
    if( data.length() != 0 ) {
        _consumerPayeeAccountNumber = data;
    } else {
        warning( "Value for the mandatory \"Consumer Payee Account Number\" field is missing." );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Last Paid Date field of the Consumer Payee Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseLastPaidDate( String data ) throws Exception
    {
        int fieldLength = data.length();
    data = data.trim();
    if( (data.length() == fieldLength) && ( data.length() != 0 ) ) {
            _lastPaidDate = data;
        } else if(data.length() != fieldLength) {
            warning( "Dates are required to be 8 characters long!" );
        } else {
        warning( "Value for the mandatory \"Last Paid Date\" field is missing." );
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Parses Consumer Payee Status field of the Consumer Payee Information File.
    ///////////////////////////////////////////////////////////////////////////
    private void parseConsumerPayeeStatus( String data ) throws Exception
    {
        boolean validValue = false;
        data = data.trim();
        for(int i = 0; i < CONSUMER_PAYEE_STATUS.length; ++i) {
            if( data.equals(CONSUMER_PAYEE_STATUS[i]) ) {
                validValue = true;
        break;
            }
        }

        if( !validValue ) {
        warning( "Value for the mandatory \"Consumer Payee Status\" field " +
                 "doesn't match any of the pre-defined values." );
        }
    _consumerPayeeStatus = data;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Overrides java.lang.Object.toString().
    ///////////////////////////////////////////////////////////////////////////
    public String toString()
    {
    String LINE_SEP = System.getProperty( "line.separator" );
    return "_consumerID = " + (_consumerID == null ? "null" : "\"" + _consumerID + "\"" ) + LINE_SEP +
               "_consumerPayeeReferenceNumber = " + _consumerPayeeReferenceNumber + LINE_SEP +
               "_internalPayeeID = " + (_internalPayeeID == null ? "null" : "\"" + _internalPayeeID + "\"" ) + LINE_SEP +
               "_consumerPayeeAccountNumber = " + (_consumerPayeeAccountNumber == null ? "null" : "\"" + _consumerPayeeAccountNumber + "\"" ) + LINE_SEP +
               "_lastPaidDate = " + (_lastPaidDate == null ? "null" : "\"" + _lastPaidDate + "\"" ) + LINE_SEP +
               "_consumerPayeeStatus = " + (_consumerPayeeStatus == null ? "null" : "\"" + _consumerPayeeStatus + "\"" ) + LINE_SEP;
    }
}
