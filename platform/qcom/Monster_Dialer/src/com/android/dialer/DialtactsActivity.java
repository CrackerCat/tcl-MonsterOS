/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.dialer;

import android.widget.ImageView;
import android.graphics.Color;
import android.graphics.Rect;

import com.mst.t9search.ContactsHelper;
import com.mst.t9search.ContactsHelper.OnContactsLoad;
import com.android.contacts.common.mst.FragmentCallbacks;
import mst.widget.FloatingActionButton.OnFloatActionButtonClickListener;
import mst.widget.FloatingActionButton;
import android.R.anim;
import mst.app.dialog.AlertDialog;
import mst.app.dialog.AlertDialog.Builder;
import  mst.view.menu.bottomnavigation.BottomNavigationView;
import  mst.view.menu.bottomnavigation.BottomNavigationView.OnNavigationItemSelectedListener;
import android.widget.MstSearchView;
import android.widget.MstSearchView.OnQueryTextListener;
import android.widget.MstSearchView.OnCloseListener;
import android.widget.MstSearchView.OnSuggestionListener;
import mst.widget.toolbar.Toolbar;
import mst.widget.ActionMode;
import mst.widget.ActionModeListener;
import mst.widget.ActionMode.Item;
import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Trace;
import mst.provider.CallLog.Calls;
import android.provider.MstContactsContract;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.support.v4.view.ViewPager;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnDragListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView.OnScrollListener;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.common.activity.TransactionSafeActivity;
import com.android.contacts.common.dialog.ClearFrequentsDialog;
import com.android.contacts.common.interactions.ImportExportDialogFragment;
import com.android.contacts.common.interactions.TouchPointManager;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.contacts.common.widget.FloatingActionButtonController;
import com.android.contacts.commonbind.analytics.AnalyticsUtil;
import com.android.dialer.calllog.CallLogActivity;
import com.android.dialer.calllog.CallLogAsyncTaskUtil;
import com.android.dialer.calllog.CallLogFragment;
import com.android.dialer.calllog.CallLogQueryHandler;
import com.android.dialer.database.DialerDatabaseHelper;
import com.android.dialer.dialpad.DialpadFragment;
import com.android.dialer.dialpad.SmartDialNameMatcher;
import com.android.dialer.dialpad.SmartDialPrefix;
import com.android.dialer.interactions.PhoneNumberInteraction;
import com.android.dialer.list.DragDropController;
import com.android.dialer.list.ListsFragment;
import com.android.dialer.list.OnDragDropListener;
import com.android.dialer.list.OnListFragmentScrolledListener;
import com.android.dialer.list.PhoneFavoriteSquareTileView;
import com.android.dialer.list.RegularSearchFragment;
import com.android.dialer.list.SearchFragment;
import com.android.dialer.list.SmartDialSearchFragment;
import com.android.dialer.list.SpeedDialFragment;
import com.android.dialer.settings.DialerSettingsActivity;
import com.android.dialer.util.IntentUtil;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.widget.ActionBarController;
import com.android.dialer.widget.EmptyContentView;
import com.android.dialer.widget.SearchEditTextLayout;
import com.android.dialer.widget.SearchEditTextLayout.Callback;
import com.android.dialerbind.DatabaseHelperManager;
import com.android.phone.common.animation.AnimUtils;
import com.android.ims.ImsManager;
import com.android.phone.common.animation.AnimationListenerAdapter;
import com.mediatek.dialer.util.DialerFeatureOptions;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
;/**
 * The dialer tab's title is 'phone', a more common name (see strings.xml).
 */
