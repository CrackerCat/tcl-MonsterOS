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

package com.android.services.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.GsmCdmaCallTracker;
import com.android.internal.telephony.GsmCdmaConnection;
import com.android.internal.telephony.GsmCdmaPhone;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyComponentFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.cdma.CdmaCallWaitingNotification;
import com.android.internal.telephony.imsphone.ImsExternalCallTracker;
import com.android.internal.telephony.imsphone.ImsExternalConnection;
import com.android.phone.PhoneUtils;

import com.google.common.base.Preconditions;

import java.util.Objects;

//[SOLUTION]-Add-BEGIN by TCTNB.(JiangLong Pan), 08/12/2016, SOLUTION-2706813
//[Call]Call forwarding message is not displayed on MS
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.services.telephony.TelephonyGlobals;

import com.android.phone.R;
//[SOLUTION]-Add-END by TCTNB.(JiangLong Pan)
import android.provider.Settings;
import android.media.AudioManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Listens to incoming-call events from the associated phone object and notifies Telecom upon each
 * occurence. One instance of these exists for each of the telephony-based call services.
 */
final class PstnIncomingCallNotifier {
    /** New ringing connection event code. */
    private static final int EVENT_NEW_RINGING_CONNECTION = 100;
    private static final int EVENT_CDMA_CALL_WAITING = 101;
    private static final int EVENT_UNKNOWN_CONNECTION = 102;

//[SOLUTION]-Add-BEGIN by TCTNB.(JiangLong Pan), 08/12/2016, SOLUTION-2706813
//[Call]Call forwarding message is not displayed on MS
    public static final int EVENT_SUPP_SERVICE_NOTIFY = 103;

    private String[] mSubName = {"SIM1", "SIM2", "SIM3"};
    private boolean isCFNotify;
    private SuppServiceNotification suppSvcNotification;
    private String mDisplayName;
//[SOLUTION]-Add-END by TCTNB.(JiangLong Pan)

    /** The phone object to listen to. */
    private final Phone mPhone;

    //ADD-BEGIN by Dingyi  2016/08/24 SOLUTION 2473957
    private final Context mContext;
    private CallManager mCM ;
    private Sensor mAccelerometerSensor;
    private SensorManager mSensorManager;
    private SensorEventListener mSensorListener;
    private boolean mIsgetZ = false;
    private boolean mIsFaceDown = true;
    private boolean isTurnover = false;
    private double mCurrentDegree = 0;
    public static final int PHONE_STATE_CHANGED = 104;
    /* incoming call slince message ID */
    private static final int INCOMING_CALL_SLINCE_MESSAGE = 200;
    /* Delay incoming call slince time */
    private static final int DELAY_INCOMING_CALL_SLINCE_TIME = 200;
    private static final double DELTA_DEGREE = 2.5;

    //ADD-END by Dingyi  2016/08/24 SOLUTION 2473957
    /**
     * Used to listen to events from {@link #mPhone}.
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case EVENT_NEW_RINGING_CONNECTION:
                    handleNewRingingConnection((AsyncResult) msg.obj);
                    break;
                case EVENT_CDMA_CALL_WAITING:
                    handleCdmaCallWaiting((AsyncResult) msg.obj);
                    break;
                case EVENT_UNKNOWN_CONNECTION:
                    handleNewUnknownConnection((AsyncResult) msg.obj);
                    break;
//[SOLUTION]-Add-BEGIN by TCTNB.(JiangLong Pan), 08/12/2016, SOLUTION-2706813
//[Call]Call forwarding message is not displayed on MS
                case EVENT_SUPP_SERVICE_NOTIFY:
                    Log.i(this, "EVENT_SUPP_SERVICE_NOTIFY");
                    if (msg.obj != null && ((AsyncResult) msg.obj).result != null) {
                        suppSvcNotification = (SuppServiceNotification) ((AsyncResult) msg.obj).result;
                        isCFNotify = true;
                    }
                    break;
//[SOLUTION]-Add-END by TCTNB.(JiangLong Pan)
                //ADD-BEGIN by Dingyi  2016/08/24 SOLUTION 2473957
                case PHONE_STATE_CHANGED:
                    doTurnOverToMuteIfNecessary();
                    break;
                //ADD-END by Dingyi  2016/08/24 SOLUTION 2473957
                default:
                    break;
            }
        }
    };

    /**
     * Persists the specified parameters and starts listening to phone events.
     *
     * @param phone The phone object for listening to incoming calls.
     */
    PstnIncomingCallNotifier(Phone phone) {
        Preconditions.checkNotNull(phone);

        mPhone = phone;

        //ADD-BEGIN by Dingyi  2016/08/24 SOLUTION 2473957
        mCM=CallManager.getInstance();
        mContext=mPhone.getContext();
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mAccelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        //ADD-END by Dingyi  2016/08/24 SOLUTION 2473957
        registerForNotifications();
    }

