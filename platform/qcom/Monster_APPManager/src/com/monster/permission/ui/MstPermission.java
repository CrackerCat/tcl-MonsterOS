package com.monster.permission.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.android.internal.telephony.ISms;
import com.android.internal.telephony.SmsUsageMonitor;
import com.monster.appmanager.R;
import com.monster.appmanager.Utils;

import android.Manifest;
import android.app.Activity;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.service.vr.VrListenerService;
import android.text.TextUtils;
import android.util.ArraySet;
import android.widget.SpinnerPopupDialog;

public class MstPermission {
	// sensitive permission
	public static final String LOCATION_GROUP = Manifest.permission_group.LOCATION;
	public static final String STORAGE_GROUP = Manifest.permission_group.STORAGE;
	public static final String CALENDAR_GROUP = Manifest.permission_group.CALENDAR;
	public static final String PHONE_GROUP = Manifest.permission_group.PHONE;
	public static final String CAMERA_GROUP = Manifest.permission_group.CAMERA;
	public static final String SMS_GROUP = Manifest.permission_group.SMS;
	public static final String SENSORS_GROUP = Manifest.permission_group.SENSORS;
	public static final String CONTACT_GROUP = Manifest.permission_group.CONTACTS;
	public static final String MICROPHONE_GROUP = Manifest.permission_group.MICROPHONE;
	public static final String SYSTEM_ALERT_WINDOW_GROUP = Manifest.permission.SYSTEM_ALERT_WINDOW;
	public static final String INSTALL_SHORTCUT_GROUP = Manifest.permission.INSTALL_SHORTCUT;
	
	// senior permission
    public static final String DEVICE_MANAGER_GROUP = "android.permission.ADMIN_DEVICE";
    public static final String VR_SERVICE_GROUP = "android.permission.VR_SERVICE";
	public static final String WRITE_SETTINGS_GROUP = Manifest.permission.WRITE_SETTINGS;
	public static final String POST_NOTIFICATION_GROUP = "android.permission.POST_NOTIFICATION";
	public static final String PREMIUM_SMS_GROUP = "android.permission.PREMIUM_SMS";
	public static final String USAGE_STATS_GROUP = Manifest.permission.PACKAGE_USAGE_STATS;

	
	private static final Uri GN_PERM_URI = Uri.parse("content://com.monster.settings.PermissionProvider/permissions");
	public static final int ALLOW_MODE = 1;
	public static final int ASK_MODE = 0;
	public static final int DISABLE_MODE = -1;
	public static final int UNKNOWN_MODE = - 100;

	private Context mContext;
	private PackageManager mPm;
	private Map<String, MstAppGroup> mAppGroupMap = new HashMap<>();
	private Map<String, MstPermGroup> mPermGroupMap = new HashMap<>();
	private OnPermChangeListener mListener;
    private ISms mSmsManager;

	public MstPermission(Context context) {
		mContext = context;
		mPm = context.getPackageManager();
		mSmsManager = ISms.Stub.asInterface(ServiceManager.getService("isms"));
	}
	
