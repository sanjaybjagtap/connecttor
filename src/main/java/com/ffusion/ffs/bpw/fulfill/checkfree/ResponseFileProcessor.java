// Copyright (c) 2002 Financial Fusion, Inc.
// All rights reserved.

package com.ffusion.ffs.bpw.fulfill.checkfree;

import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.PmtInfo;
import com.ffusion.ffs.bpw.interfaces.PmtTrnRslt;
import com.ffusion.ffs.bpw.master.BackendProcessor;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.TypeCspRecords;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.TypePmtHistInfo;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.TypePmtHistory;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.TypeRetCreditDetail;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.CheckFree.TypeSettlement;


public class ResponseFileProcessor {
    private static BackendProcessor _backendProcessor;
    private static int _batchSize ;
    protected static void init()
    {
        if (_backendProcessor == null) {
            _backendProcessor = new BackendProcessor();
        }
    }

    protected static void setBatchSize(int batchSize){
        _batchSize = batchSize;
    }
    /////////////////////////////////////////////////////////////////////////
    // Payment History response file
    /////////////////////////////////////////////////////////////////////////

    protected static boolean process( TypePmtHistory hist, FFSConnectionHolder dbh )
    throws Exception
    {
        boolean success = true;

        // check CSP ID in trailer
        if (!CheckFreeUtil.isCspIDEqual( hist.PHTrailer.CspID )) {
            throw new Exception( "Error in Payment History file: "
                                 + "Wrong CSP ID in trailer" );
        }

        int processedBatchesCount = 1; // number of batches processed up to now            
        int processedRecordsCount = 0; // number of records processed up to now            

        // process records
        if (hist.PmtHistInfoExists) {
            TypePmtHistInfo[] records = hist.PmtHistInfo;
              
            for (int i = 0; i < records.length; i++) {
                try {
                    processPmtHistInfo( records[ i ], dbh );
                    processedRecordsCount ++; // increase number of processed 
                    // records by one
                    //If we processed a batch size of records, commit the database
                    //transaction and reset the counter
                    if (processedRecordsCount >= _batchSize) {
                        dbh.conn.commit();

                        CheckFreeUtil.log("processSrvcTransRSFile(): Finished processing batch # " + 
                                           (processedBatchesCount++)+ ".Total number of " +
                                          "Payment History records processed: " + processedRecordsCount );
                        processedRecordsCount = 0; // reset the records counter
                    }
                } catch (ResponseRecordException e) {
                    CheckFreeUtil.warn( "Error in Payment History with "
                                        + "SrvrTID=" + e.getRecordID()
                                        + ": " + e.getMessage() );
                    success = false;
                }
            }
            processedBatchesCount = 1; 
        }

        return success;
    }

    // process a payment history record
    private static void processPmtHistInfo( TypePmtHistInfo record,
                                            FFSConnectionHolder dbh )
    throws Exception
    {
        // check that Csp ID matches
        if (!CheckFreeUtil.isCspIDEqual( record.CspID )) {
            String msg = "Wrong CSP ID";
            throw new ResponseRecordException( msg, record.SrvrTimeStamp );
        }

        // check the payment status
        if (!record.PmtStatus.equals( CheckFreeConsts.CF_STATUS_COMPLETED )) {
            String msg = "Unknown payment status";
            throw new ResponseRecordException( msg, record.SrvrTimeStamp );
        }

        // update the database
        try {
            String srvrTID = CheckFreeDBAPI.getSrvrTIDBySrvrSubmitDate( record.SrvrTimeStamp, dbh );
            PmtInfo info = CheckFreeDBAPI.getPmtInfo( srvrTID, dbh );

            // add to the CHKF_PmtHist table
            CheckFreeDBAPI.setPmtHist( record, dbh );

            // check to see if the payment needs updating or if it has failed
            if (info.Status.equals( DBConsts.PROCESSEDON )) {
                return;
            } else if (info.Status.equals( DBConsts.FAILEDON )) {
                String msg = "Payment already marked as failed";
                throw new ResponseRecordException( msg, srvrTID );
            }

            // update the payment to "PROCESSEDON"
            PmtTrnRslt rslt = new PmtTrnRslt( info.CustomerID,
                                              info.SrvrTID,
                                              DBConsts.STATUS_OK,
                                              CheckFreeConsts.MSG_STATUS_OK,
                                              info.ExtdPmtInfo );
            rslt.logID = info.LogID;
            _backendProcessor.processOnePmtRslt( rslt, info.LogID, info.Status, info.FIID, dbh );
        } catch (Exception e) {
            throw new ResponseRecordException( e.getLocalizedMessage(), record.SrvrTimeStamp );
        }
    }


