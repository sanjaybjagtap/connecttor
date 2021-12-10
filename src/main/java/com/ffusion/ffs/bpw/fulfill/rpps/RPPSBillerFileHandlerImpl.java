
package com.ffusion.ffs.bpw.fulfill.rpps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;

import com.ffusion.ffs.bpw.audit.FMLogAgent;
import com.ffusion.ffs.bpw.db.Payee;
import com.ffusion.ffs.bpw.db.PayeeEditMask;
import com.ffusion.ffs.bpw.db.RPPSBiller;
import com.ffusion.ffs.bpw.db.RPPSFI;
import com.ffusion.ffs.bpw.handler.OSGIUtil;
import com.ffusion.ffs.bpw.interfaces.BPWResource;
import com.ffusion.ffs.bpw.interfaces.DBConsts;
import com.ffusion.ffs.bpw.interfaces.PayeeInfo;
import com.ffusion.ffs.bpw.interfaces.PayeeRouteInfo;
import com.ffusion.ffs.bpw.interfaces.RPPSBillerInfo;
import com.ffusion.ffs.bpw.interfaces.RPPSFIInfo;
import com.ffusion.ffs.bpw.serviceMsg.BPWMsgBroker;
import com.ffusion.ffs.bpw.util.ACHAdapterUtil;
import com.ffusion.ffs.bpw.util.BPWUtil;
import com.ffusion.ffs.bpw.util.XMLTokenizer;
import com.ffusion.ffs.db.FFSConnectionHolder;
import com.ffusion.ffs.interfaces.FFSException;
import com.ffusion.ffs.util.FFSConst;
import com.ffusion.ffs.util.FFSDebug;
import com.ffusion.ffs.util.FFSRegistry;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.RPPSBillerMsgSet.Typebiller;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.RPPSBillerMsgSet.Typebillers;
import com.ffusion.msgbroker.generated.MessageBroker.mdf.RPPSBillerMsgSet.Typebillers_MSG;
import com.ffusion.util.beans.filemonitor.FMLogRecord;
import com.sap.banking.common.interceptors.PerfLoggerUtil;
import com.sap.banking.io.beans.File;
import com.sap.banking.io.provider.interfaces.FileHandlerProvider;

/**
 * Process Biller Directory files.
 *
 */

public class RPPSBillerFileHandlerImpl implements FFSConst, BPWResource {

    String  _fiId           = null;
    String  _fiRPPSId       = null;

    String _importDir       = RPPSConsts.DEFAULT_IMPORT_DIR;
    String _errorDir        = RPPSConsts.DEFAULT_ERROR_DIR;
    String _dtdFile         = RPPSConsts.RPPS_BILLER_DTD;

    int _routeId = -1;
    double _paymentCost = 0;


    BPWMsgBroker _bpwMB     = null;

    boolean _debug          = false;


    public RPPSBillerFileHandlerImpl ( String fiId, int routeId, double paymentCost )
    throws FFSException {

        _fiId = fiId;

        // reset fi rpps id 
        _fiRPPSId = null;

        //
        // Get import dir and error dir from BPW Server properties
        //
        _importDir  = RPPSUtil.getProperty( DBConsts.RPPS_IMPORT_DIR,
                                            RPPSConsts.DEFAULT_IMPORT_DIR );
        _errorDir   = RPPSUtil.getProperty( DBConsts.RPPS_ERROR_DIR,
                                            RPPSConsts.DEFAULT_ERROR_DIR );
        // default locaiton of where bpw is installed is current
        // \ or / does not matter here for DTD file location
        _dtdFile   = RPPSUtil.getProperty( DBConsts.BPW_INSTALL_DIR, "." ) 
                     + File.separator
                     + DBConsts.BPW_DTD_DIR
                     + File.separator
                     + RPPSConsts.RPPS_BILLER_DTD ; 



        // get MB instance for parsing
        _bpwMB = (BPWMsgBroker)FFSRegistry.lookup( BPWResource.BPWMSGBROKER);

        // if the error level is lower or equal to logLevel,
        _debug = FFSDebug.checkLogLevel( FFSDebug.PRINT_ALL);

        _routeId = routeId;
        _paymentCost = paymentCost;



    }

