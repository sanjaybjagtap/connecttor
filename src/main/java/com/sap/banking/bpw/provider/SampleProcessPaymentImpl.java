package com.sap.banking.bpw.provider;

import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.ProcessPayment;
import com.ffusion.ffs.db.FFSConnectionHolder;

import java.util.ArrayList;
import java.util.List;

public class SampleProcessPaymentImpl implements ProcessPayment {

	@Override
	public List<String> getSupportedScheduleTypes() {
		List<String> scheduleTypes= null;
		scheduleTypes= new ArrayList<String>();
		scheduleTypes.add("SamplePayment");
		return scheduleTypes;

	}

	@Override
	public List<String> getSupportedRecScheduleTypes() {
		List<String> scheduleTypes= null;
		scheduleTypes= new ArrayList<String>();
		scheduleTypes.add("SampleRecPayment");
		return scheduleTypes;
	}

	@Override
	public void start(EventInfoArray evt, FFSConnectionHolder dbh, boolean resubmit) throws Exception {
		

	}

	@Override
	public void batchStart(EventInfoArray evt, FFSConnectionHolder dbh, boolean resubmit) throws Exception {
		

	}

	@Override
	public void process(EventInfoArray evt, FFSConnectionHolder dbh, boolean resubmit) throws Exception {
		

	}

	@Override
	public void batchEnd(EventInfoArray evt, FFSConnectionHolder dbh, boolean resubmit) throws Exception {

	}

	@Override
	public void end(EventInfoArray evt, FFSConnectionHolder dbh, boolean resubmit) throws Exception {

	}

}
