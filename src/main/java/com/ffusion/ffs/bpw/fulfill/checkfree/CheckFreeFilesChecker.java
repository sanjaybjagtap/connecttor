package com.ffusion.ffs.bpw.fulfill.checkfree;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.StringTokenizer;

import com.ffusion.ffs.bpw.audit.FMLogAgent;
import com.ffusion.ffs.bpw.handler.OSGIUtil;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.util.beans.filemonitor.FMLogRecord;
import com.sap.banking.common.interceptors.PerfLoggerUtil;
import com.sap.banking.io.beans.File;
import com.sap.banking.io.beans.FileInputStream;
import com.sap.banking.io.beans.FileOutputStream;
import com.sap.banking.io.beans.FileReader;
import com.sap.banking.io.beans.FileWriter;
import com.sap.banking.io.exception.FileNotFoundException;
import com.sap.banking.io.provider.interfaces.FileHandlerProvider;

/**
 * Schedule a task that executes once every second.
 */

public class CheckFreeFilesChecker implements FFSConst {
    private  static String  _fiID       = null;
    private  static String  _importDir  = CheckFreeConsts.DEFAULT_IMPORT_DIR;
    private  static String  _errorDir   = CheckFreeConsts.DEFAULT_ERROR_DIR;

    private FFSConnectionHolder dbh = null;
    
    public CheckFreeFilesChecker(FFSConnectionHolder dbh) throws Exception {
    	CheckFreeHandler.init();
    	ResponseFileProcessor.init();
    	getProperties();
    }

