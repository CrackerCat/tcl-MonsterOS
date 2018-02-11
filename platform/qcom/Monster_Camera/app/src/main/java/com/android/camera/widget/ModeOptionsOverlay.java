/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.camera.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.camera.CaptureLayoutHelper;
import com.android.camera.ShutterButton;
import com.android.camera.debug.Log;
import com.android.camera.ui.PreviewOverlay;
import com.android.camera.ui.TouchCoordinate;
import com.tct.camera.R;

/**
 * ModeOptionsOverlay is a FrameLayout which positions mode options in
 * in the bottom of the preview that is visible above the bottom bar.
 */
public class ModeOptionsOverlay extends FrameLayout
    implements PreviewOverlay.OnPreviewTouchedListener,
               ShutterButton.OnShutterButtonListener {

    private final static Log.Tag TAG = new Log.Tag("ModeOptionsOverlay");

    private ModeOptions mModeOptions;

    private CaptureLayoutHelper mCaptureLayoutHelper = null;

    public ModeOptionsOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Sets a capture layout helper to query layout rect from.
     */
    public void setCaptureLayoutHelper(CaptureLayoutHelper helper) {
        mCaptureLayoutHelper = helper;
    }

    public void setToggleClickable(boolean clickable) {
        // mModeOptionsToggle.setClickable(clickable);
    }

    @Override
    public void onFinishInflate() {
        mModeOptions = (ModeOptions) findViewById(R.id.mode_options);
        mModeOptions.setClickable(true);
        mModeOptions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeModeOptions();
            }
        });
    }

    @Override
    public void onPreviewTouched(MotionEvent ev) {
        closeModeOptions();
    }

    @Override
    public void onShutterButtonClick() {
        closeModeOptions();
    }
    

    @Override
    public void onShutterButtonLongClick() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onShutterCoordinate(TouchCoordinate coord) {
        // Do nothing.
    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {
        // noop
//        if(mEnableShutterButtonFocus)
//            this.setVisibility(pressed?GONE:VISIBLE);
    }

    /**
     * Schedule (or re-schedule) the options menu to be closed after a number
     * of milliseconds.  If the options menu is already closed, nothing is
     * scheduled.
     */
    public void closeModeOptions() {
        mModeOptions.animateHidden();
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        checkOrientation(configuration.orientation);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mCaptureLayoutHelper == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            Log.e(TAG, "Capture layout helper needs to be set first.");
        } else {
            RectF uncoveredPreviewRect = mCaptureLayoutHelper.getUncoveredPreviewRect();
            super.onMeasure(MeasureSpec.makeMeasureSpec(
                            (int) uncoveredPreviewRect.width(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec((int) uncoveredPreviewRect.height(),
                            MeasureSpec.EXACTLY)
            );
        }
    }

    /**
     * Set the layout gravity of the child layout to be bottom or top right
     * depending on orientation.
     */
    private void checkOrientation(int orientation) {
        final boolean isPortrait = (Configuration.ORIENTATION_PORTRAIT == orientation);

        final int modeOptionsDimension = (int) getResources()
            .getDimension(R.dimen.mode_options_height);

        FrameLayout.LayoutParams modeOptionsParams
            = (FrameLayout.LayoutParams) mModeOptions.getLayoutParams();

        if (isPortrait) {
            modeOptionsParams.height = modeOptionsDimension;
            modeOptionsParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            modeOptionsParams.gravity = Gravity.TOP;
        } else {
            modeOptionsParams.width = modeOptionsDimension;
            modeOptionsParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            modeOptionsParams.gravity = Gravity.RIGHT;
        }

        requestLayout();
    }
}
