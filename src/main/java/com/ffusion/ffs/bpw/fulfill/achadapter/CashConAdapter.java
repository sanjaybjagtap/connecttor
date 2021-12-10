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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;

import com.ffusion.ffs.bpw.achagent.ACHAgent;
import com.ffusion.ffs.bpw.db.ACHFI;
import com.ffusion.ffs.bpw.db.BPWBankAcct;
import com.ffusion.ffs.bpw.db.BPWFI;
import com.ffusion.ffs.bpw.db.CashCon;
import com.ffusion.ffs.bpw.db.Customer;
import com.ffusion.ffs.bpw.db.SmartCalendar;
import com.ffusion.ffs.bpw.handler.OSGIUtil;
import com.ffusion.ffs.bpw.interfaces.ACHConsts;
import com.ffusion.ffs.bpw.interfaces.ACHFIInfo;
import com.ffusion.ffs.bpw.interfaces.ACHRecordInfo;
import com.ffusion.ffs.bpw.interfaces.BPWBankAcctInfo;
import com.ffusion.ffs.bpw.interfaces.BPWFIInfo;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.CCBatchHistInfo;
import com.ffusion.ffs.bpw.interfaces.CCCompanyAcctInfo;
import com.ffusion.ffs.bpw.interfaces.CCCompanyCutOffInfo;
import com.ffusion.ffs.bpw.interfaces.CCCompanyInfo;
import com.ffusion.ffs.bpw.interfaces.CCEntryInfo;
import com.ffusion.ffs.bpw.interfaces.CCLocationInfo;
import com.ffusion.ffs.bpw.interfaces.CutOffInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.MacGenerator;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.ScheduleHist;
import com.ffusion.ffs.bpw.interfaces.handlers.ICashConAdapter;
import com.ffusion.ffs.bpw.interfaces.util.CashConUtil;
import com.ffusion.ffs.bpw.master.LimitCheckApprovalProcessor;
import com.ffusion.ffs.bpw.util.ACHAdapterConsts;
import com.ffusion.ffs.bpw.util.ACHAdapterUtil;
import com.ffusion.ffs.bpw.util.BPWUtil;
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
import com.ffusion.msgbroker.generated.MessageBroker.mdf.ACHMsgSet.TypeCCDEntryDetailRecord;
import com.ffusion.util.logging.AuditLogTranTypes;
import com.sap.banking.common.interceptors.PerfLoggerUtil;
import com.sap.banking.io.beans.File;
import com.sap.banking.io.provider.interfaces.FileHandlerProvider;

public class CashConAdapter implements ICashConAdapter, ACHAdapterConsts {
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
    private String _cashConExtension = null;
    private int _pageSize = 0;
    private String _nextProcessDt = null;
    private boolean _reRunCutOff = false;
    private boolean _crashRecovery = false;
    private int _effectiveFutureDays = DBConsts.DEFAULT_BPW_CASHCON_BATCH_EFFECTIVE_FUTURE_DAYS;
    /** Trace Number Counter. */
    private String traceNumCount = "000000000000001";

