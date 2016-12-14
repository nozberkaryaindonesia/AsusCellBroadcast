/* 
 * ASUS BEGIN, dion_yang:for support hot switch SIM
 * We need to monitor SIM status, and updating hot switch event 
 * to all SIM dependence function in Messaging
 */

package com.android.cellbroadcastreceiver;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

class IccCardStatusReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "IccCardStatusReceiver"; 
    private static final boolean DEBUG = true;
    private Activity mActivity = null;
    private final String SUBSCRIPTION_KEY = "subscription";

    public IccCardStatusReceiver(Activity activity) {
        mActivity = activity;
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String iccCardStatus = null;
        if(DEBUG) Log.d(LOG_TAG, "onReceive: receiving SIM status change!");
        if (!intent.getAction().equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
            Log.w(LOG_TAG, "Wrong intent. Ignore");
            return;
        }

        if(intent.getExtras() != null) {
            Log.d(LOG_TAG, "Received extras " + intent.getExtras().getString(IccCardConstants.INTENT_KEY_ICC_STATE));
            iccCardStatus = intent.getExtras().getString(IccCardConstants.INTENT_KEY_ICC_STATE);
            //ASUS BEGIN, dion_yang:20121024:Fix after radio on/off, RIL would override SIM status from LOADED to READY
            if(iccCardStatus != null && !iccCardStatus.equals(IccCardConstants.INTENT_VALUE_ICC_LOADED)) {
                if(iccCardStatus.equals(IccCardConstants.INTENT_VALUE_ICC_READY)) {
                    Log.d(LOG_TAG, "SIM was READY, may be we are after airplane mode!");
                } else {
                    if(mActivity != null) {
                        if((mActivity instanceof GsmUmtsCellBroadcastSms) ||
                           ((mActivity instanceof GsmUmtsCellBroadcastSmsDual) && (!(CellBroadcastUtils.isSimReady(0))) && (!(CellBroadcastUtils.isSimReady(1))) )
                          ){
                            if (!CellBroadcastUtils.isMultiSimDSDA()) {
                                Log.w(LOG_TAG, "IccCard was not loaded! Finish this GsmUmtsCellBroadcastSms");
                                Toast.makeText(CellBroadcastReceiverApp.getApplication(), CellBroadcastReceiverApp.getApplication().getString(R.string.toast_sim_status_error_toast, iccCardStatus), Toast.LENGTH_LONG).show();
                                mActivity.finish();
                            } else {
                                long slot = intent.getExtras().getLong(SUBSCRIPTION_KEY, 0);
                                if (slot == PhoneConstants.SUB1) {
                                    Log.w(LOG_TAG, "IccCard was not loaded! Finish this GsmUmtsCellBroadcastSms DSDA slot= " + slot);
                                    Toast.makeText(CellBroadcastReceiverApp.getApplication(), CellBroadcastReceiverApp.getApplication().getString(R.string.toast_sim_status_error_toast, iccCardStatus), Toast.LENGTH_LONG).show();
                                    mActivity.finish();
                                }
                            }
                        } else if(mActivity instanceof CellBroadcastSettings) {
                            Log.w(LOG_TAG, "IccCard was not loaded! we are updating MessagingPreferenceActivity UI");
                            Toast.makeText(CellBroadcastReceiverApp.getApplication(), CellBroadcastReceiverApp.getApplication().getString(R.string.toast_sim_status_error_toast, iccCardStatus), Toast.LENGTH_LONG).show();
                            mActivity.finish();
                        }
                    } else {
                        Log.e(LOG_TAG, "Activity was null!");
                    }
                }
            }
            //ASUS END, dion_yang:20121024:Fix after radio on/off, RIL would override SIM status from LOADED to READY
        }
    }
}
