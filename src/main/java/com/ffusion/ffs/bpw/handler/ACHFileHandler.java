//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2001.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.util.ArrayList;
import java.util.Hashtable;

import com.ffusion.ffs.bpw.interfaces.ACHFileInfo;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.handlers.IACHFileAdapter;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.scheduling.constants.ScheduleConstants;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.sap.banking.common.interceptors.PerfLoggerUtil;



//=====================================================================
//
// This class contains a callback handler that is registered in the 
// InstructionType table.  The registered callback handler will be called
// by the Scheduling engine for the ACH files Processing.
//
//=====================================================================

public class ACHFileHandler implements
com.ffusion.ffs.bpw.interfaces.DBConsts,FFSConst,
com.ffusion.ffs.bpw.interfaces.BPWResource,BPWScheduleHandler {
    private boolean _okToCall;

    private final String ACH_FILE_ADAPTER_IMPL_NAME = "com.ffusion.ffs.bpw.fulfill.achadapter.ACHFileAdapter";
    private IACHFileAdapter fileAdapter = null;
    private String _FIId;
    private boolean isResubmit = false;

    public ACHFileHandler(){
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
    	String methodName = "ACHFileHandler.eventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);

        FFSDebug.log("ACHFileHandler.eventHandler: begin, eventSeq=" + eventSequence, PRINT_DEV);

        if ( eventSequence == ScheduleConstants.EVT_SEQUENCE_FIRST ) {         // FIRST sequence

            _okToCall = true;

            Class classDefinition = Class.forName( ACH_FILE_ADAPTER_IMPL_NAME );
            fileAdapter = (IACHFileAdapter) classDefinition.newInstance();

            // initialize ACH File adapter
            fileAdapter.start(evts._array[0].cutOffId, evts._array[0].processId);
            _FIId = evts._array[0].FIId;
            if (evts._array[0].ScheduleFlag == ScheduleConstants.SCH_FLAG_RESUBMIT)
                isResubmit = true;
        } else if ( eventSequence == ScheduleConstants.EVT_SEQUENCE_BATCH_START ) {    // Batch-Start sequence
        } else if ( eventSequence == ScheduleConstants.EVT_SEQUENCE_NORMAL ) {    // NORMAL sequence
            if (_okToCall) {
                try {
                    // Invoke only when there are events
                    if ( ( evts != null ) && ( evts._array != null )  ) {
                        // For now, the only thing this object carries is the fileId.
                        ArrayList list = new ArrayList();
                        String instrutionType=ACHFILETRN;
                        // copy evnts over to ACHFileInfo
                        for ( int i = 0; i < evts._array.length; i++ ) {

                            if ( evts._array[i] != null ) {
                                if ( ( evts._array[i].InstructionType != null ) &&
                                     (( evts._array[i].InstructionType.compareTo( ACHFILETRN ) == 0 ) ||
                                    		 ( evts._array[i].InstructionType.compareTo( SAMEDAYACHFILETRN ) == 0 )	 
                                    		 ) ) {

                                    ACHFileInfo achFileInfo = new ACHFileInfo();
                                    achFileInfo.setFileId( evts._array[i].InstructionID );
                                    achFileInfo.setFiId(_FIId);
                                    if (isResubmit)
                                    {
                                        Hashtable ht = new Hashtable();
                                        ht.put("IsResubmit","true");
                                        achFileInfo.setMemo(ht);
                                    }
                                    list.add(achFileInfo);
                                    instrutionType=evts._array[i].InstructionType;
                                } else {
                                    FFSDebug.log("ACHFileHandler.eventHandler: Invalid InstructionType = " + evts._array[i].InstructionType, PRINT_ERR);
                                    FFSDebug.log("ACHFileHandler.eventHandler: This instruction is skipped. Id: " + evts._array[i].InstructionID, PRINT_ERR);
                                    continue;
                                }
                            }
                        }

                        // Pass to ACHFileAdapter
                        if (list.size() > 0)
                            fileAdapter.processACHFiles( (ACHFileInfo[]) list.toArray(new ACHFileInfo[0]),instrutionType,dbh );
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
                    throw e;
                }
            } else {
                FFSDebug.log("ACHFileHandler.eventHandler: invalid eventSequence = " + eventSequence, PRINT_ERR);
                PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
                throw new Exception( "ACHFileHandler.eventHandler: invalid eventSequence!" );
            }
        } else if ( eventSequence == ScheduleConstants.EVT_SEQUENCE_BATCH_END ) {    // Batch-End sequence
        } else if ( eventSequence == ScheduleConstants.EVT_SEQUENCE_LAST ) {    // LAST sequence

            // cleanup ACH File adapter
            fileAdapter.shutdown();

        }
        FFSDebug.log("ACHFileHandler.eventHandler: end", PRINT_DEV);
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
    	String methodName = "ACHFileHandler.resubmitEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
    	
        FFSDebug.log("=== ACHFileHandler.resubmitEventHandler: begin, eventSeq="
                     + eventSequence
                     + ",length="
                     + evts._array.length
                     + ",instructionType="
                     + evts._array[0].InstructionType);
        eventHandler( eventSequence, evts, dbh );
        FFSDebug.log("=== ACHFileHandler.resubmitEventHandler: end");
        PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
    } 
}
