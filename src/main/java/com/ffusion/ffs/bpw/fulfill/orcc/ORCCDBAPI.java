// Copyright (c) 2001 Financial Fusion, Inc.  All Rights Reserved.

package com.ffusion.ffs.bpw.fulfill.orcc;


import java.util.ArrayList;

import com.ffusion.ffs.bpw.db.CustPayee;
import com.ffusion.ffs.bpw.db.CustPayeeRoute;
import com.ffusion.ffs.bpw.db.Customer;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.db.Payee;
import com.ffusion.ffs.bpw.interfaces.BPWException;
import com.ffusion.ffs.bpw.interfaces.CustPayeeRslt;
import com.ffusion.ffs.bpw.interfaces.CustomerInfo;
import com.ffusion.ffs.bpw.interfaces.CustomerPayeeInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.FulfillmentInfo;
import com.ffusion.ffs.bpw.interfaces.PayeeInfo;
import com.ffusion.ffs.bpw.interfaces.PmtInfo;
import com.ffusion.ffs.bpw.interfaces.PmtTrnRslt;
import com.ffusion.ffs.bpw.master.CommonProcessor;
import com.ffusion.ffs.bpw.master.PayeeProcessor;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.db.FFSResultSet;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSUtil;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.OFX151.TypePayeeV1Aggregate;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeDPRRQRecord;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFCustInfo;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFLinkInfo;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ORCC_1999.TypeMLFMerchInfo;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

///////////////////////////////////////////////////////////////////////////////
// Database API of the ORCC connector.
///////////////////////////////////////////////////////////////////////////////
public class ORCCDBAPI implements DBConsts
{
    // SQL statements constants.
    private static final String SQL_UPDATE_ORCC_CUSTOMER_PAYEE =
         "UPDATE BPW_CustomerPayee "
         + "SET LinkID=?, LinkGoDate =?, Status=? "
         + "WHERE CustomerID=? AND PayeeListID=?";

    private static String SQL_FIND_CUSTPAYEE_BY_STATUS =
        "SELECT BPW_CustomerPayee.CustomerID, BPW_CustomerPayee.PayeeListID, "
        + "BPW_CustomerPayee.PayeeID "
        + "FROM BPW_CustomerPayee "
        + "WHERE BPW_CustomerPayee.Status =?";

    private static String SQL_FIND_CUSTPAYEE_BY_STATUS_BY_PAYEE_STATUS =
        "SELECT BPW_CustomerPayee.CustomerID, BPW_CustomerPayee.PayeeListID, "
        + "BPW_Payee.PayeeID "
        + "FROM BPW_CustomerPayee, BPW_Payee "
        + "WHERE BPW_CustomerPayee.PayeeID = BPW_Payee.PayeeID "
        + "AND BPW_CustomerPayee.Status =? "
        + "AND BPW_Payee.Status = ? ";

    private static final String SQL_SELECT_DUP_CUSTPAYEE =
        "SELECT A.CustomerID , A.PayeeListID, A.PayeeID  "
        +"FROM BPW_CustomerPayee A, BPW_CustomerPayee B "
        + "WHERE A.CustomerID = B.CustomerID "
        + "AND A.PayeeID=? AND B.PayeeID=? ";

    private static final String SQL_DELETE_FROM_CUST_PAYEE =
            "DELETE FROM BPW_CustomerPayee "
        + "WHERE CustomerID=? AND PayeeID=?";

    private static final String SQL_DELETE_FROM_ORCCLINKCROSSREF =
            "DELETE FROM " + BPW_ORCCLinkCrossReference
        + " WHERE CustomerID=? AND PayeeID=?";

    private static final String SQL_SELECT_CUSTPAYEE_STATUS_BY_STATUS = "SELECT "
            + "PayeeID, PayeeListID, CustomerID, PayAcct, Status, "
        + "ErrCode, ErrMsg, ExtdInfo, LinkID, LinkGoDate, Submitdate "
        + "FROM BPW_CustomerPayee "
        + "WHERE Status=?";

    // SQL statements constants.
    private static final String  SQL_GET_ORCC_PAYEE_MASK =
           "SELECT AcctMinLength, "
             + " AcctMaxLength, AcctMask1, AcctMask2, AcctMask3, "
             + " AcctMask4, AcctMask5 "
                     + "FROM " + BPW_ORCC_PAYEE_MASK + " WHERE PayeeID = ?";

    private final static String SQL_STORE_PAYEE_MASK =
         "INSERT INTO " + BPW_ORCC_PAYEE_MASK + "( PayeeID, AcctMinLength, AcctMaxLength, AcctMask1, "
             + "AcctMask2, AcctMask3, AcctMask4, AcctMask5) "
                         + "VALUES(?,?,?,?, ?,?,?,? )";

    private static final String SQL_STORE_ORCC_PAYEE =
         "INSERT INTO BPW_Payee( "
         + "PayeeID,    ExtdPayeeID,    PayeeType,  RouteID, "
         + "Status,     PayeeName,  ContactName,    PayeeLevelType, "
         + "Nickname,   Extension,  Addr1,      City, "
         + "State,  Zipcode,    DaysToPay,      Submitdate) "
         + "VALUES( "
         + "?,?,?,?,    ?,?,?,?,    ?,?,?,?,    ?,?,?,?)";

    private static final String SQL_DELETE_ORCC_PAYEE_MASK =
        "DELETE FROM " + BPW_ORCC_PAYEE_MASK + " WHERE PayeeID=?";

//    private static final String SQL_FIND_CUSTOMER_BY_CONSUMER_ID =
//      "SELECT ConsumerStatus FROM BPW_Customer WHERE CustomerID = ?";

    private static final String SQL_UPDATE_ORCC_PAYEE_MASK =
         "UPDATE " + BPW_ORCC_PAYEE_MASK + " SET AcctMinLength=?, AcctMaxLength=?, "
             +"AcctMask1=?, AcctMask2=?, AcctMask3=?, AcctMask4=?, AcctMask5 =? "
                     + " WHERE PayeeID = ?";

    private static final String SQL_UPDATE_PAYEEINFO_BY_EXTDPAYEEID =
         "UPDATE BPW_Payee SET Status=?, PayeeName=?, Nickname=?, ContactName=?, "
     + "Phone =?, Extension=?, Addr1=?, City=?, State=?, Zipcode=?, DaysToPay=?"
         + " WHERE ExtdPayeeID = ?";

    private static final String  SQL_GET_PAYEENAME_EXTDPAYEEID_BY_PAYEEID =
        "SELECT PayeeName, ExtdPayeeID FROM BPW_Payee WHERE PayeeID = ?";

    private static final String SQL_STORE_REMOTELINKID =
         "INSERT INTO " + BPW_ORCCLinkCrossReference + "( ID, RemoteLinkID, "
        +  " PayeeID, CustomerID ) values( ?, ?, ?, ? )";

    private static final String SQL_GET_CUSTOMERID_BY_ORCCCUSTOMERID =
        "SELECT CustomerID FROM " + BPW_ORCCCustomerCrossReference
                  + " WHERE ORCCCustomerID = ? ";

    private static final String SQL_GET_ORCCCUSTOMERID_BY_CUSTOMERID =
            "SELECT ORCCCustomerID, ORCCAcctID "
            + "FROM " + BPW_ORCCCustomerCrossReference
            + " WHERE CustomerID = ? ";

    private static final String SQL_STORE_CUSTOMERID_TO_CUSTOMER_CROSSREF =
             "INSERT INTO " + BPW_ORCCCustomerCrossReference
             + " ( ORCCCustomerID, ORCCAcctID, CustomerID ) "
             + " VALUES( ?, ?, ? )";

    private static final String SQL_DELETE_CUSTOMERID_FROM_CUSTOMER_CROSSREF =
         "DELETE FROM " + BPW_ORCCCustomerCrossReference + " WHERE ORCCCustomerID = ?";

    private static final String SQL_GET_ORCC_CUSTOMERPAYEEINFO =
         "SELECT LastName, FirstName "
         + "FROM BPW_Customer WHERE CustomerID = ?";

    private static final String SQL_GET_CUSTOMERPAYEEINFO_BY_REMOTELINKID =
         "SELECT C.PayeeID, C.CustomerID, C.PayeeListID "
            + "FROM " + BPW_ORCCLinkCrossReference + " O, BPW_CustomerPayee C "
            + "WHERE O.RemoteLinkID = ? "
            + "AND O.PayeeID = C.PayeeID "
            + "AND O.CustomerID=C.CustomerID";

    private static final String SQL_GET_CUSTOMER_NAMES_BY_ID =
            "SELECT LastName, FirstName "
         + "FROM BPW_Customer WHERE CustomerID = ?";

    private static final String SQL_SELECT_ORCCLINKCROSSREF_BY_CUSTID_PAYEEID =
         "SELECT ID, RemoteLinkID, PayeeID, CustomerID "
     + "FROM " + BPW_ORCCLinkCrossReference
     + " WHERE CustomerID = ? and PayeeID = ? ";

    private static final String SQL_GET_EXTDPAYEEID_BY_PAYEEID =
          "SELECT ExtdPayeeID FROM BPW_Payee WHERE PayeeID = ?";


    private static final String SQL_GET_PMTTRNRSLT =
          "SELECT CustomerID, ExtdPmtInfo, LogID "
         + "FROM BPW_PmtInstruction WHERE SrvrTID = ?";

    private static final String SQL_UPDATE_PAYMENT_STATUS =
          "UPDATE BPW_PmtInstruction SET Status=?,  DateCreate = ?  "
              + "WHERE SrvrTID=?";

     private static final String SQL_UPDATE_PAYMENT_STATUS2 =
           "UPDATE BPW_PmtInstruction "
       + "SET Status=?, ExtdPmtInfo=?, DateCreate = ?  "
       + "WHERE SrvrTID=?";

    private static final String  SQL_UPDATE_EXTDPAYEEID_BY_PAYEEID =
           "UPDATE BPW_Payee SET ExtdPayeeID=? "
         + " WHERE PayeeID = ?";

    private static final String SQL_UPDATE_CUSTOMERPAYEE_STATUS=
          "UPDATE BPW_CustomerPayee SET Status=? "
         + " WHERE CustomerID = ? and PayeeListID=? ";

    private static final String UPDATE_PAYEE_STATUS_BY_EXTDPAYEEID =
          "UPDATE BPW_Payee SET Status=?  WHERE ExtdPayeeID = ? ";

    private static final String  SQL_UPDATE_CUSTOMER_STATUS=
          "UPDATE BPW_Customer SET ConsumerStatus=? "
         + " WHERE ConsumerStatus = ? ";

    private static final String  SQL_UPDATE_CUSTOMERROUTE_STATUS=
          "UPDATE BPW_CustomerRoute SET Status=? "
         + " WHERE Status = ? and RouteID=?";

//    private static final String SQL_UPDATE_CUSTOMER__PAYEE_STATUS2 =
//          "UPDATE BPW_CustomerPayee SET Status=? "
//       + " WHERE Status = ? ";

//    private static final String SQL_GET_PAYMENT_IDS =
//        "SELECT SrvrTID FROM BPW_PmtInstruction "
//        + "WHERE CustomerID=? ";

//    private static final String SQL_GET_REC_PAYMENT_IDS =
//        "SELECT RecSrvrTID FROM " + BPW_RecPmtInstruction
//        + " WHERE CustomerID=? ";

//    private static final String SQL_UPDATE_PAYMENT =
//      "UPDATE BPW_PmtInstruction  "
//  + " SET PayeeID=?, PayeeListID=?, StartDate=?, Status=? "
//  + "  WHERE SrvrTID=?";

    private static final String   SQL_UPDATE_PAYEE_STATUS =
        "UPDATE BPW_Payee SET Status=? WHERE status = ? ";

