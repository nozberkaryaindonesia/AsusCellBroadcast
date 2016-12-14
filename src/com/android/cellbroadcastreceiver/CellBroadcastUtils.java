
package com.android.cellbroadcastreceiver;

import static com.android.cellbroadcastreceiver.CellBroadcastReceiver.DBG;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.SmsManager;
//import android.telephony.SubInfoRecord;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.UiccSmsController;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.ISms;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SmsCbConstants;






import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CellBroadcastUtils {
    private static final String TAG = "CellBroadcastUtils";
    private static final boolean DEBUG = true;
    // +++ dion_yang@20131023:Add support ATT sku
    private static final String SKU_PROPERTIES = "ro.build.asus.sku";
    private static final String ATT_SKU = "ATT";
    // --- dion_yang@20131023:Add support ATT sku
    private static final String VZW_SKU = "VZW";//OliverOu@20151119:Add support Verison sku
    private static final String AP_DEBUG_PROPERTIES = "persist.asus.cb.debug";

    // +++ dion_yang@20140225:Apply NCC LTE spec
    public static final int MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_ADDITIONAL_LANGUAGES = 4383;
    // --- dion_yang@20140225:Apply NCC LTE spec

    // +++ joey_lee, add channels for Civil Emergency Message
    public static final int MESSAGE_ID_CMAS_ALERT_CIVIL_EMERGENCY_MESSAGE = 0x038F;  // 911, for Chinese
    public static final int MESSAGE_ID_CMAS_ALERT_CIVIL_EMERGENCY_MESSAGE_ADDITIONAL_LANGUAGES = 0x0397;  // 919, for English
    // +++ joey_lee, add channels for Civil Emergency Message

    // +++ gary_hsu@20160307: NCC PLMN08/10 20151222 spec
    public static final int MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_ADDITIONAL_LANGUAGES = 0x1120;//4384
    public static final int MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY_ADDITIONAL_LANGUAGES = 0x1121;//4385
    public static final int MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED_ADDITIONAL_LANGUAGES = 0x1122;//4386
    public static final int MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY_ADDITIONAL_LANGUAGES = 0x1123;//4387
    public static final int MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED_ADDITIONAL_LANGUAGES = 0x1124;//4388
    public static final int MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY_ADDITIONAL_LANGUAGES = 0x1125;//4389
    public static final int MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED_ADDITIONAL_LANGUAGES = 0x1126;//4390
    public static final int MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY_ADDITIONAL_LANGUAGES = 0x1127;//4391
    public static final int MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_ADDITIONAL_LANGUAGES = 0x1128;//4392
    public static final int MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST_ADDITIONAL_LANGUAGES = 0x1129;//4393
    // +++ gary_hsu@20160307: NCC PLMN08/10 20151222 spec

    // +++ andrew_tu@20140623 Remodel AsusCellBroadcast.
    private static String SP_FILE_NAME = "GsmUmtsSharedPref";

    public static String getMsgIdPrefsName() {
        return SP_FILE_NAME;
    }
    // --- andrew_tu@20140623 Remodel AsusCellBroadcast.

    // M: Added by cenxingcan@wind-mobi.com fix bug #130431 20160907 did not refresh slot name begin
    private static TelephonyManager mTelephonyManager;
    // M: Added by cenxingcan@wind-mobi.com fix bug #130431 20160907 did not refresh slot name end

    public static boolean isMultiSimEnabled() {
        return TelephonyManager.getDefault().isMultiSimEnabled();
    }

    // +++ Gary_Hsu@asus.com, Fix 778560
    public static boolean isSupportSim2CellBroadcast(){
        String deviceName = SystemProperties.get("ro.product.device", "");
        //ZE550ML/ZE551ML not support SIM2 PWS
        if(deviceName.equalsIgnoreCase("Z008") || deviceName.equalsIgnoreCase("Z008_1")) {
            return false;
        }
        if(deviceName.equalsIgnoreCase("Z00A") || deviceName.equalsIgnoreCase("Z00A_1") || deviceName.equalsIgnoreCase("Z00A_3")) {
            return false;
        }
        return true;
    }
    // --- Gary_Hsu@asus.com, Fix 778560

    public static boolean isMultiSimDSDA() {
        String simConfig =
                SystemProperties.get(TelephonyProperties.PROPERTY_MULTI_SIM_CONFIG, null);
        if (simConfig != null && simConfig.equals("dsda")) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean hasIccCard(Context context) {
        /** origin code
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            if (isMultiSimEnabled() && !isMultiSimDSDA()) {
                boolean[] hasIccCard = new boolean[2];
                hasIccCard[0] = hasIccCard(telephonyManager, 0);
                hasIccCard[1] = hasIccCard(telephonyManager, 1);
                Log.d(TAG, "hasIccCard[0]=" + hasIccCard[0] + ", hasIccCard[1]=" + hasIccCard[1]);
                return (hasIccCard[0] || hasIccCard[1]);
            } else {
                return telephonyManager.hasIccCard();
            }
        }
        **/
        // M: Added by cenxingcan@wind-mobi.com fix bug #130431 20160907 did not refresh slot name begin
        if (getTelephonyManager(context) != null) {
            if (isMultiSimEnabled() && !isMultiSimDSDA()) {
                Log.d(TAG, "hasIccCard[0] = " + hasIccCard(context, 0) + " , hasIccCard[1] = " + hasIccCard(context, 1));
                return (hasIccCard(context, 0) || hasIccCard(context, 1));
            } else {
                return getTelephonyManager(context).hasIccCard();
            }
        }
        // M: Added by cenxingcan@wind-mobi.com fix bug #130431 20160907 did not refresh slot name end
        return false;
    }

    // M: Added by cenxingcan@wind-mobi.com fix bug #130431 20160907 did not refresh slot name begin
    private static TelephonyManager getTelephonyManager(Context context) {
        if (mTelephonyManager == null) {
            mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        }
        return mTelephonyManager;
    }

    public static boolean hasIccCard(Context context, int slotId) {
        if (getTelephonyManager(context) != null) {
            boolean[] hasIccCard = new boolean[2];
            hasIccCard[slotId] = hasIccCard(getTelephonyManager(context), slotId);
            Log.d(TAG, "slotId = " + slotId + " , hasIccCard[ " + slotId + " ]" + hasIccCard[slotId]);
            return hasIccCard[slotId];
        }
        return false;
    }
    // M: Added by cenxingcan@wind-mobi.com fix bug #130431 20160907 did not refresh slot name end

    // M: Added by cenxingcan@wind-mobi.com for bug #136426 20161025 begin
    /**
    * To determine whether in Brazil
    * */
    public static boolean isBrazil (Context context) {
        boolean isBR = false;
        if (getTelephonyManager(context) != null) {
            String countryIso = getTelephonyManager(context).getSimCountryIso();
            Log.d(TAG, "isBrazil () , countryIso = " + countryIso);
            if (!TextUtils.isEmpty(countryIso)) {
                countryIso = countryIso.toUpperCase(Locale.US);
                Log.d(TAG, "isBrazil () , it's not isEmpty, countryIso toUpperCase is  = " + countryIso);
                if (countryIso.contains("BR")) {
                    isBR = true;
                    Log.d(TAG, "in isBrazil , isBZ = " + isBR);
                }
            } else {
                Log.d(TAG, "countryIso isEmpty...");
            }
        }
        Log.d(TAG, "fianl isBrazil() return ? " + isBR);
        return isBR;
    }

    // M: Added by cenxingcan@wind-mobi.com for bug #136426 20161025 end

    // +++ andrew_tu@20150302 : TT-556388 Fix NoSuchMethodError
    public static boolean hasIccCard(TelephonyManager telephonyManager, int slotId) {
        Class classInstance;
        try {
            classInstance = Class.forName(telephonyManager.getClass().getName());
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "Class Not Found : " + telephonyManager.getClass().getName());
            return false;
        }
        boolean result = false;
        Method method;
        Class[] paramTypes = new Class[1];
        Object arglist[] = new Object[1];
        try {
            paramTypes[0] = int.class;
            arglist[0] = (int) slotId;
            method = classInstance.getMethod("hasIccCard", paramTypes);
            result = (Boolean) method.invoke(telephonyManager, arglist);
        } catch (Exception ex1) {
            Log.w(TAG, "hasIccCard(int) does not exist");
            try {
                paramTypes[0] = long.class;
                arglist[0] = (long) slotId;
                method = classInstance.getMethod("hasIccCard", paramTypes);
                result = (Boolean) method.invoke(telephonyManager, arglist);
            } catch (Exception ex2) {
                Log.w(TAG, "hasIccCard(long) does not exist");
                result = false;
            }
        }
        return result;
    }
    // --- andrew_tu@20150302 : TT-556388 Fix NoSuchMethodError

    public static boolean isSimReady(int slot) {
        boolean ready = false;
        try {
            ready = TelephonyManager.getDefault().getSimState(slot) == TelephonyManager.SIM_STATE_READY;
        } catch (Exception e){
            // do nothing
        }
        return ready;
    }

    public static String getNetworkOperatorName(int slot) {
        String sName = TelephonyManager.getDefault()
                .getNetworkOperatorName(slot).equals("null") ? "Sim " + (slot + 1) :
                    TelephonyManager.getDefault().getNetworkOperatorName(slot);
        return sName;
    }

    public static String getSlotName(Context context, int slot) {
        String value = null;
        // M: Modified by cenxingcan@wind-mobi.com fix bug #130431 20160907 did not refresh slot name begin
        if (hasIccCard(context, slot)) {
            if (slot == 0) {
                value = Settings.Global.getString(context.getContentResolver(),
                        "multi_sim_one_slot_name");
            } else if (slot == 1) {
                value = Settings.Global.getString(context.getContentResolver(),
                        "multi_sim_two_slot_name");
            } else {
                Log.e(TAG, "Unknow slot Id");
                return "Unknow slot Id";
            }
        }
        // M: Modified by cenxingcan@wind-mobi.com fix bug #130431 20160907 did not refresh slot name end
        if (TextUtils.isEmpty(value)) {
            String simId = "1";
            if (slot == 1) {
                simId = "2";
            }
            value = "SIM " + simId;
        }
        return value;
    }

    // +++ dion_yang@20131026:For ATT requirement
    public static void enableETWSAlert(long subId) {
        try {
        ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
        boolean result = iccISms.enableCellBroadcastRangeForSubscriber((int)subId,
                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "enabling subId= " + subId + ", MESSAGE_ID_ETWS_EARTHQUAKE_WARNING to MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING cell broadcast channels "+(result?"success":"fail"));
        result = iccISms.enableCellBroadcastForSubscriber((int)subId, SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "enabling subId= " + subId + ", MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE cell broadcast channels "+(result?"success":"fail"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void disableETWSAlert(long subId) {
        try {
        ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
        boolean result = iccISms.disableCellBroadcastRangeForSubscriber((int)subId,
                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "disabled subId= " + subId + ", MESSAGE_ID_ETWS_EARTHQUAKE_WARNING to MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING cell broadcast channels "+(result?"success":"fail"));
        result = iccISms.disableCellBroadcastForSubscriber((int)subId, SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "disabled subId= " + subId + ", MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE cell broadcast channels "+(result?"success":"fail"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void enableETWSTestAlert(long subId) {
        try {
        ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
        boolean result = iccISms.enableCellBroadcastForSubscriber((int)subId, SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "enabling subId= " + subId + ", MESSAGE_ID_ETWS_TEST_MESSAGE cell broadcast channels "+(result?"success":"fail"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void disableETWSTestAlert(long subId) {
        try {
        ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
        boolean result = iccISms.disableCellBroadcastForSubscriber((int)subId, SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "disabled subId= " + subId + ", MESSAGE_ID_ETWS_TEST_MESSAGE cell broadcast channels "+(result?"success":"fail"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void enableETWSFutureVersionsAlert(long subId) {
        try {
        ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
        boolean result = iccISms.enableCellBroadcastRangeForSubscriber((int)subId,
                (SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE+1),
                (SmsCbConstants.MESSAGE_ID_CMAS_FIRST_IDENTIFIER-1),
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "enabling subId= " + subId + ", MESSAGE_ID_CMAS_ALERT_FUTURE_VERSIONS(4383) to MESSAGE_ID_PWS_LAST_IDENTIFIER cell broadcast channels "+(result?"success":"fail"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void disableETWSFutureVersionsAlert(long subId) {
        try {
        ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
        boolean result = iccISms.disableCellBroadcastRangeForSubscriber((int)subId,
                (SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE+1),
                (SmsCbConstants.MESSAGE_ID_CMAS_FIRST_IDENTIFIER-1),
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "disabled subId= " + subId + ", MESSAGE_ID_CMAS_ALERT_FUTURE_VERSIONS(4357) to MESSAGE_ID_PWS_LAST_IDENTIFIER cell broadcast channels "+(result?"success":"fail"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    // --- dion_yang@20131026:For ATT requirement

    public static void enableCMASPresidentialAlert(long subId) {
        try {
        // CMAS Presidential must be on (See 3GPP TS 22.268 Section 6.2).
        ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
        boolean result = iccISms.enableCellBroadcastForSubscriber((int)subId, SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "enabling subId= " + subId + ", MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL cell broadcast channels "+(result?"success":"fail"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    // --- dion_yang@20130912 : Add DSDS get from SIM

    // +++ dion_yang@20140225:Apply NCC LTE spec
    public static void enableCMASPresidentialAlertAdditionalLanguages(long subId) {
        try {
        ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
        boolean result = iccISms.enableCellBroadcastForSubscriber((int)subId, MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_ADDITIONAL_LANGUAGES, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "enabling subId= " + subId + ", MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_ADDITIONAL_LANGUAGES cell broadcast channels "+(result?"success":"fail"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    // --- dion_yang@20140225:Apply NCC LTE spec

    // +++ joey_lee, add channels for Civil Emergency Message
    // Why we use GsmBroadcastConfigurator?
    // Because the message from 911 & 919 should be CMAS messages but framework does not support these two channels yet.
    // Besides, 911 & 919 are just between custom cell broadcast ID range: MIN_CBS_UI_ID(0) and MAX_CBS_UI_ID(999).
    // So use GsmBroadcastConfigurator to add these two channels as custom cell broadcast channels (also added into GsmUmtsSharedPref.xml)
    // This is like a workaround, but it should be fine when framework is ready...?
    public static void enableCMASCivilEmergencyMessageAlert(Context context, int slotId) {
        GsmBroadcastConfigurator gbc = GsmBroadcastConfigurator.getInstance(context);
        if (gbc != null) {
            int gbcMsgId = gbc.getMsgId();
            String gbcSpKey = gbc.getSpKey();
            try {
                gbc.setMsgId(MESSAGE_ID_CMAS_ALERT_CIVIL_EMERGENCY_MESSAGE);
                String prefKey = (slotId == GsmUmtsCellBroadcastSmsFragmentBase.SLOTID_SLOT1 ? "" : GsmUmtsCellBroadcastSmsFragmentBase.PREFIX_SIM2)
                        + String.valueOf(MESSAGE_ID_CMAS_ALERT_CIVIL_EMERGENCY_MESSAGE);
                gbc.setSpKey(prefKey);
                boolean result = gbc.switchService(true, slotId);
                if (DBG) {
                    Log.d(TAG, "enableCMASCivilEmergencyMessageAlert() with GsmBroadcastConfigurator");
                    Log.d(TAG, "enabling slotId= " + slotId + ", MESSAGE_ID_CMAS_ALERT_CIVIL_EMERGENCY_MESSAGE cell broadcast channels "+(result?"success":"fail"));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                gbc.setMsgId(gbcMsgId);
                gbc.setSpKey(gbcSpKey);
            }
        }
    }

    public static void disableCMASCivilEmergencyMessageAlert(Context context, int slotId) {
        GsmBroadcastConfigurator gbc = GsmBroadcastConfigurator.getInstance(context);
        if (gbc != null) {
            int gbcMsgId = gbc.getMsgId();
            String gbcSpKey = gbc.getSpKey();
            try {
                gbc.setMsgId(MESSAGE_ID_CMAS_ALERT_CIVIL_EMERGENCY_MESSAGE);
                String prefKey = (slotId == GsmUmtsCellBroadcastSmsFragmentBase.SLOTID_SLOT1 ? "" : GsmUmtsCellBroadcastSmsFragmentBase.PREFIX_SIM2)
                        + String.valueOf(MESSAGE_ID_CMAS_ALERT_CIVIL_EMERGENCY_MESSAGE);
                gbc.setSpKey(prefKey);
                boolean result = gbc.switchService(false, slotId);
                if (DBG) {
                    Log.d(TAG, "disableCMASCivilEmergencyMessageAlert() with GsmBroadcastConfigurator");
                    Log.d(TAG, "disabling slotId= " + slotId + ", MESSAGE_ID_CMAS_ALERT_CIVIL_EMERGENCY_MESSAGE cell broadcast channels "+(result?"success":"fail"));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                gbc.setMsgId(gbcMsgId);
                gbc.setSpKey(gbcSpKey);
            }
        }
    }

    public static void enableCMASCivilEmergencyMessageAlertAdditionalLanguages(Context context, int slotId) {
        GsmBroadcastConfigurator gbc = GsmBroadcastConfigurator.getInstance(context);
        if (gbc != null) {
            int gbcMsgId = gbc.getMsgId();
            String gbcSpKey = gbc.getSpKey();
            try {
                gbc.setMsgId(MESSAGE_ID_CMAS_ALERT_CIVIL_EMERGENCY_MESSAGE_ADDITIONAL_LANGUAGES);
                String prefKey = (slotId == GsmUmtsCellBroadcastSmsFragmentBase.SLOTID_SLOT1 ? "" : GsmUmtsCellBroadcastSmsFragmentBase.PREFIX_SIM2)
                        + String.valueOf(MESSAGE_ID_CMAS_ALERT_CIVIL_EMERGENCY_MESSAGE_ADDITIONAL_LANGUAGES);
                gbc.setSpKey(prefKey);
                boolean result = gbc.switchService(true, slotId);
                if (DBG) {
                    Log.d(TAG, "enableCMASCivilEmergencyMessageAlertAdditionalLanguages() with GsmBroadcastConfigurator");
                    Log.d(TAG, "enabling slotId= " + slotId + ", MESSAGE_ID_CMAS_ALERT_CIVIL_EMERGENCY_MESSAGE_ADDITIONAL_LANGUAGES cell broadcast channels "+(result?"success":"fail"));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                gbc.setMsgId(gbcMsgId);
                gbc.setSpKey(gbcSpKey);
            }
        }
    }

    public static void disableCMASCivilEmergencyMessageAlertAdditionalLanguages(Context context, int slotId) {
        GsmBroadcastConfigurator gbc = GsmBroadcastConfigurator.getInstance(context);
        if (gbc != null) {
            int gbcMsgId = gbc.getMsgId();
            String gbcSpKey = gbc.getSpKey();
            try {
                gbc.setMsgId(MESSAGE_ID_CMAS_ALERT_CIVIL_EMERGENCY_MESSAGE_ADDITIONAL_LANGUAGES);
                String prefKey = (slotId == GsmUmtsCellBroadcastSmsFragmentBase.SLOTID_SLOT1 ? "" : GsmUmtsCellBroadcastSmsFragmentBase.PREFIX_SIM2)
                        + String.valueOf(MESSAGE_ID_CMAS_ALERT_CIVIL_EMERGENCY_MESSAGE_ADDITIONAL_LANGUAGES);
                gbc.setSpKey(prefKey);
                boolean result = gbc.switchService(false, slotId);
                if (DBG) {
                    Log.d(TAG, "disableCMASCivilEmergencyMessageAlertAdditionalLanguages() with GsmBroadcastConfigurator");
                    Log.d(TAG, "disabling slotId= " + slotId + ", MESSAGE_ID_CMAS_ALERT_CIVIL_EMERGENCY_MESSAGE_ADDITIONAL_LANGUAGES cell broadcast channels "+(result?"success":"fail"));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                gbc.setMsgId(gbcMsgId);
                gbc.setSpKey(gbcSpKey);
            }
        }
    }
    // --- joey_lee, add channels for Civil Emergency Message

    // +++ gary_hsu@20160307: NCC PLMN08/10 20151222 spec
    public static void enableCMASEmergencyAlertAdditionalLanguageForTw(long subId) {
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            boolean result = iccISms.enableCellBroadcastRangeForSubscriber((int)subId,
                    MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_ADDITIONAL_LANGUAGES,
                    MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_ADDITIONAL_LANGUAGES,
                    SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
            if (DBG)
                Log.d(TAG,
                        "enabling subId= " + subId
                                + ", MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_ADDITIONAL_LANGUAGES to MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_ADDITIONAL_LANGUAGES cell broadcast channels "
                                + (result ? "success" : "fail"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void disableCMASEmergencyAlertAdditionalLanguageForTw(long subId) {
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            boolean result = iccISms.disableCellBroadcastRangeForSubscriber((int)subId,
                    MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_ADDITIONAL_LANGUAGES,
                    MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_ADDITIONAL_LANGUAGES,
                    SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
            if (DBG)
                Log.d(TAG,
                        "disabled subId= " + subId
                                + ", MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_ADDITIONAL_LANGUAGES to MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_ADDITIONAL_LANGUAGES cell broadcast channels "
                                + (result ? "success" : "fail"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // --- gary_hsu@20160307: NCC PLMN08/10 20151222 spec

    // +++ dion_yang@20131026:For ATT requirement
    public static void enableCMASExtremeAlert(long subId) {
        try {
        ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
        boolean result = iccISms.enableCellBroadcastRangeForSubscriber((int)subId,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "enabling subId= " + subId + ", MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED to MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY cell broadcast channels "+(result?"success":"fail"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void disableCMASExtremeAlert(long subId) {
        try {
        ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
        boolean result = iccISms.disableCellBroadcastRangeForSubscriber((int)subId,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "disabled subId= " + subId + ", MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED to MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY cell broadcast channels "+(result?"success":"fail"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void enableCMASSevereAlert(long subId) {
        try {
        ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
        boolean result = iccISms.enableCellBroadcastRangeForSubscriber((int)subId,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "enabling subId= " + subId + ", MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED to MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY cell broadcast channels "+(result?"success":"fail"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void disableCMASSevereAlert(long subId) {
        try {
        ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
        boolean result = iccISms.disableCellBroadcastRangeForSubscriber((int)subId,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "disabled subId= " + subId + ", MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED to MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY cell broadcast channels "+(result?"success":"fail"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void enableCMASAmberAlert(long subId) {
        try {
        ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
        boolean result = iccISms.enableCellBroadcastForSubscriber((int)subId, SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "enabling subId= " + subId + ", MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY cell broadcast channels "+(result?"success":"fail"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void disableCMASAmberAlert(long subId) {
        try {
        ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
        boolean result = iccISms.disableCellBroadcastForSubscriber((int)subId, SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "disabled subId= " + subId + ", MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY cell broadcast channels "+(result?"success":"fail"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void enableCMASTestAlert(long subId) {
        try {
        ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
        boolean result = iccISms.enableCellBroadcastRangeForSubscriber((int)subId,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "enabling subId= " + subId + ", MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST to MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE cell broadcast channels "+(result?"success":"fail"));
        result = iccISms.enableCellBroadcastForSubscriber((int)subId, MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST_ADDITIONAL_LANGUAGES, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "enabling subId= " + subId + ", MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST_ADDITIONAL_LANGUAGES cell broadcast channels "+(result?"success":"fail"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void disableCMASTestAlert(long subId) {
        try {
        ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
        boolean result = iccISms.disableCellBroadcastRangeForSubscriber((int)subId,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "disabled subId= " + subId + ", MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST to MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE cell broadcast channels "+(result?"success":"fail"));
        result = iccISms.disableCellBroadcastForSubscriber((int)subId, MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST_ADDITIONAL_LANGUAGES, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "disabled subId= " + subId + ", MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST_ADDITIONAL_LANGUAGES cell broadcast channels "+(result?"success":"fail"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void enableCMASFutureVersionsAlert(long subId) {
        try {
        ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
        boolean result = iccISms.enableCellBroadcastRangeForSubscriber((int)subId,
                (SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE+1),
                SmsCbConstants.MESSAGE_ID_PWS_LAST_IDENTIFIER,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "enabling subId= " + subId + ", MESSAGE_ID_CMAS_ALERT_FUTURE_VERSIONS(4383) to MESSAGE_ID_PWS_LAST_IDENTIFIER cell broadcast channels "+(result?"success":"fail"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void disableCMASFutureVersionsAlert(int subId) {
        try {
        ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
        boolean result = iccISms.disableCellBroadcastRangeForSubscriber(subId,
                (SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE+1),
                SmsCbConstants.MESSAGE_ID_PWS_LAST_IDENTIFIER,
                SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
        if (DBG) Log.d(TAG, "disabled subId= " + subId + ", MESSAGE_ID_CMAS_ALERT_FUTURE_VERSIONS(4383) to MESSAGE_ID_PWS_LAST_IDENTIFIER cell broadcast channels "+(result?"success":"fail"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void startAlertPreview(Context context, boolean alertSound) {
        if(context == null) {
            Log.e(TAG,"context was null");
            return;
        }
        // start audio/vibration service for emergency alerts preview
        Intent audioIntent = new Intent(context, CellBroadcastAlertAudio.class);
        audioIntent.setAction(CellBroadcastAlertAudio.ACTION_START_ALERT_AUDIO);
        AlertDialog.Builder alertPreview = new AlertDialog.Builder(context);
        if(alertSound) {
            Log.d(TAG,"alert sound preview");
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_VIBRATE_EXTRA, false);
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_ETWS_VIBRATE_EXTRA, false);
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_DURATION_EXTRA, 10600);
            alertPreview.setMessage(context.getResources().getString(R.string.att_emergency_alerts_alert_sound_preview_title));
        } else {
            Log.d(TAG,"alert vibration preview");
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_ONLY_VIBRATE_EXTRA, true);
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_ETWS_VIBRATE_EXTRA, true);
            alertPreview.setMessage(context.getResources().getString(R.string.att_emergency_alerts_alert_vibration_preview_title));
        }
        alertPreview.setNegativeButton(android.R.string.cancel, null);
        alertPreview.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                Log.d(TAG,"stop sound alert preview");
                CellBroadcastReceiverApp.getApplication().stopService(new Intent(CellBroadcastReceiverApp.getApplication(), CellBroadcastAlertAudio.class));
            }
        });
        
        Log.d(TAG,"start sound alert preview");
        context.startService(audioIntent);
        alertPreview.create().show();
        Log.d(TAG,"start sound alert preview dialog");
    }

    //TODO: Need to update from KK to L
    public static void dumpSIMConfigs(int subscription) {
//        ArrayList<SmsBroadcastConfigInfo> cbsSettings = AsusMSimSmsManager.getInstance().getGsmCellBroadcastConfig(subscription);
//        Log.d(TAG, "SIM "+(subscription == 0 ? 1 : 2));
//        if(cbsSettings != null) {
//            Log.d(TAG, "SIM channel="+cbsSettings.toString());
//            cbsSettings.clear();
//        } else {
//            Log.w(TAG, "SIM channel= null");
//        }
    }
    // --- dion_yang@20131026:For ATT requirement

    // +++ dion_yang@20131023:Add support ATT sku
    public static boolean isATTSku() {
        String sku = SystemProperties.get(SKU_PROPERTIES);
        Log.d(TAG,"Properties sku="+sku);
        if(!TextUtils.isEmpty(sku)) {
            return ATT_SKU.equals(sku);
        } else {
            return false;
        }
    }
    // --- dion_yang@20131023:Add support ATT sku
    // +++ OliverOu@20151119:Add support Verison sku
    public static boolean isVZWSku() {
        String sku = SystemProperties.get(SKU_PROPERTIES);
        Log.d(TAG,"Properties sku="+sku);
        if(!TextUtils.isEmpty(sku)) {
            return VZW_SKU.equals(sku);
        } else {
            return false;
        }
    }
    // --- OliverOu@20151119:Add support Verison sku
    // +++ Oliver_Ou@20150610 : Checking for the TW sku
    public static boolean isTWSku() {
        Boolean isTw = false;
        String sku = SystemProperties.get(SKU_PROPERTIES);
        Log.d(TAG,"Properties sku="+sku);
        if(!TextUtils.isEmpty(sku)) {
          if (sku.equalsIgnoreCase("TW")) {
              isTw = true;
          } else if (sku.equalsIgnoreCase("CHT")) {
              isTw = true;
          }
        } else{
          Log.d(TAG, "Sku information is empty");
        }

        return isTw;
    }
    // --- Oliver_Ou@20150610 : Checking for the TW sku
    // +++ joey_lee, add channels for Civil Emergency Message
    public static boolean isCountryCodeTW() {
        String rtn = "";
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Object ob = c.newInstance();
            Method m = c.getMethod("get", new Class[] {
                    String.class, String.class
            });
            rtn = (String)m.invoke(ob, new Object[] {
                    "ro.config.versatility", ""
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(TAG, "isCountryCodeTW(), ro.config.versatility = " + rtn);
        return rtn.toLowerCase().startsWith("tw");
    }
    // +++ joey_lee, add channels for Civil Emergency Message

    public static boolean isUserMode() {
        String buildType = SystemProperties.get(Build.TYPE);
        boolean isUserMode = !TextUtils.isEmpty(buildType) && "user".equals(buildType);
        Log.d(TAG,"Properties build type= " + buildType + ", is userMode= " + isUserMode);
        return isUserMode;
    }
    // +++ dion_yang@20131031:Fix ETWS GCF mode
    public static boolean isGcfMode() {
        String isSystemGcfMode = SystemProperties.get("persist.asus.gcf.mode");
        String isMessageGcfMode = SystemProperties.get("persist.asus.cb.gcf.mode");
        if ((!TextUtils.isEmpty(isSystemGcfMode) && "1".equals(isSystemGcfMode)) ||
                (!TextUtils.isEmpty(isMessageGcfMode) && "1".equals(isMessageGcfMode))) {
            Log.d(TAG, "is GCF Mode");
            return true;
        } else {
            Log.d(TAG, "is not GCF Mode");
            return false;
        }
    }
    // --- dion_yang@20131031:Fix ETWS GCF mode
    // +++ andrew_tu@20140430 TT-399013 : Hide vibrate alert if DUT has no vibrator.
    public static boolean hasVibrator(Context context) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        return vibrator.hasVibrator();
    }
    // --- andrew_tu@20140430 TT-399013 : Hide vibrate alert if DUT has no vibrator.

    public static void initEmergencyAlertDualMode(final int slotId) {
        try {
            new AsyncTask<Void, Void, Void>() {
                protected Void doInBackground(Void... none) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(CellBroadcastReceiverApp.getApplication());
                    Log.d(TAG, prefs.toString());
                    Resources res = CellBroadcastReceiverApp.getApplication().getResources();
                    // Check for system property defining the emergency channel ranges to enable
                    String emergencyIdRange = SystemProperties.get("ro.cellbroadcast.emergencyids");

                    long subId = 0;
                    try {
                        subId = (long)(SubscriptionManager.getSubId(slotId)[0]);
                    } catch (Exception e) {
                        Log.e(TAG,"initEmergencyAlertDualMode slotId= " + slotId + ", subId= " + subId + ", Exception= ", e);
                    }

                    // +++ lide_yang@20131023: AT&T CDR-ECB-660
                    boolean enableEmergencyAlerts = prefs.getBoolean(getPrefKeyEnableEmergencyAlerts(slotId), true);
                    // --- lide_yang@20131023: AT&T CDR-ECB-660

                    if (enableEmergencyAlerts) {
                        if (DEBUG) Log.d(TAG, "enabling emergency cell broadcast channels");
                        //M:Added by cenxingcan@wind-mobi.com for fixing bug #132168 20161108 begin
                        if (CellBroadcastUtils.mCBEnabledRunnable != null) {
                            //Cenxingcan:We have to override mode data, because of we dont know modem.
                            //Cenxingcan:We must check modem at first or setting channels fail.
                            new Thread(CellBroadcastUtils.mCBEnabledRunnable).start();
                        }
                        //M:Added by cenxingcan@wind-mobi.com for fixing bug #132168 20161108 end
                        if (!TextUtils.isEmpty(emergencyIdRange)) {
                            if (DEBUG) Log.d(TAG, "enabling emergency cell broadcast custom channels");
                            setChannelRange(emergencyIdRange, true, subId);
                        // +++ dion_yang@20131026:For ATT requirement
                        } else if(CellBroadcastUtils.isATTSku()){
                            if (DEBUG) Log.d(TAG, "enabling emergency cell broadcast channels for ATT sku");
                            //Extreme Alert
                            boolean enableCMASExtremeAlerts = prefs.getBoolean(getPrefKeyExtremeAlerts(slotId), true);
                            //Severe Alert
                            boolean enableCMASSevereAlerts = prefs.getBoolean(getPrefKeySevereAlerts(slotId), true);
                            //Amber alert
                            boolean enableCMASAmberAlerts = prefs.getBoolean(getPrefKeyAmberAlerts(slotId), true);
                            //ETWS test
                            boolean enableETWStestAlerts = prefs.getBoolean(getPrefKeyEnableETWSTestAlerts(slotId), false);
                            //CMAS test
                            boolean enableCMAStestAlerts = prefs.getBoolean(getPrefKeyEnableCMASTestAlerts(slotId), false);

                            if (DEBUG) {
                                Log.d(TAG, "initEmergencyAlertDualMode:"
                                        + "\n  slotId" + slotId
                                        + "\n  subId" + subId
                                        + "\n  enableEmergencyAlerts = " + enableEmergencyAlerts
                                        + "\n  enableEtwsTestAlerts = " + enableETWStestAlerts
                                        + "\n  enableCmasExtremeAlerts = " + enableCMASExtremeAlerts
                                        + "\n  enableCmasSevereAlerts = " + enableCMASSevereAlerts
                                        + "\n  enableCmasAmberAlerts = " + enableCMASAmberAlerts
                                        + "\n  enableCmasTestAlerts = " + enableCMAStestAlerts);
                            }

                            CellBroadcastUtils.enableETWSAlert(subId);
                            if(enableETWStestAlerts) {
                                CellBroadcastUtils.enableETWSTestAlert(subId);
                            } else {
                                CellBroadcastUtils.disableETWSTestAlert(subId);
                            }
                            if(enableCMASExtremeAlerts) {
                                CellBroadcastUtils.enableCMASExtremeAlert(subId);
                            } else {
                                CellBroadcastUtils.disableCMASExtremeAlert(subId);
                            }
                            if(enableCMASSevereAlerts) {
                                CellBroadcastUtils.enableCMASSevereAlert(subId);
                            } else {
                                CellBroadcastUtils.disableCMASSevereAlert(subId);
                            }
                            if(enableCMASAmberAlerts) {
                                CellBroadcastUtils.enableCMASAmberAlert(subId);
                            } else {
                                CellBroadcastUtils.disableCMASAmberAlert(subId);
                            }
                            if(enableCMAStestAlerts) {
                                CellBroadcastUtils.enableCMASTestAlert(subId);
                            } else {
                                CellBroadcastUtils.disableCMASTestAlert(subId);
                            }
                        } else {
                            // No emergency channel system property, enable all emergency channels
                            CellBroadcastUtils.enableETWSAlert(subId);
                            CellBroadcastUtils.enableETWSTestAlert(subId);
                            CellBroadcastUtils.enableCMASExtremeAlert(subId);
                            CellBroadcastUtils.enableCMASSevereAlert(subId);
                            CellBroadcastUtils.enableCMASAmberAlert(subId);
                            CellBroadcastUtils.enableCMASTestAlert(subId);
                            // +++ gary_hsu@20160307: NCC PLMN08/10 20151222 spec
                            if(isTWSku() || isCountryCodeTW()){
                                CellBroadcastUtils.enableCMASCivilEmergencyMessageAlert(CellBroadcastReceiverApp.getApplication(), slotId);
                                CellBroadcastUtils.enableCMASCivilEmergencyMessageAlertAdditionalLanguages(CellBroadcastReceiverApp.getApplication(), slotId);
                                CellBroadcastUtils.enableCMASEmergencyAlertAdditionalLanguageForTw(subId);
                            }
                            // --- gary_hsu@20160307: NCC PLMN08/10 20151222 spec
                            if (DEBUG) Log.d(TAG, "enabled all ETWS/CMAS emergency cell broadcast channels");
                        }
                    } else {
                        // we may have enabled these channels previously, so try to disable them
                        if (DEBUG) Log.d(TAG, "disabling emergency cell broadcast channels");
                        if (!TextUtils.isEmpty(emergencyIdRange)) {
                            if (DEBUG) Log.d(TAG, "disabling emergency cell broadcast custom channels");
                            setChannelRange(emergencyIdRange, false, subId);
                        } else {
                            // No emergency channel system property, disable all emergency channels
                            CellBroadcastUtils.disableETWSAlert(subId);
                            CellBroadcastUtils.disableETWSTestAlert(subId);
                            CellBroadcastUtils.disableCMASExtremeAlert(subId);
                            CellBroadcastUtils.disableCMASSevereAlert(subId);
                            CellBroadcastUtils.disableCMASAmberAlert(subId);
                            CellBroadcastUtils.disableCMASTestAlert(subId);
                            // +++ gary_hsu@20160307: NCC PLMN08/10 20151222 spec
                            if(isTWSku() || isCountryCodeTW()){
                                CellBroadcastUtils.disableCMASCivilEmergencyMessageAlert(CellBroadcastReceiverApp.getApplication(), slotId);
                                CellBroadcastUtils.disableCMASCivilEmergencyMessageAlertAdditionalLanguages(CellBroadcastReceiverApp.getApplication(), slotId);
                                CellBroadcastUtils.disableCMASEmergencyAlertAdditionalLanguageForTw(subId);
                            }
                            // --- gary_hsu@20160307: NCC PLMN08/10 20151222 spec
                            if (DEBUG) Log.d(TAG, "disabled all ETWS/CMAS emergency cell broadcast channels");
                        }
                    }
                    // Presidential must on
                    CellBroadcastUtils.enableCMASPresidentialAlert(subId);
                    CellBroadcastUtils.enableCMASPresidentialAlertAdditionalLanguages(subId); // dion_yang@20140225:Apply NCC LTE spec
                    //TODO: Need to update from KK to L
                    //CellBroadcastUtils.dumpSIMConfigs(subId);
                    // --- dion_yang@20131026:For ATT requirement
                    return null;
                }
            }.execute();
        } catch (Exception ex) {
            Log.e(TAG, "exception enabling cell broadcast channels", ex);
            ex.printStackTrace();
        }
    }

    private static void setChannelRange(String ranges, boolean enable, long subId) {
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            for (String channelRange : ranges.split(",")) {
                int dashIndex = channelRange.indexOf('-');
                if (dashIndex != -1) {
                    int startId = Integer.decode(channelRange.substring(0, dashIndex));
                    int endId = Integer.decode(channelRange.substring(dashIndex + 1));
                    if (enable) {
                        if (DEBUG) Log.d(TAG, "enabling emergency IDs " + startId + '-' + endId);
                        iccISms.enableCellBroadcastRangeForSubscriber((int)subId, startId, endId, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    } else {
                        if (DEBUG) Log.d(TAG, "disabling emergency IDs " + startId + '-' + endId);
                        iccISms.disableCellBroadcastRangeForSubscriber((int)subId, startId, endId, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    }
                } else {
                    int messageId = Integer.decode(channelRange);
                    if (enable) {
                        if (DEBUG) Log.d(TAG, "enabling emergency message ID " + messageId);
                        iccISms.enableCellBroadcastForSubscriber((int)subId, messageId, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    } else {
                        if (DEBUG) Log.d(TAG, "disabling emergency message ID " + messageId);
                        iccISms.disableCellBroadcastForSubscriber((int)subId, messageId, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "setChannelRange Exception parsing emergency channel range", e);
            e.printStackTrace();
        }
    }

    private static void setChannelRange(SmsManager manager, String ranges, boolean enable) {
        try {
            for (String channelRange : ranges.split(",")) {
                int dashIndex = channelRange.indexOf('-');
                if (dashIndex != -1) {
                    int startId = Integer.decode(channelRange.substring(0, dashIndex));
                    int endId = Integer.decode(channelRange.substring(dashIndex + 1));
                    if (enable) {
                        if (DEBUG) Log.d(TAG, "enabling emergency IDs " + startId + '-' + endId);
                        manager.enableCellBroadcastRange(startId, endId, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    } else {
                        if (DEBUG) Log.d(TAG, "disabling emergency IDs " + startId + '-' + endId);
                        manager.disableCellBroadcastRange(startId, endId, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    }
                } else {
                    int messageId = Integer.decode(channelRange);
                    if (enable) {
                        if (DEBUG) Log.d(TAG, "enabling emergency message ID " + messageId);
                        manager.enableCellBroadcast(messageId, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    } else {
                        if (DEBUG) Log.d(TAG, "disabling emergency message ID " + messageId);
                        manager.disableCellBroadcast(messageId, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                    }
                }
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Number Format Exception parsing emergency channel range", e);
        }
    }

    public static String getPrefKeyEnableEmergencyAlerts(int slotId) {
        return (slotId == 0) ? GsmUmtsCellBroadcastSmsFragment.PREF_KEY_ENABLE_EMBERGENCYALERTS :
            GsmUmtsCellBroadcastSmsFragment2.PREF_KEY_ENABLE_EMBERGENCYALERTS;
    }

    public static String getPrefKeyExtremeAlerts(int slotId) {
        return (slotId == 0) ? GsmUmtsCellBroadcastSmsFragment.PREF_KEY_EXTREMEALERTS :
            GsmUmtsCellBroadcastSmsFragment2.PREF_KEY_EXTREMEALERTS;
    }

    public static String getPrefKeySevereAlerts(int slotId) {
        return (slotId == 0) ? GsmUmtsCellBroadcastSmsFragment.PREF_KEY_SEVEREALERTS :
            GsmUmtsCellBroadcastSmsFragment2.PREF_KEY_SEVEREALERTS;
    }

    public static String getPrefKeyAmberAlerts(int slotId) {
        return (slotId == 0) ? GsmUmtsCellBroadcastSmsFragment.PREF_KEY_AMBERALERTS :
            GsmUmtsCellBroadcastSmsFragment2.PREF_KEY_AMBERALERTS;
    }

    public static String getPrefKeyEnableETWSTestAlerts(int slotId) {
        return (slotId == 0) ? GsmUmtsCellBroadcastSmsFragment.PREF_KEY_ENABLE_ETWSTESTALERTS:
            GsmUmtsCellBroadcastSmsFragment2.PREF_KEY_ENABLE_ETWSTESTALERTS;
    }

    public static String getPrefKeyEnableCMASTestAlerts(int slotId) {
        return (slotId == 0) ? GsmUmtsCellBroadcastSmsFragment.PREF_KEY_ENABLE_CMASTESTALERTS:
            GsmUmtsCellBroadcastSmsFragment2.PREF_KEY_ENABLE_CMASTESTALERTS;
    }

    public static String getPrefKeyEnableAlertVibration(int slotId) {
        return (slotId == 0) ? GsmUmtsCellBroadcastSmsFragment.PREF_KEY_ENABLE_ALERTVIBRATION :
            GsmUmtsCellBroadcastSmsFragment2.PREF_KEY_ENABLE_ALERTVIBRATION;
    }

    public static String getPrefKeySMSCBLanguage(int slotId) {
        return (slotId == 0) ? GsmUmtsCellBroadcastSmsFragment.PREF_KEY_SMSCBLANGUAGE :
            GsmUmtsCellBroadcastSmsFragment2.PREF_KEY_SMSCBLANGUAGE;
    }
    //M:Added by cenxingcan@wind-mobi.com for fixing bug #132168 20161108 begin
    /*
    * TODO:To fix this situation : Should not set CB info when CB is disabled.
    * */
    public static Runnable mCBEnabledRunnable = new Runnable() {
        @Override
        public void run() {
            if (SmsManager.getDefault().activateCellBroadcastSms(true)) {
                Log.d(TAG, "mCBEnabledRunnable ->> activateCellBroadcastSms -->> success");
            } else {
                Log.d(TAG, "mCBEnabledRunnable ->> activateCellBroadcastSms -->> fail");
                final boolean disable = SmsManager.getDefault().activateCellBroadcastSms(false);
                Log.d(TAG, "mCBEnabledRunnable ->> disable activateCellBroadcastSms ? " + (disable ? "success" : "fail"));
                if (SmsManager.getDefault().activateCellBroadcastSms(true)) {
                    Log.d(TAG, "mCBEnabledRunnable ->> try again activateCellBroadcastSms -->> success");
                } else {
                    Log.d(TAG, "mCBEnabledRunnable ->> try again activateCellBroadcastSms -->> fail");
                }
            }
        }
    };
    //M:Added by cenxingcan@wind-mobi.com for fixing bug #132168 20161108 end
}
