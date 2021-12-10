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

import java.util.ArrayList;

import com.ffusion.ffs.bpw.achagent.ACHAgent;
import com.ffusion.ffs.bpw.audit.TransAuditLog;
import com.ffusion.ffs.bpw.db.ACHFI;
import com.ffusion.ffs.bpw.db.ACHLeadDays;
import com.ffusion.ffs.bpw.db.BPWFI;
import com.ffusion.ffs.bpw.db.Customer;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.db.ExternalTransferAccount;
import com.ffusion.ffs.bpw.db.ExternalTransferCompany;
import com.ffusion.ffs.bpw.db.SmartCalendar;
import com.ffusion.ffs.bpw.db.Transfer;
import com.ffusion.ffs.bpw.handler.OSGIUtil;
import com.ffusion.ffs.bpw.interfaces.ACHAddendaInfo;
import com.ffusion.ffs.bpw.interfaces.ACHConsts;
import com.ffusion.ffs.bpw.interfaces.ACHFIInfo;
import com.ffusion.ffs.bpw.interfaces.ACHRecordInfo;
import com.ffusion.ffs.bpw.interfaces.BPWFIInfo;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.CustomerInfo;
import com.ffusion.ffs.bpw.interfaces.CutOffInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.ETFACHBatchInfo;
import com.ffusion.ffs.bpw.interfaces.ETFACHEntryInfo;
import com.ffusion.ffs.bpw.interfaces.ETFACHFileInfo;
import com.ffusion.ffs.bpw.interfaces.ExtTransferAcctInfo;
import com.ffusion.ffs.bpw.interfaces.ExtTransferAcctList;
import com.ffusion.ffs.bpw.interfaces.ExtTransferCompanyInfo;
import com.ffusion.ffs.bpw.interfaces.MacGenerator;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.ScheduleHist;
import com.ffusion.ffs.bpw.interfaces.TransferInfo;
import com.ffusion.ffs.bpw.interfaces.handlers.IExternalTransferAdapter;
import com.ffusion.ffs.bpw.interfaces.util.AccountUtil;
import com.ffusion.ffs.bpw.master.LimitCheckApprovalProcessor;
import com.ffusion.ffs.bpw.util.ACHAdapterConsts;
import com.ffusion.ffs.bpw.util.ACHAdapterUtil;
import com.ffusion.ffs.bpw.util.BPWUtil;
import com.ffusion.ffs.bpw.util.resources.BPWLocaleUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.scheduling.db.ScheduleHistory;
import com.ffusion.ffs.util.ACHBatchCache;
import com.ffusion.ffs.util.ACHFileCache;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.ffusion.ffs.util.FFSUtil;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ACHMsgSet.TypeBatchControlRecord;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ACHMsgSet.TypeBatchHeaderRecord;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ACHMsgSet.TypeCCDAddendaRecord;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ACHMsgSet.TypeCCDEntryDetailRecord;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ACHMsgSet.TypePPDAddendaRecord;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ACHMsgSet.TypePPDEntryDetailRecord;
import com.ffusion.util.logging.AuditLogRecord;
import com.ffusion.util.logging.AuditLogTranTypes;
import com.sap.banking.common.interceptors.PerfLoggerUtil;
import com.sap.banking.io.beans.File;
import com.sap.banking.io.provider.interfaces.FileHandlerProvider;

public class ExternalTransferAdapter implements IExternalTransferAdapter, ACHAdapterConsts {
    // logLevel for auditLog
    private int _logLevel;
    // keeps infomation of file control for ODFIACHID
    ACHFileCache _fileCache = null;
    // export, temp and error directories. For example, C:\FinancialFusion\export\
    // C:\FinancialFusion\temp\ or C:\FinancialFusion\error\
    private String _exportFileBase;
    private String _tempFileBase;
    private String _errorFileBase;
    private String _tempFileName;
    private ACHFIInfo _achFIInfo;
    private CutOffInfo _cutOffInfo;
    private BPWFIInfo _bpwFIInfo;
    private String _processId;
    private ACHAgent _achAgent = null;
    private String _achVersion = "MsgSet";
    private String _InterExtension = null;
    private int _pageSize = 0;
    private ArrayList _records;
    private String _nextProcessDt = null;
    private boolean _reRunCutOff = false;
    private boolean _crashRecovery = false;
    private String _fIId = null;
    private String traceNumCount = "000000000000001";

    private ACHRecordInfo _currentBatchHeader = null;

    private String _fileId = null; //Id of ETF ACHFile record