    /**
     * This method is called when the CashCon handler is created. It is
     * used to perform initialization if necessary.
     */
    public void start() 
    throws Exception
    {
    	String methodName = "CashConAdapter.start";
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

        _cashConExtension = ACHAdapterUtil.getProperty(DBConsts.CASH_CON_FILE_EXTENSION,
                                                               DBConsts.DEFAULT_CASH_CON_FILE_EXTENSION);
        if ( _achAgent == null ) {
            FFSDebug.log("CashConAdapter.start: ACHAgent has not been started! Terminating process!" , FFSDebug.PRINT_ERR);
            PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
            throw new FFSException( "CashConAdapter.start: ACHAgent has not been started! Terminating process!" );
        }
        // get size 
        _pageSize = ACHAdapterUtil.getPropertyInt( DBConsts.BATCHSIZE, 
                                                   DBConsts.DEFAULT_BATCH_SIZE );        
        PropertyConfig propertyConfig = (PropertyConfig)FFSRegistry.lookup( BPWResource.PROPERTYCONFIG);
        _logLevel = propertyConfig.LogLevel;

        _effectiveFutureDays = ACHAdapterUtil.getPropertyInt(DBConsts.BPW_CASHCON_BATCH_EFFECTIVE_FUTURE_DAYS,
                                                             DBConsts.DEFAULT_BPW_CASHCON_BATCH_EFFECTIVE_FUTURE_DAYS);

        FFSDebug.log(methodName + "successful", FFSDebug.PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    /**
        Method processOneCutOff(CutOffInfo cutOffInfo, String processId): 
        . Create file and FileHeader in temp directory.
        . Get all CC Companies with this cutOffId.
        . Create a new fileCache to calculate some info in fileControl.
        . Start db transaction:
        . For each CC Company:
            . Create a batch header. Append its content to the file
            . Read CC_CompanyCutOff to get transactionType.		
            . processOneCompany(CompInfo, processedId, fileCache, file)            
            . Create a batch control. Append its content to the file.
        . Get CreateEmptyFile from SCH_InstructionType by FIID and instructionType.
        . If (CreateEmptyFile is true || fileCache.recCount > 0) {
        . Append FileControl to the file.
    */

    public void processOneCutOff(FFSConnectionHolder dbh, 
                                 CutOffInfo cutOffInfo,
                                 String FIId,
                                 String processId,
                                 boolean createEmptyFile,
                                 boolean reRunCutOff,
                                 boolean crashRecovery,
                                 boolean isSameDayCashConEnabled, 
                                 boolean isSameDayCashConTran ) throws Exception
    {       
        String methodName = "CashConAdapter.processOneCutOff: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log(methodName + "start. CutOffId = " + cutOffInfo.getCutOffId() +
                     ". FIID = " + FIId, FFSDebug.PRINT_DEV);

        _cutOffInfo = cutOffInfo; // get CutOffInfo for this process
        _processId = processId; // get processId for this process
        _reRunCutOff = reRunCutOff;
        _crashRecovery = crashRecovery;

        // get ACHFIInfo with flag CutOffDIF is on to get the file name
        _achFIInfo = getCutOffACHFIInfo(dbh,FIId);
        _bpwFIInfo = BPWFI.getBPWFIInfo(dbh,FIId);
        _tempFileName = getTempFileName(_achFIInfo);
        _nextProcessDt = BPWUtil.getDateInNewFormat(cutOffInfo.getNextProcessTime(),
                                                    DBConsts.START_TIME_FORMAT,
                                                    DBConsts.DUE_DATE_FORMAT);
        _nextProcessDt = _nextProcessDt + "00"; // the value in db is yyyyMMdd00
        traceNumCount = "" + System.currentTimeMillis();

        // Get Company Ids by cutOffId in pages
        String startCompId = "0";

        CCCompanyCutOffInfo[] ccCompCutOffInfos = CashCon.getCCCompanyCutOffArrayByCutOffId(dbh,
                                                                                            cutOffInfo.getCutOffId(),
                                                                                            startCompId,
                                                                                            _pageSize);                
        while (ccCompCutOffInfos != null) {
            int len = ccCompCutOffInfos.length;
            if (len == 0) { // there is no CCCompanyCutOffInfo found, exit the while loop
                break;
            }
            for (int i = 0; i < len; i++) {
                String transType = ccCompCutOffInfos[i].getTransactionType();
                if ( transType != null) {
                    processOneBatch(dbh, ccCompCutOffInfos[i].getCompId(),transType, isSameDayCashConEnabled, isSameDayCashConTran );                    
                }
            }
            if (len >= _pageSize) {
                // set the new startCompId and get the next page of CCCompanyCutOffInfos
                startCompId = ccCompCutOffInfos[len-1].getCompId();
                ccCompCutOffInfos = CashCon.getCCCompanyCutOffArrayByCutOffId(dbh,
                                                                              cutOffInfo.getCutOffId(),
                                                                              startCompId,
                                                                              _pageSize);
            } else {
                // the number of CCCompanyCutOffInfos found at last time is smaller than
                // the pageSize. It means there is no more CCCompanyCutOffInfo left
                break;
            }
        }         

        if (createEmptyFile == true || _fileCache.fileBatchCount > 0) {
            // create a fileControl and append it to the file.
            writeFileControl();
            moveToExport(dbh, isSameDayCashConEnabled, isSameDayCashConTran );
        } else {
            // delete temp file
            deleteTempFile();
        }

        FFSDebug.log(methodName + "done.", FFSDebug.PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
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
                              boolean crashRecovery,
                              boolean isSameDayCashConEnabled, 
                              boolean isSameDayCashConTran ) 
    throws Exception
    {
        String methodName = "CashConAdapter.processRunNow: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log(methodName + "start. FIID = " + FIId, FFSDebug.PRINT_DEV);
        _processId = processId;
        _reRunCutOff = reRunCutOff;
        _crashRecovery = crashRecovery;
        _achFIInfo = getCutOffACHFIInfo(dbh,FIId);
        _bpwFIInfo = BPWFI.getBPWFIInfo(dbh,FIId);
        _tempFileName = getTempFileName(_achFIInfo);
        _cutOffInfo = new CutOffInfo();
        _cutOffInfo.setFIId(FIId);
        
        if (isSameDayCashConTran) {
        	_cutOffInfo.setInstructionType(DBConsts.SAMEDAYCASHCONTRN);
        } else {
        	_cutOffInfo.setInstructionType(DBConsts.CASHCONTRN);
        }
        
        _cutOffInfo.setProcessTime(FFSUtil.getDateString(DBConsts.START_TIME_FORMAT));
        _cutOffInfo.setNextProcessTime(FFSUtil.getDateString(DBConsts.START_TIME_FORMAT));

        _nextProcessDt = BPWUtil.getDateInNewFormat(_cutOffInfo.getNextProcessTime(),
                                                    DBConsts.START_TIME_FORMAT,
                                                    DBConsts.DUE_DATE_FORMAT);
        _nextProcessDt = _nextProcessDt + "00"; // the value in db is yyyyMMdd00

        traceNumCount = "" + System.currentTimeMillis();
        // get all companies for this FIId
        String startCompId = "0";
        CCCompanyInfo[] compInfos = CashCon.getCCCompanyArrayByFIId(dbh, 
                                                                    FIId, 
                                                                    startCompId,
                                                                    _pageSize);
        while (compInfos != null) {

            int len = compInfos.length;

            if (len == 0) { // there is no CCCompanyInfo found, exit the while loop
                break;
            }

            for (int i = 0; i < len; i++) {
                processOneBatch(dbh,compInfos[i].getCompId(),DBConsts.CONCENTRATION, isSameDayCashConEnabled, isSameDayCashConTran );
                processOneBatch(dbh,compInfos[i].getCompId(),DBConsts.DISBURSEMENT, isSameDayCashConEnabled, isSameDayCashConTran );
            }
            if (len >= _pageSize) {
                // set the new startCompId and get the next page of CCCompanyInfo
                startCompId = compInfos[len-1].getCompId();
                compInfos = CashCon.getCCCompanyArrayByFIId(dbh,
                                                            FIId,
                                                            startCompId,
                                                            _pageSize);
            } else {
                // the number of CCCompanyInfos found at last time is smaller than
                // the pageSize. It means there is no more CCCompanyInfo left
                break;
            }
        }  

        if (createEmptyFile == true || _fileCache.fileBatchCount > 0) {
            // create a fileControl and append it to the file.
            writeFileControl();
        } else {
            // delete temp file
            deleteTempFile();
        }

        moveToExport(dbh, isSameDayCashConEnabled, isSameDayCashConTran );
        FFSDebug.log(methodName + "done.", FFSDebug.PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    /*
    public void rerunCutOff(FFSConnectionHolder dbh, CutOffInfo cutOffInfo,
                            String FIId, String processId,
                            boolean createEmptyFile) throws Exception
    {

    }
    */

    /**
     * This method is called for housekeeping purposes when the BPW server
     * is shut down.
     */
    public void shutdown() 
    throws Exception
    {
        FFSDebug.log( "CashConAdapter.shutdown is called", FFSDebug.PRINT_DEV );
        _fileCache = null;
        _exportFileBase = null;
        _tempFileBase = null;
        _errorFileBase = null;    
        _tempFileName = null;
        _achFIInfo = null;
        _cutOffInfo = null;
        _bpwFIInfo = null;
        _processId = null;
        _achAgent = null;    
        _cashConExtension = null;
    }


    /**
     * Get the first ACHFIInfo of the CC company(compId) whose flag 
     * CashConDFI is on
     * 
     * @param dbh
     * @param compId
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
                                                             _cashConExtension);             

            _fileCache.recCount ++; // for File Header
        }
        return _tempFileName;
    }

    /**
    . Get all locations by compId.
    . Add a new record in CC_BatchHist
    . For each location:
    .   processOneLocationDeposit(locationInfo, processeId, fileCache, file)	
    . If compInfo.batchType is BATCH_BALANCED, then create an offset entry, append its content to the file. Update fileCache.
    . update CC_BatchHist.
    */
    private void processOneBatch(FFSConnectionHolder dbh,
                                 String compId, 
                                 String transType,
                                 boolean isSameDayCashConEnabled, 
                                 boolean isSameDayCashConTran )
    throws FFSException
    {
        String methodName = "CashConAdapter.processOneBatch: ";
        FFSDebug.log(methodName + "start. CompId= " + compId, FFSDebug.PRINT_DEV);
        boolean headerAdded = false;

        // get CCCompanyInfo
        CCCompanyInfo compInfo = new CCCompanyInfo();
        compInfo.setCompId(compId);
        compInfo = CashCon.getCCCompany(dbh,compInfo);

        boolean isActiveCustomer = true;
        try {
            isActiveCustomer = Customer.isActive(compInfo.getCustomerId(), dbh);
        } catch (Exception e){}

        // initialize BatchCache
        _fileCache.batchCache = new ACHBatchCache();

        ACHRecordInfo batchHeader = null;

        ArrayList records = new ArrayList();

        // check whether this batch's offset account exists or not
        // if not, fail all the entries belong to batch

        boolean compOffAcctExist = true; 

        String batchType = compInfo.getBatchType();

        CCCompanyAcctInfo offsetAcct = new CCCompanyAcctInfo();
        offsetAcct.setCompId( compId );
        offsetAcct.setTransactionType( transType );

        if ( batchType == null) {
            // invalid batch type
            // fail all the entries
            compOffAcctExist = false;
        } else {

            if (batchType.compareTo(ACHConsts.BATCH_BALANCED_BATCH) == 0) {
                // get offset account

                if ( transType.equals( DBConsts.DISBURSEMENT ) == true ) {

                    offsetAcct.setAcctId( compInfo.getDisburseAcctId() );

                } else {
                    // Concentration:
                    offsetAcct.setAcctId(compInfo.getConcentrateAcctId());
                }

                offsetAcct = CashCon.getCCCompanyAcct(dbh, offsetAcct );
                if ( offsetAcct.getStatusCode() != DBConsts.SUCCESS ) {

                    // fail all the entries
                    compOffAcctExist = false;

                }
            }
        }

        // get all locations by compId
        String startLocationId = "0";
        CCLocationInfo[] locationArray = getCCLocationArrayByCompId(dbh, compId, startLocationId, transType, isSameDayCashConEnabled, isSameDayCashConTran  );
        while (locationArray != null && locationArray.length > 0) {
            int len = locationArray.length;
            if (len == 0) {
                break;
            }

            int recordNum = 0;
            for (int i = 0; i < len ; i++) {
                if (!isActiveCustomer)
                {
                    // fail all the cc entries belong this batch for this location
                    String errorMsg = ACHConsts.CAN_NOT_FIND_ACTIVE_CUSTOMER_MSG;
                    this.failUserEntriesOfLocation(dbh, locationArray[i], transType, errorMsg , isSameDayCashConEnabled, isSameDayCashConTran  );
                } else
                if ( compOffAcctExist == true ) {


                    processOneLocation(dbh,locationArray[i],compInfo,records, 
                                       transType, isSameDayCashConEnabled, isSameDayCashConTran ); 

                    recordNum = records.size();

                    if ( headerAdded == false && recordNum > 0) {
                        headerAdded = true;
                        // a new batch started, batch count + 1
                        // also avoid that batch number starts with 0
                        _fileCache.fileBatchCount++; // add new batch
                        batchHeader = createBatchHeader( compInfo, isSameDayCashConEnabled, isSameDayCashConTran );
                        _fileCache.recCount++; // this is for batch header entry
                        records.add(0, batchHeader);                                       
                    }


                    if (recordNum > _pageSize) {
                        // reaches page size, save to file and commit
                        writeRecordsInFile(records);
                        records = new ArrayList();
                        recordNum = 0;
                        dbh.conn.commit();
                    }
                } else {
                    // fail all the cc entries belong this batch for this location
                    String errorMsg = "Can not find offset account for this CashCon company.";
                    this.failUserEntriesOfLocation(dbh, locationArray[i], transType, errorMsg, isSameDayCashConEnabled, isSameDayCashConTran );
                }
            } // end for

            // check whether there are more locations or  not
            if (len < _pageSize) {
                // no more locations, break
                break;
            } else {
                // more locations
                startLocationId = locationArray[len-1].getLocationId();
                locationArray = getCCLocationArrayByCompId(dbh,compId,startLocationId, transType, isSameDayCashConEnabled, isSameDayCashConTran );
            }

        } // end while


        if ( compOffAcctExist == true ) {
            // Only do following when compOffAcctExist. If it not exist, 
            // we have failed all the entries.

            if (headerAdded == true) {

                if (batchType.compareTo(ACHConsts.BATCH_BALANCED_BATCH) == 0) {
                    // create an offet entry for this batch
                    createOffsetEntryForBatch(dbh, compInfo, records, offsetAcct );
                }

                // Create batch control
                ACHRecordInfo batchControl = createBatchControl(compInfo,batchHeader);
                // add it at the end of records
                records.add(batchControl);
                // add records into the file
                writeRecordsInFile(records);

                // add a new record CC_BatchHist not include offset acct
                if (this._reRunCutOff == false) {
                    addBatchHist(dbh, compInfo, transType);
                }
                // updafe fielCache.hash
                _fileCache.fileHash += _fileCache.batchCache.batchHash;
            } // else: empty batch: don't add batch control or write records into file
        }

        FFSDebug.log(methodName + "done. " , FFSDebug.PRINT_DEV);
    } 

    /**
     * Add records in ArrayList records into a file of fileName
     * 
     * @param records  a list of ACHRecordInfo objects
     * @param fileName Name of the output file
     * @exception FFSException
     */
    private void writeRecordsInFile(ArrayList records)
    throws FFSException
    {
        if (records != null && _tempFileName != null) {
            int len = records.size();
            for (int i = 0; i < len; i++) {
                ACHAdapterUtil.writeACHRecord(_tempFileName,(ACHRecordInfo)records.get(i),true,_achAgent);
            }
        }
    }

    /**
    . RecordTypeCode = 5			
    . CompanyName = CCCompanyInfo.CompName
    . CompanyDiscretionaryData = (empty)
    . CompanyIdentification = CCCompanyInfo.CCCompId
    . StandardEntryClassCode = CCD
    . CompanyEntryDescription = 'Concentrate'	for batch containing deposit entries and concentration transaction, 'Disburse" for batch containing disbursement transactions, 'Cash Con' otherwise.		
    . OriginatorStatusCode = 1
    . OriginatingDFIIdetification = ODFIACHID of the first ACHFIInfo whose FIID = CCCompanyInfo.CustomerId's FIID and flag CashConDFI is on.
    . BatchNumber: starting from 0000001
    . ServiceClassCode is 225 if it is deposit, 220 if it is disbursement and 200 if it is mixed. 
    . EffectiveEntryDate: = currentDate +1 if it contains deposit entries, currentDate + 2 if it contains disbursement entries.
    */
    private ACHRecordInfo createBatchHeader(CCCompanyInfo compInfo, 
								            boolean isSameDayCashConEnabled, 
								            boolean isSameDayCashConTran ) 
    throws FFSException
    {
        ACHRecordInfo batchHeader = new ACHRecordInfo();        

        // create an empty batch header record
        TypeBatchHeaderRecord batchHeaderRecord = new TypeBatchHeaderRecord();
        batchHeaderRecord.Record_Type_Code = ACHConsts.BATCH_HEADER;

        // always mixed
        batchHeaderRecord.Service_Class_Code = ACHConsts.ACH_MIXED_DEBIT_CREDIT;
        batchHeaderRecord.Company_Entry_Description = "Cash Con";
        batchHeaderRecord.Effective_Entry_Date = getBatchHeaderEffectiveEntryDate(_achFIInfo.getFIId(),
                                                                                  _effectiveFutureDays, isSameDayCashConEnabled, isSameDayCashConTran);

        batchHeaderRecord.Company_Name = compInfo.getCompName();
        batchHeaderRecord.Company_Identification = compInfo.getCCCompId();
        batchHeaderRecord.Standard_Entry_Class_Code = ACHConsts.ACH_RECORD_SEC_CCD;        
        batchHeaderRecord.Originating_DFI_Identification = _achFIInfo.getODFIACHId().substring(0,8);
        batchHeaderRecord.Originator_Status_Code = "1";
        batchHeaderRecord.Batch_Number = _fileCache.fileBatchCount; 

        batchHeader.setRecordType(ACHConsts.BATCH_HEADER);
        batchHeader.setClassCode(ACHConsts.ACH_RECORD_SEC_CCD);
        batchHeader.setAchVersion(_achVersion); 
        batchHeader.setRecord(batchHeaderRecord);               
        return batchHeader;
    }

    private ACHRecordInfo createBatchControl(CCCompanyInfo compInfo, ACHRecordInfo batchHeader)
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
        batchControlRecord.Company_Identification = compInfo.getCCCompId();
        batchControlRecord.Batch_Number = _fileCache.fileBatchCount;
        // copy values from batch header 
        TypeBatchHeaderRecord  batchHeaderRecord = ( TypeBatchHeaderRecord)batchHeader.getRecord();
        batchControlRecord.Service_Class_Code = batchHeaderRecord.Service_Class_Code;
        batchControlRecord.Reserved6 = "";
        batchControlRecord.Originating_DFI_Identification = batchHeaderRecord.Originating_DFI_Identification;
        batchControlRecord.Company_Identification = batchHeaderRecord.Company_Identification;
        batchControlRecord.Entry_Hash = _fileCache.batchCache.batchHash;

        batchControl.setRecordType(ACHConsts.BATCH_CONTROL);
        batchControl.setClassCode(ACHConsts.ACH_RECORD_SEC_CCD);
        batchControl.setAchVersion(_achVersion); 
        batchControl.setRecord(batchControlRecord);
        // update fileCache
        _fileCache.recCount++;
        return batchControl;
    }

    /**
     * Get Effective_Entry_Date for batch header. It will be
     * 1 or 2 days later than today. Its format is yyMMdd
     * 
     * @param number The number of days later than today
     * @return a String in format yyMMdd
     */
    private String getBatchHeaderEffectiveEntryDate(String FIId, int number, boolean isSameDayCashConEnabled, boolean isSameDayCashConTran )
    		throws FFSException
    {
        // get today
        Calendar cal = Calendar.getInstance();
        java.text.SimpleDateFormat s = new java.text.SimpleDateFormat("yyyyMMdd");
        String newDate = s.format(cal.getTime());
        int newBusDateInt = Integer.parseInt(newDate);

        try {
            // If today is not a business day, get the next business day.
            if (SmartCalendar.isTodayNonACHBusinessDay(FIId)) {
                newBusDateInt = SmartCalendar.getACHBusinessDay(FIId,
                                                             newBusDateInt,
                                                             true) ;
            }
        } catch (Exception e) {
            throw new FFSException(e, "Failed to check if today is not a business day "
                                   + "of FIID: " + FIId);
        }

        if (!isSameDayCashConTran) {
        	// add number of business days in newBusDateInt
        	for (int i = 0; i < number; i++) {
        		try {
        			newBusDateInt = SmartCalendar.getACHBusinessDay(FIId,
        					newBusDateInt,
        					true);
        		} catch (Exception e) {
        			throw new FFSException(e, "Failed to get business day of FIID: " + 
        					FIId + ". Date: " + newDate);
        		}
        	}
        }
        

        String newBusDate = Integer.toString(newBusDateInt);
        // newBusDate is in format yyyyMMdd
        // remove the first 2 digits in the newBusDate
        return newBusDate.substring(2);
    }
    /**
   * Process a CCLocation for a company. We need to process deposit
   * prenote(if any), Disbursement prenote(if any) and then user
   * entries
   * 
   * @param dbh      database connection holder
   * @param locationInfo
   *                 location will be processed
   * @param compInfo Company of the location
   * @param records  array of ACHRecordInfo objects
   * @exception FFSException
   */
    private void processOneLocation(FFSConnectionHolder dbh,
                                    CCLocationInfo locationInfo, 
                                    CCCompanyInfo compInfo,
                                    ArrayList records,
                                    String transType,
                                    boolean isSameDayCashConEnabled, 
                                    boolean isSameDayCashConTran  )
    throws FFSException
    {
        String methodName = "CashConAdapter.processOneLocation: ";
        FFSDebug.log(methodName + "start. LocationId: " + locationInfo.getLocationId()
                     + ". Transaction Type: " + transType, FFSDebug.PRINT_DEV);

        if ( this._reRunCutOff == true ) {
            processOneLocationReRunCutOff(dbh,
                                          locationInfo, 
                                          compInfo,
                                          records,
                                          transType, isSameDayCashConEnabled, isSameDayCashConTran );
        } else {
            // The following method can handler crash recovery and normal cases
            // crash recovery:
            //  1. Cancel Prenote Entry. (PrenoteStatus needs to be set to be null)
            //  2. Cancel Anticipatory entry
            //  3. Do everything as normal

            processOneLocationNormal(dbh,
                                     locationInfo, 
                                     compInfo,
                                     records,
                                     transType, isSameDayCashConEnabled, 
                                     isSameDayCashConTran );
        }
    }



    /**
     * ReRunCutOff: Process a CCLocation for a company. We need to process deposit
     * prenote(if any), Disbursement prenote(if any) and then user
     * entries
     * 
     * @param dbh       database connection holder
     * @param locationInfo
     *                  location will be processed
     * @param compInfo  Company of the location
     * @param records   array of ACHRecordInfo objects
     * @param transType
     * @return 
     * @exception FFSException
     */
    private void processOneLocationReRunCutOff(FFSConnectionHolder dbh,
                                               CCLocationInfo locationInfo, 
                                               CCCompanyInfo compInfo,
                                               ArrayList records,
                                               String transType,
                                               boolean isSameDayCashConEnabled, 
                                               boolean isSameDayCashConTran  )
    throws FFSException
    {
        String methodName = "CashConAdapter.processOneLocationReRunCutOff: ";
        FFSDebug.log(methodName + "start. LocationId: " + locationInfo.getLocationId()
                     + ". Transaction Type: " + transType, FFSDebug.PRINT_DEV);

        int numRecords = records.size();
        FFSDebug.log(methodName + ": Before processing, current number of entries: " + numRecords , FFSDebug.PRINT_DEV);

        // get processed entries
        // 
        CCEntryInfo[] entries = this.getProcessedEntries(dbh,locationInfo,transType, isSameDayCashConEnabled, isSameDayCashConTran );

        int size = 0;
        if ( entries != null ) {
            size = entries.length;
        }


        // If entries includes one entry
        if ( size == 1) {

            // If it is prenote entry, just save it into the file

            // If it is anticipatory entry, just save it into and create offset entry
            this.processOneEntry(dbh,locationInfo, entries[0],compInfo,records );

        } else if ( size != 0 ) {
            // include mutiple enties
            // check threshold
            // if yes
            // CONCENTRATION is different from Disburesment here
            if ( transType.equals( DBConsts.CONCENTRATION ) == true ) {
                // Concentration:

                // 5. Check Threshold
                long thresholdAmtLong = CashConUtil.getThresholdDeposAmtLong(locationInfo);

                if ( thresholdAmtLong != 0 ) {

                    // process threshold for this location
                    // either create a big entry, or process them as usual
                    processDepositEntriesWithThresholdAmount(dbh,
                                                             locationInfo,
                                                             compInfo,
                                                             records,
                                                             entries);
                } else {
                    // no threshold set

                    // 6. Check Consolidte
                    if ( locationInfo.getConsolidateDepos() != null &&
                         locationInfo.getConsolidateDepos().equals("Y") ) {

                        // Consolidate is set

                        // process Consolidate, create a big entry
                        processDepositEntriesWithConsolidateDeposit(dbh,
                                                                    locationInfo,
                                                                    compInfo,
                                                                    records,
                                                                    entries  );
                    } else {
                        // no threshold, no consolidate
                        // process them as usual
                        processUserEntries(dbh,
                                           locationInfo,
                                           compInfo,
                                           records,
                                           transType,
                                           entries );
                    }

                } 


            } else {
                // Disbursement:
                // no threshold, 
                // process them as usual
                processUserEntries(dbh,
                                   locationInfo,
                                   compInfo,
                                   records,
                                   transType,
                                   entries );
            }

        }

        // Don't update Location last request time

        FFSDebug.log(methodName + "done. Current number of entries: " + records.size() , FFSDebug.PRINT_DEV);

    }

    /**
     * Process a CCLocation for a company. We need to process deposit
     * prenote(if any), Disbursement prenote(if any) and then user
     * entries
     * 
     * @param dbh       database connection holder
     * @param locationInfo
     *                  location will be processed
     * @param compInfo  Company of the location
     * @param records   array of ACHRecordInfo objects
     * @param transType
     * @return 
     * @exception FFSException
     */
    private void processOneLocationNormal(FFSConnectionHolder dbh,
                                          CCLocationInfo locationInfo, 
                                          CCCompanyInfo compInfo,
                                          ArrayList records,
                                          String transType,
                                          boolean isSameDayCashConEnabled, 
                                          boolean isSameDayCashConTran  )
    throws FFSException
    {
        String methodName = "CashConAdapter.processOneLocationNormal: ";
        FFSDebug.log(methodName + "start. LocationId: " + locationInfo.getLocationId()
                     + ". Transaction Type: " + transType, FFSDebug.PRINT_DEV);

        int numRecords = records.size();
        FFSDebug.log(methodName + ": Before processing, current number of entries: " + numRecords , FFSDebug.PRINT_DEV);

        // 1. get all entries which need to be process in this run
        // get unprocessed and processed entries
        // 
        CCEntryInfo[] entries = getAllUserEntries(dbh,locationInfo, transType, isSameDayCashConEnabled, isSameDayCashConTran );

        // check offset account, if it does not exist, fail all the entries for this locaiton

        boolean locationOffAcctExist = true; 

        String batchType = compInfo.getBatchType();
        CCCompanyAcctInfo offsetAcct = new CCCompanyAcctInfo();
        offsetAcct.setCompId( compInfo.getCompId() );
        offsetAcct.setTransactionType( transType );

        if ( batchType == null) {
            // invalid batch type
            // fail all the entries
            locationOffAcctExist = false;
        } else {

            if (batchType.compareTo(ACHConsts.ENTRY_BALANCED_BATCH) == 0) {
                // get offset account

                if ( transType.equals( DBConsts.DISBURSEMENT ) == true ) {

                    offsetAcct.setAcctId( locationInfo.getDisburseAcctId() );

                } else {
                    // Concentration:

                    offsetAcct.setAcctId( locationInfo.getConcentrateAcctId());
                }

                offsetAcct = CashCon.getCCCompanyAcct(dbh, offsetAcct );
                if ( offsetAcct.getStatusCode() != DBConsts.SUCCESS ) {

                    // fail all the entries
                    locationOffAcctExist = false;

                }
            }
        }

        if ( locationOffAcctExist == true ) {

            // 2. Check Prenote, and process prenote if necessary, 
            //    also return true, if prenote status is not SUCCESS
            if ( processLocationPrenote( dbh,locationInfo, compInfo,records, transType, isSameDayCashConEnabled, isSameDayCashConTran ) == true ) {
                // true: Prenote is required and Prenote has been processed
                // we need to fail all the pending transactions

                FFSDebug.log(methodName + ": Prenote is not mature, fail entries. ", FFSDebug.PRINT_DEV);
                String errorMsg = "Failed to process CCEntry because its location's prenote has not matured.";
                this.failUserEntriesOfLocation(dbh,locationInfo,entries,transType, errorMsg  );

            } else {

                // Not prenote process is required

                // 3. Check size enties
                if ( entries.length == 0 ) {

                    // 4. Check anticipatory for CONCENTRATION entries
                    long anticipatoryDeposLong = CashConUtil.getAnticipatoryDeposLong(locationInfo);

                    if ( ( transType.equals( DBConsts.CONCENTRATION ) == true ) && 
                         ( anticipatoryDeposLong != 0 ) ) {

                        // not zero, we need to create an anticipatory entry
                        FFSDebug.log(methodName + ": Creating AnticipatoryDepos: " + anticipatoryDeposLong, FFSDebug.PRINT_DEV);

                        processAnticipatoryEntryForLocation(dbh,
                                                            locationInfo,
                                                            compInfo,
                                                            records,
                                                            anticipatoryDeposLong );
                    }

                } else {
                    // There are some entries

                    // CONCENTRATION is different from Disburesment here
                    if ( transType.equals( DBConsts.CONCENTRATION ) == true ) {
                        // Concentration:

                        // 5. Check Threshold
                        long thresholdAmtLong = CashConUtil.getThresholdDeposAmtLong(locationInfo);

                        if ( thresholdAmtLong != 0 ) {

                            // process threshold for this location
                            // either create a big entry, or not process at all
                            processDepositEntriesWithThresholdAmount(dbh,
                                                                     locationInfo,
                                                                     compInfo,
                                                                     records,
                                                                     entries);
                        } else {
                            // no threshold set

                            // 6. Check Consolidte
                            if ( locationInfo.getConsolidateDepos() != null &&
                                 locationInfo.getConsolidateDepos().equals("Y") ) {

                                // Consolidate is set

                                // process Consolidate, create a big entry
                                processDepositEntriesWithConsolidateDeposit(dbh,
                                                                            locationInfo,
                                                                            compInfo,
                                                                            records,
                                                                            entries  );
                            } else {
                                // no threshold, no consolidate
                                // process them as usual
                                processUserEntries(dbh,
                                                   locationInfo,
                                                   compInfo,
                                                   records,
                                                   transType,
                                                   entries );
                            }

                        } 


                    } else {
                        // Disbursement:
                        // no threshold, 
                        // process them as usual
                        processUserEntries(dbh,
                                           locationInfo,
                                           compInfo,
                                           records,
                                           transType,
                                           entries );
                    }


                } // some entries end

            } // not prenote end

            if ( records.size() > numRecords ) {
                // new records have been added for this location
                // Update lastRequestTime
                CashCon.updateLastRequestTimeOfCCLocation(dbh,locationInfo );
            }

        } else {

            // fail all the entries this location have 
            String errorMsg = "Can not find offset account for this CashCon location.";
            this.failUserEntriesOfLocation(dbh,locationInfo,entries,transType, errorMsg );
        }
        FFSDebug.log(methodName + "done. Current number of entries: " + records.size() , FFSDebug.PRINT_DEV);

    }


    /**
     * Process all user entries for a location and cutoffInfo.
     * 1) get unprocessed(WILLPROCESSON) entries, process them
     * 2) get processed(POSTEDON with same processId), process them:
     *    if it is anticipatory entry, create a new one, set it
     *    entryId, modify it. Otherwise, re-process it.
     * 
     * @param dbh
     * @param locationInfo
     * @param compInfo
     * @param records
     * @exception FFSException
    */
    private void processUserEntries(FFSConnectionHolder dbh,
                                    CCLocationInfo locationInfo,
                                    CCCompanyInfo compInfo,
                                    ArrayList records,
                                    String transType,
                                    CCEntryInfo[] entries )
    throws FFSException
    {
        String methodName = "CashConAdapter.processUserEntries: ";
        FFSDebug.log(methodName + "start. LocationId: " + locationInfo.getLocationId(),
                     FFSDebug.PRINT_DEV);

        int len = entries.length;
        for (int i = 0; i < len; i++) {
            processOneEntry(dbh,locationInfo, entries[i], compInfo, records);
        }// end for loop

        FFSDebug.log(methodName + "done.", FFSDebug.PRINT_DEV);
    }

    /**
     * Check Disbursement / Concentration Prenote (if the location support it). 
     * 
     * @param dbh
     * @param locationInfo
     * @param transType
     * @return 
     * @exception FFSException
     */
    private boolean processLocationPrenote(FFSConnectionHolder dbh,
                                           CCLocationInfo locationInfo,
                                           CCCompanyInfo compInfo,
                                           ArrayList records,
                                           String transType,
                                           boolean isSameDayCashConEnabled, 
                                           boolean isSameDayCashConTran  )
    throws FFSException
    {

        boolean result = false;
        boolean isPrenoteOn = false;
        
        // 1. Check prenote
        // 2. If prenote is created, fail  the user entries, cancel anticipatory enties
        // process deposit(debit) prenote entry if transType is CONCENTRATION.
        if (transType.equals(DBConsts.CONCENTRATION)) {
            // if deposit prenote is on and its status is not Success, return
        	
        	if (isSameDayCashConEnabled && isSameDayCashConTran) {
        		
        		if (locationInfo.getDepositSameDayPrenote() != null && 
        				locationInfo.getDepositSameDayPrenote().equals("Y")) {
        			isPrenoteOn = true;
        		}
        	} else {
        		 if ( (locationInfo.getDepositPrenote() != null &&
                         locationInfo.getDepositPrenote().equals("Y")) && 
                         	(locationInfo.getDepositSameDayPrenote() != null && 
                         		locationInfo.getDepositSameDayPrenote().equals("N"))) {
        			 isPrenoteOn = true;
        		 }
        	}
        	
        	
            if (isPrenoteOn) {

                // prenote is required 
                if (locationInfo.getDepPrenoteStatus() == null  ) {

                    // Need to do new prenote
                    // Try to add a prenote entry
                    // a prenote entry has not been created. Create a new one.
                    // add to records, db
                    createPrenote(dbh,locationInfo, compInfo,records, transType, isSameDayCashConEnabled, isSameDayCashConTran );                    

                    // need to fail pending TX
                    result = true;

                } else if (locationInfo.getDepPrenoteStatus().equals( DBConsts.CC_LOCATION_PRENOTE_SUCCESS ) == false  ) {
                    // check prenote mature

                    // prenote is not mature yet
                    // need to fail pending TX
                    result =  true;
                }
            }
        }
        isPrenoteOn = false;
        
        // process disbursement(credit) prenote entry if transType is DISBURSEMENT
        if (transType.equals(DBConsts.DISBURSEMENT)) {
        	
        	
       	if (isSameDayCashConEnabled && isSameDayCashConTran) {
        		
        		if (locationInfo.getDisburseSameDayPrenote() != null && 
        				locationInfo.getDisburseSameDayPrenote().equals("Y")) {
        			isPrenoteOn = true;
        		}
        	} else {
        		 if ( (locationInfo.getDisbursePrenote() != null &&
                         locationInfo.getDisbursePrenote().equals("Y")) && 
                         	(locationInfo.getDisburseSameDayPrenote() != null && 
                         		locationInfo.getDisburseSameDayPrenote().equals("N"))) {
        			 isPrenoteOn = true;
        		 }
        	}
            // if disburse prenote is on and its status is not Success, return
            if (isPrenoteOn) {

                // prenote is required 
                if ( locationInfo.getDisPrenoteStatus() == null ) {

                    // Need to do new prenote
                    // Try to add a prenote entry
                    createPrenote(dbh,locationInfo, compInfo,records, transType, isSameDayCashConEnabled, isSameDayCashConTran );                    
                    // need to fail pending TX
                    result =  true;
                } else if ( locationInfo.getDisPrenoteStatus().equals( DBConsts.CC_LOCATION_PRENOTE_SUCCESS ) == false  ) {
                    // check prenote mature

                    // prenote is not mature yet

                    // need to fail pending TX
                    result =  true;

                }
            }
        }

        String validatePrenoteStatus = ACHAdapterUtil.getProperty(DBConsts.BPW_CASHCON_ENFORCE_PRENOTE_PERIOD,
                                                                           DBConsts.DEFAULT_BPW_CASHCON_ENFORCE_PRENOTE_PERIOD);

        if (!validatePrenoteStatus.equalsIgnoreCase("Y")) {
            result =  false;
        }
        return result;
    }

    /**
     * Create a deposit(debit) prenote entry for a location     
     * 
     * @param locationInfo
     *                   contains information about RTN, AcctNum, locationId and locationName     
     * @param records : an array list of records.
    */
    private void createPrenote(FFSConnectionHolder dbh,
                               CCLocationInfo locationInfo,
                               CCCompanyInfo compInfo,
                               ArrayList records,
                               String transType,
                               boolean isSameDayCashConEnabled, 
                               boolean isSameDayCashConTran   )
    throws FFSException
    {
        String methodName = "CashConAdapter.createPrenote: ";
        FFSDebug.log(methodName + "start", FFSDebug.PRINT_DEV);

        // create prenote CC entry into memory

        // create an commen entry with amount 0
        CCEntryInfo prenoteEntry = createCommonEntry(locationInfo,
                                                     transType,
                                                     "0", // amount 
                                                     DBConsts.PRENOTE_ENTRY);
        short transCode = getTransactionCode(locationInfo.getAccountType(),
                                             transType,
                                             DBConsts.PRENOTE_ENTRY);
        // update transcode
        ACHRecordInfo recordInfo = createACHRecordInfo(locationInfo,prenoteEntry,transCode);

        // add it to array of records
        records.add(recordInfo);

        //if LastModifier is null then set it to SubmittedBy
        if (prenoteEntry.getLastModifier() == null) {
            prenoteEntry.setLastModifier(prenoteEntry.getSubmittedBy());
        }

        // add it into db
        prenoteEntry = CashCon.addCCEntryFromAdapter(dbh,prenoteEntry);

        // check status
        if (prenoteEntry.getStatusCode() != DBConsts.SUCCESS) {

            // failed to add, throw exception
            FFSDebug.log(methodName + "failed. Reason: " + prenoteEntry.getStatusMsg(),
                         FFSDebug.PRINT_DEV);
            throw new FFSException(prenoteEntry.getStatusCode(),
                                   prenoteEntry.getStatusMsg());
        }

        // AuditLog

        doAuditLog( dbh, prenoteEntry, "Successfully processed prenote CCEntry." );


        if ( transType.equals( DBConsts.CONCENTRATION ) == true ) {

            // update location's DepPrenoteStatus but not its submitted date
            updateDepositPrenoteInLocation(dbh,locationInfo,
                                           DBConsts.CC_LOCATION_PRENOTE_PENDING, FFSUtil.getDateString( DBConsts.LASTREQUESTTIME_DATE_FORMAT ), isSameDayCashConEnabled, isSameDayCashConTran  );        

        } else {

            // update disburse prenote status but not its submitted date
            updateDisbursePrenoteInLocation(dbh,locationInfo,
                                            DBConsts.CC_LOCATION_PRENOTE_PENDING,  FFSUtil.getDateString(DBConsts.LASTREQUESTTIME_DATE_FORMAT), isSameDayCashConEnabled, isSameDayCashConTran  );        
        }

        // no need to create offset entry for prenote because amount = 0
        // deposit prenote status of this location will be changed to SUCCESS 6 days laters
        FFSDebug.log(methodName + "done.", FFSDebug.PRINT_DEV);
    }

    /**
     * Create an CCEntryInfo with specified transaction type and
     * amount and category.
     * All CCEntryIinfos created in this class have common
     * things: locationId, lastProcessId, status, submittedBy,
     * startTime, WillProcessTime, ProcessedTime
     * 
     * @param locationInfo contains information of a location
     * @param transType CONCENTRATION or DISBURSEMENT
     * @param amount
     * @param category  PRENOTE_ENTRY, USER_ENTRY or ANTICIPATORY_ENTRY
     * @return 
     */
    private CCEntryInfo createCommonEntry(CCLocationInfo locationInfo,
                                          String transType,
                                          String amount,
                                          String category)
    {
        CCEntryInfo entry = new CCEntryInfo();
        entry.setLocationId(locationInfo.getLocationId());
        entry.setAgentId(locationInfo.getAgentId());
        entry.setAgentType(locationInfo.getAgentType());
        entry.setAmount(amount);
        entry.setEntryCategory(category);
        entry.setLastProcessId(_processId);
        entry.setDueDate(FFSUtil.getDateString( DBConsts.DUE_DATE_FORMAT ) + "00");
        entry.setStatus(DBConsts.POSTEDON);
        entry.setSubmittedBy(locationInfo.getSubmittedBy());
        entry.setTransactionType(transType);
        entry.setLogId(com.ffusion.ffs.bpw.util.BPWTrackingIDGenerator.getNextId());
        entry.setWillProcessTime(FFSUtil.getDateString());
        entry.setProcessedTime(FFSUtil.getDateString());

        return entry;
    }

    /**
     * update deposit prenote status for CCLocation
     * 
     * @param dbh
     * @param locationInfo
     * @param newPrenoteStatus
     * @exception FFSException
     */
    private void updateDepositPrenoteInLocation(FFSConnectionHolder dbh,
                                                CCLocationInfo locationInfo,
                                                String newPrenoteStatus,
                                                String submitDate,
                                                boolean isSameDayCashConEnabled, 
                                                boolean isSameDayCashConTran  )
    throws FFSException
    {
        locationInfo.setDepPrenoteStatus(newPrenoteStatus);
        locationInfo.setDepPrenSubDate(submitDate);
        CashCon.updateDepositPrenoteOfCCLocation(dbh,locationInfo, isSameDayCashConEnabled, isSameDayCashConTran  );
        if (locationInfo.getStatusCode() != DBConsts.SUCCESS) {
            FFSDebug.log("Failed to update deposit prenote information in location: " 
                         + locationInfo.getLocationId() + ". Reason: "
                         + locationInfo.getStatusMsg(), FFSDebug.PRINT_ERR);
            throw new FFSException(locationInfo.getStatusCode(),locationInfo.getStatusMsg());
        }
    }

    /**
     * update disburse prenote status for CC Location
     * 
     * @param dbh
     * @param locationInfo
     * @param newPrenoteStatus
     * @param submitDate
     * @exception FFSException
     */
    private void updateDisbursePrenoteInLocation(FFSConnectionHolder dbh,
                                                 CCLocationInfo locationInfo,
                                                 String newPrenoteStatus,
                                                 String submitDate,
                                                 boolean isSameDayCashConEnabled, 
                                                 boolean isSameDayCashConTran )
    throws FFSException
    {
        locationInfo.setDisPrenoteStatus(newPrenoteStatus);
        locationInfo.setDisPrenSubDate(submitDate);
        CashCon.updateDisbursePrenoteOfCCLocation(dbh,locationInfo, isSameDayCashConEnabled, isSameDayCashConTran  );
        if (locationInfo.getStatusCode() != DBConsts.SUCCESS) {
            FFSDebug.log("Failed to update disburse prenote information in location: " 
                         + locationInfo.getLocationId() + ". Reason: "
                         + locationInfo.getStatusMsg(), FFSDebug.PRINT_ERR);
            throw new FFSException(locationInfo.getStatusCode(),locationInfo.getStatusMsg());
        }
    }


    /**
     * Get all user entries for a location and cutoffInfo.
     * 1) get unprocessed(WILLPROCESSON) entries, process them
     * 2) (Crash Recovery case) get processed(POSTEDON with same processId), 
     * process them:
     *    if it is anticipatory entry, cancel it
     *    if it is prenote entry, cancel and set prenote status to be nul
     * 
     * @param dbh
     * @param locationInfo
     * @param compInfo
     * @param records
     * @exception FFSException
     */
    private CCEntryInfo[] getAllUserEntries(FFSConnectionHolder dbh,
                                            CCLocationInfo locationInfo,
                                            String transType,
                                            boolean isSameDayCashConEnabled, 
                                            boolean isSameDayCashConTran )
    throws FFSException
    {
        String methodName = "CashConAdapter.getUserEntries: ";
        FFSDebug.log(methodName + "start. LocationId: " + locationInfo.getLocationId(),
                     FFSDebug.PRINT_DEV);

        ArrayList allEntries = new ArrayList();

        if ( this._crashRecovery == true ) {

            // get processed entries in case
            CCEntryInfo[] entries = getProcessedEntries(dbh,locationInfo, transType, isSameDayCashConEnabled, isSameDayCashConTran );
            if (entries != null) {
                int len = entries.length;
                for (int i = 0; i < len; i++) {

                    // Cancel Disbursement / Concentration Prenote (if the location support it). 
                    // and Anticipatory entries

                    if ( entries[i].getEntryCategory().equals( DBConsts.PRENOTE_ENTRY) == true ) {

                        // If this entry is prenote entry
                        // set its status to be CANCLEDON
                        CashCon.updateEntryStatus(dbh, DBConsts.CANCELEDON, entries[i].getEntryId() );



                        entries[i].setStatus( DBConsts.CANCELEDON );
                        // AuditLog
                        this.doAuditLog(dbh, entries[i], "Server crashes last time. Cancel this entry first and the re-proess prenote." );

                        // Update prenote status
                        // add it into db
                        if ( transType.equals( DBConsts.CONCENTRATION ) == true ) {

                            // update location's DepPrenoteStatus but not its submitted date
                            updateDepositPrenoteInLocation(dbh,locationInfo,
                                                           null,null, isSameDayCashConEnabled, isSameDayCashConTran );        

                        } else {

                            // update disburse prenote status but not its submitted date
                            updateDisbursePrenoteInLocation(dbh,locationInfo,
                                                            null, null, isSameDayCashConEnabled, isSameDayCashConTran );        
                        }


                    } else if ( entries[i].getEntryCategory().equals( DBConsts.ANTICIPATORY_ENTRY ) == true ) {
                        // cancel anticipatory entry as well
                        // set its status to be CANCLEDON
                        CashCon.updateEntryStatus(dbh, DBConsts.CANCELEDON, entries[i].getEntryId() );

                        entries[i].setStatus( DBConsts.CANCELEDON );
                        // AuditLog
                        this.doAuditLog(dbh, entries[i], "Server crashes last time. Cancel this entry first and the re-proess anticipatory entry." );

                    } else {
                        // normal TX, we need to process them
                        allEntries.add( entries[i] );
                    }



                }// end for loop
            } // else : there is no processed entry
        }

        // get unprocessed entries
        CCEntryInfo[] unprocessedEntries = getUnprocessedEntries(dbh,locationInfo, transType, isSameDayCashConEnabled, isSameDayCashConTran);
        if ( unprocessedEntries != null) {
            int len =  unprocessedEntries.length;
            for (int i = 0; i < len; i++) {
                allEntries.add( unprocessedEntries[i] );
            }
        } // else no new user entry found


        CCEntryInfo[] userEntries = (CCEntryInfo[]) allEntries.toArray( new CCEntryInfo[0] );

        FFSDebug.log(methodName + "done. Total user entries: " + userEntries.length, FFSDebug.PRINT_DEV);

        return userEntries;
    }



    /**
     * Get all WILLPROCESSON entries with the specified
     * locationId. Their start time is earlier than _cutOffInfo.nextProcessTime
     * 
     * @param dbh    database connection holder
     * @param locationInfo   CCLocationInfo
     * @return an array of CCEntryInfo
     * @exception FFSException
     */
    private CCEntryInfo[] getUnprocessedEntries(FFSConnectionHolder dbh,
                                                CCLocationInfo locationInfo,
                                                String transType, boolean isSameDayCashConEnabled, 
                                                boolean isSameDayCashConTran)
    throws FFSException
    {              
        return CashCon.getUnprocessedEntries(dbh,
                                             locationInfo.getLocationId(),
                                             _nextProcessDt,
                                             transType, isSameDayCashConEnabled, 
                                             isSameDayCashConTran);
    }

    /**
     * Get all processed(POSTEDON) entries by location Id, _processId and
     * starttime is earlier than a specified time
     * 
     * @param dbh
     * @param locationInfo
     * @return 
     * @exception FFSException
     */
    private CCEntryInfo[] getProcessedEntries(FFSConnectionHolder dbh,
                                              CCLocationInfo locationInfo,
                                              String transType,
                                              boolean isSameDayCashConEnabled, 
                                              boolean isSameDayCashConTran )
    throws FFSException
    {
        return CashCon.getProcessedEntries(dbh,
                                           locationInfo.getLocationId(),
                                           _nextProcessDt,
                                           _processId,
                                           transType, isSameDayCashConEnabled, isSameDayCashConTran );
    }

    /**
     * Create and save anticipatory entry into DB
     * @param dbh
     * @param locationInfo
     * @param compInfo
     * @param records
     * @exception FFSException
     */
    private void processAnticipatoryEntryForLocation(FFSConnectionHolder dbh,
                                                     CCLocationInfo locationInfo,
                                                     CCCompanyInfo compInfo,
                                                     ArrayList records,
                                                     long anticipatoryDeposLong )
    throws FFSException
    {
        String methodName = "CashConAdapter.processAnticipatoryEntryForLocation: ";
        FFSDebug.log(methodName + "start. LocationId = " + locationInfo.getLocationId(),
                     FFSDebug.PRINT_DEV);

        // create anticipatory entry in memory
        CCEntryInfo anticipatoryEntry = createAnticipatoryEntry(dbh, locationInfo,
                                                                compInfo,
                                                                records,
                                                                anticipatoryDeposLong );
        if (anticipatoryEntry == null) {
            return;
        }

        // add to DB
        // set status to be POSTEDON
        anticipatoryEntry.setStatus(DBConsts.POSTEDON);

        anticipatoryEntry = CashCon.addCCEntryFromAdapter(dbh,anticipatoryEntry);

        if (anticipatoryEntry.getStatusCode() != DBConsts.SUCCESS) {
            String err = "Failed to create anticiaptory entry for batch." +
                         " CompId: " + compInfo.getCompId() +
                         "; LocationId: " + locationInfo.getLocationId() +
                         ". Reason: " + anticipatoryEntry.getStatusMsg();
            FFSDebug.log(methodName + err, FFSDebug.PRINT_DEV);
            throw new FFSException(anticipatoryEntry.getStatusCode(),
                                   err);
        }

        // AuditLog

        doAuditLog( dbh, anticipatoryEntry, "Successfully processed anticipatory CCEntry." );

        // If the batchType is ENTRY_BALANCED_BATCH, we need to create an offset
        // entry for the anticipatory entry.
        String batchType = compInfo.getBatchType();
        if (batchType != null && batchType.equals(ACHConsts.ENTRY_BALANCED_BATCH)) {
            createOffsetEntry(dbh,locationInfo,compInfo,anticipatoryEntry,records);            
        }

        FFSDebug.log(methodName + "done.", FFSDebug.PRINT_DEV);
    }




    /**
     * Get array of CCLocationInfo whose CompId is specified
     * 
     * @param dbh    database connection holder
     * @param compId specified compId
     * @return 
     * @exception FFSException
     */
    private CCLocationInfo[] getCCLocationArrayByCompId(FFSConnectionHolder dbh, 
                                                        String compId,
                                                        String startLocationId,
                                                        String transType,
                                                        boolean isSameDayCashConEnabled, 
                                                        boolean isSameDayCashConTran )
    throws FFSException
    {

        return CashCon.getCCLocationArrayByCompId(dbh,compId,
                                                  startLocationId,_pageSize, transType, isSameDayCashConEnabled, isSameDayCashConTran );        

    }














































    /**
     * Create an offset entry for a specified entryInfo.
     * If entryInfo is CONCENTRATION, offset entry is DISBURSEMENT.
     * (and vice versa).
     * Create an ACHRecordInfo for this offset entry.
     * Add it into array of records
     * 
     * @param locationInfo
     * @param compInfo
     * @param entryInfo
     * @param records
     * @exception FFSException
     */
    private void createOffsetEntry(FFSConnectionHolder dbh,
                                   CCLocationInfo locationInfo, 
                                   CCCompanyInfo compInfo,
                                   CCEntryInfo entryInfo,
                                   ArrayList records)
    throws FFSException
    {
    	String methodName = "CashConAdapter.createOffsetEntry: " ;
        FFSDebug.log(methodName + "start. For entry " + entryInfo.getEntryId(),
                     FFSDebug.PRINT_DEV);
        // create an offset entry
        CCEntryInfo offsetEntry = new CCEntryInfo();
        BPWBankAcctInfo bankAcct = null;
        String offsetAcctId = null;
        short transCode = 0;
        if (entryInfo.getTransactionType().equals(DBConsts.CONCENTRATION)) {
            // Concentration, deposit, debit
            offsetEntry = createCommonEntry(locationInfo,
                                            DBConsts.DISBURSEMENT,
                                            entryInfo.getAmount(),
                                            DBConsts.USER_ENTRY);
            
            offsetEntry.setSameDayCashCon(entryInfo.getSameDayCashCon());
            
            offsetAcctId = locationInfo.getConcentrateAcctId();// offset accout for concentration enties

            bankAcct = getBankAcctByOffsetAcctId(dbh,offsetAcctId);

            // need to be credit
            transCode = getTransactionCode(bankAcct.getAcctType(),
                                           DBConsts.DISBURSEMENT,
                                           DBConsts.USER_ENTRY);            
        } else {
            // Disbursement, credit
            offsetEntry = createCommonEntry(locationInfo,
                                            DBConsts.CONCENTRATION,
                                            entryInfo.getAmount(),
                                            DBConsts.USER_ENTRY);
            
            offsetEntry.setSameDayCashCon(entryInfo.getSameDayCashCon());

            offsetAcctId = locationInfo.getDisburseAcctId();// offset accout for disbursement enties

            bankAcct = getBankAcctByOffsetAcctId(dbh,offsetAcctId);

            // need to be debit
            transCode = getTransactionCode(bankAcct.getAcctType(),
                                           DBConsts.CONCENTRATION,
                                           DBConsts.USER_ENTRY);            
        } 
        // create an ACHRecordInfo for this offset entry        
        ACHRecordInfo recordInfo = createACHRecordInfoForOffsetEntry(dbh,
                                                                     offsetEntry,
                                                                     compInfo,
                                                                     bankAcct,
                                                                     transCode);
        records.add(recordInfo);
        FFSDebug.log(methodName + "done.", FFSDebug.PRINT_DEV);
    }

    private void createOffsetEntryForBatch(FFSConnectionHolder dbh,
                                           CCCompanyInfo compInfo,
                                           ArrayList records,
                                           CCCompanyAcctInfo offsetAcct)
    throws FFSException
    {
        String methodName = "CashConAdapter.createOffsetEntryForBatch: ";
        FFSDebug.log(methodName + "start. CompId: " + compInfo.getCompId(),
                     FFSDebug.PRINT_DEV);

        // create an temp entry info with amount, transactionType
        CCEntryInfo tempEntry = new CCEntryInfo();
        short transCode = 0;
        BigDecimal batchCreditSum = _fileCache.batchCache.batchCreditSum;
        BigDecimal batchDebitSum = _fileCache.batchCache.batchDebitSum;

        if (batchCreditSum.compareTo(batchDebitSum) == 0) {
            // the batch is balanced, no need to create an offset entry
            return;
        } else if (batchCreditSum.compareTo(batchDebitSum) > 0) {
            tempEntry.setAmount(batchCreditSum.subtract(batchDebitSum).toString());
            // The live entries are moving into this Location, credit entries, DISBURSMENT entries
            // Offset entry should be
            // Move money out from  offset account
            // we need to create debit offset entry 
            tempEntry.setTransactionType(DBConsts.CONCENTRATION);

            transCode = getTransactionCode( offsetAcct.getAcctType(),
                                            tempEntry.getTransactionType(),
                                            DBConsts.USER_ENTRY);
        } else {
            tempEntry.setAmount(batchDebitSum.subtract(batchCreditSum).toString());
            // The live entries are moving money out from this Location, debit entries, CONCENTRATION entries
            // Offset entry should be
            // Move money into this offset account
            // we need to create credit offset entry 
            tempEntry.setTransactionType(DBConsts.DISBURSEMENT);

            transCode = getTransactionCode(offsetAcct.getAcctType(),
                                           tempEntry.getTransactionType(),
                                           DBConsts.USER_ENTRY);
        }
        ACHRecordInfo recordInfo = createACHRecordInfoForOffsetEntry(dbh,
                                                                     tempEntry,
                                                                     compInfo,
                                                                     offsetAcct,
                                                                     transCode);

        records.add( recordInfo );
        FFSDebug.log(methodName + "done.", FFSDebug.PRINT_DEV);
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
     * Process one CCEntry:
     * 1) Create an ACHRecordInfo for this entry
     * 2) Add it in array list of records
     * 3) Update its status to POSTEDON.
     * 4) Update _fileCache and _fileCache.batchCache
     * 5) If batchType is ENTRY_BALANCED_BATCH, create an offset
     * entry for this entry.
     * 
     * @param dbh
     * @param locationInfo
     * @param batchType
     * @param records
     * @exception FFSException
     */
    private void processOneEntry(FFSConnectionHolder dbh,
                                 CCLocationInfo locationInfo,
                                 CCEntryInfo entryInfo,
                                 CCCompanyInfo compInfo,
                                 ArrayList records)
    throws FFSException
    {    
        String methodName = "CashConAdapter.processOneEntry: ";
        FFSDebug.log(methodName + "start. EntryID: " + entryInfo.getEntryId(),
                     FFSDebug.PRINT_DEV);

        short transCode = getTransactionCode(locationInfo.getAccountType(),
                                             entryInfo.getTransactionType(),
                                             entryInfo.getEntryCategory());
        ACHRecordInfo recordInfo = createACHRecordInfo(locationInfo,entryInfo,transCode);
        records.add(recordInfo);

        // create offset entry if batchType is ENTRY_BALANCED
        String batchType = compInfo.getBatchType();
        if (batchType != null && batchType.equals(ACHConsts.ENTRY_BALANCED_BATCH)) {

            // only non prenote entry needs offset entry
            if ( entryInfo.getEntryCategory().equals( DBConsts.PRENOTE_ENTRY ) == false ) {

                createOffsetEntry(dbh,locationInfo,compInfo,entryInfo,records);        

            }
        }

        // update entry infor and do auditlog only when reruncutoff is not true
        if ( this._reRunCutOff == false) {
            // update entry info with lastProcessId, ProcessedTime and Status
            updateEntryInfo(dbh,entryInfo);
            // log in transaction audit log this entry
            String msg = "Successfully processed CCEntry";

            doAuditLog( dbh, entryInfo, msg );


        }

        FFSDebug.log(methodName + "done.", FFSDebug.PRINT_DEV);
    }

