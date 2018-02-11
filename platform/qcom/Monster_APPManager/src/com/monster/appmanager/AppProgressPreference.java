/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.monster.appmanager;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ProgressBar;
import com.monster.appmanager.R;

public class AppProgressPreference extends TintablePreference {

    private int mProgress;

    public AppProgressPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_app);
        setWidgetLayoutResource(R.layout.widget_progress_bar);
    }

    public void setProgress(int amount) {
        mProgress = amount;
        notifyChanged();
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        final ProgressBar progress = (ProgressBar) view.findViewById(android.R.id.progress);
        progress.setProgress(mProgress);
    }
}