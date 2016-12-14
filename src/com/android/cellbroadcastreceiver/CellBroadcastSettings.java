/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (c) 2012, The Linux Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.cellbroadcastreceiver;

import android.content.Context;
import com.android.internal.telephony.TelephonyIntents;

import android.app.Activity;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.Settings;
//import android.telephony.MSimTelephonyManager; // Asus Jenpang: porting layer implementation (remove import)
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Settings activity for the cell broadcast receiver.
 */
public class CellBroadcastSettings extends Activity {
    
    private static final String TAG = "CellBroadcastSettings";

    // Duration of alert sound (in seconds).
    public static final String KEY_ALERT_SOUND_DURATION = "alert_sound_duration";

    // Default alert duration (in seconds).
    public static final String ALERT_SOUND_DEFAULT_DURATION = "4";

    // Enable vibration on alert (unless master volume is silent).
    public static final String KEY_ENABLE_ALERT_VIBRATE = "enable_alert_vibrate";

    // Speak contents of alert after playing the alert sound.
    public static final String KEY_ENABLE_ALERT_SPEECH = "enable_alert_speech";

    // Preference category for emergency alert and CMAS settings.
    public static final String KEY_CATEGORY_ALERT_SETTINGS = "category_alert_settings";

    // Preference category for ETWS related settings.
    public static final String KEY_CATEGORY_ETWS_SETTINGS = "category_etws_settings";

    // Whether to display CMAS extreme threat notifications (default is enabled).
    public static final String KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS =
            "enable_cmas_extreme_threat_alerts";

    // Whether to display CMAS severe threat notifications (default is enabled).
    public static final String KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS =
            "enable_cmas_severe_threat_alerts";

    // Whether to display CMAS amber alert messages (default is enabled).
    public static final String KEY_ENABLE_CMAS_AMBER_ALERTS = "enable_cmas_amber_alerts";

    // Preference category for development settings (enabled by settings developer options toggle).
    public static final String KEY_CATEGORY_DEV_SETTINGS = "category_dev_settings";

    // Whether to display ETWS test messages (default is disabled).
    public static final String KEY_ENABLE_ETWS_TEST_ALERTS = "enable_etws_test_alerts";

    // Whether to display CMAS monthly test messages (default is disabled).
    public static final String KEY_ENABLE_CMAS_TEST_ALERTS = "enable_cmas_test_alerts";

    // Preference category for Brazil specific settings.
    public static final String KEY_CATEGORY_BRAZIL_SETTINGS = "category_brazil_settings";

    // Preference key for whether to enable channel 50 notifications
    // Enabled by default for phones sold in Brazil, otherwise this setting may be hidden.
    public static final String KEY_ENABLE_CHANNEL_50_ALERTS = "enable_channel_50_alerts";

    // Preference key for initial opt-in/opt-out dialog.
    public static final String KEY_SHOW_CMAS_OPT_OUT_DIALOG = "show_cmas_opt_out_dialog";

    private static IccCardStatusReceiver mSimStatusListener = null;

