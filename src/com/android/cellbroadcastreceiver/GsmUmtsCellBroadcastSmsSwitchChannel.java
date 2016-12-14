package com.android.cellbroadcastreceiver;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class GsmUmtsCellBroadcastSmsSwitchChannel extends Thread {
    private static final String TAG = "GsmUmtsCellBroadcastSmsSwitchChannel";
    private final boolean DEBUG = true;

    private GsmBroadcastConfigurator mGsmBroadcastConfigurator;
    private Handler mUIHandler;
    private boolean mEnable, mRemoveMode;
    private int mChannelId = -1;
    private int mSlot = 0;
    private SharedPreferences.Editor mSharedPreferenceEditor = null;

    // CB SET handle mode ID
    public static final int CB_SET_SUCCESSED = 0;
    public static final int CB_SET_FAILED = 1;
    public static final int CB_UPDATING_SUCCESSED = 2;
    public static final int CB_UPDATING_FAILED = 3;
    public static final int CB_EDIT_SET_SUCCESSED = 4;

    public static final String KEY_SLOT = "slot";
    public static final String KEY_ACTIVATED = "activated";
    public static final String KEY_CHANNEL_ID = "channel_id";
    public static final String KEY_REMOVE_MODE = "remove_mode";

    public GsmUmtsCellBroadcastSmsSwitchChannel(GsmBroadcastConfigurator gbc, Handler handler, int slotId, boolean enable, boolean remove, int channelId, SharedPreferences.Editor editor) {
        mGsmBroadcastConfigurator = gbc;
        mUIHandler = handler;
        mSlot = slotId;
        mEnable = enable;
        mRemoveMode = remove;
        mChannelId = channelId;
        mSharedPreferenceEditor = editor;
    }

    @Override
    public void run() {
        super.run();
        boolean result = false;
        boolean activated = false;
        if (mGsmBroadcastConfigurator.isMsgIdSupported(mChannelId)) {
            result = mGsmBroadcastConfigurator.switchService(mEnable, mSlot);
            if (result) {
                activated = mEnable;
            }
        }
        Message msg = new Message();
        if (DEBUG)
            Log.d(TAG, "CB SETTING: mSlot=" + mSlot +
                    ", mChannelId=" + mChannelId + ", activated=" + activated +
                    ", result=" + result);
        if (result) {
            msg.what = CB_SET_SUCCESSED;
        } else {
            msg.what = CB_SET_FAILED;
        }

        mSharedPreferenceEditor.commit();
        Bundle channel_data = new Bundle();
        channel_data.putInt(KEY_SLOT, mSlot);
        channel_data.putBoolean(KEY_ACTIVATED, activated);
        channel_data.putInt(KEY_CHANNEL_ID, mChannelId);
        channel_data.putBoolean(KEY_REMOVE_MODE, mRemoveMode);
        msg.setData(channel_data);
        if (mUIHandler != null) {
            mUIHandler.sendMessage(msg);
        } else {
            Log.w(TAG, "UI handler was null!");
        }
    }
}