public class DialtactsActivity extends TransactionSafeActivity implements View.OnClickListener,
DialpadFragment.OnDialpadQueryChangedListener,
OnListFragmentScrolledListener,
CallLogFragment.HostInterface,
DialpadFragment.HostInterface,
ListsFragment.HostInterface,
SpeedDialFragment.HostInterface,
SearchFragment.HostInterface,
OnDragDropListener,
OnPhoneNumberPickerActionListener,
PopupMenu.OnMenuItemClickListener,
ViewPager.OnPageChangeListener,
ActionBarController.ActivityUi,
FragmentCallbacks,
mst.widget.toolbar.Toolbar.OnMenuItemClickListener,
OnContactsLoad{
	private static final String TAG = "DialtactsActivity";
	private ActionMode actionMode;
	private BottomNavigationView bottomBar;
	public static final boolean DEBUG = true;

	public static final String SHARED_PREFS_NAME = "com.android.dialer_preferences";

	/** @see #getCallOrigin() */
	private static final String CALL_ORIGIN_DIALTACTS =
			"com.android.dialer.DialtactsActivity";

	private static final String KEY_IN_REGULAR_SEARCH_UI = "in_regular_search_ui";
	private static final String KEY_IN_DIALPAD_SEARCH_UI = "in_dialpad_search_ui";
	private static final String KEY_SEARCH_QUERY = "search_query";
	private static final String KEY_FIRST_LAUNCH = "first_launch";
	private static final String KEY_IS_DIALPAD_SHOWN = "is_dialpad_shown";
	private static final String ADD_PARTICIPANT_KEY = "add_participant";

	private static final String TAG_DIALPAD_FRAGMENT = "dialpad";
	private static final String TAG_REGULAR_SEARCH_FRAGMENT = "search";
	private static final String TAG_SMARTDIAL_SEARCH_FRAGMENT = "smartdial";
	private static final String TAG_FAVORITES_FRAGMENT = "favorites";

	/**
	 * Just for backward compatibility. Should behave as same as {@link Intent#ACTION_DIAL}.
	 */
	private static final String ACTION_TOUCH_DIALER = "com.android.phone.action.TOUCH_DIALER";
	public static final String EXTRA_SHOW_TAB = "EXTRA_SHOW_TAB";

	private static final int ACTIVITY_REQUEST_CODE_VOICE_SEARCH = 1;

	private static final int FAB_SCALE_IN_DELAY_MS = 300;

	private View mParentLayout;

	/**
	 * Fragment containing the dialpad that slides into view
	 */
	protected DialpadFragment mDialpadFragment;
	private CallLogFragment mRecentsFragment;
	private static final int MAX_RECENTS_ENTRIES = 1000;
	// Oldest recents entry to display is 2 weeks old.
	private static final long OLDEST_RECENTS_DATE = 1000L * 60 * 60 * 24 * 14;

	/**
	 * Fragment for searching phone numbers using the alphanumeric keyboard.
	 */
	private RegularSearchFragment mRegularSearchFragment;

	/**
	 * Fragment for searching phone numbers using the dialpad.
	 */
	private SmartDialSearchFragment mSmartDialSearchFragment;

	private boolean mDialConferenceButtonPressed = false;

	/**
	 * Animation that slides in.
	 */
	private Animation mSlideIn;

	/**
	 * Animation that slides out.
	 */
	private Animation mSlideOut;

	AnimationListenerAdapter mSlideInListener = new AnimationListenerAdapter() {
		@Override
		public void onAnimationEnd(Animation animation) {
			Log.d(TAG,"onAnimationEnd");
			//			mDialpadFragment.getDigitsContainer().setVisibility(View.VISIBLE);			 
			getWindow().setStatusBarColor(getResources().getColor(R.color.mst_dialpad_listview_bg_color)); 
			toolbar.setVisibility(View.GONE);

			//			digits_container.setVisibility(View.VISIBLE);
			maybeEnterSearchUi();
		}
	};

	/**
	 * Listener for after slide out animation completes on dialer fragment.
	 */
	AnimationListenerAdapter mSlideOutListener = new AnimationListenerAdapter() {
		@Override
		public void onAnimationEnd(Animation animation) {
			//			toolbar.setVisibility(View.VISIBLE);
			commitDialpadFragmentHide();

		}
	};

	/**
	 * Fragment containing the speed dial list, recents list, and all contacts list.
	 */
	private ListsFragment mListsFragment;

	/**
	 * Tracks whether onSaveInstanceState has been called. If true, no fragment transactions can
	 * be commited.
	 */
	private boolean mStateSaved;
	private boolean mIsRestarting;
	private boolean mInDialpadSearch;
	private boolean mInRegularSearch;
	private boolean mClearSearchOnPause;
	private boolean mIsDialpadShown;
	private boolean mShowDialpadOnResume;

	/**
	 * Whether or not the device is in landscape orientation.
	 */
	private boolean mIsLandscape;

	/**
	 * True if the dialpad is only temporarily showing due to being in call
	 */
	private boolean mInCallDialpadUp;

	/**
	 * True when this activity has been launched for the first time.
	 */
	private boolean mFirstLaunch;

	/**
	 * Search query to be applied to the SearchView in the ActionBar once
	 * onCreateOptionsMenu has been called.
	 */
	private String mPendingSearchViewQuery;

	private PopupMenu mOverflowMenu;
	//	private EditText mSearchView;
	private View mVoiceSearchButton;
	private View mDialCallButton;

	private String mSearchQuery;

	private DialerDatabaseHelper mDialerDatabaseHelper;
	private DragDropController mDragDropController;
	private ActionBarController mActionBarController;
	private ImageButton mFloatingActionButton;
	private FloatingActionButton floatingActionButtonContainer;

	private ImageButton mConferenceDialButton;
	private FloatingActionButtonController mFloatingActionButtonController;

	private int mActionBarHeight;

	/**
	 * The text returned from a voice search query.  Set in {@link #onActivityResult} and used in
	 * {@link #onResume()} to populate the search box.
	 */
	private String mVoiceSearchQuery;

	protected class OptionsPopupMenu extends PopupMenu {
		public OptionsPopupMenu(Context context, View anchor) {
			super(context, anchor, Gravity.END);
		}

		@Override
		public void show() {
			final boolean hasContactsPermission =
					PermissionsUtil.hasContactsPermissions(DialtactsActivity.this);
			//			final Menu menu = getMenu();
			final MenuItem clearFrequents = menu.findItem(R.id.menu_clear_frequents);
			clearFrequents.setVisible(/*mListsFragment != null &&
					mListsFragment.getSpeedDialFragment() != null &&
					mListsFragment.getSpeedDialFragment().hasFrequents() && hasContactsPermission*/false);

			menu.findItem(R.id.menu_import_export).setVisible(/*hasContactsPermission*/false);
			menu.findItem(R.id.menu_add_contact).setVisible(/*hasContactsPermission*/false);

			menu.findItem(R.id.menu_history).setVisible(/*
					PermissionsUtil.hasPhonePermissions(DialtactsActivity.this)*/false);
			super.show();
		}
	}

	/**
	 * Listener that listens to drag events and sends their x and y coordinates to a
	 * {@link DragDropController}.
	 */
	private class LayoutOnDragListener implements OnDragListener {
		@Override
		public boolean onDrag(View v, DragEvent event) {
			if (event.getAction() == DragEvent.ACTION_DRAG_LOCATION) {
				mDragDropController.handleDragHovered(v, (int) event.getX(), (int) event.getY());
			}
			return true;
		}
	}

	/**
	 * Listener used to send search queries to the phone search fragment.
	 */
	private final TextWatcher mPhoneSearchQueryTextListener = new TextWatcher() {
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
			final String newText = s.toString();
			if (newText.equals(mSearchQuery)) {
				// If the query hasn't changed (perhaps due to activity being destroyed
				// and restored, or user launching the same DIAL intent twice), then there is
				// no need to do anything here.
				return;
			}
			if (DEBUG) {
				Log.d(TAG, "onTextChange for mSearchView called with new query: " + newText);
				Log.d(TAG, "Previous Query: " + mSearchQuery);
			}
			mSearchQuery = newText;

			// Show search fragment only when the query string is changed to non-empty text.
			if (!TextUtils.isEmpty(newText)) {
				// Call enterSearchUi only if we are switching search modes, or showing a search
				// fragment for the first time.
				final boolean sameSearchMode = (mIsDialpadShown && mInDialpadSearch) ||
						(!mIsDialpadShown && mInRegularSearch);
				Log.d(TAG,"sameSearchMode:"+sameSearchMode);
				if (!sameSearchMode) {
					enterSearchUi(mIsDialpadShown, mSearchQuery, true /* animate */);
				}
			}

			if (mSmartDialSearchFragment != null && mSmartDialSearchFragment.isVisible()) {
				Log.d(TAG,"onTextChanged1");				
				mSmartDialSearchFragment.setQueryString(mSearchQuery, false /* delaySelection */);
			} else if (mRegularSearchFragment != null && mRegularSearchFragment.isVisible()) {
				Log.d(TAG,"onTextChanged2");
				mRegularSearchFragment.setQueryString(mSearchQuery, false /* delaySelection */);
			}
		}

		@Override
		public void afterTextChanged(Editable s) {
		}
	};


	/**
	 * Open the search UI when the user clicks on the search box.
	 */
	private final View.OnClickListener mSearchViewOnClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			if (!isInSearchUi()) {
				//				mActionBarController.onSearchBoxTapped();	
				Log.d(TAG,"onclick searchview");
				mSearchContainer.setAlpha(1);
				enterSearchUi(false /* smartDialSearch */, "",
						true /* animate */);
			}
		}
	};

	/**
	 * Handles the user closing the soft keyboard.
	 */
	private final View.OnKeyListener mSearchEditTextLayoutListener = new View.OnKeyListener() {
		@Override
		public boolean onKey(View v, int keyCode, KeyEvent event) {
			if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
				if (TextUtils.isEmpty(/*mSearchView.getText()*/mstSearchView.getQuery().toString())) {
					// If the search term is empty, close the search UI.
					maybeExitSearchUi();
				} else {
					// If the search term is not empty, show the dialpad fab.
					showFabInSearchUi();
				}
			}
			return false;
		}
	};

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		if (ev.getAction() == MotionEvent.ACTION_DOWN) {
			TouchPointManager.getInstance().setPoint((int) ev.getRawX(), (int) ev.getRawY());
		}
		return super.dispatchTouchEvent(ev);

	}

	private Toolbar toolbar;
	private View mSearchContainer;
	private MstSearchView mstSearchView;


	private View digits_container;

	public View getDigits_container() {
		return digits_container;
	}

	public BottomNavigationView getmBottomBar() {
		return bottomBar;
	}
	public ActionMode getmActionMode() {
		return actionMode;
	}

	public void query(String s){
		Log.d(TAG,"query,queryString:"+s);	

		final String newText = s.toString();
		if (newText.equals(mSearchQuery)) {
			// If the query hasn't changed (perhaps due to activity being destroyed
			// and restored, or user launching the same DIAL intent twice), then there is
			// no need to do anything here.
			return;
		}
		if (DEBUG) {
			Log.d(TAG, "onTextChange for mSearchView called with new query: " + newText);
			Log.d(TAG, "Previous Query: " + mSearchQuery);
		}
		mSearchQuery = TextUtils.equals("mst_enter_into_search_mode", newText)?"":newText;

		// Show search fragment only when the query string is changed to non-empty text.
		if (!TextUtils.isEmpty(newText)) {
			// Call enterSearchUi only if we are switching search modes, or showing a search
			// fragment for the first time.
			final boolean sameSearchMode = (mIsDialpadShown && mInDialpadSearch) ||
					(!mIsDialpadShown && mInRegularSearch);
			Log.d(TAG,"sameSearchMode:"+sameSearchMode);
			if (!sameSearchMode) {
				enterSearchUi(mIsDialpadShown, mSearchQuery, true /* animate */);
			}
		}

		if (mSmartDialSearchFragment != null && mSmartDialSearchFragment.isVisible()) {
			Log.d(TAG,"onTextChanged1");
			//			mSmartDialSearchFragment.setQueryString(mSearchQuery, false /* delaySelection */);
			mSmartDialSearchFragment.setQueryStringMst(mSearchQuery);
		} else if (mRegularSearchFragment != null && mRegularSearchFragment.isVisible()) {
			Log.d(TAG,"onTextChanged2");
			//			mRegularSearchFragment.setQueryString(mSearchQuery, false /* delaySelection */);
			mRegularSearchFragment.setQueryStringMst(mSearchQuery);
		}	
	}

	private ImageView backIcon;
	private Menu menu;
	private void setMenuVisibility(boolean isvisible){
		menu.findItem(R.id.record_call_settings).setVisible(isvisible);
		menu.findItem(R.id.menu_call_settings).setVisible(isvisible);
		menu.findItem(R.id.mst_menu_goto_contact).setVisible(isvisible);
	}
	private EmptyContentView mEmptyListView;

	/**监听软键盘状态
	 * @param activity
	 * @param listener
	 */
	private boolean sLastVisiable=false;
	public void addOnSoftKeyBoardVisibleListener() {
		final View decorView = getWindow().getDecorView();
		decorView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				Log.d(TAG,"mInRegularSearch:"+mInRegularSearch);
				if(!mInRegularSearch) return;
				
				if(isOnResumeInSearchMode) {			
					if(System.currentTimeMillis()-onResumeBeginTime<300) {
						isOnResumeInSearchMode=false;
						onResumeBeginTime=0;
					}
					onResumeBeginTime=System.currentTimeMillis();
					return;
				}
				
				Rect rect = new Rect();
				decorView.getWindowVisibleDisplayFrame(rect);
				int displayHight = rect.bottom - rect.top;
				int hight = decorView.getHeight();
				boolean visible = (double) displayHight / hight < 0.8;

				Log.d(TAG, "DecorView display hight = " + displayHight);
				Log.d(TAG, "DecorView hight = " + hight);
				Log.d(TAG, "softkeyboard visible = " + visible);

				if(!visible && visible != sLastVisiable){
					mstSearchView.clearFocus();
				}
				sLastVisiable = visible;
			}
		});
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Trace.beginSection(TAG + " onCreate");
		Log.d(TAG,"oncreate2");
		super.onCreate(savedInstanceState);
		mFirstLaunch = true;

		final Resources resources = getResources();
		mActionBarHeight = resources.getDimensionPixelSize(R.dimen.action_bar_height_large);

		Trace.beginSection(TAG + " setContentView");
		setMstContentView(R.layout.dialtacts_activity);
		Trace.endSection();

		//		final ActionBar actionBar = getActionBar();
		//		actionBar.hide();
		getWindow().setBackgroundDrawableResource(android.R.color.white);
		digits_container=findViewById(R.id.digits_container);
		mEmptyListView = (EmptyContentView) findViewById(R.id.empty_list_view);
		mEmptyListView.setImage(R.drawable.mst_no_calllog_image);
		Trace.beginSection(TAG + " setup Views");
		toolbar = getToolbar();
		toolbar.setElevation(0f);
		toolbar.setBackgroundColor(getResources().getColor(R.color.mst_toolbar_background_color));
		toolbar.inflateMenu(R.menu.dialtacts_options);		

		menu = toolbar.getMenu();
		//		menu.setGroupVisible(0, true);
		final MenuItem clearFrequents = menu.findItem(R.id.menu_clear_frequents);
		clearFrequents.setVisible(/*mListsFragment != null &&
				mListsFragment.getSpeedDialFragment() != null &&
				mListsFragment.getSpeedDialFragment().hasFrequents() && hasContactsPermission*/false);
		menu.findItem(R.id.menu_import_export).setVisible(/*hasContactsPermission*/false);
		menu.findItem(R.id.menu_add_contact).setVisible(/*hasContactsPermission*/false);
		menu.findItem(R.id.menu_history).setVisible(/*hasContactsPermission*/false);

		final LayoutInflater inflater=(LayoutInflater) getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);		

		View backIconView=inflater.inflate(R.layout.mst_back_icon_view, null);
		backIconView.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				Log.d(TAG,"backIconView onclick");
				// TODO Auto-generated method stub
				if (isInSearchUi()) {
					exitSearchUi();
					DialerUtils.hideInputMethod(mParentLayout);
				}
			}
		});
		backIcon=(ImageView)backIconView.findViewById(R.id.mst_back_icon_img);
		backIcon.setVisibility(View.GONE);
		backIcon.setColorFilter(getResources().getColor(R.color.mst_toolbar_icon_normal_color));
		getToolbar().addView(backIconView,
				new mst.widget.toolbar.Toolbar.LayoutParams(Gravity.CENTER_VERTICAL | Gravity.START));

		mSearchContainer = inflater.inflate(R.layout.mst_search_bar_expanded, null);
		mstSearchView=(MstSearchView)mSearchContainer.findViewById(R.id.search_view);
		mstSearchView.needHintIcon(true);
		getToolbar().addView(mSearchContainer,
				new mst.widget.toolbar.Toolbar.LayoutParams(Gravity.CENTER_VERTICAL | Gravity.START));
		//		mstSearchView.setOnClickListener(mSearchViewOnClickListener);    
		mstSearchView.setQueryHint(getString(R.string.mst_calllog_searchview_hint));
		mstSearchView.setOnQueryTextListener(new OnQueryTextListener(){
			@Override
			public boolean onQueryTextChange(String s) {
				query(s);
				return true;
			}

			@Override
			public boolean onQueryTextSubmit(String str) {
				return false;
			}
		});

		mstSearchView.setOnQueryTextFocusChangeListener(new OnFocusChangeListener(){
			public void onFocusChange(View v, boolean hasFocus){
				if(hasFocus){
					Log.d(TAG,"hasFocus1");
					mstSearchView.needHintIcon(false);
					mstSearchView.requestFocus();
					backIcon.setVisibility(View.VISIBLE);
					mEmptyListView.setVisibility(View.GONE);
					query("mst_enter_into_search_mode");

					//					mSearchContainer.setAlpha(1);
					//					enterSearchUi(false /* smartDialSearch */, "",
					//							true /* animate */);
				}
			}
		});

		bottomBar = (BottomNavigationView)findViewById(R.id.bottom_navigation_view);
		bottomBar.setNavigationItemSelectedListener(new OnNavigationItemSelectedListener() {

			@Override
			public boolean onNavigationItemSelected(MenuItem arg0) {
				// TODO Auto-generated method stub
				Log.d(TAG,"onNavigationItemSelected,arg0.getItemId():"+arg0.getItemId());
				switch (arg0.getItemId()) {
				case R.id.mst_contacts_delete:
					int count=mRecentsFragment.getAdapter().getCheckedCount();
					AlertDialog.Builder builder = new Builder(DialtactsActivity.this);
					builder.setMessage(DialtactsActivity.this.getString(R.string.mst_delete_call_log_message,count));
					builder.setTitle(null);
					builder.setNegativeButton(DialtactsActivity.this.getString(R.string.mst_cancel), null);
					builder.setPositiveButton(DialtactsActivity.this.getString(R.string.mst_ok), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which)
						{
							dialog.dismiss();
							Log.d(TAG,"delete");
							mRecentsFragment.deleteSelectedCallLogs();
						}
					});
					AlertDialog alertDialog = builder.create();
					alertDialog.show();
					break;

				default:
					break;
				} 
				return false;
			}
		});
		//		mSearchView.addTextChangedListener(mPhoneSearchQueryTextListener);
		//		mSearchContainerInitial.setOnClickListener(mSearchViewOnClickListener);

		//		actionBar.setCustomView(R.layout.search_edittext);
		//		actionBar.setDisplayShowCustomEnabled(true);
		//		actionBar.setBackgroundDrawable(null);
		//
		//						SearchEditTextLayout searchEditTextLayout =
		//								(SearchEditTextLayout) actionBar.getCustomView().findViewById(R.id.search_view_container);
		//				searchEditTextLayout.setPreImeKeyListener(mSearchEditTextLayoutListener);
		//
		//				mActionBarController = new ActionBarController(this, searchEditTextLayout);
		//
		//		mSearchView = (EditText) searchEditTextLayout.findViewById(R.id.search_view);
		//		mSearchView.addTextChangedListener(mPhoneSearchQueryTextListener);
		//		mVoiceSearchButton = searchEditTextLayout.findViewById(R.id.voice_search_button);
		//		searchEditTextLayout.findViewById(R.id.search_magnifying_glass)
		//		.setOnClickListener(mSearchViewOnClickListener);
		//		searchEditTextLayout.findViewById(R.id.search_box_start_search)
		//		.setOnClickListener(mSearchViewOnClickListener);
		//		searchEditTextLayout.setOnClickListener(mSearchViewOnClickListener);
		//		searchEditTextLayout.setCallback(new SearchEditTextLayout.Callback() {
		//			@Override
		//			public void onBackButtonClicked() {
		//				onBackPressed();
		//			}
		//
		//			@Override
		//			public void onSearchViewClicked() {
		//				// Hide FAB, as the keyboard is shown.
		//				mFloatingActionButtonController.scaleOut();
		//			}
		//		});

		mIsLandscape = getResources().getConfiguration().orientation
				== Configuration.ORIENTATION_LANDSCAPE;

		floatingActionButtonContainer = (FloatingActionButton)findViewById(
				R.id.floating_action_button_container);
		//		mFloatingActionButton = (ImageButton) findViewById(R.id.floating_action_button);
		//		mDialCallButton =  findViewById(R.id.floating_action_button);
		//		floatingActionButtonContainer.setOnClickListener(this);
		floatingActionButtonContainer.setOnFloatingActionButtonClickListener(new OnFloatActionButtonClickListener(){
			public void onClick(View view){

				Log.d(TAG,"fab onclick");
				mDialConferenceButtonPressed = false;
				if (mDialpadFragment != null) {
					mDialpadFragment.showDialConference(false);
				}
				if (mListsFragment != null && mListsFragment.getCurrentTabIndex()
						== ListsFragment.TAB_INDEX_ALL_CONTACTS && !mInRegularSearch) {
					DialerUtils.startActivityWithErrorToast(
							DialtactsActivity.this,
							IntentUtil.getNewContactIntent(),
							R.string.add_contact_not_available);
				} else if (!mIsDialpadShown) {
					mInCallDialpadUp = false;
					//					toolbar.setVisibility(View.GONE);

					if(TextUtils.isEmpty(mDialpadFragment.getDigits().getText())){
						mstSearchView.setQuery(null,false);
					}
					showDialpadFragment(true);
					//					mFloatingActionButton.setImageResource(R.drawable.fab_ic_call);
					//					mFloatingActionButton.setVisibility(view.VISIBLE);
					floatingActionButtonContainer.setIconDrawable(getResources().getDrawable(R.drawable.mst_fab_ic_call));
					//					setConferenceDialButtonImage(false);
					//					setConferenceDialButtonVisibility(true);
				} else {
					// Dial button was pressed; tell the Dialpad fragment
					mDialpadFragment.dialButtonPressed();
				}
			}
		});
		//		 mConferenceDialButton = (ImageButton) findViewById(R.id.dialConferenceButton);
		//		 mConferenceDialButton.setOnClickListener(this);
		mFloatingActionButtonController = new FloatingActionButtonController(this,
				floatingActionButtonContainer,mFloatingActionButton);

		//		ImageButton optionsMenuButton =
		//				(ImageButton) searchEditTextLayout.findViewById(R.id.dialtacts_options_menu_button);
		//		optionsMenuButton.setOnClickListener(this);
		//
		//		View contactsList =
		//				searchEditTextLayout.findViewById(R.id.contacts_list);
		//		contactsList.setOnClickListener(this);
		//
		//		mOverflowMenu = buildOptionsMenu(searchEditTextLayout);
		//		optionsMenuButton.setOnTouchListener(mOverflowMenu.getDragToOpenListener());

		// Add the favorites fragment but only if savedInstanceState is null. Otherwise the
		// fragment manager is responsible for recreating it.

		if(mRecentsFragment==null) {
			actionMode=getActionMode();
			Log.d(TAG,"actionMode:"+actionMode);
			Bundle bundle=new Bundle();
			bundle.putInt("filterType", CallLogQueryHandler.CALL_TYPE_ALL);
			bundle.putInt("logLimit",MAX_RECENTS_ENTRIES);
			//modify by lgy for 3443947
			//			bundle.putLong("dateLimit",System.currentTimeMillis() - OLDEST_RECENTS_DATE);
			bundle.putLong("dateLimit",0);
			mRecentsFragment = new CallLogFragment(CallLogQueryHandler.CALL_TYPE_ALL,
					MAX_RECENTS_ENTRIES, /*System.currentTimeMillis() - OLDEST_RECENTS_DATE*/0,actionMode,bottomBar);
			mRecentsFragment = new CallLogFragment();
			mRecentsFragment.setArguments(bundle);
			mRecentsFragment.setEmptyListView(mEmptyListView);
			//			mRecentsFragment.setCallbacks(this);
		}
		mRecentsFragment.setEmptyListView(mEmptyListView);
		setupActionModeWithDecor(toolbar);
		if (/*savedInstanceState == null*/true) {
			Log.d(TAG, "savedInstanceState == null");
			mDialpadFragment=new DialpadFragment();
			getFragmentManager().beginTransaction()
			.add(R.id.dialtacts_frame, /*new ListsFragment(),--liyang modify--*/ mRecentsFragment,TAG_FAVORITES_FRAGMENT)
			.add(R.id.dialtacts_container, mDialpadFragment, TAG_DIALPAD_FRAGMENT)
			.commit();
			//			mDialpadFragment.setmCallbacks(this);
			//			mDialpadFragment.setDigitsContainer(digits_container);
		} else {
			mSearchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY);
			mInRegularSearch = savedInstanceState.getBoolean(KEY_IN_REGULAR_SEARCH_UI);
			mInDialpadSearch = savedInstanceState.getBoolean(KEY_IN_DIALPAD_SEARCH_UI);
			mFirstLaunch = savedInstanceState.getBoolean(KEY_FIRST_LAUNCH);
			mShowDialpadOnResume = savedInstanceState.getBoolean(KEY_IS_DIALPAD_SHOWN);
			if (mActionBarController != null) {
				mActionBarController.restoreInstanceState(savedInstanceState);
			}
		}

		final boolean isLayoutRtl = DialerUtils.isRtl();
		if (mIsLandscape) {
			mSlideIn = AnimationUtils.loadAnimation(this,
					isLayoutRtl ? R.anim.dialpad_slide_in_left : R.anim.dialpad_slide_in_right);
			mSlideOut = AnimationUtils.loadAnimation(this,
					isLayoutRtl ? R.anim.dialpad_slide_out_left : R.anim.dialpad_slide_out_right);
		} else {
			mSlideIn = AnimationUtils.loadAnimation(this, R.anim.dialpad_slide_in_bottom);
			mSlideOut = AnimationUtils.loadAnimation(this, R.anim.dialpad_slide_out_bottom);
		}

		mSlideIn.setInterpolator(AnimUtils.EASE_IN);
		mSlideOut.setInterpolator(AnimUtils.EASE_OUT);

		mSlideIn.setAnimationListener(mSlideInListener);
		mSlideOut.setAnimationListener(mSlideOutListener);

		mParentLayout = findViewById(R.id.dialtacts_mainlayout);
		mParentLayout.setOnDragListener(new LayoutOnDragListener());