/**
 * update entry info with lastProcessId = _processId,
 * ProcessedTime= _cutOffInfo.nextProcessTime 
 * and Status = POSTEDON
 * 
 * @param dbh
 * @param entryInfo
 * @exception FFSException
 */
    private void updateEntryInfo(FFSConnectionHolder dbh, CCEntryInfo entryInfo)
    throws FFSException
    {
        entryInfo.setLastProcessId(_processId);
        entryInfo.setProcessedTime(FFSUtil.getDateString(DBConsts.START_TIME_FORMAT));
        entryInfo.setStatus(DBConsts.POSTEDON);
        CashCon.updateCCEntryInfoFromAdapter(dbh,entryInfo);
        if (entryInfo.getStatusCode() != DBConsts.SUCCESS) {
            throw new FFSException(entryInfo.getStatusCode(),entryInfo.getStatusMsg());
        }
    }

/**
 * Get transaction code from account type and transaction type.
 * There are only 2 kinds of account types: CHECKING and SAVINGS
 * 2 kinds of transaction type CONCENTRATION and DISBURSEMENT
 * 
 * @param acctType: CHECKING or SAVINGS
 * @param transType: CONCENTRATION or DISBURSEMENT
 * @return short value 
 */
    private short getTransactionCode(String acctType, String transType, String category)
    {
        short transCode = 0;
        if (acctType != null && transType != null) {

            if ( acctType.equalsIgnoreCase(ACHConsts.ACH_ACCOUNT_TYPE_CHECKING) ) {
                if ( transType.equals(DBConsts.CONCENTRATION)) {
                    // Concentration: debit
                    if (category.equals(DBConsts.PRENOTE_ENTRY)) {
                        transCode = ACHConsts.ACH_PRENOTE_RECORD_DEBIT_CHECKING;
                    } else {
                        transCode = ACHConsts.ACH_RECORD_DEBIT_CHECKING;
                    }
                } else {
                    // Disbursement: credit
                    if (category.equals(DBConsts.PRENOTE_ENTRY)) {
                        transCode = ACHConsts.ACH_PRENOTE_RECORD_CREDIT_CHECKING;
                    } else {
                        transCode = ACHConsts.ACH_RECORD_CREDIT_CHECKING;
                    }
                }
            } else {
                // Savings because there are only 2 kinds of accounts: 
                // Checking and Savings
                if (transType.equals(DBConsts.CONCENTRATION)) {
                    if (category.equals(DBConsts.PRENOTE_ENTRY)) {
                        transCode = ACHConsts.ACH_PRENOTE_RECORD_DEBIT_SAVINGS;
                    } else {
                        transCode = ACHConsts.ACH_RECORD_DEBIT_SAVINGS;
                    }
                } else {
                    if (category.equals(DBConsts.PRENOTE_ENTRY)) {
                        transCode = ACHConsts.ACH_PRENOTE_RECORD_CREDIT_SAVINGS;
                    } else {
                        transCode = ACHConsts.ACH_RECORD_CREDIT_SAVINGS;
                    }
                }
            }
        }
        return transCode;
    }

