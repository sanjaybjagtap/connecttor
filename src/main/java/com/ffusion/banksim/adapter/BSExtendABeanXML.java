//
// BSExtendABeanXML.java
//
// Copyright (c) 2003 Financial Fusion, Inc.
// All Rights Reserved.
//

package com.ffusion.banksim.adapter;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.ffusion.banksim.db.DBConnection;
import com.ffusion.banksim.interfaces.BSException;
import com.ffusion.beans.ExtendABean;
import com.ffusion.dataconsolidator.adapter.exception.DCException;

public class BSExtendABeanXML{


    // SQL queries
    private static final String SQL_ADD_XML = "INSERT INTO BS_ExtendABeanXML( ExtendABeanID, XMLSegmentNumber, XMLSegment ) VALUES( ?, ?, ?)";

    private static final String SQL_DELETE_XML = "DELETE FROM BS_ExtendABeanXML WHERE ExtendABeanID=?";

    private static final String SQL_GET_XML = "SELECT XMLSegment FROM BS_ExtendABeanXML WHERE ExtendABeanID=? ORDER BY XMLSegmentNumber ASC";


    /**
    	Add new ExtendABean XML to the BS_ExtendABeanXML table
	@param XML: ExtendABean XML to be added
    	@return the DCExtendABeanID of the new row
    */
    public static long addExtendABeanXML( DBConnection connection, String XML ) throws BSException
    {
	if( XML == null || (XML.length() == 0) ) {
	    return 0;
	}

	PreparedStatement stmt = null;

        try {
	    long id = BSRecordCounter.getNextIndex( connection, BSRecordCounter.TYPE_EXTENDABEAN, String.valueOf(BSRecordCounter.EXTENDABEAN_ID), BSRecordCounter.EXTENDABEAN_INDEX );

	    stmt = connection.prepareStatement( connection, SQL_ADD_XML );

	    int segmentNumber = 1;
	    String segment = null;
	    boolean notDone = true;
	    while( notDone ) {
		if( XML.length() > 2000 )  {
		    segment = XML.substring( 0, 2000 );
		    XML = XML.substring( 2000, XML.length() );
		} else {
		    segment = XML;
		    notDone = false;
		}
		stmt.setLong( 1, id );
		stmt.setInt( 2, segmentNumber );
		stmt.setString( 3, segment );
		DBConnection.executeUpdate( stmt, SQL_ADD_XML );
		segmentNumber++;
	    }

	    return id;

	} catch ( Exception e ) {
	    throw new BSException ( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	}
    }


    /**
    	Delete all entries in the BS_ExtendABeanXML table that have the given id
	@param id: DCExtendABeanID to be deleted
    */
    public static void deleteExtendABeanXML( DBConnection connection, long id ) throws BSException
    {
	PreparedStatement stmt = null;
        try {
	    stmt = connection.prepareStatement( connection, SQL_DELETE_XML );
	    stmt.setLong( 1, id );
	    DBConnection.executeUpdate( stmt, SQL_DELETE_XML );

	} catch ( Exception e ) {
	    throw new BSException ( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {
	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }
	}
    }



    /**
        Get the XML from the db, put it back together in order, then return a String containing the XML
	@param id: DCExtendABeanID of XML to retrieve
	@return String containing the ExtendABean XML
    */
    public static String getExtendABeanXML( long id, DBConnection connection ) throws BSException
    {
	// an id of 0 means that there is no ExtendABeanXML
	if( id == 0 ) {
	    return null;
	}

	PreparedStatement stmt = null;
        try {

	    stmt = connection.prepareStatement( connection, SQL_GET_XML );
	    stmt.setLong( 1, id );
	    ResultSet rs = DBConnection.executeQuery( stmt, SQL_GET_XML );

	    StringBuffer sb = new StringBuffer();
	    while( rs.next() ) {
		// put the xml back together again in the correct order
		sb.append( rs.getString( 1 ) );
	    }
	    return sb.toString();

	} catch ( Exception e ) {
	    throw new BSException ( BSException.BSE_DB_EXCEPTION, e.getMessage() );
	} finally {

	    if( stmt != null ) {
		DBConnection.closeStatement( stmt );
	    }

	    if( connection != null ) {
		connection.close();
	    }
	}

    }
    
    public static void fillExtendABean(DBConnection connection, ExtendABean bean, ResultSet rs, int column )throws BSException{
    	
    	try{
			String xml = getExtendABeanXML(  rs.getLong( column ),connection );
			if( xml != null ) {
				bean.setExtendABeanXML( xml );
			}
		} catch ( Exception e ) {
			throw new BSException(BSException.BSE_DB_EXCEPTION, "Failed to fill ExtendABean object", e );
		}
    }
    
}
