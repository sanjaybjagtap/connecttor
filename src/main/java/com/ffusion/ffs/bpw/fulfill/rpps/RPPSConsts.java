// Copyright (c) 2002 Financial Fusion, Inc.
// All rights reserved.

package com.ffusion.ffs.bpw.fulfill.rpps;

import org.osgi.annotation.versioning.ProviderType;

@ProviderType 
 public interface RPPSConsts
{
    public static final String RPPS_FLAG = "MC";
    
    // default dir for import, export and error folders
    public static final String DEFAULT_IMPORT_DIR		= "import";
    public static final String DEFAULT_EXPORT_DIR		= "export";
    public static final String DEFAULT_ERROR_DIR		= "error";
    
    // Constants for the folder names for Biller Directory File, and Confirmation & Return files
    public static final String DIR_BILLER		        = "billers";
    public static final String DIR_CONFIRMATION		        = "confirmation";
    
    public static final int TX_CODE_CREDIT = 22;
    public static final int TX_CODE_DEBIT  = 27;

    // Token used to tokenize the Biller Fil
    public static final String BILLER_TOKEN		        = "biller";
    public static final String BILLERS_TOKEN		        = "billers";

    // Message type name for Biller
    public static final String MB_BILLER_TYPE_NAME		= "biller";

    // Message set name for Biller
    public static final String MB_BILLER_SET_NAME		= "RPPSBillerMsgSet";

    public static final String RPPS_BILLER_DTD		        = "RPPSBillerMsgSet.dtd";

    // RPPS Biller modification indicator
    public static final String ACD_INDICATION_ADD               = "A"; // add
    public static final String ACD_INDICATION_CHANGE            = "C"; // change
    public static final String ACD_INDICATION_DELETE            = "D"; // detele


    // Error messages when failing to process a pmt
    public static final String PAYEE_IS_NOT_RPPS_BILLER         = "Payee is not a RPPS biller.";
    public static final String BILLER_GUARPAYONLY               = "Pay amount is negative but biller is GuarPayOnly.";
    public static final String INVALID_PAYACCOUNT               = "Invalid pay account.";
    public static final String INVALID_CUSTOMER                 = "Can not find customer for this payement.";
    


    public static final int MIN_SUBID_LENGTH   = 9;
    public static final int RPPS_RECORD_TRACENUM_DIGITS = 7;
    public static final int RPPS_BATCH_NUM_DIGITS = 7;

    public static final String MB_RPPS_SET_NAME = "RPPSMsgSet";
    public static final String MB_RPPS_TYPE_FILE_HEADER = "FileHeaderRecord";
    public static final String MB_RPPS_TYPE_FILE_CONTROL = "FileControlRecord";
    public static final String MB_RPPS_TYPE_BATCH_HEADER = "BatchHeaderRecord";
    public static final String MB_RPPS_TYPE_BATCH_CONTROL = "BatchControlRecord";
    public static final String MB_RPPS_TYPE_ENTRY_DETAIL = "CSPPmtEntryDetailRecord";
    public static final String MB_RPPS_TYPE_CONFIRM_ENTRY_DETAIL = "CSPConfirmEntryDetailRecord";
    public static final String MB_RPPS_TYPE_RETURN_ENTRY_DETAIL_ADDENDUM = "CSPReturnEntryDetailAddendumRecord";


    public static final String  STR_EXPORT_FILE_SUFFIX      = ".RPPS";
    public static final String  STR_RPPS_FILE_SEPARATOR      = ".";

    public final static String DEFAULT_REFERENCE_CODE = "";
    public final static String DEFAULT_RESERVED20 = "                    ";
    public final static String DEFAULT_RESERVED3 = "   ";
    public final static String DEFAULT_RESERVED2 = "  ";
    public final static String DEFAULT_RESERVED26 = "                          ";
    public final static String DEFAULT_ORIGNIATOR_STATUS_CODE = "1"; //java.lang.String Originator_Status_Code,
    public final static String DEFAULT_ENTRY_HASH = "          ";
    public final static String DEFAULT_RESERVED25 = "                         ";
    public final static String DEFAULT_BLOCK_COUNT = "      ";
    public final static String ENTRY_DESCRIPTION_RPPSPAYMENT = "RPSPAYMENT";
    public final static String ENTRY_DESCRIPTION_REVERSAL = "REVERSAL";
    public final static String ORIGNIATOR_STATUS_CODE     =   "1";
    public final static short ADDENA_INDICATOR_NO_ADDENDA = 0;