/**
 * Create an anticipatory entry
 * This method is called when there is no deposit for this location at the time
 * processed.
 * 
 * @param dbh
 * @param locationInfo
 *                 contains information of the location
 * @param compInfo
 * @param records  an array of records in one file
 * @param anticipatoryDeposLong
 * @return null if the location doesn't support anticipatory
 * @exception FFSException
 */
    private CCEntryInfo createAnticipatoryEntry(FFSConnectionHolder dbh,
                                                CCLocationInfo locationInfo,
                                                CCCompanyInfo compInfo,
                                                ArrayList records,
                                                long anticipatoryDeposLong )
    throws FFSException
    {
        String methodName = "CashConAdapter.createAnticipatoryEntry: ";
        FFSDebug.log(methodName + "start. LocationId: " + locationInfo.getLocationId()
                     + ". Anticipatory deposit amount: " + anticipatoryDeposLong,
                     FFSDebug.PRINT_DEV);

        // times 100
        String anticipatoryDepositAmount = ( new Long ( anticipatoryDeposLong ) ).toString();

        CCEntryInfo anticipatoryEntry = null;

        // it is ok to create anticipatory entry in memory
        anticipatoryEntry = createCommonEntry(locationInfo,
                                              DBConsts.CONCENTRATION,
                                              anticipatoryDepositAmount,
                                              DBConsts.ANTICIPATORY_ENTRY);

        // Check entitlement
        // Tracy said, there is no need to check limits
        boolean entitlementCheck =
        LimitCheckApprovalProcessor.checkEntitlementCCEntry(anticipatoryEntry,
                                                            compInfo.getCustomerId(),
                                                            null);
        if ( entitlementCheck == false) {
            FFSDebug.log(methodName + ": Failed entitlement check for anticipatory deposit entry",
                         FFSDebug.PRINT_DEV );
            return null;
        }

        // create an ACHRecordInfo for it            
        ACHRecordInfo recordInfo = null;
        if (locationInfo.getAccountType().equalsIgnoreCase(ACHConsts.ACH_ACCOUNT_TYPE_CHECKING)) {
            recordInfo = createACHRecordInfo(locationInfo,anticipatoryEntry,
                                             ACHConsts.ACH_RECORD_DEBIT_CHECKING);
        } else {
            recordInfo = createACHRecordInfo(locationInfo,anticipatoryEntry,
                                             ACHConsts.ACH_RECORD_DEBIT_SAVINGS);
        }
        // add to arraylist
        records.add(recordInfo); 
        
        FFSDebug.log(methodName + "done.", FFSDebug.PRINT_DEV);
        return anticipatoryEntry;
    }    

