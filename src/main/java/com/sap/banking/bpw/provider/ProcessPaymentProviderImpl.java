package com.sap.banking.bpw.provider;

import com.ffusion.ffs.bpw.interfaces.ProcessPayment;
import com.sap.banking.bpw.provider.interfaces.ProcessPaymentProvider;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Resource;
import java.util.List;

public class ProcessPaymentProviderImpl implements ProcessPaymentProvider {
	@Autowired(required=false)
	@Resource(name="processPayments")
	private List<ProcessPayment> processPayments;

	@Autowired(required=false)
	@Resource(name = "processPayments")
	private List<Object> processPaymentsList;
	
	@Override
	public List<ProcessPayment> getProcessPayments() {
		return processPayments;
	}
	
	@Override
	public void setProcessPayments(List<ProcessPayment> processPayments) {
		this.processPayments = processPayments;
	}
	
	public List<Object> getProcessPaymentsList() {
		return processPaymentsList;
	}
	
	public void setProcessPaymentsList(List<Object> processPaymentsList) {
		this.processPaymentsList = processPaymentsList;
	}
	
	@Override
	public ProcessPayment getProcessPayment(String schedulerName) throws Exception {
		ProcessPayment processPayment = null;

		for (ProcessPayment processPaymentService : processPayments) {
			List<String> supportedInstTypes = processPaymentService.getSupportedScheduleTypes();
			if (null != supportedInstTypes) {
				if (supportedInstTypes.contains(schedulerName)) {
					processPayment = processPaymentService;
					break;
				}
			}

			if (null == processPayment) {
				List<String> supportedRecInstType = processPaymentService.getSupportedRecScheduleTypes();
				if (null != supportedRecInstType) {
					if (supportedRecInstType.contains(schedulerName)) {
						processPayment = processPaymentService;
						break;
					}
				}
			}
		}
		
		return processPayment;
	}
}
