//
// BSDsbSummary.java
//
// Copyright (c) 2002 Financial Fusion, Inc.
// All Rights Reserved.
//

package com.ffusion.banksim.adapter;


import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;

import com.ffusion.banksim.db.DBConnection;
import com.ffusion.banksim.db.util.BSUtil;
import com.ffusion.banksim.interfaces.BSConstants;
import com.ffusion.banksim.interfaces.BSException;
import com.ffusion.ffs.bpw.handler.OSGIUtil;
import com.ffusion.beans.DateTime;
import com.ffusion.beans.SecureUser;
import com.ffusion.beans.common.Currency;
import com.ffusion.beans.disbursement.DisbursementAccount;
import com.ffusion.beans.disbursement.DisbursementAccounts;
import com.ffusion.beans.disbursement.DisbursementPresentmentSummaries;
import com.ffusion.beans.disbursement.DisbursementPresentmentSummary;
import com.ffusion.beans.disbursement.DisbursementSummaries;
import com.ffusion.beans.disbursement.DisbursementSummary;
import com.ffusion.csil.CSILException;
import com.ffusion.util.db.ConnectionDefines;
import com.ffusion.util.db.DBUtil;



public class BSDsbSummary {
	
    private static final String SQL_ADD_DSSUMMARY = "INSERT INTO BS_DsbSummary( AccountID, DataDate, DataSource, NumItemsPending, TotalDebits, TotalCredits, " +
    						"TotalDTCCredits, ImmedFundsNeeded, OneDayFundsNeeded, TwoDayFundsNeeded, ValueDateTime, ChecksPaidEarly, ChecksPaidLate, " +
						"ChecksPaidLast, FedEstimate, LateDebits, ExtendABeanXMLID, Extra, BAIFileIdentifier ) VALUES( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPD_DSSUMMARY = "UPDATE BS_DsbSummary SET AccountID=?, DataDate=?, DataSource=?, NumItemsPending=?, TotalDebits=?, TotalCredits=?, " +
    						"TotalDTCCredits=?, ImmedFundsNeeded=?, OneDayFundsNeeded=?, TwoDayFundsNeeded=?, ValueDateTime=?, ChecksPaidEarly=?, ChecksPaidLate=?, " +
						"ChecksPaidLast=?, FedEstimate=?, LateDebits=?, BAIFileIdentifier=? " +
						"WHERE AccountID=? AND DataDate=? ";

    private static String SQL_GET_DSSUMMARIES1 = "SELECT b.DataDate, b.NumItemsPending, b.TotalDebits, b.TotalCredits, b.TotalDTCCredits, b.ImmedFundsNeeded, b.OneDayFundsNeeded, " +
    						"b.TwoDayFundsNeeded, b.ValueDateTime, b.ChecksPaidEarly, b.ChecksPaidLate, b.ChecksPaidLast, b.FedEstimate, b.LateDebits, b.ExtendABeanXMLID " +
						"FROM BS_Account a, BS_DsbSummary b WHERE a.AccountID=b.AccountID AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate>=? AND b.DataDate<=? ORDER by b.DataDate";

    private static String SQL_GET_DSSUMMARIES2 = "SELECT b.DataDate, b.NumItemsPending, b.TotalDebits, b.TotalCredits, b.TotalDTCCredits, b.ImmedFundsNeeded, b.OneDayFundsNeeded, " +
    						"b.TwoDayFundsNeeded, b.ValueDateTime, b.ChecksPaidEarly, b.ChecksPaidLate, b.ChecksPaidLast, b.FedEstimate, b.LateDebits, b.ExtendABeanXMLID " +
						"FROM BS_Account a, BS_DsbSummary b WHERE a.AccountID=b.AccountID AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate>=? ORDER by b.DataDate";

    private static String SQL_GET_DSSUMMARIES3 = "SELECT b.DataDate, b.NumItemsPending, b.TotalDebits, b.TotalCredits, b.TotalDTCCredits, b.ImmedFundsNeeded, b.OneDayFundsNeeded, " +
    						"b.TwoDayFundsNeeded, b.ValueDateTime, b.ChecksPaidEarly, b.ChecksPaidLate, b.ChecksPaidLast, b.FedEstimate, b.LateDebits, b.ExtendABeanXMLID " +
						"FROM BS_Account a, BS_DsbSummary b WHERE a.AccountID=b.AccountID AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate<=? ORDER by b.DataDate";

    private static String SQL_GET_DSSUMMARIES4 = "SELECT b.DataDate, b.NumItemsPending, b.TotalDebits, b.TotalCredits, b.TotalDTCCredits, b.ImmedFundsNeeded, b.OneDayFundsNeeded, " +
    						"b.TwoDayFundsNeeded, b.ValueDateTime, b.ChecksPaidEarly, b.ChecksPaidLate, b.ChecksPaidLast, b.FedEstimate, b.LateDebits, b.ExtendABeanXMLID " +
						"FROM BS_Account a, BS_DsbSummary b WHERE a.AccountID=b.AccountID AND a.AccountID=? AND a.RoutingNum=? ORDER by b.DataDate";

    private static String SQL_GET_DSSUMMARIES5 = "SELECT b.DataDate, b.NumItemsPending, b.TotalDebits, b.TotalCredits, b.TotalDTCCredits, b.ImmedFundsNeeded, b.OneDayFundsNeeded, " +
    						"b.TwoDayFundsNeeded, b.ValueDateTime, b.ChecksPaidEarly, b.ChecksPaidLate, b.ChecksPaidLast, b.FedEstimate, b.LateDebits, b.ExtendABeanXMLID " +
						"FROM BS_Account a, BS_DsbSummary b WHERE a.AccountID=b.AccountID AND a.AccountID=? AND a.RoutingNum=? AND b.DataDate=?";

    // the following queries retrieve disbursement Presentment summaries  - if any of these change
    // also review corresponding ASE strings

    private static String SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_ONE =
    	"SELECT a.presentment, b.presentment, c.presentment, a.count1, b.count2, c.count3, a.credits, b.debits " +
    	"FROM (SELECT presentment, count(*) AS count1, sum(amount) AS credits FROM bs_dsbtransactions WHERE amount >= 0 ";

    private static String SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_TWO =
    	" GROUP BY presentment) a FULL OUTER JOIN (select presentment, count(*) AS count2, sum(amount) AS " +
    	"debits FROM bs_dsbtransactions WHERE amount < 0 ";

    private static String SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_THREE =
    	" GROUP BY presentment) b ON a.presentment = b.presentment FULL OUTER JOIN (select presentment, " +
    	" count(*) AS count3 FROM bs_dsbtransactions WHERE amount IS null ";

    private static String SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_FOUR =
	" GROUP BY presentment) c ON a.presentment = c.presentment OR b.presentment = c.presentment ";
    
    private static String SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_FOUR_POSTGRESQL =
    		" GROUP BY presentment) c ON a.presentment = c.presentment OR b.presentment = c.presentment ";

    private static String SQL_GET_DS_PRESENTMENT_SUMMARIES_DATE_CLAUSE_BOTH = " AND DataDate >= ? AND DataDate <= ? ";
    private static String SQL_GET_DS_PRESENTMENT_SUMMARIES_DATE_CLAUSE_START = " AND DataDate >= ? ";
    private static String SQL_GET_DS_PRESENTMENT_SUMMARIES_DATE_CLAUSE_END = " AND DataDate <= ? ";
    
    
    // the following queries retrieve disbursement presentment summaries for ASE

    private static String SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_ONE_ASE =
	"SELECT Presentment, Presentment, Presentment, sum(count1) AS count1, sum(count2) AS count2, sum(count3) AS count3, " +
	"sum(credits) AS credits, sum(debits) AS debits  FROM " +
	"(SELECT Presentment, count(*) AS count1, 0 AS count2, 0 AS count3, sum(Amount) AS credits, 0 AS debits " +
	"FROM BS_DsbTransactions WHERE Amount >= 0 ";

    private static String SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_TWO_ASE =
	"GROUP BY Presentment UNION ALL " +
	"select Presentment, 0 AS count1, count(*) AS count2, 0 AS count3, 0 AS credits,  sum(Amount) AS debits " +
	"FROM BS_DsbTransactions WHERE Amount < 0 ";

    private static String SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_THREE_ASE =
	"GROUP BY Presentment UNION ALL " +
	"select Presentment, 0 AS count1, 0 AS count2, count(*) AS count3, 0 AS credits,  0 AS debits " +
	"FROM BS_DsbTransactions WHERE Amount IS null ";

    private static String SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_FOUR_ASE =
	" GROUP BY Presentment) a " +
	" GROUP BY Presentment ORDER BY Presentment";
  private static String SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE = " AND AccountID IN ";

