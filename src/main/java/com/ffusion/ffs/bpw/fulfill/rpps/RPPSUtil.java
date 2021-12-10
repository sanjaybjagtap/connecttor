// Copyright (c) 2002 Financial Fusion, Inc.
// All rights reserved.

package com.ffusion.ffs.bpw.fulfill.rpps;

import java.util.HashMap;

import com.ffusion.ffs.bpw.handler.OSGIUtil;
import com.ffusion.ffs.bpw.util.ACHAdapterUtil;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.sap.banking.io.provider.interfaces.FileHandlerProvider;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

public class RPPSUtil extends ACHAdapterUtil implements RPPSConsts {

    private static HashMap _respMsgMap = new HashMap();    
    private static HashMap _rejectMsgMap = new HashMap();    
    private static HashMap _returnMsgMap = new HashMap();        


    static
    {


        // Create hashmap for RPPSConsts.RESP_FILE_ERROR code and messages
        createRespMsgMap();        

        // create hashmap for all the error code page 4-24 to 4-27 RPPS Manual, table
        // 4.2, 4.3, 4.4, 4.5
        createFilePmtRejectMap();
        createBatchPmtRejectMap();
        createSingleEntryRejectMap();
        createReturnMap();

    }

    // log a message
    public static final void log( String str )
    {
        FFSDebug.log( "RPPS Adapter: " + str, FFSConst.PRINT_DEV );
    }

    public static void log( Throwable t, String str )
    {
        FFSDebug.log( t, "RPPS Adapter: " + str, FFSConst.PRINT_DEV );
    }

    public static final void log( String str, int level )
    {
        FFSDebug.log( "RPPS Adapter: " + str, level );
    }

    // log an error message
    public static final void warn( String err )
    {
        FFSDebug.log( "WARNING! RPPS Adapter: " + err, FFSConst.PRINT_ERR );
    }



    /**
     * Get trace num from Trace_Num field. The format of
     * this field < FI RPPS ID> + < 7 digits >
     * 
     * @return 
     */
    public static int parseTraceNum( String traceNumStr )
    {
    	String method = "RPPSUtil.parseTraceNum";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        int traceNum = 0;
        if ( ( traceNumStr != null ) 
             && ( traceNumStr.length() == RPPSConsts.TRACE_NUM_LENGTH ) ) {
            try {
                traceNum = Integer.parseInt( traceNumStr.substring( 8, RPPSConsts.TRACE_NUM_LENGTH ) );
            } catch ( Exception e ) {

                FFSDebug.log( e, "Invalid trace numer!", FFSDebug.PRINT_ERR );
                PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            }

        }
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return traceNum;
    }

    /**
     * Remove blanks, remove check digit
     * 
     * @param id
     * @return 
     */
    public static String parseRPPSId( String id )
    {
        // remove check digit and blank
        if ( id != null ) {
            id = id.trim();
            id = id.substring( 0, id.length()-1 );
        }
        return id;
    }
    /**
     * 
     * Do look up according to RPPS manual 4-24
     * Return reject message according code from reject msg
     * 
     * @param errorCode
     * @return 
     */
    public static String getRejectMsg( String errorCode )
    {
        // create hashmap for all the error code page 4-24 to 4-27, table
        // 4.2, 4.3, 4.4, 4.5

        String errorMsg = "Rejected by RPPS Network. Error code: " + errorCode 
                          + "; Error message: " + _rejectMsgMap.get( errorCode ) ;
        return errorMsg;
    }

    /**
     * Do look up according to RPPS manual 4-24
     * 
     * @param errorCode
     * @return 
     */
    public static String getReturnMsg( String errorCode )
    {
        // TODO
        // create hashmap for all the error code page 4-24 to 4-27, table
        // 4.2, 4.3, 4.4, 4.5

        String errorMsg = "Returned by BSP. Error code: " + errorCode 
                          + "; Error message: " + _returnMsgMap.get( errorCode );
        return errorMsg;
    }


    /**
     * Do look up according to RPPS manual 4-24
     * 
     * @param errorCode
     * @return 
     */
    public static String getInvalidFileErrorMsg( int code )
    {
        // TODO
        // create hashmap for all the error code page 4-24 to 4-27, table
        // 4.2, 4.3, 4.4, 4.5

        String errorMsg = "Returned by BSP. Error code: " + code 
                          + "; Error message: " +  _respMsgMap.get( new Integer( code ) );
        return errorMsg;
     }

