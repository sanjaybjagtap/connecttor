//
// DBSQLConstants.java
//
// Copyright (c) 2001 Financial Fusion, Inc.
// All Rights Reserved.
//

package com.ffusion.banksim.db;

public interface DBSQLConstants
{
    // The maximum length you can use setBytes() or setString() for Oracle
    // beyond which you need to use some form of streaming to get the data across.
    public static final int ORACLE_MAX_NON_LOB_LEN = 4000;

    // The default string we put in when the real one is too big
    public static final String ORACLE_SMALL_STRING = " ";
    
    // The default byte[] we put in when the real one is too big
    public static final byte[] ORACLE_SMALL_BYTES = { 0 };
    
    // Length of a name column
    // 255 is as big as we can get with a char/varchar in ASE
    public static final int NAME_LEN = 255;
    
    // Multi-Database constants
    //Begin Modify by TCG Team for MS SQL Support
    public static final String NULLABLE     = " {null|null||null||null} ";
    public static final String IDENTITY     = " {int|numeric(8,0)|int|int|int|int} ";
    public static final String IDENTITY_COL = " {identity not null primary key|identity not null primary key|not null primary key generated always as identity|not null primary key|not null primary key generated always as identity|identity not null primary key}  ";
    public static final String VARCHAR      = " varchar{|||2||}";
    // ASE doesn't support foreign keys with cascade on delete
    public static final String REFERENCES   = " {references||references|references|references|references} ";
    public static final String DEL_CASCADE  = " {on delete cascade||on delete cascade|on delete cascade|on delete cascade|on delete cascade} ";
    // For CLOBs and BLOBs, ASA has a 2GB limit.  DB2 can specify much more, but we set
    // the limit at 1GB so that the column can be logged.  Not logging affects roll-forward
    // operations.  See the DB2 Admin guide for more info.  Suffice to say, <=1GB are logged.
    public static final String CLOB         = " {long varchar|text|clob(1G)|clob|clob(1G)|text} ";
    public static final String BLOB         = " {long binary|image|blob(1G)|blob|blob(1G)|image} ";
    public static final String NAME         = VARCHAR+ "(" + NAME_LEN + ") ";
    public static final String NAME_ARG     = " {?|?|?|rpad(?," + NAME_LEN + ")|?|?} ";
    public static final String INT64        = " {bigint|numeric(19,0)|bigint|number(19,0)|bigint|numeric(19,0)} ";
    //End Modify

    // Database specific constants
    public static final String ASE_NEWLINE  = "\r\n";
}
