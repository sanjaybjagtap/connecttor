package com.ffusion.ffs.bpw.fulfill.socialpayment;

import java.io.BufferedWriter;
import java.io.PrintWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.ffusion.ffs.bpw.interfaces.CustomerInfo;
import com.ffusion.ffs.bpw.interfaces.CustomerPayeeInfo;
import com.ffusion.ffs.bpw.interfaces.PayeeInfo;
import com.ffusion.ffs.bpw.interfaces.PayeeRouteInfo;
import com.ffusion.ffs.bpw.interfaces.PmtInfo;
import com.ffusion.ffs.bpw.interfaces.handlers.FulfillmentAPI;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.sap.banking.io.beans.FileWriter;
import com.sap.banking.io.provider.interfaces.FileHandlerProvider;


public class SocialPaymentHandler implements FulfillmentAPI
{
	protected Logger logger = LoggerFactory.getLogger(SocialPaymentHandler.class);

	@Autowired
    private FileHandlerProvider fileHandlerProvider;
	
    public SocialPaymentHandler()
    {
    }

    public void start() throws Exception{}
    public void shutdown() throws Exception{}

    public void addPayees(PayeeInfo[] payees, FFSConnectionHolder dbh )
    throws Exception
    {
        int i;
        logger.info("====================================================================================================");
        logger.info("  Begin of SocialPaymentHandler.addPayees  .....");
        logger.info("  SocialPaymentHandler addPayees will communicate with the fulfillment system in backend .");

        for (i=0; i<payees.length; i++)
        {
          logger.info("  ** PayeeInfo :  " + i);
          logger.info("       PayeeID    =   " + payees[i].PayeeID);
          logger.info("       PayeeName  =   " + payees[i].PayeeName);
          logger.info("       Addr1      =   " + payees[i]. Addr1);
          logger.info("       City       =   " + payees[i]. City);
          logger.info("       State      =   " + payees[i]. State);
          logger.info("       Zipcode    =   " + payees[i]. Zipcode);
          logger.info("       Status     =   " + payees[i]. Status);
        }

        logger.info("  End of SocialPaymentHandler.addPayees  .....");
        logger.info("====================================================================================================");

    }

    public void modPayees(PayeeInfo[] payees, FFSConnectionHolder dbh )throws Exception{}
    public void deletePayees(PayeeInfo[] payees, FFSConnectionHolder dbh )throws Exception{}
    public void startPayeeBatch( FFSConnectionHolder dbh ) throws Exception{}
    public void endPayeeBatch( FFSConnectionHolder dbh ) throws Exception{}

    public void startPmtBatch( FFSConnectionHolder dbh )
    throws Exception
    {
    }


    public void endPmtBatch( FFSConnectionHolder dbh )
    throws Exception
    {
    }


    public void startCustomerPayeeBatch( FFSConnectionHolder dbh )
    throws Exception
    {
    }


    public void endCustomerPayeeBatch( FFSConnectionHolder dbh )
    throws Exception
    {
    }

    public void startCustBatch( FFSConnectionHolder dbh ) throws Exception
    {
    }

    public void endCustBatch( FFSConnectionHolder dbh ) throws Exception
    {
    }


    public int addCustomers( CustomerInfo[] customers, FFSConnectionHolder dbh )
    throws Exception
    {
	return 0;
    }


    public int modifyCustomers( CustomerInfo[] customers, FFSConnectionHolder dbh )
    throws Exception
    {
	return 0;
    }


    public int deleteCustomers( CustomerInfo[] customers, FFSConnectionHolder dbh )
    throws Exception
    {
	return 0;
    }


