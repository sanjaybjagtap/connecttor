package com.ffusion.ffs.bpw.handler;

import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandlerProvider;
import com.ffusion.ffs.bpw.interfaces.InstructionType;
import com.sap.banking.connector.bo.interfaces.ConnectorInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Properties;

public class BPWScheduleHandlerProviderImpl implements
		BPWScheduleHandlerProvider {

	@Autowired
	@Qualifier("connectorLibConfig")
	private Properties commonConfigProps;

	@Autowired
	@Qualifier("connectorLibInterface")
	private ConnectorInterface connectorInterface;

	@Override
	public BPWScheduleHandler getScheduleHandler(InstructionType instructionType)
			throws Exception {
		/*boolean enableConnectorDelegation = "true".equals(commonConfigProps.getProperty("ocb.connectorlib.delegatebpw"))
				&& "true".equals(commonConfigProps.getProperty("ocb.connectorlib.enable"));
		if(enableConnectorDelegation) {
			return new ConnectorDelegationScheduleHandler(instructionType,connectorInterface);
		} else {*/
			String handlerClassName = instructionType.HandlerClassName;
			Class handlerClass = Class.forName(handlerClassName);
			BPWScheduleHandler scheduleHandler = (BPWScheduleHandler) handlerClass
					.newInstance();
			return scheduleHandler;
		//}
	}

}
