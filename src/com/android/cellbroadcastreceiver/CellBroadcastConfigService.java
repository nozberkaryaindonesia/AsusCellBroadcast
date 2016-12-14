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

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
//import android.telephony.CellBroadcastMessage;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.ISms;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.telephony.gsm.SmsCbConstants;

import static com.android.cellbroadcastreceiver.CellBroadcastReceiver.DBG;

/**
 * This service manages enabling and disabling ranges of message identifiers
 * that the radio should listen for. It operates independently of the other
 * services and runs at boot time and after exiting airplane mode.
 *
 * Note that the entire range of emergency channels is enabled. Test messages
 * and lower priority broadcasts are filtered out in CellBroadcastAlertService
 * if the user has not enabled them in settings.
 *
 * TODO: add notification to re-enable channels after a radio reset.
 */
public class CellBroadcastConfigService extends IntentService {
    private static final String TAG = "CellBroadcastConfigService";

    static final String ACTION_ENABLE_CHANNELS_GSM = "ACTION_ENABLE_CHANNELS_GSM";
    static final String ACTION_ENABLE_CHANNELS_CDMA = "ACTION_ENABLE_CHANNELS_CDMA";

    static final String EMERGENCY_BROADCAST_RANGE_GSM =
            "ro.cb.gsm.emergencyids";

    // system property defining the emergency cdma channel ranges
    // Note: key name cannot exceeds 32 chars.
    static final String EMERGENCY_BROADCAST_RANGE_CDMA =
            "ro.cb.cdma.emergencyids";
    //private static int sSubscription;
    private static long sSubscription;

    public CellBroadcastConfigService() {
        super(TAG);          // use class name for worker thread name
    }

