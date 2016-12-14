
package com.android.cellbroadcastreceiver.zen;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

public class AsusFullScreenLinearLayout extends LinearLayout {

    private int mInsetsBottom;

    public AsusFullScreenLinearLayout(Context context) {
        super(context);
    }

    public AsusFullScreenLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AsusFullScreenLinearLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected boolean fitSystemWindows(Rect insets) {
        final int windowSystemUiVisibility = getWindowSystemUiVisibility();
        final boolean stable = (windowSystemUiVisibility & View.SYSTEM_UI_FLAG_LAYOUT_STABLE) != 0;
        final int lastInsetsBottom = mInsetsBottom;
        if (!stable) {
            mInsetsBottom = 0;
        } else {
            mInsetsBottom = insets.bottom;
        }
        if (mInsetsBottom != lastInsetsBottom) {
            MarginLayoutParams marginLayoutParams = (MarginLayoutParams) getLayoutParams();
            marginLayoutParams.bottomMargin = mInsetsBottom;
            requestLayout();
        }
        return stable;
    }

}
