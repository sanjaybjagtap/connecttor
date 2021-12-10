package com.ffusion.banksim.db;


public interface DBConnectionDefines
{
	// connection properties
	static public final String DB_TYPE                  = "TYPE";
	static public final String DB_DEFAULT_CONNECTIONS	= "DEFAULT_CONNECTIONS";
	static public final String DB_MAX_CONNECTIONS		= "MAX_CONNECTIONS";
	static public final String DB_DRIVER				= "DRIVER";
	static public final String DB_USER					= "USER";
	static public final String DB_PASSWORD			    = "PASSWORD";
	static public final String DB_SERVER				= "SERVER";
	static public final String DB_URL					= "URL";
	static public final String DB_CONNECTIONTIMEOUT		= "CONNECTIONTIMEOUT";
	static public final String DB_POOL					= "POOL";

	// db type
	public static final String DB_ORACLE                = "ORACLE";
	public static final String DB_MICROSOFT             = "MICROSOFT";
	public static final String DB_SQL		             = "SQL";
	public static final String DB_SYBASE                = "SYBASE";
	public static final String DB_IBM                   = "IBM";
	public static final String DB_DB2					= "DB2";

	// pool types
	public static final String POOL_WEBSPHERE			= "WEBSPHERE";
}
