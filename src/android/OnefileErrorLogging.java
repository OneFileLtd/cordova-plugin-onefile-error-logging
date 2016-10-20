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
import java.text.SimpleDateFormat;
import java.util.Date;

public class OnefileErrorLogging extends CordovaPlugin {

	BroadcastReceiver receiver;
	ConnectivityManager conMan;
	ErrorDatabaseAccess errorDb;

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
								tryUploadStoredErrors();
							}
						});
					}
				}
			};
			webView.getContext().registerReceiver(this.receiver, intentFilter);
		}
	}

	private JSONObject getNextError() throws JSONException {
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
				ErrorDatabaseAccess.ErrorEntry.COLUMN_ENDPOINT
		};

		String sortOrder = ErrorDatabaseAccess.ErrorEntry.COLUMN_DATE_LOGGED + " ASC";

		Cursor c = db.query(
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

			obj.put("error", err);
			obj.put("headers", headers);
			return obj;
		}
		return null;
	}

	private void tryUploadStoredErrors() {
		try {
			boolean hasError;
			do {
				JSONObject error = getNextError();
				if (error == null)
					hasError = false;
				else {
					hasError = true;
					if (tryLogErrorOnServer(error)) {
						removeError(error);
					}
				}
			} while (hasError);
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
		Log.i("OnefileErrorLogging", "Im in here!");
		if (action.equals("logError")) {
			final JSONObject config = args.getJSONObject(0);
			cordova.getThreadPool().execute(new Runnable() {
				public void run() {
					if (tryLogErrorOnServer(config)) {
						callbackContext.success();
					} else if (saveErrorToDatabase(config)) {
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

	private boolean saveErrorToDatabase(JSONObject config) {
		try {
			SQLiteDatabase db = getDbConnection(cordova.getActivity()).getWritableDatabase();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
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
			Log.i("OnefileErrorLogging", "Unable to save error: " + e.getMessage());
			return false;
		}
		return true;
	}

	private boolean tryLogErrorOnServer(JSONObject config) {
		ConnectivityManager cm = (ConnectivityManager) cordova.getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);

		NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
		if (activeNetwork != null && activeNetwork.isConnectedOrConnecting()) {
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
			Log.i("OnefileErrorLogging", "Error Code: " + HttpResult);
			return false;
		}
	}
}