    private static final String SQL_DELETE_XREF_BY_PAYEEID =
        "DELETE FROM " + BPW_ORCCLinkCrossReference + " WHERE PayeeID = ?";

    private static final String SQL_DELETE_XREF_BY_CUSTID =
        "DELETE FROM " + BPW_ORCCLinkCrossReference + " WHERE CustomerID = ?";

//    private static final String SQL_DISABLE_CUSTOMER_PAYEES_BY_PAYEEID =
//      "UPDATE BPW_CustomerPayee SET Status = 'CLOSED' WHERE PayeeID = ?";

//    private static final String SQL_GET_PAYMENT_IDS_BY_PAYEEID =
//        "SELECT SrvrTID FROM BPW_PmtInstruction WHERE PayeeID=? ";

//    private static final String SQL_GET_REC_PAYMENT_IDS_BY_PAYEEID =
//        "SELECT RecSrvrTID FROM " + BPW_RecPmtInstruction + " WHERE PayeeID=? ";

//    private static final String SQL_GET_ACCTID =
//         "SELECT IndexValue FROM " + BPW_InternalIndices + " WHERE IndexName = 'PayeeSetID'";

//    private static final String SQL_UPDATE_ALL_LINK_PAYEEID_BY_PAYEEID =
//      "UPDATE BPW_CustomerPayee SET PayeeID=? WHERE PayeeID=?";

//    private static final String SQL_UPDATE_ALL_PMT_PAYEEID_BY_PAYEEID =
//      "UPDATE BPW_PmtInstruction SET PayeeID=?, PayeeListID=? "
//  + "WHERE PayeeID=?";

//    private static final String SQL_UPDATE_ALL_REC_PMT_PAYEEID_BY_PAYEEID =
//      "UPDATE " + BPW_RecPmtInstruction + " SET PayeeID=?, PayeeListID=? "
//  + "WHERE PayeeID=?";

    private static final String SQL_SELECT_PAYEE_BY_EXTDPAYEEID = "SELECT "
            + "PayeeID, ExtdPayeeID, PayeeType, PayeeName, Encoding, "
        + "Addr1, Addr2, Addr3, City, State, "
        + "Zipcode, Country, Phone, Extension, RouteID, "
        + "LinkPayeeID, Status, DisbursementType, PayeeLevelType, Nickname, "
        + "ContactName, DaysToPay, Submitdate "
        + "FROM BPW_Payee "
        + "WHERE ExtdPayeeID= ?";

    private static final String SQL_UPDATE_PMTINSTR_PAYEE_INFO_BY_CUSTID_PAYEEID =
            "UPDATE BPW_PmtInstruction SET "
        + "PayeeID=?, PayeeListID=?, PayAcct=? "
        + "WHERE CustomerID=? AND PayeeID=?";

    private static final String SQL_UPDATE_RECPMTINSTR_PAYEE_INFO_BY_CUSTID_PAYEEID =
            "UPDATE " + BPW_RecPmtInstruction + " SET "
        + "PayeeID=?, PayeeListID=?, PayAcct=? "
        + "WHERE CustomerID=? AND PayeeID=?";
    private static final String SQL_SELECT_PMTINSTR_BY_PAYEEID = "SELECT "
            + "DISTINCT( CustomerID ) "
        + "FROM BPW_PmtInstruction "
        + "WHERE PayeeID=? ";

    private static final String SQL_SELECT_RECPMTINSTR_BY_PAYEEID = "SELECT "
            + "DISTINCT( CustomerID ) "
        + "FROM " + BPW_RecPmtInstruction
        + " WHERE PayeeID=? ";

    private static final String SQL_SELECT_CUSTPAYEE_BY_CUSTID_PAYEEID = "SELECT "
            + "PayeeID, PayeeListID, CustomerID, PayAcct, NameOnAcct, "
        + "Status, ErrCode, ErrMsg, ExtdInfo, LinkID, "
        + "LinkGoDate, Submitdate "
        + "FROM BPW_CustomerPayee "
        + "WHERE CustomerID=? AND PayeeID=? ";

    private static final String SQL_UPDATE_ORCCCUSTPAYEE_PAYEEID =
            "UPDATE " + BPW_ORCCLinkCrossReference
        + " SET PayeeID=? WHERE PayeeID=? ";

    private static final String SQL_UPDATE_CUSTPAYEE_PAYEEID =
            "UPDATE BPW_CustomerPayee "
        + "SET PayeeID=? WHERE PayeeID=? ";

    private static final String SQL_CHECK_EXISTING_SRVRTID =
            "SELECT COUNT(*) FROM BPW_PmtInstruction WHERE SrvrTID=?";

    // Cross reference between ORCCCustomer ID and BPWCustomer ID
    static class ORCCCustCrossRef {
    int orccCustID;
    int orccAcctID;
    }

    static class ORCCLinkCrossRef{
    int ID;
    String  remoteLinkID;
    String  payeeID;
    String  customerID;
    }

    private static FulfillmentInfo  _fulfill=null;
    static void setFulfillment( FulfillmentInfo fulfill ) {_fulfill=fulfill;}


    ///////////////////////////////////////////////////////////////////////////
    // Reads ORCCPayeeMaskFields from the BPW_ORCCPayeeMaskFields.
    ///////////////////////////////////////////////////////////////////////////
    public static ORCCPayeeMaskFields getORCCPayeeMaskFields( PayeeInfo info,
                               FFSConnectionHolder dbh )
                                 throws Exception
    {
        String method = "ORCCDBAPI.getORCCPayeeMaskFields";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.getORCCPayeeMaskFields" + info.PayeeID, FFSConst.PRINT_DEV );

        Object[] args =
        {
            info.PayeeID
        };
    ORCCPayeeMaskFields fields = null;

    FFSResultSet rset = null;
        try
        {
            rset = DBUtil.openResultSet(dbh, SQL_GET_ORCC_PAYEE_MASK, args);
            if( rset.getNextRow() ) {
        fields = new ORCCPayeeMaskFields();
                fields.payeeID       = info.PayeeID;
            fields.acctMinLength = rset.getColumnInt(1);
            fields.acctMaxLength = rset.getColumnInt(2);
            fields.acctMask1 = rset.getColumnString(3);
            fields.acctMask2 = rset.getColumnString(4);
            fields.acctMask3 = rset.getColumnString(5);
            fields.acctMask4 = rset.getColumnString(6);
            fields.acctMask5 = rset.getColumnString(7);

            }//--if
        } catch( Exception exc ) {
            String trace = FFSDebug.stackTrace( exc );
            ORCCUtil.log( "*** ORCCDBAPI.getORCCPayeeMaskFields failed:" + trace );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new BPWException( exc.toString() );
    } finally {
        try{
        if( rset!=null ) rset.close();
        } catch (Exception e ) {
        	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        }
        }//try

        ORCCUtil.log( "ORCCDBAPI.getORCCPayeeMaskFields , payeeID=" +
                      ((fields==null) ? "null" : info.PayeeID), FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return fields;
    }//--getORCCPayeeMaskFields( payeeInfo, dbh)


    ///////////////////////////////////////////////////////////////////////////
    // store ORCCPayeeMaskFields into the BPW_ORCCPayeeMaskFields.
    ///////////////////////////////////////////////////////////////////////////
    public static boolean storeORCCPayeeMaskToDB( ORCCPayeeMaskFields fields,
                           FFSConnectionHolder dbh )
    throws BPWException
    {
    	String method = "ORCCDBAPI.storeORCCPayeeMaskToDB";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);

        ORCCUtil.log( "ORCCDBAPI.storeORCCPayeeMaskToDB start, payeeID="
             + fields.payeeID, FFSConst.PRINT_DEV );
        Object minLObj = null;
    Object maxLObj = null;
    if( fields.acctMinLength != -1 ) minLObj = new Integer(fields.acctMinLength);
    if( fields.acctMaxLength != -1 ) minLObj = new Integer(fields.acctMaxLength);
        Object[] args =
        {
        fields.payeeID,
        minLObj,
        maxLObj,
            fields.acctMask1,
            fields.acctMask2,
            fields.acctMask3,
            fields.acctMask4,
            fields.acctMask5
        };

        try
        {
            int rows = DBUtil.executeStatement(dbh, SQL_STORE_PAYEE_MASK, args);
        } catch( Exception exc ) {
            String trace = FFSDebug.stackTrace( exc );
            ORCCUtil.log( "*** ORCCDBAPI.storeORCCPayeeMaskToDB failed: " + trace );
                         exc.printStackTrace();
                         PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new BPWException( exc.toString());

        }

        ORCCUtil.log( "ORCCDBAPI.storeORCCPayeeMaskToDB  done, payeeID="
                   + fields.payeeID, FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return true;
    }

    ///////////////////////////////////////////////////////////////////////////
    // store Payees into the BPW_Payee.
    ///////////////////////////////////////////////////////////////////////////
    public static boolean storePayeeToDB( PayeeInfo info,
                          FFSConnectionHolder dbh )
    throws BPWException
    {
    	String method = "ORCCDBAPI.storePayeeToDB";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);

        ORCCUtil.log( "ORCCDBAPI.storePayeeToDB start, payeeID="
             + info.PayeeID, FFSConst.PRINT_DEV );
    if( _fulfill==null ) {
    	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        throw new BPWException( "Fulfillment system ORCC not found!" );
    }

        Object[] args =
        {
        info.PayeeID,
        info.ExtdPayeeID,
        new Integer( DBConsts.GLOBAL ),
        new Integer( _fulfill.RouteID ),
        info.Status,
        info.PayeeName,
        info.ContactName,
        info.PayeeLevelType,
        info.NickName,
        info.Extension,
        info.Addr1,
        info.City,
        info.State,
        info.Zipcode,
        new Integer( info.DaysToPay),
        FFSUtil.getDateString()
        };

        try {
            int rows = DBUtil.executeStatement(dbh, SQL_STORE_ORCC_PAYEE, args);
        } catch( Exception exc ) {
            String trace = FFSDebug.stackTrace( exc );
            ORCCUtil.log( "*** ORCCDBAPI.storePayeeToDB failed: " + trace );
            System.err.println( trace );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new BPWException( exc.toString());

        }

        // store to smart payee table
        Payee.storePayeeToSmartPayee(dbh, info.PayeeID, info.PayeeName);

        ORCCUtil.log( "ORCCDBAPI.storePayeeToDB  done, payeeID="
             + info.PayeeID, FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return true;
    }

    ///////////////////////////////////////////////////////////////////////////
    // remove ORCCPayeeMaskFields from the BPW_ORCCPayeeMaskFields.
    ///////////////////////////////////////////////////////////////////////////
    public static void removeORCCPayeeMaskFromDB( String payeeID, FFSConnectionHolder dbh )
    throws BPWException
    {
    	String method = "ORCCDBAPI.removeORCCPayeeMaskFromDB";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.removeORCCPayeeMaskFromDB  start, payeeID="
             + payeeID, FFSConst.PRINT_DEV );


        Object[] args =
        {
            payeeID
        };


        try
        {
            int rows = DBUtil.executeStatement(dbh, SQL_DELETE_ORCC_PAYEE_MASK, args);
        } catch( Exception exc ) {

            String trace = FFSDebug.stackTrace( exc );
            ORCCUtil.log( "*** ORCCDBAPI.removeORCCPayeeMaskFromDB failed:"  +  trace );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new BPWException( exc.toString() );

         }

         ORCCUtil.log( "ORCCDBAPI.removeORCCPayeeMaskFromDB done, payeeID="
                  + payeeID, FFSConst.PRINT_DEV );
         PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }



