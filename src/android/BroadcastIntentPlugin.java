package org.limitstate.intent;

import android.app.Activity;

import android.util.Log;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import android.util.Base64;

import java.lang.String;
import java.lang.System;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CordovaActivity;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import static android.content.ContentValues.TAG;

public class BroadcastIntentPlugin extends CordovaPlugin {
	// Debugging
	private static final String TAG = "BroadcastIntentPlugin";
	private static final boolean D = false;

	final String ACTION = "com.symbol.datawedge.api.ACTION";
	final String SWITCH = "com.symbol.datawedge.api.SWITCH_TO_PROFILE";
	final String CREATE_PROFILE = "com.symbol.datawedge.api.CREATE_PROFILE";
	final String PROFILE_NAME = "PROFILE_NAME";
	final String PROFILE_STATUS = "PROFILE_ENABLED";
	final String CONFIG_MODE = "CONFIG_MODE";
	final String CONFIG_MODE_UPDATE = "UPDATE";
	final String CONFIG_MODE_CREATE = "CREATE_IF_NOT_EXIST";
	final String SET_CONFIG = "com.symbol.datawedge.api.SET_CONFIG";
	final String SOFT_SCAN_TRIGGER = "com.symbol.datawedge.api.SOFT_SCAN_TRIGGER";
	final String MY_ACTION = "org.limitstate.intent.BroadcastIntentPlugin.SCAN_RESULT";

	// BCR states
	// Constants that indicate the current connection state
	public static final int STATE_NONE = 0;       // we're doing nothing
	public static final int STATE_READY = 1; // BCR reader ready
	public static final int STATE_READING = 2; //reading BCR reader
	public static final int STATE_READ = 3; //read received BCR reader
	public static final int STATE_ERROR = 4; // Error
	public static final int STATE_DESTROYED = 5; // BCR reader destroyed
	private int mState;

	// Local BCR adapter
	private BCRBroadcastReceiver myBroadcastReceiver = null;
	private boolean bCodeScanReceiverRegistered = false;

	// BCR actions
	private static final String ACTION_INIT = "init";
	private static final String LISTEN = "listen";
	private static final String DESTROY = "destroy";
	private static final String ACTION_GETSTATE = "getState";

	// Member fields
	private JSONObject szComData;
	private String mProfileName = "BroadcastIntentPlugin";
	String myActivityName = null;
	String myPackageName = null;

	// some specials for QR-codes --------------------------

	// after the prefix comes *real* binary data (to be taken as is and to be base64 encoded)
	// has to at least end with alphanumerics, so the QR-Code is multi-part

	//private static final String kPrefixBinary = "";
	private static final String kPrefixBinary = "#LSAD";


	// total prefix-length can be more, e.g. with "#LSAD01" or "#LSAD02" for sub-types

	//private static final int kPrefixLength = 0;
	private static final int kPrefixLength = 7;

	// end of specials for QR-codes ------------------------

