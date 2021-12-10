package com.ffusion.ffs.bpw.fulfill.handler;

import com.ffusion.ffs.bpw.interfaces.FulfillmentInfo;
import com.ffusion.ffs.bpw.interfaces.IImmediateBillPayHandler;
import com.ffusion.ffs.bpw.interfaces.handlers.BPWFulfillmentHandlerProvider;
import com.ffusion.ffs.bpw.interfaces.handlers.BPWFulfillmentRespFileHandler;
import com.ffusion.ffs.bpw.interfaces.handlers.FulfillmentAPI;
import com.ffusion.ffs.bpw.handler.OSGIUtil;

public class BPWFulfillmentHandlerProviderImpl implements
		BPWFulfillmentHandlerProvider {

	@Override
	public FulfillmentAPI getFulfillmentHandler(FulfillmentInfo fulfillmentInfo)
			throws Exception {

		Class handlerClass = Class.forName(fulfillmentInfo.HandlerName);
		//FulfillmentAPI fulfillmentHandler = (FulfillmentAPI) handlerClass.newInstance();
		FulfillmentAPI fulfillmentHandler = (FulfillmentAPI) OSGIUtil.getBean(handlerClass);
		return fulfillmentHandler;

	}

	@Override
	public IImmediateBillPayHandler getImmediateBillPayHandler(
			FulfillmentInfo fulfillmentInfo) throws Exception {

		Class handlerClass = Class.forName(fulfillmentInfo
				.getImmediateHandlerName());
		IImmediateBillPayHandler immediateHandler = (IImmediateBillPayHandler) handlerClass
				.newInstance();
		return immediateHandler;
	}
	

	@Override
	public BPWFulfillmentRespFileHandler getBPWFulfillmentRespFileHandler(String className) throws Exception {
	
		Class handlerClass = Class.forName(className);
		BPWFulfillmentRespFileHandler fulfillmentRespFileHandler = (BPWFulfillmentRespFileHandler) handlerClass
				.newInstance();
		return fulfillmentRespFileHandler;
	}
	
	

}
