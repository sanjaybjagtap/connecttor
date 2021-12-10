//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2001.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.util.ArrayList;
import java.util.Hashtable;

import com.ffusion.ffs.bpw.interfaces.ACHBatchInfo;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.handlers.IACHBatchAdapter;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.scheduling.constants.ScheduleConstants;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.sap.banking.common.interceptors.PerfLoggerUtil;



//=====================================================================
//
// This class contains a callback handler that is registered in the 
// InstructionType table.  The registered callback handler will be called
// by the Scheduling engine for the ACH batches Processing.
//
//=====================================================================

public class ACHBatchHandler implements
com.ffusion.ffs.bpw.interfaces.DBConsts,FFSConst,
com.ffusion.ffs.bpw.interfaces.BPWResource, BPWScheduleHandler {
    private boolean _okToCall;

    private final String ACH_BATCH_ADAPTER_IMPL_NAME = "com.ffusion.ffs.bpw.fulfill.achadapter.ACHBatchAdapter";
    private IACHBatchAdapter bactchAdapter = null;
    private boolean isResubmit = false;

    public ACHBatchHandler(){
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
    	String methodName = "ACHBatchHandler.eventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);

        FFSDebug.log("ACHBatchHandler.eventHandler: begin, eventSeq=" + eventSequence, PRINT_DEV);

        if ( eventSequence == ScheduleConstants.EVT_SEQUENCE_FIRST ) {         // FIRST sequence

            _okToCall = true;

            Class classDefinition = Class.forName( ACH_BATCH_ADAPTER_IMPL_NAME );
            bactchAdapter = (IACHBatchAdapter) classDefinition.newInstance();

            boolean createEmptyFile = false;
            String FIId = null;
            if (evts._array != null && evts._array.length > 0) {
                createEmptyFile = evts._array[0].createEmptyFile;
                FIId = evts._array[0].FIId;
                if (evts._array[0].ScheduleFlag == ScheduleConstants.SCH_FLAG_RESUBMIT)
                    isResubmit = true;
            }

            // initialize ACH Batch adapter
            bactchAdapter.start(dbh,evts._array[0].cutOffId, evts._array[0].processId,
                                createEmptyFile, FIId);

        } else if ( eventSequence == ScheduleConstants.EVT_SEQUENCE_BATCH_START ) {    // Batch-Start sequence

            // start a batch
            // we don't need to tell anything to adapter

        } else if ( eventSequence == ScheduleConstants.EVT_SEQUENCE_NORMAL ) {    // NORMAL sequence
            if (_okToCall) {
                try {
                    // Invoke only when there are events
                    if ( ( evts != null ) && ( evts._array != null )  ) {
                        // For now, the only thing this object carries is the batchId.
                        ArrayList list = new ArrayList();
//                        ACHBatchInfo[] achBatchInfos = new ACHBatchInfo[ evts._array.length ];
                        String instrutionType=ACHBATCHTRN;
                        // copy evnts over to ACHBatchInfo
                        for ( int i = 0; i < evts._array.length; i++ ) {

                            if ( evts._array[i] != null ) {
                                if ( ( evts._array[i].InstructionType != null ) &&
                                     ( evts._array[i].InstructionType.compareTo( ACHBATCHTRN ) == 0 || evts._array[i].InstructionType.compareTo( SAMEDAYACHBATCHTRN )==0) ) {

                                    ACHBatchInfo achBatchInfo = new ACHBatchInfo();
                                    achBatchInfo.setBatchId( evts._array[i].InstructionID );
                                    if (isResubmit)
                                    {
                                        Hashtable ht = new Hashtable();
                                        ht.put("IsResubmit","true");
                                        achBatchInfo.setExtInfo(ht);
                                    }
                                    list.add(achBatchInfo);
                                    instrutionType=evts._array[i].InstructionType;
                                } else {
                                    FFSDebug.log("ACHBatchHandler.eventHandler: Invalid InstructionType = " + evts._array[i].InstructionType, PRINT_ERR);
                                    FFSDebug.log("ACHBatchHandler.eventHandler: This instruction is skipped. Id: " + evts._array[i].InstructionID, PRINT_ERR);
                                    continue;
                                }
                            }
                        }

                        // Pass to ACHBatchAdapter
                        if (list.size() > 0)
                            bactchAdapter.processACHBatches( (ACHBatchInfo[]) list.toArray(new ACHBatchInfo[0]),instrutionType,dbh );

                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
                    throw e;
                }
            } else {
                FFSDebug.log("ACHBatchHandler.eventHandler: invalid eventSequence = " + eventSequence, PRINT_ERR);
                PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
                throw new Exception( "ACHBatchHandler.eventHandler: invalid eventSequence!" );
            }
        } else if ( eventSequence == ScheduleConstants.EVT_SEQUENCE_BATCH_END ) {    // Batch-End sequence

            // end of a chunk
            // we don't need to tell anything to adapter


        } else if ( eventSequence == ScheduleConstants.EVT_SEQUENCE_LAST ) {    // LAST sequence

            // cleanup ACH Batch adapter
            bactchAdapter.shutdown(dbh);

        }
        FFSDebug.log("ACHBatchHandler.eventHandler: end", PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    }

//=====================================================================
// resubmitEventHandler()
// Description: This method is called by the Scheduling engine during
//    event resubmission.  It will set the possibleDuplicate to true
//    before calling the ToBackend handler.
// Arguments: none 
// Returns:	  none
// Note: 
//=====================================================================
    public void resubmitEventHandler(
                                    int eventSequence,
                                    EventInfoArray evts,
                                    FFSConnectionHolder dbh )
    throws Exception
    {
    	String methodName = "ACHBatchHandler.resubmitEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
        FFSDebug.log("=== ACHBatchHandler.resubmitEventHandler: begin, eventSeq="
                     + eventSequence
                     + ",length="
                     + evts._array.length
                     + ",instructionType="
                     + evts._array[0].InstructionType);
        eventHandler( eventSequence, evts, dbh );
        FFSDebug.log("=== ACHBatchHandler.resubmitEventHandler: end");
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    } 
    
}
