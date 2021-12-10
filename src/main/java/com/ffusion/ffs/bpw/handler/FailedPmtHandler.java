package com.ffusion.ffs.bpw.handler;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import com.ffusion.ffs.bpw.audit.TransAuditLog;
import com.ffusion.ffs.bpw.custimpl.interfaces.BackendProvider;
import com.ffusion.ffs.bpw.custimpl.interfaces.FailedPayment;
import com.ffusion.ffs.bpw.db.PmtInstruction;
import com.ffusion.ffs.bpw.interfaces.AuditLogConsts;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.BPWScheduleHandler;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.EventInfo;
import com.ffusion.ffs.bpw.interfaces.EventInfoArray;
import com.ffusion.ffs.bpw.interfaces.InstructionType;
import com.ffusion.ffs.bpw.interfaces.PmtInfo;
import com.ffusion.ffs.bpw.interfaces.PropertyConfig;
import com.ffusion.ffs.bpw.util.BPWRegistryUtil;
import com.ffusion.ffs.bpw.util.BPWUtil;
import com.ffusion.ffs.bpw.util.resources.BPWLocaleUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.scheduling.constants.ScheduleConstants;
import com.ffusion.ffs.scheduling.db.Event;
import com.ffusion.ffs.scheduling.db.EventInfoLog;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.ffusion.util.ILocalizable;
import com.ffusion.util.logging.AuditLogTranTypes;
import com.sap.banking.common.interceptors.PerfLoggerUtil;
public class FailedPmtHandler implements BPWScheduleHandler,DBConsts, FFSConst,BPWResource{

	private boolean _okToCall;

	public FailedPmtHandler()
	{
	}

	//=====================================================================
	// eventHandler()
	// Description: This method is called by the Scheduling engine
	// Arguments: none
	// Returns:   none
	// Note:
	//=====================================================================
	// eventHandler()
	//
	// This method passes the failed pmt information to the backend.
	// The are two parts of the process.
	// First it checks for performing crash recovery from the EventInfo record.
	// Second, in normal processing, it saves the data in the EventInfo record in case
	// it needs to do crash recovery.
	// The record from the EventInfo is copied over to the EventInfoLog for the purpose
	// of event resubmitting.
	// The EventInfo and EventInfoLog table are defined as follow:
	//      EventID - save the EventID of the EventInfo
	//      InstructionID - save the SrvrTID of the record
	//      InstructionType - save the InstructionType of the schedule event
	//      ScheduleFlag - save the following data for the failed pmts
	//                      ScheduleConstants.SCH_FLAG_FUNDSFAILEDON
	//                      ScheduleConstants.SCH_FLAG_NOFUNDSON
	//                      ScheduleConstants.SCH_FLAG_FUNDSREVERTED
	//                      ScheduleConstants.SCH_FLAG_FUNDSREVERTFAILED
	//      LogID - null
	//

	@Override
	public void eventHandler(int eventSequence, EventInfoArray evts,
			FFSConnectionHolder dbh) throws Exception {

		eventHandler(eventSequence, evts, dbh, false );		
	}


	public void eventHandler(
			int eventSequence,
			EventInfoArray evts,
			FFSConnectionHolder dbh,
			boolean isRecover )
					throws Exception
	{
		String methodName = "FailedPmtHandler.eventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
		FFSDebug.log("=== FailedPmtHandler.eventHandler: begin, eventSeq=" + eventSequence, PRINT_DEV);
		if ( eventSequence == 0 ) {         // FIRST sequence
			_okToCall = false;
		} else if ( eventSequence == 1 ) {    // NORMAL sequence
			_okToCall = true;
		} else if ( eventSequence == 2 ) {    // LAST sequence
			if (_okToCall || isRecover) {
				String fiId = evts._array[0].FIId;
				String instructionType = evts._array[0].InstructionType;

				FailedPayment failedPayment = null;
				try{
					BackendProvider backendProvider = getBackendProviderService();
					failedPayment = backendProvider.getFailedPaymentInstance();

				} catch(Throwable t){
					FFSDebug.log("Unable to get FailedTransfers Instance" , PRINT_ERR);
					PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
				}


				recoverFromCrash(dbh, fiId, instructionType, ScheduleConstants.SCH_FLAG_FUNDSFAILEDON, failedPayment);
				recoverFromCrash(dbh, fiId, instructionType, ScheduleConstants.SCH_FLAG_NOFUNDSON, failedPayment);
				recoverFromCrash(dbh, fiId, instructionType, ScheduleConstants.SCH_FLAG_FUNDSREVERTED, failedPayment);
				recoverFromCrash(dbh, fiId, instructionType, ScheduleConstants.SCH_FLAG_FUNDSREVERTFAILED, failedPayment);
				recoverFromCrash(dbh, fiId, instructionType, ScheduleConstants.SCH_FLAG_LIMIT_REVERT_FAILED, failedPayment);
				recoverFromCrash(dbh, fiId, instructionType, ScheduleConstants.SCH_FLAG_LIMIT_CHECK_FAILED, failedPayment);

				doFailedPmts(dbh, fiId, instructionType, FUNDSREVERTED, failedPayment);
				doFailedPmts(dbh, fiId, instructionType, FUNDSREVERTFAILED, failedPayment);
				doFailedPmts(dbh, fiId, instructionType, NOFUNDSON, failedPayment);
				doFailedPmts(dbh, fiId, instructionType, FUNDSFAILEDON, failedPayment);
				doFailedPmts(dbh, fiId, instructionType, LIMIT_REVERT_FAILED, failedPayment);
				doFailedPmts(dbh, fiId, instructionType, LIMIT_CHECK_FAILED, failedPayment);
			}
		} else if ( eventSequence == 3 ) {    // Batch-Start sequence
		} else if ( eventSequence == 4 ) {    // Batch-End sequence
		}
		FFSDebug.log("=== FailedPmtHandler.eventHandler: end", PRINT_DEV);
		PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
	}

