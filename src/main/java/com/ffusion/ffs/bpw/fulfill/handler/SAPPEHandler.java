package com.ffusion.ffs.bpw.fulfill.handler;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import com.ffusion.ffs.bpw.custimpl.interfaces.BackendProvider;
import com.ffusion.ffs.bpw.db.DBConnCache;
import com.ffusion.ffs.bpw.interfaces.CustomerInfo;
import com.ffusion.ffs.bpw.interfaces.CustomerPayeeInfo;
import com.ffusion.ffs.bpw.interfaces.PayeeInfo;
import com.ffusion.ffs.bpw.interfaces.PayeeRouteInfo;
import com.ffusion.ffs.bpw.interfaces.PmtInfo;
import com.ffusion.ffs.bpw.interfaces.ToBackend;
import com.ffusion.ffs.bpw.interfaces.handlers.FulfillmentAPI;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.bpw.handler.OSGIUtil;

public class SAPPEHandler implements FulfillmentAPI{

	@Override
	public void start() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startPmtBatch(FFSConnectionHolder dbh) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startCustBatch(FFSConnectionHolder dbh) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int addCustomers(CustomerInfo[] customers, FFSConnectionHolder dbh) throws Exception {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int modifyCustomers(CustomerInfo[] customers, FFSConnectionHolder dbh) throws Exception {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int deleteCustomers(CustomerInfo[] customers, FFSConnectionHolder dbh) throws Exception {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void endCustBatch(FFSConnectionHolder dbh) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startPayeeBatch(FFSConnectionHolder dbh) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void addPayees(PayeeInfo[] payees, FFSConnectionHolder dbh) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void modPayees(PayeeInfo[] payees, FFSConnectionHolder dbh) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void deletePayees(PayeeInfo[] payees, FFSConnectionHolder dbh) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void endPayeeBatch(FFSConnectionHolder dbh) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startCustomerPayeeBatch(FFSConnectionHolder dbh) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void addCustomerPayees(CustomerPayeeInfo[] info, PayeeInfo[] payees, FFSConnectionHolder dbh)
			throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void modCustomerPayees(CustomerPayeeInfo[] info, PayeeInfo[] payees, FFSConnectionHolder dbh)
			throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void deleteCustomerPayees(CustomerPayeeInfo[] info, PayeeInfo[] payees, FFSConnectionHolder dbh)
			throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void endCustomerPayeeBatch(FFSConnectionHolder dbh) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void addPayments(PmtInfo[] pmtInfo, PayeeRouteInfo[] routeinfo, FFSConnectionHolder dbh) throws Exception {
		final String currentMethod = "SAPPEHandler.addPayments";
		FFSDebug.log(currentMethod + " starts" + FFSConst.PRINT_DEV);
		
		// Generate a new batch key and bind the db connection
        // using the batch key.
        String batchKey = DBConnCache.getNewBatchKey();
        DBConnCache.bind(batchKey, dbh);
		try {
			// Set batch key to payments
	        for(int idx=0; idx<pmtInfo.length; idx++) {
	        	pmtInfo[idx].setBatchKey(batchKey);
	        }
			
	        // Send schedule payments to BaS
	        ToBackend billPaymentBackendHandler = getBillPaymentBackendHandler();
			billPaymentBackendHandler.processScheduledBillPayments(pmtInfo, new HashMap<String, Object>());
		} catch (Exception e) {
			FFSDebug.log(currentMethod + " exception during SAPPEHandler.addPayments: " + e.toString(), FFSConst.PRINT_DEV);
			throw e;
		} finally {
			// Remove the binding of the db connection and the batch key.
	        DBConnCache.unbind(batchKey);
		}
		FFSDebug.log(currentMethod + " End" + FFSConst.PRINT_DEV);
	}

	@Override
	public void endPmtBatch(FFSConnectionHolder dbh) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void shutdown() throws Exception {
		// TODO Auto-generated method stub
		
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
