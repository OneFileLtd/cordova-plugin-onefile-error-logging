package uk.co.onefile.onefileeportfolio.errorlogging;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

public class ErrorDatabaseAccess extends SQLiteOpenHelper {

	public static final int DATABASE_VERSION = 1;
	public static final String DATABASE_NAME = "OnefileErrorLogging.db";
	static final String ERROR_TABLE_DEFINITION = "CREATE TABLE IF NOT EXISTS " +
			ErrorEntry.TABLE_NAME + " (" + ErrorEntry.COLUMN_ID + " INTEGER PRIMARY KEY," +
			ErrorEntry.COLUMN_DATE_LOGGED + " TEXT NOT NULL," +
			ErrorEntry.COLUMN_NAME + " TEXT NOT NULL, " +
			ErrorEntry.COLUMN_USER_ID + " TEXT NOT NULL, " +
			ErrorEntry.COLUMN_CURRENT_PLATFORM + " TEXT NOT NULL, " +
			ErrorEntry.COLUMN_MESSAGE + " TEXT NOT NULL, " +
			ErrorEntry.COLUMN_CAUSE + " TEXT NOT NULL, " +
			ErrorEntry.COLUMN_STACK_TRACE + "StackTrace TEXT NOT NULL, " +
			ErrorEntry.COLUMN_CURRENT_PLATFORM_VERSION + " TEXT NOT NULL, "+
			ErrorEntry.COLUMN_CURRENT_USERNAME + " TEXT NOT NULL,"+
			ErrorEntry.COLUMN_ENDPOINT + " TEXT NOT NULL);";

	static final String REMOVE_ERROR_TABLE = "DROP TABLE IF EXISTS " + ErrorEntry.TABLE_NAME + ";";

	public ErrorDatabaseAccess(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(ERROR_TABLE_DEFINITION);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.execSQL(REMOVE_ERROR_TABLE);
		onCreate(db);
	}

	/* Inner class that defines the table contents */
	public static class ErrorEntry implements BaseColumns {
		public static final String TABLE_NAME = "Error";
		public static final String COLUMN_ID = "ID";
		public static final String COLUMN_DATE_LOGGED = "DateLogged";
		public static final String COLUMN_NAME = "Name";
		public static final String COLUMN_MESSAGE = "Message";
		public static final String COLUMN_CAUSE = "Cause";
		public static final String COLUMN_STACK_TRACE = "StackTrace";
		public static final String COLUMN_USER_ID = "UserID";
		public static final String COLUMN_CURRENT_PLATFORM = "CurrentPlatform";
		public static final String COLUMN_CURRENT_PLATFORM_VERSION = "CurrentPlatformVersion";
		public static final String COLUMN_CURRENT_USERNAME = "CurrentUsername";
		public static final String COLUMN_ENDPOINT = "endpoint";
	}
}