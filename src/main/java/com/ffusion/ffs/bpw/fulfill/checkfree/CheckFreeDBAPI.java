// Copyright (c) 2002 Financial Fusion, Inc.
// All rights reserved.

package com.ffusion.ffs.bpw.fulfill.checkfree;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

import com.ffusion.ffs.bpw.db.CustPayee;
import com.ffusion.ffs.bpw.db.CustPayeeRoute;
import com.ffusion.ffs.bpw.db.CustRoute;
import com.ffusion.ffs.bpw.db.Customer;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.db.Payee;
import com.ffusion.ffs.bpw.db.PmtExtraInfo;
import com.ffusion.ffs.bpw.db.PmtInstruction;
import com.ffusion.ffs.bpw.interfaces.BPWException;
import com.ffusion.ffs.bpw.interfaces.CustomerInfo;
import com.ffusion.ffs.bpw.interfaces.CustomerRouteInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.PmtInfo;
import com.ffusion.ffs.bpw.interfaces.PmtInvoice;
import com.ffusion.ffs.bpw.master.CommonProcessor;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.db.FFSResultSet;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.TypePmtHistInfo;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.TypeRetCreditDetail;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.ValueSetPmtMethod;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.ValueSetPmtTransCode;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.ValueSetPmtType;
import com.sap.banking.common.interceptors.PerfLoggerUtil;



public class CheckFreeDBAPI {

    private static final String SQL_GET_SRVRTID_WHERE_SRVRSUBMITDATE =
    "SELECT SrvrTID FROM BPW_PmtHist WHERE SrvrSubmitdate=? ";

    private static final String SQL_GET_SRVRTID_WHERE_SUBMITDATE =
    "SELECT SrvrTID FROM BPW_PmtHist WHERE Submitdate=? ";

    private static final String SQL_GET_SRVRTID_WHERE_LOCALPMTID =
    "SELECT SrvrTID FROM BPW_PmtHist WHERE LocalPmtID=? ";

    private static final String SQL_GET_LOCALPMTID_SRVRSUBMITDATE_WHERE_SRVRTID =
    "SELECT LocalPmtID, SrvrSubmitdate FROM BPW_PmtHist WHERE SrvrTID=? ";

    private static final String SQL_GET_MOST_RECENT_LOCALPMTID =
    "SELECT LocalPmtID FROM BPW_PmtHist ORDER BY LocalPmtID DESC";

    private static final String SQL_INSERT_SRVRTID_SRVRSUBMITDATE =
    "INSERT INTO BPW_PmtHist (SrvrTID, SrvrSubmitdate) "
    + "VALUES (?,?)";

    private static final String SQL_INSERT_SRVRTID_LOCALPMTID =
    "INSERT INTO BPW_PmtHist (SrvrTID, LocalPmtID) "
    + "VALUES (?,?)";

    private static final String SQL_UPDATE_SRVRSUBMITDATE_WHERE_SRVRTID =
    "UPDATE BPW_PmtHist SET SrvrSubmitdate=? WHERE SrvrTID=? ";

    private static final String SQL_UPDATE_SRVRTID_WHERE_SRVRSUBMITDATE =
    "UPDATE BPW_PmtHist SET SrvrTID=? WHERE SrvrSubmitdate=? ";

    private static final String SQL_UPDATE_PMTHIST =
    "UPDATE BPW_PmtHist SET "
    + "FileTrackID=?, PrcDate=?,"
    + "PmtType=?, SettleMethod=?, PmtTransType=?, "
    + "CreditTrcNum=?, CheckNum=?, Submitdate=? "
    + "WHERE SrvrSubmitdate=?";

    private static final String SQL_INSERT_CREDITRET =
    "UPDATE BPW_PmtHist SET "
    + "DateCreditted=?,CheckNum=?,SettleMethod=?,"
    + "DateCreditRtn=?,CreditRtnReason=?,CreditTrcNum=? "
    + "WHERE SrvrTID=?";

    private static final String SQL_GET_PAYEESTATUS_BY_PAYEEID =
    "SELECT Status FROM BPW_Payee WHERE PayeeID=?";

    private static final String SQL_SELECT_PAYMENT_BY_DETAILS =
    "SELECT "
    + "P.CustomerID,P.PayeeID,P.PayeeListID, P.BankID, "
    + "P.AcctDebitID,P.AcctDebitType, P.PayAcct,P.DateCreate, "
    + "P.CurDef, P.Amount, P.RouteID, P.StartDate, "
    + "P.Status, P.LogID, P.Memo, P.PaymentType, "
    + "P.SrvrTID, P.FIID "
    + "FROM BPW_PmtInstruction P, BPW_Payee M "
    + "WHERE M.PayeeName=? AND P.PayeeID=M.PayeeID "
    + "AND P.CustomerID=? AND P.Amount=? "
    + "AND P.RouteID=? AND P.PayAcct=? "
    + "AND (P.StartDate=? OR P.StartDate=?)";

