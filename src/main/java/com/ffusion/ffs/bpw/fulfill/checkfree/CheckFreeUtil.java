// Copyright (c) 2002 Financial Fusion, Inc.
// All rights reserved.

package com.ffusion.ffs.bpw.fulfill.checkfree;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.text.DateFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import com.ffusion.ffs.bpw.BPWServer;
import com.ffusion.ffs.bpw.handler.OSGIUtil;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.sap.banking.common.interceptors.PerfLoggerUtil;
import com.sap.banking.io.beans.File;
import com.sap.banking.io.beans.FileInputStream;
import com.sap.banking.io.beans.FileWriter;
import com.sap.banking.io.beans.RandomAccessFile;
import com.sap.banking.io.provider.interfaces.FileHandlerProvider;

public class CheckFreeUtil {
    public static final DateFormat CF_DATE_FORMAT  = new SimpleDateFormat( "yyyyMMddHHmmssSSS'000'" );
    public static final DateFormat OFX_DATE_FORMAT = new SimpleDateFormat( "yyyyMMddHHmm" );

    public static int _maxRecordCount = -1;

    private static final char[] ALPHABET_MAP = {
        '0', '1', '2', '3', '4', '5', '6',
        '7', '8', '9',
        'A', 'B', 'C', 'D', 'E', 'F', 'G',
        'H', 'I', 'J', 'K', 'L', 'M', 'N',
        'O', 'P', 'Q', 'R', 'S', 'T', 'U',
        'V', 'W', 'X', 'Y', 'Z'};

    private static final HashMap    _statesMap   = new HashMap();
    private static final boolean    statesInitialized = false;
    private static final HashMap    _alphabetMap = new HashMap();

    public static void init()
    {
        // init max record count
        String max = getProperty( DBConsts.CHECKFREE_MAX_RECORDS );
        _maxRecordCount = CheckFreeConsts.DEFAULT_MAX_RECORDS;

        if ( max != null ) {
            try {
                _maxRecordCount = Integer.parseInt( max );
            } catch ( Exception e ) {
                // use default
            }
        }
    }

    // get string in given date format
    public static final String getDateString( DateFormat fmt, Date dt )
    {
        return fmt.format( dt );
    }

    // parse a string to the given date format
    public static final Date parseDateTime( DateFormat fmt, String str )
    {
        return fmt.parse( str, new ParsePosition( 0 ) );
    }

    // log a message
    public static final void log( String str )
    {
        FFSDebug.log( "CheckFree Adapter: " + str, FFSConst.PRINT_DEV );
    }

    public static void log( Throwable t, String str )
    {
        FFSDebug.log( t, "CheckFree Adapter: " + str, FFSConst.PRINT_DEV );
    }

    public static final void log( String str, int level )
    {
        FFSDebug.log( "CheckFree Adapter: " + str, level );
    }
    
    public static final void forcedLog( String str )
    {
        FFSDebug.log( "CheckFree Adapter: " +str );
    }
    // log an error message
    public static final void warn( String err )
    {
        FFSDebug.log( "WARNING! CheckFree Adapter: " + err, FFSConst.PRINT_ERR );
    }


    public static String getProperty( String key )
    {
        String val = null;
        try {
            val= BPWServer.getPropertyValue( key );
        } catch (FFSException e) {
            return null;
        }

        return val;
    }

    public static String getProperty( String key, String defVal )
    {
        String val = null;

        try {
            val = (String)BPWServer.getPropertyValue( key, defVal );
        } catch (FFSException e ) {
            return null;
        }

        return val;
    }


    public static char getAlphabetFromInt( int idx ) { return ALPHABET_MAP[idx];}

    public static int getIntFromAlphabet( char c )
    {   //initialize _alphabetmap.. take this out later because it's called in init()
        initAlphabetMap();

        Integer val = (Integer)_alphabetMap.get( new Character( c ) );
        return( val==null ) ?-1 :val.intValue();
    }


    private static void initAlphabetMap()
    {
        for ( int i=0; i<ALPHABET_MAP.length; ++i ) {
            _alphabetMap.put( new Character( ALPHABET_MAP[i] ),
                              new Integer( i ) );
        }
    }

    // test CSP ID for equality
    protected static boolean isCspIDEqual( String id ) {
        log("******* in isCspIDEqual():-id="+id);
        log("******* in isCspIDEqual():-Property ="+CheckFreeUtil.getProperty( DBConsts.CHECKFREE_SPONSOR_ID ));
        return id.equals( CheckFreeUtil.getProperty( DBConsts.CHECKFREE_SPONSOR_ID ) );
    }


