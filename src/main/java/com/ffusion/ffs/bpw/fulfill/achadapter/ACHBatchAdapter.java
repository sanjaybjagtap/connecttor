/*
 *
 * Copyright (c) 2000 Financial Fusion, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of
 * Financial Fusion, Inc. You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of
 * the license agreement you entered into with Financial Fusion, Inc.
 * No part of this software may be reproduced in any form or by any
 * means - graphic, electronic or mechanical, including photocopying,
 * recording, taping or information storage and retrieval systems -
 * except with the written permission of Financial Fusion, Inc.
 *
 * CopyrightVersion 1.0
 *
 */
package com.ffusion.ffs.bpw.fulfill.achadapter;

import java.io.File;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;

import com.ffusion.ffs.bpw.achagent.ACHAgent;
import com.ffusion.ffs.bpw.audit.TransAuditLog;
import com.ffusion.ffs.bpw.db.ACHBatch;
import com.ffusion.ffs.bpw.db.ACHCompany;
import com.ffusion.ffs.bpw.db.ACHFI;
import com.ffusion.ffs.bpw.db.ACHPayee;
import com.ffusion.ffs.bpw.db.BPWFI;
import com.ffusion.ffs.bpw.db.Customer;
import com.ffusion.ffs.bpw.interfaces.ACHAddendaInfo;
import com.ffusion.ffs.bpw.interfaces.ACHBatchInfo;
import com.ffusion.ffs.bpw.interfaces.ACHCompanyInfo;
import com.ffusion.ffs.bpw.interfaces.ACHConsts;
import com.ffusion.ffs.bpw.interfaces.ACHFIInfo;
import com.ffusion.ffs.bpw.interfaces.ACHPayeeInfo;
import com.ffusion.ffs.bpw.interfaces.ACHRecordInfo;
import com.ffusion.ffs.bpw.interfaces.BPWFIInfo;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.CustomerInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.ScheduleHist;
import com.ffusion.ffs.bpw.interfaces.handlers.IACHBatchAdapter;
import com.ffusion.ffs.bpw.master.ACHBatchProcessor;
import com.ffusion.ffs.bpw.master.LimitCheckApprovalProcessor;
import com.ffusion.ffs.bpw.util.ACHAdapterConsts;
import com.ffusion.ffs.bpw.util.ACHAdapterUtil;
import com.ffusion.ffs.bpw.util.BPWUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.scheduling.db.ScheduleHistory;
import com.ffusion.ffs.util.ACHFileCache;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.ffusion.ffs.util.FFSUtil;
import com.ffusion.util.logging.AuditLogRecord;
import com.ffusion.util.logging.AuditLogTranTypes;
import com.sap.banking.common.interceptors.PerfLoggerUtil;


/**
 * This class saves ACH Batches to ACH Files. The convention of the ACH Files is
 * <export>/<ODFI ACHID>.<Create_Date>.<Create_Time>.<Modifier: A-Z, 0-9>.ACH.
 * The Non ADV ACH Batches who have same ODFI ACHID are saved in one  file.
 * The ADV ACH Batches who have same ODFI ACHID are saved in  another file.
 * <export> is a property passed into BPW Server when it gets started.
 *
 * Callers must call start() method
 * before calling any other methods. Caller also must call shutdown() mehod when
 * there are no more ACH Batches need to be porcessed. Method processACHBatches
 * is called to process a chunk of batches.
 *
 * For each batch,
 *      1. Read the batch information from database
 *      2. Check AchFileName colum, if it not null, move the file specified
 *          by this file exists, we move it to <error> folder
 *          with <AchFileName>.SystemCurrentMills as the file name
 *          Notes: If this colum is not null, this means that this batch has
 *                  been processed before. It happens when the last process
 *                  crashed in the middle or clients invoke resubmit events method
 *      3. Use the ODFI ACHID as the key, find entry from _fileNames HashMap.
 *          If it doesn't
 *          exist, we create a new file, compose File Header Record, save it into
 *          the new file, and save this file name to _fileNames. Then continue
 *          If it exists, continue
 *
 *      4. Compose Batch Header for the current batch, save it into this file.
 *      5. Save all the records belong to this batch inot this file.
 *      6. Compose Batch Control for the current batch, save it into this file.
 *
 *      7. Collection stats for this file: recCount, BlockCount, BatchCount, Entry/Addenda
 *          Count, Hash, TotalCredit and TotalDebit
 *      8. When shutdown() is called , for each file, create File Control
 *          and save it into this file.
 *          Notes: We don't verfiy the file stats with the values gotten from
 *                  database. The information has been validated by front-end
 *                  and we assume that clients will not modify these information
 *                  on the database directly.
 *
 *
 * Notes: A FI could have multiple ACH Operators. ODFI ACH ID is the id provided
 *        the ACH Operators. Each ACH Operator assigns different ACH IDs to a
 *        particular FI. This ID is unique among all the ACH Operators.
 */
public class ACHBatchAdapter implements IACHBatchAdapter, ACHAdapterConsts {

    private String _exportFileBase = "";
    private String _errorFileBase = "";
    private String _tempFileBase = "";

    private int _pageSize = 1000; // configurable

    private String _achVersion = "MsgSet"; // configurable

    // Two possible files: ADV and Non-ADV
    // If the file names are null for a ODFI ACH ID, it means it is first time
    // ACHBatchAdapter.process
    // is called. We need generated a file name and write file header into it.
    // Otherwise, this is not first time. The file is ready to write ACH Batch into

    private HashMap _tempFileNames = null;
    private HashMap _tempADVFileNames = null;

    String _exportDir = null;
    String _tempDir  = null;

    // Hold the stats for each file
    // Those stats are used to create File Control for ACH Files
    private HashMap _fileCaches = null; // Non ADV batches
    private HashMap _advFileCaches = null; // ADV batches


    private ACHBatchProcessor _achBatchProcessor = null;

    private ACHAgent _achAgent = null;

    private int _logLevel;

    private String _FIID;
    private ScheduleHist schHist = null;

