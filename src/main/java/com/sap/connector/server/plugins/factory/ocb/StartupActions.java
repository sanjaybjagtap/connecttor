package com.sap.connector.server.plugins.factory.ocb;

import com.ffusion.beans.common.DataSourceProperties;
import com.ffusion.beans.util.StringUtil;
import com.ffusion.efs.adapters.entitlements.EntitlementProfileAdapterImpl;
import com.ffusion.efs.adapters.entitlements.exception.EntitlementException;
import com.ffusion.ffs.bpw.db.DBInstructionType;
import com.ffusion.ffs.bpw.db.DBUtil;
import com.ffusion.ffs.bpw.interfaces.InstructionType;
import com.ffusion.ffs.bpw.util.BPWRegistryUtil;
import com.ffusion.ffs.bpw.util.OSGIUtil;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.util.FFSRegistry;
import com.ffusion.util.ResourceUtil;
import com.ffusion.util.XMLTag;
import com.ffusion.util.XMLUtil;
import com.sap.banking.common.constants.ConfigConstants;
import com.sap.banking.configuration.services.beans.ConfigurationProperties;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.rmi.AlreadyBoundException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

@Component
public class StartupActions {

    @Autowired
    ApplicationContext applicationContext;
    Logger logger = LoggerFactory.getLogger(StartupActions.class);

    @Autowired
    DataSource dataSource;

    @Autowired
    OCBHelper ocbHelper;

    @Autowired
    @Resource(name = "datasourceConfigProperties")
    Map dataProps;

    @Autowired
    ConfigurationProperties commonProperties;

    @Autowired
    EntitlementProfileAdapterImpl entitlementProfileAdapter;

    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) throws Exception {
        logger.info("Performing bootstrap tasks for business framework");
        OSGIUtil.setApplicationContext(applicationContext);
        new com.ffusion.ffs.bpw.custimpl.OSGIUtil().setApplicationContext(applicationContext);
        commonProperties.updateConfiguration(getCommonProps(dataSource));
        initializeAuditLog();
        populateInstructionTypes();
        populateAccountTypes();
        initializeBusinessClasses();
        logger.info("Application context loaded successfully!");
    }

    private void populateInstructionTypes() throws FFSException, AlreadyBoundException {
        FFSConnectionHolder ffsConnectionHolder = new FFSConnectionHolder();
        ffsConnectionHolder.conn = DBUtil.getConnection();
        ArrayList list = DBInstructionType.readInstructionTypes(ffsConnectionHolder);
        ffsConnectionHolder.conn.commit();
        InstructionType[] instructionTypes = (InstructionType[])list.toArray(new InstructionType[list.size()]);
        BPWRegistryUtil.setInstructionTypes(instructionTypes);
        FFSRegistry.bind("GLOBAL_CN_FLAG",true);
        DBUtil.freeConnection(ffsConnectionHolder.conn);
    }

    private void populateAccountTypes() throws FFSException {
        ocbHelper.populateAndInitializeAccountTypes();
    }

    private Map<String,String> getCommonProps(DataSource dataSource) throws SQLException {
        Map<String,String> result = new HashMap<>();
        try(Connection connection = dataSource.getConnection()) {
            String key,value,defValue;
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("select property_name,property_value,default_value from ocb_config where module_name = 'com.sap.banking.common'");
            while(resultSet.next()) {
                key = resultSet.getString("property_name");
                value = resultSet.getString("property_value");
                defValue = resultSet.getString("default_value");
                if(!StringUtils.isEmpty(value)) {
                    result.put(key,value);
                } else {
                    result.put(key,defValue);
                }
            }
            resultSet.close();
            statement.close();
        }
        return result;
    }

    private void initializeAuditLog() throws Exception {
        HashMap settings = settings();
        String poolName = "BPWFFIPool";
        String dbType = (String)dataProps.get("dbType");
        String dbName = (String)dataProps.get("dbName");
        String user = (String)dataProps.get("dbUsername");

        DataSourceProperties dataSourceProperties = new DataSourceProperties(poolName, dbType, dbName, user);
        // Initialize all required elements
        Properties props = commonProperties.getPropertiesFromMap();
        try {
            com.ffusion.util.logging.AuditLog.initialize(dataSourceProperties, settings, props);
        } catch (Throwable auditException) {
            logger.error("AuditLog.initialize:" + auditException);
            throw auditException;
        }

    }

    private HashMap<String,String> settings() {
        InputStream is = null;
        HashMap settings = null;
        try {
            is = ResourceUtil.getFileContentsAsStream("common/csil.xml");
            int len = 0;
            int size = 0;

            ArrayList byteList;
            byte[] buf;
            for (byteList = new ArrayList(3); size != -1; len += size) {
                buf = new byte[1024];
                size = is.read(buf, 0, 1024);
                if (size == -1) {
                    break;
                }

                byteList.add(buf);
            }

            buf = new byte[len];
            int ofs = 0;

            for (Iterator iter = byteList.iterator(); iter.hasNext(); ofs += size) {
                byte[] temp = (byte[]) iter.next();
                if (len - ofs > 1024) {
                    size = 1024;
                } else {
                    size = len - ofs;
                }

                System.arraycopy(temp, 0, buf, ofs, size);
            }

            XMLTag tag = new XMLTag(true);
            tag.build(new String(buf, 0, len));
            settings = XMLUtil.tagToHashMap(tag);

            String bankIdentifer = commonProperties.get(ConfigConstants.BANK_IDENTIFIER_TYPE);
            if (StringUtil.isNotEmpty(bankIdentifer)) {
                settings.put(ConfigConstants.BANK_IDENTIFIER_DISPLAY_KEY, bankIdentifer);
            } else {
                settings.put(ConfigConstants.BANK_IDENTIFIER_DISPLAY_KEY, ConfigConstants.BANKID_DISPLAY_DEFAULT);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return settings;
    }

    private void initializeBusinessClasses() throws EntitlementException {
        logger.info("Initializing business classes");
        entitlementProfileAdapter.initialize();
    }
}
