package com.ffusion.ffs.bpw.fulfill.handler;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import com.ffusion.ffs.bpw.custimpl.interfaces.BackendProvider;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.IImmediateBillPayHandler;
import com.ffusion.ffs.bpw.interfaces.PmtInfo;
import com.ffusion.ffs.bpw.interfaces.PmtTrnRslt;
import com.ffusion.ffs.bpw.interfaces.ToBackend;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.bpw.handler.OSGIUtil;

public class SAPPEImmediateHandler implements IImmediateBillPayHandler{

	@Override
	public PmtInfo[] processBillPayment(PmtInfo[] payments, String batchKey, HashMap extraInfo) throws Exception {

		final String currentMethod = "SAPPEImmediateHandler.processBillPayment";
		
		FFSDebug.log(currentMethod + " starts" + FFSConst.PRINT_DEV);
		
		if (payments != null && payments.length > 0) {
			PmtInfo payment = payments[0];
			String returnStatus = DBConsts.PROCESSEDON; // default return status

			try {
				// Set batch key to the immediate payment
				payment.setBatchKey(batchKey);
				
				ToBackend intraBackendHandler = getBillPaymentBackendHandler();
				PmtTrnRslt pmtTrnRslt = intraBackendHandler.processImmediateBillPayment(payment, extraInfo);
				
				returnStatus = getStatus(pmtTrnRslt.status);
				
				payment.setStatus(returnStatus);
				
				payment.setStatusCode(pmtTrnRslt.getStatusCode());
				payment.setStatusMsg(pmtTrnRslt.getStatusMsg());
				payment.setConfirmationNumber(pmtTrnRslt.getConfirmationNum());
				
				// Processing extra info for payment
				payment.extraFields = pmtTrnRslt.extraFields;
				
			} catch (Exception e) {
				FFSDebug.log(currentMethod + " exception during SAPPEImmediateHandler.processBillPayment: " + e.toString(), FFSConst.PRINT_DEV);
				throw e;
			}

			FFSDebug.log(currentMethod + " returns status: " + payment.getStatus(), FFSConst.PRINT_DEV);
		}
		return payments;
	}
	
	/**
	 * This API convert status return by back end system to the fulfillment processing status
	 * @param code
	 * @return
	 */
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
	 
	/**
	 * provide a way for users to implement and user there handler to implement immediate Bill Payment
	 * @return
	 * @throws Exception
	 */
	private ToBackend getBillPaymentBackendHandler() throws Exception
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
