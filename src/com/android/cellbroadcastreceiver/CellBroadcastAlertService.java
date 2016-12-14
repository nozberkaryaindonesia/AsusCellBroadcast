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

import com.android.internal.telephony.PhoneConstants;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Telephony;
//import android.telephony.CellBroadcastMessage;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
//import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;

//import org.apache.commons.codec.binary.Hex;//Oliver@20151102, porting to M

/// M: Added by cenxingcan@wind-mobile.com 20160815 NCC PWS Switch don't work start.
import com.android.internal.telephony.gsm.SmsCbConstants;
/// M: Added by cenxingcan@wind-mobile.com 20160815 NCC PWS Switch don't work end.

/**
 * This service manages the display and animation of broadcast messages.
 * Emergency messages display with a flashing animated exclamation mark icon,
 * and an alert tone is played when the alert is first shown to the user
 * (but not when the user views a previously received broadcast).
 */
public class CellBroadcastAlertService extends Service {
    private static final String TAG = "CellBroadcastAlertService";

    /** Intent action to display alert dialog/notification, after verifying the alert is new. */
    static final String SHOW_NEW_ALERT_ACTION = "cellbroadcastreceiver.SHOW_NEW_ALERT";

    /** Use the same notification ID for non-emergency alerts. */
    static final int NOTIFICATION_ID = 1;

    /** system property to enable/disable broadcast duplicate detecion.  */
    private static final String CB_DUP_DETECTION = "persist.cb.dup_detection";

    /** Check for system property to enable/disable duplicate detection.  */
    static boolean mUseDupDetection = SystemProperties.getBoolean(CB_DUP_DETECTION, true);

    /** Sticky broadcast for latest area info broadcast received. */
    static final String CB_AREA_INFO_RECEIVED_ACTION =
            "android.cellbroadcastreceiver.CB_AREA_INFO_RECEIVED";

    /** Container for message ID and geographical scope, for duplicate message detection. */
    private static final class MessageServiceCategoryAndScope {
        private final int mServiceCategory;
        private final int mSerialNumber;
        private final SmsCbLocation mLocation;

        MessageServiceCategoryAndScope(int serviceCategory, int serialNumber,
                SmsCbLocation location) {
            mServiceCategory = serviceCategory;
            mSerialNumber = serialNumber;
            mLocation = location;
        }

        @Override
        public int hashCode() {
            return mLocation.hashCode() + 5 * mServiceCategory + 7 * mSerialNumber;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o instanceof MessageServiceCategoryAndScope) {
                MessageServiceCategoryAndScope other = (MessageServiceCategoryAndScope) o;
                return (mServiceCategory == other.mServiceCategory &&
                        mSerialNumber == other.mSerialNumber &&
                        mLocation.equals(other.mLocation));
            }
            return false;
        }

