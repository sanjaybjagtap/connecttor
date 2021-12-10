
// Copyright (c) 2002 Financial Fusion, Inc.  All Rights Reserved.

package com.ffusion.ffs.bpw.fulfill.rpps;

import java.util.ArrayList;

import com.ffusion.ffs.bpw.achagent.ACHAgent;
import com.ffusion.ffs.bpw.audit.FMLogAgent;
import com.ffusion.ffs.bpw.db.PmtInstruction;
import com.ffusion.ffs.bpw.db.RPPSDB;
import com.ffusion.ffs.bpw.db.RPPSFI;
import com.ffusion.ffs.bpw.handler.OSGIUtil;
import com.ffusion.ffs.bpw.interfaces.ACHConsts;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.PmtInfo;
import com.ffusion.ffs.bpw.interfaces.PmtTrnRslt;
import com.ffusion.ffs.bpw.interfaces.RPPSFIInfo;
import com.ffusion.ffs.bpw.interfaces.RPPSPmtFileInfo;
import com.ffusion.ffs.bpw.interfaces.RPPSPmtInfo;
import com.ffusion.ffs.bpw.master.BackendProcessor;
import com.ffusion.ffs.bpw.util.ACHAdapterUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.RPPSMsgSet.TypeBatchControlRecord;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.RPPSMsgSet.TypeBatchHeaderRecord;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.RPPSMsgSet.TypeCSPConfirmEntryDetailRecord;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.RPPSMsgSet.TypeCSPReturnEntryDetailAddendumRecord;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.RPPSMsgSet.TypeFileControlRecord;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.RPPSMsgSet.TypeFileHeaderRecord;
import com.ffusion.util.beans.filemonitor.FMLogRecord;
import com.sap.banking.common.interceptors.PerfLoggerUtil;
import com.sap.banking.io.beans.File;
import com.sap.banking.io.provider.interfaces.FileHandlerProvider;

/**
 * Process Respones files from RPPS network: Confirmation, Return files.
 */
public class RPPSConfirmRtnFileHandlerImpl implements FFSConst, RPPSConsts, BPWResource {


    private String  _fiId           = null;
    private String  _fiRPPSId       = null;

    private String _importDir       = RPPSConsts.DEFAULT_IMPORT_DIR;
    private String _errorDir        = RPPSConsts.DEFAULT_ERROR_DIR;


    /**
     * false, we only can update pmt status when the
     * original status is BATCH_INPROCESS
     * true, we can overwrite original status
     */
    private boolean _allowRepeatable = false;

    // The string which holds current record in the response file
    private String _currRecordStr   = null;

    // Payment files whose result is included in this confirmation file
    // and are partially accepted
    private ArrayList _pmtPartiallyAcceptedFileIds;

    // non static
    private BackendProcessor _backendProcessor   = null;


    public RPPSConfirmRtnFileHandlerImpl ( String fiId )
    throws FFSException
    {

        _fiId = fiId;

        // reset fi rpps id
        _fiRPPSId = null;

        //
        // Get import dir and error dir from BPW Server properties
        //
        _importDir  = RPPSUtil.getProperty( DBConsts.RPPS_IMPORT_DIR,
                                            RPPSConsts.DEFAULT_IMPORT_DIR );
        _errorDir   = RPPSUtil.getProperty( DBConsts.RPPS_ERROR_DIR,
                                            RPPSConsts.DEFAULT_ERROR_DIR );



        String repeatable = RPPSUtil.getProperty( DBConsts.RPPS_IMPORT_REPEATABLE, DBConsts.FALSE );

        _allowRepeatable = repeatable.equalsIgnoreCase( DBConsts.TRUE );

        _backendProcessor = new BackendProcessor();
    }

