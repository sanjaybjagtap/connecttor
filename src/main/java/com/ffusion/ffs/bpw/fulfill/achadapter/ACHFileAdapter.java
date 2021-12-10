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
import java.io.FileWriter;
import java.io.PrintWriter;

import com.ffusion.ffs.bpw.db.ACHFile;
import com.ffusion.ffs.bpw.db.Customer;
import com.ffusion.ffs.bpw.interfaces.ACHConsts;
import com.ffusion.ffs.bpw.interfaces.ACHFileInfo;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.ScheduleHist;
import com.ffusion.ffs.bpw.interfaces.handlers.IACHFileAdapter;
import com.ffusion.ffs.bpw.util.ACHAdapterConsts;
import com.ffusion.ffs.bpw.util.ACHAdapterUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.scheduling.db.ScheduleHistory;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.ffusion.ffs.util.FFSStringTokenizer;
import com.ffusion.util.logging.AuditLogTranTypes;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

public class ACHFileAdapter implements IACHFileAdapter, ACHAdapterConsts {

    private String _exportFileBase = "";
    private String _tempFileBase = "";
    private String _errorFileBase = "";
    private int _logLevel;
    private String _currentBatchEntryClassCode;
    private String _achFileExtension;
    private String _cutOffId = null;
    private String _processId = null;
    private ScheduleHist schHist = null;
    private String InstructionType=DBConsts.ACHFILETRN;

    /**
     * This method is called when the ACHFile handler is created. It is
     * used to perform initialization if necessary.
     */
    public void start(String cutOffId, String processId)
    throws Exception
    {
    	 String methodName = "ACHFileAdapter.start";
         long start = System.currentTimeMillis();
         int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log( "ACHFileAdapter.start is called", FFSDebug.PRINT_DEV );

        //save cutOffId and processId to _cutOffId, _processId
        _cutOffId = cutOffId;
        _processId = processId;

        String exportDir = ACHAdapterUtil.getProperty( DBConsts.ACH_EXPORT_DIR, DEFAULT_EXPORT_DIR );

        // Create exportDir it does not exist, add File.separator
        _exportFileBase = ACHAdapterUtil.getFileNameBase( exportDir );

        String tempDir = _exportFileBase + this.STR_TEMP_DIR_NAME;

        _tempFileBase = ACHAdapterUtil.getFileNameBase(tempDir);

        String errorDir = ACHAdapterUtil.getProperty( DBConsts.ACH_ERROR_DIR, DEFAULT_ERROR_DIR );

        // Create errorDir it does not exist, add File.separator
        _errorFileBase = ACHAdapterUtil.getFileNameBase( errorDir );

        PropertyConfig propertyConfig = (PropertyConfig)FFSRegistry.lookup( BPWResource.PROPERTYCONFIG);
        _logLevel = propertyConfig.LogLevel;

        _achFileExtension = ACHAdapterUtil.getProperty(DBConsts.ACH_EXPORT_ACH_UPLOAD_FILE_FILE_EXTENSION,
                                                       DBConsts.DEFAULT_ACH_FILE_EXTENSION);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }
    /**
     * Process ACH Files
     *
     * @param achFileInfos
     * @param dbh    - Database connection holder
     *
     *               NB: Do NOT modify the object dbh, or commit the changes with dbh.conn
     *               in ANY part of the IACHFileAdapter implementation. This could cause
     *               unexpected results and behaviors with the entire BPW system.
     * @exception Exception
     */
    public void processACHFiles( ACHFileInfo[] achFileInfos,String InstructionType, FFSConnectionHolder dbh )
    throws Exception