    /**
 * Creates a hashmap with resp. file error codes, error msgs.
 */
    private static void createRespMsgMap() 
    {
        _respMsgMap.put(new Integer(RESP_FILE_ERROR_MISSING_FILE_HEADER),
                        RESP_FILE_ERROR_MISSING_FILE_HEADER_MSG);
        _respMsgMap.put(new Integer(RESP_FILE_ERROR_MISSING_BATCH_HEADER), 
                        RESP_FILE_ERROR_MISSING_BATCH_HEADER_MSG);
        _respMsgMap.put(new Integer(RESP_FILE_ERROR_MISSING_BATCH_CONTROL), 
                        RESP_FILE_ERROR_MISSING_BATCH_CONTROL_MSG);
        _respMsgMap.put(new Integer(RESP_FILE_ERROR_MISSING_FILE_CONTROL), 
                        RESP_FILE_ERROR_MISSING_FILE_CONTROL_MSG);
        _respMsgMap.put(new Integer(RESP_FILE_ERROR_INVALID_ENTRY_DESCRIPTION),
                        RESP_FILE_ERROR_INVALID_ENTRY_DESCRIPTION_MSG);
        _respMsgMap.put(new Integer(RESP_FILE_ERROR_MISSING_ADDENDA),
                        RESP_FILE_ERROR_MISSING_ADDENDA_MSG);
        _respMsgMap.put(new Integer(RESP_FILE_ERROR_CANNOT_FIND_ORIGINAL_PAYMENT),
                        RESP_FILE_ERROR_CANNOT_FIND_ORIGINAL_PAYMENT_MSG);                
        _respMsgMap.put(new Integer(RESP_FILE_ERROR_CANNOT_FIND_PAYMENT_FILE_MAP),
                        RESP_FILE_ERROR_CANNOT_FIND_PAYMENT_FILE_MAP_MSG);                
    }  

    /**
     * Creates a hashmap with file pmt. reject codes, msgs.
     * RPPS Manual 4-23 table 4.1
     */
    private static void createFilePmtRejectMap() 
    {
        _rejectMsgMap.put(TOTAL_FILE_REJECT_UNMATCHED_ENTRY_BATCH_COUNT,
                          TOTAL_FILE_REJECT_UNMATCHED_ENTRY_BATCH_COUNT_MSG);                          
        _rejectMsgMap.put(TOTAL_FILE_REJECT_CSP_ID_NOT_ACTIVATED,
                          TOTAL_FILE_REJECT_CSP_ID_NOT_ACTIVATED_MSG);                          
        _rejectMsgMap.put(TOTAL_FILE_REJECT_FIELD_FORMAT_ERROR,
                          TOTAL_FILE_REJECT_FIELD_FORMAT_ERROR_MSG);                          
        _rejectMsgMap.put(TOTAL_FILE_REJECT_UNMATCHED_TOTALS,
                          TOTAL_FILE_REJECT_UNMATCHED_TOTALS_MSG);                          
        _rejectMsgMap.put(TOTAL_FILE_REJECT_RECORD_SEQUENCE_ERROR,
                          TOTAL_FILE_REJECT_RECORD_SEQUENCE_ERROR_MSG);                          
        _rejectMsgMap.put(TOTAL_FILE_REJECT_DUPLICATE_FILE_CONTROL,
                          TOTAL_FILE_REJECT_DUPLICATE_FILE_CONTROL_MSG);                          
        _rejectMsgMap.put(TOTAL_FILE_REJECT_EXCEEDED_CREDIT_CAP,
                          TOTAL_FILE_REJECT_EXCEEDED_CREDIT_CAP_MSG);                          
        _rejectMsgMap.put(TOTAL_FILE_REJECT_PPD_FORMAT_ERROR,
                          TOTAL_FILE_REJECT_PPD_FORMAT_ERROR_MSG);                          
        _rejectMsgMap.put(TOTAL_FILE_NON_INITALIZED_FIELD_IN_HEADER,
                          TOTAL_FILE_NON_INITALIZED_FIELD_IN_HEADER_MSG);                          
        _rejectMsgMap.put(TOTAL_FILE_NON_INITALIZED_FIELD_IN_TRAILER,
                          TOTAL_FILE_NON_INITALIZED_FIELD_IN_TRAILER_MSG);                                                    
    }
    
