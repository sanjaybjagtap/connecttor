package com.ffusion.banksim.db.util;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Locale;

import com.ffusion.banksim.db.DBConnection;
import com.ffusion.banksim.interfaces.BSDBParams;
import com.ffusion.beans.DateTime;
import com.ffusion.beans.common.Currency;

public class BSUtil {

	public static  void fillDate( PreparedStatement stmt, int column, DateTime obj ) throws Exception
    {
	if( obj != null ) {
	    stmt.setDate( column, new java.sql.Date( obj.getTime().getTime() ) );
	} else {
	    stmt.setDate( column, null );
	}

    }
	
	
	public static void fillCurrencyColumn( PreparedStatement stmt, int column, Currency obj ) throws Exception
    {
	if( obj != null ) {
	    BigDecimal bigD = obj.getAmountValue();
	    try {
		bigD = bigD.setScale( 3, BigDecimal.ROUND_UNNECESSARY );
	    } catch ( ArithmeticException e ) {
		throw new Exception( "Invalid currency amount. Scale value must contain no more than three digits.", e );
	    }
	    stmt.setBigDecimal( column, bigD );
	} else {
	    //	    stmt.setBigDecimal( column, null );
	    stmt.setNull( column, java.sql.Types.NUMERIC );
	}
    }
	
	
	public static void fillTimestampColumn(DBConnection conn,PreparedStatement stmt, int column, Calendar obj) throws Exception
	{
		if (obj != null) {
                    //SQL SERVER datetime precision is 0.03Second
				if(conn.getParams().getConnectionType() == BSDBParams.CONN_MSSQL){
                        obj.set(Calendar.MILLISECOND, 0);                        
                  }
                    stmt.setTimestamp(column, new Timestamp(obj.getTime().getTime()));
		} else {
			stmt.setTimestamp(column, null);
		}
	}
	
	public static DateTime getTimestampColumn( Timestamp date, Locale locale )
	{
	if( date == null ) {
		return null;
	} else {
		return ( new DateTime( date, locale ) );
	}
	}

	
	public static Currency getCurrencyColumn( BigDecimal amount, String currency, Locale locale  )
    {
	if( amount == null ) {
	    return null;
	} else {
	    return ( new Currency( amount, currency, locale ) );
	}
    }
	
	
	public static Currency getCurrencyColumn( BigDecimal amount, Locale locale  )
    {
	if( amount == null ) {
	    return null;
	} else {
	    return ( new Currency( amount, locale ) );
	}
    }
	
	public static Currency getCurrencyColumn( BigDecimal amount, String currencyCode )
    {
	return getCurrencyColumn( amount, currencyCode, Locale.getDefault() );
    }
	
	public static DateTime getTimestampColumn( Timestamp date )
    {
	return getTimestampColumn( date, Locale.getDefault() );
    }

	
	public static  Currency getCurrencyColumn( BigDecimal amount )
    {
		return getCurrencyColumn( amount, Locale.getDefault() ); 
    }
}
