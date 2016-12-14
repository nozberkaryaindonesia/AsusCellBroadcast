
package com.android.cellbroadcastreceiver;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.cellbroadcastreceiver.GsmUmtsCellBroadcastSmsFragment;

public class BroadcastConfigInstantiatorService extends IntentService {
    private static final String LOG_TAG = "BroadcastConfigInstantiatorIntentService";
    private static final Boolean DEBUG = true;
    private static final Boolean TURN_ON_INITIAL = true;
    private Map<String, Boolean> mIDs = new HashMap<String, Boolean>();
    private Map<String, Boolean> mID2s = new HashMap<String, Boolean>(); // dion_yang@20130912 : Add DSDS get from SIM
    // ASUS BEGIN, dion_yang:For get From SIM Card container
    private Map<String, Boolean> mOnSimIDs = new HashMap<String, Boolean>();
    private Map<String, Boolean> mOnSim2IDs = new HashMap<String, Boolean>(); // dion_yang@20130912 : Add DSDS get from SIM
    // ASUS END, dion_yang:For get From SIM Card container

    // +++ dion_yang@20130912 : Add DSDS get from SIM
    private static final int INITIAL_SIM1 = 0;
    private static final int INITIAL_SIM2 = 1;
    private static final int INITIAL_BOTH = 2;
    // --- dion_yang@20130912 : Add DSDS get from SIM
    private static final String SKU_PROPERTIES = "ro.build.asus.sku";// +++ Oliver_Ou@20150610 : Checking for the sku
    // +++ dion_yang@20131015:Moving Cb service to non-stick
    public BroadcastConfigInstantiatorService() {
        super(LOG_TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (DEBUG) Log.d(LOG_TAG, "onStartCommand:intent=" + intent);
        // ASUS BEGIN, Rejecting received abnormal intent which was null
        if (intent == null) {
            Log.w(LOG_TAG, "intent was null");
            return;
        }
        // ASUS END, Rejecting received abnormal intent which was null
        if (intent.getAction().equals(CellBroadcastReceiver.ACTION_UPDATING_CBS_CHANNEL)
                && TURN_ON_INITIAL) {
            // +++ dion_yang@20130912 : Add DSDS get from SIM
            if(CellBroadcastUtils.isMultiSimEnabled()  && !CellBroadcastUtils.isMultiSimDSDA()) {
                initialOnDualSIMDevice();
            } else {
                initialOnSingleSIMDevice();
            }
            // --- dion_yang@20130912 : Add DSDS get from SIM
        }
    }
    // --- dion_yang@20131015:Moving Cb service to non-stick

    // +++ dion_yang@20130912 : Add DSDS get from SIM
    private void initialOnDualSIMDevice() {
        if (DEBUG) Log.d(LOG_TAG, "initialOnDualSIMDevice");
        if(CellBroadcastUtils.isMultiSimEnabled() && !CellBroadcastUtils.isMultiSimDSDA()) {
            if(CellBroadcastUtils.isSimReady(0) && CellBroadcastUtils.isSimReady(1)) {
                if (DEBUG) Log.d(LOG_TAG, "sim 1 and sim2 are ready");
                initialDualSim(INITIAL_BOTH);
            } else if(CellBroadcastUtils.isSimReady(0)) {
                if (DEBUG) Log.d(LOG_TAG, "sim 1 is ready");
                initialDualSim(INITIAL_SIM1);
            } else if(CellBroadcastUtils.isSimReady(1)) {
                if (DEBUG) Log.d(LOG_TAG, "sim 2 is ready");
                initialDualSim(INITIAL_SIM2);
            } else {
                if (DEBUG) Log.w(LOG_TAG, "no exist any ready sim");
            }
        }
    }

    private void initialDualSim(int initialMode) {
        if (DEBUG) Log.d(LOG_TAG, "initialDualSim initialMode="+initialMode);
        SharedPreferences mChannel1Prefs = getSharedPreferences(
                CellBroadcastUtils.getMsgIdPrefsName(), Context.MODE_PRIVATE);
        //SharedPreferences.Editor sim1Editor = mChannel1Prefs.edit();
        SharedPreferences mChannel2Prefs = getSharedPreferences(
                CellBroadcastUtils.getMsgIdPrefsName(), Context.MODE_PRIVATE);
        //SharedPreferences.Editor sim2Editor = mChannel2Prefs.edit();
        switch(initialMode) {
            case INITIAL_SIM1:
                try {
                    mIDs = loadPrefs(mChannel1Prefs);
                    if (DEBUG) {
                        Log.d(LOG_TAG, "mIDs="+mIDs.toString());
                        Log.d(LOG_TAG, "mChannel1Prefs:" + mChannel1Prefs.getAll().toString());
                    }
                    getChannelIdFromSIM(0, mOnSimIDs, mIDs);
                    Log.d(LOG_TAG, "AfterLoading:");
                    if (DEBUG) dumpMap();
                    syncWithSimAndPrefs(0, mOnSimIDs, mIDs, mChannel1Prefs);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case INITIAL_SIM2:
                try {
                    mID2s = loadPrefs(mChannel2Prefs);
                    if (DEBUG) {
                        Log.d(LOG_TAG, "mID2s="+mID2s.toString());
                        Log.d(LOG_TAG, "mChannel2Prefs:" + mChannel2Prefs.getAll().toString());
                    }
                    getChannelIdFromSIM(1, mOnSim2IDs, mID2s);
                    Log.d(LOG_TAG, "AfterLoading:");
                    if (DEBUG) dumpMap();
                    syncWithSimAndPrefs(1, mOnSim2IDs, mID2s, mChannel2Prefs);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case INITIAL_BOTH:
                try {
                    mIDs = loadPrefs(mChannel1Prefs);
                    mID2s = loadPrefs(mChannel2Prefs);
                    if (DEBUG) {
                        Log.d(LOG_TAG, "mIDs="+mIDs.toString());
                        Log.d(LOG_TAG, "mChannel1Prefs:" + mChannel1Prefs.getAll().toString());
                        Log.d(LOG_TAG, "mID2s="+mID2s.toString());
                        Log.d(LOG_TAG, "mChannel2Prefs:" + mChannel2Prefs.getAll().toString());
                    }
                    getChannelIdFromSIM(0, mOnSimIDs, mIDs);
                    getChannelIdFromSIM(1, mOnSim2IDs, mID2s);
                    Log.d(LOG_TAG, "AfterLoading:");
                    if (DEBUG) dumpMap();
                    syncWithSimAndPrefs(0, mOnSimIDs, mIDs, mChannel1Prefs);
                    syncWithSimAndPrefs(1, mOnSim2IDs, mID2s, mChannel2Prefs);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            default:
                Log.w(LOG_TAG,"loadPrefs with error mode mode="+initialMode);
                break;
        }
    }

    private Map<String, Boolean> loadPrefs(SharedPreferences mChannelPrefs) {
        Map<String, Boolean> mIDs = (Map<String, Boolean>) mChannelPrefs.getAll();
        // To filter null key value
        if(mIDs != null) {
            mIDs.remove("");
            return mIDs;
        } else {
            Log.w(LOG_TAG, "mIDs was null");
            return new HashMap<String, Boolean>();
        }
    }

    private void syncWithSimAndPrefs(final int slotId, final Map<String, Boolean> mOnSimIDs,
            final Map<String, Boolean> mIDs, final SharedPreferences mChannelPrefs) {
        final boolean isFOTA = isFOTA();
        // M: Modified by cenxingcan@wind-mobi.com for bug #136426 20161025 begin
        final boolean enableChannel50Support = CellBroadcastUtils.isBrazil(this);  //Only default enable in Brazil.
        Log.d(LOG_TAG, "syncWithSimAndPrefs default enableChannel50Support " + enableChannel50Support);
        // M: Modified by cenxingcan@wind-mobi.com for bug #136426 20161025 end
        if (mIDs.isEmpty() && mOnSimIDs.isEmpty()) {
            // ASUS END,dion_yang:We get Channel ID from SIM Card
            if (DEBUG) {
                Log.d(LOG_TAG, "syncWithSimAndPrefs slotId= " + slotId
                        + " mIDs is empty, we initial channel 50");
            }
            // For EMT and Tim, Europe operator request turn off the
            // default value in Cell Broadcast
            // M: Modified by cenxingcan@wind-mobi.com for bug #136426 20161025 begin
            //addChannelToPerfs("50", false, slotId, mChannelPrefs.edit(), isFOTA);  //origin code
            Log.d(LOG_TAG, "syncWithSimAndPrefs firt boot isBrazil ? " + enableChannel50Support);
            addChannelToPerfs("50", enableChannel50Support, slotId, mChannelPrefs.edit(), isFOTA);
            // M: Modified by cenxingcan@wind-mobi.com for bug #136426 20161025 end
            // For EMT and Tim, Europe operator request turn off the
            // default value in Cell Broadcast
            updateCBSVersion(isFOTA);
            
        } else {
            // +++ dion_yang@20131015:Moving Cb service to non-stick
            if (DEBUG) {
                Log.d(LOG_TAG, "syncWithSimAndPrefs slotId=" + slotId
                    + " mIDs existed, we load existed channel list");
            }
            loadAlreadyExistedAddedID(slotId, mIDs, mOnSimIDs, mChannelPrefs.edit(), isFOTA);
            
            updateCBSVersion(isFOTA);
            // --- dion_yang@20131015:Moving Cb service to non-stick
        }

        // ASUS BEGIN, dion_yang:Support ETWS channel config
        if(slotId !=0 && slotId != 1){
            Log.w(LOG_TAG, "syncWithSimAndPrefs error subscription");
        }else{
            CellBroadcastUtils.initEmergencyAlertDualMode(slotId);
        }
        // ASUS END, dion_yang:Support ETWS channel config
    }

    private void initialOnSingleSIMDevice() {
        if (DEBUG) Log.d(LOG_TAG, "initialOnSingleSIMDevice");
        TelephonyManager mTelephonyManager =
                (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        // M: Modified by cenxingcan@wind-mobi.com for bug #136426 20161025 begin
        final boolean enableChannel50Support = CellBroadcastUtils.isBrazil(this);  //Only default enable in Brazil.
        Log.d(LOG_TAG, "initialOnSingleSIMDevice default enableChannel50Support " + enableChannel50Support);
        // M: Modified by cenxingcan@wind-mobi.com for bug #136426 20161025 end
        if (mTelephonyManager.hasIccCard()) {
            final SharedPreferences mChannelPrefs = getSharedPreferences(
                    CellBroadcastUtils.getMsgIdPrefsName(), Context.MODE_PRIVATE);
            SharedPreferences.Editor mPrefsEditor = mChannelPrefs.edit();
            // ASUS BEGIN, dion_yang:for determine we are FOTA from A66
            final boolean isFOTA = isFOTA();
            // ASUS END, dion_yang:for determine we are FOTA from A66

            try {
                mIDs = (Map<String, Boolean>) mChannelPrefs.getAll();
                // To filter null key value
                mIDs.remove("");
                if (DEBUG) Log.d(LOG_TAG, mIDs.toString());
                if (DEBUG) Log.d(LOG_TAG, "channelPrefs:" + mChannelPrefs.getAll().toString());
            } catch (Exception e) {
                e.printStackTrace();
            }

            // ASUS BEGIN,dion_yang:We get Channel ID from SIM Card
            getChannelIdFromSIM();
            if (DEBUG) Log.d(LOG_TAG, "mIDs= " + mIDs.toString());
            if (DEBUG) Log.d(LOG_TAG, "mOnSimIDs= " + mOnSimIDs.toString());

            if (mIDs.isEmpty() && mOnSimIDs.isEmpty()) {
                // ASUS END,dion_yang:We get Channel ID from SIM Card
                if (DEBUG) Log.d(LOG_TAG, "onStartCommand:mIDs is empty, we initial channel 50");
                // For EMT and Tim, Europe operator request turn off the
                // default value in Cell Broadcast
                // M: Modified by cenxingcan@wind-mobi.com for bug #136426 20161025 begin
                //addChannelToPerfs("50", false, 0, mPrefsEditor, isFOTA);  //origin code
                Log.d(LOG_TAG, "initialOnSingleSIMDevice firt boot isBrazil ? " + enableChannel50Support);
                addChannelToPerfs("50", enableChannel50Support, 0, mPrefsEditor, isFOTA);
                // M: Modified by cenxingcan@wind-mobi.com for bug #136426 20161025 end
                // For EMT and Tim, Europe operator request turn off the
                // default value in Cell Broadcast
                updateCBSVersion(isFOTA);
            } else {
                // +++ dion_yang@20131015:Moving Cb service to non-stick
                if (DEBUG) Log.d(LOG_TAG, "onStartCommand:mIDs existed, we load existed channel list");
                loadAlreadyExistedAddedID(0, mIDs, mOnSimIDs, mPrefsEditor, isFOTA);
                
                updateCBSVersion(isFOTA);
                // --- dion_yang@20131015:Moving Cb service to non-stick
            }
            // ASUS BEGIN, dion_yang:Support ETWS channel config
            CellBroadcastUtils.initEmergencyAlertDualMode(0);
            // ASUS END, dion_yang:Support ETWS channel config
        } else {
            Log.w(LOG_TAG, "We don't have SIM Card insert, Cancel initial CBS!");
        }
    }
    // --- dion_yang@20130912 : Add DSDS get from SIM

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(LOG_TAG, "onDestroy");
        super.onDestroy();
        mIDs.clear();
        mID2s.clear(); // dion_yang@20130912 : Add DSDS get from SIM
        mOnSimIDs.clear();
        mOnSim2IDs.clear(); // dion_yang@20130912 : Add DSDS get from SIM
        mIDs = null;
        mOnSimIDs = null;
        // +++ dion_yang@20130912 : Add DSDS get from SIM
        mID2s = null;
        mOnSim2IDs = null;
        // --- dion_yang@20130912 : Add DSDS get from SIM
    }

    // +++ dion_yang@20130912 : Add DSDS get from SIM
    private void addChannelToPerfs(String id, boolean enable, int slotId,
            SharedPreferences.Editor mPrefsEditor, boolean isFOTA) {
    // --- dion_yang@20130912 : Add DSDS get from SIM
        if (DEBUG) {
            Log.d(LOG_TAG, "addChannelToPerfs:id=" + id + " status:"
                    + (enable ? "enable" : "disable") + " slotId=" + slotId + " isFOTA=" + isFOTA);
        }
        if (!enable) {
            String key = ((slotId == GsmUmtsCellBroadcastSmsFragmentBase.SLOTID_SLOT2) ? GsmUmtsCellBroadcastSmsFragmentBase.PREFIX_SIM2 + id : id);
            Log.d(LOG_TAG, "key = " + key);
            mPrefsEditor.putBoolean(key, false);
            mPrefsEditor.commit();
            if (isFOTA) { // If it was fota from a66, we reset all channel id
                           // to disable status
                enableChannel(id, false, slotId);
            }
            return;
        }

        if (enableChannel(id, enable, slotId)) { // dion_yang@20130912 : Add DSDS get from SIM
            String key = slotId == GsmUmtsCellBroadcastSmsFragmentBase.SLOTID_SLOT2 ? GsmUmtsCellBroadcastSmsFragmentBase.PREFIX_SIM2 + id : id;
            mPrefsEditor.putBoolean(key, enable);
        } else {
            // If failed, This channel must be disable status
            String key = slotId == GsmUmtsCellBroadcastSmsFragmentBase.SLOTID_SLOT2 ? GsmUmtsCellBroadcastSmsFragmentBase.PREFIX_SIM2 + id : id;
            mPrefsEditor.putBoolean(key, false);
        }
        mPrefsEditor.commit();
    }

    private boolean enableChannel(String id, boolean enable, int slotId) {
        GsmBroadcastConfigurator gbc = GsmBroadcastConfigurator.getInstance(this);
        gbc.setMsgId(Integer.valueOf(id));//OliverOu@20150915, TT-633437, Register those channels we switched on before.
        if(gbc != null) {
            if (gbc.switchService(enable, slotId)) {
                if (DEBUG) Log.d(LOG_TAG, "enableChannel:id=" + id + " to:" + (enable ? "enable" : "disable") + " success");
                return true;
            } else {
                if (DEBUG) Log.d(LOG_TAG, "enableChannel:id=" + id + " to:" + (enable ? "enable" : "disable") + " failed");
            }
        } else {
            Log.w(LOG_TAG, "GsmBroadcastConfigurator was null!");
        }
        return false;
    }

    // +++ dion_yang@20130912 : Add DSDS get from SIM
    private void loadAlreadyExistedAddedID(int slotId, Map<String, Boolean> mIDs,
            Map<String, Boolean> mOnSimIDs, SharedPreferences.Editor mPrefsEditor, boolean isFOTA) {
        String[] ids = null;
        Map<String, Boolean> mapForSort = new HashMap<String, Boolean>();
        mapForSort.putAll(mIDs);
        if (mapForSort.containsKey("")) {
            mapForSort.remove("");
        }
        ids = (String[]) mapForSort.keySet().toArray(new String[0]);
        if (ids == null) {
            return;
        }
        int idForSort[] = new int[ids.length];
        for (int i = 0; i < ids.length; i++) {
            try {
                String id = ids[i];
                if (slotId == GsmUmtsCellBroadcastSmsFragmentBase.SLOTID_SLOT2) {
                    if (id.startsWith(GsmUmtsCellBroadcastSmsFragmentBase.PREFIX_SIM2)) {
                        idForSort[i] = Integer.valueOf(id.substring(GsmUmtsCellBroadcastSmsFragmentBase.PREFIX_SIM2.length()));
                    }
                } else {
                    if (!id.startsWith(GsmUmtsCellBroadcastSmsFragmentBase.PREFIX_SIM2)) {
                        idForSort[i] = Integer.valueOf(id);
                    }
                }
            } catch (NumberFormatException ex) {
                Log.e(LOG_TAG, "ids[i]=" + ids[i] + " i=" + i);
                if (DEBUG) Log.d(LOG_TAG, mIDs.toString());
                ex.printStackTrace();
            }
        }
        Arrays.sort(idForSort);
        for (int id : idForSort) {
            String sid = String.valueOf(id);
            String key = slotId == GsmUmtsCellBroadcastSmsFragmentBase.SLOTID_SLOT2 ? GsmUmtsCellBroadcastSmsFragmentBase.PREFIX_SIM2 + sid : sid;
            if (mIDs.get(key) != null) {
                // We sync with SIM card, if different with SIM card, we change
                // the UI to disable status
                if (isFOTA) {
                    // If is first run after fota, we turn off all channel id to
                    // disable
                    addChannelToPerfs(sid, false, slotId, mPrefsEditor, isFOTA);
                } else {
                 // +++ OliverOu@20150731 TT-633437
                    // Since L, we don't need this feature, which is for qualcomm devices.
//                  if (mOnSimIDs.get(sid) == null) {
//                      addChannelToPerfs(sid, false, slotId, mPrefsEditor, isFOTA);
//                  } else {
                      // +++ OliverOu@20150908, need to judge whether the channel is either enable or disable.
                         boolean isEnable = mIDs.get(key);
                         addChannelToPerfs(sid, isEnable, slotId, mPrefsEditor, isFOTA);
                      // --- OliverOu@20150908, need to judge whether the channel is either enable or disable.
//                  }
                    // --- OliverOu@20150731 TT-633437
                }
            }
        }
        mapForSort.clear();
        mapForSort = null;
    }
    // --- dion_yang@20130912 : Add DSDS get from SIM

    // ASUS BEGIN,dion_yang:We get Channel ID from SIM Card
    private void getChannelIdFromSIM() {
        if (DEBUG) Log.d(LOG_TAG, "getChannelIdFromSIM from Default");
        ArrayList<SmsBroadcastConfigInfo> cbsSetting = null;
        mOnSimIDs.clear();
        try {
            //TODO: Need to update from KK to L
//            cbsSetting = AsusMSimSmsManager.getInstance().getGsmCellBroadcastConfig(AsusMSimConstants.SUB1);
            if (cbsSetting == null) {
                return;
            }
            if (DEBUG) Log.d(LOG_TAG, "SIM CBS setting configs num =" + cbsSetting.size());
            for (int i = 0; i < cbsSetting.size(); i++) {
                SmsBroadcastConfigInfo cbsConfig = cbsSetting.get(i);
                if (DEBUG) Log.d(LOG_TAG, "SIM CBS setting configs [" + i + "] value =" + cbsConfig.toString());
                for (int id = cbsConfig.getFromServiceId(); id <= cbsConfig.getToServiceId(); id++) {
                    if (id > GsmUmtsCellBroadcastSmsFragment.MAX_CBS_UI_ID) {
                        mOnSimIDs.put(String.valueOf(id), cbsConfig.isSelected());
                    } else if (id <= GsmUmtsCellBroadcastSmsFragment.MAX_CBS_UI_ID
                            && id >= GsmUmtsCellBroadcastSmsFragment.MIN_CBS_UI_ID) {
                        // We only add Custom ID to mIDs
                        if (DEBUG) Log.d(LOG_TAG, "Put SIM ID =" + String.valueOf(id) + " status:" + cbsConfig.isSelected());
                        mIDs.put(String.valueOf(id), cbsConfig.isSelected());
                        mOnSimIDs.put(String.valueOf(id), cbsConfig.isSelected());
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    // ASUS END,dion_yang:We get Channel ID from SIM Card

    // +++ dion_yang@20130912 : Add DSDS get from SIM
    private void getChannelIdFromSIM(int slotId, Map<String, Boolean> onSimIDs, Map<String, Boolean> idTable) {
        if (DEBUG) Log.d(LOG_TAG, "getChannelIdFromSIM from slotId =" + slotId);
        ArrayList<SmsBroadcastConfigInfo> cbsSetting = null;
        onSimIDs.clear();
        try {
            // Asus Jenpang begin: porting layer implementation
            //TODO: Need to update from KK to L
//            cbsSetting = AsusMSimSmsManager.getInstance().getGsmCellBroadcastConfig(subscription);
            if (cbsSetting == null) {
                Log.w(LOG_TAG,"cbsSetting was null");
                return;
            }
            // cbsSetting = smsMgr.getGsmCellBroadcastConfig();
            // Asus Jenpang end: porting layer implementation
            if (DEBUG) Log.d(LOG_TAG, "SIM CBS setting configs num =" + cbsSetting.size());
            for (int i = 0; i < cbsSetting.size(); i++) {
                SmsBroadcastConfigInfo cbsConfig = cbsSetting.get(i);
                if (DEBUG) Log.d(LOG_TAG, "SIM CBS setting configs [" + i + "] value =" + cbsConfig.toString());
                for (int id = cbsConfig.getFromServiceId(); id <= cbsConfig.getToServiceId(); id++) {
                    if (id > GsmUmtsCellBroadcastSmsFragment.MAX_CBS_UI_ID) {
                        onSimIDs.put(String.valueOf(id), cbsConfig.isSelected());
                    } else if (id <= GsmUmtsCellBroadcastSmsFragment.MAX_CBS_UI_ID
                            && id >= GsmUmtsCellBroadcastSmsFragment.MIN_CBS_UI_ID) {
                        // We only add Custom ID to mIDs
                        if (DEBUG) Log.d(LOG_TAG, "Put SIM ID =" + String.valueOf(id) + " status:" + cbsConfig.isSelected());
                        idTable.put(String.valueOf(id), cbsConfig.isSelected());
                        onSimIDs.put(String.valueOf(id), cbsConfig.isSelected());
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    // --- dion_yang@20130912 : Add DSDS get from SIM

    // ASUS BEGIN, dion_yang: For FOTA
    // For FOTA from A66, to this version, we would disable all sim config
    // first, to prevent 1049A bug
    private boolean isFOTA() {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(CellBroadcastReceiverApp.getApplication());
        int version = prefs.getInt(GsmUmtsCellBroadcastSmsFragment.GSM_CBS_DATABASE_VERSION_KEY, 0);
        if (DEBUG) Log.d(LOG_TAG, "CBS db version=" + version + " AP db version=" + GsmUmtsCellBroadcastSmsFragment.GSM_CBS_DATABASE_VERSION);
        if (version < GsmUmtsCellBroadcastSmsFragment.GSM_CBS_DATABASE_VERSION) {
            if (DEBUG) Log.d(LOG_TAG, "We need to updating database setting!");
            return true;
        }
        return false;
    }
    // ASUS END, dion_yang: For FOTA
    // +++ dion_yang@20130912 : Add DSDS get from SIM
    private void updateCBSVersion(boolean isFOTA) {
        if (isFOTA) {
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(CellBroadcastReceiverApp.getApplication());
            prefs.edit().putInt(GsmUmtsCellBroadcastSmsFragment.GSM_CBS_DATABASE_VERSION_KEY,
                        GsmUmtsCellBroadcastSmsFragment.GSM_CBS_DATABASE_VERSION)
                    .apply();
            if (DEBUG) Log.d(LOG_TAG, "FOTA CBS data completed!");
        }
    }

    private void dumpMap() {
        Log.d(LOG_TAG, "mIDs="+mIDs.toString());
        Log.d(LOG_TAG, "mOnSimIDs="+mOnSimIDs.toString());
        Log.d(LOG_TAG, "mID2s="+mID2s.toString());
        Log.d(LOG_TAG, "mOnSim2IDs="+mOnSim2IDs.toString());
    }
    // --- dion_yang@20130912 : Add DSDS get from SIM

}