    // retrieve summaries for a particular Presentment - if any of these change
    // also review corresponding ASE strings
    private static String SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_1 = "SELECT a.count1, b.count2, c.count3, " +
    	"a.credits, b.debits FROM (SELECT t.accountid, count(*) AS count1, sum(t.amount) AS credits FROM " +
    	"BS_DsbTransactions t, BS_Account acc WHERE t.amount >= 0 AND t.datadate >= ? AND t.datadate <= ? " +
    	"AND t.presentment = ? AND acc.AccountID = t.AccountID AND acc.AccountID = ? AND acc.RoutingNum = ? " +
    	"GROUP BY t.accountid) a FULL OUTER JOIN (SELECT t2.accountid, count(*) AS count2, sum(t2.amount) AS " +
    	"debits FROM BS_DsbTransactions t2, BS_Account acc2 WHERE t2.amount < 0 AND t2.datadate >= ? AND " +
    	"t2.datadate <= ? AND t2.presentment = ? AND acc2.AccountID = t2.AccountID AND acc2.AccountID = ? " +
    	"AND acc2.RoutingNum = ? GROUP BY t2.accountid) b ON a.accountid = b.accountid FULL OUTER JOIN " +
    	"(SELECT t3.accountid, count(*) AS count3 FROM BS_DsbTransactions t3, BS_Account acc3 WHERE t3.amount " +
    	"IS NULL AND t3.datadate >= ? AND t3.datadate <= ? AND t3.presentment = ? AND acc3.AccountID = t3.AccountID " +
    	"AND acc3.AccountID = ? AND acc3.RoutingNum = ? GROUP BY t3.accountid) c ON a.accountid = c.accountid " +
    	"OR b.accountid = c.accountid";
    
    private static String SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_1_POSTGRESQL = "SELECT a.count1, b.count2, c.count3, " +
        	"a.credits, b.debits FROM (SELECT t.accountid, count(*) AS count1, sum(t.amount) AS credits FROM " +
        	"BS_DsbTransactions t, BS_Account acc WHERE t.amount >= 0 AND t.datadate >= ? AND t.datadate <= ? " +
        	"AND t.presentment = ? AND acc.AccountID = t.AccountID AND acc.AccountID = ? AND acc.RoutingNum = ? " +
        	"GROUP BY t.accountid) a FULL OUTER JOIN (SELECT t2.accountid, count(*) AS count2, sum(t2.amount) AS " +
        	"debits FROM BS_DsbTransactions t2, BS_Account acc2 WHERE t2.amount < 0 AND t2.datadate >= ? AND " +
        	"t2.datadate <= ? AND t2.presentment = ? AND acc2.AccountID = t2.AccountID AND acc2.AccountID = ? " +
        	"AND acc2.RoutingNum = ? GROUP BY t2.accountid) b ON a.accountid = b.accountid FULL OUTER JOIN " +
        	"(SELECT t3.accountid, count(*) AS count3 FROM BS_DsbTransactions t3, BS_Account acc3 WHERE t3.amount " +
        	"IS NULL AND t3.datadate >= ? AND t3.datadate <= ? AND t3.presentment = ? AND acc3.AccountID = t3.AccountID " +
        	"AND acc3.AccountID = ? AND acc3.RoutingNum = ? GROUP BY t3.accountid) c ON a.accountid = c.accountid " +
        	"AND (c.accountid is not null OR b.accountid = c.accountid)";

    private static String SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_2 = "SELECT a.count1, b.count2, c.count3, " +
    	"a.credits, b.debits FROM (SELECT t.accountid, count(*) AS count1, sum(t.amount) AS credits FROM " +
    	"BS_DsbTransactions t, BS_Account acc WHERE t.amount >= 0 AND t.datadate >= ? " +
    	"AND t.presentment = ? AND acc.AccountID = t.AccountID AND acc.AccountID = ? AND acc.RoutingNum = ? " +
    	"GROUP BY t.accountid) a FULL OUTER JOIN (SELECT t2.accountid, count(*) AS count2, sum(t2.amount) AS " +
    	"debits FROM BS_DsbTransactions t2, BS_Account acc2 WHERE t2.amount < 0 AND t2.datadate >= ? AND " +
    	"t2.presentment = ? AND acc2.AccountID = t2.AccountID AND acc2.AccountID = ? AND acc2.RoutingNum = ? " +
    	"GROUP BY t2.accountid) b ON a.accountid = b.accountid FULL OUTER JOIN (SELECT t3.accountid, count(*) " +
    	"AS count3 FROM BS_DsbTransactions t3, BS_Account acc3 WHERE t3.amount IS NULL AND t3.datadate >= ? " +
    	"AND t3.presentment = ? AND acc3.AccountID = t3.AccountID AND acc3.AccountID = ? AND acc3.RoutingNum = ? " +
    	"GROUP BY t3.accountid) c ON a.accountid = c.accountid OR b.accountid = c.accountid";
    
    private static String SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_2_POSTGRESQL = "SELECT a.count1, b.count2, c.count3, " +
        	"a.credits, b.debits FROM (SELECT t.accountid, count(*) AS count1, sum(t.amount) AS credits FROM " +
        	"BS_DsbTransactions t, BS_Account acc WHERE t.amount >= 0 AND t.datadate >= ? " +
        	"AND t.presentment = ? AND acc.AccountID = t.AccountID AND acc.AccountID = ? AND acc.RoutingNum = ? " +
        	"GROUP BY t.accountid) a FULL OUTER JOIN (SELECT t2.accountid, count(*) AS count2, sum(t2.amount) AS " +
        	"debits FROM BS_DsbTransactions t2, BS_Account acc2 WHERE t2.amount < 0 AND t2.datadate >= ? AND " +
        	"t2.presentment = ? AND acc2.AccountID = t2.AccountID AND acc2.AccountID = ? AND acc2.RoutingNum = ? " +
        	"GROUP BY t2.accountid) b ON a.accountid = b.accountid FULL OUTER JOIN (SELECT t3.accountid, count(*) " +
        	"AS count3 FROM BS_DsbTransactions t3, BS_Account acc3 WHERE t3.amount IS NULL AND t3.datadate >= ? " +
        	"AND t3.presentment = ? AND acc3.AccountID = t3.AccountID AND acc3.AccountID = ? AND acc3.RoutingNum = ? " +
        	"GROUP BY t3.accountid) c ON a.accountid = c.accountid AND (c.accountid is not null OR b.accountid = c.accountid)";

    private static String SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_3 = "SELECT a.count1, b.count2, c.count3, " +
	"a.credits, b.debits FROM (SELECT t.AccountID, count(*) AS count1, sum(t.Amount) AS credits FROM " +
	"BS_DsbTransactions t, BS_Account acc WHERE t.Amount >= 0 AND t.DataDate <= ? " +
	"AND t.Presentment = ? AND acc.AccountID = t.AccountID AND acc.AccountID = ? AND acc.RoutingNum = ? " +
	"GROUP BY t.AccountID) a FULL OUTER JOIN (SELECT t2.AccountID, count(*) AS count2, sum(t2.Amount) AS " +
	"debits FROM BS_DsbTransactions t2, BS_Account acc2 WHERE t2.Amount < 0 AND " +
	"t2.DataDate <= ? AND t2.Presentment = ? AND acc2.AccountID = t2.AccountID AND acc2.AccountID = ? " +
	"AND acc2.RoutingNum = ? GROUP BY t2.AccountID) b ON a.AccountID = b.AccountID FULL OUTER JOIN " +
	"(SELECT t3.AccountID, count(*) AS count3 FROM BS_DsbTransactions t3, BS_Account acc3 WHERE t3.Amount " +
	"IS NULL AND t3.DataDate <= ? AND t3.Presentment = ? AND acc3.AccountID = t3.AccountID " +
	"AND acc3.AccountID = ? AND acc3.RoutingNum = ? GROUP BY t3.AccountID) c ON a.AccountID = c.AccountID " +
	"OR b.AccountID = c.AccountID";
    
    private static String SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_3_POSTGRESQL = "SELECT a.count1, b.count2, c.count3, " +
    		"a.credits, b.debits FROM (SELECT t.AccountID, count(*) AS count1, sum(t.Amount) AS credits FROM " +
    		"BS_DsbTransactions t, BS_Account acc WHERE t.Amount >= 0 AND t.DataDate <= ? " +
    		"AND t.Presentment = ? AND acc.AccountID = t.AccountID AND acc.AccountID = ? AND acc.RoutingNum = ? " +
    		"GROUP BY t.AccountID) a FULL OUTER JOIN (SELECT t2.AccountID, count(*) AS count2, sum(t2.Amount) AS " +
    		"debits FROM BS_DsbTransactions t2, BS_Account acc2 WHERE t2.Amount < 0 AND " +
    		"t2.DataDate <= ? AND t2.Presentment = ? AND acc2.AccountID = t2.AccountID AND acc2.AccountID = ? " +
    		"AND acc2.RoutingNum = ? GROUP BY t2.AccountID) b ON a.AccountID = b.AccountID FULL OUTER JOIN " +
    		"(SELECT t3.AccountID, count(*) AS count3 FROM BS_DsbTransactions t3, BS_Account acc3 WHERE t3.Amount " +
    		"IS NULL AND t3.DataDate <= ? AND t3.Presentment = ? AND acc3.AccountID = t3.AccountID " +
    		"AND acc3.AccountID = ? AND acc3.RoutingNum = ? GROUP BY t3.AccountID) c ON a.AccountID = c.AccountID " +
    		"AND (c.AccountID is not null OR b.AccountID = c.AccountID)";