    public void addCustomerPayees( CustomerPayeeInfo[] info, PayeeInfo[] payees, FFSConnectionHolder dbh )
    throws Exception
    {
        // Customer can implement this method to communicate with the fulfillment system in backend.
        int i;
        logger.info("====================================================================================================");
        logger.info("  Begin of SocialPaymentHandler.....");
        logger.info("  SocialPaymentHandler addCustomerPayees will communicate with the fulfillment system in backend .");

        logger.info( "  The length of customerpayees in customerpayee add requests: " + info.length );
        for (i=0; i<info.length; i++)
        {
          logger.info("  -- CustomerPayeeInfo :  " + i);
          logger.info("        CustomerID  =   " + info[i].CustomerID);
          logger.info("        PayeeID     =   " + info[i].PayeeID);
          logger.info("        PayeeListID =   " + info[i]. PayeeListID);
          logger.info("        PayAcct     =   " + info[i]. PayAcct);
          logger.info("        Status      =   " + info[i]. Status);
        }

        for (i=0; i<payees.length; i++)
        {
          logger.info("  ** PayeeInfo :  " + i);
          logger.info("       PayeeID    =   " + payees[i].PayeeID);
          logger.info("       PayeeName  =   " + payees[i].PayeeName);
          logger.info("       Addr1      =   " + payees[i]. Addr1);
          logger.info("       City       =   " + payees[i]. City);
          logger.info("       State      =   " + payees[i]. State);
          logger.info("       Zipcode    =   " + payees[i]. Zipcode);
          logger.info("       Status     =   " + payees[i]. Status);
        }

        logger.info("  SocialPaymentHandler addCustomerPayees will communicate with the fulfillment system in backend .");
        logger.info("  End of SocialPaymentHandler.....");
        logger.info("====================================================================================================");

    }

    public void modCustomerPayees( CustomerPayeeInfo[] info, PayeeInfo[] payees, FFSConnectionHolder dbh )
    throws Exception
    {
         // Customer can implement this method to call the fulfillment system in backend.
        int i;
        logger.info("====================================================================================================");
        logger.info("  Begin of SocialPaymentHandler.....");
        logger.info("  SocialPaymentHandler modCustomerPayees will communicate with the fulfillment system in backend .");

        logger.info( "  The length of customerpayees in customerpayee add requests: " + info.length );
        for (i=0; i<info.length; i++)
        {
          logger.info("  -- CustomerPayeeInfo :  " + i);
          logger.info("        CustomerID  =   " + info[i].CustomerID);
          logger.info("        PayeeID     =   " + info[i].PayeeID);
          logger.info("        PayeeListID =   " + info[i]. PayeeListID);
          logger.info("        PayAcct     =   " + info[i]. PayAcct);
          logger.info("        Status      =   " + info[i]. Status);
        }

        for (i=0; i<payees.length; i++)
        {
          logger.info("  ** PayeeInfo :  " + i);
          logger.info("       PayeeID    =   " + payees[i].PayeeID);
          logger.info("       PayeeName  =   " + payees[i].PayeeName);
          logger.info("       Addr1      =   " + payees[i]. Addr1);
          logger.info("       City       =   " + payees[i]. City);
          logger.info("       State      =   " + payees[i]. State);
          logger.info("       Zipcode    =   " + payees[i]. Zipcode);
          logger.info("       Status     =   " + payees[i]. Status);
        }

        logger.info("  SocialPaymentHandler modCustomerPayees will communicate with the fulfillment system in backend .");
        logger.info("  End of SocialPaymentHandler.....");
        logger.info("====================================================================================================");

    }

