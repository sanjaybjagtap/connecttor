package com.sap.connector.server.framework.service.plugins;

import com.sap.banking.connector.beans.enums.RemoteActionMessageIntention;
import com.sap.banking.connector.beans.enums.RemoteActionMessageStatus;
import com.sap.connector.server.framework.service.beans.ApplicationContants;
import com.sap.connector.server.framework.service.beans.MessageBean;
import com.sap.connector.server.framework.service.beans.MetadataBean;
import com.sap.connector.server.framework.service.exception.ActionFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * The Abstract class that provides base functionality for all the custom plug-ins.
 * All plug-ins <b>MUST</b> extend this class and use the supporting methods in this class
 * for sending responses and obtaining DB connections
 * I537791
 */
public abstract class AbstractPlugin {

    @Autowired
    protected SimpMessagingTemplate messagingTemplate;

    @Autowired
    protected DataSource dataSource;

    protected AbstractPlugin() {
    }

    protected AbstractPlugin(SimpMessagingTemplate simpMessagingTemplate) {
        messagingTemplate = simpMessagingTemplate;
    }

    public SimpMessagingTemplate getMessagingTemplate() {
        return messagingTemplate;
    }

    public void setMessagingTemplate(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendNotificationResponse(MetadataBean metadataBean, MessageBean messageBean) {
        messageBean.setMessageIntention(RemoteActionMessageIntention.NOTIFY_MESSAGE);
        messageBean.setStatus(RemoteActionMessageStatus.IN_PROGRESS);
        sendResponse(metadataBean,messageBean);
    }

    public void sendStatusResponse(MetadataBean metadataBean, MessageBean messageBean) {
        messageBean.setMessageIntention(RemoteActionMessageIntention.STATUS_UPDATE);
        messageBean.setStatus(RemoteActionMessageStatus.IN_PROGRESS);
        sendResponse(metadataBean,messageBean);
    }

    private void sendResponse(MetadataBean metadataBean, MessageBean messageBean) {
        messageBean.setTransactionId(metadataBean.getTransactionID());
        messageBean.setUserId(metadataBean.getUser());
        messageBean.setConnectionId(metadataBean.getConnectionId());
        messagingTemplate.convertAndSendToUser(metadataBean.getConnectionId(), ApplicationContants.OUTBOUND_QUEUE,messageBean);
    }

    /**
     * Get a database connection
     * @return
     * @throws ActionFailedException
     */
    protected Connection getDatabaseConnection() throws ActionFailedException {
        try {
            return dataSource.getConnection();
        } catch (SQLException throwable) {
            throw new ActionFailedException("couldn't get DB connection",throwable);
        }
    }

    /**
     * Return the connection
     * @param connection
     * @throws ActionFailedException
     */
    protected void returnConnection(Connection connection) throws ActionFailedException {
        try {
            connection.close();
        } catch (SQLException throwable) {
            throw new ActionFailedException("couldn't close DB connection",throwable);
        }
    }
}
