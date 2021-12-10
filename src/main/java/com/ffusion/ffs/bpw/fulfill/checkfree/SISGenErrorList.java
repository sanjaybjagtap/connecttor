// Copyright (c) 2004 Financial Fusion, Inc.  All Rights Reserved.

package com.ffusion.ffs.bpw.fulfill.checkfree;

import java.util.ArrayList;

/**
 * Contains a list of all errors encountered during CheckFree
 * SIS file generation.
 */
public class SISGenErrorList {

    private ArrayList _subInfoErrors;           // 1000 records
    private ArrayList _bankAcctInfoErrors;      // 2010 records
    private ArrayList _payeeInfoErrors;         // 3000 records
    private ArrayList _payeeAcctInfoErrors;     // 3010 records
    private ArrayList _pmtInfoErrors;           // 4000/4010 records

    public SISGenErrorList()
    {
        // Create empty lists.
        _subInfoErrors = new ArrayList();
        _bankAcctInfoErrors = new ArrayList();
        _payeeInfoErrors = new ArrayList();
        _payeeAcctInfoErrors = new ArrayList();
        _pmtInfoErrors = new ArrayList();
    }// SISGenErrorHolder()

    public void addSubInfoError(SISGenError subError)
    {
        _subInfoErrors.add(subError);
    }

    public void addBankAcctInfoError(SISGenError bankAcctError)
    {
        _bankAcctInfoErrors.add(bankAcctError);
    }

    public void addPayeeInfoError(SISGenError payeeError)
    {
        _payeeInfoErrors.add(payeeError);
    }

    public void addPayeeAcctInfoError(SISGenError payeeAcctError)
    {
        _payeeAcctInfoErrors.add(payeeAcctError);
    }

    public void addPmtInfoError(SISGenError pmtError)
    {
        _pmtInfoErrors.add(pmtError);
    }

    public ArrayList getSubInfoErrors()
    {
        return _subInfoErrors;
    }

    public ArrayList getBankAcctInfoErrors()
    {
        return _bankAcctInfoErrors;
    }

    public ArrayList getPayeeInfoErrors()
    {
        return _payeeInfoErrors;
    }

    public ArrayList getPayeeAcctInfoErrors()
    {
        return _payeeAcctInfoErrors;
    }

    public ArrayList getPmtInfoErrors()
    {
        return _pmtInfoErrors;
    }

    public int getSubInfoErrorCount()
    {
        return _subInfoErrors.size();
    }

    public int getBankAcctInfoErrorCount()
    {
        return _bankAcctInfoErrors.size();
    }

    public int getPayeeInfoErrorCount()
    {
        return _payeeInfoErrors.size();
    }

    public int getPayeeAcctInfoErrorCount()
    {
        return _payeeAcctInfoErrors.size();
    }

    public int getPmtInfoErrorCount()
    {
        return _pmtInfoErrors.size();
    }

}
