package com.ffusion.ffs.bpw.fulfill.handler;

import java.io.BufferedWriter;
import java.io.PrintWriter;

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


public class SampleFulfillmentHandler implements FulfillmentAPI
{
	@Autowired
    private FileHandlerProvider fileHandlerProvider;
	
    public SampleFulfillmentHandler()
    {
    }

    public void start() throws Exception{}
    public void shutdown() throws Exception{}

    public void addPayees(PayeeInfo[] payees, FFSConnectionHolder dbh )
    throws Exception
    {
        int i;
        System.out.println("====================================================================================================");
        System.out.println("  Begin of SampleFulfillmentHandler.addPayees  .....");
        System.out.println("  SampleFulfillmentHandler addPayees will communicate with the fulfillment system in backend .");

        for (i=0; i<payees.length; i++)
        {
          System.out.println("  ** PayeeInfo :  " + i);
          System.out.println("       PayeeID    =   " + payees[i].PayeeID);
          System.out.println("       PayeeName  =   " + payees[i].PayeeName);
          System.out.println("       Addr1      =   " + payees[i]. Addr1);
          System.out.println("       City       =   " + payees[i]. City);
          System.out.println("       State      =   " + payees[i]. State);
          System.out.println("       Zipcode    =   " + payees[i]. Zipcode);
          System.out.println("       Status     =   " + payees[i]. Status);
        }

        System.out.println("  End of SampleFulfillmentHandler.addPayees  .....");
        System.out.println("====================================================================================================");

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
        System.out.println("====================================================================================================");
        System.out.println("  Begin of SampleFulfillmentHandler.....");
        System.out.println("  SampleFulfillmentHandler addCustomerPayees will communicate with the fulfillment system in backend .");

        System.out.println( "  The length of customerpayees in customerpayee add requests: " + info.length );
        for (i=0; i<info.length; i++)
        {
          System.out.println("  -- CustomerPayeeInfo :  " + i);
          System.out.println("        CustomerID  =   " + info[i].CustomerID);
          System.out.println("        PayeeID     =   " + info[i].PayeeID);
          System.out.println("        PayeeListID =   " + info[i]. PayeeListID);
          System.out.println("        PayAcct     =   " + info[i]. PayAcct);
          System.out.println("        Status      =   " + info[i]. Status);
        }

        for (i=0; i<payees.length; i++)
        {
          System.out.println("  ** PayeeInfo :  " + i);
          System.out.println("       PayeeID    =   " + payees[i].PayeeID);
          System.out.println("       PayeeName  =   " + payees[i].PayeeName);
          System.out.println("       Addr1      =   " + payees[i]. Addr1);
          System.out.println("       City       =   " + payees[i]. City);
          System.out.println("       State      =   " + payees[i]. State);
          System.out.println("       Zipcode    =   " + payees[i]. Zipcode);
          System.out.println("       Status     =   " + payees[i]. Status);
        }

        System.out.println("  SampleFulfillmentHandler addCustomerPayees will communicate with the fulfillment system in backend .");
        System.out.println("  End of SampleFulfillmentHandler.....");
        System.out.println("====================================================================================================");

    }

