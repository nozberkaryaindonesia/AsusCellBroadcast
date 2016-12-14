package com.android.cellbroadcastreceiver;

import java.util.Locale;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.Log;
import android.view.MenuItem;


public class GsmUmtsCellBroadcastSmsFragment2 extends GsmUmtsCellBroadcastSmsFragmentBase {
    private static final String TAG = "GsmUmtsCellBroadcastSmsFragment2";

    // These may be referred by BroadcastConfigInstantiatorService
    public static final String PREF_CATEGORY_ALERTSETTINGS = "emergency_alert_settings2";
    public static final String PREF_CATEGORY_CUSTOMSETTINGS = "custom_setting2";
    public static final String PREF_KEY_PRESIDENTIALALERTS = "preference_key_presidential_alerts_for_sim2";
    public static final String PREF_KEY_ALLALERTS = "preference_key_all_alerts_for_sim2";
    public static final String PREF_KEY_EXTREMEALERTS = "preference_key_extreme_alerts_for_sim2";
    public static final String PREF_KEY_SEVEREALERTS = "preference_key_severe_alerts_for_sim2";
    public static final String PREF_KEY_AMBERALERTS = "preference_key_amber_alerts_for_sim2";
    public static final String PREF_KEY_ENABLE_EMBERGENCYALERTS = "preference_key_enable_emergency_alerts_for_sim2";;
    public static final String PREF_KEY_ENABLE_ALERTVIBRATION = "preference_key_alert_vibration_setting_for_sim2";
    public static final String PREF_KEY_ENABLE_ETWSTESTALERTS = "preference_key_enable_etws_test_alerts_for_sim2";
    public static final String PREF_KEY_ENABLE_CMASTESTALERTS = "preference_key_enable_cmas_test_alerts_for_sim2";
    public static final String PREF_KEY_SMSCBLANGUAGE = "list_language2";

    private static String sLanguageNow = "";//OliverOu@20160107: TT-722138;//OliverOu@20160107: TT-722138

    public GsmUmtsCellBroadcastSmsFragment2() {
        mSlotId = SLOTID_SLOT2;

        mPrefCategoryAlertSettings = PREF_CATEGORY_ALERTSETTINGS;
        mPrefCategoryCustomSettings = PREF_CATEGORY_CUSTOMSETTINGS;

        mPrefKeyPresidentialAlerts = PREF_KEY_PRESIDENTIALALERTS;
        mPrefKeyAllAlerts = PREF_KEY_ALLALERTS;
        mPrefKeyExtremeAlerts = PREF_KEY_EXTREMEALERTS;
        mPrefKeySevereAlerts = PREF_KEY_SEVEREALERTS;
        mPrefKeyAmberAlerts = PREF_KEY_AMBERALERTS;
        mPrefKeyEnableEmergencyAlerts = PREF_KEY_ENABLE_EMBERGENCYALERTS;
        mPrefKeyEnableAlertVibration = PREF_KEY_ENABLE_ALERTVIBRATION;
        mPrefKeyEnableETWSTestAlerts = PREF_KEY_ENABLE_ETWSTESTALERTS;
        mPrefKeyEnableCMASTestAlerts = PREF_KEY_ENABLE_CMASTESTALERTS;

        // General SMS-CB preference key
        mPrefKeySMSCBLanguage = PREF_KEY_SMSCBLANGUAGE;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.gsm_umts_cell_broadcast_sms_2);
        // +++ OliverOu@20160107, TT-722138
        ListPreference prefLanguage = (ListPreference) findPreference(mPrefKeySMSCBLanguage);
        if (prefLanguage != null) {
            initLanguageNow(getActivity());
            updateLanguagePref();
            setLanguageListener();
        }
        // --- OliverOu@20160107, TT-722138
    }

    // +++ OliverOu@20160107: TT-722138
    private void updateLanguagePref() {
        ListPreference prefLanguage = (ListPreference) findPreference(mPrefKeySMSCBLanguage);
        Resources r = getResources();
        SharedPreferences sp = getActivity().getSharedPreferences("com.android.cellbroadcastreceiver_preferences", getActivity().MODE_PRIVATE);
        String languageScale = sp.getString(PREF_KEY_SMSCBLANGUAGE, "default");
        Log.d(TAG, "languageScale:" + languageScale);
        if(!languageScale.isEmpty()){
            int index = prefLanguage.findIndexOfValue(languageScale);
            String[] languageNames = r.getStringArray(R.array.list_gsm_language_entries);
            prefLanguage.setSummary(languageNames[index]);
        }
    }

    private void setLanguageListener(){
        final ListPreference prefLanguage = (ListPreference) findPreference(mPrefKeySMSCBLanguage);
        prefLanguage.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Log.d(TAG, "preference=" + preference + ", newValue=" + newValue);
                setLanguageNow((String)newValue);
                int index = prefLanguage.findIndexOfValue(getLanguageNow());
                String[] languageNames = getResources().getStringArray(R.array.list_gsm_language_entries);
                prefLanguage.setSummary(languageNames[index]);
                return true;
            }
        });
    }

    private void setLanguageNow(String language) {
        sLanguageNow = language;
    }

    private String getLanguageNow() {
        return sLanguageNow;
    }

    public static void initLanguageNow(Context context) {
        // load preference
        SharedPreferences sp = context.getSharedPreferences("com.asus.cellbroadcastreceiver_preferences", 0);
        String languageScale = sp.getString(PREF_KEY_SMSCBLANGUAGE, "default");
        sLanguageNow = languageScale;
    }
    // --- OliverOu@20160107: TT-722138

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (SLOTID_SLOT2 != GsmUmtsCellBroadcastSmsDual.SELECED_SIM_TAB) return false;
        if(DEBUG) Log.d(TAG, "onContextItemSelected MenuItem:"+item);

        if(item.toString().equals(getString(R.string.edit))) {
            if(DEBUG) Log.d(TAG, "onContextItemSelected edit");
            createEditDialog();
        } else if(item.toString().equals(getString(R.string.delete))) {
            if(DEBUG) Log.d(TAG, "onContextItemSelected delete");
            createDeleteDialog();
        }
        return super.onContextItemSelected(item);
    }
}
