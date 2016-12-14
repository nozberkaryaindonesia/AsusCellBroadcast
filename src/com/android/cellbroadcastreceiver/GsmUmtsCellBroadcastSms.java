package com.android.cellbroadcastreceiver;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.cellbroadcastreceiver.zen.AsusZenUiUtils;
import com.android.internal.telephony.TelephonyIntents;

public class GsmUmtsCellBroadcastSms extends Activity {
    // debug data
    private static final String TAG = "GsmUmtsCellBroadcastSms";
    private static final boolean DEBUG = true;
    private static IccCardStatusReceiver mSimStatusListener = null;
    
    private ActionBar mActionBar;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(DEBUG) Log.d(TAG, "onCreate:");
        setContentView(R.layout.gsm_cellbroadcast_activity);
        AsusZenUiUtils.updateStatusActionBarBackgroundView(this, false,
                R.color.asus_status_action_bar_background);
        mActionBar = getActionBar();
        if (mActionBar != null) {
            mActionBar.setDisplayHomeAsUpEnabled(true);
        }

        if(CellBroadcastUtils.hasIccCard(this)) {
            getFragmentManager().beginTransaction().replace(R.id.gsm_cellbroadcast_main,
                    new GsmUmtsCellBroadcastSmsFragment()).commit();
        } else {
            Log.w(TAG,"We don't have SIM Card insert, stop CBS Activity!");
            Toast.makeText(this, getString(R.string.toast_without_sim_toast), Toast.LENGTH_LONG).show();
            finish();
        }
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        AsusZenUiUtils.updateStatusActionBarBackgroundView(this, false,
                R.color.asus_status_action_bar_background);
    }

    @Override
    protected void onStart() {
        super.onStart();
        //ASUS BEGIN, dion_yang:for support hot switch SIM
        if (!CellBroadcastUtils.hasIccCard(this)) {
            if(DEBUG) Log.d(TAG, "onStart:No IccCard!We finish this Activity!");
            Toast.makeText(this, getString(R.string.toast_without_sim_toast), Toast.LENGTH_LONG).show();
            finish();
        } else {
            mSimStatusListener = new IccCardStatusReceiver(this);
            registerReceiver(mSimStatusListener, new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED));
        }
        //ASUS END, dion_yang:for support hot switch SIM
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home: {
            finish();
            return true;
        }
        default: {
            return super.onOptionsItemSelected(item);
        }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(DEBUG) Log.d(TAG, "onStop");
        //ASUS BEGIN, dion_yang:for support hot switch SIM
        if(mSimStatusListener != null) {
            try{
               unregisterReceiver(mSimStatusListener);
            } catch(Exception e){
               Log.e(TAG, " " + e);
            }
            mSimStatusListener = null;
        }
        //ASUS END, dion_yang:for support hot switch SIM
    }
}
