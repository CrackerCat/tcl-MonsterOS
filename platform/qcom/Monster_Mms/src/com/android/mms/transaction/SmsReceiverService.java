/*
 * Copyright (C) 2010-2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
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

package com.android.mms.transaction;

import static android.content.Intent.ACTION_BOOT_COMPLETED;
import static android.provider.Telephony.Sms.Intents.SMS_DELIVER_ACTION;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import mst.provider.Telephony.Sms;
import mst.provider.Telephony.Sms.Inbox;
import mst.provider.Telephony.Sms.Intents;
import mst.provider.Telephony.Sms.Outbox;
import android.telephony.CellBroadcastMessage;
import android.telephony.ServiceState;
import android.telephony.SmsCbMessage;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.mms.LogTag;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.Conversation;
import com.android.mms.ui.ClassZeroActivity;
import com.android.mms.ui.MessageUtils;
import com.android.mms.util.Recycler;
import com.android.mms.util.SendingProgressTokenManager;
//import com.android.mms.widget.MmsWidgetProvider;
import com.google.android.mms.MmsException;
//ligy add begin
import com.android.mms.MmsApp;
//ligy add end
//xiaoyuan add begin
import cn.com.xy.sms.sdk.ui.popu.util.XySdkUtil;
import cn.com.xy.sms.sdk.ui.notification.SmartSmsNotificationManager;
import cn.com.xy.sms.util.SdkCallBack;
import cn.com.xy.sms.util.ParseSmsMessage;
import org.json.JSONObject;
import org.json.JSONException;
import cn.com.xy.sms.sdk.util.StringUtils;
//xiaoyuan add end

/**
 * This service essentially plays the role of a "worker thread", allowing us to store
 * incoming messages to the database, update notifications, etc. without blocking the
 * main thread that SmsReceiver runs on.
 */
public class SmsReceiverService extends Service {

    private static final String TAG = LogTag.APP + "/SmsReceiverService";
    private static final String TAG_XY = "XIAOYUAN";
    private static final boolean DEBUG = LogTag.DEBUG;

    private final static String SMS_PRIORITY = "priority";

    private ServiceHandler mServiceHandler;
    private Looper mServiceLooper;
    private static Map<Integer, Boolean> mSending = new HashMap<Integer, Boolean>();

    public static final String MESSAGE_SENT_ACTION =
        "com.android.mms.transaction.MESSAGE_SENT";

    // Indicates next message can be picked up and sent out.
    public static final String EXTRA_MESSAGE_SENT_SEND_NEXT ="SendNextMsg";

    public static final String ACTION_SEND_MESSAGE =
            "com.android.mms.transaction.SEND_MESSAGE";
    public static final String ACTION_SEND_INACTIVE_MESSAGE =
            "com.android.mms.transaction.SEND_INACTIVE_MESSAGE";

    // This must match the column IDs below.
    private static final String[] SEND_PROJECTION = new String[] {
        Sms._ID,        //0
        Sms.THREAD_ID,  //1
        Sms.ADDRESS,    //2
        Sms.BODY,       //3
        Sms.STATUS,     //4
        Sms.SUBSCRIPTION_ID,   //5
        SMS_PRIORITY,   //6
    };

    static final String CB_AREA_INFO_RECEIVED_ACTION =
            "android.cellbroadcastreceiver.CB_AREA_INFO_RECEIVED";

    /* Cell Broadcast for channel 50 */
    static final int CB_CHANNEL_50 = 50;

    /* Cell Broadcast for channel 60 */
    static final int CB_CHANNEL_60 = 60;

    public Handler mToastHandler = new Handler();
    public Handler mNotificationHandler = new Handler();

    // This must match SEND_PROJECTION.
    private static final int SEND_COLUMN_ID         = 0;
    private static final int SEND_COLUMN_THREAD_ID  = 1;
    private static final int SEND_COLUMN_ADDRESS    = 2;
    private static final int SEND_COLUMN_BODY       = 3;
    private static final int SEND_COLUMN_STATUS     = 4;
    private static final int SEND_COLUMN_SUB_ID   = 5;
    private static final int SEND_COLUMN_PRIORITY   = 6;

    @Override
    public void onCreate() {
        // Temporarily removed for this duplicate message track down.
        if (DEBUG) {
            Log.v(TAG, "==onCreate==");
        }

        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.
        HandlerThread thread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        mServiceLooper = thread.getLooper();
        if (DEBUG) Log.v(TAG, "onCreate(), >>>new ServiceHandler()");
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) {
            Log.v(TAG, "==onStartCommand==");
        }
        if (!MmsConfig.isSmsEnabled(this)) {
            Log.d(TAG, "SmsReceiverService: is not the default sms app");
            // NOTE: We MUST not call stopSelf() directly, since we need to
            // make sure the wake lock acquired by AlertReceiver is released.
            SmsReceiver.finishStartingService(SmsReceiverService.this, startId);
            return Service.START_NOT_STICKY;
        }
        // Temporarily removed for this duplicate message track down.

        int resultCode = intent != null ? intent.getIntExtra("result", 0) : 0;

