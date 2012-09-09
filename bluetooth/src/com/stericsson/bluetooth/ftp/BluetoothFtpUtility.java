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

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.stericsson.bluetooth.R;
import com.google.android.collect.Lists;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * This class has some utilities for Ftp application;
 */
public class BluetoothFtpUtility {

	private static final String TAG = "BluetoothFtpUtility";
	private static final boolean D = Constants.DEBUG;
	private static final boolean V = Constants.VERBOSE;

	/**
	 * Helper function to build the progress text.
	 */
	public static String formatProgressText(long totalBytes, long currentBytes) {
		if (totalBytes <= 0) {
			return "0%";
		}
		long progress = currentBytes * 100 / totalBytes;
		StringBuilder sb = new StringBuilder();
		sb.append(progress);
		sb.append('%');
		return sb.toString();
	}

	/**
	 * Get status description according to status code.
	 */
	public static String getStatusDescription(Context context, int statusCode) {
		String ret;
		if (statusCode == BluetoothShare.STATUS_PENDING) {
			ret = context.getString(R.string.status_pending);
		} else if (statusCode == BluetoothShare.STATUS_RUNNING) {
			ret = context.getString(R.string.status_running);
		} else if (statusCode == BluetoothShare.STATUS_SUCCESS) {
			ret = context.getString(R.string.status_success);
		} else if (statusCode == BluetoothShare.STATUS_NOT_ACCEPTABLE) {
			ret = context.getString(R.string.status_not_accept);
		} else if (statusCode == BluetoothShare.STATUS_FORBIDDEN) {
			ret = context.getString(R.string.status_forbidden);
		} else if (statusCode == BluetoothShare.STATUS_CANCELED) {
			ret = context.getString(R.string.status_canceled);
		} else if (statusCode == BluetoothShare.STATUS_FILE_ERROR) {
			ret = context.getString(R.string.status_file_error);
		} else if (statusCode == BluetoothShare.STATUS_ERROR_NO_SDCARD) {
			ret = context.getString(R.string.status_no_sd_card);
		} else if (statusCode == BluetoothShare.STATUS_CONNECTION_ERROR) {
			ret = context.getString(R.string.status_connection_error);
		} else if (statusCode == BluetoothShare.STATUS_ERROR_SDCARD_FULL) {
			ret = context.getString(R.string.status_no_space);
		} else if ((statusCode == BluetoothShare.STATUS_BAD_REQUEST)
				|| (statusCode == BluetoothShare.STATUS_LENGTH_REQUIRED)
				|| (statusCode == BluetoothShare.STATUS_PRECONDITION_FAILED)
				|| (statusCode == BluetoothShare.STATUS_UNHANDLED_OBEX_CODE)
				|| (statusCode == BluetoothShare.STATUS_OBEX_DATA_ERROR)) {
			ret = context.getString(R.string.status_protocol_error);
		} else {
			ret = context.getString(R.string.status_unknown_error);
		}
		return ret;
	}
}
