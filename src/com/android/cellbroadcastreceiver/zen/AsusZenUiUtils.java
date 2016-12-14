
package com.android.cellbroadcastreceiver.zen;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import com.android.cellbroadcastreceiver.R;

public class AsusZenUiUtils {

    public static void updateStatusActionBarBackgroundView(Activity activity, boolean hasTab,
            int backgroundColorResId) {
        if (activity == null) {
            return;
        }

//        View statusActionBarBackgroundView = activity
//                .findViewById(R.id.asus_status_action_bar_background_view);
//        int statusBarHeight = AsusZenUiUtils.getStatusBarHeight(activity);
//        int actionbarHeight = AsusZenUiUtils.getActionBarHeight(activity);
//        if (statusActionBarBackgroundView != null) {
//            ViewGroup.LayoutParams params = statusActionBarBackgroundView.getLayoutParams();
//            Resources resources = activity.getResources();
//            Configuration configuration = resources.getConfiguration();
//            if (hasTab && (configuration.screenWidthDp <= 360)) {
//                params.height = statusBarHeight + (actionbarHeight * 2);
//            } else {
//                params.height = statusBarHeight + actionbarHeight;
//            }
//            statusActionBarBackgroundView.setLayoutParams(params);
//
//            int backgroundColor = resources.getColor(backgroundColorResId);
//            statusActionBarBackgroundView.setBackgroundColor(backgroundColor);
//
//            statusActionBarBackgroundView.setVisibility(View.VISIBLE);
//        }
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        activity.getWindow().setStatusBarColor(activity.getResources().getColor(R.color.asus_message_status_bar_color));
    }

    private static int getStatusBarHeight(Activity activity) {
        final Display display = activity.getWindowManager().getDefaultDisplay();
        if (display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY) {
            return 0;
        }

        int h = 0;
        Resources res = activity.getResources();
        int resourceId = res.getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            h = res.getDimensionPixelSize(resourceId);
        }
        return h;
    }

    private static int getActionBarHeight(Activity activity) {
        int h = 0;
        ActionBar actionBar = activity.getActionBar();
        if (actionBar != null) {
            TypedValue tv = new TypedValue();
            activity.getBaseContext().getTheme()
                    .resolveAttribute(android.R.attr.actionBarSize, tv, true);
            h = activity.getResources().getDimensionPixelSize(tv.resourceId);
        }
        return h;
    }

    public static void addActionBarTab(Activity activity,
            int tabCustomViewResId, int tabCustomViewTextViewResId,
            String[] tabTextStrings, TabListener[] tabListeners) {
        if ((activity == null) || (tabTextStrings == null)
                || (tabListeners == null)) {
            return;
        }

        ActionBar actionBar = activity.getActionBar();
        if (actionBar == null) {
            return;
        }

        int tabCount = tabTextStrings.length;
        if (tabCount != tabListeners.length) {
            return;
        }

        LayoutInflater layoutInflater = LayoutInflater.from(activity);
        int acitonBarHeight = AsusZenUiUtils.getActionBarHeight(activity);
        for (int i = 0; i < tabCount; i++) {
            View tabCustomView = layoutInflater.inflate(tabCustomViewResId,
                    null);
            tabCustomView.setMinimumHeight(acitonBarHeight);
            if (tabCustomView instanceof ViewGroup) {
                ViewGroup tabCustomViewGroup = (ViewGroup) tabCustomView;
                tabCustomViewGroup.removeAllViews();
                TextView tabCustomViewTextView = (TextView) layoutInflater
                        .inflate(tabCustomViewTextViewResId, null);
                tabCustomViewTextView.setText(tabTextStrings[i]);
                tabCustomViewGroup.addView(tabCustomViewTextView);
            }
            Tab tab = actionBar.newTab().setCustomView(tabCustomView)
                    .setTabListener(tabListeners[i]);
            actionBar.addTab(tab);
        }
    }

    public static void updateActionBarTab(Activity activity,
            int tabCustomViewTextViewResId, String[] tabTextStrings) {
        if ((activity == null) || (tabTextStrings == null)) {
            return;
        }

        ActionBar actionBar = activity.getActionBar();
        if (actionBar == null) {
            return;
        }

        int tabCount = tabTextStrings.length;
        if (tabCount != actionBar.getTabCount()) {
            return;
        }

        int acitonBarHeight = AsusZenUiUtils.getActionBarHeight(activity);
        LayoutInflater layoutInflater = LayoutInflater.from(activity);
        for (int i = 0; i < tabCount; i++) {
            View tabCustomView = actionBar.getTabAt(i).getCustomView();
            tabCustomView.setMinimumHeight(acitonBarHeight);
            if (tabCustomView instanceof ViewGroup) {
                ViewGroup tabCustomViewGroup = (ViewGroup) tabCustomView;
                tabCustomViewGroup.removeAllViews();
                TextView tabCustomViewTextView = (TextView) layoutInflater
                        .inflate(tabCustomViewTextViewResId, null);
                tabCustomViewTextView.setText(tabTextStrings[i]);
                tabCustomViewGroup.addView(tabCustomViewTextView);
            }
        }
    }
}