    /////////////////////////////////////////////////////////////////////////
    // Settlement response file
    /////////////////////////////////////////////////////////////////////////
    // NOTE: We require that we receive PmtHistory for a payment BEFORE
    //  receiving a Settlement record.  If not, there would be no way to
    //  find the credit trace number, hence no way to find the SrvrTID!

    protected static boolean process( TypeSettlement slmt, FFSConnectionHolder dbh )
    throws Exception
    {
        boolean success = true;
        TypeCspRecords record = null;

        // process each collection of Csp records
        for (int i = 0; i < slmt.CspRecords.length; i++) {
            record = slmt.CspRecords[ i ];

            // check Csp IDs
            if (!CheckFreeUtil.isCspIDEqual( record.CspRecord.CspID )) {
                throw new Exception( "Error in Settlment file: "
                                     + "Wrong CSP ID in header" );
            } else if (!CheckFreeUtil.isCspIDEqual( record.CspSummary.CspID )) {
                throw new Exception( "Error in Settlement file: "
                                     + "Wrong CSP ID in trailer" );
            }
            int processedBatchesCount = 1; // number of batches processed up to now            
            int processedRecordsCount = 0; // number of records processed up to now            

            // process each returned credit record
            if (record.RetCreditDetailExists) {

                // Retrieve some configurable properties.
                String s = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_IMPORT_REPEATABLE,
                                                      DBConsts.FALSE );
                boolean allowRepeat = s.equalsIgnoreCase( DBConsts.TRUE );
                String cfSettlementVersion = CheckFreeUtil.getProperty(DBConsts.CHECKFREE_SETTLEMENT_VERSION,
                                                                       "");

                for (int j = 0; j < record.RetCreditDetail.length; j++) {
                    try {
                        processRetCreditDetail( record.RetCreditDetail[ j ],
                                                allowRepeat,
                                                cfSettlementVersion,
                                                dbh );
                        
                        processedRecordsCount ++; // increase number of processed 
                        // records by one
                        //If we processed a batch size of records, commit the database
                        //transaction and reset the counter
                        if (processedRecordsCount >= _batchSize) {
                            dbh.conn.commit();

                            CheckFreeUtil.log("processSrvcTransRSFile(): Finished processing batch # " + 
                                               (processedBatchesCount++)+ ".Total number of " +
                                              "Settlement records processed: " + processedRecordsCount );
                            processedRecordsCount = 0; // reset the records counter
                        }
                    } catch (ResponseRecordException e) {
                        CheckFreeUtil.warn( "Error processing return credit record No."
                                            + (i+1)
                                            + ": " + e.getMessage() );
                        success = false;
                        continue;
                    }
                }
                processedBatchesCount = 1;
            }
        }