    private static final String SQL_SELECT_PAYMENT_BY_CFPMTID =
    "SELECT "
    + "P.CustomerID,P.PayeeID,P.PayeeListID, P.BankID, "
    + "P.AcctDebitID,P.AcctDebitType, P.PayAcct,P.DateCreate, "
    + "P.CurDef, P.Amount, P.RouteID, P.StartDate, "
    + "P.Status, P.LogID, P.Memo, P.PaymentType, "
    + "P.SrvrTID, P.FIID "
    + "FROM BPW_PmtInstruction P, BPW_PmtHist H "
    + "WHERE P.SrvrTID=H.SrvrTID "
    + "AND H.SrvrSubmitdate=? ";

    // return PmtInfo object representing the payment associated with srvrTID
    public static PmtInfo getPmtInfo( String srvrTID, FFSConnectionHolder dbh )
    throws Exception
    {
    	String method = "CheckFreeDBAPI.getPmtInfo";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        CheckFreeUtil.log( "CheckFreeDBAPI.getPmtInfo start, "
                           + "SrvrTID = " + srvrTID );

        PmtInfo pmt = PmtInstruction.getPmtInfo( srvrTID, dbh );

        if (pmt == null) {
            String msg = "Cannot find SrvrTID";

            CheckFreeUtil.warn( "*** CheckFreeDBAPI.getPmtInfo failed: " + msg + srvrTID);
            
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            
            throw new BPWException( msg );
        }

        CheckFreeUtil.log( "CheckFreeDBAPI.getPmtInfo done, "
                           + "SrvrTID = " + srvrTID );
        
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return pmt;
    }


    // update CustPayee status
    public static void updateCustPayeeStatus( String uid, long listID, int routeID,
                                              String status, int errCode,
                                              String errMsg,
                                              FFSConnectionHolder dbh )
    throws Exception
    {
    	
    	String method = "CheckFreeDBAPI.updateCustPayeeStatus";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        CheckFreeUtil.log( "CheckFreeDBAPI.updateCustPayeeStatus start, "
                           + "uid = " + uid + ", listID = " + listID );

        // get the CustPayee info
        CustPayee cp = new CustPayee();
        cp.findCustPayeeByPayeeListID( uid, (int)listID, dbh );

        if (cp.getCustomerID() == null) {
            String msg = "Cannot find CustomerPayee";

            CheckFreeUtil.warn( "*** CheckFreeDBAPI.updateCustPayeeStatus failed: "
                    + msg + "uid = " + uid + ", listID = " + listID);
            
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            
            throw new BPWException( msg );
        }

        // we may need to update BPW_Payee
        String payeeID = cp.getPayeeID();
        Object[] args = { payeeID};
        FFSResultSet rset = null;

        String payeeStatus = null;
        try {
            rset = DBUtil.openResultSet( dbh, SQL_GET_PAYEESTATUS_BY_PAYEEID, args );
            if (rset.getNextRow()) {
                payeeStatus = rset.getColumnString( 1 );
            } else {
                String msg = "Cannot find Payee with payeeID=" + payeeID;

                CheckFreeUtil.warn( "*** CheckFreeDBAPI.updateCustPayeeStatus failed: "
                        + msg + "uid = " + uid + ", listID = " + listID);
                
                PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
                
                throw new BPWException( msg );
            }
        } catch (Exception e) {
            CheckFreeUtil.warn( "*** CheckFreeDBAPI.updateCustPayeeStatus failed: "
                    + e.getMessage()+ "uid = " + uid + ", listID = " + listID );
            
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            
            throw e;
        } finally {
            if (rset != null) rset.close();
        }

        if (payeeStatus.equals( DBConsts.INPROCESS )) {
            Payee.updateStatus( payeeID, DBConsts.ACTIVE, dbh );
        }

        // we do need to update BPW_CustomerPayee
        CustPayeeRoute custPayRoute = CustPayeeRoute.getCustPayeeRoute2(uid, (int)listID, routeID, dbh);
        if (custPayRoute != null) {
            String cpStatus = custPayRoute.Status;
            boolean canUpdate = !cpStatus.equals( DBConsts.MOD )
                                && !status.equals( DBConsts.CANC );

            if ((!cpStatus.equals( status ) && canUpdate) || (status.equals(DBConsts.FAILEDON))) {
                try {
                    CustPayeeRoute.updateCustPayeeRouteStatus( uid,
                                                               (int)listID,
                                                               routeID, status, dbh);
                    if (status.equals(DBConsts.CLOSED)) {
                        CustPayee.updateStatus( uid, (int)listID, status, errCode, errMsg, dbh );
                    }
                } catch (Exception e) {
                    CheckFreeUtil.warn( "*** CheckFreeDBAPI.updateCustPayeeStatus failed: "
                            + FFSDebug.stackTrace( e ) + " uid = " + uid + ", listID = " + listID);
                    
                    PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
                    throw e;
                }
            }
        }

        if (status.equals(DBConsts.FAILEDON) == true) {
            // Check if we should fail any payments as a result of this payee's state.
            // Defaults to "true" to maintain backwards compatibility.
            String failPayeeFailPmt = CheckFreeUtil.getProperty(DBConsts.BPW_FAILPAYEE_TOFAILPMT,
                                                                DBConsts.TRUE);
            boolean failAssociatedPmts = true;
            if (failPayeeFailPmt != null) {
                // This next statement may look strange, but remember that we want
                // to default to true. i.e. false = false, everything else = true
                failAssociatedPmts = !(failPayeeFailPmt.equalsIgnoreCase(DBConsts.FALSE));
            }

            if (failAssociatedPmts == true) {
                // Fail any payments in a "WILLPROCESSON" state that are associated with
                // this customer's payee.
                CommonProcessor.failWillProcessOnPaymentByPayeeListID( dbh, uid, (int)listID );
            }
        }

        CheckFreeUtil.log( "CheckFreeDBAPI.updateCustPayeeStatus done, "
                           + "uid = " + uid + ", listID = " + listID );
        
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }

