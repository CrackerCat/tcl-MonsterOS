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

package com.monster.appmanager.utils;

import android.Manifest;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.Loader;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.ArraySet;
import android.util.Log;

import com.monster.appmanager.R;
import com.monster.appmanager.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class PermissionGroups implements LoaderCallbacks<List<PermissionGroup>> {
    private final ArrayList<PermissionGroup> mGroups = new ArrayList<>();
    private final Context mContext;
    private final LoaderManager mLoaderManager;
    private final PermissionsGroupsChangeCallback mCallback;

    public interface PermissionsGroupsChangeCallback {
        public void onPermissionGroupsChanged();
    }

    public PermissionGroups(Context context, LoaderManager loaderManager,
            PermissionsGroupsChangeCallback callback) {
        mContext = context;
        mLoaderManager = loaderManager;
        mCallback = callback;
    }

    @Override
    public Loader<List<PermissionGroup>> onCreateLoader(int id, Bundle args) {
        return new PermissionsLoader(mContext);
    }

    @Override
    public void onLoadFinished(Loader<List<PermissionGroup>> loader,
            List<PermissionGroup> groups) {
        if (mGroups.equals(groups)) {
            return;
        }
        mGroups.clear();
        mGroups.addAll(groups);
        mCallback.onPermissionGroupsChanged();
    }

    @Override
    public void onLoaderReset(Loader<List<PermissionGroup>> loader) {
        mGroups.clear();
        mCallback.onPermissionGroupsChanged();
    }

    public void refresh() {
        mLoaderManager.restartLoader(0, null, this);
        mLoaderManager.getLoader(0).forceLoad();
    }

    public List<PermissionGroup> getGroups() {
        return mGroups;
    }

    public PermissionGroup getGroup(String name) {
        for (PermissionGroup group : mGroups) {
            if (group.getName().equals(name)) {
                return group;
            }
        }
        return null;
    }

    private static final class PermissionsLoader extends AsyncTaskLoader<List<PermissionGroup>> {

        public PermissionsLoader(Context context) {
            super(context);
        }

        @Override
        public List<PermissionGroup> loadInBackground() {
            List<PermissionGroup> groups = new ArrayList<>();
            Set<String> seenPermissions = new ArraySet<>();


            PackageManager packageManager = getContext().getPackageManager();
            List<PermissionGroupInfo> groupInfos = packageManager.getAllPermissionGroups(0);

            for (PermissionGroupInfo groupInfo : groupInfos) {
                // Mare sure we respond to cancellation.
                if (isLoadInBackgroundCanceled()) {
                    return Collections.emptyList();
                }

                // Get the permissions in this group.
                final List<PermissionInfo> groupPermissions;
                try {
                    groupPermissions = packageManager.queryPermissionsByGroup(groupInfo.name, 0);
                } catch (PackageManager.NameNotFoundException e) {
                    continue;
                }

                boolean hasRuntimePermissions = false;

                // Cache seen permissions and see if group has runtime permissions.
                for (PermissionInfo groupPermission : groupPermissions) {
                    seenPermissions.add(groupPermission.name);
                    if (groupPermission.protectionLevel == PermissionInfo.PROTECTION_DANGEROUS
                            && (groupPermission.flags & PermissionInfo.FLAG_INSTALLED) != 0
                            && (groupPermission.flags & PermissionInfo.FLAG_REMOVED) == 0) {
                        hasRuntimePermissions = true;
                    }
                }

                // No runtime permissions - not interesting for us.
                if (!hasRuntimePermissions) {
                    continue;
                }

                CharSequence label = loadItemInfoLabel(groupInfo);
                Drawable icon = loadItemInfoIcon(groupInfo);

                // Create the group and add to the list.
                PermissionGroup group = new PermissionGroup(groupInfo.name,
                        groupInfo.packageName, label, icon);
                groups.add(group);
            }


            // Make sure we add groups for lone runtime permissions.
            List<PackageInfo> installedPackages = getContext().getPackageManager()
                    .getInstalledPackages(PackageManager.GET_PERMISSIONS);


            // We will filter out permissions that no package requests.
            Set<String> requestedPermissions = new ArraySet<>();
            for (PackageInfo installedPackage : installedPackages) {
                if (installedPackage.requestedPermissions == null) {
                    continue;
                }
                for (String requestedPermission : installedPackage.requestedPermissions) {
                    requestedPermissions.add(requestedPermission);
                }
            }

            for (PackageInfo installedPackage : installedPackages) {
                if (installedPackage.permissions == null) {
                    continue;
                }

                for (PermissionInfo permissionInfo : installedPackage.permissions) {
                    // If we have handled this permission, no more work to do.
                    if (!seenPermissions.add(permissionInfo.name)) {
                        continue;
                    }

                    // We care only about installed runtime permissions.
                    if ((permissionInfo.protectionLevel != PermissionInfo.PROTECTION_DANGEROUS
                            || (permissionInfo.flags & PermissionInfo.FLAG_INSTALLED) == 0)  && !Utils.isModernPermissionGroupSelf(permissionInfo.group)) {
                        continue;
                    }

                    // If no app uses this permission,
                    if (!requestedPermissions.contains(permissionInfo.name)) {
                        continue;
                    }

                    CharSequence label = loadItemInfoLabel(permissionInfo);
                    Drawable icon = loadItemInfoIcon(permissionInfo);

                    // Create the group and add to the list.
                    PermissionGroup group = new PermissionGroup(permissionInfo.name,
                            permissionInfo.packageName, label, icon);
                    groups.add(group);
                }
            }

            Collections.sort(groups);
            return groups;
        }

        private CharSequence loadItemInfoLabel(PackageItemInfo itemInfo) {
            CharSequence label = itemInfo.loadLabel(getContext().getPackageManager());
            if (label == null) {
                label = itemInfo.name;
            }
            return label;
        }
        
        private Drawable loadItemInfoIcon(PackageItemInfo itemInfo) {
        	return PermissionGroups.loadItemInfoIcon(getContext(), itemInfo);
        }

    }
    
    public static Drawable loadItemInfoIcon(Context context, PackageItemInfo itemInfo) {
    	Drawable icon = null;
    	icon = loadCustomPermissionIcon(context, itemInfo.name);
    	if(icon != null) {
    		return icon;
    	}

    	if (itemInfo.icon > 0) {
    		icon = Utils.loadDrawable(context.getPackageManager(),
    				itemInfo.packageName, itemInfo.icon);
    	} else {
    		icon = context.getDrawable(R.drawable.ic_perm_device_info);
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
    	} /*else if(name.equals(Manifest.permission_group.SYSTEM_ALERT_WINDOW)) {
    		res = R.drawable.permission_alert_window;
    	} else if(name.equals(Manifest.permission_group.INSTALL_SHORTCUT)) {
    		res = R.drawable.permission_shortcut;
    	} */else if(name.equals(Manifest.permission_group.CALENDAR)) {
    		res = R.drawable.permission_calendar;
    	} else if(name.equals(Manifest.permission_group.PHONE)) {
    		res = R.drawable.permission_phone;
    	} else if(name.equals(Manifest.permission_group.SMS)) {
    		res = R.drawable.permission_sms;
    	} else if(name.equals("android.permission-group.WRITE_SETTINGS")) {
    		res = R.drawable.permission_modify_setting;
    	} else if(name.equals(Manifest.permission_group.CAMERA)) {
    		res = R.drawable.permission_camera;
    	} else if(name.equals(Manifest.permission_group.CONTACTS)) {
    		res = R.drawable.permission_contact;
    	} else if(name.equals(Manifest.permission_group.MICROPHONE)) {
    		res = R.drawable.permission_record;
    	} else if(name.equals(Manifest.permission_group.SENSORS)) {
    		res = R.drawable.permission_sensor;
    	}
    	return res;
    }
}
