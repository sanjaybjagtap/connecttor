package com.sap.connector.server.framework.service.spring;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.beans.PropertyVetoException;
import java.sql.SQLException;

@Configuration
@ConfigurationProperties(prefix = "spring.datasource")
public class DataSourceConfigurer extends HikariConfig {

    @Bean
    public DataSource dataSource() {
       return new HikariDataSource(this);

        /*
        ComboPooledDataSource cpds = new ComboPooledDataSource();
        cpds.setDriverClass( this.getDriverClassName()); //loads the jdbc driver
        cpds.setJdbcUrl(this.getJdbcUrl());
        cpds.setUser(this.getUsername());
        cpds.setPassword(this.getPassword());
        cpds.setAutoCommitOnClose(false);
        cpds.setMinPoolSize(10);
        cpds.setInitialPoolSize(15);
        cpds.setMaxPoolSize(100);
        cpds.setAcquireRetryAttempts(10);
        cpds.setMaxStatements(100);
        cpds.setMaxStatementsPerConnection(20);
        cpds.setConnectionCustomizerClassName("com.sap.poc.webSocketDNF.framwork.service.spring.ConnectionCustomizer");
        return new P6DataSource(cpds);*/
    }

}
