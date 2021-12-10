package com.ffusion.ffs.bpw.fulfill.orcc;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import com.ffusion.ffs.bpw.handler.OSGIUtil;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSUtil;
import com.sap.banking.io.beans.File;
import com.sap.banking.io.provider.interfaces.FileHandlerProvider;

/**
 * Schedule a task that executes once every second.
 */

public class ORCCFilesChecker implements FFSConst {
  //  private static FFSTimer timer           = null;
    private  static String 	_fiID		= null;
    private  static String 	_importDir 	= ORCCConstants.DEFAULT_IMPORT_DIR;
    private  static String 	_errorDir 	= ORCCConstants.DEFAULT_ERROR_DIR;

    private long        _interval            = 24 * 3600*1000;  // in seconds
    private Date        _startTime           = new Date();
    private boolean     _isDaemon            = false;
    private Date        _removeTime          = new Date();
    
    private static int _routeID = 0;

    // get properConfig for EnforcePayment option
    private static PropertyConfig _propConfig = null;
    
    private static boolean     _runConnector;

    public ORCCFilesChecker() throws Exception {
	ORCCHandler.init();
	_routeID = ORCCHandler.getRouteID();
	_propConfig = ORCCHandler.getPropConfig();
	getProperties();
    }

	
    public ORCCFilesChecker( String theDir, FFSConnectionHolder dbh )
    throws Exception
    {
        ORCCHandler.unlock();
	ORCCHandler.init();
	_routeID = ORCCHandler.getRouteID();
	_propConfig = ORCCHandler.getPropConfig();
	getProperties();
	this._importDir = theDir;
	checkForFiles( dbh );
    }
    
    public ORCCFilesChecker(String theDir, Date firstTime, long interval)
    throws Exception
    {
	ORCCHandler.init();
	_routeID = ORCCHandler.getRouteID();
	_propConfig = ORCCHandler.getPropConfig();
	this._importDir		= theDir;
        this._startTime      	= firstTime;
        this._interval       	= interval;
    }


    public void startChecking()
    {
    }