	//=====================================================================
	// resubmitEventHandler()
	// Description: This method is called by the Scheduling engine during
	//     event resubmission.  It will set the possibleDuplicate to true
	//     before calling the ToBackend handler.
	// Arguments: none
	// Returns:   none
	// Note:
	//=====================================================================

	@Override
	public void resubmitEventHandler(int eventSequence, EventInfoArray evts,
			FFSConnectionHolder dbh) throws Exception {
		String methodName = "FailedPmtHandler.resubmitEventHandler";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
		FFSDebug.log("=== FailedPmtHandler.resubmitEventHandler: begin, eventSeq="
				+ eventSequence
				+ ",length="
				+ evts._array.length
				+ ",instructionType="
				+ evts._array[0].InstructionType, PRINT_DEV);
		if ( eventSequence == 0 ) {         // FIRST sequence
			_okToCall = false;
		} else if ( eventSequence == 1 ) {    // NORMAL sequence
			_okToCall = true;
		} else if ( eventSequence == 2 ) {    // LAST sequence
			int scheduleFlag = evts._array[0].ScheduleFlag; // retrieve scheduleFlag
			if (scheduleFlag == ScheduleConstants.SCH_FLAG_RESUBMIT) {
				if (_okToCall) {
					String fiId = evts._array[0].FIId;
					String instructionType = evts._array[0].InstructionType;
					String logDate = evts._array[0].InstructionID; // retrieve logDate from InstructionID

					FailedPayment failedPayment = null;

					try{
						BackendProvider backendProvider = getBackendProviderService();
						failedPayment = backendProvider.getFailedPaymentInstance();

					} catch(Throwable t){
						FFSDebug.log("Unable to get FailedTransfers Instance" , PRINT_ERR);
						PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
					}

					resubmitFailPmts(dbh, fiId, instructionType,
							ScheduleConstants.SCH_FLAG_FUNDSFAILEDON, logDate, failedPayment);
					resubmitFailPmts(dbh, fiId, instructionType,
							ScheduleConstants.SCH_FLAG_NOFUNDSON, logDate, failedPayment);
					resubmitFailPmts(dbh, fiId, instructionType,
							ScheduleConstants.SCH_FLAG_FUNDSREVERTED, logDate, failedPayment);
					resubmitFailPmts(dbh, fiId, instructionType,
							ScheduleConstants.SCH_FLAG_FUNDSREVERTFAILED, logDate, failedPayment);
					resubmitFailPmts(dbh, fiId, instructionType,
							ScheduleConstants.SCH_FLAG_LIMIT_REVERT_FAILED, logDate, failedPayment);
					resubmitFailPmts(dbh, fiId, instructionType,
							ScheduleConstants.SCH_FLAG_LIMIT_CHECK_FAILED, logDate, failedPayment);
				}
			} else {
				// do crash recovery when scheduleFlag=SCH_FLAG_NORMAL
				eventHandler(eventSequence, evts, dbh, true);
			}
		} else if ( eventSequence == 3 ) {    // Batch-Start sequence
		} else if ( eventSequence == 4 ) {    // Batch-End sequence
		}
		FFSDebug.log("=== FailedPmtHandler.resubmitEventHandler: end", PRINT_DEV);
		PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
	}

