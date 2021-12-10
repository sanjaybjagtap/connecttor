package com.ffusion.ffs.bpw.fulfill.checkfree;

import java.util.Map;
import java.util.Properties;

import com.ffusion.ffs.bpw.BPWServer;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.handlers.BPWFulfillmentRespFileHandler;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

/**
 * Schedule a task that executes once every second.
 */


public class CheckFreeRespFileHandlerImpl implements FFSConst, BPWResource , BPWFulfillmentRespFileHandler
{
    private String         dir            = null;
    private PropertyConfig propertyConfig = null;
    
    private static final String POSSIBLE_DUPLICATE_FLAG = "POSSIBLE_DUPLICATE_FLAG";

    public CheckFreeRespFileHandlerImpl() {
        propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);
        
        dir = propertyConfig.otherProperties.getProperty(DBConsts.CHECKFREE_IMPORT_DIR,
							 CheckFreeConsts.DEFAULT_IMPORT_DIR);
        if (dir == null || dir.length() == 0) {
            String err = "Failed to start CheckFree file checker. " +
                         "\nImport directory is invalid (null). Make sure import directory is " +
                         " the system path and try again";
            System.out.println(err);
            FFSDebug.log(err,FFSDebug.PRINT_ERR);
        }
    }

    /***/
    public void processResponseFiles(FFSConnectionHolder dbh,Map<String,Object> extra){

    	String method = "CheckFreeRespFileHandlerImpl.processResponseFiles";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        FFSDebug.log("CheckFree Connector start checking for response files");
        FFSDebug.console("CheckFree Connector start checking for response files");
        Boolean possibleDuplicate = false;
        possibleDuplicate = (Boolean)extra.get(POSSIBLE_DUPLICATE_FLAG);
        try {
            FFSDebug.log("Calling:CheckFreeHandler to check file: " + this.dir );
	        CheckFreeHandler.lock();
            //Make sure we set the handler for Crash Recovery mode
	    	CheckFreeHandler.setPossibleDuplicate(possibleDuplicate.booleanValue());

	        CheckFreeFilesChecker cfc = new CheckFreeFilesChecker(dbh);
        } catch( Exception e ) {
            FFSDebug.log("CheckFreeRespFilehandlerImpl.processResponseFiles():"+FFSDebug.stackTrace(e));
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        } finally{
	        CheckFreeHandler.unlock();
	        //Reset Crash Recovery mode flag.
	        CheckFreeHandler.setPossibleDuplicate(false);
            FFSDebug.log("CheckFree Connector finished checking for response files");
            FFSDebug.console("CheckFree Connector finished checking for response files");
        }
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }

    private void loadProperties(){
        try {
            Properties props = BPWServer.getProperties();
            if (props != null) {
                dir = props.getProperty( DBConsts.CHECKFREE_IMPORT_DIR,
					 CheckFreeConsts.DEFAULT_IMPORT_DIR );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