    // Get SrvrTID with given SrvrSubmitDate
    public static String getSrvrTIDBySrvrSubmitDate( String srvrTimeStamp,
                                                     FFSConnectionHolder dbh )
    throws Exception
    {
    	
    	String method = "CheckFreeDBAPI.getSrvrTIDBySrvrSubmitDate";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        CheckFreeUtil.log( "CheckFreeDBAPI.getSrvrTIDBySrvrSubmitDate start, "
                           + "SrvrTimeStamp = " + srvrTimeStamp );

        FFSResultSet rset = null;
        String srvrTID;
        try {
            Object[] args =
            {
                srvrTimeStamp
            };

            rset = DBUtil.openResultSet( dbh, SQL_GET_SRVRTID_WHERE_SRVRSUBMITDATE, args );
            if (rset.getNextRow()) {
                srvrTID = rset.getColumnString(1);
            } else {
            	
            	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            	
                throw new Exception( "CheckFreeDBAPI.getSrvrTIDBySrvrSubmitDate: Unable to get SrvrTID from :"
                                     + srvrTimeStamp );
            }

        } catch (Exception e) {
            CheckFreeUtil.warn( "*** CheckFreeDBAPI.getSrvrTIDBySrvrSubmitDate failed: "
                               + FFSDebug.stackTrace( e ) );
            
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            
            throw e;
        } finally {
            try {
                if (rset != null) rset.close();
            } catch (Exception e) {
            	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
                // ignore
            }
        }

        CheckFreeUtil.log( "CheckFreeDBAPI.getSrvrTIDBySrvrSubmitDate done, "
                           + "SrvrTimeStamp = " + srvrTimeStamp );
        
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return srvrTID;
    }

    // Get SrvrTID with given LocalPmtID
    public static String getSrvrTIDByLocalPmtID( String localPmtID,
                                                 FFSConnectionHolder dbh)
    throws Exception
    {
    	String method = "CheckFreeDBAPI.getSrvrTIDByLocalPmtID";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        CheckFreeUtil.log( "CheckFreeDBAPI.getSrvrTIDByLocalPmtID start, "
                           + "LocalPmtID = " + localPmtID );

        FFSResultSet rset = null;
        String srvrTID = null;
        try {
            Object[] args =
            {
                localPmtID
            };

            rset = DBUtil.openResultSet( dbh, SQL_GET_SRVRTID_WHERE_LOCALPMTID, args );
            if (rset.getNextRow()) {
                srvrTID = rset.getColumnString(1);
            } else {
                // This is for backward compatability
                rset = DBUtil.openResultSet( dbh, SQL_GET_SRVRTID_WHERE_SUBMITDATE, args );
                if (rset.getNextRow()) {
                    srvrTID = rset.getColumnString(1);
                }
            }
        } catch (Exception e) {
            CheckFreeUtil.warn( "*** CheckFreeDBAPI.getSrvrTIDByPmtID failed: "
                               + "LocalPmtID = " + localPmtID+" "+ FFSDebug.stackTrace( e ) );
            
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            
            throw e;
        } finally {
            try {
                if (rset != null) rset.close();
            } catch (Exception e) {
                // ignore
            	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            }
        }

        CheckFreeUtil.log( "CheckFreeDBAPI.getSrvrTIDByLocalPmtID done, "
                           + "LocalPmtID = " + localPmtID );
        
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        
        return srvrTID;
    }