    public void deleteCustomerPayees( CustomerPayeeInfo[] info, PayeeInfo[] payees, FFSConnectionHolder dbh )
    throws Exception
    {
         // Customer can implement this method to call the fulfillment system in backend.
        int i;
        logger.info("====================================================================================================");
        logger.info("  Begin of SocialPaymentHandler.....");
        logger.info("  SocialPaymentHandler deleteCustomerPayees will communicate with the fulfillment system in backend .");

        logger.info( "  The length of customerpayees in customerpayee add requests: " + info.length );
        for (i=0; i<info.length; i++)
        {
          logger.info("  -- CustomerPayeeInfo :  " + i);
          logger.info("        CustomerID  =   " + info[i].CustomerID);
          logger.info("        PayeeID     =   " + info[i].PayeeID);
          logger.info("        PayeeListID =   " + info[i]. PayeeListID);
          logger.info("        PayAcct     =   " + info[i]. PayAcct);
          logger.info("        Status      =   " + info[i]. Status);
        }

        for (i=0; i<payees.length; i++)
        {
          logger.info("  ** PayeeInfo :  " + i);
          logger.info("       PayeeID    =   " + payees[i].PayeeID);
          logger.info("       PayeeName  =   " + payees[i].PayeeName);
          logger.info("       Addr1      =   " + payees[i]. Addr1);
          logger.info("       City       =   " + payees[i]. City);
          logger.info("       State      =   " + payees[i]. State);
          logger.info("       Zipcode    =   " + payees[i]. Zipcode);
          logger.info("       Status     =   " + payees[i]. Status);
        }

        logger.info("  SocialPaymentHandler deleteCustomerPayees will communicate with the fulfillment system in backend .");
        logger.info("  End of SocialPaymentHandler.....");
        logger.info("====================================================================================================");

    }




    public void addPayments( PmtInfo[] pmtinfo, PayeeRouteInfo[] routeinfo, FFSConnectionHolder dbh )
    throws Exception
    {
        int i;
        logger.info("====================================================================================================");
        logger.info("  Begin of SocialPaymentHandler.....");
        logger.info("  SocialPaymentHandler addPayments will communicate with the fulfillment system in backend .");

        logger.info( "  The length of payments: " + pmtinfo.length );
        try
        {
            // open a file and write the info to the file.
        	FileWriter fw = new FileWriter("SocialPayment Payments.out");
        	fw.setFileHandlerProvider(fileHandlerProvider);
            PrintWriter writer = new PrintWriter(new BufferedWriter(fw), true);


            for( i = 0; i < pmtinfo.length; i++ )
            {
//				java.util.Date date = new java.util.Date();

				// for the result trn schedule to make the callback
				// the following fields are required
                // QTS 765561: Memo field cannot be empty or the SamplePmtResult cannot find three fields
                // and doesn't process that payment.  Memo is used to store "ERROR" or "OK" to know if
                // the transaction failed or not.
                String myMemo = pmtinfo[i].Memo;
                if (myMemo == null || myMemo.trim().length() == 0)
                    myMemo = "OK";
				writer.println(pmtinfo[i].SrvrTID + "|" + pmtinfo[i].CustomerID + "|" + myMemo);
				
//				writer.println("  -- PaymentInfo :  " + i);
//				writer.println("       Date  = " + date);
//				writer.println("       Payment SrvrTID  =  " + pmtinfo[i].SrvrTID);
//				writer.println("       PayeeID          =  " + pmtinfo[i].PayeeID);
//				writer.println("       Payment amount   =  " + pmtinfo[i].getAmt());
//				writer.println("       Payment type     =  " + pmtinfo[i].PaymentType);
//				writer.println("  -- PayeeInfo :  " + i);
//				writer.println("       PayeeID          =  " + pmtinfo[i].payeeInfo.PayeeID);
//				writer.println("       Payee Name       =  " + pmtinfo[i].payeeInfo.PayeeName);
//				logger.info("      PayeeAcctID      =  " + routeinfo[i].AcctID);
//				logger.info("      PayeeAcctType    =  " + routeinfo[i].AcctType);

            }

			writer.close();

        } catch( Exception exc )
        {
            throw new Exception("Exception in building payment out file in  Social Payment Handler System." + exc.toString());
        }

        logger.info("  SocialPaymentHandler addPayments will communicate with the fulfillment system in backend .");
        logger.info("  End of SocialPaymentHandler.....");
        logger.info("====================================================================================================");


    }

}
