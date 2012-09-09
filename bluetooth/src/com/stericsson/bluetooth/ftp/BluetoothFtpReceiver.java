/*
 * Copyright (c) 2008-2009, Motorola, Inc.
 * Copyright (C) ST-Ericsson SA 2010
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.stericsson.bluetooth.ftp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothDevicePicker;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;
import android.widget.Toast;

import com.stericsson.bluetooth.R;

/**
 * Receives and handles: system broadcasts; Intents from other applications; Intents from
 * FtpService; Intents from modules in Ftp application layer.
 */
public class BluetoothFtpReceiver extends BroadcastReceiver {

	private static final String TAG = "BluetoothFtpReceiver";
	private static final boolean V = Constants.VERBOSE;

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		if (V) Log.v(TAG, "Received intent: " + action);

		if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
			context.startService(new Intent(context, BluetoothFtpService.class));
		} else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
			if (BluetoothAdapter.STATE_ON == intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
					BluetoothAdapter.ERROR)) {
				if (V) Log.v(TAG, "Received BLUETOOTH_STATE_CHANGED_ACTION, BLUETOOTH_STATE_ON");
				context.startService(new Intent(context, BluetoothFtpService.class));

				// If this is within a sending process, continue the handle logic to display device
				// picker dialog.
				synchronized (this) {
					if (BluetoothFtpManager.getInstance(context).mSendingFlag) {
						// reset the flags
						BluetoothFtpManager.getInstance(context).mSendingFlag = false;

						Intent in1 = new Intent(BluetoothDevicePicker.ACTION_LAUNCH);
						in1.putExtra(BluetoothDevicePicker.EXTRA_NEED_AUTH, false);
						in1.putExtra(BluetoothDevicePicker.EXTRA_FILTER_TYPE,
								BluetoothDevicePicker.FILTER_TYPE_TRANSFER);
						in1.putExtra(BluetoothDevicePicker.EXTRA_LAUNCH_PACKAGE,
								Constants.THIS_PACKAGE_NAME);
						in1.putExtra(BluetoothDevicePicker.EXTRA_LAUNCH_CLASS,
								BluetoothFtpReceiver.class.getName());

						in1.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						context.startActivity(in1);
					}
				}
			}
		} else if (action.equals(BluetoothDevicePicker.ACTION_DEVICE_SELECTED)) {

			BluetoothFtpManager mFtpManager = BluetoothFtpManager.getInstance(context);
			BluetoothDevice remoteDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

			if (V) Log.v(TAG, "Received BT device selected intent, bt device: " + remoteDevice);

			// Insert transfer session record to database
			mFtpManager.startTransfer(remoteDevice);

			// Display toast message
			String deviceName = mFtpManager.getDeviceName(remoteDevice);
			String toastMsg;
			if (mFtpManager.mMultipleFlag) {
				toastMsg = context.getString(R.string.bt_toast_send_batch, Integer
						.toString(mFtpManager.mfileNumInBatch), deviceName);
			} else {
				toastMsg = context.getString(R.string.bt_toast_send_single, deviceName);
			}
			Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show();
		} else if (action.equals(BluetoothShare.INCOMING_FILE_CONFIRMATION_REQUEST_ACTION)) {
			if (V) Log.v(TAG, "Receiver INCOMING_FILE_NOTIFICATION");

			Toast.makeText(context, context.getString(R.string.incoming_file_toast_msg),
					Toast.LENGTH_SHORT).show();

		} else if (action.equals(Constants.ACTION_HIDE)) {
			if (V) Log.v(TAG, "Receiver hide for " + intent.getData());
			Cursor cursor = context.getContentResolver().query(intent.getData(), null, null, null,
					null);
			if (cursor != null) {
				if (cursor.moveToFirst()) {
					int statusColumn = cursor.getColumnIndexOrThrow(BluetoothShare.STATUS);
					int status = cursor.getInt(statusColumn);
					int visibilityColumn = cursor.getColumnIndexOrThrow(BluetoothShare.VISIBILITY);
					int visibility = cursor.getInt(visibilityColumn);
					int userConfirmationColumn = cursor
							.getColumnIndexOrThrow(BluetoothShare.USER_CONFIRMATION);
					int userConfirmation = cursor.getInt(userConfirmationColumn);
					if ((BluetoothShare.isStatusCompleted(status) || (userConfirmation == BluetoothShare.USER_CONFIRMATION_PENDING))
							&& visibility == BluetoothShare.VISIBILITY_VISIBLE) {
						ContentValues values = new ContentValues();
						values.put(BluetoothShare.VISIBILITY, BluetoothShare.VISIBILITY_HIDDEN);
						context.getContentResolver().update(intent.getData(), values, null, null);
						if (V) Log.v(TAG, "Action_hide received and db updated");
					}
				}
				cursor.close();
			}
		}
	}
}
