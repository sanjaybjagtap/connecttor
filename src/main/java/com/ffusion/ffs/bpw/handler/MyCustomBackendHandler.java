package com.ffusion.ffs.bpw.handler;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import com.ffusion.ffs.bpw.custimpl.MyCustomBackendImpl;
import com.ffusion.ffs.bpw.custimpl.interfaces.BackendProvider;
import com.ffusion.ffs.bpw.handler.OSGIUtil;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
/**
 */
public class MyCustomBackendHandler extends CustomBackendHandler
{
  /**
   * Constructor for MyCustomBackendHandler.
   */
  public MyCustomBackendHandler()
  {
    //super();
    
    try{
	  	   BackendProvider backendProvider = getBackendProviderService();
	  	   _custImpl = backendProvider.getMyCustomBackendInstance();
	  		   
	  	   } catch(Throwable t){
	  		   FFSDebug.log("Unable to get MyCustomBackend Instance" , PRINT_ERR);
	  	   }
    
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
