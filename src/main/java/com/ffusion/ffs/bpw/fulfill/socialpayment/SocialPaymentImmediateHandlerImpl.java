package com.ffusion.ffs.bpw.fulfill.socialpayment;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import com.ffusion.ffs.bpw.custimpl.interfaces.BackendProvider;
import com.ffusion.ffs.bpw.handler.OSGIUtil;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.IImmediateBillPayHandler;
import com.ffusion.ffs.bpw.interfaces.IntraTrnInfo;
import com.ffusion.ffs.bpw.interfaces.IntraTrnRslt;
import com.ffusion.ffs.bpw.interfaces.PayeeInfo;
import com.ffusion.ffs.bpw.interfaces.PayeeRouteInfo;
import com.ffusion.ffs.bpw.interfaces.PmtInfo;
import com.ffusion.ffs.bpw.interfaces.ToBackend;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;


public class SocialPaymentImmediateHandlerImpl implements IImmediateBillPayHandler
{
	private static final String IS_PAYTOCONTACT= "isPayToContact";
	private static final String MEMO = "Memo";	
	
	public PmtInfo[] processBillPayment(PmtInfo[] payments, String batchKey, HashMap extraInfo) throws Exception
	{
		final String currentMethod = "SocialPaymentImmediateHandlerImpl.processBillPayment";
		
		FFSDebug.log(currentMethod + " starts" + FFSConst.PRINT_DEV);
		
		if(payments != null && payments.length > 0)
		{
			PmtInfo payment = payments[0];
			String returnStatus = DBConsts.PROCESSEDON;	// default return status
			
			try
			{
				IntraTrnInfo intraTrnInfo = getIntraTransferInfoFromPmtInfo(payment, batchKey);	
				HashMap extraFields = null;
				if(intraTrnInfo.extraFields == null){
					extraFields = new HashMap();
					intraTrnInfo.extraFields = extraFields;
				}else {
					extraFields = (HashMap) intraTrnInfo.extraFields;
				}
				extraFields.put(IS_PAYTOCONTACT, "true");
				extraFields.put(MEMO, payment.getMemo());

				ToBackend intraBackendHandler = getIntraTransferBackendHandler();
				IntraTrnRslt result = intraBackendHandler.ProcessImmediateIntraTrn(intraTrnInfo);

				returnStatus = getStatus(result.status);
				
				payment.setStatus(returnStatus);
				
				payment.setStatusCode(DBConsts.SUCCESS);
				payment.setStatusMsg(DBConsts.SUCCESS_MSG);
				payment.setConfirmationNumber(result.confirmationNum);
				
				// Processing extra info for payment
				payment.extraFields = result.extraFields;
			}
			catch(Exception e)
			{
				FFSDebug.log(currentMethod + " exception during IntraTransfer: " + e.toString(), FFSConst.PRINT_DEV);
				throw e;
			}
			
			FFSDebug.log(currentMethod + " returns status: " + payment.getStatus(), FFSConst.PRINT_DEV);
		}
		
		FFSDebug.log(currentMethod + " ends", FFSConst.PRINT_DEV);
		
		// TODO Auto-generated method stub
		return payments;
	}
	
	
	
	
	// convert information from a PmtInfo object into a IntraTrnInfo
	// to pose the payment as an intra-transfer
	public IntraTrnInfo getIntraTransferInfoFromPmtInfo(PmtInfo pmtInfo, String batchKey)
	{
		PayeeInfo payeeInfo = pmtInfo.getPayeeInfo();
		PayeeRouteInfo payeeRouteInfo = payeeInfo.getPayeeRouteInfo();
		
		IntraTrnInfo intraTrnInfo = new IntraTrnInfo();
		
		intraTrnInfo.customerId = pmtInfo.getCustomerID();
		intraTrnInfo.bankId = payeeRouteInfo.BankID;
		intraTrnInfo.acctIdFrom = pmtInfo.getAcctDebitID();
		intraTrnInfo.acctTypeFrom = pmtInfo.getAcctDebitType();
		intraTrnInfo.acctIdTo = payeeRouteInfo.AcctID;
		intraTrnInfo.acctTypeTo = payeeRouteInfo.AcctType;
		
		intraTrnInfo.amount = pmtInfo.getAmt();
		intraTrnInfo.curDef = pmtInfo.getCurDef();
		intraTrnInfo.dateToPost = pmtInfo.dateToPost;
		intraTrnInfo.srvrTid = pmtInfo.getSrvrTID();
		intraTrnInfo.logId = pmtInfo.getLogID();
		
		intraTrnInfo.eventId = "0";	// immediate mode
		intraTrnInfo.eventSequence = 1;
		
		intraTrnInfo.possibleDuplicate = false;
		
		intraTrnInfo.recSrvrTid = pmtInfo.getRecSrvrTID();
		
		intraTrnInfo.batchKey = batchKey;
		
		intraTrnInfo.lastModified = pmtInfo.getLastModified();
		intraTrnInfo.submittedBy = pmtInfo.getSubmittedBy();
		
		intraTrnInfo.fiId = pmtInfo.getFIID();
		intraTrnInfo.fromBankId = pmtInfo.getBankID();
		intraTrnInfo.setCustomerInfo(pmtInfo.getCustomerInfo());
		if(pmtInfo.getPayeeInfo()!= null && pmtInfo.getPayeeInfo().getPayeeRouteInfo() != null){
			intraTrnInfo.toAmtCurrency = pmtInfo.getPayeeInfo().getPayeeRouteInfo().CurrencyCode;
			intraTrnInfo.toAmount = String.valueOf(pmtInfo.getPayeeInfo().getPayeeRouteInfo().PaymentCost);
		}
		return intraTrnInfo;
	}
	