        if (resultCode != 0) {
            if (DEBUG) Log.v(TAG, "onStart: #" + startId + " resultCode: " + resultCode +
                    " = " + translateResultCode(resultCode));
        }

        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);
        return Service.START_NOT_STICKY;
    }

    private static String translateResultCode(int resultCode) {
        switch (resultCode) {
            case Activity.RESULT_OK:
                return "Activity.RESULT_OK";
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                return "SmsManager.RESULT_ERROR_GENERIC_FAILURE";
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                return "SmsManager.RESULT_ERROR_RADIO_OFF";
            case SmsManager.RESULT_ERROR_NULL_PDU:
                return "SmsManager.RESULT_ERROR_NULL_PDU";
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                return "SmsManager.RESULT_ERROR_NO_SERVICE";
            case SmsManager.RESULT_ERROR_LIMIT_EXCEEDED:
                return "SmsManager.RESULT_ERROR_LIMIT_EXCEEDED";
            case SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE:
                return "SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE";
            default:
                return "Unknown error code";
        }
    }

    @Override
    public void onDestroy() {
        // Temporarily removed for this duplicate message track down.
        if (DEBUG) {
            Log.v(TAG, "==onDestroy==");
        }
        mServiceLooper.quit();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        /**
         * Handle incoming transaction requests.
         * The incoming requests are initiated by the MMSC Server or by the MMS Client itself.
         */
        @Override
        public void handleMessage(Message msg) {
            int serviceId = msg.arg1;
            Intent intent = (Intent)msg.obj;
            if (DEBUG) {
                Log.v(TAG, "handleMessage serviceId: " + serviceId + " intent: " + intent);
            }
            if (intent != null && MmsConfig.isSmsEnabled(getApplicationContext())) {
                String action = intent.getAction();

                int error = intent.getIntExtra("errorCode", 0);

                if (DEBUG) {
                    Log.v(TAG, "handleMessage action: " + action + " error: " + error);
                }

                if (MESSAGE_SENT_ACTION.equals(action)) {
                    handleSmsSent(intent, error);
                } else if (SMS_DELIVER_ACTION.equals(action)) {
                    handleSmsReceived(intent, error);
                } else if (CB_AREA_INFO_RECEIVED_ACTION.equals(action)) {
                    handleCbSmsReceived(intent, error);
                } else if (ACTION_BOOT_COMPLETED.equals(action)) {
                    handleBootCompleted();
                } else if (TelephonyIntents.ACTION_SERVICE_STATE_CHANGED.equals(action)) {
                    handleServiceStateChanged(intent);
                } else if (ACTION_SEND_MESSAGE.endsWith(action)) {
                    handleSendMessage(intent);
                } else if (ACTION_SEND_INACTIVE_MESSAGE.equals(action)) {
                    handleSendInactiveMessage();
                }
            }
            // NOTE: We MUST not call stopSelf() directly, since we need to
            // make sure the wake lock acquired by AlertReceiver is released.
            SmsReceiver.finishStartingService(SmsReceiverService.this, serviceId);
        }
    }

    private void handleServiceStateChanged(Intent intent) {
        // If service just returned, start sending out the queued messages
        ServiceState serviceState = ServiceState.newFromBundle(intent.getExtras());
        int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY, -1);
        // if service state is IN_SERVICE & current subscription is same as
        // preferred SMS subscription.i.e.as set under SIM Settings, then
        // sendFirstQueuedMessage.
        if (serviceState.getState() == ServiceState.STATE_IN_SERVICE) {
            sendFirstQueuedMessage(subId);
        }
    }

    private void handleSendMessage(Intent intent) {
        int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                SubscriptionManager.getDefaultSmsSubscriptionId());
        if (mSending.get(subId) == null) {
           mSending.put(subId, false);
        }
        if (!mSending.get(subId)) {
            sendFirstQueuedMessage(subId);
        }
    }

    private void handleSendInactiveMessage() {
        // Inactive messages includes all messages in outbox and queued box.
        moveOutboxMessagesToQueuedBox();
        // Process queued messages on all SUB's
        List<SubscriptionInfo> subInfoList =
                SubscriptionManager.from(getApplicationContext()).getActiveSubscriptionInfoList();
        if(subInfoList != null) {
            for (SubscriptionInfo info : subInfoList)
            {
                int subId = info.getSubscriptionId();
                if (mSending.get(subId) == null) {
                    mSending.put(subId, false);
                }
                if (!mSending.get(subId)) {
                    sendFirstQueuedMessage(subId);
                }
            }
        }
    }

    // Send first queued message of the given subscription
    public synchronized void sendFirstQueuedMessage(int subscription) {
        boolean success = true;
        boolean isExpectMore = false;
        // get all the queued messages from the database
        final Uri uri = Uri.parse("content://sms/queued");
        ContentResolver resolver = getContentResolver();
        String where = "sub_id=?";
        String[] whereArgs = new String[] {Integer.toString(subscription)};
        Cursor c = SqliteWrapper.query(this, resolver, uri,
                        SEND_PROJECTION, where, whereArgs, "date ASC"); // date ASC so we send out
                                                                       // in same order the user
                                                                       // tried to send messages.
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    String msgText = c.getString(SEND_COLUMN_BODY);
                    String address = c.getString(SEND_COLUMN_ADDRESS);
                    int threadId = c.getInt(SEND_COLUMN_THREAD_ID);
                    int status = c.getInt(SEND_COLUMN_STATUS);

                    int msgId = c.getInt(SEND_COLUMN_ID);
                    int subId = c.getInt(SEND_COLUMN_SUB_ID);
                    int priority = c.getInt(SEND_COLUMN_PRIORITY);
                    Uri msgUri = ContentUris.withAppendedId(Sms.CONTENT_URI, msgId);
                    // Get the information of is there any messages are pending to process.
                    // If yes, send this inforamtion to framework to control the link and send all
                    // messages on same link based on the support in framework
                    if (c.moveToNext()) {
                        isExpectMore = true;
                    }
                    SmsMessageSender sender = new SmsSingleRecipientSender(this,
                            address, msgText, threadId, status == Sms.STATUS_PENDING,
                            msgUri, subId, isExpectMore);

                    if(priority != -1){
                        ((SmsSingleRecipientSender)sender).setPriority(priority);
                    }

                    if (LogTag.DEBUG_SEND ||
                            LogTag.VERBOSE ||
                            Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "sendFirstQueuedMessage " + msgUri +
                                ", address: " + address +
                                ", threadId: " + threadId);
                    }

                    try {
                        sender.sendMessage(SendingProgressTokenManager.NO_TOKEN);;
                        mSending.put(subscription, true);
                    } catch (MmsException e) {
                        Log.e(TAG, "sendFirstQueuedMessage: failed to send message " + msgUri
                                + ", caught ", e);
                        mSending.put(subscription, false);
                        messageFailedToSend(msgUri, SmsManager.RESULT_ERROR_GENERIC_FAILURE);
                        success = false;
                        // Sending current message fails. Try to send more pending messages
                        // if there is any.
                        Intent intent = new Intent(SmsReceiverService.ACTION_SEND_MESSAGE, null,
                                this,
                                SmsReceiver.class);
                        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subscription);
                        sendBroadcast(intent);
                    }
                }
            } finally {
                c.close();
            }
        }
        if (success) {
            // We successfully sent all the messages in the queue. We don't need to
            // be notified of any service changes any longer.
            // In case of MSIM don't unregister service state change if there are any messages
            // pending for process on other subscriptions. There may be a chance of other
            // subscription is register and waiting for sevice state changes to process the message.
            if (!(TelephonyManager.getDefault().getPhoneCount() > 1) ||
                    isUnRegisterAllowed(subscription)) {
                unRegisterForServiceStateChanges();
            }
        }
    }

    private void handleSmsSent(Intent intent, int error) {
        Uri uri = intent.getData();
        int resultCode = intent.getIntExtra("result", 0);
        boolean sendNextMsg = intent.getBooleanExtra(EXTRA_MESSAGE_SENT_SEND_NEXT, false);
        int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                SubscriptionManager.getDefaultSmsSubscriptionId());
        mSending.put(subId, false);
        if (LogTag.DEBUG_SEND) {
            Log.v(TAG, "handleSmsSent uri: " + uri + " sendNextMsg: " + sendNextMsg +
                    " resultCode: " + resultCode +
                    " = " + translateResultCode(resultCode) + " error: " + error);
        }

        if (resultCode == Activity.RESULT_OK) {
            if (sendNextMsg) {
                if (LogTag.DEBUG_SEND || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                    Log.v(TAG, "handleSmsSent: move message to sent folder uri: " + uri);
                }
                if (!Sms.moveMessageToFolder(this, uri, Sms.MESSAGE_TYPE_SENT, error)) {
                    Log.e(TAG, "handleSmsSent: failed to move message " + uri + " to sent folder");
                }
                sendFirstQueuedMessage(subId);

            }

            // Update the notification for failed messages since they may be deleted.
            MessagingNotification.nonBlockingUpdateSendFailedNotification(this);
        } else if ((resultCode == SmsManager.RESULT_ERROR_RADIO_OFF) ||
                (resultCode == SmsManager.RESULT_ERROR_NO_SERVICE)) {
            if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                Log.v(TAG, "handleSmsSent: no service, queuing message w/ uri: " + uri);
            }
            // We got an error with no service or no radio. Register for state changes so
            // when the status of the connection/radio changes, we can try to send the
            // queued up messages.
            registerForServiceStateChanges();
            // We couldn't send the message, put in the queue to retry later.
            Sms.moveMessageToFolder(this, uri, Sms.MESSAGE_TYPE_QUEUED, error);
            mToastHandler.post(new Runnable() {
                public void run() {
                    Toast.makeText(SmsReceiverService.this, getString(R.string.message_queued),
                            Toast.LENGTH_SHORT).show();
                }
            });
        } else if (resultCode == SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE) {
            messageFailedToSend(uri, resultCode);
            mToastHandler.post(new Runnable() {
                public void run() {
                    Toast.makeText(SmsReceiverService.this, getString(R.string.fdn_check_failure),
                            Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            messageFailedToSend(uri, error);
            if (sendNextMsg) {
                sendFirstQueuedMessage(subId);
            }
        }
    }

    private void messageFailedToSend(Uri uri, int error) {
        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE) || LogTag.DEBUG_SEND) {
            Log.v(TAG, "messageFailedToSend msg failed uri: " + uri + " error: " + error);
        }
        Sms.moveMessageToFolder(this, uri, Sms.MESSAGE_TYPE_FAILED, error);
        MessagingNotification.notifySendFailed(getApplicationContext(), true);
    }

    private void handleSmsReceived(Intent intent, int error) {
        if (DEBUG) {
            Log.v(TAG, "==handleSmsReceived==, intent = "+intent);
        }
        SmsMessage[] msgs = Intents.getMessagesFromIntent(intent);
        String format = intent.getStringExtra("format");

        // Because all sub id have been changed to phone id in Mms,
        // so also change it here.
        int saveLoc = MessageUtils.getSmsPreferStoreLocation(this,
                SubscriptionManager.getPhoneId(msgs[0].getSubId()));
        if (getResources().getBoolean(R.bool.config_savelocation)
                && saveLoc == MessageUtils.PREFER_SMS_STORE_CARD) {
            for (int i = 0; i < msgs.length; i++) {
                SmsMessage sms = msgs[i];
                boolean saveSuccess = saveMessageToIcc(sms);
                if (saveSuccess) {
                    int subId = TelephonyManager.getDefault().isMultiSimEnabled()
                            ? sms.getSubId() : (int)MessageUtils.SUB_INVALID;
                    int phoneId = SubscriptionManager.getPhoneId(subId);
                    String address = MessageUtils.convertIdp(this,
                            sms.getDisplayOriginatingAddress(), phoneId);
                    //Log.d(TAG, "handleSmsReceived, Notification address = "+address);
                    MessagingNotification.blockingUpdateNewIccMessageIndicator(
                            this, address, sms.getDisplayMessageBody(),
                            subId, sms.getTimestampMillis());
                    getContentResolver().notifyChange(MessageUtils.getIccUriBySubscription(
                            phoneId), null);
                } else {
                    mToastHandler.post(new Runnable() {
                        public void run() {
                            Toast.makeText(getApplicationContext(),
                                    getString(R.string.pref_sim_card_full_save_to_phone),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                    // save message to phone if failed save to icc.
                    saveMessageToPhone(msgs, error, format);
                    break;
                }
            }
        } else {
            saveMessageToPhone(msgs, error, format);
        }
    }

    private void saveMessageToPhone(SmsMessage[] msgs, int error, String format){
        if (DEBUG) {
            Log.v(TAG, "==saveMessageToPhone==");
        }

        if (DEBUG) Log.v(TAG, "saveMessageToPhone, >>>insertMessage()");
        Uri messageUri = insertMessage(this, msgs, error, format);

        if (DEBUG) {
            SmsMessage sms = msgs[0];
            Log.v(TAG, "saveMessageToPhone" + (sms.isReplace() ? "(replace)" : "") +
                    " messageUri: " + messageUri +
                    ", address: " + sms.getOriginatingAddress() +
                    ", body: " + sms.getMessageBody());
        }

        MessageUtils.checkIsPhoneMessageFull(this);

        //lichao refactor codes in 2016-12-03 begin
        if (messageUri == null) {
            return;
        }

        String phoneNum = msgs[0].getOriginatingAddress();

        if (MmsConfig.TELECOM_OPERATORS_PHONE_NUMBERS.contains(phoneNum)) {
            mNotificationHandler.postDelayed(new Runnable() {
                public void run() {
                    long threadId = MessagingNotification.getSmsThreadId(SmsReceiverService.this, messageUri);
                    if (DEBUG) Log.d(TAG, "saveMessageToPhone(), after 2000ms delay, threadId = "+threadId);
                    if(threadId > 0){
                        parseMessageForXySdk(msgs, messageUri.getLastPathSegment());
                        showMessagingNotification(SmsReceiverService.this, threadId);
                        parseMessageForNetCar(SmsReceiverService.this, msgs, messageUri);
                    }
                }
            }, 2000);
        }else{
            long threadId = MessagingNotification.getSmsThreadId(SmsReceiverService.this, messageUri);
            if (DEBUG) Log.d(TAG, "saveMessageToPhone(), getSmsThreadId = "+threadId);
            if(threadId > 0){
                parseMessageForXySdk(msgs, messageUri.getLastPathSegment());
                showMessagingNotification(SmsReceiverService.this, threadId);
                parseMessageForNetCar(SmsReceiverService.this, msgs, messageUri);
            }else{
                Log.e(TAG, "saveMessageToPhone(), getSmsThreadId = "+threadId);
            }
        }
        //lichao refactor codes in 2016-12-03 end
    }

    //lichao refactor codes in 2016-12-03 begin
    private void parseMessageForXySdk(SmsMessage[] msgs, String msgId){
        //int simIndex =-1;
        final int SMARTSMS_PARSE_TYPE_NOTIFY_POUPP = 1;
        int subId = TelephonyManager.getDefault().isMultiSimEnabled() ? msgs[0].getSubId() : MessageUtils.SUB_INVALID;
        XySdkUtil.parseMsg(msgId, msgs, SMARTSMS_PARSE_TYPE_NOTIFY_POUPP, subId);
    }

    private void showMessagingNotification(Context context, long threadId){
        if(threadId <= 0){
            return;
        }
//            if(DEBUG) Log.d(TAG_XY, "saveMessageToPhone(), >>>callApiToNotification(), phoneNum = "+phoneNum);
//            boolean xiaoyuan_notification_result = SmartSmsNotificationManager.callApiToNotification(this,
//                    Long.parseLong(messageUri.getLastPathSegment()),//msgId
//                    msgs[0].getOriginatingAddress(),
//                    msgs[0].getServiceCenterAddress(),//smsCenterNum
//                    msgs[0].getMessageBody(),//msg
//                    msgs[0].getTimestampMillis(),//smsReceiveTime
//                    null//Map<String, String> extendMap
//            );
//
//            //if return false, call the original methord
//            if(false == xiaoyuan_notification_result)
//            {
                // Called off of the UI thread so ok to block.
                Log.d(TAG, "saveMessageToPhone(), >>>blockingUpdateNewMessageIndicator");
                MessagingNotification.blockingUpdateNewMessageIndicator(context, threadId, false);
                //MessagingNotification.blockingUpdateNewMessageIndicator(this, messageUri, false);
//            }
    }

    private void parseMessageForNetCar(Context context, SmsMessage[] msgs, Uri messageUri){
        if (msgs == null || messageUri == null) {
            return;
        }
        String msgId = messageUri.getLastPathSegment();

        //HashMap<String, String> extend = new HashMap<String, String>();
        //extend.put("msgTime", String.valueOf(msgs[0].getTimestampMillis()));

        //lichao add for test
        String phoneNumber = msgs[0].getOriginatingAddress();
        if (MmsConfig.TEST_PHONE_NUMBERS.contains(StringUtils.getPhoneNumberNo86(phoneNumber))) {
            Log.d(TAG_XY, "saveMessageToPhone(), change phoneNumber from "+phoneNumber+" to 106550101955");
            phoneNumber = "106550101955";
        }

        //lichao add for xiaoyuan net book car in 2016-10-16 begin
            /*
            parseMessage(final String msgId, final String phoneNum,
            final String smsCenterNum, final String smsContent, final long smsReceiveTime,
            int parseFlag, HashMap<String, Object> extend,
            final SdkCallBack xyCallBack, boolean scrollFing)
            */
        ParseSmsMessage.parseMessage(
                msgId,//msgId
                phoneNumber,
                msgs[0].getServiceCenterAddress(),//smsCenterNum
                msgs[0].getMessageBody(),//smsContent
                msgs[0].getTimestampMillis(),//smsReceiveTime
                ParseSmsMessage.DUOQU_SMARTSMS_SHOW_BUBBLE_RICH_FLAG,//parseFlag
                null,//HashMap<String, Object> extend
                getCarCallBack(context, messageUri),//SdkCallBack
                false //scrollFing
        );
        //lichao add for xiaoyuan net book car in 2016-10-16 end
    }
    //lichao refactor codes in 2016-12-03 end

    private void handleCbSmsReceived(Intent intent, int error) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }
        CellBroadcastMessage cbMessage = (CellBroadcastMessage) extras.get("message");
        if (cbMessage == null) {
            return;
        }
        boolean isMSim = TelephonyManager.getDefault().isMultiSimEnabled();
        String country = "";
        if (isMSim) {
            country = TelephonyManager.getDefault().getSimCountryIso(cbMessage.getSubId());
        } else {
            country = TelephonyManager.getDefault().getSimCountryIso();
        }
        int serviceCategory = cbMessage.getServiceCategory();
        if ("in".equals(country) && (serviceCategory == CB_CHANNEL_50 ||
                serviceCategory == CB_CHANNEL_60)) {
            Uri cbMessageUri = storeCbMessage(this, cbMessage, error);
            if (cbMessageUri != null) {
                long threadId = MessagingNotification.getSmsThreadId(this, cbMessageUri);
                // Called off of the UI thread so ok to block.
                MessagingNotification.blockingUpdateNewMessageIndicator(this, threadId, false);
            }
        }

    }

    private void handleBootCompleted() {
        // Some messages may get stuck in the outbox. At this point, they're probably irrelevant
        // to the user, so mark them as failed and notify the user, who can then decide whether to
        // resend them manually.
        moveOutboxMessagesToFailedBox();
        int numUnreadFailed = getUnreadFailedMessageCount();
        if (numUnreadFailed > 0) {
            MessagingNotification.notifySendFailed(getApplicationContext(), true);
        }
        // Send any queued messages that were waiting from before the reboot.
        // // Process queued messages on all SUB's
        List<SubscriptionInfo> subInfoList =
                SubscriptionManager.from(getApplicationContext()).getActiveSubscriptionInfoList();
        if(subInfoList != null) {
            for (SubscriptionInfo info : subInfoList)
            {
                int subId = info.getSubscriptionId();
                if (mSending.get(subId) == null) {
                    mSending.put(subId, false);
                }
                if (mSending.get(subId) != null) {
                    if (!mSending.get(subId)) {
                        sendFirstQueuedMessage(subId);
                    }
                }
            }
        }
        // Called off of the UI thread so ok to block.
        MessagingNotification.blockingUpdateNewMessageIndicator(
                this, MessagingNotification.THREAD_ALL, false);
    }

    private int getUnreadFailedMessageCount() {
        final Uri uri = Uri.parse("content://sms/failed");
        Cursor cursor = SqliteWrapper.query(
                getApplicationContext(), getContentResolver(), uri,
                new String[] { Sms._ID }, "read=0", null, null);
        int count = 0;
        if (cursor != null) {
            count = cursor.getCount();
            cursor.close();
        }

        return count;
    }

    /**
     * Move all messages that are in the outbox to the queued state
     * @return The number of messages that were actually moved
     */
    private int moveOutboxMessagesToQueuedBox() {
        ContentValues values = new ContentValues(1);

        values.put(Sms.TYPE, Sms.MESSAGE_TYPE_QUEUED);

        int messageCount = SqliteWrapper.update(
                getApplicationContext(), getContentResolver(), Outbox.CONTENT_URI,
                values, "type = " + Sms.MESSAGE_TYPE_OUTBOX, null);
        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE) || LogTag.DEBUG_SEND) {
            Log.v(TAG, "moveOutboxMessagesToQueuedBox messageCount: " + messageCount);
        }
        return messageCount;
    }

    /**
     * Move all messages that are in the outbox to the failed state and set them to unread.
     * @return The number of messages that were actually moved
     */
    private int moveOutboxMessagesToFailedBox() {
        ContentValues values = new ContentValues(3);

        values.put(Sms.TYPE, Sms.MESSAGE_TYPE_FAILED);
        values.put(Sms.ERROR_CODE, SmsManager.RESULT_ERROR_GENERIC_FAILURE);
        values.put(Sms.READ, Integer.valueOf(0));

        int messageCount = SqliteWrapper.update(
                getApplicationContext(), getContentResolver(), Outbox.CONTENT_URI,
                values, "type = " + Sms.MESSAGE_TYPE_OUTBOX, null);
        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE) || LogTag.DEBUG_SEND) {
            Log.v(TAG, "moveOutboxMessagesToFailedBox messageCount: " + messageCount);
        }
        return messageCount;
    }

    public static final String CLASS_ZERO_BODY_KEY = "CLASS_ZERO_BODY";

    // This must match the column IDs below.
    private final static String[] REPLACE_PROJECTION = new String[] {
        Sms._ID,
        Sms.ADDRESS,
        Sms.PROTOCOL
    };

    // This must match REPLACE_PROJECTION.
    private static final int REPLACE_COLUMN_ID = 0;

    /**
     * If the message is a class-zero message, display it immediately
     * and return null.  Otherwise, store it using the
     * <code>ContentResolver</code> and return the
     * <code>Uri</code> of the thread containing this message
     * so that we can use it for notification.
     */
    private Uri insertMessage(Context context, SmsMessage[] msgs, int error, String format) {
        // Build the helper classes to parse the messages.
        SmsMessage sms = msgs[0];

        if (sms.getMessageClass() == SmsMessage.MessageClass.CLASS_0) {
            displayClassZeroMessage(context, sms, format);
            return null;
        } else if (sms.isReplace()) {
            return replaceMessage(context, msgs, error);
        } else {
            return storeMessage(context, msgs, error);
        }
    }

    /**
     * This method is used if this is a "replace short message" SMS.
     * We find any existing message that matches the incoming
     * message's originating address and protocol identifier.  If
     * there is one, we replace its fields with those of the new
     * message.  Otherwise, we store the new message as usual.
     *
     * See TS 23.040 9.2.3.9.
     */
    private Uri replaceMessage(Context context, SmsMessage[] msgs, int error) {
        SmsMessage sms = msgs[0];
        ContentValues values = extractContentValues(sms);
        values.put(Sms.ERROR_CODE, error);
        int pduCount = msgs.length;

        if (pduCount == 1) {
            // There is only one part, so grab the body directly.
            values.put(Inbox.BODY, replaceFormFeeds(sms.getDisplayMessageBody()));
        } else {
            // Build up the body from the parts.
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < pduCount; i++) {
                sms = msgs[i];
                if (sms.mWrappedSmsMessage != null) {
                    body.append(sms.getDisplayMessageBody());
                }
            }
            values.put(Inbox.BODY, replaceFormFeeds(body.toString()));
        }

        ContentResolver resolver = context.getContentResolver();
        String originatingAddress = MessageUtils.convertIdp(this,
                sms.getOriginatingAddress(), sms.getSubId());
        int protocolIdentifier = sms.getProtocolIdentifier();
        String selection;
        String[] selectionArgs;

        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, " SmsReceiverService: replaceMessage:");
        }
        selection = Sms.ADDRESS + " = ? AND " +
                    Sms.PROTOCOL + " = ? AND " +
                    Sms.SUBSCRIPTION_ID +  " = ? ";
        selectionArgs = new String[] {
                originatingAddress, Integer.toString(protocolIdentifier),
                Integer.toString(sms.getSubId())
            };

        Cursor cursor = SqliteWrapper.query(context, resolver, Inbox.CONTENT_URI,
                            REPLACE_PROJECTION, selection, selectionArgs, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    long messageId = cursor.getLong(REPLACE_COLUMN_ID);
                    Uri messageUri = ContentUris.withAppendedId(
                            Sms.CONTENT_URI, messageId);

                    SqliteWrapper.update(context, resolver, messageUri,
                                        values, null, null);
                    return messageUri;
                }
            } finally {
                cursor.close();
            }
        }
        return storeMessage(context, msgs, error);
    }

    public static String replaceFormFeeds(String s) {
        // Some providers send formfeeds in their messages. Convert those formfeeds to newlines.
        return s == null ? "" : s.replace('\f', '\n');
    }

