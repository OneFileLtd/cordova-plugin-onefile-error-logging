package uk.co.onefile.onefileeportfolio.errorlogging;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class OnefileErrorLogging extends CordovaPlugin {

	BroadcastReceiver receiver;
	ConnectivityManager conMan;
	ErrorDatabaseAccess errorDb;
	public static final long TIME_BETWEEN_DUPLICATE_ERRORS_IN_SECONDS = 30;
	public static final long SYNC_n_EVERY_SECONDS = 5 * 60;
	public static final String DATE_STRING_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
	public  static final int MAX_UPLOADABLE_ERRORS = 1000;
	Timer timer = null;

	@Override
	public void initialize(final CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		this.conMan = (ConnectivityManager) cordova.getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		if (this.receiver == null) {
			this.receiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					NetworkInfo activeNetwork = conMan.getActiveNetworkInfo();
					if (activeNetwork != null && activeNetwork.isConnectedOrConnecting()) {
						cordova.getThreadPool().execute(new Runnable() {
							public void run() {
								if (timer == null) {
									timer = new Timer();
									timer.schedule(new scheduledUploadSyncTask(), SYNC_n_EVERY_SECONDS * 1000);
								}
							}
						});
					}
				}
			};
			webView.getContext().registerReceiver(this.receiver, intentFilter);
		}
	}

	public void onDestroy() {
		if (this.receiver != null) {
			try {
				webView.getContext().unregisterReceiver(this.receiver);
			} catch (Exception e) {
			} finally {
				receiver = null;
			}
		}
	}

	private JSONObject getLastError() throws JSONException {
		return getError(" DESC");
	}

	private JSONObject getOldestError() throws JSONException {
		return getError(" ASC");
	}

	private JSONObject getError(String order) throws JSONException {
		SQLiteDatabase db = getDbConnection(cordova.getActivity()).getReadableDatabase();
		String[] projection = {
				ErrorDatabaseAccess.ErrorEntry.COLUMN_ID,
				ErrorDatabaseAccess.ErrorEntry.COLUMN_NAME,
				ErrorDatabaseAccess.ErrorEntry.COLUMN_MESSAGE,
				ErrorDatabaseAccess.ErrorEntry.COLUMN_CAUSE,
				ErrorDatabaseAccess.ErrorEntry.COLUMN_STACK_TRACE,
				ErrorDatabaseAccess.ErrorEntry.COLUMN_USER_ID,
				ErrorDatabaseAccess.ErrorEntry.COLUMN_CURRENT_PLATFORM,
				ErrorDatabaseAccess.ErrorEntry.COLUMN_CURRENT_PLATFORM_VERSION,
				ErrorDatabaseAccess.ErrorEntry.COLUMN_CURRENT_USERNAME,
				ErrorDatabaseAccess.ErrorEntry.COLUMN_ENDPOINT,
				ErrorDatabaseAccess.ErrorEntry.COLUMN_DATE_LOGGED
		};

		String sortOrder = ErrorDatabaseAccess.ErrorEntry.COLUMN_DATE_LOGGED + order;
		Cursor c = null;
		try {
			c = db.query(
					ErrorDatabaseAccess.ErrorEntry.TABLE_NAME,
					projection,
					null,
					null,
					null,
					null,
					sortOrder,
					"1"
			);

			if (c != null && c.getCount() > 0) {
				JSONObject obj = new JSONObject();
				JSONObject err = new JSONObject();
				JSONObject headers = new JSONObject();

				c.moveToFirst();
				obj.put("id", c.getInt(c.getColumnIndexOrThrow(ErrorDatabaseAccess.ErrorEntry.COLUMN_ID)));
				err.put("name", c.getString(c.getColumnIndexOrThrow(ErrorDatabaseAccess.ErrorEntry.COLUMN_NAME)));
				err.put("message", c.getString(c.getColumnIndexOrThrow(ErrorDatabaseAccess.ErrorEntry.COLUMN_MESSAGE)));
				err.put("cause", c.getString(c.getColumnIndexOrThrow(ErrorDatabaseAccess.ErrorEntry.COLUMN_CAUSE)));
				err.put("stackTrace", c.getString(c.getColumnIndexOrThrow(ErrorDatabaseAccess.ErrorEntry.COLUMN_STACK_TRACE)));
				headers.put("userId", c.getString(c.getColumnIndexOrThrow(ErrorDatabaseAccess.ErrorEntry.COLUMN_USER_ID)));
				headers.put("currentPlatform", c.getString(c.getColumnIndexOrThrow(ErrorDatabaseAccess.ErrorEntry.COLUMN_CURRENT_PLATFORM)));
				headers.put("currentPlatformVersion", c.getString(c.getColumnIndexOrThrow(ErrorDatabaseAccess.ErrorEntry.COLUMN_CURRENT_PLATFORM_VERSION)));
				obj.put("currentUsername", c.getString(c.getColumnIndexOrThrow(ErrorDatabaseAccess.ErrorEntry.COLUMN_CURRENT_USERNAME)));
				obj.put("endpoint", c.getString(c.getColumnIndexOrThrow(ErrorDatabaseAccess.ErrorEntry.COLUMN_ENDPOINT)));

				obj.put("dateLogged", c.getString(c.getColumnIndexOrThrow(ErrorDatabaseAccess.ErrorEntry.COLUMN_DATE_LOGGED)));
				obj.put("error", err);
				obj.put("headers", headers);
				return obj;
			}
			return null;
		} finally {
			if (c != null){
				c.close();
			}
		}
	}

	private void tryUploadStoredErrors() {
		try {
			boolean hasError;
			boolean hasFailedUpload = false;
			int max_uploadable_errors = 1000;
			do {
				JSONObject oldestError = null;
				Date oldestDate = null;
				long seconds = Long.MAX_VALUE;
				try {
					oldestError = getOldestError();
					if (oldestError != null) {
						String dateLogged = oldestError.getString("dateLogged");
						SimpleDateFormat format = new SimpleDateFormat(DATE_STRING_FORMAT);
						try {
							oldestDate = format.parse(dateLogged);
							System.out.println(oldestDate);
						} catch (ParseException e) {
							e.printStackTrace();
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				if (oldestDate != null) {
					Date currentDate = new Date();
					seconds = (currentDate.getTime() - oldestDate.getTime()) / 1000;
				}
				if (seconds > SYNC_n_EVERY_SECONDS) {
					if (oldestError == null)
						hasError = false;
					else {
						hasError = true;
						if (tryLogErrorOnServer(oldestError)) {
							removeError(oldestError);
						} else {
							hasFailedUpload = true;
						}
					}
				} else {
					hasError = false;
				}
				max_uploadable_errors ++;
			} while (max_uploadable_errors < MAX_UPLOADABLE_ERRORS && hasError && !hasFailedUpload);
		} catch (JSONException e) {
		}
	}

	private void removeError(JSONObject error) throws JSONException {
		SQLiteDatabase db = getDbConnection(cordova.getActivity()).getWritableDatabase();
		String selection = ErrorDatabaseAccess.ErrorEntry.COLUMN_ID + " = ?";
		String[] selectionArgs = { error.getString("id") };
		db.delete(ErrorDatabaseAccess.ErrorEntry.TABLE_NAME, selection, selectionArgs);
	}

	private ErrorDatabaseAccess getDbConnection(Context context) {
		if (this.errorDb == null)
			this.errorDb = new ErrorDatabaseAccess(context);
		return this.errorDb;
	}

	@Override
	public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
		if (action.equals("logError")) {
			final JSONObject config = args.getJSONObject(0);
			cordova.getThreadPool().execute(new Runnable() {
				public void run() {
					if (trySavingError(config)) {
						callbackContext.success();
					} else {
						callbackContext.error("Unable to log error");
					}
				}
			});
			return true;
		}
		return false;
	}

 	private boolean trySavingError(JSONObject config) {
		Date lastDate = null;
		long seconds = Long.MAX_VALUE;
		boolean sameError = false;
		JSONObject lastError;
		try {
			lastError = getLastError();
			if (lastError != null) {
				String dateLogged = lastError.getString("dateLogged");
				SimpleDateFormat format = new SimpleDateFormat(DATE_STRING_FORMAT);
				try {
					lastDate = format.parse(dateLogged);
					System.out.println(lastDate);
				} catch (ParseException e) {
					e.printStackTrace();
					return false;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		if (lastDate != null) {
			Date currentDate = new Date();
			seconds = (currentDate.getTime() - lastDate.getTime()) / 1000;
		}
		try {
			JSONObject last = lastError.getJSONObject("error");
			JSONObject current = config.getJSONObject("error");
			String name1 = current.getString("name");
			String name2 = last.getString("name");
			String message1 = current.getString("message");
			String message2 = last.getString("message");
			String cause1 = current.getString("cause");
			String cause2 = last.getString("cause");
			if(name1.equals(name2) &&
					message1.equals(message2) &&
					cause1.equals(cause2)) {
				sameError = true;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		if (!sameError || (sameError && seconds > TIME_BETWEEN_DUPLICATE_ERRORS_IN_SECONDS)) {
			if (saveErrorToDatabase(config)) {
				if (timer == null) {
					timer = new Timer();
					timer.schedule(new scheduledUploadSyncTask(), SYNC_n_EVERY_SECONDS * 1000);
				}
			} else {
				return false;
			}
		}
		return true;
	}

	class scheduledUploadSyncTask extends TimerTask {
		@Override
		public void run() {
			timer = null;
			tryUploadStoredErrors();
		}
	};

	private boolean saveErrorToDatabase(JSONObject config) {
		try {
			SQLiteDatabase db = getDbConnection(cordova.getActivity()).getWritableDatabase();
			SimpleDateFormat sdf = new SimpleDateFormat(DATE_STRING_FORMAT);
			String currentDateandTime = sdf.format(new Date());
			JSONObject headers = config.getJSONObject("headers");
			JSONObject error = config.getJSONObject("error");

			ContentValues values = new ContentValues();
			values.put(ErrorDatabaseAccess.ErrorEntry.COLUMN_DATE_LOGGED, currentDateandTime);
			values.put(ErrorDatabaseAccess.ErrorEntry.COLUMN_CAUSE, error.getString("cause"));
			values.put(ErrorDatabaseAccess.ErrorEntry.COLUMN_CURRENT_PLATFORM, headers.getString("currentPlatform"));
			values.put(ErrorDatabaseAccess.ErrorEntry.COLUMN_CURRENT_PLATFORM_VERSION, headers.getString("currentPlatformVersion"));
			values.put(ErrorDatabaseAccess.ErrorEntry.COLUMN_MESSAGE, error.getString("message"));
			values.put(ErrorDatabaseAccess.ErrorEntry.COLUMN_NAME, error.getString("name"));
			values.put(ErrorDatabaseAccess.ErrorEntry.COLUMN_STACK_TRACE, error.getString("stackTrace"));
			values.put(ErrorDatabaseAccess.ErrorEntry.COLUMN_USER_ID, headers.getString("userId"));
			values.put(ErrorDatabaseAccess.ErrorEntry.COLUMN_CURRENT_USERNAME, config.getString("currentUsername"));
			values.put(ErrorDatabaseAccess.ErrorEntry.COLUMN_ENDPOINT, config.getString("endpoint"));

			db.insert(ErrorDatabaseAccess.ErrorEntry.TABLE_NAME, null, values);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	private boolean tryLogErrorOnServer(JSONObject config) {
		ConnectivityManager cm = (ConnectivityManager) cordova.getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);

		NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
		if (activeNetwork == null || !activeNetwork.isConnectedOrConnecting()) {
			return false;
		}
		try {
			if (makeRequest(config))
				return true;
			return false;
		} catch (Exception e) {
			return false;
		}
	}

	public static boolean makeRequest(JSONObject params) throws Exception {
		String url = params.getString("endpoint");
		URL object = new URL(url);
		JSONObject headers = params.getJSONObject("headers");
		JSONObject error = params.getJSONObject("error");
		JSONObject body = new JSONObject();
		body.put("Name", error.getString("name"));
		body.put("Message", error.getString("message"));
		body.put("Cause", error.getString("cause"));
		body.put("StackTrace", error.getString("stackTrace"));
		body.put("CurrentUsername", params.getString("currentUsername"));


		HttpURLConnection con = (HttpURLConnection) object.openConnection();
		con.setDoOutput(true);
		con.setDoInput(true);
		con.setRequestProperty("Content-Type", "application/json");
		con.setRequestProperty("Accept", "application/json");
		con.setRequestMethod("POST");
		con.setRequestProperty("X-UserID", headers.getString("userId"));
		con.setRequestProperty("X-Current-Platform", headers.getString("currentPlatform"));
		con.setRequestProperty("X-Current-Platform-Version", headers.getString("currentPlatformVersion"));

		OutputStreamWriter wr = new OutputStreamWriter(con.getOutputStream());
		wr.write(body.toString());
		wr.flush();

		int HttpResult = con.getResponseCode();
		if (HttpResult == HttpURLConnection.HTTP_OK) {
			return true;
		} else {
			return false;
		}
	}
}