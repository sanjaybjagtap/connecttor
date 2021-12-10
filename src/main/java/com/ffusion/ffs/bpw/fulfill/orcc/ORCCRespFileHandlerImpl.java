package com.ffusion.ffs.bpw.fulfill.orcc;

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

public class ORCCRespFileHandlerImpl implements FFSConst, BPWResource,BPWFulfillmentRespFileHandler
{
    private String         dir            = null;
    private PropertyConfig propertyConfig = null;

    public ORCCRespFileHandlerImpl() {
        propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);
        
        dir = propertyConfig.otherProperties.getProperty(DBConsts.ORCC_IMPORT_DIR);
        if (dir == null || dir.length() == 0) {
            String err = "Failed to start ORCC file checker. " +
                         "\nImport directory is invalid (null). Make sure import directory is " +
                         " the system path and try again";
            System.out.println(err);
            FFSDebug.log(err);
        }
    }

    /***/
    public void processResponseFiles(FFSConnectionHolder dbh,Map<String,Object> extra){

    	String method = "ORCCRespFileHandlerImpl.processResponseFiles";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        FFSDebug.log("ORCC Connector start checking for response files");
        FFSDebug.console("ORCC Connector start checking for response files");
        try {
            FFSDebug.log("Calling:ORCCHandler to check file: " + this.dir );
	    ORCCHandler.lock();
	    ORCCFilesChecker ofc = new ORCCFilesChecker( this.dir, dbh );
        } catch( Exception e ) {
            String trace = FFSDebug.stackTrace( e );
            FFSDebug.log( "*** ORCCRespfileHandlerImpl.processResponseFiles failed:" + trace );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        } finally{
	    ORCCHandler.unlock();
            FFSDebug.log("ORCC Connector finished checking for response files");
            FFSDebug.console("ORCC Connector finished checking for response files");
        }
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }

    private void loadProperties(){
        try {
            Properties props = BPWServer.getProperties();
            if (props != null) {
                dir = props.getProperty( DBConsts.ORCC_IMPORT_DIR );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