    private String _achBatchFileExtension = DBConsts.DEFAULT_ACH_FILE_EXTENSION;

    private String _processId = null;
    private BPWFIInfo _bpwFIInfo = null;

    private String _validatePrenotePayee = null;
    
    private String InstructionType=DBConsts.ACHBATCHTRN ;

    /**
     * This method is called when the ACHBatch handler is created. It is
     * used to perform initialization if necessary.
     *  1. Create export file base, the folder while export files will
     *      be placed if it doesn't not exist
     *  2. Create error file base, the folder while export files will
     *      be placed if it doesn't not exist
     *  3. Create hashMaps to hold export file names
     *  4. Create hashMaps to hold File Caches
     *  5. Reterieve ACHAgent from FFSResitry
     */

    public void start(FFSConnectionHolder dbh, String cutOffId, String processId,
                      boolean createEmptyFile, String FIID)
    throws Exception
    {
    	String methodName = "ACHBatchAdapter.start";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
    	

        FFSDebug.log( "ACHBatchAdapter.start is called", FFSDebug.PRINT_DEV );

        _processId = processId;

        _pageSize = ACHAdapterUtil.getPropertyInt( DBConsts.BATCHSIZE,
                                                   DBConsts.DEFAULT_BATCH_SIZE );

        _achVersion = ACHAdapterUtil.getProperty( DBConsts.ACH_VERSION,
                                                          DBConsts.DEFAULT_ACH_VERSION );

        _exportDir = ACHAdapterUtil.getProperty( DBConsts.ACH_EXPORT_DIR,
                                                        DEFAULT_EXPORT_DIR );

        // Create exportDir it does not exist, add File.separator
        _exportFileBase = ACHAdapterUtil.getFileNameBase( _exportDir );

        // get temp file name and create tempDir is it doesn't exist, add File.separator
        _tempDir = _exportFileBase + STR_TEMP_DIR_NAME;

        _tempFileBase = ACHAdapterUtil.getFileNameBase( _tempDir );


        String errorDir = ACHAdapterUtil.getProperty( DBConsts.ACH_ERROR_DIR,
                                                              DEFAULT_ERROR_DIR );

        // Create errorDir it does not exist, add File.separator
        _errorFileBase = ACHAdapterUtil.getFileNameBase( errorDir );

        _tempFileNames = new HashMap();
        _tempADVFileNames = new HashMap();

        _fileCaches = new HashMap();
        _advFileCaches = new HashMap();

        // get ACHAgent from FFSResitory. This agent must have been started.
        _achAgent = (ACHAgent)FFSRegistry.lookup( BPWResource.BPWACHAGENT );
        if ( _achAgent == null ) {
            FFSDebug.log("ACHBatchAdapter.start: ACHAgent has not been started! Terminating process!" , FFSDebug.PRINT_ERR);
            PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
            throw new FFSException( "ACHBatchAdapter.start: ACHAgent has not been started! Terminating process!" );
            
        }

        this._achBatchProcessor = new ACHBatchProcessor();

        PropertyConfig propertyConfig = (PropertyConfig)FFSRegistry.lookup( BPWResource.PROPERTYCONFIG);
        _logLevel = propertyConfig.LogLevel;
        _FIID = FIID;

        _achBatchFileExtension = ACHAdapterUtil.getProperty(DBConsts.ACH_EXPORT_ACH_BATCH_FILE_EXTENSION,
                                                            DBConsts.DEFAULT_ACH_FILE_EXTENSION);

        // create empty files if needed
        if (createEmptyFile) {
            processEmptyFile(dbh,FIID);
        }

        _bpwFIInfo = BPWFI.getBPWFIInfo(dbh,FIID);

        _validatePrenotePayee = ACHAdapterUtil.getProperty(DBConsts.BPW_ACH_PAYEE_ENFORCE_PRENOTE_PERIOD,
                                                                   DBConsts.DEFAULT_BPW_ACH_PAYEE_ENFORCE_PRENOTE_PERIOD);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);

    }
    /**
     * Process ACH Batches
     *
     * @param achBatchInfos
     * @param dbh    - Database connection holder
     *
     *               NB: Do NOT modify the object dbh, or commit the changes with dbh.conn
     *               in ANY part of the IACHBatchAdapter implementation. This could cause
     *               unexpected results and behaviors with the entire BPW system.
     * @exception Exception
     */
    public void processACHBatches( ACHBatchInfo[] achBatchInfos,String InstructionType, FFSConnectionHolder dbh )
    throws Exception

    {
    	 String methodName = "ACHBatchAdapter.processACHBatches";
         long start = System.currentTimeMillis();
         int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log( "ACHBatchAdapter.processACHBatches is called", FFSDebug.PRINT_DEV );
        this.InstructionType=InstructionType;
        if ( achBatchInfos == null ) {
        	PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
            return;
        }

        for ( int i = 0; i < achBatchInfos.length; i++ ) {
            // process one by one

            // todo: for SP2 - For an IAT Batch, the batch can be made up of different Destination Countries.  But IAT
            // batches need to be only one destination country, so we need to split apart the batch into multiple batches
            // and set the destination country, currency, and Foreign Exchange Method for each batch
            // see ACHBatchInfo[] getBatchInfos(ACHBatchInfo achBatchInfo) in ACHAgent.java
//            ACHBatchInfo[] batchInfos = ACHAgent.getBatchInfos(achBatchInfos[i]);
//
//            for (int bi = 0; bi < batchInfos.length; bi++)
//            {
//                ACHBatchInfo batchInfo = batchInfos[bi];
//                processACHBatch (batchInfo, dbh);
//            }
//
//

            // ProcessACHBatch now will split the file if it overflows
            processACHBatch( achBatchInfos[i], dbh );

        }
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    /**
     * This method is called for housekeeping purposes when the BPW server
     * is shut down.
     * Clean _exportFileName hashmap
     */
    public void shutdown(FFSConnectionHolder dbh)
    throws Exception
    {
    	 String methodName = "ACHBatchAdapter.shutdown";
         long start = System.currentTimeMillis();
         int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log( "ACHBatchAdapter.shutdown is called", FFSDebug.PRINT_DEV );

        // write file control for _exportFileNames and _exportADVFileNames
        //
        writeFileControls( _tempFileNames, _fileCaches );

        // write file control for ADV files
        writeFileControls( _tempADVFileNames, _advFileCaches );

        // copy the temp files to export directory
        moveFilesToExport(_tempFileNames, dbh);
        moveFilesToExport(_tempADVFileNames, dbh);

        //Reset file names to null
        _tempFileNames.clear();
        _tempADVFileNames.clear();
        _tempFileNames = null;
        _tempADVFileNames = null;

        _fileCaches.clear();
        _advFileCaches.clear();

        _fileCaches = null;

        _advFileCaches = null;

        _validatePrenotePayee = null;
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    /**
     * Copy files in temp dir to export dir
     *
     * @param tempFileNames contains file names in temp dir
     * @exception Exception
     */
    private void moveFilesToExport(HashMap tempFileNames, FFSConnectionHolder dbh)
    throws Exception
    {
        String mName = "ACHBatchAdapter.moveFilesToExport";
        if ( (tempFileNames != null )  && (tempFileNames.size() > 0) ) {
            // convert values in the hashmap to array
            Object[] fileNames = tempFileNames.values().toArray();

            // for each file in temp dir, copy it to the file in export dir
            for (int i = 0; i < fileNames.length; i++) {
                // get file name in export directory
                File tempFile = new File( (String)fileNames[i] );

                File exportFile = new File( _exportFileBase + tempFile.getName() );

                // if exportFile exists, move it to folder error
                if (exportFile.exists()) {

                    // export file exists, move it to folder error
                    FFSDebug.log( "Export file exists " + exportFile.getCanonicalPath(), FFSDebug.PRINT_INF );

                    // Move this file to error, and add System.getCurrentMis to the end of this file
                    String fullErrorFileName = _errorFileBase
                                               + exportFile.getName()
                                               + STR_ACH_FILE_SEPARATOR
                                               + System.currentTimeMillis()
                                               + STR_ACH_FILE_SEPARATOR
                                               + _achBatchFileExtension;

                    File errorFile = new File( fullErrorFileName );

                    exportFile.renameTo( errorFile );

                    // create a new one
                    exportFile = new File( _exportFileBase + tempFile.getName() );

                    FFSDebug.log( "The existing export file has been moved to  " + errorFile.getCanonicalPath(), FFSDebug.PRINT_INF );
                }

                // move file from tempFile to exportFile
                tempFile.renameTo( exportFile );

                //do the FM logging
                ACHAdapterUtil.doFMLoggingForACH(dbh, exportFile.getCanonicalPath(), mName);
                logScheduleHist(dbh, exportFile.getName() );

            }
        } // else tempFileNames.size == 0, ignore it  or tempFileName == null, ignore it
    }

    private void logScheduleHist(FFSConnectionHolder dbh, String fileName)
    {
        // Get the last entry in SCH_Hist table
        // by fiId, InstructionType
        try {
            if (schHist == null)
                schHist = ScheduleHistory.getLastScheduleHist( _FIID,InstructionType );
            schHist.FileName = fileName;
            schHist.EventType = DBConsts.SCH_EVENTTYPE_PROCESSINGFILEGENERATED;
            ScheduleHistory.createScheduleHist(dbh,schHist);
        } catch (Exception e) {
            String trace = FFSDebug.stackTrace( e );
            FFSDebug.log( "*** ACHBatchAdapter.logScheduleHist: exception:" + trace, FFSConst.PRINT_DEV );
            return;
        }
    }

    /**
     * Process an ACH Batch
     *
     *      1. Read the batch information from database
     *      2. Check AchFileName colum, if it not null, move the file specified
     *          by this file exists, we move it to <error> folder
     *          with <AchFileName>.SystemCurrentMills as the file name
     *          Notes: If this colum is not null, this means that this batch has
     *                  been processed before. It happens when the last process
     *                  crashed in the middle or clients invoke resubmit events method
     *      3. Use the ODFI ACHID as the key, find entry from _fileNames HashMap.
     *          If it doesn't
     *          exist, we create a new file, compose File Header Record, save it into
     *          the new file, and save this file name to _fileNames. Then continue
     *          If it exists, continue
     *
     *      4. Compose Batch Header for the current batch, save it into this file.
     *      5. Save all the records belong to this batch inot this file.
     *      6. Compose Batch Control for the current batch, save it into this file.
     *
     *      7. Collection stats for this file:recCount,  BlockCount, BatchCount, Entry/Addenda
     *          Count, Hash, TotalCredit and TotalDebit
     *      8. When shutdown() is called , for each file, create File Control
     *          and save it into this file.
     *          Notes: We don't verfiy the file stats with the values gotten from
     *                  database. The information has been validated by front-end
     *                  and we assume that clients will not modify these information
     *                  on the database directly.
     *
     * @param achBatchInfo
     * @param dbh    - Database connection holder
     *
     *               NB: Do NOT modify the object dbh, or commit the changes with dbh.conn
     *               in ANY part of the IACHBatchAdapter implementation. This could cause
     *               unexpected results and behaviors with the entire BPW system.
     * @exception FFSException
     */
    private void processACHBatch( ACHBatchInfo achBatchInfo, FFSConnectionHolder dbh )
    throws FFSException

    {
        if ( achBatchInfo == null ) {
            return;
        }

        boolean isResubmit = achBatchInfo.getExtInfo() != null && achBatchInfo.getExtInfo().get("IsResubmit") != null;

        String mName = "ACHBatchAdapter.processACHBatch: ";
        String batchId = achBatchInfo.getBatchId();
        FFSDebug.log( mName + " is called. Batch id: " + batchId, FFSDebug.PRINT_DEV );


        // ===========================================
        // Read the batch infor and first chunk of this batch
        // ===========================================

        // Set the page size
        achBatchInfo.setBatchPageSize( _pageSize );
        achBatchInfo.setRecordCursor( 0 ); // To get first page and load batch information

        // false: not recurring
        // true: load records
        // false: don't parse records/addendas
        achBatchInfo = _achBatchProcessor.getBatchTXFromAdapter( dbh,
                                                                 achBatchInfo,
                                                                 false, // not recurring
                                                                 true, // load records
                                                                 false); // don't parse

        // Check whether we can find the batch specified by the batch id
        if ( achBatchInfo.getStatusCode() != ACHConsts.SUCCESS ) {
            FFSDebug.log( mName + "Can't find ACH Batch Info for this id! Id: " + batchId, FFSDebug.PRINT_ERR);
            FFSDebug.log( mName + "Can't find ACH Batch Info for this id! error code: " + achBatchInfo.getStatusCode(), FFSDebug.PRINT_ERR);
            FFSDebug.log( mName + "Can't find ACH Batch Info for this id! error message: " + achBatchInfo.getStatusMsg(), FFSDebug.PRINT_ERR);
            FFSDebug.log( mName + "This batch is skipped. Id: " + batchId, FFSDebug.PRINT_ERR);
            return;
        }

        //check if Company exists or not
        String compId = achBatchInfo.getCompId();
        ACHCompanyInfo compInfo = ACHCompany.getCompanyInfo(compId,dbh);

        if ( compInfo.getStatusCode() != DBConsts.SUCCESS ) {
            // can not find company for this batch anymore
            // fail this batch
            String errMsg = ACHConsts.CAN_NOT_FIND_ACH_COMPANY_FOR_THIS_BATCH_MSG + "Batch Id: " + achBatchInfo.getBatchId();
            FFSDebug.log( mName + errMsg, FFSDebug.PRINT_ERR );

            this.failThisBatch( achBatchInfo,dbh, errMsg );

            //IMP: we need to set the status code AFTER failThisBatch method.
            //The reason is: failThisBatch sets the status to SUCCESS
            //(by calling ACHBatch.updateACHBatchStatus)
            achBatchInfo.setStatusCode(ACHConsts.CAN_NOT_FIND_ACH_COMPANY_FOR_THIS_BATCH);
            achBatchInfo.setStatusMsg(errMsg);
            return;

        }

        //String customerId = compInfo.getCustomerId();
        //get Customer Info
        CustomerInfo customer = null;
        try {
            customer = Customer.getCustomerInfo(compInfo.getCustomerId(), dbh, achBatchInfo);

        } catch (Exception e) {
        	
        }
        if (customer == null || !DBConsts.ACTIVE.equals(customer.status)) {
            // no customer found OR inactive customer found
            // can not find company for this batch anymore
            // fail this batch
            String errMsg = ACHConsts.CAN_NOT_FIND_ACTIVE_CUSTOMER_MSG + "  Batch Id: " + achBatchInfo.getBatchId();
            FFSDebug.log( mName + errMsg, FFSDebug.PRINT_ERR );

            this.failThisBatch( achBatchInfo,dbh, errMsg );

            //IMP: we need to set the status code AFTER failThisBatch method.
            //The reason is: failThisBatch sets the status to SUCCESS
            //(by calling ACHBatch.updateACHBatchStatus)
            achBatchInfo.setStatusCode(ACHConsts.CAN_NOT_FIND_ACTIVE_CUSTOMER);
            achBatchInfo.setStatusMsg(errMsg);
            return;
        }

        if (!isResubmit &&
            !ACHConsts.ACH_BATCH_REVERSAL.equals(achBatchInfo.getBatchCategory()) &&       // don't check Effective Date if doing a batch reversal
            !ACHConsts.ACH_MULTIBATCH_TEMPLATE.equals(achBatchInfo.getBatchCategory()) &&       // don't check Effective Date if doing a Multiple Batch Template
            !ACHConsts.ACH_BATCH_TEMPLATE.equals(achBatchInfo.getBatchCategory())) {       // don't check Effective Date if doing a batch template
            this.checkEffectiveEntryDate( dbh, achBatchInfo );

            if ( achBatchInfo.getStatusCode() != ACHConsts.SUCCESS ) {
                FFSDebug.log( mName + achBatchInfo.getStatusMsg() + " Cannot process this batch. Id: " + batchId, FFSDebug.PRINT_ERR);
                // fail the batch
                this.failThisBatch(achBatchInfo,dbh,achBatchInfo.getStatusMsg());
                return;
            }
        }

        // do two things for preonotes
        // 1: check the prenote status for all the payees targeted by the all the records in this batch
        if (_validatePrenotePayee.compareTo("Y") == 0 && !ACHConsts.ACH_BATCH_PRENOTE.equals(achBatchInfo.getBatchCategory())) {
            if ( ACHPayee.checkPayeePrenoteForACHBatch(dbh, batchId, achBatchInfo.getBatchType() ) == false ) {

                String errMsg = "This batch includes prenote failed payees.";
                FFSDebug.log( mName + errMsg + " Id: " + batchId, FFSDebug.PRINT_ERR);
                failThisBatch( achBatchInfo, dbh, errMsg );
                return;
            }
        }

        // The current FI's ACH ID
        // We need to get ODFIACHId from achBatchInfo(not from its header)
        // because we want to get exact value of ODFIACHId for later use:
        // to get ACHFI from DB

        // String odfiACHId = achBatchInfo.getOdfiId();

        // achBatchInfo.getOdfiId() is the batch header's Originator DFI Id, could be
        // different from File Header's Immediate Orgin
        // So, we need to get odfiACHId from ACH_FIInfo through ACH_Company, ACH_Batch


        //Checking if the ODFIACHID exists in ACH_FIInfo and if exists,
        //status is not CLOSED
        String odfiACHId = compInfo.getODFIACHId();

        ACHFIInfo achFIInfo = ACHFI.getACHFIInfo(dbh, odfiACHId );

        if ( ( achFIInfo == null ) ||
             ( achFIInfo.getFIStatus() == null )  ||
             ( achFIInfo.getFIStatus().equals( DBConsts.CLOSED ) ) ) {

            String msg = ACHConsts.FIID_DOES_NOT_EXIST_MSG + odfiACHId;
            FFSDebug.log( mName, " failed:", msg, FFSDebug.PRINT_ERR);
            failThisBatch( achBatchInfo, dbh, msg );
            return;
        }


        // get ACHFI information from database

        // ===========================================
        // Get file name for this batch
        // ===========================================

        // Get the file name for this batch
        String fullFileName = null;
        ACHFileCache achFileCache = null;

        // 1. check what kind of ACH File, ADV or not ADV
        //
        // Get the service class code from batch header
        // NOT the classCode int ACHBatchInfo class.
        int classCode = achBatchInfo.getBatchHeaderFieldValueShort( ACHConsts.SERVICE_CLASS_CODE );

        // Check against ACH_ADV 280
        if ( classCode != ACHConsts.ACH_ADV ) {

            // NON ADV file

            //  1. Check the ACHBatchInfo.exportFileName, if the value is not null
            //      move this file to error folder
            //  2. Get export file name from HashMap, if it does not exist
            //      1. compose exportFileName, prepare export file (Write header)
            //      2. create ACHFileCache
            fullFileName = getTempFileName( achBatchInfo,
                                            _tempFileNames,
                                            _fileCaches,
                                            achFIInfo,
                                            false ); // false: Non ADV

            // Get ACH File Cache
            achFileCache = (ACHFileCache) _fileCaches.get( odfiACHId );

        } else {

            // It is ADV batch
            // Use the ADV file name

            //  1. Check the ACHBatchInfo.exportFileName, if the value is not null
            //      move this file to error folder
            //  2. Get export file name from HashMap, if it does not exist
            //      1. compose exportFileName, prepare export file (Write header)
            //      2. create ACHFileCache
            fullFileName = getTempFileName( achBatchInfo,
                                            _tempADVFileNames,
                                            _advFileCaches,
                                            achFIInfo,
                                            true ); // true: ADV

            // Get ACH File Cache, a new is
            achFileCache = (ACHFileCache) _advFileCaches.get( odfiACHId );
        }

        FFSDebug.log( "ACHBatchAdapter.processACHBatch:  Export file name : " + fullFileName, FFSDebug.PRINT_DEV );


        // Batch Count
        achFileCache.fileBatchCount ++;

        // ===========================================
        // Write this batch into this file.
        // ===========================================

        // set the current batch number to batch header and batch control
        String batchCountStr = ( new Integer( achFileCache.fileBatchCount) ).toString();

        //TEST
        achBatchInfo.setBatchHeaderFieldValueObject(ACHConsts.BATCH_NUMBER, batchCountStr );
        achBatchInfo.setBatchControlFieldValueObject(ACHConsts.BATCH_NUMBER, batchCountStr );

        //Review
        //make the company name and entry desc to upper - 16 Aug 04
        String companyName = ((String)achBatchInfo.getBatchHeaderFieldValueObject(ACHConsts.COMPANY_NAME));
        if (companyName != null)
            companyName = companyName.toUpperCase();
        String compEntryDesc = ((String)achBatchInfo.getBatchHeaderFieldValueObject(ACHConsts.COMPANY_ENTRY_DESC));
        if (compEntryDesc != null)
            compEntryDesc = compEntryDesc.toUpperCase();
        achBatchInfo.setBatchHeaderFieldValueObject(ACHConsts.COMPANY_NAME, companyName );
        achBatchInfo.setBatchHeaderFieldValueObject(ACHConsts.COMPANY_ENTRY_DESC, compEntryDesc );

        // set the Company ID according to TaxID or ACH Company ID
        ACHBatchInfo.setOriginatorInfoToMBRecord(achBatchInfo, customer, compInfo);

        // write batch header
        // mb layer creates this header (ACHRecordInfo object)
        ACHAdapterUtil.writeACHRecord(fullFileName,
                                      achBatchInfo.getBatchHeader(),
                                      true,     // append true
                                      _achAgent );

        achFileCache.recCount ++; // for Batch Header

        boolean append = true;
        boolean more = true;

        while ( more ) {
            // Process the records read by
            ACHRecordInfo[] achRecords = achBatchInfo.getRecords();
            if ( (achRecords != null ) && (achRecords.length != 0 )) {

                StringBuffer recordContents = new StringBuffer();
                // build all the records into a string and write them into the file
                for ( int i = 0; i < achRecords.length; i ++ ) {

                    // We save the record content into database
                    // and set it when retrieving
                    // We don't need to build the message anymore.
                    ACHRecordInfo achRecordInfo = achRecords[i];

                    // TraceNum, set by front end in ACHBatchProcessor.java
                    recordContents.append( achRecordInfo.getRecordContent() );

                    achFileCache.recCount ++; // for ACH Record

                    ACHAddendaInfo[] addendaInfos = achRecords[i].getAddenda();

                    if ( addendaInfos != null ) {
                        int addendaCount = addendaInfos.length;
                        for (int j = 0; j < addendaCount; j++) {

                            recordContents.append( addendaInfos[j].getAddendaContent() );

                            achFileCache.recCount ++; // for ACH Record

                        } // add all the addenda for this record
                    }

                }
                // write all the built records
                ACHAdapterUtil.writeRecordContents( fullFileName,
                                                    recordContents.toString(),
                                                    append );

                // set start record id for next chunk
                achBatchInfo.setStartRecordId( achRecords[ achRecords.length-1].getRecordId() );

                // update status of these records to be PROCESSEDON

            } else {
                // We have read anything
                // There is no more
                more = false;
            }

            // check whether it is the last chunk
            //Check if sending the last chunk. If the total number of
            //records still retrieved is equal to TotalBatchSize, it
            //indicates the last chunk
            // we can call isLastPage(), however, it is too expensive

            if ( achBatchInfo.isLastPage() == true ) {
                more = false;
            }
            if ( more ) {
                // set start record id and read again
                // false: not recurring
                // true: load records
                // false: don't parse records/addendas
                achBatchInfo = ACHBatch.getACHBatch(achBatchInfo,
                                                    dbh,
                                                    false, // not recurring
                                                    true, // load records
                                                    false); // don't parse
                // check result
            }
        } // end while

        // ===========================================
        // Finish the batch records
        // ===========================================

        // write batch controller
        // mb layer creates this header (ACHRecordInfo object)
        ACHRecordInfo batchControl= achBatchInfo.getBatchControl();

        ACHAdapterUtil.writeACHRecord(fullFileName,
                                      batchControl,
                                      true,     // append true
                                      _achAgent );

        // update status of these records to be POSTEDON
        // and save the exportFileName ( file name only, not include full path)
        int idx = fullFileName.lastIndexOf( File.separator );
        String fileName = fullFileName.substring( idx + 1);
        // update batch status and exportFileName
        
      //check if the batch is process on the same day it was supposed to be effective.If not update the Same day flag in the DB
        if(achBatchInfo.getSameDay()==ACHConsts.ACH_SAME_DAY)
        {
        	String currentDateStr=FFSUtil.getDateString( new java.text.SimpleDateFormat(DBConsts.DUE_DATE_FORMAT) );    	
        	String dueDateStr=achBatchInfo.getDueDate();
        	if(!(dueDateStr.equals(currentDateStr)))
        	{
        		ACHBatch.updateACHBatchSameDayFlag( dbh,achBatchInfo);
        	}
        	
        }

        // pass processId to this method and save processId in ProcessId column
        if (!isResubmit)                // don't update if doing a resubmit
            ACHBatch.updateACHBatchPostedOn( dbh,
                                         achBatchInfo,
                                         fileName,
                                         achFileCache.fileBatchCount,
                                         _processId );

        // do auditLog for this batch. We don't need to check the status of this batch
        // here because the above method is successful or throws exception
        doTransAuditLog( dbh, achBatchInfo, (!isResubmit?"Successfully process an ACH Batch":"Successfully resubmit an ACH Batch") );

        // 2. Update the prenoteSubmitDate for pending
        // After a ACH Record is processed, do following:
        if ( ( achBatchInfo.getBatchCategory() != null )
             && ( achBatchInfo.getBatchCategory().compareTo( ACHConsts.ACH_BATCH_PRENOTE ) == 0 ) ) {
            // assume there is only one record for a prenote batch
            if ( ( achBatchInfo.getRecords() != null )
                 && ( achBatchInfo.getRecords().length != 0 )
                 && ( achBatchInfo.getRecords()[0] != null ) ) {

                ACHPayeeInfo payeeInfo = new ACHPayeeInfo();
                payeeInfo.setPayeeID( achBatchInfo.getRecords()[0].getPayeeId() );
                payeeInfo.setPrenoteSubmitDate( FFSUtil.getDateString( new java.text.SimpleDateFormat(DBConsts.ACHPAYEE_PRENOTE_DATE_FORMAT) ) );
                ACHPayee.updateACHPayeePrenoteSubmitDate( dbh, payeeInfo );

            }
        }

        // udpate ACHFileCache
        // block count
        achFileCache.recCount ++; // for Batch Control

        // entry/addenda count
        achFileCache.fileEntryCount += achBatchInfo.getBatchControlFieldValueInt(ACHConsts.ENTRY_ADDENDA_COUNT);

        achFileCache.fileHash += achBatchInfo.getBatchControlFieldValueLong(ACHConsts.ENTRY_HASH);

        achFileCache.fileDebitSum = achFileCache.fileDebitSum.add(BigDecimal.valueOf(
        	achBatchInfo.getBatchControlFieldValueLong(ACHConsts.TOTAL_DEBITS)));

        achFileCache.fileCreditSum = achFileCache.fileCreditSum.add(BigDecimal.valueOf(
        	achBatchInfo.getBatchControlFieldValueLong(ACHConsts.TOTAL_CREDITS)));

    }



    /**
    *  1. Check the ACHBatchInfo.exportFileName, if the value is not null
    *      move this file to error folder
    *  2. Get export file name from HashMap, if it does not exist
    *      1. compose exportFileName, prepare export file (Write header)
    *      2. create ACHFileCache
    *
    * @param achBatchInfo
    * @param fileNames
    * @param caches
    * @param achFIInfo
    * @param bADV
    * @return
    */
    private String getTempFileName( ACHBatchInfo achBatchInfo,
                                    HashMap fileNames,
                                    HashMap caches,
                                    ACHFIInfo achFIInfo,
                                    boolean bADV ) // true: ADV
    throws FFSException
    {
        // 1. Check ACHBatchInfo.exportFileName
        String oldFileName = achBatchInfo.getExportFileName();

        if ( ( oldFileName != null ) && ( oldFileName.length() != 0 ) ) {

            // This batch has been processed before, move this file
            // to error folder

            // check whether this file exists or not
            File oldTempFile = new File( _tempFileBase + oldFileName );

            oldTempFile.deleteOnExit();

        }

        // 2. Find new file name

        String odfiACHId = achFIInfo.getODFIACHId();

        String tempFileName = (String) fileNames.get( odfiACHId );
        // 2.5 - check if the batch will be too big for the file
        // QTS 645333: check if the current batch will fit in the file (not too big)
        // if it is too big, we need to end this file now and start a new one.
        ACHFileCache achFileCache = (ACHFileCache)caches.get(odfiACHId);
        if (achFileCache != null)
        {
            BigDecimal totalDebits = new BigDecimal("0");
            BigDecimal totalCredits = new BigDecimal("0");
            totalDebits = totalDebits.add(achFileCache.fileDebitSum);
            totalCredits = totalCredits.add(achFileCache.fileCreditSum);

            totalDebits = BPWUtil.setDefaultScale(totalDebits.add(BigDecimal.valueOf(achBatchInfo.
                getBatchControlFieldValueLong(ACHConsts.TOTAL_DEBITS)))).movePointLeft(2);
            totalCredits = BPWUtil.setDefaultScale(totalCredits.add(BigDecimal.valueOf(achBatchInfo.
                getBatchControlFieldValueLong(ACHConsts.TOTAL_CREDITS)))).movePointLeft(2);

            boolean newFile = false;
            if (totalDebits.compareTo(ACHConsts.MAX_TOTAL_DEBITS_BD) > 0) {
                String err = "Need to create a new file because FileDebits will exceed the maximum limit for the file. " +
                             "the limit: " + totalDebits;
                FFSDebug.log("ACHBatchAdapter.getTempFileName: " + err + " oldFile = " + tempFileName);
                newFile = true;
            } else if (totalCredits.compareTo(ACHConsts.MAX_TOTAL_CREDITS_BD) > 0) {
                String err = "Need to create a new file because FileCredits will exceed the maximum limit for the file. " +
                             "the limit: " + totalCredits;
                FFSDebug.log("ACHBatchAdapter.getTempFileName: " + err + " oldFile = " + tempFileName);
                newFile = true;
            }
            if (newFile)
            {
                String key = ""+System.currentTimeMillis();
                fileNames.put(key, tempFileName);
                caches.put(key, achFileCache);
                tempFileName = null;        // force new file
            }
        }

        if ( tempFileName == null ) {
            // This is the first batch , generate file name write file header


            // We need to generate file name and write File Header Record
            // into this file.

            //Fomular: ODFIID.CRATION_DATE.CREATION_TIME.<A-Z,0-9).ACH

            tempFileName = ACHAdapterUtil.prepareExportFile( achFIInfo,
                                                             _tempFileBase,
                                                             _errorFileBase,
                                                             bADV,
                                                             _achAgent,
                                                             _achVersion,
                                                             _achBatchFileExtension );


            // fullFileName = prepareExportFile( achFIInfo, bADV ); // ADV

            // Save the full file name to HashMap
            fileNames.put( odfiACHId, tempFileName );

            // Create a new File Cache and save it into map
            achFileCache = new ACHFileCache();
            caches.put( odfiACHId, achFileCache );

            achFileCache.recCount ++; // for File Header
        }

        return tempFileName;
    }

    /**
     * Get file name in export directory
     *
     * @param tempFileName
     *               file name in temp directory
     * @return
     * @exception FFSException
    private String getFileNameOnly(String tempFileName)
    throws FFSException
    {
        FFSDebug.log("Temp File Name: " + tempFileName, FFSDebug.PRINT_DEV);
        // replace "temp" with "export"
        String tempPart = _tempDir + File.separator;
        // find the index of "\temp\" in tempFileName
        int index = tempFileName.lastIndexOf( tempPart );
        // tempFileName = .......\temp\....
        //               |-part1-|    | - part2 ..
        String part1 = tempFileName.substring( 0,index );
        String part2 = tempFileName.substring( index + tempPart.length() );
        String exportPart = _exportDir + File.separator;
        String exportFileName = part1 + exportPart + part2;
        FFSDebug.log("Export File Name: " + exportFileName, FFSDebug.PRINT_DEV);
        return exportFileName;
    }

    */

    /**
     * Go through all the files saved _exportFileName, get AChFileCache from
     * achFileCaches, create ACH File Control, and save it into the file.
     * @param exportFileNames
     * @param achFileCaches
     * @exception FFSException
     */
    private void writeFileControls( HashMap exportFileNames, HashMap achFileCaches )
    throws FFSException
    {

        // Get all the odfiACHIds
        Iterator odfiACHIds = exportFileNames.keySet().iterator();

        //Go through each
        while ( odfiACHIds.hasNext() ) {

            // Get the ODFI ACH ID
            String odfiACHId = (String) odfiACHIds.next();

            String fullFileName = (String) exportFileNames.get( odfiACHId );

            if ( fullFileName == null ) {
                FFSDebug.log("ACHBatchAdapter.writeFileControls: Can't find fullFileName for this ODFI ACH id! id: " + odfiACHId, FFSDebug.PRINT_ERR);
                continue;
            }
            // Get the ACHFileCache for this id
            ACHFileCache achFileCache = (ACHFileCache) achFileCaches.get( odfiACHId );

            if ( achFileCache == null ) {
                FFSDebug.log("ACHBatchAdapter.writeFileControls: Can't find ACHFileCache for this ODFI ACH id! Id: " + odfiACHId, FFSDebug.PRINT_ERR);
                continue;
            }

            achFileCache.recCount ++; // for File Control

            ACHAdapterUtil.writeFileControl( fullFileName,
                                             achFileCache,
                                             _achAgent,
                                             _achVersion );

        } // go through each odfiACHIds

    }


    /**
     * Do transaction audit log which logs this information to
     * audit_log table in CB
     *
     * @param dbh
     * @param batchInfo
     * @param preDesc
     */
    private void doTransAuditLog(FFSConnectionHolder dbh, ACHBatchInfo batchInfo,
                                 String preDesc )
    throws FFSException
    {
        String currMethodName = "ACHBatchAdapter.doTransAuditLog:";
        if (_logLevel >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE) {
            //calculate total
            long totalAmountLong = batchInfo.getNonOffBatchCreditSum()
                                     + batchInfo.getNonOffBatchDebitSum();
            BigDecimal amount = BigDecimal.valueOf(totalAmountLong).movePointLeft(2);

            // get customerId
            int customerId = 0 ;
            try {
                customerId = Integer.parseInt(batchInfo.getCustomerId());
            } catch (NumberFormatException nfe) {
                String errDescrip = currMethodName + " CustomerId is not an integer - "
                                    + batchInfo.getCustomerId() + " - " + nfe;
                FFSDebug.log(errDescrip + FFSDebug.stackTrace(nfe),FFSDebug.PRINT_ERR);
                throw new FFSException(nfe, errDescrip);
            }

            // get description
            String desc = preDesc + ", Batch Category = " + batchInfo.getBatchCategory()
                          + ", Batch type = " + batchInfo.getBatchType()
                          + ", Batch balanced type= " + batchInfo.getBatchBalanceType();
            FFSDebug.log(currMethodName + desc, FFSDebug.PRINT_DEV);

            int tranType = AuditLogTranTypes.BPW_ACHBATCHTRN;
            if (batchInfo.getBatchStatus().equals(DBConsts.POSTEDON)) {
                // This is for backward compatibility
                if ( ( batchInfo.getBatchCategory() != null ) &&
                     ( batchInfo.getBatchCategory().compareTo( ACHConsts.ACH_BATCH_TAX ) == 0 ) ) {
                    tranType = AuditLogTranTypes.BPW_TAXPAYMENT_SENT;
                } else {
                    tranType = AuditLogTranTypes.BPW_ACHBATCH_SENT;
                }
            } else if (batchInfo.getBatchCategory() != null && batchInfo.getBatchCategory().equals(ACHConsts.ACH_BATCH_PRENOTE)) {
                tranType = AuditLogTranTypes.BPW_ACHBATCHTRN_PRENOTE;
            }

            AuditLogRecord _auditLogRec = new AuditLogRecord(batchInfo.getSubmittedBy(),
                                                             null,
                                                             null,
                                                             desc,
                                                             batchInfo.getLogId(),
                                                             tranType,
                                                             customerId,
                                                             amount,
                                                             null,
                                                             batchInfo.getBatchId(),
                                                             batchInfo.getBatchStatus(),
                                                             null,
                                                             null,
                                                             null,
                                                             null,
                                                             0);
			TransAuditLog.addExtraAuditInfo(batchInfo, _auditLogRec);
            TransAuditLog.logTransAuditLog(_auditLogRec, dbh.conn.getConnection());
        }
    }


    /**
     * Change thet status for this batch to be FAILEDON
     *
     * @param achBatchInfo
     * @param dbh
     * @param errMsg
     */
    private void failThisBatch( ACHBatchInfo achBatchInfo, FFSConnectionHolder dbh, String errMsg )
    throws FFSException
    {

        // Failed to process this batch
        int result = LimitCheckApprovalProcessor.processACHBatchDelete(
                                                                      dbh,
                                                                      achBatchInfo,
                                                                      null); // extraInfo
        String status = DBConsts.FAILEDON;
        if ( result == LimitCheckApprovalProcessor.LIMIT_REVERT_FAILED ) {
            // failed in reverting Limits
            status = DBConsts.LIMIT_REVERT_FAILED;
        }
        // errMsg could be used for audit log
        ACHBatch.updateACHBatchStatus(achBatchInfo, status,dbh, false );
        achBatchInfo.setBatchStatus( status );
        // log it into auditLog
        doTransAuditLog(dbh,achBatchInfo, "Failed to process this ACHBatch: " + errMsg);
    }

    /**
     * This method checks whether the batch has missed processing date or not.
     * 1) Check if batch's effective entry date is a business day.
     * 2) If the effective entry date is today, check if the batch's company
     * allowed same day effectie date for the batch's service class code.
     * If no, fail the batch.
     *
     * @param batchInfo ACHBatchInfo
     * @param dbh
     * @exception FFSException
     */
    private void checkEffectiveEntryDate(FFSConnectionHolder dbh,
                                         ACHBatchInfo batchInfo)
    throws FFSException
    {
        //String mName = "ACHBatchAdapter.checkEffectiveEntryDate: ";

        int scheduleRunDate = Integer.parseInt(FFSUtil.getDateString(DBConsts.DUE_DATE_FORMAT));

        _achBatchProcessor.validateEffectiveDate(dbh,batchInfo, _bpwFIInfo,scheduleRunDate); // don't check upper bound
    }

    /**
     * 1.Get all ACHFIInfos for the specified FIID
     * 2.For each ACHFIInfo, get its ODFIACHID, check if _fileCaches containing the ODFIACHID.
     * 3.If no, call createEmptyFile for this OFFIACHID
     * NOTE: THIS PROCESS ASSUMES THERE IS NOT ADV MESSAGE
     * @param FIID
     * @exception Exception
     */
    public void processEmptyFile(FFSConnectionHolder dbh, String FIID) throws Exception
    {
    	String methodName = "ACHBatchAdapter.processEmptyFile";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        // Get all ACHFIInfos for the specified FIID
        ACHFIInfo[] achFIInfos = ACHFI.getACHFIInfosByFIID(dbh,FIID);
        if (achFIInfos != null && achFIInfos.length > 0) {
            // For each ACHFIInfo, get its ODFIACHID, check if _fileCaches containing the ODFIACHID.
            for (int i = 0; i < achFIInfos.length; i++) {
                String ODFIACHId = achFIInfos[i].getODFIACHId();
                Object achFICache = _fileCaches.get(ODFIACHId);
                // If not exists, call createEmptyFile for this OFFIACHID
                if (achFICache == null) {
                    createEmptyFile(achFIInfos[i]);
                }
            }
        }
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    /**
     * 1.Call ACHAdapterUtil.prepareExportFile() to get file name and create file header.
     * 2.Add the new file name into _fileCaches.
     *
     * @param achFIInfo
     * @exception Exception
     */
    public void createEmptyFile(ACHFIInfo achFIInfo) throws Exception
    {
    	String methodName = "ACHBatchAdapter.createEmptyFile";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        // create a tempfile with header
        String tempFileName = ACHAdapterUtil.prepareExportFile( achFIInfo,
                                                                _tempFileBase,
                                                                _errorFileBase,
                                                                false,
                                                                _achAgent,
                                                                _achVersion,
                                                                _achBatchFileExtension );

        // add tempFileName into _tempFileNames: so it will be moved to export dir
        _tempFileNames.put( achFIInfo.getODFIACHId(), tempFileName);

        // Create a new File Cache and save it into map
        ACHFileCache achFileCache = new ACHFileCache();
        achFileCache.recCount ++;
        _fileCaches.put( achFIInfo.getODFIACHId(), achFileCache );

        // shutdown() will add file control and move the file to export dir
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }
    
}