    /**
     * This method is called when the Inter handler is created. It is
     * used to perform initialization if necessary.
     */
    public void start()
    throws Exception
    {
    	
        
        String methodName = "ExternalTransferAdapter.start: " ;
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log( methodName + "is called", FFSDebug.PRINT_DEV );

        // get exportDir it does not exist, add File.separator
        String exportDir = ACHAdapterUtil.getProperty( DBConsts.ACH_EXPORT_DIR,
                                                              DEFAULT_EXPORT_DIR );
        _exportFileBase = ACHAdapterUtil.getFileNameBase( exportDir );

        // get temp file base and create tempDir is it doesn't exist, add File.separator
        String tempDir = _exportFileBase + STR_TEMP_DIR_NAME;
        _tempFileBase = ACHAdapterUtil.getFileNameBase( tempDir );

        // get error file name base and create errorDir if it doen't exist
        String errorDir = ACHAdapterUtil.getProperty( DBConsts.ACH_ERROR_DIR,
                                                              DEFAULT_ERROR_DIR );
        _errorFileBase = ACHAdapterUtil.getFileNameBase( errorDir );

        _fileCache = new ACHFileCache();

        _achVersion = ACHAdapterUtil.getProperty( DBConsts.ACH_VERSION,
                                                          DBConsts.DEFAULT_ACH_VERSION );

        _achAgent = (ACHAgent)FFSRegistry.lookup( BPWResource.BPWACHAGENT );

        _InterExtension = ACHAdapterUtil.getProperty(DBConsts.EXTERNAL_TRANSFER_FILE_EXTENSION,
                                                             DBConsts.DEFAULT_EXTERNAL_TRANSFER_FILE_EXTENSION);
        if ( _achAgent == null ) {
            FFSDebug.log("InterAdapter.start: ACHAgent has not been started! Terminating process!" , FFSDebug.PRINT_ERR);
            PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
            throw new FFSException( "InterAdapter.start: ACHAgent has not been started! Terminating process!" );
        }
        // get size
        _pageSize = ACHAdapterUtil.getPropertyInt( DBConsts.BATCHSIZE,
                                                   DBConsts.DEFAULT_BATCH_SIZE );
        _pageSize = 1; // test
        _records = new ArrayList();

        PropertyConfig propertyConfig = (PropertyConfig)FFSRegistry.lookup( BPWResource.PROPERTYCONFIG);
        _logLevel = propertyConfig.LogLevel;
        
        FFSDebug.log(methodName + "successful", FFSDebug.PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    /**
     * Method processOneCutOff():
     * process export file header
     * get transfers by startDate, by status, order by customerId, recipient type
     * load payments for the one customer
     * processOneCustomer
     * commit
     * processPrenote;// create prenote entry for ExternalAccts who require prenote
     * process file control
     *
     * @param dbh
     * @param cutOffInfo
     * @param FIId
     * @param processId
     * @param createEmptyFile
     * @exception Exception
     */

    public void processOneCutOff(FFSConnectionHolder dbh,
                                 CutOffInfo cutOffInfo,
                                 String FIId,
                                 String processId,
                                 boolean createEmptyFile,
                                 boolean reRunCutOff,
                                 boolean crashRecovery ) throws Exception
    {
        String methodName = "ExternalTransferAdapter.processOneCutOff: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log(methodName + "start. CutOffId = " + cutOffInfo.getCutOffId() +
                     ". FIID = " + FIId, FFSDebug.PRINT_DEV);

        ETFACHFileInfo fileInfo = null;

        _cutOffInfo = cutOffInfo; // get CutOffInfo for this process
        _processId = processId; // get processId for this process
        _reRunCutOff = reRunCutOff;
        _crashRecovery = crashRecovery;
        _fIId = FIId;

        // get ACHFIInfo with flag CutOffDIF is on to get the file name
        _achFIInfo = getCutOffACHFIInfo(dbh,FIId);
        _bpwFIInfo = BPWFI.getBPWFIInfo(dbh,FIId);
        _tempFileName = getTempFileName(_achFIInfo);
        traceNumCount = "" + System.currentTimeMillis();


        FFSDebug.log(methodName + "crashRecovery" + crashRecovery,
                     FFSDebug.PRINT_DEV );

        // hwu: TODO start
        if ( crashRecovery == true ) {
            // delete record from ETF_ACHFile by process Id
            try {

                FFSDebug.log(methodName + "Deleting ETFACHFile by processId="
                             + _processId, FFSDebug.PRINT_DEV );

                Transfer.deleteETFACHFileByProcessId(dbh, _processId);

            } catch (FFSException e) {
                FFSDebug.log(methodName + "failed to delete ETF ACHFile record "
                             + e, FFSDebug.PRINT_ERR );
                PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
                throw e;
            }

        }

        FFSDebug.log(methodName + "reRunCutOff" + reRunCutOff,
                     FFSDebug.PRINT_DEV );

        // if reruncut off is false
        // Create a new record in ETF_ACHFile
        if ( !reRunCutOff ) {
            fileInfo = new ETFACHFileInfo();
            fileInfo.setCreateDate( DBUtil.getCurrentLogDate() );
            fileInfo.setProcessId(_processId);
            fileInfo.setFileStatus(DBConsts.PROCESSING);

            int fileNameOffSet = 0;
            if (_tempFileBase != null) {
                fileNameOffSet = _tempFileBase.length();
            }
            String exportFileName = _tempFileName.substring( fileNameOffSet );
            fileInfo.setExportFileName(exportFileName);

            try {

                FFSDebug.log(methodName + "adding ETFACHFile", FFSDebug.PRINT_DEV );

                fileInfo = Transfer.addETFACHFile(dbh, fileInfo);
                _fileId = fileInfo.getFileId();

            } catch (FFSException e) {
                FFSDebug.log(methodName + "failed to create ETF ACHFile record "
                             + e, FFSDebug.PRINT_ERR );
                PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
                throw e;
            }
        }
        // hwu: TODO end

        // Get ExtTransferCompanyInfo by fiid in pages
        String startCompId = "0";

        ExtTransferCompanyInfo [] extCompList
        = ExternalTransferCompany.getExtTransferCompArrayByFIId(dbh,
                                                                FIId,
                                                                startCompId,
                                                                _pageSize);
        FFSDebug.log(methodName + "extCompList.length="
                     + extCompList.length, FFSDebug.PRINT_DEV );

        while (extCompList != null) {

            int len = extCompList.length;
            if (len == 0) { // there is no company found, exit the while loop
                break;
            }

            for (int i = 0; i < len; i++) {
                try {
                    // process one customer,
                    // process one company, since each customer only has
                    // one company
                    processOneCustomer(dbh, extCompList[i] );

                } catch (FFSException e) {
                    FFSDebug.log(methodName + "failed to process customer "
                                 + extCompList[i].getCustomerId() + ". Error: " + e,
                                 FFSDebug.PRINT_ERR );
                    PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
                    throw e;
                }
                writeRecordsInFile(dbh, _records, extCompList[i].getCustomerId(),
                                   reRunCutOff);
                _records = new ArrayList();
                dbh.conn.commit();
            }

            if (len >= _pageSize) {
                // set the new startCompId and get the next page of ExtTransferCompanyInfo
                startCompId = extCompList[len-1].getCompId();
                extCompList = ExternalTransferCompany.getExtTransferCompArrayByFIId(dbh,
                                                                                    FIId,
                                                                                    startCompId,
                                                                                    _pageSize);
            } else {
                // the number of ExtTransferCompanyInfo found at last time is smaller than
                // the pageSize. It means there is no more ExtTransferCompanyInfo left
                break;
            }
        }

        if (createEmptyFile == true || _fileCache.fileBatchCount > 0) {
            // create a fileControl and append it to the file.
            writeFileControl();
            moveToExport(dbh);

            // if reruncut off is false
            // Update the ETF_ACHFile record with control information
            if ( !reRunCutOff ) {
                fileInfo.setBatchCount( String.valueOf( _fileCache.fileBatchCount ) );
                fileInfo.setRecordCount( String.valueOf( _fileCache.fileEntryCount ) );
                fileInfo.setTotalDebits( String.valueOf( _fileCache.fileDebitSum ) );
                fileInfo.setNumberOfDebits( String.valueOf( _fileCache.debitCount ) );
                fileInfo.setTotalCredits( String.valueOf( _fileCache.fileCreditSum ) );
                fileInfo.setNumberOfCredits( String.valueOf( _fileCache.creditCount ) );
                fileInfo.setFileStatus(DBConsts.POSTEDON);

                try {
                    FFSDebug.log(methodName + "updating ETFACHFile", FFSDebug.PRINT_DEV );

                    fileInfo = Transfer.updateETFACHFileWithCtrlInfo(dbh, fileInfo);

                } catch (FFSException e) {
                    FFSDebug.log(methodName
                                 + "failed to update ETF ACHFile record "
                                 + e, FFSDebug.PRINT_ERR );
                    PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
                    throw e;
                }
            }
        } else {
            // delete temp file
            deleteTempFile();
        }

        //Batch count is zero means no external transfer record has been
        //processed. So delete the already created ETF_ACHFile record as that
        //does not have any batch in it.
        if (_fileCache.fileBatchCount == 0) {

            try {

                FFSDebug.log(methodName + "deleting blank  ETFACHFile",
                             FFSDebug.PRINT_DEV );

                fileInfo = Transfer.deleteETFACHFile(dbh, fileInfo);

            } catch (FFSException e) {
                FFSDebug.log(methodName + "failed to delete ETF ACHFile record "
                             + e, FFSDebug.PRINT_ERR );
                PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
                throw e;
            }
        }

        FFSDebug.log(methodName + "done.", FFSDebug.PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    /**
     * calculate the nextProcessDate which is used to get matured transfers.
     * All transfers are processed 2 days earlier then their dateToPost.
     */
    private void getNextProcessDate(CustomerInfo customerInfo, String recipientType) throws FFSException
    {
        if (_reRunCutOff == true) {
            // for re-run cutoff, we get the current date as the _nextProcessDt
            _nextProcessDt = FFSUtil.getDateString(DBConsts.DUE_DATE_FORMAT);
        } else {
            _nextProcessDt = BPWUtil.getDateInNewFormat(_cutOffInfo.getNextProcessTime(),
                                                        DBConsts.START_TIME_FORMAT,
                                                        DBConsts.DUE_DATE_FORMAT);
        }
        int nextProcessDtInt = Integer.parseInt(_nextProcessDt);

        // todo: get EffectiveDate ACH Lead Days - from Business
        int serviceClassCode = 200;     // 200 = Mixed, since External Transfers are ALWAYS one debit & one credit
        String secCode = "CCD";     // Assume BUSINESS

        if (recipientType.equals(DBConsts.EXT_TRN_ACCT_RECIPIENT_TYPE_BUSINESS))
            secCode = "CCD";
        if (recipientType.equals(DBConsts.EXT_TRN_ACCT_RECIPIENT_TYPE_PERSONAL))
            secCode = "PPD";
        // we can just " + Addenda" because all of the SEC Codes that are credit are stored as " + Addenda" in the database
        if (secCode != null && secCode.length() == 3)
            secCode += " + Addenda";

        // get lead time for external transfer
        int daysForward = ACHLeadDays.getProcessLeadDay(customerInfo, secCode, serviceClassCode);

        //move 2 business days ahead
        for ( int i = 0; i < daysForward; i++ ) {
            try {
                nextProcessDtInt = SmartCalendar.getACHBusinessDay(_fIId,nextProcessDtInt,true);
            } catch (Exception e) {
                String err = FFSDebug.stackTrace(e); // extract the stack
                FFSDebug.log(err, FFSDebug.PRINT_ERR);
                throw new FFSException (e, err);
            }
        }

        nextProcessDtInt *= 100;
        _nextProcessDt = Integer.toString(nextProcessDtInt);
    }



    /**
     * Process one customer or company:
     * There is only on esternal transfer company for one customer.
     * Information from this company is for batch header.
     *
     * Prorcess one batch for business, process one batch for personal
     *
     * @param dbh
     * @param companyInfo
     * @exception FFSException
     */

    private void processOneCustomer(FFSConnectionHolder dbh,
                                    ExtTransferCompanyInfo companyInfo)
    throws FFSException
    {
        String methodName = "ExternalTransferAdapter.processOneCustomer: ";

        String customerId = companyInfo.getCustomerId();

        FFSDebug.log(methodName + "start, CustomerId =" + customerId, FFSDebug.PRINT_DEV);
        FFSDebug.log(methodName + " External CompanyACHId : " + companyInfo.getCompACHId(), FFSDebug.PRINT_DEV );

        CustomerInfo customerInfo = Customer.getCustomerInfo(customerId, dbh, companyInfo);

        //sanity check
        if (customerInfo == null) {
	        String msg = BPWLocaleUtil.getMessage(ACHConsts.CUSTOMER_DOES_NOT_EXIST, new String[]{customerId}, BPWLocaleUtil.TRANSFER_MESSAGE);
            throw new FFSException(ACHConsts.CUSTOMER_DOES_NOT_EXIST,
                                   msg);
        }
        FFSDebug.log(methodName + " customerId : " + customerInfo.customerID,
                     FFSDebug.PRINT_DEV );

        // process one batch: business external accounts
        processOneBatch(dbh,
                        customerInfo,
                        companyInfo,
                        DBConsts.EXT_TRN_ACCT_RECIPIENT_TYPE_BUSINESS );
        // process one batch: personal external accounts
        processOneBatch(dbh,
                        customerInfo,
                        companyInfo,
                        DBConsts.EXT_TRN_ACCT_RECIPIENT_TYPE_PERSONAL );

    }

    /**
     * 1. Get all external transfer accounts
     * 2. Check prenote
     * 3. Process prenote
     * For each such external transfer account, process its prenote.
     * process transfers
     *
     *
     * @param dbh
     * @param customerInfo
     * @param companyInfo
     * @exception FFSException
     */
    private void processOneBatch(FFSConnectionHolder dbh,
                                 CustomerInfo customerInfo,
                                 ExtTransferCompanyInfo companyInfo,
                                 String recipientType )
    throws FFSException
    {
        String methodName = "ExternalTransferAdapter.processOneBatch: ";

        FFSDebug.log(methodName + "start, recipientType =" + recipientType, FFSDebug.PRINT_DEV);

        // get all active External Accounts by recipient type
        String [] customerIdList = new String[1];
        customerIdList[0] = customerInfo.customerID;

        ExtTransferAcctList acctList = new ExtTransferAcctList();
        acctList.setCustomerId( customerIdList );
        acctList.setRecipientType( recipientType );

        acctList = ExternalTransferAccount.getExternalTransferAccountList(dbh, acctList);

        if (acctList.getStatusCode() != DBConsts.SUCCESS ) {
            FFSDebug.log(methodName + ": no external accounts found for this customer: customerId=" + customerInfo.customerID, FFSDebug.PRINT_DEV);
            return;
        }

        ExtTransferAcctInfo[] extAcctInfos = acctList.getExtTransferAccts();

        FFSDebug.log(methodName + "extAcctInfos length=" + extAcctInfos.length,
                     FFSDebug.PRINT_DEV);

        // reset batch flags
        this._fileCache.batchHeaderFound = false; // new batch  started
        this._fileCache.batchCtrlFound = false;
        // initialize BatchCache
        this._fileCache.batchCache = new ACHBatchCache();


        if (extAcctInfos != null ) {
            for (int i = 0; i < extAcctInfos.length; i++) {

                if ( this._reRunCutOff == false ) {
                    processOneAccountNormal(dbh,customerInfo,companyInfo,recipientType, extAcctInfos[i] );
                } else {
                    processOneAccountRerunCutOff(dbh,customerInfo,companyInfo,recipientType, extAcctInfos[i] );
                }

            }
        }
        if (  this._fileCache.batchHeaderFound == true ) {

            // batch header  created
            // create batch control
            ACHRecordInfo batchControl = createBatchControl( companyInfo, this._currentBatchHeader );
            // add it in records list
            _records.add(batchControl);
            this._fileCache.batchCtrlFound = true;
        }


    }

    /**
     * Process one account
     * @param dbh
     * @param customerInfo
     * @exception FFSException
     */
    private void processOneAccountNormal(FFSConnectionHolder dbh,
                                         CustomerInfo customerInfo,
                                         ExtTransferCompanyInfo companyInfo,
                                         String recipientType,
                                         ExtTransferAcctInfo acctInfo )
    throws FFSException
    {
        String methodName = "ExternalTransferAdapter.processOneAccount: ";
        FFSDebug.log(methodName + "start, accountId =" + acctInfo.getAcctId(), FFSDebug.PRINT_DEV);

        // get the process date:
        // transfers whose DateToPost is equal or less than this date
        // will be processed
        getNextProcessDate(customerInfo, recipientType);

        //
        // Get all the transfers: Unprocessed and processed (crash recovery)
        // if crash recover, the prenote transfer is canceled
        TransferInfo[] transfers = this.getTransfersForAccount(dbh, customerInfo.customerID, acctInfo );


        // 2. Check Prenote, and process prenote if necessary,
        //    also return true, if prenote status is not SUCCESS
        if ( needToProcessPrenote( acctInfo ) == true ) {

            // create prenote transfer and put it into db and memory
            processPrenoteExtTransferAcct(dbh, acctInfo,companyInfo, recipientType,customerInfo );
        }

        // 3. Check whether we can continue to process transfers
        if ( isPrenoteMatured( acctInfo ) == false ) {

            // false: Prenote is not matured
            // we need to fail all the pending transactions

            FFSDebug.log(methodName + ": Prenote is not mature, fail transfers. ", FFSDebug.PRINT_DEV);
            String errorMsg = "Failed to process Transfer because its account's prenote has not matured.";
            this.failTransferInfos(dbh,transfers, errorMsg );

        } else {

            // Not prenote process is required
            // or it is matured

            // process them as usual
            processUserTransfers(dbh,customerInfo,companyInfo,recipientType,acctInfo,transfers );


        } // not prenote end

        FFSDebug.log(methodName + "done. Current number of transfers: " + _records.size() , FFSDebug.PRINT_DEV);

    }

    /**
 * Process one account
 * @param dbh
 * @param customerInfo
 * @exception FFSException
 */
    private void processOneAccountRerunCutOff(FFSConnectionHolder dbh,
                                              CustomerInfo customerInfo,
                                              ExtTransferCompanyInfo companyInfo,
                                              String recipientType,
                                              ExtTransferAcctInfo acctInfo )
    throws FFSException
    {
        String methodName = "ExternalTransferAdapter.processOneAccountRerunCutOff: ";
        FFSDebug.log(methodName + "start, accountId =" + acctInfo.getAcctId(), FFSDebug.PRINT_DEV);


        //
        // Get transfers: processed
        TransferInfo[] transfers = Transfer.getProcessedTransfersForAccount(dbh,
                                                                            acctInfo.getAcctId(),
                                                                            _processId);

        // process all the transfers, could include prenote

        // process them as usual
        processUserTransfers(dbh,customerInfo,companyInfo,recipientType,acctInfo,transfers );



        FFSDebug.log(methodName + "done. Current number of transfers: " + _records.size() , FFSDebug.PRINT_DEV);

    }

    /**
     * Process transfers for one account
     *
     * @param dbh
     * @param customerInfo
     * @param companyInfo
     * @param recipientType
     * @param acctInfo
     * @exception FFSException
     */
    private void processUserTransfers(FFSConnectionHolder dbh,
                                      CustomerInfo customerInfo,
                                      ExtTransferCompanyInfo companyInfo,
                                      String recipientType,
                                      ExtTransferAcctInfo acctInfo,
                                      TransferInfo[] transfers   )
    throws FFSException
    {

        String methodName = "ExternalTransferAdapter.processUserTransfers: ";
        FFSDebug.log(methodName + "start, accountId =" + acctInfo.getAcctId(), FFSDebug.PRINT_DEV);
        if ( transfers == null ) {
            return;
        }

         if (!DBConsts.ACTIVE.equals(customerInfo.status))
         {
             FFSDebug.log(methodName + ": customer is not active, fail transfers. ", FFSDebug.PRINT_DEV);
             String errorMsg = "Failed to process Transfer because customer is not active.";
             this.failTransferInfos(dbh,transfers, errorMsg );
             return;
         }


        int len = transfers.length;
        FFSDebug.log(methodName + "start, len =" + len, FFSDebug.PRINT_DEV);
        // process each transfer
        for (int i = 0; i < len; i++) {
            TransferInfo transfer = transfers[i];

            if (validateTransfer(dbh, transfer,customerInfo ) == false) {
                // validateTransfer is already failing this transfer
                continue;
            }
            // start processing this transfer
            transfer.setAccountToNum(acctInfo.getAcctNum());
            transfer.setAccountToType(acctInfo.getAcctType());

            // check whether we need to create batch header or not

            if ( this._fileCache.batchHeaderFound == false ) {

                this._currentBatchHeader = this.createBatchHeader( companyInfo ,recipientType);

                _records.add( this._currentBatchHeader );

                this._fileCache.batchHeaderFound = true; // indicate batch header is created
            }

            processOneTransfer(dbh,customerInfo, transfer,acctInfo);

        }
    }




    /**
     * Check whether we need to create prenote entry or not
     *
     * @param acctInfo
     * @return
     */
    private boolean needToProcessPrenote(  ExtTransferAcctInfo acctInfo )
    {

        String methodName = "ExternalTransferAdapter.needToProcessPrenote: ";
        String prenoteStatus = acctInfo.getPrenoteStatus();
        FFSDebug.log(methodName, " prenote=", acctInfo.getPrenote(), FFSDebug.PRINT_DEV);
        FFSDebug.log(methodName, " prenoteStatus=", prenoteStatus, FFSDebug.PRINT_DEV);

        if ( (acctInfo.getPrenote() != null) &&
             (acctInfo.getPrenote().trim().equalsIgnoreCase("Y") ) ) {
            // no need to create prenote transfer
            return prenoteStatus == null; // need to create prenote transfer
        }
        return false; // do Prenote is not Y
    }

    /**
     * Check whether prenote is matured or not, is required or not
     *
     * @param acctInfo
     * @return
     */
    private boolean isPrenoteMatured(  ExtTransferAcctInfo acctInfo )
    {

        String methodName = "ExternalTransferAdapter.isPrenoteMatured: ";
        String prenoteStatus = acctInfo.getPrenoteStatus();
        FFSDebug.log(methodName, " prenote=", acctInfo.getPrenote(), FFSDebug.PRINT_DEV);
        FFSDebug.log(methodName, " prenoteStatus=", prenoteStatus, FFSDebug.PRINT_DEV);
        if ( (acctInfo.getPrenote() != null) &&
             (acctInfo.getPrenote().trim().equalsIgnoreCase("Y") ) ) {

            // prenote is required
            // not matured
            return (prenoteStatus != null) &&
                    (prenoteStatus.equalsIgnoreCase(DBConsts.EXT_TRN_ACCT_PRENOTE_SUCCESS) != false);
            // else, matured
        } // else, not required
        return true;
    }

    /**
     *
     * @param dbh
     * @param acctInfo
     * @return
     * @exception FFSException
     */
    private TransferInfo[] getTransfersForAccount(FFSConnectionHolder dbh,
                                                  String customerId,
                                                  ExtTransferAcctInfo acctInfo)
    throws FFSException
    {

        String methodName = "ExternalTransferAdapter.getTransfersForAccount: ";

        String acctId = acctInfo.getAcctId();

        FFSDebug.log(methodName + "start. AccountId: " + acctInfo.getAcctId(), FFSDebug.PRINT_DEV);

        ArrayList allTransfers = new ArrayList();

        if ( this._crashRecovery == true ) {

            // get processed entries in case
            TransferInfo[] processedTransfer= Transfer.getProcessedTransfersForAccount(dbh,
                                                                                       acctId,
                                                                                       _processId);
            if ( processedTransfer != null) {
                int len =  processedTransfer.length;
                for (int i = 0; i < len; i++) {

                    // Cancel Prenote (if the location support it).

                    if ( processedTransfer[i].getTransferCategory().equals( DBConsts.PRENOTE_ENTRY) == true ) {

                        // If this entry is prenote entry
                        // set its status to be CANCLEDON
                        processedTransfer[i].setPrcStatus( DBConsts.CANCELEDON );
                        Transfer.updateStatus(dbh,processedTransfer[i], false );


                        // AuditLog
                        this.doTransAuditLog(dbh,processedTransfer[i], "Server crashes last time. Cancel this entry first and the re-proess prenote." );

                        // Update prenote status
                        // add it into db

                        // update location's PrenoteStatus but not its submitted date
                        this.updateAcctPrenote(dbh, acctInfo, null, null );

                    } else {
                        // normal TX, we need to process them
                        allTransfers.add( processedTransfer[i] );
                    }



                }// end for loop
            } // else : there is no processed entry
        } // end crash recovery

        // get unprocessed entries
        TransferInfo[] unprocessedTransfers = Transfer.getUnprocessedTransfersForAcct(dbh,acctId, customerId, _nextProcessDt);

        if ( unprocessedTransfers != null) {
            int len =  unprocessedTransfers.length;
            for (int i = 0; i < len; i++) {
                allTransfers.add( unprocessedTransfers[i] );
            }
        } // else no new user entry found


        TransferInfo[] userTransfers = (TransferInfo[]) allTransfers.toArray( new TransferInfo[0] );

        FFSDebug.log(methodName + "done. Total user entries: " + userTransfers.length, FFSDebug.PRINT_DEV);

        return userTransfers;
    }



    /**
     * Process all companies for this FIId now. This method is called
     * when there is no cutOffId.
     *
     * @param dbh       database connection holder
     * @param FIId
     * @param processId
     * @exception Exception
     */
    public void processRunNow(FFSConnectionHolder dbh, String FIId,
                              String processId,
                              boolean createEmptyFile,
                              boolean reRunCutOff,
                              boolean crashRecovery )
    throws Exception
    {
        String methodName = "ExternalTransferAdapter.processRunNow: ";
        FFSDebug.log(methodName + "start. FIID = " + FIId, FFSDebug.PRINT_DEV);
        _processId = processId;
        _reRunCutOff = reRunCutOff;

        _crashRecovery = crashRecovery;

        _achFIInfo = getCutOffACHFIInfo(dbh,FIId);
        _bpwFIInfo = BPWFI.getBPWFIInfo(dbh,FIId);
        _tempFileName = getTempFileName(_achFIInfo);
        _cutOffInfo = new CutOffInfo();
        _cutOffInfo.setFIId(FIId);
        _cutOffInfo.setInstructionType(DBConsts.ETFTRN);
        _cutOffInfo.setProcessTime(FFSUtil.getDateString(DBConsts.START_TIME_FORMAT));
        _cutOffInfo.setNextProcessTime(FFSUtil.getDateString(DBConsts.START_TIME_FORMAT));
        traceNumCount = "" + System.currentTimeMillis();

        processOneCutOff(dbh,_cutOffInfo,FIId,processId,createEmptyFile,reRunCutOff, crashRecovery);

        FFSDebug.log(methodName + "done.", FFSDebug.PRINT_DEV);
    }

    /**
     * Process prenote for a specified external transfer account
     * 1) Create and add a batch header.
     * 2) Create and add a prenote record.
     * 3) Create and add a batch control.
     * 4) update prenore status
     *
     * @param extTransferAcct
     *               external transfer account
     * @param extTransferComp
     *               external transfer company to create batch header/control
     * @exception FFSException
     */
    private void processPrenoteExtTransferAcct(FFSConnectionHolder dbh,
                                               ExtTransferAcctInfo extTransferAcct,
                                               ExtTransferCompanyInfo extTransferComp,
                                               String recipientType,
                                               CustomerInfo customerInfo )
    throws FFSException
    {
        ACHRecordInfo prenoteRecord = null;

        // check batch header
        if ( this._fileCache.batchHeaderFound == false ) {

            this._currentBatchHeader = this.createBatchHeader( extTransferComp,recipientType);

            // change batch header's service class code to credit only because the common
            // type is MIXED and this batch has only one credit record
            TypeBatchHeaderRecord batchHeaderRecord = (TypeBatchHeaderRecord) this._currentBatchHeader.getRecord();
            batchHeaderRecord.Service_Class_Code = ACHConsts.ACH_CREDITS_ONLY;

            _records.add( this._currentBatchHeader );

            this._fileCache.batchHeaderFound = true; // indicate batch header is created
        }
        if ( recipientType.compareTo(DBConsts.EXT_TRN_ACCT_RECIPIENT_TYPE_BUSINESS) == 0) {
            // this is CCD record

            // there is no prenote trasnfer, create a new one
            prenoteRecord = createPrenoteCCDRecord(dbh,extTransferAcct, customerInfo);

        } else {

            // prenote transfer, create a new one.
            prenoteRecord = createPrenotePPDRecord(dbh,extTransferAcct, customerInfo);
        }
        // we can not create batch control here because, this customer could have
        // more then one account
        // add them in records list
        _records.add(prenoteRecord);
        // _records.add(batchControl);

        // update prenote status in external transfer account to PENDING
        this.updateAcctPrenote(dbh,extTransferAcct,
                               DBConsts.ACH_PAYEE_PRENOTE_PENDING,
                               FFSUtil.getDateString(DBConsts.LASTREQUESTTIME_DATE_FORMAT ) );


    }

    /**
     * Update prenote status and sub date for account
     *
     * @param dbh
     * @param extTransferAcct
     * @param prenoteStatus
     * @param subDate
     */
    private void updateAcctPrenote(FFSConnectionHolder dbh,
                                   ExtTransferAcctInfo extTransferAcct,
                                   String prenoteStatus,
                                   String subDate )

    throws FFSException
    {
        extTransferAcct.setPrenoteStatus( prenoteStatus );

        extTransferAcct.setPrenoteSubDate( subDate );

        extTransferAcct = ExternalTransferAccount.modify(dbh,extTransferAcct);
    }

    /**
     * . Create/add a prenote transfer in db
     * . get transaction code for the prenote. It is credit
     * and depends on account type of external account(Checking/Savings)
     * . Create and CCD ACHRecordInfo and return it.
     *
     * @param dbh    database connection holder to add a transfer in db
     * @param extTransferAcct
     *               external transfer account
     * @return an ACHRecordInfo object
     * @exception FFSException
     */
    private ACHRecordInfo createPrenoteCCDRecord(FFSConnectionHolder dbh,
                                                 ExtTransferAcctInfo extTransferAcct,
                                                 CustomerInfo customerInfo)
    throws FFSException
    {
        // create an prenote transfer info with zero amount.
        TransferInfo prenoteInfo = createPrenoteTransfer(dbh,extTransferAcct, customerInfo);
        // prenote's transaction code is credit and depends on external transfer account's
        // account type
        short transCode = getPrenoteTransactionCode(extTransferAcct);
        ACHRecordInfo prenoteRecord = createCCDRecordInfo(prenoteInfo,
                                                          extTransferAcct,
                                                          transCode);
        return prenoteRecord;
    }

    /**
     *
     * @param extTransferAcct
     * @return
     */
    private short getPrenoteTransactionCode(ExtTransferAcctInfo extTransferAcct)
    {
        short transCode = 0;
        String acctType = extTransferAcct.getAcctType();
        if ( acctType != null && acctType.equalsIgnoreCase(ACHConsts.ACH_ACCOUNT_TYPE_CHECKING)) {
            transCode = ACHConsts.ACH_PRENOTE_RECORD_CREDIT_CHECKING;
        } else {
            transCode = ACHConsts.ACH_PRENOTE_RECORD_CREDIT_SAVINGS;
        }
        return transCode;
    }

    /**
     * . Create/add a prenote transfer in db
     * . get transaction code for the prenote. It is credit
     * and depends on account type of external account(Checking/Savings)
     * . Create and PPD ACHRecordInfo and return it.
     *
     * @param dbh    database connection holder to add a transfer in db
     * @param extTransferAcct
     *               external transfer account
     * @return an ACHRecordInfo object
     * @exception FFSException
     */
    private ACHRecordInfo createPrenotePPDRecord(FFSConnectionHolder dbh,
                                                 ExtTransferAcctInfo extTransferAcct,
                                                 CustomerInfo customerInfo)
    throws FFSException
    {
        // create an empty transfer info with zero amount.
        TransferInfo prenoteInfo = createPrenoteTransfer(dbh,extTransferAcct, customerInfo);
        // prenote's transaction code is credit and depends on external transfer account's
        // account type
        short transCode = 0;
        String acctType = extTransferAcct.getAcctType();
        if ( acctType != null && acctType.equalsIgnoreCase(ACHConsts.ACH_ACCOUNT_TYPE_CHECKING)) {
            transCode = ACHConsts.ACH_PRENOTE_RECORD_CREDIT_CHECKING;
        } else {
            transCode = ACHConsts.ACH_PRENOTE_RECORD_CREDIT_SAVINGS;
        }
        ACHRecordInfo prenoteRecord = createPPDRecordInfo(prenoteInfo,
                                                          extTransferAcct,
                                                          transCode);
        return prenoteRecord;
    }

    /**
     * Create/add a prenote transfer inDB
     *
     * @param dbh
     * @param extTransferAcct
     * @return
     * @exception FFSException
     */
    private TransferInfo createPrenoteTransfer(FFSConnectionHolder dbh,
                                               ExtTransferAcctInfo extTransferAcct,
                                               CustomerInfo customerInfo)
    throws FFSException
    {
        TransferInfo prenoteInfo = new TransferInfo();
        prenoteInfo.setAmount("0");
        prenoteInfo.setCustomerId(extTransferAcct.getCustomerId());
        prenoteInfo.setDateCreate(FFSUtil.getDateString("yyyyMMdd"));
        prenoteInfo.setDateDue(FFSUtil.getDateString("yyyyMMdd"));
        prenoteInfo.setDateToPost(FFSUtil.getDateString("yyyyMMdd"));
        prenoteInfo.setExternalAcctId(extTransferAcct.getAcctId());
        prenoteInfo.setLastProcessId(_processId);
        prenoteInfo.setLogId(extTransferAcct.getLogId());
        prenoteInfo.setSubmittedBy(extTransferAcct.getSubmittedBy());
        prenoteInfo.setTransferType(DBConsts.PMTTYPE_CURRENT);
        prenoteInfo.setTransferCategory(DBConsts.PRENOTE_ENTRY);
        prenoteInfo.setTransferDest(DBConsts.INTER_XFER_TYPE);
        prenoteInfo.setBankFromRtn(_bpwFIInfo.getFIRTN());
        prenoteInfo.setAccountFromNum("PrenoteAccount");
        prenoteInfo.setAccountFromType(extTransferAcct.getAcctType());
        prenoteInfo.setPrcStatus(DBConsts.POSTEDON);

        prenoteInfo = Transfer.addTransferFromAdapter(dbh,prenoteInfo,false, _bpwFIInfo, customerInfo); // false: single
        if (prenoteInfo.getStatusCode() != DBConsts.SUCCESS) {
            throw new FFSException(prenoteInfo.getStatusCode(),prenoteInfo.getStatusMsg());
        }

        this.doTransAuditLog(dbh, prenoteInfo, "Successfully created prenote entry for this external accounts." );

        return prenoteInfo;
    }

    /**
     * Process one transfer:
     * create an ACHRecordInfo for the transfer, add it in records.
     * add an offset transfer for it
     * update transfer info: lastProcessid, datePosted and status
     *
     * @param dbh
     * @param transferInfo
     * @param customerInfo
     * @param extAcctInfo
     * @exception FFSException
     */
    private void processOneTransfer(FFSConnectionHolder dbh,
                                    CustomerInfo customerInfo,
                                    TransferInfo transferInfo,
                                    ExtTransferAcctInfo extAcctInfo)
    throws FFSException
    {
        // create an ACHRecordInfo for the transferInfo, add it in record list
        ACHRecordInfo recordInfo = null;
        ACHRecordInfo offsetRecord = null;
        String memo = null;
        String category = transferInfo.getTransferCategory();
        short transCode =  getTransactionCode(extAcctInfo.getAcctType(), category );
        // add an offset transferInfo for it because of ENTRY_BALANCED_BATCH
        short offsetTransCode = 0;
        // revert of transfer debit/credit

        if (extAcctInfo.getAcctType().equalsIgnoreCase(ACHConsts.ACH_ACCOUNT_TYPE_CHECKING)) {
            offsetTransCode = ACHConsts.ACH_RECORD_DEBIT_CHECKING;
        } else {
            offsetTransCode = ACHConsts.ACH_RECORD_DEBIT_SAVINGS;
        }

        if (extAcctInfo.getRecipientType().equals(DBConsts.EXT_TRN_ACCT_RECIPIENT_TYPE_BUSINESS)) {
            recordInfo = createCCDRecordInfo(transferInfo, extAcctInfo, transCode);

            //if transferInfo.memo is not null, not empty, create a CCD Addenda Record
            memo = transferInfo.getMemo();
            if (memo != null && memo.trim().length() > 0) {
                createCCDAddendaInfo(transferInfo, recordInfo);
            }

            if (  category.equals( DBConsts.PRENOTE_ENTRY ) == false ) {
                // no offset entry for prenote entry
                offsetRecord = createOffsetCCDRecordInfo(customerInfo, transferInfo, offsetTransCode);
            }
        } else {
            recordInfo = createPPDRecordInfo(transferInfo, extAcctInfo, transCode);

            //if transferInfo.memo is not null, not empty, create a PPD Addenda Record
            memo = transferInfo.getMemo();
            if (memo != null && memo.trim().length() > 0) {
                createPPDAddendaInfo(transferInfo, recordInfo);
            }

            if (  category.equals( DBConsts.PRENOTE_ENTRY ) == false ) {
                // no offset entry for prenote entry
                offsetRecord = createOffsetPPDRecordInfo(customerInfo, transferInfo, offsetTransCode);
            }
        }

        _records.add(recordInfo);

        if (  category.equals( DBConsts.PRENOTE_ENTRY ) == false ) {
            // no offset entry for prenote entry
            // better way is not to create it at beginning
            _records.add(offsetRecord);


            // set the service class code: it should be mixed
            TypeBatchHeaderRecord batchHeaderRecord = (TypeBatchHeaderRecord) this._currentBatchHeader.getRecord();
            batchHeaderRecord.Service_Class_Code = ACHConsts.ACH_MIXED_DEBIT_CREDIT;

        }
        // update date base only when re-runcut off is false
        if ( this._reRunCutOff == false ) {

            // update transferInfo: lastProcessid, datePosted and status
            transferInfo.setLastProcessId(_processId);
            transferInfo.setDatePosted(FFSUtil.getDateString());
            transferInfo.setPrcStatus(DBConsts.POSTEDON);
            transferInfo = Transfer.updateStatusFromAdapter(dbh,transferInfo);

            if (transferInfo.getStatusCode() != DBConsts.SUCCESS) {
                throw new FFSException(transferInfo.getStatusCode(), transferInfo.getStatusMsg());
            }
            // log AutiLog
            doTransAuditLog(dbh,transferInfo,"Transfer processed");
        }
    }

    /**
     * Create a CCD ACHRecordInfo based on transfer and external transfer
     * account information.
     *
     * @param transferInfo
     * @param extAcctInfo
     * @param transactionCode
     * @return
     */
    private ACHRecordInfo createCCDRecordInfo(TransferInfo transferInfo,
                                              ExtTransferAcctInfo extAcctInfo,
                                              short transactionCode)
    throws FFSException {
        ACHRecordInfo recordInfo = new ACHRecordInfo();
        TypeCCDEntryDetailRecord typeCCD = new TypeCCDEntryDetailRecord();
        // length of recipientId could be longer than IdentificationNumber's
        typeCCD.Identification_Number = BPWUtil.truncateString(extAcctInfo.getRecipientId(),
                                                               ACHConsts.IDENTIFICATION_NUMBER_LENGTH);
        typeCCD.Identification_NumberExists = true;
        // length of recipientName could be longer than ReceivingCompanyName's
        typeCCD.Receiving_Company_Name = BPWUtil.truncateString(extAcctInfo.getRecipientName(),
                                                                ACHConsts.RECEIVING_COMPANY_NAME_LENGTH);
        typeCCD.Record_Type_Code = ACHConsts.ENTRY_DETAIL;
        typeCCD.Transaction_Code = transactionCode;
        typeCCD.Receiving_DFI_Identification = extAcctInfo.getAcctBankRtn().substring(0,8);
        typeCCD.Check_Digit = BPWUtil.calculateCheckDigit(typeCCD.Receiving_DFI_Identification);
        typeCCD.DFI_Account_Number = extAcctInfo.getAcctNum17();
        typeCCD.Amount = convertToLongAmount(transferInfo.getAmount());
        long traceNum = (new Long(traceNumCount)).longValue() + _fileCache.fileEntryCount;
        try {
            typeCCD.Trace_Number = (BPWUtil.composeTraceNum(_bpwFIInfo.getFIRTN().substring(0,8),
                                                            FFSUtil.padWithChar(Long.toString(traceNum),
                                                                                10,
                                                                                true,
                                                                                '0'))).longValue();
        } catch (Exception e) { //ignore it
        }
        recordInfo.setRecord(typeCCD);
        recordInfo.setRecordType(ACHConsts.ENTRY_DETAIL);
        recordInfo.setClassCode(ACHConsts.ACH_RECORD_SEC_CCD);
        recordInfo.setAchVersion(_achVersion);

        //store logId of the transferInfo in ACHRecordInfo.LogID
        recordInfo.setLogId(transferInfo.getLogId());

        //update batchCache.hash
        _fileCache.batchCache.batchHash += Long.parseLong(typeCCD.Receiving_DFI_Identification);
        updateCreditInFileCache(transferInfo);
        return recordInfo;
    }

    /**
     *
     * @param customerInfo
     * @param transferInfo
     * @param transactionCode
     * @return
     */
    private ACHRecordInfo createOffsetCCDRecordInfo(CustomerInfo customerInfo,
                                                    TransferInfo transferInfo,
                                                    short transactionCode)
    throws FFSException {
        ACHRecordInfo recordInfo = new ACHRecordInfo();
        TypeCCDEntryDetailRecord typeCCD = new TypeCCDEntryDetailRecord();
        typeCCD.Identification_Number = "Balanced";
        typeCCD.Identification_NumberExists = true;
        // length of customer's firstname could be longer than ReceivingCompanyName
        typeCCD.Receiving_Company_Name = BPWUtil.truncateString(customerInfo.firstName,
                                                                ACHConsts.RECEIVING_COMPANY_NAME_LENGTH);
        typeCCD.Record_Type_Code = ACHConsts.ENTRY_DETAIL;
        typeCCD.Transaction_Code = transactionCode;
        typeCCD.Receiving_DFI_Identification = transferInfo.getBankFromRtn().substring(0,8);
        typeCCD.Check_Digit = BPWUtil.calculateCheckDigit(typeCCD.Receiving_DFI_Identification);
        typeCCD.DFI_Account_Number = transferInfo.getAccountFromNum17();
        typeCCD.Amount = convertToLongAmount(transferInfo.getAmount());
        long traceNum = (new Long(traceNumCount)).longValue() + _fileCache.fileEntryCount;
        try {
            typeCCD.Trace_Number = (BPWUtil.composeTraceNum(_bpwFIInfo.getFIRTN().substring(0,8),
                                                            FFSUtil.padWithChar(Long.toString(traceNum),
                                                                                10,
                                                                                true,
                                                                                '0'))).longValue();
        } catch (Exception e) { //ignore it
        }
        recordInfo.setRecord(typeCCD);
        recordInfo.setRecordType(ACHConsts.ENTRY_DETAIL);
        recordInfo.setClassCode(ACHConsts.ACH_RECORD_SEC_CCD);
        recordInfo.setAchVersion(_achVersion);

        //store logId of the transferInfo in ACHRecordInfo.LogID
        recordInfo.setLogId(transferInfo.getLogId());

        //update batchCache.hash
        _fileCache.batchCache.batchHash += Long.parseLong(typeCCD.Receiving_DFI_Identification);
        updateDebitInFileCache(transferInfo);
        return recordInfo;
    }

    /**
     *
     * @param transferInfo
     * @param recordInfo
     * @return
     */
    private ACHAddendaInfo createCCDAddendaInfo(TransferInfo transferInfo,
                                                ACHRecordInfo recordInfo)
    {
        //create a CCD addenda record
        // Set memo to CCDAddendaRecord.Payment Related Information
        // Addenda Sequence Number is 1

        ACHAddendaInfo addendaInfo = new ACHAddendaInfo();

        TypeCCDAddendaRecord mbObj = new TypeCCDAddendaRecord();
        mbObj.Record_Type_Code = ACHConsts.ADDENDA;
        mbObj.Addenda_Type_Code = 05;

        try {

            mbObj.Payment_Related_Information =
            FFSUtil.padWithChar(transferInfo.getMemo(),
                                ACHConsts.PAYMENT_RELATED_INFORMATION_LENGTH,
                                false, //leftJustify = false
                                ACHConsts.DEFAULT_CHAR_VALUE_FOR_OPTIONAL_FIELD);

            mbObj.Payment_Related_InformationExists = true;

        } catch (Exception e) {
            mbObj.Payment_Related_InformationExists = false; //for sanity
        }

        mbObj.Addenda_Sequence_Number = 1;

        //The value of Entry Detail Sequence Number should be the last 7
        //chars of its Entry Detail Records's Trace Number.
        mbObj.Entry_Detail_Sequence_Number = BPWUtil.composeAddendaEntryDetailSeqNum( (String.valueOf(recordInfo.getFieldValueObject(ACHConsts.TRACE_NUMBER)) ) ).intValue();

        addendaInfo.setAddenda(mbObj);
        addendaInfo.setClassCode(ACHConsts.ACH_RECORD_SEC_CCD);
        addendaInfo.setAchVersion(_achVersion);

        short ind = 1;
        recordInfo.setFieldValueObject(ACHConsts.ADDENDA_REC_INDICATOR, new Short(ind));

        recordInfo.setAddenda(new ACHAddendaInfo[] {addendaInfo});

        //store logId of the transferInfo in ACHRecordInfo.LogID
        recordInfo.setLogId(transferInfo.getLogId());

        //update file cache
        _fileCache.recCount++;
        _fileCache.fileEntryCount++;
        _fileCache.batchCache.batchEntryCount++;

        return addendaInfo;
    }

    /**
     *
     * @param transferInfo
     * @param extAcctInfo
     * @param transactionCode
     * @return
     */
    private ACHRecordInfo createPPDRecordInfo(TransferInfo transferInfo,
                                              ExtTransferAcctInfo extAcctInfo,
                                              short transactionCode)
    throws FFSException {
        ACHRecordInfo recordInfo = new ACHRecordInfo();
        TypePPDEntryDetailRecord typePPD = new TypePPDEntryDetailRecord();
        typePPD.Record_Type_Code = ACHConsts.ENTRY_DETAIL;
        typePPD.Transaction_Code = transactionCode;
        typePPD.Receiving_DFI_Identification = extAcctInfo.getAcctBankRtn().substring(0,8);
        typePPD.Check_Digit = BPWUtil.calculateCheckDigit(typePPD.Receiving_DFI_Identification);
        typePPD.DFI_Account_Number = extAcctInfo.getAcctNum17();
        typePPD.Amount = convertToLongAmount(transferInfo.getAmount());
        typePPD.Individual_Identification_Number = BPWUtil.truncateString(extAcctInfo.getRecipientId(), ACHConsts.INDIVIDUAL_IDENTIFICATION_NUMBER_LENGTH);
        typePPD.Individual_Identification_NumberExists = true;
        typePPD.Individual_Name = BPWUtil.truncateString(extAcctInfo.getRecipientName(),
                                                  ACHConsts.INDIVIDUAL_NAME_LENGTH);
        long traceNum = (new Long(traceNumCount)).longValue() + _fileCache.fileEntryCount;
        try {
            typePPD.Trace_Number = (BPWUtil.composeTraceNum(_bpwFIInfo.getFIRTN().substring(0,8),
                                                            FFSUtil.padWithChar(Long.toString(traceNum),
                                                                                10,
                                                                                true,
                                                                                '0'))).longValue();
        } catch (Exception e) { //ignore it
        }
        recordInfo.setRecord(typePPD);
        recordInfo.setRecordType(ACHConsts.ENTRY_DETAIL);
        recordInfo.setClassCode(ACHConsts.ACH_RECORD_SEC_PPD);
        recordInfo.setAchVersion(_achVersion);

        //store logId of the transferInfo in ACHRecordInfo.LogID
        recordInfo.setLogId(transferInfo.getLogId());

        //update batchCache.hash
        _fileCache.batchCache.batchHash += Long.parseLong(typePPD.Receiving_DFI_Identification);
        updateCreditInFileCache(transferInfo);
        return recordInfo;
    }


    /**
     *
     * @param customerInfo
     * @param transferInfo
     * @param transactionCode
     * @return
     */
    private ACHRecordInfo createOffsetPPDRecordInfo(CustomerInfo customerInfo,
                                                    TransferInfo transferInfo,
                                                    short transactionCode)
    throws FFSException {
        ACHRecordInfo recordInfo = new ACHRecordInfo();
        TypePPDEntryDetailRecord typePPD = new TypePPDEntryDetailRecord();
        typePPD.Record_Type_Code = ACHConsts.ENTRY_DETAIL;
        typePPD.Transaction_Code = transactionCode;
        typePPD.Receiving_DFI_Identification = transferInfo.getBankFromRtn().substring(0,8);
        typePPD.Check_Digit = BPWUtil.calculateCheckDigit(typePPD.Receiving_DFI_Identification);
        typePPD.DFI_Account_Number = transferInfo.getAccountFromNum17();
        typePPD.Amount = convertToLongAmount(transferInfo.getAmount());
        typePPD.Individual_Identification_Number = "Balanced";
        typePPD.Individual_Identification_NumberExists = true;
        typePPD.Individual_Name = BPWUtil.truncateString(customerInfo.firstName,
                                           ACHConsts.INDIVIDUAL_NAME_LENGTH);
        long traceNum = (new Long(traceNumCount)).longValue() + _fileCache.fileEntryCount;
        try {
            typePPD.Trace_Number = (BPWUtil.composeTraceNum(_bpwFIInfo.getFIRTN().substring(0,8),
                                                            FFSUtil.padWithChar(Long.toString(traceNum),
                                                                                10,
                                                                                true,
                                                                                '0'))).longValue();
        } catch (Exception e) { //ignore it
        }
        recordInfo.setRecord(typePPD);
        recordInfo.setRecordType(ACHConsts.ENTRY_DETAIL);
        recordInfo.setClassCode(ACHConsts.ACH_RECORD_SEC_PPD);
        recordInfo.setAchVersion(_achVersion);

        //store logId of the transferInfo in ACHRecordInfo.LogID
        recordInfo.setLogId(transferInfo.getLogId());

        //update batchCache.hash
        _fileCache.batchCache.batchHash += Long.parseLong(typePPD.Receiving_DFI_Identification);
        updateDebitInFileCache(transferInfo);
        return recordInfo;
    }

    /**
     *
     * @param transferInfo
     * @param recordInfo
     * @return
     */
    private ACHAddendaInfo createPPDAddendaInfo(TransferInfo transferInfo,
                                                ACHRecordInfo recordInfo)
    {
        //create a PPD addenda record
        // Set memo to PPDAddendaRecord.Payment Related Information
        // Addenda Sequence Number is 1

        ACHAddendaInfo addendaInfo = new ACHAddendaInfo();

        TypePPDAddendaRecord mbObj = new TypePPDAddendaRecord();
        mbObj.Record_Type_Code = ACHConsts.ADDENDA;
        mbObj.Addenda_Type_Code = 05;

        try {

            mbObj.Payment_Related_Information =
            FFSUtil.padWithChar(transferInfo.getMemo(),
                                ACHConsts.PAYMENT_RELATED_INFORMATION_LENGTH,
                                false, //leftJustify = false
                                ACHConsts.DEFAULT_CHAR_VALUE_FOR_OPTIONAL_FIELD);

            mbObj.Payment_Related_InformationExists = true;

        } catch (Exception e) {
            mbObj.Payment_Related_InformationExists = false; //for sanity
        }

        mbObj.Addenda_Sequence_Number = 1;

        //The value of Entry Detail Sequence Number should be the last 7
        //chars of its Entry Detail Records's Trace Number.
        mbObj.Entry_Detail_Sequence_Number = BPWUtil.composeAddendaEntryDetailSeqNum( (String.valueOf(recordInfo.getFieldValueObject(ACHConsts.TRACE_NUMBER)) ) ).intValue();

        addendaInfo.setAddenda(mbObj);
        addendaInfo.setClassCode(ACHConsts.ACH_RECORD_SEC_PPD);
        addendaInfo.setAchVersion(_achVersion);

        short ind = 1;
        recordInfo.setFieldValueObject(ACHConsts.ADDENDA_REC_INDICATOR, new Short(ind));

        recordInfo.setAddenda(new ACHAddendaInfo[] {addendaInfo});

        //update file cache
        _fileCache.recCount++;
        _fileCache.fileEntryCount++;
        _fileCache.batchCache.batchEntryCount++;

        return addendaInfo;
    }

    /**
     * This method is called for housekeeping purposes when the BPW server
     * is shut down.
     */
    public void shutdown()
    throws Exception
    {
        FFSDebug.log( "InterAdapter.shutdown is called", FFSDebug.PRINT_DEV );
    }

    /**
     * Get the first ACHFIInfo of the CC company(compId) whose flag
     * CashConDFI is on
     *
     * @param dbh
     * @param FIId
     * @return
     * @exception FFSException
     */
    private ACHFIInfo getCutOffACHFIInfo(FFSConnectionHolder dbh, String FIId)
    throws FFSException
    {
        ACHFIInfo achFIInfo = ACHFI.getCutOffACHFIInfo(dbh,FIId);
        if (achFIInfo == null || achFIInfo.getStatusCode() != DBConsts.SUCCESS) {
            throw new FFSException(achFIInfo.getStatusCode(),
                                   "Failed to get ACHFI with CurOff for FIId: "
                                   + FIId + ". " + achFIInfo.getStatusMsg());
        }
        return achFIInfo;
    }

    /**
     * Get ACH file name of a ACHFIINfo in temp directory.
     * The fileName is in format:
     * <ODFIACHID>.<Date>.<Time>.<modifier>.ACH[.Extension]
     *
     * @param achFIInfo contains ODFIACHID
     * @return
     * @exception FFSException
     */
    private String getTempFileName(ACHFIInfo achFIInfo)
    throws FFSException
    {

        if (_tempFileName == null) {
            _tempFileName = ACHAdapterUtil.prepareExportFile(achFIInfo,_tempFileBase,
                                                             _errorFileBase,false,
                                                             _achAgent, _achVersion,
                                                             _InterExtension);

            _fileCache.recCount ++; // for File Header
        }
        return _tempFileName;
    }


    /**
     * Add records in ArrayList records into a file of fileName
     *
     * @param dbh
     * @param records  a list of ACHRecordInfo objects
     * @param reRunCutOff
     * @exception FFSException
     */
    private void writeRecordsInFile(FFSConnectionHolder dbh,
                                    ArrayList records,
                                    String customerId,
                                    boolean reRunCutOff)
    throws FFSException
    {
        String methodName = "ExternalTransferAdapter.writeRecordsInFile: ";
        FFSDebug.log(methodName, "start", FFSDebug.PRINT_DEV);

        ACHRecordInfo record = null;
        String batchId = null;
        ETFACHBatchInfo batchInfo = null;
        if (records != null && _tempFileName != null) {
            int len = records.size();
            for (int i = 0; i < len; i++) {
                record = (ACHRecordInfo)records.get(i);
                ACHAdapterUtil.writeACHRecord(_tempFileName,record,true,_achAgent);

                // hwu: TODO start
                // if reruncutoff is false
                // records include batches,
                // batch header, record1... batch control, batch header 2, ... batch control2
                // 1. Find first batch control, insert a record in ETF_ACHBatch
                // 2. Insert all the records in this batch in ETF_ACHRecord table
                // 3. Move the next batch
                // hwu: TODO end

                FFSDebug.log(methodName + "reRunCutOff" + reRunCutOff,
                             FFSDebug.PRINT_DEV );
                if (!reRunCutOff) {
                    if (ACHConsts.BATCH_HEADER == record.getRecordType() ) {

                        //This is ACH Batch header, create an ETF_ACHBatch record
                        TypeBatchHeaderRecord batchHeaderRecord = (TypeBatchHeaderRecord) record.getRecord();
                        batchInfo = new ETFACHBatchInfo();
                        batchInfo.setFileId(_fileId);
                        batchInfo.setCustomerId(customerId);
                        batchInfo.setCompanyName(batchHeaderRecord.Company_Name);
                        batchInfo.setCompanyIdentification(batchHeaderRecord.Company_Identification);
                        batchInfo.setStdEntryClassCode(batchHeaderRecord.Standard_Entry_Class_Code);
                        batchInfo.setEffectiveEntryDate(batchHeaderRecord.Effective_Entry_Date);
                        batchInfo.setBatchNumber( String.valueOf(batchHeaderRecord.Batch_Number) );

                        try {

                            FFSDebug.log(methodName + "adding ETFACHBatch" ,
                                         FFSDebug.PRINT_DEV );
                            batchInfo = Transfer.addETFACHBatch(dbh, batchInfo);
                            batchId = batchInfo.getBatchId();

                        } catch (FFSException e) {
                            FFSDebug.log(methodName
                                         + "failed to create ETF ACHBatch record "
                                         + e, FFSDebug.PRINT_ERR );
                            throw e;
                        }
                    } else if (ACHConsts.BATCH_CONTROL == record.getRecordType() ) {

                        //This is ACH Batch control, update the already created
                        //ETF_ACHBatch record
                        TypeBatchControlRecord batchControlRecord = (TypeBatchControlRecord) record.getRecord();
                        batchInfo.setEntryAddendaCount(String.valueOf(batchControlRecord.Entry_Addenda_Count));
                        batchInfo.setTotalDebits(String.valueOf(batchControlRecord.Total_Debits));
                        batchInfo.setNumberOfDebits(String.valueOf(_fileCache.debitCount));
                        batchInfo.setTotalCredits(String.valueOf(batchControlRecord.Total_Credits));
                        batchInfo.setNumberOfCredits(String.valueOf(_fileCache.creditCount));

                        try {

                            FFSDebug.log(methodName + "updating ETFACHBatch" ,
                                         FFSDebug.PRINT_DEV );
                            batchInfo = Transfer.updateETFACHBatchWithCtrlInfo(dbh, batchInfo);

                        } catch (FFSException e) {
                            FFSDebug.log(methodName
                                         + "failed to update ETF ACHBatch record "
                                         + e, FFSDebug.PRINT_ERR );
                            throw e;
                        }
                    } else { //ACHConsts.ENTRY_DETAIL

                        //This is ACH record, create an ETF_ACHEntry record
                        ETFACHEntryInfo achEntry = new ETFACHEntryInfo();
                        if (ACHConsts.ACH_RECORD_SEC_CCD.equals( record.getClassCode() ) ) {

                            //CCD ACH Record
                            TypeCCDEntryDetailRecord typeCCD = (TypeCCDEntryDetailRecord) record.getRecord();
                            achEntry.setBatchId(batchId);
                            achEntry.setTransactionCode(String.valueOf(typeCCD.Transaction_Code));
                            achEntry.setRecvDFIIdentification(typeCCD.Receiving_DFI_Identification);
                            achEntry.setDFIAccountNumber(typeCCD.DFI_Account_Number);
                            achEntry.setRcvCompIndvName(typeCCD.Receiving_Company_Name);
                            achEntry.setIdentificationNumber(typeCCD.Identification_Number);
                            achEntry.setAmount(String.valueOf(typeCCD.Amount));
                            achEntry.setTraceNumber(BPWUtil.composeTraceNumStr(null, String.valueOf(typeCCD.Trace_Number)));

                        } else { //PPD ACH Record

                            TypePPDEntryDetailRecord typePPD = (TypePPDEntryDetailRecord) record.getRecord();
                            achEntry.setBatchId(batchId);
                            achEntry.setTransactionCode(String.valueOf(typePPD.Transaction_Code));
                            achEntry.setRecvDFIIdentification(typePPD.Receiving_DFI_Identification);
                            achEntry.setDFIAccountNumber(typePPD.DFI_Account_Number);
                            achEntry.setRcvCompIndvName(typePPD.Individual_Name);
                            achEntry.setIdentificationNumber(typePPD.Individual_Identification_Number);
                            achEntry.setAmount(String.valueOf(typePPD.Amount));
                            achEntry.setTraceNumber(BPWUtil.composeTraceNumStr(null, String.valueOf(typePPD.Trace_Number)));
                        }

                        achEntry.setLogId( record.getLogId() );

                        try {

                            FFSDebug.log(methodName + "adding ETFACHEntry" ,
                                         FFSDebug.PRINT_DEV );
                            achEntry = Transfer.addETFACHEntry(dbh, achEntry);

                        } catch (FFSException e) {
                            FFSDebug.log(methodName
                                         + "failed to create ETF ACHEntry record "
                                         + e, FFSDebug.PRINT_ERR );
                            throw e;
                        }
                    }
                }
            }
        }
    }


    /**
     * Create Batch Header
     *
     * @param compInfo
     * @return
     * @exception FFSException
     */
    private ACHRecordInfo createBatchHeader(ExtTransferCompanyInfo compInfo, String recipientType)
    throws FFSException
    {
        ACHRecordInfo batchHeader = new ACHRecordInfo();

        // a new batch started, batch count + 1
        // also avoid that batch number starts with 0
        _fileCache.fileBatchCount++;

        // create an empty batch header record
        TypeBatchHeaderRecord batchHeaderRecord = new TypeBatchHeaderRecord();
        batchHeaderRecord.Record_Type_Code = ACHConsts.BATCH_HEADER;

        // make it default, processOneTransfer is going to overwrite this value
        // so, prenote entry does not need to overwrite it
        batchHeaderRecord.Service_Class_Code = ACHConsts.ACH_CREDITS_ONLY; // make it default, processOneTransfer is going to overwrite this value

        //nextProcessDt is in format yyyyMMdd00, we need to trim the first an the last 2 digits
        batchHeaderRecord.Effective_Entry_Date = _nextProcessDt.substring(2,_nextProcessDt.length()-2);

        batchHeaderRecord.Company_Name = compInfo.getCompName();
        batchHeaderRecord.Company_Identification = compInfo.getCompACHId();

        batchHeaderRecord.Originating_DFI_Identification = _achFIInfo.getODFIACHId8();
        batchHeaderRecord.Originator_Status_Code = "1";
        batchHeaderRecord.Batch_Number = _fileCache.fileBatchCount;

        if ( recipientType.compareTo(DBConsts.EXT_TRN_ACCT_RECIPIENT_TYPE_BUSINESS) == 0) {
            // this is CCD record
            batchHeaderRecord.Company_Entry_Description = "Business";
            batchHeaderRecord.Standard_Entry_Class_Code = ACHConsts.ACH_RECORD_SEC_CCD;
        } else {
            batchHeaderRecord.Company_Entry_Description = "Personal";
            batchHeaderRecord.Standard_Entry_Class_Code = ACHConsts.ACH_RECORD_SEC_PPD;
        }

        batchHeader.setRecordType(ACHConsts.BATCH_HEADER);
        batchHeader.setRecord(batchHeaderRecord);
        // update fileCache
        _fileCache.recCount++;
        _fileCache.batchCache = new ACHBatchCache();
        return batchHeader;
    }


    /**
     * Create Batch Control
     *
     * @param compInfo
     * @param batchHeader
     * @return
     */
    private ACHRecordInfo createBatchControl(ExtTransferCompanyInfo compInfo,
                                             ACHRecordInfo batchHeader)
    {
        ACHRecordInfo batchControl = new ACHRecordInfo();
        // create an empty batch control
        TypeBatchControlRecord batchControlRecord = new TypeBatchControlRecord();
        batchControlRecord.Record_Type_Code = ACHConsts.BATCH_CONTROL;
        MacGenerator macGenerator = (MacGenerator)OSGIUtil.getBean(MacGenerator.class);
        batchControlRecord.Message_Authentication_Code =
        macGenerator.generateMac(_achFIInfo.getODFIACHId());
        batchControlRecord.Message_Authentication_CodeExists = true;
        batchControlRecord.Entry_Addenda_Count = _fileCache.batchCache.batchEntryCount;
        batchControlRecord.Total_Debits = _fileCache.batchCache.batchDebitSum.longValue();
        batchControlRecord.Total_Credits = _fileCache.batchCache.batchCreditSum.longValue();
        batchControlRecord.Company_Identification = compInfo.getCompACHId();
        batchControlRecord.Batch_Number = _fileCache.fileBatchCount;
        // copy values from batch header
        TypeBatchHeaderRecord  batchHeaderRecord = ( TypeBatchHeaderRecord)batchHeader.getRecord();
        batchControlRecord.Service_Class_Code = batchHeaderRecord.Service_Class_Code;
        batchControlRecord.Reserved6 = "";
        batchControlRecord.Originating_DFI_Identification = batchHeaderRecord.Originating_DFI_Identification;
        batchControlRecord.Company_Identification = batchHeaderRecord.Company_Identification;
        batchControlRecord.Entry_Hash = _fileCache.batchCache.batchHash;

        batchControl.setRecordType(ACHConsts.BATCH_CONTROL);
        batchControl.setRecord(batchControlRecord);
        // update fileCache
        _fileCache.recCount++;
        _fileCache.fileHash += _fileCache.batchCache.batchHash;
        return batchControl;
    }

    private void writeFileControl() throws FFSException
    {
        _fileCache.recCount ++; // for File Control

        ACHAdapterUtil.writeFileControl( _tempFileName,
                                         _fileCache,
                                         _achAgent,
                                         _achVersion);
    }

    /**
     * Delete file in temp directory. This method is called when
     * curOffInfo.createEmptyFile is false and there is no record
     * found (_fileCache.entryCount == 0)
     *
     * @exception Exception
     */
    private void deleteTempFile() throws Exception
    {
        File tempFile = new File(_tempFileName);
        tempFile.setFileHandlerProvider(getFileHandlerProvider());
        tempFile.delete();
    }


    /**
     * Get transaction code from account type and transaction type.
     * There are only 2 kinds of account types: CHECKING and SAVINGS
     * 2 kinds of categories PRENOTE_ENTRY and USER_ENTRY
     * It must be CREDIT
     *
     * @param acctType: CHECKING or SAVINGS
     * @param category: USER_ENTRY or PRENOTE_ENTRY
     * @return short value
     */
    private short getTransactionCode(String acctType, String category)
    {
        short transCode = 0;
        if (acctType != null && category != null) {

            if ( acctType.equalsIgnoreCase(ACHConsts.ACH_ACCOUNT_TYPE_CHECKING) ) {
                if ( category.equals(DBConsts.PRENOTE_ENTRY)) {
                    // prenote credit
                    transCode = ACHConsts.ACH_PRENOTE_RECORD_CREDIT_CHECKING;
                } else {
                    // user credit
                    transCode = ACHConsts.ACH_RECORD_CREDIT_CHECKING;
                }
            } else {
                // Savings because there are only 2 kinds of accounts:
                // Checking and Savings
                if (category.equals(DBConsts.PRENOTE_ENTRY)) {
                    transCode = ACHConsts.ACH_PRENOTE_RECORD_CREDIT_SAVINGS;
                } else {
                    transCode = ACHConsts.ACH_RECORD_CREDIT_SAVINGS;
                }
            }
        }
        return transCode;
    }


    /**
     * Update credit values in _fileCache
     *
     * @param transferInfo
     */
    private void updateCreditInFileCache(TransferInfo transferInfo)
    {
        if (transferInfo != null) {
            _fileCache.recCount++;
            _fileCache.fileEntryCount++;
            _fileCache.creditCount++;

            _fileCache.fileCreditSum = _fileCache.fileCreditSum.add(
            	BPWUtil.getBigDecimal("" + convertToLongAmount(transferInfo.getAmount())));
            _fileCache.batchCache.creditCount++;
            _fileCache.batchCache.batchEntryCount++;
            _fileCache.batchCache.batchCreditSum = _fileCache.batchCache.batchCreditSum.add(
            		BPWUtil.getBigDecimal("" + convertToLongAmount(transferInfo.getAmount())));
        }
    }

    /**
     * Update debit values in _fileCache
     *
     * @param transferInfo
     */
    private void updateDebitInFileCache(TransferInfo transferInfo)
    {
        if (transferInfo != null) {
            _fileCache.recCount++;
            _fileCache.fileEntryCount++;
            _fileCache.debitCount++;
            _fileCache.fileDebitSum = _fileCache.fileDebitSum.add(
            		BPWUtil.getBigDecimal("" + convertToLongAmount(transferInfo.getAmount())));
            _fileCache.batchCache.batchEntryCount++;
            _fileCache.batchCache.debitCount++;
            _fileCache.batchCache.batchDebitSum = _fileCache.batchCache.batchDebitSum.add(
            		BPWUtil.getBigDecimal("" + convertToLongAmount(transferInfo.getAmount())));
        }
    }

    /**
     *
     * @param dbh
     * @exception Exception
     */
    private void moveToExport(FFSConnectionHolder dbh)
    throws Exception
    {
        // get export file name
        String methodName = "InterAdapter.getExportFileName: ";
        File tempFile = new File(_tempFileName);
        tempFile.setFileHandlerProvider(getFileHandlerProvider());
        String fileName = tempFile.getName();
        String exportFileName = _exportFileBase + fileName;

        // check whether this file exists or not
        File exportFile = new File( exportFileName );
        exportFile.setFileHandlerProvider(getFileHandlerProvider());
        if ( exportFile.exists() ) {
            FFSDebug.log( methodName + " export ACH file exist: " + exportFileName,
                          FFSDebug.PRINT_INF );
            // Move this file to error, and add System.getCurrentMis to the end of this file
            String exportErrorFileName = _errorFileBase
                                         + fileName
                                         + STR_EXPORT_FILE_SUFFIX
                                         + STR_ACH_FILE_SEPARATOR
                                         + System.currentTimeMillis();

            File errorFile = new File( exportErrorFileName );
            errorFile.setFileHandlerProvider(getFileHandlerProvider());
            exportFile.renameTo( errorFile );

            FFSDebug.log( methodName + " the existing file has been moved to  " +
                          exportErrorFileName, FFSDebug.PRINT_INF );
        }
        // move file

        tempFile.renameTo( exportFile );
        //do the FM logging
        if ( this._reRunCutOff == false ) {
            // only do logging when re-run cut off is false
            ACHAdapterUtil.doFMLoggingForACH(dbh, exportFile.getCanonicalPath(), methodName);
            logScheduleHist(dbh, exportFile.getCanonicalPath());
        }
    }

    /**
     *
     * Set status to failed on, revert limit, do audit log
     *
     * @param dbh
     * @param transfers
     * @param msg
     * @exception FFSException
     */
    private void failTransferInfos(FFSConnectionHolder dbh, TransferInfo[] transfers, String msg)
    throws FFSException
    {
        if ( transfers != null) {
            int len =  transfers.length;
            for (int i = 0; i < len; i++) {

                // fail one by one
                this.failTransferInfo(dbh, transfers[i], msg );


            }// end for loop
        }
    }


    /**
     * Set status to failed on, revert limit, do audit log
     *
     * @param dbh
     * @param transfer
     * @param msg
     * @exception FFSException
     */
    private void failTransferInfo(FFSConnectionHolder dbh, TransferInfo transfer, String msg)
    throws FFSException
    {
        String methodName = "ExternalTransferAdapter.failTransferInfo: ";
        FFSDebug.log( methodName + "Failed to process transfer: " + transfer.getSrvrTId(), FFSDebug.PRINT_DEV );
        FFSDebug.log( methodName + "Failed to process transfer: " + msg, FFSDebug.PRINT_DEV );
        transfer.setPrcStatus(DBConsts.FAILEDON);
        transfer.setLastProcessId(_processId);
        transfer = Transfer.updateStatusFromAdapter(dbh,transfer );

        if (transfer.getStatusCode() != DBConsts.SUCCESS) {
            FFSDebug.log(methodName + transfer.getStatusMsg(), FFSDebug.PRINT_ERR);
            throw new FFSException(transfer.getStatusCode(),transfer.getStatusMsg());
        }

        // Failed to process this transfer
        int result = LimitCheckApprovalProcessor.processExternalTransferDelete(dbh, transfer, null );

        String status = DBConsts.FAILEDON;
        if ( result == LimitCheckApprovalProcessor.LIMIT_REVERT_FAILED ) {
            // failed in reverting Limits
            status = DBConsts.LIMIT_REVERT_FAILED;
        }
        transfer.setPrcStatus( status );

        transfer = Transfer.updateStatus(dbh,transfer,false);


        doTransAuditLog(dbh,transfer,"Failed to process a transfer:" + msg );
    }


    /**
     *
     * @param dbh
     * @param transfer
     * @param preDesc
     * @exception FFSException
     */
    private void doTransAuditLog(FFSConnectionHolder dbh,
                                 TransferInfo transfer,
                                 String preDesc )
    throws FFSException
    {
        String currMethodName = "ExternalTransferAdapter.doTransAuditLog:";

        try {

            if ( this._reRunCutOff == true ) {
                // don't log anything if it is rerun cut off
                return;
            }
            if (_logLevel >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE) {
                //convert amount to BigDecimal with 2 decimal places
                java.math.BigDecimal amount = new java.math.BigDecimal(transfer.getAmount());

                // get customerId
                int customerId = 0 ;
                try {
                    customerId = Integer.parseInt(transfer.getCustomerId());
                } catch (NumberFormatException nfe) {
                    String errDescrip = currMethodName + " CustomerId is not an integer - "
                                        + transfer.getCustomerId() + " - " + nfe;
                    FFSDebug.log(errDescrip + FFSDebug.stackTrace(nfe),FFSDebug.PRINT_ERR);
                    throw new FFSException(nfe, errDescrip);
                }

                // get description
                String desc = preDesc + ", Transfer server TID  = " + transfer.getSrvrTId();
                FFSDebug.log(currMethodName + desc, FFSDebug.PRINT_DEV);

                int tranType = AuditLogTranTypes.BPW_EXTERNALTRANSFERHANDLER;

                if ( transfer.getPrcStatus().equals( DBConsts.POSTEDON) == true ) {

                    tranType = AuditLogTranTypes.BPW_EXTERNAL_TRANSFER_SENT;

                }

                String toAcctId = null;
                String toAcctRTN = null;
                String fromAcctId = null;
                String fromAcctRTN = null;

                // Get the ToAccountNum and ToAccountType given ExtAcctId
                ExtTransferAcctInfo extTransferAcctInfo = new ExtTransferAcctInfo();

	            String acctId = null;
	            if (DBConsts.BPW_TRANSFER_DEST_ITOE.equalsIgnoreCase(transfer.getTransferDest())) {
		            // Then ToAccount is external
		            acctId = transfer.getAccountToId();
	            } else if (DBConsts.BPW_TRANSFER_DEST_ETOI.equalsIgnoreCase(transfer.getTransferDest())) {
		            // Then FromAccount is external
		            acctId = transfer.getAccountFromId();
	            } else {
		            // Add support for other destinations in the future
	            }

                extTransferAcctInfo.setAcctId(acctId);
                extTransferAcctInfo = ExternalTransferAccount.getExternalTransferAccount(dbh,
                                               extTransferAcctInfo);

	            if (extTransferAcctInfo.getStatusCode() == ACHConsts.SUCCESS) {
		            if (DBConsts.BPW_TRANSFER_DEST_ITOE.equals(transfer.getTransferDest()))
		            {
		                toAcctId = com.ffusion.ffs.bpw.interfaces.util.AccountUtil.buildExtTransferAcctId(extTransferAcctInfo);
		                toAcctRTN = extTransferAcctInfo.getAcctBankRtn();
		            } else
			        if (DBConsts.BPW_TRANSFER_DEST_ETOI.equals(transfer.getTransferDest()))
			        {
				        fromAcctId = com.ffusion.ffs.bpw.interfaces.util.AccountUtil.buildExtTransferAcctId(extTransferAcctInfo);
				        fromAcctRTN = extTransferAcctInfo.getAcctBankRtn();
			        }
	            }
                // Just use whatever is in TransferInfo
	            if (toAcctId == null)
	            {
	                toAcctId = AccountUtil.buildTransferToAcctId(transfer);
		            toAcctRTN = transfer.getBankToRtn();
	            }
			    if (fromAcctId == null)
			    {
				    fromAcctId = AccountUtil.buildTransferFromAcctId(transfer);
		            fromAcctRTN = transfer.getBankFromRtn();
			    }
                AuditLogRecord _auditLogRec = new AuditLogRecord(transfer.getSubmittedBy(),
                                                                 null,
                                                                 null,
                                                                 desc,
                                                                 transfer.getLogId(),
                                                                 tranType,
                                                                 customerId,
                                                                 amount,
                                                                 transfer.getAmountCurrency(),
                                                                 transfer.getSrvrTId(),
                                                                 transfer.getPrcStatus(),
                                                                 toAcctId,
                                                                 toAcctRTN,
                                                                 fromAcctId,
                                                                 fromAcctRTN,
                                                                 0);
				TransAuditLog.addExtraAuditInfo(transfer, _auditLogRec);
                TransAuditLog.logTransAuditLog(_auditLogRec, dbh.conn.getConnection());
            }
        } catch (Exception ex) {
            String errDescrip = currMethodName + " failed " + ex;
            FFSDebug.log(errDescrip + FFSDebug.stackTrace(ex), FFSDebug.PRINT_ERR);
            throw new FFSException(ex, errDescrip);
        }

    }

    private void logScheduleHist(FFSConnectionHolder dbh, String fileName)
    {
        if (_reRunCutOff) {
            // don't log in re-submit case
            return;
        }
        // Get the last entry in SCH_Hist table
        // by fiId, InstructionType
        ScheduleHist schHist = null;
        try {
            schHist = ScheduleHistory.getLastScheduleHist(_fIId, DBConsts.ETFTRN);
            schHist.FileName = fileName;
            schHist.EventType = DBConsts.SCH_EVENTTYPE_PROCESSINGFILEGENERATED;
            ScheduleHistory.createScheduleHist(dbh,schHist);
        } catch (Exception e) {
            String trace = FFSDebug.stackTrace( e );
            FFSDebug.log( "*** ScheduleRunnable.getLastScheduleHist: exception:" + trace, FFSConst.PRINT_DEV );
            return;
        }
    }

    /**
     * Validate a trasnfer info before process it:
     * 2) Make sure its dateToPost is not earlier than its lower bound
     *
     * @param dbh      database connection holder
     * @param customerInfo
     * @param transfer
     * @return true if 2 conditions are satified
     * @exception FFSException
     */
    private boolean validateTransfer(FFSConnectionHolder dbh,
                                     TransferInfo transfer,
                                     CustomerInfo customerInfo )
    throws FFSException
    {
        if ( this._reRunCutOff == true ) {
            return true; // re run cut does not need to validate date to post
        }
        return true;
    }

    private long convertToLongAmount(String amount)
    {
    	return BPWUtil.getBigDecimal(amount).movePointRight(2).longValue();
    }
    
    private FileHandlerProvider getFileHandlerProvider() {
    	return (FileHandlerProvider)OSGIUtil.getBean(FileHandlerProvider.class);
    }
}