    /**
    *mergeFiles: concatenate inFiles into an output stream.
    */
    static void mergeFiles( File[] inFiles, OutputStream out ) throws Exception
    {
        log( "CheckFreeUtil.mergeFiles start" );
        if ( inFiles==null || inFiles.length<=0 ) {
            log( "CheckFreeUtil.mergeFile end" );
            return;
        }

        InputStream in = null;
        String record = null;

        // build the output file
        try {
            byte[] buff;
            int len;
            int endOffset;
            for ( int i=0; i<inFiles.length; ++i ) {
                in = new BufferedInputStream( new FileInputStream( inFiles[i] ) );
                len = in.available();
                buff = new byte[len];
                in.read( buff, 0, len );

                // Find the end of the last record in the file
                endOffset = findEndLastRecord( buff, buff.length-1 );

                // Write the records only, not the empty line after them
                out.write( buff, 0, endOffset+1 );
                in.close();
            }
            out.flush();
        } catch ( Exception e ) {
            warn( "*** Error creating master file in CheckFreeUtil.mergeFiles: "
                  + FFSDebug.stackTrace( e ) );
            try {
                if ( in!=null) in.close();
            } catch ( Exception ex ) {
                // ignore
            }

            throw e;
        }

        log( "CheckFreeUtil.mergeFile end" );
    }


