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

package com.android.providers.telephony;

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Process;
import android.provider.Telephony;
import android.text.TextUtils;
import android.text.TextUtils.StringSplitter;
import android.util.Log;

import com.android.internal.telephony.SmsApplication;

import android.location.CountryDetector;

/**
 * Helpers
 */
public class ProviderUtil {
    private final static String TAG = "SmsProvider";

    /**
     * Check if a caller of the provider has restricted access,
     * i.e. being non-system, non-phone, non-default SMS app
     *
     * @param context the context to use
     * @param packageName the caller package name
     * @param uid the caller uid
     * @return true if the caller is not system, or phone or default sms app, false otherwise
     */
    public static boolean isAccessRestricted(Context context, String packageName, int uid) {
        return (uid != Process.SYSTEM_UID
                && uid != Process.PHONE_UID
                && !SmsApplication.isDefaultSmsApplication(context, packageName))
                && !packageName.equalsIgnoreCase("com.android.mms")//modify by ligy
                && !packageName.equalsIgnoreCase("com.monster.interception");//modify by tangyisen
    }

    /**
     * Whether should set CREATOR for an insertion
     *
     * @param values The content of the message
     * @param uid The caller UID of the insertion
     * @return true if we should set CREATOR, false otherwise
     */
    public static boolean shouldSetCreator(ContentValues values, int uid) {
        return (uid != Process.SYSTEM_UID && uid != Process.PHONE_UID) ||
                (!values.containsKey(Telephony.Sms.CREATOR) &&
                        !values.containsKey(Telephony.Mms.CREATOR));
    }

    /**
     * Whether should remove CREATOR for an update
     *
     * @param values The content of the message
     * @param uid The caller UID of the update
     * @return true if we should remove CREATOR, false otherwise
     */
    public static boolean shouldRemoveCreator(ContentValues values, int uid) {
        return (uid != Process.SYSTEM_UID && uid != Process.PHONE_UID) &&
                (values.containsKey(Telephony.Sms.CREATOR) ||
                        values.containsKey(Telephony.Mms.CREATOR));
    }

    /**
     * Notify the default SMS app of an SMS/MMS provider change if the change is being made
     * by a package other than the default SMS app itself.
     *
     * @param uri The uri the provider change applies to
     * @param callingPackage The package name of the provider caller
     * @param Context
     */
    public static void notifyIfNotDefaultSmsApp(final Uri uri, final String callingPackage,
            final Context context) {
        if (TextUtils.equals(callingPackage, Telephony.Sms.getDefaultSmsPackage(context))) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.d(TAG, "notifyIfNotDefaultSmsApp - called from default sms app");
            }
            return;
        }
        // Direct the intent to only the default SMS app, and only if the SMS app has a receiver
        // for the intent.
        ComponentName componentName =
                SmsApplication.getDefaultExternalTelephonyProviderChangedApplication(context, true);
        if (componentName == null) {
            return;     // the default sms app doesn't have a receiver for this intent
        }

        final Intent intent =
                new Intent(Telephony.Sms.Intents.ACTION_EXTERNAL_PROVIDER_CHANGE);
        intent.setFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.setComponent(componentName);
        if (uri != null) {
            intent.setData(uri);
        }
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.d(TAG, "notifyIfNotDefaultSmsApp - called from " + callingPackage + ", notifying");
        }
        context.sendBroadcast(intent);
    }

    public static Context getCredentialEncryptedContext(Context context) {
        if (context.isCredentialProtectedStorage()) {
            return context;
        }
        return context.createCredentialProtectedStorageContext();
    }

    public static Context getDeviceEncryptedContext(Context context) {
        if (context.isDeviceProtectedStorage()) {
            return context;
        }
        return context.createDeviceProtectedStorageContext();
    }

    //begin tangyisen
    public static Uri BLACK_URI = Uri.parse("content://com.android.contacts/black");

    public static boolean validateIsRejectAddress(Context context, String address) {
        ContentResolver cr = context.getContentResolver();
        Cursor cursor = null;
        /*String number= null;
        if (getCurrentCountryIso(context).equals("CN")
            && address.startsWith("+86")) {
            number = address.substring(3);
        } else {
            number = address;
        }*/
        try {
            cursor = cr.query(BLACK_URI, /*new String[]{"_id"}*/null, /*"number='"+number+"'"*/getPhoneNumberEqualString(address) + " and reject != 1", null, null);
        } catch (Exception e) {
            return false;
        }
        if(cursor == null || cursor.getCount() == 0) {
            return false;
        }
        return true;
    }

    public static final String getCurrentCountryIso(Context context) {
        CountryDetector detector = (CountryDetector) context
                .getSystemService(Context.COUNTRY_DETECTOR);
        return detector.detectCountry().getCountryIso();
    }

    public static final String ACTION_NOTIFY_SMS = "com.monster.interception.ACTION_NOTIFY_SMS";
    public static void sendRejectNotification(Context context) {
        Intent intent = new Intent();
        intent.setAction(ACTION_NOTIFY_SMS);
        context.sendBroadcast(intent);
    }

    public static String getPhoneNumberEqualString(String number) {
        return " PHONE_NUMBERS_EQUAL(number, \"" + number + "\", 0) ";
    }
    //end tangyisen
}