    /***/
    private void checkForFiles( FFSConnectionHolder dbh )
    {
	ORCCUtil.log("ORCC Connector start checking for response files");
	FFSDebug.console("ORCC Connector start checking for response files");
	ArrayList problemFiles = new ArrayList();
	try {
		FileHandlerProvider fileHandlerProvider = (FileHandlerProvider)OSGIUtil.getBean(FileHandlerProvider.class);
	    File errorDir = new File( _errorDir );
	    errorDir.setFileHandlerProvider(fileHandlerProvider);
	    File respDir = new File( _importDir );
	    respDir.setFileHandlerProvider(fileHandlerProvider);
	    FFSDebug.log("Checking for files in dir: " + respDir.getName());
	    String respFiles [] = respDir.list();
	    FFSDebug.log("int try block Checking for files in dir: " + respFiles);
	    int numFiles = respFiles.length;

	    if( numFiles<=0 ) return;

	    ArrayList mlfFiles = new ArrayList( numFiles ); // list of ORCC MLF
	    ArrayList errFiles = new ArrayList( numFiles ); // list of ORCC ERR

	    // Group the files into two sets: Consumer Information files and
	    // other ones. Also cache references to all files.
	    for( int i = 0; i < numFiles; i++ ) {
		File resp = new File( respDir, respFiles[i] );
		resp.setFileHandlerProvider(fileHandlerProvider);
		ORCCUtil.log( "Checking file type: "+resp.getAbsolutePath());
		int type = ORCCUtil.getFileType( respFiles[i] );

		switch( type ) {
		    case ORCCUtil.ORCC_MLF:
			ORCCUtil.log( "MLF file found: "+resp.getAbsolutePath());
			mlfFiles.add( resp );
			break;
		    case ORCCUtil.ORCC_ERR:
			ORCCUtil.log( "ERR file found: "+resp.getAbsolutePath());
			errFiles.add( resp );
			break;
		    default:
			log( "File "+respFiles[i]
			    + " ignored since its name does not"
			    + " agree with ORCC response file"
			    + " naming convention." );
		}
	    }

	    // Go on processing only when we have both MLF and ERR files
	    if( mlfFiles.isEmpty() ) return;

	    // Process ERR files
	    numFiles = errFiles.size();
	    for( int i = 0; i < numFiles; i++ ) {
		File f = (File)errFiles.get( i );

		log("Calling:ORCCHandler to process file: " + f.getName());

		// For ERR file, add to problemFiles no matter the processing
		// is successful or not.
		problemFiles.add( f );
		ORCCHandler.processELFRSFile(f, dbh);
	    }
	    errFiles.clear();

	    // Process MLF files
	    numFiles = mlfFiles.size();
	    boolean totalSuccess = true;
	    for( int i = 0; i < numFiles; i++ ) {
		File f = (File)mlfFiles.get( i );

		log("Calling:ORCCHandler to process file: " + f.getName());
		boolean success = false;
		try{
		    success = ORCCHandler.processMLFRSFile(f, dbh);
		    totalSuccess = totalSuccess && success;
		} catch (Exception e ) {
		    log( e.toString() );
		    totalSuccess = false;

		    // If an MLF fails, add to problemFiles
		    // so it will be moved to error path to let the
		    // FI manually inspect.
		    problemFiles.add( f );
		    throw e;
		}

		try{
		    if( success ) {
			log("File to be deleted: " + f.getAbsolutePath());
			if ( f.delete() ) {
			    FFSDebug.log("Deleted file: " + f.getAbsolutePath());
			} else {
			    FFSDebug.log("Failed to delete file: " + f.getAbsolutePath());
			}
		    }
		} catch (Exception e ) {
		    log( e.toString() );
		    throw e;
		}
	    }
	    mlfFiles.clear();

	    //update all record with INPROGRESS status to PROCESSEDON
	    // Update status only if everything is going right.
	    if( totalSuccess ) {
		ORCCDBAPI.updateCustomer( DBConsts.INPROCESS, DBConsts.ACTIVE, dbh );
		ORCCDBAPI.deleteCustomer( dbh );
//		    ORCCDBAPI.updatePayee( DBConsts.INPROCESS, DBConsts.ACTIVE,  dbh );
//		    ORCCDBAPI.deletePayee( dbh );

		ORCCDBAPI.updateCustomerPayee( DBConsts.INPROCESS, dbh );
		ORCCDBAPI.updateCustomerPayee( DBConsts.CANC_INPROCESS, dbh );
		if( _propConfig.EnforcePayment ) {
		    ORCCDBAPI.updateCustomerWithRouteID( _routeID,
		                DBConsts.INPROCESS, DBConsts.ACTIVE, dbh );
		    ORCCDBAPI.deleteCustomerWithRouteID( _routeID, dbh );
		}
//		    ORCCDBAPI.deleteCustomerPayee( dbh );
	    } else {
		FFSDebug.console( "Warning: Response file proccessing has failed on 1 or more files. "
				    + "Please look at the log file for details." );
		ORCCUtil.log( "Warning: Response file proccessing has failed on 1 or more files. "
				    + "Please look at the log file for details." );
	    }

	} catch( Exception e ) {
	    if( dbh!=null && dbh.conn!=null) dbh.conn.rollback();
	    log( e.toString() );
	} finally{
	    if( !problemFiles.isEmpty() ) moveProblemFiles( problemFiles );
	    problemFiles.clear();
	    FFSDebug.console("ORCC Connector finished checking for response files");
	}
    }


