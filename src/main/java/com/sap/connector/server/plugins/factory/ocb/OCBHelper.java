package com.sap.connector.server.plugins.factory.ocb;

import com.ffusion.beans.wiretransfers.WireAccountMap;
import com.ffusion.util.db.DBUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Component
public class OCBHelper {
    Logger logger = LoggerFactory.getLogger(OCBHelper.class);
    @Value("${spring.datasource.poolName}")
    String poolName;

    public void populateAndInitializeAccountTypes() {
        try {
            HashMap acctMapToStr = new HashMap<Integer, String>();
            HashMap acctMapToInt = getAccountTypes(poolName);

            Iterator itr = acctMapToInt.entrySet().iterator();

            while(itr.hasNext()){
                Map.Entry<String, Integer> entry= (Map.Entry<String, Integer>)itr.next();

                acctMapToStr.put(entry.getValue(), entry.getKey());
            }

            WireAccountMap.init(acctMapToInt,acctMapToStr);
        } catch (Throwable e) {
            logger.error("FMLog.initialize:" + e);
            throw e;
        }
    }

    protected HashMap getAccountTypes(String poolName) {
        HashMap acctTypes = new HashMap();
        String GET_ACCOUNT_TYPES = "SELECT AccountTypeInt,AccountTypeString  FROM AccountTypesMapping  ";
        ResultSet rSet = null;
        Statement stmt = null;
        Connection conn = null;

        try {
            conn = DBUtil.getConnection("BPWFFIPool", true, 2);
            DatabaseMetaData metadata = conn.getMetaData();
            String dbProductName = metadata.getDatabaseProductName();
            boolean useScrollInsensitive = null == dbProductName || !dbProductName.equalsIgnoreCase("HDB");

            if (useScrollInsensitive) {
                stmt = conn.createStatement(1004, 1007);
            } else {
                stmt = conn.createStatement(1003, 1007);
            }

            rSet = stmt.executeQuery(GET_ACCOUNT_TYPES);

            while(rSet.next()) {
                acctTypes.put(rSet.getString(2), new Integer(rSet.getInt(1)));
            }
        } catch (Exception var18) {
            logger.error("AccountTypesMap.init() failed:" + var18);
        } finally {
            try {
                if (rSet != null) {
                    rSet.close();
                }

                if (stmt != null) {
                    stmt.close();
                }

                if (conn != null) {
                    conn.close();
                }
            } catch (Exception var17) {
                logger.error("AccountTypesMap.init() failed:" + var17);
            }

        }
        return acctTypes;
    }
}
