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
	final String START_SCANNING = "START_SCANNING";

	final String SCAN_RESULT = "SCAN_RESULT";
	final String MY_ACTION = "org.limitstate.intent.BroadcastIntentPlugin.SCAN_RESULT";

	private final String DW_PKG_NAME = "com.symbol.datawedge";
	private final String DW_INTENT_SUPPORT_VERSION = "6.3";

    private static final String SCAN = "scan";
    private static final String LISTEN = "listen";

	CallbackContext callbackContext = null;
	JSONArray requestArgs = null;
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

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		Log.d(TAG, "BroadcastIntentPlugin.initialize called!");
		super.initialize(cordova, webView);

		myActivityName = cordova.getActivity().getComponentName().getClassName();
		Log.d(TAG, "BroadcastIntentPlugin.createDataWedgeProfile myActivityName=" + myActivityName);

		myPackageName = cordova.getActivity().getComponentName().getPackageName();
		Log.d(TAG, "BroadcastIntentPlugin.createDataWedgeProfile myPackageName=" + myPackageName);

		//All the DataWedge version does not support creating the profile using the DataWedge intent API.
		//To avoid crashes on the device, make sure to check the DtaaWedge version before creating the profile.
		int result = -1;
		String versionCurrent="";
		// Find out current DW version, if the version is 6.3 or higher then we know it support intent config
		// Then we can send CartScan profile via intent
		try {
			PackageInfo pInfo = cordova.getActivity().getPackageManager().getPackageInfo(DW_PKG_NAME, PackageManager.GET_META_DATA);
			versionCurrent = pInfo.versionName;
			Log.i(TAG, "BroadcastIntentPlugin.initialize: versionCurrent=" + versionCurrent);

			result = compareVersionString(versionCurrent, DW_INTENT_SUPPORT_VERSION);
			Log.i(TAG, "BroadcastIntentPlugin.initialize: result=" + result);
		} catch (PackageManager.NameNotFoundException e1) {
			Log.e(TAG, "BroadcastIntentPlugin.initialize: DW_PKG_NAME=" + DW_PKG_NAME + " NameNotFoundException:", e1);
		}
		if (result >= 0) {
			createDataWedgeProfile();
		}
		Log.d(TAG, "BroadcastIntentPlugin.initialize returned!");
	}

    @Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        this.callbackContext = callbackContext;
        this.requestArgs = args;

        Log.d(TAG, "BroadcastIntentPlugin.execute called! action=" + action);
		if (action.equals(SCAN)) {
			try {
				IntentFilter filter = new IntentFilter();
				filter.addCategory(Intent.CATEGORY_DEFAULT);
				filter.addAction(MY_ACTION);

				Context context = this.cordova.getActivity().getApplicationContext();
				context.registerReceiver(myBroadcastReceiver, filter);
				Log.d(TAG, "BroadcastIntentPlugin.execute: receiver for action=" + MY_ACTION + " registred!");

				Intent i = new Intent();
				i.setAction(ACTION);
				i.putExtra(SOFT_SCAN_TRIGGER, START_SCANNING);
				this.cordova.getActivity().sendBroadcast(i);
				Log.d(TAG, "BroadcastIntentPlugin.execute returned: SOFT_SCAN_TRIGGER, START_SCANNING sent!");
			} catch (Exception e) {
				Log.e(TAG, "BroadcastIntentPlugin.execute: Exception occured:" + e.getMessage());
				e.printStackTrace();
				sendError(e.getMessage());
			}
			Log.d(TAG, "BroadcastIntentPlugin.execute returned!");
			return true;
		} else if (action.equals(LISTEN)) {
			try {
				IntentFilter filter = new IntentFilter();
				filter.addCategory(Intent.CATEGORY_DEFAULT);
				filter.addAction(MY_ACTION);

				Context context = this.cordova.getActivity().getApplicationContext();
				context.registerReceiver(myBroadcastReceiver, filter);
				Log.d(TAG, "BroadcastIntentPlugin.execute: receiver for action=" + MY_ACTION + " registred!");
			} catch (Exception e) {
				Log.e(TAG, "BroadcastIntentPlugin.execute: Exception occured:" + e.getMessage());
				e.printStackTrace();
				sendError(e.getMessage());
			}
			Log.d(TAG, "BroadcastIntentPlugin.execute returned!");
			return true;
		} else {
			Log.e(TAG, "BroadcastIntentPlugin.execute returned invalid action=" + action);
			return false;
		}
	}

	private BroadcastReceiver myBroadcastReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG, "BroadcastIntentPlugin.BroadcastReceiver.onReceive called!");
		    String action = intent.getAction();
		    Bundle b = intent.getExtras();
		    //  This is useful for debugging to verify the format of received intents from DataWedge
		    //for (String key : b.keySet())
		    //{
		    //    Log.v(LOG_TAG, key);
		    //}
		    if (action.equals(MY_ACTION)) {
			//  Received a barcode scan
				try {
					sendScanResult(intent, "via Broadcast");
				} catch (Exception e) {
					//  Catch if the UI does not exist when we receive the broadcast... this is not designed to be a production app
					Log.e(TAG, "BroadcastReceiver.onReceive: Exception occured:" + e.getMessage());
					e.printStackTrace();
					sendError(e.getMessage());
				}
		    }
			Log.d(TAG, "BroadcastIntentPlugin.BroadcastIntentPlugin.BroadcastReceiver.onReceive returned!");
		}
	};

	private void sendScanResult(Intent initiatingIntent, String howDataReceived)
	{
		Log.d(TAG, "BroadcastIntentPlugin.sendScanResult called!");
		String decodedSource = initiatingIntent.getStringExtra("com.symbol.datawedge.source");
		String decodedData = initiatingIntent.getStringExtra("com.symbol.datawedge.data_string");
		String decodedLabelType = initiatingIntent.getStringExtra("com.symbol.datawedge.label_type");

		if (null == decodedSource)
		{
		    decodedSource = initiatingIntent.getStringExtra("com.motorolasolutions.emdk.datawedge.source");
		    decodedData = initiatingIntent.getStringExtra("com.motorolasolutions.emdk.datawedge.data_string");
		    decodedLabelType = initiatingIntent.getStringExtra("com.motorolasolutions.emdk.datawedge.label_type");
		}
		Log.i(TAG, "BroadcastIntentPlugin.sendScanResult: decodedSource=" + decodedSource);
		Log.i(TAG, "BroadcastIntentPlugin.sendScanResult: decodedLabelType=" + decodedLabelType);
		Log.i(TAG, "BroadcastIntentPlugin.sendScanResult: decodedData=" + decodedData);
		if (kPrefixBinary.length() > 0 && decodedData.indexOf(kPrefixBinary) == 0 ){
			int nTmp = decodedData.length() - kPrefixLength;
			byte[] readBytes = initiatingIntent.getByteArrayExtra("com.symbol.datawedge.data_raw");
			if (null == readBytes)
			{
				readBytes = initiatingIntent.getByteArrayExtra("com.motorolasolutions.emdk.datawedge.data_raw");
			}
			if (null != readBytes) {
				byte[] tmpbuf = new byte[readBytes.length - kPrefixLength];
				System.arraycopy(readBytes, kPrefixLength, tmpbuf, 0, readBytes.length - kPrefixLength);
				decodedData.substring(0,kPrefixLength).concat(Base64.encodeToString(tmpbuf, Base64.NO_WRAP));
				tmpbuf = null;
				readBytes = null;
			}
		}

		JSONObject obj = new JSONObject();
		try {
			obj.put("text", decodedData);
			obj.put("format", decodedLabelType);
			sendSuccess(obj);
		} catch (Exception e) {
			Log.e(TAG, "BroadcastIntentPlugin.sendScanResult: Exception occured:" + e.getMessage());
			e.printStackTrace();
			sendError(e.getMessage());
		}
		Log.d(TAG, "BroadcastIntentPlugin.sendScanResult returned!");
	}


	private void sendSuccess(JSONObject info) {
		Log.d(TAG, "BroadcastIntentPlugin.sendSuccess called!");
		try {
			callbackContext.success(info);
		} catch (Exception e) {
			Log.e(TAG, "BroadcastIntentPlugin.sendSuccess: Exception occured:" + e.getMessage());
		}
		Log.d(TAG, "BroadcastIntentPlugin.sendSuccess returned!");
	}

	private void sendError(String error) {
		Log.d(TAG, "BroadcastIntentPlugin.sendError called!");
		try {
			callbackContext.error(error);
		} catch (Exception e) {
			Log.e(TAG, "BroadcastIntentPlugin.sendError: Exception occured:" + e.getMessage());
		}
		Log.d(TAG, "BroadcastIntentPlugin.sendError returned!");
	}

	/**
	 * This code demonstrates how to create the DataWedge programatically and modify the settings.
	 * This code can be skipped if the profile is created on the DataWedge manaually and pushed to different device though MDM
	 */
	public void createDataWedgeProfile()
	{
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

			configBundle.putString(PROFILE_NAME, "BroadcastIntentPlugin");
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
			Log.e(TAG, "BroadcastIntentPlugin.createDataWedgeProfile: Exception occured:" + e.getMessage());
		}

		//TO recieve the scanned via intent, the keystroke must disabled.
		try {
			Bundle configBundle = new Bundle();
			Bundle bConfig = new Bundle();
			Bundle bParams = new Bundle();

			bParams.putString("keystroke_output_enabled", "false");

			configBundle.putString(PROFILE_NAME, "BroadcastIntentPlugin");
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
			Log.e(TAG, "BroadcastIntentPlugin.createDataWedgeProfile: Exception occured:" + e.getMessage());
		}
		Log.d(TAG, "BroadcastIntentPlugin.createDataWedgeProfile returned!");
	}

    //DataWedge version comparision
    private int compareVersionString(String v1, String v2) {

        try {

            if (v1.equals(v2)) {
                return 0;
            }

            if (v1.length() == 0 || v2.length() == 0) {
                return -1;
            }

            v1 = v1.replaceAll("\\s", "");
            v2 = v2.replaceAll("\\s", "");
            String[] a1 = v1.split("\\.");
            String[] a2 = v2.split("\\.");
            List<String> l1 = Arrays.asList(a1);
            List<String> l2 = Arrays.asList(a2);

            int i = 0;
            while (true) {
                Double d1 = null;
                Double d2 = null;

                try{
                    String temp1 = l1.get(i).replaceAll("[\\D]", "");

                    String split1[] = l1.get(i).split("[\\D]");
                    if(split1 != null) {
                        temp1 = split1[0];
                    }

                    d1 = Double.parseDouble(temp1);
                }catch(IndexOutOfBoundsException e){
                    if(e !=null) {
                        Log.d(TAG, "Exception: " + e.getMessage());
                    }
                } catch(NumberFormatException e) {
                    if(e !=null) {
                        Log.d(TAG, "Exception: " + e.getMessage());
                    }
                }

                try{
                    String temp2 = l2.get(i).replaceAll("[\\D]", "");

                    String split2[] = l2.get(i).split("[\\D]");
                    if(split2 != null) {
                        temp2 = split2[0];
                    }
                    d2 = Double.parseDouble(temp2);
                }catch(IndexOutOfBoundsException e){
                    if(e !=null) {
                        Log.d(TAG, "Exception: " + e.getMessage());
                    }
                }catch(NumberFormatException e) {
                    if(e !=null) {
                        Log.d(TAG, "Exception: " + e.getMessage());
                    }
                }

                Log.d("VersionCheck", "d1==== " + d1);
                Log.d("VersionCheck", "d2==== " + d2);
                if (d1 != null && d2 != null) {
                    if (d1.doubleValue() > d2.doubleValue()) {
                        return 1;
                    } else if (d1.doubleValue() < d2.doubleValue()) {
                        return -1;
                    }
                } else if (d2 == null && d1 != null) {
                    if (d1.doubleValue() > 0) {
                        return 1;
                    }
                } else if (d1 == null && d2 != null) {
                    if (d2.doubleValue() > 0) {
                        return -1;
                    }
                } else {
                    break;
                }
                i++;
            }

        } catch(Exception ex) {
            if(ex !=null) {
                Log.e(TAG, "Exception: " + ex.getMessage());
            }
        }
        return 0;
    }
}