	public void refresh(List<String> packageNameList, List<String> permissionList) {
		mAppGroupMap.clear();
		mPermGroupMap.clear();
		
		String selection = null;
		if(packageNameList != null || permissionList != null) {
			selection = buildSelection(packageNameList, permissionList);
		}

		String[] projection = new String[] { "permission", "packagename", "status", "permissiongroup" };
		try {
			Cursor cursor = mContext.getContentResolver().query(GN_PERM_URI, projection, selection, null, "status");
			if (cursor != null) {
				String packageName = null;
				int status = UNKNOWN_MODE;
				String groupName = null;
				cursor.moveToFirst();
				MstPermEntry entry;
				MstAppGroup appGroup;
				MstPermGroup permGroup;
				ApplicationInfo appInfo;
				boolean hasPremiumSms = false;
				
				ArraySet<String> adminDeviceList = null;
				while (!cursor.isAfterLast()) {
					if(!hasPremiumSms) {
						packageName = cursor.getString(1);
						status = cursor.getInt(2);
						groupName = cursor.getString(3);
					} else {
						hasPremiumSms = false;
						groupName = PREMIUM_SMS_GROUP;
					}
					
					appInfo = mstGetApplicationInfo(mContext, packageName);
					if(appInfo == null || Utils.isThirdPartyAppDisabled(appInfo)) {
						cursor.moveToNext();
						continue;
					}
					
					if(!sAllPermissionsSequenceMap.containsKey(groupName)) {
						cursor.moveToNext();
						continue;
					}

					entry = new MstPermEntry(packageName, null, groupName, status);
					if(groupName.contains("ADMIN_DEVICE")) {
						if(adminDeviceList == null) {
							adminDeviceList = loadAdminDevicePackages(mContext);
						}
						boolean isChanged = false;
						if(adminDeviceList.contains(packageName) && (status < ASK_MODE)) {
							entry.setStatus(ASK_MODE);
							isChanged = true;
						} else if(!adminDeviceList.contains(packageName) && (status >= ASK_MODE)) {
							entry.setStatus(DISABLE_MODE);
							isChanged = true;
						}
						if(isChanged) {
							MstPermission.mstUpdatePermissionStatusToDb(mContext, entry);
						}
					}
					appGroup = mAppGroupMap.get(packageName);
					if (appGroup == null) {
						appGroup = new MstAppGroup(packageName);
						appGroup.setAppName(mstGetAppNameLabel(mContext, packageName, appInfo));
						mAppGroupMap.put(packageName, appGroup);
					}
					appGroup.add(entry);

					permGroup = mPermGroupMap.get(groupName);
					if (permGroup == null) {
						permGroup = new MstPermGroup(groupName);
						permGroup.setLabel(mstGetPkgPermLabel(groupName));
						permGroup.setDrawable(loadCustomPermissionIcon(mContext, groupName));
						mPermGroupMap.put(groupName, permGroup);
					}
					permGroup.addApp(appGroup);
					
					if(entry.status == ALLOW_MODE) {
						permGroup.setGrantedCount(permGroup.getGrantedCount() + 1);
						appGroup.setGrantedCount(appGroup.getGrantedCount() + 1);
					} else if(entry.status == ASK_MODE) {
						permGroup.setAskCount(permGroup.getAskCount() + 1);
						appGroup.setAskCount(appGroup.getAskCount() + 1);
					} else {
						permGroup.setDisabledCount(permGroup.getDisabledCount() + 1);
						appGroup.setDisabledCount(appGroup.getDisabledCount() + 1);
					}
					
					if(groupName.equals(SMS_GROUP)) {
						status = getPremiumSmsStatus(packageName);
						if(status != UNKNOWN_MODE) {
							hasPremiumSms = true;
							continue;
						}
					}

					cursor.moveToNext();
				}
				cursor.close();
			}
		} catch (Exception e) {
			if (mListener != null) {
				((Activity)mContext).runOnUiThread(new Runnable() {
					public void run() {
						mListener.onPermRefreshError();
					}
				});
			}
			return;
		}

		if (mListener != null) {
			((Activity)mContext).runOnUiThread(new Runnable() {
				public void run() {
					mListener.onPermRefreshComplete();
				}
			});
		}
	}

	public void refreshAsync(final List<String> packageNameList, final List<String> permissionList) {
		// todo
		new Thread(new Runnable() {
			@Override
			public void run() {
				refresh(packageNameList, permissionList);
			}
		}).start();
	}
	
	private String buildSelection(List<String> packageNameList, List<String> permissionList) {
		StringBuilder sb = new StringBuilder();
		
		if(packageNameList != null && packageNameList.size() >0) {
			int i = 0;
			sb.append("(");
			for (String packageName : packageNameList) {
				if(i > 0) {
					sb.append(" or ");
				}
				sb.append("packagename = '" );
				sb.append(packageName);
				sb.append("'");
				i++;
			}
			sb.append(")");
		}
		
		if(permissionList != null && permissionList.size() >0) {
			int i = 0;
			if(packageNameList != null && packageNameList.size() > 0) {
				sb.append(" and (");
			} else {
				sb.append(" (");
			}
			for (String permission : permissionList) {
				if(i > 0) {
					sb.append(" or ");
				}
				sb.append("permissiongroup  = '" );
				sb.append(permission);
				sb.append("'");
				i++;
			}
			sb.append(")");
		}
		
		return sb.toString();
	}
	
	public MstPermEntry getPermEntry(String packageName, String groupName) {
		MstPermEntry entry = null;
		if(mAppGroupMap != null) {
			MstAppGroup appGroup = mAppGroupMap.get(packageName);
			if(appGroup != null) {
				entry = appGroup.get(groupName);
			}
		}
		
		return entry;
	}
	