    private static String SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_4 = "SELECT a.count1, b.count2, c.count3, " +
	"a.credits, b.debits FROM (SELECT t.AccountID, count(*) AS count1, sum(t.Amount) AS credits FROM " +
	"BS_DsbTransactions t, BS_Account acc WHERE t.Amount >= 0 " +
	"AND t.Presentment = ? AND acc.AccountID = t.AccountID AND acc.AccountID = ? AND acc.RoutingNum = ? " +
	"GROUP BY t.AccountID) a FULL OUTER JOIN (SELECT t2.AccountID, count(*) AS count2, sum(t2.Amount) AS " +
	"debits FROM BS_DsbTransactions t2, BS_Account acc2 WHERE t2.Amount < 0 AND t2.Presentment = ? " +
	"AND acc2.AccountID = t2.AccountID AND acc2.AccountID = ? AND acc2.RoutingNum = ? GROUP BY t2.AccountID) b " +
	"ON a.AccountID = b.AccountID FULL OUTER JOIN (SELECT t3.AccountID, count(*) AS count3 FROM " +
	"BS_DsbTransactions t3, BS_Account acc3 WHERE t3.Amount IS NULL AND t3.Presentment = ? AND " +
	"acc3.AccountID = t3.AccountID AND acc3.AccountID = ? AND acc3.RoutingNum = ? GROUP BY t3.AccountID) " +
	"c ON a.AccountID = c.AccountID OR b.AccountID = c.AccountID";
    
    private static String SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_4_POSTGRESQL = "SELECT a.count1, b.count2, c.count3, " +
    		"a.credits, b.debits FROM (SELECT t.AccountID, count(*) AS count1, sum(t.Amount) AS credits FROM " +
    		"BS_DsbTransactions t, BS_Account acc WHERE t.Amount >= 0 " +
    		"AND t.Presentment = ? AND acc.AccountID = t.AccountID AND acc.AccountID = ? AND acc.RoutingNum = ? " +
    		"GROUP BY t.AccountID) a FULL OUTER JOIN (SELECT t2.AccountID, count(*) AS count2, sum(t2.Amount) AS " +
    		"debits FROM BS_DsbTransactions t2, BS_Account acc2 WHERE t2.Amount < 0 AND t2.Presentment = ? " +
    		"AND acc2.AccountID = t2.AccountID AND acc2.AccountID = ? AND acc2.RoutingNum = ? GROUP BY t2.AccountID) b " +
    		"ON a.AccountID = b.AccountID FULL OUTER JOIN (SELECT t3.AccountID, count(*) AS count3 FROM " +
    		"BS_DsbTransactions t3, BS_Account acc3 WHERE t3.Amount IS NULL AND t3.Presentment = ? AND " +
    		"acc3.AccountID = t3.AccountID AND acc3.AccountID = ? AND acc3.RoutingNum = ? GROUP BY t3.AccountID) " +
    		"c ON a.AccountID = c.AccountID AND (c.AccountID is not null OR b.AccountID = c.AccountID)";

	// retrieve summaries for a particular Presentment   ASE
    private static String SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_1_ASE = "SELECT sum(count1) AS count1, sum(count2) AS count2, sum(count3) AS count3, " +
	"sum(credits) AS credits, sum(debits) AS debits FROM " +
	"(SELECT t.AccountID, count(*) AS count1, 0  AS count2, 0 AS count3, sum(t.Amount) AS credits, 0 AS debits " +
	"FROM BS_DsbTransactions t, BS_Account acc WHERE t.Amount >= 0 AND t.DataDate >= ? AND t.DataDate <= ? " +
	"AND t.Presentment = ? AND acc.AccountID = t.AccountID AND acc.AccountID = ? AND acc.RoutingNum = ? " +
	"GROUP BY t.AccountID UNION ALL " +
	"SELECT t2.AccountID, 0 AS count1, count(*) AS count2, 0 AS count3, 0 AS credits, sum(t2.Amount) AS debits " +
	"FROM BS_DsbTransactions t2, BS_Account acc2 WHERE t2.Amount < 0 AND t2.DataDate >= ? AND " +
	"t2.DataDate <= ? AND t2.Presentment = ? AND acc2.AccountID = t2.AccountID AND acc2.AccountID = ? " +
	"AND acc2.RoutingNum = ? GROUP BY t2.AccountID UNION ALL " +
	"SELECT t3.AccountID, 0 AS count1, 0 AS count2, count(*) AS count3, 0 AS credits, 0 AS debits " +
	"FROM BS_DsbTransactions t3, BS_Account acc3 WHERE t3.Amount " +
	"IS NULL AND t3.DataDate >= ? AND t3.DataDate <= ? AND t3.Presentment = ? AND acc3.AccountID = t3.AccountID " +
	"AND acc3.AccountID = ? AND acc3.RoutingNum = ? GROUP BY t3.AccountID) a " ;

    private static String SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_2_ASE = "SELECT sum(count1) AS count1, sum(count2) AS count2, sum(count3) AS count3, " +
	"sum(credits) AS credits, sum(debits) AS debits FROM " +
	"(SELECT t.AccountID, count(*) AS count1, 0  AS count2, 0 AS count3, sum(t.Amount) AS credits, 0 AS debits " +
	"FROM BS_DsbTransactions t, BS_Account acc WHERE t.Amount >= 0 AND t.DataDate >= ? " +
	"AND t.Presentment = ? AND acc.AccountID = t.AccountID AND acc.AccountID = ? AND acc.RoutingNum = ? " +
	"GROUP BY t.AccountID UNION ALL " +
	"SELECT t2.AccountID, 0 AS count1, count(*) AS count2, 0 AS count3, 0 AS credits, sum(t2.Amount) AS debits " +
	"FROM BS_DsbTransactions t2, BS_Account acc2 WHERE t2.Amount < 0 AND t2.DataDate >= ? AND " +
	"t2.Presentment = ? AND acc2.AccountID = t2.AccountID AND acc2.AccountID = ? " +
	"AND acc2.RoutingNum = ? GROUP BY t2.AccountID UNION ALL " +
	"SELECT t3.AccountID, 0 AS count1, 0 AS count2, count(*) AS count3, 0 AS credits, 0 AS debits " +
	"FROM BS_DsbTransactions t3, BS_Account acc3 WHERE t3.Amount " +
	"IS NULL AND t3.DataDate >= ? AND t3.Presentment = ? AND acc3.AccountID = t3.AccountID " +
	"AND acc3.AccountID = ? AND acc3.RoutingNum = ? GROUP BY t3.AccountID) a " ;

    private static String SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_3_ASE = "SELECT sum(count1) AS count1, sum(count2) AS count2, sum(count3) AS count3, " +
	"sum(credits) AS credits, sum(debits) AS debits FROM " +
	"(SELECT t.AccountID, count(*) AS count1, 0  AS count2, 0 AS count3, sum(t.Amount) AS credits, 0 AS debits " +
	"FROM BS_DsbTransactions t, BS_Account acc WHERE t.Amount >= 0 AND t.DataDate <= ? " +
	"AND t.Presentment = ? AND acc.AccountID = t.AccountID AND acc.AccountID = ? AND acc.RoutingNum = ? " +
	"GROUP BY t.AccountID UNION ALL " +
	"SELECT t2.AccountID, 0 AS count1, count(*) AS count2, 0 AS count3, 0 AS credits, sum(t2.Amount) AS debits " +
	"FROM BS_DsbTransactions t2, BS_Account acc2 WHERE t2.Amount < 0 AND " +
	"t2.DataDate <= ? AND t2.Presentment = ? AND acc2.AccountID = t2.AccountID AND acc2.AccountID = ? " +
	"AND acc2.RoutingNum = ? GROUP BY t2.AccountID UNION ALL " +
	"SELECT t3.AccountID, 0 AS count1, 0 AS count2, count(*) AS count3, 0 AS credits, 0 AS debits " +
	"FROM BS_DsbTransactions t3, BS_Account acc3 WHERE t3.Amount " +
	"IS NULL AND t3.DataDate <= ? AND t3.Presentment = ? AND acc3.AccountID = t3.AccountID " +
	"AND acc3.AccountID = ? AND acc3.RoutingNum = ? GROUP BY t3.AccountID) a " ;