    ///////////////////////////////////////////////////////////////////////////
    // update ORCCPayeeMaskFields of the BPW_ORCCPayeeMaskFields.
    ///////////////////////////////////////////////////////////////////////////
    public static boolean updateORCCPayeeMaskFields(  ORCCPayeeMaskFields fields,
                               FFSConnectionHolder dbh )
                                 throws Exception
    {
    	String method = "ORCCDBAPI.updateORCCPayeeMaskFields";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.updateORCCPayeeMaskFields start payeeID = "
                 + fields.payeeID, FFSConst.PRINT_DEV );

        Object minLObj = null;
    Object maxLObj = null;
    if( fields.acctMinLength != -1 ) minLObj = new Integer(fields.acctMinLength);
    if( fields.acctMaxLength != -1 ) minLObj = new Integer(fields.acctMaxLength);
        Object[] args =
        {
        fields.payeeID,
        minLObj,
        maxLObj,
            fields.acctMask1,
            fields.acctMask2,
            fields.acctMask3,
            fields.acctMask4,
            fields.acctMask5
        };

        try
        {
            int rows = DBUtil.executeStatement(dbh, SQL_UPDATE_ORCC_PAYEE_MASK, args);
        } catch( Exception exc ) {

            String trace = FFSDebug.stackTrace( exc );
            ORCCUtil.log( "*** ORCCDBAPI.updateORCCPayeeMaskFields failed:" + trace );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new BPWException( exc.toString() );
        }//try

        ORCCUtil.log( "ORCCDBAPI.updateORCCPayeeMaskFields done payeeID=" +
                      ((fields==null) ? "null" : fields.payeeID), FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return true;
    }//--updateORCCPayeeMaskFields( payeeInfo, dbh)

    ///////////////////////////////////////////////////////////////////////////
    // get ORCC specific customer info of the BPW_CUSTOMER table.
    // get PayeeList from bpw_customerpayee and convert it to minfo.acctID
    ///////////////////////////////////////////////////////////////////////////
    public static boolean getORCCCustomerInfo( CustomerInfo info, TypeMLFCustInfo minfo,
                               FFSConnectionHolder dbh )
                                 throws Exception
    {
    	String method = "ORCCDBAPI.getORCCCustomerInfo";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.getORCCCustomerInfo start customerID="
                 + info.customerID, FFSConst.PRINT_DEV );
        try {
        int orccCustID = getORCCCustomerIDByCustomerID( info.customerID, dbh ).orccCustID;
        if( orccCustID >=0 ) {
        minfo.CustID = Integer.toString( orccCustID );
        minfo.AcctID = minfo.CustID;
        } else {
        minfo.CustID = Integer.toString(
            storeCustomerIDTOCROSSREF( info.customerID, dbh ) );
        minfo.AcctID = minfo.CustID;
        }
        } catch( Exception exc ) {

            String trace = FFSDebug.stackTrace( exc );
            ORCCUtil.log( "*** ORCCDBAPI.getORCCCustomerInfo failed:" + trace );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new BPWException( exc.toString() );
        }//try
        ORCCUtil.log( "ORCCDBAPI.getORCCCustomerInfo done customerID="
                 + info.customerID, FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return true;
    }//--getORCCCustomerInfo( ..., dbh)

    ///////////////////////////////////////////////////////////////////////////
    // get ORCC specific customerPayee info of the BPW_CUSTOMER table.
    // Only when customer is not new, we try to get ORCCCustomerID and
    // ORCCAcctID out from ORCCCustomerCrossReference table
    // Only when payee is not new, we try to get extPayeeID and
    // payacct out from bpw payee table
    // These two boolean variables are added to avoid access the newly
    // added uncommited records
    ///////////////////////////////////////////////////////////////////////////
    public static boolean generateORCCCustomerPayeeInfo(
                    CustomerPayeeInfo info,
                TypeMLFLinkInfo mInfo,
                boolean newCust,
                boolean newPayee,
                FFSConnectionHolder dbh ) throws Exception
    {
    	String method = "ORCCDBAPI.generateORCCCustomerPayeeInfo";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.getORCCCustomerPayeeInfo start customerID="
                 + info.CustomerID, FFSConst.PRINT_DEV );

        FFSResultSet rset = null;
    if( !newCust ) {
        Object[] args = { info.CustomerID };
        try {
        //assign ORCCCustomerID of this info.customerID to acctID
        ORCCCustCrossRef ref = getORCCCustomerIDByCustomerID( info.CustomerID, dbh );
        mInfo.AcctID = Integer.toString(ref.orccAcctID );
        if( mInfo.NameOnAcct==null) {
            rset = DBUtil.openResultSet(dbh, SQL_GET_ORCC_CUSTOMERPAYEEINFO, args);
            if( rset.getNextRow() ) {
            String nameStr = rset.getColumnString(1) + ", " + rset.getColumnString(2);
            mInfo.NameOnAcct = nameStr;
            }//--if
        }
        } catch( Exception exc ) {

        String trace = FFSDebug.stackTrace( exc );
        ORCCUtil.log( "*** ORCCDBAPI.getORCCCustomerPayeeInfo failed:" + trace );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        throw new BPWException( exc.toString() );
        } finally {
        try{
            if( rset!=null) rset.close();
        } catch( Exception e ) {
        	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        }
        }//try
    }

    if( !newPayee ) {
        Object[] args1 = { info.PayeeID };
        rset = null;
        try
        {
        rset = DBUtil.openResultSet(dbh, SQL_GET_PAYEENAME_EXTDPAYEEID_BY_PAYEEID, args1);
        if( rset.getNextRow() ) {
            // use the first 12 characters of payeename as link nickname
            String linkName = rset.getColumnString( 1 );
            mInfo.LinkNickname = (linkName.length()>12)
                        ? linkName.substring(0, 12)
                    : linkName;
            mInfo.MerchID = rset.getColumnString(2);
        }//--if
        } catch( Exception exc ) {
        String trace = FFSDebug.stackTrace( exc );
        ORCCUtil.log( "*** ORCCDBAPI.getORCCCustomerPayeeInfo failed:" + trace );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        throw new BPWException( exc.toString() );
        } finally {
        try{
            if( rset!=null) rset.close();
        } catch( Exception e ) {
        	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        }
        }//try
    }

    rset = null;
        try {
        int id = DBUtil.getNextIndex( DBConsts.ORCCLINKCROSSREFERENCE );
        mInfo.RemoteLinkID = id2RemoteLinkID( id );
            storeRemoteLinkIDToDB(  info, id, mInfo.RemoteLinkID, dbh );
    } catch( Exception e) {
            String trace = FFSDebug.stackTrace( e );
            ORCCUtil.log( "*** ORCCDBAPI.getORCCCustomerPayeeInfo failed:" + trace );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new BPWException( e.toString() );
    } finally {
        try{
        if( rset!=null) rset.close();
        } catch( Exception e ) {
        	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        }
    }

        ORCCUtil.log( "ORCCDBAPI.generateORCCCustomerPayeeInfo done customerID="
                 + info.CustomerID, FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return true;
    }//--getORCCCustomerPayeeInfo( ..., dbh)


    ///////////////////////////////////////////////////////////////////////////
    // get ORCC specific customerPayee info of the BPW_CUSTOMER table.
    // Only when customer is not new, we try to get ORCCCustomerID and
    // ORCCAcctID out from ORCCCustomerCrossReference table
    // Only when payee is not new, we try to get extPayeeID and
    // payacct out from bpw payee table
    // These two boolean variables are added to avoid access the newly
    // added uncommited records
    ///////////////////////////////////////////////////////////////////////////
    public static boolean getORCCCustomerPayeeInfo(
                    CustomerPayeeInfo info,
                TypeMLFLinkInfo mInfo,
                boolean lookupCust,
                boolean lookupPayee,
                FFSConnectionHolder dbh ) throws Exception
    {
    	String method = "ORCCDBAPI.getORCCCustomerPayeeInfo";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.getORCCCustomerPayeeInfo start customerID="
                 + info.CustomerID, FFSConst.PRINT_DEV );

        FFSResultSet rset = null;
    if( lookupCust ) {
        Object[] args = { info.CustomerID };
        try {
        //assign ORCCCustomerID of this info.customerID to acctID
        ORCCCustCrossRef ref = getORCCCustomerIDByCustomerID( info.CustomerID, dbh );
        mInfo.AcctID = Integer.toString(ref.orccAcctID );

        if( mInfo.NameOnAcct==null || mInfo.NameOnAcct.length()<=0 ) {
            rset = DBUtil.openResultSet(dbh, SQL_GET_CUSTOMER_NAMES_BY_ID, args);
            if( rset.getNextRow() ) {
            String nameStr = rset.getColumnString(1)
                    + ", "
                    + rset.getColumnString(2);
            mInfo.NameOnAcct = nameStr;
            }
        }//--if
        } catch( Exception exc ) {

        String trace = FFSDebug.stackTrace( exc );
        ORCCUtil.log( "*** ORCCDBAPI.getORCCCustomerPayeeInfo failed:" + trace );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        throw new BPWException( exc.toString() );
        } finally {
        try{
            if( rset!=null) rset.close();
        } catch( Exception e ) {
        	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        }
        }//try
    }

    if( lookupPayee ) {
        Object[] args1 = { info.PayeeID };
        rset = null;
        try {
        rset = DBUtil.openResultSet(dbh, SQL_GET_PAYEENAME_EXTDPAYEEID_BY_PAYEEID, args1);
        if( rset.getNextRow() ) {
            // use the first 12 characters of payeename as link nickname
            String linkName = rset.getColumnString( 1 );
            mInfo.LinkNickname = (linkName.length()>12)
                        ? linkName.substring(0, 12)
                    : linkName;
            mInfo.MerchID = rset.getColumnString(2);
        }//--if
        } catch( Exception exc ) {
        String trace = FFSDebug.stackTrace( exc );
        ORCCUtil.log( "*** ORCCDBAPI.getORCCCustomerPayeeInfo failed:" + trace );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        throw new BPWException( exc.toString() );
        } finally {
        try{
            if( rset!=null) rset.close();
        } catch( Exception e ) {
        	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        }
        }//try
    }

    ORCCLinkCrossRef ref = getORCCLinkCrossRef( info.CustomerID, info.PayeeID, dbh );
    if( ref==null ) {
    	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        throw new Exception ("Customer-payee link not registered with ORCC. "
                    + "CustomerID="
                + info.CustomerID
                + ";\tPayeeID="
                + info.PayeeID );
    }
    mInfo.RemoteLinkID = ref.remoteLinkID;

        ORCCUtil.log( "ORCCDBAPI.getORCCCustomerPayeeInfo done customerID="
                 + info.CustomerID, FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return true;
    }//--getORCCCustomerPayeeInfo( ..., dbh)


    ///////////////////////////////////////////////////////////////////////////
    // get remoteLinkId from BPW_ORCCLinkCrossReference
    ///////////////////////////////////////////////////////////////////////////
    private static String getRemoteLinkID(
                    String CustomerID,
                    String PayeeID,
                    FFSConnectionHolder dbh )
                    throws Exception
    {
    ORCCLinkCrossRef ref = getORCCLinkCrossRef( CustomerID, PayeeID, dbh );
        return (ref==null) ?null :ref.remoteLinkID;
    }