    /**
     * Process Biller file.
     * 
     * @param dbh
     * @param fiId
     * @param dir
     */
    public void processResponseFiles( FFSConnectionHolder dbh )
    throws FFSException {
    	
    	String method = "RPPSBillerFileHandlerImpl.processResponseFiles";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(method,start);

        String mName = "RPPSBillerFileHandlerImpl.processResponseFiles: ";
        FFSDebug.log( mName + "RPPS Connector start checking for Biller Directory files", FFSDebug.PRINT_DEV);

        if ( _fiRPPSId == null ) {
            RPPSFIInfo rppsFIInfo =  RPPSFI.getRPPSFIInfoByFIId( dbh, _fiId );
            _fiRPPSId = rppsFIInfo.getFiRPPSId();
        }

        
        File errorDir = new File( _errorDir );
        errorDir.setFileHandlerProvider(RPPSUtil.getFileHandlerProvider());
        File respDir = new File( _importDir );
        respDir.setFileHandlerProvider(RPPSUtil.getFileHandlerProvider());
        boolean success = true;

        FFSDebug.log( mName + "Checking for files in dir: " + respDir.getName(), FFSDebug.PRINT_DEV);

        // get file dirs
        File billerDir = new File( respDir + File.separator + RPPSConsts.DIR_BILLER );
        billerDir.setFileHandlerProvider(RPPSUtil.getFileHandlerProvider());

        try {
            FFSDebug.log( mName + "check file: " + billerDir.getCanonicalPath(), FFSDebug.PRINT_DEV );

            // Get Biller Directory file from rpps.import.dir + BillerDirectory folder
            File[] billerFiles = billerDir.listFiles();

            if ( ( billerFiles == null) || ( billerFiles.length == 0 ) ) {
                // no file in biller dir
                FFSDebug.log( mName + "No Biller Directory file." + billerDir.getCanonicalPath() );
            } else {
                // process biller files one by one
                for ( int i = 0; i < billerFiles.length; i++ ) {

                    if ( billerFiles[ i ].isDirectory() ) {
                        continue;
                    } else {

                        this.processOneBillerFile( dbh, billerFiles[i].getCanonicalPath() );

                    }

                }
            }
        } catch ( Exception e ) {
            FFSDebug.log(e, mName + "RPPS Connector failed to process Biller Directory files." );
            PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
        }

        FFSDebug.log(mName + "RPPS Connector finished checking for Biller Directory files", FFSDebug.PRINT_DEV);
        PerfLoggerUtil.stopPerfLogging(method, start, uniqueIndex);
    }