/**
 * Create and ACHRecordInfo for a CCEntryInfo in a location
 * 
 * @param locationInfo
 *                  contains information of the location
 * @param entryInfo contains information of CC entry
 * @param transactionCode
 *                  transaction code for this ACHRecordInfo
 * @return 
 */
    private ACHRecordInfo createACHRecordInfo(CCLocationInfo locationInfo,
                                              CCEntryInfo entryInfo,
                                              short transactionCode)
    throws FFSException {
        if (entryInfo.getTransactionType().equals(DBConsts.CONCENTRATION)) {
            updateDebitInFileCache(entryInfo);
        } else {
            updateCreditInFileCache(entryInfo);
        }
        ACHRecordInfo recordInfo = new ACHRecordInfo();
        TypeCCDEntryDetailRecord typeCCD = new TypeCCDEntryDetailRecord();
        typeCCD.Receiving_Company_Name = BPWUtil.truncateString(locationInfo.getLocationName(), ACHConsts.RECEIVING_COMPANY_NAME_LENGTH);
        typeCCD.Record_Type_Code = ACHConsts.ENTRY_DETAIL;        
        typeCCD.Transaction_Code = transactionCode;         
        typeCCD.Receiving_DFI_Identification = locationInfo.getBankRtn().substring(0,8);
        typeCCD.Check_Digit = BPWUtil.calculateCheckDigit(typeCCD.Receiving_DFI_Identification);
        typeCCD.DFI_Account_Number = locationInfo.getAccountNum();
        typeCCD.Amount = convertToLongAmount(entryInfo.getAmount());
        typeCCD.Identification_Number = BPWUtil.truncateString(locationInfo.getCCLocationId(), ACHConsts.IDENTIFICATION_NUMBER_LENGTH);
        typeCCD.Identification_NumberExists = true;
        // public String Discretionary_Data;
        // public boolean Discretionary_DataExists;
        // public short Addenda_Record_Indicator;
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
        //update batchCache.hash
        _fileCache.batchCache.batchHash += convertToLongAmount(typeCCD.Receiving_DFI_Identification);
        return recordInfo;
    }