    ///////////////////////////////////////////////////////////////////////////
    // get remoteLinkId from BPW_ORCCLinkCrossReference
    ///////////////////////////////////////////////////////////////////////////
    private static ORCCLinkCrossRef getORCCLinkCrossRef(
                            String customerID,
                            String payeeID,
                                FFSConnectionHolder dbh )
                        throws Exception
    {
        ORCCUtil.log( "ORCCDBAPI.getRemoteLinkID start CustomerID = " + customerID
                 + " PayeeID = " + payeeID, FFSConst.PRINT_DEV );

        Object[] args =
        {
        customerID,
        payeeID
        };
    ORCCLinkCrossRef ref = null;
    FFSResultSet rset = null;
        try {
            rset = DBUtil.openResultSet(dbh,
                SQL_SELECT_ORCCLINKCROSSREF_BY_CUSTID_PAYEEID,
            args);
            if( rset.getNextRow() ) {
        ref = new ORCCLinkCrossRef();
        ref.ID = rset.getColumnInt( 1 );
        ref.remoteLinkID = rset.getColumnString(2);
        ref.payeeID = payeeID;
        ref.customerID = customerID;
            }
        } catch( Exception exc ) {

            String trace = FFSDebug.stackTrace( exc );
            ORCCUtil.log( "*** ORCCDBAPI.storeRemoteLinkIDToDB failed:" + trace );
            throw new BPWException( exc.toString() );
    } finally {
        try{
        if( rset!=null ) rset.close();
        } catch (Exception e ) {
        }
        }
        ORCCUtil.log( "ORCCDBAPI.getRemoteLinkID done CustomerID = " + customerID
                 + " PayeeID = " + payeeID, FFSConst.PRINT_DEV );
        return ref;
    }

