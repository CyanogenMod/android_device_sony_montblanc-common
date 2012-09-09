 /*
 * Copyright (C) 2010, ST-Ericsson SA
 * Author: Arek Lichwa <arkadiusz.lichwa@tieto.com> for ST-Ericsson
 * Licence terms: 3-clause BSD
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
 * - Neither the name of the ST-Ericsson SA nor the names of its contributors
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

import com.stericsson.bluetooth.R;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.InputFilter.LengthFilter;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;

/**
 * BluetoothFtpActivity asks the user to authorize ftp connection
 * with remote Bluetooth device. It is an activity that appears as a dialog.
 */
public class BluetoothFtpActivity extends AlertActivity implements DialogInterface.OnClickListener
{
	private static final String TAG = "BluetoothFtpActivity";
	private static final boolean D = Constants.DEBUG;

	private CheckBox mAlwaysAllowed;
	private boolean mTimeout = false;
	private boolean mAlwaysAllowedValue = false;
	private static final int DISMISS_TIMEOUT_DIALOG = 0;
	private static final int DISMISS_TIMEOUT_DIALOG_VALUE_IN_MS = 5000;

	private View mView;
	private TextView mMessageView;
	private String mRemoteDevName = null;

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (D) Log.d(TAG, "Local receiver got message: "+ intent.getAction());
			if (Constants.USER_CONFIRM_TIMEOUT_ACTION.equals(intent.getAction())) {
				onTimeout();
			}
		}
	};
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		if (!intent.getAction().equals(Constants.AUTHORIZE_REQUEST_ACTION))
		{
			Log.e(TAG,
				  "Error: this activity may be started only with intent " +
				  Constants.AUTHORIZE_REQUEST_ACTION);
			finish();
		}
		showFtpDialog();
		registerReceiver(mReceiver, new IntentFilter(Constants.USER_CONFIRM_TIMEOUT_ACTION));
	}

	private void showFtpDialog() {
		final AlertController.AlertParams p = mAlertParams;
		p.mIconId = android.R.drawable.ic_dialog_info;
		p.mTitle = getString(R.string.ftp_authorization_request);
		p.mView = createView();
		p.mPositiveButtonText = getString(android.R.string.yes);
		p.mPositiveButtonListener = this;
		p.mNegativeButtonText = getString(android.R.string.no);
		p.mNegativeButtonListener = this;
		setupAlert();
	}

	private View createView() {
		mView = getLayoutInflater().inflate(R.layout.ftp_access, null);
		mRemoteDevName = BluetoothFtpService.getRemoteDeviceName();
		mMessageView = (TextView) mView.findViewById(R.id.message);
		String dialogMessage = getString(R.string.ftp_authorization_msg, mRemoteDevName);
		dialogMessage += getString(R.string.ftp_authorization_service_msg);
		dialogMessage += getString(R.string.ftp_authorization_service_name);
		mMessageView.setText(dialogMessage);

		mAlwaysAllowed = (CheckBox)mView.findViewById(R.id.ftp_alwaysallowed);
		mAlwaysAllowed.setChecked(false);
		mAlwaysAllowed.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					mAlwaysAllowedValue = true;
				} else {
					mAlwaysAllowedValue = false;
				}
			}
		});
		return mView;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(mReceiver);
	}

	private void dismissDialog() {
		this.dismiss();
	}

	private void onPositive() {
		if (!mTimeout) {
			sendIntentToReceiver(Constants.AUTHORIZE_ALLOWED_ACTION,
					Constants.EXTRA_AUTHORIZE_ALWAYS_ALLOWED, mAlwaysAllowedValue);
		}
		mTimeout = false;
		finish();
	}

	private void onNegative() {
		sendIntentToReceiver(Constants.AUTHORIZE_DISALLOWED_ACTION, null, false);
		finish();
	}

	private void sendIntentToReceiver(final String intentName, final String extraName,
			final boolean extraValue) {
		Intent intent = new Intent(intentName);
		if (extraName != null) {
			intent.putExtra(extraName, extraValue);
		}
		sendBroadcast(intent);
	}

	public void onClick(DialogInterface dialog, int which) {
		switch (which) {
			case DialogInterface.BUTTON_POSITIVE:
				onPositive();
				break;

			case DialogInterface.BUTTON_NEGATIVE:
				onNegative();
				break;
		}
	}

	private void onTimeout() {
		mTimeout = true;
		mMessageView.setText(getString(R.string.ftp_acceptance_timeout_message, mRemoteDevName));
		mAlert.getButton(DialogInterface.BUTTON_NEGATIVE).setVisibility(View.GONE);
		mAlwaysAllowed.setVisibility(View.GONE);
		mAlwaysAllowed.clearFocus();
		mRemoteDevName = null;
		mTimeoutHandler.sendMessageDelayed(mTimeoutHandler.obtainMessage(DISMISS_TIMEOUT_DIALOG),
				DISMISS_TIMEOUT_DIALOG_VALUE_IN_MS);
	}

	private final Handler mTimeoutHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if (D) Log.d(TAG, "Timeout handler got message: " + msg.what);
			switch (msg.what) {
				case DISMISS_TIMEOUT_DIALOG:
					if (D) Log.d(TAG, "Received DISMISS_TIMEOUT_DIALOG msg.");
					finish();
					break;
				default:
					break;
			}
		}
	};
}
