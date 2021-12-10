
// Copyright (c) 2002 Financial Fusion, Inc.
// All rights reserved.

package com.ffusion.ffs.bpw.fulfill.rpps;

import java.util.Map;

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
 * Process Confirmation, Return and Biller Directory files.
 */
public class RPPSRespFileHandlerImpl implements FFSConst, BPWResource,BPWFulfillmentRespFileHandler {
    private String         dir            = null;
    private PropertyConfig propertyConfig = null;

    int _routeId = -1;
    double _paymentCost = 0;
    private static final String FIID= "FIID";
    /**
     * Get import directory.
     * Init RPPSHandler. 
     */
    public RPPSRespFileHandlerImpl() 
    {
        propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);

        dir = propertyConfig.otherProperties.getProperty(DBConsts.RPPS_IMPORT_DIR,
                                                         RPPSConsts.DEFAULT_IMPORT_DIR);
        if (dir == null || dir.length() == 0) {
            String err = "Failed to start RPPS file checker. " +
                         "\nImport directory is invalid (null). Make sure import directory is " +
                         " the system path and try again. " + dir;
            FFSDebug.log(err, FFSDebug.PRINT_ERR);
        }

        RPPSHandler.init(); // So, other handlers can get routeid and payment cost

        // RPPS Handler must be been initialized
        _routeId = RPPSHandler.getRouteID();
        _paymentCost = RPPSHandler.getPaymentCost();
    }

    /**
     * Process Biller file, Confirmation & Return file.
     * @param dbh
     * @param fiId
     */
    public void processResponseFiles(FFSConnectionHolder dbh, Map<String,Object> extra ){
    	
    	String method = "RPPSRespFileHandlerImpl.processResponseFiles";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);

        FFSDebug.log("RPPS Connector start checking for response files", FFSDebug.PRINT_DEV);
       
        String fiId = null;
        
        if(extra != null){
        	fiId = (String)extra.get(FIID);
        }
        
        try {

            if ( fiId == null || fiId.trim().length() == 0) {
                String err ="***RPPSRespFileHandlerImpl.processResponseFiles failed: fiid is null!";
                FFSDebug.log(err, PRINT_ERR);
                PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
                throw new Exception(err); 
            }

            FFSDebug.log("Calling:RPPSHandler to check file: " + this.dir, FFSDebug.PRINT_DEV );
            // this.lock();

            // Process Biller directory file first
            RPPSBillerFileHandlerImpl billerHandler = new  RPPSBillerFileHandlerImpl(fiId,
                                                                                     _routeId,
                                                                                     _paymentCost );

            billerHandler.processResponseFiles( dbh );


            // Process pmt confirmation & return file
            RPPSConfirmRtnFileHandlerImpl confirmRtnFileHandler = new RPPSConfirmRtnFileHandlerImpl( fiId );
            confirmRtnFileHandler.processResponseFiles( dbh );

        } catch ( Exception e ) {
            FFSDebug.log(e, "RPPS Connector failed to process response files." );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        } finally {
            // this.unlock();
            FFSDebug.log("RPPS Connector finished checking for response files", FFSDebug.PRINT_DEV);
        }
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }
    

}


