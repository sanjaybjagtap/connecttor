package com.ffusion.ffs.bpw.handler;


import java.util.ArrayList;

import com.ffusion.ffs.bpw.db.ExternalTransferAccount;
import com.ffusion.ffs.bpw.db.Transfer;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.ExtTransferAcctInfo;
import com.ffusion.ffs.bpw.interfaces.TransferInfo;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;

/**
 * This class contains a callback handler that is registered in the
 * IntructionType table. The registered callback handler will be called by the
 * Scheduling engine for the transaction Processing.
 */
public class SameDayETFHandler extends TransferHandler {

	private static final long serialVersionUID = 1L;

	public SameDayETFHandler() throws Exception {
		super();
	}

	/**
	 * Get transfers with status of INPROCESS and match the designated processId and fiid
	 * @param dbh
	 * @param processId
	 * @param fIId
	 * @param processNumber
	 * @param batchSize
	 * @param category
	 * @return ArrayList containing TransferInfo objects
	 * @throws FFSException
	 */
	@Override
	public ArrayList getInProcessTransfersForFIId(FFSConnectionHolder dbh, 
			String processId, 
			String fIId, 
			int processNumber, 
			int batchSize,
			String category) throws FFSException {

		return Transfer.getInProcessTransfersForFIId(dbh, processId, fIId, processNumber, batchSize, category, isSameDayEnabledForETF(), true /* SameDayETFTran */ );
	}

	/**
	 * Get prnote transfers by processId and processNumber
	 * @param dbh
	 * @param processId
	 * @param processNumber
	 * @param category
	 * @return
	 * @throws FFSException
	 */
	@Override
	public TransferInfo[] getPrenoteTransferByProcessId(FFSConnectionHolder dbh,
			String processId,
			int processNumber,
			String category) throws FFSException {

		return Transfer.getPrenoteTransferByProcessId(dbh, processId, processNumber, category, isSameDayEnabledForETF(), true /* SameDayETFTran */);
	}

	/**
	 *  Get both processed and unprocessed transfers
	 * which have lastChangeDate > processedTime
	 * @param dbh
	 * @param processId
	 * @param fIId
	 * @param processNumber
	 * @param batchSize
	 * @param category
	 * @return ArrayList containing TransferInfo objects
	 * @throws FFSException
	 */
	@Override
	public ArrayList getAllTransfersForBackendByFIId(FFSConnectionHolder dbh,
			String processId,
			String fIId,
			int processNumber,
			int batchSize,
			String category) throws FFSException {

		return Transfer.getAllTransfersForBackendByFIId(dbh, processId, fIId, processNumber, batchSize, category, isSameDayEnabledForETF(), true /* SameDayETFTran */);
	}

	/**
	 * /** Get all transfers by FIID = ? and DueDate = DateToPost
	 * order by Compid and TransferDest. Note: if FIID column is
	 * null, get FIID of the customerId column. CompId is not in
	 * BPW_Transfer table, we need to link it to ETF_Company table
	 * by CustomerId. If customerId of BPW_Transfer table is null,
	 * we will use its FIID.
	 * SQL statement looks like:
	 * SELECT transfer, company.CompName, company.CompACHId FROM BPW_Transfer transfer,
	 * ETF_Company company WHERE DateToPost = ? AND
	 * ( (transfer.CustomerId is not null AND transfer.CustomerId = company.CustomerId)
	 * OR (transfer.CustomerId is null AND transfer.FIId is not null AND
	 * transfer.FIId = company.FIId) ORDER BY company.CopmId, transfer.TransferDest
	 *
	 * Get transfer which have status = WILLPROCESSON
	 * 
	 * @param dbh
	 * @param fIId
	 * @param batchSize
	 * @param category
	 * @return
	 * @throws FFSException
	 */
	@Override
	public ArrayList getUnProcessedTransfersForFIId(FFSConnectionHolder dbh,
			String fIId,
			int batchSize,
			String category) throws FFSException {

		return Transfer.getUnProcessedTransfersForFIId(dbh, fIId, batchSize, category, isSameDayEnabledForETF(), true /* SameDayETFTran */);
	}
	
	/**
	 * Gets the number of business days a prenote before the payee can be used.
	 * 
	 * @return prenoteBusinessDays
	 */
	public int getPrenoteBusinessDays() {
		int prenoteBusinessDays = DBConsts.DEFAULT_BPW_ACH_PAYEE_SAME_DAY_PRENOTE_BUSINESS_DAYS;
		try {
			prenoteBusinessDays = getPrenoteBusinessDays(DBConsts.BPW_ACH_PAYEE_SAME_DAY_PRENOTE_BUSINESS_DAYS, 
					DBConsts.DEFAULT_BPW_ACH_PAYEE_SAME_DAY_PRENOTE_BUSINESS_DAYS);
		} catch (Exception ex) {
			// Do nothing
		}
		return prenoteBusinessDays;
	}
	
	 /**
     * Get the number of external accounts whose date is matured
     *
     * @param dbh
     * @param matureDate The date to be matured
     * @return The array of ExtTransferAcctInfo objects
     * @exception FFSException
     */
    public  ExtTransferAcctInfo[] getMaturedExtTransferAcctInfo(FFSConnectionHolder dbh,
		        String fiId,
		        String formattedMatureDateStr) throws FFSException {

    	return ExternalTransferAccount.getMaturedExtTransferAcctInfo(dbh, fiId, formattedMatureDateStr, isSameDayEnabledForETF(), true /* SameDayETFTran */);
    }
    
    /**
     * Update matured ext account prenote status
     *
     * @param dbh
     * @return number of records updated
     * @exception FFSException
     */
    public int updateMaturedExtAcctPrenoteStatus(FFSConnectionHolder dbh,
                                                        String fiId,
                                                        String formattedMatureDateStr) throws FFSException {
    	
    	return ExternalTransferAccount.updateMaturedExtAcctPrenoteStatus(dbh, fiId, formattedMatureDateStr, isSameDayEnabledForETF(), true /* SameDayETFTran */);
    }
    
    /**
     *  Get all un-matured external accounts for the fiid. change status for each account to PENDING
     * 
     * @param dbh
     * @param fiId
     * @param excludeUnmanaged
     * @return
     * @throws FFSException
     */
    public ExtTransferAcctInfo[] getUnMaturedExtTransferAcctInfo(FFSConnectionHolder dbh,
            String fiId,
            boolean excludeUnmanaged) throws FFSException {
    	// include unmanaged accounts
    	return ExternalTransferAccount.getUnMaturedExtTransferAcctInfo(dbh, fiId, false, isSameDayEnabledForETF(), true /* SameDayETFTran */);
    }
    
    /**
     * Set Prenote transfer info - 0 = normal Prenote, 1= SameDay Prenote
     */
    public void setPrenoteTransferInfo(TransferInfo prenoteInfo) {
    	prenoteInfo.setSameDayTransfer(1); // 0 = normal Prenote, 1= SameDay Prenote
    }
}
