/*
 * Copyright (C) 2007 The Android Open Source Project
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

package mst.preference;

import mst.widget.toolbar.Toolbar;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Represents a top-level {@link Preference} that
 * is the root of a Preference hierarchy. A {@link PreferenceActivity}
 * points to an instance of this class to show the preferences. To instantiate
 * this class, use {@link PreferenceManager#createPreferenceScreen(Context)}.
 * <ul>
 * This class can appear in two places:
 * <li> When a {@link PreferenceActivity} points to this, it is used as the root
 * and is not shown (only the contained preferences are shown).
 * <li> When it appears inside another preference hierarchy, it is shown and
 * serves as the gateway to another screen of preferences (either by showing
 * another screen of preferences as a {@link Dialog} or via a
 * {@link Context#startActivity(android.content.Intent)} from the
 * {@link Preference#getIntent()}). The children of this {@link PreferenceScreen}
 * are NOT shown in the screen that this {@link PreferenceScreen} is shown in.
 * Instead, a separate screen will be shown when this preference is clicked.
 * </ul>
 * <p>Here's an example XML layout of a PreferenceScreen:</p>
 * <pre>
&lt;PreferenceScreen
        xmlns:android="http://schemas.android.com/apk/res/android"
        android:key="first_preferencescreen"&gt;
    &lt;CheckBoxPreference
            android:key="wifi enabled"
            android:title="WiFi" /&gt;
    &lt;PreferenceScreen
            android:key="second_preferencescreen"
            android:title="WiFi settings"&gt;
        &lt;CheckBoxPreference
                android:key="prefer wifi"
                android:title="Prefer WiFi" /&gt;
        ... other preferences here ...
    &lt;/PreferenceScreen&gt;
&lt;/PreferenceScreen&gt; </pre>
 * <p>
 * In this example, the "first_preferencescreen" will be used as the root of the
 * hierarchy and given to a {@link PreferenceActivity}. The first screen will
 * show preferences "WiFi" (which can be used to quickly enable/disable WiFi)
 * and "WiFi settings". The "WiFi settings" is the "second_preferencescreen" and when
 * clicked will show another screen of preferences such as "Prefer WiFi" (and
 * the other preferences that are children of the "second_preferencescreen" tag).
 * 
 * <div class="special reference">
 * <h3>Developer Guides</h3>
 * <p>For information about building a settings UI with Preferences,
 * read the <a href="{@docRoot}guide/topics/ui/settings.html">Settings</a>
 * guide.</p>
 * </div>
 *
 * @see PreferenceCategory
 */