    ///////////////////////////////////////////////////////////////////////////
    // store remoteLink ID to cross reference table BPW_ORCCLinkCrossreference.
    ///////////////////////////////////////////////////////////////////////////
    private static boolean storeRemoteLinkIDToDB(
                    CustomerPayeeInfo info,
                    int id,
                String remoteLinkID,
                FFSConnectionHolder dbh ) throws Exception
    {
        ORCCUtil.log( "ORCCDBAPI.storeRemoteLinkIDToDB start customerID="
                  + info.CustomerID, FFSConst.PRINT_DEV );

        Object[] args =
        {
        new Integer( id ),
        remoteLinkID,
        info.PayeeID,
            info.CustomerID
        };

        try
        {
            int rows = DBUtil.executeStatement(dbh, SQL_STORE_REMOTELINKID, args);
        } catch( Exception exc ) {
            String trace = FFSDebug.stackTrace( exc );
            ORCCUtil.log( "*** ORCCDBAPI.storeRemoteLinkIDToDB failed:" + trace );
            throw new BPWException( exc.toString() );
        }//try
        ORCCUtil.log( "ORCCDBAPI.storeRemoteLinkIDToDB done customerID="  + info.CustomerID);
        return true;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Translating id to remoteLinkID
    ///////////////////////////////////////////////////////////////////////////
    private static final String id2RemoteLinkID( int id )
    {
    char[] c = new char[10];

    int pos = 10-1;
    int idx;
    while( id>0 ) {
        idx = id%ORCCUtil.NUM_ALPHABETS;
        id /=ORCCUtil.NUM_ALPHABETS;
        c[pos--] = ORCCUtil.getAlphabetFromInt( idx );
    }
    for( int i=pos; i>=0; --i )c[i]=ORCCUtil.getAlphabetFromInt( 0 );

    return new String( c );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Translating remoteLinkID back to ID
    ///////////////////////////////////////////////////////////////////////////
    private static final int remoteLinkID2ID ( String rlinkID )
    throws NumberFormatException
    {
    char[] c = new char[10];
    rlinkID.getChars( 0, 10, c, 0 );

    int ret = 0;
    int temp = 1;
    int digit = -1;
    for( int i=9; i>=0; --i ){
        digit = ORCCUtil.getIntFromAlphabet( c[i] );
        if( digit>=0 ) {
        ret += digit*temp;
        } else {
        String msg = "Character "
                +c[i]
                + " is not alphabet."
                + " RemoteLinkID parsing failed!";
        ORCCUtil.log( msg );
        throw new NumberFormatException( msg );
        }

        // prevent temp from overflowing
        if( temp>= Integer.MAX_VALUE/ORCCUtil.NUM_ALPHABETS ) break;
        temp *=ORCCUtil.NUM_ALPHABETS;
    }

    return ret;
    }


    ///////////////////////////////////////////////////////////////////////////
    // store Customer ID to cross reference table BPW_ORCCCustomerCrossreference.
    // return ORCCCustomerID used by ORCCCustomer record
    ///////////////////////////////////////////////////////////////////////////
    private static int storeCustomerIDTOCROSSREF( String CustomerID, FFSConnectionHolder dbh )
                                 throws Exception
    {
        ORCCUtil.log( "ORCCDBAPI.storeCustomerIDToCRESSREF start customerID="  + CustomerID);

        int ORCCCustomerID = -1;

        try {
        ORCCCustomerID = getORCCCustomerIDByCustomerID( CustomerID, dbh ).orccCustID;
        if( ORCCCustomerID != -1 ) {
        String trace = " cannot add customerID already exists ";
        throw new Exception ( trace );
        } else {
        ORCCCustomerID = DBUtil.getNextIndex( DBConsts.ORCCCUSTOMERID  );
        Object[] args =
        {
            new Integer( ORCCCustomerID ),
            new Integer( ORCCCustomerID ),  //default ORCCAcctID = ORCCCustomerID
            CustomerID
        };

        int rows = DBUtil.executeStatement(dbh,
                    SQL_STORE_CUSTOMERID_TO_CUSTOMER_CROSSREF,
                    args);
        }
        } catch( Exception exc ) {
            String trace = FFSDebug.stackTrace( exc );
            ORCCUtil.log( "*** ORCCDBAPI.storeRemoteLinkIDToDB failed:" + trace );
            throw new BPWException( exc.toString() );
        }//try
        ORCCUtil.log( "ORCCDBAPI.storeCustomerIDToCROSSREF done customerID="
                  + CustomerID, FFSConst.PRINT_DEV );
    return ORCCCustomerID;
    }

    ///////////////////////////////////////////////////////////////////////////
    // get cusomterID with remoteLinkId from BPW_ID
    ///////////////////////////////////////////////////////////////////////////
    public static ORCCCustCrossRef getORCCCustomerIDByCustomerID(
                        String CustomerID,
                        FFSConnectionHolder dbh ) throws Exception
    {
    	String method = "ORCCDBAPI.getORCCCustomerIDByCustomerID";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.getORCCCustomerIDByCustomerID start customerID="
                 + CustomerID, FFSConst.PRINT_DEV );

        Object[] args =
        {
        CustomerID
        };

    ORCCCustCrossRef ref = new ORCCCustCrossRef();
    FFSResultSet rset = null;
        try
        {
            rset = DBUtil.openResultSet(dbh,
                SQL_GET_ORCCCUSTOMERID_BY_CUSTOMERID, args);
            if( rset.getNextRow() ) {
        ref.orccCustID  = rset.getColumnInt(1);
        ref.orccAcctID  = rset.getColumnInt( 2 );
            } else {
        ref.orccCustID  = -1;
        ref.orccAcctID  = -1;
        }
        } catch( Exception exc ) {
            String trace = FFSDebug.stackTrace( exc );
            ORCCUtil.log( "*** ORCCDBAPI.getORCCCustomerIDByCustomerID failed:" + trace );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new BPWException( exc.toString() );
        }finally{
        try{
                if( rset!=null )rset.close();
        } catch( Exception e ) {
        	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        }
    }
        ORCCUtil.log( "ORCCDBAPI.getORCCCustomerIDByCustomerID done customerID="
                  + CustomerID, FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return ref;
    }


    ///////////////////////////////////////////////////////////////////////////
    // get cusomterID with ORCCCustomerID from BPW_ORCCCUSTOMERCROSSREFERENCE
    ///////////////////////////////////////////////////////////////////////////
    public static String getCustomerIDByORCCCustomerID( int ORCCCustomerID,
                                         FFSConnectionHolder dbh )
                                 throws Exception
    {
    	String method = "ORCCDBAPI.getCustomerIDByORCCCustomerID";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
        ORCCUtil.log( "ORCCDBAPI.getCustomerIDByORCCCustomerID start ORCCcustomerID="
                   + ORCCCustomerID, FFSConst.PRINT_DEV );

        Object[] args =
        {
        new Integer(ORCCCustomerID)
        };
        String CustomerID  = null;
    FFSResultSet rset = null;
        try
        {
            rset = DBUtil.openResultSet(dbh,
                SQL_GET_CUSTOMERID_BY_ORCCCUSTOMERID, args);
            if( rset.getNextRow() ) {
        CustomerID  = rset.getColumnString(1);
            }//--if
        } catch( Exception exc ) {
            String trace = FFSDebug.stackTrace( exc );
            ORCCUtil.log( "*** ORCCDBAPI.getCustomerIDByORCCCustomerID failed:" + trace );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new BPWException( exc.toString() );
        }finally{
        try{
                if( rset!=null )rset.close();
        } catch( Exception e ) {
        	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        }
    }

        ORCCUtil.log( "ORCCDBAPI.getCustomerIDByORCCCustomerID done ORCCCustomerID="
                    + ORCCCustomerID, FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return CustomerID;
    }


    ///////////////////////////////////////////////////////////////////////////
    // get cusomterPayeeInfo with remoteLinkId from BPW_PayeeInfo
    ///////////////////////////////////////////////////////////////////////////
    public static CustomerPayeeInfo getCustomerPayeeInfoByRemoteLinkID(
                                 String remoteLinkID,
                                         FFSConnectionHolder dbh )
                                 throws Exception
    {
    	String method = "ORCCDBAPI.getCustomerPayeeInfoByRemoteLinkID";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.getCustomerPayeeInfoByRemoteLinkID started remotelinkID = "
                   + remoteLinkID, FFSConst.PRINT_DEV );
        Object[] args = { remoteLinkID };
        CustomerPayeeInfo info = null;
    FFSResultSet rset = null;

        try
        {
            rset = DBUtil.openResultSet(dbh,
                SQL_GET_CUSTOMERPAYEEINFO_BY_REMOTELINKID, args);
            if( rset.getNextRow() ) {
        info = new CustomerPayeeInfo();
        info.PayeeID = rset.getColumnString(1);
        info.CustomerID  = rset.getColumnString(2);
        info.PayeeListID = rset.getColumnInt( 3 );
            }//--if
        } catch( Exception exc ) {
            String trace = FFSDebug.stackTrace( exc );
            ORCCUtil.log( "*** ORCCDBAPI.getCustomerPayeeInfoByRemoteLinkID  failed:" + trace );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new BPWException( trace );
        }finally{
        try{
                if( rset!=null )rset.close();
        } catch( Exception e ) {
        	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        }
    }
        ORCCUtil.log( "ORCCDBAPI.getCustomerPayeeInfoByRemoteLinkID done remotelinkID = "
                  + remoteLinkID, FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return info;
    }

    ///////////////////////////////////////////////////////////////////////////
    // get ORCC specific pmtInfo into typedprrqrecord.
    // MerchantID from bpw_payee   NameonAccount from bpw_customer
    // remoteLinkid from BPW_ORCCLinkCrossreference
    ///////////////////////////////////////////////////////////////////////////
    public static boolean getORCCPmtInfo( PmtInfo info, TypeDPRRQRecord minfo,
                          boolean newCust, boolean newPayee,
                      boolean newCustPayee,
                      FFSConnectionHolder dbh ) throws Exception
    {
    	String method = "ORCCDBAPI.getORCCPmtInfo";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.getORCCPmtInfo started info.CustomerID="
                  + info.CustomerID + "info.PayeeID="
              + info.PayeeID, FFSConst.PRINT_DEV );
        FFSResultSet rset = null;

    // only when customer is not newly added, we try to get its ORCCCustomerID
        if( ! newCust ) {
        Object[] args =
        {
        info.CustomerID,
        };

        try {

        //assign ORCCCustomerID of this info.customerID to acctID
        ORCCCustCrossRef ref = getORCCCustomerIDByCustomerID( info.CustomerID, dbh );
        minfo.AcctID = Integer.toString( ref.orccAcctID );
        minfo.CustID = Integer.toString( ref.orccCustID );

        if( minfo.NameOnAcct==null || minfo.NameOnAcct.length()<=0 ) {
            rset = DBUtil.openResultSet(dbh, SQL_GET_CUSTOMER_NAMES_BY_ID, args);
            if( rset.getNextRow() ) {
            String nameStr = rset.getColumnString(1)
                    + ", "
                    + rset.getColumnString(2);
            minfo.NameOnAcct = nameStr;
            }
        }//--if
        } catch( Exception exc ) {
        String trace = FFSDebug.stackTrace( exc );
        ORCCUtil.log( "*** ORCCDBAPI.getORCCPmtInfo failed:" + trace );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        throw new BPWException( exc.toString() );
        }finally{
        try{
            if( rset!=null )rset.close();
        } catch( Exception e ) {
        	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        }
        }//try
    }

    // only when Payee is not newly added, we try to get its ExtdPayeeID
        if( ! newPayee ) {
        Object[] args1 = {info.PayeeID};
        rset = null;
        try
        {
        rset = DBUtil.openResultSet(dbh, SQL_GET_EXTDPAYEEID_BY_PAYEEID, args1);
        if( rset.getNextRow() ) {
            minfo.MerchID = rset.getColumnString(1) ;
        }//--if
        } catch( Exception exc ) {
        String trace = FFSDebug.stackTrace( exc );
        ORCCUtil.log( "*** ORCCDBAPI.getORCCPmtInfo failed:" + trace );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        throw new BPWException( exc.toString() );
        }finally{
        try{
            if( rset!=null )rset.close();
        } catch( Exception e ) {
        	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        }
        }//try
        }

    //get remotelinkid for TypeDPRRQRecord minfo
    if( !newCustPayee ) {
        String nameOnAcct = getCustomerPayeeInfo( dbh, info.CustomerID, info.PayeeID ).NameOnAcct;
        if(nameOnAcct!=null)minfo.NameOnAcct = nameOnAcct;
        minfo.RemoteLinkID = getRemoteLinkID( info.CustomerID, info.PayeeID, dbh );
    }

        ORCCUtil.log( "ORCCDBAPI.getORCCPmtInfo done info.CustomerID="
                  + info.CustomerID + "info.PayeeID="
              + info.PayeeID, FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return true;
    }//--getORCCPmtInfo( ..., dbh)


    ///////////////////////////////////////////////////////////////////////////
    // get pmtTrnRslt from database.
    ///////////////////////////////////////////////////////////////////////////
    public static boolean getPmtTrnRslt( PmtTrnRslt rslt, FFSConnectionHolder dbh )
                                 throws Exception
    {
    	String method = "ORCCDBAPI.getPmtTrnRslt";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.getPmtTrnRslt started srvrTID = "
                  + rslt.srvrTid, FFSConst.PRINT_DEV );

        Object[] args =
        {
            rslt.srvrTid
        };

    FFSResultSet rset = null;
        try {
            rset = DBUtil.openResultSet(dbh, SQL_GET_PMTTRNRSLT, args);
            if( rset.getNextRow() ) {
        rslt.customerID = rset.getColumnString(1);
        rslt.extdPmtInfo = rset.getColumnString(2);
        rslt.logID = rset.getColumnString( 3 );
            }//--if
        } catch( Exception exc ) {
            String trace = FFSDebug.stackTrace( exc );
            ORCCUtil.log( "*** ORCCDBAPI.getPmtTrnRslt failed:" + trace );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new BPWException( exc.toString() );
    } finally {
        try{
        if( rset!= null ) rset.close();
        } catch( Exception e ) {
        	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        }
        }//try
        ORCCUtil.log( "ORCCDBAPI.getPmtTrnRslt done srvrTID = "
                  + rslt.srvrTid, FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return true;
    }//--getPmtTrnRslt( ..., dbh)

    ///////////////////////////////////////////////////////////////////////////
    // updatePmtStatus.
    ///////////////////////////////////////////////////////////////////////////
    public static void updatePmtStatus( String srvrTid, String status,
                        String createDate, FFSConnectionHolder dbh )
                    throws Exception
    {
    	String method = "ORCCDBAPI.updatePmtStatus";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        FFSDebug.log( "ORCCDBAPI.updatePmtStatus start, srvrTID="
                  + srvrTid, FFSConst.PRINT_DEV );

        try
        {
            Object args[] =
            {
                status,
        createDate,
                srvrTid
            };

            int rows = DBUtil.executeStatement(dbh, SQL_UPDATE_PAYMENT_STATUS, args);
         }
         catch( Exception exc ) {
             String trace = FFSDebug.stackTrace( exc );
             String err = "*** Payment.updateStatus failed:" + trace ;
             ORCCUtil.log( err);
             PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
             throw new BPWException( exc.toString() );
         }

         FFSDebug.log( "ORCCDBAPI.updateStatus done, srvrTID="
                    + srvrTid, FFSConst.PRINT_DEV );
         PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }

    ///////////////////////////////////////////////////////////////////////////
    // updatePmtStatus.
    ///////////////////////////////////////////////////////////////////////////
    public static void updatePmtStatus( String srvrTid,
                        String status, String extdPmtInfo,
                    String createDate, FFSConnectionHolder dbh )
                       throws Exception
    {
    	String method = "ORCCDBAPI.updatePmtStatus";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        FFSDebug.log( "ORCCDBAPI.updateStatus start, srvrTID="
                    + srvrTid, FFSConst.PRINT_DEV );


        try
        {
            Object args[] =
            {
                status,
                extdPmtInfo,
        createDate,
                srvrTid
            };

            int rows = DBUtil.executeStatement(dbh, SQL_UPDATE_PAYMENT_STATUS2, args);
         }
         catch( Exception exc )
         {
             String trace = FFSDebug.stackTrace( exc );
             String err = "*** Payment.updateStatus failed:" + trace ;
             ORCCUtil.log( err);
             PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
             throw new BPWException( exc.toString() );
         }

         FFSDebug.log( "ORCCDBAPI.updateStatus done, srvrTID="
                    + srvrTid, FFSConst.PRINT_DEV );
         PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }


    ///////////////////////////////////////////////////////////////////////////
    // proccess MLFLinkKeyChange
    // update extdPayeeID (MerchID in ORCC).
    ///////////////////////////////////////////////////////////////////////////
    public static void processMLFLinkChange( TypeMLFLinkInfo linkInfo,
                            FFSConnectionHolder dbh )
                        throws Exception
    {
    	String method = "ORCCDBAPI.processMLFLinkChange";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.processMLFLinkChange start, MerchID="
            + linkInfo.MerchID.trim(), FFSConst.PRINT_DEV );
        linkInfo.MerchID = linkInfo.MerchID.trim();
        try {
        // Get the customerpayee link record by remote link ID
        CustomerPayeeInfo custPayeeInfo = getCustomerPayeeInfoByRemoteLinkID(
                        linkInfo.RemoteLinkID,
                    dbh);
        if( custPayeeInfo==null ) {
        // origPayee does not exist, return
        String msg = "BPW is unable to find the Customer-Payee link. "
                + " Refered by remoteLinkID="
                + linkInfo.RemoteLinkID
                + ". Assuming this link is already changed "
                + "by previous response processing. "
                + "link change record ignored.";
        ORCCUtil.log( msg );
        return;
        }

        // Get the payee custPayeeInfo is linked to
        PayeeInfo origPayee = Payee.findPayeeByID( custPayeeInfo.PayeeID, dbh );
        // Find the payee the MerchID refers to
        PayeeInfo targetPayee = Payee.findPayeeByExtendedID( linkInfo.MerchID, dbh );

        String payeeID = null;
        if( origPayee!=null ) {
        // origPayee exists, i.e. it is not deleted from DB because of previous
        // LinkChange processing. Here we do the dirty work on the payees
        if( targetPayee == null ) {
            // If targetPayee is null, update the ExtdPayeeID on the origPayee in DB
            Object[] args = { linkInfo.MerchID, origPayee.PayeeID };
            DBUtil.executeStatement(dbh, SQL_UPDATE_EXTDPAYEEID_BY_PAYEEID, args );

            // Work on origPayee after this if-block if it is not active
            payeeID = (origPayee.Status.equalsIgnoreCase( DBConsts.ACTIVE ) )
                        ? null
                        : origPayee.PayeeID;
        } else {
            // If targetPayee is not null, make changes on payee and link
            if( !origPayee.PayeeID.equalsIgnoreCase( targetPayee.PayeeID ) ) {
            // If these 2 Payees are not from the same DB row
            // Delete all the duplicate links that could cause problems
            // by the PayeeID change on BPW_CustomerPayee table

            // Update all the payee-related info in all pmt schedules
            modifyPmtTargetPayee( dbh, origPayee.PayeeID,
                        targetPayee.PayeeID );

            // Delete the potential cust-payee duplicates
            purgeDuplicateCustomerPayee( dbh,
                    origPayee.PayeeID,
                    targetPayee.PayeeID );

            // Update all the links s.t. PayeeID=origPayee.PayeeID
            // to targetPayee.PayeeID
            updateLinkPayeeID( dbh, origPayee.PayeeID, targetPayee.PayeeID );

            // If this is a temp payee added by MerchAdd, delete it
            // i.e. it is not a permanent MerchID change from ORCC
            if( ORCCHandler.isTempMerchID( origPayee.ExtdPayeeID ) ) {
                CommonProcessor.deletePayee( dbh, origPayee.PayeeID );
            }
            }

            // Work on targetPayee.PayeeID if it's not active
            payeeID = (targetPayee.Status.equalsIgnoreCase( DBConsts.ACTIVE ) )
                        ? null
                        : targetPayee.PayeeID;
        }
        }

        // Update payee status to active if necessary
        if( payeeID !=null ) Payee.updateStatus( payeeID,  DBConsts.ACTIVE, dbh );

        // Modify the rest of the fields in table BPW_CustomerPayee
        StringBuffer sb = new StringBuffer( "MM/DD/YYYY".length() );
        sb.append( linkInfo.LinkDate.substring( 0, "MM".length() ) );
        sb.append( linkInfo.LinkDate.substring( "MM/".length(), "MM/DD".length() ) );
        sb.append( linkInfo.LinkDate.substring( "MM/DD/".length(), "MM/DD/YYYY".length() ) );

        Object[] args =
        {
        new Integer( linkInfo.ORCCLinkID ),
        new Integer( sb.toString() ),
        DBConsts.ACTIVE,
        custPayeeInfo.CustomerID,
        new Integer( custPayeeInfo.PayeeListID )
        };
        DBUtil.executeStatement(dbh, SQL_UPDATE_ORCC_CUSTOMER_PAYEE, args);
    } catch( Exception exc ) {
        String trace = FFSDebug.stackTrace( exc );
        ORCCUtil.log( "*** ORCCDBAPI.processMLFLinkChange failed:" + trace );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        throw new BPWException( exc.toString() );
    }

        ORCCUtil.log( "ORCCDBAPI.processMLFLinkChange done, MerchID="
                  + linkInfo.MerchID, FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
   }

    ///////////////////////////////////////////////////////////////////////////
    // update temp extdPayeeID to (MerchID in ORCC).
    ///////////////////////////////////////////////////////////////////////////
    public static void updateExtdPayeeIDByPayeeID( String ExtdPayeeID, String PayeeID,
                            FFSConnectionHolder dbh )
                        throws Exception
   {
    	String method = "ORCCDBAPI.updateExtdPayeeIDByPayeeID";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.updateExtdPayeeIDByPayeeID start, payeeID=" + PayeeID
                     + " ExtdPayeeID = " + ExtdPayeeID, FFSConst.PRINT_DEV );
        try {
        // Update all the links s.t. PayeeID=origPayee.PayeeID
        // to targetPayee.PayeeID
        Object[] args = { ExtdPayeeID, PayeeID };
        DBUtil.executeStatement(dbh, SQL_UPDATE_EXTDPAYEEID_BY_PAYEEID, args );

    } catch( Exception exc ) {
        String trace = FFSDebug.stackTrace( exc );
        ORCCUtil.log( "*** ORCCDBAPI.updateExtdPayeeIDByPayeeID failed:" + trace );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        throw new BPWException( exc.toString() );
    }

        ORCCUtil.log( "ORCCDBAPI.updateExtdPayeeIDByPayeeID done, payeeID=" + PayeeID
                     + " ExtdPayeeID = " + ExtdPayeeID, FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
   }

    ///////////////////////////////////////////////////////////////////////////
    // Delete the link records where PayeeID=srcPayeeID for those customers who
    // have both PayeeID's
    ///////////////////////////////////////////////////////////////////////////
    private static void purgeDuplicateCustomerPayee(
                FFSConnectionHolder dbh,
                String srcPayeeID, String destPayeeID ) throws Exception
    {
        ORCCUtil.log( "ORCCDBAPI.purgeDuplicateCustomerPayee start, srcPayeeID="
                  + srcPayeeID + " destPayeeID = " + destPayeeID,
              FFSConst.PRINT_DEV );
    ArrayList list = new ArrayList();
    FFSResultSet rset = null;
    CustomerPayeeInfo custPayeeInfo = null;
    try{
        Object[] args = {srcPayeeID, destPayeeID};
        rset = DBUtil.openResultSet( dbh,
                SQL_SELECT_DUP_CUSTPAYEE,
            args);
        while( rset.getNextRow() ) {
        custPayeeInfo = new CustomerPayeeInfo();
        custPayeeInfo.CustomerID = rset.getColumnString( 1 );
        custPayeeInfo.PayeeListID = rset.getColumnInt( 2 );
        custPayeeInfo.PayeeID = rset.getColumnString( 3 );
        list.add( custPayeeInfo );
        }
    } catch( Exception e ) {
        throw e;
    } finally {
        try {
        if( rset!=null ) rset.close();
        } catch( Exception e ) {
        }
    }

    int len = list.size();
    Object[] args = new Object[2];
    PayeeProcessor payeeProcessor = new PayeeProcessor();
    TypePayeeV1Aggregate payeeAggregate = null;
    for( int i=0; i<len; ++i ) {
        custPayeeInfo = (CustomerPayeeInfo)list.get( i );
        deleteCustomerPayee( dbh,
                    custPayeeInfo.CustomerID,
                    custPayeeInfo.PayeeID );
    }
    list.clear();
        ORCCUtil.log( "ORCCDBAPI.purgeDuplicateCustomerPayee done, srcPayeeID="
                  + srcPayeeID + " destPayeeID = " + destPayeeID,
              FFSConst.PRINT_DEV );
    }


    ///////////////////////////////////////////////////////////////////////////
    // update Customer Payee status by PayeeID and customer ID
    ///////////////////////////////////////////////////////////////////////////
    public static void updateCustomerPayeeStatus( String CustomerID,
                                       int payeeListID,
                       String status,
                       FFSConnectionHolder dbh ) throws Exception
    {
    	String method = "ORCCDBAPI.updateCustomerPayeeStatus";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.updateCustomerPayeeStatus start ", FFSConst.PRINT_DEV );
        Object[] args =
        {
        status,
        CustomerID,
        new Integer( payeeListID )
        };

        try
        {
            int rows = DBUtil.executeStatement(dbh, SQL_UPDATE_CUSTOMERPAYEE_STATUS, args);
        } catch( Exception exc ) {
            String trace = FFSDebug.stackTrace( exc );

            ORCCUtil.log( "*** ORCCDBAPI.updateCustomerPayeeStatus failed:" + trace );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new BPWException( exc.toString() );
        }//try

        ORCCUtil.log( "ORCCDBAPI.updateCustomerPayeeStatus end ", FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
   }

    ///////////////////////////////////////////////////////////////////////////
    // update Customer Payee status by PayeeID and customer ID
    ///////////////////////////////////////////////////////////////////////////
    public static void updateCustomerPayeeStatusWithRouteID( String CustomerID,
                                       int payeeListID,
                       int routeID,
                       String status,
                       FFSConnectionHolder dbh ) throws Exception
    {
    	String method = "ORCCDBAPI.updateCustomerPayeeStatusWithRouteID";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);

        ORCCUtil.log( "ORCCDBAPI.updateCustomerPayeeStatusWithRouteID start ", FFSConst.PRINT_DEV );
    CustomerPayeeInfo[] cps = CustPayee.getCustPayeeByUIDAndRID(
                           CustomerID,
                           routeID,
                           dbh );

    for( int i = 0; i < cps.length; i ++ ) {
        //CustPayee.updateStatus( cps[i].CustomerID,
        //              cps[i].PayeeListID,
        //              DBConsts.ACTIVE,
        //              dbh );
            CustPayeeRoute.updateCustPayeeRouteStatus(cps[i].CustomerID, cps[i].PayeeListID,
                                           routeID, DBConsts.ACTIVE, dbh);
    }

        ORCCUtil.log( "ORCCDBAPI.updateCustomerPayeeStatusWithRouteID end ", FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
   }

    ///////////////////////////////////////////////////////////////////////////
    // update Payee status by ExtdPayeeID
    ///////////////////////////////////////////////////////////////////////////
    public static void updatePayeeStatusByExtdPayeeID( String ExtdPayeeID,
                       String status,
                       FFSConnectionHolder dbh ) throws Exception
    {
    	String method = "ORCCDBAPI.updatePayeeStatusByExtdPayeeID";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.updatePayeeByExtdPayeeID start extdPayeeID="
              + ExtdPayeeID, FFSConst.PRINT_DEV );
        Object[] args =
        {
        status,
        ExtdPayeeID
        };

        try
        {
            int rows = DBUtil.executeStatement(dbh, UPDATE_PAYEE_STATUS_BY_EXTDPAYEEID, args);
        } catch( Exception exc )  {
            String trace = FFSDebug.stackTrace( exc );
            ORCCUtil.log( "*** ORCCDBAPI.updatePayeeByExtdPayeeID failed:" + trace );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new BPWException( exc.toString() );
        }//try

        ORCCUtil.log( "ORCCDBAPI.updatePayeeByExtdPayeeID done extdPayeeID="
                  + ExtdPayeeID, FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
   }


    ///////////////////////////////////////////////////////////////////////////
    // update Customer status
    ///////////////////////////////////////////////////////////////////////////
    public static void updateCustomer(
                           String oldStatus,
                                       String newStatus,
                       FFSConnectionHolder dbh ) throws Exception
    {
    	String method = "ORCCDBAPI.updateCustomer";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.updateCustomer start ", FFSConst.PRINT_DEV );

        Object[] args =
        {
        newStatus,
        oldStatus,
        };

        try {
            int rows = DBUtil.executeStatement(dbh, SQL_UPDATE_CUSTOMER_STATUS, args);
        } catch( Exception exc ) {
            String trace = FFSDebug.stackTrace( exc );
            ORCCUtil.log( "*** ORCCDBAPI.updateCustomer failed:" + trace );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new BPWException( exc.toString() );
        }//try

        ORCCUtil.log( "ORCCDBAPI.updateCustomer end ", FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
   }

    ///////////////////////////////////////////////////////////////////////////
    // update Customer status
    ///////////////////////////////////////////////////////////////////////////
    public static void updateCustomerWithRouteID(
                                       int routeID,
                           String oldStatus,
                                       String newStatus,
                       FFSConnectionHolder dbh ) throws Exception
    {
    	String method = "ORCCDBAPI.updateCustomerWithRouteID";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.updateCustomer start ", FFSConst.PRINT_DEV );

        Object[] args =
        {
        newStatus,
        oldStatus,
        new Integer( routeID )
        };

        try {
            int rows = DBUtil.executeStatement(dbh, SQL_UPDATE_CUSTOMERROUTE_STATUS, args);
        } catch( Exception exc ) {
            String trace = FFSDebug.stackTrace( exc );
            ORCCUtil.log( "*** ORCCDBAPI.updateCustomer failed:" + trace );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new BPWException( exc.toString() );
        }//try

        ORCCUtil.log( "ORCCDBAPI.updateCustomer end ", FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
   }


    ///////////////////////////////////////////////////////////////////////////
    // update Payee status
    ///////////////////////////////////////////////////////////////////////////
    public static void updatePayee( String oldStatus,
                                       String newStatus,
                       FFSConnectionHolder dbh ) throws Exception
    {
    	String method = "ORCCDBAPI.updatePayee";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.updatePayee start", FFSConst.PRINT_DEV );

        Object[] args =
        {
        newStatus,
        oldStatus
        };

        try
        {
            int rows = DBUtil.executeStatement(dbh, SQL_UPDATE_PAYEE_STATUS, args);
        } catch( Exception exc ) {
            String trace = FFSDebug.stackTrace( exc );
            ORCCUtil.log( "*** ORCCDBAPI.updatePayee failed:" + trace );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw ( new BPWException( exc.toString() ) );
        }//try

        ORCCUtil.log( "ORCCDBAPI.updatePayee end", FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
   }

    ///////////////////////////////////////////////////////////////////////////
    // delete Payee where its status is closed
    ///////////////////////////////////////////////////////////////////////////
    public static void deletePayee( FFSConnectionHolder dbh ) throws Exception
    {
    	String method = "ORCCDBAPI.deletePayee";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.deletePayee starts", FFSConst.PRINT_DEV );
    PayeeInfo[] infos = Payee.findPayeeByStatus( DBConsts.CANC_INPROCESS, dbh );
    for( int i = 0; i < infos.length; i ++ ) {
        CommonProcessor.deletePayee( dbh, infos[i].PayeeID );
    }

        ORCCUtil.log( "ORCCDBAPI.deletePayee ends", FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }


    // update the payee fields which are possibly changed in orcc customer response
    public static void updatePayeeORCCFields( PayeeInfo info, FFSConnectionHolder dbh )
        throws Exception
    {
    	
    	String method = "ORCCDBAPI.updatePayeeORCCFields";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);

    ORCCUtil.log( "*** ORCCDBAPI.updatePayeeORCCFields started info.payeeID="
                  + info.PayeeID, FFSConst.PRINT_DEV );
    Object[] args =
        {
        info.Status,
        info.PayeeName,
        info.NickName,
        info.ContactName,
        info.Phone,
        info.Extension,
        info.Addr1,
        info.City,
        info.State,
        info.Zipcode,
        new Integer( info.DaysToPay ),
        info.ExtdPayeeID

        };
    try {
        DBUtil.executeStatement( dbh, SQL_UPDATE_PAYEEINFO_BY_EXTDPAYEEID, args );
    } catch( Exception exc ) {
            String trace = FFSDebug.stackTrace( exc );
        ORCCUtil.log( "*** ORCCDBAPI.updatePayeeORCCFields failed:" + trace );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        throw new BPWException( exc.toString() );
    }//try
    ORCCUtil.log( "*** ORCCDBAPI.updatePayeeORCCFields done info.payeeID="
                  + info.PayeeID, FFSConst.PRINT_DEV );
    PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }


    ///////////////////////////////////////////////////////////////////////////
    // delete Customer where its status is closed
    ///////////////////////////////////////////////////////////////////////////
    public static void deleteCustomer( FFSConnectionHolder dbh ) throws Exception
    {
    	String method = "ORCCDBAPI.deleteCustomer";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.deleteCustomer started", FFSConst.PRINT_DEV );
    CustomerInfo[] infos = Customer.findCustomerByStatus( DBConsts.CANC_INPROCESS, dbh );
        for( int i = 0 ; i < infos.length; i ++ )  {
             Object[] custIDArg = { infos[i].customerID };
             DBUtil.executeStatement( dbh, SQL_DELETE_XREF_BY_CUSTID, custIDArg );
             CommonProcessor.deleteCustomer( dbh, infos[i].customerID, 0 );
    }

        ORCCUtil.log( "ORCCDBAPI.deleteCustomer ended", FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }

    ///////////////////////////////////////////////////////////////////////////
    // delete Customer where its status is closed
    ///////////////////////////////////////////////////////////////////////////
    public static void deleteCustomerWithRouteID( int routeID, FFSConnectionHolder dbh ) throws Exception
    {
    	String method = "ORCCDBAPI.deleteCustomerWithRouteID";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.deleteCustomerWithRouteID started", FFSConst.PRINT_DEV );
    CustomerInfo[] infos = Customer.findCustomerByStatus( DBConsts.CANC_INPROCESS, dbh );
        for( int i = 0 ; i < infos.length; i ++ )  {
            Object[] custIDArg = { infos[i].customerID };
            DBUtil.executeStatement( dbh, SQL_DELETE_XREF_BY_CUSTID, custIDArg );
            CommonProcessor.deleteCustomerWithRouteID( dbh, infos[i].customerID, routeID );
    }

        ORCCUtil.log( "ORCCDBAPI.deleteCustomerWithRouteID ended", FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }

    ///////////////////////////////////////////////////////////////////////////
    // update Customer Payee status
    ///////////////////////////////////////////////////////////////////////////
    public static void updateCustomerPayee(
                       String oldStatus,
                   FFSConnectionHolder dbh ) throws Exception
    {
    	String method = "ORCCDBAPI.updateCustomerPayee";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.updateCustomerPayee started", FFSConst.PRINT_DEV );

    FFSResultSet rset = null;
        try {
        String newStatus = null;
        if( oldStatus.equals( DBConsts.INPROCESS ) ) {
        Object[] args =
        {
            DBConsts.INPROCESS,
            DBConsts.ACTIVE,
        };

        // Finding the BPW_CustomerPayee rows to update
        ArrayList list = new ArrayList();
        CustomerPayeeInfo custPayee = null;
        try{
            rset = DBUtil.openResultSet(dbh,
                SQL_FIND_CUSTPAYEE_BY_STATUS_BY_PAYEE_STATUS,
                args);
            while( rset.getNextRow()) {
            custPayee = new CustomerPayeeInfo();
            custPayee.CustomerID = rset.getColumnString( 1 );
            custPayee.PayeeListID = rset.getColumnInt( 2 );
            custPayee.PayeeID = rset.getColumnString( 3 );
            list.add( custPayee );
             }
        } catch( Exception exc ) {
            String trace = FFSDebug.stackTrace( exc );
            ORCCUtil.log( "*** ORCCDBAPI.updatePayee failed:" + trace );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new BPWException( exc.toString() );
        } finally {
            try{
            if( rset!=null ) rset.close();
            }catch( Exception e ) {
            	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            }
        }
        rset = null;

        // Updating these custPayee rows
        int len = list.size();
        for( int i = 0; i < len; i ++ ) {
            custPayee = (CustomerPayeeInfo)list.get( i );
            updateCustomerPayeeStatus( custPayee.CustomerID,
                        custPayee.PayeeListID,
                        DBConsts.ACTIVE,
                        dbh );
        }
        list.clear();
        } else if( oldStatus.equals( DBConsts.CANC_INPROCESS ) ) {
        Object[] args = {DBConsts.CLOSED };
        rset = DBUtil.openResultSet( dbh,
                SQL_SELECT_CUSTPAYEE_STATUS_BY_STATUS,
                args );
        String payeeID = null;
        String custID = null;
        while( rset.getNextRow() ) {
            payeeID = rset.getColumnString( 1 );
            custID = rset.getColumnString( 3 );
            deleteCustomerPayee( dbh, payeeID, custID );
        }
        }
        } catch( Exception exc ) {
            String trace = FFSDebug.stackTrace( exc );
            ORCCUtil.log( "*** ORCCDBAPI.updatePayee failed:" + trace );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new BPWException( exc.toString() );
        } finally {
        try{
        if( rset!=null ) rset.close();
        }catch( Exception e ) {
        	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        }
    }
        ORCCUtil.log( "ORCCDBAPI.updateCustomerPayee end", FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
   }

   ///////////////////////////////////////////////////////////////////////////
   ///////////////////////////////////////////////////////////////////////////
   private static CustomerPayeeInfo[] findCustomerPayeeByStatuses(
                      String currStatus, FFSConnectionHolder dbh )
                      throws Exception
   {
        ORCCUtil.log( "ORCCDBAPI.findCustomerPayeeByPayeeStatus start status="
                  + currStatus, FFSConst.PRINT_DEV );

    if( currStatus.equals( DBConsts.INPROCESS ) ) {
        Object[] args =
        {
        DBConsts.INPROCESS,
        DBConsts.ACTIVE,
        };

        ArrayList al = new ArrayList();
        CustomerPayeeInfo[] infos = null;
        FFSResultSet rset = null;
        try {
        rset = DBUtil.openResultSet(dbh,
            SQL_FIND_CUSTPAYEE_BY_STATUS_BY_PAYEE_STATUS,
            args);
        while( rset.getNextRow()) {
            CustomerPayeeInfo custPayee = new CustomerPayeeInfo();
            custPayee.CustomerID = rset.getColumnString( 1 );
            custPayee.PayeeListID = rset.getColumnInt( 2 );
            custPayee.PayeeID = rset.getColumnString( 3 );
            al.add( custPayee );
         }
        } catch( Exception exc ) {
        String trace = FFSDebug.stackTrace( exc );
        ORCCUtil.log( "*** ORCCDBAPI.updatePayee failed:" + trace );
        throw new BPWException( exc.toString() );
        } finally {
        try{
            if( rset!=null ) rset.close();
        }catch( Exception e ) {
        }
        }//try
        infos = new CustomerPayeeInfo[ al.size() ];
        infos = (CustomerPayeeInfo[])al.toArray( infos );
        ORCCUtil.log( "ORCCDBAPI.findCustomerPayeeByPayeeStatus end" );

        return infos;
    } else if( currStatus.equals( DBConsts.CANC_INPROCESS ) ) {
        Object args[] = {DBConsts.CANC_INPROCESS };
        ArrayList al = new ArrayList();
        CustomerPayeeInfo[] infos = null;
        FFSResultSet rset = null;

        try {
        rset = DBUtil.openResultSet(dbh,
            SQL_FIND_CUSTPAYEE_BY_STATUS,
            args);
        while( rset.getNextRow()) {
            CustomerPayeeInfo custPayee = new CustomerPayeeInfo();
            custPayee.CustomerID = rset.getColumnString( 1 );
            custPayee.PayeeListID = rset.getColumnInt( 2 );
            custPayee.PayeeID = rset.getColumnString( 3 );
            al.add( custPayee );
         }
        } catch( Exception exc ) {
        String trace = FFSDebug.stackTrace( exc );
        ORCCUtil.log( "*** ORCCDBAPI.updatePayee failed:" + trace );
        throw new BPWException( exc.toString() );
        } finally {
        try{
            if( rset!=null ) rset.close();
        }catch( Exception e ) {
        }
        }//try
        infos = new CustomerPayeeInfo[ al.size() ];
        infos = (CustomerPayeeInfo[])al.toArray( infos );
            ORCCUtil.log( "ORCCDBAPI.findCustomerPayeeByPayeeStatus done status="
                      + currStatus, FFSConst.PRINT_DEV );

        return infos;
    } else {
            ORCCUtil.log( "ORCCDBAPI.findCustomerPayeeByPayeeStatus done get nothing, status="
                      + currStatus, FFSConst.PRINT_DEV );
        // Otherwise return nothing back
        return new CustomerPayeeInfo[0];
    }
   }


   /////////////////////////////////////////////////////////////////////////////////////////////
   // add new ORCC payee into database, add its orccpayeemaskfields also.
   /////////////////////////////////////////////////////////////////////////////////////////////
   public static void addPayee( PayeeInfo pinfo, ORCCPayeeMaskFields fields,
            FFSConnectionHolder dbh ) throws Exception
   {
	   String method = "ORCCDBAPI.addPayee";
       long start = System.currentTimeMillis();
       int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
       
       ORCCUtil.log( "ORCCDBAPI.addPayee start payeeID=" + pinfo.PayeeID);
       storePayeeToDB( pinfo, dbh );
       storeORCCPayeeMaskToDB( fields, dbh );
       ORCCUtil.log( "ORCCDBAPI.addPayee done payeeID=" + pinfo.PayeeID);
       PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);

   }



    public static void processOneCustPayeeRslt( CustPayeeRslt  rslt, FFSConnectionHolder dbh )
    throws BPWException
    {

    	String method = "ORCCDBAPI.processOneCustPayeeRslt";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ORCCUtil.log( "ORCCDBAPI.processONeCustPayeeRslt start ", FFSConst.PRINT_DEV );
        // update customer status.

        String prcStatus = null;

        if ( rslt.status == 0 )
        {
            if ( rslt.action.equals( "CANC") )
            {
                    prcStatus = DBConsts.CLOSED;
            } else
                prcStatus = DBConsts.ACTIVE;
        } else
        {
            //if ( rslt.action.equals( "CANC") )
            //    prcStatus = PENDING;
            //else
                prcStatus = DBConsts.ERROR;
        }

        try
        {
            //
            // update the status in the CustomerPayee table structure
            //
            CustPayeeRoute custPayRoute = CustPayeeRoute.getCustPayeeRoute2(rslt.customerID,
                                                         rslt.payeeListID, ORCCHandler.getRouteID(), dbh);
            if (custPayRoute != null) {
                String status = custPayRoute.Status;
                if ( (!status.startsWith( "MOD" )) &&( !status.equals( "CANC" )) )
                {
                    CustPayeeRoute.updateCustPayeeRouteStatus(rslt.customerID,
                                                              rslt.payeeListID,
                                                              ORCCHandler.getRouteID(),
                                                              prcStatus,
                                                              dbh);
                    if (prcStatus.equals(CLOSED)) {
                        // Set the custPayee status to "CLOSED".
                        CustPayee.updateStatus( rslt.customerID,
                                                rslt.payeeListID,
                                                prcStatus,
                                                rslt.status,
                                                rslt.message,
                                                dbh );
                    }
                }
            }
        } catch ( Exception exc ) {
            String msg = "Exception in ORCCDBAPI.processOneCustPayeeRslt: " + exc.toString();
            // FFSDebug.log( exc, msg );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);

            throw new BPWException( msg );
        }
        ORCCUtil.log( "ORCCDBAPI.processONeCustPayeeRslt start ", FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);

    }


    static void processMLFMerchKeyChange( String keyChange,
                    TypeMLFMerchInfo mInfo,
                    FFSConnectionHolder dbh ) throws Exception
    {
    	String method = "ORCCDBAPI.processMLFMerchKeyChange";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
    ORCCUtil.log( "Start making DB changes based on MLFMerchKeyChange", FFSConst.PRINT_DEV );
    FFSResultSet rset = null;
    ORCCPayeeMaskFields masks = new ORCCPayeeMaskFields();
    try{
        String payeeID=null;
        Object[] args = new Object[1];
        args[0] = keyChange;    // keychange = temp MerchID for Merch#
        rset = DBUtil.openResultSet( dbh, SQL_SELECT_PAYEE_BY_EXTDPAYEEID, args );
        if( rset.getNextRow() ) {
        payeeID = rset.getColumnString( 1 );
        } else {
        String msg= "Error, cannot find merchant with temp. ID="+keyChange;
        ORCCUtil.log( msg );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);

        throw new Exception( msg );
        }
        masks.payeeID = payeeID;
    } catch (Exception e ) {
    	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);

        throw e;
    } finally {
        if( rset!=null ) rset.close();
        rset = null;
    }
    try{
        masks.acctMaxLength = Integer.parseInt( mInfo.AcctLengthMax );
    } catch ( NumberFormatException e ) {
        masks.acctMaxLength = -1;
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);

    }
    try{
        masks.acctMinLength = Integer.parseInt( mInfo.AcctLengthMin );
    } catch ( NumberFormatException e ) {
        masks.acctMinLength = -1;
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);

    }
    masks.acctMask1 = (mInfo.AcctMask1==null||mInfo.AcctMask1.length()<=0 )
                ? null
                : mInfo.AcctMask1;
    masks.acctMask2 = (mInfo.AcctMask2==null||mInfo.AcctMask2.length()<=0 )
                ? null
                : mInfo.AcctMask2;
    masks.acctMask3 = (mInfo.AcctMask3==null||mInfo.AcctMask3.length()<=0 )
                ? null
                : mInfo.AcctMask3;
    masks.acctMask4 = (mInfo.AcctMask4==null||mInfo.AcctMask4.length()<=0 )
                ? null
                : mInfo.AcctMask4;
    masks.acctMask5 = (mInfo.AcctMask5==null||mInfo.AcctMask5.length()<=0 )
                ? null
                : mInfo.AcctMask5;

    // Store payee masks to DB
    storeORCCPayeeMaskToDB( masks, dbh );
    ORCCUtil.log( "End making DB changes based on MLFMerchKeyChange", FFSConst.PRINT_DEV );
    PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);

    }


    private static final CustomerPayeeInfo getCustomerPayeeInfo(
                    FFSConnectionHolder dbh,
                String custID, String payeeID )
                throws FFSException
    {
    ORCCUtil.log( "Start getting customer-payee info: custID="
            + custID
            + "; payeeID="
            + payeeID, FFSConst.PRINT_DEV );

    CustomerPayeeInfo customerPayeeInfo = null;
    FFSResultSet rset = null;

    try{
        Object args[] = { custID, payeeID };
        rset = DBUtil.openResultSet( dbh,
                    SQL_SELECT_CUSTPAYEE_BY_CUSTID_PAYEEID,
                    args);
        if( rset.getNextRow() ) {
        customerPayeeInfo = new CustomerPayeeInfo();
        customerPayeeInfo.PayeeID       = payeeID;
        customerPayeeInfo.PayeeListID       = rset.getColumnInt( 2 );
        customerPayeeInfo.CustomerID        = custID;
        customerPayeeInfo.PayAcct       = rset.getColumnString( 4 );
        customerPayeeInfo.NameOnAcct        = rset.getColumnString( 5 );
        customerPayeeInfo.Status        = rset.getColumnString( 6 );
        customerPayeeInfo.ErrCode       = rset.getColumnInt( 7 );
        customerPayeeInfo.ErrMsg        = rset.getColumnString( 8 );
        customerPayeeInfo.ExtdInfo      = rset.getColumnString( 9 );
        customerPayeeInfo.LinkID        = rset.getColumnInt( 10 );
        customerPayeeInfo.LinkGoDate        = rset.getColumnInt( 11 );
        customerPayeeInfo.SubmitDate        = rset.getColumnString( 12 );
        }
    } catch ( FFSException e ) {
        throw e;
    } catch ( Exception e ) {
        throw new FFSException( e.getLocalizedMessage() );
    } finally {
        if( rset!=null ) rset.close();
    }

    ORCCUtil.log( "End getting customer-payee info: custID="
            + custID
            + "; payeeID="
            + payeeID, FFSConst.PRINT_DEV );

    return customerPayeeInfo;
    }



    ///////////////////////////////////////////////////////////////////////////
    // Move all the payment schedules refering to srcPayeeID to be refereing to
    // destPayeeID
    ///////////////////////////////////////////////////////////////////////////
    private static final void modifyPmtTargetPayee(
                FFSConnectionHolder dbh,
                String srcPayeeID,
                String destPayeeID ) throws Exception
    {
    ORCCUtil.log( "Modifying the customer-payee info in payment schedules...", FFSConst.PRINT_DEV );
    FFSResultSet rset1 = null;
    Object[] args = null;
    try{
        // Find out the payee list id
        String pID = null;      // payeeID
        String cID = null;      // custID
        String pAcct = null;    // PayAcct
        int plID = -1;      // PayeeListID

        // Get all the pmt schedules payable to srcPayeeID
        args = new Object[1];
        args[0] = srcPayeeID;
        rset1 = DBUtil.openResultSet( dbh, SQL_SELECT_PMTINSTR_BY_PAYEEID, args );

        while( rset1.getNextRow() ) {
        pID = srcPayeeID;
        cID = rset1.getColumnString( 1 );

        // Find the CustomerPayee record refered by cID and pID
        CustomerPayeeInfo customerPayeeInfo = getCustomerPayeeInfo( dbh,
                        cID,
                        pID );
        if ( customerPayeeInfo==null ) {
            // If this happens, it's a DB problem
            String msg = "DB inconsistency found. CustomerPayee "
                    + "record customerID= "
                    + cID
                    +" and payeeID= "
                    + pID
                    + " not found!";
            throw new FFSException ( msg );
        } else {
            // Find the CustomerPayee record refered by cID and destPayeeID
            CustomerPayeeInfo customerPayeeInfo2 = getCustomerPayeeInfo(
                            dbh,
                            cID,
                            destPayeeID );
            if( customerPayeeInfo2==null ) {
            // Get the payeelistID and the payAcct
            plID  = customerPayeeInfo.PayeeListID;
            pAcct = customerPayeeInfo.PayAcct;
            } else {
            // If it exists, change pmt
            plID  = customerPayeeInfo2.PayeeListID;
            pAcct = customerPayeeInfo2.PayAcct;
            }

            // Now use destPayeeID, cID, plID and pAcct to update the records
            args = new Object[5];
            args[0] = destPayeeID;
            args[1] = new Integer( plID );
            args[2] = pAcct;
            args[3] = cID;
            args[4] = srcPayeeID;
            DBUtil.executeStatement( dbh,
                        SQL_UPDATE_PMTINSTR_PAYEE_INFO_BY_CUSTID_PAYEEID,
                        args );
        }
        }
        ORCCUtil.log( "Payment table payee change complete", FFSConst.PRINT_DEV );

        ORCCUtil.log( "Start updating recurring payments", FFSConst.PRINT_DEV );
        // Get all the rec pmt schedules payable to srcPayeeID
        args = new Object[1];
        args[0] = srcPayeeID;
        rset1 = DBUtil.openResultSet( dbh, SQL_SELECT_RECPMTINSTR_BY_PAYEEID, args );

        while( rset1.getNextRow() ) {
        pID = srcPayeeID;
        cID = rset1.getColumnString( 1 );

        // Find the CustomerPayee record refered by cID and pID
        CustomerPayeeInfo customerPayeeInfo = getCustomerPayeeInfo( dbh,
                        cID,
                        pID );
        if ( customerPayeeInfo==null ) {
            // If this happens, it's a DB problem
            String msg = "DB inconsistency found. CustomerPayee "
                    + "record customerID= "
                    + cID
                    +" and payeeID= "
                    + pID
                    + " not found!";
            throw new FFSException ( msg );
        } else {
            // Find the CustomerPayee record refered by cID and destPayeeID
            CustomerPayeeInfo customerPayeeInfo2 = getCustomerPayeeInfo(
                            dbh,
                            cID,
                            destPayeeID );
            if( customerPayeeInfo2==null ) {
            // Get the payeelistID and the payAcct
            plID = customerPayeeInfo.PayeeListID;
            pAcct = customerPayeeInfo.PayAcct;
            } else {
            // If it exists, change pmt
            plID  = customerPayeeInfo2.PayeeListID;
            pAcct = customerPayeeInfo2.PayAcct;
            }

            // Now use destPayeeID, cID, plID and pAcct to update the records
            args = new Object[5];
            args[0] = destPayeeID;
            args[1] = new Integer( plID );
            args[2] = pAcct;
            args[3] = cID;
            args[4] = srcPayeeID;
            DBUtil.executeStatement( dbh,
                    SQL_UPDATE_RECPMTINSTR_PAYEE_INFO_BY_CUSTID_PAYEEID,
                    args );
        }
        }
        ORCCUtil.log( "Recurrent payment table payee change complete", FFSConst.PRINT_DEV );
    } catch( Exception e ) {
        ORCCUtil.log( e.toString() );
    } finally {
        try{
        if( rset1!=null ) rset1.close();
        } catch( Exception e ) {
        }
    }
    ORCCUtil.log( "Finished modifying the customer-payee info in payment schedules...", FFSConst.PRINT_DEV );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Modify payeeID field of the BPW_CustomerPayee, also update the ORCC
    // cross reference table.
    ///////////////////////////////////////////////////////////////////////////
    private static final void updateLinkPayeeID(
                FFSConnectionHolder dbh,
            String srcPayeeID,
            String destPayeeID ) throws Exception
    {
    Object[] args = {destPayeeID, srcPayeeID};
    DBUtil.executeStatement( dbh, SQL_UPDATE_ORCCCUSTPAYEE_PAYEEID, args );
    DBUtil.executeStatement( dbh, SQL_UPDATE_CUSTPAYEE_PAYEEID, args );
    }



    private static final void deleteCustomerPayee(
                FFSConnectionHolder dbh,
            String custID,
            String payeeID ) throws Exception
    {
    Object args[] = new Object[2];
    args[0] = custID;
    args[1] = payeeID;

    // Delete the custPayee record, and the ORCCLinkCrossRef
    DBUtil.executeStatement( dbh, SQL_DELETE_FROM_ORCCLINKCROSSREF, args );
    DBUtil.executeStatement( dbh, SQL_DELETE_FROM_CUST_PAYEE, args );
    }

    static final int getTransIDMultiplier() throws BPWException
    {
    return DBUtil.getIndex( DBConsts.ORCCTIDMULTIPLIER );
    }

    static final void incrementTransIDMultiplier() throws BPWException
    {
    DBUtil.getNextIndex( DBConsts.ORCCTIDMULTIPLIER );
    }

    static final String findSrvrTID( FFSConnectionHolder dbh,
                    int hi,
                int lo ) throws Exception
    {
    	String method = "ORCCDBAPI.findSrvrTID";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
    FFSResultSet rset = null;
    String id = null;

    try{
        int count = 0;
        Object[] args = { Integer.toString( hi ) };
        rset = DBUtil.openResultSet( dbh, SQL_CHECK_EXISTING_SRVRTID, args );
        rset.getNextRow();
        count = rset.getColumnInt( 1 );
        if( count>0 ) {
        id = Integer.toString( hi );
        } else {
        rset.close();
        rset = null;

        args[0] = Integer.toString( lo );
        rset = DBUtil.openResultSet( dbh, SQL_CHECK_EXISTING_SRVRTID, args );
        rset.getNextRow();
        count = rset.getColumnInt( 1 );
        if( count>0 ) {
            id = Integer.toString( lo );
        } else {
        	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new Exception( "Cannot find SrvrTID between candidates "
                    + hi
                + " and "
                + lo );
        }
        }
    } catch ( Exception e ) {
    	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        throw new BPWException( e.getLocalizedMessage());
    } finally {
        if( rset!=null ) rset.close();
    }
    PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    return id;
    }
}
