//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2001.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.util.Calendar;
import java.util.Date;

import com.ffusion.ffs.bpw.db.ACHBatch;
import com.ffusion.ffs.bpw.db.ACHFile;
import com.ffusion.ffs.bpw.db.ACHPayee;
import com.ffusion.ffs.bpw.db.SmartCalendar;
import com.ffusion.ffs.bpw.interfaces.ACHBatchInfo;
import com.ffusion.ffs.bpw.interfaces.ACHConsts;
import com.ffusion.ffs.bpw.interfaces.ACHFileInfo;
import com.ffusion.ffs.bpw.interfaces.ACHPayeeInfo;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.ffusion.ffs.util.FFSUtil;
import com.ffusion.util.logging.AuditLogTranTypes;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

//=====================================================================
//
// This class contains represents the schedule that cleans up the
// TempHist table
//
//=====================================================================

public class ACHCleanupHandler implements DBConsts, FFSConst,
BPWResource, BPWScheduleHandler {

    private PropertyConfig propertyConfig = null;
    private final int _logLevel;

    public ACHCleanupHandler()
    {
        PropertyConfig propertyConfig = (PropertyConfig)FFSRegistry.lookup( BPWResource.PROPERTYCONFIG);
        _logLevel = propertyConfig.LogLevel;
    }

    //=====================================================================
    // Description: This method is called by the Scheduling engine
    //=====================================================================
    public void eventHandler(
                            int eventSequence,
                            EventInfoArray evts,
                            FFSConnectionHolder dbh )throws Exception
    {
    	String methodName = "ACHCleanupHandler.eventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);

        if ( eventSequence == 2 ) {    // LAST sequence
            propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);

            String waitInterval = propertyConfig.otherProperties.getProperty(
                                                                            DBConsts.ACHBATCH_WAIT_INTERVAL,
                                                                            DBConsts.ACHBATCH_WAIT_INTERVAL_VALUE);
            int interval           =  30; // 30 minutes
            if (waitInterval == null || waitInterval.trim().length() == 0) {
                String err = "No wait interval specified default "
                             + "value (30 minutes) will be used";
                FFSDebug.log(err);
                interval = 30; // 30 minutes
            } else {
                try {
                    interval = Integer.parseInt(waitInterval);
                } catch (Throwable t) {
                    interval = 30;
                    PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
                }
            }

            interval = interval + ACHConsts.ACH_CLEANUP_INTERVAL_BASE;
            String cutOffTime = FFSUtil.getCutOffTime( interval );

            String fiId = evts._array[0].FIId;
            FFSDebug.log("ACHCleanupHandler.eventHandler: begin cleanIncompleteBatches.  FIID="+fiId, PRINT_DEV);
            cancelIncompleteSingleBatches(dbh, cutOffTime, fiId);
            cancelIncompleteRecBatches(dbh, cutOffTime, fiId);
            FFSDebug.log("ACHCleanupHandler.eventHandler: cleanIncompleteBatches end", PRINT_DEV);

            cleanIncompleteACHFiles(dbh, cutOffTime, fiId);

            updateMaturedPayeePrenoteStatus(dbh, fiId,true);//For Same Day Prenote
            
            updateMaturedPayeePrenoteStatus(dbh, fiId,false);//For Normal Prenote

        }
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

//=====================================================================
// resubmitEventHandler()
// Description: This method is called by the Scheduling engine during
//    event resubmission.
//=====================================================================
    public void resubmitEventHandler(
                                    int eventSequence,
                                    EventInfoArray evts,
                                    FFSConnectionHolder dbh) throws Exception  {
    	String methodName = "ACHCleanupHandler.resubmitEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log("ACHCleanupHandler.resubmitEventHandler: begin",
                     PRINT_DEV);
        eventHandler(eventSequence, evts, dbh);
        FFSDebug.log("ACHCleanupHandler.resubmitEventHandler: end",
                     PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    private void cancelIncompleteSingleBatches(FFSConnectionHolder dbh, String cutOffTime, String fiID)
    throws FFSException
    {
        cancelIncompleteBatches(dbh, cutOffTime, fiID, false);
    }

    private void cancelIncompleteRecBatches(FFSConnectionHolder dbh, String cutOffTime, String fiID)
    throws FFSException
    {
        cancelIncompleteBatches(dbh, cutOffTime, fiID, true);
    }

    /**
     * Cancel batch/recbatch whose submitdate is less than the cutOffTime
     * Step 1: Get all incomplete batches
     * Step 2: cancel them
     * Note: in recurring case, we don't need to cancel single batches for
     * recurring batches, because
     * 1) when single batch created, recurring batch's status should be WILLPROCESSON.
     * 2) This is called after all single batches are canceled.
     *
     * @param dbh
     * @param cutOffTime
     * @param fiID the FIID to be cleaned up
     * @param isRecurring
     * @exception FFSException
     */
    private void cancelIncompleteBatches(FFSConnectionHolder dbh, String cutOffTime, String fiID,
                                         boolean isRecurring)
    throws FFSException
    {

        ACHBatchInfo[] incompleteBatches = ACHBatch.getIncompleteBatches(dbh,cutOffTime,fiID,
                                                                         isRecurring);
        if (incompleteBatches != null) {
            int len = incompleteBatches.length;
            for (int i = 0; i < len; i++) {
                ACHBatch.updateACHBatchStatus(incompleteBatches[i], DBConsts.CANCELEDON,
                                              dbh, isRecurring);
                String preDesc = "Cancel incomplete batch";
                if (isRecurring) {
                    preDesc = "Cancel incomplete recurring batch";
                }
                doTransAuditLog(dbh,incompleteBatches[i], preDesc);
            }
        }

        // QTS 653756: now cancel templates that use an ACH Company that is now CLOSED
        ACHBatchInfo[] closedTemplates = ACHBatch.getACHCompanyClosedTemplates(dbh,fiID,
                                                                         isRecurring);
        if (closedTemplates != null) {
            int len = closedTemplates.length;
            for (int i = 0; i < len; i++) {
                ACHBatch.updateACHBatchStatus(closedTemplates[i], DBConsts.CANCELEDON,
                                              dbh, isRecurring);
                String preDesc = "Cancel TEMPLATE batch using a CLOSED ACH_COMPANY";
                if (isRecurring) {
                    preDesc = "Cancel TEMPLATE recurring batch using a CLOSED ACH_COMPANY";
                }
                doTransAuditLog(dbh,closedTemplates[i], preDesc);
            }
        }
    }

    /**
     * Do transaction audit log which logs this information to
     * audit_log table in CB
     *
     * @param batchInfo ACH Batch Info
     */
    private void doTransAuditLog(FFSConnectionHolder dbh, ACHBatchInfo batchInfo,
                                 String preDesc )
    throws FFSException
    {
        if (_logLevel >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE) {
            ACHBatch.doTransAuditLog(dbh,batchInfo,
                                     preDesc,
                                     batchInfo.getSubmittedBy(),
                                     AuditLogTranTypes.BPW_ACHCLEANUP);
        }
    }

    /**
     * Clean up incomplete ACHFiles:
     *  1) Get all dangling ACH files
     *  2) If there is any such file, delete them. If the loglevel is statusupdate
     *      or above, log it into auditLog
     *
     * @param dbh
     * @param cutOffTime
     * @exception Exception
     */
    public void cleanIncompleteACHFiles(FFSConnectionHolder dbh, String cutOffTime, String fiID)
    throws Exception
    {
    	String methodName = "ACHCleanupHandler.cleanIncompleteACHFiles";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log("ACHCleanupHandler.eventHandler: begin cleanIncompleteACHFiles. CutOff="+cutOffTime+", FIID="+fiID, PRINT_DEV);
        // get all incomplete files for auditLog later with their control (true)
        ACHFileInfo[] fileInfos = null;
        fileInfos = ACHFile.getACHFileInfoToBeDeleted(dbh, true, cutOffTime, fiID);

        // cancel dangling ACH files for the FIID
        // log into auditLog: one by one
        if (fileInfos != null && fileInfos.length > 0 ) {
            for (int i = 0; i < fileInfos.length; i++ ) {
                ACHFile.cancelIncompleteACHFile(dbh, fileInfos[i].getFileId());
                if (_logLevel >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE)
                {
                    ACHFile.doTransAuditLog(dbh,fileInfos[i],fileInfos[i].getSubmittedBy(),
                                            "Cancel an incomplete ACHFile",
                                            AuditLogTranTypes.BPW_ACHCLEANUP);
                }
            }
        }

        FFSDebug.log("ACHCleanupHandler.eventHandler: cleanIncompleteACHFiles end", PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    /**
     * Update all fiid's matured prenote payees' prenote status.
     *
     * @param dbh
     * @param fiId   FIID
     * @exception Exception
     */
    public void updateMaturedPayeePrenoteStatus(FFSConnectionHolder dbh,
                                                String fiId,boolean isSameDay)
    throws Exception
    {
        String currMethodName = "ACHCleanupHandler.updateMaturedPayeePrenoteStatus: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(currMethodName,start);
        FFSDebug.log(currMethodName + "begins, FIID="+fiId, PRINT_DEV);
        // get today
        Calendar cal = Calendar.getInstance();
        java.text.SimpleDateFormat s = new java.text.SimpleDateFormat("yyyyMMdd");
        String todayStr = s.format(cal.getTime());
        int startDateInt = Integer.parseInt( todayStr );

        int prenoteBusinessDays = DBConsts.DEFAULT_BPW_ACH_PAYEE_PRENOTE_BUSINESS_DAYS;
        int sameDayPrenoteBusinessDays = DBConsts.DEFAULT_BPW_ACH_PAYEE_SAME_DAY_PRENOTE_BUSINESS_DAYS;
		if(isSameDay)
		{
			 try {
		         // Default is BPW managed customer
		         String prenoteBusinessDaysSTR =
		         propertyConfig.otherProperties.getProperty(
		                             DBConsts.BPW_ACH_PAYEE_SAME_DAY_PRENOTE_BUSINESS_DAYS,
		                             "" + DBConsts.DEFAULT_BPW_ACH_PAYEE_SAME_DAY_PRENOTE_BUSINESS_DAYS);
		
		         prenoteBusinessDays = new Integer(prenoteBusinessDaysSTR).intValue();
		
		     } catch (Exception ex) {
		    	 PerfLoggerUtil.stopPerfLogging(currMethodName, start, uniqueIndex);
		     }
		}
		else
		{
			 try {
		         // Default is BPW managed customer
		         String prenoteBusinessDaysSTR =
		         propertyConfig.otherProperties.getProperty(
		                             DBConsts.BPW_ACH_PAYEE_PRENOTE_BUSINESS_DAYS,
		                             "" + DBConsts.DEFAULT_BPW_ACH_PAYEE_PRENOTE_BUSINESS_DAYS);
		
		         prenoteBusinessDays = new Integer(prenoteBusinessDaysSTR).intValue();
		
		     } catch (Exception ex) {
		    	 PerfLoggerUtil.stopPerfLogging(currMethodName, start, uniqueIndex);
		     }
		}
      
       
		int previousBusinessDays=prenoteBusinessDays;
		if(isSameDay)
		{
			previousBusinessDays=sameDayPrenoteBusinessDays;
		}

        // move to 6 previous business day
        for ( int i = 0; i < previousBusinessDays; i++ ) {
            startDateInt = SmartCalendar.getBusinessDay(fiId, startDateInt, false ); // false: previous
        }

        // String: 20031011
        String matureDateStr = ( new Integer(startDateInt ) ).toString();

        // Date: 20031011
        Date matureDate = s.parse( matureDateStr );

        // parse to validate format of duedate
        // Date: 2003/10/11
        java.text.SimpleDateFormat s2 = new java.text.SimpleDateFormat(DBConsts.ACHPAYEE_PRENOTE_DATE_FORMAT );
        s2.setLenient(false);
        String formattedMatureDateStr = s2.format( matureDate );

        // get all mature payees if the log level is statusupdate
        ACHPayeeInfo[] payeeInfos = null;
        payeeInfos = ACHPayee.getMaturedACHPayeeInfo(dbh, matureDateStr, fiId,isSameDay);

        // log into auditLog: one by one
        if (payeeInfos != null && payeeInfos.length > 0 ) {
            for (int i = 0; i < payeeInfos.length; i++ ) {
                int maturePayeePrenote = ACHPayee.updateMaturedACHPayeePrenoteStatus( dbh,  formattedMatureDateStr, payeeInfos[i].getPayeeID() );
                FFSDebug.log(currMethodName + "matured ACH Payee prenotes: " + maturePayeePrenote, PRINT_DEV);
                if (_logLevel >= DBConsts.AUDITLOGLEVEL_STATUSUPDATE)
                {
                    try
                    {
                    ACHPayee.doTransAuditLog(dbh,payeeInfos[i],payeeInfos[i].getSubmittedBy(),
                                             "Update prenote status of an ACHPayee",
                                             AuditLogTranTypes.BPW_ACHCLEANUP);
                    } catch(Exception e)
                    {
                    	PerfLoggerUtil.stopPerfLogging(currMethodName, start, uniqueIndex);
                        // we don't care if there was an error updating the audit log
                    }
                }
            }
        }


        FFSDebug.log(currMethodName + " ends", PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(currMethodName, start, uniqueIndex);
    }   
}
