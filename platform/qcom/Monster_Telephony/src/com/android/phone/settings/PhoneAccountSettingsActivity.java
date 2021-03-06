/**
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
 * limitations under the License
 */

package com.android.phone.settings;

import android.os.Bundle;
import mst.preference.PreferenceActivity;
import android.view.MenuItem;
import com.android.phone.ActivityCollector;//[SOLUTION]-Add-BEGIN by TCTNB.(Chuanjun Chen), 08/31/2016, SOLUTION- 2499549 And TASk-2781362

import com.android.phone.R;

public class PhoneAccountSettingsActivity extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        getActionBar().setTitle(R.string.phone_accounts);
        getFragmentManager().beginTransaction().replace(
                android.R.id.content, new PhoneAccountSettingsFragment()).commit();
        ActivityCollector.addActivity(this);//[SOLUTION]-Add-BEGIN by TCTNB.(Chuanjun Chen), 08/31/2016, SOLUTION- 2499549 And TASk-2781362
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    //[SOLUTION]-Add-BEGIN by TCTNB.(Chuanjun Chen), 08/31/2016, SOLUTION- 2499549 And TASk-2781362
    @Override
    protected void onDestroy() {
        super.onDestroy();
        ActivityCollector.removeActivity(this);
    }
    //[SOLUTION]-Add-END by TCTNB.(Chuanjun Chen)
}
