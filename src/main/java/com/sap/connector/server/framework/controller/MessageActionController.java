package com.sap.connector.server.framework.controller;

import com.sap.banking.connector.beans.enums.RemoteActionMessageIntention;
import com.sap.banking.connector.beans.enums.RemoteActionMessageStatus;
import com.sap.connector.server.framework.service.beans.*;
import com.sap.connector.server.framework.service.interfaces.MessageActionService;
import com.sap.connector.server.framework.service.queues.MessageQueue;
import com.sap.connector.server.framework.service.threads.InboundMessageWatcher;
import com.sap.connector.server.framework.service.threads.OutboundMessageWatcher;
import com.sap.connector.server.framework.service.utils.PluginUtils;
import com.sap.connector.server.framework.service.plugins.AbstractPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Controller class that deals with handling and processing incoming and outgoing messages
 */
public class MessageActionController implements MessageActionService {

    Logger logger = LoggerFactory.getLogger(MessageActionController.class);

    @Autowired
    @Qualifier("inboundQueue")
    private final MessageQueue inboundMessageQueue;

    @Autowired
    @Qualifier("outboundQueue")
    private final MessageQueue outboundMessageQueue;

    @Autowired
    @Qualifier("inboundWatcherPool")
    ExecutorService inboundThreadPool;

    @Autowired
    @Qualifier("outboundWatcherPool")
    ExecutorService outboundThreadPool;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    PluginUtils pluginUtils;

    @Autowired
    @Qualifier("txnStatusMap")
    Map<String,TransactionStatusBean> statusBeanMap;

    @Value("${app.inbound.maxthreadpoolsize}")
    String inboundPoolSize;

    @Value("${app.outbound.maxthreadpoolsize}")
    String outboundPoolSize;

    /**
     * Constructor
     * @param inboundThreadPool
     * @param outboundThreadPool
     * @param inboundMessageQueue
     * @param outboundMessageQueue
     * @param inboundPoolSize
     * @param outboundPoolSize
     */
    public MessageActionController(ExecutorService inboundThreadPool,
                                   ExecutorService outboundThreadPool,
                                   MessageQueue inboundMessageQueue,
                                   MessageQueue outboundMessageQueue,
                                   String inboundPoolSize,
                                   String outboundPoolSize) {
        this.inboundThreadPool = inboundThreadPool;
        this.inboundMessageQueue = inboundMessageQueue;
        this.outboundMessageQueue = outboundMessageQueue;
        this.outboundThreadPool = outboundThreadPool;
        this.inboundPoolSize = inboundPoolSize;
        this.outboundPoolSize = outboundPoolSize;
    }

    /**
     * Add incoming message for processing
     * @param message
     * @return
     */
    public boolean addToInboundMessageQueue(Serializable message) {
        if(message instanceof RemoteActionMessageBean) {
            statusBeanMap.put(((RemoteActionMessageBean) message).getTransactionId().toString(),
                    populateTransactionBean((RemoteActionMessageBean) message));
        } else {
            logger.warn("Message is not instance of RemoteActionMessageBean, transaction logging will not work");
        }
        return inboundMessageQueue.addMessage(message);
    }

    /**
     * Retrieve a message in the inbound queue for processing
     * @return
     * @throws InterruptedException
     */
    public Serializable getFromInboundMessageQueue() throws InterruptedException {
        return inboundMessageQueue.getMessage();
    }

    /**
     * Add a response message to the outbound queue
     * @param message
     * @return
     */
    public boolean addToOutboundMessageQueue(Serializable message) {
        return outboundMessageQueue.addMessage(message);
    }

    /**
     * Get a message in the outbound queue for transmission
     * @return
     * @throws InterruptedException
     */
    public Serializable getFromOutboundMessageQueue() throws InterruptedException {
        return outboundMessageQueue.getMessage();
    }

