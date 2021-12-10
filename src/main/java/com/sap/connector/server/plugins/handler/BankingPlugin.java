package com.sap.connector.server.plugins.handler;

import com.ffusion.banksim.interfaces.RealTimeBankingBackend;
import com.ffusion.beans.SecureUser;
import com.ffusion.beans.accounts.Account;
import com.ffusion.beans.user.User;
import com.ffusion.csil.CSILException;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.beans.accounts.BankingEventWrapperBean;
import com.sap.banking.common.constants.ConfigConstants;
import com.sap.banking.common.exception.BankingException;
import com.sap.connector.server.framework.service.annotation.EventExecutor;
import com.sap.connector.server.framework.service.annotation.EventTarget;
import com.sap.connector.server.framework.service.beans.MetadataBean;
import com.sap.connector.server.framework.service.beans.SimpleMessageBean;
import com.sap.connector.server.framework.service.plugins.AbstractPlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

@Component
@EventExecutor(module = "banking")
public class BankingPlugin extends AbstractPlugin {

    /**
     *
     * Common Config BO
     */
    @Autowired
    private com.sap.banking.common.bo.interfaces.CommonConfig commonConfigBO;

    @Resource(name="realTimeBankingBackendServices")
    private List<RealTimeBankingBackend> bankingBackends;

    @EventTarget(event = "getAccounts")
    public List<Account> getAccounts(MetadataBean metadataBean, BankingEventWrapperBean bankingEventWrapperBean) throws Exception {

        this.sendNotificationResponse(metadataBean,
                new SimpleMessageBean("Starting Processing the instruction "+ bankingEventWrapperBean.getBackendUserId()));

        User customer = null;
        Enumeration e = null;
        RealTimeBankingBackend bankingBackend = null;
        List<Account> acctList = new ArrayList<Account>();
        try {
            bankingBackend = getBankingBackendService();
            SecureUser sUser = bankingEventWrapperBean.getSecureUser();
            Map<String, Object> extraParam = bankingEventWrapperBean.getExtraMap();
            customer = bankingBackend.signOn(sUser, bankingEventWrapperBean.getBackendUserId(), bankingEventWrapperBean.getBackendUserPassword(), extraParam);
            e = bankingBackend.getAccounts(sUser, customer, extraParam);

            while (e.hasMoreElements()) {
                Account account = (Account) e.nextElement();
                acctList.add(account);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        this.sendNotificationResponse(metadataBean, new SimpleMessageBean("Transfer is done!"));
        return acctList;
    }


    protected RealTimeBankingBackend getBankingBackendService() throws BankingException {
        RealTimeBankingBackend realTimeBankingBackend = null;

        // get backend type property from config_master

        String backendType = "";
        try {
            backendType = commonConfigBO.getBankingBackendType();
        } catch (Exception e) {
            e.printStackTrace();
            //throw new BankingException(CSILException.ERROR_LOADING_CONFIGURATION, "Error loading configuration - " + ConfigConstants.BANKING_BACKEND_TYPE  ,  e);
        }

        // iterate through the list of service refs and return ref based on configuration
        if(bankingBackends != null && !bankingBackends.isEmpty()) {
            Iterator<RealTimeBankingBackend> iteratorBankingBackend =  bankingBackends.iterator();
            while(iteratorBankingBackend.hasNext()) {
                RealTimeBankingBackend bankingBackendService = iteratorBankingBackend.next();
                if(backendType != null && backendType.equals(bankingBackendService.getBankingBackendType())) {
                    realTimeBankingBackend = bankingBackendService;
                    break;
                }
            }
        }

        if(realTimeBankingBackend == null) {
            System.out.println("Invalid backend type." + backendType);
            // throw new BankingException(ERROR_UNKNOWN, "Invalid backend type." + backendType);
        }
        return realTimeBankingBackend;
    }

}