	public List<Map.Entry<String, MstPermGroup>> getSortedPermGroupList() {
		List<Map.Entry<String, MstPermGroup>> mappingList = 
				new ArrayList<Map.Entry<String, MstPermGroup>>(mPermGroupMap.entrySet());
		Collections.sort(mappingList, new Comparator<Map.Entry<String, MstPermGroup>>(){
			public int compare(Map.Entry<String, MstPermGroup> mappingLeft, Map.Entry<String, MstPermGroup> mappingRight){
				return mappingLeft.getValue().compareTo(mappingRight.getValue());
			}
		}); 
		return mappingList;
	}
	
	public List<Map.Entry<String, MstPermEntry>> getSortedPermEntryList(Map<String, MstPermEntry> permEntryMap) {
		List<Map.Entry<String, MstPermEntry>> mappingList = 
				new ArrayList<Map.Entry<String, MstPermEntry>>(permEntryMap.entrySet());
		Collections.sort(mappingList, new Comparator<Map.Entry<String, MstPermEntry>>(){
			public int compare(Map.Entry<String, MstPermEntry> mappingLeft, Map.Entry<String, MstPermEntry> mappingRight){
				return mappingLeft.getValue().compareTo(mappingRight.getValue());
			}
		}); 
		return mappingList;
	}

	public String mstGetPkgPermLabel(final String groupName) {
		if (groupName.contains("INSTALL_SHORTCUT")) {
			return mContext.getResources().getString(R.string.perm_label_install_shortcut);
		}
		if (groupName.contains("SYSTEM_ALERT_WINDOW")) {
			return mContext.getResources().getString(R.string.perm_label_system_alert_window);
		}
		if (groupName.contains("PACKAGE_USAGE_STATS")) {
			return mContext.getResources().getString(R.string.perm_label_package_usage_state);
		}
		if (groupName.contains("POST_NOTIFICATION")) {
			return mContext.getResources().getString(R.string.perm_label_notifcation);
		}
		if (groupName.contains("PREMIUM_SMS")) {
			return mContext.getResources().getString(R.string.perm_label_premium_sms);
		}
		if (groupName.contains("VR_SERVICE")) {
			return mContext.getResources().getString(R.string.perm_label_vr_helper_service);
		}
		if (groupName.contains("ADMIN_DEVICE")) {
			return mContext.getResources().getString(R.string.perm_label_device_manager);
		}
		if (groupName.contains("WRITE_SETTINGS")) {
			return mContext.getResources().getString(R.string.perm_label_write_system_setting);
		}

		return mstGetPermGroupName(groupName);
	}

	private String mstGetPermGroupName(String groupName) {
		String name = null;
		try {
			PermissionGroupInfo groupInfo = mPm.getPermissionGroupInfo(groupName, 0);
			name = groupInfo.loadLabel(mPm) + "";
		} catch (Exception e) {
		}
		if (name == null) {
			name = groupName;
		}
		return name;
	}
	
	private ApplicationInfo mstGetApplicationInfo(Context context, String pkgName) {
		ApplicationInfo appInfo = null;
		try {
			appInfo = mPm.getApplicationInfo(pkgName, 0);
		} catch (NameNotFoundException e) {
		}
		return appInfo;
	}

	private String mstGetAppNameLabel(Context context, String pkgName, ApplicationInfo appInfo) {
		String label = "";
		try {
			if(appInfo == null) {
				appInfo = mPm.getApplicationInfo(pkgName, 0);
			}
			String temp = (String) mPm.getApplicationLabel(appInfo);
			label = (TextUtils.isEmpty(temp)) ? appInfo.processName : temp;
		} catch (NameNotFoundException e) {
		}
		return label;
	}
	
	private static Drawable mstGetAppIcon(PackageManager mPm, Context context, String pkgName) {
		Drawable icon = null;
		try {
			ApplicationInfo appInfo = mPm.getApplicationInfo(pkgName, 0);
			icon = mPm.getApplicationIcon(appInfo);
		} catch (NameNotFoundException e) {
		}
		return icon;
	}
	
	public static Drawable loadCustomPermissionIcon(Context context, String name) {
    	Drawable icon = null;
    	int res = getCustomPermissionIcon(name);
    	if(res > 0) {
    		icon = context.getResources().getDrawable(res);
    	}
    	return icon;
    }