//    private static int count = 0;

    private Uri storeMessage(Context context, SmsMessage[] msgs, int error) {
        if (DEBUG) {
            Log.v(TAG, "==storeMessage==");
        }
        // Check to see whether short message count is up to 2000 for cmcc
        if (MessageUtils.checkIsPhoneMessageFull(this)) {
            return null;
        }

        SmsMessage sms = msgs[0];

        // Store the message in the content provider.
        ContentValues values = extractContentValues(sms);
        values.put(Sms.ERROR_CODE, error);
        values.put(Sms.SUBSCRIPTION_ID, sms.getSubId());
        //lichao add for xiaoyuan net book car in 2016-10-24
        values.put(Sms.CAR_CODE, 0);

        int pduCount = msgs.length;

        if (pduCount == 1) {
            // There is only one part, so grab the body directly.
            values.put(Inbox.BODY, replaceFormFeeds(sms.getDisplayMessageBody()));
        } else {
            // Build up the body from the parts.
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < pduCount; i++) {
                sms = msgs[i];
                if (sms.mWrappedSmsMessage != null) {
                    body.append(sms.getDisplayMessageBody());
                }
            }
            values.put(Inbox.BODY, replaceFormFeeds(body.toString()));
        }

        // Make sure we've got a thread id so after the insert we'll be able to delete
        // excess messages.
        Long threadId = values.getAsLong(Sms.THREAD_ID);
        String address = values.getAsString(Sms.ADDRESS);

        // Code for debugging and easy injection of short codes, non email addresses, etc.
        // See Contact.isAlphaNumber() for further comments and results.