    /**
     * Split one large response file into several smaller request files
     *
     * 1. The header lines of the main file become the header
     *    lines of each small file.
     * 2. The trailer lines of the main file become the trailer
     *    lines of each small file
     * 3. The number of data records in each file is approximated
     *    by the property N = checkfree.max.records
     * 4. The first N records are dumped into File[0], the next
     *    N into File[1], and so on.
     * 5. Records, whose record type appears in the dependency
     *    list, will not be orphaned from the previous record in
     *    the main file. Another way of stating this is that no
     *    small file may begin with a record whose record type
     *    appears in the dependency list.
     * 6. Returns an array of File objects representing the
     *    small files
     *
     * Note: Even though the parameters of this method indicate
     * that it can be used generically, the code still relies
     * on the fact that it will be a CheckFree response file
     * that is used as the master file. It also requires that
     * the trailer is a single line.
     *
     * @param masterFile File object representing the source file to use for
     *                   splitting.
     * @param nLnHeader  Number of lines at the beginning of the masterFile that
     *                   constitute the header section.
     * @param nLnTrailer Number of lines at the end of the masterFile that
     *                   constitute the trailer section.
     * @param dependentRecords
     *                   Array of record types that should not be orphaned from
     *                   the previous record in the master file.
     * @return Array of File objects respresenting the newly created
     *         smaller files.
     * @exception Exception
     */
    protected static File[] splitFile(File masterFile,
                                      int nLnHeader,
                                      int nLnTrailer,
                                      String[] dependentRecords)
    throws Exception
    {
        log("CheckFreeUtil.splitFile start, masterFile="
            + masterFile.getName());

        // If it doesn't already exist, create a temporary directory
        // for temp files.
        String tempDirName = masterFile.getAbsoluteFile().getParent()
                             + File.separator
                             + CheckFreeConsts.STR_TEMP_DIR_NAME;
        FileHandlerProvider fileHandlerProvider = (FileHandlerProvider)OSGIUtil.getBean(FileHandlerProvider.class);
        File tempDir = new File(tempDirName);
        tempDir.setFileHandlerProvider(fileHandlerProvider);
        if (tempDir.exists() == false) {
            tempDir.mkdir();
        }

        // Extract the header and trailer lines from the master file.
        long headerLength = 0;
        String[] header = new String[nLnHeader];
        String[] trailer = new String[nLnTrailer];
        RandomAccessFile master = null;
        try {
            master = new RandomAccessFile(masterFile, "r");

            // Determine the delimiter length for this file.
            // DOS uses a CR/LF (\n + \r)combo. Unix/AIX only
            // uses a LF (/r) character.
            byte[] b = new byte[1];
            master.seek(master.readLine().length()+1);
            master.read(b, 0, 1);
            int delimitLength = 1;
            if (b[0]=='\n') {
                delimitLength = 2;
            }

            // Start at the beginning of the file.
            master.seek(0);

            // Read in the provided number of lines.
            for (int i = 0; i < nLnHeader; i++) {
                header[i] = master.readLine();
                if (header[i] == null) {
                    throw new Exception("Incomplete header in master file");
                }
                // Add line length + end-of-line character(s) length.
                headerLength += header[i].length() + delimitLength;
            }


            // Attempt to skip over the rest of the file except for the
            // actual trailer data.
            long newPos = masterFile.length() -
                          (CheckFreeConsts.CHECKFREE_TRAILER_LINE_LEN * nLnTrailer);
            if (newPos > 0) {
                master.seek(newPos);
            }

            // Populate an array of strings with lines that we read as we
            // go through the last section of the file. We need to do this
            // because we only estimated the starting position of the trailer
            // in the above seek() operation. We only need to keep nLnTrailer
            // lines, so we will keep a count and wrap around the array using
            // modular arithmetic.
            String[] lastLines = new String[nLnTrailer];
            int i = 0;
            for (String trailerline = master.readLine();
                trailerline != null;
                trailerline = master.readLine()) {
                lastLines[(i++%nLnTrailer)] = trailerline;
            } // End for-loop

            // We've read through until the end of the file. Now take the lines
            // that we've read and align them to match the array index.
            // We want the trailer array to have the last nLnTrailer lines
            // in order, which means starting at the i-th position of the
            // lastLines array and getting the next nLnTrailer lines, wrapping
            // around the end of the array as appropriate.
            for (int j = 0; j < nLnTrailer; j++) {
                trailer[j] = lastLines[(j + i) % nLnTrailer];
                if (trailer[j] == null) {
                    throw new Exception("Incomplete trailer in master file");
                }
            }
        } catch (Throwable e) {
            FFSDebug.log(FFSDebug.stackTrace(e));
            warn("*** Error finding header/trailer in CheckFreeUtil.splitFile: "
                 + FFSDebug.stackTrace(e));
            if (master != null) {
                try {
                    master.close();
                } catch (Exception ex) {
                    // ignore
                }
            }
            throw new FFSException(e, "*** Error finding header/trailer in CheckFreeUtil.splitFile: ");
        }

        // Now that we've extracted the header and trailer lines,
        // do the actual file splitting.

        // Hold the File objects representing the files that we've created.
        ArrayList retFileList = new ArrayList();
        BufferedWriter temp = null;
        try {
            int count = 0;
            String record = null;

            // Skip over the header data and move to the first record.
            master.seek(headerLength);

            // Process each record.
            // The algorithm uses a "read ahead" approach. We read a line of
            // data, determine if we should flush the existing write buffer,
            // and then add the line of data to the write buffer. In otherwords,
            // when we flush out the write buffer, we do so without including
            // the current line.
            // This approach is used so that we can handle the dependcy list
            // cases (where the item must be kept with the previous record.
            for (record = master.readLine();
                record != null;
                record = master.readLine()) {

                if (record.trim().length() == 0) {
                    // Skip blank lines. This check is here
                    // to deal with files that have blank lines
                    // that trail the CF trailer.
                    continue;
                }
                // Check if we need to flush the current buffer (which doesn't
                // include the current line).
                if (count >= _maxRecordCount) {
                    // We don't flush just because the count has met or
                    // exceeded the maxRecordCount limit. We need to check
                    // on a couple of other items before we decide to flush.
                    boolean flush = true;

                    if (record.startsWith(CheckFreeConsts.CHKF_TRAILER) == true) {
                        // Don't call closeTempWithTrailer. We don't want to
                        // double-write the trailer. Write this trailer record
                        // to the write buffer and let the write/close operation
                        // occur when we exit our loop.
                        flush = false;
                    } else if (dependentRecords != null) {
                        // Check our list of dependent records.
                        for (int i = 0; i < dependentRecords.length; i++) {
                            if (record.startsWith(dependentRecords[i]) == true) {
                                // The current record's record type exists in
                                // the dependency list. We want to keep the
                                // current line with the previous line, so
                                // don't flush yet.
                                flush = false;
                                break;
                            }
                        }
                    }

                    if ( (temp != null) &&
                         (flush == true) ) {
                        // Add the trailer records to the write buffer
                        // and flush the buffer to disk.
                        closeTempWithTailer(temp, trailer);
                        temp = null;
                        count = 0;
                    }
                } // end _maxRecordCount check.

                // If needed, open a temp file.
                if (temp == null) {
                    // Create a new file and add the header lines to the
                    // write buffer.
                    temp = openTempWithHeader(retFileList, tempDir, header);
                }

                // Add the current line to the write buffer.
                temp.write(record + CheckFreeConsts.MB_LINE_SEP);
                count++;

            } // End for-loop

            // We've finished reading the master file, including
            // the trailer record. Finish up by closing the temp file.
            if (temp != null) {
            	temp.flush();
            }

        } catch (Throwable e) {
            warn("*** Error splitting master file in CheckFreeUtil.splitFile: "
                 + FFSDebug.stackTrace(e));
            throw new FFSException(e, "*** Error splitting master file in CheckFreeUtil.splitFile");
        } finally {
        	if (null != master) {
        		try {
        		master.close();
        		} catch(Exception e) {
        			// ignore
        		}
        	}
        	if (null != temp) {
        		try {
                // Safety measure for the case where an exception
                // thrown and we weren't able to close up.
                    temp.close();
	            } catch (Exception e) {
	                // ignore
	            }
        	}
        }

        // build the return array
        File[] ret = (File[])retFileList.toArray(new File[retFileList.size()]);

        log("CheckFreeUtil.splitFile end, masterFile=" + masterFile.getName());
        return ret;
    }

