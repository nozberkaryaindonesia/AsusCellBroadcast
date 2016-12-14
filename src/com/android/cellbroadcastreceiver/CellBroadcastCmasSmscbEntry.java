
package com.android.cellbroadcastreceiver;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

public class CellBroadcastCmasSmscbEntry extends PreferenceActivity {

    private static final String TAG = CellBroadcastCmasSmscbEntry.class.getSimpleName();

    public static final String GSM_UMTS_CMAS = "pref_key_gsm_umts_cmas";

    public static final String GSM_UMTS_SMSCB = "pref_key_gsm_umts_smscb";

    private Preference mGsmUmtsCMASPref;

    private Preference mGsmUmtsSMSCBPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_cmas_smscb_entry);
        mGsmUmtsCMASPref = findPreference(GSM_UMTS_CMAS);
        mGsmUmtsSMSCBPref = findPreference(GSM_UMTS_SMSCB);
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    @Deprecated
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        boolean isShowMultiSimSetting = CellBroadcastUtils.isMultiSimEnabled()
                & CellBroadcastUtils.isSupportSim2CellBroadcast();
        if (preference == mGsmUmtsCMASPref) {
            startCellBroadcastSetting(true, isShowMultiSimSetting);
        } else if (preference == mGsmUmtsSMSCBPref) {
            startCellBroadcastSetting(false, isShowMultiSimSetting);
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private void startCellBroadcastSetting(boolean configCmas, boolean dualSim) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        if (dualSim) {
            intent.setComponent(new ComponentName("com.android.cellbroadcastreceiver",
                    "com.android.cellbroadcastreceiver.GsmUmtsCellBroadcastSmsDual"));
        } else {
            intent.setComponent(new ComponentName("com.android.cellbroadcastreceiver",
                    "com.android.cellbroadcastreceiver.GsmUmtsCellBroadcastSms"));
        }
        intent.putExtra("config_cmas", configCmas);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            Log.d(TAG,
                    "startCellBroadcastSetting: configCmas=" + configCmas + ", dualSim=" + dualSim);
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            Log.e(TAG, "" + ignored);
        }
    }
}
