/*
 * Copyright (c) 2011, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Code Aurora Forum, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.cellbroadcastreceiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.ServiceManager;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.internal.telephony.ISms;

/*
 * Control gsm broadcast configuration using this class.
 * As of now there is support for message_id 50 only.
 * This can be extended to support any number of message_ids.
 */
public class GsmBroadcastConfigurator {
    private static final String TAG = "GsmBroadcastConfigurator";
    private static GsmBroadcastConfigurator sInstance = null;
    // Asus Jenpang begin: porting layer implementation
    //int phoneCount = AsusTelephonyManager.getInstance().getPhoneCount();
    // int phoneCount = MSimTelephonyManager.getDefault().getPhoneCount();
    // Asus Jenpang end: porting layer implementation
    boolean mConfig[] = new boolean[1];

    // Message IDs. dion modify
    public static int AREA_INFO_MSG_ID = 50;

    // String keys for shared preference lookup
    private static String SP_KEY = "";

    private Context mContext;

    private GsmBroadcastConfigurator() {
        // not allow initiating without parameters
    }

    public static GsmBroadcastConfigurator getInstance(Context context) {
        if (sInstance == null) {
            synchronized(GsmBroadcastConfigurator.class){
                if(sInstance == null){
                    sInstance = new GsmBroadcastConfigurator();
                    sInstance.setContext(context);
                }
            }
        }
        return sInstance;
    }

    private void setContext(Context context) {
        mContext = context;
    }

    public boolean switchService(boolean newStateIsOn, int slotId) {
        boolean ret = true;
        Context context = mContext;
        // dion:We have to override mode data, because of we dont know modem
        // really status
        // we close Switch Service, because A66 non-support DSDS
        // if (mConfig[mSubscription][0] != newStateIsOn) {
        ret = smsManagerSwitchService(newStateIsOn, slotId);
        if (ret) {
            mConfig[0] = newStateIsOn;

            SharedPreferences sp = context.getSharedPreferences(CellBroadcastUtils.getMsgIdPrefsName(), Context.MODE_PRIVATE);
            SharedPreferences.Editor spe = sp.edit();
            String spKey = SP_KEY;
            spe.putBoolean(spKey, newStateIsOn);
            spe.commit();
        } else {
            Log.d(TAG, "switchService(" + newStateIsOn + ", slotId= " + slotId + "):");
			/// M: Remove disable action from Asus AP or leading to AT> AT+CBSB=1,added by cenxingcan@wind-mobi.com 20160817 begin.
            /** origin code
            Log.d(TAG, "We may " + (newStateIsOn ? "enable already " : "disable not ")
                    + "exist channel Id, we try to " +
                    (!newStateIsOn ? "enable" : "disable") + " it first than re-"
                    + (newStateIsOn ? "enable" : "disable") + " it");
            ret = smsManagerSwitchService(!newStateIsOn, slotId);
            Log.d(TAG, "try to " + (!newStateIsOn ? "enable" : "disable") + " it first:"
                    + (ret ? "Success" : "Fail"));
            if (ret) {
                Log.d(TAG, "try to " + (!newStateIsOn ? "enable" : "disable")
                        + " it first success try " + (newStateIsOn ? "enable" : "disable")
                        + " it again!");
                ret = smsManagerSwitchService(newStateIsOn, slotId);
                Log.d(TAG, "try " + (newStateIsOn ? "enable" : "disable") + " it again:"
                        + (ret ? "Success" : "Fail"));
            }
            **/
            //M:Added by cenxingcan@wind-mobi.com for fixing bug #132168 20161108 begin
            if (CellBroadcastUtils.mCBEnabledRunnable != null) {
                //Cenxingcan:We have to override mode data, because of we dont know modem.
                //Cenxingcan:We must check modem at first or setting channels fail.
                new Thread(CellBroadcastUtils.mCBEnabledRunnable).start();
            }
            //M:Added by cenxingcan@wind-mobi.com for fixing bug #132168 20161108 end
            ret = smsManagerSwitchService(newStateIsOn, slotId);
            /// M: Remove disable action from Asus AP or leading to AT> AT+CBSB=1,added by cenxingcan@wind-mobi.com 20160817 end
            if (ret) {
                Log.d(TAG, "try " + (newStateIsOn ? "enable" : "disable") + " it again Success");
                mConfig[0] = newStateIsOn;
                SharedPreferences sp = context.getSharedPreferences(CellBroadcastUtils.getMsgIdPrefsName(), Context.MODE_PRIVATE);
                SharedPreferences.Editor spe = sp.edit();
                String spKey = SP_KEY;
                spe.putBoolean(spKey, newStateIsOn);
                spe.commit();
            } else {
                Log.d(TAG, "try " + (newStateIsOn ? "enable" : "disable") + " it again Fail");
                mConfig[0] = newStateIsOn;

                SharedPreferences sp = context.getSharedPreferences(CellBroadcastUtils.getMsgIdPrefsName(), Context.MODE_PRIVATE);
                SharedPreferences.Editor spe = sp.edit();
                String spKey = SP_KEY;
                spe.putBoolean(spKey, !newStateIsOn);
                spe.commit();
            }
        }
        // }

        return ret;
    }

    private boolean smsManagerSwitchService(boolean newStateIsOn, int slotId) {
        Log.d(TAG, "smsManagerSwitchService AREA_INFO_MSG_ID=" + AREA_INFO_MSG_ID);
        if (CellBroadcastUtils.isMultiSimEnabled() && !CellBroadcastUtils.isMultiSimDSDA()) {
            //long subId = SubscriptionManager.getSubId(slotId)[0];
            int subId = SubscriptionManager.getSubId(slotId)[0];
            Log.d(TAG,"Multi-Sim Enabled, try to set slot = " + slotId + ", sub= " + subId);
            try {
                ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
                if (newStateIsOn) {
                    return iccISms.enableCellBroadcastForSubscriber((int)subId, AREA_INFO_MSG_ID, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                } else {
                    return iccISms.disableCellBroadcastForSubscriber((int)subId, AREA_INFO_MSG_ID, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
                }
            } catch(Exception e) {
                e.printStackTrace();
                return false;
            }
        } else {
            Log.d(TAG,"Multi-Sim Disabled");
            SmsManager sm = SmsManager.getDefault();
            if (newStateIsOn) {
                return sm.enableCellBroadcast(AREA_INFO_MSG_ID, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
            } else {
                return sm.disableCellBroadcast(AREA_INFO_MSG_ID, SmsManager.CELL_BROADCAST_RAN_TYPE_GSM);
            }
        }
        
        // Asus Jenpang end: porting layer implementation
    }

    public boolean isMsgIdSupported(int msgId) {
        if (msgId == AREA_INFO_MSG_ID) {
            return true;
        }

        return false;
    }

    public int getMsgId() {
        return AREA_INFO_MSG_ID;
    }

    public void setMsgId(int msgId) {
        AREA_INFO_MSG_ID = msgId;
    }

    // +++ joey_lee, add channels for Civil Emergency Message
    public String getSpKey() {
        return SP_KEY;
    }

    public void setSpKey(String spKey) {
        SP_KEY = spKey;
    }
    // --- joey_lee, add channels for Civil Emergency Message
}
