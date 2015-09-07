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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.util.Log;

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
import java.util.Date;

public class SmsReceiver extends BroadcastReceiver {
    private String TAG = SmsReceiver.class.getSimpleName();

    /*
     * Empty constructor needed
     */
    public SmsReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String link = prefs.getString(context.getResources().getString(R.string.serverlink_key), "");
        String serverKey = prefs.getString(context.getResources().getString(R.string.serverkey_key), "");

        Bundle bundle = intent.getExtras();
        SmsMessage[] msgs = null;
        String str = "";
        String from = "";
        if (bundle != null) {
            Object[] pdus = (Object[]) bundle.get("pdus");
            msgs = new SmsMessage[pdus.length];
            // For every SMS message received
            for (int i = 0; i < msgs.length; i++) {
                // Convert object array to SmsMessage
                msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                // Get sender's phone number
                from = msgs[i].getOriginatingAddress();
                // Fetch the text message
                str += msgs[i].getMessageBody();
                // Newline
                str += "\n";
            }
            // Send SMS to server
            String sendParams;
            try {
                sendParams = "KEY=" + URLEncoder.encode(serverKey, "UTF-8") +
                        "&FROM=" + URLEncoder.encode(from, "UTF-8") +
                        "&DATA=" + URLEncoder.encode(str, "UTF-8");
                new DownloadWebpageTask().execute(link, sendParams, from);
            } catch (Exception e) {
                writeToLog("ERROR: Can't send SMS to server!");
                e.printStackTrace();
            }

        }
    }

    /*
     * Handles sending received messages to the server.
     */
    private class DownloadWebpageTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... data) {
            try {
                writeToLog("SMS received from " + data[2] + "!");
                return postURL(data[0], data[1]);
            } catch (IOException e) {
                writeToLog("ERROR: No response from server. URL or key may be invalid!");
                return "No response from server. URL or key may be invalid.";
            }
        }
        @Override
        protected void onPostExecute(String result) {
            writeToLog("SMS forwarded to server." + result);
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
            conn.setReadTimeout(10000);//10 sec read timeout
            conn.setConnectTimeout(15000);//15 sec connection timeout
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