/**
 * Create an ACHRecordInfo for an offset entry of a BATCH_BALANCED batch
 * 
 * @param dbh
 * @param compInfo
 * @param entryInfo
 * @param transactionCode
 * @return 
 */
    private ACHRecordInfo createACHRecordInfoForOffsetEntry(FFSConnectionHolder dbh,                                                            
                                                            CCEntryInfo entryInfo,
                                                            CCCompanyInfo compInfo,
                                                            BPWBankAcctInfo bankAcct,
                                                            short transactionCode)
    throws FFSException
    {
        if (entryInfo.getTransactionType().equals(DBConsts.CONCENTRATION)) {
            updateDebitInFileCache(entryInfo);
        } else {
            updateCreditInFileCache(entryInfo);
        }
        ACHRecordInfo recordInfo = new ACHRecordInfo();
        TypeCCDEntryDetailRecord typeCCD = new TypeCCDEntryDetailRecord();
        typeCCD.Identification_Number = BPWUtil.truncateString(compInfo.getCCCompId(), ACHConsts.IDENTIFICATION_NUMBER_LENGTH);
        typeCCD.Identification_NumberExists = true;
        typeCCD.Receiving_Company_Name = BPWUtil.truncateString(compInfo.getCompName(), ACHConsts.RECEIVING_COMPANY_NAME_LENGTH);
        typeCCD.Record_Type_Code = ACHConsts.ENTRY_DETAIL;        
        typeCCD.Transaction_Code = transactionCode;        
        typeCCD.DFI_Account_Number = bankAcct.getAcctNumber();
        typeCCD.Receiving_DFI_Identification = bankAcct.getBankRTN().substring(0,8);
        typeCCD.Check_Digit = BPWUtil.calculateCheckDigit(typeCCD.Receiving_DFI_Identification);       
        typeCCD.Amount = convertToLongAmount(entryInfo.getAmount());        
        // public String Discretionary_Data;
        // public boolean Discretionary_DataExists;
        // public short Addenda_Record_Indicator;
        long traceNum = (new Long(traceNumCount)).longValue() + _fileCache.fileEntryCount;
        try {
            typeCCD.Trace_Number = (BPWUtil.composeTraceNum(_bpwFIInfo.getFIRTN().substring(0,8),
                                                            FFSUtil.padWithChar(Long.toString(traceNum),
                                                                                10,
                                                                                true,
                                                                                '0'))).longValue();
        } catch (Exception e) { // ignore it
        }
        recordInfo.setRecord(typeCCD);
        recordInfo.setRecordType(ACHConsts.ENTRY_DETAIL);
        recordInfo.setClassCode(ACHConsts.ACH_RECORD_SEC_CCD);
        recordInfo.setAchVersion(_achVersion);
        //update batchCache.hash
        _fileCache.batchCache.batchHash += convertToLongAmount(typeCCD.Receiving_DFI_Identification); 
        return recordInfo;
    }



    /**
     * 
     * @param dbh
     * @param acctId
     * @return 
     * @exception FFSException
     */
    private BPWBankAcctInfo getBankAcctByOffsetAcctId(FFSConnectionHolder dbh, 
                                                      String acctId)
    throws FFSException
    {
        BPWBankAcctInfo bankAcct = BPWBankAcct.getBPWBankAcctInfoById(dbh,
                                                                      acctId);
        if (bankAcct.getStatusCode() != DBConsts.SUCCESS) {
            throw new FFSException(bankAcct.getStatusCode(),bankAcct.getStatusMsg());
        }
        return bankAcct;
    }

    /**
     * Update credit values in _fileCache
     * 
     * @param entryInfo
     */
    private void updateCreditInFileCache(CCEntryInfo entryInfo)
    {
        if (entryInfo != null) {
            _fileCache.recCount++;
            _fileCache.fileEntryCount++;
            _fileCache.batchCache.batchEntryCount++;
            _fileCache.creditCount++;
            _fileCache.batchCache.creditCount++;
            _fileCache.fileCreditSum = _fileCache.fileCreditSum.add(BPWUtil.getBigDecimal(
            	entryInfo.getAmount()));
            _fileCache.batchCache.batchCreditSum = _fileCache.batchCache.batchCreditSum.add(
            		BPWUtil.getBigDecimal(entryInfo.getAmount()));
        }
    }