    /**
     * Creates a hashmap with batch pmt. reject codes, msgs.
     * RPPS Manual 4-23 table 4.2
     */
    private static void createBatchPmtRejectMap() 
    {
        _rejectMsgMap.put(BATCH_REJECT_ALL_ENTRY_ADDENDA_REJECTED,
                          BATCH_REJECT_ALL_ENTRY_ADDENDA_REJECTED_MSG);                          
        _rejectMsgMap.put(BATCH_REJECT_UNMATCHED_BUSINESS_CODES,
                          BATCH_REJECT_UNMATCHED_BUSINESS_CODES_MSG);                          
        _rejectMsgMap.put(BATCH_REJECT_CURRENCY_CODE_ERROR,
                          BATCH_REJECT_CURRENCY_CODE_ERROR_MSG);                          
        _rejectMsgMap.put(BATCH_REJECT_UNMATCHED_TOTALS,
                          BATCH_REJECT_UNMATCHED_TOTALS_MSG);                          
        _rejectMsgMap.put(BATCH_REJECT_EXCEEDED_BSP_DEBIT_CAP,
                          BATCH_REJECT_EXCEEDED_BSP_DEBIT_CAP_MSG);                          
        _rejectMsgMap.put(BATCH_REJECT_EXCEEDED_BILLER_DEBIT_CAP,
                          BATCH_REJECT_EXCEEDED_BILLER_DEBIT_CAP_MSG);                          
        _rejectMsgMap.put(BATCH_REJECT_EXCEEDED_CSP_DEBT_CAP,
                          BATCH_REJECT_EXCEEDED_CSP_DEBT_CAP_MSG);                          
        _rejectMsgMap.put(BATCH_REJECT_INVALID_BILLER_ID,
                          BATCH_REJECT_INVALID_BILLER_ID_MSG);                          
        _rejectMsgMap.put(BATCH_REJECT_FIELD_FORMAT_ERROR_IN_HEADER,
                          BATCH_REJECT_FIELD_FORMAT_ERROR_IN_HEADER_MSG);                          
        _rejectMsgMap.put(BATCH_REJECT_FIELD_FORMAT_ERROR_IN_TRAILER,
                          BATCH_REJECT_FIELD_FORMAT_ERROR_IN_TRAILER_MSG);                          
        _rejectMsgMap.put(BATCH_REJECT_NON_INITALIZED_FIELD_IN_HEADER,
                          BATCH_REJECT_NON_INITALIZED_FIELD_IN_HEADER_MSG);                          
        _rejectMsgMap.put(BATCH_REJECT_NON_INITALIZED_FIELD_IN_TRAILER,
                          BATCH_REJECT_NON_INITALIZED_FIELD_IN_TRAILER_MSG);                                  
    }    

    /**
     * Creates a hashmap with single entry reject codes, msgs.
     * RPPS Manual 4-23 table 4.3
     */
    private static void createSingleEntryRejectMap() 


    {
        _rejectMsgMap.put(SE_REJECT_NON_NUMERIC_IN_ADDENDUM,
                          SE_REJECT_NON_NUMERIC_IN_ADDENDUM_MSG);                          

        _rejectMsgMap.put(SE_UNMATCHED_COUNTRY_CODES,
                          SE_UNMATCHED_COUNTRY_CODES_MSG);                          

        _rejectMsgMap.put(SE_REJECT_NON_NUMERIC_IN_ADDENDUM,
                          SE_REJECT_NON_NUMERIC_IN_ADDENDUM_MSG);                          

        _rejectMsgMap.put(SE_REJECT_IMPROPER_EFFECTIVE_ENTRY_DATE,
                          SE_REJECT_IMPROPER_EFFECTIVE_ENTRY_DATE_MSG);                          

        _rejectMsgMap.put(SE_REJECT_NET_AMT_FIELD_ERROR,
                          SE_REJECT_NET_AMT_FIELD_ERROR_MSG);                          

        _rejectMsgMap.put(SE_REJECT_INVALID_ACCOUNT_MASK,
                          SE_REJECT_INVALID_ACCOUNT_MASK_MSG);                          

        _rejectMsgMap.put(SE_REJECT_CHECK_DIGIT_ERROR,
                          SE_REJECT_CHECK_DIGIT_ERROR_MSG);                          

        _rejectMsgMap.put(SE_REJECT_ADDENDUM_FORMAT_ERROR,
                          SE_REJECT_ADDENDUM_FORMAT_ERROR_MSG);                          

        _rejectMsgMap.put(SE_REJECT_FIELD_FORMAT_ERROR,
                          SE_REJECT_FIELD_FORMAT_ERROR_MSG);                          

        _rejectMsgMap.put(SE_REJECT_IMPROPER_TRACE_NUMBER,
                          SE_REJECT_IMPROPER_TRACE_NUMBER_MSG);                          

        _rejectMsgMap.put(SE_REJECT_NO_PRENOTES,
                          SE_REJECT_NO_PRENOTES_MSG);                          

        _rejectMsgMap.put(SE_REJECT_FAIR_SHARE_ERROR,
                          SE_REJECT_FAIR_SHARE_ERROR_MSG);                          

        _rejectMsgMap.put(SE_REJECT_NO_PMT,
                          SE_REJECT_NO_PMT_MSG);                          

        _rejectMsgMap.put(SE_REJECT_FIELD_FORMAT_ERROR,
                          SE_REJECT_FIELD_FORMAT_ERROR_MSG);                          

        _rejectMsgMap.put(SE_REJECT_NON_INITALIZED_FIELD_IN_ENTRY_DETAIL,
                          SE_REJECT_NON_INITALIZED_FIELD_IN_ENTRY_DETAIL_MSG);                                                    

        _rejectMsgMap.put(SE_REJECT_GROSS_AMT_ERROR,
                          SE_REJECT_GROSS_AMT_ERROR_MSG);                          

        _rejectMsgMap.put(SE_REJECT_NON_INITALIZED_FIELD_IN_POSITIONS,
                          SE_REJECT_NON_INITALIZED_FIELD_IN_POSITIONS_MSG);                                                                              
    }    

