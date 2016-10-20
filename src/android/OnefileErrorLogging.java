package uk.co.onefile.onefileeportfolio.errorlogging;

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

public class OnefileErrorLogging extends CordovaPlugin {

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		// your init code here
	}

	@Override
	public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
		Log.i("OnefileErrorLogging", "Im in here!");
		if (action.equals("logError")) {
			final JSONObject config = args.getJSONObject(0);
			cordova.getThreadPool().execute(new Runnable() {
				public void run() {
					if(tryLogError(config)){
						callbackContext.success();
					} else	{
						callbackContext.success();
					}
				}
			});
			return true;
		}
		return false;
	}

	private boolean tryLogError(JSONObject config) {
		try {
			makeRequest(config);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	public static boolean makeRequest(JSONObject params) throws Exception
	{
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