    private static void setChannelRange(SmsManager manager, String ranges, boolean enable, boolean isCdma) {
        if (DBG)log("setChannelRange: " + ranges);
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            for (String channelRange : ranges.split(",")) {
                int dashIndex = channelRange.indexOf('-');
                if (dashIndex != -1) {
                    int startId = Integer.decode(channelRange.substring(0, dashIndex).trim());
                    int endId = Integer.decode(channelRange.substring(dashIndex + 1).trim());
                    if (enable) {
                        if (DBG) log("enabling emergency IDs " + startId + '-' + endId);
                        if (isCdma) {
                            // Use default method to support cdma
                            manager.enableCellBroadcastRange(startId, endId, SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
                        } else {
                            iccISms.enableCellBroadcastRangeForSubscriber((int)sSubscription, startId, endId, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                        }
                    } else {
                        if (DBG) log("disabling emergency IDs " + startId + '-' + endId);
                        if (isCdma) {
                            // Use default method to support cdma
                            manager.disableCellBroadcastRange(startId, endId, SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
                        } else {
                            iccISms.disableCellBroadcastRangeForSubscriber((int)sSubscription, startId, endId, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                        }
                    }
                } else {
                    int messageId = Integer.decode(channelRange.trim());
                    if (enable) {
                        if (DBG) log("enabling emergency message ID " + messageId);
                        if (isCdma) {
                            // Use default method to support cdma
                            manager.enableCellBroadcast(messageId, SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
                        } else {
                            iccISms.enableCellBroadcastForSubscriber((int)sSubscription, messageId, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                        }
                    } else {
                        if (DBG) log("disabling emergency message ID " + messageId);
                        if (isCdma) {
                            // Use default method to support cdma
                            manager.disableCellBroadcast(messageId, SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
                        } else {
                            iccISms.disableCellBroadcastForSubscriber((int)sSubscription, messageId, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Number Format Exception parsing emergency channel range", e);
        }

        // Make sure CMAS Presidential is enabled (See 3GPP TS 22.268 Section 6.2).
        if (DBG) log("setChannelRange: enabling CMAS Presidential");
        if (isCdma) {
            manager.enableCellBroadcast(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT, SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
        } else {
            try {
                ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
                iccISms.enableCellBroadcastForSubscriber((int)sSubscription, SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
            } catch (Exception e) {
                Log.e(TAG, "enableCellBroadcastForSubscriber MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL", e);
            }
        }
    }

    /**
     * Returns true if this is a standard or operator-defined emergency alert message.
     * This includes all ETWS and CMAS alerts, except for AMBER alerts.
     * @param message the message to test
     * @return true if the message is an emergency alert; false otherwise
     */
    static boolean isEmergencyAlertMessage(CellBroadcastMessage message) {
        // M: cenxingcan@wind-mobi.com 20160702 add start
        if (message == null) {
            return false;
        }
        // M: cenxingcan@wind-mobi.com 20160702 add end
        if (message.isEmergencyAlertMessage()) {
            return true;
        }

        // Check for system property defining the emergency channel ranges to enable
        String emergencyIdRange = (CellBroadcastReceiver.phoneIsCdma()) ?
                SystemProperties.get(EMERGENCY_BROADCAST_RANGE_CDMA) :
                    SystemProperties.get(EMERGENCY_BROADCAST_RANGE_GSM);

        if (TextUtils.isEmpty(emergencyIdRange)) {
            return false;
        }
        try {
            int messageId = message.getServiceCategory();
            for (String channelRange : emergencyIdRange.split(",")) {
                int dashIndex = channelRange.indexOf('-');
                if (dashIndex != -1) {
                    int startId = Integer.decode(channelRange.substring(0, dashIndex).trim());
                    int endId = Integer.decode(channelRange.substring(dashIndex + 1).trim());
                    if (messageId >= startId && messageId <= endId) {
                        return true;
                    }
                } else {
                    int emergencyMessageId = Integer.decode(channelRange.trim());
                    if (emergencyMessageId == messageId) {
                        return true;
                    }
                }
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Number Format Exception parsing emergency channel range", e);
        }
        return false;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (CellBroadcastUtils.isMultiSimEnabled() && !CellBroadcastUtils.isMultiSimDSDA()) {
            long temp = intent.getLongExtra(PhoneConstants.SUBSCRIPTION_KEY, -1);
            sSubscription = (int)temp;
            //sSubscription = intent.getLongExtra(PhoneConstants.SUBSCRIPTION_KEY, -1);
            Log.i(TAG, "onHandleIntent: sSubscription= " + sSubscription);
        }
        if (ACTION_ENABLE_CHANNELS_GSM.equals(intent.getAction())) {
            configGsmChannels();
        } else if (ACTION_ENABLE_CHANNELS_CDMA.equals(intent.getAction())) {
            // Use default method to support cdma
            configChannels();
        }
    }

    private void configGsmChannels() {
        try {
            Log.i(TAG, "configGsmChannels...");
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            Resources res = getResources();

            // Check for system property defining the emergency channel ranges to enable
            String emergencyIdRange = SystemProperties.get(EMERGENCY_BROADCAST_RANGE_GSM);

            // +++ lide_yang@20131023: AT&T CDR-ECB-660
            String prefKeyEnableEmergencyAlertsForSim1 = CellBroadcastUtils.getPrefKeyEnableEmergencyAlerts(0);
            boolean enableEmergencyAlerts = prefs.getBoolean(prefKeyEnableEmergencyAlertsForSim1,
                    false);
            // --- lide_yang@20131023: AT&T CDR-ECB-660

            TelephonyManager tm = (TelephonyManager) getSystemService(
                    Context.TELEPHONY_SERVICE);

            boolean enableChannel50Support = res.getBoolean(R.bool.show_brazil_settings) ||
                    "br".equals(tm.getSimCountryIso());

            boolean enableChannel50Alerts = enableChannel50Support;

            if (CellBroadcastUtils.isMultiSimEnabled() && !CellBroadcastUtils.isMultiSimDSDA()) {
                int slotId = SubscriptionManager.getSlotId((int)sSubscription);
                Log.v(TAG,"configGsmChannels slotId= " + slotId + ", sSubscription= " + sSubscription);
                if (slotId == 0) {
                    enableChannel50Alerts = enableChannel50Alerts &&
                        prefs.getBoolean(
                              CellBroadcastChannel50Alerts.KEY_ENABLE_CHANNEL_50_ALERTS_SUB1, false);
                } else {
                    enableChannel50Alerts = enableChannel50Alerts &&
                        prefs.getBoolean(
                              CellBroadcastChannel50Alerts.KEY_ENABLE_CHANNEL_50_ALERTS_SUB2, false);
                }
            } else {
                enableChannel50Alerts = enableChannel50Alerts &&
                    prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_CHANNEL_50_ALERTS, false);
            }

            boolean enableEtwsTestAlerts = prefs.getBoolean(
                    CellBroadcastSettings.KEY_ENABLE_ETWS_TEST_ALERTS, false);

            boolean enableCmasExtremeAlerts = prefs.getBoolean(
                    CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, false);

            boolean enableCmasSevereAlerts = prefs.getBoolean(
                    CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, false);

            boolean enableCmasAmberAlerts = prefs.getBoolean(
                    CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, false);

            boolean enableCmasTestAlerts = prefs.getBoolean(
                    CellBroadcastSettings.KEY_ENABLE_CMAS_TEST_ALERTS, false);

            if (DBG) log("configGsmChannels:\nenableEmergencyAlerts = "+enableEmergencyAlerts
                    +"\nenableChannel50Alerts = "+enableChannel50Alerts
                    +"\nenableEtwsTestAlerts = "+enableEtwsTestAlerts
                    +"\nenableCmasExtremeAlerts = "+enableCmasExtremeAlerts
                    +"\nenableCmasSevereAlerts = "+enableCmasSevereAlerts
                    +"\nenableCmasAmberAlerts = "+enableCmasAmberAlerts
                    +"\nenableCmasTestAlerts = "+enableCmasTestAlerts);
            
            boolean result;
            
            SmsManager manager = SmsManager.getDefault();
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (enableEmergencyAlerts) {
                if (DBG) log("enabling emergency cell broadcast channels");
                if (!TextUtils.isEmpty(emergencyIdRange)) {
                    setChannelRange(manager, emergencyIdRange, true, false);
                } else {
                    // No emergency channel system property, enable all
                    // emergency channels
                    result = iccISms.enableCellBroadcastRangeForSubscriber((int)sSubscription,
                            SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                            SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING,
                            SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    if (DBG) log("enabling sub "+sSubscription+" MESSAGE_ID_ETWS_EARTHQUAKE_WARNING to MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING cell broadcast channels "+(result?"success":"fail"));
                    if (enableEtwsTestAlerts) {
                        result = iccISms.enableCellBroadcastForSubscriber((int)sSubscription,
                                SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                        if (DBG) log("enabling sub "+sSubscription+" MESSAGE_ID_ETWS_TEST_MESSAGE cell broadcast channels "+(result?"success":"fail"));
                    }
                    result = iccISms.enableCellBroadcastForSubscriber((int)sSubscription,
                            SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE,
                            SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    if (DBG) log("enabling sub "+sSubscription+" MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE cell broadcast channels "+(result?"success":"fail"));
                    if (enableCmasExtremeAlerts) {
                        result = iccISms.enableCellBroadcastRangeForSubscriber((int)sSubscription,
                                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED,
                                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                        if (DBG) log("enabling sub "+sSubscription+" MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED to MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY cell broadcast channels "+(result?"success":"fail"));
                    }
                    if (enableCmasSevereAlerts) {
                        result = iccISms.enableCellBroadcastRangeForSubscriber((int)sSubscription,
                                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED,
                                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                        if (DBG) log("enabling sub "+sSubscription+" MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED to MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY cell broadcast channels "+(result?"success":"fail"));
                    }
                    if (enableCmasAmberAlerts) {
                        result = iccISms.enableCellBroadcastForSubscriber((int)sSubscription,
                                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                        if (DBG) log("enabling sub "+sSubscription+" MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY cell broadcast channels "+(result?"success":"fail"));
                    }
                    if (enableCmasTestAlerts) {
                        result = iccISms.enableCellBroadcastRangeForSubscriber((int)sSubscription,
                                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST,
                                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                        if (DBG) log("enabling sub "+sSubscription+" MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST to MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE cell broadcast channels "+(result?"success":"fail"));
                    }
                    // CMAS Presidential must be on (See 3GPP TS 22.268 Section 6.2).
                    result = iccISms.enableCellBroadcastForSubscriber((int)sSubscription,
                            SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                            SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    if (DBG) log("enabling sub "+sSubscription+" MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL cell broadcast channels "+(result?"success":"fail"));
                }
                if (DBG) log("enabled emergency cell broadcast channels");
            } else {
                // we may have enabled these channels previously, so try to disable them
                if (DBG) log("disabling emergency cell broadcast channels");
                if (!TextUtils.isEmpty(emergencyIdRange)) {
                    setChannelRange(manager, emergencyIdRange, false, false);
                } else {
                    // No emergency channel system property, disable all emergency channels
                    // except for CMAS Presidential (See 3GPP TS 22.268 Section 6.2)
                    result = iccISms.disableCellBroadcastRangeForSubscriber((int)sSubscription,
                            SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                            SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING,
                            SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    result = iccISms.disableCellBroadcastForSubscriber((int)sSubscription,
                            SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
                            SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    result = iccISms.disableCellBroadcastForSubscriber((int)sSubscription,
                            SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE,
                            SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    result = iccISms.disableCellBroadcastRangeForSubscriber((int)sSubscription,
                            SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED,
                            SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY,
                            SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    result = iccISms.disableCellBroadcastRangeForSubscriber((int)sSubscription,
                            SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED,
                            SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY,
                            SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    result = iccISms.disableCellBroadcastForSubscriber((int)sSubscription,
                            SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                            SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    result = iccISms.disableCellBroadcastRangeForSubscriber((int)sSubscription,
                            SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST,
                            SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE,
                            SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    
                    // CMAS Presidential must be on.
                    result = iccISms.enableCellBroadcastForSubscriber((int)sSubscription,
                            SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                            SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                }
                if (DBG) log("disabled sub "+sSubscription+" etws and cmas emergency cell broadcast channels");
            }

            //ASUS BEGIN, dion_yang:20130103:For handle by Asus custom CBS control, we don't let Google to setting channel 0~999
            if (DBG) log("skip control cell broadcast channel 50");
            /*if (enableChannel50Alerts) {
                if (DBG) log("enabling cell broadcast channel 50");
                if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                    MSimSmsManager smsManagerMSim = MSimSmsManager.getDefault();
                    smsManagerMSim.enableCellBroadcast(50, mSubscription);
                } else {
                    manager.enableCellBroadcast(50);
                }
                if (DBG) log("enabled cell broadcast channel 50");
            } else {
                if (DBG) log("disabling cell broadcast channel 50");
                if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                    MSimSmsManager smsManagerMSim = MSimSmsManager.getDefault();
                    smsManagerMSim.disableCellBroadcast(50, mSubscription);
                } else {
                    manager.disableCellBroadcast(50);
                }
                if (DBG) log("disabled cell broadcast channel 50");
            }*/
            //ASUS END, dion_yang:20130103:For handle by Asus custom CBS control, we don't let Google to setting channel 0~999

            if (!enableEtwsTestAlerts) {
                if (DBG) Log.d(TAG, "disabling sub "+sSubscription+" cell broadcast ETWS test messages");
                result = iccISms.disableCellBroadcastForSubscriber((int)sSubscription,
                        SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
                        SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
            }
            if (!enableCmasExtremeAlerts) {
                if (DBG) Log.d(TAG, "disabling sub "+sSubscription+" cell broadcast CMAS extreme");
                result = iccISms.disableCellBroadcastRangeForSubscriber((int)sSubscription,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY,
                        SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
            }
            if (!enableCmasSevereAlerts) {
                if (DBG) Log.d(TAG, "disabling sub "+sSubscription+" cell broadcast CMAS severe");
                result = iccISms.disableCellBroadcastRangeForSubscriber((int)sSubscription,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY,
                        SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
            }
            if (!enableCmasAmberAlerts) {
                if (DBG) Log.d(TAG, "disabling sub "+sSubscription+" cell broadcast CMAS amber");
                result = iccISms.disableCellBroadcastForSubscriber((int)sSubscription,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                        SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
            }
            if (!enableCmasTestAlerts) {
                if (DBG) Log.d(TAG, "disabling sub "+sSubscription+" cell broadcast CMAS test messages");
                result = iccISms.disableCellBroadcastRangeForSubscriber((int)sSubscription,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE,
                        SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
            }
        } catch (Exception ex) {
            Log.e(TAG, "exception enabling cell broadcast channels", ex);
        }
    }

    private void configChannels() {
        try {
            Log.i(TAG, "configChannels...");
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            Resources res = getResources();

            // boolean for each user preference checkbox, true for checked, false for unchecked
            // Note: If enableEmergencyAlerts is false, it disables ALL emergency broadcasts
            // except for cmas presidential. i.e. to receive cmas severe alerts, both
            // enableEmergencyAlerts AND enableCmasSevereAlerts must be true.
//            boolean enableEmergencyAlerts = prefs.getBoolean(
//                    CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, true);
            String prefKeyEnableEmergencyAlertsForSim1 = CellBroadcastUtils.getPrefKeyEnableEmergencyAlerts(0);
            boolean enableEmergencyAlerts = prefs.getBoolean(prefKeyEnableEmergencyAlertsForSim1,
                    false);

            TelephonyManager tm = (TelephonyManager) getSystemService(
                    Context.TELEPHONY_SERVICE);

            boolean enableChannel50Support = res.getBoolean(R.bool.show_brazil_settings) ||
                    "br".equals(tm.getSimCountryIso());

            boolean enableChannel50Alerts = enableChannel50Support &&
                    prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_CHANNEL_50_ALERTS, true);

            // Note: ETWS is for 3GPP only
            boolean enableEtwsTestAlerts = prefs.getBoolean(
                    CellBroadcastSettings.KEY_ENABLE_ETWS_TEST_ALERTS, false);

            boolean enableCmasExtremeAlerts = prefs.getBoolean(
                    CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, true);

            boolean enableCmasSevereAlerts = prefs.getBoolean(
                    CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, true);

            boolean enableCmasAmberAlerts = prefs.getBoolean(
                    CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, true);

            boolean enableCmasTestAlerts = prefs.getBoolean(
                    CellBroadcastSettings.KEY_ENABLE_CMAS_TEST_ALERTS, false);

            // set up broadcast ID ranges to be used for each category
            int cmasExtremeStart =
                    SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED;
            int cmasExtremeEnd = SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY;
            int cmasSevereStart =
                    SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED;
            int cmasSevereEnd = SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY;
            int cmasAmber = SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY;
            int cmasTestStart = SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST;
            int cmasTestEnd = SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE;
            int cmasPresident = SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL;

            // set to CDMA broadcast ID rage if phone is in CDMA mode.
            boolean isCdma = CellBroadcastReceiver.phoneIsCdma();
            if (isCdma) {
                cmasExtremeStart = SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT;
                cmasExtremeEnd = cmasExtremeStart;
                cmasSevereStart = SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT;
                cmasSevereEnd = cmasSevereStart;
                cmasAmber = SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY;
                cmasTestStart = SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE;
                cmasTestEnd = cmasTestStart;
                cmasPresident = SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT;
            }

            SmsManager manager = SmsManager.getDefault();
            // Check for system property defining the emergency channel ranges to enable
            String emergencyIdRange = isCdma ?
                    "" : SystemProperties.get(EMERGENCY_BROADCAST_RANGE_GSM);
            if (enableEmergencyAlerts) {
                if (DBG) log("enabling emergency cell broadcast channels");
                if (!TextUtils.isEmpty(emergencyIdRange)) {
                    setChannelRange(manager, emergencyIdRange, true, isCdma);
                } else {
                    // No emergency channel system property, enable all emergency channels
                    // that have checkbox checked
                    if (!isCdma) {
                        manager.enableCellBroadcastRange(
                                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                        if (enableEtwsTestAlerts) {
                            manager.enableCellBroadcast(
                                    SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
                                    SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                        }
                        manager.enableCellBroadcast(
                                SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    }
                    if (enableCmasExtremeAlerts) {
                        manager.enableCellBroadcastRange(cmasExtremeStart, cmasExtremeEnd, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    }
                    if (enableCmasSevereAlerts) {
                        manager.enableCellBroadcastRange(cmasSevereStart, cmasSevereEnd, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    }
                    if (enableCmasAmberAlerts) {
                        manager.enableCellBroadcast(cmasAmber, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    }
                    if (enableCmasTestAlerts) {
                        manager.enableCellBroadcastRange(cmasTestStart, cmasTestEnd, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    }
                    // CMAS Presidential must be on (See 3GPP TS 22.268 Section 6.2).
                    manager.enableCellBroadcast(cmasPresident, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                }
                if (DBG) log("enabled emergency cell broadcast channels");
            } else {
                // we may have enabled these channels previously, so try to disable them
                if (DBG) log("disabling emergency cell broadcast channels");
                if (!TextUtils.isEmpty(emergencyIdRange)) {
                    setChannelRange(manager, emergencyIdRange, false, isCdma);
                } else {
                    // No emergency channel system property, disable all emergency channels
                    // except for CMAS Presidential (See 3GPP TS 22.268 Section 6.2)
                    if (!isCdma) {
                        manager.disableCellBroadcastRange(
                                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                        manager.disableCellBroadcast(
                                SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                        manager.disableCellBroadcast(
                                SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE,
                                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    }
                    manager.disableCellBroadcastRange(cmasExtremeStart, cmasExtremeEnd, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    manager.disableCellBroadcastRange(cmasSevereStart, cmasSevereEnd, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    manager.disableCellBroadcast(cmasAmber, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    manager.disableCellBroadcastRange(cmasTestStart, cmasTestEnd, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);

                    // CMAS Presidential must be on (See 3GPP TS 22.268 Section 6.2).
                    manager.enableCellBroadcast(cmasPresident, SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
                }
                if (DBG) log("disabled emergency cell broadcast channels");
            }

            if (isCdma) {
                if (DBG) log("channel 50 is not applicable for cdma");
            } else if (enableChannel50Alerts) {
                if (DBG) log("enabling cell broadcast channel 50");
                manager.enableCellBroadcast(50, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
            } else {
                if (DBG) log("disabling cell broadcast channel 50");
                manager.disableCellBroadcast(50, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
            }

            if ("il".equals(tm.getSimCountryIso()) || "il".equals(tm.getNetworkCountryIso())) {
                if (DBG) log("enabling channels 919-928 for Israel");
                manager.enableCellBroadcastRange(919, 928, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
            } else {
                if (DBG) log("disabling channels 919-928");
                manager.disableCellBroadcastRange(919, 928, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
            }

            // Disable per user preference/checkbox.
            // This takes care of the case where enableEmergencyAlerts is true,
            // but check box is unchecked to receive such as cmas severe alerts.
            if (!enableEtwsTestAlerts && !isCdma) {
                if (DBG) Log.d(TAG, "disabling cell broadcast ETWS test messages");
                manager.disableCellBroadcast(
                        SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
                        SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
            }
            if (!enableCmasExtremeAlerts) {
                if (DBG) Log.d(TAG, "disabling cell broadcast CMAS extreme");
                manager.disableCellBroadcastRange(
                        cmasExtremeStart,
                        cmasExtremeEnd,
                        SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
            }
            if (!enableCmasSevereAlerts) {
                if (DBG) Log.d(TAG, "disabling cell broadcast CMAS severe");
                manager.disableCellBroadcastRange(cmasSevereStart,
                        cmasSevereEnd,
                        SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
            }
            if (!enableCmasAmberAlerts) {
                if (DBG) Log.d(TAG, "disabling cell broadcast CMAS amber");
                manager.disableCellBroadcast(cmasAmber,
                        SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
            }
            if (!enableCmasTestAlerts) {
                if (DBG) Log.d(TAG, "disabling cell broadcast CMAS test messages");
                manager.disableCellBroadcastRange(cmasTestStart,
                        cmasTestEnd,
                        SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
            }
        } catch (Exception ex) {
            Log.e(TAG, "exception enabling cell broadcast channels", ex);
        }
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