    /**
     * Process one biller file.
     * 
     * @param dbh
     */
    public void processOneBillerFile( FFSConnectionHolder dbh, String fullFileName )
    throws FFSException
    {

        String mName = "RPPSBillerFileHandlerImpl.processOneBillerFile: ";
        long start = System.currentTimeMillis();
        int uniqueIndex = PerfLoggerUtil.startPerfLoggingAndGetUniqueIndex(mName,start);

        FFSDebug.log( mName + "Processing Biller file: " + fullFileName, FFSDebug.PRINT_DEV );

        XMLTokenizer st = null;
        try {
            // Log to File Monitor Log
            // We pass in null value for db connection,
            // then a new db connection will be used
            // for this log and be committed right away
            // Log to File Monitor Log
            FMLogAgent.writeToFMLog(null,
                                    DBConsts.BPW_RPPS_FILETYPE_ORIGBILLERDIR,
                                    fullFileName,
                                    DBConsts.RPPS,
                                    DBConsts.BPTW,
                                    FMLogRecord.STATUS_IN_PROCESS);

            // The reason we want to tokenize the billers first is to 
            // avoid Out Of Mem problem
            st = new XMLTokenizer( fullFileName, RPPSConsts.BILLER_TOKEN, true );

            // process one biller a time
            while (st.hasMoreTokens()) {

                String oneBiller = st.nextToken();

                FFSDebug.log( mName + "Processing biller: " + oneBiller, FFSDebug.PRINT_DEV );
                // call mb to parse
                // mbBiller = Call mb to parse strOneBiller
                Typebillers billers = this.parseOneBiller( oneBiller );
                if ( ( billers == null ) 
                     || ( billers.biller == null ) 
                     || ( billers.biller.length == 0 ) ) {

                    continue; //skip this biller

                }

                // Acctually, tokenizing has made sure these is only one biller in this object 
                // We do a while loop anyway
                for ( int i = 0; i < billers.biller.length; i ++ ) {

                    // process one parsed biller information
                    processOneBiller( dbh, billers.biller[0] );

                    // commit for each to avoid big TX
                    dbh.conn.commit();
                }

            } // loop through all the billers in the biller files.

            st.close();
            // Process Biller File successfully. Remove it.
            // remove this file
            try {
                File respFile = new File(  fullFileName );
                respFile.setFileHandlerProvider(RPPSUtil.getFileHandlerProvider());
                respFile.delete();
            } catch ( Exception e ) {
                FFSDebug.log(e, mName + "Fialed to remove file: " + fullFileName );
                PerfLoggerUtil.stopPerfLogging(mName, start, uniqueIndex);
            }

            // Log to File Monitor Log
            // We pass in null value for db connection
            // then a new db connection will be used
            // for this log and be committed right away
            // Normally we pass in the current connection,
            // but the current connection has already been
            // committed.
            FMLogAgent.writeToFMLog(null,
                                    DBConsts.BPW_RPPS_FILETYPE_ORIGBILLERDIR,
                                    fullFileName,
                                    DBConsts.RPPS,
                                    DBConsts.BPTW,
                                    FMLogRecord.STATUS_COMPLETE);

        } catch ( Exception e ) {
            if ( st != null ) {
                st.close();
            }

            // Log to File Monitor Log
            // We pass in null value for db connection,
            // then a new db connection will be used
            // for this log and be committed right away
            // The STATUS_COMPLETE log that follows this log is done inside
            // processInvalidFile method.
            FMLogAgent.writeToFMLog(null,
                                    DBConsts.BPW_RPPS_FILETYPE_ORIGBILLERDIR,
                                    fullFileName,
                                    DBConsts.RPPS,
                                    DBConsts.BPTW,
                                    FMLogRecord.STATUS_FAILED);

            this.processInvalidFile(fullFileName, e.toString() );
            PerfLoggerUtil.stopPerfLogging(mName, start, uniqueIndex);
            // move this file to the error  folder
        }
        PerfLoggerUtil.stopPerfLogging(mName, start, uniqueIndex);

    }// processed one biller file

    /**
     * Process one biller.
     * 
     * @param biller
     */
    private void processOneBiller( FFSConnectionHolder dbh, Typebiller mbBiller )
    throws FFSException 
    {

        String mName = "RPPSBillerFileHandlerImpl.processOneBiller: ";


        if ( RPPSConsts.ACD_INDICATION_ADD.compareToIgnoreCase( mbBiller.acdind.data ) == 0 ) {

            // add a new biller
            // add biller information into rpps_payeeext table
            addBiller( dbh, mbBiller );


            // add payees for this biller
            // each remittance address is mapped to a payee
            addPayees( dbh, mbBiller );

        } else if ( RPPSConsts.ACD_INDICATION_CHANGE.compareToIgnoreCase( mbBiller.acdind.data ) == 0 ) {
            // mbBiller.Typeacdind = "C"
            // Modify an existing  biller
            modifyBiller( dbh, mbBiller );

            // Create RPPSBillerInfo, PayeeInfo, PayeeRouteInfo objects from mbBiller


            // modify payees for this biller
            modifyPayees(dbh,mbBiller);

        } else if ( RPPSConsts.ACD_INDICATION_DELETE.compareToIgnoreCase( mbBiller.acdind.data ) == 0 ) {
            // mbBiller.Typeacdind = "D" 

            // delete biller
            deleteBiller( dbh, mbBiller );


            // delete payee
            deletePayees( dbh, mbBiller );

        } else {
            // // invalid flag
            // Log warning message and skip this biller
            FFSDebug.log( mName + "Skip this biller because of the invalid ACD indication: " + mbBiller.acdind.data, FFSDebug.PRINT_WRN );
            FFSDebug.log( mName + "Biller RPPS Id: " + mbBiller.blrid.data, FFSDebug.PRINT_WRN );
        }


    } // process one biller