    public static int getCustomPermissionIcon(String name) {
    	int res = 0;
    	if(name.equals(Manifest.permission_group.LOCATION)) {
    		res = R.drawable.permission_location;
    	} else if(name.equals(Manifest.permission_group.STORAGE)) {
    		res = R.drawable.permission_storage;
    	} else if(name.equals(Manifest.permission.SYSTEM_ALERT_WINDOW)) {
    		res = R.drawable.permission_alert_window;
    	} else if(name.equals(Manifest.permission.INSTALL_SHORTCUT)) {
    		res = R.drawable.permission_shortcut;
    	} else if(name.equals(Manifest.permission_group.CALENDAR)) {
    		res = R.drawable.permission_calendar;
    	} else if(name.equals(Manifest.permission_group.PHONE)) {
    		res = R.drawable.permission_phone;
    	} else if(name.equals(Manifest.permission_group.SMS)) {
    		res = R.drawable.permission_sms;
    	} else if(name.equals("android.permission-group.WRITE_SETTINGS")) {
    		res = R.drawable.permission_modify_setting;
    	} else if(name.equals(Manifest.permission.WRITE_SETTINGS)) {
    		res = R.drawable.permission_modify_setting;
    	} else if(name.equals(Manifest.permission_group.CAMERA)) {
    		res = R.drawable.permission_camera;
    	} else if(name.equals(Manifest.permission_group.CONTACTS)) {
    		res = R.drawable.permission_contact;
    	} else if(name.equals(Manifest.permission_group.MICROPHONE)) {
    		res = R.drawable.permission_record;
    	} else if(name.equals(Manifest.permission_group.SENSORS)) {
    		res = R.drawable.permission_sensor;
    	} else {
    		res = R.drawable.permission_modify_setting;
    	}
    	return res;
    }

	public OnPermChangeListener getmListener() {
		return mListener;
	}

	public void setmListener(OnPermChangeListener mListener) {
		this.mListener = mListener;
	}

	public Map<String, MstPermGroup> getPermGroupMap() {
		return mPermGroupMap;
	}
	
	public Map<String, MstAppGroup> getAppGroupMap() {
		return mAppGroupMap;
	}
	
    public static boolean isInsertedPermStatus(Context context, MstPermEntry entry) {
        boolean status = false;
        Cursor c = null;
        try {
            c = context.getContentResolver().query(GN_PERM_URI, new String[] {"status"},
                    "  packagename = ? and permissiongroup =? ", new String[] {entry.getPackageName(), entry.getGroupName()}, null);
            if (c != null && c.getCount() > 0) {
                status = true;
            }
        } finally {
            if (c != null && !c.isClosed()) {
                c.close();
                c = null;
            }
        }
        return status;
    }
    
    public static void mstUpdatePermissionStatusToDb(Context context, MstPermEntry entry) {
    	try {
    		ContentValues cv = new ContentValues();
    		cv.put("status", entry.getStatus());
    		if(isInsertedPermStatus(context, entry)) {
    			context.getContentResolver().update(GN_PERM_URI, cv, " packagename = ? and permissiongroup =?",
    					new String[] {entry.getPackageName(), entry.getGroupName()});
    		}
    	} catch (Exception e){
    		e.printStackTrace();
    	}
    }
    
    public static int mstGetNotDiabledPermissionCount(Context context, String groupName) {
    	int result = 0;
        Cursor c = null;
        try {
            c = context.getContentResolver().query(GN_PERM_URI, new String[] {"status"},
                    " permissiongroup =? and status = 1", new String[] {groupName}, null);
            if (c != null) {
            	result = c.getCount();
            }
        } finally {
            if (c != null && !c.isClosed()) {
                c.close();
                c = null;
            }
        }
        return result;
    }
    
    public static void mstUpdatePermissionStatusToDb(Context context, String groupName, int status) {
    	try {
    		ContentValues cv = new ContentValues();
    		cv.put("status", status);
    		context.getContentResolver().update(GN_PERM_URI, cv, " permissiongroup =?", new String[] {groupName});
    	} catch (Exception e){
    		e.printStackTrace();
    	}
    }
    
    public static MstPermEntry getPermissionEntry(Context context, String packageName, String groupName) {
    	MstPermEntry entry = null;
        Cursor c = null;
        try {
        	String[] projection = new String[] { "permission", "packagename", "status", "permissiongroup" };
            c = context.getContentResolver().query(GN_PERM_URI, projection,
                    "  packagename = ? and permissiongroup =? ", new String[] {packageName, groupName}, null);
            if (c != null && c.getCount() > 0) {
            	if(c.moveToFirst()) {
            		entry = new MstPermEntry(packageName, null, groupName, c.getInt(2));
            	}
            }
        } finally {
            if (c != null && !c.isClosed()) {
                c.close();
                c = null;
            }
        }
        return entry;
    }

