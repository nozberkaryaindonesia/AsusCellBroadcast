/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.app.Application;
//import android.telephony.CellBroadcastMessage;
import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.preference.PreferenceManager;
import android.os.Debug;
import android.os.Debug.MemoryInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * The application class loads the default preferences at first start,
 * and remembers the time of the most recently received broadcast.
 */
public class CellBroadcastReceiverApp extends Application {
    private static final String TAG = "CellBroadcastReceiverApp";
    public static final Boolean DEBUG_SEND = true;
    public static final boolean DEBUG_MEM = true;
    private static CellBroadcastReceiverApp sCBSApp;
    private long mLastUsedMemory = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        // TODO: fix strict mode violation from the following method call during app creation
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        sCBSApp = this;
    }

    /** List of unread non-emergency alerts to show when user selects the notification. */
    private static final ArrayList<CellBroadcastMessage> sNewMessageList =
            new ArrayList<CellBroadcastMessage>(4);

    /** Latest area info cell broadcast received. */
    private static Map<Long, CellBroadcastMessage> sLatestAreaInfo =
                                new HashMap<Long,CellBroadcastMessage>();

    /** Adds a new unread non-emergency message and returns the current list. */
    static ArrayList<CellBroadcastMessage> addNewMessageToList(CellBroadcastMessage message) {
        sNewMessageList.add(message);
        return sNewMessageList;
    }

    /** Clears the list of unread non-emergency messages. */
    static void clearNewMessageList() {
        sNewMessageList.clear();
    }

    /** Saves the latest area info broadcast received. */
    static void setLatestAreaInfo(CellBroadcastMessage areaInfo) {
        sLatestAreaInfo.put(areaInfo.getSubId(), areaInfo);
    }

    /** Returns the latest area info broadcast received. */
    static CellBroadcastMessage getLatestAreaInfo(long subId ) {
        return sLatestAreaInfo.get(subId);
    }

    protected static CellBroadcastReceiverApp getApplication() {
        return sCBSApp;
    }

    public void dumpMemoryInfo(String TAG) {
        dumpRuntimeMemoryInfo(TAG);
        dumpDebugMemoryInfo(TAG);
    }

    public void dumpDebugMemoryInfo(String TAG) {
        MemoryInfo memoryInfo = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memoryInfo);
        //Log.v(TAG,"[Memory] App getTotalPrivateDirty() = "+memoryInfo.getTotalPrivateDirty() + " KB");
        //Log.v(TAG,"[Memory] App getTotalSharedDirty() = "+memoryInfo.getTotalSharedDirty() + " KB");
        Log.v(TAG,"[Memory] App getTotalPss() = "+memoryInfo.getTotalPss()+ " KB");

/*        Log.v(TAG,"[Memory] App dalvikPrivateDirty = "+memoryInfo.dalvikPrivateDirty);
        Log.v(TAG,"[Memory] App dalvikPss = "+memoryInfo.dalvikPss);
        Log.v(TAG,"[Memory] App dalvikSharedDirty = "+memoryInfo.dalvikSharedDirty);
        Log.v(TAG,"[Memory] App nativePrivateDirty = "+memoryInfo.nativePrivateDirty);
        Log.v(TAG,"[Memory] App nativePss = "+memoryInfo.nativePss);
        Log.v(TAG,"[Memory] App nativeSharedDirty = "+memoryInfo.nativeSharedDirty);
        Log.v(TAG,"[Memory] App otherPrivateDirty = "+memoryInfo.otherPrivateDirty);
        Log.v(TAG,"[Memory] App otherPss = "+memoryInfo.otherPss);
        Log.v(TAG,"[Memory] App otherSharedDirty = "+memoryInfo.otherSharedDirty);*/
    }

    public void dumpRuntimeMemoryInfo(String TAG) {
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long usedMemory = (totalMemory-freeMemory)>>10;
        totalMemory = totalMemory >>10;
        freeMemory = freeMemory >>10;
        long Memorydiff=0;
        Memorydiff = usedMemory - mLastUsedMemory;
        mLastUsedMemory = usedMemory;
        Log.v(TAG,"[Memory] App totalMemory = "+ totalMemory + " KB");
        Log.v(TAG,"[Memory] App freeMemory = "+ freeMemory + " KB");
        Log.v(TAG,"[Memory] App usedMemory = "+ usedMemory + " KB");
        Log.v(TAG,"[Memory] App Memorydiff = "+ Memorydiff + " KB");
    }
}