	/**
	 * Create a BCR reader
	 */
	public BroadcastIntentPlugin() {
		this.setState(STATE_NONE);
	}

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		if (D) Log.d(TAG, "BroadcastIntentPlugin.initialize called!");
		super.initialize(cordova, webView);
	}

	@Override
	public void onDestroy() {
		this.setState(STATE_DESTROYED);
		if (myBroadcastReceiver != null && this.bCodeScanReceiverRegistered) {
			this.cordova.getActivity().unregisterReceiver(myBroadcastReceiver);
		}
		if(D) Log.d(TAG, "Destroyed");
		super.onDestroy();
	}

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		if (D) Log.d(TAG, "BroadcastIntentPlugin.execute called! action=" + action);
		if (ACTION_INIT.equals(action)) {
			final String profileName = args.getString(0);
			int result = -1;
			String profileResult = "";
			String versionCurrent = "";

			if (!profileName.isEmpty()) {
				mProfileName = profileName;
			}
			
			myActivityName = cordova.getActivity().getComponentName().getClassName();
			if (D) Log.d(TAG, "BroadcastIntentPlugin.createDataWedgeProfile myActivityName=" + myActivityName);

			myPackageName = cordova.getActivity().getComponentName().getPackageName();
			if (D) Log.d(TAG, "BroadcastIntentPlugin.createDataWedgeProfile myPackageName=" + myPackageName);

			profileResult = createDataWedgeProfile();
			if (profileResult.equals("Success")) {
				// init BroadcastReceiver
				if (myBroadcastReceiver == null && !this.bCodeScanReceiverRegistered) {
					myBroadcastReceiver = new BCRBroadcastReceiver();
					// Register for broadcasts to listen to scan button
					IntentFilter filter = new IntentFilter();
					filter.addCategory(Intent.CATEGORY_DEFAULT);
					filter.addAction(MY_ACTION);
					cordova.getActivity().registerReceiver(myBroadcastReceiver, filter);
					this.bCodeScanReceiverRegistered = true;
					if (D) Log.d(TAG, "BroadcastIntentPlugin.execute: receiver for action=" + MY_ACTION + " registred!");
				}
				this.setState(STATE_READY);
				callbackContext.success();
			} else {
				callbackContext.error("Datawedge profile error: " + profileResult);
			}
		} else if (action.equals(DESTROY)) {
			if ((myBroadcastReceiver != null)) {
				this.cordova.getActivity().unregisterReceiver(myBroadcastReceiver);
				this.bCodeScanReceiverRegistered = false;
			}
			callbackContext.success();
			this.onDestroy();
		} else if (action.equals(LISTEN)  && (mState != STATE_READING) ) {
			this.setState(STATE_READING);
			this.cordova.getThreadPool().execute(new Runnable() {
				public void run() {
					while (true) {
						if (mState == STATE_READ) {
							try {
								PluginResult result = new PluginResult(PluginResult.Status.OK, szComData);
								result.setKeepCallback(true);
								callbackContext.sendPluginResult(result);
								mState = STATE_READING;

								Thread.sleep(500);
							} catch (InterruptedException e) {
								mState = STATE_ERROR;
								callbackContext.error(e.getMessage());
								break;
							}
						} else if ((mState == STATE_DESTROYED)||(mState == STATE_ERROR))  {
							callbackContext.error("Not Read");
							break;
						} else {
							try {
								Thread.sleep(100);
							} catch (InterruptedException e) {
								callbackContext.error(e.getMessage());
								break;
							}
						}
					}
				}
			});
			if (D) Log.d(TAG, "BroadcastIntentPlugin.listen returned!");
		} else if (ACTION_GETSTATE.equals(action)) {
			JSONObject stateJSON = new JSONObject();
			try {
				stateJSON.put("state", mState);
				callbackContext.success(stateJSON);
			} catch (JSONException e) {
				Log.e(TAG, e.getMessage());
				this.setState(STATE_ERROR);
				callbackContext.error(e.getMessage());
			}
		} else {
			Log.e(TAG, "BroadcastIntentPlugin.execute returned invalid action=" + action);
			callbackContext.error("Action '" + action + "' not supported (now) state = " + mState);
		}
		return true;
	}

	// The BroadcastReceiver that listens BCR feedback and trigger
	private class BCRBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (D) Log.d(TAG, "BroadcastIntentPlugin.BroadcastReceiver.onReceive called!");
			String action = intent.getAction();
		
			//  This is useful for debugging to verify the format of received intents from DataWedge
			// Bundle b = intent.getExtras();
			//for (String key : b.keySet())
			//{
			//    Log.v(LOG_TAG, key);
			//}
			if (action.equals(MY_ACTION)) {
			//  Received a barcode scan
				try {
					szComData = getScanResult(intent);
					mState = STATE_READ;
				} catch (Exception e) {
					//  Catch if the UI does not exist when we receive the broadcast... this is not designed to be a production app
					Log.e(TAG, "BroadcastReceiver.onReceive: Exception occured:" + e.getMessage());
					mState = STATE_ERROR;
				}
			}
			if (D) Log.d(TAG, "BroadcastIntentPlugin.BroadcastIntentPlugin.BroadcastReceiver.onReceive returned!");
		}
	};

	private JSONObject getScanResult(Intent initiatingIntent)
	{
		if (D) Log.d(TAG, "BroadcastIntentPlugin.getScanResult called!");
		String decodedSource = initiatingIntent.getStringExtra("com.symbol.datawedge.source");
		String decodedData = initiatingIntent.getStringExtra("com.symbol.datawedge.data_string");
		String decodedLabelType = initiatingIntent.getStringExtra("com.symbol.datawedge.label_type");

		if (null == decodedSource)
		{
			decodedSource = initiatingIntent.getStringExtra("com.motorolasolutions.emdk.datawedge.source");
			decodedData = initiatingIntent.getStringExtra("com.motorolasolutions.emdk.datawedge.data_string");
			decodedLabelType = initiatingIntent.getStringExtra("com.motorolasolutions.emdk.datawedge.label_type");
		}
		Log.i(TAG, "BroadcastIntentPlugin.getScanResult: decodedSource=" + decodedSource);
		Log.i(TAG, "BroadcastIntentPlugin.getScanResult: decodedLabelType=" + decodedLabelType);
		Log.i(TAG, "BroadcastIntentPlugin.getScanResult: decodedData=" + decodedData);
		if (kPrefixBinary.length() > 0 && decodedData.indexOf(kPrefixBinary) == 0 ){
			int nTmp = decodedData.length() - kPrefixLength;
			byte[] readBytes = null;
			ArrayList<byte[]> rawData =
				(ArrayList <byte[]>) initiatingIntent.getSerializableExtra("com.symbol.datawedge.decode_data");
			if (null == rawData)
			{
				 rawData = (ArrayList <byte[]>) initiatingIntent.getSerializableExtra("com.motorolasolutions.emdk.datawedge.decode_data");
			}
			if (null != rawData)
			{
				readBytes = rawData.get(0);
			}
			if (null != readBytes)
			{
				byte[] tmpbuf = new byte[readBytes.length - kPrefixLength];
				System.arraycopy(readBytes, kPrefixLength, tmpbuf, 0, readBytes.length - kPrefixLength);
				decodedData = decodedData.substring(0,kPrefixLength).concat(Base64.encodeToString(tmpbuf, Base64.NO_WRAP));
				tmpbuf = null;
				readBytes = null;
			}
		}

		JSONObject obj = new JSONObject();
		try {
			obj.put("text", decodedData);
			obj.put("format", decodedLabelType);
			if(D) Log.d(TAG, "Read result = " + obj.get("text") );
		} catch (Exception e) {
			Log.e(TAG, "BroadcastIntentPlugin.getScanResult: Exception occured:" + e.getMessage());
		}
		if (D) Log.d(TAG, "BroadcastIntentPlugin.getScanResult returned!");
		return obj;
	}

	/**
	 * This code demonstrates how to create the DataWedge programatically and modify the settings.
	 * This code can be skipped if the profile is created on the DataWedge manaually and pushed to different device though MDM
	 */
	public String createDataWedgeProfile()
	{
		String result = "Success";
		Log.d(TAG, "BroadcastIntentPlugin.createDataWedgeProfile called!");
		//Create profile if doesn't exit and update the required settings
		try {
			Bundle configBundle = new Bundle();
			Bundle bConfig = new Bundle();
			Bundle bParams = new Bundle();
			Bundle bundleApp1 = new Bundle();

			bParams.putString("scanner_selection", "auto");
			bParams.putString("intent_output_enabled", "true");
			bParams.putString("intent_action", MY_ACTION);
			bParams.putString("intent_category", Intent.CATEGORY_DEFAULT);
			bParams.putString("intent_delivery", "2");

			configBundle.putString(PROFILE_NAME, mProfileName);
			configBundle.putString(PROFILE_STATUS, "true");
			configBundle.putString(CONFIG_MODE, CONFIG_MODE_CREATE);

			bundleApp1.putString("PACKAGE_NAME", myPackageName);
			bundleApp1.putStringArray("ACTIVITY_LIST", new String[]{myActivityName});

			configBundle.putParcelableArray("APP_LIST", new Bundle[]{bundleApp1});

			bConfig.putString("PLUGIN_NAME", "INTENT");
			bConfig.putString("RESET_CONFIG", "false");

			bConfig.putBundle("PARAM_LIST", bParams);
			configBundle.putBundle("PLUGIN_CONFIG", bConfig);

			Intent i = new Intent();
			i.setAction(ACTION);
			i.putExtra(SET_CONFIG, configBundle);
			this.cordova.getActivity().sendBroadcast(i);
		} catch (Exception e) {
			result = e.getMessage();
			Log.e(TAG, "BroadcastIntentPlugin.createDataWedgeProfile: Exception occured:" + e.getMessage());
		}

		//TO recieve the scanned via intent, the keystroke must disabled.
		try {
			Bundle configBundle = new Bundle();
			Bundle bConfig = new Bundle();
			Bundle bParams = new Bundle();

			bParams.putString("keystroke_output_enabled", "false");

			configBundle.putString(PROFILE_NAME, mProfileName);
			configBundle.putString(PROFILE_STATUS, "true");
			configBundle.putString(CONFIG_MODE, CONFIG_MODE_UPDATE);

			bConfig.putString("PLUGIN_NAME", "KEYSTROKE");
			bConfig.putString("RESET_CONFIG", "false");

			bConfig.putBundle("PARAM_LIST", bParams);
			configBundle.putBundle("PLUGIN_CONFIG", bConfig);

			Intent i = new Intent();
			i.setAction(ACTION);
			i.putExtra(SET_CONFIG, configBundle);
			this.cordova.getActivity().sendBroadcast(i);
		} catch (Exception e) {
			result = e.getMessage();
			Log.e(TAG, "BroadcastIntentPlugin.createDataWedgeProfile: Exception occured:" + e.getMessage());
		}
		if (D) Log.d(TAG, "BroadcastIntentPlugin.createDataWedgeProfile returned!");
		return result;
	}

	private void setState(int state) {
		this.mState = state;
	}
}
