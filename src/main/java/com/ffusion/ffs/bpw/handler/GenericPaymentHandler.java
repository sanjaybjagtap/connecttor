package com.ffusion.ffs.bpw.handler;

import java.util.List;

import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.ProcessPayment;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.scheduling.constants.ScheduleConstants;
import com.sap.banking.bpw.provider.interfaces.ProcessPaymentProvider;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

public class GenericPaymentHandler implements BPWScheduleHandler {
	
	private List<ProcessPayment> processPaymentServiceList;
	private ProcessPayment instTypeProcessPaymentService;

	@Override
	public void eventHandler(int eventSequence, EventInfoArray evts, FFSConnectionHolder dbh) throws Exception {
		String methodName = "GenericPaymentHandler.eventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
		processEvents(eventSequence, evts, dbh, false);
		PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
	}

	@Override
	public void resubmitEventHandler(int eventSequence, EventInfoArray evts, FFSConnectionHolder dbh) throws Exception {
		String methodName = "GenericPaymentHandler.resubmitEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
		processEvents(eventSequence, evts, dbh, true);
		PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
	}
	
	protected void processEvents(int eventSequence, EventInfoArray evts, FFSConnectionHolder dbh, boolean resubmit)
			throws Exception {
		if (eventSequence == ScheduleConstants.EVT_SEQUENCE_FIRST) {
			init(evts._array[0].InstructionType);
			instTypeProcessPaymentService.start(evts, dbh, resubmit);
		} else if (eventSequence == ScheduleConstants.EVT_SEQUENCE_BATCH_START) {
			instTypeProcessPaymentService.batchStart(evts, dbh, resubmit);
		} else if (eventSequence == ScheduleConstants.EVT_SEQUENCE_NORMAL) {
			instTypeProcessPaymentService.process(evts, dbh, resubmit);
		} else if (eventSequence == ScheduleConstants.EVT_SEQUENCE_BATCH_END) {
			instTypeProcessPaymentService.batchEnd(evts, dbh, resubmit);
		} else if (eventSequence == ScheduleConstants.EVT_SEQUENCE_LAST) {
			instTypeProcessPaymentService.end(evts, dbh, resubmit);
		}
	}
	
	protected void init(String instructionType) throws Exception {
		ProcessPaymentProvider processPaymentProvider = (ProcessPaymentProvider)OSGIUtil.getBean("processPaymentProviderRef");
		instTypeProcessPaymentService = processPaymentProvider.getProcessPayment(instructionType);

		if (null == instTypeProcessPaymentService) {
			throw new Exception("Payment Process Service for " + instructionType
					+ " is not available in Payment Process Service list");
		}
	}

}