    /**
     * 
     * @param strBiller
     * @return 
     * @exception FFSException
     */
    private Typebillers parseOneBiller( String strBiller )
    throws FFSException
    {
        Typebillers_MSG message = null;
        String lineSeparator = (String)System.getProperty( "line.separator" );

        // append <billers> and </billers>

        strBiller = "<" + RPPSConsts.BILLERS_TOKEN + ">" 
                    + lineSeparator 
                    + strBiller 
                    + lineSeparator
                    + "</" + RPPSConsts.BILLERS_TOKEN + ">";

        // append DTD ... to strBiller
        // <?xml version="1.0" encoding="UTF-8"?>
        // <!DOCTYPE RPPSBiller SYSTEM "file:/D:/BPW5.0/dev/classes/ifx140.dtd">

        strBiller = "<?xml version = \"1.0\"?>" + lineSeparator 
                    + " <!DOCTYPE billers SYSTEM \"file:/" 
                    + _dtdFile
                    + "\">" + lineSeparator 
                    + lineSeparator 
                    + strBiller;
        try {
            FFSDebug.log ( "RPPSBillerFileHandlerImpl.parseOneBiller: Parsing: " +lineSeparator + strBiller );
            message = (Typebillers_MSG) _bpwMB.parseMsg( strBiller, RPPSConsts.MB_BILLER_TYPE_NAME,
                                                         RPPSConsts.MB_BILLER_SET_NAME,
                                                         _debug );

        } catch ( Exception e ) {
            FFSDebug.log (e, "*** RPPS Adapter: Error when processing " + RPPSConsts.MB_BILLER_TYPE_NAME 
                          + ".  Processing canceled.", 
                          FFSDebug.PRINT_ERR );

            throw new FFSException( e, "RPPS Adapter: Error when processing " + RPPSConsts.MB_BILLER_TYPE_NAME + ".  Processing canceled." );
        }

        return message.billers;
    }

    /**
     * add payees for this biller
     * each remittance address is mapped to a payee
     * 
     * @param dbh
     * @param mbBiller
     */
    private void addPayees( FFSConnectionHolder dbh, Typebiller mbBiller )
    throws FFSException

    {

        // get masks and save them
        String[] masks = this.abstractMaks(mbBiller);

        // Get PayeeInfo from mbBiller
        // each address maps to one payee
        PayeeInfo[] payeeList = abstractPayeeInfos( mbBiller ) ;

        for ( int i  = 0; i < payeeList.length; i ++ ) {


            // Create  PayeeRouteInfo from PayeeInfo
            PayeeRouteInfo pri = createPayeeRouteInfo( payeeList[i] );

            // Save Payee & PayeeRoute into database into database
            // Create a new method in Payee class
            // addPayee( pi, pri, dbh );
            Payee.addPayeeByRouteIdExtIdNameAddress( payeeList[i], pri, dbh );

            // Save mbBiller.Typebillermasks (PayeeInfo.payeeId, "", mbBiller.Typebillermasks[I].Typebillermask.mask) to BPW_PayeeEditMask table
            PayeeEditMask.addPayeeEditMask(dbh, payeeList[i].PayeeID,masks );

        }
    }


    /**
     * add payees for this biller
     * each remittance address is mapped to a payee
     *
     *
     * Case 1: Name is not changed, one address has been modified
     
        Existing payees                 New payees
        
        payeeid1 name1 + address1        name1 + address1
        payeeid2 name1 + address2        name1 + address2'
        payeeid3 name1 + address3        name1 + address3

        Action: try to add the three new payees
                                        payeeid1
                                        payeeid2'
                                        payeeid3 

                payeeid2 will be not matched. we delete it

     * 
     * @param dbh
     * @param mbBiller
     */
    private void modifyPayees( FFSConnectionHolder dbh, Typebiller mbBiller )

    throws FFSException
    {
        // get existing payees for this biller
        // find payee list by route id and extended id
        PayeeInfo[] oldPayeeList = Payee.findPayeeByRouteIdExtId( dbh, 
                                                                  this._routeId,
                                                                  mbBiller.blrid.data  );

        // create hashmap payeeId is the key
        HashMap oldPayeeHashMap = new HashMap();
        for ( int i = 0; i < oldPayeeList.length; i ++ ) {
            oldPayeeHashMap.put( oldPayeeList[i].PayeeID, oldPayeeList[i] );
        }

        // get masks and save them
        String[] masks = this.abstractMaks(mbBiller);

        // Get PayeeInfo from mbBiller
        // each address maps to one payee
        PayeeInfo[] payeeList = abstractPayeeInfos( mbBiller ) ;

        for ( int i  = 0; i < payeeList.length; i ++ ) {

            // Create  PayeeRouteInfo from PayeeInfo
            PayeeRouteInfo pri = createPayeeRouteInfo( payeeList[i] );

            // Save Payee & PayeeRoute into database into database
            // Create a new method in Payee class
            // if the payee exist, we only modify it
            Payee.addPayeeByRouteIdExtIdNameAddress( payeeList[i], pri, dbh );

            // Save mbBiller.Typebillermasks (PayeeInfo.payeeId, "", mbBiller.Typebillermasks[I].Typebillermask.mask) to BPW_PayeeEditMask table
            PayeeEditMask.modPayeeEditMask(dbh, payeeList[i].PayeeID,masks );

            // if this payee's id exist in oldPayeeHashMap, it is not a new payee
            oldPayeeHashMap.remove( payeeList[i].PayeeID );

        }

        // we need to delete the payees left in oldPayeeHashMap 
        Iterator payeeIt = oldPayeeHashMap.keySet().iterator();
        while ( payeeIt.hasNext() ) {
            Payee.deletePayee(dbh, (String) (payeeIt.next() ) );
        }

    }