/**
 * Convert String to long. No matter this String includes .0 or not
 * 
 * @param amount
 * @return 
 */
    private long convertToLongAmount(String amount)
    {
        double d_amount = Double.parseDouble(amount);
        return(new Double(d_amount)).longValue();
    }
/**
 * Update debit values in _fileCache
 * 
 * @param entryInfo
 */
    private void updateDebitInFileCache(CCEntryInfo entryInfo)
    {
        if (entryInfo != null) {
            _fileCache.recCount++;
            _fileCache.fileEntryCount++;
            _fileCache.batchCache.batchEntryCount++;
            _fileCache.debitCount++;
            _fileCache.batchCache.debitCount++;
            _fileCache.fileDebitSum = _fileCache.fileDebitSum.add(
            		BPWUtil.getBigDecimal(entryInfo.getAmount()));
            _fileCache.batchCache.batchDebitSum = _fileCache.batchCache.batchDebitSum.add(
            		BPWUtil.getBigDecimal(entryInfo.getAmount()));
        }
    }

/**
 * Add a new record in CC_BatchHist table
 * 
 * @param dbh
 * @param compInfo
 * @exception FFSException
 */
    private void addBatchHist(FFSConnectionHolder dbh,
                              CCCompanyInfo compInfo,
                              String transType) throws FFSException
    {
        CCBatchHistInfo batchHist = new CCBatchHistInfo();       
        batchHist.setBatchNumber(Integer.toString(_fileCache.fileBatchCount));
        batchHist.setCompId(compInfo.getCompId());
        batchHist.setEffectiveEntryDate(_nextProcessDt);
        batchHist.setProcessId(_processId);
        batchHist.setSubmittedBy(compInfo.getSubmittedBy());
        batchHist.setLogId(compInfo.getLogId());        
        batchHist.setNumberOfDeposits((new Long(_fileCache.batchCache.debitCount)).intValue());
        batchHist.setNumberOfDisburses((new Long(_fileCache.batchCache.creditCount)).intValue());        
        batchHist.setTotalCreditAmount(_fileCache.batchCache.batchCreditSum.toString());
        batchHist.setTotalDebitAmount(_fileCache.batchCache.batchDebitSum.toString());
        batchHist.setTransactionType(transType);
        batchHist = CashCon.addCCBatchHistInfo(dbh,batchHist);
        if (batchHist.getStatusCode() != DBConsts.SUCCESS) {
            throw new FFSException(batchHist.getStatusCode(), batchHist.getStatusMsg());
        }
        batchHist.setStatusCode(DBConsts.SUCCESS);
        batchHist.setStatusMsg(DBConsts.SUCCESS_MSG);
    }


