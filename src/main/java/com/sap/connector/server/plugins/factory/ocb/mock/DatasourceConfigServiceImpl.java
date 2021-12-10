package com.sap.connector.server.plugins.factory.ocb.mock;

import org.springframework.beans.factory.annotation.Value;


public class DatasourceConfigServiceImpl implements com.sap.banking.configuration.services.DatasourceConfigService{
    @Value("${db.type}")
    String dbType;

    @Override
    public String getDbDriverClass() {
        return null;
    }

    @Override
    public void setDbDriverClass(String s) {

    }

    @Override
    public String getDbJdbcUrl() {
        return null;
    }

    @Override
    public void setDbJdbcUrl(String s) {

    }

    @Override
    public String getDbUsername() {
        return null;
    }

    @Override
    public void setDbUsername(String s) {

    }

    @Override
    public String getDbPassword() {
        return null;
    }

    @Override
    public void setDbPassword(String s) {

    }

    @Override
    public String getDbName() {
        return null;
    }

    @Override
    public void setDbName(String s) {

    }

    @Override
    public String getDbHost() {
        return null;
    }

    @Override
    public void setDbHost(String s) {

    }

    @Override
    public String getDbPort() {
        return null;
    }

    @Override
    public void setDbPort(String s) {

    }

    @Override
    public String getBanksimMaxConnection() {
        return null;
    }

    @Override
    public void setBanksimMaxConnection(String s) {

    }

    @Override
    public String getBanksimDefaultConnection() {
        return null;
    }

    @Override
    public void setBanksimDefaultConnection(String s) {

    }

    @Override
    public String getBanksimDBType() {
        return null;
    }

    @Override
    public void setBanksimDBType(String s) {

    }

    @Override
    public String getDbType() {
        return dbType;
    }

    @Override
    public void setDbType(String s) {

    }

    @Override
    public String getEclipseLinkDBPlatform() {
        return null;
    }

    @Override
    public void setEclipseLinkDBPlatform(String s) {

    }
}
