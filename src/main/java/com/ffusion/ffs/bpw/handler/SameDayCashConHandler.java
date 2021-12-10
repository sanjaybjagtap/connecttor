package com.ffusion.ffs.bpw.handler;


import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.sap.banking.common.interceptors.PerfLoggerUtil;

//=====================================================================
//
//This class contains a callback handler that is registered in the 
//InstructionType table.  The registered callback handler will be called
//by the Scheduling engine for the ACH files Processing.
//
//=====================================================================

public class SameDayCashConHandler extends CashConHandler {

	private static final long serialVersionUID = 1L;

	public SameDayCashConHandler() throws Exception {
		super();
	}
	
	
    /**
     * Check Valid Instruction Type.
     * 
     * @param instructionType
     * @return boolean value
     */
	@Override
    public boolean checkValidInstructionType (String instructionType) {
    	if (( instructionType != null ) &&
    			( instructionType.compareTo( SAMEDAYCASHCONTRN ) == 0 ) ) {
    		return true;
    	}
    	return false;
    }

    /**
     * SAMEDAYCASHCONTRN - same day cash con tran. (false - CASHCONTRN , true SAMEDAYCASHCONTRN)
     * 
     * @return boolean value
     */
	@Override
    public boolean isSameDayCashConTran() {
    	return true;
    }
	
    /**
     * Update all fiid's matured location prenote  status. - SAMEDAYCASHCONTRN
     * 
     * @param dbh
     * @param fiid   FIID
     * @exception Exception
     */
	@Override
    public void updateMaturedLocationPrenoteStatus(FFSConnectionHolder dbh,
                                                   String fiId) throws Exception {
    	updateMaturedLocationPrenoteStatus(dbh, fiId, isSameDayCashConEnabled(), isSameDayCashConTran());  // false - CASHCONTRN , true SAMEDAYCASHCONTRN)
    }
	
	
	/**
	 * Gets the number of business days a prenote before the payee can be used.
	 * 
	 * @return prenoteBusinessDays
	 */
	@Override
	public int getPrenoteBusinessDays() {
		String methodName = "SameDayCashConHandler.getPrenoteBusinessDays";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(methodName,start);
		int prenoteBusinessDays = DBConsts.DEFAULT_BPW_ACH_PAYEE_SAME_DAY_PRENOTE_BUSINESS_DAYS;
		try {
			prenoteBusinessDays = getPrenoteBusinessDays(DBConsts.BPW_ACH_PAYEE_SAME_DAY_PRENOTE_BUSINESS_DAYS, 
					DBConsts.DEFAULT_BPW_ACH_PAYEE_SAME_DAY_PRENOTE_BUSINESS_DAYS);
		} catch (Exception ex) {
			 PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
			// Do nothing
		}
		 PerfLoggerUtil.stopPerfLogging(methodName, start, uniqueIndex);
		return prenoteBusinessDays;
	}
}
