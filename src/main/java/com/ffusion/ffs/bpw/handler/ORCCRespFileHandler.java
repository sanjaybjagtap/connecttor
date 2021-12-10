//
// Confidential property of Financial Fusion, Inc.
// (c) Copyright Financial Fusion, Inc. 2000-2001.
// All rights reserved
//

package com.ffusion.ffs.bpw.handler;

import java.util.HashMap;
import java.util.Map;

import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.handlers.BPWFulfillmentHandlerProvider;
import com.ffusion.ffs.bpw.interfaces.handlers.BPWFulfillmentRespFileHandler;
import com.ffusion.ffs.bpw.util.OSGIUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.sap.banking.common.interceptors.PerfLoggerUtil;



//=====================================================================
//
// This class contains a callback handler that is registered in the 
// InstructionType table.  The registered callback handler will be called
// by the Scheduling engine for the customer files Processing.
//
//=====================================================================

public class ORCCRespFileHandler implements
   com.ffusion.ffs.bpw.interfaces.DBConsts,FFSConst,
   com.ffusion.ffs.bpw.interfaces.BPWResource, BPWScheduleHandler
{
   //private ORCCRespFileHandlerImpl _respFile = null;
   private boolean _okToCall;

   public ORCCRespFileHandler(){
	   // _respFile = new ORCCRespFileHandlerImpl();
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
	   String methodName = "ORCCRespFileHandler.eventHandler";
       long start = System.currentTimeMillis();
       int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);

      FFSDebug.log("ORCCRespFileHandler.eventHandler: begin, eventSeq=" + eventSequence, PRINT_DEV);
      if ( eventSequence == 0 ) {         // FIRST sequence
          _okToCall = false;
      }
      else if ( eventSequence == 1 ) {    // NORMAL sequence
          _okToCall = true;
      }
      else if ( eventSequence == 2 ) {    // LAST sequence
          if (_okToCall){
               try {
/*                   Class classDefinition = Class.forName("com.ffusion.ffs.bpw.fulfill.orcc.ORCCRespFileHandlerImpl");
                   Object respImpl = classDefinition.newInstance();
                   Class[] parameterTypes = new Class[] {FFSConnectionHolder.class};
                  
                   Object[] arguments = new Object[] {dbh};
                   Method processMethod = classDefinition.getMethod("processResponseFiles", parameterTypes);
                   processMethod.invoke(respImpl, arguments);
 */
           	    	Map<String,Object> extra = new HashMap<String,Object>();   
              		BPWFulfillmentHandlerProvider fulfillmentHandlerProvider = (BPWFulfillmentHandlerProvider) OSGIUtil.getBean(BPWFulfillmentHandlerProvider.class);
         			BPWFulfillmentRespFileHandler fulfillmentRespFileHandler = fulfillmentHandlerProvider.getBPWFulfillmentRespFileHandler("com.ffusion.ffs.bpw.fulfill.orcc.ORCCRespFileHandlerImpl");
         			fulfillmentRespFileHandler.processResponseFiles(dbh, extra);
 
               } catch (Exception e) {
                   e.printStackTrace();
                   PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
                   throw e;
               } 
          }
      }
      else if ( eventSequence == 3 ) {    // Batch-Start sequence
      }
      else if ( eventSequence == 4 ) {    // Batch-End sequence
      }
      FFSDebug.log("ORCCRespFileHandler.eventHandler: end", PRINT_DEV);
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
	   String methodName = "ORCCRespFileHandler.resubmitEventHandler";
       long start = System.currentTimeMillis();
       int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
      FFSDebug.log("=== ORCCRespFileHandler.resubmitEventHandler: begin, eventSeq="
                                      + eventSequence
                                      + ",length="
                                      + evts._array.length
                                      + ",instructionType="
                                      + evts._array[0].InstructionType);
      eventHandler( eventSequence, evts, dbh );
      FFSDebug.log("=== ORCCRespFileHandler.resubmitEventHandler: end");
      PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
   } 
   
}