    public final static int RPPS_BILLER_NAME_LENGTH = 16;
    public final static int RPPS_CONSUMER_NAME_LENGTH = 15;
    public final static int RPPS_NAME_LENGTH = 23;
    public final static int FI_RPPS_NAME_LENGTH = 23;
    public final static int TRACE_NUM_LENGTH = 15;
    public final static int BLOCK_COUNT_LENGTH = 6;    
    public final static int ENTRY_HASH_LENGTH = 10;  
    public final static int ENTRY_ADDENDA_COUNT_LENGTH = 8;    
    public final static int TOTAL_DEBIT_LENGTH = 12;        
    public final static int TOTAL_CREDIT_LENGTH = 12;  
    public final static int AMOUNT_LENGTH = 10;                  

    public final static String RESP_FILE_NO_DATA_INDICATOR = "NO DATA";
    public final static String RESP_FILE_ACCEPT_FILE = "ACCEPT FIL";
    public final static String RESP_FILE_REJECT_FILE = "REJECT FIL";
    public final static String RESP_FILE_BATCH_REJECT_BATCH = "REJECT BAT";
    public final static String RESP_FILE_BATCH_REJECT_ENTRY = "REJECT ENT";
    public final static String RESP_FILE_BATCH_RETURN = "RETURN";

    public final static int RESP_FILE_WHOLE_FILE_REJECTED = 1;
    public final static int RESP_FILE_WHOLE_FILE_ACCEPTED = 2;
    public final static int RESP_FILE_PARTIAL_FILE_ACCEPTED = 3;
    


    // Error code and error message for invalid response file
    public final static int RESP_FILE_SUCCESS = 0;

    public final static int RESP_FILE_ERROR_MISSING_FILE_HEADER = 100;
    public final static String RESP_FILE_ERROR_MISSING_FILE_HEADER_MSG = "No file header";

    public final static int RESP_FILE_ERROR_MISSING_BATCH_HEADER = 101;
    public final static String RESP_FILE_ERROR_MISSING_BATCH_HEADER_MSG = "No batch header";

    public final static int RESP_FILE_ERROR_MISSING_BATCH_CONTROL = 102;
    public final static String RESP_FILE_ERROR_MISSING_BATCH_CONTROL_MSG = "No batch control";

    public final static int RESP_FILE_ERROR_MISSING_FILE_CONTROL = 103;
    public final static String RESP_FILE_ERROR_MISSING_FILE_CONTROL_MSG = "No file control";

    public final static int RESP_FILE_ERROR_INVALID_ENTRY_DESCRIPTION = 104;
    public final static String RESP_FILE_ERROR_INVALID_ENTRY_DESCRIPTION_MSG = "Invalid entry description"; 

    public final static int RESP_FILE_ERROR_MISSING_ADDENDA = 105;
    public final static String RESP_FILE_ERROR_MISSING_ADDENDA_MSG = "No addendum"; 

    public final static int RESP_FILE_ERROR_CANNOT_FIND_ORIGINAL_PAYMENT = 106;
    public final static String RESP_FILE_ERROR_CANNOT_FIND_ORIGINAL_PAYMENT_MSG = "Can not find original payement";

    public final static int RESP_FILE_ERROR_CANNOT_FIND_PAYMENT_FILE_MAP = 107;
    public final static String RESP_FILE_ERROR_CANNOT_FIND_PAYMENT_FILE_MAP_MSG = "Can not find payement file from high-level summary batch";



    // RPPS Manual
    // Total file reject codes and messages
    public final static String TOTAL_FILE_REJECT_UNMATCHED_ENTRY_BATCH_COUNT = "F01";
    public final static String TOTAL_FILE_REJECT_UNMATCHED_ENTRY_BATCH_COUNT_MSG 
    = "Actual number of entries or batches does not agree with file control";

    public final static String TOTAL_FILE_REJECT_CSP_ID_NOT_ACTIVATED = "F02";
    public final static String TOTAL_FILE_REJECT_CSP_ID_NOT_ACTIVATED_MSG 
    = "Originator/CSP's id not activated in RPPS DB";
    