    private static String SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_4_ASE = "SELECT sum(count1) AS count1, sum(count2) AS count2, sum(count3) AS count3, " +
	"sum(credits) AS credits, sum(debits) AS debits FROM " +
	"(SELECT t.AccountID, count(*) AS count1, 0  AS count2, 0 AS count3, sum(t.Amount) AS credits, 0 AS debits " +
	"FROM BS_DsbTransactions t, BS_Account acc WHERE t.Amount >= 0 " +
	"AND t.Presentment = ? AND acc.AccountID = t.AccountID AND acc.AccountID = ? AND acc.RoutingNum = ? " +
	"GROUP BY t.AccountID UNION ALL " +
	"SELECT t2.AccountID, 0 AS count1, count(*) AS count2, 0 AS count3, 0 AS credits, sum(t2.Amount) AS debits " +
	"FROM BS_DsbTransactions t2, BS_Account acc2 WHERE t2.Amount < 0 " +
	"AND t2.Presentment = ? AND acc2.AccountID = t2.AccountID AND acc2.AccountID = ? " +
	"AND acc2.RoutingNum = ? GROUP BY t2.AccountID UNION ALL " +
	"SELECT t3.AccountID, 0 AS count1, 0 AS count2, count(*) AS count3, 0 AS credits, 0 AS debits " +
	"FROM BS_DsbTransactions t3, BS_Account acc3 WHERE t3.Amount " +
	"IS NULL AND t3.Presentment = ? AND acc3.AccountID = t3.AccountID " +
	"AND acc3.AccountID = ? AND acc3.RoutingNum = ? GROUP BY t3.AccountID) a " ;

    public static final String SECURE_USER = "SECURE_USER";
    
    public static void addDisbursementSummary( DisbursementSummary incomingSummary, int dataSource, DBConnection connection, HashMap extra ) throws BSException
    {
	PreparedStatement stmt = null;
	try {

	    // get the BAI filename and timestamp
	    String BAIFileIdentifier = null;
	    if( extra != null ) {
	    	BAIFileIdentifier = (String)extra.get( BSConstants.BAI_FILE_IDENTIFIER );
	    }

	    DisbursementAccount account = incomingSummary.getAccount();

	    String accountID = account.getAccountID();

	    DisbursementSummary existingSummary = getDisbursementSummary( account, incomingSummary.getSummaryDate(), connection );
	    DisbursementSummary summary = null;

	    if( existingSummary == null ) {
		stmt = connection.prepareStatement( connection, SQL_ADD_DSSUMMARY );
		summary = incomingSummary;
	    } else {
		stmt = connection.prepareStatement( connection, SQL_UPD_DSSUMMARY );
		summary = existingSummary;

		Currency curr = null;
		DateTime dt = null;
		int iVal = -1;

		iVal = incomingSummary.getNumItemsPending();
		if( iVal != -1 ) {
		    summary.setNumItemsPending( iVal );
		}

		curr = incomingSummary.getTotalDebits();
		if( curr != null ) {
		    summary.setTotalDebits( curr );
		}

		curr = incomingSummary.getTotalCredits();
		if( curr != null ) {
		    summary.setTotalCredits( curr );
		}

		curr = incomingSummary.getTotalDTCCredits();
		if( curr != null ) {
		    summary.setTotalDTCCredits( curr );
		}

		curr = incomingSummary.getImmediateFundsNeeded();
		if( curr != null ) {
		    summary.setImmediateFundsNeeded( curr );
		}

		curr = incomingSummary.getOneDayFundsNeeded();
		if( curr != null ) {
		    summary.setOneDayFundsNeeded( curr );
		}

		curr = incomingSummary.getTwoDayFundsNeeded();
		if( curr != null ) {
		    summary.setTwoDayFundsNeeded( curr );
		}

		dt = incomingSummary.getValueDateTime();
		if( dt != null ) {
		    summary.setValueDateTime( dt );
		}

		curr = incomingSummary.getTotalChecksPaidEarlyAmount();
		if( curr != null ) {
		    summary.setTotalChecksPaidEarlyAmount( curr );
		}

		curr = incomingSummary.getTotalChecksPaidLateAmount();
		if( curr != null ) {
		    summary.setTotalChecksPaidLateAmount( curr );
		}

		curr = incomingSummary.getTotalChecksPaidLastAmount();
		if( curr != null ) {
		    summary.setTotalChecksPaidLastAmount( curr );
		}

		curr = incomingSummary.getFedPresentmentEstimate();
		if( curr != null ) {
		    summary.setFedPresentmentEstimate( curr );
		}

		curr = incomingSummary.getLateDebits();
		if( curr != null ) {
		    summary.setLateDebits( curr );
		}
	    }

	    stmt.setString( 1, accountID );
	    BSUtil.fillTimestampColumn( connection, stmt, 2, summary.getSummaryDate() );
	    stmt.setInt( 3, dataSource );
	    stmt.setInt( 4, summary.getNumItemsPending() );
	    BSUtil.fillCurrencyColumn( stmt, 5, summary.getTotalDebits() );
	    BSUtil.fillCurrencyColumn( stmt, 6, summary.getTotalCredits() );
	    BSUtil.fillCurrencyColumn( stmt, 7, summary.getTotalDTCCredits() );
	    BSUtil.fillCurrencyColumn( stmt, 8, summary.getImmediateFundsNeeded() );
	    BSUtil.fillCurrencyColumn( stmt, 9, summary.getOneDayFundsNeeded() );
	    BSUtil.fillCurrencyColumn( stmt, 10, summary.getTwoDayFundsNeeded() );
	    BSUtil.fillTimestampColumn(connection, stmt, 11, summary.getValueDateTime() );
	    BSUtil.fillCurrencyColumn( stmt, 12, summary.getTotalChecksPaidEarlyAmount() );
	    BSUtil.fillCurrencyColumn( stmt, 13, summary.getTotalChecksPaidLateAmount() );
	    BSUtil.fillCurrencyColumn( stmt, 14, summary.getTotalChecksPaidLastAmount() );
	    BSUtil.fillCurrencyColumn( stmt, 15, summary.getFedPresentmentEstimate() );
	    BSUtil.fillCurrencyColumn( stmt, 16, summary.getLateDebits() );

	    if( existingSummary == null ) {
		stmt.setLong( 17, BSExtendABeanXML.addExtendABeanXML( connection, summary.getExtendABeanXML() ) );
		stmt.setString( 18, null );
		stmt.setString( 19, BAIFileIdentifier );
		DBConnection.executeUpdate( stmt, SQL_ADD_DSSUMMARY );
	    } else {
		stmt.setString( 17, BAIFileIdentifier );
		stmt.setString( 18, accountID );
		BSUtil.fillTimestampColumn(connection, stmt, 19, summary.getSummaryDate() );
		DBConnection.executeUpdate( stmt, SQL_UPD_DSSUMMARY );
	    }

	} catch ( Exception e ) {
	    throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	}

    }