    // insert a payment history record into the PmtHist table
    // given SrvrTID and CfPmtID
    public static void insertCfPmtIDIntoPmtHist( String srvrTID, String cfPmtID,
                                                 FFSConnectionHolder dbh )
    throws Exception
    {
    	String method = "CheckFreeDBAPI.insertCfPmtIDIntoPmtHist";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        CheckFreeUtil.log( "CheckFreeDBAPI.insertCfPmtIDIntoPmtHist start, " + "SrvrTID = " +
                           srvrTID + ", cfPmtID = " + cfPmtID);
        if (srvrTID == null ||  srvrTID.length() == 0) {
            String err = "CheckFreeDBAPI.insertCfPmtIDIntoPmtHist Invalid srvrTID: " + srvrTID +
                         " is passed in the echo file, this payment failed at CheckFree." +
                         " No record for this payment will be inserted in the history table";
            CheckFreeUtil.log(err , FFSConst.PRINT_ERR);
            throw new Exception(err);
        }
        if (cfPmtID == null ||  cfPmtID.length() == 0) {
            String err = "CheckFreeDBAPI.insertCfPmtIDIntoPmtHist Invalid CheckFree PmtId : " + cfPmtID +
                         " is passed in the echo file, this payment failed at CheckFree." +
                         " No record for this payment will be inserted in the history table";
            CheckFreeUtil.log(err , FFSConst.PRINT_ERR);
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new Exception(err);
        }

        try {
            // insert into PmtHist
            Object[] args = { cfPmtID, srvrTID};

            // Attempt to update the CFPmtID first, in case the SrvrTID is
            // already in the table but the CfPmtID is not
            int rows = DBUtil.executeStatement( dbh, SQL_UPDATE_SRVRSUBMITDATE_WHERE_SRVRTID, args );

            if (rows == 0) {
                // No rows were updated, try update in the reverse order
                // in case the CfPmtID is already in the table but the
                // SrvrTID is not
                args[0] = srvrTID;
                args[1] = cfPmtID;
                rows = DBUtil.executeStatement( dbh, SQL_UPDATE_SRVRTID_WHERE_SRVRSUBMITDATE, args );
                if (rows == 0) {
                    // This is for backward compatability
                    // No rows were updated, this is a new row.  Insert it.
                    rows = DBUtil.executeStatement( dbh, SQL_INSERT_SRVRTID_SRVRSUBMITDATE, args );
                }
            }
        } catch (Exception e) {
            CheckFreeUtil.warn( "*** CheckFreeDBAPI.insertCfPmtIDIntoPmtHist failed: "
                               + "SrvrTID = " +srvrTID + ", cfPmtID = " + cfPmtID+" "
                               + FFSDebug.stackTrace( e ) );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw e;
        }

        CheckFreeUtil.log( "CheckFreeDBAPI.insertCfPmtIDIntoPmtHist done, "
                           + "SrvrTID = " + srvrTID );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }

    /**
     * Insert the SrvrTID and CspPmtID into the PmtHist table.
     *
     * @param srvrTID  The srvrTID of the payment.
     * @param cspPmtID The cspPmtID of the payment.
     * @param dbh      The handle to the database connection.
     * @return The cspPmtID.
     * @exception Exception
     */
    public static String insertCspPmtIDIntoPmtHist( String srvrTID, String cspPmtID,
                                                    FFSConnectionHolder dbh )
    throws Exception
    {
    	String method = "CheckFreeDBAPI.insertCspPmtIDIntoPmtHist";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        FFSResultSet rset = null;

        CheckFreeUtil.log( "CheckFreeDBAPI.insertCspPmtIDIntoPmtHist start, " + "SrvrTID = " +
                           srvrTID + ", cspPmtID = " + cspPmtID);
        // added by omer
        if (srvrTID == null ||  srvrTID.length() == 0) {
            String err = "CheckFreeDBAPI.insertCspPmtIDIntoPmtHist Invalid srvrTID: " + srvrTID +
                         " No record for this payment will be inserted in the history table";
            CheckFreeUtil.log(err , FFSConst.PRINT_ERR);
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new Exception(err);
        }
        if (cspPmtID == null ||  cspPmtID.length() == 0) {
            String err = "CheckFreeDBAPI.insertCspPmtIDIntoPmtHist Invalid CSP PmtId : " + cspPmtID +
                         " No record for this payment will be inserted in the history table";
            CheckFreeUtil.log(err , FFSConst.PRINT_ERR);
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new Exception(err);
        }

        try {

            Object[] args1 = { srvrTID};
            // The below values are reserved for future sanity check
            //String cfPmtIDVal = null;

            rset = DBUtil.openResultSet( dbh,
                                         SQL_GET_LOCALPMTID_SRVRSUBMITDATE_WHERE_SRVRTID,
                                         args1 );

            if (rset.getNextRow()) {
                // This must be a resubmit event.
                // Use the original cspPPmtID
                cspPmtID = rset.getColumnString(DBConsts.BPW_PMTHIST_LOCALPMTID);

                // The below values are reserved for future sanity check
                /*cfPmtIDVal = rset.getColumnString(DBConsts.BPW_PMTHIST_SRVRSUBMITDATE);
                if (cfPmtIDVal != null &&
                    cfPmtIDVal.length() > 0) {
                    // The echo file of this payment has already been processed
                }*/
            } else {
                // insert into PmtHist
                Object[] args2 = { srvrTID, cspPmtID};

                DBUtil.executeStatement( dbh, SQL_INSERT_SRVRTID_LOCALPMTID, args2 );
            }

        } catch (Exception e) {
            CheckFreeUtil.log( "*** CheckFreeDBAPI.insertCspPmtIDIntoPmtHist failed: "
                               + "SrvrTID = " +srvrTID + ", cspPmtID = " + cspPmtID+" "
                               + FFSDebug.stackTrace( e ) );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw e;
        } finally {
            try {
                if ( rset!=null ) {
                    rset.close();
                    rset = null;
                }
            } catch (Exception e) {
                // ignore
            	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            }
        }

        CheckFreeUtil.log( "CheckFreeDBAPI.insertCspPmtIDIntoPmtHist done, "
                           + "SrvrTID = " + srvrTID );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return cspPmtID;
    }


