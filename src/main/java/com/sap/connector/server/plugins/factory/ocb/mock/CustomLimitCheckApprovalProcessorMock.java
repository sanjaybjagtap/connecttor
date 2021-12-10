package com.sap.connector.server.plugins.factory.ocb.mock;

import com.ffusion.csil.beans.entitlements.EntitlementGroupMember;
import com.ffusion.csil.beans.entitlements.Limits;
import com.ffusion.csil.beans.entitlements.MultiEntitlement;
import com.ffusion.ffs.bpw.db.BPWFI;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.interfaces.*;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.util.beans.PagingContext;

import java.util.Date;
import java.util.HashMap;

public class CustomLimitCheckApprovalProcessorMock implements com.ffusion.ffs.bpw.interfaces.CustomLimitCheckApprovalProcessor {
    private final HashMap _fiList = new HashMap();

    @Override
    public int processACHBatchAdd(FFSConnectionHolder ffsConnectionHolder, ACHBatchInfo achBatchInfo, HashMap hashMap) throws FFSException {
        return 6;
    }

    @Override
    public int processACHBatchDelete(FFSConnectionHolder ffsConnectionHolder, ACHBatchInfo achBatchInfo, HashMap hashMap) throws FFSException {
        return 6;
    }

    @Override
    public int processACHBatchReject(FFSConnectionHolder ffsConnectionHolder, ACHBatchInfo achBatchInfo, HashMap hashMap) throws FFSException {
        return 6;
    }

    @Override
    public int processCCEntryAdd(FFSConnectionHolder ffsConnectionHolder, CCEntryInfo ccEntryInfo, HashMap hashMap) throws FFSException {
        return 6;
    }

    @Override
    public int processCCEntryReject(FFSConnectionHolder ffsConnectionHolder, CCEntryInfo ccEntryInfo, HashMap hashMap) throws FFSException {
        return 6;
    }

    @Override
    public int processCCEntryDelete(FFSConnectionHolder ffsConnectionHolder, CCEntryInfo ccEntryInfo, HashMap hashMap) throws FFSException {
        return 6;
    }

    @Override
    public int processWireAdd(FFSConnectionHolder ffsConnectionHolder, WireInfo wireInfo, HashMap hashMap) throws FFSException {
        return 6;
    }

    @Override
    public int processWireReject(FFSConnectionHolder ffsConnectionHolder, WireInfo wireInfo, HashMap hashMap) throws FFSException {
        return 6;
    }

    @Override
    public int processWireDelete(FFSConnectionHolder ffsConnectionHolder, WireInfo wireInfo, HashMap hashMap) throws FFSException {
        return 6;
    }

    @Override
    public int processPmtAdd(FFSConnectionHolder ffsConnectionHolder, PmtInfo pmtInfo, HashMap hashMap) throws FFSException {
        return 6;
    }

    @Override
    public int processPmtReject(FFSConnectionHolder ffsConnectionHolder, PmtInfo pmtInfo, HashMap hashMap) throws FFSException {
        return 6;
    }

    @Override
    public int processPmtDelete(FFSConnectionHolder ffsConnectionHolder, PmtInfo pmtInfo, HashMap hashMap) throws FFSException {
        return 6;
    }

    @Override
    public boolean checkEntitlementACHBatch(ACHBatchInfo achBatchInfo, HashMap hashMap) throws FFSException {
        return false;
    }

    @Override
    public boolean checkEntitlementPmt(PmtInfo pmtInfo, HashMap hashMap) throws FFSException {
        return false;
    }

    @Override
    public boolean checkEntitlementIntra(IntraTrnInfo intraTrnInfo, HashMap hashMap) throws FFSException {
        return false;
    }

    @Override
    public boolean checkEntitlementWire(FFSConnectionHolder ffsConnectionHolder, WireInfo wireInfo, HashMap hashMap) throws FFSException {
        return false;
    }

    @Override
    public boolean checkEntitlementExtTrn(TransferInfo transferInfo, HashMap hashMap) throws FFSException {
        return false;
    }

    @Override
    public int processIntraTrnAdd(FFSConnectionHolder ffsConnectionHolder, IntraTrnInfo intraTrnInfo, HashMap hashMap) throws FFSException {
        return 6;
    }

    @Override
    public int processIntraTrnReject(FFSConnectionHolder ffsConnectionHolder, IntraTrnInfo intraTrnInfo, HashMap hashMap) throws FFSException {
        return 6;
    }

    @Override
    public int processIntraTrnDelete(FFSConnectionHolder ffsConnectionHolder, IntraTrnInfo intraTrnInfo, HashMap hashMap) throws FFSException {
        return 6;
    }

    @Override
    public int processExternalTransferAdd(FFSConnectionHolder ffsConnectionHolder, TransferInfo transferInfo, HashMap hashMap) throws FFSException {
        return 6;
    }

    @Override
    public int processExternalTransferReject(FFSConnectionHolder ffsConnectionHolder, TransferInfo transferInfo, HashMap hashMap) throws FFSException {
        return 6;
    }

