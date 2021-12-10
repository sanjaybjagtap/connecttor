/*
 *
 * Copyright (c) 2000 Financial Fusion, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of
 * Financial Fusion, Inc. You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of
 * the license agreement you entered into with Financial Fusion, Inc.
 * No part of this software may be reproduced in any form or by any
 * means - graphic, electronic or mechanical, including photocopying,
 * recording, taping or information storage and retrieval systems -
 * except with the written permission of Financial Fusion, Inc.
 *
 * CopyrightVersion 1.0
 *
 */
package com.ffusion.ffs.bpw.fulfill.rpps;

import java.io.BufferedReader;
import java.io.IOException;

import com.ffusion.ffs.bpw.handler.OSGIUtil;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.interfaces.FFSException;
import com.sap.banking.common.interceptors.PerfLoggerUtil;
import com.sap.banking.io.beans.File;
import com.sap.banking.io.beans.FileReader;
import com.sap.banking.io.provider.interfaces.FileHandlerProvider;

/**
 * Tokenize RPPS file into strings whose length is 94, skip line separators 
 */
public final class RPPSFileTokenizer {


    private String              _fileName;
    private boolean             _hasMore;
    private String              _next;
    private BufferedReader      _bufReader;
    private boolean             _closed;


    private char[]              _lineSeparators;

    /**
     * Open the file and make the first token ready.
     * 
     * @param fileName
     * @param delimiter
     * @param returnDelims
     * @exception FFSException
     */
    public RPPSFileTokenizer( String fileName ) 
    throws FFSException
    {

        try {
            File fileRef = new File(fileName);
            fileRef.setFileHandlerProvider(RPPSUtil.getFileHandlerProvider());

            if (!fileRef.exists()) {
                throw new FFSException( "File not exist: " + fileName);
            }

            if (!fileRef.canRead()) {
                throw new FFSException( "File read access denied: " + fileName);
            }

            // load file                    
            _bufReader = new BufferedReader(new FileReader(fileRef));
        } catch (Exception ex) {
            throw new FFSException( "Error opening file for read " + fileName + ". " + ex.toString() );
        }

        _closed = false;
        _fileName = fileName;


        _lineSeparators = new char[2];
        _lineSeparators[0] = (char) 10;
        _lineSeparators[1] = (char) 13;

        // load first token
        readOneToken();
    }

    /**
     * Whether there are more tokens
     * @return 
     */
    public boolean hasMoreTokens() 
    throws FFSException
    {
        ensureOpen();
        return _hasMore;
    }

    private void ensureOpen()
    throws FFSException
    {
        if ( _closed == true ) {
            throw new FFSException( "Tokenizer is closed." );
        }
    }
    /**
     * Return the current token and load the next token
     * @return 
     */
    public String nextToken()
    throws FFSException
    {
    	String method = "RPPSFileTokenizer.nextToken";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        ensureOpen();
        String tokenStr = null;

        if ( _next != null ) {
            tokenStr = _next.toString();
        }

        // read next token
        readOneToken();

        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return tokenStr;
    }

    /**
     * Close the buffer reader
     */
    public void close()
    {
    	String method = "RPPSFileTokenizer.close";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);
    	
        _closed = true;
        if ( _bufReader != null) {
            try {
                _bufReader.close();
            } catch ( Exception e ) {
            	PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
            }

        }
        _bufReader = null;
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }

    /**
     * Try to read one token from file
     * Can't not handle when there two tokens in one line
     * "<delim> adfasdf </delib> <delim> asdfas</delib>"
     * 
     */
    private void readOneToken ()
    throws FFSException
    {
        _next = this.readOneRecord();

        if ( _next !=  null ) {
            // there is more token
            _hasMore = true;
        } else {
            // no more token
            _hasMore = false;
            _next = null;
        }
    }



    /**
     * Read 94 bytes, skip 0D and 0A, and take care of exception
     * @return 
     */
    private String readOneRecord()
    throws FFSException
    {

        StringBuffer line = new StringBuffer();
        try {
            char firstChar  = (char) _bufReader.read();

            // skip 0D 0A
            while ( true ) {
                if ( firstChar == -1 ) {
                    return null; // end of file
                }
                if ( isLineSeparator( firstChar ) == true ) {
                    firstChar = (char)_bufReader.read();
                } else {
                    break;
                }
            }
            // got fist un 0D 0A char

            // rest of this is 93 chars
            char[] restOfTheLine = new char[ DBConsts.ACH_RECORD_LEN - 1 ];

            // read next 93 chars
            int i = _bufReader.read( restOfTheLine, 0, DBConsts.ACH_RECORD_LEN  - 1);
            if ( i == -1 ) {
                // incomplete line
                return null;
            } else {

                // It is possible that restOfTheLine does not have 93 chars
                // If the rpps file i NO DATA file
                line.append( firstChar).append( restOfTheLine );

            }


        } catch (IOException ioe) {
            this.close();
            throw new FFSException(ioe, "Can't read from file " + _fileName);
        }
        return line.toString();

    }


    /**
     * Check whether a char is line separators or not
     * 
     * @param oneChard
     * @return 
     */
    private boolean isLineSeparator ( char oneChar ) 
    {
        for ( int i = 0; i < _lineSeparators.length; i ++ ) {
            if ( oneChar == _lineSeparators[i]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return the file's name which is being tokenizered
     * 
     * @return 
     */
    public String getFileName()
    {
        return this._fileName;
    }

}