    {
    	 String methodName = "ACHFileAdapter.processACHFiles";
         long start = System.currentTimeMillis();
         int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log( "ACHFileAdapter.processACHFiles is called", FFSDebug.PRINT_DEV );
        this.InstructionType=InstructionType;
        if ( achFileInfos == null ) {
            return;
        }

        for ( int i = 0; i < achFileInfos.length; i++ ) {
            // process one by one
            processACHFile( achFileInfos[i], dbh );
        }
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    /**
     * This method is called for housekeeping purposes when the BPW server
     * is shut down.
     */
    public void shutdown()
    throws Exception
    {
        FFSDebug.log( "ACHFileAdapter.shutdown is called", FFSDebug.PRINT_DEV );
    }


    /**
    * Process an ACH File
    *
    * @param achFileInfo
    * @param dbh    - Database connection holder
    *
    *               NB: Do NOT modify the object dbh, or commit the changes with dbh.conn
    *               in ANY part of the IACHFileAdapter implementation. This could cause
    *               unexpected results and behaviors with the entire BPW system.
    * @exception Exception
    */
    private void processACHFile( ACHFileInfo achFileInfo, FFSConnectionHolder dbh )
    throws Exception

    {
        String mName = "ACHFileAdapter.processACHFile: ";
        if ( achFileInfo == null ) {
            return;
        }

        String fileId = achFileInfo.getFileId();
        if ( fileId == null ) {
            return;
        }
        String _fiid = achFileInfo.getFiId();
        FFSDebug.log(mName + "File id: " + fileId, FFSDebug.PRINT_DEV);

        boolean isResubmit = achFileInfo.getMemo() != null && achFileInfo.getMemo().get("IsResubmit") != null;

        achFileInfo = ACHFile.getACHFileInfo(dbh, achFileInfo, false );

        if ( achFileInfo.getStatusCode() != ACHConsts.SUCCESS ) {
            FFSDebug.log(mName + "Can not get ACH File Info from DB, skipping this file: " + fileId, FFSDebug.PRINT_ERR);
            return;
        }

        if (achFileInfo.getCustomerId() != null && !Customer.isActive(achFileInfo.getCustomerId(), dbh))
        {
            ACHFile.updateFileStatus(achFileInfo, DBConsts.CANCELEDON, dbh);
            FFSDebug.log(mName + "Customer is not active, cancelling this file: " + fileId, FFSDebug.PRINT_ERR);
            return;
        }

        String fileName = ACHFile.getACHFileExportFileName(dbh, fileId, STR_ACH_FILE_SEPARATOR );

        if (fileName == null || fileName.length() == 0) {

            FFSDebug.log(mName + "Can not get export file name, skipping this file: " + fileId, FFSDebug.PRINT_ERR);
            
            return;
        }

        fileName += STR_EXPORT_FILE_SUFFIX;
        // add achFileExtension if it is not null and not empty
        if (_achFileExtension != null && _achFileExtension.length() > 0) {
            fileName += STR_ACH_FILE_SEPARATOR + _achFileExtension;
        }

        // get temp export file name
        String fullTempFileName = getFullTempFileName(fileName);

        FFSDebug.log(mName + "Temp file name : " + fullTempFileName, FFSDebug.PRINT_DEV);

        // get the min chunk id
        int minChunkId = ACHFile.getMinACHFileChunkId(dbh,fileId );

        FFSDebug.log(mName + "min chunk id: " + minChunkId, FFSDebug.PRINT_DEV);

        // get the max chunk id
        int maxChunkId = ACHFile.getMaxACHFileChunkId(dbh,fileId );

        FFSDebug.log(mName + "max chunk id: " + maxChunkId, FFSDebug.PRINT_DEV);

        // Open the file
        boolean append = true;
        PrintWriter pw = new PrintWriter( new FileWriter(fullTempFileName, append ) );

        try {
            for ( int i = minChunkId; i <= maxChunkId; i++ ) {
                String chunkContent = ACHFile.getACHFileChunkByFileIdAndChunkId(dbh,fileId,i);

                if ( ( chunkContent == null ) || ( chunkContent.length() == 0 ) ) {
                    continue;
                }
                // beatify chunkContent
                //

                FFSStringTokenizer ffsStrTok = null;

                if (FFSStringTokenizer.hasDelimeter( chunkContent, ACHConsts.REC_DELIMS)) {
                    ffsStrTok = new FFSStringTokenizer( chunkContent, ACHConsts.REC_DELIMS);
                } else {
                    ffsStrTok = new FFSStringTokenizer( chunkContent, ACHConsts.ACH_RECORD_LEN);
                }

                StringBuffer strBuffer = new StringBuffer();
                while (ffsStrTok.hasMoreTokens()) {

                    String oneLine = ffsStrTok.nextToken();
                    strBuffer.append( oneLine ).append( DBConsts.LINE_SEPARATOR );

                }
                pw.write( strBuffer.toString() );

                // change the status of this chunck to be POSTEDON
                ACHFile.updateACHFileChunkStatus(dbh,fileId,i, DBConsts.POSTEDON);

            }
        } catch ( FFSException ffse ) {
        	
            throw ffse;
        } finally {
            pw.close();
        }

        // Move the file from temp folder to export folder
        // Get export full file name
        // Check whether this file exist or not, if exists, move it error folder
        String fullFileName = getFullFileName(fileName);

        FFSDebug.log(mName + "Export file name : " + fullFileName, FFSDebug.PRINT_DEV);

        File tempFile = new File(fullTempFileName);
        File exportFile = new File(fullFileName);
        tempFile.renameTo(exportFile);

        // change the status of this file to be POSTEDON
        // also update the ProcessId and export file name
        achFileInfo.setExportFileName(fullFileName);
        if (!isResubmit)
            ACHFile.updateFilePostedOn(achFileInfo, _processId, dbh );

        // do auditlog this transaction
        doTransAuditLog(dbh,achFileInfo, (!isResubmit?"Successfully process an ACH file.":"Successfully resubmit an ACH file."));

        //do the FM logging
        ACHAdapterUtil.doFMLoggingForACH(dbh, exportFile.getCanonicalPath(), mName);
        logScheduleHist(dbh, exportFile.getName(), _fiid);

    }

    private void logScheduleHist(FFSConnectionHolder dbh, String fileName, String fiID)
    {
        // Get the last entry in SCH_Hist table
        // by fiId, InstructionType
        try {
            if (schHist == null)
                schHist = ScheduleHistory.getLastScheduleHist( fiID, InstructionType );
            schHist.FileName = fileName;
            schHist.EventType = DBConsts.SCH_EVENTTYPE_PROCESSINGFILEGENERATED;
            ScheduleHistory.createScheduleHist(dbh,schHist);
        } catch (Exception e) {
            String trace = FFSDebug.stackTrace( e );
            FFSDebug.log( "*** ACHFileAdapter.logScheduleHist: exception:" + trace, FFSConst.PRINT_DEV );
            return;
        }
    }


    /**
     * Get export full file name
     * Check whether this file exist or not, if exists, move it error folder
     *
     * @return
     */
    private final String getFullFileName( String fileName )
    throws Exception
    {

        String fullFileName = _exportFileBase + fileName;

        // check whether this file exists or not
        File exportFile = new File( fullFileName );

        if ( exportFile.exists() ) {
            FFSDebug.log( "ACHFileAdapter.getFullFileName, export ACH file exist: " + fullFileName, FFSDebug.PRINT_INF );
            // Move this file to error, and add System.getCurrentMis to the end of this file
            String fullErrorFileName = _errorFileBase
                                       + fileName
                                       + STR_EXPORT_FILE_SUFFIX
                                       + STR_ACH_FILE_SEPARATOR
                                       + System.currentTimeMillis();

            File errorFile = new File( fullErrorFileName );
            exportFile.renameTo( errorFile );

            FFSDebug.log( "ACHFileAdapter.getFullFileName, the existing file has been moved to  " + fullErrorFileName, FFSDebug.PRINT_INF );
        }

        return fullFileName;
    }

    /**
     * Get full file name in temp, if the file exists, delete it
     *
     * @param fileName
     * @return
     * @exception Exception
     */
    private final String getFullTempFileName(String fileName)
    throws Exception
    {
        String mName = "ACHFileAdapter.getFullTempFileName: ";
        String fullFileName = _tempFileBase + fileName;
        File exportFile = new File(fullFileName);
        if (exportFile.exists()) {
            // delete it is ok
            FFSDebug.log(mName + " export ACH file exist. Deleting: " + fullFileName, FFSDebug.PRINT_INF);

            exportFile.delete();
        }
        return fullFileName;
    }

    /**
     * Do transaction audit log which logs this information to
     * audit_log table in CB
     *
     * @param achBatchInfo
     */
    /**
     * Do transaction audit log which logs this information to
     * audit_log table in CB
     *
     * @param fileInfo
     */
    private void doTransAuditLog(FFSConnectionHolder dbh, ACHFileInfo fileInfo,
                                 String preDesc )
    throws FFSException
    {
        if (_logLevel >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE) {
            // load file header and control to get more info
            fileInfo = ACHFile.getACHFileInfo(dbh,fileInfo,true);
            int tranType = AuditLogTranTypes.BPW_ACHFILETRN;
            if (fileInfo.getFileStatus().equals(DBConsts.POSTEDON)) {
                // This is for backward compatibility
                tranType = AuditLogTranTypes.BPW_ACHBATCH_SENT;
            }
            ACHFile.doTransAuditLog(dbh, fileInfo,
                                    fileInfo.getSubmittedBy(),
                                    preDesc,
                                    tranType);
        }
    }
}
