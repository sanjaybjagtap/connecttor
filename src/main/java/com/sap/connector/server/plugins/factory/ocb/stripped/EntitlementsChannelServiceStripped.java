package com.sap.connector.server.plugins.factory.ocb.stripped;

import com.ffusion.beans.SecureUser;
import com.ffusion.csil.beans.entitlements.Channel;
import com.ffusion.csil.beans.entitlements.EntChannelOps;
import com.ffusion.csil.beans.entitlements.EntitlementGroupMember;
import com.ffusion.efs.adapters.entitlements.exception.EntitlementException;
import com.ffusion.efs.adapters.entitlements.interfaces.EntitlementProfileAdapter;
import com.ffusion.services.entitlements.interfaces.EntitlementsChannelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class EntitlementsChannelServiceStripped implements EntitlementsChannelService {

    @Autowired
    EntitlementProfileAdapter entitlementProfileAdapter;

    @Override
    public List<Channel> getMasterChannelList() throws EntitlementException {
        throw new RuntimeException("Not implemented yet!");
    }

    @Override
    public List<Channel> getEntitledChannels(int i) throws EntitlementException {
        throw new RuntimeException("Not implemented yet!");
    }

    @Override
    public List<Channel> getSubscribedChannels(SecureUser secureUser, int i, Map<String, Object> map) throws EntitlementException {
        throw new RuntimeException("Not implemented yet!");
    }

    @Override
    public int cleanUpChannelTrnInfo(long l) throws EntitlementException {
        throw new RuntimeException("Not implemented yet!");
    }

    @Override
    public EntChannelOps getChannelIdForTrn(EntitlementGroupMember entitlementGroupMember, String trnId, String opType) throws EntitlementException {
        return entitlementProfileAdapter.getChannelIdForTrn(entitlementGroupMember,trnId,opType);
    }

    @Override
    public void deleteChannelInfoForTrn(EntChannelOps entChannelOps) throws EntitlementException {
        throw new RuntimeException("Not implemented yet!");
    }

    @Override
    public void deleteChannelInfoForTrns(List<EntChannelOps> list) throws EntitlementException {
        throw new RuntimeException("Not implemented yet!");
    }

    @Override
    public int updateChannelInfoForTrn(EntChannelOps entChannelOps, String s) throws EntitlementException {
        throw new RuntimeException("Not implemented yet!");
    }

    @Override
    public void addChannelInfoForTrn(EntChannelOps entChannelOps) throws EntitlementException {
        throw new RuntimeException("Not implemented yet!");
    }

    @Override
    public EntChannelOps getChannelIdForTrn(String s, String s1, String s2) throws EntitlementException {
        throw new RuntimeException("Not implemented yet!");
    }

    @Override
    public int updatechannelInfoForBatch(EntChannelOps entChannelOps) throws EntitlementException {
        throw new RuntimeException("Not implemented yet!");
    }

    @Override
    public EntChannelOps getEntChannelOpsByTrnId(String s) throws EntitlementException {
        throw new RuntimeException("Not implemented yet!");
    }
}