    /***/
    private void checkForFiles(FFSConnectionHolder dbh)
    {
        FileHandlerProvider fileHandlerProvider = (FileHandlerProvider)OSGIUtil.getBean(FileHandlerProvider.class);
    	
        CheckFreeUtil.log("CheckFree Connector start checking for response files");
        FFSDebug.console("CheckFree Connector start checking for response files");

        File errorDir = new File( _errorDir );
        errorDir.setFileHandlerProvider(fileHandlerProvider);
        File respDir = new File( _importDir );
        respDir.setFileHandlerProvider(fileHandlerProvider);

        boolean success = true;
    
        log("checkForFiles():Checking for files in dir: " + respDir.getName());

        // get file dirs
        File srvcTransDir = new File( respDir + File.separator
                + CheckFreeConsts.DIR_SRVCTRANS );
        srvcTransDir.setFileHandlerProvider(fileHandlerProvider);

        File pmtHistDir = new File( respDir + File.separator
                       + CheckFreeConsts.DIR_PMTHIST );
        pmtHistDir.setFileHandlerProvider(fileHandlerProvider);

        File settlementDir = new File( respDir + File.separator
                       + CheckFreeConsts.DIR_SETTLEMENT );
        settlementDir.setFileHandlerProvider(fileHandlerProvider);

        String srvcTransErrDir = new String( errorDir + File.separator
                       + CheckFreeConsts.DIR_SRVCTRANS );
        String pmtHistErrDir = new String( errorDir + File.separator
                       + CheckFreeConsts.DIR_PMTHIST );
        String settlementErrDir = new String( errorDir + File.separator
                       + CheckFreeConsts.DIR_SETTLEMENT);

        try {

            // get files
            File[] srvcTransFiles = srvcTransDir.listFiles();
            File[] pmtHistFiles = pmtHistDir.listFiles();
            File[] settlementFiles = settlementDir.listFiles();
            File[] splitFiles = null;

            // 	Process Srvc Trans files
            if (srvcTransFiles==null) 
                log("checkForFiles(): No Service Transaction file");
            else {
                boolean hasSrvcTransFiles = false;
                for( int i = 0; i < srvcTransFiles.length; i++ ) {
                    if( srvcTransFiles[ i ].isDirectory() ) continue;
                    hasSrvcTransFiles = true;
                    success = true;
                    File f = srvcTransFiles[ i ];
                    
                    FFSDebug.log("checkForFiles(): Started processing Service and Transaction File: : " + f.getName() );

                    // Log to File Monitor Log
                    // We pass in null value for db connection,
                    // then a new db connection will be used
                    // for this log and be committed right away
                    FMLogAgent.writeToFMLog(null,
                                            DBConsts.BPW_CHECKFREE_FILETYPE_SISSRVTRAN,
                                            srvcTransDir.getPath() + File.separator + f.getName(),
                                            DBConsts.CHECKFREE,
                                            DBConsts.BPTW,
                                            FMLogRecord.STATUS_IN_PROCESS);

                    try{
                        // filter this file and append \r\n to the end of eachline
                        //filterFile(f);


                        // split the large response file into several smaller
                        // files -- do not split a 4010 into a different file than
                        // its corresponding 4000 record
                        String[] noFlush = { CheckFreeConsts.CHKF_SRVCTRANS_INVINFO };
                        splitFiles = CheckFreeUtil.splitFile( f,
                                        CheckFreeConsts.SRVCTRANS_HEADER_LINES,
                                        CheckFreeConsts.SRVCTRANS_TRAILER_LINES,
                                        noFlush );

                        // process each smaller file
                        for( int j = 0; j < splitFiles.length; j++ ) {
                            FFSDebug.log("checkForFiles(): Started processing Split File:"+splitFiles[ j ].getName()+
                                    ", for Service and Transaction File:" + f.getName() );
                            
                            success &= CheckFreeHandler.processSrvcTransRSFile(
                                                      splitFiles[ j ], dbh );
                            FFSDebug.log("checkForFiles(): Finished processing Split File:"+splitFiles[ j ].getName()+
                                    ", for Service and Transaction File:" + f.getName() );
                        }

                        if( success ) {
                            // Log to File Monitor Log
                            FMLogAgent.writeToFMLog(dbh,
                                                    DBConsts.BPW_CHECKFREE_FILETYPE_SISSRVTRAN,
                                                    srvcTransDir.getPath() + File.separator + f.getName(),
                                                    DBConsts.CHECKFREE,
                                                    DBConsts.BPTW,
                                                    FMLogRecord.STATUS_COMPLETE);
                        }

                        // commit the change
                        //dbh.conn.commit();
                        
                        FFSDebug.log("checkForFiles(): Finished processing Service and Transaction File: : " + f.getName() );
                        
                        if( success ) {
                            // delete file
                            log("checkForFiles():File to be deleted: " + f.getAbsolutePath());
                            if ( f.delete() ) {
                                log("checkForFiles():Deleted file: " + f.getAbsolutePath());
                            } else {
                                warn("checkForFiles():Failed to delete file: " + f.getAbsolutePath());
                            }
                        } else {
                            // Log to File Monitor Log
                            // We pass in null value for db connection,
                            // then a new db connection will be used
                            // for this log and be committed right away
                            FMLogAgent.writeToFMLog(null,
                                                    DBConsts.BPW_CHECKFREE_FILETYPE_SISSRVTRAN,
                                                    srvcTransDir.getPath() + File.separator + f.getName(),
                                                    DBConsts.CHECKFREE,
                                                    DBConsts.BPTW,
                                                    FMLogRecord.STATUS_FAILED);

                            moveProblemFile( f, srvcTransErrDir );

                            // Log to File Monitor Log
                            // We pass in null value for db connection,
                            // then a new db connection will be used
                            // for this log and be committed right away
                            FMLogAgent.writeToFMLog(null,
                                                    DBConsts.BPW_CHECKFREE_FILETYPE_SISSRVTRAN,
                                                    srvcTransErrDir + File.separator + f.getName(),
                                                    DBConsts.BPTW,
                                                    DBConsts.CHECKFREE_ERR,
                                                    FMLogRecord.STATUS_COMPLETE);
                        }
                    } catch (Throwable e ) {
                        FFSDebug.log( FFSDebug.stackTrace(e));
                        dbh.conn.rollback();

                        // Log to File Monitor Log
                        // We pass in null value for db connection,
                        // then a new db connection will be used
                        // for this log and be committed right away
                        FMLogAgent.writeToFMLog(null,
                                                DBConsts.BPW_CHECKFREE_FILETYPE_SISSRVTRAN,
                                                srvcTransDir.getPath() + File.separator + f.getName(),
                                                DBConsts.CHECKFREE,
                                                DBConsts.BPTW,
                                                FMLogRecord.STATUS_FAILED);

                        // If a file fails, move to error path to let the FI
                        // manually inspect
                        moveProblemFile( f, srvcTransErrDir );

                        // Log to File Monitor Log
                        // We pass in null value for db connection,
                        // then a new db connection will be used
                        // for this log and be committed right away
                        FMLogAgent.writeToFMLog(null,
                                                DBConsts.BPW_CHECKFREE_FILETYPE_SISSRVTRAN,
                                                srvcTransErrDir + File.separator + f.getName(),
                                                DBConsts.BPTW,
                                                DBConsts.CHECKFREE_ERR,
                                                FMLogRecord.STATUS_COMPLETE);

                        throw new FFSException(e, "checkForFiles():Process response files failed");
                    } finally {
                        if (splitFiles != null){
                            // clean up temp files
                            for( int j = 0; j < splitFiles.length; j++ ) {
                                try {
                                    if( !splitFiles[ j ].delete() ) {
                                        warn( "checkForFiles():Could not remove temporary file "
                                                + splitFiles[ j ].getAbsolutePath() );
                                    }
                                } catch( Exception e ) {
                                    // ignore
                                }
                            }
                        }
                    }
                }
                if (!hasSrvcTransFiles) {
                   log("checkForFiles(): No Service Transaction file");
                }
            }

        // Process PmtHist files
            if (pmtHistFiles==null) 
                log("checkForFiles(): No Payment History file");
            else {
                boolean hasPmtHistFiles = false;
                for( int i = 0; i < pmtHistFiles.length; i++ ) {
                    if( pmtHistFiles[ i ].isDirectory() ) continue;
                    hasPmtHistFiles =  true;

                    success = true;
                    File f = pmtHistFiles[ i ];
                    FFSDebug.log("checkForFiles(): Started processing Payment History File: : " + f.getName() );

                    // Log to File Monitor Log
                    // We pass in null value for db connection,
                    // then a new db connection will be used
                    // for this log and be committed right away
                    FMLogAgent.writeToFMLog(null,
                                            DBConsts.BPW_CHECKFREE_FILETYPE_SISPMTHIST,
                                            pmtHistDir.getPath() + File.separator + f.getName(),
                                            DBConsts.CHECKFREE,
                                            DBConsts.BPTW,
                                            FMLogRecord.STATUS_IN_PROCESS);

                    try{
                        // filter this file and append \r\n to the end of eachline
                        //filterFile(f);

                        // split the large response file into several smaller
                        // files
                        splitFiles = CheckFreeUtil.splitFile( f,
                                        CheckFreeConsts.PMTHIST_HEADER_LINES,
                                        CheckFreeConsts.PMTHIST_TRAILER_LINES,
                                        null );

                        // process each smaller file
                        for( int j = 0; j < splitFiles.length; j++ ) {
                            
                            FFSDebug.log("checkForFiles(): Started processing Split File:"+splitFiles[ j ].getName()+
                                    ", for Payment History File:" + f.getName() );
                            
                            success &= CheckFreeHandler.processPmtHistFile(
                                                      splitFiles[ j ], dbh );
                            FFSDebug.log("checkForFiles(): Finished processing Split File:"+splitFiles[ j ].getName()+
                                    ", for Payment History File:" + f.getName() );
                        }

                        // commit the change
                        dbh.conn.commit();

                        if( success ) {
                            // Log to File Monitor Log
                            FMLogAgent.writeToFMLog(dbh,
                                                    DBConsts.BPW_CHECKFREE_FILETYPE_SISPMTHIST,
                                                    pmtHistDir.getPath() + File.separator + f.getName(),
                                                    DBConsts.CHECKFREE,
                                                    DBConsts.BPTW,
                                                    FMLogRecord.STATUS_COMPLETE);

                            // delete file
                            log("checkForFiles():File to be deleted: " + f.getAbsolutePath());
                            if ( f.delete() ) {
                                log("checkForFiles():Deleted file: " + f.getAbsolutePath());
                            } else {
                                warn("checkForFiles():Failed to delete file: " + f.getAbsolutePath());
                            }
                        } else {
                            // Log to File Monitor Log
                            // We pass in null value for db connection,
                            // then a new db connection will be used
                            // for this log and be committed right away
                            FMLogAgent.writeToFMLog(null,
                                                    DBConsts.BPW_CHECKFREE_FILETYPE_SISPMTHIST,
                                                    pmtHistDir.getPath() + File.separator + f.getName(),
                                                    DBConsts.CHECKFREE,
                                                    DBConsts.BPTW,
                                                    FMLogRecord.STATUS_FAILED);

                            moveProblemFile( f, pmtHistErrDir );

                            // Log to File Monitor Log
                            // We pass in null value for db connection,
                            // then a new db connection will be used
                            // for this log and be committed right away
                            FMLogAgent.writeToFMLog(null,
                                                    DBConsts.BPW_CHECKFREE_FILETYPE_SISPMTHIST,
                                                    pmtHistErrDir + File.separator + f.getName(),
                                                    DBConsts.BPTW,
                                                    DBConsts.CHECKFREE_ERR,
                                                    FMLogRecord.STATUS_COMPLETE);
                        }
                    } catch (Throwable e ) {
                        FFSDebug.log( FFSDebug.stackTrace(e));
                        dbh.conn.rollback();

                        // Log to File Monitor Log
                        // We pass in null value for db connection,
                        // then a new db connection will be used
                        // for this log and be committed right away
                        FMLogAgent.writeToFMLog(null,
                                                DBConsts.BPW_CHECKFREE_FILETYPE_SISPMTHIST,
                                                pmtHistDir.getPath() + File.separator + f.getName(),
                                                DBConsts.CHECKFREE,
                                                DBConsts.BPTW,
                                                FMLogRecord.STATUS_FAILED);

                        // If a file fails, move to error path to let the FI
                        // manually inspect
                        moveProblemFile( f, pmtHistErrDir );

                        // Log to File Monitor Log
                        // We pass in null value for db connection,
                        // then a new db connection will be used
                        // for this log and be committed right away
                        FMLogAgent.writeToFMLog(null,
                                                DBConsts.BPW_CHECKFREE_FILETYPE_SISPMTHIST,
                                                pmtHistErrDir + File.separator + f.getName(),
                                                DBConsts.BPTW,
                                                DBConsts.CHECKFREE_ERR,
                                                FMLogRecord.STATUS_COMPLETE);

                        throw new FFSException(e, "checkForFiles: Faile to process Payment History file");
                    } finally {
                        if (splitFiles != null){
                            // clean up temp files
                            for( int j = 0; j < splitFiles.length; j++ ) {
                                try {
                                    if( !splitFiles[ j ].delete() ) {
                                        warn( "checkForFiles():Could not remove temporary file "
                                                + splitFiles[ j ].getAbsolutePath() );
                                    }
                                } catch( Exception e ) {
                                    // ignore
                                } 
                            }
                        }
                    }
                }
                if (!hasPmtHistFiles) {
                    log("checkForFiles(): No Payment History file");
                }
            }

        // Process Settlement files
            if (settlementFiles==null) 
                log("checkForFiles(): No Settlement file");
            else {
                boolean hasSettlementFiles = false;
                for( int i = 0; i < settlementFiles.length; i++ ) {
                    if( settlementFiles[ i ].isDirectory() ) continue;
                    hasSettlementFiles = true;

                    success = true;
                    File f = settlementFiles[ i ];
                    log("checkForFiles(): Started processing Settlement File: : " + f.getName() );

                    // Log to File Monitor Log
                    // We pass in null value for db connection,
                    // then a new db connection will be used
                    // for this log and be committed right away
                    FMLogAgent.writeToFMLog(null,
                                            DBConsts.BPW_CHECKFREE_FILETYPE_SISSETLMNT,
                                            settlementDir.getPath() + File.separator + f.getName(),
                                            DBConsts.CHECKFREE,
                                            DBConsts.BPTW,
                                            FMLogRecord.STATUS_IN_PROCESS);

                    try{
                        // filter this file and append \r\n to the end of eachline
                        //filterFile(f);

                        // split the large response file into several smaller
                        // files -- do not split apart the trailer and do not split
                        // after a 8010, since 8010 is flushed as part of the trailer
                        // into each file
                        String[] noFlush = { CheckFreeConsts.CHKF_SETTLEMENT_ENDCSP,
                                             CheckFreeConsts.CHKF_SETTLEMENT_ENDFILE };
                        splitFiles = CheckFreeUtil.splitFile( f,
                                        CheckFreeConsts.SETTLEMENT_HEADER_LINES,
                                        CheckFreeConsts.SETTLEMENT_TRAILER_LINES,
                                        noFlush );

                        // process each smaller file
                        for( int j = 0; j < splitFiles.length; j++ ) {
                            
                            FFSDebug.log("checkForFiles(): Started processing Split File:"+splitFiles[ j ].getName()+
                                    ", for Settlement File:" + f.getName() );
                            
                            success &= CheckFreeHandler.processSettlementFile(
                                                      splitFiles[ j ], dbh );
                            
                            FFSDebug.log("checkForFiles(): Finished processing Split File:"+splitFiles[ j ].getName()+
                                    ", for Settlement File:" + f.getName() );
                            
                        }

                        // commit the change
                        dbh.conn.commit();

                        if( success ) {
                            // Log to File Monitor Log
                            FMLogAgent.writeToFMLog(dbh,
                                                    DBConsts.BPW_CHECKFREE_FILETYPE_SISSETLMNT,
                                                    settlementDir.getPath() + File.separator + f.getName(),
                                                    DBConsts.CHECKFREE,
                                                    DBConsts.BPTW,
                                                    FMLogRecord.STATUS_COMPLETE);

                            // delete file
                            log("checkForFiles():File to be deleted: " + f.getAbsolutePath());
                            if ( f.delete() ) {
                                log("checkForFiles():Deleted file: " + f.getAbsolutePath());
                            } else {
                                warn("checkForFiles():Failed to delete file: " + f.getAbsolutePath());
                            }
                        } else {
                            // Log to File Monitor Log
                            // We pass in null value for db connection,
                            // then a new db connection will be used
                            // for this log and be committed right away
                            FMLogAgent.writeToFMLog(null,
                                                    DBConsts.BPW_CHECKFREE_FILETYPE_SISSETLMNT,
                                                    settlementDir.getPath() + File.separator + f.getName(),
                                                    DBConsts.CHECKFREE,
                                                    DBConsts.BPTW,
                                                    FMLogRecord.STATUS_FAILED);

                            moveProblemFile( f, settlementErrDir );

                            // Log to File Monitor Log
                            // We pass in null value for db connection,
                            // then a new db connection will be used
                            // for this log and be committed right away
                            FMLogAgent.writeToFMLog(null,
                                                    DBConsts.BPW_CHECKFREE_FILETYPE_SISSETLMNT,
                                                    settlementErrDir + File.separator + f.getName(),
                                                    DBConsts.BPTW,
                                                    DBConsts.CHECKFREE_ERR,
                                                    FMLogRecord.STATUS_COMPLETE);
                        }
                    } catch (Throwable e ) {
                        FFSDebug.log( FFSDebug.stackTrace(e));
                        dbh.conn.rollback();

                        // Log to File Monitor Log
                        // We pass in null value for db connection,
                        // then a new db connection will be used
                        // for this log and be committed right away
                        FMLogAgent.writeToFMLog(null,
                                                DBConsts.BPW_CHECKFREE_FILETYPE_SISSETLMNT,
                                                settlementDir.getPath() + File.separator + f.getName(),
                                                DBConsts.CHECKFREE,
                                                DBConsts.BPTW,
                                                FMLogRecord.STATUS_FAILED);

                        // If a file fails, move to error path to let the FI
                        // manually inspect
                        moveProblemFile( f, settlementErrDir );

                        // Log to File Monitor Log
                        // We pass in null value for db connection,
                        // then a new db connection will be used
                        // for this log and be committed right away
                        FMLogAgent.writeToFMLog(null,
                                                DBConsts.BPW_CHECKFREE_FILETYPE_SISSETLMNT,
                                                settlementErrDir + File.separator + f.getName(),
                                                DBConsts.BPTW,
                                                DBConsts.CHECKFREE_ERR,
                                                FMLogRecord.STATUS_COMPLETE);

                        throw new FFSException(e, "CheckFreeFilesChecker.checkForFiles: process Settlement file failed");
                    } finally {

                        if (splitFiles != null){
                            // clean up temp files
                            for( int j = 0; j < splitFiles.length; j++ ) {
                                try {
                                    if( !splitFiles[ j ].delete() ) {
                                        warn( "checkForFiles():Could not remove temporary file "
                                                + splitFiles[ j ].getAbsolutePath() );
                                    }
                                } catch( Exception e ) {
                                    // ignore
                                }
                            }
                        }
                    }
                }
                if (!hasSettlementFiles) {
                    log("checkForFiles(): No Settlement file");
                }
            }
        
            dbh.conn.commit();
    } catch( Exception e ) {
            warn("*** checkForFiles(): exception="+e.toString());
            e.printStackTrace();
        if( dbh!=null && dbh.conn!=null) dbh.conn.rollback();
        //log( e.getMessage() );
    } finally{
        FFSDebug.console("CheckFree Connector finished checking for response files");
    }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Moving the pFile to the error path so FI can examine manually to find out
    // the problem.
    ///////////////////////////////////////////////////////////////////////////
    private final void moveProblemFile( File pFile, String dir )
    {
    FileHandlerProvider fileHandlerProvider = (FileHandlerProvider)OSGIUtil.getBean(FileHandlerProvider.class);
    String fName = pFile.getName();
    File targetFile = new File( dir + File.separator + fName );
    targetFile.setFileHandlerProvider(fileHandlerProvider);
    log( "Moving a problemic file from import path to error path. File name="+fName);
    try{
        if( targetFile.exists() ) {
        log( "File of the same name found in error path, deleting this file...");
        targetFile.delete();
        }
        log( "Moving the file to error path, new path="+targetFile.getAbsolutePath());
        copyFile( pFile, targetFile );
        pFile.delete();
    } catch( Exception e ) {
        warn( "File name="+fName+" "+ FFSDebug.stackTrace( e ));
    }
    }

    private final void copyFile( File src, File dest ) throws IOException
    {
    FileInputStream in = null;
    FileOutputStream out = null;
    byte[] buff = new byte[16*1024];
    int vol=0;
    try {
    	in = new FileInputStream( src );
        out = new FileOutputStream( dest );
        out.flush();
        vol = in.read( buff );
        do{
        out.write( buff, 0, vol );
        vol=in.read( buff );
        } while( vol>0 );
        out.flush();
    } catch ( IOException e ) {
        throw e;
    } finally {
    	if(null != in) {
	        try{
	        in.close();
	        } catch ( Exception e ) {
	        }
    	}
    	if(null != out) {
	        try {
	        out.close();
	        } catch ( Exception e ) {
	        }
    	}
    }
    }

    private void getProperties(){
    _importDir  = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_IMPORT_DIR,
            CheckFreeConsts.DEFAULT_IMPORT_DIR );
    _errorDir   = CheckFreeUtil.getProperty( DBConsts.CHECKFREE_ERROR_DIR,
            CheckFreeConsts.DEFAULT_ERROR_DIR );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Outputs a message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    static final void log( String str )
    {
        FFSDebug.log( "CheckFreeFilesChecker: " + str, FFSConst.PRINT_DEV );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Outputs an exception and a message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    static final void log( Throwable t, String str )
    {
        FFSDebug.log( t, "CheckFreeFilesChecker: " + str, FFSConst.PRINT_DEV );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Outputs a warning message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    static final void warn( String msg )
    {
        FFSDebug.log( "WARNING! CheckFreeFilesChecker: " + msg, FFSConst.PRINT_ERR );
    }
   
    ///////////////////////////////////////////////////////////////////////////
    // Change all \r or \n to \r\n in write the file back overriding the original file
    public static void filterFile(File file) throws Exception {
    	String method = "CheckFreeFilesChecker.filterFile";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
        String str = fileToString(file);

        StringBuffer buff = new StringBuffer();
        for(StringTokenizer tokens = new StringTokenizer(str, "\r\n"); tokens.hasMoreTokens();) {

            String line = tokens.nextToken();
            if (line != null && !line.equals("\n") && !line.equals("\r") && !line.equals("\r\n")) {
                buff.append(line);
                System.err.println("line: [" + line + "]");
                buff.append("\r\n");
            }
        }
        stringToFile(buff.toString() , file);
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }
   
    ///////////////////////////////////////////////////////////////////////////
    // write string to specified file
    public static void stringToFile(String str, File file) throws Exception{
     
    	String method = "CheckFreeFilesChecker.stringToFile";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
      try{
            PrintWriter out = new PrintWriter(new FileWriter(file));
            out.write(str);
            out.close();
      }catch(Exception ex){
            ex.printStackTrace();
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            throw ex;
      }
      PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }

   
    ///////////////////////////////////////////////////////////////////////////
    // read string from specified file.
    public static String fileToString(File file) throws Exception{
    	
    	String method = "CheckFreeFilesChecker.fileToString";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
       String str = null;
       FileReader is  = null;
       if (file == null) {
          return str; 
       }
       int strLen = (int)file.length();
       
       if(strLen <= 0) {
          return str;
      }

       char[] buf = new char[strLen];

       try {
          is  = new FileReader(file);
          if (is == null) {
              return str;
          }

          for(int off = 0, c = 0; strLen > 0; strLen -= c, off += c) {
             c = is.read(buf, off, strLen);
             if(c == -1) {
                return str;
             }
          }
       } catch(FileNotFoundException fe) {
           fe.printStackTrace();
           PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
          throw fe;
       } catch( IOException ioe ) {
           ioe.printStackTrace();
           PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
           throw ioe;
        } finally{
            try {
                is.close();             
            }catch (Exception e){
                e.printStackTrace();
                PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
                throw e;
            }
        }
       PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return new String (buf);
    } // endOfLine of fileToString

}