    // this method is for converting status returned by the backend
	// from intra transfer result to fulfillment processing status
    public static String getStatus(int code)
    {
        if (code == DBConsts.STATUS_OK)	// 0
        {
            return DBConsts.PROCESSEDON;
        }
        else if (code == DBConsts.STATUS_INSUFFICIENT_FUNDS)	// 10504
        {
            return DBConsts.NOFUNDSON;
        }
        else if (code == DBConsts.STATUS_GENERAL_ERROR)	// 2000
        {
            return DBConsts.FAILEDON;
        }
        else
        {	
            return DBConsts.FAILEDON;
        }
    }
	
	// provide a way for users to implement and use their own
	// handler for intra transfer
	public ToBackend getIntraTransferBackendHandler() throws Exception
	{
		BackendProvider backendProvider = getBackendProviderService();
		ToBackend backend = backendProvider.getToBackendInstance();
		return backend;
	}
	
	
   /**
    * Gets the backend provider service instance.
    *
    * @return the backend provider service instance
    */
   protected BackendProvider getBackendProviderService() throws Exception{
	   BackendProvider backendProvider = null;
	   Object obj =  OSGIUtil.getBean("backendProviderServices");
	   String backendType = null;
	   if(obj != null && obj instanceof List) {
  			List<BackendProvider> backendProviders = (List<BackendProvider>)obj;
  			
  			// get backend type property from config_master
  			com.sap.banking.common.bo.interfaces.CommonConfig commonConfigBO = (com.sap.banking.common.bo.interfaces.CommonConfig)OSGIUtil.getBean(com.sap.banking.common.bo.interfaces.CommonConfig.class);
  			backendType = getBankingBackendType(commonConfigBO);
  			
  			// iterate through the list of service refs and return ref based on configuration
  			if(backendProviders != null && !backendProviders.isEmpty()) {
  				Iterator<BackendProvider> iteratorBackendProvider =  backendProviders.iterator();
  				while(iteratorBackendProvider.hasNext()) {
  					BackendProvider backendProviderRef = iteratorBackendProvider.next();
  					if(backendType != null && backendType.equals(backendProviderRef.getBankingBackendType())) {
  						backendProvider = backendProviderRef;
  						break;
  					}
  				}
  			}
	   }
	   if(backendProvider == null) {
			FFSDebug.log("Invalid backend type." + backendType, FFSConst.PRINT_ERR);
			throw new Exception("Invalid backend type." + backendType);
	   }
	   return backendProvider;
	}
   
   private String getBankingBackendType(com.sap.banking.common.bo.interfaces.CommonConfig commonConfigBO) {
		
		String backendType = null;
		try {
			 backendType = commonConfigBO.getBankingBackendType();
		} catch (Exception e) {
			 FFSDebug.log(Level.SEVERE, "==== getBankingBackendType" , e);
		}
		
		return backendType;
	}
}