        @Override
        public String toString() {
            return "{mServiceCategory: " + mServiceCategory + " serial number: " + mSerialNumber +
                    " location: " + mLocation.toString() + '}';
        }
    }

    /** Cache of received message IDs, for duplicate message detection. */
    private static final HashSet<MessageServiceCategoryAndScope> sCmasIdSet =
            new HashSet<MessageServiceCategoryAndScope>(8);

    /** Maximum number of message IDs to save before removing the oldest message ID. */
    private static final int MAX_MESSAGE_ID_SIZE = 65535;

    /** List of message IDs received, for removing oldest ID when max message IDs are received. */
    private static final ArrayList<MessageServiceCategoryAndScope> sCmasIdList =
            new ArrayList<MessageServiceCategoryAndScope>(8);

    /** Index of message ID to replace with new message ID when max message IDs are received. */
    private static int sCmasIdListIndex = 0;

    // +++ OliverOu@20151216, [MES-61][MES-139]NCC CB 911/919 behavior
    public static boolean is911Or919 = false;
    public static boolean sNeedCheckNotification = true;
    public static final int NOTIFICATION_ID_PLAY_SOUND = 125;
    private static NotificationPlayer sNotificationPlayer = new NotificationPlayer(TAG);
    private static Handler sHandler = new Handler();
    // --- OliverOu@20151216, [MES-61][MES-139]NCC CB 911/919 behavior

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (Telephony.Sms.Intents.SMS_EMERGENCY_CB_RECEIVED_ACTION.equals(action) ||
                Telephony.Sms.Intents.SMS_CB_RECEIVED_ACTION.equals(action)) {
            handleCellBroadcastIntent(intent);
        } else if (SHOW_NEW_ALERT_ACTION.equals(action)) {
            showNewAlert(intent);
        // +++ dion_yang@20140102:Fix Airplane mode didn't clear dup-detect list
        } else if(Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
            sCmasIdSet.clear();
            Log.d(TAG, "Air Mode change on, clear dup-detect list");
        // --- dion_yang@20140102:Fix Airplane mode didn't clear dup-detect list
        } else {
            Log.e(TAG, "Unrecognized intent action: " + action);
        }
        return START_NOT_STICKY;
    }

    private void handleCellBroadcastIntent(Intent intent) {
        if(CellBroadcastReceiverApp.DEBUG_SEND) {
            Log.d(TAG, "handleCellBroadcastIntent=" + intent +
                    ", sub=" + /*intent.getLongExtra(PhoneConstants.SUBSCRIPTION_KEY, -1)*/
                    intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY, -1));//OliverOu@20150824 TT-650980
        }
        Bundle extras = intent.getExtras();
        if (extras == null) {
            Log.e(TAG, "received SMS_CB_RECEIVED_ACTION with no extras!");
            return;
        }

        //ASUS BEGIN, dion_yang, 20121213: CBS SMS raw log
        byte[][] pdus;
        Object[] obj = (Object[])intent.getSerializableExtra("pdus");
        
        if (obj != null) {
            pdus = new byte[obj.length][];

            if(CellBroadcastReceiverApp.DEBUG_SEND) {
                Log.d(TAG, "Pages="+obj.length);
            }
            // +++ Oliver@20151102, porting to M
            /*for (int i = 0; i < obj.length; i++) {
                pdus[i] = (byte[])obj[i];
                if(pdus[i] != null && CellBroadcastReceiverApp.DEBUG_SEND) {
                    String hexString = new String(Hex.encodeHex(pdus[i]));
                    Log.d(TAG, "Handle CB Message Page "+(i+1)+" pdu="+hexString);
                }
            }*/
            // --- Oliver@20151102, porting to M
        } else {
            Log.e(TAG, "Failed to extraxt pdus from CB SMS Intent.");
        }
        //ASUS END, dion_yang, 20121213: CBS SMS raw log

        SmsCbMessage message = (SmsCbMessage) extras.get("message");
        // +++ OliverOu@20150824 TT-650980
        //long subId = intent.getLongExtra(PhoneConstants.SUBSCRIPTION_KEY, -1);
        long subId = (long)(intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY, -1));
        // --- OliverOu@20150824 TT-650980
        int slotId = intent.getIntExtra(PhoneConstants.SLOT_KEY, 0);

        Log.v(TAG,"handleCellBroadcastIntent slotId= " + slotId + ", subId= " + subId + ", message= " + message);
        if (message == null) {
            Log.e(TAG, "received SMS_CB_RECEIVED_ACTION with no message extra");
            return;
        } else {
            if(CellBroadcastReceiverApp.DEBUG_SEND) {
                Log.d(TAG, "handleCellBroadcastIntent message="+message.toString());
            }
        }
        final CellBroadcastMessage cbm = new CellBroadcastMessage(message);
        cbm.setSubId(subId);
        cbm.setSlotId(slotId);
        // +++ OliverOu@20151216, [MES-61][MES-139]NCC CB 911/919 behavior
        if( (cbm.getServiceCategory() == 911) || cbm.getServiceCategory() == 919){
           is911Or919 = true;
           Log.d(TAG, "is911Or919=" + is911Or919);
        } else{
           is911Or919 = false;
        }
        // --- OliverOu@20151216, [MES-61][MES-139]NCC CB 911/919 behavior
        if (!isMessageEnabledByUser(cbm)) {
            Log.d(TAG, "ignoring alert of type " + cbm.getServiceCategory() +
                    " by user preference");
            return;
        }

        // +++ dion_yang@20131031:Fix ETWS GCF mode
        if(CellBroadcastUtils.isGcfMode()) {
            if(cbm.getSerialNumber() == 61492) {
                Log.d(TAG, "ignoring GCF alert of type " + cbm.getServiceCategory() + " serial_number=" +cbm.getSerialNumber() +
                        " by for GCF test case issue");
                return;
            }
        }
        // --- dion_yang@20131031:Fix ETWS GCF mode
        /// M: Added by cenxingcan@wind-mobile.com 20160815 NCC PWS Switch don't work start.
        if (getApplicationContext().getResources().getBoolean(R.bool.interception_channel_ids)) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(CellBroadcastReceiverApp.getApplication());
            boolean enableEmergencyAlerts = false;
            if (CellBroadcastUtils.isMultiSimEnabled() && !CellBroadcastUtils.isMultiSimDSDA()) {
               if (slotId == PhoneConstants.SUB1 || slotId == PhoneConstants.SUB2) {
                   enableEmergencyAlerts = prefs.getBoolean(CellBroadcastUtils.getPrefKeyEnableEmergencyAlerts(slotId), true);
                   Log.d(TAG, "slotId = " + slotId + " , enableEmergencyAlerts = " + enableEmergencyAlerts
                       + " , cbm.getServiceCategory() = " + cbm.getServiceCategory()
                       + " , isCustomServiceCategory(cbm.getServiceCategory()) = " + isCustomServiceCategory(cbm.getServiceCategory()));
                   if (!enableEmergencyAlerts) {
                       if (!isCustomServiceCategory(cbm.getServiceCategory())) {
                           return;
                       }
                   }
               }
            } else {
                enableEmergencyAlerts = prefs.getBoolean(CellBroadcastUtils.getPrefKeyEnableEmergencyAlerts(PhoneConstants.SUB1), true);
                Log.d(TAG, "single slotId = " + PhoneConstants.SUB1 + " , enableEmergencyAlerts = " + enableEmergencyAlerts
                    + " , cbm.getServiceCategory() = " + cbm.getServiceCategory()
                    + " , isCustomServiceCategory(cbm.getServiceCategory()) = " + isCustomServiceCategory(cbm.getServiceCategory()));
                if (!enableEmergencyAlerts) {
                    if (!isCustomServiceCategory(cbm.getServiceCategory())) {
                        return;
                    }
                }
            }
        }
        /// M: Added by cenxingcan@wind-mobile.com 20160815 NCC PWS Switch don't work end.


        if (mUseDupDetection) {
            // Check for duplicate message IDs according to CMAS carrier requirements. Message IDs
            // are stored in volatile memory. If the maximum of 65535 messages is reached, the
            // message ID of the oldest message is deleted from the list.
            MessageServiceCategoryAndScope newCmasId = new MessageServiceCategoryAndScope(
                    message.getServiceCategory(), message.getSerialNumber(), message.getLocation());
    
            // Add the new message ID to the list. It's okay if this is a duplicate message ID,
            // because the list is only used for removing old message IDs from the hash set.
            if (sCmasIdList.size() < MAX_MESSAGE_ID_SIZE) {
                sCmasIdList.add(newCmasId);
            } else {
                // Get oldest message ID from the list and replace with the new message ID.
                MessageServiceCategoryAndScope oldestCmasId = sCmasIdList.get(sCmasIdListIndex);
                sCmasIdList.set(sCmasIdListIndex, newCmasId);
                Log.d(TAG, "message ID limit reached, removing oldest message ID " + oldestCmasId);
                // Remove oldest message ID from the set.
                sCmasIdSet.remove(oldestCmasId);
                if (++sCmasIdListIndex >= MAX_MESSAGE_ID_SIZE) {
                    sCmasIdListIndex = 0;
                }
            }
            // Set.add() returns false if message ID has already been added
            if (!sCmasIdSet.add(newCmasId)) {
                Log.d(TAG, "ignoring duplicate alert with " + newCmasId);
                return;
            }
        } else {
            Log.d(TAG,"ignoring duplicate was off");
        }

        final Intent alertIntent = new Intent(SHOW_NEW_ALERT_ACTION);
        alertIntent.setClass(this, CellBroadcastAlertService.class);
        alertIntent.putExtra("message", cbm);
        alertIntent.putExtra("slot", cbm.getSlotId());
        // write to database on a background thread
        new CellBroadcastContentProvider.AsyncCellBroadcastTask(getContentResolver())
                .execute(new CellBroadcastContentProvider.CellBroadcastOperation() {
                    @Override
                    public boolean execute(CellBroadcastContentProvider provider) {
                        if (provider.insertNewBroadcast(cbm)) {
                            // new message, show the alert or notification on UI thread
                            startService(alertIntent);
                            return true;
                        } else {
                            return false;
                        }
                    }
                });
    }

    private void showNewAlert(Intent intent) {
        if(CellBroadcastReceiverApp.DEBUG_SEND) {
            Log.d(TAG, "showNewAlert="+intent);
        }
        Bundle extras = intent.getExtras();
        if (extras == null) {
            Log.e(TAG, "received SHOW_NEW_ALERT_ACTION with no extras!");
            return;
        }

        CellBroadcastMessage cbm = (CellBroadcastMessage) intent.getParcelableExtra("message");
        int slotId = intent.getIntExtra("slot", 0);
        cbm.setSlotId(slotId);
        if (cbm == null) {
            Log.e(TAG, "received SHOW_NEW_ALERT_ACTION with no message extra");
            return;
        } else {
            if(CellBroadcastReceiverApp.DEBUG_SEND && cbm.getSmsCbMessage() != null) {
                Log.d(TAG, "showNewAlert message= " + cbm.getSmsCbMessage().toString());
            }
        }

        if (CellBroadcastConfigService.isEmergencyAlertMessage(cbm)) {
            // start alert sound / vibration / TTS and display full-screen alert
            //ASUS BEGIN, dion_yang, 20121208: For GCF ETWS test
            //We should check the ETWS message would popup or not
            if(cbm.isEtwsPopupAlert() || cbm.isCmasMessage()) { // dion_yang@20131218:For ATT CellBroadcast Test issue
                if(CellBroadcastReceiverApp.DEBUG_SEND) {
                    Log.d(TAG, "showNewAlert messag is "+(cbm.isEtwsPopupAlert() ? "ETWS" : "CMAS")+" Popup Alert");
                }
                openEmergencyAlertNotification(cbm);
            }
            //+++ cenxingcan@wind-mobi.com add for Japan EWTS special fearture fix bug #144774 20161201 begin +++
            // M:0x1100-0x1104(4352-4356) @{
            else if (cbm.getServiceCategory() >= SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING &&
                    cbm.getServiceCategory() <= SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE) {
                if(CellBroadcastReceiverApp.DEBUG_SEND) {
                    Log.d(TAG, "showNewAlert messag is "+(cbm.getServiceCategory()) + " ETWS Popup Alert");
                }
                openEmergencyAlertNotification(cbm);
            }
            // @}
            //cenxingcan@wind-mobi.com add for Japan EWTS special fearture fix bug #144774 20161201 end
            else {
                if(CellBroadcastReceiverApp.DEBUG_SEND) {
                    Log.d(TAG, "showNewAlert messag is not ETWS Popup Alert nor Cmas Message");
                }
                addToNotificationBar(cbm);
            }
            //ASUS END, dion_yang, 20121208: For GCF ETWS test
        } else {
            // add notification to the bar
            //ASUS BEGIN, dion_yang, 20121213: For Normal CBS display popup issue
            if(CellBroadcastReceiverApp.DEBUG_SEND) {
                Log.d(TAG, "showNewAlert messag is Normal CBS message");
            }
            if(cbm.getSmsCbMessage() != null && cbm.getSmsCbMessage().getGeographicalScope() == SmsCbMessage.GEOGRAPHICAL_SCOPE_CELL_WIDE_IMMEDIATE) {
                if(CellBroadcastReceiverApp.DEBUG_SEND) {
                    Log.d(TAG, "showNewAlert messag is Normal CBS message GEOGRAPHICAL_SCOPE_CELL_WIDE_IMMEDIATE");
                }
                openEmergencyAlertNotification(cbm);
            } else {
                if(CellBroadcastReceiverApp.DEBUG_SEND) {
                    Log.d(TAG, "showNewAlert messag is Normal CBS message ");
                }
                addToNotificationBar(cbm);
            }
            //ASUS END, dion_yang, 20121208: For Normal CBS display popup issue
        }
    }

    /**
     * Filter out broadcasts on the test channels that the user has not enabled,
     * and types of notifications that the user is not interested in receiving.
     * This allows us to enable an entire range of message identifiers in the
     * radio and not have to explicitly disable the message identifiers for
     * test broadcasts. In the unlikely event that the default shared preference
     * values were not initialized in CellBroadcastReceiverApp, the second parameter
     * to the getBoolean() calls match the default values in res/xml/preferences.xml.
     *
     * @param message the message to check
     * @return true if the user has enabled this message type; false otherwise
     */
    private boolean isMessageEnabledByUser(CellBroadcastMessage message) {
        //ASUS BEGIN, dion_yang, 20121217: Add Normal CBS channel id and language checking
        String msgLang = message.getLanguageCode();
        int msgId = message.getServiceCategory();
        String settingLang = "default";
        SharedPreferences mSettingIdsSP = null;
        SharedPreferences mSettingLangSP = null;
        // +++ dion_yang@20131026:For ATT requirement
        boolean etwsSettings = false;
        boolean etwsTestSettings = false;
        boolean cmasExtremeSettings = false;
        boolean cmasSevereSettings = false;
        boolean cmasAmberSettings = false;
        boolean cmasTestSettings = false;
        // --- dion_yang@20131026:For ATT requirement
        //ASUS END, dion_yang, 20121217: Add Normal CBS channel id and language checking

        // +++ Add DSDS get from SIM
        int slotId = message.getSlotId();
        long subId = message.getSubId();
        String settingId = slotId == GsmUmtsCellBroadcastSmsFragmentBase.SLOTID_SLOT2 ?
                GsmUmtsCellBroadcastSmsFragmentBase.PREFIX_SIM2 + String.valueOf(msgId) :
                String.valueOf(msgId);
        // +++ AT&T CDR-ECB-660
        boolean isPrefKeyEnableEmergencyAlertsExist = PreferenceManager.getDefaultSharedPreferences(this).contains(CellBroadcastUtils.getPrefKeyEnableEmergencyAlerts(slotId));
        // +++ gary_hsu@20160303: Fix TT752115
        if(!CellBroadcastUtils.isATTSku() && !isPrefKeyEnableEmergencyAlertsExist){
            Log.v(TAG, "not ATT Sku , PrefKeyEnableEmergencyAlerts not exist , enable etwsSettings");
            etwsSettings = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(CellBroadcastUtils.getPrefKeyEnableEmergencyAlerts(slotId), true);
        }else{
            etwsSettings = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(CellBroadcastUtils.getPrefKeyEnableEmergencyAlerts(slotId), false);
        }
        // --- gary_hsu@20160303: Fix TT752115
        // --- AT&T CDR-ECB-660
        // +++ For ATT requirement
        etwsTestSettings = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                CellBroadcastUtils.getPrefKeyEnableETWSTestAlerts(slotId), false);
        cmasExtremeSettings = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                CellBroadcastUtils.getPrefKeyExtremeAlerts(slotId), false);
        cmasSevereSettings = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                CellBroadcastUtils.getPrefKeySevereAlerts(slotId), false);
        cmasAmberSettings = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                CellBroadcastUtils.getPrefKeyAmberAlerts(slotId), false);
        cmasTestSettings = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                CellBroadcastUtils.getPrefKeyEnableCMASTestAlerts(slotId), false);
        // --- For ATT requirement

        Log.d(TAG, "Emergency Alert recevied message on soltId " + slotId + ", subId= " + subId +
                ", etwsSettings=" + etwsSettings +
                ", etwsTestSettings=" + etwsTestSettings +
                ", cmasExtremeSettings=" + cmasExtremeSettings +
                ", cmasSevereSettings=" + cmasSevereSettings +
                ", cmasAmberSettings=" + cmasAmberSettings +
                ", cmasTestSettings=" + cmasTestSettings);
 
        mSettingIdsSP = getSharedPreferences(CellBroadcastUtils.getMsgIdPrefsName(), Context.MODE_PRIVATE);
        mSettingLangSP = getSharedPreferences("com.android.cellbroadcastreceiver_preferences", Context.MODE_PRIVATE);
        settingLang = mSettingLangSP.getString(CellBroadcastUtils.getPrefKeySMSCBLanguage(slotId), "default");

        Log.d(TAG, "SMS CB message receving setting, ID: " + mSettingIdsSP.getAll().toString() +", setting:" + mSettingIdsSP.getBoolean(String.valueOf(settingId),false) + " Language: " + settingLang);
        Log.d(TAG, "SMS CB message recevied, ID: " + msgId + " Language: " + msgLang +" slotId= " + slotId + " subId= " + subId);
        // --- Add DSDS get from SIM

        // +++ dion_yang@20131026:For ATT requirement
        if(!CellBroadcastUtils.isATTSku()) {
            Log.d(TAG, "SMS CB not ATT Sku, using etwsSettings to apply all settings");
            etwsTestSettings = etwsSettings;
            cmasExtremeSettings = etwsSettings;
            cmasSevereSettings = etwsSettings;
            cmasAmberSettings = etwsSettings;
            cmasTestSettings = etwsSettings;
        }
        // --- dion_yang@20131026:For ATT requirement

        if (message.isEtwsTestMessage()) {
            //TODO:we should let original CBS setting config support dual sim function
            boolean originalSettings = PreferenceManager.getDefaultSharedPreferences(this)
                    .getBoolean(CellBroadcastSettings.KEY_ENABLE_ETWS_TEST_ALERTS, false);
            return etwsTestSettings || originalSettings; // dion_yang@20131026:For ATT requirement
        }

        if (message.isCmasMessage()) {
            int messageClass = message.getCmasMessageClass();
            Log.v(TAG,"isMessageEnabledByUser CmasMessageClass= " + messageClass);
            switch (messageClass) {
                case SmsCbCmasInfo.CMAS_CLASS_EXTREME_THREAT:
                    return (cmasExtremeSettings || PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                            CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, false)); // dion_yang@20131026:For ATT requirement

                case SmsCbCmasInfo.CMAS_CLASS_SEVERE_THREAT:
                    return (cmasSevereSettings || PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                            CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, false)); // dion_yang@20131026:For ATT requirement

                case SmsCbCmasInfo.CMAS_CLASS_CHILD_ABDUCTION_EMERGENCY:
                    return (cmasAmberSettings || PreferenceManager.getDefaultSharedPreferences(this)
                            .getBoolean(CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, false)); // dion_yang@20131026:For ATT requirement

                case SmsCbCmasInfo.CMAS_CLASS_REQUIRED_MONTHLY_TEST:
                case SmsCbCmasInfo.CMAS_CLASS_CMAS_EXERCISE:
                case SmsCbCmasInfo.CMAS_CLASS_OPERATOR_DEFINED_USE:
                    return (cmasTestSettings || PreferenceManager.getDefaultSharedPreferences(this)
                            .getBoolean(CellBroadcastSettings.KEY_ENABLE_CMAS_TEST_ALERTS, false)); // dion_yang@20131026:For ATT requirement

                default:
                    // +++ TT-528516
                    if (msgLang == null) return true;
                    // --- TT-528516
                    //ASUS BEGIN, dion_yang, 20121217: Add Presidential level CMAS CBS language checking
                    //For 22.268 CH 6.2 
                    //If Presidential Warning Notification is received in English, then it shall be displayed by the UE.
                    //If Presidential Warning Notification is received in other than English, then it shall only be displayed by the UE if the User has selected that language
                    if(msgLang.equals("en")) {
                        return true;    // presidential-level CMAS alerts in English are always enabled
                    } else {
                        //In other language, we detect the UE settings
                        if(settingLang.equals("default")) return true;
                        if(!settingLang.equals(msgLang)){
                            Log.w(TAG, "Unsupported presidential-level CMAS SMS CB message recevied, Language: " + msgLang);
                            Log.w(TAG, "supported presidential-level CMAS SMS CB message recevied, Language: " + settingLang);
                            return false;
                        }
                        return true;
                    }
                    //ASUS END, dion_yang, 20121217: Add Presidential level CMAS CBS language checking
            }
        }

        // +++ gary_hsu@20160303, check 911/919 for tw
        if(CellBroadcastUtils.isTWSku() || CellBroadcastUtils.isCountryCodeTW()){
            if(etwsSettings == false) return false;
            if(msgId == 911 || msgId == 919) {
                if (msgLang == null || settingLang.equals("default")) return true;
                if(!settingLang.equals(msgLang)){
                    Log.w(TAG, "Unsupported CMAS SMS CB message recevied, Language: " + msgLang);
                    Log.w(TAG, "supported CMAS SMS CB message recevied, Language: " + settingLang);
                    return false;
                }
                return true;
            }
        }
        // --- gary_Hsu@20160303, check 911/919 for tw

        if (msgId == 50) {
            // save latest area info broadcast for Settings display and send as broadcast
//            CellBroadcastReceiverApp.setLatestAreaInfo(message);
//            Intent intent = new Intent(CB_AREA_INFO_RECEIVED_ACTION);
//            intent.putExtra("message", message);
//            sendBroadcastAsUser(intent, UserHandle.ALL,
//                    android.Manifest.permission.READ_PHONE_STATE);
            //20161004@Gary_Hsu: add for brazil spec
            //TelephonyManager tm = (TelephonyManager) getSystemService(
                    //Context.TELEPHONY_SERVICE);

            boolean enableChannel50Support = CellBroadcastUtils.isBrazil(this);//"br".equals(tm.getSimCountryIso());
            if(enableChannel50Support){
                sendBrazilAreaInfoNotify(message);
                return false;
            }
            //20161004@Gary_Hsu: add for brazil spec
        }

        if(!CellBroadcastUtils.isUserMode()){
            if(msgId >= GsmUmtsCellBroadcastSmsFragment.MIN_CBS_UI_ID && msgId <= GsmUmtsCellBroadcastSmsFragment.MAX_CBS_UI_ID) {
                if(!mSettingIdsSP.getBoolean(String.valueOf(settingId), false)) {
                    Log.v(TAG, "supported SMS CB message recevied, ID: " + mSettingIdsSP.getAll().toString() + ", setting:" + mSettingIdsSP.getBoolean(String.valueOf(settingId),false) + ", settingId= " + settingId);
                    Log.v(TAG, "So reject the message!");
                    return false;
                }
                if (msgLang == null || settingLang.equals("default")) return true;
                if(!settingLang.equals(msgLang)){
                    Log.w(TAG, "Unsupported CMAS SMS CB message recevied, Language: " + msgLang);
                    Log.w(TAG, "supported CMAS SMS CB message recevied, Language: " + settingLang);
                    return false;
                }
                return true;
            }
        }

        return true;    // other broadcast messages are always enabled
    }

    private void sendBrazilAreaInfoNotify(CellBroadcastMessage message) {
        CharSequence channelName = CellBroadcastResources.getDialogTitleResource(this,message);
        String messageBody = message.getMessageBody();
        final String ringtoneStr = getNotificationSound(this);
        PendingIntent pi = PendingIntent.getActivity(this, NOTIFICATION_ID, new Intent(),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(this)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setSmallIcon(R.drawable.ic_notify_alert)
                .setContentTitle(channelName)
                .setContentText(messageBody)
                .setWhen(System.currentTimeMillis())
                .setCategory(Notification.CATEGORY_SYSTEM)
                .setPriority(Notification.PRIORITY_HIGH)
                .setColor(getColor(R.color.notification_color))
                .setVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * Display a full-screen alert message for emergency alerts.
     * @param message the alert to display
     */
    private void openEmergencyAlertNotification(CellBroadcastMessage message) {
        // Acquire a CPU wake lock until the alert dialog and audio start playing.
        CellBroadcastAlertWakeLock.acquireScreenCpuWakeLock(this);

        // Close dialogs and window shade
        Intent closeDialogs = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        sendBroadcast(closeDialogs);

        //ASUS BEGIN, dion_yang,20121209:Fix GCF User Alert issue
        // start audio/vibration/speech service for emergency alerts
        CharSequence channelName = CellBroadcastResources.getDialogTitleResource(this,message);
        String messageBody = message.getMessageBody();

        if(message.isEtwsMessage() && message.getEtwsWarningInfo().isEmergencyUserAlert()) {
            startAlertSound(message);
        } else if(message.isCmasMessage()) {
            startAlertSound(message);
        } else if(is911Or919){
            // +++ gary_hsu@20160219, spec change: http://pmd.asus.com:8180/display/AMAX/Cell+broadcast
            startAlertSound(message);
            // --- gary_hsu@20160219, spec change: http://pmd.asus.com:8180/display/AMAX/Cell+broadcast
        } else {
            CellBroadcastAlertWakeLock.releaseCpuLock();
        }
        //ASUS END, dion_yang,20121209:Fix GCF User Alert issue

        // Decide which activity to start based on the state of the keyguard.
        Class c = CellBroadcastAlertDialog.class;
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km.inKeyguardRestrictedInputMode()) {
            // Use the full screen activity for security.
            c = CellBroadcastAlertFullScreen.class;
        }

        ArrayList<CellBroadcastMessage> messageList = new ArrayList<CellBroadcastMessage>(1);
        messageList.add(message);

        Intent alertDialogIntent = createDisplayMessageIntent(this, c, messageList);
        alertDialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(alertDialogIntent);
    }

    //ASUS BEGIN, dion_yang,20121209:Fix GCF User Alert issue
    private void startAlertSound(CellBroadcastMessage message) {
     // start audio/vibration/speech service for emergency alerts
        Intent audioIntent = new Intent(this, CellBroadcastAlertAudio.class);
        audioIntent.setAction(CellBroadcastAlertAudio.ACTION_START_ALERT_AUDIO);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        int duration;   // alert audio duration in ms
        if (message.isCmasMessage()) {
            // CMAS requirement: duration of the audio attention signal is 10.5 seconds.
            duration = 10600;
        } else {
            duration = Integer.parseInt(prefs.getString(
                    CellBroadcastSettings.KEY_ALERT_SOUND_DURATION,
                    CellBroadcastSettings.ALERT_SOUND_DEFAULT_DURATION)) * 1000;
        }
        audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_DURATION_EXTRA, duration);
        audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_911_919_CHANNEL_EXTRA, is911Or919);
        if (message.isEtwsMessage()) {
            // For ETWS, always vibrate, even in silent mode.
            //audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_VIBRATE_EXTRA, true);
            //audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_ETWS_VIBRATE_EXTRA, true);
        } else {
            // +++ dion_yang@20140113:Add CellBroadcast Vibration settings
            boolean ringerModeEnableVibrate = true;
            if(message.getSlotId() == 0) {
                ringerModeEnableVibrate = prefs.getBoolean(CellBroadcastUtils.getPrefKeyEnableAlertVibration(message.getSlotId()), true);
                Log.v(TAG, "Get sim 1 ringer mode vibration by app settings="+ringerModeEnableVibrate);
            } else {
                ringerModeEnableVibrate = prefs.getBoolean(CellBroadcastUtils.getPrefKeyEnableAlertVibration(message.getSlotId()), true);
                Log.v(TAG, "Get sim 2 ringer mode vibration by app settings="+ringerModeEnableVibrate);
            }
            // --- dion_yang@20140113:Add CellBroadcast Vibration settings
            // For other alerts, vibration can be disabled in app settings.
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_VIBRATE_EXTRA, ringerModeEnableVibrate);
        }

        String messageBody = message.getMessageBody();

        if (prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_ALERT_SPEECH, true)) {
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_BODY, messageBody);

            String language = message.getLanguageCode();
            if (message.isEtwsMessage() && !"ja".equals(language)) {
                Log.w(TAG, "bad language code for ETWS - using Japanese TTS");
                language = "ja";
            } else if (message.isCmasMessage() && !"en".equals(language)) {
                Log.w(TAG, "bad language code for CMAS - using English TTS");
                language = "en";
            }
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_LANGUAGE,
                    language);
        }
        startService(audioIntent);
    }
    //ASUS END, dion_yang,20121209:Fix GCF User Alert issue

    /**
     * Add the new alert to the notification bar (non-emergency alerts), or launch a
     * high-priority immediate intent for emergency alerts.
     * @param message the alert to display
     */
    private void addToNotificationBar(CellBroadcastMessage message) {
        CharSequence channelName = CellBroadcastResources.getDialogTitleResource(this,message);
        String messageBody = message.getMessageBody();

        // Pass the list of unread non-emergency CellBroadcastMessages
        ArrayList<CellBroadcastMessage> messageList = CellBroadcastReceiverApp
                .addNewMessageToList(message);

        // Create intent to show the new messages when user selects the notification.
        Intent intent = createDisplayMessageIntent(this, CellBroadcastAlertDialog.class,
                messageList);
        intent.putExtra(CellBroadcastAlertFullScreen.FROM_NOTIFICATION_EXTRA, true);

        PendingIntent pi = PendingIntent.getActivity(this, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);

        // use default sound/vibration/lights for non-emergency broadcasts
        // +++ gary_hsu@20160219: porting to M
        boolean ringerModeEnableVibrate = true;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if(message.getSlotId() == 0) {
            ringerModeEnableVibrate = prefs.getBoolean(CellBroadcastUtils.getPrefKeyEnableAlertVibration(message.getSlotId()), true);
            Log.v(TAG, "Get sim 1 ringer mode vibration by app settings="+ringerModeEnableVibrate);
        } else {
            ringerModeEnableVibrate = prefs.getBoolean(CellBroadcastUtils.getPrefKeyEnableAlertVibration(message.getSlotId()), true);
            Log.v(TAG, "Get sim 2 ringer mode vibration by app settings="+ringerModeEnableVibrate);
        }
        final String ringtoneStr = getNotificationSound(this);
        Uri ringToneUri = TextUtils.isEmpty(ringtoneStr) ? RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION) :(Uri.parse(ringtoneStr));
        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_notify_alert)
                .setTicker(channelName)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(pi)
                .setCategory(Notification.CATEGORY_SYSTEM)
                .setPriority(Notification.PRIORITY_HIGH)
                .setColor(getColor(R.color.notification_color))
                .setSound(ringToneUri)
                .setVisibility(Notification.VISIBILITY_PUBLIC);
                if(isVibrateWhileNotification(ringerModeEnableVibrate)) builder.setVibrate(new long[]{0,1000});

        // --- gary_hsu@20160219: porting to M

        // increment unread alert count (decremented when user dismisses alert dialog)
        int unreadCount = messageList.size();
        if (unreadCount > 1) {
            // use generic count of unread broadcasts if more than one unread
            builder.setContentTitle(getString(R.string.notification_multiple_title));
            builder.setContentText(getString(R.string.notification_multiple, unreadCount));
        } else {
            builder.setContentTitle(channelName).setContentText(messageBody);
        }

        NotificationManager notificationManager =
            (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);