    /*
	 Retrieve the summary information for a specified disbursement account for a date range
	 @param account: the disbursement account for which we want the summaries
	 @param startDate: the start date of summaries to get or null if no start date
	 @param endDate: the end date of summaries to get or null if no start date
    */
    public static DisbursementSummaries getDisbursementSummaries(
    									DisbursementAccount account,
									Calendar startDate,
									Calendar endDate,
									DBConnection connection,
									HashMap extra )
									throws BSException
     {
	PreparedStatement stmt = null;
	DisbursementSummaries sums = null;

	ResultSet rs = null;
	try {
	    if( startDate != null ) {
		if( endDate != null ) {
		    stmt = connection.prepareStatement( connection, SQL_GET_DSSUMMARIES1 );
		    stmt.setString(1, account.getAccountID() );
		    stmt.setString(2, account.getRoutingNumber() );
		    BSUtil.fillTimestampColumn( connection,stmt, 3, startDate );
		    BSUtil.fillTimestampColumn(connection, stmt, 4, endDate );
		    rs = DBConnection.executeQuery( stmt, SQL_GET_DSSUMMARIES1 );
		} else {
		    stmt = connection.prepareStatement( connection, SQL_GET_DSSUMMARIES2 );
		    stmt.setString(1, account.getAccountID() );
		    stmt.setString(2, account.getRoutingNumber() );
		    BSUtil.fillTimestampColumn(connection, stmt, 3, startDate );
		    rs = DBConnection.executeQuery( stmt, SQL_GET_DSSUMMARIES2 );
		}
	    } else if( endDate != null ) {
		stmt = connection.prepareStatement( connection, SQL_GET_DSSUMMARIES3 );
		stmt.setString(1, account.getAccountID() );
		stmt.setString(2, account.getRoutingNumber() );
		BSUtil.fillTimestampColumn(connection, stmt, 3, endDate );
		rs = DBConnection.executeQuery( stmt, SQL_GET_DSSUMMARIES3 );
	    } else {
		stmt = connection.prepareStatement( connection, SQL_GET_DSSUMMARIES4 );
		stmt.setString(1, account.getAccountID() );
		stmt.setString(2, account.getRoutingNumber() );
		rs = DBConnection.executeQuery( stmt, SQL_GET_DSSUMMARIES4 );
	    }


	    sums = new DisbursementSummaries( );
	    DisbursementSummary currentSummary = null;
	    while( rs.next() ) {
		currentSummary = createSummary( connection, account, rs);
		sums.add( currentSummary );
	    }
	    DBUtil.closeResultSet( rs );
	    return sums;

	} catch ( Exception e ) {
	    throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }

	    if( connection != null ) {
		connection.close();
	    }
	}
     }


    /*
	 Retrieve the summary information for a specified disbursement account for a date
	 @param account: the disbursement account for which we want the summaries
	 @param date: the date of summary to get
	 @param connection: connection on which to retrieve data
    */
    private static DisbursementSummary getDisbursementSummary(
    									DisbursementAccount account,
									Calendar date,
									DBConnection connection )
	throws BSException
     {
	PreparedStatement stmt = null;
	DisbursementSummaries sums = null;
	ResultSet rs = null;
	try {
	    stmt = connection.prepareStatement( connection, SQL_GET_DSSUMMARIES5 );
	    stmt.setString(1, account.getAccountID() );
	    stmt.setString(2, account.getRoutingNumber() );
	    BSUtil.fillTimestampColumn(connection, stmt, 3, date );
	    rs = DBConnection.executeQuery( stmt, SQL_GET_DSSUMMARIES5 );

	    DisbursementSummary currentSummary = null;
	    if( rs.next() ) {
		currentSummary = createSummary(connection , account, rs );
	    }
	    DBUtil.closeResultSet( rs );
	    return currentSummary;

	} catch ( Exception e ) {
	    throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	}
     }
    
   /*
     Retrieve the summary information for a specified presentment
     @param presentment: the presentment for which we want to retrieve summaries
   */
   public static DisbursementSummaries getDisbursementSummariesForPresentment (
                                    DisbursementAccount account,
                                    String presentment,
                                    Calendar startDate,
                                    Calendar endDate,
                                    DBConnection connection,
                                    HashMap extra )
                                    throws BSException
    {
       boolean isASEMSSQL = BSAdapter.CONNECTIONTYPE.equalsIgnoreCase( ConnectionDefines.DB_SYBASE_ASE )
                       || BSAdapter.CONNECTIONTYPE.equalsIgnoreCase( ConnectionDefines.DB_MS_SQLSERVER );
       if( isASEMSSQL ) {
           return getDisbursementSummariesForPresentmentASEMSSQL ( account, presentment, startDate, endDate, connection, extra );
       } else {
           return getDisbursementSummariesForPresentmentDB2Oracle ( account, presentment, startDate, endDate, connection, extra );
       }
       
   }
   
   
   /**
    * ASE version of getDisbursementSummariesForPresentment()
    */
   private static DisbursementSummaries getDisbursementSummariesForPresentmentDB2Oracle (
           DisbursementAccount account,
           String presentment,
           Calendar startDate,
           Calendar endDate,
           DBConnection connection,
           HashMap extra )
   throws BSException
   {
       PreparedStatement stmt = null;
       DisbursementSummaries sums = null;
       ResultSet rs = null;
       try {
    	   boolean isPostgreSQL = BSAdapter.CONNECTIONTYPE.equalsIgnoreCase(ConnectionDefines.DB_POSTGRESQL);
           if( startDate != null ) {
               if( endDate != null ) {
            	   if (isPostgreSQL) {
            		   stmt = connection.prepareStatement( connection, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_1_POSTGRESQL );
            	   } else {
            		   stmt = connection.prepareStatement( connection, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_1 );
            	   }
                   
                   BSUtil.fillTimestampColumn(connection, stmt, 1, startDate );
                   BSUtil.fillTimestampColumn(connection, stmt, 2, endDate );
                   stmt.setString( 3, presentment );
                   stmt.setString( 4, account.getAccountID() );
                   stmt.setString( 5, account.getRoutingNumber() );
                   BSUtil.fillTimestampColumn(connection, stmt, 6, startDate );
                   BSUtil.fillTimestampColumn(connection, stmt, 7, endDate );
                   stmt.setString( 8, presentment );
                   stmt.setString( 9, account.getAccountID() );
                   stmt.setString( 10, account.getRoutingNumber() );
                   BSUtil.fillTimestampColumn(connection, stmt, 11, startDate );
                   BSUtil.fillTimestampColumn(connection, stmt, 12, endDate );
                   stmt.setString( 13, presentment );
                   stmt.setString( 14, account.getAccountID() );
                   stmt.setString( 15, account.getRoutingNumber() );
                   if (isPostgreSQL) {
                	   rs = DBConnection.executeQuery( stmt, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_1_POSTGRESQL );
                   } else {
                	   rs = DBConnection.executeQuery( stmt, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_1 );
                   }
               } else {
                   if (isPostgreSQL) {
                	   stmt = connection.prepareStatement( connection, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_2_POSTGRESQL );
                   } else {
                	   stmt = connection.prepareStatement( connection, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_2 );
                   }
            	   
                   BSUtil.fillTimestampColumn(connection, stmt, 1, startDate );
                   stmt.setString( 2, presentment );
                   stmt.setString( 3, account.getAccountID() );
                   stmt.setString( 4, account.getRoutingNumber() );
                   BSUtil.fillTimestampColumn(connection, stmt, 5, startDate );
                   stmt.setString( 6, presentment );
                   stmt.setString( 7, account.getAccountID() );
                   stmt.setString( 8, account.getRoutingNumber() );
                   BSUtil.fillTimestampColumn(connection, stmt, 9, startDate );
                   stmt.setString( 10, presentment );
                   stmt.setString( 11, account.getAccountID() );
                   stmt.setString( 12, account.getRoutingNumber() );
                   if (isPostgreSQL) {
                	   rs = DBConnection.executeQuery( stmt, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_2_POSTGRESQL );
                   } else {
                	   rs = DBConnection.executeQuery( stmt, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_2 );
                   }
               }
           } else if( endDate != null ) {
        	   if (isPostgreSQL) {
        		   stmt = connection.prepareStatement( connection, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_3_POSTGRESQL );
        	   } else {
        		   stmt = connection.prepareStatement( connection, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_3 );
        	   }
               
               BSUtil.fillTimestampColumn(connection, stmt, 1, endDate );
               stmt.setString( 2, presentment );
               stmt.setString( 3, account.getAccountID() );
               stmt.setString( 4, account.getRoutingNumber() );
               BSUtil.fillTimestampColumn(connection, stmt, 5, endDate );
               stmt.setString( 6, presentment );
               stmt.setString( 7, account.getAccountID() );
               stmt.setString( 8, account.getRoutingNumber() );
               BSUtil.fillTimestampColumn(connection, stmt, 9, endDate );
               stmt.setString( 10, presentment );
               stmt.setString( 11, account.getAccountID() );
               stmt.setString( 12, account.getRoutingNumber() );
               if (isPostgreSQL) {
            	   rs = DBConnection.executeQuery( stmt, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_3_POSTGRESQL );
               } else {
            	   rs = DBConnection.executeQuery( stmt, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_3 );
               }
               
           } else {
        	   if (isPostgreSQL) {
        		   stmt = connection.prepareStatement( connection, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_4_POSTGRESQL );
        	   } else {
        		   stmt = connection.prepareStatement( connection, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_4 );
        	   }
               stmt.setString( 1, presentment );
               stmt.setString( 2, account.getAccountID() );
               stmt.setString( 3, account.getRoutingNumber() );
               stmt.setString( 4, presentment );
               stmt.setString( 5, account.getAccountID() );
               stmt.setString( 6, account.getRoutingNumber() );
               stmt.setString( 7, presentment );
               stmt.setString( 8, account.getAccountID() );
               stmt.setString( 9, account.getRoutingNumber() );
               if (isPostgreSQL) {
            	   rs = DBConnection.executeQuery( stmt, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_4_POSTGRESQL );
               } else {
            	   rs = DBConnection.executeQuery( stmt, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_4 );
               }
           }


           sums = new DisbursementSummaries( );
           DisbursementSummary currentSummary = null;
           while( rs.next() ) {
               currentSummary = createSummaryForPresentment( account, presentment, rs );
               sums.add( currentSummary );
           }
           DBUtil.closeResultSet( rs );
           return sums;

       } catch ( Exception e ) {
           throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
       } finally {
           if( stmt != null ) {
               DBConnection.closeStatement( stmt );
           }

           if( connection != null ) {
               connection.close();
           }
       }
   }
   
    
   /**
    * ASE version of getDisbursementSummariesForPresentment()
    */
    public static DisbursementSummaries getDisbursementSummariesForPresentmentASEMSSQL (
									DisbursementAccount account,
									String presentment,
									Calendar startDate,
									Calendar endDate,
									DBConnection connection,
									HashMap extra )
									throws BSException
     {
	PreparedStatement stmt = null;
	DisbursementSummaries sums = null;
	ResultSet rs = null;
	try {
	    if( startDate != null ) {
		if( endDate != null ) {
		    stmt = connection.prepareStatement( connection, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_1_ASE );
		    BSUtil.fillTimestampColumn(connection, stmt, 1, startDate );
		    BSUtil.fillTimestampColumn(connection, stmt, 2, endDate );
		    stmt.setString( 3, presentment );
		    stmt.setString( 4, account.getAccountID() );
		    stmt.setString( 5, account.getRoutingNumber() );
		    BSUtil.fillTimestampColumn(connection, stmt, 6, startDate );
		    BSUtil.fillTimestampColumn(connection, stmt, 7, endDate );
		    stmt.setString( 8, presentment );
		    stmt.setString( 9, account.getAccountID() );
		    stmt.setString( 10, account.getRoutingNumber() );
		    BSUtil.fillTimestampColumn(connection, stmt, 11, startDate );
		    BSUtil.fillTimestampColumn(connection, stmt, 12, endDate );
		    stmt.setString( 13, presentment );
		    stmt.setString( 14, account.getAccountID() );
		    stmt.setString( 15, account.getRoutingNumber() );
		    rs = DBConnection.executeQuery( stmt, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_1_ASE );
		} else {
		    stmt = connection.prepareStatement( connection, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_2_ASE );
		    BSUtil.fillTimestampColumn(connection, stmt, 1, startDate );
		    stmt.setString( 2, presentment );
		    stmt.setString( 3, account.getAccountID() );
		    stmt.setString( 4, account.getRoutingNumber() );
		    BSUtil.fillTimestampColumn(connection, stmt, 5, startDate );
		    stmt.setString( 6, presentment );
		    stmt.setString( 7, account.getAccountID() );
		    stmt.setString( 8, account.getRoutingNumber() );
		    BSUtil.fillTimestampColumn(connection, stmt, 9, startDate );
		    stmt.setString( 10, presentment );
		    stmt.setString( 11, account.getAccountID() );
		    stmt.setString( 12, account.getRoutingNumber() );
		    rs = DBConnection.executeQuery( stmt, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_2_ASE );
		}
	    } else if( endDate != null ) {
		stmt = connection.prepareStatement( connection, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_3_ASE );
		BSUtil.fillTimestampColumn(connection, stmt, 1, endDate );
		stmt.setString( 2, presentment );
		stmt.setString( 3, account.getAccountID() );
		stmt.setString( 4, account.getRoutingNumber() );
		BSUtil.fillTimestampColumn(connection, stmt, 5, endDate );
		stmt.setString( 6, presentment );
		stmt.setString( 7, account.getAccountID() );
		stmt.setString( 8, account.getRoutingNumber() );
		BSUtil.fillTimestampColumn(connection, stmt, 9, endDate );
		stmt.setString( 10, presentment );
		stmt.setString( 11, account.getAccountID() );
		stmt.setString( 12, account.getRoutingNumber() );
		rs = DBConnection.executeQuery( stmt, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_3_ASE );
	    } else {
		stmt = connection.prepareStatement( connection, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_4_ASE );
		stmt.setString( 1, presentment );
		stmt.setString( 2, account.getAccountID() );
		stmt.setString( 3, account.getRoutingNumber() );
		stmt.setString( 4, presentment );
		stmt.setString( 5, account.getAccountID() );
		stmt.setString( 6, account.getRoutingNumber() );
		stmt.setString( 7, presentment );
		stmt.setString( 8, account.getAccountID() );
		stmt.setString( 9, account.getRoutingNumber() );
		rs = DBConnection.executeQuery( stmt, SQL_GET_DSSUMMARIES_FOR_PRESENTMENT_4_ASE );
	    }
	    
	    
	    sums = new DisbursementSummaries( );
	    DisbursementSummary currentSummary = null;
	    while( rs.next() ) {
		currentSummary = createSummaryForPresentment( account, presentment, rs );
		sums.add( currentSummary );
	    }
	    DBUtil.closeResultSet( rs );
        return sums;

    } catch ( Exception e ) {
        throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
    } finally {
        if( stmt != null ) {
            DBConnection.closeStatement( stmt );
        }

        if( connection != null ) {
            connection.close();
        }
    }
}

   
   /*
   Retrieve the presentment summary information for a date range
   @param startDate: the start date of summaries to get or null if no start date
   @param endDate: the end date of summaries to get or null if no start date
    */
   public static DisbursementPresentmentSummaries getDisbursementPresentmentSummaries(
           DisbursementAccounts accounts,
           Calendar startDate,
           Calendar endDate,
           DBConnection connection,
           HashMap extra )
   throws BSException
   {
       boolean isASEMSSQL = BSAdapter.CONNECTIONTYPE.equalsIgnoreCase( ConnectionDefines.DB_SYBASE_ASE )
                      || BSAdapter.CONNECTIONTYPE.equalsIgnoreCase( ConnectionDefines.DB_MS_SQLSERVER );
       if( isASEMSSQL ) {
           return getDisbursementPresentmentSummariesASEMSSQL( accounts, startDate, endDate, connection, extra );
       } else {
           return getDisbursementPresentmentSummariesDB2Oracle( accounts, startDate, endDate, connection, extra );
       }
   }
 

   /**
    * ASE version of getDisbursementPresentmentSummaries()
    */
   private static DisbursementPresentmentSummaries getDisbursementPresentmentSummariesASEMSSQL(
           DisbursementAccounts accounts,
           Calendar startDate,
           Calendar endDate,
           DBConnection connection,
           HashMap extra )
   throws BSException
   {
        DisbursementPresentmentSummaries sums = new DisbursementPresentmentSummaries();

        if ( accounts.size() <= 0 ) {
            return sums;
        }

        // We want to separate the DisbursementAccounts by currency code.
        HashMap accountsMap = new HashMap();
        for( int i = 0; i < accounts.size(); i++ ) {
            DisbursementAccount acc = (DisbursementAccount)accounts.get(i);
            String currencyCode = acc.getCurrencyType();

            if( accountsMap.containsKey( currencyCode ) ) {
                ArrayList accList = (ArrayList)accountsMap.get( currencyCode );
                accList.add( acc );
            } else {
                ArrayList accList = new ArrayList();
                accList.add( acc );
                accountsMap.put( currencyCode, accList );
            }
        }


        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {

            Iterator iter = accountsMap.keySet().iterator();

            while( iter.hasNext() ) {
                String currencyCode = (String)iter.next();
                ArrayList accList = (ArrayList)accountsMap.get( currencyCode );

                // construct a string buffer with the accounts in clause as the starting string
                StringBuffer accountsIDString = new StringBuffer();
                boolean firstAccountInString = true;
                for ( int accountPos = 0; accountPos < accList.size(); accountPos++ ) {
                    DisbursementAccount currentAccount = (DisbursementAccount)accList.get( accountPos );
                    if ( ( currentAccount.getAccountID() != null ) && ( currentAccount.getAccountID().length() != 0 ) ) {
                        if ( !firstAccountInString ) {
                            accountsIDString.append( ", " );
                        } else {
                            firstAccountInString = false;
                            accountsIDString.append( " ( " );
                        }
                        accountsIDString.append( "'" + currentAccount.getAccountID() + "'" );
                    }
                }

                if ( accountsIDString.length() != 0 ) {
                    accountsIDString.append( " ) " );
                } else {
                    continue;
                }

                StringBuffer queryString = new StringBuffer( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_ONE_ASE );



                if( startDate != null ) {
                    if( endDate != null ) {
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_DATE_CLAUSE_BOTH );
                        if ( accountsIDString.length() != 0 ) {
                            queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                            queryString.append( accountsIDString );
                        }
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_TWO_ASE );
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_DATE_CLAUSE_BOTH );
                        if ( accountsIDString.length() != 0 ) {
                            queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                            queryString.append( accountsIDString );
                        }
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_THREE_ASE );
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_DATE_CLAUSE_BOTH );
                        if ( accountsIDString.length() != 0 ) {
                            queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                            queryString.append( accountsIDString );
                        }
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_FOUR_ASE );
                        stmt = connection.prepareStatement( connection, queryString.toString() );
                        BSUtil.fillTimestampColumn(connection, stmt, 1, startDate );
                        BSUtil.fillTimestampColumn(connection, stmt, 2, endDate );
                        BSUtil.fillTimestampColumn(connection, stmt, 3, startDate );
                        BSUtil.fillTimestampColumn(connection, stmt, 4, endDate );
                        BSUtil.fillTimestampColumn(connection, stmt, 5, startDate );
                        BSUtil.fillTimestampColumn(connection, stmt, 6, endDate );
                        rs = DBConnection.executeQuery( stmt, queryString.toString() );
                    } else {
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_DATE_CLAUSE_START );
                        if ( accountsIDString.length() != 0 ) {
                            queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                            queryString.append( accountsIDString );
                        }
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_TWO_ASE );
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_DATE_CLAUSE_START );
                        if ( accountsIDString.length() != 0 ) {
                            queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                            queryString.append( accountsIDString );
                        }
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_THREE_ASE );
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_DATE_CLAUSE_START );
                        if ( accountsIDString.length() != 0 ) {
                            queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                            queryString.append( accountsIDString );
                        }
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_FOUR_ASE );
                        stmt = connection.prepareStatement( connection, queryString.toString() );
                        BSUtil.fillTimestampColumn(connection, stmt, 1, startDate );
                        BSUtil.fillTimestampColumn(connection, stmt, 2, startDate );
                        BSUtil.fillTimestampColumn(connection, stmt, 3, startDate );
                        rs = DBConnection.executeQuery( stmt, queryString.toString() );
                    }
                } else if( endDate != null ) {
                    queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_DATE_CLAUSE_END );
                    if ( accountsIDString.length() != 0 ) {
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                        queryString.append( accountsIDString );
                    }
                    queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_TWO_ASE );
                    queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_DATE_CLAUSE_END );
                    if ( accountsIDString.length() != 0 ) {
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                        queryString.append( accountsIDString );
                    }
                    queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_THREE_ASE );
                    queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_DATE_CLAUSE_END );
                    if ( accountsIDString.length() != 0 ) {
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                        queryString.append( accountsIDString );
                    }
                    queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_FOUR_ASE );

                    stmt = connection.prepareStatement( connection, queryString.toString() );
                    BSUtil.fillTimestampColumn(connection, stmt, 1, endDate );
                    BSUtil.fillTimestampColumn(connection, stmt, 2, endDate );
                    BSUtil.fillTimestampColumn(connection, stmt, 3, endDate );
                    rs = DBConnection.executeQuery( stmt, queryString.toString() );
                } else {
                    if ( accountsIDString.length() != 0 ) {
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                        queryString.append( accountsIDString );
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_TWO_ASE );
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                        queryString.append( accountsIDString );
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_THREE_ASE );
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                        queryString.append( accountsIDString );
                    } else {
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_TWO_ASE );
                        queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_THREE_ASE );
                    }
                    queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_FOUR_ASE );

                    stmt = connection.prepareStatement( connection, queryString.toString() );
                    rs = DBConnection.executeQuery( stmt, queryString.toString() );
                }

                DisbursementPresentmentSummary subSummary = null;
                while( rs.next() ) {
                    subSummary = createPresentmentSummary( rs, currencyCode );

                    // Determine which summary this subSummary should be added to.
                    Iterator sumsIter = sums.iterator();
                    boolean foundOne = false;
                    while( sumsIter.hasNext() ) {
                        DisbursementPresentmentSummary sum = (DisbursementPresentmentSummary)sumsIter.next();

                        if( sum.getPresentment().equals( subSummary.getPresentment() ) ) {
                            addSubSummary(sum ,subSummary ,null );
                            foundOne = true;
                            break;
                        }
                    }
                    if( !foundOne ) {
                        // We need to create a new summary bean
                        DisbursementPresentmentSummary currentSummary = new DisbursementPresentmentSummary();
                        SecureUser sUser = (SecureUser)extra.get( SECURE_USER );
                        addSubSummary( currentSummary, subSummary,sUser );

                        sums.add( currentSummary );
                    }
                }
                rs.close();
            }
            return sums;

        } catch ( Exception e ) {
            throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
        } finally {
            if( stmt != null ) {
                DBConnection.closeStatement( stmt );
            }

            if( connection != null ) {
                connection.close();
            }
        }
   }
   
   
   /**
    * DB2/Oracle version of getDisbursementPresentmentSummaries()
    */
   private static DisbursementPresentmentSummaries getDisbursementPresentmentSummariesDB2Oracle(
           DisbursementAccounts accounts,
           Calendar startDate,
           Calendar endDate,
           DBConnection connection,
           HashMap extra )
   throws BSException
   {
	   boolean isPostgreSQL = BSAdapter.CONNECTIONTYPE.equalsIgnoreCase(ConnectionDefines.DB_POSTGRESQL);
	   DisbursementPresentmentSummaries sums = new DisbursementPresentmentSummaries();

       if ( accounts.size() <= 0 ) {
           return sums;
       }

       // We want to separate the DisbursementAccounts by currency code.
       HashMap accountsMap = new HashMap();
       for( int i = 0; i < accounts.size(); i++ ) {
           DisbursementAccount acc = (DisbursementAccount)accounts.get(i);
           String currencyCode = acc.getCurrencyType();

           if( accountsMap.containsKey( currencyCode ) ) {
               ArrayList accList = (ArrayList)accountsMap.get( currencyCode );
               accList.add( acc );
           } else {
               ArrayList accList = new ArrayList();
               accList.add( acc );
               accountsMap.put( currencyCode, accList );
           }
       }


       ResultSet rs = null;
       PreparedStatement stmt = null;
       try {

           Iterator iter = accountsMap.keySet().iterator();

           while( iter.hasNext() ) {
               String currencyCode = (String)iter.next();
               ArrayList accList = (ArrayList)accountsMap.get( currencyCode );

               // construct a string buffer with the accounts in clause as the starting string
               StringBuffer accountsIDString = new StringBuffer();
               boolean firstAccountInString = true;
               for ( int accountPos = 0; accountPos < accList.size(); accountPos++ ) {
                   DisbursementAccount currentAccount = (DisbursementAccount)accList.get( accountPos );
                   if ( ( currentAccount.getAccountID() != null ) && ( currentAccount.getAccountID().length() != 0 ) ) {
                       if ( !firstAccountInString ) {
                           accountsIDString.append( ", " );
                       } else {
                           firstAccountInString = false;
                           accountsIDString.append( " ( " );
                       }
                       accountsIDString.append( "'" + currentAccount.getAccountID() + "'" );
                   }
               }

               if ( accountsIDString.length() != 0 ) {
                   accountsIDString.append( " ) " );
               } else {
                   continue;
               }

               StringBuffer queryString = new StringBuffer( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_ONE );

               if( startDate != null ) {
                   if( endDate != null ) {
                       queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_DATE_CLAUSE_BOTH );
                       if ( accountsIDString.length() != 0 ) {
                           queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                           queryString.append( accountsIDString );
                       }
                       queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_TWO );
                       queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_DATE_CLAUSE_BOTH );
                       if ( accountsIDString.length() != 0 ) {
                           queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                           queryString.append( accountsIDString );
                       }
                       queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_THREE );
                       queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_DATE_CLAUSE_BOTH );
                       if ( accountsIDString.length() != 0 ) {
                           queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                           queryString.append( accountsIDString );
                       }    
                       if (isPostgreSQL) {
                    	   queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_FOUR_POSTGRESQL );
                       } else {
                    	   queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_FOUR );
                       }
                       stmt = connection.prepareStatement( connection, queryString.toString() );
                       BSUtil.fillTimestampColumn(connection, stmt, 1, startDate );
                       BSUtil.fillTimestampColumn(connection, stmt, 2, endDate );
                       BSUtil.fillTimestampColumn(connection, stmt, 3, startDate );
                       BSUtil.fillTimestampColumn(connection, stmt, 4, endDate );
                       BSUtil.fillTimestampColumn(connection, stmt, 5, startDate );
                       BSUtil.fillTimestampColumn(connection, stmt, 6, endDate );         
                       rs = DBConnection.executeQuery( stmt, queryString.toString() );
                   } else {
                       queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_DATE_CLAUSE_START );
                       if ( accountsIDString.length() != 0 ) {
                           queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                           queryString.append( accountsIDString );
                       }
                       queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_TWO );
                       queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_DATE_CLAUSE_START );
                       if ( accountsIDString.length() != 0 ) {
                           queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                           queryString.append( accountsIDString );
                       }
                       queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_THREE );
                       queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_DATE_CLAUSE_START );
                       if ( accountsIDString.length() != 0 ) {
                           queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                           queryString.append( accountsIDString );
                       }
                       if (isPostgreSQL) {
                    	   queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_FOUR_POSTGRESQL );
                       } else {
                    	   queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_FOUR );
                       }            

                       stmt = connection.prepareStatement( connection, queryString.toString() );
                       BSUtil.fillTimestampColumn(connection, stmt, 1, startDate );
                       BSUtil.fillTimestampColumn(connection, stmt, 2, startDate );
                       BSUtil.fillTimestampColumn(connection, stmt, 3, startDate );
                       rs = DBConnection.executeQuery( stmt, queryString.toString() );
                   }
               } else if( endDate != null ) {
                   queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_DATE_CLAUSE_END );
                   if ( accountsIDString.length() != 0 ) {
                       queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                       queryString.append( accountsIDString );
                   }
                   queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_TWO );
                   queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_DATE_CLAUSE_END );
                   if ( accountsIDString.length() != 0 ) {
                       queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                       queryString.append( accountsIDString );
                   }
                   queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_THREE );
                   queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_DATE_CLAUSE_END );
                   if ( accountsIDString.length() != 0 ) {
                       queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                       queryString.append( accountsIDString );
                   }
                   if (isPostgreSQL) {
                	   queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_FOUR_POSTGRESQL );
                   } else {
                	   queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_FOUR );
                   }

                   stmt = connection.prepareStatement( connection, queryString.toString() );
                   BSUtil.fillTimestampColumn(connection, stmt, 1, endDate );
                   BSUtil.fillTimestampColumn(connection, stmt, 2, endDate );
                   BSUtil.fillTimestampColumn(connection, stmt, 3, endDate );
                   rs = DBConnection.executeQuery( stmt, queryString.toString() );
               } else {
                   if ( accountsIDString.length() != 0 ) {
                       queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                       queryString.append( accountsIDString );
                       queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_TWO );
                       queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                       queryString.append( accountsIDString );
                       queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_THREE );
                       queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARY_ACCOUNTS_IN_CLAUSE );
                       queryString.append( accountsIDString );
                   } else {
                       queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_TWO );
                       queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_THREE );
                   }
                   if (isPostgreSQL) {
                	   queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_FOUR_POSTGRESQL );
                   } else {
                	   queryString.append( SQL_GET_DS_PRESENTMENT_SUMMARIES_COMMON_PART_FOUR );
                   }

                   stmt = connection.prepareStatement( connection, queryString.toString() );
                   rs = DBConnection.executeQuery( stmt, queryString.toString() );
               }

               DisbursementPresentmentSummary subSummary = null;
               while( rs.next() ) {
                   subSummary = createPresentmentSummary( rs, currencyCode );

                   // Determine which summary this subSummary should be added to.
                   Iterator sumsIter = sums.iterator();
                   boolean foundOne = false;
                   while( sumsIter.hasNext() ) {
                       DisbursementPresentmentSummary sum = (DisbursementPresentmentSummary)sumsIter.next();

                       if( sum.getPresentment().equals( subSummary.getPresentment() ) ) {
                           addSubSummary( sum ,subSummary, null );
                           foundOne = true;
                           break;
                       }
                   }
                   if( !foundOne ) {
                       // We need to create a new summary bean
                       DisbursementPresentmentSummary currentSummary = new DisbursementPresentmentSummary();
                       SecureUser sUser =(SecureUser)extra.get( SECURE_USER );
                       addSubSummary( currentSummary, subSummary , sUser);
                       sums.add( currentSummary );
                   }
               }
               DBUtil.closeResultSet( rs );
           }
           return sums;

	} catch ( Exception e ) {
	    throw new BSException( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
		    }

           if( connection != null ) {
               connection.close();
           }
       }
   }
   

     private static DisbursementSummary createSummary(DBConnection conn,  DisbursementAccount account, ResultSet rs )
     	throws Exception
     {
	DisbursementSummary currentSummary = new DisbursementSummary();

	currentSummary.setAccount( account );
	currentSummary.setSummaryDate( BSUtil.getTimestampColumn( rs.getTimestamp( 1 ) ) );
	currentSummary.setNumItemsPending( rs.getInt( 2) );
	currentSummary.setTotalDebits( BSUtil.getCurrencyColumn( rs.getBigDecimal( 3 ), account.getCurrencyType() ) );
	currentSummary.setTotalCredits( BSUtil.getCurrencyColumn(  rs.getBigDecimal( 4 ), account.getCurrencyType() ) );
	currentSummary.setTotalDTCCredits( BSUtil.getCurrencyColumn(  rs.getBigDecimal( 5 ), account.getCurrencyType() ) );
	currentSummary.setImmediateFundsNeeded( BSUtil.getCurrencyColumn(  rs.getBigDecimal( 6 ), account.getCurrencyType() ) );
	currentSummary.setOneDayFundsNeeded( BSUtil.getCurrencyColumn(  rs.getBigDecimal(  7 ), account.getCurrencyType() ) );
	currentSummary.setTwoDayFundsNeeded( BSUtil.getCurrencyColumn(  rs.getBigDecimal( 8 ), account.getCurrencyType() ) );
	currentSummary.setValueDateTime( BSUtil.getTimestampColumn( rs.getTimestamp( 9 ) ) );
	currentSummary.setTotalChecksPaidEarlyAmount( BSUtil.getCurrencyColumn(  rs.getBigDecimal( 10 ), account.getCurrencyType() ) );
	currentSummary.setTotalChecksPaidLateAmount( BSUtil.getCurrencyColumn(  rs.getBigDecimal( 11 ), account.getCurrencyType() ) );
	currentSummary.setTotalChecksPaidLastAmount( BSUtil.getCurrencyColumn(  rs.getBigDecimal( 12 ), account.getCurrencyType() ) );
	currentSummary.setFedPresentmentEstimate( BSUtil.getCurrencyColumn(  rs.getBigDecimal( 13 ), account.getCurrencyType() ) );
	currentSummary.setLateDebits( BSUtil.getCurrencyColumn(  rs.getBigDecimal( 14 ), account.getCurrencyType() ) );
	BSExtendABeanXML.fillExtendABean( conn, currentSummary, rs, 15 );

	return currentSummary;
     }

     private static DisbursementSummary createSummaryForPresentment( DisbursementAccount account, String presentment,
								     ResultSet rs )
     	throws Exception
     {
	DisbursementSummary currentSummary = new DisbursementSummary();

	currentSummary.setAccount( account );
	currentSummary.setPresentment( presentment );
	int creditCount = rs.getInt( 1 );
	int debitCount = rs.getInt( 2 );
	int nullCount = rs.getInt( 3 );
	currentSummary.setNumItemsPending( creditCount + debitCount + nullCount );
	currentSummary.setTotalCredits( BSUtil.getCurrencyColumn( rs.getBigDecimal( 3 ), account.getCurrencyType() ) );
	currentSummary.setTotalDebits( BSUtil.getCurrencyColumn( rs.getBigDecimal( 4 ), account.getCurrencyType() ) );

	return currentSummary;
     }

     private static DisbursementPresentmentSummary createPresentmentSummary( ResultSet rs,
     									     String currencyCode )
	 throws Exception {
	 DisbursementPresentmentSummary newSummary = new DisbursementPresentmentSummary();
	 for ( int index = 1; index <= 3; index++ ) {
	     String currentPresentmentName = rs.getString( index );
	     if ( currentPresentmentName != null ) {
		 newSummary.setPresentment( currentPresentmentName );
		 break;
	     }
	 }
	 int creditCount = rs.getInt( 4 );
	 int debitCount = rs.getInt( 5 );
	 int nullCount = rs.getInt( 6 );
	 newSummary.setNumItemsPending( creditCount + debitCount + nullCount );
	 newSummary.setTotalCredits( BSUtil.getCurrencyColumn( rs.getBigDecimal( 7 ), currencyCode ) );
	 newSummary.setTotalDebits( BSUtil.getCurrencyColumn( rs.getBigDecimal( 8 ), currencyCode ) );
	 return newSummary;
     }
     
     
     /**
   	Add a summary containing records from this disbursement of a certain
	currency.
	@param subSummary a summary bean with the information to add to this bean
  */
 private static void addSubSummary( DisbursementPresentmentSummary summary , DisbursementPresentmentSummary subSummary , SecureUser sUser ) {
	 
	 summary.getDisbursementPresentmentSummeries().add( subSummary );

	 summary.setPresentment(subSummary.getPresentment());
	 
	 int numItemsPending = summary.getNumItemsPending();
	 
	 numItemsPending = numItemsPending + subSummary.getNumItemsPending();
	 
	 summary.setNumItemsPending(numItemsPending);

	if( summary.getTotalDebits() == null ) {
		summary.setTotalDebits(new Currency( "0", sUser.getBaseCurrency(), summary.getLocale() ));
	}
	if( summary.getTotalCredits() == null ) {
	    summary.setTotalCredits(new Currency( "0", sUser.getBaseCurrency(), summary.getLocale()));
	}

	String subCurrencyCode;

	if( subSummary.getTotalDebits() == null ) {
	     subCurrencyCode = subSummary.getTotalCredits().getCurrencyCode();
	} else {
	     subCurrencyCode = subSummary.getTotalDebits().getCurrencyCode();
	}

	if( subCurrencyCode.equals( sUser.getBaseCurrency() ) ) {
	    if( subSummary.getTotalDebits() != null ) {
	    	summary.getTotalDebits().addAmount( subSummary.getTotalDebits() );
	    }
	    if( subSummary.getTotalCredits() != null ) {
	    	summary.getTotalCredits().addAmount( subSummary.getTotalCredits() );
	    }
	} else {
	    // We need to convert the currency
	    HashMap ext = new HashMap();

	    com.sap.banking.forex.bo.interfaces.Forex fxBO = (com.sap.banking.forex.bo.interfaces.Forex)OSGIUtil.getBean(com.sap.banking.forex.bo.interfaces.Forex.class);
	    
	    try {
		if( subSummary.getTotalDebits() != null ) {
			summary.getTotalDebits().addAmount( fxBO.convertToBaseCurrency( sUser, subSummary.getTotalDebits(), ext ) );
		}

		if( subSummary.getTotalCredits() != null ) {
			summary.getTotalCredits().addAmount( fxBO.convertToBaseCurrency( sUser, subSummary.getTotalCredits(), ext ) );
		}
	    } catch( CSILException csilE ) {
		csilE.printStackTrace();
	    }

	}
 }
}