//        switch (count++ % 8) {
//            case 0: address = "AB12"; break;
//            case 1: address = "12"; break;
//            case 2: address = "Jello123"; break;
//            case 3: address = "T-Mobile"; break;
//            case 4: address = "Mobile1"; break;
//            case 5: address = "Dogs77"; break;
//            case 6: address = "****1"; break;
//            case 7: address = "#4#5#6#"; break;
//        }

        if (!TextUtils.isEmpty(address)) {
            Contact cacheContact = Contact.get(address,true);
            if (cacheContact != null) {
                address = cacheContact.getNumber();
            }
        } else if (TextUtils.isEmpty(address)
                && getResources().getBoolean(R.bool.def_hide_unknown_sender)) {
            values.put(Sms.ADDRESS, "");
        } else {
            address = getString(R.string.unknown_sender);
            values.put(Sms.ADDRESS, address);
        }

        if (((threadId == null) || (threadId <= 0)) && (address != null)) {
		    //ligy modify begin
            if(MmsApp.isCreateConversaitonIdBySim) {
                threadId = Conversation.getOrCreateThreadId(context, address, sms.getSubId());
            } else {
                threadId = Conversation.getOrCreateThreadId(context, address);	
            }
			//ligy modify end
            values.put(Sms.THREAD_ID, threadId);
        }

        ContentResolver resolver = context.getContentResolver();

        Uri insertedUri = SqliteWrapper.insert(context, resolver, Inbox.CONTENT_URI, values);

        // Now make sure we're not over the limit in stored messages
        Recycler.getSmsRecycler().deleteOldMessagesByThreadId(context, threadId);
        //MmsWidgetProvider.notifyDatasetChanged(context);

        return insertedUri;
    }

    private Uri storeCbMessage(Context context, CellBroadcastMessage sms, int error) {
        // Store the broadcast message in the content provider.
        ContentValues values = new ContentValues();
        values.put(Sms.ERROR_CODE, error);
        values.put(Sms.SUBSCRIPTION_ID, sms.getSubId());

        // CB messages are concatenated by telephony framework into a single
        // message in intent, so grab the body directly.
        values.put(Inbox.BODY, sms.getMessageBody());

        // Make sure we've got a thread id so after the insert we'll be able to
        // delete excess messages.
        String address = getString(R.string.cell_broadcast_sender)
                + Integer.toString(sms.getServiceCategory());
        values.put(Sms.ADDRESS, address);
		//ligy modify begin
        Long threadId;
        if(MmsApp.isCreateConversaitonIdBySim) {
        	threadId = Conversation.getOrCreateThreadId(context, address, sms.getSubId());
        } else {
            threadId = Conversation.getOrCreateThreadId(context, address);	
        }
		//ligy modify end
        values.put(Sms.THREAD_ID, threadId);
        values.put(Inbox.READ, 0);
        values.put(Inbox.SEEN, 0);

        ContentResolver resolver = context.getContentResolver();
        Uri insertedUri = SqliteWrapper.insert(context, resolver, Inbox.CONTENT_URI, values);

        // Now make sure we're not over the limit in stored messages
        Recycler.getSmsRecycler().deleteOldMessagesByThreadId(context, threadId);
        //MmsWidgetProvider.notifyDatasetChanged(context);

        return insertedUri;
    }

    /**
     * Extract all the content values except the body from an SMS
     * message.
     */
    private ContentValues extractContentValues(SmsMessage sms) {
        // Store the message in the content provider.
        ContentValues values = new ContentValues();

        values.put(Inbox.ADDRESS, MessageUtils.convertIdp(this, sms.getDisplayOriginatingAddress(),
                sms.getSubId()));

        // Use now for the timestamp to avoid confusion with clock
        // drift between the handset and the SMSC.
        // Check to make sure the system is giving us a non-bogus time.
        Calendar buildDate = new GregorianCalendar(2011, 8, 18);    // 18 Sep 2011
        Calendar nowDate = new GregorianCalendar();
        long now = System.currentTimeMillis();
        nowDate.setTimeInMillis(now);

        if (nowDate.before(buildDate)) {
            // It looks like our system clock isn't set yet because the current time right now
            // is before an arbitrary time we made this build. Instead of inserting a bogus
            // receive time in this case, use the timestamp of when the message was sent.
            now = sms.getTimestampMillis();
        }

        values.put(Inbox.DATE, new Long(now));
        values.put(Inbox.DATE_SENT, Long.valueOf(sms.getTimestampMillis()));
        values.put(Inbox.PROTOCOL, sms.getProtocolIdentifier());
        values.put(Inbox.READ, 0);
        values.put(Inbox.SEEN, 0);
        if (sms.getPseudoSubject().length() > 0) {
            values.put(Inbox.SUBJECT, sms.getPseudoSubject());
        }
        values.put(Inbox.REPLY_PATH_PRESENT, sms.isReplyPathPresent() ? 1 : 0);
        values.put(Inbox.SERVICE_CENTER, sms.getServiceCenterAddress());
        return values;
    }

    /**
     * Displays a class-zero message immediately in a pop-up window
     * with the number from where it received the Notification with
     * the body of the message
     *
     */
    private void displayClassZeroMessage(Context context, SmsMessage sms, String format) {
        int subId = sms.getSubId();

        // Using NEW_TASK here is necessary because we're calling
        // startActivity from outside an activity.
        Intent smsDialogIntent = new Intent(context, ClassZeroActivity.class)
                .putExtra("pdu", sms.getPdu())
                .putExtra("format", format)
                .putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                          | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

        context.startActivity(smsDialogIntent);
    }

    private void registerForServiceStateChanges() {
        Context context = getApplicationContext();
        unRegisterForServiceStateChanges();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE) || LogTag.DEBUG_SEND) {
            Log.v(TAG, "registerForServiceStateChanges");
        }

        context.registerReceiver(SmsReceiver.getInstance(), intentFilter);
    }

    private void unRegisterForServiceStateChanges() {
        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE) || LogTag.DEBUG_SEND) {
            Log.v(TAG, "unRegisterForServiceStateChanges");
        }
        try {
            Context context = getApplicationContext();
            context.unregisterReceiver(SmsReceiver.getInstance());
        } catch (IllegalArgumentException e) {
            // Allow un-matched register-unregister calls
        }
    }

    // Returns true, if there are no queued messages on other subscriptions
    private boolean isUnRegisterAllowed(int subId) {
        boolean success = true;
        List<SubscriptionInfo> subInfoList =
                SubscriptionManager.from(getApplicationContext()).getActiveSubscriptionInfoList();
        if(subInfoList != null) {
            final Uri uri = Uri.parse("content://sms/queued");
            ContentResolver resolver = getContentResolver();
            String where = "sub_id == ?";
            for (SubscriptionInfo info : subInfoList) {
                int subInfoSubId = info.getSubscriptionId();
                if (subInfoSubId == subId) {
                    String[] whereArgs = new String[] {Integer.toString(subId)};
                    Cursor c = SqliteWrapper.query(this, resolver, uri,
                            SEND_PROJECTION, where, whereArgs, "date ASC");
                    if (c != null) {
                        try {
                            if (c.moveToFirst()) {
                                success = false;
                            }
                        } finally {
                            c.close();
                        }
                    }
                    if(!success) {
                        return success;
                    }
                }
            }
        }
        return success;
    }

    private boolean saveMessageToIcc(SmsMessage sms) {
        boolean result = true;
        int subscription = sms.getSubId();
        String address = MessageUtils.convertIdp(this, sms.getOriginatingAddress(),
            subscription);
        ContentValues values = new ContentValues();
        values.put(PhoneConstants.SUBSCRIPTION_KEY, subscription);
        values.put(Sms.ADDRESS, address);
        values.put(Sms.BODY, sms.getMessageBody());
        values.put(MessageUtils.SMS_BOX_ID, Sms.MESSAGE_TYPE_INBOX);
        values.put(Sms.DATE, sms.getTimestampMillis());
        Uri uri = getContentResolver().insert(MessageUtils.ICC_URI, values);
        if (uri != null) {
            result = MessageUtils.COPY_SMS_INTO_SIM_SUCCESS.equals(uri.getLastPathSegment());
        }
        return result;
    }

    //lichao add for xiaoyuan net book car in 2016-10-16 begin
    private SdkCallBack carSdkCallBack =null;
    private final static int DUOQU_CALLBACK_UITHREAD_NEEDPARSE=-2;
    private final static int DUOQU_CALLBACK_UITHREAD_NODATA=-1;
    private final static int DUOQU_CALLBACK_UITHREAD_HASDATA=0;
    private final static int DUOQU_CALLBACK_BACKTHREAD_HASDATA=1;
    private JSONObject mCacheCarJSONData = null;

    private SdkCallBack  getCarCallBack(Context context, Uri messageUri){
        if(DEBUG) Log.d(TAG_XY, "getCarCallBack(), messageUri = "+messageUri);
        if(carSdkCallBack ==null){
            carSdkCallBack = new SdkCallBack(){
                public void execute(final Object... obj) {
                    if(obj==null || obj.length==0){
                        if(DEBUG) Log.d(TAG_XY, "new SdkCallBack(), return");
                        return;
                    }
                    final int status = (Integer)obj[0];
                    if(DEBUG) Log.d(TAG_XY, "getCarCallBack(), statu = "+status);
                    switch(status){
                        case DUOQU_CALLBACK_UITHREAD_NEEDPARSE:
                            if(DEBUG) Log.d(TAG_XY, "getCarCallBack(), NEED PARSE");
                            break;
                        case DUOQU_CALLBACK_UITHREAD_NODATA:
                            if(DEBUG) Log.d(TAG_XY, "getCarCallBack(), NO DATA");
                            break;
                        case DUOQU_CALLBACK_UITHREAD_HASDATA:
                            if(DEBUG) Log.d(TAG_XY, "getCarCallBack(), UITHREAD HAS DATA");
                        case DUOQU_CALLBACK_BACKTHREAD_HASDATA:
                            if(DEBUG) Log.d(TAG_XY, "getCarCallBack(), BACKTHREAD HAS DATA");
                            mCacheCarJSONData = (JSONObject)obj[1];
                            boolean isCarMsg = parseCarJSONData(mCacheCarJSONData);
                            if(isCarMsg){
                                if(DEBUG) Log.d(TAG_XY, "getCarCallBack(), isCarMsg, >>>markAsCarMsg()");
                                markAsCarMsg(context, messageUri);
                            }
                            break;
                        default:
                            break;
                    }
                }
            };
        }
        return carSdkCallBack;
    }

    private boolean parseCarJSONData(JSONObject carJSONData){

        if(DEBUG) Log.d(TAG_XY, "parseCarJSONData(), carJSONData: "+carJSONData.toString());
        /*
        您于（18:08）在（香格里拉大酒店）的订单，已被张某某（车牌粤B58888，手机18655558888）接单，感谢使用 .【嘀嘀打车】
         */
        String driver_name = getJSONDataString(carJSONData, "t_na_dver");//姓名
        String driver_phone = getJSONDataString(carJSONData, "t_ph");//手机
        String car_type = getJSONDataString(carJSONData, "t_ty_car");//车型
        String car_plate_number = getJSONDataString(carJSONData, "t_num_car");//车牌号
        String pickup_point = getJSONDataString(carJSONData, "t_add");//上车地点
        String order_status = getJSONDataString(carJSONData, "t_st");//状态
        String pickup_date = getJSONDataString(carJSONData, "t_date");//日期
        String pickup_time = getJSONDataString(carJSONData, "t_time");//时间
        String book_person_name = getJSONDataString(carJSONData, "t_na_bker");//预订人
        String passenger_name = getJSONDataString(carJSONData, "t_na_pger");//乘客
        String currency_type = getJSONDataString(carJSONData, "t_mt");//币种
        String little_extra_money = getJSONDataString(carJSONData, "t_my_tip");//小费
        String car_company = getJSONDataString(carJSONData, "t_ut");//单位
        String reminder = getJSONDataString(carJSONData, "t_tips");//提示
        String platform_name = getJSONDataString(carJSONData, "t_channel_name");//约车平台

//        String driver_name = getJSONDataString(carJSONData, "traffic_order_driver");//姓名
//        String driver_phone = getJSONDataString(carJSONData, "traffic_order_phonenum");//手机
//        String car_type = getJSONDataString(carJSONData, "t_ty_car");//车型
//        String car_plate_number = getJSONDataString(carJSONData, "traffic_order_carnum");//车牌号
//        String pickup_point = getJSONDataString(carJSONData, "traffic_order_add");//上车地点
//        String order_status = getJSONDataString(carJSONData, "t_st");//状态
//        String pickup_date = getJSONDataString(carJSONData, "t_date");//日期
//        String pickup_time = getJSONDataString(carJSONData, "traffic_order_time");//时间
//        String book_person_name = getJSONDataString(carJSONData, "t_na_bker");//预订人
//        String passenger_name = getJSONDataString(carJSONData, "t_na_pger");//乘客
//        String currency_type = getJSONDataString(carJSONData, "t_mt");//币种
//        String little_extra_money = getJSONDataString(carJSONData, "t_my_tip");//小费
//        String car_company = getJSONDataString(carJSONData, "t_ut");//单位
//        String reminder = getJSONDataString(carJSONData, "t_tips");//提示
//        String platform_name = getJSONDataString(carJSONData, "traffic_order_channelname");//约车平台

        if(DEBUG) Log.d(TAG_XY, "parseCarJSONData(), 司机姓名： "+driver_name);
        if(DEBUG) Log.d(TAG_XY, "parseCarJSONData(), 司机电话： "+driver_phone);
        if(DEBUG) Log.d(TAG_XY, "parseCarJSONData(), 车子型号： "+car_type);
        if(DEBUG) Log.d(TAG_XY, "parseCarJSONData(), 车牌号码： "+car_plate_number);
        if(DEBUG) Log.d(TAG_XY, "parseCarJSONData(), 上车地点： "+pickup_point);
        if(DEBUG) Log.d(TAG_XY, "parseCarJSONData(), 订单状态： "+order_status);
        if(DEBUG) Log.d(TAG_XY, "parseCarJSONData(), 上车日期： "+pickup_date);
        if(DEBUG) Log.d(TAG_XY, "parseCarJSONData(), 上车时间： "+pickup_time);
        if(DEBUG) Log.d(TAG_XY, "parseCarJSONData(), 约车单位： "+car_company);
        if(DEBUG) Log.d(TAG_XY, "parseCarJSONData(), 约车平台： "+platform_name);
        
        if(!TextUtils.isEmpty(driver_phone)
                && !TextUtils.isEmpty(car_plate_number)
                && !TextUtils.isEmpty(pickup_time)
                && !TextUtils.isEmpty(platform_name)
                ){
            return true;
        }
        if(DEBUG) Log.d(TAG_XY, "parseCarJSONData(), return false");
        return false;
    }

    private String getJSONDataString(JSONObject carJSONData, String str_key){
        String str_value = null;
        try {
            str_value = carJSONData.getString(str_key);
        }
        catch (JSONException e) {
            if(DEBUG) Log.w(TAG_XY, "getJSONDataString(), JSONException: "+e);
        }
        return str_value;
    }

    public static final int MESSAGE_BOOK_CAR = 1;
    private void markAsCarMsg(Context context, final Uri msgUri) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    ContentValues values = new ContentValues(1);
                    values.put(Sms.CAR_CODE, MESSAGE_BOOK_CAR);
                    SqliteWrapper.update(context, getContentResolver(),
                            msgUri, values, null, null);
                } catch (Exception e) {
                    Log.e(TAG, "Update CAR_CODE Error", e);
                }
            }
        }).start();
    }
    //lichao add for xiaoyuan net book car in 2016-10-16 end
}