    void teardown() {
        unregisterForNotifications();
    }

    /**
     * Register for notifications from the base phone.
     */
    private void registerForNotifications() {
        if (mPhone != null) {
            Log.i(this, "Registering: %s", mPhone);
            mPhone.registerForNewRingingConnection(mHandler, EVENT_NEW_RINGING_CONNECTION, null);
            mPhone.registerForCallWaiting(mHandler, EVENT_CDMA_CALL_WAITING, null);
            mPhone.registerForUnknownConnection(mHandler, EVENT_UNKNOWN_CONNECTION, null);
//[SOLUTION]-Add-BEGIN by TCTNB.(JiangLong Pan), 08/12/2016, SOLUTION-2706813
//[Call]Call forwarding message is not displayed on MS
            mPhone.registerForSuppServiceNotification(mHandler, EVENT_SUPP_SERVICE_NOTIFY, null);
//[SOLUTION]-Add-END by TCTNB.(JiangLong Pan)
            //ADD-BEGIN by Dingyi  2016/08/24 SOLUTION 2473957
            mPhone.registerForPreciseCallStateChanged(mHandler, PHONE_STATE_CHANGED, null);
            //ADD-END by Dingyi  2016/08/24 SOLUTION 2473957
        }
    }

    private void unregisterForNotifications() {
        if (mPhone != null) {
            Log.i(this, "Unregistering: %s", mPhone);
            mPhone.unregisterForNewRingingConnection(mHandler);
            mPhone.unregisterForCallWaiting(mHandler);
            mPhone.unregisterForUnknownConnection(mHandler);
//[SOLUTION]-Add-BEGIN by TCTNB.(JiangLong Pan), 08/12/2016, SOLUTION-2706813
//[Call]Call forwarding message is not displayed on MS
            mPhone.unregisterForSuppServiceNotification(mHandler);
//[SOLUTION]-Add-END by TCTNB.(JiangLong Pan)
            //ADD-BEGIN by Dingyi  2016/08/24 SOLUTION 2473957
            mPhone.unregisterForPreciseCallStateChanged(mHandler);
            //ADD-END by Dingyi  2016/08/24 SOLUTION 2473957
        }
    }

    /**
     * Verifies the incoming call and triggers sending the incoming-call intent to Telecom.
     *
     * @param asyncResult The result object from the new ringing event.
     */
    private void handleNewRingingConnection(AsyncResult asyncResult) {
        Log.d(this, "handleNewRingingConnection");
        Connection connection = (Connection) asyncResult.result;

//[SOLUTION]-Add-BEGIN by TCTNB.(JiangLong Pan), 08/12/2016, SOLUTION-2706813
//[Call]Call forwarding message is not displayed on MS
        Phone phone = connection.getCall().getPhone();
        if (isCFNotify && suppSvcNotification != null) {
            updateServiceNotify(suppSvcNotification,phone);
            isCFNotify = false;
        }
//[SOLUTION]-Add-END by TCTNB.(JiangLong Pan)

        if (connection != null) {
            Call call = connection.getCall();

            // Final verification of the ringing state before sending the intent to Telecom.
            if (call != null && call.getState().isRinging()) {
                sendIncomingCallIntent(connection);
            }
        }
    }

    private void handleCdmaCallWaiting(AsyncResult asyncResult) {
        Log.d(this, "handleCdmaCallWaiting");
        CdmaCallWaitingNotification ccwi = (CdmaCallWaitingNotification) asyncResult.result;
        Call call = mPhone.getRingingCall();
        if (call.getState() == Call.State.WAITING) {
            Connection connection = call.getLatestConnection();
            if (connection != null) {
                String number = connection.getAddress();
                if (number != null && Objects.equals(number, ccwi.number)) {
                    sendIncomingCallIntent(connection);
                }
            }
        }
    }