    /**
     * Creates a hashmap with return/reversal codes, msgs.
     * RPPS Manual 4-23 table 4.4
     */
    private static void createReturnMap() 
    {
        _returnMsgMap.put(RETURN_ACCOUNT_CLOSED,
                          RETURN_ACCOUNT_CLOSED_MSG);                                                                              

        _returnMsgMap.put(RETURN_NO_ACCOUNT,
                          RETURN_NO_ACCOUNT_MSG);                                                                              

        _returnMsgMap.put(RETURN_INVALID_ACCOUNT_NUM1,
                          RETURN_INVALID_ACCOUNT_NUM1_MSG);                                                                              

        _returnMsgMap.put(RETURN_NO_PRENOTIFICATION,
                          RETURN_NO_PRENOTIFICATION_MSG);                                                                              

        _returnMsgMap.put(RETURN_ORIGINATOR_REQUEST,
                          RETURN_ORIGINATOR_REQUEST_MSG);                                                                              

        _returnMsgMap.put(RETURN_ACCOUNT_CLOSED,
                          RETURN_ACCOUNT_CLOSED_MSG);                                                                              

        _returnMsgMap.put(RETURN_AUTHORIZATION_REVOKED,
                          RETURN_AUTHORIZATION_REVOKED_MSG);                                                                              

        _returnMsgMap.put(RETURN_UNCOLLECTED_FUNDS,
                          RETURN_UNCOLLECTED_FUNDS_MSG);                                                                              

        _returnMsgMap.put(RETURN_NOT_AUTHORIZED,
                          RETURN_NOT_AUTHORIZED_MSG);                                                                              

        _returnMsgMap.put(RETURN_CUSTOMER_DECEASED,
                          RETURN_CUSTOMER_DECEASED_MSG); 

        _returnMsgMap.put(RETURN_ACCOUNT_CLOSED,
                          RETURN_ACCOUNT_CLOSED_MSG);    

        _returnMsgMap.put(RETURN_INDIVIDUAL_DECEASED,
                          RETURN_INDIVIDUAL_DECEASED_MSG);                                                                              

        _returnMsgMap.put(RETURN_ACCOUNT_FROZEN,
                          RETURN_ACCOUNT_FROZEN_MSG);                                                                              

        _returnMsgMap.put(RETURN_IMPROPER_EFFECTIVE_ENTRY_DATE,
                          RETURN_IMPROPER_EFFECTIVE_ENTRY_DATE_MSG);                                                                              

        _returnMsgMap.put(RETURN_AMOUNT_FIELD_ERROR,
                          RETURN_AMOUNT_FIELD_ERROR_MSG);                                                                              

        _returnMsgMap.put(RETURN_NON_TRN_ACCOUNT,
                          RETURN_NON_TRN_ACCOUNT_MSG);                                                                              

        _returnMsgMap.put(RETURN_INVALID_BILLER_ID,
                          RETURN_INVALID_BILLER_ID_MSG);                                                                              

        _returnMsgMap.put(RETURN_INVALID_ACCOUNT_NUM2,
                          RETURN_INVALID_ACCOUNT_NUM2_MSG);                                                                              

        _returnMsgMap.put(RETURN_PMT_REFUSED,
                          RETURN_PMT_REFUSED_MSG);                                                                              

        _returnMsgMap.put(RETURN_DUPLICATE_ENTRY,
                          RETURN_DUPLICATE_ENTRY_MSG);                                                                              

        _returnMsgMap.put(RETURN_FAIR_SHARE_ERROR,
                          RETURN_FAIR_SHARE_ERROR_MSG);                                                                              

        _returnMsgMap.put(RETURN_IMPROPER_REVERSAL,
                          RETURN_IMPROPER_REVERSAL_MSG);                                                                                      
        _returnMsgMap.put(MNFT_INCORRECT_ACCOUNT_NUM,
                          MNFT_INCORRECT_ACCOUNT_NUM_MSG);                                                                              

        _returnMsgMap.put(MNFT_INCORRECT_COMPANY_ID,
                          MNFT_INCORRECT_COMPANY_ID_MSG);                                                                                                                                  
    }        

    public static FileHandlerProvider getFileHandlerProvider() {
    	return (FileHandlerProvider)OSGIUtil.getBean(FileHandlerProvider.class);
    }
}