    /**
     * Does the actual processing of a message
     * Identify the handler, map the arguments to target types and execute the handler
     * @param wrapperMessageBean
     */
    public void processMessage(MessageBean wrapperMessageBean) {

        RemoteActionMessageBean remoteActionMessageBean = (RemoteActionMessageBean)wrapperMessageBean;

        TransactionStatusBean transactionStatusBean = statusBeanMap.get(remoteActionMessageBean.getTransactionId().toString());
        if(transactionStatusBean == null) {
            transactionStatusBean = populateTransactionBean(remoteActionMessageBean);
        }
        transactionStatusBean.setStatus("STARTED");
        statusBeanMap.put(remoteActionMessageBean.getTransactionId().toString(),transactionStatusBean);


        logger.info("Processing message with ID "+remoteActionMessageBean.getTransactionId());
        Serializable returnVal = null;
        EventTargetBean eventTargetBean = null;

            try {
                eventTargetBean = pluginUtils.getTargetForEvent(remoteActionMessageBean);

                Method targetMethod = eventTargetBean.getMethod();
                Serializable message = eventTargetBean.getMessageObject();
                AbstractPlugin abstractPlugin = eventTargetBean.getPluginObject();

                MetadataBean metadataBean = new MetadataBean();
                metadataBean.setUser(remoteActionMessageBean.getUserId());
                metadataBean.setTransactionID(remoteActionMessageBean.getTransactionId());
                metadataBean.setConnectionId(remoteActionMessageBean.getConnectionId());

                returnVal = (Serializable) targetMethod.invoke(abstractPlugin, metadataBean, message);
                remoteActionMessageBean.setMessage(returnVal);
                remoteActionMessageBean.setMessageIntention(RemoteActionMessageIntention.FINISH);
                remoteActionMessageBean.setStatus(RemoteActionMessageStatus.COMPLETED);
            } catch (Exception e) {
                logger.error("Exception occurred during request processing for TxN id "+remoteActionMessageBean.getTransactionId());
                logger.error(e.getMessage(),e);
                transactionStatusBean.setExceptionStack(pluginUtils.populateStacktraceForTracking(e));
                remoteActionMessageBean.withMessage(new SimpleMessageBean(e.toString()));
                remoteActionMessageBean.setMessageIntention(RemoteActionMessageIntention.FINISH);
                remoteActionMessageBean.setStatus(RemoteActionMessageStatus.FAILED);
            }
        logger.info("Finished processing message with ID "+remoteActionMessageBean.getTransactionId());
        this.addToOutboundMessageQueue(remoteActionMessageBean);

        //Time logging
        transactionStatusBean.setStatus("FINISHED");
        transactionStatusBean.setProcessingCompleteTime(LocalDateTime.now());
        Duration duration = Duration.between(transactionStatusBean.getRecievedTime(),transactionStatusBean.getProcessingCompleteTime());
        transactionStatusBean.setTimeTookForProcessing(duration.toMillis());
    }

    /**
     * Shouldn't be called directly
     * @param serializable
     */
    public void sendMessage(Serializable serializable) {
        MessageBean messageBean = (MessageBean) serializable;
        messagingTemplate.convertAndSendToUser(messageBean.getConnectionId(),ApplicationContants.OUTBOUND_QUEUE, messageBean);
    }

    /**
     * Start the inbound and outbound watcher threads
     * @return
     */
    public boolean prepareWatcherThreads() {
        Runnable incomingWatcher = null;
        logger.info("Preparing inbound watchers!");
        try {
            for (int i = 0; i < Integer.parseInt(inboundPoolSize); i++) {
                incomingWatcher = new InboundMessageWatcher(this);
                inboundThreadPool.submit(incomingWatcher);
            }
        } catch (Exception e) {
            logger.error("inbound thread pool starting failed",e);
        }
        try {
            Runnable outgoingWatcher = null;
            logger.info("Preparing outbound watchers!");
            for (int i = 0; i < Integer.parseInt(outboundPoolSize); i++) {
                outgoingWatcher = new OutboundMessageWatcher(this);
                outboundThreadPool.submit(outgoingWatcher);
            }
        } catch (Exception e) {
            logger.error("outbound thread pool starting failed",e);
        }
        return false;
    }

    /**
     * populate Txn history bean for diagnostics
     * @param remoteActionMessageBean
     * @return
     */
    private TransactionStatusBean populateTransactionBean(RemoteActionMessageBean remoteActionMessageBean) {
        TransactionStatusBean transactionStatusBean = new TransactionStatusBean();
        transactionStatusBean.setSourceServer(remoteActionMessageBean.getOriginServer());
        transactionStatusBean.setRemoteActionMessageBean(remoteActionMessageBean);
        transactionStatusBean.setTxnId(remoteActionMessageBean.getTransactionId());
        transactionStatusBean.setStatus("RECEIVED");
        transactionStatusBean.setInbound(remoteActionMessageBean.getMessage());
        transactionStatusBean.setRecievedTime(LocalDateTime.now());
        return transactionStatusBean;
    }

}