    /**
     * Processes all the files in rpps.import.dir + "confirmation" folder.
     * Updates payments' status according the information in
     * these files. After these files are processed
     * they are deleted.
     *
     * @param dbh    - Database connection holder. Should pass any open TX in.
     *               This conneciton is committed before it returns.
     * @exception FFSException
     */
    public void processResponseFiles(FFSConnectionHolder dbh )
    throws FFSException
    {
        String mName = "RPPSConfirmRtnFileHandlerImpl.processResponseFiles: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(mName,start);
        
        FFSDebug.log( mName + "RPPS Connector start checking for Confirmation & Return files", FFSDebug.PRINT_DEV);



        if ( _fiRPPSId == null ) {
            RPPSFIInfo rppsFIInfo =  RPPSFI.getRPPSFIInfoByFIId( dbh, _fiId );

            // rppsFIInfo has to exist
            if ( ( rppsFIInfo == null ) || ( rppsFIInfo.getStatusCode() != DBConsts.SUCCESS )) {
                String errMsg = mName + "This FI does have RPPS information. FI id: " + _fiId;
                FFSDebug.log( errMsg, FFSDebug.PRINT_ERR);
                PerfLoggerUtil.stopPerfLogging(mName, start, uniqueIndex);
                throw new FFSException( errMsg );
            }

            _fiRPPSId = rppsFIInfo.getFiRPPSId();
        }

        // get file dirs
        File confirmDir = new File( _importDir + File.separator + RPPSConsts.DIR_CONFIRMATION );
        confirmDir.setFileHandlerProvider(RPPSUtil.getFileHandlerProvider());
        try {

            FFSDebug.log( mName + "check file: " + confirmDir.getCanonicalPath(), FFSDebug.PRINT_DEV );

            // Get Confirmation & Return file from rpps.import.dir + confirmation folder
            File[] confirmFiles = confirmDir.listFiles();

            if ( ( confirmFiles == null) || ( confirmFiles.length == 0 ) ) {

                // no file in  confirmation dir
                FFSDebug.log( mName + "No Confirmation & Return file." + confirmDir.getCanonicalPath(), FFSDebug.PRINT_DEV );

            } else {

                // Payment files whose result is included in this confirmation file
                // and are partially accepted
                _pmtPartiallyAcceptedFileIds = new ArrayList();

                // process  confirmation files one by one
                for ( int i = 0; i < confirmFiles.length; i++ ) {

                    if ( confirmFiles[ i ].isDirectory() ) {
                        // skip directories
                        continue;
                    } else {

                        this.processOneRespFile( dbh, confirmFiles[i].getCanonicalPath() );
                    }
                }

                // No news is good news,
                // update the payments' status to be SUCCESS
                // who are included in the three fileIds lists and whose
                // status have not be touched.
                processPartiallySucceededPmtFile( dbh );

            }
        } catch ( Exception e ) {
            FFSDebug.log(e, mName + "RPPS Connector failed to process Confirmation & Return files.", FFSDebug.PRINT_ERR );
            PerfLoggerUtil.stopPerfLogging(mName, start, uniqueIndex);
            throw new FFSException(e.toString());
        }

        FFSDebug.log(mName + "RPPS Connector finished checking for Confirmation & Return files", FFSDebug.PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(mName, start, uniqueIndex);

    }

    /**
     * Process one Confirmation & Return file.
     *
     * @param dbh
     * @param fileName
     * @exception FFSException
     */
    private void processOneRespFile( FFSConnectionHolder dbh, String fileName )
    throws FFSException
    {
        String mName = "RPPSConfirmRtnFileHandlerImpl.processOneRespFile: ";

        int result = RPPSConsts.RESP_FILE_SUCCESS;

        // reset _currRecordStr
        _currRecordStr = null;

        FFSDebug.log( mName + "start ... fileName: " + fileName, FFSDebug.PRINT_DEV );
        RPPSFileTokenizer fileTokenizer = null;
        try {
            // Open this file
            fileTokenizer = new RPPSFileTokenizer( fileName );

            // Read first line. This line could be file header or NO DATA indicator
            if ( fileTokenizer.hasMoreTokens() == true ) {

                _currRecordStr = fileTokenizer.nextToken();

                if ( _currRecordStr.indexOf( RPPSConsts.RESP_FILE_NO_DATA_INDICATOR ) != -1 ) {
                    // this line is NO DATA record stop;
                    FFSDebug.log( mName + " The response file is 'NO DATA' file. File name: "
                                  + fileName, FFSDebug.PRINT_WRN);

                    // Skip NO DATA file
                    return;

                }

                // The the first line is file header
                // 1. Process file header;
                TypeFileHeaderRecord fHeader = parseFileHeader( _currRecordStr );

                // remove check digit
                String immediateDest = RPPSUtil.parseRPPSId(fHeader.Immediate_Destination);

                // check whether this confirmation file should be processed FOR _fiId
                if ( this._fiRPPSId.compareTo( immediateDest ) != 0) {
                    FFSDebug.log( mName + " The response file does not belong to this FI. "
                                  + " FI RPPS id: " + _fiRPPSId, FFSDebug.PRINT_WRN );
                    FFSDebug.log( mName + " Skip this file. "
                                  + " File name" + fileName + ".", FFSDebug.PRINT_WRN );

                    // Skip file which does not belong to this FI
                    return;
                }

                // Really start to process this file

                // Log to File Monitor Log
                // We pass in null value for db connection,
                // then a new db connection will be used
                // for this log and be committed right away
                FMLogAgent.writeToFMLog(null,
                                        DBConsts.BPW_RPPS_FILETYPE_ORIGCONFIRMRTN,
                                        fileName,
                                        DBConsts.RPPS,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_IN_PROCESS);

                // load first batch header
                // batch header must exist
                if ( fileTokenizer.hasMoreTokens() == true ) {

                    // 2. Process batches
                    _currRecordStr = fileTokenizer.nextToken();

                    //
                    result = processBatches( dbh, fileTokenizer );

                    if ( result == RPPSConsts.RESP_FILE_SUCCESS ) {

                        // 3. Process file control
                        parseFileControl( _currRecordStr );

                    }
                } else {
                    result = RPPSConsts.RESP_FILE_ERROR_MISSING_BATCH_HEADER;
                }

            } else {
                // Invalid Confirmation & return file
                result = RPPSConsts.RESP_FILE_ERROR_MISSING_FILE_HEADER;
            }


            if ( fileTokenizer != null ) {
                fileTokenizer.close();
            }

            if ( result != RPPSConsts.RESP_FILE_SUCCESS ) {

                // Log to File Monitor Log
                // We pass in null value for db connection,
                // then a new db connection will be used
                // for this log and be committed right away
                // The STATUS_COMPLETE log that follows this log is done inside
                // processInvalidRespFile method.
                FMLogAgent.writeToFMLog(null,
                                        DBConsts.BPW_RPPS_FILETYPE_ORIGCONFIRMRTN,
                                        fileName,
                                        DBConsts.RPPS,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_FAILED);

                this.processInvalidRespFile(fileName, result );

            } else {

                // Log to File Monitor Log
                FMLogAgent.writeToFMLog(dbh,
                                        DBConsts.BPW_RPPS_FILETYPE_ORIGCONFIRMRTN,
                                        fileName,
                                        DBConsts.RPPS,
                                        DBConsts.BPTW,
                                        FMLogRecord.STATUS_COMPLETE);

                // remove this file
                File respFile = new File(  fileName );
                respFile.setFileHandlerProvider(RPPSUtil.getFileHandlerProvider());
                respFile.delete();

            }

        } catch ( Exception e ) {
            if ( fileTokenizer != null ) {
                fileTokenizer.close();
            }
            FFSDebug.log( mName + "failed: " + e.toString(), FFSDebug.PRINT_ERR );

            // Log to File Monitor Log
            // We pass in null value for db connection,
            // then a new db connection will be used
            // for this log and be committed right away
            // The STATUS_COMPLETE log that normally follows
            // this log is not logged in this case
            FMLogAgent.writeToFMLog(null,
                                    DBConsts.BPW_RPPS_FILETYPE_ORIGCONFIRMRTN,
                                    fileName,
                                    DBConsts.RPPS,
                                    DBConsts.BPTW,
                                    FMLogRecord.STATUS_FAILED);
        }

        // reset _currRecordStr
        _currRecordStr = null;

        FFSDebug.log( mName + "done. result: " + result, FFSDebug.PRINT_DEV );
    }

    /**
     * Process all the high-level summary batches and other normal batches
     *
     * @param dbh
     * @param fileTokenizer
     * @return SUCCESS: It is valid response file so far, false, not a valid
     * @exception FFSException
     */
    private int processBatches( FFSConnectionHolder dbh,
                                RPPSFileTokenizer fileTokenizer )
    throws FFSException
    {
        String mName = "RPPSConfirmRtnFileHandlerImpl.processBatches: ";
        FFSDebug.log( mName + "start... ", FFSDebug.PRINT_DEV );

        // process high-level summary batches
        int result = RPPSConsts.RESP_FILE_SUCCESS;
        int recordType = 0;

        // batch header has been loaded by caller
        while ( true ) {

            // This header could be High-level summary batch header
            TypeBatchHeaderRecord bHeader = parseBatchHeader( _currRecordStr );

            // check If this batch header includes "ACCEPT FIL", nor "REJECT FIL"
            if ( ( bHeader.Entry_Description.compareTo( RPPSConsts.RESP_FILE_ACCEPT_FILE ) != 0 )
                 && ( bHeader.Entry_Description.compareTo( RPPSConsts.RESP_FILE_REJECT_FILE ) != 0 ) ) {

                // Finish all the summary batches
                // It is not a summary batch
                // it is normal reject/return batch
                result = processNormalBatches( dbh, fileTokenizer );

                break;
            } else {

                // try to match sum batch ( or tow sum batches ) to a pmt file
                result = processOneSumBatchPmtFileMatch( dbh, fileTokenizer );
                // end process one payment file's high level summary batch

                if ( result == RPPSConsts.RESP_FILE_SUCCESS ) {
                    // Read next record, it must exist

                    // it could be 1. : Batch header ( Summary ) - loop
                    //          2. : Batch header ( normal ) - loop
                    //          3. : File control header - Exit

                    // load first batch header
                    if ( fileTokenizer.hasMoreTokens() == true ) {
                        _currRecordStr = fileTokenizer.nextToken();
                    } else {
                        result =  RPPSConsts.RESP_FILE_ERROR_MISSING_FILE_CONTROL;
                        // it is invalid file, stop processing it
                        break;
                    }

                    recordType = ACHAgent.getRecordType(_currRecordStr );
                    if ( recordType == ACHConsts.BATCH_HEADER) {
                        // a new batch, could be summary or normal
                        continue;

                    } else if ( recordType == ACHConsts.FILE_CONTROL ) {
                        // reaches the end of file
                        // this method does handler this
                        // calller needs to handler file control
                        break;

                    } else {
                        result = RPPSConsts.RESP_FILE_ERROR_MISSING_FILE_CONTROL;
                        break;
                    }
                } else {
                    // it is invalid file, stop processing it
                    break;
                }
            } // batch header is sum batch

        }// while
        // end process all the payment files' high level summary batches and normal batches

        // COMMIT here to avoid big TX
        dbh.conn.commit();
        // File control record has been loaded

        FFSDebug.log( mName + "done. result: " + result, FFSDebug.PRINT_DEV );
        return result;
    }


    /**
     * It is summary batch header
     * find FileMap record according this batch header/control
     * or this and next batch header/control
     *
     *
     * For each payment file, there are one or two high level summary batches in a confirmation file.
     * 1.      If there is one, this batch must be one of the following cases:
     * a.      Include ACCEPT FIL and the entry count and total debit/credit
     *          amount must be same as this payment file.
     *          (If no payments rejected then this scenario is affirmative.
     *          If there are rejects then the high level reporting also includes the
     *          next batch as REJECT FIL.)
     *
     *          Constant: RPPSConsts.RESP_FILE_WHOLE_FILE_ACCEPTED;
     *
     * b.      Include REJECT FIL and the entry count and total debit/credit amount
     *          must be same this payment file. Error code is also set.
     *          (After the high-level ACCEPT FIL and REJECT FIL all of the rejects
     *          are listed with error codes.  The total of the nine record or file
     *          trailer will equal the number on the REJECT FIL totals)
     *
     *          Constant: RPPSConsts.RESP_FILE_WHOLE_FILE_REJECTED;
     *
     * 2.      If there are two, they must be two continuously batches.
     *          These two batches
     * i.      First batch includes ACCEPT FIL.
     * ii.     Second batch includes REJECT FIL. Error code is not set
     * iii.    The sum of these two batches' total entry count, debit amount, credit amount must match this payments file.
     *
     *          Constant: RPPSConsts.RESP_FILE_PARTIAL_FILE_ACCEPTED;
     *
     * @param dbh
     * @param fileTokenizer
     * @return
     * @exception FFSException
     */
    private int processOneSumBatchPmtFileMatch( FFSConnectionHolder dbh, RPPSFileTokenizer fileTokenizer )
    throws FFSException
    {

        String mName = "RPPSConfirmRtnFileHandlerImpl.processOneSumBatchPmtFileMatch: ";
        FFSDebug.log( mName + "start... ", FFSDebug.PRINT_DEV );

        int result = RPPSConsts.RESP_FILE_SUCCESS;
        // We have read a batch header already
        // The first batch's header has been loaded

        int recordType = 0;

        // Parse batch header
        TypeBatchHeaderRecord bHeader = parseBatchHeader( _currRecordStr );
        // This file includes High level Summary batch header
        // Read following record;
        // this line must be batch control
        // if this record is not batch control
        // invalid confirmation file
        TypeBatchControlRecord bControl = readBatchControl( fileTokenizer );
        if ( bControl == null ) {
            // Invalid Confirmation & return file
            return RPPSConsts.RESP_FILE_ERROR_MISSING_BATCH_CONTROL;
        }

        // found high level batch header and batch control
        // Process batch header and batch control

        RPPSPmtFileInfo pmtFileInfo = RPPSDB.getRPPSPmtFileMap(dbh,
                                                               _fiId,
                                                               bHeader.Transmission_Date,
                                                               bControl.Entry_Addenda_Count6,
                                                               bControl.Total_Debits,
                                                               bControl.Total_Credits );


        if ( ( pmtFileInfo != null )
             && ( pmtFileInfo.getStatusCode() == DBConsts.SUCCESS ) ) {

            // found a matched pmt file

            if ( bHeader.Entry_Description.compareTo( RPPSConsts.RESP_FILE_ACCEPT_FILE ) == 0 ) {
                // it is 1.a case {

                // the whole file is accepted
                processPmtsInOneFile( dbh,
                                      pmtFileInfo,
                                      DBConsts.STATUS_OK,
                                      DBConsts.MSG_STATUS_OK );


            } else {
                // it is 1.b case
                // the whole file is rejected
                processPmtsInOneFile( dbh,
                                      pmtFileInfo,
                                      DBConsts.STATUS_GENERAL_ERROR,
                                      RPPSUtil.getRejectMsg(bHeader.Error_Code)  );

            }
        } else { // can not find pmt file map
            //
            // possible
            //  1. partially accepted, then there must be another batch
            //  2. can not find pmt file for this summary batch at all,
            //      because this file is invalid, or database is corrupted

            // read further check next record
            if ( fileTokenizer.hasMoreTokens() != true ) {
                return RPPSConsts.RESP_FILE_ERROR_MISSING_FILE_CONTROL;
            }

            // there is one more record
            _currRecordStr = fileTokenizer.nextToken();

            recordType = ACHAgent.getRecordType(_currRecordStr );

            if ( recordType != ACHConsts.BATCH_HEADER) {

                // no more batchs after this sum batch
                FFSDebug.log( mName + "Can not find payment file map from high-level summary batches. Maybe BPW database has been corrupted or this confirmation file is invalid. ", FFSDebug.PRINT_ERR );
                FFSDebug.log( mName + "Batch number: " + bHeader.Batch_Number, FFSDebug.PRINT_ERR );

                // !!! We could skip these two batch and continue with
                // the rest batches here.
                // For this release, we consider it is an invalid response file
                return RPPSConsts.RESP_FILE_ERROR_CANNOT_FIND_PAYMENT_FILE_MAP;

            }

            // The current record is batch header

            // Now we know there is another batch following this batch
            // we assume the pmt file is partially accepted
            // HOWEVER, if the first sum batch is corrupted, the second
            // batch (no matter it is sum or not) will be affected
            // (possible be tossed)


            TypeBatchHeaderRecord bHeader2 = parseBatchHeader( _currRecordStr );
            if ( bHeader == null ) {
                // If it is not batch header, INVALID Confirmation file STOP
                return RPPSConsts.RESP_FILE_ERROR_MISSING_BATCH_HEADER;
            }

            // check whether first batch and second have same effective date
            if ( bHeader2.Transmission_Date.compareTo( bHeader.Transmission_Date ) != 0 ) {
                // first batch and second batch are not for same date,
                // this means the first batch can not find pmt file,
                // invalid file

                // no more batchs after this sum batch
                FFSDebug.log( mName + "Can not find payment file map from high-level summary batches. Maybe BPW database has been corrupted or this confirmation file is invalid. ", FFSDebug.PRINT_ERR );
                FFSDebug.log( mName + "Batch number: " + bHeader.Batch_Number, FFSDebug.PRINT_ERR );

                // !!! We could skip these two batch and continue with
                // the rest batches here.
                // For this release, we consider it is an invalid response file
                return RPPSConsts.RESP_FILE_ERROR_CANNOT_FIND_PAYMENT_FILE_MAP;
            }
            // read batch control
            TypeBatchControlRecord bControl2 = readBatchControl( fileTokenizer );
            if ( bControl == null ) {
                // Invalid Confirmation & return file
                // If it is not batch control, INVALID Confirmation file STOP
                return RPPSConsts.RESP_FILE_ERROR_MISSING_BATCH_CONTROL;
            }
            // Process these two batches, we must be able to find a payment file
            // which matches  trans date, entry count, debit, credit
            pmtFileInfo = RPPSDB.getRPPSPmtFileMap(dbh,
                                                   _fiId,
                                                   bHeader.Transmission_Date,
                                                   bControl.Entry_Addenda_Count6 + bControl2.Entry_Addenda_Count6,
                                                   bControl.Total_Debits + bControl2.Total_Debits,
                                                   bControl.Total_Credits + bControl2.Total_Credits );

            if ( ( pmtFileInfo != null ) && ( pmtFileInfo.getStatusCode() == DBConsts.SUCCESS ) ) {

                // found a matched pmt file

                // we can not process this pmt file yet
                // we have to wait until failed pmts have been processed
                // the failed pmts information are in later part of this response file
                // so, we save the file id first

                // Add fileId into pmtPartiallyAcceptedFileIds;
                _pmtPartiallyAcceptedFileIds.add( pmtFileInfo );

            } else {
                // still can not find pmt file match,
                // log error message
                // WE could skip these two sum batches and continue
                // For this release, we consider it is an invalid response file
                FFSDebug.log( mName + "Can not find payment file map from high-level summary batches. Maybe BPW database has been corrupted or this confirmation is invalid. ", FFSDebug.PRINT_ERR );
                FFSDebug.log( mName + "Batch numbers: " + bHeader.Batch_Number + " and " + bHeader2.Batch_Number, FFSDebug.PRINT_ERR );
                return RPPSConsts.RESP_FILE_ERROR_CANNOT_FIND_PAYMENT_FILE_MAP;

            } // can not find pmt from these two batches


        } // try two batches

        return result;

    }
    /**
     * Process all the response normal batches (Rejected or Return)
     * Caller must have set _currRecordStr to be batch header
     *
     * @param fileTokenizer
     * @return
     */
    private int processNormalBatches ( FFSConnectionHolder dbh,
                                       RPPSFileTokenizer fileTokenizer )

    throws FFSException
    {
        String mName = "RPPSConfirmRtnFileHandlerImpl.processNormalBatches: ";
        FFSDebug.log( mName + "start... ", FFSDebug.PRINT_DEV );

        int result = RPPSConsts.RESP_FILE_SUCCESS;
        int batchNum = 0;
        // Start to process real Reject payments or Return payments.

        // We have read a batch header already

        // The first batch's header has been loaded
        boolean bRejectedPmt = true;
        int recordType = 0;

        while ( true ) {

            // process batch by batch

            // 1. Processs batch header

            // Parse batch header
            TypeBatchHeaderRecord bHeader = parseBatchHeader( _currRecordStr );

            batchNum = bHeader.Batch_Number;
            if ( ( bHeader.Entry_Description.compareTo( RPPSConsts.RESP_FILE_BATCH_REJECT_BATCH ) == 0  )
                 || ( bHeader.Entry_Description.compareTo( RPPSConsts.RESP_FILE_BATCH_REJECT_ENTRY ) == 0 ) ) {
                //
                // for reject
                bRejectedPmt = true;
            } else if ( bHeader.Entry_Description.compareTo( RPPSConsts.RESP_FILE_BATCH_RETURN ) == 0 ) {
                // BATCH RETURN
                //  this batch is for return
                bRejectedPmt = false;
            } else {
                result = RPPSConsts.RESP_FILE_ERROR_INVALID_ENTRY_DESCRIPTION;
                break;
            }

            // load first entry detail and prepare for processEntries method
            if ( fileTokenizer.hasMoreTokens() == true) {
                _currRecordStr = fileTokenizer.nextToken();
            } else {
                result = RPPSConsts.RESP_FILE_ERROR_MISSING_BATCH_CONTROL;
                break;
            }

            recordType = ACHAgent.getRecordType(_currRecordStr );
            if ( recordType == ACHConsts.ENTRY_DETAIL) {

                // 2. Process all the entries/addenda
                result = processEntries( dbh, fileTokenizer, batchNum, bRejectedPmt );
                // This method will load batch control to currRecord

                if ( result != RPPSConsts.RESP_FILE_SUCCESS ) {
                    // invalid file
                    break;
                }
            } else {
                // it must be batch control
            }


            // all the entries in this batch has been processed
            // Process Batch Control for this batch

            // The batch control has been loaded
            // 3. Process batch control

            // parse batch control first
            TypeBatchControlRecord batchControl = parseBatchControl( _currRecordStr );

            // 4. Prepare for next batch or end of file

            // read next record, this record could be
            // batch header or file control

            if ( fileTokenizer.hasMoreTokens() == true) {
                _currRecordStr = fileTokenizer.nextToken();
            } else {
                // invalid response file
                result = RPPSConsts.RESP_FILE_ERROR_MISSING_FILE_CONTROL;
                break;
            }

            recordType = ACHAgent.getRecordType(_currRecordStr );

            // load another record
            // possible:
            //  1. batch header: a new normal batch
            //  2. file control: no more batch

            if ( recordType == ACHConsts.BATCH_HEADER ) {
                // one more batch
                continue;
            } else if ( recordType == ACHConsts.FILE_CONTROL ) {
                // no more batch
                // end of file
                break;

            } else {
                // invalid response file
                result = RPPSConsts.RESP_FILE_ERROR_MISSING_FILE_CONTROL;
                break;
            } // end process all the reject/return records
        } // end of all the batches

        //  File Control record has been loaded into _currRecordStr

        FFSDebug.log( mName + "done. result: " + result, FFSDebug.PRINT_DEV );
        return result;


    }


    /**
     * Process all the entries/addena belonging to one batch
     * batch control record is loaded when this method exits
     * @param dbh
     * @param fileTokenizer
     * @param batchNum
     * @return
     */
    private int processEntries (FFSConnectionHolder dbh,
                                RPPSFileTokenizer fileTokenizer,
                                int batchNum,
                                boolean bRejectedPmt )

    throws FFSException
    {
        String mName = "RPPSConfirmRtnFileHandlerImpl.processEntries: ";
        FFSDebug.log( mName + "start... ", FFSDebug.PRINT_DEV );

        int result = RPPSConsts.RESP_FILE_SUCCESS;
        int recordType = 0;

        //  Process entries in this batch
        while ( true ) {

            // process one entry
            // and its addenda if there is any
            result = processOneEntry( dbh, fileTokenizer, batchNum, bRejectedPmt );

            if ( result != RPPSConsts.RESP_FILE_SUCCESS ) {
                break;
            }

            // The next entry record or this batch's control
            // has been loaded  by processRespEntry method

            // possible:
            //  1. Entry
            //  2. batch control

            recordType = ACHAgent.getRecordType(_currRecordStr );

            if ( recordType == ACHConsts.ENTRY_DETAIL ) {
                // aonther entry record
                continue;
            } else if ( recordType == ACHConsts.BATCH_CONTROL ) {

                // no more entries in this batch
                break;

            } else {
                // invalid response file
                result = RPPSConsts.RESP_FILE_ERROR_MISSING_BATCH_CONTROL;
                break;
            }

        } // end of all enties in this batch

        // Commit each batch
        // COMMIT to avoid big TX
        dbh.conn.commit();

        // Batch control has been loaded

        FFSDebug.log( mName + "done. result: " + result, FFSDebug.PRINT_DEV );
        return result;

    }

    /**
     * Process an entry record and its addenda
     * next record after the entry will be loaded after this call
     * @param entryRecord
     * @return
     */
    private int processOneEntry (FFSConnectionHolder dbh,
                                 RPPSFileTokenizer fileTokenizer,
                                 int batchNum,
                                 boolean bRejectedPmt )
    throws FFSException
    {

        String mName = "RPPSConfirmRtnFileHandlerImpl.processOneEntry: ";
        FFSDebug.log( mName + "start... ", FFSDebug.PRINT_DEV );

        int result = RPPSConsts.RESP_FILE_SUCCESS;

        String srvrTId = null;
        String errorMsg = "";
        int recordType = 0;

        // _currRecordStr must have entry record set
        // read one entry
        TypeCSPConfirmEntryDetailRecord entryRecord = parseEntryDetail( _currRecordStr );
        int traceNum = RPPSUtil.parseTraceNum( entryRecord.Trace_Number );

        if ( bRejectedPmt == true ) {

            errorMsg = RPPSUtil.getRejectMsg( entryRecord.Error_Code );

            // Now, use trace num
            // get srvrTID
            // 1. Get srvrTID by rppsPmtInfo (batchNum, traceNum, payAccount, consumer name,
            srvrTId = RPPSDB.getRPPSPmtEntryMapSrvrTID(dbh,
                                                       batchNum,
                                                       traceNum,
                                                       entryRecord.Consumer_Account_Number,
                                                       entryRecord.Consumer_Name );


        } else {
            // return payments

            errorMsg = RPPSUtil.getReturnMsg( entryRecord.Error_Code );

            // first check whether there is addenda for this entry
            if ( entryRecord.Addendum_Record_Indicator != 0 ) {
                if ( fileTokenizer.hasMoreTokens() == true) {
                    _currRecordStr = fileTokenizer.nextToken();
                } else {

                    //
                    return RPPSConsts.RESP_FILE_ERROR_MISSING_ADDENDA;
                }

                // parse addenda
                TypeCSPReturnEntryDetailAddendumRecord addendum = parseAddendum( _currRecordStr );

                // use the trace num and errormsg from addenda
                errorMsg = RPPSUtil.getReturnMsg(addendum.Return_Reason_Code);
                traceNum = RPPSUtil.parseTraceNum( addendum.Original_Entry_Trace_Number );
            }

            // WE MAY NEED TO PROCESS C09 and C11 ( Non-Financial TX specially)

            // for return records, use trace num only to get srvrTID
            srvrTId = RPPSDB.getRPPSPmtEntryMapSrvrTID(dbh, traceNum );

        }

        if ( srvrTId == null ) {
            // Can not find original payment
            // Log error message and return
            FFSDebug.log( mName + "Can not find original payment for entry : " + traceNum, FFSDebug.PRINT_ERR );
            // skip this pmt

        } else {
            this.processFailedPmtBySrvrTID( dbh, srvrTId, errorMsg, bRejectedPmt );
        }



        // one entry has been processed
        // read all the addenda and ingore them
        while ( true) {
            if ( fileTokenizer.hasMoreTokens() == true) {
                _currRecordStr = fileTokenizer.nextToken();

                // The current loaded record must be
                //  1. batch control
                //  2. Entry detail
                //  2. Addenda
                // In this method we are not going to check it
                // caller processRespBatches will check it
                recordType = ACHAgent.getRecordType(_currRecordStr );

                if ( recordType == ACHConsts.ADDENDA ) {
                    // aonther addenda, skip it and read next one
                    continue;
                } else {
                    break;
                    // caller is going to check whether this record is batch control
                    // or not
                }
            } else {

                result = RPPSConsts.RESP_FILE_ERROR_MISSING_BATCH_CONTROL;
                break;
            }
        }

        // Next entry record or batch Control has been loaded

        FFSDebug.log( mName + "done. result: " + result, FFSDebug.PRINT_DEV );
        return result;
    }



    /**
     * Update payment status
     *
     * @param dbh
     * @param srvrTID
     * @param errorMsg
     * @exception FFSException
     */
    private void processFailedPmtBySrvrTID( FFSConnectionHolder dbh,
                                   String srvrTID,
                                   String errorMsg,
                                   boolean bRejectedPmt )
    throws FFSException
    {
        String mName = "RPPSConfirmRtnFileHandlerImpl.processFailedPmtBySrvrTID: ";
        FFSDebug.log( mName + "start...", FFSDebug.PRINT_DEV );
        try {
            PmtInfo info = PmtInstruction.getPmtInfo( srvrTID, dbh );

            if ( info == null ) {
                FFSDebug.log( mName + "Can not find payment! srvrTID" + srvrTID, FFSDebug.PRINT_ERR );

            } else {
                this.processFailedPmtInfo(dbh,info,errorMsg,bRejectedPmt);
            }

        } catch ( Exception e ) {
            FFSDebug.log( mName + "Failed to process reject/return payment: " + e.toString()
                          + " srvrTID: " + srvrTID, FFSDebug.PRINT_ERR );

            throw new FFSException( FFSDebug.stackTrace( e ) );
        }
    }

    /**
     * Update payment status
     *
     * @param dbh
     * @param info
     * @param errorMsg
     * @param bRejectedPmt
     * @exception FFSException
     */
    private void processFailedPmtInfo( FFSConnectionHolder dbh,
                                       PmtInfo info,
                                       String errorMsg,
                                       boolean bRejectedPmt )
    throws FFSException
    {
        String mName = "RPPSConfirmRtnFileHandlerImpl.processFailedPmtInfo: ";
        FFSDebug.log( mName + "start...", FFSDebug.PRINT_DEV );
        try {
            // add payment failed
            //
            if ( bRejectedPmt == true ) {

                // this pmt rejected by RPPS network
                if ( ( _allowRepeatable == true ) ||
                     ( info.Status.equals(DBConsts.BATCH_INPROCESS) == true ) ) {

                    // if allow repeat
                    //          update the status to FAILED
                    // else
                    //  update un processed pmts
                    //      if status is BATCH_INPROCESS
                    //      update status to be FAILED

                    // Another way to understand
                    // if allow repeat, we can overwrite any current status
                    // if not allow repeat, the current status of this pmt must
                    // be BATCH_INPROCESS
                    updateOnePmtStatus( dbh, info, DBConsts.STATUS_GENERAL_ERROR, errorMsg );

                } else {
                    // not allow repeat and the existing status is not BATCH_INPROCESS
                    // which means it could have been processed

                    FFSDebug.log( mName + "This payment has been processed. Duplicate payment processing not allowed, SrvrTID=" + info.SrvrTID, FFSDebug.PRINT_WRN );
                }
            } else {

                // this pmt returned from Biller or BSP
                // it could arrive several days later. The current pmt's status
                // could be PROCESSEDON or BATCH_INPROCESS

                if ( ( _allowRepeatable == true ) ||
                     ( info.Status.equals(DBConsts.PROCESSEDON) == true ) ||
                     ( info.Status.equals(DBConsts.BATCH_INPROCESS) == true ) ) {

                    // if allow repeat, we can overwrite any current status
                    // if not allow repeat, the current status of this pmt must
                    // be BATCH_INPROCESS
                    updateOnePmtStatus( dbh, info, DBConsts.STATUS_GENERAL_ERROR, errorMsg );

                } else {
                    // not allow repeat and the existing status is not BATCH_INPROCESS
                    // which means it could have been processed

                    FFSDebug.log( mName + "This payment has been processed. Duplicate payment processing not allowed, SrvrTID=" + info.SrvrTID, FFSDebug.PRINT_WRN );
                }
            }


        } catch ( Exception e ) {
            FFSDebug.log( mName + "Failed to process reject/return payment: " + e.toString()
                          + " srvrTID: " + info.SrvrTID, FFSDebug.PRINT_ERR );

            throw new FFSException( FFSDebug.stackTrace( e ) );
        }
    }

    /**
   * No news is good news,
   * update the payments' status to be SUCCESS
   * who are included in the fileIds list and whose
   * status have not be touched.
   * TODO
   *
   * @param dbh
   * @param fileId
   */
    private void processPartiallySucceededPmtFile( FFSConnectionHolder dbh )
    throws FFSException
    {
        int fileId = 0;

        // whole file is partially accepted
        // updte unfailed the pmts included in this file with SUCCESS
        // Payment files whose result is included in this confirmation file and are partially accepted

        int size = _pmtPartiallyAcceptedFileIds.size(); // Payment files whose result is included

        for ( int i = 0; i < size; i ++ ) {
            // Payment files whose result is included

            RPPSPmtFileInfo pmtFileInfo = ( RPPSPmtFileInfo )this._pmtPartiallyAcceptedFileIds.get(i) ;

            processPmtsInOneFile( dbh,
                                  pmtFileInfo,
                                  DBConsts.STATUS_OK,
                                  DBConsts.MSG_STATUS_OK );
        }
    }


    /**
     * No news is good news,
     * update the payments' status to be SUCCESS or FAIL
     *
     * Commit by chunk
     *
     * @param dbh
     * @return
     */
    private void processPmtsInOneFile( FFSConnectionHolder dbh,
                                       RPPSPmtFileInfo pmtFileInfo,
                                       int status,
                                       String message )
    throws FFSException
    {
        int fileId = pmtFileInfo.getFileId();
        int startTraceNum = 0;
        // updte all the pmts included by this file with SUCCESS
        // 1. whole file is accepted

        RPPSPmtInfo[] rppsPmtInfos = null;

        // get srvrTID by chunks
        rppsPmtInfos = RPPSDB.getRPPSPmtEntryMapByFileIdAndTraceNum( dbh,
                                                                     fileId,
                                                                     startTraceNum );
        while ( ( rppsPmtInfos != null ) && ( rppsPmtInfos.length != 0 ) ) {


            // update all the pmts to be SUCCESS
            updatePmtsStatus( dbh, rppsPmtInfos, status, message );

            dbh.conn.commit();

            // move the startTraceNum
            startTraceNum = rppsPmtInfos[rppsPmtInfos.length - 1].getTraceNum() + 1;

            rppsPmtInfos = RPPSDB.getRPPSPmtEntryMapByFileIdAndTraceNum( dbh,
                                                                         fileId,
                                                                         startTraceNum );
        }

        // update the confirmed field to be YES
        pmtFileInfo.setConfirmed( "Y" );

        RPPSDB.updateRPPSPmtFileMap(dbh,pmtFileInfo);

        // commit all the open TX
        dbh.conn.commit();


    }


    /**
     * Update a chunk of pmts' status
     *
     * @param dbh
     * @param rppsPmtInfos
     * @param succeeded
     * @exception FFSException
     */
    private void updatePmtsStatus( FFSConnectionHolder dbh,
                                   RPPSPmtInfo[] rppsPmtInfos,
                                   int status,
                                   String message )
    throws FFSException
    {

        String mName = "RPPSConfirmRtnFileHandlerImpl.updatePmtsStatus: ";
        FFSDebug.log( mName + "start...", FFSDebug.PRINT_DEV );

        for ( int i = 0; i < rppsPmtInfos.length; i++ ) {

            String srvrTID = rppsPmtInfos[i].getSrvrTId();

            PmtInfo info = PmtInstruction.getPmtInfo( srvrTID, dbh );

            if ( info == null ) {
                FFSDebug.log( mName + "Can not find payment! srvrTID" + srvrTID, FFSDebug.PRINT_ERR );

            } else if ( status == DBConsts.STATUS_OK  ) { // succeeded pmts

                if ( _allowRepeatable == true ) {
                    if ( ( info.Status.equals(DBConsts.FAILEDON) == false )
                         && ( info.Status.equals(DBConsts.FAILEDON_NOTIF) == false ) ) { // in case failed Pmt schedule starts at the same

                        // NOTE: in allow repeat case,
                        // if the status of pmt has is changed from FAILED to SUCCEEDED
                        // The current implementation can overwrite this pmt's
                        // status to SUCCEEDED

                        // if allow repeat
                        // ---update non-failed pmts
                        //     if the status is not FAILED
                        //          update the status to SUCCESS
                        updateOnePmtStatus( dbh,
                                            info,
                                            DBConsts.STATUS_OK,
                                            DBConsts.MSG_STATUS_OK );

                    }
                } else { // not allow repeat

                    if ( info.Status.equals( DBConsts.BATCH_INPROCESS ) == true ) {
                        // ---update un processed pmts
                        //      if status is BATCH_INPROCESS
                        //      update status to be SUCESS
                        updateOnePmtStatus( dbh,
                                            info,
                                            DBConsts.STATUS_OK,
                                            DBConsts.MSG_STATUS_OK );
                    } else {
                        // can't update status for this pmt anymore
                        FFSDebug.log( mName + "This payment has been processed. Duplicate payment processing not allowed, SrvrTID=" + info.SrvrTID, FFSDebug.PRINT_WRN );
                    }
                }

            } else { // failed pmts

                this.processFailedPmtInfo(dbh,info,message, true ); // true: It is a rejected payment

            } // found pmt from bpw_pmtinstruciton table
        } // end for each pmt
    }

    /**
     * Update payment status
     *
     * @param dbh
     * @param info
     * @param status
     * @param message
     * @exception FFSException
     */
    private void updateOnePmtStatus( FFSConnectionHolder dbh,
                                     PmtInfo info,
                                     int status,
                                     String message )
    throws FFSException
    {
        String mName = "RPPSConfirmRtnFileHandlerImpl.updateOnePmtStatus: ";
        FFSDebug.log( mName + "start...", FFSDebug.PRINT_DEV );
        try {
            // add payment failed
            PmtTrnRslt rslt = new PmtTrnRslt( info.CustomerID,
                                              info.SrvrTID,
                                              status,
                                              message,
                                              info.ExtdPmtInfo );
            rslt.logID = info.LogID;

            // update the database
            _backendProcessor.processOnePmtRslt(rslt, info.LogID, info.Status, info.FIID, dbh);


        } catch ( Exception e ) {
            FFSDebug.log( mName + "Failed to process reject/return payment: " + e.toString()
                          + " srvrTID: " + info.SrvrTID, FFSDebug.PRINT_ERR );

            throw new FFSException( FFSDebug.stackTrace( e ) );
        }
    }
    /**
     * Process invalid response file:
     * 1. Log error message
     * 2. Move this file to error folder.
     *
     * @param fileName
     * @param errorCode
     * @exception FFSException
     */
    private void processInvalidRespFile( String fileName, int errorCode )
    throws FFSException
    {
        String mName = "RPPSConfirmRtnFileHandlerImpl.processInvalidRespFile:";
        // do the mapping to get error message
        String errorMsg = RPPSUtil.getInvalidFileErrorMsg( errorCode ); //

        FFSDebug.log( "RPPS Response File Handler: "
                      + fileName + " is an invalid confirmation file! "
                      + "Reason: " + errorMsg
                      + "\n\n Please contact with RPPS Network immediately!", FFSDebug.PRINT_ERR );

        try {

            // start to move the invalid repsonse file to error folder

            // Create errorDir it does not exist, add File.separator
            String errorFileBase = ACHAdapterUtil.getFileNameBase( _errorDir );
            if ( ( fileName != null ) && ( fileName.length() != 0 ) ) {

                // This pmt has been processed before, move this file
                // to error folder

                // check whether this file exists or not
                File invalidFile = new File(  fileName );
                invalidFile.setFileHandlerProvider(RPPSUtil.getFileHandlerProvider());

                if ( invalidFile.exists() ) {
                    FFSDebug.log( mName + "Move invalid confirmation file to error folder. File name: "
                                  + fileName, FFSDebug.PRINT_ERR );

                    // Move this file to error, and add System.getCurrentMis to the end of this file

                    String fullErrorFileName = errorFileBase
                                               + invalidFile.getName()
                                               + RPPSConsts.STR_RPPS_FILE_SEPARATOR
                                               + String.valueOf( System.currentTimeMillis() ) ;

                    File errorFile = new File( fullErrorFileName );
                    errorFile.setFileHandlerProvider(RPPSUtil.getFileHandlerProvider());
                    invalidFile.renameTo( errorFile );

                    // Log to File Monitor Log
                    // We pass in null value for db connection,
                    // then a new db connection will be used
                    // for this log and be committed right away
                    // Normally we pass in the db connection, but since it is not available here
                    // we will use a new one and commit it right away.
                    FMLogAgent.writeToFMLog(null,
                                            DBConsts.BPW_RPPS_FILETYPE_ORIGCONFIRMRTN,
                                            fullErrorFileName,
                                            DBConsts.BPTW,
                                            DBConsts.RPPS_ERR,
                                            FMLogRecord.STATUS_COMPLETE);

                    FFSDebug.log( mName + "The invalid confirmation file has been moved to  " + fullErrorFileName, FFSDebug.PRINT_ERR );
                }
            }
        } catch ( Exception e ) {
            String err = mName + "Failed to process invalid confirmation file. Error message: " + e.toString();
            FFSDebug.log( err, FFSDebug.PRINT_ERR );
            throw new FFSException( e, err );
        }

    }


    /**
     * Read a batch header object from resp file
     * Caller assumes that the next record must be batch
     * header
     *
     * @param fileTokenizer
     * @return
     */
    private TypeBatchHeaderRecord readBatchHeader(  RPPSFileTokenizer fileTokenizer )
    throws FFSException
    {
        if ( fileTokenizer.hasMoreTokens() == true ) {
            _currRecordStr = fileTokenizer.nextToken();
            // parse this line
            return parseBatchHeader( _currRecordStr );
        } else {
            return null;
        }
    }

    /**
     * Read a batch control object from resp file
     * Caller assumes that the next record must be batch
     * control
     *
     * @param fileTokenizer
     * @return
     */
    private TypeBatchControlRecord readBatchControl(  RPPSFileTokenizer fileTokenizer )
    throws FFSException
    {
        if ( fileTokenizer.hasMoreTokens() == true ) {
            _currRecordStr = fileTokenizer.nextToken();
            // parse this line
            return parseBatchControl( _currRecordStr );
        } else {
            return null;
        }
    }

    /**
     * Just parse this header string.
     *
     * @param headerStr
     */
    private TypeFileHeaderRecord parseFileHeader( String headerStr )
    throws FFSException
    {
        return( TypeFileHeaderRecord) ACHAgent.parsePureRecord(MB_RPPS_SET_NAME,
                                                               MB_RPPS_TYPE_FILE_HEADER,
                                                               headerStr);
    }


    /**
     * Just parse this control string.
     *
     * @param headerStr
     */
    private TypeFileControlRecord parseFileControl( String controlStr )
    throws FFSException
    {
        return( TypeFileControlRecord) ACHAgent.parsePureRecord(MB_RPPS_SET_NAME,
                                                                MB_RPPS_TYPE_FILE_CONTROL,
                                                                controlStr);
    }
    /**
     * Parse this batch header string.
     *
     * @param headerStr
     */
    private TypeBatchHeaderRecord parseBatchHeader( String headerStr )
    throws FFSException
    {
        return(TypeBatchHeaderRecord) ACHAgent.parsePureRecord(MB_RPPS_SET_NAME,
                                                               MB_RPPS_TYPE_BATCH_HEADER,
                                                               headerStr);
    }

    /**
     * Parse this batch control string.
     *
     * @param controlStr
     */
    private TypeBatchControlRecord parseBatchControl( String controlStr )
    throws FFSException
    {
        return(TypeBatchControlRecord) ACHAgent.parsePureRecord(MB_RPPS_SET_NAME,
                                                                MB_RPPS_TYPE_BATCH_CONTROL,
                                                                controlStr);
    }

    /**
     * Parse this entry detailed record string.
     *
     * @param entryStr
     */
    private TypeCSPConfirmEntryDetailRecord parseEntryDetail( String entryStr )
    throws FFSException
    {
        return(TypeCSPConfirmEntryDetailRecord) ACHAgent.parsePureRecord(MB_RPPS_SET_NAME,
                                                                         MB_RPPS_TYPE_CONFIRM_ENTRY_DETAIL,
                                                                         entryStr);
    }

    /**
   * Parse this addenda record string.
   *
   * @param entryStr
   */
    private TypeCSPReturnEntryDetailAddendumRecord parseAddendum( String addendumStr )
    throws FFSException
    {
        return(TypeCSPReturnEntryDetailAddendumRecord) ACHAgent.parsePureRecord(MB_RPPS_SET_NAME,
                                                                                MB_RPPS_TYPE_RETURN_ENTRY_DETAIL_ADDENDUM,
                                                                                addendumStr);
    }

}