    /**
     * Retrieve the last CspPmtId that was assigned. This is
     * obtained by finding the largest (String comparison)
     * LocalPmtId from the BPW_PmtHist database table. Returns
     * the value as a Calendar object. If no value is found,
     * return the current date/time in a Calendar object.
     *
     * @param dbh    Database connection holder to execute the database
     *               operation on.
     * @return Calendar object representing the more recent LocalPmtId
     *         found in the database. Will return today's date/time if
     *         no value is found.
     */
    public static Calendar getLastUsedCspPmtId(FFSConnectionHolder dbh)
    throws Exception
    {
        String methodName = "CheckFreeDBAPI.getLastUsedCspPmtId";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        
        CheckFreeUtil.log(methodName + " start.");

        // Create a Calendar object to store the results.
        Calendar localPmtIdCal = Calendar.getInstance();

        FFSResultSet rset = null;
        try {
            // Query the database for the most recent (largest)
            // LocalPmtId value.
            rset = DBUtil.openResultSet( dbh,
                                         SQL_GET_MOST_RECENT_LOCALPMTID,
                                         null );

            if (rset.getNextRow() == true) {
                // Grab the localPmtId value from the first row.
                // The format of localPmtId is "yyyyMMddHHmmssSSS000".
                // The SimpleDateFormat class cannot parse this format
                // because of the three extra zeros at the end of the String.
                // We will crop these three zeros (which were hardcoded
                // additions, anyways) and parse the cropped value.
                String localPmtId = rset.getColumnString(DBConsts.BPW_PMTHIST_LOCALPMTID);
                CheckFreeUtil.log(methodName +
                                  ": Seeding with localPmtId = " +
                                  localPmtId);

                // Crop to "yyyyMMddHHmmssSSS".
                String localPmtIdCrop = localPmtId.substring(0, 17);

                // Convert to a Calendar object via SimpleDateFormat => Date => Calendar.
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS");
                Date localPmtIdDate = sdf.parse(localPmtIdCrop);
                localPmtIdCal.setTime(localPmtIdDate);
            } else {
                // Just use the current time/date in the retVal calendar.
            }
        } catch (Exception e) {
            CheckFreeUtil.warn( "*** " + methodName + " failed: "
                               + FFSDebug.stackTrace(e) );
            PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
            throw e;
        } finally {
            try {
                if (rset != null) {
                    rset.close();
                    rset = null;
                }
            } catch (Exception e) {
                // ignore
            	PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
            }
        }

        CheckFreeUtil.log(methodName + " done.");
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
        return localPmtIdCal;
    }


    // update a payment history record into the PmtHist table
    public static void setPmtHist( TypePmtHistInfo info,
                                   FFSConnectionHolder dbh )
    throws Exception
    {
    	String method = "CheckFreeDBAPI.setPmtHist";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        CheckFreeUtil.log( "CheckFreeDBAPI.setPmtHist start, "
                           + "SrvrTimeStamp = " + info.SrvrTimeStamp );

        try {
            // insert into PmtHist
            Object[] args =
            {
                info.FileTrackID,
                info.PmtProcessedDate,
                getPmtType(info.PmtType.value()),
                getPmtMethod(info.PmtMethod.value()),
                getPmtTransCode(info.PmtTransCode.value()),
                info.CreditTraceNum,
                Integer.toString( info.CheckNum ),
                DBUtil.getCurrentLogDate(),
                info.SrvrTimeStamp  // This is the CFPmtID in the Echo file
            };

            DBUtil.executeStatement( dbh, SQL_UPDATE_PMTHIST, args );

        } catch (Exception e) {
            CheckFreeUtil.warn( "*** CheckFreeDBAPI.setPmtHist failed: "
                         + "SrvrTimeStamp = " + info.SrvrTimeStamp+" "+ FFSDebug.stackTrace( e ) );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw e;
        }

        CheckFreeUtil.log( "CheckFreeDBAPI.setPmtHist done, "
                           + "SrvrTimeStamp = " + info.SrvrTimeStamp );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }

