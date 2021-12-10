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


public class ON_USHandler implements FulfillmentAPI {
	@Autowired
    private FileHandlerProvider fileHandlerProvider;
	
    public ON_USHandler()
    {
    }

    public void start()
    throws Exception
    {
    }

    public void shutdown()
    throws Exception
    {
    }

    public void addPayees(PayeeInfo[] payees, FFSConnectionHolder dbh )
    throws Exception
    {
    }

    public void modPayees(PayeeInfo[] payees, FFSConnectionHolder dbh )
    throws Exception
    {
    }

    public void deletePayees(PayeeInfo[] payees, FFSConnectionHolder dbh )
    throws Exception
    {
    }

    public void startPayeeBatch( FFSConnectionHolder dbh )
    throws Exception
    {
    }

    public void endPayeeBatch( FFSConnectionHolder dbh )
    throws Exception
    {
    }

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

    public void startCustBatch( FFSConnectionHolder dbh )
    throws Exception
    {
    }

    public void endCustBatch( FFSConnectionHolder dbh )
    throws Exception
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
        // For ON_US payment we don't do anything about customerpayees in backend.
        System.out.println( "The length of customerpayees in customerpayee add requests: " + info.length );
    }

    public void modCustomerPayees( CustomerPayeeInfo[] info, PayeeInfo[] payees, FFSConnectionHolder dbh )
    throws Exception
    {
        // For ON_US payment we don't do anything about customerpayees in backend.
        System.out.println( "The length of customerpayees in customerpayee mod requests: " + info.length );
    }

    public void deleteCustomerPayees( CustomerPayeeInfo[] info, PayeeInfo[] payees, FFSConnectionHolder dbh )
    throws Exception
    {
        // For ON_US payment we don't do anything about customerpayees in backend.
        System.out.println( "The length of customerpayees in customerpayee cancel requests: " + info.length );
    }

    public void addPayments( PmtInfo[] pmtinfo, PayeeRouteInfo[] routeinfo, FFSConnectionHolder dbh )
    throws Exception
    {
        System.out.println( "The length of payments: " + pmtinfo.length );
        try {
            // open a file and write the info to the file.
        	FileWriter fw = new FileWriter("ON_USPayments.out");
        	fw.setFileHandlerProvider(fileHandlerProvider);
            PrintWriter writer = new PrintWriter(new BufferedWriter(fw), true);

            for (int i = 0; i < pmtinfo.length; i++ ) {
                java.util.Date date = new java.util.Date();
                writer.println( date
                                + "Payment SrvrTID = " + pmtinfo[i].SrvrTID
                                + ", PayeeID = " + pmtinfo[i].PayeeID
                                + ", Payment amount = " + pmtinfo[i].getAmt());
            }
            writer.close();

        } catch ( Exception exc ) {
            throw new Exception("Exception in building ON_US payment out file." + exc.toString());
        }
    }
}
