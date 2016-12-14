/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (c) 2012, The Linux Foundation. All rights reserved.
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

package com.android.cellbroadcastreceiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.provider.Telephony;
//import android.telephony.CellBroadcastMessage;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaSmsCbProgramData;
import android.util.Log;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;


public class CellBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "CellBroadcastReceiver";
    static final boolean DBG = true;    // STOPSHIP: change to false before ship

    private static final String GET_LATEST_CB_AREA_INFO_ACTION =
            "android.cellbroadcastreceiver.GET_LATEST_CB_AREA_INFO";

    //ASUS BEGIN, dion_yang:For background initial CBS Channel setting
    protected static final String ACTION_UPDATING_CBS_CHANNEL="com.android.mms.cbs_updating";
    //ASUS END, dion_yang

    @Override
    public void onReceive(Context context, Intent intent) {
        onReceiveWithPrivilege(context, intent, false);
    }

    protected void onReceiveWithPrivilege(Context context, Intent intent, boolean privileged) {
        if (DBG) log("onReceive " + intent);

        String action = intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            if (DBG) log("Registering for ServiceState updates");
          //TODO: Need to update from KK to L
//            AsusTelephonyManager tm = (AsusTelephonyManager) context.getSystemService(
//                    Context.TELEPHONY_SERVICE);
//            tm.listen(new ServiceStateListener(context.getApplicationContext()),
//                    PhoneStateListener.LISTEN_SERVICE_STATE);
          TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
          tm.listen(new ServiceStateListener(context.getApplicationContext()),
                  PhoneStateListener.LISTEN_SERVICE_STATE);
        } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
            boolean airplaneModeOn = intent.getBooleanExtra("state", false);
            if (DBG) log("airplaneModeOn: " + airplaneModeOn);
            if (!airplaneModeOn) {
                startConfigService(context);
            // +++ dion_yang@20140102:Fix Airplane mode didn't clear dup-detect list
            } else {
                intent.setClass(context, CellBroadcastAlertService.class);
                context.startService(intent);
            }
            // --- dion_yang@20140102:Fix Airplane mode didn't clear dup-detect list
        } else if (Telephony.Sms.Intents.SMS_EMERGENCY_CB_RECEIVED_ACTION.equals(action) ||
                Telephony.Sms.Intents.SMS_CB_RECEIVED_ACTION.equals(action)) {
            if (DBG) log("Receiving CBS message");
            // If 'privileged' is false, it means that the intent was delivered to the base
            // no-permissions receiver class.  If we get an SMS_CB_RECEIVED message that way, it
            // means someone has tried to spoof the message by delivering it outside the normal
            // permission-checked route, so we just ignore it.
            if (privileged) {
                intent.setClass(context, CellBroadcastAlertService.class);
                context.startService(intent);
            } else {
                Log.e(TAG, "ignoring unprivileged action received " + action);
            }
        } else if (Telephony.Sms.Intents.SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED_ACTION
                .equals(action)) {
            if (privileged) {
                CdmaSmsCbProgramData[] programDataList = (CdmaSmsCbProgramData[])
                        intent.getParcelableArrayExtra("program_data_list");
                if (programDataList != null) {
                    handleCdmaSmsCbProgramData(context, programDataList);
                } else {
                    Log.e(TAG, "SCPD intent received with no program_data_list");
                }
            } else {
                Log.e(TAG, "ignoring unprivileged action received " + action);
            }
        } else if (GET_LATEST_CB_AREA_INFO_ACTION.equals(action)) {
            if (privileged) {
                long subId = intent.getLongExtra(PhoneConstants.SUBSCRIPTION_KEY, -1);
                Log.v(TAG,"GET_LATEST_CB_AREA_INFO_ACTION subId= " + subId);
                CellBroadcastMessage message = CellBroadcastReceiverApp.getLatestAreaInfo(subId);
                if (message != null) {
                    Intent areaInfoIntent = new Intent(
                            CellBroadcastAlertService.CB_AREA_INFO_RECEIVED_ACTION);
                    areaInfoIntent.putExtra("message", message);
                    context.sendBroadcastAsUser(areaInfoIntent, UserHandle.ALL,
                            android.Manifest.permission.READ_PHONE_STATE);
                }
            } else {
                Log.e(TAG, "caller missing READ_PHONE_STATE permission, returning");
            }
        //ASUS BEGIN, dion_yang,20121214:Fix boot completed didn't initial CBS
        } else if(TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
            String iccCardStatus = null;
            if(intent.getExtras() != null) {
                Log.d(TAG, "Received extras " + intent.getExtras().getString(IccCardConstants.INTENT_KEY_ICC_STATE));
                iccCardStatus = intent.getExtras().getString(IccCardConstants.INTENT_KEY_ICC_STATE);
            }
            if(iccCardStatus == null) {
                Log.w(TAG, "No IccCardStatus in this intent. Ignore it!");
                return;
            } else if(iccCardStatus.equals(IccCardConstants.INTENT_VALUE_ICC_LOADED)){
                Log.v(TAG, "IccCard LOADED, we start updating Service");
                // +++ dion_yang@20131026:For ATT requirement
                startConfigService(context);
                // --- dion_yang@20131026:For ATT requirement
                return;
            }
            Log.e(TAG, "Waiting SIM until LOADED. Ignore this intent");
        //ASUS END, dion_yang,20121214:Fix boot completed didn't initial CBS
        } else {
            Log.w(TAG, "onReceive() unexpected action " + action);
        }
    }

    /**
     * Handle Service Category Program Data message.
     * TODO: Send Service Category Program Results response message to sender
     *
     * @param context
     * @param programDataList
     */
    private void handleCdmaSmsCbProgramData(Context context,
            CdmaSmsCbProgramData[] programDataList) {
        for (CdmaSmsCbProgramData programData : programDataList) {
            switch (programData.getOperation()) {
                case CdmaSmsCbProgramData.OPERATION_ADD_CATEGORY:
                    tryCdmaSetCategory(context, programData.getCategory(), true);
                    break;

                case CdmaSmsCbProgramData.OPERATION_DELETE_CATEGORY:
                    tryCdmaSetCategory(context, programData.getCategory(), false);
                    break;

                case CdmaSmsCbProgramData.OPERATION_CLEAR_CATEGORIES:
                    tryCdmaSetCategory(context,
                            SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT, false);
                    tryCdmaSetCategory(context,
                            SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT, false);
                    tryCdmaSetCategory(context,
                            SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY, false);
                    tryCdmaSetCategory(context,
                            SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE, false);
                    break;

                default:
                    loge("Ignoring unknown SCPD operation " + programData.getOperation());
            }
        }
    }

    private void tryCdmaSetCategory(Context context, int category, boolean enable) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        switch (category) {
            case SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT:
                sharedPrefs.edit().putBoolean(
                        CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, enable)
                        .apply();
                break;

            case SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT:
                sharedPrefs.edit().putBoolean(
                        CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, enable)
                        .apply();
                break;

            case SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY:
                sharedPrefs.edit().putBoolean(
                        CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, enable).apply();
                break;

            case SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE:
                sharedPrefs.edit().putBoolean(
                        CellBroadcastSettings.KEY_ENABLE_CMAS_TEST_ALERTS, enable).apply();
                break;

            default:
                Log.w(TAG, "Ignoring SCPD command to " + (enable ? "enable" : "disable")
                        + " alerts in category " + category);
        }
    }

    /**
     * Tell {@link CellBroadcastConfigService} to enable the CB channels.
     * @param context the broadcast receiver context
     */
    static void startConfigService(Context context) {
        // +++ dion_yang@20131026:For ATT requirement
        /*String action = CellBroadcastConfigService.ACTION_ENABLE_CHANNELS_GSM;
        if (phoneIsCdma()) {
            action = CellBroadcastConfigService.ACTION_ENABLE_CHANNELS_CDMA;
        }
        Intent serviceIntent = new Intent(action, null,
                context, CellBroadcastConfigService.class);
        context.startService(serviceIntent);*/
        Intent startService = new Intent();
        startService.setAction(ACTION_UPDATING_CBS_CHANNEL);
        startService.setClassName("com.android.cellbroadcastreceiver", "com.android.cellbroadcastreceiver.BroadcastConfigInstantiatorService");
        context.startService(startService);
        // --- dion_yang@20131026:For ATT requirement
    }

    static void startConfigService(Context context,int slotId) {
        // +++ dion_yang@20131026:For ATT requirement
        /*String action = CellBroadcastConfigService.ACTION_ENABLE_CHANNELS_GSM;
        if (phoneIsCdma(subscription)) {
            action = CellBroadcastConfigService.ACTION_ENABLE_CHANNELS_CDMA;
        }
        Intent serviceIntent = new Intent(action, null,
                context, CellBroadcastConfigService.class);
        // Asus Jenpang begin: porting layer implementation
        serviceIntent.putExtra(AsusCBDependency.SUBSCRIPTION_KEY, subscription);
        //serviceIntent.putExtra(MSimConstants.SUBSCRIPTION_KEY, subscription);
        // Asus Jenpang end: porting layer implementation
        context.startService(serviceIntent);*/
        //long suId = SubscriptionManager.getSubId(slotId)[0];
        int suId = SubscriptionManager.getSubId(slotId)[0];
        Intent startService = new Intent();
        startService.putExtra(PhoneConstants.SLOT_KEY, slotId);
        startService.putExtra(PhoneConstants.SUBSCRIPTION_KEY, suId);
        startService.setAction(ACTION_UPDATING_CBS_CHANNEL);
        startService.setClassName("com.android.cellbroadcastreceiver", "com.android.cellbroadcastreceiver.BroadcastConfigInstantiatorService");
        context.startService(startService);
        Log.v(TAG,"startConfigService BroadcastConfigInstantiatorService slotId= " + slotId + ", subId= " + suId);
        // --- dion_yang@20131026:For ATT requirement
    }

    /**
     * @return true if the phone is a CDMA phone type
     */
    static boolean phoneIsCdma() {
        boolean isCdma = false;
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            if (phone != null) {
                isCdma = (phone.getActivePhoneType() == TelephonyManager.PHONE_TYPE_CDMA);
            }
        } catch (RemoteException e) {
            Log.w(TAG, "phone.getActivePhoneType() failed", e);
        }
        return isCdma;
    }

    private static class ServiceStateListener extends PhoneStateListener {
        private final Context mContext;
        private int mServiceState = -1;
        private int mSubscription = 0; // dion_yang@20130913:Add QC patch and implement dependency

        ServiceStateListener(Context context) {
            mContext = context;
        }

        ServiceStateListener(Context context, int subscription) {
            mContext = context;
            mSubscription = subscription; // dion_yang@20130913:Add QC patch and implement dependency
        }

        @Override
        public void onServiceStateChanged(ServiceState ss) {
            int newState = ss.getState();
            if (newState != mServiceState) {
                Log.d(TAG, "Service state changed! " + newState + " Full: " + ss);
                mServiceState = newState;
                if (newState == ServiceState.STATE_IN_SERVICE ||
                        newState == ServiceState.STATE_EMERGENCY_ONLY) {
                    if (CellBroadcastUtils.isMultiSimEnabled() && !CellBroadcastUtils.isMultiSimDSDA()) { // dion_yang@20130913:Add QC patch and implement dependency
                        Log.d(TAG, "Service state changed for Subscription: " + mSubscription);
                        startConfigService(mContext, mSubscription);
                    } else {
                        startConfigService(mContext);
                    }
                }
            }
        }
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    private static void loge(String msg) {
        Log.e(TAG, msg);
    }
}