//		floatingActionButtonContainer.getViewTreeObserver().addOnGlobalLayoutListener(
//				new ViewTreeObserver.OnGlobalLayoutListener() {
//					@Override
//					public void onGlobalLayout() {
//						final ViewTreeObserver observer =
//								floatingActionButtonContainer.getViewTreeObserver();
//						if (!observer.isAlive()) {
//							return;
//						}
//						observer.removeOnGlobalLayoutListener(this);
//						int screenWidth = mParentLayout.getWidth();
//						mFloatingActionButtonController.setScreenWidth(screenWidth);
//						mFloatingActionButtonController.align(
//								getFabAlignment(), false /* animate */);
//					}
//				});

		Trace.endSection();

		Trace.beginSection(TAG + " initialize smart dialing");

		/// M: [MTK Dialer Search] @{
		if (!DialerFeatureOptions.isDialerSearchEnabled()) {
			mDialerDatabaseHelper = DatabaseHelperManager.getDatabaseHelper(this);
			SmartDialPrefix.initializeNanpSettings(this);
		}
		/// @}

		//		getContentResolver().registerContentObserver(Settings.System.CONTENT_URI, true, mSettingsObserver);	



		Trace.endSection();
		Trace.endSection();	

		addOnSoftKeyBoardVisibleListener();
	}

	//add by liyang
	private ContactsHelper mContactsHelper;
	public void initT9Search(){
		//		ContactsHelper.getInstance().setContext(DialtactsActivity.this);

		if(mContactsHelper==null) {
			mContactsHelper=new ContactsHelper();
		}
		mContactsHelper.setContext(DialtactsActivity.this);

		mContactsHelper.setOnContactsLoad(this);
		boolean startLoad = mContactsHelper.startLoadContacts(true);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		//		try{
		//			getContentResolver().unregisterContentObserver(mSettingsObserver);	
		//		}catch(Exception e){
		//			e.printStackTrace();
		//		}
	}

	private boolean isOnResumeInSearchMode=false;
	private long onResumeBeginTime=0;
	@Override
	protected void onResume() {
		Trace.beginSection(TAG + " onResume");
		super.onResume();
		if(mInRegularSearch) {
			isOnResumeInSearchMode=true;
			onResumeBeginTime=0;
		}
		
		Log.d(TAG,"onresume:"+mFirstLaunch);
		mStateSaved = false;
		if (mFirstLaunch) {
			displayFragment(getIntent());
		} else if (!phoneIsInUse() && mInCallDialpadUp) {
			hideDialpadFragment(false, true);
			mInCallDialpadUp = false;
		} else if (mShowDialpadOnResume) {
			showDialpadFragment(false);
			mShowDialpadOnResume = false;
		}

		if(mFirstLaunch){
			initT9Search();
		}
		// If there was a voice query result returned in the {@link #onActivityResult} callback, it
		// will have been stashed in mVoiceSearchQuery since the search results fragment cannot be
		// shown until onResume has completed.  Active the search UI and set the search term now.
		if (!TextUtils.isEmpty(mVoiceSearchQuery)) {
			if (mActionBarController != null) {
				mActionBarController.onSearchBoxTapped();
			}
			//			mSearchView.setText(mVoiceSearchQuery);
			mstSearchView.setQuery(mVoiceSearchQuery,false);
			mVoiceSearchQuery = null;
		}		

		if (mIsRestarting) {
			// This is only called when the activity goes from resumed -> paused -> resumed, so it
			// will not cause an extra view to be sent out on rotation
			if (mIsDialpadShown) {
				AnalyticsUtil.sendScreenView(mDialpadFragment, this);
			}
			mIsRestarting = false;
		}

		prepareVoiceSearchButton();

		/// M: [MTK Dialer Search] @{
		if (!DialerFeatureOptions.isDialerSearchEnabled()) {
			mDialerDatabaseHelper.startSmartDialUpdateThread();
		}
		/// @}

		mFloatingActionButtonController.align(getFabAlignment(), false /* animate */);
		setConferenceDialButtonImage(false);
		setConferenceDialButtonVisibility(true);

		if (getIntent().hasExtra(EXTRA_SHOW_TAB)) {
			int index = getIntent().getIntExtra(EXTRA_SHOW_TAB, ListsFragment.TAB_INDEX_SPEED_DIAL);
			if (mListsFragment != null && index < mListsFragment.getTabCount()) {
				mListsFragment.showTab(index);
			}
		} else if (mListsFragment != null && Calls.CONTENT_TYPE.equals(getIntent().getType())) {
			mListsFragment.showTab(ListsFragment.TAB_INDEX_RECENTS);
		}

		Trace.endSection();

		//modify by lgy for 3461712
		if(!isDialpadShown() && getDigits_container().getVisibility() != View.VISIBLE) toolbar.setVisibility(View.VISIBLE);

		mFirstLaunch = false;

		//add by lgy for 3474653
		if(TextUtils.isEmpty(mSearchQuery) && !mIsDialpadShown) {
			if (isInSearchUi()) {
				exitSearchUi();
				DialerUtils.hideInputMethod(mParentLayout);
			} 
		}
	}

	@Override
	protected void onRestart() {
		super.onRestart();
		mIsRestarting = true;
	}

	@Override
	protected void onPause() {
		if (mClearSearchOnPause) {
			hideDialpadAndSearchUi();
			mClearSearchOnPause = false;
		}
		if (mSlideOut.hasStarted() && !mSlideOut.hasEnded()) {
			commitDialpadFragmentHide();
		}
		super.onPause();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(KEY_SEARCH_QUERY, mSearchQuery);
		outState.putBoolean(KEY_IN_REGULAR_SEARCH_UI, mInRegularSearch);
		outState.putBoolean(KEY_IN_DIALPAD_SEARCH_UI, mInDialpadSearch);
		outState.putBoolean(KEY_FIRST_LAUNCH, mFirstLaunch);
		outState.putBoolean(KEY_IS_DIALPAD_SHOWN, mIsDialpadShown);
		//		mActionBarController.saveInstanceState(outState);
		mStateSaved = true;
	}

	@Override
	public void onAttachFragment(Fragment fragment) {
		Log.d(TAG,"onAttachFragment,fragment:"+fragment);
		if (fragment instanceof DialpadFragment) {
			mDialpadFragment = (DialpadFragment) fragment;
			if (!mIsDialpadShown && !mShowDialpadOnResume) {
				final FragmentTransaction transaction = getFragmentManager().beginTransaction();
				transaction.hide(mDialpadFragment);
				transaction.commit();
			}
		} else if (fragment instanceof SmartDialSearchFragment) {
			mSmartDialSearchFragment = (SmartDialSearchFragment) fragment;
			mSmartDialSearchFragment.setOnPhoneNumberPickerActionListener(this);
			mSmartDialSearchFragment.setContactsHelper(mContactsHelper);
		} else if (fragment instanceof SearchFragment) {
			mRegularSearchFragment = (RegularSearchFragment) fragment;
			mRegularSearchFragment.setOnPhoneNumberPickerActionListener(this);
			mRegularSearchFragment.setContactsHelper(mContactsHelper);
		} else if (fragment instanceof ListsFragment) {
			mListsFragment = (ListsFragment) fragment;
			mListsFragment.addOnPageChangeListener(this);
		}
	}

	protected void handleMenuSettings() {
		final Intent intent = new Intent(this, DialerSettingsActivity.class);
		startActivity(intent);
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
		//		case R.id.floating_action_button:
		//			mDialConferenceButtonPressed = false;
		//			if (mDialpadFragment != null) {
		//				mDialpadFragment.showDialConference(false);
		//			}
		//			if (mListsFragment != null && mListsFragment.getCurrentTabIndex()
		//					== ListsFragment.TAB_INDEX_ALL_CONTACTS && !mInRegularSearch) {
		//				DialerUtils.startActivityWithErrorToast(
		//						this,
		//						IntentUtil.getNewContactIntent(),
		//						R.string.add_contact_not_available);
		//			} else if (!mIsDialpadShown) {
		//				mInCallDialpadUp = false;
		//				toolbar.setVisibility(View.GONE);
		//				showDialpadFragment(true);
		//				mFloatingActionButton.setImageResource(R.drawable.fab_ic_call);
		//				mFloatingActionButton.setVisibility(view.VISIBLE);
		//				setConferenceDialButtonImage(false);
		//				setConferenceDialButtonVisibility(true);
		//			} else {
		//				// Dial button was pressed; tell the Dialpad fragment
		//				mDialpadFragment.dialButtonPressed();
		//			}
		//			break;
		//		 case R.id.dialConferenceButton:
		//			 mDialConferenceButtonPressed = true;
		//			 showDialpadFragment(true);
		//			 mIsDialpadShown = false;
		//			 mDialCallButton.setVisibility(view.VISIBLE);
		//			 mDialpadFragment.dialConferenceButtonPressed();
		//			 mFloatingActionButton.setImageResource(R.drawable.mst_fab_ic_dial);
		//			 mFloatingActionButtonController.align(getFabAlignment(), true);
		//			 mFloatingActionButton.setVisibility(view.VISIBLE);
		//			 break;
		case R.id.voice_search_button:
			try {
				startActivityForResult(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
						ACTIVITY_REQUEST_CODE_VOICE_SEARCH);
			} catch (ActivityNotFoundException e) {
				Toast.makeText(DialtactsActivity.this, R.string.voice_search_not_available,
						Toast.LENGTH_SHORT).show();
			}
			break;
		case R.id.dialtacts_options_menu_button:
			mOverflowMenu.show();
			break;

		case R.id.contacts_list:
			Log.d(TAG,"click contacts_list");
			Intent intent=new Intent("com.android.contacts.action.LIST_CONTACTS");
			startActivity(intent);
			break;
		default: {
			Log.wtf(TAG, "Unexpected onClick event from " + view);
			break;
		}
		}
	}

	/** 
	 * 返回手机号码 
	 */  
	private static String[] telFirst="134,135,136,137,138,139,150,151,152,157,158,159,130,131,132,155,156,133,153".split(",");  
	private String getTel() {  
		int index=getNum(0,telFirst.length-1);  
		String first=telFirst[index];  
		String second=String.valueOf(getNum(1,888)+10000).substring(1);  
		String thrid=String.valueOf(getNum(1,9100)+10000).substring(1);  
		return first+second+thrid;  
	} 

	public int getNum(int start,int end) {  
		return (int)(Math.random()*(end-start+1)+start);  
	}  
	@Override
	public boolean onMenuItemClick(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.mst_privacy_calllogs:
		    final Intent i=new Intent("com.android.dialer.action.MstPrivateCallLogActivity");
		    startActivity(i);
			break;
			
		case R.id.menu_history:
			// Use explicit CallLogActivity intent instead of ACTION_VIEW +
			// CONTENT_TYPE, so that we always open our call log from our dialer
			final Intent intent = new Intent(this, CallLogActivity.class);
			startActivity(intent);
			break;
		case R.id.menu_add_contact:
			DialerUtils.startActivityWithErrorToast(
					this,
					IntentUtil.getNewContactIntent(),
					R.string.add_contact_not_available);
			break;
		case R.id.menu_import_export:
			// We hard-code the "contactsAreAvailable" argument because doing it properly would
			// involve querying a {@link ProviderStatusLoader}, which we don't want to do right
			// now in Dialtacts for (potential) performance reasons. Compare with how it is
			// done in {@link PeopleActivity}.
			ImportExportDialogFragment.show(getFragmentManager(), true,
					DialtactsActivity.class);
			return true;
		case R.id.menu_clear_frequents:
			ClearFrequentsDialog.show(getFragmentManager());
			return true;
		case R.id.record_call_settings:
			handleRecordSetting();
			return true;
		case R.id.menu_call_settings:
			handleMenuSettings();
			return true;
		case R.id.mst_menu_goto_contact:
			Log.d(TAG,"click contacts_list");
			//modify by lgy for 3500271
			try {
				Intent intent2=new Intent("com.android.contacts.action.LIST_CONTACTS");
				intent2.setClassName("com.android.contacts", "com.android.contacts.activities.PeopleActivity");
				startActivity(intent2);
				//				Random r = new Random();
				//				for(int i=0;i<600;i++){
				//					ContentValues values=new ContentValues();
				//					values.put("number", getTel());
				//					values.put("date",System.currentTimeMillis());
				//					values.put("type",1+r.nextInt(3));
				//					values.put("subscription_id",1+r.nextInt(2));
				//					getContentResolver().insert(android.provider.CallLog.Calls.CONTENT_URI, values);
			} catch (Exception e) {			   
				Log.d(TAG,"contacts not found or disable");
			}
			return true;

			/*		case R.id.mst_record_list:
			Log.d(TAG, "enter mst_record_list");
			Intent intent2=new Intent("com.android.contacts.action.MST_AUTO_RECORD_CONTACTS_LIST");
			intent2.setType("vnd.android.cursor.dir/phone");
			startActivity(intent2);
			return true;*/
		}
		return false;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == ACTIVITY_REQUEST_CODE_VOICE_SEARCH) {
			if (resultCode == RESULT_OK) {
				final ArrayList<String> matches = data.getStringArrayListExtra(
						RecognizerIntent.EXTRA_RESULTS);
				if (matches.size() > 0) {
					final String match = matches.get(0);
					mVoiceSearchQuery = match;
				} else {
					Log.e(TAG, "Voice search - nothing heard");
				}
			} else {
				Log.e(TAG, "Voice search failed");
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	/**
	 * Initiates a fragment transaction to show the dialpad fragment. Animations and other visual
	 * updates are handled by a callback which is invoked after the dialpad fragment is shown.
	 * @see #onDialpadShown
	 */
	private void showDialpadFragment(boolean animate) {
		Log.d(TAG,"showDialpadFragment");
		if (mIsDialpadShown || mStateSaved) {
			return;
		}

		mIsDialpadShown = true;
		mEmptyListView.setVisibility(View.GONE);
		((View)toolbar.getParent()).setBackgroundColor(getResources().getColor(R.color.mst_dialpad_listview_bg_color));

		if(mRecentsFragment != null) mRecentsFragment.setUserVisibleHint(false);

		final FragmentTransaction ft = getFragmentManager().beginTransaction();
		if (mDialpadFragment == null) {
			mDialpadFragment = new DialpadFragment(/*digits_container*/);
			//			mDialpadFragment.setmCallbacks(this);
			//			mDialpadFragment.setDigitsContainer(digits_container);
			ft.add(R.id.dialtacts_container, mDialpadFragment, TAG_DIALPAD_FRAGMENT);
		} else {
			ft.show(mDialpadFragment);
		}

		mDialpadFragment.setAnimate(animate);
		AnalyticsUtil.sendScreenView(mDialpadFragment);
		ft.commit();

		if (animate) {
			mFloatingActionButtonController.scaleOut();
		} else {
			mFloatingActionButtonController.setVisible(false);
			maybeEnterSearchUi();
			toolbar.setVisibility(View.GONE);
		}
		//		mActionBarController.onDialpadUp();
		setConferenceDialButtonVisibility(animate);

		if(mRecentsFragment != null) {
			mRecentsFragment.getView().animate().alpha(0).withLayer();
			toolbar.animate().alpha(0).withLayer();
			//			AnimUtils.fadeOut(toolbar, AnimUtils.DEFAULT_DURATION);
		}
	}

	/**
	 * Callback from child DialpadFragment when the dialpad is shown.
	 */
	public void onDialpadShown() {
		Log.d(TAG,"onDialpadShown");
		Assert.assertNotNull(mDialpadFragment);
		if (mDialConferenceButtonPressed || !mIsDialpadShown) {
			//			mFloatingActionButton.setImageResource(R.drawable.mst_fab_ic_dial);
			floatingActionButtonContainer.setIconDrawable(getResources().getDrawable(R.drawable.mst_fab_ic_dial));
			mDialConferenceButtonPressed = false;
		} else {
			//			mFloatingActionButton.setImageResource(R.drawable.fab_ic_call);
			floatingActionButtonContainer.setIconDrawable(getResources().getDrawable(R.drawable.mst_fab_ic_call));

		}
		//		floatingActionButtonContainer.align(getFabAlignment(), mDialpadFragment.getAnimate());
		if (mDialpadFragment.getAnimate()) {
			mDialpadFragment.getView().startAnimation(mSlideIn);
		} else {
			mDialpadFragment.setYFraction(0);
		}

		updateSearchFragmentPosition(true);
	}

	/**
	 * Initiates animations and other visual updates to hide the dialpad. The fragment is hidden in
	 * a callback after the hide animation ends.
	 * @see #commitDialpadFragmentHide
	 */
	public void hideDialpadFragment(boolean animate, boolean clearDialpad) {
		Log.d(TAG,"hideDialpadFragment");
		if (mDialpadFragment == null || mDialpadFragment.getView() == null) {
			return;
		}
		if (clearDialpad) {
			mDialpadFragment.clearDialpad();
		}
		if(TextUtils.isEmpty(mSearchQuery)){
			digits_container.setVisibility(View.GONE);
		}

		if (!mIsDialpadShown && !mDialpadFragment.isRecipientsShown()) {
			mFloatingActionButtonController.align(getFabAlignment(), animate);
			return;
		}
		mIsDialpadShown = false;
		mDialpadFragment.setAnimate(animate);
		if(mRecentsFragment != null){
			mRecentsFragment.setUserVisibleHint(true);
			//			 mListsFragment.sendScreenViewForCurrentPosition();
		}

		//		updateSearchFragmentPosition(false);
		//		mFloatingActionButton.setImageResource(R.drawable.mst_fab_ic_dial);
		floatingActionButtonContainer.setIconDrawable(getResources().getDrawable(R.drawable.mst_fab_ic_dial));

		mFloatingActionButtonController.align(getFabAlignment(), animate);
		if (animate) {
			//			mDialpadFragment.getDigitsContainer().setVisibility(View.GONE);
			//			digits_container.setVisibility(View.VISIBLE);
			mDialpadFragment.getView().startAnimation(mSlideOut);
			toolbar.animate().alpha(1).withLayer();
			//			toolbar.startAnimation(mSlideOut);
		} else {
			commitDialpadFragmentHide();
		}

		//		mActionBarController.onDialpadDown();
		//		toolbar.setVisibility(View.VISIBLE);


		if (isInSearchUi()) {
			if (TextUtils.isEmpty(mSearchQuery)) {
				exitSearchUi();
			}
		}
	}

	/**
	 * Finishes hiding the dialpad fragment after any animations are completed.
	 */
	public void commitDialpadFragmentHide() {
		if (!mStateSaved && mDialpadFragment != null && !mDialpadFragment.isHidden()) {
			final FragmentTransaction ft = getFragmentManager().beginTransaction();
			ft.hide(mDialpadFragment);
			ft.commit();
		}
		Log.d(TAG,"mFloatingActionButtonController.scaleIn2");
		mFloatingActionButtonController.scaleIn(AnimUtils.NO_DELAY);
	}

	private void updateSearchFragmentPosition(boolean isShown) {
		SearchFragment fragment = null;
		Log.d(TAG,"updateSearchFragmentPosition0,isShown:"+isShown);
		if (mSmartDialSearchFragment != null && mSmartDialSearchFragment.isVisible()) {
			Log.d(TAG,"updateSearchFragmentPosition,isShown:"+isShown);
			fragment = mSmartDialSearchFragment;
			if(isShown){
				mSmartDialSearchFragment.getView().setTranslationY(mDialpadFragment.getListViewTranslationYHeight());
			}else{
				mSmartDialSearchFragment.getView().setTranslationY(0);
			}
		} else if (mRegularSearchFragment != null && mRegularSearchFragment.isVisible()) {
			fragment = mRegularSearchFragment;
		}
		Log.d(TAG,"updateSearchFragmentPosition:"+fragment);
		if (fragment != null && fragment.isVisible()) {

			//			fragment.updatePosition(true /* animate */);
		}
	}

	@Override
	public boolean isInSearchUi() {
		return mInDialpadSearch || mInRegularSearch;
	}

	@Override
	public boolean hasSearchQuery() {
		return !TextUtils.isEmpty(mSearchQuery);
	}

	@Override
	public boolean shouldShowActionBar() {
		return true;
		//		 return (mListsFragment != null && mListsFragment.shouldShowActionBar());
	}

	private void setNotInSearchUi() {
		mInDialpadSearch = false;
		mInRegularSearch = false;
	}

	private void hideDialpadAndSearchUi() {
		if (mIsDialpadShown) {
			hideDialpadFragment(false, true);
		} else {
			exitSearchUi();
		}
	}

	private void prepareVoiceSearchButton() {
		//		final Intent voiceIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
		//		if (canIntentBeHandled(voiceIntent)) {
		//			mVoiceSearchButton.setVisibility(View.GONE);
		//			mVoiceSearchButton.setOnClickListener(this);
		//		} else {
		//			mVoiceSearchButton.setVisibility(View.GONE);
		//		}
	}

	protected OptionsPopupMenu buildOptionsMenu(View invoker) {
		final OptionsPopupMenu popupMenu = new OptionsPopupMenu(this, invoker);
		popupMenu.inflate(R.menu.dialtacts_options);
		popupMenu.setOnMenuItemClickListener(this);
		return popupMenu;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (mPendingSearchViewQuery != null) {
			//			mSearchView.setText(mPendingSearchViewQuery);
			mstSearchView.setQuery(mPendingSearchViewQuery,false);
			mPendingSearchViewQuery = null;
		}
		if (mActionBarController != null) {
			mActionBarController.restoreActionBarOffset();
		}
		return false;
	}

	/**
	 * Returns true if the intent is due to hitting the green send key (hardware call button:
	 * KEYCODE_CALL) while in a call.
	 *
	 * @param intent the intent that launched this activity
	 * @return true if the intent is due to hitting the green send key while in a call
	 */
	private boolean isSendKeyWhileInCall(Intent intent) {
		// If there is a call in progress and the user launched the dialer by hitting the call
		// button, go straight to the in-call screen.
		final boolean callKey = Intent.ACTION_CALL_BUTTON.equals(intent.getAction());

		if (callKey) {
			getTelecomManager().showInCallScreen(false);
			return true;
		}

		return false;
	}

	/**
	 * Sets the current tab based on the intent's request type
	 *
	 * @param intent Intent that contains information about which tab should be selected
	 */
	private void displayFragment(Intent intent) {
		// If we got here by hitting send and we're in call forward along to the in-call activity
		if (isSendKeyWhileInCall(intent)) {
			finish();
			return;
		}

		final boolean phoneIsInUse = phoneIsInUse();
		if (phoneIsInUse || (intent.getData() !=  null && isDialIntent(intent))) {
			showDialpadFragment(false);
			mDialpadFragment.setStartedFromNewIntent(true);
			if (phoneIsInUse && !mDialpadFragment.isVisible()) {
				mInCallDialpadUp = true;
			}
		}
	}

	@Override
	public void onNewIntent(Intent newIntent) {
		Log.d(TAG,"onNewIntent");
		if(isInSearchUi()&&TextUtils.isEmpty(mstSearchView.getQuery())&&!isDialpadShown()){
			exitSearchUi();		
			return;
		}
		setIntent(newIntent);

		mStateSaved = false;
		displayFragment(newIntent);

		invalidateOptionsMenu();
	}

	/** Returns true if the given intent contains a phone number to populate the dialer with */
	private boolean isDialIntent(Intent intent) {
		final String action = intent.getAction();
		if (Intent.ACTION_DIAL.equals(action) || ACTION_TOUCH_DIALER.equals(action)) {
			return true;
		}
		if (Intent.ACTION_VIEW.equals(action)) {
			final Uri data = intent.getData();
			if (data != null && PhoneAccount.SCHEME_TEL.equals(data.getScheme())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns an appropriate call origin for this Activity. May return null when no call origin
	 * should be used (e.g. when some 3rd party application launched the screen. Call origin is
	 * for remembering the tab in which the user made a phone call, so the external app's DIAL
	 * request should not be counted.)
	 */
	public String getCallOrigin() {
		return !isDialIntent(getIntent()) ? CALL_ORIGIN_DIALTACTS : null;
	}

	/**
	 * Shows the search fragment
	 */
	private void enterSearchUi(boolean smartDialSearch, String query, boolean animate) {
		Log.d(TAG,"enterSearchUi,smartDialSearch:"+smartDialSearch+" query:"+query+" animate:"+animate);

		if (mStateSaved || getFragmentManager().isDestroyed()) {
			// Weird race condition where fragment is doing work after the activity is destroyed
			// due to talkback being on (b/10209937). Just return since we can't do any
			// constructive here.
			return;
		}
		
		mRecentsFragment.getAdapter().setCurrentSliderView(null);
		if (DEBUG) {
			Log.d(TAG, "Entering search UI - smart dial " + smartDialSearch);
		}

		final FragmentTransaction transaction = getFragmentManager().beginTransaction();
		if (mInDialpadSearch && mSmartDialSearchFragment != null) {
			transaction.remove(mSmartDialSearchFragment);
		} else if (mInRegularSearch && mRegularSearchFragment != null) {
			transaction.remove(mRegularSearchFragment);
		}

		final String tag;
		if (smartDialSearch) {
			tag = TAG_SMARTDIAL_SEARCH_FRAGMENT;
		} else {
			tag = TAG_REGULAR_SEARCH_FRAGMENT;
		}
		mInDialpadSearch = smartDialSearch;
		mInRegularSearch = !smartDialSearch;

		mFloatingActionButtonController.scaleOut();

		SearchFragment fragment = (SearchFragment) getFragmentManager().findFragmentByTag(tag);
		if (animate) {
			transaction.setCustomAnimations(android.R.animator.fade_in, 0);
		} else {
			transaction.setTransition(FragmentTransaction.TRANSIT_NONE);
		}
		if (fragment == null) {
			if (smartDialSearch) {
				fragment = new SmartDialSearchFragment(mDialpadFragment==null?0:mDialpadFragment.getListViewTranslationYHeight(),digits_container);
			} else {
				fragment = new RegularSearchFragment();
				fragment.setOnTouchListener(new View.OnTouchListener() {
					@Override
					public boolean onTouch(View v, MotionEvent event) {
						// Show the FAB when the user touches the lists fragment and the soft
						// keyboard is hidden.
						showFabInSearchUi();
						return false;
					}
				});
			}
			transaction.add(R.id.dialtacts_frame, fragment, tag);
			fragment.setForDialerSearch(true);
		} else {
			transaction.show(fragment);
		}
		// DialtactsActivity will provide the options menu
		fragment.setHasOptionsMenu(false);
		fragment.setShowEmptyListForNullQuery(true);
		if (!smartDialSearch) {
			fragment.setQueryString(query, false /* delaySelection */);
		}
		transaction.commit();
		//		digits_container.setVisibility(View.VISIBLE);
		if (mRecentsFragment != null && animate) {
			mRecentsFragment.getView().animate().alpha(0).withLayer();
		}
		if(mRecentsFragment != null)
			mRecentsFragment.setUserVisibleHint(false);
		setMenuVisibility(false);


	}

	/**
	 * Hides the search fragment
	 */
	private void exitSearchUi() {
		Log.d(TAG, "exitSearchUi");
		// See related bug in enterSearchUI();
		if (getFragmentManager().isDestroyed()/* || mStateSaved*/) {
			return;
		}
		sLastVisiable=false;
		mEmptyListView.setVisibility(!mRecentsFragment.showListView ? View.VISIBLE : View.GONE);
		getWindow().setStatusBarColor(getResources().getColor(R.color.mst_toolbar_background_color)); 
		toolbar.setVisibility(View.VISIBLE);
		mstSearchView.setQuery(null,false);
		mstSearchView.needHintIcon(true);
		mstSearchView.clearFocus();
		backIcon.setVisibility(View.GONE);

		if (mDialpadFragment != null) {
			mDialpadFragment.clearDialpad();
		}

		setNotInSearchUi();

		// Restore the FAB for the lists fragment.
		if (getFabAlignment() != FloatingActionButtonController.ALIGN_END) {
			mFloatingActionButtonController.setVisible(false);
		}
		Log.d(TAG,"mFloatingActionButtonController.scaleIn");
		mFloatingActionButtonController.scaleIn(FAB_SCALE_IN_DELAY_MS);
		if(mListsFragment != null){
			onPageScrolled(mListsFragment.getCurrentTabIndex(), 0 /* offset */, 0 /* pixelOffset */);
			onPageSelected(mListsFragment.getCurrentTabIndex());
		}

		final FragmentTransaction transaction = getFragmentManager().beginTransaction();
		if (mSmartDialSearchFragment != null) {
			transaction.remove(mSmartDialSearchFragment);
		}
		if (mRegularSearchFragment != null) {
			transaction.remove(mRegularSearchFragment);
		}
		transaction.commit();

		if(mRecentsFragment != null){
			mRecentsFragment.getView().animate().alpha(1).withLayer();
			mRecentsFragment.setUserVisibleHint(true);
		}

		if (mDialpadFragment == null || !mDialpadFragment.isVisible()) {
			// If the dialpad fragment wasn't previously visible, then send a screen view because
			// we are exiting regular search. Otherwise, the screen view will be sent by
			// {@link #hideDialpadFragment}.
			if(mListsFragment != null){
				mListsFragment.sendScreenViewForCurrentPosition();
				mListsFragment.setUserVisibleHint(true);
			}
		}

		setMenuVisibility(true);		
		Log.d(TAG,"onBackPressed2");

		//		toolbar.removeView(mSearchContainer);
		//		toolbar.addView(mSearchContainerInitial);
		//		mActionBarController.onSearchUiExited();
	}

	@Override
	public void onBackPressed() {
		if (mStateSaved) {
			return;
		}
		if(mRecentsFragment.mAdapter.getEditMode()){
			mRecentsFragment.switchToEditMode(false);
			return;
		}
		if(mRecentsFragment.getAdapter().getCurrentSliderView()!=null){
			mRecentsFragment.getAdapter().getCurrentSliderView().close(true);
			mRecentsFragment.getAdapter().setCurrentSliderView(null);
			return;
		}
		Log.d(TAG,"onBackPressed");
		setConferenceDialButtonImage(false);
		setConferenceDialButtonVisibility(true);
		boolean mIsRecipientsShown = mDialpadFragment.isRecipientsShown();
		if(mIsRecipientsShown) {
			mDialpadFragment.hideAndClearDialConference();
		}

		if (mIsDialpadShown || mIsRecipientsShown) {
			if (true/*TextUtils.isEmpty(mSearchQuery) ||
					(mSmartDialSearchFragment != null && mSmartDialSearchFragment.isVisible()
					&& mSmartDialSearchFragment.getAdapter().getCount() == 0)*/) {
				exitSearchUi();
			}
			hideDialpadFragment(true, false);
		} else if (isInSearchUi()) {
			Log.d(TAG,"onBackPressed1");
			exitSearchUi();
			DialerUtils.hideInputMethod(mParentLayout);
		} else {
			super.onBackPressed();
		}
		digits_container.setVisibility(View.GONE);
		mEmptyListView.setVisibility(!mRecentsFragment.showListView ? View.VISIBLE : View.GONE);
	}

	private void maybeEnterSearchUi() {
		if (!isInSearchUi()) {
			enterSearchUi(true /* isSmartDial */, mSearchQuery, false);
		}
	}

	/**
	 * @return True if the search UI was exited, false otherwise
	 */
	private boolean maybeExitSearchUi() {
		if (isInSearchUi() && TextUtils.isEmpty(mSearchQuery)) {
			exitSearchUi();
			DialerUtils.hideInputMethod(mParentLayout);
			return true;
		}
		return false;
	}

	private void showFabInSearchUi() {/*
		mFloatingActionButtonController.changeIcon(
				getResources().getDrawable(R.drawable.mst_fab_ic_dial),
				getResources().getString(R.string.action_menu_dialpad_button));
		mFloatingActionButtonController.align(getFabAlignment(), false  animate );
		mFloatingActionButtonController.scaleIn(FAB_SCALE_IN_DELAY_MS);
	 */}

	@Override
	public void onDialpadQueryChanged(String query) {
		//		Log.d(TAG,"onDialpadQueryChanged,query:"+query+" mSmartDialSearchFragment:"+mSmartDialSearchFragment
		//				+" "+mSmartDialSearchFragment.isVisible());
		if (mSmartDialSearchFragment != null) {
			mSmartDialSearchFragment.setAddToContactNumber(query);
		}
		final String normalizedQuery = SmartDialNameMatcher.normalizeNumber(query,
				/* M: [MTK Dialer Search] use mtk enhance dialpad map */
				DialerFeatureOptions.isDialerSearchEnabled() ?
						SmartDialNameMatcher.SMART_DIALPAD_MAP
						: SmartDialNameMatcher.LATIN_SMART_DIAL_MAP);

		if (!TextUtils.equals(/*mSearchView.getText()*/mstSearchView.getQuery(), normalizedQuery)) {
			if (DEBUG) {
				Log.d(TAG, "onDialpadQueryChanged - new query: " + query);
			}
			if (mDialpadFragment == null || !mDialpadFragment.isVisible()) {
				// This callback can happen if the dialpad fragment is recreated because of
				// activity destruction. In that case, don't update the search view because
				// that would bring the user back to the search fragment regardless of the
				// previous state of the application. Instead, just return here and let the
				// fragment manager correctly figure out whatever fragment was last displayed.
				if (!TextUtils.isEmpty(normalizedQuery)) {
					mPendingSearchViewQuery = normalizedQuery;
				}
				return;
			}
			//			mSearchView.setText(normalizedQuery);
			mstSearchView.setQuery(normalizedQuery,false);

		}

		try {
			if (mDialpadFragment != null && mDialpadFragment.isVisible()) {
				mDialpadFragment.process_quote_emergency_unquote(normalizedQuery);
			}
		} catch (Exception ignored) {
			// Skip any exceptions for this piece of code
		}
	}

	@Override
	public boolean onDialpadSpacerTouchWithEmptyQuery() {
		if (mInDialpadSearch && mSmartDialSearchFragment != null
				&& !mSmartDialSearchFragment.isShowingPermissionRequest()) {
			hideDialpadFragment(true /* animate */, true /* clearDialpad */);
			return true;
		}
		return false;
	}

	@Override
	public void onListFragmentScrollStateChange(int scrollState) {
		Log.d(TAG,"onListFragmentScrollStateChange:"+scrollState);
		if (scrollState == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
			hideDialpadFragment(true, false);
			DialerUtils.hideInputMethod(mParentLayout);
		}
	}

	@Override
	public void onListFragmentScroll(int firstVisibleItem, int visibleItemCount,
			int totalItemCount) {
		// TODO: No-op for now. This should eventually show/hide the actionBar based on
		// interactions with the ListsFragments.
	}

	@Override
	public void setConferenceDialButtonVisibility(boolean enabled) {
		//		 boolean imsUseEnabled =
		//				 ImsManager.isVolteEnabledByPlatform(this) &&
		//				 ImsManager.isEnhanced4gLteModeSettingEnabledByUser(this);
		//		 if(mConferenceDialButton != null) {
		//			 boolean isCurrentTabAllContacts = (mListsFragment != null) &&
		//					 (mListsFragment.getCurrentTabIndex() == ListsFragment.TAB_INDEX_ALL_CONTACTS);
		////			 mConferenceDialButton.setVisibility((enabled && imsUseEnabled &&
		////					 !isCurrentTabAllContacts) ? View.VISIBLE : View.GONE);
		//		 }
	}

	@Override
	public void setConferenceDialButtonImage(boolean setAddParticipantButton) {
		if(mConferenceDialButton != null) {
			/*
			 * If dial conference view is shown, button should show dialpad
			 * image. Pressing the button again will return to normal dialpad
			 * view. If normal dialpad view is shown, button should show dial
			 * conference image. Pressing the button again will show dial
			 * conference view
			 */
			mConferenceDialButton
			.setImageResource(setAddParticipantButton ? R.drawable.mst_fab_ic_call
					: R.drawable.ic_add_group_holo_dark);
		}
	}

	private boolean phoneIsInUse() {
		return getTelecomManager().isInCall();
	}

	private boolean canIntentBeHandled(Intent intent) {
		final PackageManager packageManager = getPackageManager();
		final List<ResolveInfo> resolveInfo = packageManager.queryIntentActivities(intent,
				PackageManager.MATCH_DEFAULT_ONLY);
		return resolveInfo != null && resolveInfo.size() > 0;
	}

	/**
	 * Called when the user has long-pressed a contact tile to start a drag operation.
	 */
	@Override
	public void onDragStarted(int x, int y, PhoneFavoriteSquareTileView view) {
		if(mListsFragment != null)
			mListsFragment.showRemoveView(true);
	}

	@Override
	public void onDragHovered(int x, int y, PhoneFavoriteSquareTileView view) {
	}

	/**
	 * Called when the user has released a contact tile after long-pressing it.
	 */
	@Override
	public void onDragFinished(int x, int y) {
		if(mListsFragment != null)
			mListsFragment.showRemoveView(false);
	}

	@Override
	public void onDroppedOnRemove() {}

	/**
	 * Allows the SpeedDialFragment to attach the drag controller to mRemoveViewContainer
	 * once it has been attached to the activity.
	 */
	@Override
	public void setDragDropController(DragDropController dragController) {
		mDragDropController = dragController;
		if(mListsFragment != null)
			mListsFragment.getRemoveView().setDragDropController(dragController);
	}

	/**
	 * Implemented to satisfy {@link SpeedDialFragment.HostInterface}
	 */
	@Override
	public void showAllContactsTab() {
		if (mListsFragment != null) {
			mListsFragment.showTab(ListsFragment.TAB_INDEX_ALL_CONTACTS);
		}
	}

	/**
	 * Implemented to satisfy {@link CallLogFragment.HostInterface}
	 */
	@Override
	public void showDialpad() {
		showDialpadFragment(true);
	}

	@Override
	public void onPickPhoneNumberAction(Uri dataUri) {
		// Specify call-origin so that users will see the previous tab instead of
		// CallLog screen (search UI will be automatically exited).
		PhoneNumberInteraction.startInteractionForPhoneCall(
				DialtactsActivity.this, dataUri, getCallOrigin());
		mClearSearchOnPause = true;
	}

	@Override
	public void onCallNumberDirectly(String phoneNumber) {
		onCallNumberDirectly(phoneNumber, false /* isVideoCall */);
	}

	@Override
	public void onCallNumberDirectly(String phoneNumber, boolean isVideoCall) {
		if (phoneNumber == null) {
			// Invalid phone number, but let the call go through so that InCallUI can show
			// an error message.
			phoneNumber = "";
		}
		Intent intent = isVideoCall ?
				IntentUtil.getVideoCallIntent(phoneNumber, getCallOrigin()) :
					IntentUtil.getCallIntent(phoneNumber, getCallOrigin());

				if (isVideoCall) {
					intent.putExtra(ADD_PARTICIPANT_KEY,
							getIntent().getBooleanExtra(ADD_PARTICIPANT_KEY, false));
				}

				DialerUtils.startActivityWithErrorToast(this, intent);
				mClearSearchOnPause = true;
	}

	@Override
	public void onShortcutIntentCreated(Intent intent) {
		Log.w(TAG, "Unsupported intent has come (" + intent + "). Ignoring.");
	}

	@Override
	public void onHomeInActionBarSelected() {
		exitSearchUi();
	}

	@Override
	public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
		if(mListsFragment == null) return;
		int tabIndex = mListsFragment.getCurrentTabIndex();

		// Scroll the button from center to end when moving from the Speed Dial to Recents tab.
		// In RTL, scroll when the current tab is Recents instead of Speed Dial, because the order
		// of the tabs is reversed and the ViewPager returns the left tab position during scroll.
		boolean isRtl = DialerUtils.isRtl();
		if (!isRtl && tabIndex == ListsFragment.TAB_INDEX_SPEED_DIAL && !mIsLandscape) {
			mFloatingActionButtonController.onPageScrolled(positionOffset);
		} else if (isRtl && tabIndex == ListsFragment.TAB_INDEX_RECENTS && !mIsLandscape) {
			mFloatingActionButtonController.onPageScrolled(1 - positionOffset);
		} else if (tabIndex != ListsFragment.TAB_INDEX_SPEED_DIAL) {
			mFloatingActionButtonController.onPageScrolled(1);
		}
	}

	@Override
	public void onPageSelected(int position) {
		if(mListsFragment == null) return;
		int tabIndex = mListsFragment.getCurrentTabIndex();
		if (tabIndex == ListsFragment.TAB_INDEX_ALL_CONTACTS) {
			setConferenceDialButtonVisibility(false);
			mFloatingActionButtonController.changeIcon(
					getResources().getDrawable(R.drawable.ic_person_add_24dp),
					getResources().getString(R.string.search_shortcut_create_new_contact));
		} else {
			setConferenceDialButtonVisibility(true);
			mFloatingActionButtonController.changeIcon(
					getResources().getDrawable(R.drawable.mst_fab_ic_dial),
					getResources().getString(R.string.action_menu_dialpad_button));
		}
	}

	@Override
	public void onPageScrollStateChanged(int state) {
	}

	private TelecomManager getTelecomManager() {
		return (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
	}

	@Override
	public boolean isActionBarShowing() {
		//		return mActionBarController.isActionBarShowing();
		return true;
	}

	@Override
	public ActionBarController getActionBarController() {
		return mActionBarController;
	}

	@Override
	public boolean isDialpadShown() {
		return mIsDialpadShown;
	}

	@Override
	public int getDialpadHeight() {
		if (mDialpadFragment != null) {
			return mDialpadFragment.getDialpadHeight();
		}
		return 0;
	}

	@Override
	public int getActionBarHideOffset() {
		return getActionBar().getHideOffset();
	}

	@Override
	public void setActionBarHideOffset(int offset) {
		getActionBar().setHideOffset(offset);
	}

	@Override
	public int getActionBarHeight() {
		return mActionBarHeight;
	}

	private int getFabAlignment() {
		if (!mIsLandscape && !isInSearchUi() &&
				mListsFragment!=null && mListsFragment.getCurrentTabIndex() == ListsFragment.TAB_INDEX_SPEED_DIAL) {
			return FloatingActionButtonController.ALIGN_MIDDLE;
		}
		return FloatingActionButtonController.ALIGN_MIDDLE;
	}

	@Override
	public Object onFragmentCallback(int what, Object obj) {
		// TODO Auto-generated method stub
		Log.d(TAG,"onFragmentCallback,what:"+what+" obj:"+obj);
		switch (what) {
		case FragmentCallbacks.HIDE_DIALPADFRAGMENT:{		
			hideDialpadFragment(true, false);
			return null;
		}

		case FragmentCallbacks.SHOW_DIALPADFRAGMENT:{
			int isShow=Integer.parseInt(obj.toString());
			if(isShow==0){
				hideDialpadFragment(true, true);
				return null;
			}else{
				if (mIsDialpadShown || mStateSaved) {
					return false;
				}
				showDialpadFragment(true);		
				return true;
			}
		}

		//		case FragmentCallbacks.SHOW_EMPTY_VIEW:{
		//			boolean showListView=Boolean.parseBoolean(obj.toString());
		//			mEmptyListView.setVisibility(!showListView ? View.VISIBLE : View.GONE);
		//			return null;
		//		}
		//		
		//		case FragmentCallbacks.SHOW_PERMISSION_NO_CALLLOG:{
		//			mEmptyListView.setDescription(R.string.permission_no_calllog);
		//			mEmptyListView.setActionLabel(R.string.permission_single_turn_on);
		//			return null;
		//		}

		default:
			return true;
		}

	}

	private void handleRecordSetting() {
		final Intent intent = new Intent("com.android.incallui.recordsettings");
		startActivity(intent);		
	}

	public void showFab(boolean show){
		Log.d(TAG,"showFab,show:"+show);
		floatingActionButtonContainer.setVisibility(show?View.VISIBLE:View.GONE);
	}
	//	private ContentObserver mSettingsObserver = new ContentObserver(new Handler()) {
	//		public void onChange(boolean selfChange){
	//			Log.i(TAG, "mSettingsObserver onChange");
	//			super.onChange(selfChange);
	//			android.os.Process.killProcess(android.os.Process.myPid());   //获取PID 
	//			System.exit(0); 
	//		}
	//	};

	@Override
	public void onContactsLoadSuccess() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onContactsLoadFailed() {
		// TODO Auto-generated method stub

	}

}