        return success;
    }

    // process a returned credit detail record
    private static void processRetCreditDetail( TypeRetCreditDetail record,
                                                boolean allowRepeat,
                                                String cfSettlementVersion,
                                                FFSConnectionHolder dbh )
    throws Exception
    {
        try {
            // Convert payment amount to a string containing decimal point
            String val  = CFConvert_getValue(record.OrigPmtAmt.substring(12,13));
            int sign = CFConvert_getSign(record.OrigPmtAmt.substring(12,13));

            long amountLong = Long.parseLong(record.OrigPmtAmt.substring(0,11)) * sign;
            String amountStr = Long.toString(amountLong) + "."
                               + record.OrigPmtAmt.substring(11,12) + val;
            //com.ffusion.ffs.util.FFSDebug.log("record.OrigPmtAmt="+record.OrigPmtAmt+",amountStr="+amountStr);

            // remove leading 0 for subscriber ID
            String subIDStr = record.SubID;
            for (int i=0; i<subIDStr.length(); i++) {
                if (subIDStr.charAt(i) == '0') continue;
                else {
                    subIDStr = record.SubID.substring(i);
                    break;
                }
            }

            // Get payment info. Two algorithms for finding the payment info.
            // 1. CheckFree v0402: Search for a unique match using a combination
            //                     of data (customer ID, payeeName, payeeAcct, Amount).
            // 2. CheckFree v0403: Use newly supplied CheckFree Payment ID
            //                     (equal to CheckFree Server timestamp).
            PmtInfo info = null;
            if (cfSettlementVersion.equalsIgnoreCase("0402") == true) {
                // Search using data combination.
                info = CheckFreeDBAPI.getPmtInfoByDetails(
                                                         subIDStr,
                                                         record.PayeeName,
                                                         record.SubPayeeAcct,
                                                         amountStr,
                                                         CheckFreeHandler.getRouteID(),
                                                         record.PmtDueDate,
                                                         dbh );
            } else {
                // Search using CheckFree Payment ID.
                info = CheckFreeDBAPI.getPmtInfoByCFPmtID(
                                                         record.PaymentId,
                                                         subIDStr,
                                                         record.PayeeName,
                                                         record.SubPayeeAcct,
                                                         amountStr,
                                                         CheckFreeHandler.getRouteID(),
                                                         record.PmtDueDate,
                                                         dbh );
            }

            // update payment history table
            CheckFreeDBAPI.updatePmtHist( record, info.SrvrTID, dbh );

            // check to see if the payment needs updating
            if ((allowRepeat == false) &&
                (info.Status.equals(DBConsts.FAILEDON) == true)) {
                CheckFreeUtil.warn( "Duplicate failed payment, SrvrTID="
                                    + info.SrvrTID );
                return;
            }

            // update the payment to "FAILEDON"
            PmtTrnRslt rslt = new PmtTrnRslt( info.CustomerID,
                                              info.SrvrTID,
                                              DBConsts.STATUS_GENERAL_ERROR,
                                              record.ReturnReason,
                                              info.ExtdPmtInfo );
            rslt.logID = info.LogID;
            _backendProcessor.processOnePmtRslt( rslt, info.LogID, info.Status, info.FIID, dbh );

        } catch (Exception e) {
            throw new ResponseRecordException( e.getMessage(),
                                               record.CreditTraceNum );
        }
    }

    // CheckFree specific conversion
    // do a number conversion from the amount in the settement file
    // get the sign
    private static int CFConvert_getSign( String c)
    throws Exception
    {
        int sign = 0;
        if (c.equals("{")) {
            sign = 1;
        } else if (c.equals("}")) {
            sign = -1;
        } else if (c.equals("A")) {
            sign = 1;
        } else if (c.equals("B")) {
            sign = 1;
        } else if (c.equals("C")) {
            sign = 1;
        } else if (c.equals("D")) {
            sign = 1;
        } else if (c.equals("E")) {
            sign = 1;
        } else if (c.equals("F")) {
            sign = 1;
        } else if (c.equals("G")) {
            sign = 1;
        } else if (c.equals("H")) {
            sign = 1;
        } else if (c.equals("I")) {
            sign = 1;
        } else if (c.equals("J")) {
            sign = -1;
        } else if (c.equals("K")) {
            sign = -1;
        } else if (c.equals("L")) {
            sign = -1;
        } else if (c.equals("M")) {
            sign = -1;
        } else if (c.equals("N")) {
            sign = -1;
        } else if (c.equals("O")) {
            sign = -1;
        } else if (c.equals("P")) {
            sign = -1;
        } else if (c.equals("Q")) {
            sign = -1;
        } else if (c.equals("R")) {
            sign = -1;
        } else
            throw new Exception("CFConvert_getSign: not supported conversion value="+c);
        return sign;
    }

    // CheckFree specific conversion
    // do a number conversion from the amount in the settement file
    // get the value
    private static String CFConvert_getValue( String c)
    throws Exception
    {
        String value = "0";
        if (c.equals("{")) {
            value = "0";
        } else if (c.equals("}")) {
            value = "0";
        } else if (c.equals("A")) {
            value = "1";
        } else if (c.equals("B")) {
            value = "2";
        } else if (c.equals("C")) {
            value = "3";
        } else if (c.equals("D")) {
            value = "4";
        } else if (c.equals("E")) {
            value = "5";
        } else if (c.equals("F")) {
            value = "6";
        } else if (c.equals("G")) {
            value = "7";
        } else if (c.equals("H")) {
            value = "8";
        } else if (c.equals("I")) {
            value = "9";
        } else if (c.equals("J")) {
            value = "1";
        } else if (c.equals("K")) {
            value = "2";
        } else if (c.equals("L")) {
            value = "3";
        } else if (c.equals("M")) {
            value = "4";
        } else if (c.equals("N")) {
            value = "5";
        } else if (c.equals("O")) {
            value = "6";
        } else if (c.equals("P")) {
            value = "7";
        } else if (c.equals("Q")) {
            value = "8";
        } else if (c.equals("R")) {
            value = "9";
        } else
            throw new Exception("CFConvert_getValue: not supported conversion value="+c);
        return value;
    }

}
