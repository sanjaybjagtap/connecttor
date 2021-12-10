package com.sap.connector.server.plugins.factory;

import com.ffusion.efs.adapters.entitlements.EntitlementAdapterImpl;
import com.ffusion.efs.adapters.entitlements.EntitlementProfileAdapterImpl;
import com.ffusion.efs.adapters.entitlements.exception.EntitlementException;
import com.ffusion.efs.adapters.entitlements.interfaces.EntitlementAdapter;
import com.sap.banking.common.bo.interfaces.CommonConfig;
import com.sap.banking.common.config.service.provider.CommonConfigServiceImpl;
import com.sap.banking.common.encryption.EncryptionManagerImpl;
import com.sap.banking.common.encryption.interfaces.EncryptionManager;
import com.sap.banking.common.provider.CommonConfigImpl;
import com.sap.banking.common.utils.ApplicationPropertyPlaceholderConfigurer;
import com.sap.banking.configuration.services.CommonConfigService;
import com.sap.banking.configuration.services.DatasourceConfigService;
import com.sap.banking.configuration.services.beans.ConfigurationProperties;
import com.sap.connector.server.framework.service.exception.ActionFailedException;
import com.sap.connector.server.plugins.factory.ocb.mock.DatasourceConfigServiceImpl;
import org.osgi.service.cm.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Configuration
public class OCBRootFactory {
    private final Logger logger = LoggerFactory.getLogger(OCBRootFactory.class);

    @Bean
    ApplicationPropertyPlaceholderConfigurer applicationPropertyPlaceholderConfigurer(EncryptionManager encryptionManager)
            throws ActionFailedException {

        Properties properties = new Properties();
        Resource [] mappingLocations = null;
        try (InputStream input = this.getClass().getClassLoader().getResourceAsStream("application.properties")) {
            properties.load(input);
            if(properties.size() == 0) {
                logger.warn("No properties loaded!, this can be classpath issue!");
            }
            ResourcePatternResolver patternResolver = new PathMatchingResourcePatternResolver();
            mappingLocations = patternResolver.getResources("classpath*:config/*.cfg");
        } catch (IOException ex) {
            logger.error("Config file resolution failed! \nStopping startup..");
            throw new ActionFailedException(ex.getMessage(),ex);
        }
        if(mappingLocations.length == 0) {
            logger.warn("No config files loaded!, this can be classpath issue!");
        } else {
            logger.info("Found "+mappingLocations.length+" config files, registering config context");
            Arrays.stream(mappingLocations).forEach(e->logger.info("Registering : "+e.getFilename()));
        }

        ApplicationPropertyPlaceholderConfigurer applicationPropertyPlaceholderConfigurer = new ApplicationPropertyPlaceholderConfigurer(encryptionManager);
        applicationPropertyPlaceholderConfigurer.setIgnoreUnresolvablePlaceholders(true);
        applicationPropertyPlaceholderConfigurer.setLocations(mappingLocations);

        return applicationPropertyPlaceholderConfigurer;
    }

    @Bean
    EncryptionManager encryptionManager() throws ConfigurationException, ActionFailedException {
        Properties properties1 = new Properties();
        try (InputStream input = this.getClass().getClassLoader().getResourceAsStream("config/com.sap.banking.common_encryption.cfg")) {
            /*if(input == null || input.available() == 0) {
                logger.error("Couldn't get encryption properties!");
            } else {
                input.reset();
            }*/
            properties1.load(input);
        } catch (IOException ex) {
            logger.error("Encryption manager couldn't be started!");
            throw new ActionFailedException(ex.getMessage(),ex);
        }

        EncryptionManager encryptionManager = new EncryptionManagerImpl();
        ((EncryptionManagerImpl)encryptionManager).updated((Dictionary) properties1);
        return encryptionManager;
    }

    @Bean
    public CommonConfig commonConfig() {
        CommonConfig commonConfig =new CommonConfigImpl();
        ConfigurationProperties configurationProperties = new ConfigurationProperties();
        return commonConfig;
    }

    @Bean("commonProperties")
    public ConfigurationProperties getConfigurationProperties() {
        ConfigurationProperties configurationProperties = new ConfigurationProperties();
        configurationProperties.setProperties(new HashMap<>());
        return configurationProperties;
    }

    @Bean("commonAdapterConfigProp")
    public ConfigurationProperties commonAdapterConfigProp() {
        ConfigurationProperties configurationProperties = new ConfigurationProperties();
        configurationProperties.setProperties(new HashMap<>());
        return configurationProperties;
    }

    @Bean("profileEntitlementAdaptor")
    public EntitlementProfileAdapterImpl entitlementProfileAdapter() throws EntitlementException {
        EntitlementProfileAdapterImpl entitlementProfileAdapter = new EntitlementProfileAdapterImpl();
        return entitlementProfileAdapter;
    }

    @Bean
    public DatasourceConfigService datasourceConfigService() {
        return new DatasourceConfigServiceImpl();
    }

    //@Bean
    public EntitlementAdapter entitlementAdapterImpl() {
        return new EntitlementAdapterImpl();
    }

    @Bean("ocbconfigPropConfig")
    public Properties ocbconfigPropConfig() {
       // ConfigurationProperties configurationProperties = new ConfigurationProperties();
        Properties configurationProperties = new Properties();
        return configurationProperties;
    }

    @Bean
    public CommonConfigService getCommonConfigService() {
        return new CommonConfigServiceImpl();
    }
}