    // get SrvrTID from PmtHist by payment details
    public static PmtInfo getPmtInfoByDetails(
                                             String custID,
                                             String payeeName,
                                             String payAcct,
                                             String amtStr,
                                             int routeID,
                                             String pmtDate,
                                             FFSConnectionHolder dbh )
    throws Exception
    {
    	String method = "CheckFreeDBAPI.getPmtInfoByDetails";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        CheckFreeUtil.log( "CheckFreeDBAPI.getPmtInfoByDetails start: "
                           + "customerID="+custID
                           + "; payeeName="+payeeName
                           + "; payeeAccount="+payAcct
                           + "; amount="+amtStr
                           + "; routeID="+routeID );

        String srvrTID = null;
        FFSResultSet rset = null;
        PmtInfo pmtInfo=null;

        // Ref: PmtInfo, DB table BPW_PmtInstruction. StartDate is of
        // format YYYYMMDD00. Therefore we need to multiply it by 100
        int startDate = Integer.parseInt( pmtDate )*100;

        // Remove last "0" in the amount string
        String amount = amtStr;
        if (amtStr.charAt(amtStr.length()-1) == '0') {
            amount = amtStr.substring(0, amtStr.length()-1);
        }

        try {
            Object[] args = { payeeName, custID, amount, new Integer( routeID), payAcct,
                new Integer( startDate ), new Integer( startDate+1 )};  // scheduled and immed pmts
            rset = DBUtil.openResultSet( dbh, SQL_SELECT_PAYMENT_BY_DETAILS, args );
            if (rset.getNextRow()) {
                pmtInfo = new PmtInfo();

                pmtInfo.SrvrTID = rset.getColumnString("SrvrTID");
                pmtInfo.FIID = rset.getColumnString("FIID");
                pmtInfo.CustomerID = rset.getColumnString("CustomerID");
                pmtInfo.PayeeID = rset.getColumnString("PayeeID");
                pmtInfo.PayeeListID = rset.getColumnInt("PayeeListID");
                pmtInfo.BankID = rset.getColumnString("BankID");
                pmtInfo.AcctDebitID = rset.getColumnString("AcctDebitID");
                pmtInfo.AcctDebitType = rset.getColumnString("AcctDebitType");
                pmtInfo.PayAcct = rset.getColumnString("PayAcct");
                pmtInfo.OriginatedDate = rset.getColumnString("DateCreate");
                pmtInfo.CurDef = rset.getColumnString("CurDef");
                pmtInfo.setAmt(rset.getColumnString("Amount"));
                pmtInfo.StartDate = rset.getColumnInt("StartDate");
                pmtInfo.Status = rset.getColumnString("Status");
                pmtInfo.LogID  = rset.getColumnString("LogID");
                pmtInfo.Memo = rset.getColumnString("Memo");
                pmtInfo.PaymentType = rset.getColumnString("PaymentType");

                HashMap extra = PmtExtraInfo.getHashMap(srvrTID, dbh);

                // get the payment invoice information if available
                // the PmtInvoice object will be saved in pmtInfo.extraFields.
                String pmtInvInfo = (String) extra.get(PmtInfo.EXTRAFIELDS_HASHKEY_INVOICE);

                if (pmtInvInfo != null) {
                    PmtInvoice pmtInv = new PmtInvoice();
                    pmtInv.parse(pmtInvInfo);
                    extra.remove(PmtInfo.EXTRAFIELDS_HASHKEY_INVOICE);
                    extra.put(PmtExtraInfo.NAME_INVOICE, pmtInv);
                }

                pmtInfo.extraFields = extra;

            } else {
                String err="Unable to find a payment record in DB. Search criteria:"
                           + "BPW_PmtInstruction.CustomerID="+custID
                           + "; BPW_Payee.PayeeName="+payeeName
                           + "; BPW_PmtInstruction.PayAcct="+payAcct
                           + "; BPW_PmtInstruction.Amount="+amtStr
                           + "; BPW_PmtInstruction.SrvrTID = " + srvrTID;
                PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
                throw new Exception( err );
            }

            // If rset still has a row, then search result is not unique
            if (rset.getNextRow()) {
                pmtInfo=null;
                String err="More than 1 payment record found in DBS Search criteria:"
                           + "BPW_PmtInstruction.CustomerID="+custID
                           + "; BPW_Payee.PayeeName="+payeeName
                           + "; BPW_PmtInstruction.PayAcct="+payAcct
                           + "; BPW_PmtInstruction.Amount="+amtStr
                           + "; BPW_PmtInstruction.SrvrTID = " + srvrTID;
                PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
                throw new Exception( err );
            }
        } catch (Exception e) {
            CheckFreeUtil.warn( "*** CheckFreeDBAPI.getPmtInfoByDetails failed: "
                               + FFSDebug.stackTrace( e ) );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw e;
        } finally {
            try {
                if (rset != null) rset.close();
            } catch (Exception e) {
            	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
                // ignore
            }
        }

        CheckFreeUtil.log( "CheckFreeDBAPI.getPmtInfoByDetails done: "
                           + "customerID="+custID
                           + "; payeeName="+payeeName
                           + "; payeeAccount="+payAcct
                           + "; amount="+amtStr
                           + "; routeID="+routeID
                           + "; SrvrTID = " + srvrTID, FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return pmtInfo;
    }

    /**
     * Find the correct PmtInfo using the cfPmtID. We will use the
     * BPW_PmtHist table to find the srvrTID and then use the srvrTID
     * to find the correct PmtInfo in the BPW_PmtInstruction table.
     */
    public static PmtInfo getPmtInfoByCFPmtID(
                                             String cfPmtID,
                                             String custID,
                                             String payeeName,
                                             String payAcct,
                                             String amtStr,
                                             int routeID,
                                             String pmtDate,
                                             FFSConnectionHolder dbh )
    throws Exception
    {
    	String method = "CheckFreeDBAPI.getPmtInfoByCFPmtID";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        CheckFreeUtil.log( "CheckFreeDBAPI.getPmtInfoByCFPmtID start: "
                           + "CheckFreePmtID="+cfPmtID
                           + "; customerID="+custID
                           + "; payeeName="+payeeName
                           + "; payeeAccount="+payAcct
                           + "; amount="+amtStr
                           + "; routeID="+routeID );

        FFSResultSet rset = null;
        PmtInfo pmtInfo=null;
        try {
            Object[] args = {
                cfPmtID
            };

            rset = DBUtil.openResultSet( dbh, SQL_SELECT_PAYMENT_BY_CFPMTID, args );
            if (rset.getNextRow()) {
                pmtInfo = new PmtInfo();

                pmtInfo.SrvrTID = rset.getColumnString("SrvrTID");
                pmtInfo.FIID = rset.getColumnString("FIID");
                pmtInfo.CustomerID = rset.getColumnString("CustomerID");
                pmtInfo.PayeeID = rset.getColumnString("PayeeID");
                pmtInfo.PayeeListID = rset.getColumnInt("PayeeListID");
                pmtInfo.BankID = rset.getColumnString("BankID");
                pmtInfo.AcctDebitID = rset.getColumnString("AcctDebitID");
                pmtInfo.AcctDebitType = rset.getColumnString("AcctDebitType");
                pmtInfo.PayAcct = rset.getColumnString("PayAcct");
                pmtInfo.OriginatedDate = rset.getColumnString("DateCreate");
                pmtInfo.CurDef = rset.getColumnString("CurDef");
                pmtInfo.setAmt(rset.getColumnString("Amount"));                
                pmtInfo.StartDate = rset.getColumnInt("StartDate");
                pmtInfo.Status = rset.getColumnString("Status");
                pmtInfo.LogID  = rset.getColumnString("LogID");
                pmtInfo.Memo = rset.getColumnString("Memo");
                pmtInfo.PaymentType = rset.getColumnString("PaymentType");

                HashMap extra = PmtExtraInfo.getHashMap(pmtInfo.SrvrTID, dbh);

                // get the payment invoice information if available
                // the PmtInvoice object will be saved in pmtInfo.extraFields.
                String pmtInvInfo = (String) extra.get(PmtInfo.EXTRAFIELDS_HASHKEY_INVOICE);

                if (pmtInvInfo != null) {
                    PmtInvoice pmtInv = new PmtInvoice();
                    pmtInv.parse(pmtInvInfo);
                    extra.remove(PmtInfo.EXTRAFIELDS_HASHKEY_INVOICE);
                    extra.put(PmtExtraInfo.NAME_INVOICE, pmtInv);
                }

                pmtInfo.extraFields = extra;

            } else {
                String err="Unable to find a payment record in DB. Search criteria:"
                           + "CheckFreePmtID="+cfPmtID
                           + ". Extra data: BPW_PmtInstruction.CustomerID="+custID
                           + "; BPW_Payee.PayeeName="+payeeName
                           + "; BPW_PmtInstruction.PayAcct="+payAcct
                           + "; BPW_PmtInstruction.Amount="+amtStr;
                PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
                throw new Exception( err );
            }

            // If rset still has a row, then search result is not unique
            if (rset.getNextRow()) {
                String err="More than 1 payment record found in DB. Search criteria:"
                           + "CheckFreePmtID="+cfPmtID
                           + ". Extra data: BPW_PmtInstruction.CustomerID="+custID
                           + "; BPW_Payee.PayeeName="+payeeName
                           + "; BPW_PmtInstruction.PayAcct="+payAcct
                           + "; BPW_PmtInstruction.Amount="+amtStr;
                PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
                throw new Exception( err );
            }
        } catch (Exception e) {
            CheckFreeUtil.warn( "*** CheckFreeDBAPI.getPmtInfoByCFPmtID failed: "
                               + FFSDebug.stackTrace( e ) );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw e;
        } finally {
            try {
                if (rset != null) {
                    rset.close();
                }
            } catch (Exception e) {
                // ignore
            	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            }
        }

        CheckFreeUtil.log( "CheckFreeDBAPI.getPmtInfoByCFPmtID done: "
                           + "CheckFreePmtID="+cfPmtID
                           + "; customerID="+custID
                           + "; payeeName="+payeeName
                           + "; payeeAccount="+payAcct
                           + "; amount="+amtStr
                           + "; routeID="+routeID,
                           FFSConst.PRINT_DEV );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return pmtInfo;
    }


    // insert a credit return record into the PmtHist table
    public static void updatePmtHist( TypeRetCreditDetail info,
                                      String srvrTID,
                                      FFSConnectionHolder dbh )
    throws Exception
    {
    	String method = "CheckFreeDBAPI.updatePmtHist";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        CheckFreeUtil.log( "CheckFreeDBAPI.updatePmtHist start, "
                           + "SrvrTID = " + srvrTID );

        // update PmtHist record -- PmtHist record will exist from PmtHistory
        //  file processing, so we just need an update
        try {
            Object[] args =
            {
                info.PmtDueDate,
                info.CheckNum,
                info.PmtMethod,
                info.ReturnDate,
                info.ReturnReason,
                info.CreditTraceNum,
                srvrTID
            };

            DBUtil.executeStatement( dbh, SQL_INSERT_CREDITRET, args );

        } catch (Exception e) {
            CheckFreeUtil.warn( "*** CheckFreeDBAPI.updatePmtHist failed: "
                    + "SrvrTID = " + srvrTID+" " + FFSDebug.stackTrace( e ) );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw e;
        }

        CheckFreeUtil.log( "CheckFreeDBAPI.updatePmtHist done, "
                           + "SrvrTID = " + srvrTID );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }


    // get a CustomerInfo object representing the database entry
    public static CustomerInfo getCustomerInfo( String custID, FFSConnectionHolder dbh )
    throws Exception
    {
    	String method = "CheckFreeDBAPI.getCustomerInfo";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        CheckFreeUtil.log( "CheckFreeDBAPI.getCustomerInfo start, "
                           + "ConsumerID = " + custID );

        CustomerInfo ci = Customer.getCustomerByID( custID, dbh );

        if (ci == null) {
            String msg = "Consumer ID not found";

            CheckFreeUtil.warn( "*** CheckFreeDBAPI.getCustomerInfo failed: "
                               + "Consumer ID "+custID+"not found" );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw new BPWException( msg );
        }

        CheckFreeUtil.log( "CheckFreeDBAPI.getCustomerInfo done, "
                           + "ConsumerID = " + custID );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return ci;
    }

    // update the status of a customer
    public static void updateCustomerStatusWithRouteID( String custID,
                                                        int routeID,
                                                        String status,
                                                        FFSConnectionHolder dbh )
    throws Exception
    {
    	String method = "CheckFreeDBAPI.updateCustomerStatusWithRouteID";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        CheckFreeUtil.log( "CheckFreeDBAPI.updateCustomerStatusWithRouteID start, "
                           + "ConsumerID = " + custID );

        CustomerRouteInfo crInfo = CustRoute.getCustomerRoute(custID, routeID, dbh);
        if (crInfo.Status.equals(DBConsts.CANC)) {
            CheckFreeUtil.warn( "updateCustomerStatusWithRouteID, cannot update CANC record,"
                                + "custID=" + custID + ",routeID=" + routeID );
        } else {
            if (Customer.updateCustomerStatusWithRouteID( custID, routeID, status, dbh ) <= 0) {
                String msg = "Cannot find ConsumerID";

                CheckFreeUtil.warn( "*** CheckFreeDBAPI.updateCustomerStatusWithRouteID failed: "
                                   + msg +"= " + custID);
                PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
                throw new BPWException( msg );
            }
        }

        CheckFreeUtil.log( "CheckFreeDBAPI.updateCustomerStatusWithRouteID done, "
                           + "ConsumeRID = " + custID );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }

    // remove a customer
    public static void deleteCustomerWithRouteID( String custID, int routeID, FFSConnectionHolder dbh )
    throws Exception
    {
    	String method = "CheckFreeDBAPI.deleteCustomerWithRouteID";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        CheckFreeUtil.log( "CheckFreeDBAPI.deleteCustomerWithRouteID start, "
                           + "ConsumerID = " + custID );

        try {
            CommonProcessor.deleteCustomerWithRouteID( dbh, custID, routeID );
        }catch (BPWException be) {
        	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw be;
        }catch (Exception e) {
            CheckFreeUtil.warn( "*** CheckFreeDBAPI.deleteCustomerWithRouteID failed: "
                    + "ConsumerID = " + custID +" "+ FFSDebug.stackTrace( e ) );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw e;
        }

        CheckFreeUtil.log( "CheckFreeDBAPI.deleteCustomerWithRouteID done, "
                           + "ConsumerID = " + custID );
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }

    private static String getPmtType(int val)
    throws BPWException
    {
        String res = null;
        try {
            // Use the MessageBroker generated utility classes
            // to get the correct String value from the enumeration
            // value.
            res = ValueSetPmtType.getValue(val);
        } catch (Throwable t) {
            // The was an error looking up the enumeration
            // value. Print out the Exception message and then
            // propagate the parameter value back in an Exception
            //  message.
            String msg = "Enumeration value (" + val
            + ") not found for Payment Type.";

            CheckFreeUtil.warn( "*** CheckFreeDBAPI.getPmtType failed: "
                               +msg+ t.getMessage());


            throw new BPWException( msg );
        }

        return res;
    }

    private static String getPmtMethod(int val)
    throws BPWException
    {
        String res = null;
        try {
            // Use the MessageBroker generated utility classes
            // to get the correct String value from the enumeration
            // value.
            res = ValueSetPmtMethod.getValue(val);
        } catch (Throwable t) {
            // The was an error looking up the enumeration
            // value. Print out the Exception message and then
            // propagate the parameter value back in an Exception
            //  message.
            String msg = "Enumeration value (" + val
            + ") not found for Payment Method.";

            CheckFreeUtil.warn( "*** CheckFreeDBAPI.getPmtMethod failed: "
                               +msg+ t.getMessage() );


            throw new BPWException( msg );
        }

        return res;
    }

    private static String getPmtTransCode(int val)
    throws BPWException
    {
        String res = null;
        try {
            // Use the MessageBroker generated utility classes
            // to get the correct String value from the enumeration
            // value.
            res = ValueSetPmtTransCode.getValue(val);
        } catch (Throwable t) {
            // The was an error looking up the enumeration
            // value. Print out the Exception message and then
            // propagate the parameter value back in an Exception
            //  message.

            String msg = "Enumeration value (" + val
            + ") not found for Payment Transaction Code.";

            CheckFreeUtil.warn( "*** CheckFreeDBAPI.getPmtTransCode failed: "
                               +msg+ t.getMessage() );


            throw new BPWException( msg );
        }
        return res;
    }
}
