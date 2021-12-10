// Copyright (c) 2002 Financial Fusion, Inc.
// All rights reserved.

package com.ffusion.ffs.bpw.fulfill.checkfree;

import org.osgi.annotation.versioning.ProviderType;

@ProviderType 
 public interface CheckFreeConsts
{

    public static final String MB_MESSAGE_SET_NAME		= "CheckFree";
    public static final String MB_SRVC_TRANS_MESSAGE_NAME 	= "SrvcTrans";
    public static final String MB_PMT_HIST_MESSAGE_NAME		= "PmtHistory";
    public static final String MB_SETTLEMENT_MESSAGE_NAME	= "Settlement";
    public static final String SRVC_TRANS_EXTENSION_NAME	= ".srvctrans";
    public static final String MB_NULL_FIELD_VALUE		= "";
    public static final String MB_NULL_SUBINFO_FILLER2		= "00000000";
    public static final int MB_NIL_FIELD_VALUE			= 0;
    public static final int MB_PMTMEMO_FIELD_LENGTH		= 40;
    public static final String BYPASS				= "BYPASS";
    public static final String TAX_NIL_FIELD_VALUE		= "000000000";
    public static final String STARTING_CHECK_NUM		= "000000";

    // response file directories
    public static final String DIR_SRVCTRANS			= "SrvcTrans";
    public static final String DIR_PMTHIST			= "PmtHist";
    public static final String DIR_SETTLEMENT			= "Settlement";
    
    // Default property values
    public static final String DEFAULT_SW_SPEC_VER		= "";
    public static final String DEFAULT_SW_SPEC_ID		= "                                ";
    public static final String DEFAULT_IMPORT_DIR		= "import";
    public static final String DEFAULT_EXPORT_DIR		= "export";
    public static final String DEFAULT_ERROR_DIR		= "error";
    public static final String DEFAULT_MB_HOST			= "localhost";
    public static final String DEFAULT_MB_PORT			= "2638";
    public static final String DEFAULT_MB_DB_TYPE		= "ASA";
    public static final String DEFAULT_MB_DB_NAME		= "MBDemo";
    public static final String DEFAULT_MB_DB_USER		= "DBA";
    public static final String DEFAULT_MB_DB_PASSWORD		= "SQL";
    public static final String DEFAULT_CACHE_PATH		= "cache";
    public static final String DEFAULT_FILE_VERSION		= "0401";
    public static final String DEFAULT_COUNTRY			= "USA";

    public static final int DEFAULT_FILE_CHECK_PERIOD		= 60;
    public static final int DEFAULT_MAX_RECORDS			= 5000;

    // record types
    public static final String CHKF_HEADER			= "0000";
    public static final String CHKF_SETTLEMENT_ENDCSP		= "8010";
    public static final String CHKF_SETTLEMENT_ENDFILE		= "8012";
    public static final String CHKF_SRVCTRANS_INVINFO		= "4010";
    public static final String CHKF_TRAILER			= "9999";

    // CHECKFREE_TRAILER_REGION is the maximum number of bytes each line in
    // the trailer of the message can occupy.  The split file mechanism will
    // look for the entire trailer in CHECKFREE_TRAILER_LINE_LEN * nLines
    public static final int    CHECKFREE_TRAILER_LINE_LEN	= 4096;

    // Other constants
    public static final String STR_NOW				= "NOW";
    public static final String STR_TRUE				= "true";
    public static final String STR_FALSE			= "false";
    public static final String FILE_SEP 			= System.getProperty( "file.separator" );
    public static final String FILE_PREFIX 			= "CHKF";
    public static final String STR_CHECKFREE_DATE_FORMAT	= "yyyyMMdd";
    public static final String STR_CHECKFREE_TIME_FORMAT	= "hhmmssSSSSSS";
    
    static final char[] MB_LINE_SEP_BYTES		= { 13, 10 };
    public static final String MB_LINE_SEP			= new String( MB_LINE_SEP_BYTES );

    public static final String	CF_STATUS_COMPLETED	= "COM";
    public static final String	MSG_STATUS_OK		= "Payment Processed";
    public static final String	MSG_STATUS_GENERAL_ERR	= "General Error";


    // File splitting constants
    public static final String  STR_TEMP_DIR_NAME	= "temp";
    public static final String  STR_TEMP_FILE_PREFIX	= "CHKF";
    public static final String  STR_TEMP_INFILE_SUFFIX	= ".in";
    public static final String  STR_TEMP_OUTFILE_SUFFIX	= ".out";
    public static final String  STR_EXPORT_FILE_SUFFIX  = ".SIS";

    // these constants describe how many lines each file's header/trailer
    // region will occupy.  this information is used by the file splitter
    // to decide which lines should be concatenated to the beginning and
    // end of each temporary file.
    public static final int	SETTLEMENT_HEADER_LINES = 2; // 0000,0010
    public static final int	SETTLEMENT_TRAILER_LINES= 3; // 8010,8012,9999
    public static final int	PMTHIST_HEADER_LINES	= 1; // 0000
    public static final int	PMTHIST_TRAILER_LINES	= 1; // 9999
    public static final int	SRVCTRANS_HEADER_LINES	= 1; // 0000
    public static final int	SRVCTRANS_TRAILER_LINES = 1; // 9999
    
    //Const added for Response File processing
    public static final int CUSTOMER_ROUTE_NOT_FOUND= 1000;
}
