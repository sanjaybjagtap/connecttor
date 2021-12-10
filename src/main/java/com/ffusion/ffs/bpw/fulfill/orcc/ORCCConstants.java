// Copyright (c) 2001 Financial Fusion, Inc.  All Rights Reserved.

package com.ffusion.ffs.bpw.fulfill.orcc;


import org.osgi.annotation.versioning.ProviderType;


@ProviderType 
 public interface ORCCConstants
{
    // upper bound of TransID value
    public static final int	ORCC_TRANSID_BASE	= 10000000;
    public static final int	ORCC_TRANSID_LIMIT	= 90000000;

    public static final String FILE_SEP = System.getProperty( "file.separator" );
    public static final String DEFAULT_PROP_FILE 	= "orcc.properties";
    public static final String MB_MESSAGE_SET_NAME	= "ORCC_1999";
    public static final String MB_DPR_MESSAGE_NAME	= "DPRRQ";
    public static final String MB_ELF_RS_MESSAGE_NAME	= "ELFRS";
    public static final String MB_MLF_RQ_MESSAGE_NAME	= "MLFRQ";
    public static final String MB_MLF_RS_MESSAGE_NAME	= "MLFRS";
    public static final String MB_NULL_FIELD_VALUE	= "";

    // Default property values
    public static final String DEFAULT_IMPORT_DIR 	= "import";
    public static final String DEFAULT_EXPORT_DIR 	= "export";
    public static final String DEFAULT_ERROR_DIR 	= "error";
    public static final String DEFAULT_MB_HOST	   	= "localhost";
    public static final String DEFAULT_MB_PORT	   	= "3002";
    public static final String DEFAULT_MB_DB_TYPE 	= "ASA";
    public static final String DEFAULT_MB_DB_NAME 	= "orcc";
    public static final String DEFAULT_MB_DB_USER 	= "DBA";
    public static final String DEFAULT_MB_DB_PASSWORD 	= "SQL";
    public static final String DEFAULT_ORCC_CACHE_PATH	= "ORCCcache";

    public static final int DEFAULT_FILE_CHECK_PERIOD	= 60;

    // Other constants
    public static final String STR_NOW			= "NOW";
    public static final String MLF_EXTENSION_NAME	= ".MLF";
    public static final String ERR_EXTENSION_NAME	= ".ERR";
    public static final String DPR_EXTENSION_NAME	= ".DPR";
    public static final String ORCC_FILE_PREFIX		= "F";
    public static final String FI_FILE_PREFIX		= "T";
    public static final String STR_TRUE			= "true";
    public static final String STR_FALSE		= "false";
    public static final String STR_ORCC_DATE_FORMAT	= "MM/dd/yyyyHH:mm";
    public static final String STR_OFX_DATE_FORMAT	= "yyyyMMddHHmm";
    public static final String DEFAULT_ORCC_FRONT_ID	= "FF";

    // Error messages that may occur in ERR files
    public static final String ERR_ADDING_DUPE		= "ADDING DUPE";
    public static final String ERR_INVALID_MERCH_NUMBER = "THE MERCHANT NUMBER IS NOT VALID";
    public static final String ERR_INVALID_MERCH_ID	= "INVALID MERCHANT ID";
    public static final String ERR_TOO_OLD		= "THE DATE IS MORE THAN 5 DAYS OLD";
    public static final String ERR_UPDATE_NOT_FOUND	= "UPDATE NOT FOUND";
    public static final String ERR_DELETE_NOT_FOUND	= "DELETE NOT FOUND";
    public static final String ERR_CUST_NON_EXIST	= "THIS CUSTOMER DOES NOT EXIST IN THE DATABASE";
    public static final String ERR_INVALID_LINK_CODE	= "MISSING/INVALID LINK CODE";
    public static final String ERR_CRITICAL_ERROR	= "CRITICAL ERROR";
    // This is a substring, please use substring match instead of a string match
    public static final String ERR_INVALID_FIELD_VALUE	= "IS EMPTY OR THE WRONG TYPE";
}