    public final static String TOTAL_FILE_REJECT_FIELD_FORMAT_ERROR = "F03";
    public final static String TOTAL_FILE_REJECT_FIELD_FORMAT_ERROR_MSG 
    = "Field format error in file header or file control";

    public final static String TOTAL_FILE_REJECT_UNMATCHED_TOTALS = "F04";
    public final static String TOTAL_FILE_REJECT_UNMATCHED_TOTALS_MSG 
    = "Entry/addenda or total debit or total credit does not agree with file control";    
    
    public final static String TOTAL_FILE_REJECT_RECORD_SEQUENCE_ERROR = "F05";
    public final static String TOTAL_FILE_REJECT_RECORD_SEQUENCE_ERROR_MSG 
    = "Record sequence error in file";

    public final static String TOTAL_FILE_REJECT_DUPLICATE_FILE_CONTROL = "F06";
    public final static String TOTAL_FILE_REJECT_DUPLICATE_FILE_CONTROL_MSG 
    = "Duplicate file control record";
    
    public final static String TOTAL_FILE_REJECT_EXCEEDED_CREDIT_CAP = "F07";
    public final static String TOTAL_FILE_REJECT_EXCEEDED_CREDIT_CAP_MSG 
    = "Exceeded credit cap";    
    
    public final static String TOTAL_FILE_REJECT_PPD_FORMAT_ERROR = "F08";
    public final static String TOTAL_FILE_REJECT_PPD_FORMAT_ERROR_MSG 
    = "PPD transit or routing format error";
    
    public final static String TOTAL_FILE_NON_INITALIZED_FIELD_IN_HEADER = "F32";
    public final static String TOTAL_FILE_NON_INITALIZED_FIELD_IN_HEADER_MSG 
    = "High/low non initialized value required in file header";
    
    public final static String TOTAL_FILE_NON_INITALIZED_FIELD_IN_TRAILER = "F42";
    public final static String TOTAL_FILE_NON_INITALIZED_FIELD_IN_TRAILER_MSG 
    = "High/low non initialized value required in file trailer";
    
    // Batch pmt. reject codes and messages
    public final static String BATCH_REJECT_ALL_ENTRY_ADDENDA_REJECTED = "B01";
    public final static String BATCH_REJECT_ALL_ENTRY_ADDENDA_REJECTED_MSG 
    = "Batch rejected due to all entries/addenda records rejecting";

    public final static String BATCH_REJECT_UNMATCHED_BUSINESS_CODES = "B02";
    public final static String BATCH_REJECT_UNMATCHED_BUSINESS_CODES_MSG 
    = "BSP business code not equal to CSP business code";
    
    public final static String BATCH_REJECT_CURRENCY_CODE_ERROR = "B03";
    public final static String BATCH_REJECT_CURRENCY_CODE_ERROR_MSG 
    = "Currency code error";

    public final static String BATCH_REJECT_UNMATCHED_TOTALS = "B04";
    public final static String BATCH_REJECT_UNMATCHED_TOTALS_MSG 
    = "Entry/addenda or total debit or total credit does not agree with batch control";
    
    public final static String BATCH_REJECT_EXCEEDED_BSP_DEBIT_CAP = "B07";
    public final static String BATCH_REJECT_EXCEEDED_BSP_DEBIT_CAP_MSG 
    = "Exceeded concentrator/BSP debit cap";

    public final static String BATCH_REJECT_EXCEEDED_BILLER_DEBIT_CAP = "B08";
    public final static String BATCH_REJECT_EXCEEDED_BILLER_DEBIT_CAP_MSG 
    = "Exceeded biller debit cap";
    
    public final static String BATCH_REJECT_EXCEEDED_CSP_DEBT_CAP = "B09";
    public final static String BATCH_REJECT_EXCEEDED_CSP_DEBT_CAP_MSG 
    = "Exceeded CSP debt cap per biller";

    public final static String BATCH_REJECT_INVALID_BILLER_ID = "B21";
    public final static String BATCH_REJECT_INVALID_BILLER_ID_MSG 
    = "Invalid biller id";
    
    public final static String BATCH_REJECT_FIELD_FORMAT_ERROR_IN_HEADER = "B26";
    public final static String BATCH_REJECT_FIELD_FORMAT_ERROR_IN_HEADER_MSG 
    = "Field format error in batch header";

