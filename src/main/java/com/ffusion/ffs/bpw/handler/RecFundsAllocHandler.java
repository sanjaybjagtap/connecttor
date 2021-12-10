//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2001.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import com.ffusion.ffs.bpw.db.Payee;
import com.ffusion.ffs.bpw.db.PmtInstruction;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.OFXException;
import com.ffusion.ffs.bpw.interfaces.PayeeInfo;
import com.ffusion.ffs.bpw.interfaces.PmtInfo;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.scheduling.constants.ScheduleConstants;
import com.ffusion.ffs.scheduling.db.ScheduleInfo;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.sap.banking.common.interceptors.PerfLoggerUtil;


//=====================================================================
//
// This class contains a callback handler that is registered in the
// InstructionType table.  The registered callback handler will be called
// by the Scheduling engine for the Recurring Funds Alloc Processing.
//
//=====================================================================

public class RecFundsAllocHandler implements BPWScheduleHandler{
    public RecFundsAllocHandler()
    {
    }

//=====================================================================
// eventHandler()
// Description: This method is called by the Scheduling engine
// Arguments: none
// Returns:   none
// Note:
//=====================================================================
    public void eventHandler(
                            int eventSequence,
                            EventInfoArray evts,
                            FFSConnectionHolder dbh )
    throws FFSException
    {
    	String methodName = "RecFundsAllocHandler.eventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log("=== RecFundsAllocHandler.eventHander: begin, eventSeq=" + eventSequence
                     + ",length=" + evts._array.length, FFSConst.PRINT_DEV);

        try {
            // =================================
            // Do event sequence
            // =================================
            if ( eventSequence == 0 ) {   // FIRST sequence
            } else if ( eventSequence == 1 ) {   // NORMAL sequence

                for (int i = 0; i < evts._array.length; i++) {



                 	if (evts._array[i] == null) {

 						FFSDebug.log("RecFundsAllocHandler.eventHander: ",
 								" ++--++ Invalid Transaction in this batch: "
 										+ evts._array[i],
 								", Transcaction will be ignored, Transaction counter: "
 										+ i, FFSConst.PRINT_DEV);
 						continue;
 					}


                    String srvrTID = evts._array[i].InstructionID;
                    PmtInstruction pmt = PmtInstruction.getPmtInstr( srvrTID, dbh );
                    if (pmt == null) {
                        String msg = "*** RecFundsAllocHandler.eventHandler failed: could not find the SrvrTID="+srvrTID+" in BPW_PmtInstruction";
                        FFSDebug.console(msg);
                        FFSDebug.log(msg);
                        continue;
                    }
                    PmtInfo pmtinfo = pmt.getPmtInfo();
                    PayeeInfo payeeinfo = Payee.findPayeeByID(pmtinfo.PayeeID, dbh);
                    if (payeeinfo == null) {
                        String msg = "*** RecFundsAllocHandler.eventHandler failed: could not find the PayeeID="+pmtinfo.PayeeID+" in BPW_Payee for pmt of SrvrTID="+srvrTID;
                        FFSDebug.console(msg);
                        FFSDebug.log(msg);
                        continue;
                    }
                    ScheduleInfo sinfo = ScheduleInfo.getScheduleInfo(evts._array[i].FIId,
                                                                      DBConsts.RECFUNDTRN, srvrTID, dbh );
                    if ( (sinfo != null) &&
                         (sinfo.StatusOption != ScheduleConstants.SCH_STATUSOPTION_CLOSE_SCHEDULED) ) {

                        String status = PmtInstruction.getStatus(srvrTID, dbh);
                        if (status.equals(DBConsts.WILLPROCESSON)) {
                            // When the pmt.status is WILLPROCESSON, we will not create a FUNDTRN;
                            // Since the RECFUNDTRN scheduleInfo was updated,the CurInstanceNum
                            // was increased already, so we need to rollback the RECFUNDTRN scheduleInfo.
                            sinfo.CurInstanceNum = sinfo.CurInstanceNum - 1;
                            ScheduleInfo.computeNextInstanceDate(sinfo);
                            ScheduleInfo.modifySchedule(dbh, sinfo.ScheduleID, sinfo);
                            continue;
                        } else if (!status.equals(DBConsts.INFUNDSALLOC)
                                   && !status.equals(DBConsts.NOFUNDSON)
                                   && !status.equals(DBConsts.NOFUNDSON_NOTIF)) {
                            ScheduleInfo.cancelSchedule(dbh, DBConsts.RECFUNDTRN, srvrTID);
                            continue;
                        }

                        // create FUNDTRN to retry
                        ScheduleInfo.moveNextInstanceDate(sinfo, -1);  // take yesterday of sinfo
                        ScheduleInfo info = createScheduleInfo( sinfo.NextInstanceDate, sinfo.LogID, pmtinfo.FIID );
                        String scheduleID = ScheduleInfo.createSchedule( dbh, DBConsts.FUNDTRN, srvrTID, info);
                    }
                }

            } else if ( eventSequence == 2 ) {   // LAST sequence
            } else if ( eventSequence == 3 ) {   // Batch-Start sequence
            } else if ( eventSequence == 4 ) {   // Batch-End sequence
            }
        } catch (Exception exc) {
            String errDescrip = "*** RecFundsAllocHandler.eventHandler failed:";
            FFSDebug.log( errDescrip + exc);
            PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
            throw new FFSException(exc.toString());
        }

        FFSDebug.log("==== RecFundsAllocHandler.eventHander: end", FFSConst.PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

//=====================================================================
// resubmitEventHandler()
// Description: This method is called by the Scheduling engine during
//    event resubmission.  It will set the possibleDuplicate to true
//    before calling the ToBackend handler.
// Arguments: none
// Returns:   none
// Note:
//=====================================================================
    public void resubmitEventHandler(
                                    int eventSequence,
                                    EventInfoArray evts,
                                    FFSConnectionHolder dbh )
    throws Exception
    {
    	String methodName = "RecFundsAllocHandler.resubmitEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log("=== RecFundsAllocHandler.resubmitEventHandler: begin, eventSeq="
                     + eventSequence
                     + ",length="
                     + evts._array.length
                     + ",instructionType="
                     + evts._array[0].InstructionType);
        eventHandler( eventSequence, evts, dbh );
        FFSDebug.log("=== RecFundsAllocHandler.resubmitEventHandler: end");
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

    private ScheduleInfo createScheduleInfo(int startdate, String logID, String fiID)
    throws Exception
    {
        ScheduleInfo info = new ScheduleInfo();
        info.FIId = fiID;
        info.Status = ScheduleConstants.SCH_STATUS_ACTIVE;
        info.Frequency = ScheduleConstants.SCH_FREQ_ONCE;
        info.StartDate = startdate;
        info.NextInstanceDate = startdate;

        info.InstanceCount = 1;
        info.LogID = logID;
        info.CurInstanceNum = 1;
        return info;
    }
}
