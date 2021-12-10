//
// Client.java
//
// Copyright (c) 2002 Financial Fusion, Inc.
// All Rights Reserved.
//

package com.ffusion.banksim.db;

import com.ffusion.banksim.interfaces.*;
import com.ffusion.beans.user.User;

import com.ffusion.beans.*;

import java.sql.SQLException;

public class DBClient
{
	private static final String GET_CUSTOMER_COUNT =
			"Select count(*) from BS_Customer where UserID = ?";

	private static final String GET_CUSTOMER =
			"Select CustomerID, UserID, Password, FirstName, MiddleName, " +
			"LastName, Address1, Address2, City, State, PostalCode, Country, " +
			"DayPhone, EveningPhone, EMailAddress from BS_Customer where UserID = ?";

	/**
	 * try to signon users with the specified userID and password
	 *
	 * @param userID   A String
	 * @param password A String
	 * @param conn     DBConnection object to connect to the database
	 * @return User object that contains the user's information
	 * @throws BSException that states the signon failed
	 */

	public static final User signOn(String userID, String password, DBConnection conn) throws BSException
	{
		User customer = null;
		DBResultSet dbResultSet = null;
		try {
			Object[] params = {userID};

			// get a count of how many users match the user_id.
			// Don't want the query to include password, as it won't necessarily match for consumer.
			// Corporate allows multiple businesses with the same business name, which is used as the user name.
			dbResultSet = conn.prepareQuery(GET_CUSTOMER_COUNT);
			dbResultSet.open(params);
			int count = 0;
			if (dbResultSet.getNextRow()) {
				count = dbResultSet.getColumnInt(1);
			}
			dbResultSet.close();
			boolean bFound = false;
			if (count > 0) {
				dbResultSet = conn.prepareQuery(GET_CUSTOMER);
				dbResultSet.open(params);
				// Check to see if there is such userID in the database
				while (dbResultSet.getNextRow()) {
					customer = new User();
					if (count > 1) {
						String rsPassword = dbResultSet.getColumnString(3);
						if (!rsPassword.equals(password))
							continue;
					}
					bFound = true;
					customer.setId(dbResultSet.getColumnString(1));
					customer.setUserName(dbResultSet.getColumnString(2));
					customer.setPassword(dbResultSet.getColumnString(3));
					customer.setFirstName(dbResultSet.getColumnString(4));
					customer.setMiddleName(dbResultSet.getColumnString(5));
					customer.setLastName(dbResultSet.getColumnString(6));
					customer.setStreet(dbResultSet.getColumnString(7));
					customer.setStreet2(dbResultSet.getColumnString(8));
					customer.setCity(dbResultSet.getColumnString(9));
					customer.setState(dbResultSet.getColumnString(10));
					customer.setZipCode(dbResultSet.getColumnString(11));
					customer.setCountry(dbResultSet.getColumnString(12));
					customer.setPhone(dbResultSet.getColumnString(13));
					customer.setPhone2(dbResultSet.getColumnString(14));
					customer.setEmail(dbResultSet.getColumnString(15));
					break;
				}
				dbResultSet.close();
			}
			// No userID in the database matches the one specified

			if (!bFound) {
				dbResultSet.close();
				throw new BSException(BSException.BSE_CUSTOMER_NOT_EXISTS,
						MessageText.getMessage(IBSErrConstants.ERR_USERID_NOT_EXISTS));
			}
		} catch (SQLException sqle) {
			throw new BSException(BSException.BSE_DB_EXCEPTION,
					DBSqlUtils.getRealSQLException(sqle));
		}
		return customer;
	}

}
