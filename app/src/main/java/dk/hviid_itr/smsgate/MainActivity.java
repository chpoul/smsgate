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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    public static String LOGFILEDIR = "/SMSgate";
    public static String LOGFILEPATH = LOGFILEDIR + "/log.txt";
    private boolean isCRON;
    private boolean isRunning;
    private String linkStr;
    private String serverKeyStr;
    private SharedPreferences prefs;
    private TextView statusTxt;
    private TextView logTxt;
    private File appDir;
    private File logFile;
    Timer checkLogTimer;
    Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mContext = this;

        appDir = new File(Environment.getExternalStorageDirectory() + LOGFILEDIR);
        logFile = new File(Environment.getExternalStorageDirectory() + LOGFILEPATH);

        if (!appDir.exists() && !appDir.mkdirs()) {
            Log.w(TAG, "No SMSgate directory created!");
        }
        try {
            if (!logFile.exists() && !logFile.createNewFile()) {
                Log.w(TAG, "No log file created!");
            }
        } catch (IOException e) { e.printStackTrace(); }


        isCRON = false;
        isRunning = false;

        Button cronBtn = (Button) findViewById(R.id.cronBtn);

        statusTxt = (TextView) findViewById(R.id.status);
        logTxt = (TextView) findViewById(R.id.logOutput);
        logTxt.setMovementMethod(new ScrollingMovementMethod());

        logListen(true);
        checkNetwork();
        toggleRun("LAUNCH");
        cronBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                toggleRun("CRON");
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
    @Override
    protected void onResume() {
        super.onResume();
        checkNetwork();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_settings:
                Intent prefIntent = new Intent(this, SettingsActivity.class);
                startActivity(prefIntent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*
     * Toggles status text and starts the listening service
     */
    private void toggleRun(String mode) {
        linkStr = prefs.getString(getResources().getString(R.string.serverlink_key), "");
        serverKeyStr = prefs.getString(getResources().getString(R.string.serverkey_key), "");

        Intent cronIntent = new Intent(getApplicationContext(), ListeningService.class);
        cronIntent.addCategory(ListeningService.TAG);

        cronIntent.putExtra("link", linkStr);
        cronIntent.putExtra("serverKey", serverKeyStr);
        if (mode.equals("CRON") && !isCRON) {
            isCRON = true;
            isRunning = true;
            toggleTxt(isRunning, getString(R.string.cron_run));
            startService(cronIntent);
        } else if (mode.equals("LAUNCH")) {
            // If service is already running, toggle accordingly
            if (stopService(cronIntent)) {
                toggleRun("CRON");
            }
        } else {
            isCRON = false;
            isRunning = false;
            stopService(cronIntent);
            toggleTxt(isRunning, getString(R.string.not_run));
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    readLog();
                }
            }, 2000);
        }
    }

    /*
     * Changes status text
     */
    private void toggleTxt(Boolean run, String txt) {
        if (run) {
            statusTxt.setTextColor(Color.GREEN);
        } else {
            statusTxt.setTextColor(Color.RED);
        }
        statusTxt.setText(txt);
    }

    /*
     * Checks for data connection
     */
    public void checkNetwork() {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isConnected()) {
            Toast.makeText(this, getString(R.string.no_network), Toast.LENGTH_LONG).show();
        }
    }

    /*
     * Starts/stops a task that reads from the logfile every 3. second
     */
    public void logListen(Boolean run) {
        if (run) {
            checkLogTimer = new Timer("CheckLogTimer", true);
            checkLogTimer.scheduleAtFixedRate(new LogTask(), 3000, 3000);
        }
        else {
            checkLogTimer.cancel();
        }
    }

    /*
     * Task that invokes reading from the logfile
     */
    private class LogTask extends TimerTask {
        public void run() {
            if (isRunning) {
                readLog();
            }
        }
    }

    /*
     * Reads from the logfile
     */
    private void readLog() {
        try {
            InputStream is = new FileInputStream(logFile);
            //final String ret = "";
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String receiveString = "";
            StringBuilder stringBuilder = new StringBuilder();
            while ((receiveString = br.readLine()) != null) {
                stringBuilder.append(receiveString + "\n");
            }
            is.close();
            final String ret = stringBuilder.toString();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    logTxt.setText(ret);
                    final int scrollAmount = logTxt.getLayout().getLineTop(logTxt.getLineCount()) - logTxt.getHeight();
                    if (scrollAmount > 0) {
                        logTxt.scrollTo(0, scrollAmount);
                    } else {
                        logTxt.scrollTo(0, 0);
                    }
                }
            });
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found: " + e.toString());
        } catch (IOException e) {
            Log.e(TAG, "Can not read file: " + e.toString());
        }
    }

}