//        //ASUS BEGIN, dion_yang,20121209:Fix GCF User Alert issue
//        if(message.isEtwsMessage() && message.getEtwsWarningInfo().isEmergencyUserAlert()) {
//            builder.setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE);
//            startAlertSound(message);
//        } else if(message.isCmasMessage()) {
//            builder.setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE);
//            startAlertSound(message);
//        }
//        //ASUS END, dion_yang,20121209:Fix GCF User Alert issue

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    static Intent createDisplayMessageIntent(Context context, Class intentClass,
            ArrayList<CellBroadcastMessage> messageList) {
        // Trigger the list activity to fire up a dialog that shows the received messages
        Intent intent = new Intent(context, intentClass);
        intent.putParcelableArrayListExtra(CellBroadcastMessage.SMS_CB_MESSAGE_EXTRA, messageList);
        return intent;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;    // clients can't bind to this service
    }

    private boolean isVibrateWhileNotification(boolean ringerModeEnableVibrate){
         AudioManager audioManager= (AudioManager) getSystemService(Context.AUDIO_SERVICE);
         switch (audioManager.getRingerMode()) {
         case AudioManager.RINGER_MODE_SILENT:
             Log.v(TAG, "Ringer mode: silent, turn off audio and vibrate");
             return false;
         case AudioManager.RINGER_MODE_VIBRATE:
             Log.v(TAG, "Ringer mode: vibrate, force vibrate");
             return true;

         case AudioManager.RINGER_MODE_NORMAL:
         default:
             return ringerModeEnableVibrate;
         }
    }

    public String getNotificationSound(Context context) {
        String ringtoneStr = "";
        ringtoneStr = Settings.System.getString(context.getContentResolver(), Settings.System.NOTIFICATION_SOUND);
        return ringtoneStr;
    }

    /// M: Added by cenxingcan@wind-mobile.com 20160815 NCC PWS Switch don't work start.
    private boolean isCustomServiceCategory (int channelid) {
        // channel id is 4370 or 4383
        return ((channelid == SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL)
			|| (channelid == SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE));
    }
    /// M: Added by cenxingcan@wind-mobile.com 20160815 NCC PWS Switch don't work end.

}
