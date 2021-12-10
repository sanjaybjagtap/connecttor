// Confidential property of Financial Fusion, Inc.
// Copyright (c) 2002 Financial Fusion, Inc.
// All rights reserved.

package com.ffusion.ffs.bpw.fulfill.checkfree;


/*
 * this exception is used by the CheckFree response file processors to
 * indicate a problem with an individual record in a response
 *
 */
class ResponseRecordException extends Exception
{
    private String _record;
    
    public ResponseRecordException( String msg, String recordID )
    {
	super( msg );
	_record = recordID;
    }

    public String getRecordID()
    {
	return _record;
    }
}
