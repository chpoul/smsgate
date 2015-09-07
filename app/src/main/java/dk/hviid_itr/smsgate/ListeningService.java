/*
 * Copyright 2015 HVIID ITR
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dk.hviid_itr.smsgate;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.*;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class ListeningService extends Service {

    public static final String TAG = "ListeningService";
    public static final String SERVICE_WAKE = "Service Connectivity";
    public static String SMS_SENT = "SMS_SENT";

    public boolean run = true;
    private String urlParameters;
    private String link;
    private String serverKey;
    private int freq;
    private SharedPreferences prefs;
    private Context mContext;
    private List<String> sendIds;
    Timer checkTimer;
    private BroadcastReceiver smsSendReceiver;
    private BroadcastReceiver smsReceiver;
    private PowerManager.WakeLock mWakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "GO!");
        writeToLog("Message listening service started...");

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        link = prefs.getString(getResources().getString(R.string.serverlink_key), "");
        serverKey = prefs.getString(getResources().getString(R.string.serverkey_key), "");
        freq = Integer.valueOf(prefs.getString(getResources().getString(R.string.serverfreq_key), "30"));

        //sendIds = prefs.getStringSet("sendIds", null);
        sendIds = new ArrayList<>();

        mContext = getApplicationContext();
        createWakeLock();
        acquireWakeLock();

        smsReceiver = new SmsReceiver();
        smsSendReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        writeToLog("SMS send!");
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        writeToLog("Error sending SMS - ResultCode: " + getResultCode());
                        break;
                }
            }
        };
        registerReceiver(smsReceiver, new IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION));
        registerReceiver(smsSendReceiver, new IntentFilter(SMS_SENT));

        checkTimer = new Timer("CheckMsgTimer", true);
        checkTimer.scheduleAtFixedRate(new CheckTask(), 5000, (freq * 1000));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        checkTimer.cancel();
        releaseWakeLock();
        unregisterReceiver(smsReceiver);
        unregisterReceiver(smsSendReceiver);
        Log.d(TAG, "ET TU, BRUTE!");
        writeToLog("Message listening service stopped!");
    }

    /*
     * Repeating timed task that checks for messages from the server
     */
    private class CheckTask extends TimerTask {
        public void run() {
            try {
                writeToLog("Checking for new messages...");
                urlParameters = "KEY=" + URLEncoder.encode(serverKey, "UTF-8");
                new DownloadWebpageTask().execute(link, urlParameters);
            } catch (Exception e) {
                writeToLog("ERROR: Message checking timer not running!");
                e.printStackTrace();
            }
        }
    }

    /*
     * Handles getting JSON data and sending send confirmations.
     */
    private class DownloadWebpageTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... data) {
            try {
                return postURL(data[0], data[1]);
            } catch (IOException e) {
                writeToLog("ERROR: No response from server. URL or key may be invalid!");
                return "No response from server. URL or key may be invalid.";
            }
        }
        @Override
        protected void onPostExecute(String result) {
            JSONArray JSONresult = null;
            int JSONlen;
            try {
                JSONresult = new JSONArray(result);
            } catch (Exception e) {
                Log.d(TAG, "Result is not JSON!");
            }
            if (JSONresult != null) {
                JSONlen = JSONresult.length();
                for (int i = 0; i < JSONlen; ++i) {
                    try {
                        JSONObject jsonObj = JSONresult.getJSONObject(i);
                        Log.d(TAG, "SMS LOADED:\nID: " + String.valueOf(jsonObj.getInt("ID")) + "\nNO:" + jsonObj.getString("NO") + "\nMSG: " + jsonObj.getString("MSG"));
                        sendMsg(jsonObj.getString("ID"), jsonObj.getString("NO"), jsonObj.getString("MSG"));
                    } catch (JSONException e) {
                        writeToLog("ERROR: Invalid message data!");
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /*
     * Handles sending POST requests
     */
    private String postURL(String myURL, String myParams) throws IOException {
        InputStream is = null;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(myURL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(10000);
            conn.setConnectTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Content-Length", "" + Integer.toString(myParams.getBytes().length));
            conn.setRequestProperty("Content-Language", "da-DK");
            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            //Send request
            DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
            wr.writeBytes(myParams);
            wr.flush();
            wr.close();
            //Get Response
            is = conn.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String line;
            StringBuffer response = new StringBuffer();
            while ((line = rd.readLine()) != null) {
                response.append(line);
                response.append('\r');
            }
            rd.close();
            return response.toString();
        } finally {
            if (is != null) {
                is.close();
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /*
     * Handles WakeLocks, so the service keep listening for messages instead of letting the phone sleep
     */
    private synchronized void createWakeLock() {
        // Create a new wake lock if we haven't made one yet.
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, SERVICE_WAKE);
            mWakeLock.setReferenceCounted(false);
        }
    }
    private void acquireWakeLock() {
        // It's okay to double-acquire this because we are not using it
        // in reference-counted mode.
        mWakeLock.acquire();
    }
    private void releaseWakeLock() {
        // Don't release the wake lock if it hasn't been created and acquired.
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    /*
     * Sends SMS, unless the SMS id is in the list of recently send messages.
     */
    public void sendMsg(String Id, String No, String Msg) {
        if (sendIds.contains(Id)) {
            writeToLog("SMS (id:" + Id + ") already sent!");
            return;
        }
        PendingIntent sentPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(SMS_SENT), 0);
        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(No, null, Msg, sentPendingIntent, null);
        writeToLog("Sending SMS to " + No + "...");
        returnToSender(Id);
        //logMsg(Id);
    }

    /*
     * Sends POST request to server containing server key and id of a send SMS
     */
    private void returnToSender(String id) {
        String sendParams;
        try {
            sendParams = "KEY=" + URLEncoder.encode(serverKey, "UTF-8") +
                    "&ID=" + URLEncoder.encode(id, "UTF-8") +
                    "&SEND=" + URLEncoder.encode("true", "UTF-8");
            new DownloadWebpageTask().execute(link, sendParams);
        } catch (Exception e) {
            writeToLog("ERROR: Can't notify server of send SMS!");
            e.printStackTrace();
        }
    }
/*
    private synchronized void logMsg(String Id) {
        sendIds.add(Id);
        int lastElem = sendIds.size();
        int firstElem;
        int elemCount = 1000;
        if (lastElem >= elemCount) {
            firstElem = lastElem - elemCount;
        }
        else {
            firstElem = 0;
        }
        List<String> tmpIds = sendIds.subList(firstElem, lastElem);
        sendIds.clear();
        sendIds = tmpIds;
    }
*/

    /*
     *  Writes to the log file that is presented in MainActivity
     */
    private void writeToLog(String logMsg) {
        String logPath = Environment.getExternalStorageDirectory() + MainActivity.LOGFILEPATH;
        String ret;
        String now = DateFormat.getTimeInstance().format(new Date());
        try {
            File logFile = new File(logPath);
            InputStream is = new FileInputStream(logFile);
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String receiveString = "";
            StringBuilder stringBuilder = new StringBuilder();
            while ((receiveString = br.readLine()) != null) {
                stringBuilder.append(receiveString + "\n");
            }
            is.close();
            ret = stringBuilder.toString();
            OutputStream os = new FileOutputStream(logFile);
            OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
            int retLen = ret.length();
            int logLen = 10000;
            if (retLen > logLen) {
                ret = ret.substring(retLen - logLen);
            }
            osw.write(ret + now + "#: " + logMsg);
            osw.close();
        } catch (IOException e) {
            Log.e(TAG, "Log read/write failed: " + e.toString());
        }
    }

}