    public void modCustomerPayees( CustomerPayeeInfo[] info, PayeeInfo[] payees, FFSConnectionHolder dbh )
    throws Exception
    {
         // Customer can implement this method to call the fulfillment system in backend.
        int i;
        System.out.println("====================================================================================================");
        System.out.println("  Begin of SampleFulfillmentHandler.....");
        System.out.println("  SampleFulfillmentHandler modCustomerPayees will communicate with the fulfillment system in backend .");

        System.out.println( "  The length of customerpayees in customerpayee add requests: " + info.length );
        for (i=0; i<info.length; i++)
        {
          System.out.println("  -- CustomerPayeeInfo :  " + i);
          System.out.println("        CustomerID  =   " + info[i].CustomerID);
          System.out.println("        PayeeID     =   " + info[i].PayeeID);
          System.out.println("        PayeeListID =   " + info[i]. PayeeListID);
          System.out.println("        PayAcct     =   " + info[i]. PayAcct);
          System.out.println("        Status      =   " + info[i]. Status);
        }

        for (i=0; i<payees.length; i++)
        {
          System.out.println("  ** PayeeInfo :  " + i);
          System.out.println("       PayeeID    =   " + payees[i].PayeeID);
          System.out.println("       PayeeName  =   " + payees[i].PayeeName);
          System.out.println("       Addr1      =   " + payees[i]. Addr1);
          System.out.println("       City       =   " + payees[i]. City);
          System.out.println("       State      =   " + payees[i]. State);
          System.out.println("       Zipcode    =   " + payees[i]. Zipcode);
          System.out.println("       Status     =   " + payees[i]. Status);
        }

        System.out.println("  SampleFulfillmentHandler modCustomerPayees will communicate with the fulfillment system in backend .");
        System.out.println("  End of SampleFulfillmentHandler.....");
        System.out.println("====================================================================================================");

    }

    public void deleteCustomerPayees( CustomerPayeeInfo[] info, PayeeInfo[] payees, FFSConnectionHolder dbh )
    throws Exception
    {
         // Customer can implement this method to call the fulfillment system in backend.
        int i;
        System.out.println("====================================================================================================");
        System.out.println("  Begin of SampleFulfillmentHandler.....");
        System.out.println("  SampleFulfillmentHandler deleteCustomerPayees will communicate with the fulfillment system in backend .");

        System.out.println( "  The length of customerpayees in customerpayee add requests: " + info.length );
        for (i=0; i<info.length; i++)
        {
          System.out.println("  -- CustomerPayeeInfo :  " + i);
          System.out.println("        CustomerID  =   " + info[i].CustomerID);
          System.out.println("        PayeeID     =   " + info[i].PayeeID);
          System.out.println("        PayeeListID =   " + info[i]. PayeeListID);
          System.out.println("        PayAcct     =   " + info[i]. PayAcct);
          System.out.println("        Status      =   " + info[i]. Status);
        }

        for (i=0; i<payees.length; i++)
        {
          System.out.println("  ** PayeeInfo :  " + i);
          System.out.println("       PayeeID    =   " + payees[i].PayeeID);
          System.out.println("       PayeeName  =   " + payees[i].PayeeName);
          System.out.println("       Addr1      =   " + payees[i]. Addr1);
          System.out.println("       City       =   " + payees[i]. City);
          System.out.println("       State      =   " + payees[i]. State);
          System.out.println("       Zipcode    =   " + payees[i]. Zipcode);
          System.out.println("       Status     =   " + payees[i]. Status);
        }

        System.out.println("  SampleFulfillmentHandler deleteCustomerPayees will communicate with the fulfillment system in backend .");
        System.out.println("  End of SampleFulfillmentHandler.....");
        System.out.println("====================================================================================================");

    }




    public void addPayments( PmtInfo[] pmtinfo, PayeeRouteInfo[] routeinfo, FFSConnectionHolder dbh )
    throws Exception
    {
        int i;
        System.out.println("====================================================================================================");
        System.out.println("  Begin of SampleFulfillmentHandler.....");
        System.out.println("  SampleFulfillmentHandler addPayments will communicate with the fulfillment system in backend .");

        System.out.println( "  The length of payments: " + pmtinfo.length );
        try
        {
            // open a file and write the info to the file.
        	FileWriter fw = new FileWriter("SampleFulfillment Payments.out");
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
//				System.out.println("      PayeeAcctID      =  " + routeinfo[i].AcctID);
//				System.out.println("      PayeeAcctType    =  " + routeinfo[i].AcctType);

            }

			writer.close();

        } catch( Exception exc )
        {
            throw new Exception("Exception in building payment out file in  Sample Fulfillment System." + exc.toString());
        }

        System.out.println("  SampleFulfillmentHandler addPayments will communicate with the fulfillment system in backend .");
        System.out.println("  End of SampleFulfillmentHandler.....");
        System.out.println("====================================================================================================");


    }

}
