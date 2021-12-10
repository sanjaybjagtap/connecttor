package com.ffusion.ffs.bpw.fulfill.metavante;

import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;

import com.ffusion.ffs.bpw.BPWServer;
import com.ffusion.ffs.bpw.handler.OSGIUtil;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.interfaces.handlers.BPWFulfillmentRespFileHandler;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.sap.banking.common.interceptors.PerfLoggerUtil;
import com.sap.banking.io.beans.File;
import com.sap.banking.io.provider.interfaces.FileHandlerProvider;

/**
 * Schedule a task that executes once every second.
 */

public class MetavanteRespFileHandlerImpl implements FFSConst, BPWResource , BPWFulfillmentRespFileHandler{
    private String         dir            = null;
    private PropertyConfig propertyConfig = null;

    public MetavanteRespFileHandlerImpl() {
        propertyConfig = (PropertyConfig)FFSRegistry.lookup(PROPERTYCONFIG);

        dir = propertyConfig.otherProperties.getProperty(DBConsts.METAVANTE_IMPORT_DIR);
        if (dir == null || dir.length() == 0) {
            String err = "Failed to start Metavante file checker. " +
                         "\nImport directory is invalid (null). Make sure import directory is " +
                         " the system path and try again";
            System.out.println(err);
            FFSDebug.log(err);
        }
    }

    /***/
    public void processResponseFiles(FFSConnectionHolder dbh, Map<String,Object> extra){

    	String method = "MetavanteRespFileHandlerImpl.processResponseFiles";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        FFSDebug.log("Metavante Connector start checking for response files");
        FFSDebug.console("Metavante Connector start checking for response files");
        try {
        	FileHandlerProvider fileHandlerProvider = (FileHandlerProvider)OSGIUtil.getBean(FileHandlerProvider.class);
            ArrayList orderdRespFiles = new ArrayList(); // list of response files ordered
            File respDir = new File( this.dir );
            respDir.setFileHandlerProvider(fileHandlerProvider);
            FFSDebug.log("Checking for files in dir: " + respDir.getName());
            String respFiles [] = respDir.list();
            int filesNum = respFiles.length;

            FFSDebug.log("Response files total number: " + filesNum);
            int len = filesNum > 8 ? filesNum : 8;
            File orderedfilenames [] = new File [len];

            if ( filesNum != 0 ) {
                FFSDebug.log("MetavanteRespFileHandlerImpl.processResponseFiles: loading information to the cache. Please wait...." );
                MetavanteHandler.init();                   
                FFSDebug.log("MetavanteRespFileHandlerImpl.processResponseFiles: cache loaded successfully ....." );
                // Put file in specific order to be processed in this order.
                // Also cache references to all files.
                for ( int i = 0; i < filesNum; i++ ) {
                    File resp = new File( respDir, respFiles[i] );
                    resp.setFileHandlerProvider(fileHandlerProvider);
                    FFSDebug.log("Response file:respFiles[i] " + respFiles[i]);
                    FFSDebug.log("Response file:resp.getName(): " + resp.getName());
                    String filename = respFiles[i].toUpperCase();
                    if (filename.startsWith( MetavanteHandler.FT_CONS_XREF_AD)) {

                        orderedfilenames[0] = resp;
                        FFSDebug.log("Added respose file: " + respFiles[i] + 
                                     " to the response list.", PRINT_CFG);
                    }
                    else if (filename.startsWith( MetavanteHandler.FT_CONSUMER_AD)) {
                        orderedfilenames[1] = resp;
                        FFSDebug.log("Added respose file: " + respFiles[i] + 
                                     " to the response list.", PRINT_CFG);
                    }
                    else if (filename.startsWith( MetavanteHandler.FT_CONS_BANK_AD)) {
                        orderedfilenames[2] = resp;
                        FFSDebug.log("Added respose file: " + respFiles[i] + 
                                     " to the response list.", PRINT_CFG);
                    }
                    else if (filename.startsWith( MetavanteHandler.FT_CONSPRDACC_AD)) {
                        orderedfilenames[3] = resp;
                        FFSDebug.log("Added respose file: " + respFiles[i] + 
                                     " to the response list.", PRINT_CFG);
                    }
                    else if (filename.startsWith( MetavanteHandler.FT_PAYEE_AD) ) {
                        orderedfilenames[4] = resp;
                        FFSDebug.log("Added respose file: " + respFiles[i] + 
                                     " to the response list.", PRINT_CFG);
                    }
                    else if (filename.startsWith( MetavanteHandler.FT_PAYEE_EDIT_AD)) {
                        orderedfilenames[5] = resp;
                        FFSDebug.log("Added respose file: " + respFiles[i] + 
                                     " to the response list.", PRINT_CFG);
                    }
                    else if (filename.startsWith( MetavanteHandler.FT_CONS_PAYEE_AD)) {
                        orderedfilenames[6] = resp;
                        FFSDebug.log("Added respose file: " + respFiles[i] + 
                                     " to the response list.", PRINT_CFG);
                    }
                    else if (filename.startsWith( MetavanteHandler.FT_HISTORY_AD)) {
                        orderedfilenames[7] = resp;
                        FFSDebug.log("Added respose file: " + respFiles[i] + 
                                     " to the response list.", PRINT_CFG);

                    }
                    else if (filename.startsWith("IMPORTDATA")) {
                        orderedfilenames[0] = resp;
                        FFSDebug.log("Added respose file: " + respFiles[i] + 
                                     " to the response list.", PRINT_CFG);
                    }
                    else {
                        FFSDebug.log("+++Invalid Metavante file: " + respFiles[i] + 
                                     " This file will be ignored.", PRINT_ERR);
                        System.out.println("++++Invalid Metavante file: " + respFiles[i] + 
                                           " This file will be ignored.");
                    }
                }

                // First process any non-consumer information files.
                int orderdFilesCount = orderedfilenames.length;
                FFSDebug.log("orderd Files Count: " + orderdFilesCount);
                for ( int i = 0; i < orderdFilesCount; i++ ) {
                    File f = (File)orderedfilenames[i];
                    if (f == null) {
                        FFSDebug.log("Invalid response file orderd Files list: " + f); 
                        continue;
                    }
                    File toDelete = null;
                    FFSDebug.log("Calling:MetavanteHandler to process file: " + f.getName());
                    toDelete = MetavanteHandler.processResponseFile(f, dbh);
                    if (toDelete != null) {
                        FFSDebug.log("File to be deleted: " + toDelete.getName());
                        boolean  del = toDelete.delete();
                        if (del) {
                            FFSDebug.log("Deleted file: " + toDelete.getName());
                        }
                        else {
                            FFSDebug.log("Failed to delete file: " + toDelete.getName());
                        }
                    }
                }

            }
            else {
                FFSDebug.log("No response file available. Import directory is empty!");
            }
        }
        catch ( Exception e ) {
            e.printStackTrace();
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        }
        finally {
            FFSDebug.log("Metavante Connector finished checking for response files");
            FFSDebug.console("Metavante Connector finished checking for response files");
        }
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }

    private void loadProperties(){
        try {
            Properties props = BPWServer.getProperties();
            if (props != null) {
                dir = props.getProperty( DBConsts.METAVANTE_IMPORT_DIR );
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