public final class PreferenceScreen extends PreferenceGroup implements AdapterView.OnItemClickListener,
        DialogInterface.OnDismissListener {

    private ListAdapter mRootAdapter;
    
    private Dialog mDialog;

    private ListView mListView;
    
    private Toolbar mToolbar;
    
    private TextView mStatusView;
    
    private ImageView mStatusIconView;
    
    private ImageView mStatusArrowView;
    
    private CharSequence mStatus;
    
    private Drawable mStatusIcon;
    
    private boolean mStatusArrowShowing = true;
    
    /**
     * Do NOT use this constructor, use {@link PreferenceManager#createPreferenceScreen(Context)}.
     * @hide-
     */
    public PreferenceScreen(Context context, AttributeSet attrs) {
        super(context, attrs, com.mst.R.attr.preferenceScreenStyle);
    }

    /**
     * Returns an adapter that can be attached to a {@link PreferenceActivity}
     * or {@link PreferenceFragment} to show the preferences contained in this
     * {@link PreferenceScreen}.
     * <p>
     * This {@link PreferenceScreen} will NOT appear in the returned adapter, instead
     * it appears in the hierarchy above this {@link PreferenceScreen}.
     * <p>
     * This adapter's {@link Adapter#getItem(int)} should always return a
     * subclass of {@link Preference}.
     * 
     * @return An adapter that provides the {@link Preference} contained in this
     *         {@link PreferenceScreen}.
     */
    public ListAdapter getRootAdapter() {
        if (mRootAdapter == null) {
            mRootAdapter = onCreateRootAdapter();
        }
        
        return mRootAdapter;
    }
    
    /**
     * Creates the root adapter.
     * 
     * @return An adapter that contains the preferences contained in this {@link PreferenceScreen}.
     * @see #getRootAdapter()
     */
    protected ListAdapter onCreateRootAdapter() {
        return new PreferenceGroupAdapter(this);
    }

    /**
     * Binds a {@link ListView} to the preferences contained in this {@link PreferenceScreen} via
     * {@link #getRootAdapter()}. It also handles passing list item clicks to the corresponding
     * {@link Preference} contained by this {@link PreferenceScreen}.
     * 
     * @param listView The list view to attach to.
     */
    public void bind(ListView listView) {
        listView.setOnItemClickListener(this);
        listView.setAdapter(getRootAdapter());
        
        onAttachedToActivity();
    }
    
    @Override
    protected void onClick() {
        if (getIntent() != null || getFragment() != null || getPreferenceCount() == 0) {
            return;
        }
        
        showDialog(null);
    }
    
    
    public CharSequence getStatus(){
    	return mStatus;
    }
    
    public void setStatus(CharSequence status){  
    	  if (status == null && mStatus != null || status != null && !status.equals(mStatus)) {
    		  mStatus = status;
              notifyChanged();
          }
    	
    }
    
    public void setStatusIcon(Drawable icon){
    	 if ((icon == null && mStatusIcon != null) || (icon != null && mStatusIcon != icon)) {
    		 mStatusIcon = icon;
             notifyChanged();
         }
    }
    
    public Drawable getStatusIconDrawable(){
    	return mStatusIcon;
    }
    
    public boolean isStatusArrowShowing(){
    	return mStatusArrowShowing;
    }
    
    public void showStatusArrow(boolean show){
    	mStatusArrowShowing = show;
    }
    
    @Override
    protected void onBindView(View view) {
    	// TODO Auto-generated method stub
    	super.onBindView(view);
    	mStatusIconView = (ImageView)view.findViewById(com.mst.R.id.next_widget_icon);
		if (mStatusIconView != null) {
			if (getStatusIconDrawable() != null) {
				mStatusIconView.setVisibility(View.VISIBLE);
				mStatusIconView.setImageDrawable(getStatusIconDrawable());
			} else {
				mStatusIconView.setVisibility(View.GONE);
			}
		}
    	
    	mStatusView = (TextView)view.findViewById(com.mst.R.id.next_widget_status);
		if (mStatusView != null) {
			if (!TextUtils.isEmpty(getStatus())) {
				mStatusView.setText(getStatus());
			}
		}
    	
    	mStatusArrowView = (ImageView)view.findViewById(com.mst.R.id.next_widget_arrow);
    	if(mStatusArrowView != null){
    		mStatusArrowView.setVisibility(isStatusArrowShowing()?View.VISIBLE:View.GONE);
    	}
    }
    
    
    
    
    private void showDialog(Bundle state) {
        Context context = getContext();
        if (mListView != null) {
            mListView.setAdapter(null);
        }

        LayoutInflater inflater = (LayoutInflater)
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View childPrefScreen = inflater.inflate(
                com.mst.R.layout.preference_list_dialog_material, null);
        mListView = (ListView) childPrefScreen.findViewById(android.R.id.list);
        bind(mListView);

        // Set the title bar if title is available, else no title bar
        final CharSequence title = getTitle();
        Dialog dialog = mDialog = new Dialog(context, context.getThemeResId());
        mToolbar = (Toolbar) childPrefScreen.findViewById(com.mst.R.id.toolbar);
        dialog.setContentView(childPrefScreen);
        mToolbar.setTitle(title);
        mToolbar.setNavigationOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				mDialog.dismiss();
			}
		});
        dialog.setOnDismissListener(this);
        if (state != null) {
            dialog.onRestoreInstanceState(state);
        }

        // Add the screen to the list of preferences screens opened as dialogs
        getPreferenceManager().addPreferencesScreen(dialog);
        
        dialog.show();
    }
    
    public void onDismiss(DialogInterface dialog) {
        mDialog = null;
        getPreferenceManager().removePreferencesScreen(dialog);
    }
    
    /**
     * Used to get a handle to the dialog. 
     * This is useful for cases where we want to manipulate the dialog
     * as we would with any other activity or view.
     */
    public Dialog getDialog() {
        return mDialog;
    }
    
    public Toolbar getToolbar(){
    	return mToolbar;
    }

    public void onItemClick(AdapterView parent, View view, int position, long id) {
        // If the list has headers, subtract them from the index.
        if (parent instanceof ListView) {
            position -= ((ListView) parent).getHeaderViewsCount();
        }
        Object item = getRootAdapter().getItem(position);
        if (!(item instanceof Preference)) return;

        final Preference preference = (Preference) item; 
        preference.performClick(this);
    }

    @Override
    protected boolean isOnSameScreenAsChildren() {
        return false;
    }
    
    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        final Dialog dialog = mDialog;
        if (dialog == null || !dialog.isShowing()) {
            return superState;
        }
        
        final SavedState myState = new SavedState(superState);
        myState.isDialogShowing = true;
        myState.dialogBundle = dialog.onSaveInstanceState();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }
         
        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        if (myState.isDialogShowing) {
            showDialog(myState.dialogBundle);
        }
    }
    
    private static class SavedState extends BaseSavedState {
        boolean isDialogShowing;
        Bundle dialogBundle;
        
        public SavedState(Parcel source) {
            super(source);
            isDialogShowing = source.readInt() == 1;
            dialogBundle = source.readBundle();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(isDialogShowing ? 1 : 0);
            dest.writeBundle(dialogBundle);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
    
}
