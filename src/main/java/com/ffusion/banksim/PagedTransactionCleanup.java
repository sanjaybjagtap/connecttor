//
// PagedTransactionCleanup.java
//
// Copyright (c) 2002 Financial Fusion, Inc.
// All Rights Reserved.
//

package com.ffusion.banksim;

import com.ffusion.banksim.db.*;

import java.awt.event.*;

// package level class to instantiate for the Timer object so that ununsed paged transactions
// get cleaned up
class PagedTransactionCleanup implements ActionListener
{
    public void actionPerformed( ActionEvent e )
    {
	DBTransaction.closeUnusedPagedTransactions();
    }
}