	private void doFailedPmts( FFSConnectionHolder dbh,
			String fiId,
			String instructionType,
			String status,
			FailedPayment failedPayment )
					throws Exception
	{
		try {
			// Retrieve a batch of failed pmt record with status.
			// Save in EventInfo with EVT_STATUS_SUBMIT status.
			// Copy the EventInfo into EventInfoLog.
			// Send the batch to the FailedPayment.processFailedPayments()

			//      ScheduleFlag - save the following data for the failed pmts
			//                      ScheduleConstants.SCH_FLAG_FUNDSFAILEDON
			//                      ScheduleConstants.SCH_FLAG_NOFUNDSON
			//                      ScheduleConstants.SCH_FLAG_FUNDSREVERTED
			//                      ScheduleConstants.SCH_FLAG_FUNDSREVERTFAILED
			int schFlag = ScheduleConstants.SCH_FLAG_FUNDSFAILEDON;
			if (status.equals(FUNDSFAILEDON)) {
				schFlag = ScheduleConstants.SCH_FLAG_FUNDSFAILEDON;
			} else if (status.equals(NOFUNDSON)) {
				schFlag = ScheduleConstants.SCH_FLAG_NOFUNDSON;
			} else if (status.equals(FUNDSREVERTED)) {
				schFlag = ScheduleConstants.SCH_FLAG_FUNDSREVERTED;
			} else if (status.equals(FUNDSREVERTFAILED)) {
				schFlag = ScheduleConstants.SCH_FLAG_FUNDSREVERTFAILED;
			} else if (status.equals(LIMIT_REVERT_FAILED)) {
				schFlag = ScheduleConstants.SCH_FLAG_LIMIT_REVERT_FAILED;
			} else if (status.equals(LIMIT_CHECK_FAILED)) {
				schFlag = ScheduleConstants.SCH_FLAG_LIMIT_CHECK_FAILED;
			}


			PropertyConfig propertyConfig = (PropertyConfig)FFSRegistry.lookup( BPWResource.PROPERTYCONFIG);
			ILocalizable msg = null;
			Object[] dynamicContent = new Object[1];
			int tranType = AuditLogTranTypes.BPW_GENERIC_PMTTRN;
			if (propertyConfig.LogLevel >= AUDITLOGLEVEL_STATUSUPDATE) {
				if (instructionType.equals(CHECKFREE_PMTTRN)) {
					dynamicContent[0] = BPWLocaleUtil.getLocalizableMessage(AuditLogConsts.AUDITLOG_MSG_CHECKFREE,
							null,
							BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
					tranType = AuditLogTranTypes.BPW_CHECKFREE_PMTTRN;
				} else if (instructionType.equals(METAVANTE_PMTTRN)) {
					dynamicContent[0] = BPWLocaleUtil.getLocalizableMessage(AuditLogConsts.AUDITLOG_MSG_METAVANTE,
							null,
							BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
					tranType = AuditLogTranTypes.BPW_METAVANTE_PMTTRN;
				} else if (instructionType.equals(ON_US_PMTTRN)) {
					dynamicContent[0] = BPWLocaleUtil.getLocalizableMessage(AuditLogConsts.AUDITLOG_MSG_ONUS,
							null,
							BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
					tranType = AuditLogTranTypes.BPW_ONUS_PMTTRN;
				} else if (instructionType.equals(ORCC_PMTTRN)) {
					dynamicContent[0] = BPWLocaleUtil.getLocalizableMessage(AuditLogConsts.AUDITLOG_MSG_ORCC,
							null,
							BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
					tranType = AuditLogTranTypes.BPW_ORCC_PMTTRN;
				} else if (instructionType.equals(RPPS_PMTTRN)) {
					dynamicContent[0] = BPWLocaleUtil.getLocalizableMessage(AuditLogConsts.AUDITLOG_MSG_RPPS,
							null,
							BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
					tranType = AuditLogTranTypes.BPW_RPPS_PMTTRN;
				} else if (instructionType.equals(BANKSIM_PMTTRN)) {
					dynamicContent[0] = BPWLocaleUtil.getLocalizableMessage(AuditLogConsts.AUDITLOG_MSG_BANKSIM,
							null,
							BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
					tranType = AuditLogTranTypes.BPW_BANKSIM_PMTTRN;
				} else if (instructionType.equals(SAMPLE_PMTTRN)) {
					dynamicContent[0] = BPWLocaleUtil.getLocalizableMessage(AuditLogConsts.AUDITLOG_MSG_SAMPLE,
							null,
							BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
					tranType = AuditLogTranTypes.BPW_SAMPLE_PMTTRN;
				} else {
					dynamicContent[0] = instructionType;
				}

				msg = BPWLocaleUtil.getLocalizableMessage(AuditLogConsts.AUDITLOG_MSG_BILLPAY_PROCESSING_FAILED_BILLPMT,
						dynamicContent,
						BPWLocaleUtil.BILLPAY_AUDITLOG_MESSAGE);
			}
			int batchSize =  propertyConfig.getBatchSize();
			PmtInfo[] pmtlist = PmtInstruction.getPmtBatchByStatus( fiId, status, batchSize, dbh );
			int addLen = pmtlist.length;
			while ( addLen!= 0 ) {
				ArrayList alist = new ArrayList();
				for ( int i = 0; i < addLen; i++ ) {
					PmtInstruction.updateStatus(dbh, pmtlist[i].SrvrTID, status+"_NOTIF" );
					String eventID = Event.createEvent( dbh,
							pmtlist[i].SrvrTID,
							fiId,
							instructionType,
							ScheduleConstants.EVT_STATUS_SUBMIT,
							schFlag,
							null );
					alist.add(eventID);

					// log into AuditLog
					if (propertyConfig.LogLevel >= AUDITLOGLEVEL_STATUSUPDATE) {
						int bizID = Integer.parseInt(pmtlist[i].CustomerID);
						BigDecimal pmtAmount = BPWUtil.getBigDecimal(pmtlist[i].getAmt());                         	
						String debitAcct = BPWUtil.getAccountIDWithAccountType(pmtlist[i].AcctDebitID, pmtlist[i].AcctDebitType);
						TransAuditLog.logTransAuditLog(dbh.conn.getConnection(),
								pmtlist[i].submittedBy, //BPW_INTERNAL_USERID,
								null,
								null,
								msg,
								pmtlist[i].LogID,
								tranType,
								bizID,
								pmtAmount,
								pmtlist[i].CurDef,
								pmtlist[i].SrvrTID,
								status+"_NOTIF",
								pmtlist[i].PayAcct,
								null,
								debitAcct,
								pmtlist[i].getBankID(),
								0);
					}
				}
				dbh.conn.commit();  // commit one batch at a time
				failedPayment.processFailedPayments(pmtlist);
				for ( int i = 0; i < addLen; i++ ) {
					EventInfoLog.createEventInfoLog(dbh,
							(String)alist.get(i),
							pmtlist[i].SrvrTID,
							fiId, instructionType,
							schFlag,
							null );
				}
				dbh.conn.commit();  // commit one batch at a time
				if ( PmtInstruction.isStatusBatchDone( fiId, status ) ) {
					addLen = 0;
				} else {
					pmtlist = PmtInstruction.getPmtBatchByStatus( fiId, status, batchSize, dbh );
					addLen = pmtlist.length;
				}
			}
		} finally {
			PmtInstruction.clearStatusBatch(fiId, status);
		}

	}

	private void recoverFromCrash(FFSConnectionHolder dbh,
			String fiId,
			String instructionType,
			int schFlag,
			FailedPayment failedPayment )
					throws Exception
	{
		try {
			// Retrieve EventInfo for those with status of "SUBMITTED" and
			// in ScheduleFlag in batch.
			// Send the batch to the FailedPayment.processFailedPayments()
			EventInfo [] evts = null;
			evts = Event.retrieveEventInfo(dbh, ScheduleConstants.EVT_STATUS_SUBMIT,
					fiId, instructionType,
					schFlag);
			if (evts != null) {
				InstructionType it = BPWRegistryUtil.getInstructionType(fiId, instructionType);
				int fileBasedRecovery = it.FileBasedRecovery;
				while (evts != null && evts.length > 0) {
					boolean isToCallHandler = true;
					ArrayList alist = new ArrayList();
					int queueLength = evts.length;
					int processedRecords = 0;
					if (fileBasedRecovery == 0) { // not file-based recovery
						EventInfoLog evLog = EventInfoLog.getByEventID(dbh,  evts[queueLength-1].EventID);
						if (evLog != null) { // do not send the batch if EventInfoLog found
							isToCallHandler = false;
						}
					}
					if (isToCallHandler) {
						while (processedRecords < queueLength) {
							EventInfoLog.updateEventInfoLog(dbh,
									evts[processedRecords].EventID,
									evts[processedRecords].InstructionID,
									evts[processedRecords].FIId,
									evts[processedRecords].InstructionType,
									evts[processedRecords].ScheduleFlag,
									evts[processedRecords].LogID);
							String srvrTID = evts[processedRecords].InstructionID;
							PmtInstruction pmt = PmtInstruction.getPmtInstr( srvrTID, dbh );
							if (pmt == null) {
								String msg = "*** FailedPmtHandler.recoverFromCrash failed: could not find the SrvrTID="+srvrTID+" in BPW_PmtInstruction";
								FFSDebug.console(msg);
								FFSDebug.log(msg);
								continue;
							}
							PmtInfo pmtinfo = pmt.getPmtInfo();

							if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_FUNDSFAILEDON) {
								pmtinfo.Status = FUNDSFAILEDON;
							} else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_NOFUNDSON) {
								pmtinfo.Status = NOFUNDSON;
							} else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_FUNDSREVERTED) {
								pmtinfo.Status = FUNDSREVERTED;
							} else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_FUNDSREVERTFAILED) {
								pmtinfo.Status = FUNDSREVERTFAILED;
							} else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_LIMIT_REVERT_FAILED) {
								pmtinfo.Status = LIMIT_REVERT_FAILED;
							} else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_LIMIT_CHECK_FAILED) {
								pmtinfo.Status = LIMIT_CHECK_FAILED;
							}


							alist.add(pmtinfo);
							processedRecords++;
						}
						PmtInfo[] pmtlist = (PmtInfo[]) alist.toArray( new PmtInfo[alist.size()] );
						failedPayment.processFailedPayments(pmtlist);
						dbh.conn.commit();  // commit one batch at a time
					}
					if ( Event.isBatchDone(fiId, instructionType)) {
						evts = new EventInfo [0];
					} else {
						evts = Event.retrieveEventInfo(dbh, ScheduleConstants.EVT_STATUS_SUBMIT,
								fiId, instructionType,
								schFlag);
					}
				}
			}
		} finally {
			Event.clearBatch(fiId, instructionType);
		}
	}

	private void resubmitFailPmts(FFSConnectionHolder dbh,
			String fiId,
			String instructionType,
			int schFlag,
			String logDate,
			FailedPayment failedPayment )
					throws Exception
	{
		try {
			// Retrieve EventInfoLog in batch.
			// Send the batch to the FailedPayment.processFailedPayments()
			EventInfo [] evts = null;
			evts = EventInfoLog.retrieveEventInfoLogs( dbh,
					schFlag,
					fiId, instructionType, logDate);
			if (evts != null) {
				while (evts != null && evts.length > 0) {
					ArrayList alist = new ArrayList();
					int queueLength = evts.length;
					int processedRecords = 0;
					while (processedRecords < queueLength) {
						String srvrTID = evts[processedRecords].InstructionID;
						PmtInstruction pmt = PmtInstruction.getPmtInstr( srvrTID, dbh );
						if (pmt == null) {
							String msg = "*** FailedPmtHandler.resubmitFailPmts failed: could not find the SrvrTID="+srvrTID+" in BPW_PmtInstruction";
							FFSDebug.console(msg);
							FFSDebug.log(msg);
							continue;
						}
						PmtInfo pmtinfo = pmt.getPmtInfo();

						if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_FUNDSFAILEDON) {
							pmtinfo.Status = FUNDSFAILEDON;
						} else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_NOFUNDSON) {
							pmtinfo.Status = NOFUNDSON;
						} else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_FUNDSREVERTED) {
							pmtinfo.Status = FUNDSREVERTED;
						} else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_FUNDSREVERTFAILED) {
							pmtinfo.Status = FUNDSREVERTFAILED;
						} else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_LIMIT_REVERT_FAILED) {
							pmtinfo.Status = LIMIT_REVERT_FAILED;
						} else if (evts[processedRecords].ScheduleFlag == ScheduleConstants.SCH_FLAG_LIMIT_CHECK_FAILED) {
							pmtinfo.Status = LIMIT_CHECK_FAILED;
						}


						alist.add(pmtinfo);
						processedRecords++;
					}
					PmtInfo[] pmtlist = (PmtInfo[]) alist.toArray( new PmtInfo[alist.size()] );
					failedPayment.processFailedPayments(pmtlist);
					dbh.conn.commit();  // commit one batch at a time
					if ( EventInfoLog.isBatchDone(fiId, instructionType)) {
						evts = new EventInfo [0];
					} else {
						evts = EventInfoLog.retrieveEventInfoLogs( dbh,
								schFlag,
								fiId, instructionType, logDate);
					}
				}
			}
		} finally {
			EventInfoLog.clearBatch(fiId, instructionType);
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