    /**
     * delete payee
     * 
     * @param dbh
     * @param mbBiller
     */
    private void deletePayees( FFSConnectionHolder dbh, Typebiller mbBiller )
    throws FFSException
    {

        // Only can delete PayeeToRoute entry and pick another fulfillment system.
        // If there is not PayeeRoute, this payee will be deleted, payee's masks 
        // would be deleted as well
        Payee.deletePayeesByRouteIdExtId( dbh, 
                                          this._routeId,
                                          mbBiller.blrid.data );



    }
    /**
     * add biller information into rpps_payeeext table
     * 
     * @param dbh
     * @param mbBiller
     */
    private void addBiller( FFSConnectionHolder dbh, Typebiller mbBiller )

    throws FFSException
    {

        // get biller information from mb 
        RPPSBillerInfo billerInfo = this.abstractBillerInfo(mbBiller);

        // set FI's rpps id
        billerInfo.setFiRPPSId( _fiRPPSId );

        // save RPPS Biller Info
        RPPSBiller.createRPPSBillerInfo(dbh, billerInfo);

    }
    /**
     * Modify an existing  biller
     * 
     * @param dbh
     * @param mbBiller
     * @exception FFSException
     */
    private void modifyBiller( FFSConnectionHolder dbh, Typebiller mbBiller )
    throws FFSException
    {

        // Create RPPSBillerInfo, PayeeInfo, PayeeRouteInfo objects from mbBiller

        RPPSBillerInfo billerInfo = this.abstractBillerInfo(mbBiller);

        // set FI's rpps id
        billerInfo.setFiRPPSId( _fiRPPSId );

        // save RPPS Biller Info
        RPPSBiller.modRPPSBillerInfo(dbh,billerInfo);


    }

    /**
     * delete biller
     * 
     * @param dbh
     * @param mbBiller
     */
    private void deleteBiller( FFSConnectionHolder dbh, Typebiller mbBiller )
    throws FFSException
    {

        // get bilerInfo
        RPPSBillerInfo billerInfo = this.abstractBillerInfo(mbBiller);

        // set FI's rpps id
        billerInfo.setFiRPPSId( _fiRPPSId );

        // delete an existing biller
        RPPSBiller.deleteRPPSBillerInfo(dbh,billerInfo);


    }