    public final static String BATCH_REJECT_DUPLICATE_BATCH = "B27";
    public final static String BATCH_REJECT_DUPLICATE_BATCH_MSG 
    = "Duplicate batch";
    
    public final static String BATCH_REJECT_FIELD_FORMAT_ERROR_IN_TRAILER = "B28";
    public final static String BATCH_REJECT_FIELD_FORMAT_ERROR_IN_TRAILER_MSG 
    = "Field format error in batch trailer";

    public final static String BATCH_REJECT_NON_INITALIZED_FIELD_IN_HEADER = "B32";
    public final static String BATCH_REJECT_NON_INITALIZED_FIELD_IN_HEADER_MSG 
    = "High/low non initialized value required in batch header";
    
    public final static String BATCH_REJECT_NON_INITALIZED_FIELD_IN_TRAILER = "B42";
    public final static String BATCH_REJECT_NON_INITALIZED_FIELD_IN_TRAILER_MSG 
    = "High/low non initialized value required in batch trailer";
    
    // Single entry record reject codes and messages
    public final static String SE_REJECT_NON_NUMERIC_IN_ADDENDUM = "J10";
    public final static String SE_REJECT_NON_NUMERIC_IN_ADDENDUM_MSG 
    = "Non numeric value in addendum";

    public final static String SE_UNMATCHED_COUNTRY_CODES = "J17";
    public final static String SE_UNMATCHED_COUNTRY_CODES_MSG 
    = "CSP and BSP country codes not equal";

    public final static String SE_REJECT_IMPROPER_EFFECTIVE_ENTRY_DATE = "J18";
    public final static String SE_REJECT_IMPROPER_EFFECTIVE_ENTRY_DATE_MSG 
    = "Improper effective entry date";
    
    public final static String SE_REJECT_NET_AMT_FIELD_ERROR = "J19";
    public final static String SE_REJECT_NET_AMT_FIELD_ERROR_MSG 
    = "Net amount field error";

    public final static String SE_REJECT_INVALID_ACCOUNT_MASK = "J22";
    public final static String SE_REJECT_INVALID_ACCOUNT_MASK_MSG 
    = "Invalid customer account mask";

    public final static String SE_REJECT_CHECK_DIGIT_ERROR = "J23";
    public final static String SE_REJECT_CHECK_DIGIT_ERROR_MSG 
    = "Check digit error";

    public final static String SE_REJECT_ADDENDUM_FORMAT_ERROR = "J25";
    public final static String SE_REJECT_ADDENDUM_FORMAT_ERROR_MSG 
    = "Addendum format error";

    public final static String SE_REJECT_FIELD_FORMAT_ERROR = "J26";
    public final static String SE_REJECT_FIELD_FORMAT_ERROR_MSG 
    = "Field format error in entry detail";

    public final static String SE_REJECT_IMPROPER_TRACE_NUMBER = "J27";
    public final static String SE_REJECT_IMPROPER_TRACE_NUMBER_MSG 
    = "Improper trace number";

    public final static String SE_REJECT_NO_PRENOTES = "J29";
    public final static String SE_REJECT_NO_PRENOTES_MSG 
    = "Biller does not accept pre-notes";

    public final static String SE_REJECT_FAIR_SHARE_ERROR = "J30";
    public final static String SE_REJECT_FAIR_SHARE_ERROR_MSG 
    = "Fair share error";

    public final static String SE_REJECT_NO_PMT = "J31";
    public final static String SE_REJECT_NO_PMT_MSG 
    = "Biller does not accept payments";
    
    public final static String SE_REJECT_NON_INITALIZED_FIELD_IN_ENTRY_DETAIL = "J32";
    public final static String SE_REJECT_NON_INITALIZED_FIELD_IN_ENTRY_DETAIL_MSG 
    = "High/low non initialized value required in entry detail";    

    public final static String SE_REJECT_GROSS_AMT_ERROR = "J33";
    public final static String SE_REJECT_GROSS_AMT_ERROR_MSG 
    = "Credit counselling gross amount field error";    
    
    public final static String SE_REJECT_NON_INITALIZED_FIELD_IN_POSITIONS = "J42";
    public final static String SE_REJECT_NON_INITALIZED_FIELD_IN_POSITIONS_MSG 
    = "High/low non initialized value required in positions 40-54";        
    