    @Override
    public int processExternalTransferDelete(FFSConnectionHolder ffsConnectionHolder, TransferInfo transferInfo, HashMap hashMap) throws FFSException {
        return 6;
    }

    @Override
    public boolean checkEntitlementCCEntry(CCEntryInfo ccEntryInfo, String s, HashMap hashMap) throws FFSException {
        return false;
    }

    @Override
    public boolean checkEntitlementACHBatchViewing(ACHBatchInfo achBatchInfo, EntitlementGroupMember entitlementGroupMember, HashMap hashMap) throws FFSException {
        return false;
    }

    @Override
    public boolean checkEntitlementCCEntryViewing(CCEntryInfo ccEntryInfo, EntitlementGroupMember entitlementGroupMember, HashMap hashMap) throws FFSException {
        return false;
    }

    @Override
    public EntitlementGroupMember getEntitlementGroupMember(String s, String s1) throws FFSException {
        return null;
    }

    @Override
    public String mapToStatus(int i) {
        return null;
    }

    @Override
    public int doApproval(String s, BPWInfoBase bpwInfoBase, Limits limits, HashMap hashMap) {
        return 6;
    }

    @Override
    public int doRequiresApproval(String s, BPWInfoBase bpwInfoBase, HashMap hashMap) {
        return 6;
    }

    @Override
    public boolean canCreateRequiresApprovalItem(String s, BPWInfoBase bpwInfoBase, HashMap hashMap) {
        return false;
    }

    @Override
    public String getApprovalResult(String s, BPWInfoBase bpwInfoBase, HashMap hashMap) {
        return null;
    }

    @Override
    public void cancelApproval(String s, String s1, HashMap hashMap) {

    }

    @Override
    public String getEntitlementObjectId(String s, String s1) {
        return null;
    }

    @Override
    public Date getSmartDate(String s, String s1) {
        return null;
    }

    @Override
    public boolean checkRequiresApproval(EntitlementGroupMember entitlementGroupMember, String[] strings) throws FFSException {
        return false;
    }

    @Override
    public boolean checkRequiresApproval(int i, String[] strings) throws FFSException {
        return false;
    }

    @Override
    public String getRTN(String s) throws FFSException {
        return null;
    }

    @Override
    public void loadFIs() throws FFSException {
        FFSConnectionHolder dbh = new FFSConnectionHolder();
        dbh.conn = DBUtil.getConnection();
        if (dbh.conn == null) {
            throw new FFSException("CustomLimitCheckApprovalProcessor.getRTN: Can not get DB Connection.");
        } else {
            try {
                String[] ids = BPWFI.getAllBPWFIIds(dbh);
                if (ids != null) {
                    for(int i = 0; i < ids.length; ++i) {
                        BPWFIInfo fi = BPWFI.getBPWFIInfo(dbh, ids[i]);
                        if (fi != null) {
                            this._fiList.put(ids[i], fi);
                        }
                    }
                }

                dbh.conn.commit();
            } catch (Exception var8) {
                dbh.conn.rollback();
                String msg = "CustomLimitCheckApprovalProcessor.getRTN failed:  Error: " + FFSDebug.stackTrace(var8);
                FFSDebug.log(msg, 0);
            } finally {
                DBUtil.freeConnection(dbh.conn);
            }

        }
    }

    @Override
    public int getCustomerEntGroupId(String s) throws FFSException {
        return 6;
    }

    @Override
    public boolean shouldCheckExtTransferLimits(FFSConnectionHolder ffsConnectionHolder, TransferInfo transferInfo, HashMap hashMap, String s, boolean b) throws FFSException {
        return false;
    }

    @Override
    public boolean checkAccountEntitlement(FFSConnectionHolder ffsConnectionHolder, EntitlementGroupMember entitlementGroupMember, MultiEntitlement multiEntitlement, int i, HashMap hashMap) throws FFSException {
        return false;
    }

    @Override
    public EntitlementGroupMember getEntitlementGroupMember(PagingContext pagingContext) {
        return null;
    }

    @Override
    public boolean checkTransferAccountEntitlement(FFSConnectionHolder ffsConnectionHolder, PagingInfo pagingInfo, TransferInfo transferInfo, String s, String s1) {
        return false;
    }

    @Override
    public boolean checkTransferAccountEntitlement(PagingInfo pagingInfo, TransferInfo transferInfo, FFSConnectionHolder ffsConnectionHolder, String s, boolean b) {
        return false;
    }

    @Override
    public boolean checkBillpayAccountEntitlement(PagingInfo pagingInfo, FFSConnectionHolder ffsConnectionHolder, String s, String s1) {
        return false;
    }

    @Override
    public boolean checkWireAccountEntitlement(PagingInfo pagingInfo, WireHistoryInfo wireHistoryInfo, FFSConnectionHolder ffsConnectionHolder) {
        return false;
    }
}