/**
 * 
 * @param dbh
 * @exception Exception
 */
    private void moveToExport(FFSConnectionHolder dbh, boolean isSameDayCashConEnabled, boolean isSameDayCashConTran ) 
    throws Exception
    {
        // get export file name
        String methodName = "CashConAdapter.getExportFileName: ";
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
        ACHAdapterUtil.doFMLoggingForACH(dbh, exportFile.getCanonicalPath(), methodName);

        logScheduleHist(dbh, exportFile.getCanonicalPath(), isSameDayCashConEnabled, isSameDayCashConTran  );
    }

    /**
     * Because prenote is not maturem fail all to be processed entries,
     * USER_ENTRY entries of a specified location
     * 
     * @param dbh       database connection holder
     * @param locationInfo
     *                  CCLocationInfo whose active user entries are going to be failed
     * @param entries
     * @param transType transaction type of entries:CONCENTRATE/DISBURSEMENT
     * @exception FFSException
     */
    private void failUserEntriesOfLocation(FFSConnectionHolder dbh,
                                           CCLocationInfo locationInfo,
                                           String transType,
                                           String errorMsg,
                                           boolean isSameDayCashConEnabled, 
                                           boolean isSameDayCashConTran )
    throws FFSException
    {
        // get entries for this location and fail them all

        if ( this._reRunCutOff == false ) {

            // fail them only when re run cut off is false
            // Which could be: Normal or Crash

            // Get all user entries for a location and cutoffInfo.
            // 1) get unprocessed(WILLPROCESSON) entries, process them
            // 2) (Crash Recovery case) get processed(POSTEDON with same processId), 
            // process them:
            //    if it is anticipatory entry, cancel it
            //    if it is prenote entry, cancel and set prenote status to be nul
            CCEntryInfo[] entries = getAllUserEntries(dbh,locationInfo, transType, isSameDayCashConEnabled, isSameDayCashConTran );

            this.failUserEntriesOfLocation(dbh,locationInfo,entries,transType, errorMsg  );

        }
    }
    /**
     * Because prenote is not maturem fail all to be processed entries,
     * Or Offset account could be not found.
     * USER_ENTRY entries of a specified location
     * 
     * @param dbh       database connection holder
     * @param locationInfo
     *                  CCLocationInfo whose active user entries are going to be failed
     * @param entries
     * @param transType transaction type of entries:CONCENTRATE/DISBURSEMENT
     * @exception FFSException
     */
    private void failUserEntriesOfLocation(FFSConnectionHolder dbh,
                                           CCLocationInfo locationInfo,
                                           CCEntryInfo[] entries,
                                           String transType,
                                           String errorMsg )
    throws FFSException
    {
        String methodName = "CashConAdapter.failUserEntriesForLocation: ";
        FFSDebug.log(methodName + "LocationId: " + locationInfo.getLocationId()
                     + ". Transaction type: " + transType, FFSDebug.PRINT_DEV);

        if (entries != null && entries.length > 0) {
            int len = entries.length;
            for (int i = 0; i < len; i++) {

                this.failOneUserEntryOfLocation(dbh,locationInfo, entries[i],transType,errorMsg );

            }
        }
        FFSDebug.log(methodName + "done.", FFSDebug.PRINT_DEV);
    }
    /**
     * Because prenote is not maturem fail all to be processed entries,
     * Or Offset account could be not found.
     * USER_ENTRY entries of a specified location
     * 
     * @param dbh       database connection holder
     * @param locationInfo
     *                  CCLocationInfo whose active user entries are going to be failed
     * @param entries
     * @param transType transaction type of entries:CONCENTRATE/DISBURSEMENT
     * @param errorMsg
     * @exception FFSException
     */
    private void failOneUserEntryOfLocation(FFSConnectionHolder dbh,
                                            CCLocationInfo locationInfo,
                                            CCEntryInfo entry,
                                            String transType,
                                            String errorMsg )
    throws FFSException
    {
        String methodName = "CashConAdapter.failOneUserEntryForLocation: ";
        FFSDebug.log(methodName + "LocationId: " + locationInfo.getLocationId()
                     + ". Transaction type: " + transType, FFSDebug.PRINT_DEV);


        // set status to be FAILEDON
        entry.setProcessedTime(FFSUtil.getDateString(DBConsts.START_TIME_FORMAT));
        entry.setLastProcessId(_processId);

        // Failed to process this cashcon
        int result = LimitCheckApprovalProcessor.processCCEntryDelete(dbh,entry, null);

        String status = DBConsts.FAILEDON;
        if ( result == LimitCheckApprovalProcessor.LIMIT_REVERT_FAILED ) {
            // failed in reverting Limits
            status = DBConsts.LIMIT_REVERT_FAILED;
        }
        entry.setStatus( status );

        // update db
        CashCon.updateCCEntryInfoFromAdapter(dbh,entry);

        // AuditLog
        doAuditLog (dbh, entry, errorMsg );

        FFSDebug.log(methodName + "done.", FFSDebug.PRINT_DEV);
    }
/**
 * process deposit entries of one location whose deposit threshold
 * amount is set:
 * . If the sum of amounts of deposit entries of this location(including
 * processed(with the same lastprocessId)/unprocessed) is less than
 * the deposit threshold amount, don't process them.
 * . Otherwise, create one deposit entry for this location.
 * . For each entry, update its info: status, lastProcessId and
 *   do auditLog
 * 
 * @param dbh
 * @param locationInfo
 * @param compInfo
 * @param records
 * @exception FFSException
 */
    private void processDepositEntriesWithThresholdAmount(FFSConnectionHolder dbh,
                                                          CCLocationInfo locationInfo,
                                                          CCCompanyInfo compInfo,
                                                          ArrayList records,
                                                          CCEntryInfo[] entries)
    throws FFSException
    {   
        String methodName = "CashConAdapter.processDepositEntriesWithThresholdAmount: ";
        FFSDebug.log(methodName + "start. LocationID: " + locationInfo.getLocationId()
                     + ". Threshold Deposit Amount: "+ locationInfo.getThresholdDeposAmt(),
                     FFSDebug.PRINT_DEV);

        // load appropriate deposit entries
        long totalAmount = getTotalAmount( entries);

        // threshold format is $100.01
        if ( totalAmount >= CashConUtil.getThresholdDeposAmtLong(locationInfo) ) {

            // threshold deposit amount reached, create one new big entry
            addBigDepositEntry(dbh,totalAmount,locationInfo,compInfo,records);

            // update entries information in db
            if ( this._reRunCutOff == false ) {
                // update only when re run cut ff is false
                updateEntries(dbh, entries);
            }

        } else {
            if ( this._reRunCutOff == true) {
                // Threshold has been changed, 
                // For this case, we just process them as usual
                processUserEntries(dbh,
                                   locationInfo,
                                   compInfo,
                                   records,
                                   DBConsts.CONCENTRATION,
                                   entries );
            }
            // for normal and crash recovery cases
            // threshold has not been reached, don't process entries for this location
        }


        FFSDebug.log(methodName + "done.", FFSDebug.PRINT_DEV);
    }



/**
 * Process a location whose consolidate deposit flag is on:
 * Get all processed/unprocessed deposit(CONCENTRATION) entries
 * for this location. Add a big deposit entry combining all
 * the above entries. Add its offset if needed. Update entries
 * information in db.
 * 
 * @param dbh
 * @param locationInfo
 * @param compInfo
 * @param records
 * @exception FFSException
 */
    private void processDepositEntriesWithConsolidateDeposit(FFSConnectionHolder dbh,
                                                             CCLocationInfo locationInfo,
                                                             CCCompanyInfo compInfo,
                                                             ArrayList records,
                                                             CCEntryInfo[] entries  )
    throws FFSException
    {
        String methodName = "CashConAdapter.processDepositEntriesWithConsolidateDeposit: ";
        FFSDebug.log(methodName + "start. LocationID: " + locationInfo.getLocationId()
                     + ". Consolidate Deposit Flag On: " + locationInfo.getConsolidateDepos(),
                     FFSDebug.PRINT_DEV);
        long totalAmount = getTotalAmount( entries);

        addBigDepositEntry(dbh,totalAmount,locationInfo,compInfo,records);

        if ( this._reRunCutOff == false ) {
            // update only when re run cut ff is false
            // update entries information in db
            updateEntries(dbh, entries);
        }

        FFSDebug.log(methodName + "done." , FFSDebug.PRINT_DEV);
    }

/**
 * Add a big entry with "totalAmount" in arraylist records.
 * Also add its offset entry if needed.
 * 
 * @param dbh
 * @param totalAmount
 * @param locationInfo
 * @param compInfo
 * @param records
 */
    private void addBigDepositEntry(FFSConnectionHolder dbh,
                                    long totalAmount, 
                                    CCLocationInfo locationInfo, 
                                    CCCompanyInfo compInfo,
                                    ArrayList records)
    throws FFSException
    {
        CCEntryInfo newEntry = new CCEntryInfo();
        newEntry.setAmount(Long.toString(totalAmount));
        newEntry.setLocationId(locationInfo.getLocationId());
        newEntry.setTransactionType(DBConsts.CONCENTRATION);
        newEntry.setEntryCategory(DBConsts.USER_ENTRY);
        short transCode = getTransactionCode(locationInfo.getAccountType(),
                                             newEntry.getTransactionType(),
                                             newEntry.getEntryCategory());
        ACHRecordInfo recordInfo = 
        createACHRecordInfo(locationInfo,newEntry,transCode);
        records.add(recordInfo);

        // create offset entry if batchType is ENTRY_BALANCED
        String batchType = compInfo.getBatchType();
        if (batchType != null && batchType.equals(ACHConsts.ENTRY_BALANCED_BATCH)) {
            createOffsetEntry(dbh,locationInfo,compInfo,newEntry,records);        
        }
    }

/**
 * Calculate the total amount of entries in array
 * 
 * @param entries
 * @return 
 */
    private long getTotalAmount(CCEntryInfo[] entries)
    {
        long total = 0;
        if (entries != null) {
            int len = entries.length;
            for (int i = 0; i < len; i++) {
                total += convertToLongAmount(entries[i].getAmount());
            }
        }
        return total;
    }

/**
 * Update information of array of CCEntryInfo: status, lastProcessId, ProcessedTime
 * and do auditLog
 * 
 * @param entries
 */
    private void updateEntries(FFSConnectionHolder dbh, CCEntryInfo[] entries)
    throws FFSException
    {
        if (entries != null) {
            int len = entries.length;
            for (int i = 0; i < len; i++) {
                // update entry info with lastProcessId, ProcessedTime and Status
                updateEntryInfo(dbh,entries[i]);
                // log in transaction audit log this entry        
                doAuditLog(dbh, entries[i], "Successfully processed CCEntry" );

            }
        }
    }


    private void logScheduleHist(FFSConnectionHolder dbh, String fileName, boolean isSameDayCashConEnabled, boolean isSameDayCashConTran )
    {
        if (_reRunCutOff) {
            // don't log in re-submit case
            return;
        }
        // Get the last entry in SCH_Hist table
        // by fiId, InstructionType
        ScheduleHist schHist = null;
        try {
        	String InstructionType = null;
        	
        	if (isSameDayCashConTran) {
        		InstructionType = DBConsts.SAMEDAYCASHCONTRN;
        	} else {
        		InstructionType = DBConsts.CASHCONTRN;
        	}
            schHist = ScheduleHistory.getLastScheduleHist( _achFIInfo.getFIId(), InstructionType );
            schHist.FileName = fileName;
            schHist.EventType = DBConsts.SCH_EVENTTYPE_PROCESSINGFILEGENERATED;
            ScheduleHistory.createScheduleHist(dbh,schHist);
        } catch (Exception e) {
            String trace = FFSDebug.stackTrace( e );
            FFSDebug.log( "*** CashConAdapter.logScheduleHist: exception:" + trace, FFSConst.PRINT_DEV );
            return;
        }
    }

    /**
     * Do audit log
     * @param dbh
     * @param entryInfo
     * @param preDesc
     */
    private void doAuditLog(FFSConnectionHolder dbh,
                            CCEntryInfo entryInfo,
                            String msg )
    throws FFSException
    {

        if (_logLevel > DBConsts.AUDITLOGLEVEL_USERACTION) {
            // Get audit log transtype according transaction type
            int auditTranType = AuditLogTranTypes.BPW_CASHCON_DEPOSIT_ENTRY_SENT;

            if ( entryInfo.getStatus().equals( DBConsts.POSTEDON) == true ) {
                if ( entryInfo.getTransactionType().equals( DBConsts.CONCENTRATION ) ) {

                    // deposit
                    auditTranType = AuditLogTranTypes.BPW_CASHCON_DEPOSIT_ENTRY_SENT;
                } else {

                    // disbursement
                    auditTranType = AuditLogTranTypes.BPW_CASHCON_DISBURSEMENT_ENTRY_SENT;
                }
            } else {
                auditTranType = AuditLogTranTypes.BPW_CASHCONHANDLER;

            }

            CashCon.logCCEntryTransAuditLog(dbh,entryInfo, msg, auditTranType );

        }

    }
    
    private FileHandlerProvider getFileHandlerProvider() {
    	return (FileHandlerProvider)OSGIUtil.getBean(FileHandlerProvider.class);
    }
}