    /**
     * Map mb biller to RPPS Biller infor object
     * 
     * @param mbBiller
     * @return 
     */
    private RPPSBillerInfo abstractBillerInfo( Typebiller mbBiller ) 
    {

        RPPSBillerInfo billerInfo = new RPPSBillerInfo();
        // payeeId is set after this method

        // billerAliasId; // assigned by bank/participant - not used
        billerInfo.setBillerRPPSId( mbBiller.blrid.data );

        // fiRPPSId is set after outside // assigned by RPPS for a bank

        // billerName; // used in batch header
        billerInfo.setBillerName( mbBiller.billerinfo.billername.data );



        // effectiveDate; // last changed on (YYYYMMDD)
        billerInfo.setEffectiveDate( mbBiller.effdate.data );


        //trnABA; // transit routing #/ABA
        if ( mbBiller.billerinfo.trnabaExists == true ) {
            billerInfo.setTrnABA( mbBiller.billerinfo.trnaba.data );
        }

        // billerClass; // industry type
        billerInfo.setBillerClass( mbBiller.billerinfo.billerclass.data );

        // billerType; // identifies biller as Gateway/Core
        billerInfo.setBillerType( mbBiller.billerinfo.billertype.data );

        // prenotes; // accepts prenotes?
        billerInfo.setPrenotes( BPWUtil.string10toBoolean(mbBiller.billerinfo.prenotes.data ) );

        // guarPayOnly; // accepts guar pay only?
        billerInfo.setGuarPayOnly( BPWUtil.string10toBoolean(mbBiller.billerinfo.guarpayonly.data ) );

        // dmpPrenotes; // accepts DMP prenotes?
        if ( mbBiller.billerinfo.dmpprenoteExists == true ) {
            billerInfo.setDmpPrenotes( BPWUtil.string10toBoolean(mbBiller.billerinfo.dmpprenote.data  ));
        }

        // dmpPayOnly; // accepts DMP pay only?    
        if ( mbBiller.billerinfo.dmppayonlyExists == true ) {
            billerInfo.setDmpPayOnly( BPWUtil.string10toBoolean(mbBiller.billerinfo.dmppayonly.data ) );
        }
        // privateFlag; // is private biller?    
        billerInfo.setPrivateFlag( BPWUtil.string10toBoolean(mbBiller.billerinfo.pvtblr.data ) );

        //  oldName; // biller name prior to change
        if ( mbBiller.billerinfo.blroldnameExists == true ) {
            billerInfo.setOldName( mbBiller.billerinfo.blroldname.data );
        }
        // submitDate is set when saving to database; 

        // billerStatus 
        billerInfo.setBillerStatus( DBConsts.ACTIVE );
        // logId, not used 

        if ( mbBiller.billerinfo.blrnoteExists == true ) {
            Hashtable memo = new Hashtable();
            memo.put( "Note", mbBiller.billerinfo.blrnote.data );

            billerInfo.setMemo( memo );
        }


        return billerInfo;

    }
    /**
     * Map mb biller to PayeeInfo
     * @param mbBiller
     * @return 
     */
    private PayeeInfo[] abstractPayeeInfos( Typebiller mbBiller ) 
    {
        ArrayList payeeList = new ArrayList();


        // save the first address only
        if ( mbBiller.addressesExists == true ) {
            if ( ( mbBiller.addresses != null ) 
                 && ( mbBiller.addresses.address != null ) 
                 && ( mbBiller.addresses.address.length != 0 ) ) {


                // each address maps a payee
                for ( int i = 0; i < mbBiller.addresses.address.length; i++ ) {
                    if ( mbBiller.addresses.address[i] != null ) {

                        PayeeInfo payee = new PayeeInfo();
                        // set biller id as the ExtdPayeeId
                        payee.ExtdPayeeID = mbBiller.blrid.data;

                        // Always global payee
                        payee.PayeeType = DBConsts.GLOBAL;

                        // Payee name
                        payee.PayeeName = mbBiller.billerinfo.billername.data;

                        // Route ID
                        payee.RouteID = _routeId;
                        payee.LinkPayeeID = null;

                        // Status is always active for RPPS Billers
                        payee.Status = DBConsts.ACTIVE;

                        // Don't know following two fields
                        // pi.DisbursementType = _disbursementType;
                        // pi.PayeeLevelType = _payeeLevelType;



                        // Get address
                        if ( mbBiller.addresses.address[i].address_1Exists == true ) {
                            payee.Addr1 = mbBiller.addresses.address[i].address_1.addr1.data;
                            if ( mbBiller.addresses.address[i].address_1.addr2Exists == true ) {
                                payee.Addr2 = mbBiller.addresses.address[i].address_1.addr2.data; 
                            }
                        }
                        if ( mbBiller.addresses.address[i].cityExists == true ) {
                            payee.City = mbBiller.addresses.address[i].city.data;
                        }
                        if ( mbBiller.addresses.address[i].stateExists == true ) {
                            payee.State = mbBiller.addresses.address[i].state.data;
                        }
                        if ( mbBiller.addresses.address[i].zipcodeExists == true ) {
                            payee.Zipcode = mbBiller.addresses.address[i].zipcode.data;
                        }
                        if ( mbBiller.addresses.address[i].countryExists == true ) {
                            payee.Country = mbBiller.addresses.address[i].country.data;
                        }

                        // No phone number in Biller file
                        // pi.Phone = _phoneNumber;


                        payeeList.add( payee );
                    }

                }

            }
        }
        return( PayeeInfo[]) payeeList.toArray(new PayeeInfo[0]);

    }


