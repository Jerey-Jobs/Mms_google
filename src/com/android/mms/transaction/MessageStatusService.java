/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.IntentService;
import android.app.Service;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Inbox;
import android.telephony.SmsMessage;
import android.util.Log;

import com.android.mms.LogTag;
import com.android.mms.MmsConfig;

/**
 * Service that gets started by the MessageStatusReceiver when a message status report is
 * received.
 */
public class MessageStatusService extends IntentService {
    private static final String[] ID_PROJECTION = new String[] { Sms._ID };
    private static final String LOG_TAG = LogTag.TAG;
    private static final Uri STATUS_URI = Uri.parse("content://sms/status");

    public MessageStatusService() {
        // Class name will be the thread name.
        super(MessageStatusService.class.getName());

        // Intent should be redelivered if the process gets killed before completing the job.
        setIntentRedelivery(true);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!MmsConfig.isSmsEnabled(this)) {
            Log.d(LOG_TAG, "MessageStatusService: is not the default sms app");
            return;
        }
        // This method is called on a worker thread.

        Uri messageUri = intent.getData();
        byte[] pdu = intent.getByteArrayExtra("pdu");
        String format = intent.getStringExtra("format");

        SmsMessage message = updateMessageStatus(this, messageUri, pdu, format);

        // Called on a background thread, so it's OK to block.
        if (message != null && message.getStatus() < Sms.STATUS_PENDING) {
            MessagingNotification.blockingUpdateNewMessageIndicator(this,
                    MessagingNotification.THREAD_NONE, message.isStatusReportMessage());
        }
    }

    private SmsMessage updateMessageStatus(Context context, Uri messageUri, byte[] pdu,
            String format) {
        SmsMessage message = SmsMessage.createFromPdu(pdu, format);
        if (message == null) {
            return null;
        }
        // Create a "status/#" URL and use it to update the
        // message's status in the database.
        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                            messageUri, ID_PROJECTION, null, null, null);

        try {
            if (cursor.moveToFirst()) {
                int messageId = cursor.getInt(0);

                Uri updateUri = ContentUris.withAppendedId(STATUS_URI, messageId);
                int status = message.getStatus();
                boolean isStatusReport = message.isStatusReportMessage();
                ContentValues contentValues = new ContentValues(2);

                if (Log.isLoggable(LogTag.TAG, Log.DEBUG)) {
                    log("updateMessageStatus: msgUrl=" + messageUri + ", status=" + status +
                            ", isStatusReport=" + isStatusReport);
                }

                contentValues.put(Sms.STATUS, status);
                contentValues.put(Inbox.DATE_SENT, System.currentTimeMillis());
                SqliteWrapper.update(context, context.getContentResolver(),
                                    updateUri, contentValues, null, null);
            } else {
                error("Can't find message for status update: " + messageUri);
            }
        }catch(NullPointerException e){
            return null;
        }finally {
            cursor.close();
        }
        return message;
    }

    private void error(String message) {
        Log.e(LOG_TAG, "[MessageStatusReceiver] " + message);
    }

    private void log(String message) {
        Log.d(LOG_TAG, "[MessageStatusReceiver] " + message);
    }
}