    // Return/reversal codes and messages
    public final static String RETURN_ACCOUNT_CLOSED = "R02";
    public final static String RETURN_ACCOUNT_CLOSED_MSG 
    = "Customer's account is closed";

    public final static String RETURN_NO_ACCOUNT = "R03";
    public final static String RETURN_NO_ACCOUNT_MSG 
    = "No account";

    public final static String RETURN_INVALID_ACCOUNT_NUM1 = "R04";
    public final static String RETURN_INVALID_ACCOUNT_NUM1_MSG 
    = "Invalid account number";

    public final static String RETURN_NO_PRENOTIFICATION = "R05";
    public final static String RETURN_NO_PRENOTIFICATION_MSG 
    = "No prenotification on file";

    public final static String RETURN_ORIGINATOR_REQUEST = "R06";
    public final static String RETURN_ORIGINATOR_REQUEST_MSG 
    = "Return per originator's request";

    public final static String RETURN_AUTHORIZATION_REVOKED = "R07";
    public final static String RETURN_AUTHORIZATION_REVOKED_MSG 
    = "Authorization revoked by customer";

    public final static String RETURN_UNCOLLECTED_FUNDS = "R09";
    public final static String RETURN_UNCOLLECTED_FUNDS_MSG 
    = "Uncollected funds";

    public final static String RETURN_NOT_AUTHORIZED = "R10";
    public final static String RETURN_NOT_AUTHORIZED_MSG 
    = "Customer advises not authorized";

    public final static String RETURN_CUSTOMER_DECEASED = "R14";
    public final static String RETURN_CUSTOMER_DECEASED_MSG 
    = "Customer deceased";

    public final static String RETURN_INDIVIDUAL_DECEASED = "R15";
    public final static String RETURN_INDIVIDUAL_DECEASED_MSG 
    = "Individual (beneficiary) deceased";

    public final static String RETURN_ACCOUNT_FROZEN = "R16";
    public final static String RETURN_ACCOUNT_FROZEN_MSG 
    = "Account frozen";

    public final static String RETURN_IMPROPER_EFFECTIVE_ENTRY_DATE = "JR18";
    public final static String RETURN_IMPROPER_EFFECTIVE_ENTRY_DATE_MSG 
    = "Improper effective entry date";

    public final static String RETURN_AMOUNT_FIELD_ERROR = "R19";
    public final static String RETURN_AMOUNT_FIELD_ERROR_MSG 
    = "Amount field error";

    public final static String RETURN_NON_TRN_ACCOUNT = "R20";
    public final static String RETURN_NON_TRN_ACCOUNT_MSG 
    = "Non transaction account";

    public final static String RETURN_INVALID_BILLER_ID = "R21";
    public final static String RETURN_INVALID_BILLER_ID_MSG 
    = "Invalid biller id number";

    public final static String RETURN_INVALID_ACCOUNT_NUM2 = "R22";
    public final static String RETURN_INVALID_ACCOUNT_NUM2_MSG 
    = "Invalid account number";

    public final static String RETURN_PMT_REFUSED = "R23";
    public final static String RETURN_PMT_REFUSED_MSG 
    = "Payment refused by biller";

    public final static String RETURN_DUPLICATE_ENTRY = "R24";
    public final static String RETURN_DUPLICATE_ENTRY_MSG 
    = "Duplicate entry"; 
    
    public final static String RETURN_FAIR_SHARE_ERROR = "R25";
    public final static String RETURN_FAIR_SHARE_ERROR_MSG 
    = "Invalid fair share amount";

    public final static String RETURN_IMPROPER_REVERSAL = "R98";
    public final static String RETURN_IMPROPER_REVERSAL_MSG 
    = "Improper reversal";       
    
    // Modified non financial transactions codes and msgs.
    public final static String MNFT_INCORRECT_ACCOUNT_NUM = "C09";
    public final static String MNFT_INCORRECT_ACCOUNT_NUM_MSG 
    = "Customer account number was incorrect";

    public final static String MNFT_INCORRECT_COMPANY_ID = "C11";
    public final static String MNFT_INCORRECT_COMPANY_ID_MSG 
    = "Incorrect company id number (biller id number)";    
}