    /**
     * Creates a RPPS PayeeRouteInfo object based on data from a
     * PayeeInfo object.
     * 
     * @param pi
     * @return 
     */
    private PayeeRouteInfo createPayeeRouteInfo( PayeeInfo pi )
    {
        PayeeRouteInfo pri = new PayeeRouteInfo();
        pri.PayeeID = pi.PayeeID;
        pri.PayeeType = pi.PayeeType;
        pri.PaymentCost = _paymentCost;
        pri.ExtdPayeeID = pi.ExtdPayeeID;
        pri.RouteID = _routeId;
        pri.BankID = null;
        pri.AcctID = null;
        pri.AcctType = null;
        pri.ExtdInfo = null;
        return pri;
    }



    ///////////////////////////////////////////////////////////////////////////
    // Store the information of the instance to the database. 

    ///////////////////////////////////////////////////////////////////////////


    /**
     * Get biller account masks. We only save the mask value
     * 
     * @param mbBiller
     * @return 
     */
    private String[] abstractMaks( Typebiller mbBiller ) 
    {
        String[] masks = null;

        if ( ( mbBiller.billermasks != null ) 
             && ( mbBiller.billermasks.billermask != null ) 
             && ( mbBiller.billermasks.billermask.length != 0 ) ) {

            masks = new String[ mbBiller.billermasks.billermask.length ];

            for ( int i = 0; i < mbBiller.billermasks.billermask.length; i++ ) {
                masks[i] = mbBiller.billermasks.billermask[i].mask.data;
            }

        }

        return masks;
    }

    /**
     * Process invalid biller directory file:
     * 1. Log error message
     * 2. Move this file to error folder.
     * 
     * @param fileName
     * @param errorCode
     * @exception FFSException
     */
    private void processInvalidFile( String fileName, String errorMsg )
    throws FFSException
    {
        String mName = "RPPSBillerFileHandlerImpl.processInvalidFile:";
        // do the mapping to get error message

        FFSDebug.log( "RPPS Response File Handler: " 
                      + fileName + " is an invalid Biller Directory file! "
                      + "Reason: " + errorMsg
                      + "\n\n Please contact with RPPS Network immediately!", FFSDebug.PRINT_ERR );

        try {

            // start to move the invalid repsonse file to error folder

            // Create errorDir it does not exist, add File.separator
            String errorFileBase = ACHAdapterUtil.getFileNameBase( _errorDir );
            if ( ( fileName != null ) && ( fileName.length() != 0 ) ) {

                // This pmt has been processed before, move this file
                // to error folder

                // check whether this file exists or not
                File invalidFile = new File(  fileName );
                invalidFile.setFileHandlerProvider(RPPSUtil.getFileHandlerProvider());

                if ( invalidFile.exists() ) {
                    FFSDebug.log( mName + "Move invalid Biller Directory file to error folder. File name: " 
                                  + fileName, FFSDebug.PRINT_ERR );

                    // Move this file to error, and add System.getCurrentMis to the end of this file

                    String fullErrorFileName = errorFileBase
                                               + invalidFile.getName()
                                               + RPPSConsts.STR_RPPS_FILE_SEPARATOR
                                               + String.valueOf( System.currentTimeMillis() ) ;

                    File errorFile = new File( fullErrorFileName );
                    errorFile.setFileHandlerProvider(RPPSUtil.getFileHandlerProvider());
                    invalidFile.renameTo( errorFile );

                    // Log to File Monitor Log
                    // We pass in null value for db connection,
                    // then a new db connection will be used
                    // for this log and be committed right away
                    // Normally we pass in the db connection, but since it is not available here
                    // we will use a new one and commit it right away.
                    FMLogAgent.writeToFMLog(null,
                                            DBConsts.BPW_RPPS_FILETYPE_ORIGBILLERDIR,
                                            fullErrorFileName,
                                            DBConsts.BPTW,
                                            DBConsts.RPPS_ERR,
                                            FMLogRecord.STATUS_COMPLETE);

                    FFSDebug.log( mName + "The invalid Biller Directory file has been moved to  " + fullErrorFileName, FFSDebug.PRINT_ERR );
                }
            }
        } catch ( Exception e ) {
            String err = mName + "Failed to process invalid Biller Directory file. Error message: " + e.toString();
            FFSDebug.log( err, FFSDebug.PRINT_ERR );
            throw new FFSException( e, err );
        }
    }

}