    ///////////////////////////////////////////////////////////////////////////
    // Moving the a list of files to the error path so FI can examine manually
    // to find out the problem.
    ///////////////////////////////////////////////////////////////////////////
    private final void moveProblemFiles( ArrayList fileList )
    {
	int len = fileList.size();
	for( int i=0; i<len; ++i ) moveProblemFile( (File)fileList.get( i ) );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Moving the pFile to the error path so FI can examine manually to find out
    // the problem.
    ///////////////////////////////////////////////////////////////////////////
    private final void moveProblemFile( File pFile )
    {
	String fName = pFile.getName();
	File targetFile = new File(ORCCFilesChecker._errorDir
			+ File.separator
			+ fName);
	targetFile.setFileHandlerProvider(pFile.getFileHandlerProvider());
	log( "Moving a problemic file from import path to error path. File name="+fName);
	try{
	    if( targetFile.exists() ) {
		log( "File of the same name found in error path, deleting this file...");
		targetFile.delete();
	    }
	    log( "Moving the file to error path, new path="+targetFile.getAbsolutePath());
	    pFile.renameTo( targetFile );
	} catch( Exception e ) {
	    log( e.toString());
	}
    }


    public static void stopChecker(){
        if (_runConnector) {
            FFSDebug.log("Preparing to stop ORCC file checker.....");
            /* if (timer != null) {
                timer.cancel();
                timer = null;
            }*/
            FFSDebug.log("ORCC response file checker stoped successfully....");
            FFSDebug.console(" ==== ORCC File checker stopped....");
            FFSDebug.log(" ====  ORCC File checker stopped....");
        }
    }


    private void getProperties(){
	_importDir 	= ORCCUtil.getProperty( DBConsts.ORCC_IMPORT_DIR,
			ORCCConstants.DEFAULT_IMPORT_DIR );
	_errorDir 	= ORCCUtil.getProperty( DBConsts.ORCC_ERROR_DIR,
			ORCCConstants.DEFAULT_ERROR_DIR );

	String startTime    = ORCCUtil.getProperty( DBConsts.ORCC_FILE_CHECKER_START_TIME );
	if (startTime == null
		|| startTime.length()<=0
		|| startTime.equalsIgnoreCase( ORCCConstants.STR_NOW) ) {
	    _startTime = new Date();
	} else {
	    _startTime = getDate(startTime);
	}

	String period = ORCCUtil.getProperty( DBConsts.ORCC_FILE_CHECK_PERIOD,
			Integer.toString(ORCCConstants.DEFAULT_FILE_CHECK_PERIOD) );
	try {
	    _interval    = FFSUtil.intValue(period)*60*1000;
	    //System.out.println(new Date() + ": calling:interval: "  + interval);
	} catch (Exception e) {
	}

	String remove = ORCCUtil.getProperty( DBConsts.ORCC_FILE_CHECKER_REMOVE_TIME );
	if (remove == null) {
	    _removeTime = new Date();
	} else {
	    _removeTime = getDate(remove);
	}

	String conn = ORCCUtil.getProperty( DBConsts.ORCC_FILE_CHECKING,
			ORCCConstants.STR_TRUE );
	_runConnector = conn.equalsIgnoreCase( ORCCConstants.STR_TRUE );
    }


    private Date getDate(String wakeupTime){
        Date run =null;
        try {
            wakeupTime = wakeupTime.trim();
            Calendar    cal  = Calendar.getInstance();
            cal.setTime(new Date());

	    // if wakeupTime has length<8, append 0
	    int len = wakeupTime.length();
	    for( int i=len; i<8; ++i ) wakeupTime = wakeupTime+"0";

            int     hrIndex     = wakeupTime.indexOf(":") - 2;
            int     instHr      = Integer.parseInt(wakeupTime.substring(hrIndex, hrIndex+2));
            int     instMin     = Integer.parseInt(wakeupTime.substring(hrIndex+3, hrIndex+5));
            int     instSec     = Integer.parseInt(wakeupTime.substring(hrIndex+6, hrIndex+8));
            cal.set(Calendar.HOUR_OF_DAY, instHr);
            cal.set(Calendar.MINUTE, instMin);
            cal.set(Calendar.SECOND, instSec);
            run  = cal.getTime();
        } catch (Exception ex) {
	    ORCCUtil.log( ex.toString() );
        }
        return run;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Outputs a message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    static final void log( String str )
    {
        FFSDebug.log( "ORCC file checker: " + str );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Outputs an exception and a message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    static final void log( Throwable t, String str )
    {
        FFSDebug.log( t, "ORCC file checker: " + str );
    }


    ///////////////////////////////////////////////////////////////////////////
    // Outputs a warning message to the log file.
    ///////////////////////////////////////////////////////////////////////////
    static final void warn( String msg )
    {
        FFSDebug.log( "WARNING! ORCC file checker: " + msg );
    }
}