    // Alert reminder interval ("once" = single 2 minute reminder).
    public static final String KEY_ALERT_REMINDER_INTERVAL = "alert_reminder_interval";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //ASUS BEGIN, dion_yang:Fix Pad Layout incorrectly issue
        setContentView(R.layout.gsm_cellbroadcast_activity);
        //ASUS END, dion_yang:Fix Pad Layout incorrectly issue
        //setTitle(R.string.app_label);
        if(CellBroadcastUtils.hasIccCard(this)) {
            // Display the fragment as the main content.
            getFragmentManager().beginTransaction().replace(R.id.gsm_cellbroadcast_main,
                    new CellBroadcastSettingsFragment()).commit();
        } else {
            Log.d(TAG, "onCreate:No IccCard!We finish this Activity!");
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        //ASUS BEGIN, dion_yang:for support hot switch SIM
        if(!CellBroadcastUtils.hasIccCard(this)) {
            Log.d(TAG, "onStart:No IccCard!We finish this Activity!");
            Toast.makeText(this, getString(R.string.toast_without_sim_toast), Toast.LENGTH_LONG).show();
            finish();
        } else {
            mSimStatusListener = new IccCardStatusReceiver(this);
            registerReceiver(mSimStatusListener, new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED));
        }
        //ASUS END, dion_yang:for support hot switch SIM
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume:");
        if(CellBroadcastReceiverApp.DEBUG_MEM) CellBroadcastReceiverApp.getApplication().dumpMemoryInfo(TAG);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
        //ASUS BEGIN, dion_yang:for support hot switch SIM
        if(mSimStatusListener != null) {
            unregisterReceiver(mSimStatusListener);
        }
        mSimStatusListener = null;
        //ASUS END, dion_yang:for support hot switch SIM
        if(CellBroadcastReceiverApp.DEBUG_MEM) CellBroadcastReceiverApp.getApplication().dumpMemoryInfo(TAG);
    }

    /**
     * New fragment-style implementation of preferences.
     */
    public static class CellBroadcastSettingsFragment extends PreferenceFragment {

        private static final String TAG = "CellBroadcastSettingsFragment";

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            if (CellBroadcastUtils.isMultiSimEnabled() && !CellBroadcastUtils.isMultiSimDSDA()) {
                addPreferencesFromResource(R.xml.preferencesmultisim);
            } else {
                addPreferencesFromResource(R.xml.preferences);
            }

            PreferenceScreen preferenceScreen = getPreferenceScreen();

            // Handler for settings that require us to reconfigure enabled channels in radio
            Preference.OnPreferenceChangeListener startConfigServiceListener =
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference pref, Object newValue) {
                            Log.d(TAG, "onPreferenceChange pref="+pref.getKey()+" value="+pref.getPreferenceManager().getSharedPreferences().getBoolean(pref.getKey(), false)
                                    +" newValue="+newValue);
                            CellBroadcastReceiver.startConfigService(pref.getContext());
                            return true;
                        }
                    };

            // Show extra settings when developer options is enabled in settings.
            boolean enableDevSettings = Settings.Global.getInt(getActivity().getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;

            Resources res = getResources();
            boolean showEtwsSettings = res.getBoolean(R.bool.show_etws_settings);
            boolean showEtwsTestSettings = res.getBoolean(R.bool.show_etws_test_settings);

            // +++ lide_yang@20131023: AT&T CDR-ECB-660
            String prefKeyEnableEmergencyAlertsForSim1 = CellBroadcastUtils.getPrefKeyEnableEmergencyAlerts(0);
            // --- lide_yang@20131023: AT&T CDR-ECB-660

            // Emergency alert preference category (general and CMAS preferences).
            PreferenceCategory alertCategory = (PreferenceCategory)
                    findPreference(KEY_CATEGORY_ALERT_SETTINGS);

            // alert reminder interval
            ListPreference interval = (ListPreference) findPreference(KEY_ALERT_REMINDER_INTERVAL);
            if(interval != null){
                interval.setSummary(interval.getEntry());
                interval.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference pref, Object newValue) {
                        final ListPreference listPref = (ListPreference) pref;
                        final int idx = listPref.findIndexOfValue((String) newValue);
                        listPref.setSummary(listPref.getEntries()[idx]);
                        return true;
                    }
                });
        	}

            // Show alert settings and ETWS categories for ETWS builds and developer mode.
            if (enableDevSettings || showEtwsSettings || showEtwsTestSettings) {
                // enable/disable all alerts
                // +++ lide_yang@20131023: AT&T CDR-ECB-660
                Preference enablePwsAlerts = findPreference(prefKeyEnableEmergencyAlertsForSim1);
                // --- lide_yang@20131023: AT&T CDR-ECB-660

                if (enablePwsAlerts != null) {
                    enablePwsAlerts.setOnPreferenceChangeListener(startConfigServiceListener);
                }

                // alert sound duration
                ListPreference duration = (ListPreference) findPreference(KEY_ALERT_SOUND_DURATION);
                duration.setSummary(duration.getEntry());
                duration.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference pref, Object newValue) {
                        final ListPreference listPref = (ListPreference) pref;
                        final int idx = listPref.findIndexOfValue((String) newValue);
                        listPref.setSummary(listPref.getEntries()[idx]);
                        return true;
                    }
                });
            } else {
                // Remove general emergency alert preference items (not shown for CMAS builds).
                // +++ lide_yang@20131023: AT&T CDR-ECB-660
                alertCategory.removePreference(findPreference(prefKeyEnableEmergencyAlertsForSim1));
                // --- lide_yang@20131023: AT&T CDR-ECB-660

                alertCategory.removePreference(findPreference(KEY_ALERT_SOUND_DURATION));
                alertCategory.removePreference(findPreference(KEY_ENABLE_ALERT_SPEECH));
                // Remove ETWS preference category.
                preferenceScreen.removePreference(findPreference(KEY_CATEGORY_ETWS_SETTINGS));
            }

            if (!res.getBoolean(R.bool.show_cmas_settings)) {
                // Remove CMAS preference items in emergency alert category.
                alertCategory.removePreference(
                        findPreference(KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS));
                alertCategory.removePreference(
                        findPreference(KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS));
                alertCategory.removePreference(findPreference(KEY_ENABLE_CMAS_AMBER_ALERTS));
            }

            TelephonyManager tm = (TelephonyManager) getActivity().getSystemService(
                    Context.TELEPHONY_SERVICE);

            boolean enableChannel50Support = res.getBoolean(R.bool.show_brazil_ch50_settings) ||
                    "br".equals(tm.getSimCountryIso());

            if (!enableChannel50Support) {
                preferenceScreen.removePreference(findPreference(KEY_CATEGORY_BRAZIL_SETTINGS));
            }
            if (!enableDevSettings || !res.getBoolean(R.bool.show_dev_settings)) {
                preferenceScreen.removePreference(findPreference(KEY_CATEGORY_DEV_SETTINGS));
            }

            if (!showEtwsTestSettings || !res.getBoolean(R.bool.show_etws_test_settings)) {
                preferenceScreen.removePreference(findPreference(KEY_CATEGORY_ETWS_SETTINGS));
            }

            if (!CellBroadcastUtils.isMultiSimEnabled()) {
                Preference enableChannel50Alerts = findPreference(KEY_ENABLE_CHANNEL_50_ALERTS);
                if (enableChannel50Alerts != null) {
                    enableChannel50Alerts.setOnPreferenceChangeListener(startConfigServiceListener);
                }
            }
            Preference enableEtwsAlerts = findPreference(KEY_ENABLE_ETWS_TEST_ALERTS);
            if (enableEtwsAlerts != null) {
                enableEtwsAlerts.setOnPreferenceChangeListener(startConfigServiceListener);
            }
            Preference enableCmasExtremeAlerts =
                    findPreference(KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS);
            if (enableCmasExtremeAlerts != null) {
                enableCmasExtremeAlerts.setOnPreferenceChangeListener(startConfigServiceListener);
            }
            Preference enableCmasSevereAlerts =
                    findPreference(KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS);
            if (enableCmasSevereAlerts != null) {
                enableCmasSevereAlerts.setOnPreferenceChangeListener(startConfigServiceListener);
            }
            Preference enableCmasAmberAlerts = findPreference(KEY_ENABLE_CMAS_AMBER_ALERTS);
            if (enableCmasAmberAlerts != null) {
                enableCmasAmberAlerts.setOnPreferenceChangeListener(startConfigServiceListener);
            }
            Preference enableCmasTestAlerts = findPreference(KEY_ENABLE_CMAS_TEST_ALERTS);
            if (enableCmasTestAlerts != null) {
                enableCmasTestAlerts.setOnPreferenceChangeListener(startConfigServiceListener);
            }
        }
    }
}
