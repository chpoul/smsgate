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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

public class SettingsFragment extends PreferenceFragment {
    SharedPreferences prefs;
    Preference linkEdit;
    Preference keyEdit;
    Preference freqEdit;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.prefs);
        prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

        linkEdit = findPreference(getResources().getString(R.string.serverlink_key));
        keyEdit = findPreference(getResources().getString(R.string.serverkey_key));
        freqEdit = findPreference(getResources().getString(R.string.serverfreq_key));

        setSums();

        // Makes sure text fields are updated when changing stuff
        linkEdit.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary(newValue.toString());
                return true;
            }
        });
        keyEdit.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary(newValue.toString());
                return true;
            }
        });
        freqEdit.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary(newValue.toString());
                return true;
            }
        });
    }

    /*
     * Updates text fields with values from SharedPreferences
     */
    private void setSums() {
        linkEdit.setSummary(prefs.getString(getResources().getString(R.string.serverlink_key),""));
        keyEdit.setSummary(prefs.getString(getResources().getString(R.string.serverkey_key),""));
        freqEdit.setSummary(prefs.getString(getResources().getString(R.string.serverfreq_key),""));
    }
}