    private void handleNewUnknownConnection(AsyncResult asyncResult) {
        Log.i(this, "handleNewUnknownConnection");
        if (!(asyncResult.result instanceof Connection)) {
            Log.w(this, "handleNewUnknownConnection called with non-Connection object");
            return;
        }
        Connection connection = (Connection) asyncResult.result;
        if (connection != null) {
            // Because there is a handler between telephony and here, it causes this action to be
            // asynchronous which means that the call can switch to DISCONNECTED by the time it gets
            // to this code. Check here to ensure we are not adding a disconnected or IDLE call.
            Call.State state = connection.getState();
            if (state == Call.State.DISCONNECTED || state == Call.State.IDLE) {
                Log.i(this, "Skipping new unknown connection because it is idle. " + connection);
                return;
            }

            Call call = connection.getCall();
            if (call != null && call.getState().isAlive()) {
                addNewUnknownCall(connection);
            }
        }
    }

    private void addNewUnknownCall(Connection connection) {
        Log.i(this, "addNewUnknownCall, connection is: %s", connection);

        if (!maybeSwapAnyWithUnknownConnection(connection)) {
            Log.i(this, "determined new connection is: %s", connection);
            Bundle extras = new Bundle();
            if (connection.getNumberPresentation() == TelecomManager.PRESENTATION_ALLOWED &&
                    !TextUtils.isEmpty(connection.getAddress())) {
                Uri uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, connection.getAddress(), null);
                extras.putParcelable(TelecomManager.EXTRA_UNKNOWN_CALL_HANDLE, uri);
            }
            // ImsExternalConnections are keyed by a unique mCallId; include this as an extra on
            // the call to addNewUknownCall in Telecom.  This way when the request comes back to the
            // TelephonyConnectionService, we will be able to determine which unknown connection is
            // being added.
            if (connection instanceof ImsExternalConnection) {
                ImsExternalConnection externalConnection = (ImsExternalConnection) connection;
                extras.putInt(ImsExternalCallTracker.EXTRA_IMS_EXTERNAL_CALL_ID,
                        externalConnection.getCallId());
            }

            // Specifies the time the call was added. This is used by the dialer for analytics.
            extras.putLong(TelecomManager.EXTRA_CALL_CREATED_TIME_MILLIS,
                    SystemClock.elapsedRealtime());

            PhoneAccountHandle handle = findCorrectPhoneAccountHandle();
            if (handle == null) {
                try {
                    connection.hangup();
                } catch (CallStateException e) {
                    // connection already disconnected. Do nothing
                }
            } else {
                TelecomManager.from(mPhone.getContext()).addNewUnknownCall(handle, extras);
            }
        } else {
            Log.i(this, "swapped an old connection, new one is: %s", connection);
        }
    }

    /**
     * Sends the incoming call intent to telecom.
     */
    private void sendIncomingCallIntent(Connection connection) {
        Bundle extras = new Bundle();
        if (connection.getNumberPresentation() == TelecomManager.PRESENTATION_ALLOWED &&
                !TextUtils.isEmpty(connection.getAddress())) {
            Uri uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, connection.getAddress(), null);
            extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, uri);
        }

        // Specifies the time the call was added. This is used by the dialer for analytics.
        extras.putLong(TelecomManager.EXTRA_CALL_CREATED_TIME_MILLIS,
                SystemClock.elapsedRealtime());

        PhoneAccountHandle handle = findCorrectPhoneAccountHandle();
        if (handle == null) {
            try {
                connection.hangup();
            } catch (CallStateException e) {
                // connection already disconnected. Do nothing
            }
        } else {
            TelecomManager.from(mPhone.getContext()).addNewIncomingCall(handle, extras);
        }
    }

    /**
     * Returns the PhoneAccount associated with this {@code PstnIncomingCallNotifier}'s phone. On a
     * device with No SIM or in airplane mode, it can return an Emergency-only PhoneAccount. If no
     * PhoneAccount is registered with telecom, return null.
     * @return A valid PhoneAccountHandle that is registered to Telecom or null if there is none
     * registered.
     */
    private PhoneAccountHandle findCorrectPhoneAccountHandle() {
        TelecomAccountRegistry telecomAccountRegistry = TelecomAccountRegistry.getInstance(null);
        // Check to see if a the SIM PhoneAccountHandle Exists for the Call.
        PhoneAccountHandle handle = PhoneUtils.makePstnPhoneAccountHandle(mPhone);
        if (telecomAccountRegistry.hasAccountEntryForPhoneAccount(handle)) {
            return handle;
        }
        // The PhoneAccountHandle does not match any PhoneAccount registered in Telecom.
        // This is only known to happen if there is no SIM card in the device and the device
        // receives an MT call while in ECM. Use the Emergency PhoneAccount to receive the account
        // if it exists.
        PhoneAccountHandle emergencyHandle =
                PhoneUtils.makePstnPhoneAccountHandleWithPrefix(mPhone, "", true);
        if(telecomAccountRegistry.hasAccountEntryForPhoneAccount(emergencyHandle)) {
            Log.i(this, "Receiving MT call in ECM. Using Emergency PhoneAccount Instead.");
            return emergencyHandle;
        }
        Log.w(this, "PhoneAccount not found.");
        return null;
    }

    /**
     * Define cait.Connection := com.android.internal.telephony.Connection
     *
     * Given a previously unknown cait.Connection, check to see if it's likely a replacement for
     * another cait.Connnection we already know about. If it is, then we silently swap it out
     * underneath within the relevant {@link TelephonyConnection}, using
     * {@link TelephonyConnection#setOriginalConnection(Connection)}, and return {@code true}.
     * Otherwise, we return {@code false}.
     */
    private boolean maybeSwapAnyWithUnknownConnection(Connection unknown) {
        if (!unknown.isIncoming()) {
            TelecomAccountRegistry registry = TelecomAccountRegistry.getInstance(null);
            if (registry != null) {
                TelephonyConnectionService service = registry.getTelephonyConnectionService();
                if (service != null) {
                    for (android.telecom.Connection telephonyConnection : service
                            .getAllConnections()) {
                        if (telephonyConnection instanceof TelephonyConnection) {
                            if (maybeSwapWithUnknownConnection(
                                    (TelephonyConnection) telephonyConnection,
                                    unknown)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean maybeSwapWithUnknownConnection(
            TelephonyConnection telephonyConnection,
            Connection unknown) {
        Connection original = telephonyConnection.getOriginalConnection();
        if (original != null && !original.isIncoming()
                && Objects.equals(original.getAddress(), unknown.getAddress())) {
            telephonyConnection.setOriginalConnection(unknown);
            return true;
        }
        return false;
    }

//[SOLUTION]-Add-BEGIN by TCTNB.(JiangLong Pan), 08/12/2016, SOLUTION-2706813
//[Call]Call forwarding message is not displayed on MS
    private void updateServiceNotify(SuppServiceNotification suppSvcNotification,Phone mPhone) {
        String callForwardText = getSuppSvcNotificationText(suppSvcNotification);
        if (TelephonyManager.getDefault().getPhoneCount() > 1 && mPhone != null) {
            SubscriptionInfo sub = SubscriptionManager.from(mPhone.getContext())
                    .getActiveSubscriptionInfoForSimSlotIndex(mPhone.getPhoneId());
            String displayName = (sub != null) ? sub.getDisplayName().toString() : mSubName[mPhone.getPhoneId()];
            mDisplayName = displayName + ":" + callForwardText;
        } else {
            mDisplayName = callForwardText;
        }

        if (callForwardText != null && !callForwardText.isEmpty()) {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
            Toast.makeText(mPhone.getContext(), mDisplayName, Toast.LENGTH_LONG).show();
        }
    }

    private String getSuppSvcNotificationText(SuppServiceNotification suppSvcNotification) {
        final int SUPP_SERV_NOTIFICATION_TYPE_MT = 1;
        String callForwardTxt = "";
        if (suppSvcNotification != null) {
            switch (suppSvcNotification.notificationType) {
                // The Notification is for MT call
                case SUPP_SERV_NOTIFICATION_TYPE_MT:
                   callForwardTxt = getMtSsNotificationText(suppSvcNotification.code);
                    break;
                default:
                    break;
            }
        }
       return callForwardTxt;
    }

    private String getMtSsNotificationText(int code) {
        String callForwardTxt = "";
        switch (code) {
            case SuppServiceNotification.MT_CODE_FORWARDED_CALL:
                // This message is displayed on C when the incoming
                // call is forwarded from B
                callForwardTxt = mPhone.getContext().getString(R.string.card_title_forwarded_MTcall);
                break;
            default:
                break;
        }
        return callForwardTxt;
    }
//[SOLUTION]-Add-END by TCTNB.(JiangLong Pan)
    // ADD-BEGIN by Dingyi 2016/08/24 SOLUTION 2473957
    /**
     * turn over to mute the incoming call if necessary
     */
    public void doTurnOverToMuteIfNecessary() {
        Log.d(this, "doTurnOverToMuteIfNecessary()...");
        AudioManager mAudioManager = (AudioManager) mContext
                .getSystemService(Context.AUDIO_SERVICE);
        int shouldMute = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.TCT_TURN_OVER_TO_MUTE, 0);
        int zenMode = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.ZEN_MODE, 0);
        if (shouldMute == 0 || zenMode == Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS
                || mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT) {
            if (mCM.getFirstActiveRingingCall().getState() == Call.State.INCOMING) {
                Log.d(this, "No need TurnOverToMute...");
                return;
            }
        }

        if (mCM.getFirstActiveRingingCall().getState() == Call.State.INCOMING) {
            registerTurnOverToMute();
        } else {
            unregisterTurnOverToMute();
        }
    }

    /**
     * register Sensor.TYPE_ACCELEROMETER
     */
    private void registerTurnOverToMute() {
        Log.d(this, "registerTurnOverToMute()...");
        if (mAccelerometerSensor != null) {
            if (mSensorListener == null) {
                mIsgetZ = false;
                mSensorListener = new SensorListener();
                mSensorManager.registerListener(mSensorListener, mAccelerometerSensor,
                        SensorManager.SENSOR_DELAY_UI);
            }
        }
    }

    /**
     * unregister Sensor.TYPE_ACCELEROMETER
     */
    private void unregisterTurnOverToMute() {
        Log.d(this, "unregisterTurnOverToMute()...");
        if (mSensorListener != null) {
            mSensorManager.unregisterListener(mSensorListener);
            mSensorListener = null;
        }
    }

    /**
     * SensorListener,use to determine whether to silence the ringtone
     */
    private class SensorListener implements SensorEventListener {
        private static final int ANTI_SHAKE = 6;
        private int anti_shake = 0;

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        public void onSensorChanged(SensorEvent event) {
            double g = Math.sqrt(Math.pow(event.values[0], 2) + Math.pow(event.values[1], 2)
                    + Math.pow(event.values[2], 2));
            double dAz = Math.toDegrees(Math.acos(event.values[2] / g));
            mCurrentDegree = dAz;
            if (!mIsgetZ) {
                if (dAz > 135 && dAz <= 180) {
                    mIsFaceDown = true;
                    anti_shake = 0;
                } else if (dAz >= 0 && dAz <= 60) {
                    if (++anti_shake > ANTI_SHAKE) {
                        mIsFaceDown = false;
                        mIsgetZ = true;
                    }
                } else {
                    anti_shake = 0;
                }
            }
            if (!mIsFaceDown && dAz > 135 && dAz <= 180) {
                if (mCM.getFirstActiveRingingCall().getState() == Call.State.INCOMING) {
                    sendIncomingCallSilenceMessage(dAz);
                }
            }
        }
    }

    private void sendIncomingCallSilenceMessage(double degree) {
        if (mcallHandler != null) {
            mcallHandler.sendMessageDelayed(
                    mcallHandler.obtainMessage(INCOMING_CALL_SLINCE_MESSAGE, degree),
                    DELAY_INCOMING_CALL_SLINCE_TIME);
        }
    }

    /**
     * A handler for message.
     *
     * @param null
     * @return null
     */
    private Handler mcallHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            super.handleMessage(msg);

            switch (msg.what) {
                case INCOMING_CALL_SLINCE_MESSAGE:
                    handleIncomingCallSlinceMessage(msg);
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * Handle incoming call slince message.
     *
     * @param Message msg
     * @return null
     */
    private void handleIncomingCallSlinceMessage(Message msg) {
        double lastDegree = (Double) msg.obj;
        if (Math.abs(lastDegree - mCurrentDegree) <= DELTA_DEGREE) {
            if (mCM.getFirstActiveRingingCall().getState() == Call.State.INCOMING) {
                mcallHandler.removeMessages(INCOMING_CALL_SLINCE_MESSAGE);
                TelecomManager telecomManager = (TelecomManager) mContext
                        .getSystemService(Context.TELECOM_SERVICE);
                if (telecomManager != null) {
                    telecomManager.silenceRinger();
                }
            }
        }
    }
    // ADD-BEGIN by Dingyi 2016/08/24 SOLUTION 2473957
}