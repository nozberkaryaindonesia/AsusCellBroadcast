package com.android.cellbroadcastreceiver;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.content.Context;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Toast;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;

import com.android.cellbroadcastreceiver.zen.AsusZenUiUtils;
import com.android.internal.telephony.TelephonyIntents;

public class GsmUmtsCellBroadcastSmsDual extends Activity implements TabListener, OnPageChangeListener {
    // debug data
    private static final String TAG = "GsmUmtsCellBroadcastSmsDual";
    private static final boolean DEBUG = true;
    private IccCardStatusReceiver mSimStatusListener = null;
    
    private ActionBar mActionBar;
    private MyViewPager mViewPager;
    private MyPageAdapter mViewPagerAdapter;

    private static final int VIEW_PAGER_COUNT = 2;
    public static int SELECED_SIM_TAB = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(DEBUG) Log.d(TAG, "onCreate:");
        setContentView(R.layout.gsm_cellbroadcast_activity_dual);
        AsusZenUiUtils.updateStatusActionBarBackgroundView(this, true,
                R.color.asus_status_action_bar_background);

        mActionBar = getActionBar();
        if (mActionBar != null) {
            mActionBar.setDisplayHomeAsUpEnabled(true);
        }

        //ASUS BEGIN, dion_yang:for support hot switch SIM
        if (CellBroadcastUtils.hasIccCard(this)) {
            mViewPager = (MyViewPager) findViewById(R.id.viewpager);
            mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
            // mActionBar.setDisplayShowTitleEnabled(false);
            // mActionBar.setDisplayShowHomeEnabled(true);

            // +++ dion_yang@20130912 : Add DSDS get from SIM
            String[] tabTextStrings = { CellBroadcastUtils.getSlotName(this, 0),
                    CellBroadcastUtils.getSlotName(this, 1) };
            TabListener[] tabListeners = { this, this };
            AsusZenUiUtils.addActionBarTab(this,
                    R.layout.asus_action_bar_tab_custom_view,
                    R.layout.asus_action_bar_tab_custom_view_text_view,
                    tabTextStrings, tabListeners);
            // --- dion_yang@20130912 : Add DSDS get from SIM

            mViewPagerAdapter = new MyPageAdapter(getFragmentManager());
            mViewPager.setAdapter(mViewPagerAdapter);
            mViewPager.setCurrentItem(0);
            mViewPager.setOnPageChangeListener(this);
        } else {
            if(DEBUG) Log.d(TAG, "onCreate:No IccCard!We finish this Activity!");
            Toast.makeText(this, getString(R.string.toast_without_sim_toast), Toast.LENGTH_LONG).show();
            finish();
        }
        //ASUS END, dion_yang:for support hot switch SIM

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        AsusZenUiUtils.updateStatusActionBarBackgroundView(this, true,
                R.color.asus_status_action_bar_background);

        String[] tabTextStrings = { CellBroadcastUtils.getSlotName(this, 0),
                CellBroadcastUtils.getSlotName(this, 1) };
        AsusZenUiUtils.updateActionBarTab(this,
                R.layout.asus_action_bar_tab_custom_view_text_view,
                tabTextStrings);
    }

    @Override
    protected void onStart() {
        super.onStart();
        //ASUS BEGIN, dion_yang:for support hot switch SIM
        if (!CellBroadcastUtils.hasIccCard(this)) {
            if(DEBUG) Log.d(TAG, "onStart:No IccCard!We finish this Activity!");
            Toast.makeText(this, getString(R.string.toast_without_sim_toast), Toast.LENGTH_LONG).show();
            finish();
        }
        //ASUS END, dion_yang:for support hot switch SIM
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        try{
            if (CellBroadcastUtils.hasIccCard(this)) {
                if (mSimStatusListener == null) {
                    mSimStatusListener = new IccCardStatusReceiver(this);
                    registerReceiver(mSimStatusListener,
                            new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED));
                }
            }
         } catch(Exception e){
            Log.e(TAG, e.getMessage());
            mSimStatusListener = null;
         }
        if (CellBroadcastUtils.isSimReady(0) && !CellBroadcastUtils.isSimReady(1)) {
            disableIndexViewPage(1);
        } else if (!CellBroadcastUtils.isSimReady(0) && CellBroadcastUtils.isSimReady(1)) {
            disableIndexViewPage(0);
        }
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
    protected void onPause() {
        super.onPause();
        if(DEBUG) Log.d(TAG, "onPause");
        //ASUS BEGIN, dion_yang:for support hot switch SIM
        if(mSimStatusListener != null) {
            try{
               unregisterReceiver(mSimStatusListener);
            } catch(Exception e){
               Log.e(TAG, e.getMessage());
            } finally{
                mSimStatusListener = null;
            }
        }
        //ASUS END, dion_yang:for support hot switch SIM
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(DEBUG) Log.d(TAG, "onStop");
    }

    private class MyPageAdapter extends FragmentPagerAdapter {

        public MyPageAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            if (position == 0) {
                return new GsmUmtsCellBroadcastSmsFragment();
            } else if (position == 1) {
                return new GsmUmtsCellBroadcastSmsFragment2();
            }
            return null;
        }

        @Override
        public int getCount() {
            return VIEW_PAGER_COUNT;
        }
    }

    @Override
    public void onTabReselected(Tab tab, FragmentTransaction ft) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onTabSelected(Tab tab, FragmentTransaction ft) {
        SELECED_SIM_TAB = tab.getPosition();
        mViewPager.setCurrentItem(SELECED_SIM_TAB);
    }

    @Override
    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onPageScrollStateChanged(int arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onPageScrolled(int arg0, float arg1, int arg2) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onPageSelected(int position) {
        mActionBar.getTabAt(position).select();
    }
    
    private void disableActiobBarTab(int index) {
        try {
            ViewGroup vActionBarContainer = null;
            int resId = getResources().getIdentifier("action_bar_container", "id", "android");
            vActionBarContainer = (ViewGroup) findViewById(resId);
            ((ViewGroup) ((ViewGroup) vActionBarContainer.getChildAt(2)).getChildAt(0)).getChildAt(index).setClickable(false);
        } catch (Exception e) {
            // TODO: handle exception
            try {
                ViewGroup vActionBarContainer2 = null;
                int resId = getResources().getIdentifier("action_bar", "id", "android");
                vActionBarContainer2 = (ViewGroup) findViewById(resId);
                ((ViewGroup) ((ViewGroup) vActionBarContainer2.getChildAt(1)).getChildAt(0)).getChildAt(index).setClickable(false);
            } catch (Exception e2) {
                // TODO: handle exception
                Log.e(TAG, "disableActiobBarTab Failed!!");
            }
        }
    }
    
    private void disableIndexViewPage(int index) {
        if (index == 0 || index == 1) {
            mViewPager.setPagingEnabled(false);
            disableActiobBarTab(index);
            mViewPager.setCurrentItem(index == 0 ? 1 : 0);
        } else {
            Log.w(TAG, "Unknow index = " + index);
        }
    }
}