	public static class MstPermEntry implements Comparable<MstPermEntry> {
		private String packageName;
		private String permissionName;
		private String groupName;
		private int status;

		public MstPermEntry(String packageName, String permissionName, String groupName, int status) {
			super();
			this.packageName = packageName;
			this.permissionName = permissionName;
			this.groupName = groupName;
			this.status = status;
		}

		public String getPackageName() {
			return packageName;
		}

		public void setPackageName(String packageName) {
			this.packageName = packageName;
		}

		public String getPermissionName() {
			return permissionName;
		}

		public void setPermissionName(String permissionName) {
			this.permissionName = permissionName;
		}

		public String getGroupName() {
			return groupName;
		}

		public void setGroupName(String groupName) {
			this.groupName = groupName;
		}

		public int getStatus() {
			return status;
		}

		public void setStatus(int status) {
			this.status = status;
		}

		@Override
		public int compareTo(MstPermEntry another) {
			return sAllPermissionsSequenceMap.get(getGroupName()) -
					sAllPermissionsSequenceMap.get(another.getGroupName());
		}
	}

	public static class MstAppGroup {
		private String packageName;
		private String appName;
		private Map<String, MstPermEntry> permEntryMap = new HashMap<>();
		private int grantedCount;
		private int askCount;
		private int disabledCount;

		public MstAppGroup(String packageName) {
			super();
			this.packageName = packageName;
		}

		public void add(MstPermEntry entry) {
			permEntryMap.put(entry.getGroupName(), entry);
		}
		
		public Map<String, MstPermEntry> getPermEntryMap() {
			return permEntryMap;
		}
		
		public MstPermEntry get(String permName) {
			return permEntryMap.get(permName);
		}
		
		public int size() {
			return permEntryMap.size();
		}

		public String getPackageName() {
			return packageName;
		}

		public String getAppName() {
			return appName;
		}

		public void setAppName(String appName) {
			this.appName = appName;
		}
		
		public Drawable loadAppIcon(Context context) {
			return mstGetAppIcon(context.getPackageManager(), context, packageName);
		}
		
		public int getGrantedCount() {
			return grantedCount;
		}

		public void setGrantedCount(int grantedCount) {
			this.grantedCount = grantedCount;
		}
		
		public int getAskCount() {
			return askCount;
		}

		public void setAskCount(int askCount) {
			this.askCount = askCount;
		}

		public int getDisabledCount() {
			return disabledCount;
		}

		public void setDisabledCount(int disabledCount) {
			this.disabledCount = disabledCount;
		}
	}

	public static class MstPermGroup implements Comparable<MstPermGroup>{
		private String permGroupName;
		private String label;
		private Drawable drawable;
		private int grantedCount;
		private int askCount;
		private int disabledCount;
		private Map<String, MstAppGroup> appGroupMap = new HashMap<>();

		public MstPermGroup(String permGroupName) {
			super();
			this.permGroupName = permGroupName;
		}

		public String getPermGroupName() {
			return permGroupName;
		}

		public void addApp(MstAppGroup appGroup) {
			appGroupMap.put(appGroup.getPackageName(), appGroup);
		}

		public String getLabel() {
			return label;
		}

		public void setLabel(String label) {
			this.label = label;
		}

		public Drawable getDrawable() {
			return drawable;
		}

		public void setDrawable(Drawable drawable) {
			this.drawable = drawable;
		}

		public int getGrantedCount() {
			return grantedCount;
		}

		public void setGrantedCount(int grantedCount) {
			this.grantedCount = grantedCount;
		}
		
		public Map<String, MstAppGroup> getAppGroupMap() {
			return appGroupMap;
		}

		public int getAskCount() {
			return askCount;
		}

		public void setAskCount(int askCount) {
			this.askCount = askCount;
		}

		public int getDisabledCount() {
			return disabledCount;
		}

		public void setDisabledCount(int disabledCount) {
			this.disabledCount = disabledCount;
		}
		
		public boolean isSeniorPermission() {
			return sSeniorPermissions.contains(permGroupName);
		}

		@Override
		public int compareTo(MstPermGroup another) {
			return sAllPermissionsSequenceMap.get(getPermGroupName()) -
					sAllPermissionsSequenceMap.get(another.getPermGroupName());
		}
	}

