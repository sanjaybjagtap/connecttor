//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2001.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.util.Calendar;
import java.util.Date;

import com.ffusion.ffs.bpw.config.BPWAdmin;
import com.ffusion.ffs.bpw.db.CashCon;
import com.ffusion.ffs.bpw.db.DBInstructionType;
import com.ffusion.ffs.bpw.db.SmartCalendar;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.CCLocationInfo;
import com.ffusion.ffs.bpw.interfaces.CutOffInfo;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.handlers.ICashConAdapter;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.scheduling.constants.ScheduleConstants;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.ffusion.util.logging.AuditLogTranTypes;
import com.sap.banking.common.interceptors.PerfLoggerUtil;


//=====================================================================
//
// This class contains a callback handler that is registered in the 
// InstructionType table.  The registered callback handler will be called
// by the Scheduling engine for the ACH files Processing.
//
//=====================================================================

public class CashConHandler implements
com.ffusion.ffs.bpw.interfaces.DBConsts,FFSConst,
com.ffusion.ffs.bpw.interfaces.BPWResource,BPWScheduleHandler {

    private final String CASHCON_ADAPTER_IMPL_NAME = "com.ffusion.ffs.bpw.fulfill.achadapter.CashConAdapter";
    private final ICashConAdapter cashConAdapter = null;
    private boolean _okToCall = false;
   private BPWAdmin _bpwAdmin;
    private ICashConAdapter _cashConAdapter;
    private final int _logLevel;
    
  
    
    private final PropertyConfig propertyConfig;
    private boolean isSameDayCashConEnabled = false;

   
    public CashConHandler() {
    	propertyConfig = (PropertyConfig)FFSRegistry.lookup( BPWResource.PROPERTYCONFIG);
    	_logLevel = propertyConfig.LogLevel;

    	String sameDayCashconEnabled = propertyConfig.otherProperties.getProperty(DBConsts.SAME_DAY_CASH_CON_SUPPORTED, String.valueOf(isSameDayCashConEnabled));
    

   
    	if (sameDayCashconEnabled.equalsIgnoreCase(String.valueOf(true))) {
    		this.isSameDayCashConEnabled = true;
    	}
   
    }

//=====================================================================
// Description: This method is called by the Scheduling engine
//=====================================================================
    public void eventHandler(
                            int eventSequence,
                            EventInfoArray evts,
                            FFSConnectionHolder dbh )
    throws Exception
    {
        String currMethodName = "CashConHandler.eventHandler: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(currMethodName,start);
        FFSDebug.log(currMethodName + " begin, eventSeq=" + eventSequence, PRINT_DEV);

        if ( eventSequence == ScheduleConstants.EVT_SEQUENCE_FIRST ) {
            firstEventHandler();
        } else if ( eventSequence == ScheduleConstants.EVT_SEQUENCE_LAST ) {
            lastEventHandler(dbh,evts,false, false); // false: this is a new submit
                                                     // false: this is not crash recovery
        }
        FFSDebug.log("CashConHandler.eventHandler: end", PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(currMethodName, start, uniqueIndex);
    }


    public void resubmitEventHandler(int eventSequence,
                                     EventInfoArray evts,
                                     FFSConnectionHolder dbh )
    throws Exception
    {
    	String methodName = "CashConHandler.eventHandler: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log("=== CashConHandler.resubmitEventHandler: begin, eventSeq="
                     + eventSequence
                     + ",length="
                     + evts._array.length
                     + ",instructionType="
                     + evts._array[0].InstructionType);
        if ( eventSequence == ScheduleConstants.EVT_SEQUENCE_FIRST ) {
            firstEventHandler();
        } else if ( eventSequence == ScheduleConstants.EVT_SEQUENCE_LAST ) {

            boolean reRunCutOff = false;
            boolean crashRecovery = false;

            if (evts._array[0].ScheduleFlag == ScheduleConstants.SCH_FLAG_RESUBMIT ) {

                reRunCutOff = true;
            } else {
                crashRecovery = true;
            }

            lastEventHandler(dbh,evts, reRunCutOff, crashRecovery ); // true: this is a re-run cut off
        }
        FFSDebug.log("=== CashConHandler.resubmitEventHandler: end");
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    /**
     * process event handler when it recieves EVT_SEQUENCE_FIRST:
     * start callback adapter
     */
    private void firstEventHandler() throws Exception
    {
        _okToCall = true;            
        Class classDefinition = Class.forName( CASHCON_ADAPTER_IMPL_NAME );
        _cashConAdapter = (ICashConAdapter) classDefinition.newInstance();

        // initialize Cash Con adapter
        _cashConAdapter.start();
    }

    private void lastEventHandler(FFSConnectionHolder dbh,
                                  EventInfoArray evts,
                                  boolean reRunCutOff,
                                  boolean crashRecovery ) throws Exception
    {
        if (_okToCall) {
            this.updateMaturedLocationPrenoteStatus(dbh, evts._array[0].FIId );
            try {
                // Invoke only when there are events
                if ( ( evts != null ) && ( evts._array != null )  ) {

                    if ( evts._array[0] != null ) {
                    	// Check valid instruction type.
                        if (checkValidInstructionType(evts._array[0].InstructionType)) {
                            if (evts._array[0].cutOffId != null) {
                                CutOffInfo cutOffInfo = new CutOffInfo();                                    
                                cutOffInfo.setCutOffId( evts._array[0].cutOffId );
                                cutOffInfo = DBInstructionType.getCutOffById(dbh,cutOffInfo);
                                if (cutOffInfo.getStatusCode() == DBConsts.SUCCESS) {
                                    processOneCutOff(dbh, cutOffInfo, 
                                                     evts._array[0].FIId, 
                                                     evts._array[0].processId,
                                                     evts._array[0].createEmptyFile,
                                                     reRunCutOff,
                                                     crashRecovery );
                                } else {
                                }

                            } else {
                                // Run Now request
                                processRunNow(dbh, evts._array[0].FIId, 
                                                              evts._array[0].processId,
                                                              evts._array[0].createEmptyFile,
                                                              reRunCutOff,
                                                              crashRecovery );
                            }
                        } else {
                            FFSDebug.log("Invalid InstructionType = " + evts._array[0].InstructionType, PRINT_ERR);
                            FFSDebug.log( "This instruction is skipped. Id: " + evts._array[0].InstructionID, PRINT_ERR);                               
                        }
                    }
                }

            } catch (Exception e) {
                FFSDebug.log( "***CashConHandler.lastEventHandler: Exception: " + e, FFSDebug.PRINT_ERR );
                throw e;
            }
        }
    }

    /**
     * Check Valid Instruction Type.
     * 
     * @param instructionType
     * @return boolean value
     */
    public boolean checkValidInstructionType (String instructionType) {
        return (instructionType != null) &&
                (instructionType.compareTo(CASHCONTRN) == 0);
    }
    
    /**
     * Process Cutoff
     * 
     *  Method processOneCutOff(CutOffInfo cutOffInfo, String processId): 
     *  Create file and FileHeader in temp directory.
     *  Get all CC Companies with this cutOffId.
     *  Create a new fileCache to calculate some info in fileControl.
     *  Start db transaction:
     *  For each CC Company:
     *  Create a batch header. Append its content to the file
     *  Read CC_CompanyCutOff to get transactionType.		
     *  processOneCompany(CompInfo, processedId, fileCache, file)            
     *  Create a batch control. Append its content to the file.
     *  Get CreateEmptyFile from SCH_InstructionType by FIID and instructionType.
     *  If (CreateEmptyFile is true || fileCache.recCount > 0) {
     *  Append FileControl to the file.
     * @param dbh the dbh
     * @param cutOffInfo the cutOffInfo
     * @param FIId the FIId
     * @param processId the processId
     * @param createEmptyFile the createEmptyFile
     * @param reRunCutOff the reRunCutOff
     * @param crashRecovery the crashRecovery
     * @throws Exception the Exception
     */
    public void processOneCutOff(FFSConnectionHolder dbh, CutOffInfo cutOffInfo,
            String FIID, String processId,
            boolean createEmptyFile,
            boolean reRunCutOff,
            boolean crashRecovery ) throws Exception {
    	String methodName = "CashConHandler.processOneCutOff";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
    	
    	 _cashConAdapter.processOneCutOff(dbh, cutOffInfo, 
    			 FIID, 
                 processId,
                 createEmptyFile,
                 reRunCutOff,
                 crashRecovery, this.isSameDayCashConEnabled, isSameDayCashConTran() // false - CASHCONTRN , true SAMEDAYCASHCONTRN
                 );
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
                              boolean crashRecovery ) 
    throws Exception
    {
    	String methodName = "CashConHandler.processRunNow";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
    	_cashConAdapter.processRunNow(dbh, FIId, 
    			processId,
    			createEmptyFile,
    			reRunCutOff,
    			crashRecovery, this.isSameDayCashConEnabled, 
    			isSameDayCashConTran() // false - CASHCONTRN , true SAMEDAYCASHCONTRN
    			);
    	PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }
    
    /**
     * CASHCONTRN - Not same day cash con tran. (false - CASHCONTRN , true SAMEDAYCASHCONTRN)
     * 
     * @return boolean value
     */
    public boolean isSameDayCashConTran() {
    	return false;
    }
    
    /**
     * Check if same day cash con is supported or not
     * 
     * @return isSameDayCashConEnabled - boolean value
     */
	public boolean isSameDayCashConEnabled() {
		return isSameDayCashConEnabled;
	}
	
    /**
     * Update all fiid's matured location prenote  status. - CASHCONTRN
     * 
     * @param dbh
     * @param fiid   FIID
     * @exception Exception
     */
    public void updateMaturedLocationPrenoteStatus(FFSConnectionHolder dbh,
                                                   String fiId) throws Exception {
    	updateMaturedLocationPrenoteStatus(dbh, fiId, this.isSameDayCashConEnabled, isSameDayCashConTran());  // false - CASHCONTRN , true SAMEDAYCASHCONTRN)
    }
    
	/**
	 * Gets the number of business days a prenote before the payee can be used.
	 * 
	 * @return prenoteBusinessDays
	 */
	public int getPrenoteBusinessDays() {
		int prenoteBusinessDays = DBConsts.DEFAULT_BPW_ACH_PAYEE_PRENOTE_BUSINESS_DAYS;
		try {
			prenoteBusinessDays = getPrenoteBusinessDays(DBConsts.BPW_ACH_PAYEE_PRENOTE_BUSINESS_DAYS, 
					DBConsts.DEFAULT_BPW_ACH_PAYEE_PRENOTE_BUSINESS_DAYS);
		} catch (Exception ex) {
			// Do nothing
		}
		return prenoteBusinessDays;
	}
    
	/**
	 * Gets prenote or same day prenote business days.
	 * 
	 * @param prenoteBusinessDays
	 * @param defaultPrenoteBusinessDays
	 * @return prenoteBusinessDays
	 */
	public int getPrenoteBusinessDays(String prenoteBusinessDays, int defaultPrenoteBusinessDays) {
		// Default is BPW managed customer
		return new Integer(propertyConfig.otherProperties.getProperty(prenoteBusinessDays, String.valueOf(defaultPrenoteBusinessDays))).intValue();
	}
	
	 /**
     * Get the number of deposit locations whose date is matured
     *
     * @param dbh
     * @param matureDate The date to be matured
     * @return The array of CCLocationInfo objects
     * @exception FFSException
     */
    public CCLocationInfo[] getMaturedDepositLocationInfo(FFSConnectionHolder dbh, String matureDate)
    		throws FFSException 
    {
    	return CashCon.getMaturedDepositLocationInfo(dbh, matureDate, this.isSameDayCashConEnabled, isSameDayCashConTran());
    }
    
    /**
     * Update matured deposit prenote status
     *
     * @param dbh
     * @return number of records updated
     * @exception FFSException
     */
    public int updateMaturedDepositLocationPrenoteStatus(FFSConnectionHolder dbh,
                                                                String matureDateStr)
    throws FFSException
    {
    	return CashCon.updateMaturedDepositLocationPrenoteStatus( dbh,  matureDateStr, this.isSameDayCashConEnabled, isSameDayCashConTran());
    }
    
    /**
     * Get the number of Disbursement locations whose date is matured
     *
     * @param dbh
     * @param matureDate The date to be matured
     * @return The array of CCLocationInfo objects
     * @exception FFSException
     */
    public CCLocationInfo[] getMaturedDisbursementLocationInfo(FFSConnectionHolder dbh,
                                                                      String matureDate)
    throws FFSException {
    	return  CashCon.getMaturedDisbursementLocationInfo(dbh, matureDate, this.isSameDayCashConEnabled, isSameDayCashConTran());
    }
    
    /**
     * Update matured disbursement prenote status
     *
     * @param dbh
     * @return number of records updated
     * @exception FFSException
     */
    public int updateMaturedDisbursementLocationPrenoteStatus(FFSConnectionHolder dbh,
                                                                     String matureDateStr)
    throws FFSException
    {
    	return CashCon.updateMaturedDisbursementLocationPrenoteStatus( dbh,  matureDateStr, this.isSameDayCashConEnabled, isSameDayCashConTran() );
    }

    /**
     * Update all fiid's matured location prenote  status.
     * 
     * @param dbh
     * @param fiId   FIID
     * @exception Exception
     */
    public void updateMaturedLocationPrenoteStatus(FFSConnectionHolder dbh,
                                                   String fiId,
                                                   boolean isSameDayCashConEnabled, 
                                                   boolean isSameDayCashConTran )
    throws Exception {

        String currMethodName = "CashConHandler.updateMaturedLocationPrenoteStatus: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(currMethodName,start);
        FFSDebug.log(currMethodName + "begins", PRINT_DEV);
        // get today
        Calendar cal = Calendar.getInstance();
        java.text.SimpleDateFormat s = new java.text.SimpleDateFormat("yyyyMMdd");
        String todayStr = s.format(cal.getTime());
        int startDateInt = Integer.parseInt( todayStr );
        int prenoteBusinessDays = 0;
        		
        try {
        	prenoteBusinessDays = getPrenoteBusinessDays();
        } catch (Exception ex) {
        	PerfLoggerUtil.stopPerfLogging(currMethodName, start, uniqueIndex);
        	// Do nothing
        }


        // move to 6 previous business day
        for ( int i = 0; i < prenoteBusinessDays; i++ ) {
            startDateInt = SmartCalendar.getBusinessDay(fiId, startDateInt, false ); // false: previous
        }

        // String: 20031011
        String matureDateStr = ( new Integer(startDateInt ) ).toString();

        // Date: 20031011
        Date matureDate = s.parse( matureDateStr );

        // parse to validate format of duedate
        // Date: 2003/10/11
        java.text.SimpleDateFormat s2 = new java.text.SimpleDateFormat(DBConsts.LASTREQUESTTIME_DATE_FORMAT );
        s2.setLenient(false);
        String formattedMatureDateStr = s2.format( matureDate );

        // get all mature Deposit Location if the log level is statusupdate
        CCLocationInfo [] locationInfos = null;
        if (_logLevel >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE) {
            locationInfos = getMaturedDepositLocationInfo(dbh, matureDateStr);
        }

        int maturelocationPrenote = updateMaturedDepositLocationPrenoteStatus( dbh,  formattedMatureDateStr );
        FFSDebug.log(currMethodName + "matured deposit location prenotes: " + maturelocationPrenote, PRINT_DEV);
        // log into auditLog: one by one
        int len = 0;
        if (locationInfos != null) {
            len = locationInfos.length;
        }
        if (_logLevel >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE && len > 0 ) {

            for (int i = 0; i < len; i++ ) {
                CashCon.logCCLocationTransAuditLog(dbh,
                                                   locationInfos[i],
                                                  "Update prenote status of a Location",
                                                   AuditLogTranTypes.BPW_CASHCONHANDLER); 

            }
        }

        // get all mature Disbursement Location if the log level is statusupdate
        locationInfos = null;
        if (_logLevel >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE) {
            locationInfos = getMaturedDisbursementLocationInfo(dbh, matureDateStr);
        }

        maturelocationPrenote = updateMaturedDisbursementLocationPrenoteStatus( dbh,  formattedMatureDateStr );
        FFSDebug.log(currMethodName + "matured disbursement location prenotes: " + maturelocationPrenote, PRINT_DEV);
        // log into auditLog: one by one
        len = 0;
        if (locationInfos != null) {
            len = locationInfos.length;
        }
        if (_logLevel >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE && len > 0 ) {

            for (int i = 0; i < len; i++ ) {
                CashCon.logCCLocationTransAuditLog(dbh,
                                                   locationInfos[i],
                                                  "Update prenote status of a Location",
                                                   AuditLogTranTypes.BPW_CASHCONHANDLER); 

            }
        }

        FFSDebug.log(currMethodName + " ends", PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(currMethodName, start, uniqueIndex);
    }
}