    /**
     * Open a new file and write the header lines to the file.
     *
     * @param fileList Collection to add this new file's File object to.
     * @param tempDir  File object representing the directory in which the new
     *                 file is to be created.
     * @param headerLines
     *                 Lines to write out into the new file.
     * @return
     * @exception Exception
     */
    private static BufferedWriter openTempWithHeader(ArrayList fileList,
                                                     File tempDir,
                                                     String[] headerLines)
    throws Exception
    {
        File newFile = File.createTempFile(
                                          CheckFreeConsts.STR_TEMP_FILE_PREFIX,
                                          CheckFreeConsts.STR_TEMP_INFILE_SUFFIX,
                                          tempDir);
        fileList.add(newFile);

        BufferedWriter bufWriter = new BufferedWriter(new FileWriter(newFile));
        writeToFile(bufWriter, headerLines);
        return bufWriter;
    }

    /**
     * Write the trailer lines to a write buffer and then
     * flush and close the write buffer.
     *
     * @param bufWriter BufferedWriter to the file we are to modify.
     * @param tailerLines
     *                  Trailer lines that we are to write.
     * @exception Exception
     */
    private static void closeTempWithTailer(BufferedWriter bufWriter,
                                            String[] tailerLines)
    throws Exception
    {
        writeToFile(bufWriter, tailerLines);
        bufWriter.flush();
        bufWriter.close();
    }

    /**
     * Writes an array of Strings out to a file.
     *
     * @param f      Writer object representing the file that is to be modified.
     * @param lines  Array of lines that should be written out to the file.
     * @exception Exception
     */
    private static final void writeToFile(Writer f, String[] lines)
    throws Exception
    {
        for (int i = 0; i < lines.length; i++) {
            f.write(lines[i] + CheckFreeConsts.MB_LINE_SEP);
        }
    }


    /**
    *findStartContent: find out the start position for the content
    * by skipping the first line of the i.e. the header
    * This method does O(m*n) search to save memory
    */
    public static final int findStartContent( byte[] buff )
    {
    	String method = "CheckFreeUtil.findStartContent";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);	
    	
        int lineSepLen = CheckFreeConsts.MB_LINE_SEP_BYTES.length;

        for ( int i=0; i<buff.length; ++i ) {
            if ( ((char)buff[i])==CheckFreeConsts.MB_LINE_SEP_BYTES[0] ) {
                //If 1st char matches, check the consequent chars
                for ( int j=1; j<lineSepLen; ++j ) {
                    if ( ((char)buff[i+j])==CheckFreeConsts.MB_LINE_SEP_BYTES[j] ) {

                        // Everthing matches. Return the offset right after this line sep.
                        if ( j==lineSepLen-1 ) return i+lineSepLen;
                        continue;
                    } else {
                        break;
                    }
                }
            }
        }

        // if it reaches here, it can't be right
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        return -1;
    }


    /**
    *findEndContent: find out the end position for the content by stepping backwards
    * and finding the line sep presented in reverse order i.e. '\n\r'
    * This method does O(m*n) search to save memory
    */
    static final int findEndContent( byte[] buff )
    {
        return findEndLastRecord( buff,
                                  buff.length-CheckFreeConsts.MB_LINE_SEP_BYTES.length -1 );
    }


    /**
    *findEndContent: find out the end position for the content by stepping backwards
    * and finding the line sep presented in reverse order i.e. '\n\r'
    * This method does O(m*n) search to save memory
    */
    private static final int findEndLastRecord( byte[] buff, int offset )
    {
        int lineSepLen = CheckFreeConsts.MB_LINE_SEP_BYTES.length;

        // Trivial return
        if ( offset<lineSepLen ) return -1;

        // Go backwards to mathch the pattern backwards
        for ( int i=offset; i>=0; --i ) {
            if ( ((char)buff[i])==CheckFreeConsts.MB_LINE_SEP_BYTES[lineSepLen-1] ) {
                for ( int j=1; j<lineSepLen; ++j ) {
                    // Not found
                    if ( i<j ) return -1;

                    if ( ((char)buff[i-j])==CheckFreeConsts.MB_LINE_SEP_BYTES[lineSepLen-1-j]) {
                        if ( j==lineSepLen-1 ) return i;
                        continue;
                    } else {
                        break;
                    }
                }
            }
        }

        // if it reaches here, it can't be right
        return -1;
    }


    public static final String deleteSymbols( String word )
    {
        int i = 0;
        StringBuffer sb = new StringBuffer(16);

        while ( i < word.length() ) {
            char c = word.charAt(i);
            if ( c=='0' || c=='1' || c=='2' || c=='3' || c=='4' || c=='5'
                 || c=='6' || c=='7' || c=='8' || c=='9' )
                sb.append(c);
            i++;

        }
        return sb.toString();

    }
}