	public static interface OnPermChangeListener {
		public void onPermRefreshComplete();

		public void onPermRefreshError();
	}
	
	public static Map<String, Integer> sAllPermissionsSequenceMap = new HashMap<>();
	static {
		sAllPermissionsSequenceMap.put(LOCATION_GROUP, 1);
		sAllPermissionsSequenceMap.put(STORAGE_GROUP, 2);
		sAllPermissionsSequenceMap.put(CALENDAR_GROUP, 3);
		sAllPermissionsSequenceMap.put(PHONE_GROUP, 4);
		sAllPermissionsSequenceMap.put(CAMERA_GROUP, 5);
		sAllPermissionsSequenceMap.put(SMS_GROUP, 6);
		sAllPermissionsSequenceMap.put(SENSORS_GROUP, 7);
		sAllPermissionsSequenceMap.put(CONTACT_GROUP, 8);
		sAllPermissionsSequenceMap.put(MICROPHONE_GROUP, 9);
		sAllPermissionsSequenceMap.put(SYSTEM_ALERT_WINDOW_GROUP, 10);
		sAllPermissionsSequenceMap.put(INSTALL_SHORTCUT_GROUP, 11);
		
		sAllPermissionsSequenceMap.put(DEVICE_MANAGER_GROUP, 12);
		sAllPermissionsSequenceMap.put(VR_SERVICE_GROUP, 13);
		sAllPermissionsSequenceMap.put(WRITE_SETTINGS_GROUP, 14);
//		sAllPermissionsSequenceMap.put(POST_NOTIFICATION_GROUP, 15);
		sAllPermissionsSequenceMap.put(PREMIUM_SMS_GROUP, 16);
		sAllPermissionsSequenceMap.put(USAGE_STATS_GROUP, 17);
		
	}
	
	public static HashSet<String> sSeniorPermissions = new HashSet<String>();
    static {
        sSeniorPermissions.add(DEVICE_MANAGER_GROUP);
        sSeniorPermissions.add(VR_SERVICE_GROUP);
        sSeniorPermissions .add(WRITE_SETTINGS_GROUP);
//        sSeniorPermissions .add(POST_NOTIFICATION_GROUP);
        sSeniorPermissions .add(PREMIUM_SMS_GROUP);
        sSeniorPermissions .add(USAGE_STATS_GROUP);
    }
    
    public static class PermOpSelectItem {
    	private String[] permissionTypes;
    	private MstPermEntry permEntry;
    	private SpinnerPopupDialog dialog;
    	private Context context;
    	private DialogInterface.OnClickListener listener;
		private Map<String, MstAppGroup> appGroupMap = null;
    	
    	public PermOpSelectItem(Context context) {
    		this.context = context.getApplicationContext();
    		permissionTypes = context.getResources().getStringArray(R.array.permition_types);
    	}
    	
    	public void showPermOpSelectDialog(Context context) {
    		boolean[] setValues = new boolean[permissionTypes.length];
    		setValues[getCurrnetModeLabelIndex()] = true;
    		dialog = new SpinnerPopupDialog(context);
    		dialog.setMultipleChoiceItems(permissionTypes, setValues,
    				new DialogInterface.OnMultiChoiceClickListener() {
    			@Override
    			public void onClick(DialogInterface dialog, int which,
    					boolean isChecked) {
    				if(which != getCurrnetModeLabelIndex()) {
    					setPermStatus(which);
    					if(listener != null) {
    						listener.onClick(dialog, which);
    					}
    					updateSettingIfNeeded();
    				}
    				closePermOpSelectDialog();
    			}
    		});
    		dialog.setNegativeButton(new DialogInterface.OnClickListener() {
    			@Override
    			public void onClick(DialogInterface dialog, int which) {
    				closePermOpSelectDialog();
    			}
    		});
    		dialog.show();
    	}

    	public void closePermOpSelectDialog() {
    		if(dialog != null && dialog.isShowing()) {
    			dialog.dismiss();
    		}
    	}
    	
    	private void updateMode() {
    		if(!PREMIUM_SMS_GROUP.equals(permEntry.getGroupName())) {
    			MstPermission.mstUpdatePermissionStatusToDb(context, getPermEntry());
    		}
    	}
    	
    	private int getCurrnetModeLabelIndex() {
    		int index = 0;
    		String label = getCurrenModeLabel();
    		for (int i = 0; i < permissionTypes.length; i++) {
				String curType = permissionTypes[i];
				if(curType == label) {
					index = i;
					break;
				}
			}
    		return index;
    	}
    	
    	public String getCurrenModeLabel() {
    		String label = null;
    		int mode = getPermEntry().getStatus();
    		if(mode == MstPermission.ALLOW_MODE) {
    			label = permissionTypes[0];
    		} else if(mode == MstPermission.ASK_MODE) {
    			label = permissionTypes[1];
    		} else {
    			label = permissionTypes[2];
    		}

    		return label;
    	}
    	
    	private int getCurrentMode(int permTypeIndex){
    		int mode = MstPermission.UNKNOWN_MODE;
    		if(permTypeIndex == 0) {
    			mode = MstPermission.ALLOW_MODE;
    		} else if(permTypeIndex == 1) {
    			mode = MstPermission.ASK_MODE;
    		} else if(permTypeIndex == 2) {
    			mode = MstPermission.DISABLE_MODE;
    		}
    		return mode;
    	}
    	
    	private void setPermStatus(int permTypeIndex) {
    		int mode = getCurrentMode(permTypeIndex);
    		if(mode != MstPermission.UNKNOWN_MODE) {
    			getPermEntry().setStatus(mode);
    			updateMode();
    		}
    	}

		public MstPermEntry getPermEntry() {
			return permEntry;
		}

		public void setPermEntry(MstPermEntry permEntry) {
			this.permEntry = permEntry;
		}

		public void setListener(DialogInterface.OnClickListener listener) {
			this.listener = listener;
		}
		
		private void updateSettingIfNeeded() {
			if(VR_SERVICE_GROUP.equals(permEntry.getGroupName())) {
				updateVrServiceIfNeeded();
			} else if(DEVICE_MANAGER_GROUP.equals(permEntry.getGroupName())) {
				updateAdminDeviceIfNeeded();
			} else if(PREMIUM_SMS_GROUP.equals(permEntry.getGroupName())) {
				updatePremiumSmsState(permEntry.getStatus());
			}
		}
		
		private void updateVrServiceIfNeeded() {
			if(getAppGroupMap() != null && getAppGroupMap().size() >0) {
				String intentAction;
				String servicePermission;
				String settingName;
				
				intentAction = VrListenerService.SERVICE_INTERFACE;
				servicePermission = Manifest.permission.BIND_VR_LISTENER_SERVICE; 
				settingName = Settings.Secure.ENABLED_VR_LISTENERS;
				new SettingUpdater().execute(intentAction, servicePermission, settingName);
			}
		}
		
		private void updateAdminDeviceIfNeeded() {
			boolean isActive = permEntry.getStatus() >= ASK_MODE;
			new AdminDeviceUpdater().execute(permEntry.getPackageName(), String.valueOf(isActive));
		}
		    	
    	private void updatePremiumSmsState(int status) {
    		int state = SmsUsageMonitor.PREMIUM_SMS_PERMISSION_UNKNOWN;
    		switch (status) {
			case ALLOW_MODE:
				state = SmsUsageMonitor.PREMIUM_SMS_PERMISSION_ALWAYS_ALLOW;
				break;
			case ASK_MODE:
				state = SmsUsageMonitor.PREMIUM_SMS_PERMISSION_ASK_USER;
				break;
			case DISABLE_MODE:
				state = SmsUsageMonitor.PREMIUM_SMS_PERMISSION_NEVER_ALLOW;
				break;
			}
    		
    		if(state != SmsUsageMonitor.PREMIUM_SMS_PERMISSION_UNKNOWN) {
    			ISms smsManager = ISms.Stub.asInterface(ServiceManager.getService("isms"));
    			try {
					smsManager.setPremiumSmsPermission(permEntry.getPackageName(), state);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
    		}
    	}
		
		private class AdminDeviceUpdater extends AsyncTask<String, Void, Void> {
			@Override
			protected Void doInBackground(String... params) {
				String packageName = params[0];
				boolean isActive = Boolean.valueOf(params[1]);
				ArraySet<ComponentName> adminDeviceList = loadDeviceAdminBroadcastReceiversForProfile(context);
				if(adminDeviceList.size() > 0) {
					for (ComponentName componentName : adminDeviceList) {
						if(packageName.equals(componentName.getPackageName())) {
							setAdminActive(context, componentName, isActive);
							break;
						}
					}
				}
				return null;
			}
		}
		
		private class SettingUpdater extends AsyncTask<String, Void, Void> {
			@Override
			protected Void doInBackground(String... params) {
				saveAllEnabledToSettings(params[0], params[1], params[2]);
				return null;
			}
		}
		
		private void saveAllEnabledToSettings(String intentAction, String servicePermission, String settingName) {
			if(getAppGroupMap() != null) {
				PackageManager pm = context.getPackageManager();
				List<ResolveInfo> installedServices = pm.queryIntentServices(
						new Intent(intentAction),
						PackageManager.GET_SERVICES | PackageManager.GET_META_DATA);

				StringBuilder sb = null;
				MstAppGroup appGroup;
				for (int i = 0, count = installedServices.size(); i < count; i++) {
					ResolveInfo resolveInfo = installedServices.get(i);
					ServiceInfo info = resolveInfo.serviceInfo;

					if (!servicePermission.equals(info.permission)) {
						continue;
					}
					
					appGroup = getAppGroupMap().get(info.packageName);
					if(appGroup != null) {
						MstPermEntry permEntry = appGroup.get(VR_SERVICE_GROUP);
						if(permEntry != null && permEntry.getStatus() >= ASK_MODE) {
				            ComponentName cn = new ComponentName(info.packageName, info.name);
							if (sb == null) {
								sb = new StringBuilder();
							} else {
								sb.append(':');
							}
							sb.append(cn.flattenToString());
						}
					}
				}
				
				Settings.Secure.putString(context.getContentResolver(), settingName,
						sb != null ? sb.toString() : "");
			}
		}

		public Map<String, MstAppGroup> getAppGroupMap() {
			return appGroupMap;
		}

		public void setAppGroupMap(Map<String, MstAppGroup> appGroupMap) {
			this.appGroupMap = appGroupMap;
		}
    }
    
    private static ArraySet<String> loadAdminDevicePackages(Context context) {    	
        ArraySet<String> installed = new ArraySet<String>();
    	DevicePolicyManager mDPM = (DevicePolicyManager)context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        for (ComponentName component : mDPM.getActiveAdmins()) {
            installed.add(component.getPackageName());
        }
        return installed;
    }
    
    private static ArraySet<ComponentName> loadDeviceAdminBroadcastReceiversForProfile(Context context) {
        ArraySet<ComponentName> installed = new ArraySet<>();
        final PackageManager pm = context.getPackageManager();
        List<ResolveInfo> enabledForProfile = pm.queryBroadcastReceivers(
                new Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED),
                PackageManager.GET_META_DATA | PackageManager.GET_DISABLED_UNTIL_USED_COMPONENTS);
        if (enabledForProfile == null) {
            enabledForProfile = Collections.emptyList();
        }
        final int n = enabledForProfile.size();
        for (int i = 0; i < n; ++i) {
            ResolveInfo resolveInfo = enabledForProfile.get(i);
            if((resolveInfo.activityInfo.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0) {
            	continue;
            }

            ComponentName cn = new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);
            installed.add(cn);
        }
        return installed;
    }
    
    private static void setAdminActive(Context context, ComponentName cn, boolean isActive) {
    	DevicePolicyManager dpm = (DevicePolicyManager)context.getSystemService(Context.DEVICE_POLICY_SERVICE);
    	try {
    		if(isActive) {
    			dpm.setActiveAdmin(cn, true);
    		} else {
    			dpm.removeActiveAdmin(cn);
    		}
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
    
    private int getPremiumSmsStatus(String pkg) {
    	int status = UNKNOWN_MODE;

    	int smsState = getSmsState(pkg);
    	if(smsState != SmsUsageMonitor.PREMIUM_SMS_PERMISSION_UNKNOWN) {
    		switch (smsState) {
    		case SmsUsageMonitor.PREMIUM_SMS_PERMISSION_ALWAYS_ALLOW:
    			status = ALLOW_MODE;
    			break;
    		case SmsUsageMonitor.PREMIUM_SMS_PERMISSION_ASK_USER:
    			status = ASK_MODE;
    			break;
    		case SmsUsageMonitor.PREMIUM_SMS_PERMISSION_NEVER_ALLOW:
    			status = DISABLE_MODE;
    			break;
    		}
    	}

    	return status;
    }
    
    private int getSmsState(String pkg) {
        try {
            return mSmsManager.getPremiumSmsPermission(pkg);
        } catch (Exception e) {
            return SmsUsageMonitor.PREMIUM_SMS_PERMISSION_UNKNOWN;
        }
    }
}
